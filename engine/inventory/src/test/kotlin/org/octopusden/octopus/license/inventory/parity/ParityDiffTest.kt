package org.octopusden.octopus.license.inventory.parity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.model.BuildLookup
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.Evidence.BINARY_SCAN
import org.octopusden.octopus.license.inventory.model.Evidence.BUILD_RECORD
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.inventory.model.FoundPackage
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.inventory.model.SelectionReason.DECLARED
import org.octopusden.octopus.license.inventory.model.SelectionReason.DOCKER_BUILD
import org.octopusden.octopus.license.inventory.model.SelectionReason.PATTERN
import org.octopusden.octopus.license.target.model.ComponentVersion

/** A component as the prototype wrote it in `components/<c>.json`, and the same component as the service scans it. */
class ParityDiffTest {
    private val prototype =
        """
        {"component":"sample-portal","version":"2.4.0","builds":["sample-portal_ee/2.4.0-118","sample-portal_ee_docker/2.4.0-118"],
         "artifacts":[
           {"entry":"org.example.portal:sample-portal:zip","kind":"gav","in_registry":true,"selected_by":["registry","pattern"],
            "build":"sample-portal_ee/2.4.0-118","build_found_by":"convention","path":"rnd-maven-release-local/org/example/portal/sample-portal/2.4.0-118/sample-portal-2.4.0-118.zip",
            "build_agent":"jfrog-cli-go","read":true,"scanned":true,"no_build":false,"status":"CHECKED","level":"HIGH"},
           {"entry":"org.example.portal:helper-tool:jar","kind":"gav","in_registry":false,"selected_by":[],
            "build":"sample-portal_ee/2.4.0-118","build_found_by":"convention","status":"NOT_SELECTED"},
           {"entry":"sample/portal:2.4.0-118","kind":"docker","in_registry":true,"selected_by":["docker build"],
            "build":"sample-portal_ee_docker/2.4.0-118","build_found_by":"convention","path":"rnd-docker-release-local/sample/portal/2.4.0-118/manifest.json",
            "read":true,"scanned":true,"no_build":false,"status":"CHECKED"}],
         "violations":[{"code":"LICENCE_NOT_ALLOWED","subject":"pkg:maven/x/y@1","detail":"GPL"}],
         "notes":[{"code":"LOCAL_FILE_DEPENDENCY","subject":"VTS-SDK-6.3.0.jar","detail":"shipped file without Maven coordinates; check its licence by hand"}],
         "components":[
           {"purl":"pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0","type":"maven","licences":["Apache-2.0"],
            "sources":["build-info"],"artifacts":["org.example.portal:sample-portal:zip"],"verdict":"ALLOWED","xray_licences":["Apache-2.0"]},
           {"purl":"pkg:maven/org.example.portal/sample-portal@2.4.0-118","type":"maven","licences":["Unknown"],
            "sources":["xray-artifact"],"artifacts":["org.example.portal:sample-portal:zip"],"verdict":"INTERNAL","xray_licences":[]}]}
        """.trimIndent()

    private val zip =
        ReleasedFile(
            "org.example.portal:sample-portal:zip",
            FileKind.MAVEN,
            true,
            setOf(DECLARED, PATTERN),
            "rnd-maven-release-local/org/example/portal/sample-portal/2.4.0-118/sample-portal-2.4.0-118.zip",
            BuildRef("sample-portal_ee", "2.4.0-118"),
            BuildLookup.CONVENTION,
            "jfrog-cli-go",
            FileStatus.READ,
            setOf(BINARY_SCAN, BUILD_RECORD),
        )
    private val helper =
        ReleasedFile(
            "org.example.portal:helper-tool:jar",
            FileKind.MAVEN,
            false,
            emptySet(),
            null,
            BuildRef("sample-portal_ee", "2.4.0-118"),
            BuildLookup.CONVENTION,
            null,
            FileStatus.NOT_SELECTED,
        )
    private val image =
        ReleasedFile(
            "sample/portal:2.4.0-118",
            FileKind.DOCKER,
            true,
            setOf(DOCKER_BUILD),
            "rnd-docker-release-local/sample/portal/2.4.0-118/manifest.json",
            BuildRef("sample-portal_ee_docker", "2.4.0-118"),
            BuildLookup.CONVENTION,
            "jfrog-cli-go",
            FileStatus.READ,
            setOf(BINARY_SCAN, BUILD_RECORD),
        )

