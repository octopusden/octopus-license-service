package org.octopusden.octopus.license.inventory.impl.reading.data

import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.PackageId

/** What a build recorded for one file: [packages] carry no license, the build names none. */
internal data class DependencyReading(
    val packages: List<PackageId>,
    val nested: List<NestedArchive>,
    val findings: List<Finding>,
)
