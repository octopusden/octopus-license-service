package org.octopusden.octopus.license.service.service.impl

import org.octopusden.octopus.license.files.model.LicenseFiles
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The license files as the download serves them: one zip folder per released file. */
internal object LicenseFilesZip {
    fun zip(files: LicenseFiles): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    files.folders.forEach { folder ->
                        folder.files.forEach { file ->
                            zip.putNextEntry(ZipEntry("${folder.name}/${file.name}"))
                            zip.write(file.content)
                            zip.closeEntry()
                        }
                    }
                }
            }.toByteArray()
}
