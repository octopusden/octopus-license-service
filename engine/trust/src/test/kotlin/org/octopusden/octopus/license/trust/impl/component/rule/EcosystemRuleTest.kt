package org.octopusden.octopus.license.trust.impl.component.rule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.trust.CONFIG
import org.octopusden.octopus.license.trust.assessment
import org.octopusden.octopus.license.trust.file
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.lib
import org.octopusden.octopus.license.trust.libs
import org.octopusden.octopus.license.trust.model.TrustReasonCode.ECOSYSTEM_NOT_DETECTED
import org.octopusden.octopus.license.trust.model.TrustReasonCode.FULL_LIST_UNAVAILABLE
import org.octopusden.octopus.license.trust.reasons

class EcosystemRuleTest {
    private val workedExample =
        libs(40) +
            listOf(
                lib("generic://sha256:aa/a.so", LibraryVerdict.UNIDENTIFIED),
                lib("generic://sha256:bb/b.so", LibraryVerdict.UNIDENTIFIED),
            )

    private fun grade(
        libraries: List<AssessedLibrary>,
        vararg labels: String,
    ) = EcosystemRule.reasons(GradingSubject.of(assessment(libraries = libraries), labels.toSet()))

    @Test
    @DisplayName("javascript with no npm: the worked example's detail, character for character (D4)")
    fun workedExample() {
        assertEquals(
            reasons(ECOSYSTEM_NOT_DETECTED to "missing npm (label javascript); detected maven 40, generic 2"),
            grade(workedExample, "java", "javascript"),
        )
    }

    @Test
    @DisplayName("labels sharing an ecosystem are one entry; the entries follow the sorted labels (D4)")
    fun grouping() {
        assertEquals(
            reasons(ECOSYSTEM_NOT_DETECTED to "missing cocoapods/swift (label objective_c swift); detected maven 1"),
            grade(libs(1), "swift", "objective_c"),
        )
        assertEquals(
            reasons(ECOSYSTEM_NOT_DETECTED to "missing conan/cpp (label c), golang (label go); detected maven 1"),
            grade(libs(1), "go", "c"),
        )
    }

    @Test
    @DisplayName("detected types by count, a tie in the order a type first appears by rendered id: Counter.most_common (D4)")
    fun detectedOrder() {
        // Sorted by id: golang, maven, npm, pypi. golang and pypi tie at 1, and golang comes first.
        val libraries =
            listOf(
                lib("pkg:pypi/attrs@23.2.0"),
                lib("pkg:golang/github.com/x/y@1.0"),
                lib("pkg:npm/a@1"),
                lib("pkg:npm/b@1"),
                lib("pkg:maven/org.thirdparty/a@1"),
                lib("pkg:maven/org.thirdparty/b@1"),
            )
        assertEquals(
            reasons(ECOSYSTEM_NOT_DETECTED to "missing nuget (label cs); detected maven 2, npm 2, golang 1, pypi 1"),
            grade(libraries, "cs"),
        )
    }

    @Test
    @DisplayName("files read but no third-party library: detected nothing")
    fun detectedNothing() {
        assertEquals(reasons(ECOSYSTEM_NOT_DETECTED to "missing pypi (label python); detected nothing"), grade(emptyList(), "python"))
    }

    @Test
    @DisplayName("our own libraries never satisfy a label: they are not third party")
    fun oursIgnored() {
        assertEquals(
            reasons(ECOSYSTEM_NOT_DETECTED to "missing npm (label javascript); detected nothing"),
            grade(listOf(lib("pkg:npm/@example/ui@1.0", LibraryVerdict.INTERNAL)), "javascript"),
        )
    }

    @Test
    @DisplayName("a label without an ecosystem, or one the prototype does not know: no reason")
    fun noEcosystem() {
        assertEquals(emptyList<Any>(), grade(libs(1), "java", "kotlin", "groovy"))
    }

    @Test
    @DisplayName("nothing read: no ECOSYSTEM_NOT_DETECTED")
    fun nothingRead() {
        val plainOnly = assessment(listOf(file(CONFIG, FileStatus.PLAIN_FILE)))
        assertEquals(emptyList<Any>(), EcosystemRule.reasons(GradingSubject.of(plainOnly, setOf("javascript"))))
    }

    @Test
    @DisplayName("Dart and iOS libraries without an SBOM: FULL_LIST_UNAVAILABLE, types sorted (D4)")
    fun fullListUnavailable() {
        assertEquals(
            reasons(FULL_LIST_UNAVAILABLE to "found cocoapods, pub libraries, but no SBOM"),
            grade(listOf(lib("pkg:pub/http@1.2.0"), lib("pkg:cocoapods/Alamofire@5.8.0"), lib("pkg:pub/path@1.9.0"))),
        )
    }

    @Test
    @DisplayName("an SBOM on any counted library, ours included, lifts FULL_LIST_UNAVAILABLE (D4)")
    fun sbomSeen() {
        val ours = lib("pkg:maven/org.example/app@1.0", LibraryVerdict.INTERNAL).let {
            it.copy(library = it.library.copy(evidence = setOf(Evidence.SBOM)))
        }
        assertEquals(emptyList<Any>(), grade(listOf(lib("pkg:pub/http@1.2.0"), ours)))
    }

    @Test
    @DisplayName("maven, npm and generic are listed completely: no FULL_LIST_UNAVAILABLE")
    fun completeTypes() {
        assertEquals(
            emptyList<Any>(),
            grade(
                listOf(
                    lib("pkg:maven/org.thirdparty/a@1"),
                    lib("pkg:npm/a@1"),
                    lib("generic://sha256:aa/a.so", LibraryVerdict.UNIDENTIFIED),
                ),
            ),
        )
    }
}
