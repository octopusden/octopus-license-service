package org.octopusden.octopus.license.service.service.impl

import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.octopusden.octopus.license.service.service.LicenseFiles
import org.octopusden.octopus.license.service.service.LicenseReportGenerator
import org.octopusden.octopus.license.service.service.LicenseReportService
import org.octopusden.octopus.license.service.state.report.LicenseReportStore
import org.octopusden.octopus.license.service.state.report.StoredReport
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
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
    private val startLock = Any()

    override fun start(request: LicenseReportRequest): LicenseReport {
        validate(request)
        val report = LicenseReport(
            id = UUID.randomUUID().toString(),
            component = request.component,
            version = request.version,
            status = LicenseReportStatus.RUNNING,
            mode = request.mode,
        )
        // Registering and queuing are one step: a concurrent start must not join a report whose
        // queuing is then refused and removed.
        synchronized(startLock) {
            val current = licenseReportStore.addUnlessRunning(report)
            if (current.id != report.id) return current
            try {
                licenseReportExecutor.execute { generate(report) }
            } catch (_: TaskRejectedException) {
                licenseReportStore.remove(report.id)
                throw ServiceBusyException("Every license report generation is busy; start the report again later")
            }
        }
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

    private fun validate(request: LicenseReportRequest) {
        if (request.component.isBlank()) throw InvalidRequestException("Component must not be blank")
        if (request.version.isBlank()) throw InvalidRequestException("Version must not be blank")
    }

    private fun find(id: String): StoredReport =
        licenseReportStore.find(id) ?: throw NotFoundException("License report '$id' is not found or has expired")

    private fun generate(report: LicenseReport) {
        val startedAt = clock.instant()
        val finished = runCatching { licenseReportGenerator.generate(report.component, report.version, report.mode) }.fold(
            onSuccess = { generated ->
                report.copy(status = LicenseReportStatus.DONE, summary = generated.summary).also {
                    licenseReportStore.finish(it, generated.files)
                }
            },
            onFailure = { e ->
                log.error("License report {} for {}:{} failed", report.id, report.component, report.version, e)
                // The exception text can carry upstream URLs and repository paths; it stays in the log.
                report
                    .copy(
                        status = LicenseReportStatus.FAILED,
                        error = "Generation failed; the service log has the details of report ${report.id}",
                    ).also {
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
