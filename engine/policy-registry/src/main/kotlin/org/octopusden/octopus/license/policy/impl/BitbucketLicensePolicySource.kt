package org.octopusden.octopus.license.policy.impl

import feign.FeignException
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClassicClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClientParametersProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.exception.BitbucketClientException
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.policy.exception.LicenseRegistryUnavailableException
import org.octopusden.octopus.license.policy.impl.parser.LicenseRegistryParser
import org.octopusden.octopus.license.policy.model.LicensePolicy

/**
 * Reads the license registry through Bitbucket's REST API and nothing else (D2): no `git`, no disk.
 *
 * The published client reads raw files at the default branch's head only, so a load checks the
 * head before and after it reads, and starts over when it moved (D3).
 *
 * Temporary, and must be replaced: it ties the service to Bitbucket and to a token of its own. The
 * replacement reads through octopus-vcs-facade, pinned to one commit, once the facade can list and
 * read files (TD-001, `docs/tech-debt/001-read-license-registry-through-vcs-facade.md`).
 */
class BitbucketLicensePolicySource(
    bitbucketUrl: String,
    token: String,
    private val project: String,
    private val repository: String,
) : LicensePolicySource {
    private val client: BitbucketClient =
        BitbucketClassicClient(
            object : BitbucketClientParametersProvider {
                override fun getApiUrl() = bitbucketUrl

                override fun getAuth() = BitbucketBearerTokenCredentialProvider(token)
            },
        )

    override fun headCommit(): String =
        askingBitbucket {
            client
                .getCommits(project, repository, mapOf("limit" to 1))
                .values
                .first()
                .id
        }

    override fun load(): LicensePolicy {
        repeat(MAX_TRIES) {
            val commit = headCommit()
            val files = askingBitbucket { readFiles(commit) }
            if (headCommit() == commit) {
                return LicenseRegistryParser.parse(files, commit)
            }
        }
        throw LicenseRegistryUnavailableException("The license registry moved during $MAX_TRIES loads in a row")
    }

    private fun readFiles(commit: String): Map<String, String> =
        listFiles(commit)
            .filter { it in ROOT_FILES || it.startsWith(TEXTS_DIRECTORY) }
            .associateWith { client.getRepositoryRawFileContent(project, repository, it).value }

    private fun listFiles(commit: String): List<String> {
        val paths = mutableListOf<String>()
        var start: Int? = 0
        while (start != null) {
            val page = client.getRepositoryFiles(project, repository, mapOf("at" to commit, "start" to start, "limit" to PAGE))
            paths += page.values
            start = if (page.isLastPage) null else page.nextPageStart
        }
        return paths
    }

    private fun <T> askingBitbucket(call: () -> T): T =
        try {
            call()
        } catch (e: FeignException) {
            throw unavailable(e)
        } catch (e: BitbucketClientException) {
            throw unavailable(e)
        } catch (e: IllegalStateException) {
            // The client's error decoder answers IllegalStateException for any error it cannot name.
            throw unavailable(e)
        }

    private fun unavailable(cause: Exception) =
        LicenseRegistryUnavailableException("The license registry cannot be read from Bitbucket: ${cause.message}", cause)

    private companion object {
        const val MAX_TRIES = 3
        const val PAGE = 1000
        const val TEXTS_DIRECTORY = "licenses/"
        val ROOT_FILES =
            setOf(
                "licenses-whitelist.txt",
                "licenses-hidden.txt",
                "merges.txt",
                "thirdparty-licenses.properties",
                "licenses.properties",
            )
    }
}
