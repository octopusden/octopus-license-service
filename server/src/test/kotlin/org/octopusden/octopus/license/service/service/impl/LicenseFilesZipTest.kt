package org.octopusden.octopus.license.service.service.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.files.model.FileFolder
import org.octopusden.octopus.license.files.model.LicenseFiles
import org.octopusden.octopus.license.files.model.NamedFile
import java.util.zip.ZipInputStream

class LicenseFilesZipTest {
    private fun entries(zip: ByteArray): List<Pair<String, String>> =
        ZipInputStream(zip.inputStream()).use { input ->
            generateSequence { input.nextEntry }.map { it.name to String(input.readAllBytes()) }.toList()
        }

    @Test
    @DisplayName("each file is an entry under its folder, in the writer's order")
    fun entries() {
        val files =
            LicenseFiles(
                listOf(
                    FileFolder(
                        "sample-app-1.4.0.war",
                        "g:a:war",
                        listOf(NamedFile("THIRD-PARTY.txt", "a".toByteArray()), NamedFile("mit - mit.txt", "m".toByteArray())),
                    ),
                    FileFolder("sample_ui_1.4.0", "sample/ui:1.4.0", listOf(NamedFile("THIRD-PARTY.txt", "b".toByteArray()))),
                ),
                emptyList(),
            )

        assertEquals(
            listOf(
                "sample-app-1.4.0.war/THIRD-PARTY.txt" to "a",
                "sample-app-1.4.0.war/mit - mit.txt" to "m",
                "sample_ui_1.4.0/THIRD-PARTY.txt" to "b",
            ),
            entries(LicenseFilesZip.zip(files)),
        )
    }

    @Test
    @DisplayName("no folder: an empty zip, still a valid one")
    fun empty() {
        assertEquals(emptyList<Pair<String, String>>(), entries(LicenseFilesZip.zip(LicenseFiles(emptyList(), emptyList()))))
    }

    @Test
    @DisplayName("NOT-CHECKED.txt sits at the top of the zip, before the folders")
    fun notCheckedAtTop() {
        val files =
            LicenseFiles(
                listOf(FileFolder("sample-app-1.4.0.war", "g:a:war", listOf(NamedFile("THIRD-PARTY.txt", "a".toByteArray())))),
                emptyList(),
                NamedFile("NOT-CHECKED.txt", "n".toByteArray()),
            )

        assertEquals(
            listOf("NOT-CHECKED.txt" to "n", "sample-app-1.4.0.war/THIRD-PARTY.txt" to "a"),
            entries(LicenseFilesZip.zip(files)),
        )
    }
}
