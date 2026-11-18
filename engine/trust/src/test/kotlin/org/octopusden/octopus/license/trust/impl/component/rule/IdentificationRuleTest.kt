package org.octopusden.octopus.license.trust.impl.component.rule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.trust.assessment
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.libs
import org.octopusden.octopus.license.trust.model.TrustReasonCode.UNIDENTIFIED_BINARIES
import org.octopusden.octopus.license.trust.model.TrustReasonCode.UNKNOWN_LICENSES
import org.octopusden.octopus.license.trust.reasons

class IdentificationRuleTest {
    private fun grade(libraries: List<AssessedLibrary>) =
        IdentificationRule.reasons(GradingSubject.of(assessment(libraries = libraries), emptySet()))

    @Test
    @DisplayName("6 of 42 unknown: UNKNOWN_LICENSES, the worked example's detail")
    fun unknown() {
        assertEquals(
            reasons(UNKNOWN_LICENSES to "6 of 42 outside libraries have no known license"),
            grade(libs(36) + libs(6, LibraryVerdict.UNKNOWN)),
        )
    }

    @Test
    @DisplayName("10 of 50 unidentified: UNIDENTIFIED_BINARIES")
    fun unidentified() {
        assertEquals(
            reasons(UNIDENTIFIED_BINARIES to "10 of 50 outside files identified by checksum only"),
            grade(libs(40) + libs(10, LibraryVerdict.UNIDENTIFIED)),
        )
    }

    @Test
    @DisplayName("our own libraries are not in the total")
    fun oursNotCounted() {
        assertEquals(
            reasons(UNKNOWN_LICENSES to "5 of 5 outside libraries have no known license"),
            grade(libs(5, LibraryVerdict.UNKNOWN) + libs(100, LibraryVerdict.INTERNAL)),
        )
    }

    @Test
    @DisplayName("below either threshold: no reason")
    fun below() {
        assertEquals(emptyList<Any>(), grade(libs(190) + libs(9, LibraryVerdict.UNIDENTIFIED) + libs(4, LibraryVerdict.UNKNOWN)))
    }
}
