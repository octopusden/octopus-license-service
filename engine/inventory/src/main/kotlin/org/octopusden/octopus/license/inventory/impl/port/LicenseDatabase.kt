package org.octopusden.octopus.license.inventory.impl.port

import org.octopusden.octopus.license.inventory.model.PackageId

/** Licenses of packages by their coordinates. */
internal interface LicenseDatabase {
    /** Packages it knows nothing about, or whose lookup failed, are absent from the answer (D9). */
    fun licensesOf(packages: List<PackageId.Purl>): Map<PackageId.Purl, Set<String>>
}
