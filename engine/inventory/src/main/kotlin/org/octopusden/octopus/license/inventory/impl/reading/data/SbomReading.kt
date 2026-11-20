package org.octopusden.octopus.license.inventory.impl.reading.data

import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.PackageId

/** What a build's SBOMs list, and a finding for each SBOM that could not be read. */
internal data class SbomReading(
    val packages: Map<PackageId, Set<String>>,
    val findings: List<Finding>,
)
