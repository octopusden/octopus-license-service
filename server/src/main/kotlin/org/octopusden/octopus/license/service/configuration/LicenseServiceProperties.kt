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
 * oldest finished reports that hold files go before their TTL ends.
 * @param maxStoredReports how many finished reports are kept; past it, the oldest go before their
 * TTL ends. Bounds memory where the byte limit does not: reports without files.
 * @param exclusions components out of scope that no registry fact identifies, grouped by reason.
 * @param componentsRegistryDeadline how long starting a report waits for the components registry;
 * past it, the start answers unavailable. Below the published client's 60 s read timeout, so the
 * caller gets that answer instead of giving up on a start that may still register a report.
 * @param licenseRegistry where the license policy is read from (D9).
 * @param xray how long a scan waits for Xray to finish scanning.
 * @param ownership what is ours, so the scan follows our archives and evaluation calls them internal.
 * @param licenseTexts how the license texts the registry gives only as a URL are downloaded.
 */
@ConfigurationProperties("license-service")
data class LicenseServiceProperties(
    val reportTtl: Duration = Duration.ofHours(1),
    val generationThreads: Int = 4,
    val maxQueuedReports: Int = 50,
    val maxStoredBytes: DataSize = DataSize.ofMegabytes(512),
    val maxStoredReports: Int = 500,
    val exclusions: List<ConfiguredExclusion> = emptyList(),
    val componentsRegistryDeadline: Duration = Duration.ofSeconds(45),
    val licenseRegistry: LicenseRegistryProperties = LicenseRegistryProperties(),
    val xray: XrayProperties = XrayProperties(),
    val ownership: OwnershipProperties = OwnershipProperties(),
    val licenseTexts: LicenseTextsProperties = LicenseTextsProperties(),
)

/**
 * @param downloadTimeout how long one text's download may take, connecting and its body included.
 * @param failureRetryAfter how long a failed download is not tried again.
 */
data class LicenseTextsProperties(
    val downloadTimeout: Duration = Duration.ofSeconds(10),
    val failureRetryAfter: Duration = Duration.ofMinutes(5),
)

/**
 * Where the license registry sits on Bitbucket; Bitbucket itself is [BitbucketProperties]. Both are
 * required; nullable only so their absence is reported by name at startup. They come from Spring
 * Cloud Config, never from this repository.
 *
 * @param refreshInterval how often the head commit is checked once a policy is loaded.
 * @param retryInterval how often a load is tried until the first one succeeds.
 */
data class LicenseRegistryProperties(
    val project: String? = null,
    val repository: String? = null,
    val refreshInterval: Duration = Duration.ofMinutes(15),
    val retryInterval: Duration = Duration.ofMinutes(1),
)

/**
 * Xray is reached at [ArtifactoryProperties.url], the JFrog platform's; only the wait is this
 * service's own.
 *
 * @param scanTimeout how long a FULL report's scan waits, in all, for Xray to finish scanning its
 * files; a CHECK report does not wait.
 * @param scanPollInterval how often a pending Xray scan is asked about again.
 */
data class XrayProperties(
    val scanTimeout: Duration = Duration.ofMinutes(5),
    val scanPollInterval: Duration = Duration.ofSeconds(10),
)

/**
 * What is ours, from Spring Cloud Config: company names stay out of this public repository. Both
 * are required and must not be empty.
 *
 * @param internalGroups Maven group prefixes.
 * @param ownRepositoryPrefixes repository name prefixes; a repository is ours when its name also ends with `local`.
 */
data class OwnershipProperties(
    val internalGroups: List<String> = emptyList(),
    val ownRepositoryPrefixes: List<String> = emptyList(),
)
