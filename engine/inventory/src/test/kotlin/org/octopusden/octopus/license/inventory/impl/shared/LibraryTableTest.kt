package org.octopusden.octopus.license.inventory.impl.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.OWNERSHIP
import org.octopusden.octopus.license.inventory.model.Evidence.BINARY_SCAN
import org.octopusden.octopus.license.inventory.model.Evidence.BUILD_RECORD
import org.octopusden.octopus.license.inventory.model.Evidence.CHECKSUM_MATCH
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.PackageId

class LibraryTableTest {
    private val guava = PackageId.parse("pkg:maven/com.google.guava/guava@32.1.2-jre")
    private val netty = PackageId.parse("pkg:maven/io.netty/netty-common@4.1.107.Final")
    private val unnamed = PackageId.parse("generic://sha256:${"ab".repeat(32)}/io.netty.netty-common-4.1.107.Final.jar")
    private val plugins = PackageId.parse("pkg:maven/org.example.portal/portal-plugins@2.4.0-118")
    private val blob = PackageId.parse("generic://sha256:${"cd".repeat(32)}/vendor-blob.so")

    private fun oursById(table: LibraryTable) = table.libraries().associate { it.id to it.ours }

    @Test
    @DisplayName("a Maven row is ours by its group (D2)")
    fun oursByGroup() {
        val table = LibraryTable(Ownership(OWNERSHIP))
        table.add(plugins, emptySet(), BUILD_RECORD, "zip")
        table.add(guava, emptySet(), BUILD_RECORD, "zip")

        assertEquals(mapOf(plugins to true, guava to false), oursById(table))
    }

    @Test
    @DisplayName("a checksum-only row is ours when a copy in one of our repositories was recorded for its checksum (D2)")
    fun oursByRecordedCopy() {
        val table = LibraryTable(Ownership(OWNERSHIP))
        table.add(blob, emptySet(), BINARY_SCAN, "zip")
        table.add(unnamed, emptySet(), BINARY_SCAN, "zip")

        table.recordCopyInOwnRepository("cd".repeat(32))

        assertEquals(mapOf(blob to true, unnamed to false), oursById(table))
    }

    @Test
    @DisplayName("a checksum-only row renamed to Maven coordinates is ours by its new group (D2)")
    fun oursAfterRename() {
        val table = LibraryTable(Ownership(OWNERSHIP))
        table.add(blob, emptySet(), BINARY_SCAN, "zip")

        table.rename(blob, plugins)

        assertEquals(mapOf(plugins to true), oursById(table))
    }

    @Test
    @DisplayName("one row per package: licenses, evidence and files are joined")
    fun merge() {
        val table = LibraryTable(Ownership(OWNERSHIP))
        table.add(guava, setOf("Apache-2.0"), BINARY_SCAN, "zip")
        table.add(guava, emptySet(), BUILD_RECORD, "zip")
        table.add(guava, setOf("Apache 2.0"), BINARY_SCAN, "image")

        assertEquals(
            listOf(FoundLibrary(guava, setOf("Apache-2.0", "Apache 2.0"), setOf(BINARY_SCAN, BUILD_RECORD), setOf("zip", "image"), false)),
            table.libraries(),
        )
    }

    @Test
    @DisplayName("packages come out sorted by rendered id, as the prototype wrote them")
    fun sorted() {
        val table = LibraryTable(Ownership(OWNERSHIP))
        table.add(netty, emptySet(), BUILD_RECORD, "zip")
        table.add(unnamed, setOf("Unknown"), BINARY_SCAN, "zip")
        table.add(guava, emptySet(), BUILD_RECORD, "zip")

        assertEquals(listOf(unnamed, guava, netty), table.libraries().map { it.id })
    }

    @Test
    @DisplayName("renaming moves evidence and files to the named row, adds CHECKSUM_MATCH and drops the unnamed licenses (D10.6)")
    fun rename() {
        val table = LibraryTable(Ownership(OWNERSHIP))
        table.add(unnamed, setOf("Unknown"), BINARY_SCAN, "zip")
        table.add(netty, setOf("Apache-2.0"), BUILD_RECORD, "image")

        table.rename(unnamed, netty)

        assertEquals(
            listOf(
                FoundLibrary(netty, setOf("Apache-2.0"), setOf(BINARY_SCAN, BUILD_RECORD, CHECKSUM_MATCH), setOf("zip", "image"), false),
            ),
            table.libraries(),
        )
    }

    @Test
    @DisplayName("filling licenses adds to a row's licenses")
    fun fill() {
        val table = LibraryTable(Ownership(OWNERSHIP))
        table.add(guava, emptySet(), BUILD_RECORD, "zip")

        table.addLicenses(guava, setOf("Apache-2.0"))

        assertEquals(setOf("Apache-2.0"), table.libraries().single().licenses)
    }
}