    private val inventory =
        LicenseInventory(
            ComponentVersion("sample-portal", "2.4.0"),
            listOf(BuildRef("sample-portal_ee", "2.4.0-118"), BuildRef("sample-portal_ee_docker", "2.4.0-118")),
            listOf(zip, helper, image),
            listOf(
                FoundPackage(
                    id("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0"),
                    setOf("Apache-2.0"),
                    setOf(BUILD_RECORD),
                    setOf(zip.entry),
                ),
                FoundPackage(id("pkg:maven/org.example.portal/sample-portal@2.4.0-118"), emptySet(), setOf(BINARY_SCAN), setOf(zip.entry)),
                FoundPackage(id("pkg:docker/sha256__abc@latest"), emptySet(), setOf(BINARY_SCAN), setOf(image.entry)),
                FoundPackage(id("pkg:rpm/openssl-libs@3.0.7"), setOf("OpenSSL"), setOf(BINARY_SCAN), setOf(image.entry)),
                FoundPackage(id("pkg:pypi/subscription-manager@1.29"), setOf("GPL-2.0"), setOf(BINARY_SCAN), setOf(image.entry)),
            ),
            listOf(
                Finding(
                    FindingCode.LOCAL_FILE_DEPENDENCY,
                    "VTS-SDK-6.3.0.jar",
                    "shipped file without Maven coordinates; check its license by hand",
                ),
            ),
        )

    private fun id(raw: String) = PackageId.parse(raw)

    private fun diff(service: LicenseInventory = inventory) =
        ParityDiff.diff(ParityView.ofPrototype(ObjectMapper().readTree(prototype)), ParityView.of(service))

    @Test
    @DisplayName("the same component: no difference, with the prototype's stage-11 drops and its licence spelling taken into account")
    fun same() {
        assertEquals(emptyList<String>(), diff())
    }

    @Test
    @DisplayName("a file whose status differs: one line naming the file and the field")
    fun fileStatus() {
        assertEquals(
            listOf(
                "file org.example.portal:sample-portal:zip: status: prototype CHECKED, service BUILD_INFO_ONLY",
            ),
            diff(inventory.copy(files = listOf(zip.copy(evidence = setOf(BUILD_RECORD)), helper, image))),
        )
    }

    @Test
    @DisplayName("a file only on one side, and a reordering")
    fun filesOnOneSide() {
        assertEquals(
            listOf("file org.example.portal:helper-tool:jar: only in the prototype"),
            diff(inventory.copy(files = listOf(zip, image))),
        )
        assertEquals(
            listOf(
                "file order: prototype " +
                    "[org.example.portal:sample-portal:zip, org.example.portal:helper-tool:jar, sample/portal:2.4.0-118], " +
                    "service [sample/portal:2.4.0-118, org.example.portal:sample-portal:zip, org.example.portal:helper-tool:jar]",
            ),
            diff(inventory.copy(files = listOf(image, zip, helper))),
        )
    }

    @Test
    @DisplayName("a package only on one side, or with other licenses, sources or files")
    fun packages() {
        val extra = FoundPackage(id("pkg:npm/react@18.2.0"), emptySet(), setOf(BUILD_RECORD), setOf(zip.entry))
        val changed = inventory.packages.first().copy(licenses = setOf("MIT"), evidence = setOf(BINARY_SCAN, BUILD_RECORD))

        assertEquals(
            listOf(
                "package pkg:npm/react@18.2.0: only in the service",
                "package pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0: licenses: prototype [Apache-2.0], service [MIT]",
                "package pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0: " +
                    "sources: prototype [build-info], service [build-info, xray-artifact]",
            ),
            diff(inventory.copy(packages = listOf(changed) + inventory.packages.drop(1) + extra)),
        )
    }

    @Test
    @DisplayName("a finding only on one side; the prototype's verdict violations are not compared")
    fun findings() {
        assertEquals(
            listOf(
                "finding only in the prototype: LOCAL_FILE_DEPENDENCY VTS-SDK-6.3.0.jar " +
                    "shipped file without Maven coordinates; check its license by hand",
                "finding only in the service: NOT_IN_XRAY x y",
            ),
            diff(inventory.copy(findings = listOf(Finding(FindingCode.NOT_IN_XRAY, "x", "y")))),
        )
    }

    @Test
    @DisplayName("builds that differ")
    fun builds() {
        assertEquals(
            listOf(
                "builds: prototype [sample-portal_ee/2.4.0-118, sample-portal_ee_docker/2.4.0-118], service [sample-portal_ee/2.4.0-118]",
            ),
            diff(inventory.copy(builds = inventory.builds.take(1))),
        )
    }
}
