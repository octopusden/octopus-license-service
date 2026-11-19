package org.octopusden.octopus.license.runner

import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.dto.TrustLevel

/**
 * What the runner prints for an outcome. A warning marks what the Build outcome rules ask a build
 * to warn about; nothing here fails a build.
 */
object BuildLog {
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
                        report.summary?.let { listOf(doneLine(coordinates, report.mode)) + summaryLines(it) }
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

    private fun summaryLines(summary: LicenseReportSummary): List<LogLine> {
        val inputs = summary.inputs
        val generated = LogLine(
            "Generated ${inputs.generatedAt} by license service ${inputs.serviceVersion}, " +
                "license registry commit ${inputs.licenseRegistryCommit ?: "unknown"}",
        )
        val trust = summary.trust
        val warning = trust.level in warnedTrustLevels
        val trustLines =
            listOf(LogLine("Trust: ${trust.level}", warning)) +
                trust.reasons.map { LogLine("  ${it.level} ${it.title}: ${it.detail}. Fix: ${it.fix}", warning) } +
                trust.files
                    .filter { it.level != TrustLevel.HIGH }
                    .map { LogLine("  ${it.file}: ${it.level} (${it.reasons.joinToString(", ")})", warning) }
        val libraries = summary.libraryCounts.entries
            .sortedBy { it.key }
            .joinToString(", ") { "${it.key} ${it.value}" }
            .ifEmpty { "none" }
        val violations = summary.violations.map {
            val note = if (it.preliminary) " (preliminary: Xray has not scanned these files yet)" else ""
            LogLine(
                "${it.library} is ${it.verdict} (${it.licenses.joinToString(", ")}) in ${it.files.joinToString(", ")}$note",
                warning = true,
            )
        }
        val missingTexts = summary.missingLicenseTexts.map {
            LogLine("License text of ${it.license} missing: ${it.reason}" + (it.url?.let { url -> " ($url)" } ?: ""), warning = true)
        }
        return listOf(generated) + trustLines + LogLine("Libraries: $libraries") + violations + missingTexts
    }
}

data class LogLine(
    val text: String,
    val warning: Boolean = false,
)
