package org.octopusden.octopus.license.target

data class ComponentVersion(
    val component: String,
    val version: String,
)

/**
 * What the components registry declares a component version releases. It is the input of the
 * license scan, never a wire type: it carries more than callers are shown, and it says nothing
 * about which concrete files exist in Artifactory.
 *
 * @param artifactVersions the versions released files may carry: the requested version, then the
 * registry's build version when it differs.
 */
data class LicenseTarget(
    val componentVersion: ComponentVersion,
    val artifactVersions: List<String>,
    val buildSystem: String,
    val labels: Set<String>,
    val distribution: DistributionSpec,
)

/** The registry's `GAV` and `docker` fields and the Maven artifact patterns in force for the version. */
data class DistributionSpec(
    val declared: List<DeclaredArtifact>,
    val patterns: List<ArtifactPattern>,
)

sealed interface DeclaredArtifact {
    data class MavenArtifact(
        val groupId: String,
        val artifactId: String,
        val extension: String,
        val classifier: String? = null,
    ) : DeclaredArtifact

    data class DockerImage(
        val image: String,
        val tag: String? = null,
    ) : DeclaredArtifact

    /** A registry entry that names no Maven artifact or docker image; kept so the scan can report it (D7). */
    data class Unsupported(
        val entry: String,
        val reason: String,
    ) : DeclaredArtifact
}

/**
 * Group and artifact patterns as the registry stores them: Java regular expressions, kept as
 * strings because matching them against build output is the scan's job (D6).
 */
data class ArtifactPattern(
    val groups: List<String>,
    val artifacts: List<String>,
)

/** A component excluded by configuration rather than by a registry fact; matched on the exact component id. */
data class ConfiguredExclusion(
    val component: String,
    val reason: String,
)

sealed interface TargetResolution {
    data class Resolved(
        val target: LicenseTarget,
    ) : TargetResolution

    data class Excluded(
        val componentVersion: ComponentVersion,
        val reason: String,
    ) : TargetResolution
}
