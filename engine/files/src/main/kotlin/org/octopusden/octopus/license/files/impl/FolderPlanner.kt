package org.octopusden.octopus.license.files.impl

import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.ReleasedFile

/**
 * Which released files get a folder, under which name, and which libraries each lists (D2, D3).
 * A file that was not read has no folder: it has no libraries, and trust already says why.
 */
internal object FolderPlanner {
    fun plan(assessment: LicenseAssessment): List<PlannedFolder> {
        val thirdParty = assessment.libraries.filter { it.verdict != LibraryVerdict.INTERNAL }
        val taken = mutableSetOf<String>()
        return assessment.inventory.releasedFiles
            .filter { it.status == FileStatus.READ }
            .map { file ->
                val base = baseName(file)
                // A suffixed name can be another file's own name, so every candidate is checked.
                val name = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in taken }
                taken += name
                PlannedFolder(
                    name = name,
                    releasedFile = file.entry,
                    libraries = thirdParty.filter { file.entry in it.library.foundIn },
                )
            }
    }

    // An image's path ends in its manifest, which would name every image's folder the same.
    private fun baseName(file: ReleasedFile) =
        when (file.kind) {
            FileKind.DOCKER -> null
            FileKind.MAVEN, FileKind.FILE_SHARE -> file.path?.substringAfterLast('/')
        } ?: file.entry.replace('/', '_').replace(':', '_')
}

internal data class PlannedFolder(
    val name: String,
    val releasedFile: String,
    val libraries: List<AssessedLibrary>,
)
