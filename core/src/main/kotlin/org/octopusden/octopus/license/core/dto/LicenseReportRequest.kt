package org.octopusden.octopus.license.core.dto

/** A component version to generate the license report for. The service works out its files itself. */
data class LicenseReportRequest(
    val component: String,
    val version: String,
)
