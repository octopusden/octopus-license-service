package org.octopusden.octopus.license.evaluation.model

import org.octopusden.octopus.license.inventory.model.FoundLibrary

/**
 * @param licenses the license policy's canonical names, for every verdict: the policy is asked
 * about our libraries and unnamed files too, as the prototype computes their licenses (D4).
 * @param overridden the license registry's override replaced the licenses found.
 */
data class AssessedLibrary(
    val library: FoundLibrary,
    val verdict: LibraryVerdict,
    val licenses: List<String>,
    val overridden: Boolean,
) {
    val thirdParty get() = verdict != LibraryVerdict.INTERNAL
}
