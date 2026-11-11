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

    private fun withAllSettings() = contextRunner.withPropertyValues(*REQUIRED)

    @ParameterizedTest
    @ValueSource(
        strings = [
            "components-registry-service.url",
            "bitbucket.host",
            "bitbucket.token",
            "artifactory.url",
            "artifactory.token",
            "license-service.license-registry.project",
            "license-service.license-registry.repository",
            "license-service.ownership.internal-groups",
            "license-service.ownership.own-repository-prefixes",
        ],
    )
    @DisplayName("without a required setting the service does not start, and says which property is missing")
    fun settingIsRequired(setting: String) {
        contextRunner
            .withPropertyValues(*REQUIRED.filterNot { it.startsWith("$setting=") }.toTypedArray())
            .run { context ->
                val failure = requireNotNull(context.startupFailure) { "the context should fail to start" }
                val messages = generateSequence(failure as Throwable) { it.cause }.mapNotNull { it.message }.toList()
                assertTrue(messages.any { setting in it }, messages.toString())
            }
    }

    @Test
    @DisplayName("the systems are read from the keys every service shares, not from under license-service")
    fun sharedKeys() {
        withAllSettings().run { context ->
            assertEquals(
                ComponentsRegistryServiceProperties("http://localhost:1"),
                context.getBean(ComponentsRegistryServiceProperties::class.java),
            )
            assertEquals(BitbucketProperties("http://localhost:2", "bitbucket-token"), context.getBean(BitbucketProperties::class.java))
            assertEquals(
                ArtifactoryProperties("http://localhost:3", "artifactory-token"),
                context.getBean(ArtifactoryProperties::class.java),
            )
        }
    }

    @Test
    @DisplayName("with every setting the resolver, the policy source, the scanner and the evaluator are wired")
    fun everythingIsWired() {
        withAllSettings().run { context ->
            assertEquals(1, context.getBeansOfType(LicenseTargetResolver::class.java).size)
            assertEquals(1, context.getBeansOfType(LicensePolicySource::class.java).size)
            assertEquals(1, context.getBeansOfType(LicenseInventoryScanner::class.java).size)
            assertEquals(1, context.getBeansOfType(LicenseEvaluator::class.java).size)
        }
    }

    @Test
    @DisplayName("a configured exclusion binds a comma-separated component list and one reason")
    fun configuredExclusionsBind() {
        withAllSettings()
            .withPropertyValues(
                "license-service.exclusions[0].components=test_user_guides, test_admin_guides",
                "license-service.exclusions[0].reason=Documentation component",
            ).run { context ->
                assertEquals(
                    listOf(ConfiguredExclusion(listOf("test_user_guides", "test_admin_guides"), "Documentation component")),
                    context.getBean(LicenseServiceProperties::class.java).exclusions,
                )
            }
    }

    @Test
    @DisplayName("D9: the license registry is checked every 15 minutes, and retried every minute until it loads")
    fun licenseRegistryIntervalDefaults() {
        withAllSettings().run { context ->
            val registry = context.getBean(LicenseServiceProperties::class.java).licenseRegistry
            assertEquals(Duration.ofMinutes(15), registry.refreshInterval)
            assertEquals(Duration.ofMinutes(1), registry.retryInterval)
        }
    }

    @Test
    @DisplayName("D10: Xray is waited for 5 minutes per scan, polled every 10 seconds")
    fun xrayDefaults() {
        withAllSettings().run { context ->
            val xray = context.getBean(LicenseServiceProperties::class.java).xray
            assertEquals(Duration.ofMinutes(5), xray.scanTimeout)
            assertEquals(Duration.ofSeconds(10), xray.scanPollInterval)
        }
    }

    @EnableConfigurationProperties(
        LicenseServiceProperties::class,
        ComponentsRegistryServiceProperties::class,
        BitbucketProperties::class,
        ArtifactoryProperties::class,
    )
    class PropertiesConfig

    private companion object {
        val REQUIRED =
            arrayOf(
                "components-registry-service.url=http://localhost:1",
                "bitbucket.host=http://localhost:2",
                "bitbucket.token=bitbucket-token",
                "artifactory.url=http://localhost:3",
                "artifactory.token=artifactory-token",
                "license-service.license-registry.project=SAMPLE",
                "license-service.license-registry.repository=sample-license-registry",
                "license-service.ownership.internal-groups=org.example",
                "license-service.ownership.own-repository-prefixes=rnd-",
            )
    }
}
