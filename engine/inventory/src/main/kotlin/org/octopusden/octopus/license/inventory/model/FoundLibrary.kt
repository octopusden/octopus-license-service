package org.octopusden.octopus.license.inventory.model

/**
 * A library found inside released files, by any source.
 *
 * @param licenses as the sources name them, before any policy; empty when none did.
 * @param foundIn the entries of the released files it was found in.
 * @param ours by an internal group, or, for a file known only by its checksum, by its name or a
 * copy stored in one of our repositories. A fact of where it comes from, not a judgement of its
 * license: the scan answers it because only the scan asks Artifactory.
 */
data class FoundLibrary(
    val id: PackageId,
    val licenses: Set<String>,
    val evidence: Set<Evidence>,
    val foundIn: Set<String>,
    val ours: Boolean,
)
