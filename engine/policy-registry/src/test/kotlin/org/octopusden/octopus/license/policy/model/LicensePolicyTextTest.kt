package org.octopusden.octopus.license.policy.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.policy.impl.parser.policy
import org.octopusden.octopus.license.policy.impl.parser.registryFiles

class LicensePolicyTextTest {
    @Test
    @DisplayName("a licenses/ entry gives the text from the registry")
    fun inRegistry() {
        assertEquals(
            LicenseText.InRegistry("licenses/APACHE-2.0.txt", "Apache License text"),
            policy().text("Apache-2.0"),
        )
    }

    @Test
    @DisplayName("an http entry gives the URL, kept for the license files to fetch")
    fun atUrl() {
        val policy = policy(registryFiles(whitelist = "H2-1.0", texts = "H2-1.0=http://www.example.org/h2-license.html"))
        assertEquals(LicenseText.AtUrl("http://www.example.org/h2-license.html"), policy.text("H2-1.0"))
        assertTrue(policy.licensesWithoutText.isEmpty())
    }

    @Test
    @DisplayName("a text is found from a synonym in another casing, through its canonical name (D5)")
    fun throughCanonicalName() {
        assertEquals("licenses/APACHE-2.0.txt", (policy().text("apache 2") as LicenseText.InRegistry).path)
    }

    @Test
    @DisplayName("a license with no entry has no text")
    fun noEntry() {
        assertNull(policy().text("MIT"))
    }

    @Test
    @DisplayName("a whitelisted license with no entry is a license without text; a hidden one is not")
    fun licensesWithoutText() {
        assertEquals(listOf("MIT"), policy().licensesWithoutText)
    }

    @Test
    @DisplayName("an entry pointing at a file not in licenses/ is a problem, and its license has no text")
    fun missingFile() {
        val policy = policy(registryFiles(texts = "Apache-2.0=licenses/APACHE-2.0.txt\nMIT=licenses/MIT.txt"))
        assertEquals(1, policy.problems.size)
        assertEquals(listOf("MIT"), policy.licensesWithoutText)
    }

    @Test
    @DisplayName("an entry that is neither a licenses/ file nor a URL is a problem")
    fun otherValue() {
        val policy = policy(registryFiles(texts = "Apache-2.0=licenses/APACHE-2.0.txt\nMIT=see the website"))
        assertEquals(1, policy.problems.size)
        assertNull(policy.text("MIT"))
    }

    @Test
    @DisplayName("a whitelisted synonym is covered by its canonical license's text")
    fun whitelistedSynonym() {
        val policy = policy(registryFiles(whitelist = "Apache-2.0|Apache 2|MIT", texts = "Apache-2.0=licenses/APACHE-2.0.txt"))
        assertEquals(listOf("MIT"), policy.licensesWithoutText)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "CDDL-1.1=licenses/CDDL-1.1.txt\nCDDL-1.1-GPL-2.0-CPE=licenses/CDDL-GPL-1.1.txt",
            "CDDL-1.1-GPL-2.0-CPE=licenses/CDDL-GPL-1.1.txt\nCDDL-1.1=licenses/CDDL-1.1.txt",
        ],
    )
    @DisplayName("the entry under the license's own name wins over a synonym's, which is a problem")
    fun ownEntryWinsOverSynonym(texts: String) {
        val policy = policy(cddlRegistry(texts))
        assertEquals(LicenseText.InRegistry("licenses/CDDL-1.1.txt", "CDDL"), policy.text("CDDL-1.1"))
        assertEquals(1, policy.problems.size)
    }

    @Test
    @DisplayName("of two synonyms' entries, the one with the smaller key wins, and the other is a problem")
    fun twoSynonymEntries() {
        val policy = policy(cddlRegistry("CDDL/GPLv2+CE=licenses/CDDL-1.1.txt\nCDDL-1.1-GPL-2.0-CPE=licenses/CDDL-GPL-1.1.txt"))
        assertEquals(LicenseText.InRegistry("licenses/CDDL-GPL-1.1.txt", "CDDL with GPL"), policy.text("CDDL-1.1"))
        assertEquals(1, policy.problems.size)
    }

    private fun cddlRegistry(texts: String) =
        registryFiles(
            whitelist = "CDDL-1.1",
            merges = "CDDL-1.1|CDDL/GPLv2+CE|CDDL-1.1-GPL-2.0-CPE",
            texts = texts,
            textFiles = mapOf("licenses/CDDL-1.1.txt" to "CDDL", "licenses/CDDL-GPL-1.1.txt" to "CDDL with GPL"),
        )
}
