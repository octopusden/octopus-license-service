package org.octopusden.octopus.license.service.report.impl

import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.service.report.GeneratedReport
import org.octopusden.octopus.license.service.report.LicenseReportGenerator
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.zip.ZipOutputStream

/**
 * Answers every component version with no libraries and an empty zip, until real sources are read;
 * the summary still records which license registry commit the report would have been judged with.
 */
@Component
class EmptyLicenseReportGenerator(
    private val clock: Clock,
    private val buildProperties: BuildProperties,
) : LicenseReportGenerator {
    override fun generate(
        target: LicenseTarget,
        policy: LicensePolicy,
    ) = GeneratedReport(
        LicenseReportSummary(
            inputs = GenerationInputs(
                licenseRegistryCommit = policy.commit,
                generatedAt = clock.instant(),
                serviceVersion = requireNotNull(buildProperties.version) { "build-info carries no version" },
            ),
            trust = null,
            libraryCounts = emptyMap(),
            violations = emptyList(),
        ),
        emptyZip(),
    )

    private fun emptyZip() = ByteArrayOutputStream().also { ZipOutputStream(it).close() }.toByteArray()
}
