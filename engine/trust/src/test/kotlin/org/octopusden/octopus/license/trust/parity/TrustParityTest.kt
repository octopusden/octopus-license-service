package org.octopusden.octopus.license.trust.parity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.trust.CONFIG
import org.octopusden.octopus.license.trust.IMAGE
import org.octopusden.octopus.license.trust.POM
import org.octopusden.octopus.license.trust.ZIP
import org.octopusden.octopus.license.trust.model.FileTrust
import org.octopusden.octopus.license.trust.model.TrustGrade
import org.octopusden.octopus.license.trust.model.TrustReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_BUILD_RECORD
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_THIRD_PARTY_FOUND
import org.octopusden.octopus.license.trust.model.TrustReasonCode.UNKNOWN_LICENSES

class TrustParityTest {
    private val mapper = ObjectMapper()

    // A prototype component JSON in the shape of stage 12's output, licence spelling and all.
    private val prototypeJson =
        """
        {
          "component": "sample-portal", "version": "2.4.0", "confidence": "MEDIUM",
          "reasons": [
            {"level": "MEDIUM", "code": "NO_BUILD_RECORD", "detail": "no build for: $IMAGE"},
            {"level": "MEDIUM", "code": "UNKNOWN_LICENCES", "detail": "6 of 42 outside libraries have no known licence"}
          ],
          "artifacts": [
            {"entry": "$ZIP", "level": "MEDIUM", "flags": ["UNKNOWN_LICENCES"]},
            {"entry": "$POM", "level": "", "flags": []},
            {"entry": "$IMAGE", "level": "LOW", "flags": ["NO_BUILD_RECORD", "NO_THIRD_PARTY_FOUND"]},
            {"entry": "$CONFIG", "level": "HIGH", "flags": []}
          ]
        }
        """.trimIndent()

    private val sameGrade =
        TrustGrade(
            reasons =
                listOf(
                    TrustReason(UNKNOWN_LICENSES, "6 of 42 outside libraries have no known license"),
                    TrustReason(NO_BUILD_RECORD, "no build for: $IMAGE"),
                ),
            files =
                listOf(
                    FileTrust(ZIP, listOf(UNKNOWN_LICENSES)),
                    FileTrust(IMAGE, listOf(NO_BUILD_RECORD, NO_THIRD_PARTY_FOUND)),
                    FileTrust(CONFIG, emptyList()),
                ),
        )

    private fun prototype(json: String = prototypeJson) = TrustParityView.ofPrototype(mapper.readTree(json))

    @Test
    @DisplayName("the prototype's licence spelling reads as the service's; reasons compare as a set; unselected files are left out (D10)")
    fun same() {
        assertEquals(emptyList<String>(), TrustParityDiff.diff(prototype(), TrustParityView.of(sameGrade)))
    }

    @Test
    @DisplayName("a reason or a file that differs is one line each; the level matches here, so it is not reported")
    fun differences() {
        val service =
            TrustGrade(
                reasons = listOf(TrustReason(NO_BUILD_RECORD, "no build for: $IMAGE")),
                files = listOf(
                    FileTrust(ZIP, emptyList()),
                    FileTrust(IMAGE, listOf(NO_BUILD_RECORD, NO_THIRD_PARTY_FOUND)),
                    FileTrust(CONFIG, emptyList()),
                ),
            )

        assertEquals(
            listOf(
                "trust: reason only in the prototype: UNKNOWN_LICENSES 6 of 42 outside libraries have no known license",
                "trust: file $ZIP: prototype MEDIUM [UNKNOWN_LICENSES], service HIGH []",
            ),
            TrustParityDiff.diff(prototype(), TrustParityView.of(service)),
        )
    }

    @Test
    @DisplayName("a level that differs is reported")
    fun level() {
        val low =
            TrustGrade(sameGrade.reasons + TrustReason(NO_THIRD_PARTY_FOUND, "1 file(s) checked, 0 outside libraries"), sameGrade.files)

        assertEquals(
            listOf(
                "trust: level: prototype MEDIUM, service LOW",
                "trust: reason only in the service: NO_THIRD_PARTY_FOUND 1 file(s) checked, 0 outside libraries",
            ),
            TrustParityDiff.diff(prototype(), TrustParityView.of(low)),
        )
    }

    @Test
    @DisplayName("a prototype component that failed, or had no version, has no grade to compare (D10)")
    fun ungradable() {
        assertTrue(TrustParityView.gradable(mapper.readTree(prototypeJson)))
        assertFalse(TrustParityView.gradable(mapper.readTree("""{"component": "c", "version": "1", "error": "HttpError: 500"}""")))
        assertFalse(TrustParityView.gradable(mapper.readTree("""{"component": "c", "version": null}""")))
    }
}
