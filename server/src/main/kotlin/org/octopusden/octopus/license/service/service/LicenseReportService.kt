package org.octopusden.octopus.license.service.service

import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest

interface LicenseReportService {
    /**
     * Resolves the component version first: an excluded one is registered EXCLUDED and never
     * generated; any other is registered RUNNING and generated in the background.
     *
     * @throws org.octopusden.octopus.license.core.exception.NotFoundException the registry knows no such component version.
     * @throws org.octopusden.octopus.license.core.exception.ServiceUnavailableException the registry could not be asked.
     */
    fun start(request: LicenseReportRequest): LicenseReport

    fun get(id: String): LicenseReport

    /** The zip of license files of a report that is DONE. */
    fun getFiles(id: String): LicenseFiles
}

class LicenseFiles(
    val report: LicenseReport,
    val zip: ByteArray,
)
