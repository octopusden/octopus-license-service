package org.octopusden.octopus.license.inventory.impl.shared

import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.inventory.model.PackageId

/**
 * What is ours: one definition for every step that asks. The scan follows our own archives and
 * leaves our checksum-only files unnamed, and every library it finds carries the answer of
 * [isOurs], which evaluation reads instead of deciding it again (evaluation design, D2).
 */
internal class Ownership(
    private val rules: OwnershipRules,
) {
    /**
     * [copyInOwnRepository] is asked only for a checksum-only file not named after an internal
     * group: the one case where where the file is stored decides.
     */
    fun isOurs(
        id: PackageId,
        copyInOwnRepository: (sha256: String) -> Boolean,
    ) = when (id) {
        is PackageId.Purl -> id.mavenCoordinates()?.let { isInternalGroup(it.groupId) } ?: false
        is PackageId.ChecksumOnly -> isInternalFileName(id.fileName) || copyInOwnRepository(id.sha256)
        // The prototype reads the rendering, so a Maven id kept verbatim (an SBOM's, without a version) is ours by its group too.
        is PackageId.Unrecognized -> id.raw.startsWith(MAVEN_PREFIX) && isInternalGroup(id.raw.removePrefix(MAVEN_PREFIX))
    }

    /** [groupOrId] may be a whole dependency id: the group comes first. */
    fun isInternalGroup(groupOrId: String) = rules.internalGroups.any { groupOrId.startsWith(it) }

    /** Gradle distributions name a bundled jar `group.artifact-version.jar`, which names ours even outside our repositories. */
    fun isInternalFileName(fileName: String) = rules.internalGroups.any { fileName.startsWith("$it.") }

    fun isOwnRepository(repo: String) = repo.endsWith(LOCAL) && rules.ownRepositoryPrefixes.any { repo.startsWith(it) }

    private companion object {
        const val LOCAL = "local"
        const val MAVEN_PREFIX = "pkg:maven/"
    }
}
