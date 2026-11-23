package org.octopusden.octopus.license.inventory.impl.reading.source

import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.BuiltFile
import org.octopusden.octopus.license.inventory.impl.reading.data.SbomReading
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode

/**
 * The components of every CycloneDX SBOM a build published, from any of its modules. A package in
 * several SBOMs keeps the last one's licenses (D10.8). An SBOM that is not in Artifactory or cannot
 * be parsed is skipped with finding `SBOM_UNREADABLE`; Artifactory failing to answer fails the scan,
 * as everywhere else (D11, D13). One instance per scan: a build's SBOMs are read once, however many
 * of its files are released.
 */
internal class SbomReader(
    private val store: ArtifactStore,
) {
    private val byBuild = HashMap<BuildRef, SbomReading>()

    fun read(build: BuildRecord): SbomReading =
        byBuild.getOrPut(build.ref) {
            build.modules
                .flatMap { it.files }
                .filter { CycloneDxParser.isSbom(it.name) }
                .map(::read)
                .fold(SbomReading(emptyMap(), emptyList())) { found, sbom ->
                    SbomReading(found.packages + sbom.packages, found.findings + sbom.findings)
                }
        }

    // Jackson's parse errors are IOExceptions, a bad purl an IllegalArgumentException: any of them is the SBOM's own fault.
    @Suppress("TooGenericExceptionCaught")
    private fun read(sbom: BuiltFile): SbomReading {
        val stored =
            sbom.sha1?.takeIf { it.isNotEmpty() }?.let(store::bySha1)
                ?: return unreadable(sbom.name, if (sbom.sha1.isNullOrEmpty()) "no sha1 in build-info" else "not in Artifactory")
        val content = store.read(stored)
        return try {
            SbomReading(CycloneDxParser.parse(content), emptyList())
        } catch (e: Exception) {
            unreadable(stored.fullPath, e.message?.lineSequence()?.first() ?: e.javaClass.simpleName)
        }
    }

    private fun unreadable(
        subject: String,
        detail: String,
    ) = SbomReading(emptyMap(), listOf(Finding(FindingCode.SBOM_UNREADABLE, subject, detail)))
}
