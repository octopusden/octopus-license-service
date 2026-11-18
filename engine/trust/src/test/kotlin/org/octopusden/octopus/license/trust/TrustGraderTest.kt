package org.octopusden.octopus.license.trust

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.trust.model.FileTrust
import org.octopusden.octopus.license.trust.model.TrustLevel
import org.octopusden.octopus.license.trust.model.TrustReasonCode.ARTIFACT_NOT_FOUND
import org.octopusden.octopus.license.trust.model.TrustReasonCode.ECOSYSTEM_NOT_DETECTED
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NOT_SCANNABLE
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_ARTIFACT_IN_REGISTRY
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_BUILD_RECORD
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_THIRD_PARTY_FOUND
import org.octopusden.octopus.license.trust.model.TrustReasonCode.PARTIAL_ARTIFACTS
import org.octopusden.octopus.license.trust.model.TrustReasonCode.UNKNOWN_LICENSES

class TrustGraderTest {
    private val grader = TrustGrader()

    @Test
    @DisplayName("the design's worked example: LOW, four reasons in rule order, four files graded")
    fun workedExample() {
        val files =
            listOf(
                file(ZIP),
                file(POM, FileStatus.METADATA),
                file(IMAGE, evidence = setOf(Evidence.BINARY_SCAN), kind = FileKind.DOCKER),
                file(CONFIG, FileStatus.PLAIN_FILE),
                file(LEGACY, FileStatus.NOT_FOUND),
            )
        val libraries =
            libs(34) + libs(6, LibraryVerdict.UNKNOWN) +
                listOf(
                    lib("generic://sha256:aa/a.so", LibraryVerdict.UNIDENTIFIED),
                    lib("generic://sha256:bb/b.so", LibraryVerdict.UNIDENTIFIED),
                )
        val grade = grader.grade(
            assessment(files, libraries, listOf(finding(FindingCode.ARTIFACT_NOT_FOUND, LEGACY))),
            setOf("java", "javascript"),
        )

        assertEquals(
            reasons(
                PARTIAL_ARTIFACTS to "3 of 4 registered artifacts checked",
                ECOSYSTEM_NOT_DETECTED to "missing npm (label javascript); detected maven 40, generic 2",
                NO_BUILD_RECORD to "no build for: $IMAGE",
                UNKNOWN_LICENSES to "6 of 42 outside libraries have no known license",
            ),
            grade.reasons,
        )
        assertEquals(TrustLevel.LOW, grade.level)
        assertEquals(
            listOf(
                FileTrust(ZIP, listOf(UNKNOWN_LICENSES)),
                FileTrust(IMAGE, listOf(NO_BUILD_RECORD, NO_THIRD_PARTY_FOUND)),
                FileTrust(CONFIG, emptyList()),
                FileTrust(LEGACY, listOf(ARTIFACT_NOT_FOUND)),
            ),
            grade.files,
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
        val grade = grader.grade(assessment(listOf(file(ZIP, FileStatus.NOT_READABLE)), findings = findings), emptySet())
        assertEquals(listOf(NOT_SCANNABLE, NOT_SCANNABLE), grade.reasons.map { it.code })
    }

    @Test
    @DisplayName("a file at NONE does not lower the component: it shows only as PARTIAL_ARTIFACTS (D5)")
    fun fileDoesNotLower() {
        val grade = grader.grade(assessment(listOf(file(ZIP), file(LEGACY, FileStatus.NOT_FOUND)), libs(3)), emptySet())
        assertEquals(TrustLevel.NONE, grade.files.single { it.entry == LEGACY }.level)
        assertEquals(TrustLevel.LOW, grade.level)
    }

    @Test
    @DisplayName("files never selected and metadata get no file trust (D5)")
    fun notSelected() {
        val files = listOf(file(ZIP), file(POM, FileStatus.METADATA), file("org.example.portal:portal-docs:zip", FileStatus.NOT_SELECTED))
        assertEquals(listOf(ZIP), grader.grade(assessment(files, libs(1)), emptySet()).files.map { it.entry })
    }

    @Test
    @DisplayName("no distribution at all: NONE, from the scan's finding")
    fun noDistribution() {
        val grade =
            grader.grade(
                assessment(emptyList(), findings = listOf(finding(FindingCode.NO_DISTRIBUTION, "sample-portal", "no build"))),
                emptySet(),
            )
        assertEquals(reasons(NO_ARTIFACT_IN_REGISTRY to "sample-portal: no build"), grade.reasons)
        assertEquals(TrustLevel.NONE, grade.level)
    }

    @Test
    @DisplayName("file reasons do not roll up: an image without libraries beside a zip with them")
    fun noRollUpEmpty() {
        val files = listOf(file(ZIP), file(IMAGE, kind = FileKind.DOCKER))
        val grade = grader.grade(assessment(files, libs(42)), emptySet())
        assertEquals(listOf(NO_THIRD_PARTY_FOUND), grade.files.single { it.entry == IMAGE }.reasons)
        assertEquals(emptyList<Any>(), grade.reasons)
    }

    @Test
    @DisplayName("file reasons do not roll up: 5 unknown of 20 in one file is too many, 5 of 200 overall is not")
    fun noRollUpShare() {
        val files = listOf(file(ZIP), file(IMAGE, kind = FileKind.DOCKER))
        val imageOnly = (1..180).map { lib("pkg:maven/org.thirdparty/image-$it@1.0", foundIn = setOf(IMAGE)) }
        val libraries = libs(15) + libs(5, LibraryVerdict.UNKNOWN) + imageOnly
        val grade = grader.grade(assessment(files, libraries), emptySet())
        assertEquals(listOf(UNKNOWN_LICENSES), grade.files.single { it.entry == ZIP }.reasons)
        assertEquals(emptyList<Any>(), grade.reasons)
    }

    @Test
    @DisplayName("nothing to doubt: HIGH, no reasons")
    fun high() {
        val grade = grader.grade(assessment(listOf(file(ZIP)), libs(3)), setOf("java"))
        assertEquals(TrustLevel.HIGH, grade.level)
        assertEquals(listOf(FileTrust(ZIP, emptyList())), grade.files)
    }
}
