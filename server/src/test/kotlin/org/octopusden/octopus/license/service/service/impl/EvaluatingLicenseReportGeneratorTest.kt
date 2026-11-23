package org.octopusden.octopus.license.service.service.impl

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.inventory.model.SelectionReason
import org.octopusden.octopus.license.service.configuration.LicenseServiceProperties
import org.octopusden.octopus.license.service.configuration.XrayProperties
import org.octopusden.octopus.license.service.state.policy.LicensePolicyRefresherTest.Companion.samplePolicy
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.octopus.license.trust.TrustGrader
import org.springframework.boot.info.BuildProperties
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Properties

class EvaluatingLicenseReportGeneratorTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private val meterRegistry = SimpleMeterRegistry()
    private val target =
        LicenseTarget(
            componentVersion = ComponentVersion("sample-portal", "2.4.0"),
            artifactVersions = listOf("2.4.0"),
            buildSystem = "GRADLE",
            labels = emptySet(),
            explicit = true,
            distribution = DistributionSpec(declared = emptyList(), patterns = emptyList()),
        )

    private val waits = mutableListOf<Duration>()

    private fun generator(scan: (LicenseTarget) -> LicenseInventory) =
        EvaluatingLicenseReportGenerator(
            object : LicenseInventoryScanner {
                override fun scan(
                    target: LicenseTarget,
                    xrayWait: Duration,
                ): LicenseInventory {
                    waits += xrayWait
                    return scan(target)
                }
            },
            LicenseEvaluator(),
            TrustGrader(),
            LicenseServiceProperties(xray = XrayProperties(scanTimeout = FULL_WAIT)),
            Clock.fixed(now, ZoneOffset.UTC),
            BuildProperties(Properties().apply { setProperty("version", "1.0.0") }),
            meterRegistry,
        )

    private fun scans(outcome: String) =
        meterRegistry
            .find("license.inventory.scans")
            .tag("outcome", outcome)
            .counter()
            ?.count() ?: 0.0

    private fun inventory(vararg libraries: FoundLibrary) =
        LicenseInventory(target.componentVersion, emptyList(), emptyList(), libraries.toList(), emptyList())

    private fun library(
        id: String,
        licenses: Set<String>,
    ) = FoundLibrary(PackageId.parse(id), licenses, setOf(Evidence.BINARY_SCAN), setOf(ZIP), ours = false)

    @Test
    @DisplayName("the summary comes from the scan of the target, evaluated with the given policy, and the scan is counted")
    fun scanned() {
        val scanned = mutableListOf<LicenseTarget>()
        val generated =
            generator {
                scanned += it
                inventory(library("pkg:npm/left-pad@1.3.0", emptySet()), library("pkg:npm/react@18.2.0", setOf("MIT")))
            }.generate(target, LicenseReportMode.FULL, samplePolicy("a1b2c3d"))

        assertEquals(listOf(target), scanned)
        assertEquals(GenerationInputs("a1b2c3d", now, "1.0.0"), generated.summary.inputs)
        assertEquals(
            mapOf(Verdict.ALLOWED to 1, Verdict.NOT_ALLOWED to 0, Verdict.UNKNOWN to 1, Verdict.UNIDENTIFIED to 0),
            generated.summary.libraryCounts,
        )
        assertEquals(listOf(Violation("pkg:npm/left-pad@1.3.0", Verdict.UNKNOWN, emptyList(), listOf(ZIP))), generated.summary.violations)
        assertEquals(1.0, scans("scanned"))
    }

    @Test
    @DisplayName("Artifactory or Xray unreachable: the failure goes to the caller, counted unavailable")
    fun unavailable() {
        val failure = InventorySourceUnavailableException("Xray", "Xray answered 503")

        val thrown =
            assertThrows<InventorySourceUnavailableException> {
                generator { throw failure }.generate(target, LicenseReportMode.FULL, samplePolicy("a1b2c3d"))
            }

        assertEquals(failure, thrown)
        assertEquals(1.0, scans("unavailable"))
        assertEquals(0.0, scans("failed"))
    }

    @Test
    @DisplayName("any other scan failure goes to the caller, counted failed")
    fun failed() {
        assertThrows<IllegalStateException> {
            generator { error("unexpected answer") }.generate(target, LicenseReportMode.FULL, samplePolicy("a1b2c3d"))
        }

        assertEquals(1.0, scans("failed"))
        assertEquals(0.0, scans("unavailable"))
    }

    @Test
    @DisplayName("a FULL report waits for Xray as long as configured; a CHECK does not wait at all (decision of 2026-11-12)")
    fun xrayWaitByMode() {
        val generator = generator { inventory() }

        generator.generate(target, LicenseReportMode.FULL, samplePolicy("a1b2c3d"))
        generator.generate(target, LicenseReportMode.CHECK, samplePolicy("a1b2c3d"))

        assertEquals(listOf(FULL_WAIT, Duration.ZERO), waits)
    }

    @Test
    @DisplayName("the assessment is graded with the target's registry labels, and the grade is the summary's trust")
    fun graded() {
        val zip =
            ReleasedFile(
                ZIP,
                FileKind.MAVEN,
                setOf(SelectionReason.DECLARED),
                status = FileStatus.READ,
                evidence = setOf(Evidence.BINARY_SCAN, Evidence.BUILD_RECORD),
            )
        val scanned =
            LicenseInventory(
                target.componentVersion,
                emptyList(),
                listOf(zip),
                listOf(library("pkg:maven/org.example/sample-lib@1.5", setOf("MIT"))),
                emptyList(),
            )

        val unlabelled = generator { scanned }.generate(target, LicenseReportMode.FULL, samplePolicy("a1b2c3d")).summary.trust
        val javascript =
            generator { scanned }
                .generate(
                    target.copy(labels = setOf("javascript")),
                    LicenseReportMode.FULL,
                    samplePolicy("a1b2c3d"),
                ).summary.trust

        assertEquals(TrustLevel.HIGH, unlabelled.level)
        assertEquals(listOf(ZIP), unlabelled.files.map { it.file })
        assertEquals(TrustLevel.LOW, javascript.level)
        assertEquals(listOf("ECOSYSTEM_NOT_DETECTED"), javascript.reasons.map { it.code })
    }

    private companion object {
        const val ZIP = "org.example.portal:sample-portal:zip"
        val FULL_WAIT: Duration = Duration.ofMinutes(5)
    }
}
