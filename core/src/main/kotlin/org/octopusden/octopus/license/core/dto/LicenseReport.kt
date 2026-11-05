package org.octopusden.octopus.license.core.dto

/**
 * One generation of the license report for a component version, as returned by every call.
 *
 * [summary] is set only when [status] is [LicenseReportStatus.DONE]; [exclusionReason] only for
 * [LicenseReportStatus.EXCLUDED]; [error] only for [LicenseReportStatus.FAILED].
 */
data class LicenseReport(
    val id: String,
    val component: String,
    val version: String,
    val status: LicenseReportStatus,
    val summary: LicenseReportSummary? = null,
    val exclusionReason: String? = null,
    val error: String? = null,
)
