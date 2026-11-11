package org.octopusden.octopus.license.inventory.impl.listing

import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.BuildLookup
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.SelectionReason

/**
 * A file step A listed: where it is stored and which build made it, if known.
 *
 * @param skip why it is not read ([FileStatus.NOT_SELECTED], [FileStatus.METADATA],
 * [FileStatus.NOT_FOUND], [FileStatus.UNSUPPORTED]); null when step B reads it.
 * @param ownId the file's own Maven coordinates, which an inner archive's library check ignores.
 * @param findings what listing it found, kept with the file so findings come out in file order.
 */
internal data class ListedFile(
    val entry: String,
    val kind: FileKind,
    val selectedBy: Set<SelectionReason>,
    val stored: StoredFile? = null,
    val build: BuildRecord? = null,
    val buildFoundBy: BuildLookup? = null,
    val skip: FileStatus? = null,
    val ownId: PackageId? = null,
    val findings: List<Finding> = emptyList(),
) {
    val declared get() = SelectionReason.DECLARED in selectedBy
}

/** @param findings about the whole version: [org.octopusden.octopus.license.inventory.model.FindingCode.NO_DISTRIBUTION]. */
internal data class Listing(
    val builds: List<BuildRecord>,
    val files: List<ListedFile>,
    val findings: List<Finding>,
) {
    val nothingToRead get() = files.all { it.skip == FileStatus.NOT_SELECTED || it.skip == FileStatus.METADATA }
}

internal fun ListedFile.notFound(detail: String) =
    copy(skip = FileStatus.NOT_FOUND, findings = findings + Finding(FindingCode.ARTIFACT_NOT_FOUND, entry, detail))
