package org.octopusden.octopus.license.trust.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.trust.CONFIG
import org.octopusden.octopus.license.trust.IMAGE
import org.octopusden.octopus.license.trust.LEGACY
import org.octopusden.octopus.license.trust.POM
import org.octopusden.octopus.license.trust.ZIP
import org.octopusden.octopus.license.trust.assessment
import org.octopusden.octopus.license.trust.file
import org.octopusden.octopus.license.trust.lib

class GradingSubjectTest {
    private val files =
        listOf(
            file(ZIP),
            file(POM, FileStatus.METADATA),
            file(IMAGE, evidence = setOf(Evidence.BINARY_SCAN)),
            file("org.example.portal:sample-portal-docs:zip", FileStatus.NOT_SELECTED),
            file(CONFIG, FileStatus.PLAIN_FILE),
            file(LEGACY, FileStatus.NOT_FOUND),
        )

    private fun entries(files: List<ReleasedFile>) = files.map { it.entry }

    @Test
    @DisplayName("selected files leave out the ones never selected and metadata, in listing order (D3)")
    fun selected() {
        assertEquals(listOf(ZIP, IMAGE, CONFIG, LEGACY), entries(GradingSubject.of(assessment(files), emptySet()).selected))
    }

    @Test
    @DisplayName("read files are READ; covered files are read files and plain files, in listing order (D3, D4)")
    fun readAndCovered() {
        val subject = GradingSubject.of(assessment(files), emptySet())
        assertEquals(listOf(ZIP, IMAGE), entries(subject.read))
        assertEquals(listOf(ZIP, IMAGE, CONFIG), entries(subject.covered))
    }

    @Test
    @DisplayName("third-party libraries are the counted ones that are not ours")
    fun thirdParty() {
        val subject =
            GradingSubject.of(
                assessment(
                    libraries = listOf(lib("pkg:maven/org.example/ours@1.0", LibraryVerdict.INTERNAL), lib("pkg:npm/left-pad@1.3.0")),
                ),
                emptySet(),
            )
        assertEquals(listOf("pkg:npm/left-pad@1.3.0"), subject.thirdParty.map { it.library.id.toString() })
    }

    @Test
    @DisplayName("an SBOM seen on any counted library counts, ours included (D4)")
    fun sbomSeen() {
        val ours = lib("pkg:maven/org.example/ours@1.0", LibraryVerdict.INTERNAL).let {
            it.copy(library = it.library.copy(evidence = setOf(Evidence.SBOM)))
        }
        assertTrue(GradingSubject.of(assessment(libraries = listOf(ours)), emptySet()).sbomSeen)
        assertFalse(GradingSubject.of(assessment(libraries = listOf(lib("pkg:npm/left-pad@1.3.0"))), emptySet()).sbomSeen)
    }

    @Test
    @DisplayName("a file's own third-party libraries are the ones found in it")
    fun thirdPartyIn() {
        val subject =
            GradingSubject.of(
                assessment(
                    libraries = listOf(lib("pkg:npm/a@1", foundIn = setOf(ZIP)), lib("pkg:npm/b@1", foundIn = setOf(ZIP, IMAGE))),
                ),
                emptySet(),
            )
        assertEquals(2, subject.thirdPartyIn(ZIP).size)
        assertEquals(1, subject.thirdPartyIn(IMAGE).size)
        assertEquals(0, subject.thirdPartyIn(CONFIG).size)
    }
}
