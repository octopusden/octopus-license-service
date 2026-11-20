package org.octopusden.octopus.license.evaluation.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.library
import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.ALLOWED
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.INTERNAL
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.NOT_ALLOWED
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.UNIDENTIFIED
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.UNKNOWN
import org.octopusden.octopus.license.evaluation.model.SourceDisagreement
import org.octopusden.octopus.license.evaluation.policy
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseSource

class LibraryJudgeTest {
    private val judge = LibraryJudge(policy)
    private val checksumOnly = "generic://sha256:${"cd".repeat(32)}/vendor-blob.so"

    private fun judged(library: FoundLibrary) = judge.judge(library).let { Triple(it.verdict, it.licenses, it.overridden) }

    @Test
    @DisplayName("ours is INTERNAL before any other rule, even a checksum-only file (D4.1)")
    fun internal() {
        assertEquals(INTERNAL, judge.judge(library("pkg:maven/org.example.portal/portal-plugins@2.4.0-118", ours = true)).verdict)
        assertEquals(INTERNAL, judge.judge(library(checksumOnly, ours = true)).verdict)
    }

    @Test
    @DisplayName("ours with an override is INTERNAL and still reports the override, as the prototype computes it first (D4)")
    fun internalOverridden() {
        assertEquals(
            Triple(INTERNAL, listOf("MIT"), true),
            judged(library("pkg:maven/org.example/sample-lib@1.5", setOf("GPL-3.0"), ours = true)),
        )
    }

    @Test
    @DisplayName("a checksum-only file that is not ours is UNIDENTIFIED, with or without licenses (D4.2)")
    fun unidentified() {
        assertEquals(UNIDENTIFIED, judge.judge(library(checksumOnly)).verdict)
        assertEquals(UNIDENTIFIED, judge.judge(library(checksumOnly, setOf("MIT"))).verdict)
    }

    @Test
    @DisplayName("any id written generic:// is UNIDENTIFIED, as the prototype tests the rendering; pkg:generic is judged (D4.2)")
    fun unidentifiedByRendering() {
        assertEquals(UNIDENTIFIED, judge.judge(library("generic://vendor-blob.so", setOf("MIT"))).verdict)
        assertEquals(ALLOWED, judge.judge(library("pkg:generic/vendor-blob@1.0", setOf("MIT"))).verdict)
    }

    @Test
    @DisplayName("a synonym of an allowed license is ALLOWED under its canonical name")
    fun synonym() {
        assertEquals(
            Triple(ALLOWED, listOf("Apache-2.0"), false),
            judged(library("pkg:maven/org.springframework/spring-core@6.1.4", setOf("The Apache Software License, Version 2.0"))),
        )
    }

    @Test
    @DisplayName("a Maven library inside an override's range takes the override's licenses")
    fun override() {
        assertEquals(Triple(ALLOWED, listOf("MIT"), true), judged(library("pkg:maven/org.example/sample-lib@1.5", setOf("GPL-3.0"))))
        assertEquals(
            Triple(NOT_ALLOWED, listOf("GPL-3.0"), false),
            judged(library("pkg:maven/org.example/sample-lib@2.1", setOf("GPL-3.0"))),
        )
    }

    @Test
    @DisplayName(
        "a Maven library with no licenses found, as after a failed lookup, still takes the override's licenses",
    )
    fun overrideWithoutLicenses() {
        assertEquals(Triple(ALLOWED, listOf("MIT"), true), judged(library("pkg:maven/org.example/sample-lib@1.5", emptySet())))
        assertEquals(Triple(UNKNOWN, emptyList<String>(), false), judged(library("pkg:maven/org.example/sample-lib@2.1", emptySet())))
    }

    @Test
    @DisplayName("an override never applies to a package that is not Maven, whatever its name")
    fun overrideMavenOnly() {
        assertEquals(
            Triple(NOT_ALLOWED, listOf("GPL-3.0"), false),
            judged(library("pkg:npm/org.example/sample-lib@1.5", setOf("GPL-3.0"))),
        )
    }

    @Test
    @DisplayName("licenses from two sources are judged together: one allowed name makes the library ALLOWED, as the license plugins do")
    fun sourcesDisagree() {
        val gplInXrayMitInSbom = library("pkg:maven/org.example/two-sources@1.0", setOf("GPL-3.0", "MIT"))

        assertEquals(Triple(ALLOWED, listOf("GPL-3.0", "MIT"), false), judged(gplInXrayMitInSbom))
    }

