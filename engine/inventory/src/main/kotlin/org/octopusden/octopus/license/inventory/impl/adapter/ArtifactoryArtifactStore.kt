package org.octopusden.octopus.license.inventory.impl.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.infrastructure.artifactory.client.ArtifactoryClient
import org.octopusden.octopus.infrastructure.artifactory.client.downloadArtifactTo
import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.port.StoredFile
import java.io.ByteArrayOutputStream

/** Stored files, found by AQL; [RepositoryPreference] picks among copies. */
internal class ArtifactoryArtifactStore(
    private val client: ArtifactoryClient,
    private val calls: JfrogCalls,
) : ArtifactStore {
    override fun bySha1(sha1: String) = RepositoryPreference.preferred(items("""{"actual_sha1":${json(sha1)}}""", WITH_CHECKSUMS))

    override fun byPath(
        path: String,
        name: String,
    ) = RepositoryPreference.preferred(items("""{"path":${json(path)},"name":${json(name)}}""", WITH_CHECKSUMS))

    override fun bySha256(sha256: String) = items("""{"sha256":${json(sha256)}}""", """"repo","path","name"""")

    override fun read(file: StoredFile): ByteArray =
        calls.call(JfrogCalls.ARTIFACTORY, "file ${file.fullPath}") {
            ByteArrayOutputStream().also { client.downloadArtifactTo(file.fullPath, it) }.toByteArray()
        }

    private fun items(
        criteria: String,
        include: String,
    ): List<StoredFile> =
        calls
            .call(JfrogCalls.ARTIFACTORY, "items $criteria") { client.searchByAQL("items.find($criteria).include($include)") }
            .results
            .mapNotNull { item ->
                val repo = item.repo ?: return@mapNotNull null
                StoredFile(
                    repo = repo,
                    path = item.path.orEmpty(),
                    name = item.name.orEmpty(),
                    sha1 = item.actualSha1,
                    sha256 = item.sha256,
                    properties = item.properties
                        .orEmpty()
                        .mapNotNull { p -> p.key?.let { it to p.value.orEmpty() } }
                        .toMap(),
                )
            }

    private companion object {
        const val WITH_CHECKSUMS = """"repo","path","name","actual_sha1","sha256","property""""
        private val mapper = ObjectMapper()

        fun json(value: String): String = mapper.writeValueAsString(value)
    }
}
