package org.octopusden.octopus.license.target.model

/** The registry's `GAV` and `docker` fields and the Maven artifact patterns in force for the version. */
data class DistributionSpec(
    val declared: List<DeclaredArtifact>,
    val patterns: List<ArtifactPattern>,
)
