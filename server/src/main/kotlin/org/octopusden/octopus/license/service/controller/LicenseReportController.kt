package org.octopusden.octopus.license.service.controller

import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.service.service.LicenseReportService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("rest/api/1/license-reports")
class LicenseReportController(
    private val licenseReportService: LicenseReportService,
) {
    @PostMapping
    fun startReport(
        @RequestBody request: LicenseReportRequest,
    ): ResponseEntity<LicenseReport> =
        licenseReportService.start(request).let { report ->
            ResponseEntity
                .accepted()
                .location(
                    ServletUriComponentsBuilder
                        .fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(report.id)
                        .toUri(),
                ).body(report)
        }

    @GetMapping("{id}")
    fun getReport(
        @PathVariable id: String,
    ) = licenseReportService.get(id)

    @GetMapping("{id}/files", produces = [ZIP_MEDIA_TYPE])
    fun downloadFiles(
        @PathVariable id: String,
    ): ResponseEntity<ByteArray> =
        licenseReportService.getFiles(id).let { files ->
            ResponseEntity
                .ok()
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition
                        .attachment()
                        .filename("${files.report.component}-${files.report.version}-licenses.zip")
                        .build()
                        .toString(),
                ).body(files.zip)
        }

    private companion object {
        const val ZIP_MEDIA_TYPE = "application/zip"
    }
}
