package org.octopusden.octopus.license.target.model

/**
 * A version range key of the registry's `maven-artifacts` answer that could not be parsed. Its
 * patterns are left out of the target, and it is kept so the scan can report it, the way an
 * unreadable `GAV` entry is kept as [DeclaredArtifact.Unsupported] (D7).
 */
data class UnreadableRange(
    val range: String,
    val reason: String,
)
