package org.octopusden.octopus.license.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.ALLOWED
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.INTERNAL
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.UNIDENTIFIED
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict.UNKNOWN
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.evaluation.model.SkippedPackages

class LicenseEvaluatorTest {
    private val evaluator = LicenseEvaluator()

    // The design's worked example, in the inventory's order (sorted by rendered id).
    private val blob = library("generic://sha256:${"cd".repeat(32)}/vendor-blob.so", setOf("Unknown"))
    private val layer = library("pkg:docker/sample/base@sha256__ab12", foundIn = setOf(IMAGE))
    private val plugins = library("pkg:maven/org.example.portal/portal-plugins@2.4.0-118", ours = true)
    private val sampleLib = library("pkg:maven/org.example/sample-lib@1.5", setOf("GPL-3.0"))
    private val springCore = library("pkg:maven/org.springframework/spring-core@6.1.4", setOf("The Apache Software License, Version 2.0"))
    private val leftPad = library("pkg:npm/left-pad@1.3.0")
    private val attrs = library("pkg:pypi/attrs@23.2.0", setOf("MIT"), foundIn = setOf(IMAGE, ZIP))
    private val requests = library("pkg:pypi/requests@2.31.0", setOf("Apache-2.0"), foundIn = setOf(IMAGE))
    private val openssl = library("pkg:rpm/openssl-libs@3.0.7", setOf("OpenSSL"), foundIn = setOf(IMAGE))

    @Test
    @DisplayName("the worked example: layers dropped, OS and base-image Python skipped, the rest judged in order (D3, D4, D8)")
    fun workedExample() {
        val inventory = inventory(blob, layer, plugins, sampleLib, springCore, leftPad, attrs, requests, openssl)

        assertEquals(
            LicenseAssessment(
                inventory,
                listOf(
                    AssessedLibrary(blob, UNIDENTIFIED, emptyList(), false),
                    AssessedLibrary(plugins, INTERNAL, emptyList(), false),
                    AssessedLibrary(sampleLib, ALLOWED, listOf("MIT"), true),
                    AssessedLibrary(springCore, ALLOWED, listOf("Apache-2.0"), false),
                    AssessedLibrary(leftPad, UNKNOWN, emptyList(), false),
                    AssessedLibrary(attrs, ALLOWED, listOf("MIT"), false),
                ),
                SkippedPackages(osPackages = 1, baseImagePython = 1, perFile = mapOf(IMAGE to 2)),
            ),
            evaluator.evaluate(inventory, policy),
        )
    }

    @Test
    @DisplayName("an OS package found in two files is one in the total and one in each file (D8)")
    fun osPackageInTwoFiles() {
        val inventory = inventory(library("pkg:rpm/openssl-libs@3.0.7", foundIn = setOf(IMAGE, ZIP)))

        assertEquals(
            SkippedPackages(osPackages = 1, baseImagePython = 0, perFile = mapOf(IMAGE to 1, ZIP to 1)),
            evaluator.evaluate(inventory, policy).skipped,
        )
    }

    @Test
    @DisplayName("an image layer counts nowhere, not even in a file's skipped count (D3.1)")
    fun layerCountsNowhere() {
        val assessment = evaluator.evaluate(inventory(layer), policy)

        assertEquals(LicenseAssessment(assessment.inventory, emptyList(), SkippedPackages(0, 0, emptyMap())), assessment)
    }

    @Test
    @DisplayName("no library: nothing assessed, nothing skipped, the inventory kept")
    fun empty() {
        val inventory = inventory()

        assertEquals(LicenseAssessment(inventory, emptyList(), SkippedPackages(0, 0, emptyMap())), evaluator.evaluate(inventory, policy))
    }
}
