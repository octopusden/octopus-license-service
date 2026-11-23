package org.octopusden.octopus.license.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration

class DurationsTest {
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
        "45m, PT45M",
        "30s, PT30S",
        "2h, PT2H",
        "0s, PT0S",
        "PT45M, PT45M",
        "pt1m30s, PT1M30S",
    )
    @DisplayName("a duration is read in the short form people type, or in ISO-8601")
    fun parsesShortAndIsoForms(
        text: String,
        expected: String,
    ) {
        assertEquals(Duration.parse(expected), Durations.parse(text))
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = ["", "45", "45 minutes", "m45", "-5m", "1.5h", "99999999999999999999m", "9999999999999999h"])
    @DisplayName("anything else is not a duration")
    fun rejectsTheRest(text: String) {
        assertNull(Durations.parse(text))
    }

    @Test
    @DisplayName("an ISO value that does not parse is not a duration either")
    fun rejectsBadIso() {
        assertNull(Durations.parse("PTXM"))
    }
}
