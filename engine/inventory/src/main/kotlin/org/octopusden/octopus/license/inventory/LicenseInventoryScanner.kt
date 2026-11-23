package org.octopusden.octopus.license.inventory

import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.target.model.LicenseTarget
import java.time.Duration

/** Answers which files a license target releases and which packages, with which licenses, are in them. */
interface LicenseInventoryScanner {
    /**
     * @param xrayWait how long, in all, this scan waits for Xray to finish scanning its files: none for a quick
     * check of what Xray knows now, minutes for a report that must be complete (D12).
     * @throws InventorySourceUnavailableException Artifactory or Xray could not be asked.
     */
    fun scan(
        target: LicenseTarget,
        xrayWait: Duration,
    ): LicenseInventory
}
