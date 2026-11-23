package org.octopusden.octopus.license.inventory.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PackageIdTest {
    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "pkg:maven/com.google.guava/guava@32.1.2-jre",
            "pkg:npm/@angular/core@17.3.1",
            "pkg:npm/react@18.2.0",
            "pkg:golang/golang.org/x/net@0.20.0",
            "pkg:pypi/requests@2.31.0",
            "pkg:maven/org.example/lib@",
        ],
    )
    @DisplayName("a purl the prototype writes parses to a Purl and renders back unchanged")
    fun purlRoundTrip(raw: String) {
        val id = PackageId.parse(raw)

        assertEquals(PackageId.Purl::class, id::class)
        assertEquals(raw, id.toString())
    }

    @Test
    @DisplayName("a Maven purl splits into type, group/artifact name and version")
    fun mavenParts() {
        assertEquals(
            PackageId.Purl("maven", "com.google.guava/guava", "32.1.2-jre"),
            PackageId.parse("pkg:maven/com.google.guava/guava@32.1.2-jre"),
        )
    }

    @Test
    @DisplayName("a scoped npm name keeps its @: the version follows the last @")
    fun scopedNpm() {
        assertEquals(PackageId.Purl("npm", "@angular/core", "17.3.1"), PackageId.parse("pkg:npm/@angular/core@17.3.1"))
    }

    @Test
    @DisplayName("a checksum id Xray gives a file it could not name is ChecksumOnly")
    fun checksumOnly() {
        val raw = "generic://sha256:00914796d4a05e6f6dc18ff97aed23c3822498c6128710d1fb5a552a1c96f01b/jackson-dataclass-1.3.82.jar"

        val id = PackageId.parse(raw)

        assertEquals(
            PackageId.ChecksumOnly("00914796d4a05e6f6dc18ff97aed23c3822498c6128710d1fb5a552a1c96f01b", "jackson-dataclass-1.3.82.jar"),
            id,
        )
        assertEquals(raw, id.toString())
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "generic://sha256:short/name.jar",
            "conan://zlib:1.3",
            "pkg:maven",
            "pkg:maven/no-version",
        ],
    )
    @DisplayName("anything else, or a purl that would not render back the same, is kept verbatim as Unrecognized")
    fun unrecognized(raw: String) {
        val id = PackageId.parse(raw)

        assertEquals(PackageId.Unrecognized(raw), id)
        assertEquals(raw, id.toString())
    }

    @Test
    @DisplayName("Maven coordinates of a Maven purl, none for other types")
    fun mavenCoordinates() {
        assertEquals(
            Triple("com.google.guava", "guava", "32.1.2-jre"),
            PackageId
                .Purl("maven", "com.google.guava/guava", "32.1.2-jre")
                .mavenCoordinates()
                ?.let { Triple(it.groupId, it.artifactId, it.version) },
        )
        assertEquals(null, PackageId.Purl("npm", "react", "18.2.0").mavenCoordinates())
    }

    @Test
    @DisplayName("a Maven package from its coordinates renders as the prototype's purl")
    fun mavenFromCoordinates() {
        assertEquals("pkg:maven/com.google.guava/guava@32.1.2-jre", PackageId.maven("com.google.guava", "guava", "32.1.2-jre").toString())
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        "pkg:maven/com.google.guava/guava@32.1.2-jre, maven",
        "pkg:docker/sha256__abc@latest, docker",
        "generic://sha256:0000000000000000000000000000000000000000000000000000000000000000/a.jar, generic",
        "conan://zlib:1.3, conan",
        "pkg:maven/no-version, maven",
        "no-scheme, no-scheme",
    )
    @DisplayName("the package type, read as the prototype reads it from any id")
    fun type(
        raw: String,
        type: String,
    ) {
        assertEquals(type, PackageId.parse(raw).type)
    }
}
