package org.octopusden.octopus.license.service.service.impl

import io.micrometer.core.instrument.MeterRegistry
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.service.service.GeneratedReport
import org.octopusden.octopus.license.service.service.LicenseReportGenerator
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.zip.ZipOutputStream

/**
 * Scans the target, evaluates the inventory with the policy the report started with, and maps the
 * assessment into the summary. The zip stays empty until the license files are written.
 *
 * A scan failure is counted and passed on, so the report ends FAILED; Artifactory and Xray are
 * per-request dependencies, never a health indicator (evaluation design, D9).
 */
@Component
class EvaluatingLicenseReportGenerator(
    private val scanner: LicenseInventoryScanner,
    private val evaluator: LicenseEvaluator,
    private val clock: Clock,
    private val buildProperties: BuildProperties,
    private val meterRegistry: MeterRegistry,
) : LicenseReportGenerator {
    override fun generate(
        target: LicenseTarget,
        policy: LicensePolicy,
    ): GeneratedReport {
        val inventory =
            runCatching { scanner.scan(target) }
                .onSuccess { countScan("scanned") }
                .onFailure { countScan(if (it is InventorySourceUnavailableException) "unavailable" else "failed") }
                .getOrThrow()
        val inputs =
            GenerationInputs(
                licenseRegistryCommit = policy.commit,
                generatedAt = clock.instant(),
                serviceVersion = requireNotNull(buildProperties.version) { "build-info carries no version" },
            )
        return GeneratedReport(SummaryMapper.map(evaluator.evaluate(inventory, policy), inputs), emptyZip())
    }

    private fun countScan(outcome: String) = meterRegistry.counter(SCANS_METRIC, "outcome", outcome).increment()

    private fun emptyZip() = ByteArrayOutputStream().also { ZipOutputStream(it).close() }.toByteArray()

    private companion object {
        const val SCANS_METRIC = "license.inventory.scans"
    }
}
