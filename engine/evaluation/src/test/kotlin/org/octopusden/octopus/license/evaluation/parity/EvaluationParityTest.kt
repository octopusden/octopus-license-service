package org.octopusden.octopus.license.evaluation.parity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.IMAGE
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.evaluation.ZIP
import org.octopusden.octopus.license.evaluation.inventory
import org.octopusden.octopus.license.evaluation.library
import org.octopusden.octopus.license.evaluation.parity.EvaluationParityView.LibraryView
import org.octopusden.octopus.license.evaluation.policy

class EvaluationParityTest {
    private val checksumOnly = "generic://sha256:${"cd".repeat(32)}/vendor-blob.so"

    // A prototype component JSON in run 14's shape, for the design's worked example.
    private val prototypeJson =
        """
        {
          "component": "sample-portal", "version": "2.4.0",
          "artifacts": [
            {"entry": "$ZIP", "os_skipped": 0},
            {"entry": "$IMAGE", "os_skipped": 2},
            {"entry": "org.example.portal:sample-portal:pom", "status": "SKIPPED_METADATA"}
          ],
          "components": [
            {"purl": "$checksumOnly", "verdict": "REVIEW_HASH_ONLY", "overridden": false, "xray_licences": ["Unknown"]},
            {"purl": "pkg:maven/org.example/sample-lib@1.5", "verdict": "ALLOWED", "overridden": true, "xray_licences": ["GPL-3.0"]},
            {"purl": "pkg:npm/left-pad@1.3.0", "verdict": "UNKNOWN", "overridden": false, "xray_licences": []}
          ],
          "violations": [
            {"code": "LICENCE_NOT_ALLOWED", "subject": "pkg:npm/left-pad@1.3.0", "detail": "Unknown"},
            {"code": "NOT_IN_XRAY", "subject": "$ZIP", "detail": "Xray has not indexed this file"}
          ],
          "os_packages_skipped": 1,
          "docker_python_skipped": 1
        }
        """.trimIndent()

    private val prototype = EvaluationParityView.ofPrototype(ObjectMapper().readTree(prototypeJson))

    private val service =
        EvaluationParityView.of(
            LicenseEvaluator().evaluate(
                inventory(
                    library(checksumOnly, setOf("Unknown")),
                    library("pkg:maven/org.example/sample-lib@1.5", setOf("GPL-3.0")),
                    library("pkg:npm/left-pad@1.3.0"),
                    library("pkg:pypi/requests@2.31.0", setOf("Apache-2.0"), foundIn = setOf(IMAGE)),
                    library("pkg:rpm/openssl-libs@3.0.7", setOf("OpenSSL"), foundIn = setOf(IMAGE)),
                ),
                policy,
            ),
        )

    @Test
    @DisplayName(
        "the prototype's view: REVIEW_HASH_ONLY reads as UNIDENTIFIED, LICENCE_NOT_ALLOWED subjects, files with something skipped (D11)",
    )
    fun prototypeView() {
        assertEquals(
            EvaluationParityView(
                libraries =
                    mapOf(
                        checksumOnly to LibraryView("UNIDENTIFIED", false, setOf("Unknown")),
                        "pkg:maven/org.example/sample-lib@1.5" to LibraryView("ALLOWED", true, setOf("GPL-3.0")),
                        "pkg:npm/left-pad@1.3.0" to LibraryView("UNKNOWN", false, emptySet()),
                    ),
                osPackages = 1,
                baseImagePython = 1,
                skippedPerFile = mapOf(IMAGE to 2),
                notAllowed = setOf("pkg:npm/left-pad@1.3.0"),
            ),
            prototype,
        )
    }

    @Test
    @DisplayName("the service's view of the same example is the same; LICENCE_NOT_ALLOWED is derived from UNKNOWN and NOT_ALLOWED (D5)")
    fun serviceViewMatches() {
        assertEquals(prototype, service)
        assertEquals(EvaluationDiffs(emptyList(), emptyList()), EvaluationParityDiff.diff(prototype, service))
    }

