package org.octopusden.octopus.license.inventory.impl.port.data

/** [path] includes the file name; build-info records it for docker images only. */
internal data class BuiltFile(
    val name: String,
    val sha1: String?,
    val path: String? = null,
)
