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
    private val componentsRegistryService: ComponentsRegistryServiceProperties,
    private val bitbucket: BitbucketProperties,
    private val artifactory: ArtifactoryProperties,
) {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun licenseReportStore(clock: Clock) = LicenseReportStore(clock, properties.reportTtl, properties.maxStoredBytes.toBytes())

    @Bean
    fun licenseTargetResolver(): LicenseTargetResolver =
        RegistryLicenseTargetResolver(
            requiredSetting(componentsRegistryService.url, "components-registry-service.url"),
            properties.exclusions,
        )

    @Bean
    fun licensePolicySource(): LicensePolicySource =
        with(properties.licenseRegistry) {
            BitbucketLicensePolicySource(
                requiredSetting(bitbucket.host, "bitbucket.host"),
                requiredSetting(bitbucket.token, "bitbucket.token"),
                requiredSetting(project, "license-service.license-registry.project"),
                requiredSetting(repository, "license-service.license-registry.repository"),
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
            requiredSetting(artifactory.url, "artifactory.url"),
            requiredSetting(artifactory.token, "artifactory.token"),
            OwnershipRules(
                requiredList(properties.ownership.internalGroups, "license-service.ownership.internal-groups"),
                requiredList(properties.ownership.ownRepositoryPrefixes, "license-service.ownership.own-repository-prefixes"),
            ),
            properties.xray.scanTimeout,
            properties.xray.scanPollInterval,
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
    ) = requireNotNull(value?.takeIf { it.isNotBlank() }) { "$setting is not set" }

    private fun requiredList(
        values: List<String>,
        setting: String,
    ) = values.map { it.trim() }.filter { it.isNotEmpty() }.also { require(it.isNotEmpty()) { "$setting is not set" } }
}
