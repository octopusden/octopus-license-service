package org.octopusden.octopus.license.inventory.impl

import org.octopusden.octopus.license.inventory.impl.adapter.xray.XrayComponentIds
import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.port.BinaryScanner
import org.octopusden.octopus.license.inventory.impl.port.BuildRecords
import org.octopusden.octopus.license.inventory.impl.port.LicenseDatabase
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.PackageId

/** Builds by name and number; a published file's sha1 finds every build that lists it. */
internal class FakeBuildRecords(
    vararg records: BuildRecord,
) : BuildRecords {
    private val records = records.toMutableList()
    val asked = mutableListOf<String>()

    fun add(record: BuildRecord) = records.add(record)

    override fun find(
        name: String,
        number: String,
    ): BuildRecord? {
        asked += "$name/$number"
        return records.firstOrNull { it.ref == BuildRef(name, number) }
    }

    override fun findByPublishedSha1(sha1: String) =
        records.filter { record -> record.modules.any { module -> module.files.any { it.sha1 == sha1 } } }.map { it.ref }
}

/** Files are returned in the order stored; the first copy found is the preferred one. */
internal class FakeArtifactStore(
    vararg files: StoredFile,
) : ArtifactStore {
    private val files = files.toMutableList()
    private val contents = mutableMapOf<StoredFile, ByteArray>()
    val asked = mutableListOf<String>()

    fun add(
        file: StoredFile,
        content: String? = null,
    ) {
        files += file
        content?.let { contents[file] = it.toByteArray() }
    }

    override fun bySha1(sha1: String): StoredFile? {
        asked += "sha1:$sha1"
        return files.firstOrNull { it.sha1 == sha1 }
    }

    override fun byPath(
        path: String,
        name: String,
    ): StoredFile? {
        asked += "path:$path/$name"
        return files.firstOrNull { it.path == path && it.name == name }
    }

    override fun bySha256(sha256: String): List<StoredFile> {
        asked += "sha256:$sha256"
        return files.filter { it.sha256 == sha256 }
    }

    override fun read(file: StoredFile) = contents[file] ?: error("no content for ${file.fullPath}")
}

/** A stored file with no scan is not indexed; packages are given by Xray id, as the adapter receives them. */
internal class FakeBinaryScanner : BinaryScanner {
    private val scans = mutableMapOf<String, Map<PackageId, Set<String>>>()
    val asked = mutableListOf<String>()

    fun scanned(
        file: StoredFile,
        vararg packages: Pair<String, Set<String>>,
    ) {
        scans[file.fullPath] = packages.associate { (id, licenses) -> XrayComponentIds.toPackageId(id) to licenses }
    }

    override fun scan(file: StoredFile): Map<PackageId, Set<String>>? {
        asked += file.fullPath
        return scans[file.fullPath]
    }
}

internal class FakeLicenseDatabase(
    private val licenses: Map<String, Set<String>> = emptyMap(),
) : LicenseDatabase {
    val asked = mutableListOf<List<String>>()

    override fun licensesOf(packages: List<PackageId.Purl>): Map<PackageId.Purl, Set<String>> {
        asked += packages.map { it.toString() }
        return packages.mapNotNull { purl -> licenses[purl.toString()]?.let { purl to it } }.toMap()
    }
}
