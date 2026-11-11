package org.octopusden.octopus.license.inventory.impl.port.data

import org.octopusden.octopus.license.inventory.model.BuildRef

/** A build's build-info, in this module's terms: what each module published and depended on. */
internal data class BuildRecord(
    val ref: BuildRef,
    val agent: String?,
    val modules: List<BuildModule>,
)
