package org.octopusden.octopus.license.trust.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DerivedLevelTest {
    @Test
    @DisplayName("a grade's level is its lowest reason's; a file's level never enters it")
    fun grade() {
        val grade =
            TrustGrade(
                reasons =
                    listOf(
                        TrustReason(TrustReasonCode.PARTIAL_ARTIFACTS, "3 of 4 registered artifacts checked"),
                        TrustReason(TrustReasonCode.UNKNOWN_LICENSES, "6 of 42 outside libraries have no known license"),
                    ),
                files = listOf(FileTrust("org.example.portal:sample-legacy:jar", listOf(TrustReasonCode.ARTIFACT_NOT_FOUND))),
            )
        assertEquals(TrustLevel.LOW, grade.level)
    }

    @Test
    @DisplayName("a grade or a file with no reason is HIGH")
    fun noReason() {
        assertEquals(TrustLevel.HIGH, TrustGrade(emptyList(), emptyList()).level)
        assertEquals(TrustLevel.HIGH, FileTrust("org.example.portal:sample-portal-config:yaml", emptyList()).level)
    }

    @Test
    @DisplayName("a file's level is its lowest code's")
    fun file() {
        val image = FileTrust("sample/portal:2.4.0-118", listOf(TrustReasonCode.NO_BUILD_RECORD, TrustReasonCode.NO_THIRD_PARTY_FOUND))
        assertEquals(TrustLevel.LOW, image.level)
    }
}
