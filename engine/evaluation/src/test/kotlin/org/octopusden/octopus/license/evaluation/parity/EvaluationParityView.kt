package org.octopusden.octopus.license.evaluation.parity

import com.fasterxml.jackson.databind.JsonNode
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment

/**
 * One component version's evaluation as both the prototype (`components/<c>.json`, stage 11) and the
 * service see it, so the two compare field by field (D11). The libraries themselves, their files and
 * sources are the inventory's parity view; this one adds only what evaluation decides.
 *
 * @param libraries rendered id to its verdict, whether an override applied, and the licenses found.
 * @param skippedPerFile released file entry to the packages skipped in it; files with none are left out.
 * @param notAllowed the subjects of the prototype's `LICENCE_NOT_ALLOWED`.
 */
data class EvaluationParityView(
    val libraries: Map<String, LibraryView>,
    val osPackages: Int,
    val baseImagePython: Int,
    val skippedPerFile: Map<String, Int>,
    val notAllowed: Set<String>,
) {
    data class LibraryView(
        val verdict: String,
        val overridden: Boolean,
        val licensesFound: Set<String>,
    )

    companion object {
        private const val NOT_ALLOWED_CODE = "LICENCE_NOT_ALLOWED"
        private val VIOLATING = setOf(LibraryVerdict.UNKNOWN, LibraryVerdict.NOT_ALLOWED)

        fun of(assessment: LicenseAssessment) =
            EvaluationParityView(
                libraries =
                    assessment.libraries.associate {
                        it.library.id.toString() to LibraryView(it.verdict.name, it.overridden, it.library.licenses)
                    },
                osPackages = assessment.skipped.osPackages,
                baseImagePython = assessment.skipped.baseImagePython,
                skippedPerFile = assessment.skipped.perFile,
                notAllowed = assessment.libraries
                    .filter { it.verdict in VIOLATING }
                    .map { it.library.id.toString() }
                    .toSet(),
            )

        fun ofPrototype(component: JsonNode) =
            EvaluationParityView(
                libraries =
                    component.path("components").associate { row ->
                        row.path("purl").asText() to
                            LibraryView(
                                verdict(row.path("verdict").asText()),
                                row.path("overridden").asBoolean(),
                                row.path("xray_licences").map { it.asText() }.toSet(),
                            )
                    },
                osPackages = component.path("os_packages_skipped").asInt(),
                baseImagePython = component.path("docker_python_skipped").asInt(),
                skippedPerFile =
                    component
                        .path("artifacts")
                        .filter { it.path("os_skipped").asInt() > 0 }
                        .associate { it.path("entry").asText() to it.path("os_skipped").asInt() },
                notAllowed =
                    component
                        .path("violations")
                        .filter { it.path("code").asText() == NOT_ALLOWED_CODE }
                        .map { it.path("subject").asText() }
                        .toSet(),
            )

        private fun verdict(prototype: String) = if (prototype == "REVIEW_HASH_ONLY") LibraryVerdict.UNIDENTIFIED.name else prototype
    }
}
