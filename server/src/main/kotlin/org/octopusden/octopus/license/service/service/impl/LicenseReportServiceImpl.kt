package org.octopusden.octopus.license.service.service.impl

import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceUnavailableException
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.service.policy.LicensePolicyHolder
import org.octopusden.octopus.license.service.service.LicenseFiles
import org.octopusden.octopus.license.service.service.LicenseReportGenerator
import org.octopusden.octopus.license.service.service.LicenseReportService
import org.octopusden.octopus.license.service.store.LicenseReportStore
import org.octopusden.octopus.license.service.store.StoredReport
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.exception.ComponentVersionNotFoundException
import org.octopusden.octopus.license.target.exception.ComponentsRegistryUnavailableException
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.octopus.license.target.model.TargetResolution
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
    private val licenseTargetResolver: LicenseTargetResolver,
    private val licensePolicyHolder: LicensePolicyHolder,
    @Qualifier("licenseReportExecutor") private val licenseReportExecutor: TaskExecutor,
    private val clock: Clock,
) : LicenseReportService {
    override fun start(request: LicenseReportRequest): LicenseReport {
        require(request.component.isNotBlank()) { "Component must not be blank" }
        require(request.version.isNotBlank()) { "Version must not be blank" }
        // Read once: the generation keeps this policy even if a newer one is loaded meanwhile (D7).
        val policy =
            licensePolicyHolder.current()?.policy
                ?: throw ServiceUnavailableException("The license registry has not been loaded yet")
        val report = LicenseReport(
            id = UUID.randomUUID().toString(),
            component = request.component,
            version = request.version,
            status = LicenseReportStatus.RUNNING,
        )
        return when (val resolution = resolve(request)) {
            is TargetResolution.Excluded ->
                report.copy(status = LicenseReportStatus.EXCLUDED, exclusionReason = resolution.reason).also {
                    licenseReportStore.finish(it, null)
                    log.info("License report {} for {}:{} excluded: {}", it.id, it.component, it.version, resolution.reason)
                }
            is TargetResolution.Resolved -> {
                licenseReportStore.add(report)
                licenseReportExecutor.execute { generate(report, resolution.target, policy) }
                report
            }
        }
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

    private fun resolve(request: LicenseReportRequest): TargetResolution =
        try {
            licenseTargetResolver.resolve(request.component, request.version)
        } catch (e: ComponentVersionNotFoundException) {
            throw NotFoundException(e.message ?: "Component version ${request.component}:${request.version} is not found", e)
        } catch (e: ComponentsRegistryUnavailableException) {
            log.warn("Cannot start a license report for {}:{}", request.component, request.version, e)
            throw ServiceUnavailableException(e.message ?: "Components registry is unavailable", e)
        }

    private fun generate(
        report: LicenseReport,
        target: LicenseTarget,
        policy: LicensePolicy,
    ) {
        val startedAt = clock.instant()
        val finished = runCatching { licenseReportGenerator.generate(target, policy) }.fold(
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
