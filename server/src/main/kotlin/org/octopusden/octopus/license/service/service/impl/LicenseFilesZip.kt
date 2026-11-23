package org.octopusden.octopus.license.service.service.impl

import org.octopusden.octopus.license.files.model.LicenseFiles
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The license files as the download serves them: `NOT-CHECKED.txt` at the top, then one folder per released file. */
internal object LicenseFilesZip {
    fun zip(files: LicenseFiles): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    files.notChecked?.let { zip.add(it.name, it.content) }
                    files.folders.forEach { folder ->
                        folder.files.forEach { file -> zip.add("${folder.name}/${file.name}", file.content) }
                    }
                }
            }.toByteArray()

    private fun ZipOutputStream.add(
        name: String,
        content: ByteArray,
    ) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }
}
