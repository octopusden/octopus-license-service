package org.octopusden.octopus.license.target.model

/**
 * Group and artifact patterns as the registry stores them: Java regular expressions, kept as
 * strings because matching them against build output is the scan's job.
 */
data class ArtifactPattern(
    val groups: List<String>,
    val artifacts: List<String>,
)
