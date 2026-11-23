package org.octopusden.octopus.license.trust

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.policy.impl.parser.LicenseRegistryParser
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.trust.model.TrustReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode

/**
 * Trust grades what evaluation counted, never what it skipped, so evaluation's scope decides what a
 * label can find. Run through the real [LicenseEvaluator], so a change to its scope shows up here.
 */
class EvaluatedTrustTest {
    private val policy = LicenseRegistryParser.parse(mapOf("licenses-whitelist.txt" to "Apache-2.0|MIT"), "a1b2c3d")
    private val image = file(IMAGE, kind = FileKind.DOCKER)
    private val springCore =
        FoundLibrary(
            PackageId.parse("pkg:maven/org.springframework/spring-core@6.1.4"),
            setOf("Apache-2.0"),
            setOf(Evidence.BINARY_SCAN),
            setOf(IMAGE),
            false,
        )

    private fun python(vararg evidence: Evidence) =
        FoundLibrary(PackageId.parse("pkg:pypi/requests@2.31.0"), setOf("Apache-2.0"), evidence.toSet(), setOf(IMAGE), false)

    private fun grade(vararg libraries: FoundLibrary) =
        TrustGrader().grade(
            LicenseEvaluator().evaluate(
                LicenseInventory(
                    ComponentVersion("sample-portal", "2.4.0"),
                    emptyList(),
                    listOf(image),
                    libraries.sortedBy {
                        it.id.toString()
                    },
                    emptyList(),
                ),
                policy,
            ),
            setOf("python"),
        )

    @Test
    @DisplayName("Python only the image scan found is base-image Python, skipped by evaluation, so a python label finds none")
    fun baseImagePython() {
        assertEquals(
            listOf(TrustReason(TrustReasonCode.ECOSYSTEM_NOT_DETECTED, "missing pypi (label python); detected maven 1")),
            grade(springCore, python(Evidence.BINARY_SCAN)).reasons,
        )
    }

    @Test
    @DisplayName("Python the build declared is the application's: evaluation counts it, so the python label is satisfied")
    fun declaredPython() {
        assertEquals(emptyList<TrustReason>(), grade(springCore, python(Evidence.BINARY_SCAN, Evidence.SBOM)).reasons)
        assertEquals(emptyList<TrustReason>(), grade(springCore, python(Evidence.BINARY_SCAN, Evidence.BUILD_RECORD)).reasons)
    }
}
