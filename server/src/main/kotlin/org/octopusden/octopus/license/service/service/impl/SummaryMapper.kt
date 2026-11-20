package org.octopusden.octopus.license.service.service.impl

import org.octopusden.octopus.license.core.dto.FileTrust
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseDisagreement
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.dto.MissingLicenseText
import org.octopusden.octopus.license.core.dto.MissingTextReason
import org.octopusden.octopus.license.core.dto.Trust
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.TrustReason
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.files.model.MissingText
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.trust.model.TrustGrade
import org.octopusden.octopus.license.trust.model.TrustLevel as GradedLevel

/**
 * Narrows an assessment and its grade to what a caller decides a build outcome on: third-party
 * libraries only, counted per verdict and listed when not allowed (evaluation design, D9), and the
 * grade one to one, each reason with its code's texts (trust design, D8).
 *
 * A CHECK report's violation is preliminary when Xray has scanned none of its files, so a regular
 * build does not fail on what Xray has not seen yet (license files design, D11). A package whose
 * license lookup failed in a file Xray did scan is not marked yet (TD-003).
 */
internal object SummaryMapper {
    fun map(
        assessment: LicenseAssessment,
        grade: TrustGrade,
        inputs: GenerationInputs,
        mode: LicenseReportMode = LicenseReportMode.FULL,
        missingTexts: List<MissingText> = emptyList(),
    ): LicenseReportSummary {
        val scannedFiles =
            assessment.inventory.releasedFiles
                .filter { Evidence.BINARY_SCAN in it.evidence }
                .map { it.entry }
                .toSet()
        val thirdParty = assessment.libraries.mapNotNull { library -> wireVerdict(library.verdict)?.let { library to it } }
        return LicenseReportSummary(
            inputs = inputs,
            trust = trust(grade),
            libraryCounts = Verdict.entries.associateWith { verdict -> thirdParty.count { it.second == verdict } },
            violations =
                thirdParty
                    .filter { (_, verdict) -> verdict != Verdict.ALLOWED }
                    .map { (library, verdict) ->
                        Violation(
                            library.library.id.toString(),
                            verdict,
                            library.licenses,
                            library.library.foundIn.sorted(),
                            preliminary = mode == LicenseReportMode.CHECK && library.library.foundIn.none { it in scannedFiles },
                        )
                    },
            licenseDisagreements =
                thirdParty.mapNotNull { (library, verdict) ->
                    library.disagreement?.let { disagreement ->
                        LicenseDisagreement(
                            library.library.id.toString(),
                            verdict,
                            disagreement.bySource.mapKeys { it.key.name },
                            library.library.foundIn.sorted(),
                        )
                    }
                },
            missingLicenseTexts =
                missingTexts.map { MissingLicenseText(it.license, MissingTextReason.valueOf(it.reason.name), it.url) },
        )
    }

    private fun trust(grade: TrustGrade) =
        Trust(
            level = wireLevel(grade.level),
            reasons =
                grade.reasons.map {
                    TrustReason(it.code.name, wireLevel(it.code.level), it.code.title, it.detail, it.code.meaning, it.code.fix)
                },
            files = grade.files.map { FileTrust(it.entry, wireLevel(it.level), it.reasons.map { code -> code.name }) },
        )

    private fun wireLevel(level: GradedLevel) = TrustLevel.valueOf(level.name)

    /** Null for our own libraries: the wire speaks of third-party libraries only. */
    private fun wireVerdict(verdict: LibraryVerdict) =
        when (verdict) {
            LibraryVerdict.INTERNAL -> null
            LibraryVerdict.ALLOWED -> Verdict.ALLOWED
            LibraryVerdict.NOT_ALLOWED -> Verdict.NOT_ALLOWED
            LibraryVerdict.UNKNOWN -> Verdict.UNKNOWN
            LibraryVerdict.UNIDENTIFIED -> Verdict.UNIDENTIFIED
        }
}
