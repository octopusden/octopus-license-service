package org.octopusden.octopus.license.inventory.impl.port

import org.octopusden.octopus.license.inventory.model.BuildRef

/** A build's build-info, in this module's terms: what each module published and depended on. */
internal data class BuildRecord(
    val ref: BuildRef,
    val agent: String?,
    val modules: List<BuildModule>,
)

/** [id] is the module id as recorded: `group:artifact:version` for Maven and Gradle modules. */
internal data class BuildModule(
    val id: String,
    val type: String?,
    val files: List<BuiltFile>,
    val dependencies: List<BuildDependency>,
)

/** [path] includes the file name; build-info records it for docker images only. */
internal data class BuiltFile(
    val name: String,
    val sha1: String?,
    val path: String? = null,
)

internal data class BuildDependency(
    val id: String,
    val type: String?,
    val sha1: String?,
    val scopes: List<String>,
)
