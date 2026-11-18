package org.octopusden.octopus.license.trust.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.trust.CONFIG
import org.octopusden.octopus.license.trust.IMAGE
import org.octopusden.octopus.license.trust.ZIP
import org.octopusden.octopus.license.trust.assessment
import org.octopusden.octopus.license.trust.file
import org.octopusden.octopus.license.trust.lib
import org.octopusden.octopus.license.trust.model.TrustReasonCode.NO_THIRD_PARTY_FOUND
import org.octopusden.octopus.license.trust.reasons

class EmptyContentRuleTest {
    private val twoRead = listOf(file(ZIP), file(IMAGE))
    private val ours = listOf(lib("pkg:maven/org.example/ours@1.0", LibraryVerdict.INTERNAL))

    @Test
    @DisplayName("files read, only our own libraries: NO_THIRD_PARTY_FOUND")
    fun onlyOurs() {
        assertEquals(
            reasons(NO_THIRD_PARTY_FOUND to "2 file(s) checked, 0 outside libraries"),
            EmptyContentRule.reasons(GradingSubject.of(assessment(twoRead, ours), setOf("java"))),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["c", "cpp"])
    @DisplayName("a C or C++ label adds the note that libraries are probably compiled in (D6)")
    fun nativeLabel(label: String) {
        assertEquals(
            reasons(NO_THIRD_PARTY_FOUND to "2 file(s) checked, 0 outside libraries (C/C++ build)"),
            EmptyContentRule.reasons(GradingSubject.of(assessment(twoRead, ours), setOf(label, "java"))),
        )
    }

    @Test
    @DisplayName("no C or C++ label: no note, whatever the build is called (D6)")
    fun buildNameIgnored() {
        val native = listOf(file(ZIP, build = BuildRef("NATIVELIB", "57")))
        assertEquals(
            reasons(NO_THIRD_PARTY_FOUND to "1 file(s) checked, 0 outside libraries"),
            EmptyContentRule.reasons(GradingSubject.of(assessment(native), emptySet())),
        )
    }

    @Test
    @DisplayName("nothing read, only plain files: no reason, the coverage rule speaks for it")
    fun nothingRead() {
        assertEquals(
            emptyList<Any>(),
            EmptyContentRule.reasons(GradingSubject.of(assessment(listOf(file(CONFIG, FileStatus.PLAIN_FILE))), emptySet())),
        )
    }

    @Test
    @DisplayName("one third-party library is enough: no reason")
    fun oneThirdParty() {
        assertEquals(
            emptyList<Any>(),
            EmptyContentRule.reasons(GradingSubject.of(assessment(twoRead, ours + lib("pkg:npm/left-pad@1.3.0")), emptySet())),
        )
    }
}
