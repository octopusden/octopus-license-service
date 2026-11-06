package org.octopusden.octopus.license.target

import org.octopusden.octopus.license.target.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.DeclaredArtifact.MavenArtifact
import org.octopusden.octopus.license.target.DeclaredArtifact.Unsupported

/**
 * Reads the registry's `GAV` and `docker` distribution fields. Never fails: an entry it cannot
 * read becomes [Unsupported], so one bad entry does not cost the whole version its target (D7).
 */
internal object DistributionParser {
    private val URI_PREFIXES = listOf("file:", "http:", "https:")

    fun parse(
        gav: String?,
        docker: String?,
    ): List<DeclaredArtifact> = entries(gav).map(::mavenArtifact) + entries(docker).map(::dockerImage)

    private fun entries(field: String?) =
        field
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun mavenArtifact(entry: String): DeclaredArtifact {
        if (URI_PREFIXES.any { entry.startsWith(it, ignoreCase = true) }) {
            return Unsupported(entry, "a file share or URL, not a Maven artifact")
        }
        val parts = entry.split(':')
        return when (parts.size) {
            3 -> MavenArtifact(parts[0], parts[1], parts[2])
            4 -> MavenArtifact(parts[0], parts[1], parts[2], parts[3])
            else -> Unsupported(entry, "expected group:artifact:extension[:classifier]")
        }
    }

    // The tag follows the last ':' after the last '/'; an earlier ':' is a registry port.
    private fun dockerImage(entry: String): DockerImage {
        val tagSeparator = entry.lastIndexOf(':').takeIf { it > entry.lastIndexOf('/') }
        return tagSeparator?.let { DockerImage(entry.substring(0, it), entry.substring(it + 1)) } ?: DockerImage(entry)
    }
}
