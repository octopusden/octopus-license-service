package org.octopusden.octopus.license.inventory.impl.adapter.artifactory

import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile

/** Which stored copy of a file is read when Artifactory holds several: a released copy over a cached one. */
internal object RepositoryPreference {
    private const val RELEASE = "release"
    private const val LOCAL = "local"

    fun preferred(copies: List<StoredFile>): StoredFile? = copies.minWithOrNull(compareBy({ rank(it.repo) }, { it.repo }))

    private fun rank(repo: String) =
        when {
            RELEASE in repo && repo.endsWith(LOCAL) -> 0
            repo.endsWith(LOCAL) -> 1
            else -> 2
        }
}
