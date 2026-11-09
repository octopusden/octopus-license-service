package org.octopusden.octopus.license.client

import feign.Headers
import feign.Param
import feign.RequestLine
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest

/**
 * The license service API. Errors arrive as the
 * [org.octopusden.octopus.license.core.exception.LicenseServiceException] subtypes.
 */
interface LicenseServiceClient {
    /** Starts a generation and returns at once, with status RUNNING; poll [getReport] for the result. */
    @RequestLine("POST rest/api/1/license-reports")
    @Headers("Content-Type: application/json")
    fun startReport(request: LicenseReportRequest): LicenseReport

    @RequestLine("GET rest/api/1/license-reports/{id}")
    fun getReport(
        @Param("id") id: String,
    ): LicenseReport

    /** The license files as one zip; only for a report that is DONE. */
    @RequestLine("GET rest/api/1/license-reports/{id}/files")
    fun downloadFiles(
        @Param("id") id: String,
    ): ByteArray
}
