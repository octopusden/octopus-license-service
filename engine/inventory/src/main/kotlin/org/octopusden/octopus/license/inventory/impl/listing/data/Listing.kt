package org.octopusden.octopus.license.inventory.impl.listing.data

import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding

/** @param findings about the whole version: [org.octopusden.octopus.license.inventory.model.FindingCode.NO_DISTRIBUTION]. */
internal data class Listing(
    val builds: List<BuildRecord>,
    val files: List<ListedFile>,
    val findings: List<Finding>,
) {
    val nothingToRead get() = files.all { it.skip == FileStatus.NOT_SELECTED || it.skip == FileStatus.METADATA }
}
