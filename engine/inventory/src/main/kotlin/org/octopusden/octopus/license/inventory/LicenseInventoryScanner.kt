package org.octopusden.octopus.license.inventory

import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.target.model.LicenseTarget

/** Answers which files a license target releases and which packages, with which licenses, are in them. */
interface LicenseInventoryScanner {
    /** @throws InventorySourceUnavailableException Artifactory or Xray could not be asked. */
    fun scan(target: LicenseTarget): LicenseInventory
}
