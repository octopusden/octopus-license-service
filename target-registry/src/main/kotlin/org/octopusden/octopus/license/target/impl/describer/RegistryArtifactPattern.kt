package org.octopusden.octopus.license.target.impl.describer

/** One entry of the registry's `maven-artifacts` answer, as stored: comma-separated patterns. */
internal data class RegistryArtifactPattern(
    val groupPattern: String?,
    val artifactPattern: String?,
)
