package org.octopusden.octopus.license.service.service

import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.target.model.LicenseTarget

/**
 * Generates the license report of one [LicenseTarget], synchronously; running it in the
 * background and keeping the result is the caller's job. Never sees an excluded component.
 *
 * [policy] is the one current when the report started, so a reload during a generation does not
 * change its answer (D7); the summary records its commit.
 */
interface LicenseReportGenerator {
    fun generate(
        target: LicenseTarget,
        policy: LicensePolicy,
    ): GeneratedReport
}

/** [files] is the zip of license files, ready to download. */
class GeneratedReport(
    val summary: LicenseReportSummary,
    val files: ByteArray,
)
