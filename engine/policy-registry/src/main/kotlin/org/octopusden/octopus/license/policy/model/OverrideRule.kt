package org.octopusden.octopus.license.policy.model

import org.octopusden.releng.versions.IVersionInfo
import org.octopusden.releng.versions.VersionRange

/**
 * One `thirdparty-licenses.properties` entry. [key] is the entry's key as written: the policy
 * orders matching entries by it, as the license plugins do (D5).
 */
internal data class OverrideRule(
    val key: String,
    val groupId: String,
    val artifactId: String,
    val versions: OverrideVersions,
    val licenses: Set<String>,
) {
    /** [numericVersion] is asked only for a range, and is null for a version `versions-api` cannot read. */
    fun matches(
        coordinates: MavenCoordinates,
        numericVersion: () -> IVersionInfo?,
    ) = coordinates.groupId == groupId &&
        coordinates.artifactId == artifactId &&
        when (versions) {
            is OverrideVersions.Exact -> coordinates.version == versions.version
            is OverrideVersions.InRange -> numericVersion()?.let { versions.range.containsVersion(it) } ?: false
        }
}

/**
 * The versions an override applies to (D6). A single version is compared as written: by its
 * numbers alone, `1.6R1` would also cover `1.7R2`, and an override written for one build would
 * allow another. A range is read by `versions-api`, as the license plugins read it.
 */
internal sealed interface OverrideVersions {
    data class Exact(
        val version: String,
    ) : OverrideVersions

    data class InRange(
        val range: VersionRange,
    ) : OverrideVersions
}
