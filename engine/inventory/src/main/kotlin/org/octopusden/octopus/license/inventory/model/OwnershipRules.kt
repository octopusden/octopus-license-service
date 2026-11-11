package org.octopusden.octopus.license.inventory.model

/**
 * What is ours, from configuration: company names stay out of this public repository.
 *
 * @param internalGroups Maven group prefixes, `com.example` matching `com.example.app`.
 * @param ownRepositoryPrefixes repository name prefixes; a repository is ours when its name also ends with `local`.
 */
data class OwnershipRules(
    val internalGroups: List<String>,
    val ownRepositoryPrefixes: List<String>,
)
