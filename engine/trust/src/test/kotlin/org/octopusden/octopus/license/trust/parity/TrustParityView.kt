package org.octopusden.octopus.license.trust.parity

import com.fasterxml.jackson.databind.JsonNode
import org.octopusden.octopus.license.trust.model.TrustGrade

/**
 * One component version's trust as both the prototype (`components/<c>.json`, stage 12) and the
 * service see it, in the service's spelling, so the two compare field by field (D10).
 *
 * @param reasons code and detail; a set, as the scan's findings can come in another order.
 * @param files every graded file's entry to its level and codes; files not selected are left out.
 */
data class TrustParityView(
    val level: String,
    val reasons: Set<String>,
    val files: Map<String, FileView>,
) {
    data class FileView(
        val level: String,
        val codes: List<String>,
    ) {
        override fun toString() = "$level $codes"
    }

    companion object {
        fun of(grade: TrustGrade) =
            TrustParityView(
                level = grade.level.name,
                reasons = grade.reasons.map { "${it.code.name} ${it.detail}" }.toSet(),
                files = grade.files.associate { it.entry to FileView(it.level.name, it.reasons.map { code -> code.name }) },
            )

        /** The prototype grades a component that failed, or had no version, from nothing; the service ends it FAILED. */
        fun gradable(component: JsonNode) = !component.hasNonNull("error") && component.hasNonNull("version")

        fun ofPrototype(component: JsonNode) =
            TrustParityView(
                level = component.path("confidence").asText(),
                reasons = component
                    .path(
                        "reasons",
                    ).map { "${it.path("code").asText().spelled()} ${it.path("detail").asText().spelled()}" }
                    .toSet(),
                files =
                    component
                        .path("artifacts")
                        .filter { it.path("level").asText().isNotEmpty() }
                        .associate { file ->
                            file.path("entry").asText() to
                                FileView(file.path("level").asText(), file.path("flags").map { it.asText().spelled() })
                        },
            )

        private fun String.spelled() = replace("LICENCE", "LICENSE").replace("licence", "license")
    }
}
