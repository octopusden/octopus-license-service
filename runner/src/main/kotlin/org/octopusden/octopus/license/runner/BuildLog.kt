package org.octopusden.octopus.license.runner

import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.dto.Trust
import org.octopusden.octopus.license.core.dto.TrustLevel

/**
 * What the runner prints for an outcome. A warning marks what the Build outcome rules ask a build
 * to warn about; nothing here fails a build.
 *
 * A check does not wait for Xray, so a file uploaded seconds before is usually not indexed yet and
 * graded `BUILD_LIST_ONLY`. The grade stays as the service gives it; a check only
 * does not warn about that reason and says the full report waits for it, so the warnings that
 * remain are worth reading.
 */
object BuildLog {
    private const val NOT_INDEXED_CODE = "BUILD_LIST_ONLY"
    private const val NOT_INDEXED_YET = "Xray has not indexed it yet; the full report at RC or RELEASE waits for it."
    private val warnedTrustLevels = setOf(TrustLevel.LOW, TrustLevel.NONE)

    fun lines(
        component: String,
        version: String,
        outcome: RunOutcome,
    ): List<LogLine> {
        val coordinates = "$component:$version"
        return when (outcome) {
            is RunOutcome.Unavailable -> listOf(LogLine("License service unavailable for $coordinates: ${outcome.reason}", warning = true))
            is RunOutcome.Finished -> {
                val report = outcome.report
                when (report.status) {
                    LicenseReportStatus.DONE ->
                        report.summary?.let { listOf(doneLine(coordinates, report.mode)) + summaryLines(it, report.mode) }
                            ?: listOf(LogLine("License report for $coordinates is DONE, but the service sent no summary", warning = true))
                    LicenseReportStatus.EXCLUDED -> listOf(LogLine("License report for $coordinates skipped: ${report.exclusionReason}"))
                    LicenseReportStatus.FAILED -> listOf(LogLine("License report for $coordinates FAILED: ${report.error}", warning = true))
                    LicenseReportStatus.RUNNING -> error("A finished outcome cannot be RUNNING")
                }
            }
        }
    }

    private fun doneLine(
        coordinates: String,
        mode: LicenseReportMode,
    ) = when (mode) {
        LicenseReportMode.FULL -> LogLine("License report for $coordinates: DONE")
        LicenseReportMode.CHECK -> LogLine("License check for $coordinates: DONE (quick check on what Xray knows now; preliminary)")
    }

    fun render(line: LogLine) = if (line.warning) "WARNING: ${line.text}" else line.text

    private fun summaryLines(
        summary: LicenseReportSummary,
        mode: LicenseReportMode,
    ): List<LogLine> {
        val inputs = summary.inputs
        val generated = LogLine(
            "Generated ${inputs.generatedAt} by license service ${inputs.serviceVersion}, " +
                "license registry commit ${inputs.licenseRegistryCommit ?: "unknown"}",
        )
        val trustLines = trustLines(summary.trust, mode)
        val libraries = summary.libraryCounts.entries
            .sortedBy { it.key }
            .joinToString(", ") { "${it.key} ${it.value}" }
            .ifEmpty { "none" }
        val violations = summary.violations.map {
            LogLine("${it.library} is ${it.verdict} (${it.licenses.joinToString(", ")}) in ${it.files.joinToString(", ")}", warning = true)
        }
        return listOf(generated) + trustLines + LogLine("Libraries: $libraries") + violations
    }

    private fun trustLines(
        trust: Trust,
        mode: LicenseReportMode,
    ): List<LogLine> {
        val pending = if (mode == LicenseReportMode.CHECK) setOf(NOT_INDEXED_CODE) else emptySet()
        val warning = trust.reasons.filter { it.code !in pending }.any { it.level in warnedTrustLevels }
        return listOf(LogLine("Trust: ${trust.level}", warning)) +
            trust.reasons.map {
                if (it.code in pending) {
                    LogLine("  ${it.level} ${it.title}: ${it.detail}. $NOT_INDEXED_YET")
                } else {
                    LogLine("  ${it.level} ${it.title}: ${it.detail}. Fix: ${it.fix}", warning)
                }
            } +
            trust.files
                .filter { it.level != TrustLevel.HIGH }
                .map {
                    LogLine(
                        "  ${it.file}: ${it.level} (${it.reasons.joinToString(", ")})",
                        warning && !pending.containsAll(it.reasons),
                    )
                }
    }
}

data class LogLine(
    val text: String,
    val warning: Boolean = false,
)
