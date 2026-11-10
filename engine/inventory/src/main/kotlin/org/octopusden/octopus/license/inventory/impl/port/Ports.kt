package org.octopusden.octopus.license.inventory.impl.port

import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.PackageId

/** Build-info. Every method may throw [InventorySourceUnavailableException]. */
internal interface BuildRecords {
    /** Null when the build is missing or its build-info cannot be read: no build, never a failure. */
    fun find(
        name: String,
        number: String,
    ): BuildRecord?

    fun findByPublishedSha1(sha1: String): List<BuildRef>
}

/** Stored files. Every method may throw [InventorySourceUnavailableException]. */
internal interface ArtifactStore {
    /** The preferred copy, when several repositories hold the file. */
    fun bySha1(sha1: String): StoredFile?

    /** The preferred copy at [path] (a folder) named [name]. */
    fun byPath(
        path: String,
        name: String,
    ): StoredFile?

    /** Every copy, in the order Artifactory answers. */
    fun bySha256(sha256: String): List<StoredFile>

    fun read(file: StoredFile): ByteArray
}

/** What a binary scanner recognises inside a stored file, by package, with the licenses it gives each. */
internal interface BinaryScanner {
    /** Null when the scanner has no scan of the file (D12); may throw [InventorySourceUnavailableException]. */
    fun scan(file: StoredFile): Map<PackageId, Set<String>>?
}

/** Licenses of packages by their coordinates. */
internal interface LicenseDatabase {
    /** Packages it knows nothing about, or whose lookup failed, are absent from the answer (D9). */
    fun licensesOf(packages: List<PackageId.Purl>): Map<PackageId.Purl, Set<String>>
}
