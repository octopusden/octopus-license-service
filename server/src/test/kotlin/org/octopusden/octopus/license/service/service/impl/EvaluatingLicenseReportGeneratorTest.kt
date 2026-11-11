package org.octopusden.octopus.license.service.service.impl

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.service.state.policy.LicensePolicyRefresherTest.Companion.samplePolicy
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.springframework.boot.info.BuildProperties
import java.time.Clock
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

    private fun generator(scan: (LicenseTarget) -> LicenseInventory) =
        EvaluatingLicenseReportGenerator(
            object : LicenseInventoryScanner {
                override fun scan(target: LicenseTarget) = scan(target)
            },
            LicenseEvaluator(),
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
            }.generate(target, samplePolicy("a1b2c3d"))

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
            assertThrows<InventorySourceUnavailableException> { generator { throw failure }.generate(target, samplePolicy("a1b2c3d")) }

        assertEquals(failure, thrown)
        assertEquals(1.0, scans("unavailable"))
        assertEquals(0.0, scans("failed"))
    }

    @Test
    @DisplayName("any other scan failure goes to the caller, counted failed")
    fun failed() {
        assertThrows<IllegalStateException> { generator { error("unexpected answer") }.generate(target, samplePolicy("a1b2c3d")) }

        assertEquals(1.0, scans("failed"))
        assertEquals(0.0, scans("unavailable"))
    }

    private companion object {
        const val ZIP = "org.example.portal:sample-portal:zip"
    }
}
