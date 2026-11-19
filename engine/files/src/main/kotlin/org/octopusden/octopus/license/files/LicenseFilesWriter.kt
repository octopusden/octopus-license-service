package org.octopusden.octopus.license.files

import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.files.impl.FolderPlanner
import org.octopusden.octopus.license.files.impl.LicenseTextFiles
import org.octopusden.octopus.license.files.impl.LicensesXml
import org.octopusden.octopus.license.files.impl.ThirdPartyText
import org.octopusden.octopus.license.files.model.FileFolder
import org.octopusden.octopus.license.files.model.LicenseFiles
import org.octopusden.octopus.license.files.model.NamedFile
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.policy.model.LicenseText

/**
 * Writes the license files the license plugins write, one set per released file that was read.
 * Reads no system: the texts the registry gives only as a URL are downloaded by the caller, which
 * asks [textsToDownload] first and passes what it got to [write] (D1).
 */
class LicenseFilesWriter {
    /** The URLs of the texts the listed libraries' licenses need; no registry path is among them. */
    fun textsToDownload(
        assessment: LicenseAssessment,
        policy: LicensePolicy,
    ): Set<String> =
        usedLicenses(assessment)
            .mapNotNull { (policy.text(it) as? LicenseText.AtUrl)?.url }
            .toSet()

    /** @param downloaded URL to bytes, for the downloads that succeeded only. */
    fun write(
        assessment: LicenseAssessment,
        policy: LicensePolicy,
        downloaded: Map<String, ByteArray>,
    ): LicenseFiles {
        val folders =
            FolderPlanner.plan(assessment).map { planned ->
                val licenses = planned.libraries.flatMap { it.licenses }.toSet()
                val texts = LicenseTextFiles.texts(licenses, policy, downloaded)
                planned to texts
            }
        return LicenseFiles(
            folders =
                folders.map { (planned, texts) ->
                    FileFolder(
                        planned.name,
                        planned.releasedFile,
                        listOf(
                            NamedFile(ThirdPartyText.FILE_NAME, ThirdPartyText.render(planned.libraries).toByteArray()),
                            NamedFile(LicensesXml.FILE_NAME, LicensesXml.render(planned.libraries, policy)),
                        ) + texts.files,
                    )
                },
            missingTexts =
                folders
                    .flatMap { (_, texts) -> texts.missing }
                    .distinctBy { it.license }
                    .sortedBy { it.license },
        )
    }

    private fun usedLicenses(assessment: LicenseAssessment) =
        FolderPlanner.plan(assessment).flatMap { folder -> folder.libraries.flatMap { it.licenses } }.toSet()
}
