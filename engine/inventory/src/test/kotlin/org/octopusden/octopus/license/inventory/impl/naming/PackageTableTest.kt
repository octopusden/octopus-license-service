package org.octopusden.octopus.license.inventory.impl.naming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.model.Evidence.BINARY_SCAN
import org.octopusden.octopus.license.inventory.model.Evidence.BUILD_RECORD
import org.octopusden.octopus.license.inventory.model.Evidence.CHECKSUM_MATCH
import org.octopusden.octopus.license.inventory.model.FoundPackage
import org.octopusden.octopus.license.inventory.model.PackageId

class PackageTableTest {
    private val guava = PackageId.parse("pkg:maven/com.google.guava/guava@32.1.2-jre")
    private val netty = PackageId.parse("pkg:maven/io.netty/netty-common@4.1.107.Final")
    private val unnamed = PackageId.parse("generic://sha256:${"ab".repeat(32)}/io.netty.netty-common-4.1.107.Final.jar")

    @Test
    @DisplayName("one row per package: licenses, evidence and files are joined")
    fun merge() {
        val table = PackageTable()
        table.add(guava, setOf("Apache-2.0"), BINARY_SCAN, "zip")
        table.add(guava, emptySet(), BUILD_RECORD, "zip")
        table.add(guava, setOf("Apache 2.0"), BINARY_SCAN, "image")

        assertEquals(
            listOf(FoundPackage(guava, setOf("Apache-2.0", "Apache 2.0"), setOf(BINARY_SCAN, BUILD_RECORD), setOf("zip", "image"))),
            table.packages(),
        )
    }

    @Test
    @DisplayName("packages come out sorted by rendered id, as the prototype wrote them")
    fun sorted() {
        val table = PackageTable()
        table.add(netty, emptySet(), BUILD_RECORD, "zip")
        table.add(unnamed, setOf("Unknown"), BINARY_SCAN, "zip")
        table.add(guava, emptySet(), BUILD_RECORD, "zip")

        assertEquals(listOf(unnamed, guava, netty), table.packages().map { it.id })
    }

    @Test
    @DisplayName("renaming moves evidence and files to the named row, adds CHECKSUM_MATCH and drops the unnamed licenses (D10.6)")
    fun rename() {
        val table = PackageTable()
        table.add(unnamed, setOf("Unknown"), BINARY_SCAN, "zip")
        table.add(netty, setOf("Apache-2.0"), BUILD_RECORD, "image")

        table.rename(unnamed, netty)

        assertEquals(
            listOf(FoundPackage(netty, setOf("Apache-2.0"), setOf(BINARY_SCAN, BUILD_RECORD, CHECKSUM_MATCH), setOf("zip", "image"))),
            table.packages(),
        )
    }

    @Test
    @DisplayName("filling licenses adds to a row's licenses")
    fun fill() {
        val table = PackageTable()
        table.add(guava, emptySet(), BUILD_RECORD, "zip")

        table.addLicenses(guava, setOf("Apache-2.0"))

        assertEquals(setOf("Apache-2.0"), table.packages().single().licenses)
    }
}
