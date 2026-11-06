package org.octopusden.octopus.license.target.model

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
