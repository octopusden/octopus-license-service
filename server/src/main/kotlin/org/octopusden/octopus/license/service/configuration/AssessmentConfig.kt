package org.octopusden.octopus.license.service.configuration

import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.files.LicenseFilesWriter
import org.octopusden.octopus.license.trust.TrustGrader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The steps that judge a scan: they read no system and take no setting, so nothing here can fail to
 * start. Each keeps no state, so one instance serves every generation thread.
 */
@Configuration
class AssessmentConfig {
    @Bean
    fun licenseEvaluator() = LicenseEvaluator()

    @Bean
    fun trustGrader() = TrustGrader()

    @Bean
    fun licenseFilesWriter() = LicenseFilesWriter()
}
