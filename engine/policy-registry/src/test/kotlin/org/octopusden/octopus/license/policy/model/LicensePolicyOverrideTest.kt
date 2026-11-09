package org.octopusden.octopus.license.policy.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.policy.impl.parser.policy
import org.octopusden.octopus.license.policy.impl.parser.registryFiles

class LicensePolicyOverrideTest {
    private fun overriding(overrides: String) = policy(registryFiles(overrides = overrides))

    private fun sampleLib(version: String) = MavenCoordinates("org.example", "sample-lib", version)

    @Test
    @DisplayName("an override whose range holds the version replaces the licenses found")
    fun inRange() {
        assertEquals(
            Judgement(LicenseVerdict.ALLOWED, listOf("MIT"), overridden = true),
            overriding(RANGE_TO_MIT).judge(setOf("GPL-3.0"), sampleLib("1.5")),
        )
    }

    @Test
    @DisplayName("an override whose range does not hold the version leaves the licenses found")
    fun outsideRange() {
        assertEquals(
            Judgement(LicenseVerdict.NOT_ALLOWED, listOf("GPL-3.0"), overridden = false),
            overriding(RANGE_TO_MIT).judge(setOf("GPL-3.0"), sampleLib("2.1")),
        )
    }

    @Test
    @DisplayName("an override with a single version applies to that version only")
    fun singleVersion() {
        val policy = overriding("org.example--sample-lib--1.5=MIT")
        assertEquals(true, policy.judge(setOf("GPL-3.0"), sampleLib("1.5")).overridden)
        assertEquals(false, policy.judge(setOf("GPL-3.0"), sampleLib("1.5.1")).overridden)
    }

    @Test
    @DisplayName("an override value with | gives several licenses, each made canonical")
    fun severalLicenses() {
        assertEquals(
            listOf("Apache-2.0", "MIT"),
            overriding("org.example--sample-lib--1.5=The MIT License|Apache 2").judge(setOf("GPL-3.0"), sampleLib("1.5")).licenses,
        )
    }

    @Test
    @DisplayName("of two matching overrides the greater key wins, as the plugin's last match in sorted order does")
    fun greaterKeyWins() {
        val policy =
            overriding(
                """
                org.example--sample-lib--[1.5,1.6)=Apache-2.0
                org.example--sample-lib--[1.0,2.0)=MIT
                """.trimIndent(),
            )
        assertEquals(listOf("Apache-2.0"), policy.judge(setOf("GPL-3.0"), sampleLib("1.5")).licenses)
    }

    @Test
    @DisplayName("an override with an empty value is ignored, as the plugin ignores it")
    fun emptyValue() {
        val judgement = overriding("org.example--sample-lib--1.5=").judge(setOf("GPL-3.0"), sampleLib("1.5"))
        assertEquals(Judgement(LicenseVerdict.NOT_ALLOWED, listOf("GPL-3.0"), overridden = false), judgement)
    }

    @Test
    @DisplayName("a library judged without Maven coordinates is never overridden")
    fun noCoordinates() {
        assertEquals(false, overriding(RANGE_TO_MIT).judge(setOf("GPL-3.0")).overridden)
    }

    @Test
    @DisplayName("an override for another artifact of the same group does not apply")
    fun otherArtifact() {
        val coordinates = MavenCoordinates("org.example", "other-lib", "1.5")
        assertEquals(false, overriding(RANGE_TO_MIT).judge(setOf("GPL-3.0"), coordinates).overridden)
    }

    @Test
    @DisplayName("an override key is read as Java properties read it, escapes included")
    fun escapedKey() {
        val coordinates = MavenCoordinates("org.example", "sample lib", "1.5")
        assertEquals(true, overriding("org.example--sample\\ lib--1.5=MIT").judge(setOf("GPL-3.0"), coordinates).overridden)
    }

    private companion object {
        const val RANGE_TO_MIT = "org.example--sample-lib--[1.0,2.0)=MIT"
    }
}
