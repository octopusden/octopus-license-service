package org.octopusden.octopus.license.service.service

import org.octopusden.octopus.license.core.dto.LicenseReportSummary

/**
 * Generates the license report of one component version, synchronously; running it in the
 * background and keeping the result is the caller's job.
 */
interface LicenseReportGenerator {
    fun generate(
        component: String,
        version: String,
    ): GeneratedReport
}

/** [files] is the zip of license files, ready to download. */
class GeneratedReport(
    val summary: LicenseReportSummary,
    val files: ByteArray,
)
