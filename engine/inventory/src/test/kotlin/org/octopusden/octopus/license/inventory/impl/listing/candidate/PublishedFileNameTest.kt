package org.octopusden.octopus.license.inventory.impl.listing.candidate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PublishedFileNameTest {
    @ParameterizedTest(name = "{0} -> {2} {3} {4}")
    @CsvSource(
        nullValues = ["-"],
        value = [
            "sample-portal-2.4.0-118.zip,           sample-portal, sample-portal,        -,       zip",
            "sample-portal-2.4.0-118-sources.jar,   sample-portal, sample-portal,        sources, jar",
            "sample-portal-config-2.4.0-118.yaml,   sample-portal, sample-portal-config, -,       yaml",
            "sample-portal-2.4.0-118.tar.gz,        sample-portal, sample-portal,        -,       tar.gz",
            "sample-portal-2.4.0-118-dist.tar.gz,   sample-portal, sample-portal,        dist,    tar.gz",
            "renamed.zip,                           sample-portal, sample-portal,        -,       zip",
            "archive.tar.gz,                        sample-portal, sample-portal,        -,       tar.gz",
        ],
    )
    @DisplayName("artifact id, classifier and extension come from the file name, the artifact id falling back to the module's")
    fun split(
        fileName: String,
        moduleArtifactId: String,
        artifactId: String,
        classifier: String?,
        extension: String,
    ) {
        assertEquals(
            PublishedFileName(artifactId, classifier, extension),
            PublishedFileName.split(fileName, moduleArtifactId, "2.4.0-118"),
        )
    }
}