    @Test
    @DisplayName("a verdict that differs on the same licenses found and the same override is a policy rule, not a failure (D6)")
    fun policyRule() {
        val differing = service.withLibrary("pkg:npm/left-pad@1.3.0", LibraryView("ALLOWED", false, emptySet()))

        val diffs = EvaluationParityDiff.diff(prototype, differing)

        assertEquals(listOf("library pkg:npm/left-pad@1.3.0: verdict: prototype UNKNOWN, service ALLOWED"), diffs.policyRules)
        assertEquals(emptyList<String>(), diffs.others)
    }

    @Test
    @DisplayName(
        "an override on one side only, on the same licenses found, is a policy rule too: which override matches is the policy's (D6)",
    )
    fun overridePolicyRule() {
        val otherOverride = service.withLibrary("pkg:maven/org.example/sample-lib@1.5", LibraryView("NOT_ALLOWED", false, setOf("GPL-3.0")))

        val diffs = EvaluationParityDiff.diff(prototype, otherOverride)

        assertEquals(
            listOf(
                "library pkg:maven/org.example/sample-lib@1.5: verdict: prototype ALLOWED, service NOT_ALLOWED",
                "library pkg:maven/org.example/sample-lib@1.5: overridden: prototype true, service false",
            ),
            diffs.policyRules,
        )
        assertEquals(emptyList<String>(), diffs.others)
    }

    @Test
    @DisplayName("a verdict that differs with other licenses found is a difference")
    fun verdictDifference() {
        val otherLicenses = service.withLibrary("pkg:npm/left-pad@1.3.0", LibraryView("ALLOWED", false, setOf("MIT")))

        assertEquals(
            listOf(
                "library pkg:npm/left-pad@1.3.0: verdict: prototype UNKNOWN, service ALLOWED",
                "library pkg:npm/left-pad@1.3.0: licenses found: prototype [], service [MIT]",
            ),
            EvaluationParityDiff.diff(prototype, otherLicenses).others,
        )
    }

    @Test
    @DisplayName(
        "INTERNAL or UNIDENTIFIED on one side only is a difference, whatever the licenses: evaluation decides those, not the policy",
    )
    fun ownVerdictDifference() {
        val internal = service.withLibrary("pkg:npm/left-pad@1.3.0", LibraryView("INTERNAL", false, emptySet()))

        val diffs = EvaluationParityDiff.diff(prototype, internal)

        assertEquals(listOf("library pkg:npm/left-pad@1.3.0: verdict: prototype UNKNOWN, service INTERNAL"), diffs.others)
        assertEquals(emptyList<String>(), diffs.policyRules)
    }

    @Test
    @DisplayName("a library on one side only is left to the inventory's diff, which already reports it")
    fun oneSideOnly() {
        val fewer = service.copy(libraries = service.libraries - "pkg:npm/left-pad@1.3.0", notAllowed = emptySet())

        assertEquals(EvaluationDiffs(emptyList(), emptyList()), EvaluationParityDiff.diff(prototype, fewer))
    }

    @Test
    @DisplayName("skipped totals and per-file counts are compared")
    fun skipped() {
        val other = service.copy(osPackages = 2, baseImagePython = 0, skippedPerFile = mapOf(IMAGE to 1, ZIP to 1))

        assertEquals(
            listOf(
                "OS packages skipped: prototype 1, service 2",
                "base-image Python skipped: prototype 1, service 0",
                "file $IMAGE: skipped: prototype 2, service 1",
                "file $ZIP: skipped: prototype 0, service 1",
            ),
            EvaluationParityDiff.diff(prototype, other).others,
        )
    }

    @Test
    @DisplayName("a LICENCE_NOT_ALLOWED subject is compared only where both sides give the library the same verdict")
    fun notAllowed() {
        val missing = service.copy(notAllowed = emptySet())

        assertEquals(
            listOf("LICENCE_NOT_ALLOWED pkg:npm/left-pad@1.3.0: only in the prototype"),
            EvaluationParityDiff.diff(prototype, missing).others,
        )
    }

    private fun EvaluationParityView.withLibrary(
        id: String,
        view: LibraryView,
    ) = copy(libraries = libraries + (id to view))
}
