package org.octopusden.octopus.license.inventory.impl.port.data

/** [id] is the module id as recorded: `group:artifact:version` for Maven and Gradle modules. */
internal data class BuildModule(
    val id: String,
    val type: String?,
    val files: List<BuiltFile>,
    val dependencies: List<BuildDependency>,
)
