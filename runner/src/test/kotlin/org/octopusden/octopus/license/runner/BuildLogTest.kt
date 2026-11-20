package org.octopusden.octopus.license.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.core.dto.FileTrust
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseDisagreement
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.dto.MissingLicenseText
import org.octopusden.octopus.license.core.dto.MissingTextReason
import org.octopusden.octopus.license.core.dto.Trust
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.TrustReason
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import java.time.Instant

class BuildLogTest {
    private val notIndexed =
        TrustReason(
            "BUILD_LIST_ONLY",
            TrustLevel.LOW,
            "Only the build's list used",
            "not indexed in Xray: app.jar",
            "what it means",
            "Add the repository to Xray's indexed resources, or reindex the file.",
        )

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
                        "PARTIAL_ARTIFACTS",
                        TrustLevel.LOW,
                        "Only some files checked",
                        "1 of 2 registered artifacts checked",
                    ),
                ),
                listOf(
                    FileTrust("app.jar", TrustLevel.HIGH, emptyList()),
                    FileTrust("app.war", TrustLevel.NONE, listOf("ARTIFACT_NOT_FOUND")),
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
                listOf(reason("NO_BUILD_RECORD", TrustLevel.MEDIUM, "No build record", "no build for: app.jar")),
                listOf(FileTrust("app.jar", TrustLevel.MEDIUM, listOf("NO_BUILD_RECORD"))),
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

    private fun checked(trust: Trust) =
        BuildLog.lines(
            COMPONENT,
            VERSION,
            RunOutcome.Finished(
                LicenseReport(REPORT_ID, COMPONENT, VERSION, LicenseReportStatus.DONE, LicenseReportMode.CHECK, summary(trust = trust)),
                byteArrayOf(),
            ),
        )

    @Test
    @DisplayName("a check whose only doubt is a file Xray has not indexed yet warns about nothing, and says the full report waits for it")
    fun checkNotIndexedYet() {
        val trust =
            Trust(TrustLevel.LOW, listOf(notIndexed), listOf(FileTrust("app.jar", TrustLevel.LOW, listOf("BUILD_LIST_ONLY"))))

        assertEquals(
            listOf(
                LogLine("Trust: LOW"),
                LogLine(
                    "  LOW Only the build's list used: not indexed in Xray: app.jar. " +
                        "Xray has not indexed it yet; the full report at RC or RELEASE waits for it.",
                ),
                LogLine("  app.jar: LOW (BUILD_LIST_ONLY)"),
            ),
            checked(trust).subList(2, 5),
        )
    }

    @Test
    @DisplayName("a check with another LOW reason still warns, except on the file Xray has not indexed yet")
    fun checkWithAnotherReason() {
        val partial =
            reason("PARTIAL_ARTIFACTS", TrustLevel.LOW, "Only some files checked", "1 of 2 registered artifacts checked")
        val trust =
            Trust(
                TrustLevel.LOW,
                listOf(partial, notIndexed),
                listOf(
                    FileTrust("app.jar", TrustLevel.LOW, listOf("BUILD_LIST_ONLY")),
                    FileTrust("app.war", TrustLevel.NONE, listOf("ARTIFACT_NOT_FOUND")),
                ),
            )

        assertEquals(
            listOf(true, true, false, false, true),
            checked(trust).subList(2, 7).map { it.warning },
        )
    }

    @Test
    @DisplayName("a full report keeps warning on a file Xray has not indexed, with the registry's fix: it waited for Xray")
    fun fullNotIndexed() {
        val trust =
            Trust(TrustLevel.LOW, listOf(notIndexed), listOf(FileTrust("app.jar", TrustLevel.LOW, listOf("BUILD_LIST_ONLY"))))

        val lines = BuildLog.lines(COMPONENT, VERSION, finished(summary(trust = trust)))

        assertEquals(
            LogLine(
                "  LOW Only the build's list used: not indexed in Xray: app.jar. " +
                    "Fix: Add the repository to Xray's indexed resources, or reindex the file.",
                warning = true,
            ),
            lines[3],
        )
    }

    private fun reason(
        code: String,
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

    @Test
    @DisplayName("a preliminary violation says Xray has not scanned its files yet")
    fun preliminaryViolation() {
        val summary =
            summary(
                violations = listOf(
                    Violation("pkg:npm/left-pad@1.3.0", Verdict.UNKNOWN, emptyList(), listOf("app.jar"), preliminary = true),
                ),
            )

        assertEquals(
            LogLine("pkg:npm/left-pad@1.3.0 is UNKNOWN () in app.jar (preliminary: Xray has not scanned these files yet)", warning = true),
            BuildLog.lines(COMPONENT, VERSION, finished(summary)).last(),
        )
    }

    @Test
    @DisplayName("each missing license text is one warning with its reason and URL")
    fun missingTexts() {
        val summary =
            summary(
                missingTexts =
                    listOf(
                        MissingLicenseText("GWT", MissingTextReason.DOWNLOAD_FAILED, "http://www.gwtproject.org/terms.html"),
                        MissingLicenseText("SSPL-1.0", MissingTextReason.NO_REGISTRY_ENTRY),
                    ),
            )

        assertEquals(
            listOf(
                LogLine("License text of GWT missing: DOWNLOAD_FAILED (http://www.gwtproject.org/terms.html)", warning = true),
                LogLine("License text of SSPL-1.0 missing: NO_REGISTRY_ENTRY", warning = true),
            ),
            BuildLog.lines(COMPONENT, VERSION, finished(summary)).takeLast(2),
        )
    }

    @Test
    @DisplayName("a library whose sources disagree is one warning with each source's names and the verdict used")
    fun disagreement() {
        val summary =
            summary().copy(
                licenseDisagreements =
                    listOf(
                        LicenseDisagreement(
                            "pkg:npm/left-pad@1.3.0",
                            Verdict.ALLOWED,
                            mapOf("BINARY_SCAN" to listOf("GPL-3.0"), "SBOM" to listOf("MIT")),
                            listOf("app.jar"),
                        ),
                    ),
            )

        assertEquals(
            LogLine(
                "pkg:npm/left-pad@1.3.0 is ALLOWED, but its sources disagree: BINARY_SCAN (GPL-3.0), SBOM (MIT) in app.jar",
                warning = true,
            ),
            BuildLog.lines(COMPONENT, VERSION, finished(summary)).last(),
        )
    }

    private fun finished(summary: LicenseReportSummary) =
        RunOutcome.Finished(LicenseReport(REPORT_ID, COMPONENT, VERSION, LicenseReportStatus.DONE, summary = summary), byteArrayOf())

    private fun summary(
        trust: Trust = Trust(TrustLevel.HIGH, emptyList(), emptyList()),
        libraryCounts: Map<Verdict, Int> = emptyMap(),
        violations: List<Violation> = emptyList(),
        missingTexts: List<MissingLicenseText> = emptyList(),
    ) = LicenseReportSummary(
        GenerationInputs(licenseRegistryCommit = null, generatedAt = Instant.parse("2026-11-04T10:00:00Z"), serviceVersion = "1.0.0"),
        trust,
        libraryCounts,
        violations,
        missingTexts,
    )

    private companion object {
        const val REPORT_ID = "report-1"
        const val COMPONENT = "some-component"
        const val VERSION = "1.2.3"
    }
}
