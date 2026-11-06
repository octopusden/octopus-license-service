package org.octopusden.octopus.license.service.service

import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.target.LicenseTarget

/**
 * Generates the license report of one [LicenseTarget], synchronously; running it in the
 * background and keeping the result is the caller's job. Never sees an excluded component.
 */
interface LicenseReportGenerator {
    fun generate(target: LicenseTarget): GeneratedReport
}

/** [files] is the zip of license files, ready to download. */
class GeneratedReport(
    val summary: LicenseReportSummary,
    val files: ByteArray,
)
