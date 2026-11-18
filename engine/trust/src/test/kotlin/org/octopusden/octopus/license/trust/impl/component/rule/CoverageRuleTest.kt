package org.octopusden.octopus.license.trust.impl.component.rule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.trust.CONFIG
import org.octopusden.octopus.license.trust.IMAGE
import org.octopusden.octopus.license.trust.LEGACY
import org.octopusden.octopus.license.trust.ZIP
import org.octopusden.octopus.license.trust.assessment
import org.octopusden.octopus.license.trust.file
import org.octopusden.octopus.license.trust.finding
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.model.TrustReasonCode.ARTIFACT_NOT_FOUND
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NOT_SCANNABLE
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_ARTIFACT_IN_REGISTRY
import org.octopusden.octopus.license.trust.model.TrustReasonCode.PARTIAL_ARTIFACTS
import org.octopusden.octopus.license.trust.reasons

class CoverageRuleTest {
    private fun grade(assessment: org.octopusden.octopus.license.evaluation.model.LicenseAssessment) =
        CoverageRule.reasons(GradingSubject.of(assessment, emptySet()))

    @Test
    @DisplayName("nothing selected and no distribution: NONE, from the finding, \"{subject}: {detail}\" (D4)")
    fun noDistribution() {
        val detail = "no build sample-portal_ee(_docker)/2.4.0 and no artifact in the registry GAV or docker field"
        assertEquals(
            reasons(NO_ARTIFACT_IN_REGISTRY to "sample-portal: $detail"),
            grade(assessment(files = emptyList(), findings = listOf(finding(FindingCode.NO_DISTRIBUTION, "sample-portal", detail)))),
        )
    }

    @Test
    @DisplayName("an unsupported entry and a missing file map to their own reasons; an empty detail keeps the colon (D4)")
    fun unsupportedAndMissing() {
        val files =
            listOf(file("file:///share/portal.zip", FileStatus.UNSUPPORTED, kind = FileKind.FILE_SHARE), file(LEGACY, FileStatus.NOT_FOUND))
        val findings =
            listOf(
                finding(FindingCode.UNSUPPORTED_DISTRIBUTION_ENTRY, "file:///share/portal.zip"),
                finding(FindingCode.ARTIFACT_NOT_FOUND, LEGACY, "tried versions 2.4.0"),
            )
        assertEquals(
            reasons(NO_ARTIFACT_IN_REGISTRY to "file:///share/portal.zip: ", ARTIFACT_NOT_FOUND to "$LEGACY: tried versions 2.4.0"),
            grade(assessment(files, findings = findings)),
        )
    }

    @Test
    @DisplayName("an unreadable file gives one NOT_SCANNABLE per finding, in the order found (D4)")
    fun unreadable() {
        val findings =
            listOf(
                finding(FindingCode.NOT_IN_XRAY, ZIP, "Xray has not indexed this file"),
                finding(FindingCode.NO_BUILD_INFO, ZIP, "no build by properties, checksum or sample-portal_ee"),
            )
        assertEquals(
            reasons(
                NOT_SCANNABLE to "$ZIP: Xray has not indexed this file",
                NOT_SCANNABLE to "$ZIP: no build by properties, checksum or sample-portal_ee",
            ),
            grade(assessment(listOf(file(ZIP, FileStatus.NOT_READABLE)), findings = findings)),
        )
    }

    @Test
    @DisplayName("nothing covered and no finding that says why: NOT_SCANNABLE, no registered artifact could be scanned (D4)")
    fun nothingToSayWhy() {
        assertEquals(
            reasons(NOT_SCANNABLE to "no registered artifact could be scanned"),
            grade(assessment(listOf(file(ZIP, FileStatus.NOT_READABLE)))),
        )
    }

    @Test
    @DisplayName("notes never become reasons: a plain file's NOT_SCANNABLE, a build name that differs (D4)")
    fun notesIgnored() {
        val findings =
            listOf(
                finding(FindingCode.NOT_SCANNABLE, CONFIG, "plain file, Xray does not index this type"),
                finding(FindingCode.BUILD_NAME_DIFFERS, ZIP, "PORTAL_BUILD (convention sample-portal_ee)"),
            )
        assertEquals(
            reasons(NOT_SCANNABLE to "no registered artifact could be scanned"),
            grade(assessment(listOf(file(ZIP, FileStatus.NOT_READABLE)), findings = findings)),
        )
    }

    @Test
    @DisplayName("3 of 4 covered: PARTIAL_ARTIFACTS, and the scan's findings are not repeated (D4)")
    fun partial() {
        val files = listOf(file(ZIP), file(IMAGE), file(CONFIG, FileStatus.PLAIN_FILE), file(LEGACY, FileStatus.NOT_FOUND))
        assertEquals(
            reasons(PARTIAL_ARTIFACTS to "3 of 4 registered artifacts checked"),
            grade(assessment(files, findings = listOf(finding(FindingCode.ARTIFACT_NOT_FOUND, LEGACY)))),
        )
    }

    @Test
    @DisplayName("everything covered, a plain file among them: no reason")
    fun allCovered() {
        assertEquals(emptyList<Any>(), grade(assessment(listOf(file(ZIP), file(CONFIG, FileStatus.PLAIN_FILE)))))
    }
}
