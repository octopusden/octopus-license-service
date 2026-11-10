package org.octopusden.octopus.license.inventory.model

/**
 * A library found inside released files, by any source.
 *
 * @param licenses as the sources name them, before any policy; empty when none did.
 * @param foundIn the entries of the released files it was found in.
 */
data class FoundLibrary(
    val id: PackageId,
    val licenses: Set<String>,
    val evidence: Set<Evidence>,
    val foundIn: Set<String>,
)
