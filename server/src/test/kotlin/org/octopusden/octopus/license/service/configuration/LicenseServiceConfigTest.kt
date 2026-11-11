package org.octopusden.octopus.license.service.configuration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.LicenseInventoryScanner
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.model.ConfiguredExclusion
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration

class LicenseServiceConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig::class.java, LicenseServiceConfig::class.java)
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })

    @Test
    @DisplayName("without a components registry URL the service does not start, and says which property is missing")
    fun registryUrlIsRequired() {
        contextRunner.withPropertyValues(*LICENSE_REGISTRY, *JFROG_AND_OWNERSHIP).run { context ->
            val failure = requireNotNull(context.startupFailure) { "the context should fail to start" }
            val messages = generateSequence(failure as Throwable) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(messages.any { "license-service.components-registry-url" in it }, messages.toString())
        }
    }

    @Test
    @DisplayName("with a components registry URL the license target resolver is wired")
    fun resolverIsWired() {
        contextRunner
            .withPropertyValues("license-service.components-registry-url=http://localhost:1", *LICENSE_REGISTRY, *JFROG_AND_OWNERSHIP)
            .run { context -> assertTrue(context.getBeansOfType(LicenseTargetResolver::class.java).size == 1) }
    }

    @Test
    @DisplayName("a configured exclusion binds a comma-separated component list and one reason")
    fun configuredExclusionsBind() {
        contextRunner
            .withPropertyValues(
                "license-service.components-registry-url=http://localhost:1",
                *LICENSE_REGISTRY,
                *JFROG_AND_OWNERSHIP,
                "license-service.exclusions[0].components=test_user_guides, test_admin_guides",
                "license-service.exclusions[0].reason=Documentation component",
            ).run { context ->
                assertEquals(
                    listOf(ConfiguredExclusion(listOf("test_user_guides", "test_admin_guides"), "Documentation component")),
                    context.getBean(LicenseServiceProperties::class.java).exclusions,
                )
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["bitbucket-url", "token", "project", "repository"])
    @DisplayName("D9: without a license registry setting the service does not start, and says which property is missing")
    fun licenseRegistrySettingIsRequired(setting: String) {
        contextRunner
            .withPropertyValues(
                "license-service.components-registry-url=http://localhost:1",
                *LICENSE_REGISTRY.filterNot { it.startsWith("license-service.license-registry.$setting=") }.toTypedArray(),
                *JFROG_AND_OWNERSHIP,
            ).run { context ->
                val failure = requireNotNull(context.startupFailure) { "the context should fail to start" }
                val messages = generateSequence(failure as Throwable) { it.cause }.mapNotNull { it.message }.toList()
                assertTrue(messages.any { "license-service.license-registry.$setting" in it }, messages.toString())
            }
    }

    @Test
    @DisplayName("D9: the license registry is checked every 15 minutes, and retried every minute until it loads")
    fun licenseRegistryIntervalDefaults() {
        contextRunner
            .withPropertyValues("license-service.components-registry-url=http://localhost:1", *LICENSE_REGISTRY, *JFROG_AND_OWNERSHIP)
            .run { context ->
                val registry = context.getBean(LicenseServiceProperties::class.java).licenseRegistry
                assertEquals(Duration.ofMinutes(15), registry.refreshInterval)
                assertEquals(Duration.ofMinutes(1), registry.retryInterval)
                assertTrue(context.getBeansOfType(LicensePolicySource::class.java).size == 1)
            }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "license-service.jfrog.url",
            "license-service.jfrog.token",
            "license-service.ownership.internal-groups",
            "license-service.ownership.own-repository-prefixes",
        ],
    )
    @DisplayName("D10: without a JFrog or ownership setting the service does not start, and says which property is missing")
    fun jfrogAndOwnershipSettingIsRequired(setting: String) {
        contextRunner
            .withPropertyValues(
                "license-service.components-registry-url=http://localhost:1",
                *LICENSE_REGISTRY,
                *JFROG_AND_OWNERSHIP.filterNot { it.startsWith("$setting=") }.toTypedArray(),
            ).run { context ->
                val failure = requireNotNull(context.startupFailure) { "the context should fail to start" }
                val messages = generateSequence(failure as Throwable) { it.cause }.mapNotNull { it.message }.toList()
                assertTrue(messages.any { setting in it }, messages.toString())
            }
    }

    @Test
    @DisplayName(
        "D10: with every setting the scanner and the evaluator are wired, and Xray is waited for 5 minutes, polled every 10 seconds",
    )
    fun scannerIsWired() {
        contextRunner
            .withPropertyValues("license-service.components-registry-url=http://localhost:1", *LICENSE_REGISTRY, *JFROG_AND_OWNERSHIP)
            .run { context ->
                assertTrue(context.getBeansOfType(LicenseInventoryScanner::class.java).size == 1)
                assertTrue(context.getBeansOfType(LicenseEvaluator::class.java).size == 1)
                val jfrog = context.getBean(LicenseServiceProperties::class.java).jfrog
                assertEquals(Duration.ofMinutes(5), jfrog.xrayScanTimeout)
                assertEquals(Duration.ofSeconds(10), jfrog.xrayScanPollInterval)
            }
    }

    @EnableConfigurationProperties(LicenseServiceProperties::class)
    class PropertiesConfig

    private companion object {
        val LICENSE_REGISTRY =
            arrayOf(
                "license-service.license-registry.bitbucket-url=http://localhost:1",
                "license-service.license-registry.token=token",
                "license-service.license-registry.project=SAMPLE",
                "license-service.license-registry.repository=sample-license-registry",
            )
        val JFROG_AND_OWNERSHIP =
            arrayOf(
                "license-service.jfrog.url=http://localhost:1",
                "license-service.jfrog.token=token",
                "license-service.ownership.internal-groups=org.example",
                "license-service.ownership.own-repository-prefixes=rnd-",
            )
    }
}
