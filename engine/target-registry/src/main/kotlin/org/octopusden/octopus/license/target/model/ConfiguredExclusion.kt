package org.octopusden.octopus.license.target.model

/** Components excluded by configuration rather than by a registry fact, all for one reason; matched on the exact component id. */
data class ConfiguredExclusion(
    val components: List<String>,
    val reason: String,
)
