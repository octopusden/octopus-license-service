package org.octopusden.octopus.license.inventory.impl.completing

import org.octopusden.octopus.license.inventory.impl.LibraryTable
import org.octopusden.octopus.license.inventory.impl.port.LicenseDatabase
import org.octopusden.octopus.license.inventory.model.PackageId

/** Gives the Maven and npm packages no source named a license for theirs from the license database (D9). */
internal class LicenseFiller(
    private val database: LicenseDatabase,
) {
    fun fill(table: LibraryTable) {
        val missing =
            table
                .withoutLicenses()
                .filterIsInstance<PackageId.Purl>()
                .filter { it.type in ASKED_TYPES }
                .sortedBy { it.toString() }
        if (missing.isEmpty()) return
        database.licensesOf(missing).forEach { (purl, licenses) -> table.addLicenses(purl, licenses) }
    }

    private companion object {
        val ASKED_TYPES = setOf(PackageId.MAVEN, "npm")
    }
}
