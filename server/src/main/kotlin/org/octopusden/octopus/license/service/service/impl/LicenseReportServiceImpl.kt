package org.octopusden.octopus.license.service.service.impl

import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.service.service.LicenseFiles
import org.octopusden.octopus.license.service.service.LicenseReportGenerator
import org.octopusden.octopus.license.service.service.LicenseReportService
import org.octopusden.octopus.license.service.state.report.LicenseReportStore
import org.octopusden.octopus.license.service.state.report.StoredReport
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class LicenseReportServiceImpl(
    private val licenseReportStore: LicenseReportStore,
    private val licenseReportGenerator: LicenseReportGenerator,
    @Qualifier("licenseReportExecutor") private val licenseReportExecutor: TaskExecutor,
    private val clock: Clock,
) : LicenseReportService {
    override fun start(request: LicenseReportRequest): LicenseReport {
        require(request.component.isNotBlank()) { "Component must not be blank" }
        require(request.version.isNotBlank()) { "Version must not be blank" }
        val report = LicenseReport(
            id = UUID.randomUUID().toString(),
            component = request.component,
            version = request.version,
            status = LicenseReportStatus.RUNNING,
        )
        licenseReportStore.add(report)
        licenseReportExecutor.execute { generate(report) }
        return report
    }

    override fun get(id: String) = find(id).report

    override fun getFiles(id: String): LicenseFiles {
        val stored = find(id)
        val files = stored.files
            ?: throw LicenseFilesNotAvailableException(
                "License report '$id' is ${stored.report.status}, it has no license files",
            )
        return LicenseFiles(stored.report, files)
    }

    private fun find(id: String): StoredReport =
        licenseReportStore.find(id) ?: throw NotFoundException("License report '$id' is not found or has expired")

    private fun generate(report: LicenseReport) {
        val startedAt = clock.instant()
        val finished = runCatching { licenseReportGenerator.generate(report.component, report.version) }.fold(
            onSuccess = { generated ->
                report.copy(status = LicenseReportStatus.DONE, summary = generated.summary).also {
                    licenseReportStore.finish(it, generated.files)
                }
            },
            onFailure = { e ->
                log.error("License report {} for {}:{} failed", report.id, report.component, report.version, e)
                report.copy(status = LicenseReportStatus.FAILED, error = e.message ?: e.javaClass.name).also {
                    licenseReportStore.finish(it, null)
                }
            },
        )
        log.info(
            "License report {} for {}:{} finished {} in {} ms",
            report.id,
            report.component,
            report.version,
            finished.status,
            Duration.between(startedAt, clock.instant()).toMillis(),
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(LicenseReportServiceImpl::class.java)
    }
}
