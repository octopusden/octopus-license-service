package org.octopusden.octopus.license.inventory.impl.reading.data

import org.octopusden.octopus.license.inventory.model.PackageId

/** One of our own archives a file depends on, to follow into its own build rather than list (D7). */
internal data class NestedArchive(
    val dependencyId: String,
    val group: String,
    val artifactId: String,
    val version: String,
    val type: String,
    val sha1: String?,
) {
    val id get() = PackageId.maven(group, artifactId, version)
}
