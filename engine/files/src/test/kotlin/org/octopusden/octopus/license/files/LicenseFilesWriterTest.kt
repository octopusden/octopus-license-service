package org.octopusden.octopus.license.files

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.files.model.FileFolder
import org.octopusden.octopus.license.files.model.MissingText
import org.octopusden.octopus.license.files.model.MissingTextReason
import org.octopusden.octopus.license.inventory.model.FileStatus

class LicenseFilesWriterTest {
    private val writer = LicenseFilesWriter()

    private val workedExample =
        assessment(
            listOf(file(APP), file(IMAGE)),
            listOf(
                lib(COMMONS_IO),
                lib(SLF4J, listOf("MIT"), foundIn = setOf(APP, IMAGE)),
                lib(FOO_CORE, emptyList()),
                lib(REACT, listOf("MIT"), foundIn = setOf(IMAGE)),
                lib(OWN_LIB, emptyList(), LibraryVerdict.INTERNAL),
            ),
        )

    private fun FileFolder.text(name: String) = String(files.single { it.name == name }.content)

    @Test
    @DisplayName("the design's worked example: two folders, each with its notice, summary and texts")
    fun workedExample() {
        val files = writer.write(workedExample, policy(), emptyMap())
        assertEquals(
            listOf(
                "payments-app-1.4.0.jar" to listOf("THIRD-PARTY.txt", "licenses.xml", "apache-2.0 - apache-2.0.txt", "mit - mit.txt"),
                "payments_ui_1.4.0" to listOf("THIRD-PARTY.txt", "licenses.xml", "mit - mit.txt"),
            ),
            files.folders.map { folder -> folder.name to folder.files.map { it.name } },
        )
        assertEquals(
            "\n" +
                "Lists of 3 third-party dependencies.\n" +
                "     (Unknown license) foo-core (com.example:foo-core:1.0)\n" +
                "     (Apache-2.0) commons-io (commons-io:commons-io:2.11.0)\n" +
                "     (MIT) slf4j-api (org.slf4j:slf4j-api:1.7.36)\n",
            files.folders.first().text("THIRD-PARTY.txt"),
        )
        assertEquals(listOf(APP, IMAGE), files.folders.map { it.releasedFile })
        assertEquals(emptyList<MissingText>(), files.missingTexts)
    }

    @Test
    @DisplayName("only the URLs of licenses a listed library uses are downloaded, never a registry path")
    fun textsToDownload() {
        val assessment =
            assessment(
                listOf(file()),
                listOf(lib(COMMONS_IO, listOf("Apache-2.0", "GWT")), lib(OWN_LIB, listOf("CC0"), LibraryVerdict.INTERNAL)),
            )
        assertEquals(setOf(GWT_URL), writer.textsToDownload(assessment, policy()))
    }

    @Test
    @DisplayName("a library in a file that was not read needs no text")
    fun notReadNeedsNothing() {
        val assessment =
            assessment(
                listOf(file(IMAGE, FileStatus.NOT_FOUND)),
                listOf(lib(REACT, listOf("GWT"), foundIn = setOf(IMAGE))),
            )
        assertEquals(emptySet<String>(), writer.textsToDownload(assessment, policy()))
    }

    @Test
    @DisplayName("a text missing in two folders is listed once, in license order")
    fun missingOnce() {
        val assessment =
            assessment(
                listOf(file(APP), file(IMAGE)),
                listOf(lib(COMMONS_IO, listOf("SSPL-1.0", "GWT"), LibraryVerdict.NOT_ALLOWED, setOf(APP, IMAGE))),
            )
        assertEquals(
            listOf(
                MissingText("GWT", MissingTextReason.DOWNLOAD_FAILED, GWT_URL),
                MissingText("SSPL-1.0", MissingTextReason.NO_REGISTRY_ENTRY),
            ),
            writer.write(assessment, policy(), emptyMap()).missingTexts,
        )
    }

    @Test
    @DisplayName("a downloaded text is written in every folder that uses it")
    fun downloadedEverywhere() {
        val assessment = assessment(listOf(file(APP), file(IMAGE)), listOf(lib(COMMONS_IO, listOf("GWT"), foundIn = setOf(APP, IMAGE))))
        val files = writer.write(assessment, policy(), mapOf(GWT_URL to "terms".toByteArray()))
        assertEquals(listOf("terms", "terms"), files.folders.map { it.text("gwt - terms.html") })
    }
}
