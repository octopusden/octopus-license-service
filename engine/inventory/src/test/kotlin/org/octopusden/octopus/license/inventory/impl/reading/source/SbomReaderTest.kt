package org.octopusden.octopus.license.inventory.impl.reading.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.FakeArtifactStore
import org.octopusden.octopus.license.inventory.impl.build
import org.octopusden.octopus.license.inventory.impl.builtFile
import org.octopusden.octopus.license.inventory.impl.module
import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.impl.stored
import org.octopusden.octopus.license.inventory.model.PackageId
import java.io.IOException

class SbomReaderTest {
    private val lodash = """{"components":[{"purl":"pkg:npm/lodash@4.17.21","licenses":[{"license":{"id":"MIT"}}]}]}"""
    private val store = FakeArtifactStore()
    private val record = build(modules = arrayOf(module(files = listOf(builtFile("ui.cdx.json"), builtFile("broken.cdx.json")))))

    @Test
    @DisplayName("an SBOM that is not valid JSON is skipped, and the build's other SBOMs are still read")
    fun malformedSkipped() {
        store.add(stored("ui.cdx.json"), lodash)
        store.add(stored("broken.cdx.json"), """{"components":[{"purl":""")

        assertEquals(mapOf(PackageId.parse("pkg:npm/lodash@4.17.21") to setOf("MIT")), SbomReader(store).read(record))
    }

    @Test
    @DisplayName("an SBOM whose download breaks off is skipped, and the build's other SBOMs are still read")
    fun brokenDownloadSkipped() {
        store.add(stored("ui.cdx.json"), lodash)
        store.add(stored("broken.cdx.json"))
        val breaking =
            object : ArtifactStore by store {
                override fun read(file: StoredFile): ByteArray =
                    if (file.name == "broken.cdx.json") throw IOException("connection reset") else store.read(file)
            }

        assertEquals(mapOf(PackageId.parse("pkg:npm/lodash@4.17.21") to setOf("MIT")), SbomReader(breaking).read(record))
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
