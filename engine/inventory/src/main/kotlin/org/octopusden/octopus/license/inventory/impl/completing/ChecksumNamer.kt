package org.octopusden.octopus.license.inventory.impl.completing

import org.octopusden.octopus.license.inventory.impl.LibraryTable
import org.octopusden.octopus.license.inventory.impl.Ownership
import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.model.PackageId

/**
 * Names the files Xray knew only by checksum (D8). A renamed copy of a Maven Central jar still sits
 * in a remote cache under its coordinates; that copy names it. Our own files are left as they are:
 * evaluation calls them internal.
 */
internal class ChecksumNamer(
    private val store: ArtifactStore,
    private val ownership: Ownership,
) {
    fun name(table: LibraryTable) {
        table.ids.filterIsInstance<PackageId.ChecksumOnly>().forEach { unnamed ->
            if (ownership.isInternalFileName(unnamed.fileName)) return@forEach
            val copies = store.bySha256(unnamed.sha256)
            if (copies.any { ownership.isOwnRepository(it.repo) }) return@forEach
            copies
                .firstNotNullOfOrNull { copy ->
                    val parts = copy.path.split('/')
                    if (parts.size >= 3 && copy.name.startsWith("${parts[parts.size - 2]}-${parts.last()}")) {
                        PackageId.maven(parts.dropLast(2).joinToString("."), parts[parts.size - 2], parts.last())
                    } else {
                        null
                    }
                }?.let { table.rename(unnamed, it) }
        }
    }
}
