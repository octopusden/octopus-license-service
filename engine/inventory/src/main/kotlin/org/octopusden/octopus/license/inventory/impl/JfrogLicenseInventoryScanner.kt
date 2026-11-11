package org.octopusden.octopus.license.inventory.impl

import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.inventory.impl.adapter.JfrogPorts
import org.octopusden.octopus.license.inventory.impl.completing.ChecksumNamer
import org.octopusden.octopus.license.inventory.impl.completing.LicenseFiller
import org.octopusden.octopus.license.inventory.impl.listing.ConventionBuilds
import org.octopusden.octopus.license.inventory.impl.listing.ReleasedFileLister
import org.octopusden.octopus.license.inventory.impl.listing.data.ListedFile
import org.octopusden.octopus.license.inventory.impl.port.ScanPorts
import org.octopusden.octopus.license.inventory.impl.reading.FileContentReader
import org.octopusden.octopus.license.inventory.impl.reading.source.BuildDependencyReader
import org.octopusden.octopus.license.inventory.impl.reading.source.SbomReader
import org.octopusden.octopus.license.inventory.impl.shared.BuildLocator
import org.octopusden.octopus.license.inventory.impl.shared.LibraryTable
import org.octopusden.octopus.license.inventory.impl.shared.Ownership
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.target.model.LicenseTarget
import java.time.Duration

/**
 * The license scan over Artifactory and Xray (D2): lists the released files, reads each, then names
 * checksum-only files and fills missing licenses. It decides nothing itself; every rule lives in a
 * class of its own. Keeps no state between scans, so one instance serves every generation thread.
 *
 * @param portsPerScan fresh ports for each scan: the Xray wait budget is one scan's (D12).
 */
class JfrogLicenseInventoryScanner internal constructor(
    private val portsPerScan: () -> ScanPorts,
    ownership: OwnershipRules,
) : LicenseInventoryScanner {
    private val ownership = Ownership(ownership)

    /**
     * @param url the JFrog platform URL, under which both Artifactory and Xray are served.
     * @param xrayWait how long one scan waits, in all, for Xray to finish scanning its files.
     * @param xrayPoll how often a pending Xray scan is asked about again.
     */
    constructor(
        url: String,
        token: String,
        ownership: OwnershipRules,
        xrayWait: Duration = Duration.ofMinutes(5),
        xrayPoll: Duration = Duration.ofSeconds(10),
    ) : this(JfrogPorts(url, token, xrayWait, xrayPoll)::create, ownership)

    override fun scan(target: LicenseTarget): LicenseInventory {
        val scan = portsPerScan().memoized()
        val locator = BuildLocator(scan.records)
        val listing = ReleasedFileLister(scan.records, scan.store, locator).list(target)
        val reader = FileContentReader(scan.scanner, scan.store, locator, BuildDependencyReader(ownership), SbomReader(scan.store))
        val table = LibraryTable()
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
        return LicenseInventory(target.componentVersion, listing.builds.map { it.ref }, files, table.libraries(), findings.toList())
    }

    private fun ListedFile.released(
        status: FileStatus,
        evidence: Set<Evidence> = emptySet(),
        agent: String? = null,
    ) = ReleasedFile(
        entry = entry,
        kind = kind,
        selectedBy = selectedBy,
        path = stored?.fullPath,
        build = build?.ref,
        buildFoundBy = buildFoundBy,
        buildAgent = agent,
        status = status,
        evidence = evidence,
    )
}
