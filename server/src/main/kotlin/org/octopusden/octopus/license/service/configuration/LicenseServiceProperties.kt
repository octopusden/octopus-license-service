package org.octopusden.octopus.license.service.configuration

import org.octopusden.octopus.license.target.ConfiguredExclusion
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @param reportTtl how long a finished report stays downloadable.
 * @param generationThreads how many generations run at once; the rest queue. Bounds the load the
 * service puts on Xray and Artifactory.
 * @param componentsRegistryUrl required; nullable only so its absence is reported by name at startup.
 * @param exclusions components out of scope that no registry fact identifies, each with its reason.
 */
@ConfigurationProperties("license-service")
data class LicenseServiceProperties(
    val reportTtl: Duration = Duration.ofHours(1),
    val generationThreads: Int = 4,
    val componentsRegistryUrl: String? = null,
    val exclusions: List<ConfiguredExclusion> = emptyList(),
)
