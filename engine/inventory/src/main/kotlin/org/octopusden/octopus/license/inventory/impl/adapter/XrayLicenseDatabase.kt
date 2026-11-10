package org.octopusden.octopus.license.inventory.impl.adapter

import org.octopusden.octopus.infrastructure.xray.client.XrayClient
import org.octopusden.octopus.infrastructure.xray.client.dto.ComponentDetail
import org.octopusden.octopus.infrastructure.xray.client.dto.ComponentSummaryRequest
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.impl.port.LicenseDatabase
import org.octopusden.octopus.license.inventory.model.PackageId

/** Licenses of Maven and npm packages from Xray's component summary, [BATCH] per call (D9). */
internal class XrayLicenseDatabase(
    private val client: XrayClient,
    private val calls: JfrogCalls,
) : LicenseDatabase {
    override fun licensesOf(packages: List<PackageId.Purl>): Map<PackageId.Purl, Set<String>> {
        val byXrayId = packages.mapNotNull { purl -> XrayComponentIds.toXrayId(purl)?.let { it to purl } }.toMap()
        return byXrayId.keys
            .chunked(BATCH)
            .flatMap { batch -> licenses(batch).mapNotNull { (id, licenses) -> byXrayId[id]?.let { it to licenses } } }
            .toMap()
    }

    // A batch that fails leaves its packages without licenses, as the prototype leaves them (D9).
    @Suppress("SwallowedException")
    private fun licenses(batch: List<String>): List<Pair<String, Set<String>>> {
        val byBareId = batch.associateBy(XrayComponentIds::bare)
        val summary =
            try {
                calls.call(JfrogCalls.XRAY, "licenses of ${batch.size} components") {
                    client.getComponentSummary(ComponentSummaryRequest(batch.map(::ComponentDetail)))
                }
            } catch (_: InventorySourceUnavailableException) {
                return emptyList()
            }
        return summary.artifacts.mapNotNull { artifact ->
            byBareId[XrayComponentIds.bare(artifact.general?.componentId.orEmpty())]?.let { id ->
                id to
                    artifact.licenses.map { it.name }.toSet()
            }
        }
    }

    private companion object {
        const val BATCH = 100
    }
}
