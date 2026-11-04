package org.octopusden.octopus.license.runner

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
                        listOf(LogLine("License report for $coordinates: DONE")) + summaryLines(requireNotNull(report.summary))
                    LicenseReportStatus.EXCLUDED -> listOf(LogLine("License report for $coordinates skipped: ${report.exclusionReason}"))
                    LicenseReportStatus.FAILED -> listOf(LogLine("License report for $coordinates FAILED: ${report.error}", warning = true))
                    LicenseReportStatus.RUNNING -> error("A finished outcome cannot be RUNNING")
                }
            }
        }
    }

    fun render(line: LogLine) = if (line.warning) "WARNING: ${line.text}" else line.text

    private fun summaryLines(summary: LicenseReportSummary): List<LogLine> {
        val inputs = summary.inputs
        val generated = LogLine(
            "Generated ${inputs.generatedAt} by license service ${inputs.serviceVersion}, " +
                "license registry commit ${inputs.licenseRegistryCommit ?: "unknown"}",
        )
        val trust = summary.trust?.let { trust ->
            val warning = trust.level in warnedTrustLevels
            listOf(LogLine("Trust: ${trust.level}", warning)) +
                trust.reasons.map { LogLine("  ${it.reason}; fix: ${it.fix}", warning) }
        } ?: listOf(LogLine("Trust: not computed"))
        val libraries = summary.libraryCounts.entries
            .sortedBy { it.key }
            .joinToString(", ") { "${it.key} ${it.value}" }
            .ifEmpty { "none" }
        val violations = summary.violations.map {
            LogLine("${it.library} is ${it.verdict} (${it.licenses.joinToString(", ")}) in ${it.files.joinToString(", ")}", warning = true)
        }
        return listOf(generated) + trust + LogLine("Libraries: $libraries") + violations
    }
}

data class LogLine(
    val text: String,
    val warning: Boolean = false,
)
