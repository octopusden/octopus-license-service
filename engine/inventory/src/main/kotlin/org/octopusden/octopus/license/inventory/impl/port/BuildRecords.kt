package org.octopusden.octopus.license.inventory.impl.port

import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.model.BuildRef

/** Build-info. Every method may throw [InventorySourceUnavailableException]. */
internal interface BuildRecords {
    /** Null when the build is missing or its build-info cannot be read: no build, never a failure. */
    fun find(
        name: String,
        number: String,
    ): BuildRecord?

    fun findByPublishedSha1(sha1: String): List<BuildRef>
}
