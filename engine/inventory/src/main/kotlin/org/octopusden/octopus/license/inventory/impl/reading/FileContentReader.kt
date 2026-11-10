package org.octopusden.octopus.license.inventory.impl.reading

import org.octopusden.octopus.license.inventory.impl.listing.BuildLocator
import org.octopusden.octopus.license.inventory.impl.listing.ListedFile
import org.octopusden.octopus.license.inventory.impl.naming.PackageTable
import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.port.BinaryScanner
import org.octopusden.octopus.license.inventory.impl.port.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.StoredFile
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.inventory.model.PackageId

/** How much of a released file was read, and what that noticed. */
internal data class FileReading(
    val status: FileStatus,
    val evidence: Set<Evidence>,
    val findings: List<Finding>,
)

/**
 * Step B (D6, D7) for one located file: whether it is read, then every source it has, in the
 * prototype's order, because later steps read earlier results: an inner archive's library check
 * and the ship proof both read Xray's list. Our own archives are followed into their builds,
 * recursively, and what they hold counts for the released file.
 */
internal class FileContentReader(
    private val scanner: BinaryScanner,
    private val store: ArtifactStore,
    private val locator: BuildLocator,
    private val dependencyReader: BuildDependencyReader,
    private val sbomReader: SbomReader,
) {
    fun read(
        file: ListedFile,
        conventionBuild: String,
        table: PackageTable,
    ): FileReading {
        val stored = requireNotNull(file.stored) { "${file.entry} was not located" }
        val judgement =
            FileStatusJudge.judge(file.entry, file.kind, stored.name, scanner.scan(stored) != null, file.build != null, conventionBuild)
        if (judgement.status != FileStatus.READ) return FileReading(judgement.status, judgement.evidence, judgement.findings)
        val findings = judgement.findings.toMutableList()
        Visit(file.entry, table, findings).collect(stored, file.build, file.ownId, depth = 0)
        return FileReading(judgement.status, judgement.evidence, findings.distinct())
    }

    /** One released file's walk: where its packages go, and which nested builds it has followed. */
    private inner class Visit(
        private val foundIn: String,
        private val table: PackageTable,
        private val findings: MutableList<Finding>,
    ) {
        private val visited = mutableSetOf<Triple<String, String, String>>()

        fun collect(
            file: StoredFile,
            build: BuildRecord?,
            ownId: PackageId?,
            depth: Int,
        ) {
            val scanned = scanner.scan(file).orEmpty()
            scanned.forEach { (id, licenses) -> table.add(id, licenses, Evidence.BINARY_SCAN, foundIn) }
            if (build == null) return
            // An inner archive with no library is data (a model zip): its build recorded the tools that made it, not what it holds.
            if (depth > 0 && scanned.keys.none { it != ownId && type(it) != DOCKER }) {
                findings += Finding(FindingCode.NO_LIBRARIES_IN_FILE, file.name, "inner archive without libraries, build-info ignored")
                return
            }
            sbomReader.read(build).forEach { (id, licenses) -> table.add(id, licenses, Evidence.SBOM, foundIn) }
            val reading = dependencyReader.read(build, file, scanned.keys)
            reading.packages.forEach { table.add(it, emptySet(), Evidence.BUILD_RECORD, foundIn) }
            findings += reading.findings
            reading.nested.forEach { follow(it, depth) }
        }

        private fun follow(
            archive: NestedArchive,
            depth: Int,
        ) {
            // By coordinates first: a dependency resolved by several configurations can carry its sources' checksum.
            val inner =
                store.byPath("${archive.group.replace('.', '/')}/${archive.artifactId}/${archive.version}", archive.fileName())
                    ?: archive.sha1?.takeIf { it.isNotEmpty() }?.let(store::bySha1)
            val build = inner?.let(locator::locate)?.first
            if (inner == null || build == null) {
                findings += Finding(FindingCode.UNRESOLVED_INTERNAL_ARCHIVE, archive.dependencyId, "covered by the Xray scan only")
                return
            }
            val key = Triple(build.ref.name, build.ref.number, archive.dependencyId)
            if (key in visited || depth > MAX_FOLLOWING_DEPTH) return
            visited += key
            collect(inner, build, PackageId.Purl(PackageId.MAVEN, "${archive.group}/${archive.artifactId}", archive.version), depth + 1)
        }
    }

    private fun NestedArchive.fileName() = "$artifactId-$version.$type"

    private fun type(id: PackageId) =
        when (id) {
            is PackageId.Purl -> id.type
            is PackageId.ChecksumOnly -> GENERIC
            is PackageId.Unrecognized -> id.raw.substringBefore("://")
        }

    private companion object {
        /** Archives are followed from files at this depth or less, so the deepest one read is one deeper (D7). */
        const val MAX_FOLLOWING_DEPTH = 10
        const val DOCKER = "docker"
        const val GENERIC = "generic"
    }
}
