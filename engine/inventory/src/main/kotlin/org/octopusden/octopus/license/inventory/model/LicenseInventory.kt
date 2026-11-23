package org.octopusden.octopus.license.inventory.model

import org.octopusden.octopus.license.target.model.ComponentVersion

/**
 * Every file a component version releases and every library found inside them, with the licenses
 * their sources name. Nothing here is judged: whether a library is ours, counted or allowed is
 * evaluation's question; so the libraries include ours and each file's own coordinates.
 *
 * @param builds the convention builds found, in lookup order.
 * @param releasedFiles what the version releases: every listed file, read or not, in listing order (D5).
 * @param libraries what was found inside [releasedFiles], merged across them, sorted by rendered id.
 * @param findings each once, in the order found.
 */
data class LicenseInventory(
    val componentVersion: ComponentVersion,
    val builds: List<BuildRef>,
    val releasedFiles: List<ReleasedFile>,
    val libraries: List<FoundLibrary>,
    val findings: List<Finding>,
)
