package org.octopusden.octopus.license.inventory.impl.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.FakeBuildRecords
import org.octopusden.octopus.license.inventory.impl.build
import org.octopusden.octopus.license.inventory.impl.builtFile
import org.octopusden.octopus.license.inventory.impl.module
import org.octopusden.octopus.license.inventory.impl.stored
import org.octopusden.octopus.license.inventory.model.BuildLookup

class BuildLocatorTest {
    private val byChecksum =
        build(
            name = "other_build",
            number = "12",
            modules = arrayOf(module(files = listOf(builtFile("x.tar.gz", sha1 = "sha1-lib.tar.gz")))),
        )
    private val byProperties = build(name = "shared-native-build", number = "7.1.3")

    @Test
    @DisplayName("the file's build.name and build.number properties come first")
    fun properties() {
        val located = BuildLocator(FakeBuildRecords(byChecksum, byProperties)).locate(
            stored(
                "lib.tar.gz",
                properties = mapOf(
                    "build.name" to "shared-native-build",
                    "build.number" to "7.1.3",
                ),
            ),
        )

        assertEquals(byProperties to BuildLookup.PROPERTIES, located)
    }

    @Test
    @DisplayName("properties naming a build that does not exist fall back to the checksum")
    fun missingPropertyBuild() {
        val located = BuildLocator(FakeBuildRecords(byChecksum)).locate(
            stored(
                "lib.tar.gz",
                properties = mapOf(
                    "build.name" to "gone",
                    "build.number" to "1",
                ),
            ),
        )

        assertEquals(byChecksum to BuildLookup.CHECKSUM, located)
    }

    @Test
    @DisplayName("neither: no build")
    fun none() {
        assertEquals(null, BuildLocator(FakeBuildRecords()).locate(stored("lib.tar.gz")))
    }

    @Test
    @DisplayName("a file without a sha1 is not searched by checksum")
    fun noSha1() {
        assertEquals(null, BuildLocator(FakeBuildRecords(byChecksum)).locate(stored("lib.tar.gz", sha1 = null)))
    }
}
