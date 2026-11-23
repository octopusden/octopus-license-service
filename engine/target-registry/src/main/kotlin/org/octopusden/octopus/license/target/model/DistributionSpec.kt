package org.octopusden.octopus.license.target.model

/**
 * The registry's `GAV` and `docker` fields and the Maven artifact patterns in force for the version.
 * [unreadableRanges] are pattern entries whose range could not be parsed, so it is unknown whether
 * they are in force.
 */
data class DistributionSpec(
    val declared: List<DeclaredArtifact>,
    val patterns: List<ArtifactPattern>,
    val unreadableRanges: List<UnreadableRange> = emptyList(),
)
