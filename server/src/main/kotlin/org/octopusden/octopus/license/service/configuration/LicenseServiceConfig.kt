package org.octopusden.octopus.license.service.configuration

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
    fun licenseReportExecutor() =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = properties.generationThreads
            maxPoolSize = properties.generationThreads
            setThreadNamePrefix("license-report-")
        }
}
