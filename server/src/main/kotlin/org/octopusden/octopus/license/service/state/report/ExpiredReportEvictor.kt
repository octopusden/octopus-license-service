package org.octopusden.octopus.license.service.state.report

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Drops expired reports nobody asked for again; [LicenseReportStore.find] already hides them. */
@Component
class ExpiredReportEvictor(
    private val licenseReportStore: LicenseReportStore,
) {
    @Scheduled(fixedDelayString = "PT1M")
    fun evictExpired() = licenseReportStore.evictExpired()
}
