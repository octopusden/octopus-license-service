package org.octopusden.octopus.license.service.service.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.octopusden.octopus.license.core.dto.FileTrust
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseDisagreement
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.MissingLicenseText
import org.octopusden.octopus.license.core.dto.Trust
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.TrustReason
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.evaluation.model.SkippedPackages
import org.octopusden.octopus.license.evaluation.model.SourceDisagreement
import org.octopusden.octopus.license.files.model.MissingText
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.LicenseSource
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.inventory.model.SelectionReason
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.trust.model.TrustGrade
import java.time.Instant
import org.octopusden.octopus.license.files.model.MissingTextReason as EngineMissingReason
import org.octopusden.octopus.license.trust.model.FileTrust as GradedFile
import org.octopusden.octopus.license.trust.model.TrustReason as GradedReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode as GradedCode

class SummaryMapperTest {
    private val high = TrustGrade(emptyList(), emptyList())
    private val inputs = GenerationInputs("a1b2c3d", Instant.parse("2026-11-11T10:00:00Z"), "1.0.0")
    private val checksumOnly = "generic://sha256:${"cd".repeat(32)}/vendor-blob.so"
    private val leftPad = assessed("pkg:npm/left-pad@1.3.0", LibraryVerdict.UNKNOWN, foundIn = setOf(ZIP, IMAGE))
    private val buildRecordOnly = setOf(Evidence.BUILD_RECORD)
    private val scanned = setOf(Evidence.BINARY_SCAN, Evidence.BUILD_RECORD)

    // The design's worked example, as evaluation assesses it.
    private val workedExample =
        assessment(
            assessed(checksumOnly, LibraryVerdict.UNIDENTIFIED),
            assessed("pkg:maven/org.example.portal/portal-plugins@2.4.0-118", LibraryVerdict.INTERNAL),
            assessed("pkg:maven/org.example/sample-lib@1.5", LibraryVerdict.ALLOWED, listOf("MIT")),
            assessed("pkg:maven/org.springframework/spring-core@6.1.4", LibraryVerdict.ALLOWED, listOf("Apache-2.0")),
            assessed("pkg:npm/left-pad@1.3.0", LibraryVerdict.UNKNOWN, foundIn = setOf(ZIP, IMAGE)),
            assessed("pkg:pypi/attrs@23.2.0", LibraryVerdict.ALLOWED, listOf("MIT"), setOf(IMAGE, ZIP)),
            skipped = SkippedPackages(osPackages = 1, baseImagePython = 1, perFile = mapOf(IMAGE to 2)),
        )

    private fun assessed(
        id: String,
        verdict: LibraryVerdict,
        licenses: List<String> = emptyList(),
        foundIn: Set<String> = setOf(ZIP),
    ) = AssessedLibrary(
        FoundLibrary(PackageId.parse(id), emptySet(), setOf(Evidence.BINARY_SCAN), foundIn, false),
        verdict,
        licenses,
        false,
    )

    private fun assessment(
        vararg libraries: AssessedLibrary,
        skipped: SkippedPackages = SkippedPackages(0, 0, emptyMap()),
    ) = LicenseAssessment(
        LicenseInventory(ComponentVersion("sample-portal", "2.4.0"), emptyList(), emptyList(), libraries.map { it.library }, emptyList()),
        libraries.toList(),
        skipped,
    )

    @Test
    @DisplayName("third-party libraries are counted per verdict, zeros included; internal and skipped ones are not")
    fun counts() {
        assertEquals(
            mapOf(Verdict.ALLOWED to 3, Verdict.NOT_ALLOWED to 0, Verdict.UNKNOWN to 1, Verdict.UNIDENTIFIED to 1),
            SummaryMapper.map(workedExample, high, inputs).libraryCounts,
        )
    }

    @Test
    @DisplayName("no library: every verdict counted 0, no violation")
    fun empty() {
        val summary = SummaryMapper.map(assessment(), high, inputs)

        assertEquals(Verdict.entries.associateWith { 0 }, summary.libraryCounts)
        assertEquals(emptyList<Violation>(), summary.violations)
    }

    @Test
    @DisplayName("every third-party library not ALLOWED is a violation, in the assessment's order, with its files sorted")
    fun violations() {
        assertEquals(
            listOf(
                Violation(checksumOnly, Verdict.UNIDENTIFIED, emptyList(), listOf(ZIP)),
                Violation("pkg:npm/left-pad@1.3.0", Verdict.UNKNOWN, emptyList(), listOf(ZIP, IMAGE).sorted()),
            ),
            SummaryMapper.map(workedExample, high, inputs).violations,
        )
    }

    @Test
    @DisplayName("a NOT_ALLOWED library is a violation with its canonical licenses")
    fun notAllowed() {
        val gpl = assessed("pkg:maven/org.example/sample-lib@2.1", LibraryVerdict.NOT_ALLOWED, listOf("GPL-3.0"))

        assertEquals(
            listOf(Violation("pkg:maven/org.example/sample-lib@2.1", Verdict.NOT_ALLOWED, listOf("GPL-3.0"), listOf(ZIP))),
            SummaryMapper.map(assessment(gpl), high, inputs).violations,
        )
    }

