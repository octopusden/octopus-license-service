package org.octopusden.octopus.license.runner

import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation

/** Which violation verdicts fail the build; all off unless the step asks (license files design, D11). */
data class FailOn(
    val notAllowed: Boolean,
    val unknown: Boolean,
    val unidentified: Boolean,
) {
    val any get() = notAllowed || unknown || unidentified

    fun covers(verdict: Verdict) =
        when (verdict) {
            Verdict.NOT_ALLOWED -> notAllowed
            Verdict.UNKNOWN -> unknown
            Verdict.UNIDENTIFIED -> unidentified
            Verdict.ALLOWED -> false
        }
}

/**
 * Whether the build passes, decided from the verdicts only: trust and missing license texts never
 * fail it. With a flag on, no answer fails a FULL report, so nothing ships unchecked, and passes a
 * CHECK, so Xray being down never breaks a regular build; a CHECK's preliminary violation, found
 * only in files Xray has not scanned yet, never fails (D11).
 */
sealed interface BuildOutcome {
    data object Pass : BuildOutcome

    data class Fail(
        val reasons: List<String>,
    ) : BuildOutcome

    companion object {
        fun of(
            failOn: FailOn,
            mode: LicenseReportMode,
            outcome: RunOutcome,
        ): BuildOutcome {
            if (!failOn.any) return Pass
            return when (outcome) {
                is RunOutcome.Unavailable -> noAnswer(mode, "the license service gave no report: ${outcome.reason}")
                is RunOutcome.Finished ->
                    when (outcome.report.status) {
                        LicenseReportStatus.EXCLUDED -> Pass
                        LicenseReportStatus.FAILED -> noAnswer(mode, "the license report FAILED: ${outcome.report.error}")
                        LicenseReportStatus.RUNNING -> error("A finished outcome cannot be RUNNING")
                        LicenseReportStatus.DONE ->
                            outcome.report.summary?.let { summary ->
                                summary.violations
                                    .filter { failOn.covers(it.verdict) && !(mode == LicenseReportMode.CHECK && it.preliminary) }
                                    .map(::reason)
                                    .let { reasons -> if (reasons.isEmpty()) Pass else Fail(reasons) }
                            } ?: noAnswer(mode, "the license report is DONE without a summary")
                    }
            }
        }

        fun ofRunnerError(
            failOn: FailOn,
            mode: LicenseReportMode,
            message: String,
        ): BuildOutcome = if (failOn.any) noAnswer(mode, "the license runner failed: $message") else Pass

        private fun reason(violation: Violation): String {
            val licenses = violation.licenses.joinToString(", ")
            val files = violation.files.joinToString(", ")
            return "${violation.library} is ${violation.verdict} ($licenses) in $files"
        }

        private fun noAnswer(
            mode: LicenseReportMode,
            reason: String,
        ) = when (mode) {
            LicenseReportMode.FULL -> Fail(listOf("No verdicts to check, $reason"))
            LicenseReportMode.CHECK -> Pass
        }
    }
}
