package org.octopusden.octopus.license.inventory.impl

import org.octopusden.octopus.license.inventory.model.OwnershipRules

/**
 * What is ours: one definition for every step that asks. The scan follows our own archives and
 * leaves our checksum-only files unnamed; evaluation will call our packages internal.
 */
internal class Ownership(
    private val rules: OwnershipRules,
) {
    /** [groupOrId] may be a whole dependency id: the group comes first. */
    fun isInternalGroup(groupOrId: String) = rules.internalGroups.any { groupOrId.startsWith(it) }

    /** Gradle distributions name a bundled jar `group.artifact-version.jar`, which names ours even outside our repositories. */
    fun isInternalFileName(fileName: String) = rules.internalGroups.any { fileName.startsWith("$it.") }

    fun isOwnRepository(repo: String) = repo.endsWith(LOCAL) && rules.ownRepositoryPrefixes.any { repo.startsWith(it) }

    private companion object {
        const val LOCAL = "local"
    }
}