    @Test
    @DisplayName("the grade is the summary's trust, one to one, texts included (trust design, D8)")
    fun trust() {
        val grade =
            TrustGrade(
                reasons = listOf(GradedReason(GradedCode.NO_BUILD_RECORD, "no build for: $IMAGE")),
                files = listOf(
                    GradedFile(ZIP, emptyList()),
                    GradedFile(IMAGE, listOf(GradedCode.NO_BUILD_RECORD, GradedCode.NO_THIRD_PARTY_FOUND)),
                ),
            )

        assertEquals(
            Trust(
                level = TrustLevel.MEDIUM,
                reasons =
                    listOf(
                        TrustReason(
                            "NO_BUILD_RECORD",
                            TrustLevel.MEDIUM,
                            GradedCode.NO_BUILD_RECORD.title,
                            "no build for: $IMAGE",
                            GradedCode.NO_BUILD_RECORD.meaning,
                            GradedCode.NO_BUILD_RECORD.fix,
                        ),
                    ),
                files =
                    listOf(
                        FileTrust(ZIP, TrustLevel.HIGH, emptyList()),
                        FileTrust(IMAGE, TrustLevel.LOW, listOf("NO_BUILD_RECORD", "NO_THIRD_PARTY_FOUND")),
                    ),
            ),
            SummaryMapper.map(workedExample, grade, inputs).trust,
        )
    }

    @ParameterizedTest
    @EnumSource(GradedCode::class)
    @DisplayName("every graded code goes on the wire by its name, with its level")
    fun everyCode(code: GradedCode) {
        val wire = SummaryMapper
            .map(assessment(), TrustGrade(listOf(GradedReason(code, "")), emptyList()), inputs)
            .trust.reasons
            .single()

        assertEquals(code.name, wire.code)
        assertEquals(code.level.name, wire.level.name)
    }

    @Test
    @DisplayName("the inputs are carried as given")
    fun inputs() {
        assertEquals(inputs, SummaryMapper.map(workedExample, high, inputs).inputs)
    }

    private fun withFiles(
        library: AssessedLibrary,
        vararg files: Pair<String, Set<Evidence>>,
    ) = LicenseAssessment(
        LicenseInventory(
            ComponentVersion("sample-portal", "2.4.0"),
            emptyList(),
            files.map { (entry, evidence) ->
                ReleasedFile(entry, FileKind.MAVEN, setOf(SelectionReason.DECLARED), status = FileStatus.READ, evidence = evidence)
            },
            listOf(library.library),
            emptyList(),
        ),
        listOf(library),
        SkippedPackages(0, 0, emptyMap()),
    )

    @Test
    @DisplayName("CHECK: a violation in files Xray has not scanned yet is preliminary (D11)")
    fun preliminary() {
        val summary =
            SummaryMapper.map(withFiles(leftPad, ZIP to buildRecordOnly, IMAGE to buildRecordOnly), high, inputs, LicenseReportMode.CHECK)
        assertEquals(listOf(true), summary.violations.map { it.preliminary })
    }

    @Test
    @DisplayName("CHECK: a violation in one file Xray has scanned is not preliminary (D11)")
    fun seenByXray() {
        val summary = SummaryMapper.map(withFiles(leftPad, ZIP to buildRecordOnly, IMAGE to scanned), high, inputs, LicenseReportMode.CHECK)
        assertEquals(listOf(false), summary.violations.map { it.preliminary })
    }

    @Test
    @DisplayName("FULL: a violation is never preliminary, whatever Xray saw (D11)")
    fun fullNeverPreliminary() {
        val summary = SummaryMapper.map(
            withFiles(leftPad, ZIP to buildRecordOnly, IMAGE to buildRecordOnly),
            high,
            inputs,
            LicenseReportMode.FULL,
        )
        assertEquals(listOf(false), summary.violations.map { it.preliminary })
    }

    @Test
    @DisplayName("the missing license texts are carried one to one (D8)")
    fun missingTexts() {
        val missing =
            listOf(
                MissingText("GWT", EngineMissingReason.DOWNLOAD_FAILED, "http://www.gwtproject.org/terms.html"),
                MissingText("SSPL-1.0", EngineMissingReason.NO_REGISTRY_ENTRY),
            )
        assertEquals(
            listOf(
                MissingLicenseText("GWT", "DOWNLOAD_FAILED", "http://www.gwtproject.org/terms.html"),
                MissingLicenseText("SSPL-1.0", "NO_REGISTRY_ENTRY"),
            ),
            SummaryMapper.map(workedExample, high, inputs, missingTexts = missing).missingLicenseTexts,
        )
    }

    @Test
    @DisplayName(
        "a third-party library whose sources disagree is listed with each source's names and its verdict (write-license-files D12)",
    )
    fun disagreements() {
        val disagreeing =
            AssessedLibrary(
                FoundLibrary(
                    PackageId.parse("pkg:npm/left-pad@1.3.0"),
                    setOf("GPL-3.0", "MIT"),
                    setOf(Evidence.BINARY_SCAN),
                    setOf(ZIP, IMAGE),
                    false,
                ),
                LibraryVerdict.ALLOWED,
                listOf("GPL-3.0", "MIT"),
                false,
                SourceDisagreement(mapOf(LicenseSource.BINARY_SCAN to listOf("GPL-3.0"), LicenseSource.SBOM to listOf("MIT"))),
            )

        assertEquals(
            listOf(
                LicenseDisagreement(
                    "pkg:npm/left-pad@1.3.0",
                    Verdict.ALLOWED,
                    mapOf("BINARY_SCAN" to listOf("GPL-3.0"), "SBOM" to listOf("MIT")),
                    listOf(ZIP, IMAGE),
                ),
            ),
            SummaryMapper.map(assessment(disagreeing), high, inputs).licenseDisagreements,
        )
    }

    private companion object {
        const val ZIP = "org.example.portal:sample-portal:zip"
        const val IMAGE = "sample/portal:2.4.0-118"
    }
}
