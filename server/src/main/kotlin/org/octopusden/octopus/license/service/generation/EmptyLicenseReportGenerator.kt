package org.octopusden.octopus.license.service.generation

import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.zip.ZipOutputStream

/** Answers every component version with no libraries and an empty zip, until real sources are read. */
@Component
class EmptyLicenseReportGenerator(
    private val clock: Clock,
    private val buildProperties: BuildProperties,
) : LicenseReportGenerator {
    override fun generate(
        component: String,
        version: String,
    ) = GeneratedReport(
        LicenseReportSummary(
            inputs = GenerationInputs(
                licenseRegistryCommit = null,
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
