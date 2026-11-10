package org.octopusden.octopus.license.inventory.impl.listing

import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.SelectionReason
import org.octopusden.octopus.license.target.model.DeclaredArtifact
import org.octopusden.octopus.license.target.model.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.model.DeclaredArtifact.MavenArtifact
import org.octopusden.octopus.license.target.model.LicenseTarget

/**
 * Why a candidate is released, or why it is listed but not read (D5).
 *
 * @param selectedBy holds [SelectionReason.DECLARED] when the candidate stands for a declared artifact.
 * @param skip [FileStatus.NOT_SELECTED] or [FileStatus.METADATA]; null when the file is read.
 * @param matched the declared artifact the candidate stands for, so it is no leftover.
 */
internal data class Selection(
    val selectedBy: Set<SelectionReason>,
    val skip: FileStatus?,
    val matched: DeclaredArtifact?,
)

internal class CandidateSelector(
    target: LicenseTarget,
) {
    private val declaredMaven = target.distribution.declared.filterIsInstance<MavenArtifact>()
    private val declaredImages = target.distribution.declared.filterIsInstance<DockerImage>()
    private val patterns = ArtifactPatternMatcher(target.distribution.patterns)

    fun select(candidate: Candidate): Selection =
        when (candidate) {
            is MavenCandidate -> select(candidate)
            is ImageCandidate -> select(candidate)
        }

    private fun select(candidate: MavenCandidate): Selection {
        val matched = declaredMaven.firstOrNull { it.entry() == candidate.entry }
        val selectedBy =
            buildSet {
                if (matched != null) add(SelectionReason.DECLARED)
                if (patterns.matches(candidate.group, candidate.artifactId)) add(SelectionReason.PATTERN)
            }
        val skip =
            when {
                selectedBy.isEmpty() -> FileStatus.NOT_SELECTED
                matched == null && (candidate.extension in METADATA_EXTENSIONS || candidate.classifier in METADATA_CLASSIFIERS) ->
                    FileStatus.METADATA
                else -> null
            }
        return Selection(selectedBy, skip = skip, matched = matched)
    }

    // An image is read whether declared or not; a declared image matches by name, whatever its tag (D10.1, D10.9).
    private fun select(candidate: ImageCandidate): Selection {
        val matched = declaredImages.firstOrNull { it.image == candidate.image }
        val selectedBy = setOfNotNull(SelectionReason.DOCKER_BUILD, SelectionReason.DECLARED.takeIf { matched != null })
        return Selection(selectedBy, skip = null, matched = matched)
    }

    private companion object {
        val METADATA_EXTENSIONS = setOf("pom", "module", "md5", "sha1", "sha256", "asc")
        val METADATA_CLASSIFIERS = setOf("sources", "javadoc", "test-sources", "tests", "test-fixtures")
    }
}

/** The declared artifact as the registry writes it, `group:artifact:extension[:classifier]`. */
internal fun MavenArtifact.entry() = "$groupId:$artifactId:$extension" + classifier?.let { ":$it" }.orEmpty()
