package org.octopusden.octopus.license.inventory.impl

import org.octopusden.octopus.license.inventory.impl.port.BuildRecords
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.BuildLookup

/**
 * The build that produced a stored file: its `build.name` and `build.number` properties, then any
 * build that published its sha1. Declared files built elsewhere and our nested archives are found
 * the same way, so the two cannot drift apart.
 */
internal class BuildLocator(
    private val records: BuildRecords,
) {
    fun locate(file: StoredFile): Pair<BuildRecord, BuildLookup>? {
        val name = file.properties[BUILD_NAME]
        val number = file.properties[BUILD_NUMBER]
        if (!name.isNullOrEmpty() && !number.isNullOrEmpty()) {
            records.find(name, number)?.let { return it to BuildLookup.PROPERTIES }
        }
        val sha1 = file.sha1?.takeIf { it.isNotEmpty() } ?: return null
        return records
            .findByPublishedSha1(sha1)
            .firstNotNullOfOrNull { records.find(it.name, it.number) }
            ?.let { it to BuildLookup.CHECKSUM }
    }

    private companion object {
        const val BUILD_NAME = "build.name"
        const val BUILD_NUMBER = "build.number"
    }
}
