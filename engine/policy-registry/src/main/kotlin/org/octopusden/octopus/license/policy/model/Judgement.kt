package org.octopusden.octopus.license.policy.model

/**
 * @param licenses the canonical names the verdict was reached on, sorted, so every report spells a
 * license one way.
 * @param overridden the registry's override replaced the licenses the library was found with.
 */
data class Judgement(
    val verdict: LicenseVerdict,
    val licenses: List<String>,
    val overridden: Boolean,
)
