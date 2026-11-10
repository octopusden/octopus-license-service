package org.octopusden.octopus.license.inventory.model

import org.octopusden.octopus.license.target.model.ComponentVersion

/**
 * Every file a component version releases and every package found in them, with the licenses
 * their sources name. Nothing here is judged: whether a package is ours, counted or allowed is
 * evaluation's question.
 *
 * @param builds the convention builds found, in lookup order.
 * @param files every listed file, read or not, in listing order (D5).
 * @param packages merged across files, sorted by rendered id.
 * @param findings each once, in the order found.
 */
data class LicenseInventory(
    val componentVersion: ComponentVersion,
    val builds: List<BuildRef>,
    val files: List<ReleasedFile>,
    val packages: List<FoundPackage>,
    val findings: List<Finding>,
)
