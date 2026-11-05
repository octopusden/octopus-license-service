package org.octopusden.octopus.license.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.dto.Trust
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.TrustReason
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import java.time.Instant

class BuildLogTest {
    @Test
    @DisplayName("a clean DONE report gives no warning")
    fun cleanReportHasNoWarning() {
        val lines = BuildLog.lines(COMPONENT, VERSION, finished(summary()))

        assertEquals(
            listOf(
                LogLine("License report for some-component:1.2.3: DONE"),
                LogLine("Generated 2026-11-04T10:00:00Z by license service 1.0.0, license registry commit unknown"),
                LogLine("Trust: not computed"),
                LogLine("Libraries: none"),
            ),
            lines,
        )
    }

    @Test
    @DisplayName("each violation is a warning naming the library, its licenses and its files")
    fun violationIsWarning() {
        val summary = summary(
            libraryCounts = mapOf(Verdict.ALLOWED to 12, Verdict.NOT_ALLOWED to 1),
            violations = listOf(
                Violation("org.example:lib-a:2.0", Verdict.NOT_ALLOWED, listOf("GPL-3.0-only"), listOf("app.jar", "app.war")),
            ),
        )

        val lines = BuildLog.lines(COMPONENT, VERSION, finished(summary))

        assertEquals(LogLine("Libraries: ALLOWED 12, NOT_ALLOWED 1"), lines[3])
        assertEquals(LogLine("org.example:lib-a:2.0 is NOT_ALLOWED (GPL-3.0-only) in app.jar, app.war", warning = true), lines[4])
    }

    @Test
    @DisplayName("Build outcome: trust LOW is a warning with its reasons and fixes")
    fun lowTrustIsWarning() {
        val summary = summary(trust = Trust(TrustLevel.LOW, listOf(TrustReason("app.jar has no build-info", "publish build-info"))))

        val lines = BuildLog.lines(COMPONENT, VERSION, finished(summary))

        assertEquals(LogLine("Trust: LOW", warning = true), lines[2])
        assertEquals(LogLine("  app.jar has no build-info; fix: publish build-info", warning = true), lines[3])
    }

    @Test
    @DisplayName("Build outcome: an excluded component is skipped with the reason, not a warning")
    fun excludedIsSkipped() {
        val report = LicenseReport(REPORT_ID, COMPONENT, VERSION, LicenseReportStatus.EXCLUDED, exclusionReason = "documentation component")

        val lines = BuildLog.lines(COMPONENT, VERSION, RunOutcome.Finished(report, null))

        assertEquals(listOf(LogLine("License report for some-component:1.2.3 skipped: documentation component")), lines)
    }

    @Test
    @DisplayName("Build outcome: a FAILED report is a warning with the error")
    fun failedIsWarning() {
        val report = LicenseReport(REPORT_ID, COMPONENT, VERSION, LicenseReportStatus.FAILED, error = "Xray is unreachable")

        val lines = BuildLog.lines(COMPONENT, VERSION, RunOutcome.Finished(report, null))

        assertEquals(listOf(LogLine("License report for some-component:1.2.3 FAILED: Xray is unreachable", warning = true)), lines)
    }

    @Test
    @DisplayName("Build outcome: an unavailable service is a warning with the reason")
    fun unavailableIsWarning() {
        val lines = BuildLog.lines(COMPONENT, VERSION, RunOutcome.Unavailable("Connection refused"))

        assertEquals(
            listOf(LogLine("License service unavailable for some-component:1.2.3: Connection refused", warning = true)),
            lines,
        )
    }

    @Test
    @DisplayName("a warning line is printed with a WARNING prefix, any other line as it is")
    fun renderPrefixesWarnings() {
        assertEquals("WARNING: Trust: LOW", BuildLog.render(LogLine("Trust: LOW", warning = true)))
        assertEquals("Libraries: none", BuildLog.render(LogLine("Libraries: none")))
    }

    private fun finished(summary: LicenseReportSummary) =
        RunOutcome.Finished(LicenseReport(REPORT_ID, COMPONENT, VERSION, LicenseReportStatus.DONE, summary = summary), byteArrayOf())

    private fun summary(
        trust: Trust? = null,
        libraryCounts: Map<Verdict, Int> = emptyMap(),
        violations: List<Violation> = emptyList(),
    ) = LicenseReportSummary(
        GenerationInputs(licenseRegistryCommit = null, generatedAt = Instant.parse("2026-11-04T10:00:00Z"), serviceVersion = "1.0.0"),
        trust,
        libraryCounts,
        violations,
    )

    private companion object {
        const val REPORT_ID = "report-1"
        const val COMPONENT = "some-component"
        const val VERSION = "1.2.3"
    }
}
