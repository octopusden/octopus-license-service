package org.octopusden.octopus.license.inventory.impl.port

import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.PackageId

internal data class ScanPorts(
    val records: BuildRecords,
    val store: ArtifactStore,
    val scanner: BinaryScanner,
    val database: LicenseDatabase,
) {
    /**
     * The same ports, each answer kept for the rest of one scan: the same build, file and Xray scan
     * are asked for many times within a report, and never across reports. An answer of nothing
     * found is kept as well: asked again, Xray could have finished a scan the file was already judged
     * without. Not thread-safe; one scan runs on one thread.
     */
    fun memoized() =
        ScanPorts(
            records =
                object : BuildRecords {
                    private val builds = HashMap<Pair<String, String>, BuildRecord?>()
                    private val bySha1 = HashMap<String, List<BuildRef>>()

                    override fun find(
                        name: String,
                        number: String,
                    ) = builds.once(name to number) { records.find(name, number) }

                    override fun findByPublishedSha1(sha1: String) = bySha1.once(sha1) { records.findByPublishedSha1(sha1) }
                },
            store =
                object : ArtifactStore {
                    private val bySha1 = HashMap<String, StoredFile?>()
                    private val byPath = HashMap<Pair<String, String>, StoredFile?>()
                    private val bySha256 = HashMap<String, List<StoredFile>>()

                    override fun bySha1(sha1: String) = bySha1.once(sha1) { store.bySha1(sha1) }

                    override fun byPath(
                        path: String,
                        name: String,
                    ) = byPath.once(path to name) { store.byPath(path, name) }

                    override fun bySha256(sha256: String) = bySha256.once(sha256) { store.bySha256(sha256) }

                    override fun read(file: StoredFile) = store.read(file)
                },
            scanner =
                object : BinaryScanner {
                    private val scans = HashMap<String, Map<PackageId, Set<String>>?>()

                    override fun scan(file: StoredFile) = scans.once(file.fullPath) { scanner.scan(file) }
                },
            database = database,
        )
}

private fun <K, V> MutableMap<K, V>.once(
    key: K,
    ask: () -> V,
): V {
    if (!containsKey(key)) put(key, ask())
    @Suppress("UNCHECKED_CAST")
    return get(key) as V
}
