package org.octopusden.octopus.license.service.service.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.core.dto.GenerationInputs
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
import java.time.Instant

class SummaryMapperTest {
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
            SummaryMapper.map(workedExample, inputs).libraryCounts,
        )
    }

    @Test
    @DisplayName("no library: every verdict counted 0, no violation")
    fun empty() {
        val summary = SummaryMapper.map(assessment(), inputs)

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
            SummaryMapper.map(workedExample, inputs).violations,
        )
    }

    @Test
    @DisplayName("a NOT_ALLOWED library is a violation with its canonical licenses")
    fun notAllowed() {
        val gpl = assessed("pkg:maven/org.example/sample-lib@2.1", LibraryVerdict.NOT_ALLOWED, listOf("GPL-3.0"))

        assertEquals(
            listOf(Violation("pkg:maven/org.example/sample-lib@2.1", Verdict.NOT_ALLOWED, listOf("GPL-3.0"), listOf(ZIP))),
            SummaryMapper.map(assessment(gpl), inputs).violations,
        )
    }

    @Test
    @DisplayName("trust stays null until grading; the inputs are carried as given")
    fun trustAndInputs() {
        val summary = SummaryMapper.map(workedExample, inputs)

        assertNull(summary.trust)
        assertEquals(inputs, summary.inputs)
    }

    private companion object {
        const val ZIP = "org.example.portal:sample-portal:zip"
        const val IMAGE = "sample/portal:2.4.0-118"
    }
}
