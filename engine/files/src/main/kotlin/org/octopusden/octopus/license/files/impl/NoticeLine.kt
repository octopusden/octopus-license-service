package org.octopusden.octopus.license.files.impl

import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.inventory.model.PackageId

/**
 * One library's line in `THIRD-PARTY.txt`, as the plugin's template writes it, with the artifactId
 * for the POM's name and no URL: the scan reads neither (D4).
 */
internal object NoticeLine {
    const val UNKNOWN_LICENSE = "Unknown license"
    private const val INDENT = "    "

    fun of(library: AssessedLibrary): String {
        val licenses = library.licenses.ifEmpty { listOf(UNKNOWN_LICENSE) }.joinToString("") { " ($it)" }
        return "$INDENT$licenses ${nameAndId(library.library.id)}"
    }

    fun nameAndId(id: PackageId): String =
        when (id) {
            is PackageId.Purl ->
                id.mavenCoordinates()?.let { "${it.artifactId} (${it.groupId}:${it.artifactId}:${it.version})" }
                    ?: "${id.name} ($id)"
            is PackageId.ChecksumOnly -> "${id.fileName} ($id)"
            is PackageId.Unrecognized -> "${id.raw} ($id)"
        }
}