    @Test
    @DisplayName("no license, or only Unknown, is UNKNOWN")
    fun unknown() {
        assertEquals(Triple(UNKNOWN, emptyList<String>(), false), judged(library("pkg:npm/left-pad@1.3.0")))
        assertEquals(UNKNOWN, judge.judge(library("pkg:npm/left-pad@1.3.0", setOf("Unknown"))).verdict)
    }

    @Test
    @DisplayName("the assessed library keeps the library it judged")
    fun keepsLibrary() {
        val found = library("pkg:npm/left-pad@1.3.0")

        assertEquals(AssessedLibrary(found, UNKNOWN, emptyList(), false), judge.judge(found))
    }

    private fun fromSources(
        id: String,
        vararg names: Pair<LicenseSource, Set<String>>,
        ours: Boolean = false,
    ) = library(id, names.flatMap { it.second }.toSet(), ours = ours).copy(licensesBySource = names.toMap())

    @Test
    @DisplayName("sources whose names would give different verdicts are a disagreement; the verdict stays the policy's (D12)")
    fun disagreement() {
        val judged =
            judge.judge(
                fromSources("pkg:npm/left-pad@1.3.0", LicenseSource.BINARY_SCAN to setOf("GPL-3.0"), LicenseSource.SBOM to setOf("MIT")),
            )

        assertEquals(ALLOWED, judged.verdict)
        assertEquals(
            SourceDisagreement(mapOf(LicenseSource.BINARY_SCAN to listOf("GPL-3.0"), LicenseSource.SBOM to listOf("MIT"))),
            judged.disagreement,
        )
    }

    @Test
    @DisplayName("different spellings of one license are no disagreement: each source is judged by canonical name (D12)")
    fun sameLicenseOtherName() {
        val judged =
            judge.judge(
                fromSources(
                    "pkg:maven/org.thirdparty/lib@1.0",
                    LicenseSource.BINARY_SCAN to setOf("Apache-2.0"),
                    LicenseSource.SBOM to setOf("The Apache Software License, Version 2.0"),
                ),
            )

        assertEquals(null, judged.disagreement)
    }

    @Test
    @DisplayName("different names with the same verdict are no disagreement: the verdict does not depend on the source (D12)")
    fun sameVerdict() {
        val judged =
            judge.judge(
                fromSources("pkg:npm/left-pad@1.3.0", LicenseSource.BINARY_SCAN to setOf("Apache-2.0"), LicenseSource.SBOM to setOf("MIT")),
            )

        assertEquals(null, judged.disagreement)
    }

    @Test
    @DisplayName("one source naming nothing it knows is a disagreement: UNKNOWN against ALLOWED (D12)")
    fun unknownAgainstAllowed() {
        val judged =
            judge.judge(
                fromSources("pkg:npm/left-pad@1.3.0", LicenseSource.BINARY_SCAN to setOf("Unknown"), LicenseSource.SBOM to setOf("MIT")),
            )

        assertEquals(
            listOf(LicenseSource.BINARY_SCAN, LicenseSource.SBOM),
            judged.disagreement
                ?.bySource
                ?.keys
                ?.toList(),
        )
    }

    @Test
    @DisplayName("no disagreement when an override replaced the names, or for ours, or for a file nobody could name (D12)")
    fun noDisagreementWhenNamesDoNotDecide() {
        val overridden =
            fromSources(
                "pkg:maven/org.example/sample-lib@1.5",
                LicenseSource.BINARY_SCAN to setOf("GPL-3.0"),
                LicenseSource.SBOM to setOf("MIT"),
            )
        val ours =
            fromSources(
                "pkg:maven/org.example.portal/portal-plugins@2.4.0-118",
                LicenseSource.BINARY_SCAN to setOf("GPL-3.0"),
                LicenseSource.SBOM to setOf("MIT"),
                ours = true,
            )
        val unnamed = fromSources(checksumOnly, LicenseSource.BINARY_SCAN to setOf("GPL-3.0"), LicenseSource.SBOM to setOf("MIT"))

        assertEquals(listOf(null, null, null), listOf(overridden, ours, unnamed).map { judge.judge(it).disagreement })
    }

    @Test
    @DisplayName("one source is never a disagreement")
    fun oneSource() {
        assertEquals(
            null,
            judge.judge(fromSources("pkg:npm/left-pad@1.3.0", LicenseSource.BINARY_SCAN to setOf("GPL-3.0", "MIT"))).disagreement,
        )
    }
}
