package org.octopusden.octopus.license.policy.impl.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.policy.exception.InvalidLicenseRegistryException
import org.octopusden.octopus.license.policy.model.LicenseVerdict
import org.octopusden.octopus.license.policy.model.MavenCoordinates

class LicenseRegistryParserTest {
    @Test
    @DisplayName("the policy records the commit its files were read at")
    fun commit() {
        assertEquals(COMMIT, policy().commit)
    }

    @Test
    @DisplayName("a registry without licenses-whitelist.txt is invalid")
    fun noWhitelist() {
        assertThrows<InvalidLicenseRegistryException> { policy(registryFiles(whitelist = null)) }
    }

    @Test
    @DisplayName("a whitelist naming no license is invalid: it would fail every library (D4)")
    fun emptyWhitelist() {
        assertThrows<InvalidLicenseRegistryException> { policy(registryFiles(whitelist = " | \n")) }
    }

    @Test
    @DisplayName("whitelist names are |-separated across lines and trimmed; # lines are comments")
    fun whitelistFormat() {
        val policy = policy(registryFiles(whitelist = "# allowed\n Apache-2.0 | MIT |\nISC", merges = ""))
        listOf("Apache-2.0", "MIT", "ISC").forEach {
            assertEquals(LicenseVerdict.ALLOWED, policy.judge(setOf(it)).verdict, it)
        }
        assertTrue(policy.problems.isEmpty())
    }

    @Test
    @DisplayName("a registry without licenses-hidden.txt allows the whitelist only")
    fun noHiddenList() {
        assertEquals(LicenseVerdict.NOT_ALLOWED, policy(registryFiles(hidden = null)).judge(setOf("BORLAND")).verdict)
    }

    @ParameterizedTest
    @ValueSource(strings = ["licenses-hidden.txt", "merges.txt", "thirdparty-licenses.properties", "licenses.properties"])
    @DisplayName("a missing registry file other than the whitelist is read as empty, and is a problem (D4)")
    fun missingFile(file: String) {
        val policy = policy(registryFiles() - file)
        assertEquals(listOf("$file is missing at $COMMIT; read as empty"), policy.problems)
    }

    @Test
    @DisplayName("a name on two merges.txt lines keeps the first line and is a problem")
    fun nameOnTwoMergeLines() {
        val policy = policy(registryFiles(merges = "Apache-2.0|Apache 2\nISC|Apache 2"))
        assertEquals(listOf("Apache-2.0"), policy.judge(setOf("Apache 2")).licenses)
        assertEquals(1, policy.problems.size)
    }

    @Test
    @DisplayName("an override key without three -- parts is a problem, and the other overrides apply")
    fun overrideKeyWithoutVersion() {
        val policy = policy(registryFiles(overrides = "org.example--sample-lib=MIT\norg.example--other-lib--1.0=MIT"))
        assertEquals(1, policy.problems.size)
        assertTrue(policy.judge(setOf("GPL-3.0"), MavenCoordinates("org.example", "other-lib", "1.0")).overridden)
    }

    @Test
    @DisplayName("an override range versions-api cannot parse is a problem, and the load succeeds (D6)")
    fun unparseableRange() {
        val policy = policy(registryFiles(overrides = "org.example--sample-lib--[1.0,=MIT"))
        assertEquals(1, policy.problems.size)
        assertEquals(false, policy.judge(setOf("GPL-3.0"), MavenCoordinates("org.example", "sample-lib", "1.0")).overridden)
    }

    @Test
    @DisplayName("an override with an empty value is ignored and is not a problem")
    fun emptyOverrideIsNoProblem() {
        assertTrue(policy(registryFiles(overrides = "org.example--sample-lib--1.5=")).problems.isEmpty())
    }

    @Test
    @DisplayName("a library version versions-api cannot read is not overridden, rather than failing the judgement")
    fun unreadableLibraryVersion() {
        val policy = policy(registryFiles(overrides = "org.example--sample-lib--[1.0,2.0)=MIT"))
        val coordinates = MavenCoordinates("org.example", "sample-lib", "not a version!")
        assertEquals(false, policy.judge(setOf("GPL-3.0"), coordinates).overridden)
    }
}
