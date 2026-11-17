package org.octopusden.octopus.license.inventory.impl.reading.source

import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.PackageId

/**
 * The components of every CycloneDX SBOM a build published, from any of its modules. A package in
 * several SBOMs keeps the last one's licenses (D10.8); an SBOM that cannot be fetched or parsed is
 * skipped (D11). One instance per scan: a build's SBOMs are read once, however many of its files
 * are released.
 */
internal class SbomReader(
    private val store: ArtifactStore,
) {
    private val byBuild = HashMap<BuildRef, Map<PackageId, Set<String>>>()

    fun read(build: BuildRecord): Map<PackageId, Set<String>> =
        byBuild.getOrPut(build.ref) {
            build.modules
                .flatMap { it.files }
                .filter { CycloneDxParser.isSbom(it.name) }
                .mapNotNull { file -> file.sha1?.takeIf { it.isNotEmpty() }?.let(store::bySha1) }
                .fold(emptyMap()) { found, file -> found + parsed(file) }
        }

    // Jackson's parse errors and a broken download are IOExceptions, so RuntimeException alone lets them through.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun parsed(file: StoredFile): Map<PackageId, Set<String>> =
        try {
            CycloneDxParser.parse(store.read(file))
        } catch (_: Exception) {
            emptyMap()
        }
}
