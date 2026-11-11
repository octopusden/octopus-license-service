package org.octopusden.octopus.license.inventory.impl.port

import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.PackageId

/** What a binary scanner recognises inside a stored file, by package, with the licenses it gives each. */
internal interface BinaryScanner {
    /** Null when the scanner has no scan of the file (D12); may throw [InventorySourceUnavailableException]. */
    fun scan(file: StoredFile): Map<PackageId, Set<String>>?
}
