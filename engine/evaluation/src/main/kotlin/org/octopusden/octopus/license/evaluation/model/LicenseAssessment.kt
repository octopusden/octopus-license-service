package org.octopusden.octopus.license.evaluation.model

import org.octopusden.octopus.license.inventory.model.LicenseInventory

/**
 * A license inventory with a verdict on every library that counts. Grading and the license files
 * read it whole, so the inventory travels with it unchanged.
 *
 * @param libraries the counted libraries, in the inventory's order: image layers, OS packages and
 * base-image Python are not among them (D3).
 */
data class LicenseAssessment(
    val inventory: LicenseInventory,
    val libraries: List<AssessedLibrary>,
    val skipped: SkippedPackages,
)
