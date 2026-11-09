package org.octopusden.octopus.license.service.configuration

import io.micrometer.core.instrument.MeterRegistry
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
    fun licenseReportStore(clock: Clock) = LicenseReportStore(clock, properties.reportTtl)

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
                required(bitbucketUrl, "bitbucket-url"),
                required(token, "token"),
                required(project, "project"),
                required(repository, "repository"),
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

    @Bean
    fun licenseReportExecutor() =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = properties.generationThreads
            maxPoolSize = properties.generationThreads
            setThreadNamePrefix("license-report-")
        }

    private fun required(
        value: String?,
        setting: String,
    ) = requireNotNull(value?.takeIf { it.isNotBlank() }) { "license-service.license-registry.$setting is not set" }
}
