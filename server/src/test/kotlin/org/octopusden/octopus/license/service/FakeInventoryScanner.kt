package org.octopusden.octopus.license.service

import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.target.model.LicenseTarget

/**
 * Stands in for Artifactory and Xray: every component holds [libraries], except
 * [UNAVAILABLE_COMPONENT], whose scan cannot reach Xray.
 */
class FakeInventoryScanner(
    private val libraries: List<FoundLibrary> = emptyList(),
) : LicenseInventoryScanner {
    override fun scan(target: LicenseTarget): LicenseInventory {
        if (target.componentVersion.component == UNAVAILABLE_COMPONENT) {
            throw InventorySourceUnavailableException("Xray", "Xray answered 503 after 3 tries")
        }
        return LicenseInventory(target.componentVersion, emptyList(), emptyList(), libraries, emptyList())
    }

    companion object {
        const val UNAVAILABLE_COMPONENT = "xray-down-component"
    }
}
