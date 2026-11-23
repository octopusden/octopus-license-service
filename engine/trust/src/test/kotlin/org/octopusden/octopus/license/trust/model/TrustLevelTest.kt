package org.octopusden.octopus.license.trust.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TrustLevelTest {
    @Test
    @DisplayName("nothing to doubt is HIGH")
    fun empty() {
        assertEquals(TrustLevel.HIGH, TrustLevel.lowest(emptyList()))
    }

    @Test
    @DisplayName("the lowest level wins, NONE < LOW < MEDIUM < HIGH")
    fun lowest() {
        assertEquals(TrustLevel.LOW, TrustLevel.lowest(listOf(TrustLevel.MEDIUM, TrustLevel.LOW)))
        assertEquals(TrustLevel.NONE, TrustLevel.lowest(listOf(TrustLevel.NONE, TrustLevel.HIGH)))
    }
}
