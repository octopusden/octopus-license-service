package org.octopusden.octopus.license.inventory.impl.reading.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.impl.FakeArtifactStore
import org.octopusden.octopus.license.inventory.impl.build
import org.octopusden.octopus.license.inventory.impl.builtFile
import org.octopusden.octopus.license.inventory.impl.module
import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.impl.stored
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode.SBOM_UNREADABLE
import org.octopusden.octopus.license.inventory.model.PackageId

class SbomReaderTest {
    private val lodash = """{"components":[{"purl":"pkg:npm/lodash@4.17.21","licenses":[{"license":{"id":"MIT"}}]}]}"""
    private val lodashFound = mapOf(PackageId.parse("pkg:npm/lodash@4.17.21") to setOf("MIT"))
    private val store = FakeArtifactStore()
    private val record = build(modules = arrayOf(module(files = listOf(builtFile("ui.cdx.json"), builtFile("broken.cdx.json")))))

    @Test
    @DisplayName("an SBOM that is not valid JSON gives a finding naming it, and the build's other SBOMs are still read")
    fun malformed() {
        store.add(stored("ui.cdx.json"), lodash)
        val broken = stored("broken.cdx.json")
        store.add(broken, """{"components":[{"purl":""")

        val reading = SbomReader(store).read(record)

        assertEquals(lodashFound, reading.packages)
        assertEquals(listOf(SBOM_UNREADABLE to broken.fullPath), reading.findings.map { it.code to it.subject })
        assertEquals(
            1,
            reading.findings
                .single()
                .detail
                .lines()
                .size,
        )
    }

    @Test
    @DisplayName("an SBOM build-info lists that Artifactory does not have, or names without a sha1, gives a finding")
    fun notInArtifactory() {
        store.add(stored("ui.cdx.json"), lodash)
        val missing =
            build(
                modules =
                    arrayOf(
                        module(
                            files = listOf(builtFile("ui.cdx.json"), builtFile("gone.cdx.json"), builtFile("bare.cdx.json", sha1 = null)),
                        ),
                    ),
            )

        val reading = SbomReader(store).read(missing)

        assertEquals(lodashFound, reading.packages)
        assertEquals(
            listOf(
                Finding(SBOM_UNREADABLE, "gone.cdx.json", "not in Artifactory"),
                Finding(SBOM_UNREADABLE, "bare.cdx.json", "no sha1 in build-info"),
            ),
            reading.findings,
        )
    }

    @Test
    @DisplayName("Artifactory failing while an SBOM downloads fails the scan instead of dropping the SBOM")
    fun outageFails() {
        store.add(stored("ui.cdx.json"), lodash)
        store.add(stored("broken.cdx.json"))
        val failing =
            object : ArtifactStore by store {
                override fun read(file: StoredFile): ByteArray =
                    if (file.name == "broken.cdx.json") {
                        throw InventorySourceUnavailableException("Artifactory", "Artifactory did not answer")
                    } else {
                        store.read(file)
                    }
            }

        assertThrows<InventorySourceUnavailableException> { SbomReader(failing).read(record) }
    }

    @Test
    @DisplayName("a build's SBOMs are downloaded once, however many of its files are read")
    fun readOncePerBuild() {
        store.add(stored("ui.cdx.json"), lodash)
        store.add(stored("broken.cdx.json"), lodash)
        var reads = 0
        val counting =
            object : ArtifactStore by store {
                override fun read(file: StoredFile): ByteArray {
                    reads++
                    return store.read(file)
                }
            }
        val reader = SbomReader(counting)

        repeat(3) { reader.read(record) }

        assertEquals(2, reads)
    }
}
