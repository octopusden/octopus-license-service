package org.octopusden.octopus.license.inventory.impl.reading

import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode

/** Whether a located file is read at all, and from which sources (D6). Decides; reads nothing. */
internal object FileStatusJudge {
    private val PLAIN_EXTENSIONS =
        setOf("yaml", "yml", "xml", "json", "txt", "properties", "sql", "sh", "bat", "md", "pdf", "html", "csv")

    /** @param fileName the stored file's name, whose extension marks a plain text file. */
    fun judge(
        entry: String,
        kind: FileKind,
        fileName: String,
        indexed: Boolean,
        hasBuild: Boolean,
        conventionBuild: String,
    ): FileReading {
        if (!indexed && kind == FileKind.MAVEN && fileName.substringAfterLast('.').lowercase() in PLAIN_EXTENSIONS) {
            return FileReading(
                FileStatus.PLAIN_FILE,
                emptySet(),
                listOf(Finding(FindingCode.NOT_SCANNABLE, entry, "plain file, Xray does not index this type")),
            )
        }
        val findings =
            listOfNotNull(
                Finding(FindingCode.NOT_IN_XRAY, entry, "Xray has not indexed this file").takeUnless { indexed },
                Finding(FindingCode.NO_BUILD_INFO, entry, "no build by properties, checksum or $conventionBuild").takeUnless { hasBuild },
            )
        if (!indexed && !hasBuild) return FileReading(FileStatus.NOT_READABLE, emptySet(), findings)
        val evidence =
            setOfNotNull(Evidence.BINARY_SCAN.takeIf { indexed }, Evidence.BUILD_RECORD.takeIf { hasBuild })
        return FileReading(FileStatus.READ, evidence, findings)
    }
}
