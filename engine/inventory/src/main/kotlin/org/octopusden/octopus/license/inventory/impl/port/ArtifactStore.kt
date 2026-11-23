package org.octopusden.octopus.license.inventory.impl.port

import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile

/** Stored files. Every method may throw [InventorySourceUnavailableException]. */
internal interface ArtifactStore {
    /** The preferred copy, when several repositories hold the file. */
    fun bySha1(sha1: String): StoredFile?

    /** The preferred copy at [path] (a folder) named [name]. */
    fun byPath(
        path: String,
        name: String,
    ): StoredFile?

    /** The preferred copy of a Maven file, at the repository layout `g/a/v/a-v[-classifier].extension`. */
    fun byCoordinates(
        groupId: String,
        artifactId: String,
        version: String,
        extension: String,
        classifier: String? = null,
    ): StoredFile? =
        byPath(
            "${groupId.replace('.', '/')}/$artifactId/$version",
            "$artifactId-$version" + classifier?.let { "-$it" }.orEmpty() + ".$extension",
        )

    /** Every copy, in the order Artifactory answers. */
    fun bySha256(sha256: String): List<StoredFile>

    fun read(file: StoredFile): ByteArray
}
