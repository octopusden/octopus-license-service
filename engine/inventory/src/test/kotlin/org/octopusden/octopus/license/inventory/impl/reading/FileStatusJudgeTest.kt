package org.octopusden.octopus.license.inventory.impl.reading

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.reading.data.FileReading
import org.octopusden.octopus.license.inventory.model.Evidence.BINARY_SCAN
import org.octopusden.octopus.license.inventory.model.Evidence.BUILD_RECORD
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode.NOT_IN_XRAY
import org.octopusden.octopus.license.inventory.model.FindingCode.NOT_SCANNABLE
import org.octopusden.octopus.license.inventory.model.FindingCode.NO_BUILD_INFO

class FileStatusJudgeTest {
    private val entry = "org.example.portal:sample-portal-config:yaml"

    private fun judge(
        fileName: String = "sample-portal-2.4.0.jar",
        indexed: Boolean = true,
        hasBuild: Boolean = true,
        kind: FileKind = FileKind.MAVEN,
    ) = FileStatusJudge.judge(entry, kind, fileName, indexed, hasBuild, "sample-portal_ee")

    @Test
    @DisplayName("a plain text Maven file Xray did not index is PLAIN_FILE, and its build is not read either")
    fun plainFile() {
        assertEquals(
            FileReading(
                FileStatus.PLAIN_FILE,
                emptySet(),
                listOf(Finding(NOT_SCANNABLE, entry, "plain file, Xray does not index this type")),
            ),
            judge(fileName = "sample-portal-config-2.4.0.YAML", indexed = false),
        )
    }

    @Test
    @DisplayName("an unindexed jar with a build is READ from the build only, with NOT_IN_XRAY")
    fun buildOnly() {
        assertEquals(
            FileReading(FileStatus.READ, setOf(BUILD_RECORD), listOf(Finding(NOT_IN_XRAY, entry, "Xray has not indexed this file"))),
            judge(indexed = false),
        )
    }

    @Test
    @DisplayName("an indexed file without a build is READ from Xray only, with NO_BUILD_INFO naming the convention")
    fun scanOnly() {
        assertEquals(
            FileReading(
                FileStatus.READ,
                setOf(BINARY_SCAN),
                listOf(Finding(NO_BUILD_INFO, entry, "no build by properties, checksum or sample-portal_ee")),
            ),
            judge(hasBuild = false),
        )
    }

    @Test
    @DisplayName("neither Xray nor a build: NOT_READABLE, with both findings")
    fun neither() {
        assertEquals(FileStatus.NOT_READABLE, judge(indexed = false, hasBuild = false).status)
        assertEquals(listOf(NOT_IN_XRAY, NO_BUILD_INFO), judge(indexed = false, hasBuild = false).findings.map { it.code })
    }

    @Test
    @DisplayName("both: READ with both evidences and no finding")
    fun both() {
        assertEquals(FileReading(FileStatus.READ, setOf(BINARY_SCAN, BUILD_RECORD), emptyList()), judge())
    }

    @Test
    @DisplayName("an indexed yaml is read: the plain-file rule needs Xray to have skipped it")
    fun indexedYaml() {
        assertEquals(FileStatus.READ, judge(fileName = "a.yaml").status)
    }

    @Test
    @DisplayName("an unindexed image is never PLAIN_FILE: the rule is for Maven files")
    fun imageNeverPlain() {
        assertEquals(FileStatus.READ, judge(fileName = "manifest.json", indexed = false, kind = FileKind.DOCKER).status)
    }
}
