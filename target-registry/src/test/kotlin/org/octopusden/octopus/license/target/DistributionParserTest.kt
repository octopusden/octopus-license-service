package org.octopusden.octopus.license.target

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.target.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.DeclaredArtifact.MavenArtifact
import org.octopusden.octopus.license.target.DeclaredArtifact.Unsupported

class DistributionParserTest {
    @Test
    @DisplayName("a GAV entry names group, artifact and extension")
    fun gavWithExtension() {
        assertEquals(
            listOf(MavenArtifact("org.octopusden.dmp", "dmp-bundle", "zip")),
            DistributionParser.parse(gav = "org.octopusden.dmp:dmp-bundle:zip", docker = null),
        )
    }

    @Test
    @DisplayName("a fourth GAV part is the classifier")
    fun gavWithClassifier() {
        assertEquals(
            listOf(MavenArtifact("org.octopusden.dmp", "dmp-bundle-config", "zip", "config")),
            DistributionParser.parse(gav = "org.octopusden.dmp:dmp-bundle-config:zip:config", docker = null),
        )
    }

    @Test
    @DisplayName("comma-separated GAV entries are trimmed and kept in order")
    fun severalGavEntries() {
        assertEquals(
            listOf(MavenArtifact("org.a", "x", "zip"), MavenArtifact("org.b", "y", "jar")),
            DistributionParser.parse(gav = " org.a:x:zip ,  org.b:y:jar", docker = null),
        )
    }

    @Test
    @DisplayName("D7: a GAV entry without an extension is unsupported and does not drop its neighbours")
    fun twoPartGav() {
        val declared = DistributionParser.parse(gav = "org.a:x,org.b:y:jar", docker = null)
        assertEquals(MavenArtifact("org.b", "y", "jar"), declared[1])
        val unsupported = declared[0] as Unsupported
        assertEquals("org.a:x", unsupported.entry)
        requireNotNull(unsupported.reason.ifBlank { null }) { "an unsupported entry should say why" }
    }

    @ParameterizedTest
    @ValueSource(strings = ["file:///share/dmp/bundle.zip", "http://host/bundle.zip", "https://host/a:b:c"])
    @DisplayName("D7: a GAV entry that is a file share or URL is unsupported")
    fun uriGav(entry: String) {
        val declared = DistributionParser.parse(gav = entry, docker = null).single()
        assertEquals(entry, (declared as Unsupported).entry)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", " ", " , "])
    @DisplayName("an absent or empty field declares nothing")
    fun emptyFields(field: String?) {
        assertEquals(emptyList<DeclaredArtifact>(), DistributionParser.parse(gav = field, docker = field))
    }

    @Test
    @DisplayName("a docker image without a tag")
    fun dockerWithoutTag() {
        assertEquals(listOf(DockerImage("dmp/dmp-bundle")), DistributionParser.parse(gav = null, docker = "dmp/dmp-bundle"))
    }

    @Test
    @DisplayName("a docker image with a tag")
    fun dockerWithTag() {
        assertEquals(
            listOf(DockerImage("dmp/dmp-bundle", "1.4")),
            DistributionParser.parse(gav = null, docker = "dmp/dmp-bundle:1.4"),
        )
    }

    @Test
    @DisplayName("D7: a registry port stays part of the image, with a tag")
    fun dockerRegistryPortWithTag() {
        assertEquals(
            listOf(DockerImage("registry.example:5000/dmp/dmp-bundle", "1.4")),
            DistributionParser.parse(gav = null, docker = "registry.example:5000/dmp/dmp-bundle:1.4"),
        )
    }

    @Test
    @DisplayName("D7: a registry port stays part of the image, without a tag")
    fun dockerRegistryPortWithoutTag() {
        assertEquals(
            listOf(DockerImage("registry.example:5000/dmp/dmp-bundle")),
            DistributionParser.parse(gav = null, docker = "registry.example:5000/dmp/dmp-bundle"),
        )
    }

    @Test
    @DisplayName("GAV entries come before docker images")
    fun gavThenDocker() {
        assertEquals(
            listOf(MavenArtifact("org.a", "x", "zip"), DockerImage("a/x"), DockerImage("a/y", "2")),
            DistributionParser.parse(gav = "org.a:x:zip", docker = "a/x, a/y:2"),
        )
    }
}
