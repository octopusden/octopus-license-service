package org.octopusden.octopus.license.inventory.impl.listing.candidate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.BUILD_VERSION
import org.octopusden.octopus.license.inventory.impl.GROUP
import org.octopusden.octopus.license.inventory.impl.build
import org.octopusden.octopus.license.inventory.impl.builtFile
import org.octopusden.octopus.license.inventory.impl.listing.data.ImageCandidate
import org.octopusden.octopus.license.inventory.impl.listing.data.MavenCandidate
import org.octopusden.octopus.license.inventory.impl.listing.data.Selection
import org.octopusden.octopus.license.inventory.impl.target
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.SelectionReason.DECLARED
import org.octopusden.octopus.license.inventory.model.SelectionReason.DOCKER_BUILD
import org.octopusden.octopus.license.inventory.model.SelectionReason.PATTERN
import org.octopusden.octopus.license.target.model.ArtifactPattern
import org.octopusden.octopus.license.target.model.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.model.DeclaredArtifact.MavenArtifact

class CandidateSelectorTest {
    private val pattern = ArtifactPattern(listOf("org\\.example\\.portal"), listOf("sample-.*"))

    private fun maven(
        artifactId: String = "sample-portal",
        extension: String = "zip",
        classifier: String? = null,
    ) = MavenCandidate(GROUP, artifactId, BUILD_VERSION, classifier, extension, build(), builtFile("$artifactId.$extension"))

    private fun image(name: String = "sample/portal") = ImageCandidate(name, BUILD_VERSION, build(), builtFile("manifest.json"))

    @Test
    @DisplayName("declared and matching a pattern: selected by both")
    fun declaredAndPattern() {
        val selection = CandidateSelector(target(listOf(MavenArtifact(GROUP, "sample-portal", "zip")), listOf(pattern))).select(maven())

        assertEquals(
            Selection(setOf(DECLARED, PATTERN), skip = null, matched = MavenArtifact(GROUP, "sample-portal", "zip")),
            selection,
        )
    }

    @Test
    @DisplayName("matching only a pattern: selected, not declared")
    fun patternOnly() {
        val selection = CandidateSelector(target(patterns = listOf(pattern))).select(maven())

        assertEquals(setOf(PATTERN), selection.selectedBy)
        assertEquals(null, selection.skip)
    }

    @Test
    @DisplayName("neither declared nor matching: NOT_SELECTED")
    fun neither() {
        assertEquals(
            FileStatus.NOT_SELECTED,
            CandidateSelector(target(patterns = listOf(pattern))).select(maven(artifactId = "helper-tool")).skip,
        )
    }

    @Test
    @DisplayName("a declared classifier must match too")
    fun classifier() {
        val selector = CandidateSelector(target(listOf(MavenArtifact(GROUP, "sample-portal", "zip", "dist"))))

        assertEquals(FileStatus.NOT_SELECTED, selector.select(maven()).skip)
        assertEquals(setOf(DECLARED), selector.select(maven(classifier = "dist")).selectedBy)
    }

    @Test
    @DisplayName("a pattern-selected pom, module file or checksum is METADATA")
    fun metadataByExtension() {
        val selector = CandidateSelector(target(patterns = listOf(pattern)))

        listOf("pom", "module", "md5", "sha1", "sha256", "asc").forEach {
            assertEquals(FileStatus.METADATA, selector.select(maven(extension = it)).skip, it)
        }
    }

    @Test
    @DisplayName("a pattern-selected sources, javadoc or test jar is METADATA")
    fun metadataByClassifier() {
        val selector = CandidateSelector(target(patterns = listOf(pattern)))

        listOf("sources", "javadoc", "tests", "test-sources", "test-fixtures").forEach {
            assertEquals(FileStatus.METADATA, selector.select(maven(extension = "jar", classifier = it)).skip, it)
        }
    }

    @Test
    @DisplayName("a declared pom is read like any other selected file")
    fun declaredPom() {
        val selection = CandidateSelector(target(listOf(MavenArtifact(GROUP, "sample-portal", "pom")))).select(maven(extension = "pom"))

        assertEquals(null, selection.skip)
    }

    @Test
    @DisplayName("an image is always selected, and also DECLARED when a declared image has its name, tag or no tag")
    fun images() {
        val selector = CandidateSelector(target(listOf(DockerImage("sample/portal"))))

        assertEquals(
            Selection(setOf(DOCKER_BUILD, DECLARED), skip = null, matched = DockerImage("sample/portal")),
            selector.select(image()),
        )
        assertEquals(Selection(setOf(DOCKER_BUILD), skip = null, matched = null), selector.select(image("sample/other")))
    }

    @Test
    @DisplayName("several declared images with one name: the image matches only the first, the others stay leftovers (D10.9)")
    fun firstDeclaredImage() {
        val selector = CandidateSelector(target(listOf(DockerImage("sample/portal", "1.0"), DockerImage("sample/portal", "2.0"))))

        assertEquals(DockerImage("sample/portal", "1.0"), selector.select(image()).matched)
    }
}
