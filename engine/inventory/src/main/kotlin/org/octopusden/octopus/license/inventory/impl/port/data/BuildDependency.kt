package org.octopusden.octopus.license.inventory.impl.port.data

internal data class BuildDependency(
    val id: String,
    val type: String?,
    val sha1: String?,
    val scopes: List<String>,
)
