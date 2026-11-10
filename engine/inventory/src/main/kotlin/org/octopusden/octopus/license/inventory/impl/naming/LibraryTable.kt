package org.octopusden.octopus.license.inventory.impl.naming

import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.PackageId

/**
 * The libraries of one scan, one row per id, filled by every source of every file and then renamed
 * and given licenses. Not thread-safe: one scan, one table.
 */
internal class LibraryTable {
    private class Row(
        val licenses: MutableSet<String> = linkedSetOf(),
        val evidence: MutableSet<Evidence> = linkedSetOf(),
        val foundIn: MutableSet<String> = linkedSetOf(),
    )

    private val rows = linkedMapOf<PackageId, Row>()

    val ids: Set<PackageId> get() = rows.keys

    fun add(
        id: PackageId,
        licenses: Set<String>,
        evidence: Evidence,
        foundIn: String,
    ) {
        rows.getOrPut(id, ::Row).apply {
            this.licenses += licenses
            this.evidence += evidence
            this.foundIn += foundIn
        }
    }

    /** The licenses Xray gave the unnamed file are dropped; the fill step asks by the new id (D10.6). */
    fun rename(
        from: PackageId,
        to: PackageId,
    ) {
        val row = rows.remove(from) ?: return
        rows.getOrPut(to, ::Row).apply {
            evidence += row.evidence + Evidence.CHECKSUM_MATCH
            foundIn += row.foundIn
        }
    }

    fun withoutLicenses(): List<PackageId> = rows.filterValues { it.licenses.isEmpty() }.keys.toList()

    fun addLicenses(
        id: PackageId,
        licenses: Set<String>,
    ) {
        rows[id]?.licenses?.addAll(licenses)
    }

    fun libraries(): List<FoundLibrary> =
        rows
            .map { (id, row) -> FoundLibrary(id, row.licenses.toSet(), row.evidence.toSet(), row.foundIn.toSet()) }
            .sortedBy { it.id.toString() }
}
