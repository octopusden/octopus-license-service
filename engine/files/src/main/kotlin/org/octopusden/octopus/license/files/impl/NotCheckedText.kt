package org.octopusden.octopus.license.files.impl

import org.octopusden.octopus.license.files.model.NamedFile
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.ReleasedFile

/**
 * The selected released files that were not read, each with why. A plain file holds no libraries,
 * so it was checked; a file that was not selected is not released.
 */
internal object NotCheckedText {
    const val FILE_NAME = "NOT-CHECKED.txt"

    private val reasons =
        mapOf(
            FileStatus.NOT_READABLE to "not readable, neither Xray nor a build record could see inside it",
            FileStatus.NOT_FOUND to "not found in Artifactory",
            FileStatus.UNSUPPORTED to "not looked up, the components registry names it in a form the service cannot look up",
        )

    fun render(releasedFiles: List<ReleasedFile>): NamedFile? {
        val lines = releasedFiles.mapNotNull { file -> reasons[file.status]?.let { "  ${file.entry}: $it\n" } }
        if (lines.isEmpty()) return null
        return NamedFile(
            FILE_NAME,
            ("These released files were not checked for third-party libraries:\n" + lines.joinToString("")).toByteArray(),
        )
    }
}
