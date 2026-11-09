package org.octopusden.octopus.license.policy.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.policy.impl.parser.policy
import org.octopusden.octopus.license.policy.impl.parser.registryFiles

class LicensePolicyJudgeTest {
    @Test
    @DisplayName("a whitelisted license is allowed")
    fun whitelisted() {
        assertEquals(LicenseVerdict.ALLOWED, policy().judge(setOf("MIT")).verdict)
    }

    @Test
    @DisplayName("a license on no list is not allowed")
    fun nothingAllowed() {
        assertEquals(LicenseVerdict.NOT_ALLOWED, policy().judge(setOf("GPL-3.0")).verdict)
    }

    @Test
    @DisplayName("a license on the hidden list only is allowed, as the license plugins allow it")
    fun hidden() {
        assertEquals(LicenseVerdict.ALLOWED, policy().judge(setOf("BORLAND")).verdict)
    }

    @Test
    @DisplayName("a dual-licensed library passes on its one allowed license")
    fun dualLicense() {
        assertEquals(LicenseVerdict.ALLOWED, policy().judge(setOf("GPL-2.0", "MIT")).verdict)
    }

    @Test
    @DisplayName("a library found with no license is unknown")
    fun noLicense() {
        assertEquals(Judgement(LicenseVerdict.UNKNOWN, emptyList(), overridden = false), policy().judge(emptySet()))
    }

    @ParameterizedTest
    @ValueSource(strings = ["Unknown", "unknown license", "UNKNOWN LICENSE", " "])
    @DisplayName("an unknown marker is no license, in any case")
    fun unknownMarkers(marker: String) {
        assertEquals(Judgement(LicenseVerdict.UNKNOWN, emptyList(), overridden = false), policy().judge(setOf(marker)))
    }

    @Test
    @DisplayName("a synonym of an unknown marker in merges.txt is no license")
    fun unknownMarkerSynonym() {
        val policy = policy(registryFiles(merges = "Unknown license|none"))

        assertEquals(Judgement(LicenseVerdict.UNKNOWN, emptyList(), overridden = false), policy.judge(setOf("none")))
    }

    @Test
    @DisplayName("unknown markers are dropped from the licenses a known license is judged on")
    fun unknownMarkerBesideALicense() {
        assertEquals(
            Judgement(LicenseVerdict.NOT_ALLOWED, listOf("GPL-3.0"), overridden = false),
            policy().judge(setOf("Unknown", "GPL-3.0")),
        )
    }

    @Test
    @DisplayName("a synonym from merges.txt is judged and reported under its canonical name")
    fun synonym() {
        assertEquals(
            Judgement(LicenseVerdict.ALLOWED, listOf("Apache-2.0"), overridden = false),
            policy().judge(setOf("The Apache Software License, Version 2.0")),
        )
    }

    @Test
    @DisplayName("a synonym in another casing is its canonical name: names are matched case-insensitively (D5)")
    fun synonymInAnotherCasing() {
        assertEquals(listOf("Apache-2.0"), policy().judge(setOf("apache 2")).licenses)
    }

    @Test
    @DisplayName("a whitelist name not in merges.txt, in another casing, takes the whitelist's spelling (D5)")
    fun whitelistNameInAnotherCasing() {
        val policy = policy(registryFiles(whitelist = "Bouncy Castle Licence", merges = ""))
        assertEquals(
            Judgement(LicenseVerdict.ALLOWED, listOf("Bouncy Castle Licence"), overridden = false),
            policy.judge(setOf("bouncy castle licence")),
        )
    }

    @Test
    @DisplayName("a hidden name in another casing is allowed under the hidden list's spelling (D5)")
    fun hiddenNameInAnotherCasing() {
        assertEquals(
            Judgement(LicenseVerdict.ALLOWED, listOf("BORLAND"), overridden = false),
            policy().judge(setOf("Borland")),
        )
    }

    @Test
    @DisplayName("a name found on no list keeps its own spelling and is not allowed")
    fun unknownName() {
        assertEquals(
            Judgement(LicenseVerdict.NOT_ALLOWED, listOf("Some Custom License"), overridden = false),
            policy().judge(setOf("Some Custom License")),
        )
    }

    @Test
    @DisplayName("two synonyms of one license are reported once")
    fun twoSynonymsOfOneLicense() {
        assertEquals(listOf("MIT"), policy().judge(setOf("The MIT License", "mit")).licenses)
    }
}
