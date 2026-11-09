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
 * @param licenseRegistry where the license policy is read from (D9).
 */
@ConfigurationProperties("license-service")
data class LicenseServiceProperties(
    val reportTtl: Duration = Duration.ofHours(1),
    val generationThreads: Int = 4,
    val maxQueuedReports: Int = 50,
    val maxStoredBytes: DataSize = DataSize.ofMegabytes(512),
    val componentsRegistryUrl: String? = null,
    val exclusions: List<ConfiguredExclusion> = emptyList(),
    val licenseRegistry: LicenseRegistryProperties = LicenseRegistryProperties(),
)

/**
 * The license registry on Bitbucket. The four locations are required; nullable only so their
 * absence is reported by name at startup. Project and repository come from Spring Cloud Config,
 * never from this repository.
 *
 * @param refreshInterval how often the head commit is checked once a policy is loaded.
 * @param retryInterval how often a load is tried until the first one succeeds.
 */
data class LicenseRegistryProperties(
    val bitbucketUrl: String? = null,
    val token: String? = null,
    val project: String? = null,
    val repository: String? = null,
    val refreshInterval: Duration = Duration.ofMinutes(15),
    val retryInterval: Duration = Duration.ofMinutes(1),
)
