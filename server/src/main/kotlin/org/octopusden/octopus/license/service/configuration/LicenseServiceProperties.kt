package org.octopusden.octopus.license.service.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @param reportTtl how long a finished report stays downloadable.
 * @param generationThreads how many generations run at once; the rest queue. Bounds the load the
 * service puts on Xray and Artifactory.
 */
@ConfigurationProperties("license-service")
data class LicenseServiceProperties(
    val reportTtl: Duration = Duration.ofHours(1),
    val generationThreads: Int = 4,
)
