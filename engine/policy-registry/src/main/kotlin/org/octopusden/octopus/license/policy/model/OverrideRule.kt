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
    val versions: VersionRange,
    val licenses: Set<String>,
) {
    fun matches(
        coordinates: MavenCoordinates,
        version: IVersionInfo,
    ) = coordinates.groupId == groupId && coordinates.artifactId == artifactId && versions.containsVersion(version)
}
