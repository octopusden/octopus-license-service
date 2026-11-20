package org.octopusden.octopus.license.inventory.impl.port

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.FakeArtifactStore
import org.octopusden.octopus.license.inventory.impl.FakeBinaryScanner
import org.octopusden.octopus.license.inventory.impl.FakeBuildRecords
import org.octopusden.octopus.license.inventory.impl.FakeLicenseDatabase
import org.octopusden.octopus.license.inventory.impl.stored

class ScanPortsTest {
    private val records = FakeBuildRecords()
    private val store = FakeArtifactStore()
    private val scanner = FakeBinaryScanner()
    private val ports = ScanPorts(records, store, scanner, FakeLicenseDatabase()).memoized()

    @Test
    @DisplayName("an answer of nothing found is kept for the scan too, as the prototype's cache kept it")
    fun nothingFoundIsKept() {
        val file = stored("sample-portal-2.4.0-118.zip")

        repeat(2) {
            ports.records.find("sample-portal_ee", "2.4.0-118")
            ports.store.bySha1("0a1b")
            ports.store.byPath("org/example", "missing.zip")
            ports.scanner.scan(file)
        }

        assertEquals(listOf("sample-portal_ee/2.4.0-118"), records.asked)
        assertEquals(listOf("sha1:0a1b", "path:org/example/missing.zip"), store.asked)
        assertEquals(listOf(file.fullPath), scanner.asked)
    }
}
