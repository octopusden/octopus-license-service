package org.octopusden.octopus.license.service.configuration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.model.ConfiguredExclusion
import org.springframework.boot.context.properties.EnableConfigurationProperties
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
    @DisplayName("a configured exclusion binds a comma-separated component list and one reason")
    fun configuredExclusionsBind() {
        contextRunner
            .withPropertyValues(
                "license-service.components-registry-url=http://localhost:1",
                "license-service.exclusions[0].components=test_user_guides, test_admin_guides",
                "license-service.exclusions[0].reason=Documentation component",
            ).run { context ->
                assertEquals(
                    listOf(ConfiguredExclusion(listOf("test_user_guides", "test_admin_guides"), "Documentation component")),
                    context.getBean(LicenseServiceProperties::class.java).exclusions,
                )
            }
    }

    @EnableConfigurationProperties(LicenseServiceProperties::class)
    class PropertiesConfig
}
