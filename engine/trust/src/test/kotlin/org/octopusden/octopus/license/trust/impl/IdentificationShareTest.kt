package org.octopusden.octopus.license.trust.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IdentificationShareTest {
    @ParameterizedTest(name = "{0} of {1}: {2}")
    @CsvSource("5, 50, true", "6, 42, true", "4, 10, false", "5, 51, false", "5, 5, true")
    @DisplayName("too many unknown: at least 5, and at least 10 % (D7)")
    fun unknown(
        count: Int,
        total: Int,
        crossed: Boolean,
    ) {
        assertEquals(crossed, IdentificationShare.tooManyUnknown(count, total))
    }

    @ParameterizedTest(name = "{0} of {1}: {2}")
    @CsvSource("10, 100, true", "9, 20, false", "12, 200, false", "10, 101, false")
    @DisplayName("too many unidentified: at least 10, and at least 10 % (D7)")
    fun unidentified(
        count: Int,
        total: Int,
        crossed: Boolean,
    ) {
        assertEquals(crossed, IdentificationShare.tooManyUnidentified(count, total))
    }
}
