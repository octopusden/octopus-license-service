package org.octopusden.octopus.license.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.core.dto.FileTrust
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.dto.Trust
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.TrustReason
import org.octopusden.octopus.license.core.dto.TrustReasonCode
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
                LogLine("Trust: HIGH"),
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
    @DisplayName("Build outcome: trust LOW is a warning: each reason with its detail and fix, then each file below HIGH")
    fun lowTrustIsWarning() {
        val trust =
            Trust(
                TrustLevel.LOW,
                listOf(
                    reason(
                        TrustReasonCode.PARTIAL_ARTIFACTS,
                        TrustLevel.LOW,
                        "Only some files checked",
                        "1 of 2 registered artifacts checked",
                    ),
                ),
                listOf(
                    FileTrust("app.jar", TrustLevel.HIGH, emptyList()),
                    FileTrust("app.war", TrustLevel.NONE, listOf(TrustReasonCode.ARTIFACT_NOT_FOUND)),
                ),
            )

        val lines = BuildLog.lines(COMPONENT, VERSION, finished(summary(trust = trust)))

        assertEquals(
            listOf(
                LogLine("Trust: LOW", warning = true),
                LogLine("  LOW Only some files checked: 1 of 2 registered artifacts checked. Fix: fix it.", warning = true),
                LogLine("  app.war: NONE (ARTIFACT_NOT_FOUND)", warning = true),
                LogLine("Libraries: none"),
            ),
            lines.subList(2, 6),
        )
    }

    @Test
    @DisplayName("trust MEDIUM is shown, but not as a warning")
    fun mediumTrustIsNoWarning() {
        val trust =
            Trust(
                TrustLevel.MEDIUM,
                listOf(reason(TrustReasonCode.NO_BUILD_RECORD, TrustLevel.MEDIUM, "No build record", "no build for: app.jar")),
                listOf(FileTrust("app.jar", TrustLevel.MEDIUM, listOf(TrustReasonCode.NO_BUILD_RECORD))),
            )

        val lines = BuildLog.lines(COMPONENT, VERSION, finished(summary(trust = trust)))

        assertEquals(
            listOf(
                LogLine("Trust: MEDIUM"),
                LogLine("  MEDIUM No build record: no build for: app.jar. Fix: fix it."),
                LogLine("  app.jar: MEDIUM (NO_BUILD_RECORD)"),
            ),
            lines.subList(2, 5),
        )
    }

    private fun reason(
        code: TrustReasonCode,
        level: TrustLevel,
        title: String,
        detail: String,
    ) = TrustReason(code, level, title, detail, "what it means", "fix it.")

    @Test
    @DisplayName("a quick check says it is preliminary: it judged only what Xray knew at that moment")
    fun checkIsMarkedPreliminary() {
        val report = LicenseReport(REPORT_ID, COMPONENT, VERSION, LicenseReportStatus.DONE, LicenseReportMode.CHECK, summary())

        val lines = BuildLog.lines(COMPONENT, VERSION, RunOutcome.Finished(report, null))

        assertEquals(
            LogLine("License check for some-component:1.2.3: DONE (quick check on what Xray knows now; preliminary)"),
            lines.first(),
        )
    }

    @Test
    @DisplayName("a DONE report without a summary is one warning line, never an exception")
    fun doneWithoutSummaryIsWarning() {
        val report = LicenseReport(REPORT_ID, COMPONENT, VERSION, LicenseReportStatus.DONE)

        val lines = BuildLog.lines(COMPONENT, VERSION, RunOutcome.Finished(report, byteArrayOf()))

        assertEquals(
            listOf(LogLine("License report for some-component:1.2.3 is DONE, but the service sent no summary", warning = true)),
            lines,
        )
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
        trust: Trust = Trust(TrustLevel.HIGH, emptyList(), emptyList()),
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
