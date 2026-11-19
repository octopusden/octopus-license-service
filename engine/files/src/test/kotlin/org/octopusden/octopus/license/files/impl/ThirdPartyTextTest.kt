package org.octopusden.octopus.license.files.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.octopusden.octopus.license.files.COMMONS_IO
import org.octopusden.octopus.license.files.FOO_CORE
import org.octopusden.octopus.license.files.SLF4J
import org.octopusden.octopus.license.files.lib

/**
 * The expected texts are what the plugin's `third-party-file.ftl` renders for the same libraries,
 * with the name and URL changed as Decision 4 says.
 */
class ThirdPartyTextTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "pkg:maven/commons-io/commons-io@2.11.0 | commons-io (commons-io:commons-io:2.11.0)",
            "pkg:npm/react@18.2.0 | react (pkg:npm/react@18.2.0)",
            "pkg:npm/%40babel/core@7.24.0 | %40babel/core (pkg:npm/%40babel/core@7.24.0)",
            "generic://sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/blob.dll | " +
                "blob.dll (generic://sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/blob.dll)",
            "docker://sample/base:1 | docker://sample/base:1 (docker://sample/base:1)",
        ],
    )
    @DisplayName("name and id per kind of package id, with no URL (D4)")
    fun nameAndId(
        id: String,
        expected: String,
    ) {
        assertEquals(expected, NoticeLine.nameAndId(lib(id).library.id))
    }

    @Test
    @DisplayName("several licenses are written one parenthesis each, in the assessment's order")
    fun twoLicenses() {
        assertEquals(
            "     (EPL-2.0) (LGPL-2.1) commons-io (commons-io:commons-io:2.11.0)",
            NoticeLine.of(lib(COMMONS_IO, listOf("EPL-2.0", "LGPL-2.1"))),
        )
    }

    @Test
    @DisplayName("no license is written as the plugin's Unknown license")
    fun noLicense() {
        assertEquals("     (Unknown license) foo-core (com.example:foo-core:1.0)", NoticeLine.of(lib(FOO_CORE, emptyList())))
    }

    @Test
    @DisplayName("the whole file is the template's layout: an empty first line, the count, one line per library")
    fun wholeFile() {
        val text = ThirdPartyText.render(listOf(lib(FOO_CORE, emptyList()), lib(COMMONS_IO), lib(SLF4J, listOf("MIT"))))
        assertEquals(
            "\n" +
                "Lists of 3 third-party dependencies.\n" +
                "     (Unknown license) foo-core (com.example:foo-core:1.0)\n" +
                "     (Apache-2.0) commons-io (commons-io:commons-io:2.11.0)\n" +
                "     (MIT) slf4j-api (org.slf4j:slf4j-api:1.7.36)\n",
            text,
        )
    }

    @Test
    @DisplayName("no library: the template's sentence for a module without dependencies")
    fun empty() {
        assertEquals("\nThe project has no dependencies.\n", ThirdPartyText.render(emptyList()))
    }
}
