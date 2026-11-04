package org.octopusden.octopus.license.service.service

import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest

interface LicenseReportService {
    /** Registers the report as RUNNING and generates it in the background. */
    fun start(request: LicenseReportRequest): LicenseReport

    fun get(id: String): LicenseReport

    /** The zip of license files of a report that is DONE. */
    fun getFiles(id: String): LicenseFiles
}

class LicenseFiles(
    val report: LicenseReport,
    val zip: ByteArray,
)
