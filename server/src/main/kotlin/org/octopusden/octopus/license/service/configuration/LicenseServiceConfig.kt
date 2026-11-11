package org.octopusden.octopus.license.service.configuration

import io.micrometer.core.instrument.MeterRegistry
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.inventory.impl.JfrogLicenseInventoryScanner
import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.policy.impl.BitbucketLicensePolicySource
import org.octopusden.octopus.license.service.state.policy.LicensePolicyHolder
import org.octopusden.octopus.license.service.state.policy.LicensePolicyRefresher
import org.octopusden.octopus.license.service.state.report.LicenseReportStore
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.impl.RegistryLicenseTargetResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Clock

@Configuration
@EnableScheduling
class LicenseServiceConfig(
    private val properties: LicenseServiceProperties,
) {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun licenseReportStore(clock: Clock) = LicenseReportStore(clock, properties.reportTtl, properties.maxStoredBytes.toBytes())

    @Bean
    fun licenseTargetResolver(): LicenseTargetResolver =
        RegistryLicenseTargetResolver(
            requireNotNull(properties.componentsRegistryUrl?.takeIf { it.isNotBlank() }) {
                "license-service.components-registry-url is not set"
            },
            properties.exclusions,
        )

    @Bean
    fun licensePolicySource(): LicensePolicySource =
        with(properties.licenseRegistry) {
            BitbucketLicensePolicySource(
                requiredSetting(bitbucketUrl, "license-registry.bitbucket-url"),
                requiredSetting(token, "license-registry.token"),
                requiredSetting(project, "license-registry.project"),
                requiredSetting(repository, "license-registry.repository"),
            )
        }

    @Bean
    fun licensePolicyHolder() = LicensePolicyHolder()

    @Bean
    fun licensePolicyRefresher(
        source: LicensePolicySource,
        holder: LicensePolicyHolder,
        clock: Clock,
        meterRegistry: MeterRegistry,
    ) = LicensePolicyRefresher(source, holder, properties.licenseRegistry.refreshInterval, clock, meterRegistry)

    /** One scanner for every generation thread: it keeps no state between scans. */
    @Bean
    fun licenseInventoryScanner(): LicenseInventoryScanner =
        JfrogLicenseInventoryScanner(
            requiredSetting(properties.jfrog.url, "jfrog.url"),
            requiredSetting(properties.jfrog.token, "jfrog.token"),
            OwnershipRules(
                requiredList(properties.ownership.internalGroups, "ownership.internal-groups"),
                requiredList(properties.ownership.ownRepositoryPrefixes, "ownership.own-repository-prefixes"),
            ),
            properties.jfrog.xrayScanTimeout,
            properties.jfrog.xrayScanPollInterval,
        )

    @Bean
    fun licenseEvaluator() = LicenseEvaluator()

    @Bean
    fun licenseReportExecutor() =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = properties.generationThreads
            maxPoolSize = properties.generationThreads
            queueCapacity = properties.maxQueuedReports
            setThreadNamePrefix("license-report-")
        }

    private fun requiredSetting(
        value: String?,
        setting: String,
    ) = requireNotNull(value?.takeIf { it.isNotBlank() }) { "license-service.$setting is not set" }

    private fun requiredList(
        values: List<String>,
        setting: String,
    ) = values.map { it.trim() }.filter { it.isNotEmpty() }.also { require(it.isNotEmpty()) { "license-service.$setting is not set" } }
}
