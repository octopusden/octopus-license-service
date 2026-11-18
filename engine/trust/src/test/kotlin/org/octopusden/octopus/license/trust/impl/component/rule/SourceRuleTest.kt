package org.octopusden.octopus.license.trust.impl.component.rule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.trust.IMAGE
import org.octopusden.octopus.license.trust.LEGACY
import org.octopusden.octopus.license.trust.ZIP
import org.octopusden.octopus.license.trust.assessment
import org.octopusden.octopus.license.trust.file
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.model.TrustReasonCode.BUILD_LIST_ONLY
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_BUILD_RECORD
import org.octopusden.octopus.license.trust.reasons

class SourceRuleTest {
    private fun grade(vararg files: ReleasedFile) = SourceRule.reasons(GradingSubject.of(assessment(files.toList()), emptySet()))

    @Test
    @DisplayName("an image read by the binary scanner only: NO_BUILD_RECORD, with its entry")
    fun noBuild() {
        assertEquals(
            reasons(NO_BUILD_RECORD to "no build for: $IMAGE"),
            grade(file(ZIP), file(IMAGE, evidence = setOf(Evidence.BINARY_SCAN), kind = FileKind.DOCKER)),
        )
    }

    @Test
    @DisplayName("a zip read from its build record only: BUILD_LIST_ONLY")
    fun buildOnly() {
        assertEquals(reasons(BUILD_LIST_ONLY to "not indexed in Xray: $ZIP"), grade(file(ZIP, evidence = setOf(Evidence.BUILD_RECORD))))
    }

    @Test
    @DisplayName("two files without a build record: one reason, entries space-separated in listing order")
    fun twoFiles() {
        val scanOnly = setOf(Evidence.BINARY_SCAN)
        assertEquals(
            reasons(NO_BUILD_RECORD to "no build for: $ZIP $IMAGE"),
            grade(file(ZIP, evidence = scanOnly), file(IMAGE, evidence = scanOnly, kind = FileKind.DOCKER)),
        )
    }

    @Test
    @DisplayName("an unread file never counts: the coverage rule speaks for it")
    fun unreadIgnored() {
        assertEquals(emptyList<Any>(), grade(file(ZIP), file(LEGACY, FileStatus.NOT_FOUND), file(IMAGE, FileStatus.NOT_READABLE)))
    }
}
