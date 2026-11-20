package org.octopusden.octopus.license.trust.impl.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.trust.IMAGE
import org.octopusden.octopus.license.trust.LEGACY
import org.octopusden.octopus.license.trust.ZIP
import org.octopusden.octopus.license.trust.assessment
import org.octopusden.octopus.license.trust.file
import org.octopusden.octopus.license.trust.finding
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.libs
import org.octopusden.octopus.license.trust.model.TrustReasonCode.ECOSYSTEM_NOT_DETECTED
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NOT_SCANNABLE
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_BUILD_RECORD
import org.octopusden.octopus.license.trust.model.TrustReasonCode.PARTIAL_ARTIFACTS
import org.octopusden.octopus.license.trust.model.TrustReasonCode.UNKNOWN_LICENSES

class ComponentGraderTest {
    @Test
    @DisplayName("reasons follow the prototype's order: coverage, empty content, ecosystems, sources, identification (D4)")
    fun order() {
        val files =
            listOf(
                file(ZIP),
                file(IMAGE, evidence = setOf(Evidence.BINARY_SCAN), kind = FileKind.DOCKER),
                file(LEGACY, FileStatus.NOT_FOUND),
            )
        val subject = GradingSubject.of(assessment(files, libs(15) + libs(5, LibraryVerdict.UNKNOWN)), setOf("javascript"))

        assertEquals(
            listOf(PARTIAL_ARTIFACTS, ECOSYSTEM_NOT_DETECTED, NO_BUILD_RECORD, UNKNOWN_LICENSES),
            ComponentGrader.reasons(subject).map { it.code },
        )
    }

    @Test
    @DisplayName("the same code and detail twice is given once; the same code with another detail is kept (D4)")
    fun duplicates() {
        val findings =
            listOf(
                finding(FindingCode.NOT_IN_XRAY, ZIP, "Xray has not indexed this file"),
                finding(FindingCode.NOT_IN_XRAY, ZIP, "Xray has not indexed this file"),
                finding(FindingCode.NO_BUILD_INFO, ZIP, "no build by properties, checksum or sample-portal_ee"),
            )
        val subject = GradingSubject.of(assessment(listOf(file(ZIP, FileStatus.NOT_READABLE)), findings = findings), emptySet())

        assertEquals(listOf(NOT_SCANNABLE, NOT_SCANNABLE), ComponentGrader.reasons(subject).map { it.code })
    }
}
