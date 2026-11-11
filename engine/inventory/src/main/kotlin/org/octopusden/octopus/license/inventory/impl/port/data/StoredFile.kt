package org.octopusden.octopus.license.inventory.impl.port.data

/** A file stored in Artifactory: [path] is its folder in [repo]. */
internal data class StoredFile(
    val repo: String,
    val path: String,
    val name: String,
    val sha1: String?,
    val sha256: String?,
    val properties: Map<String, String> = emptyMap(),
) {
    val fullPath get() = "$repo/$path/$name"
}
