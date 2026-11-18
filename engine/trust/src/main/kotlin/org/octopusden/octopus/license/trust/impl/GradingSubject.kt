package org.octopusden.octopus.license.trust.impl

import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.ReleasedFile

/**
 * The facts every rule reads, worked out once so no two rules can disagree on what "read" or "third
 * party" means (D3). File lists keep the inventory's listing order; library lists the assessment's.
 *
 * @param selected every released file that is not [FileStatus.NOT_SELECTED] or [FileStatus.METADATA].
 * @param covered read files and plain files: plain text cannot hold libraries.
 * @param counted every counted library, ours included.
 * @param sbomSeen whether any counted library, ours included, came from an SBOM.
 */
internal class GradingSubject private constructor(
    val labels: Set<String>,
    val selected: List<ReleasedFile>,
    val read: List<ReleasedFile>,
    val covered: List<ReleasedFile>,
    val counted: List<AssessedLibrary>,
    val thirdParty: List<AssessedLibrary>,
    val sbomSeen: Boolean,
    val findings: List<Finding>,
) {
    fun thirdPartyIn(entry: String) = thirdParty.filter { entry in it.library.foundIn }

    companion object {
        private val NOT_SELECTED = setOf(FileStatus.NOT_SELECTED, FileStatus.METADATA)

        fun of(
            assessment: LicenseAssessment,
            labels: Set<String>,
        ): GradingSubject {
            val selected = assessment.inventory.releasedFiles.filter { it.status !in NOT_SELECTED }
            return GradingSubject(
                labels = labels,
                selected = selected,
                read = selected.filter { it.status == FileStatus.READ },
                covered = selected.filter { it.status == FileStatus.READ || it.status == FileStatus.PLAIN_FILE },
                counted = assessment.libraries,
                thirdParty = assessment.libraries.filter { it.verdict != LibraryVerdict.INTERNAL },
                sbomSeen = assessment.libraries.any { Evidence.SBOM in it.library.evidence },
                findings = assessment.inventory.findings,
            )
        }
    }
}
