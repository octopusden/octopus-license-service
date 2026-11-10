package org.octopusden.octopus.license.inventory.impl.naming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.FakeArtifactStore
import org.octopusden.octopus.license.inventory.impl.OWNERSHIP
import org.octopusden.octopus.license.inventory.impl.Ownership
import org.octopusden.octopus.license.inventory.impl.stored
import org.octopusden.octopus.license.inventory.model.Evidence.BINARY_SCAN
import org.octopusden.octopus.license.inventory.model.Evidence.CHECKSUM_MATCH
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.PackageId

class ChecksumNamerTest {
    private val sha256 = "ab".repeat(32)
    private val store = FakeArtifactStore()
    private val table = LibraryTable()
    private val namer = ChecksumNamer(store, Ownership(OWNERSHIP))

    private fun unnamed(fileName: String) =
        PackageId.parse("generic://sha256:$sha256/$fileName").also {
            table.add(it, setOf("Unknown"), BINARY_SCAN, "zip")
        }

    @Test
    @DisplayName("a renamed copy of a Maven Central jar is named by the copy stored under its coordinates")
    fun renamedCopy() {
        unnamed("io.netty.netty-common-4.1.107.Final.jar")
        store.add(
            stored(
                "netty-common-4.1.107.Final.jar",
                path = "io/netty/netty-common/4.1.107.Final",
                repo = "maven-central-cache",
                sha256 = sha256,
            ),
        )

        namer.name(table)

        assertEquals(
            listOf(
                FoundLibrary(
                    PackageId.parse("pkg:maven/io.netty/netty-common@4.1.107.Final"),
                    emptySet(),
                    setOf(BINARY_SCAN, CHECKSUM_MATCH),
                    setOf("zip"),
                ),
            ),
            table.libraries(),
        )
    }

    @Test
    @DisplayName("copies are tried in the order Artifactory answers; one not in a Maven layout is passed over")
    fun firstMavenLayout() {
        unnamed("lib.jar")
        store.add(stored("renamed.jar", path = "some/folder", repo = "generic-local", sha256 = sha256))
        store.add(stored("lib-2.0.jar", path = "org/example2/lib/2.0", repo = "maven-central-cache", sha256 = sha256))

        namer.name(table)

        assertEquals(listOf("pkg:maven/org.example2/lib@2.0"), table.libraries().map { it.id.toString() })
    }

    @Test
    @DisplayName("ours by file name: not looked up, stays checksum-only")
    fun oursByName() {
        val id = unnamed("org.example.parser-1.2.jar")

        namer.name(table)

        assertEquals(listOf(id), table.libraries().map { it.id })
        assertEquals(emptyList<String>(), store.asked)
    }

    @Test
    @DisplayName("ours by a copy in an own repository: stays checksum-only")
    fun oursByRepository() {
        val id = unnamed("parser.jar")
        store.add(stored("parser-1.2.jar", path = "org/example/parser/1.2", repo = "rnd-maven-release-local", sha256 = sha256))

        namer.name(table)

        assertEquals(listOf(id), table.libraries().map { it.id })
    }

    @Test
    @DisplayName("found nowhere: stays checksum-only with its licenses")
    fun nowhere() {
        unnamed("mystery.jar")

        namer.name(table)

        assertEquals(setOf("Unknown"), table.libraries().single().licenses)
    }

    @Test
    @DisplayName("named into a package already found keeps that package's licenses and joins the evidence")
    fun mergedIntoExisting() {
        val netty = PackageId.parse("pkg:maven/io.netty/netty-common@4.1.107.Final")
        table.add(netty, setOf("Apache-2.0"), BINARY_SCAN, "image")
        unnamed("netty.jar")
        store.add(
            stored(
                "netty-common-4.1.107.Final.jar",
                path = "io/netty/netty-common/4.1.107.Final",
                repo = "maven-central-cache",
                sha256 = sha256,
            ),
        )

        namer.name(table)

        assertEquals(
            listOf(FoundLibrary(netty, setOf("Apache-2.0"), setOf(BINARY_SCAN, CHECKSUM_MATCH), setOf("image", "zip"))),
            table.libraries(),
        )
    }
}
