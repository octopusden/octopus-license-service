package org.octopusden.octopus.license.files.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.octopusden.octopus.license.files.APACHE_TEXT
import org.octopusden.octopus.license.files.GWT_URL
import org.octopusden.octopus.license.files.MIT_TEXT
import org.octopusden.octopus.license.files.model.MissingText
import org.octopusden.octopus.license.files.model.MissingTextReason
import org.octopusden.octopus.license.files.policy

class LicenseTextFilesTest {
    private val sameFileName =
        policy(
            texts =
                """
                Foo/Bar=licenses/MIT.txt
                Foo_Bar=licenses/MIT.txt
                Baz/Qux=licenses/MIT.txt
                Baz_Qux=https://example.org/terms/MIT.txt
                """.trimIndent(),
        )

    /** Expected names are what the plugin's `getLicenseFileName` gives for the same registry line. */
    @ParameterizedTest(name = "{0} = {1} -> {2}")
    @CsvSource(
        delimiter = '|',
        value = [
            "Apache-2.0 | licenses/APACHE-2.0.txt | apache-2.0 - apache-2.0.txt",
            "CC0 | licenses/cc0.html | cc0 - cc0.html",
            "GWT | http://www.gwtproject.org/terms.html | gwt - terms.html",
            "oswego-concurrent | http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/sun-u.c.license.pdf | " +
                "oswego-concurrent - sun-u.c.license.pdf",
            "GPL/LGPL | licenses/GPL | gpl_lgpl - gpl.txt",
            "Short | licenses/short.c | short - short.c.txt",
            "Bouncy Castle Licence | licenses/Bouncy-Castle.txt | bouncy castle licence - bouncy-castle.txt",
            "Raw | https://bitbucket.example/rest/api/raw/licenses%2FRAW.txt/raw?ref=master | raw - raw.txt",
            "Query | https://example.org/terms?lang=en | query - terms.txt",
        ],
    )
    @DisplayName("named as the plugin names a downloaded license (D6)")
    fun fileName(
        license: String,
        location: String,
        expected: String,
    ) {
        assertEquals(expected, LicenseTextFiles.fileName(license, location))
    }

    @Test
    @DisplayName("a registry text is written as stored, under its plugin name")
    fun registryText() {
        val texts = LicenseTextFiles.texts(setOf("Apache-2.0"), policy(), emptyMap())
        assertEquals(listOf("apache-2.0 - apache-2.0.txt" to APACHE_TEXT), texts.files.map { it.name to String(it.content) })
        assertEquals(emptyList<MissingText>(), texts.missing)
    }

    @Test
    @DisplayName("a downloaded text is written as its bytes")
    fun downloaded() {
        val bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x00, -1)
        val texts = LicenseTextFiles.texts(setOf("GWT"), policy(), mapOf(GWT_URL to bytes))
        assertEquals(
            bytes.toList(),
            texts.files
                .single()
                .content
                .toList(),
        )
    }

    @Test
    @DisplayName("a URL text that was not downloaded is missing, with its URL")
    fun downloadFailed() {
        val texts = LicenseTextFiles.texts(setOf("GWT"), policy(), emptyMap())
        assertEquals(emptyList<String>(), texts.files.map { it.name })
        assertEquals(listOf(MissingText("GWT", MissingTextReason.DOWNLOAD_FAILED, GWT_URL)), texts.missing)
    }

    @Test
    @DisplayName("a license licenses.properties does not name is missing")
    fun noEntry() {
        assertEquals(
            listOf(MissingText("SSPL-1.0", MissingTextReason.NO_REGISTRY_ENTRY)),
            LicenseTextFiles.texts(setOf("SSPL-1.0"), policy(), emptyMap()).missing,
        )
    }

    @Test
    @DisplayName("texts are ordered by file name, so a folder lists the same way every time")
    fun ordered() {
        val texts = LicenseTextFiles.texts(setOf("MIT", "CC0", "Apache-2.0"), policy(), emptyMap())
        assertEquals(listOf("apache-2.0 - apache-2.0.txt", "cc0 - cc0.html", "mit - mit.txt"), texts.files.map { it.name })
    }

    @Test
    @DisplayName("two licenses whose texts get one file name and the same text: one file (review)")
    fun sameNameSameText() {
        val texts = LicenseTextFiles.texts(setOf("Foo_Bar", "Foo/Bar"), sameFileName, emptyMap())
        assertEquals(listOf("foo_bar - mit.txt"), texts.files.map { it.name })
    }

    @Test
    @DisplayName("two licenses whose texts get one file name but differ: the later name gets -2 before the extension (review)")
    fun sameNameOtherText() {
        val texts =
            LicenseTextFiles.texts(
                setOf("Baz_Qux", "Baz/Qux"),
                sameFileName,
                mapOf("https://example.org/terms/MIT.txt" to "other terms".toByteArray()),
            )
        assertEquals(
            listOf("baz_qux - mit-2.txt" to "other terms", "baz_qux - mit.txt" to MIT_TEXT),
            texts.files.map { it.name to String(it.content) },
        )
    }
}
