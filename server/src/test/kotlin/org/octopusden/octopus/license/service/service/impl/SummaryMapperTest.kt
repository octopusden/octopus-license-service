package org.octopusden.octopus.license.service.service.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.octopusden.octopus.license.core.dto.FileTrust
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.Trust
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.TrustReason
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.dto.Violation
import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.evaluation.model.SkippedPackages
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.trust.model.TrustGrade
import java.time.Instant
import org.octopusden.octopus.license.trust.model.FileTrust as GradedFile
import org.octopusden.octopus.license.trust.model.TrustReason as GradedReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode as GradedCode

class SummaryMapperTest {
    private val high = TrustGrade(emptyList(), emptyList())
    private val inputs = GenerationInputs("a1b2c3d", Instant.parse("2026-11-11T10:00:00Z"), "1.0.0")
    private val checksumOnly = "generic://sha256:${"cd".repeat(32)}/vendor-blob.so"

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

    private companion object {
        const val ZIP = "org.example.portal:sample-portal:zip"
        const val IMAGE = "sample/portal:2.4.0-118"
    }
}
