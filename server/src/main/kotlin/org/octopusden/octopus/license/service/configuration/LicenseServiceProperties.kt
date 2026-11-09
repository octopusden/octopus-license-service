package org.octopusden.octopus.license.service.configuration

import org.octopusden.octopus.license.target.model.ConfiguredExclusion
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

/**
 * @param reportTtl how long a finished report stays downloadable.
 * @param generationThreads how many generations run at once. Bounds the load the service puts on
 * Xray and Artifactory.
 * @param maxQueuedReports how many generations wait for a thread; past it, a new report is refused
 * as busy.
 * @param maxStoredBytes how much license files the finished reports hold in memory; past it, the
 * oldest finished reports go before their TTL ends.
 * @param componentsRegistryUrl required; nullable only so its absence is reported by name at startup.
 * @param exclusions components out of scope that no registry fact identifies, grouped by reason.
 */
@ConfigurationProperties("license-service")
data class LicenseServiceProperties(
    val reportTtl: Duration = Duration.ofHours(1),
    val generationThreads: Int = 4,
    val maxQueuedReports: Int = 50,
    val maxStoredBytes: DataSize = DataSize.ofMegabytes(512),
    val componentsRegistryUrl: String? = null,
    val exclusions: List<ConfiguredExclusion> = emptyList(),
)
