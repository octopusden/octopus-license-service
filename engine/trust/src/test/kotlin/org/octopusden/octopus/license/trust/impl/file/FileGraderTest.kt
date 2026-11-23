package org.octopusden.octopus.license.trust.impl.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.trust.CONFIG
import org.octopusden.octopus.license.trust.IMAGE
import org.octopusden.octopus.license.trust.ZIP
import org.octopusden.octopus.license.trust.assessment
import org.octopusden.octopus.license.trust.file
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.libs
import org.octopusden.octopus.license.trust.model.TrustReasonCode
import org.octopusden.octopus.license.trust.model.TrustReasonCode.BUILD_LIST_ONLY
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_BUILD_RECORD
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_THIRD_PARTY_FOUND
import org.octopusden.octopus.license.trust.model.TrustReasonCode.UNIDENTIFIED_BINARIES
import org.octopusden.octopus.license.trust.model.TrustReasonCode.UNKNOWN_LICENSES

class FileGraderTest {
    private fun codes(
        file: ReleasedFile,
        libraries: List<AssessedLibrary> = libs(1),
    ) = FileGrader.grade(file, GradingSubject.of(assessment(listOf(file), libraries), emptySet())).reasons

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource("UNSUPPORTED, NO_ARTIFACT_IN_REGISTRY", "NOT_FOUND, ARTIFACT_NOT_FOUND", "NOT_READABLE, NOT_SCANNABLE")
    @DisplayName("a file that could not be read takes its status's code, and nothing else (D5)")
    fun unread(
        status: FileStatus,
        code: TrustReasonCode,
    ) {
        assertEquals(listOf(code), codes(file(ZIP, status)))
    }

    @Test
    @DisplayName("read from its build record only: BUILD_LIST_ONLY alone, as it has a build record (D5)")
    fun buildListOnly() {
        assertEquals(listOf(BUILD_LIST_ONLY), codes(file(ZIP, evidence = setOf(Evidence.BUILD_RECORD))))
    }

    @Test
    @DisplayName("read by the binary scanner only: NO_BUILD_RECORD")
    fun noBuild() {
        assertEquals(listOf(NO_BUILD_RECORD), codes(file(ZIP, evidence = setOf(Evidence.BINARY_SCAN))))
    }

    @Test
    @DisplayName("read, and no third-party library in it: NO_THIRD_PARTY_FOUND")
    fun empty() {
        assertEquals(listOf(NO_THIRD_PARTY_FOUND), codes(file(ZIP), emptyList()))
    }

    @Test
    @DisplayName("the image of only OS packages, read by the scanner only: NO_BUILD_RECORD then NO_THIRD_PARTY_FOUND (D5)")
    fun osOnlyImage() {
        val image = file(IMAGE, evidence = setOf(Evidence.BINARY_SCAN), kind = FileKind.DOCKER)
        assertEquals(listOf(NO_BUILD_RECORD, NO_THIRD_PARTY_FOUND), codes(image, libs(3)))
    }

    @Test
    @DisplayName("the thresholds apply to the file's own third-party libraries (D7)")
    fun thresholds() {
        assertEquals(listOf(UNKNOWN_LICENSES), codes(file(ZIP), libs(15) + libs(5, LibraryVerdict.UNKNOWN)))
        assertEquals(listOf(UNIDENTIFIED_BINARIES), codes(file(ZIP), libs(40) + libs(10, LibraryVerdict.UNIDENTIFIED)))
    }

    @Test
    @DisplayName("libraries found only in another file are not this file's")
    fun othersIgnored() {
        assertEquals(listOf(NO_THIRD_PARTY_FOUND), codes(file(ZIP), libs(5, LibraryVerdict.UNKNOWN, foundIn = setOf(IMAGE))))
    }

    @Test
    @DisplayName("a plain file has no codes: HIGH (D5)")
    fun plain() {
        assertEquals(emptyList<TrustReasonCode>(), codes(file(CONFIG, FileStatus.PLAIN_FILE)))
    }
}
