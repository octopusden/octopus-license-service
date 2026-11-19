package org.octopusden.octopus.license.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.dto.MissingLicenseText
import org.octopusden.octopus.license.core.dto.MissingTextReason
import org.octopusden.octopus.license.core.dto.Trust
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import java.time.Instant

class BuildOutcomeTest {
    private val none = FailOn(notAllowed = false, unknown = false, unidentified = false)
    private val all = FailOn(notAllowed = true, unknown = true, unidentified = true)

    private val notAllowed = Violation("pkg:maven/org.gpl/gpl-lib@1.0", Verdict.NOT_ALLOWED, listOf("GPL-3.0"), listOf("app.jar"))
    private val unknown = Violation("pkg:maven/com.example/foo-core@1.0", Verdict.UNKNOWN, emptyList(), listOf("app.jar"))
    private val unidentified =
        Violation("generic://sha256:${"a".repeat(64)}/blob.dll", Verdict.UNIDENTIFIED, emptyList(), listOf("app.jar"))

    private val noAnswers =
        listOf(
            RunOutcome.Unavailable("connection refused"),
            RunOutcome.Unavailable("No license report within PT30M"),
            ended(LicenseReportStatus.FAILED),
            RunOutcome.Finished(LicenseReport("report-1", "some-component", "1.2.3", LicenseReportStatus.DONE), null),
        )

    private fun done(
        violations: List<Violation> = emptyList(),
        trust: TrustLevel = TrustLevel.HIGH,
        missing: List<MissingLicenseText> = emptyList(),
    ) = RunOutcome.Finished(
        LicenseReport(
            "report-1",
            "some-component",
            "1.2.3",
            LicenseReportStatus.DONE,
            summary =
                LicenseReportSummary(
                    GenerationInputs(null, Instant.parse("2026-11-19T10:00:00Z"), "1.0.0"),
                    Trust(trust, emptyList(), emptyList()),
                    emptyMap(),
                    violations,
                    missing,
                ),
        ),
        byteArrayOf(),
    )

    private fun ended(status: LicenseReportStatus) =
        RunOutcome.Finished(LicenseReport("report-1", "some-component", "1.2.3", status, error = "see the service log"), null)

    @ParameterizedTest
    @EnumSource(LicenseReportMode::class)
    @DisplayName("no flag: every outcome passes, violations and no answer included")
    fun noFlag(mode: LicenseReportMode) {
        (noAnswers + done(listOf(notAllowed, unknown, unidentified))).forEach {
            assertEquals(BuildOutcome.Pass, BuildOutcome.of(none, mode, it))
        }
        assertEquals(BuildOutcome.Pass, BuildOutcome.ofRunnerError(none, mode, "boom"))
    }

    @Test
    @DisplayName("each flag fails on its own verdict and passes on the other two")
    fun eachFlagOwnVerdict() {
        val flags =
            mapOf(
                Verdict.NOT_ALLOWED to none.copy(notAllowed = true),
                Verdict.UNKNOWN to none.copy(unknown = true),
                Verdict.UNIDENTIFIED to none.copy(unidentified = true),
            )
        val violations = listOf(notAllowed, unknown, unidentified)
        flags.forEach { (verdict, failOn) ->
            violations.forEach { violation ->
                val outcome = BuildOutcome.of(failOn, LicenseReportMode.FULL, done(listOf(violation)))
                assertEquals(violation.verdict == verdict, outcome is BuildOutcome.Fail, "$failOn on ${violation.verdict}")
            }
        }
    }

    @Test
    @DisplayName("the reasons name each failing library with its verdict, and only those")
    fun reasonsNameLibraries() {
        val outcome = BuildOutcome.of(none.copy(unknown = true), LicenseReportMode.FULL, done(listOf(notAllowed, unknown)))

        val reasons = (outcome as BuildOutcome.Fail).reasons
        assertEquals(1, reasons.size, reasons.toString())
        assertTrue("pkg:maven/com.example/foo-core@1.0" in reasons.single() && "UNKNOWN" in reasons.single(), reasons.single())
    }

    @Test
    @DisplayName("FULL, any flag on: no answer fails, so nothing ships unchecked (D11)")
    fun fullNoAnswerFails() {
        noAnswers.forEach { assertTrue(BuildOutcome.of(none.copy(unknown = true), LicenseReportMode.FULL, it) is BuildOutcome.Fail, "$it") }
        assertTrue(BuildOutcome.ofRunnerError(none.copy(unknown = true), LicenseReportMode.FULL, "boom") is BuildOutcome.Fail)
    }

    @Test
    @DisplayName("CHECK, any flag on: no answer passes, so Xray being down never breaks a regular build (D11)")
    fun checkNoAnswerPasses() {
        noAnswers.forEach { assertEquals(BuildOutcome.Pass, BuildOutcome.of(all, LicenseReportMode.CHECK, it), "$it") }
        assertEquals(BuildOutcome.Pass, BuildOutcome.ofRunnerError(all, LicenseReportMode.CHECK, "boom"))
    }

    @Test
    @DisplayName("CHECK: a preliminary violation passes, one Xray has seen follows its flag (D11)")
    fun checkPreliminary() {
        assertEquals(BuildOutcome.Pass, BuildOutcome.of(all, LicenseReportMode.CHECK, done(listOf(unknown.copy(preliminary = true)))))
        assertTrue(BuildOutcome.of(all, LicenseReportMode.CHECK, done(listOf(unknown))) is BuildOutcome.Fail)
    }

    @ParameterizedTest
    @EnumSource(LicenseReportMode::class)
    @DisplayName("an excluded component passes, whatever the flags")
    fun excluded(mode: LicenseReportMode) {
        assertEquals(BuildOutcome.Pass, BuildOutcome.of(all, mode, ended(LicenseReportStatus.EXCLUDED)))
    }

    @Test
    @DisplayName("trust NONE and missing license texts never fail")
    fun trustAndTextsNeverFail() {
        val outcome =
            BuildOutcome.of(
                all,
                LicenseReportMode.FULL,
                done(trust = TrustLevel.NONE, missing = listOf(MissingLicenseText("GWT", MissingTextReason.DOWNLOAD_FAILED, "http://x"))),
            )
        assertEquals(BuildOutcome.Pass, outcome)
    }
}
