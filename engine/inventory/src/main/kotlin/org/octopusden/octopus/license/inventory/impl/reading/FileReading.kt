package org.octopusden.octopus.license.inventory.impl.reading

import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding

/** How much of a released file was read, from which sources, and what that noticed. */
internal data class FileReading(
    val status: FileStatus,
    val evidence: Set<Evidence>,
    val findings: List<Finding>,
)
