package org.octopusden.octopus.license.service.service.impl

import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment

/**
 * Narrows an assessment to what a caller decides a build outcome on: third-party libraries only,
 * counted per verdict and listed when not allowed (evaluation design, D9). Trust stays null until
 * grading exists.
 */
internal object SummaryMapper {
    fun map(
        assessment: LicenseAssessment,
        inputs: GenerationInputs,
    ): LicenseReportSummary {
        val thirdParty = assessment.libraries.mapNotNull { library -> wireVerdict(library.verdict)?.let { library to it } }
        return LicenseReportSummary(
            inputs = inputs,
            trust = null,
            libraryCounts = Verdict.entries.associateWith { verdict -> thirdParty.count { it.second == verdict } },
            violations =
                thirdParty
                    .filter { (_, verdict) -> verdict != Verdict.ALLOWED }
                    .map { (library, verdict) ->
                        Violation(library.library.id.toString(), verdict, library.licenses, library.library.foundIn.sorted())
                    },
        )
    }

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
