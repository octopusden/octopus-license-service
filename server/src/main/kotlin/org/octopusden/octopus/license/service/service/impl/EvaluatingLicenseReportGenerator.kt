package org.octopusden.octopus.license.service.service.impl

import io.micrometer.core.instrument.MeterRegistry
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.service.configuration.LicenseServiceProperties
import org.octopusden.octopus.license.service.service.GeneratedReport
import org.octopusden.octopus.license.service.service.LicenseReportGenerator
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.octopus.license.trust.TrustGrader
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Duration
import java.util.zip.ZipOutputStream

/**
 * Scans the target, evaluates the inventory with the policy the report started with, grades the
 * assessment with the target's registry labels, and maps both into the summary. The zip stays empty
 * until the license files are written.
 *
 * A FULL report waits for Xray up to `license-service.xray.scan-timeout`; a CHECK, a regular
 * build's optimistic check, does not wait at all (evaluation design, D10).
 *
 * A scan failure is counted and passed on, so the report ends FAILED; Artifactory and Xray are
 * per-request dependencies, never a health indicator (evaluation design, D9).
 */
@Component
class EvaluatingLicenseReportGenerator(
    private val scanner: LicenseInventoryScanner,
    private val evaluator: LicenseEvaluator,
    private val grader: TrustGrader,
    private val properties: LicenseServiceProperties,
    private val clock: Clock,
    private val buildProperties: BuildProperties,
    private val meterRegistry: MeterRegistry,
) : LicenseReportGenerator {
    override fun generate(
        target: LicenseTarget,
        mode: LicenseReportMode,
        policy: LicensePolicy,
    ): GeneratedReport {
        val xrayWait =
            when (mode) {
                LicenseReportMode.FULL -> properties.xray.scanTimeout
                LicenseReportMode.CHECK -> Duration.ZERO
            }
        val inventory =
            runCatching { scanner.scan(target, xrayWait) }
                .onSuccess { countScan("scanned") }
                .onFailure { countScan(if (it is InventorySourceUnavailableException) "unavailable" else "failed") }
                .getOrThrow()
        val inputs =
            GenerationInputs(
                licenseRegistryCommit = policy.commit,
                generatedAt = clock.instant(),
                serviceVersion = requireNotNull(buildProperties.version) { "build-info carries no version" },
            )
        val assessment = evaluator.evaluate(inventory, policy)
        return GeneratedReport(SummaryMapper.map(assessment, grader.grade(assessment, target.labels), inputs), emptyZip())
    }

    private fun countScan(outcome: String) = meterRegistry.counter(SCANS_METRIC, "outcome", outcome).increment()

    private fun emptyZip() = ByteArrayOutputStream().also { ZipOutputStream(it).close() }.toByteArray()

    private companion object {
        const val SCANS_METRIC = "license.inventory.scans"
    }
}
