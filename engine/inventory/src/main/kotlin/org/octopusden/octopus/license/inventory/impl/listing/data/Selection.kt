package org.octopusden.octopus.license.inventory.impl.listing.data

import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.SelectionReason
import org.octopusden.octopus.license.target.model.DeclaredArtifact

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
