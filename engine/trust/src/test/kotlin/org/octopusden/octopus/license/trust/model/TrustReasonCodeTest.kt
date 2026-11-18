package org.octopusden.octopus.license.trust.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

class TrustReasonCodeTest {
    // The prototype's REASONS table at e380518, minus ERROR and NEVER_BUILT (D2).
    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        "NO_ARTIFACT_IN_REGISTRY, NONE",
        "ARTIFACT_NOT_FOUND, NONE",
        "NOT_SCANNABLE, NONE",
        "PARTIAL_ARTIFACTS, LOW",
        "NO_THIRD_PARTY_FOUND, LOW",
        "ECOSYSTEM_NOT_DETECTED, LOW",
        "FULL_LIST_UNAVAILABLE, LOW",
        "BUILD_LIST_ONLY, LOW",
        "UNIDENTIFIED_BINARIES, MEDIUM",
        "UNKNOWN_LICENSES, MEDIUM",
        "NO_BUILD_RECORD, MEDIUM",
    )
    @DisplayName("each code has the prototype's level (D2)")
    fun level(
        code: TrustReasonCode,
        level: TrustLevel,
    ) {
        assertEquals(level, code.level)
    }

    @Test
    @DisplayName("the eleven codes, no more: ERROR and NEVER_BUILT cannot be reached (D2)")
    fun codes() {
        assertEquals(11, TrustReasonCode.entries.size)
    }

    @ParameterizedTest
    @EnumSource(TrustReasonCode::class)
    @DisplayName("every code has a title, a meaning and a fix, spelled license")
    fun texts(code: TrustReasonCode) {
        listOf(code.title, code.meaning, code.fix).forEach {
            assertTrue(it.isNotBlank(), "$code has a blank text")
            assertTrue("licence" !in it.lowercase(), "$code spells licence: $it")
        }
    }

    @Test
    @DisplayName("the fix that pointed to the prototype's Pipeline issues points to the files' trust (D2)")
    fun partialFix() {
        assertEquals("Fix the files that failed (see the files' trust).", TrustReasonCode.PARTIAL_ARTIFACTS.fix)
    }
}
