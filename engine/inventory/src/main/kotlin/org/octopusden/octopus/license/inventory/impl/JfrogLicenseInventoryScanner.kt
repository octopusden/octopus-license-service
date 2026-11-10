package org.octopusden.octopus.license.inventory.impl

import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.inventory.impl.listing.BuildLocator
import org.octopusden.octopus.license.inventory.impl.listing.ConventionBuilds
import org.octopusden.octopus.license.inventory.impl.listing.ListedFile
import org.octopusden.octopus.license.inventory.impl.listing.ReleasedFileLister
import org.octopusden.octopus.license.inventory.impl.naming.ChecksumNamer
import org.octopusden.octopus.license.inventory.impl.naming.LicenseFiller
import org.octopusden.octopus.license.inventory.impl.naming.PackageTable
import org.octopusden.octopus.license.inventory.impl.port.ScanPorts
import org.octopusden.octopus.license.inventory.impl.reading.BuildDependencyReader
import org.octopusden.octopus.license.inventory.impl.reading.FileContentReader
import org.octopusden.octopus.license.inventory.impl.reading.SbomReader
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.target.model.LicenseTarget

/**
 * The license scan over Artifactory and Xray (D2): lists the released files, reads each, then names
 * checksum-only files and fills missing licenses. It decides nothing itself; every rule lives in a
 * class of its own. Keeps no state between scans, so one instance serves every generation thread.
 */
class JfrogLicenseInventoryScanner internal constructor(
    private val ports: ScanPorts,
    ownership: OwnershipRules,
) : LicenseInventoryScanner {
    private val ownership = Ownership(ownership)

    override fun scan(target: LicenseTarget): LicenseInventory {
        val scan = ports.memoized()
        val locator = BuildLocator(scan.records)
        val listing = ReleasedFileLister(scan.records, scan.store, locator).list(target)
        val reader = FileContentReader(scan.scanner, scan.store, locator, BuildDependencyReader(ownership), SbomReader(scan.store))
        val table = PackageTable()
        val findings = linkedSetOf<Finding>()
        val files =
            listing.files.map { listed ->
                findings += listed.findings
                listed.skip?.let { listed.released(it) }
                    ?: reader.read(listed, ConventionBuilds.name(target), table).let { reading ->
                        findings += reading.findings
                        listed.released(reading.status, reading.evidence, listed.build?.agent)
                    }
            }
        findings += listing.findings
        if (!listing.nothingToRead) {
            ChecksumNamer(scan.store, ownership).name(table)
            LicenseFiller(scan.database).fill(table)
        }
        return LicenseInventory(target.componentVersion, listing.builds.map { it.ref }, files, table.packages(), findings.toList())
    }

    private fun ListedFile.released(
        status: FileStatus,
        evidence: Set<Evidence> = emptySet(),
        agent: String? = null,
    ) = ReleasedFile(
        entry = entry,
        kind = kind,
        declared = declared,
        selectedBy = selectedBy,
        path = stored?.fullPath,
        build = build?.ref,
        buildFoundBy = buildFoundBy,
        buildAgent = agent,
        status = status,
        evidence = evidence,
    )
}
