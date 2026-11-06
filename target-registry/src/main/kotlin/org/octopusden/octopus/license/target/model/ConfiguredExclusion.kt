package org.octopusden.octopus.license.target.model

/** A component excluded by configuration rather than by a registry fact; matched on the exact component id. */
data class ConfiguredExclusion(
    val component: String,
    val reason: String,
)
