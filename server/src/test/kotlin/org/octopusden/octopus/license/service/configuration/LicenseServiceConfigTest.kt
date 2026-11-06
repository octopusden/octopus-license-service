package org.octopusden.octopus.license.service.configuration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.target.ConfiguredExclusion
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class LicenseServiceConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig::class.java, LicenseServiceConfig::class.java)

    @Test
    @DisplayName("without a components registry URL the service does not start, and says which property is missing")
    fun registryUrlIsRequired() {
        contextRunner.run { context ->
            val failure = requireNotNull(context.startupFailure) { "the context should fail to start" }
            val messages = generateSequence(failure as Throwable) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(messages.any { "license-service.components-registry-url" in it }, messages.toString())
        }
    }

    @Test
    @DisplayName("with a components registry URL the license target resolver is wired")
    fun resolverIsWired() {
        contextRunner
            .withPropertyValues("license-service.components-registry-url=http://localhost:1")
            .run { context -> assertTrue(context.getBeansOfType(LicenseTargetResolver::class.java).size == 1) }
    }

    @Test
    @DisplayName("application.yaml seeds the exclusions with documentation components not named doc_ or doc-")
    fun seededExclusions() {
        contextRunner
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withPropertyValues("license-service.components-registry-url=http://localhost:1")
            .run { context ->
                val exclusions = context.getBean(LicenseServiceProperties::class.java).exclusions
                assertTrue(ConfiguredExclusion("cards_manuals", "Documentation component") in exclusions, exclusions.toString())
                assertTrue(exclusions.size == SEEDED_EXCLUSIONS, exclusions.toString())
            }
    }

    @EnableConfigurationProperties(LicenseServiceProperties::class)
    class PropertiesConfig

    private companion object {
        const val SEEDED_EXCLUSIONS = 11
    }
}
