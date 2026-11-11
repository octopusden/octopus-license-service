package org.octopusden.octopus.license.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.client.LicenseServiceClient
import org.octopusden.octopus.license.client.impl.ClassicLicenseServiceClient
import org.octopusden.octopus.license.client.impl.LicenseServiceClientParametersProvider
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.service.service.GeneratedReport
import org.octopusden.octopus.license.service.service.LicenseReportGenerator
import org.octopusden.octopus.license.service.service.impl.EmptyLicenseReportGenerator
import org.octopusden.octopus.license.service.state.policy.LicensePolicyHolder
import org.octopusden.octopus.license.service.state.policy.LicensePolicyRefresherTest.FakeLicensePolicySource
import org.octopusden.octopus.license.service.state.report.LicenseReportStore
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.octopus.license.target.model.TargetResolution
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One generation thread and no queue: a second report while the first runs finds the service busy. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "license-service.generation-threads=1",
        "license-service.max-queued-reports=0",
        "license-service.components-registry-url=http://localhost:1",
        "license-service.license-registry.bitbucket-url=http://localhost:1",
        "license-service.license-registry.token=token",
        "license-service.license-registry.project=SAMPLE",
        "license-service.license-registry.repository=sample-license-registry",
    ],
)
class BusyServiceApiTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var generator: BlockingGenerator

    @Autowired
    private lateinit var licenseReportStore: LicenseReportStore

    private val client: LicenseServiceClient by lazy {
        ClassicLicenseServiceClient(
            object : LicenseServiceClientParametersProvider {
                override fun getApiUrl() = "http://localhost:$port"
            },
        )
    }

    @Autowired
    private lateinit var licenseReportExecutor: ThreadPoolTaskExecutor

    @Autowired
    private lateinit var policyHolder: LicensePolicyHolder

    @BeforeEach
    fun awaitPolicyLoaded() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GATE_TIMEOUT_SECONDS)
        while (policyHolder.current() == null && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS)
        }
        requireNotNull(policyHolder.current()) { "the license policy did not load within $GATE_TIMEOUT_SECONDS s" }
    }

    @AfterEach
    fun releaseGeneration() {
        generator.gate.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GATE_TIMEOUT_SECONDS)
        while (licenseReportExecutor.activeCount > 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS)
        }
    }

    @Test
    @DisplayName("a report refused as busy is a ServiceBusyException and leaves nothing in the store")
    fun busyServiceRefusesReport() {
        client.startReport(LicenseReportRequest(COMPONENT, VERSION))
        val stored = licenseReportStore.ids()

        assertThrows<ServiceBusyException> { client.startReport(LicenseReportRequest(COMPONENT, VERSION)) }
        assertEquals(stored, licenseReportStore.ids())
    }

    @Test
    @DisplayName("busy answers 503 with Retry-After in the error envelope")
    fun busyAnswersServiceUnavailable() {
        client.startReport(LicenseReportRequest(COMPONENT, VERSION))

        val response = HttpClient.newHttpClient().send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/rest/api/1/license-reports"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"component":"$COMPONENT","version":"$VERSION"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(HTTP_SERVICE_UNAVAILABLE, response.statusCode())
        assertTrue(response.body().startsWith("""{"error":{"code":503,"""), response.body())
        assertTrue(response.headers().firstValue("Retry-After").isPresent)
    }

    class BlockingGenerator(
        private val delegate: LicenseReportGenerator,
    ) : LicenseReportGenerator {
        @Volatile
        var gate = CountDownLatch(1)

        override fun generate(
            target: LicenseTarget,
            policy: LicensePolicy,
        ): GeneratedReport {
            gate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            gate = CountDownLatch(1)
            return delegate.generate(target, policy)
        }
    }

    @TestConfiguration
    class BlockingGeneratorConfig {
        @Bean
        @Primary
        fun loadedLicensePolicySource(): LicensePolicySource = FakeLicensePolicySource()

        @Bean
        @Primary
        fun blockingGenerator(
            clock: Clock,
            buildProperties: BuildProperties,
        ) = BlockingGenerator(EmptyLicenseReportGenerator(clock, buildProperties))

        @Bean
        @Primary
        fun resolvingTargetResolver() =
            object : LicenseTargetResolver {
                override fun resolve(
                    component: String,
                    version: String,
                ) = TargetResolution.Resolved(
                    LicenseTarget(
                        ComponentVersion(component, version),
                        listOf(version),
                        "GRADLE",
                        emptySet(),
                        true,
                        DistributionSpec(emptyList(), emptyList()),
                    ),
                )
            }
    }

    private companion object {
        const val COMPONENT = "some-component"
        const val VERSION = "1.2.3"
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val GATE_TIMEOUT_SECONDS = 10L
        const val POLL_INTERVAL_MS = 20L
    }
}
