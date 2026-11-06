package org.octopusden.octopus.license.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.client.LicenseServiceClient
import org.octopusden.octopus.license.client.impl.ClassicLicenseServiceClient
import org.octopusden.octopus.license.client.impl.LicenseServiceClientParametersProvider
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceUnavailableException
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.service.report.GeneratedReport
import org.octopusden.octopus.license.service.report.LicenseReportGenerator
import org.octopusden.octopus.license.service.report.impl.EmptyLicenseReportGenerator
import org.octopusden.octopus.license.service.state.policy.LicensePolicyHolder
import org.octopusden.octopus.license.service.state.policy.LicensePolicyRefresherTest.Companion.samplePolicy
import org.octopusden.octopus.license.service.state.policy.LicensePolicyRefresherTest.FakeLicensePolicySource
import org.octopusden.octopus.license.service.state.policy.LoadedPolicy
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.exception.ComponentVersionNotFoundException
import org.octopusden.octopus.license.target.exception.ComponentsRegistryUnavailableException
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
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "license-service.components-registry-url=http://localhost:1",
        "license-service.license-registry.bitbucket-url=http://localhost:1",
        "license-service.license-registry.token=token",
        "license-service.license-registry.project=SAMPLE",
        "license-service.license-registry.repository=sample-license-registry",
    ],
)
class LicenseReportApiTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var buildProperties: BuildProperties

    @Autowired
    private lateinit var generator: ControllableGenerator

    @Autowired
    private lateinit var policyHolder: LicensePolicyHolder

    private val client: LicenseServiceClient by lazy {
        ClassicLicenseServiceClient(
            object : LicenseServiceClientParametersProvider {
                override fun getApiUrl() = "http://localhost:$port"
            },
        )
    }

    @BeforeEach
    fun awaitPolicyLoaded() {
        val deadline = Instant.now().plus(AWAIT_TIMEOUT)
        while (policyHolder.current() == null && Instant.now().isBefore(deadline)) {
            TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS)
        }
        requireNotNull(policyHolder.current()) { "the license policy did not load within $AWAIT_TIMEOUT" }
    }

    @AfterEach
    fun releaseSlowGenerations() = generator.release()

    @Test
    @DisplayName("SYS-007: the summary records the license registry commit the report was judged with")
    fun summaryRecordsRegistryCommit() {
        val done = awaitFinished(client.startReport(LicenseReportRequest(COMPONENT, VERSION)).id)

        assertEquals(REGISTRY_COMMIT, requireNotNull(done.summary).inputs.licenseRegistryCommit)
    }

    @Test
    @DisplayName("D7: a reload during a generation does not change the commit that generation records")
    fun reloadDuringGeneration() {
        val loaded = requireNotNull(policyHolder.current())
        val started = client.startReport(LicenseReportRequest(SLOW_COMPONENT, VERSION))
        try {
            policyHolder.replace(LoadedPolicy(samplePolicy("newer-commit"), Instant.now()))
            generator.release()

            assertEquals(REGISTRY_COMMIT, requireNotNull(awaitFinished(started.id).summary).inputs.licenseRegistryCommit)
        } finally {
            policyHolder.replace(loaded)
        }
    }

    @Test
    @DisplayName("D7: the health details show the loaded commit, licenses without text and problems")
    fun healthShowsPolicy() {
        val response = send(HttpRequest.newBuilder(URI("http://localhost:$port/actuator/health/licenseRegistry")).GET())

        assertEquals(HTTP_OK, response.statusCode(), response.body())
        assertTrue(response.body().contains(""""commit":"$REGISTRY_COMMIT""""), response.body())
        assertTrue(response.body().contains(""""licensesWithoutText":["Apache-2.0","MIT"]"""), response.body())
        assertTrue(response.body().contains(""""problems":[]"""), response.body())
    }

    @Test
    @DisplayName("the generation receives the license target the registry resolved for the version")
    fun generationReceivesTarget() {
        awaitFinished(client.startReport(LicenseReportRequest(COMPONENT, VERSION)).id)

        assertTrue(targetOf(COMPONENT) in generator.targets, "generated for ${generator.targets}")
    }

    @Test
    @DisplayName("an excluded component is EXCLUDED at once with the reason, and nothing is generated")
    fun excludedComponent() {
        val started = client.startReport(LicenseReportRequest(EXCLUDED_COMPONENT, VERSION))

        assertEquals(LicenseReportStatus.EXCLUDED, started.status)
        assertEquals(EXCLUSION_REASON, started.exclusionReason)
        assertEquals(started, client.getReport(started.id))
        assertTrue(generator.targets.none { it.componentVersion.component == EXCLUDED_COMPONENT })
        assertThrows<LicenseFilesNotAvailableException> { client.downloadFiles(started.id) }
    }

    @Test
    @DisplayName("an unknown component version answers 404 in the error envelope")
    fun unknownComponentVersion() {
        val response = send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/rest/api/1/license-reports"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"component":"$UNKNOWN_COMPONENT","version":"$VERSION"}""")),
        )

        assertEquals(HTTP_NOT_FOUND, response.statusCode())
        assertTrue(response.body().startsWith("""{"error":{"code":404,"""), response.body())
        assertThrows<NotFoundException> { client.startReport(LicenseReportRequest(UNKNOWN_COMPONENT, VERSION)) }
    }

    @Test
    @DisplayName("an unreachable components registry answers 503, a ServiceUnavailableException for the client")
    fun registryUnavailable() {
        assertThrows<ServiceUnavailableException> {
            client.startReport(LicenseReportRequest(REGISTRY_DOWN_COMPONENT, VERSION))
        }
    }

    @Test
    @DisplayName("a started report finishes DONE with an empty summary and an empty zip")
    fun reportFinishesWithEmptyResult() {
        val started = client.startReport(LicenseReportRequest(COMPONENT, VERSION))

        val done = awaitFinished(started.id)
        val files = client.downloadFiles(started.id)

        assertEquals(LicenseReportStatus.RUNNING, started.status)
        assertEquals(LicenseReportStatus.DONE, done.status)
        val summary = requireNotNull(done.summary) { "a DONE report carries a summary" }
        assertEquals(buildProperties.version, summary.inputs.serviceVersion)
        assertTrue(summary.libraryCounts.isEmpty())
        assertTrue(summary.violations.isEmpty())
        assertNull(ZipInputStream(ByteArrayInputStream(files)).nextEntry, "the zip holds no entries")
    }

    @Test
    @DisplayName("SYS-007: the summary records when it was generated")
    fun summaryRecordsGenerationTime() {
        val before = Instant.now()

        val done = awaitFinished(client.startReport(LicenseReportRequest(COMPONENT, VERSION)).id)

        val generatedAt = requireNotNull(done.summary).inputs.generatedAt
        assertTrue(!generatedAt.isBefore(before.minusSeconds(1)), "generatedAt $generatedAt is before the request")
    }

    @Test
    @DisplayName("POST answers 202 with the report's URL in Location")
    fun startAnswersAcceptedWithLocation() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/rest/api/1/license-reports"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"component":"$COMPONENT","version":"$VERSION"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(HTTP_ACCEPTED, response.statusCode())
        val location = response.headers().firstValue("Location").orElse(null)
        assertNotNull(location)
        assertTrue(location.matches(Regex("http://localhost:$port/rest/api/1/license-reports/[0-9a-f-]{36}")), location)
    }

    @Test
    @DisplayName("Spring's own errors keep their status in the error envelope: unknown path 404, wrong method 405")
    fun springErrorsKeepTheirStatus() {
        val unknownPath = send(HttpRequest.newBuilder(URI("http://localhost:$port/rest/api/1/no-such-resource")).GET())
        val wrongMethod = send(HttpRequest.newBuilder(URI("http://localhost:$port/rest/api/1/license-reports")).DELETE())

        assertEquals(HTTP_NOT_FOUND, unknownPath.statusCode())
        assertTrue(unknownPath.body().startsWith("""{"error":{"code":404,"""), unknownPath.body())
        assertEquals(HTTP_METHOD_NOT_ALLOWED, wrongMethod.statusCode())
        assertTrue(wrongMethod.body().startsWith("""{"error":{"code":405,"""), wrongMethod.body())
    }

    @Test
    @DisplayName("behind the gateway, the root redirects to Swagger UI under the gateway's prefix")
    fun rootRedirectsToSwaggerUi() {
        val response = send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/"))
                .header("X-Forwarded-Prefix", "/license-service")
                .GET(),
        )

        assertEquals(HTTP_FOUND, response.statusCode())
        assertEquals(
            "http://localhost:$port/license-service/swagger-ui/index.html",
            response.headers().firstValue("Location").orElse(null),
        )
    }

    @Test
    @DisplayName("behind the gateway, Swagger UI loads the API docs under the gateway's prefix")
    fun swaggerUiHonoursForwardedPrefix() {
        val response = send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/v3/api-docs/swagger-config"))
                .header("X-Forwarded-Prefix", "/license-service")
                .GET(),
        )

        assertEquals(HTTP_OK, response.statusCode(), response.body())
        assertTrue(response.body().contains(""""url":"/license-service/v3/api-docs""""), response.body())
    }

    @Test
    @DisplayName("a request with a missing field answers 400 in the error envelope")
    fun missingFieldIsBadRequest() {
        val response = send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/rest/api/1/license-reports"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"component":"$COMPONENT"}""")),
        )

        assertEquals(HTTP_BAD_REQUEST, response.statusCode())
        assertTrue(response.body().startsWith("""{"error":{"code":400,"""), response.body())
    }

    @Test
    @DisplayName("the download is a zip attachment named after the component version")
    fun downloadIsNamedZipAttachment() {
        val id = awaitFinished(client.startReport(LicenseReportRequest(COMPONENT, VERSION)).id).id

        val response = send(HttpRequest.newBuilder(URI("http://localhost:$port/rest/api/1/license-reports/$id/files")).GET())

        assertEquals(HTTP_OK, response.statusCode())
        assertEquals("application/zip", response.headers().firstValue("Content-Type").orElse(null))
        assertEquals(
            "attachment; filename=\"$COMPONENT-$VERSION-licenses.zip\"",
            response.headers().firstValue("Content-Disposition").orElse(null),
        )
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/prometheus"])
    @DisplayName("the OKD probes and the metrics scrape answer 200 without credentials")
    fun operationalEndpointsAreOpen(path: String) {
        val response = send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET())

        assertEquals(HTTP_OK, response.statusCode(), response.body())
    }

    @Test
    @DisplayName("an unknown report id is a NotFoundException on every call")
    fun unknownIdIsNotFound() {
        assertThrows<NotFoundException> { client.getReport(UNKNOWN_ID) }
        assertThrows<NotFoundException> { client.downloadFiles(UNKNOWN_ID) }
    }

    @Test
    @DisplayName("a blank component or version is an InvalidRequestException")
    fun blankRequestIsInvalid() {
        assertThrows<InvalidRequestException> { client.startReport(LicenseReportRequest(" ", VERSION)) }
        assertThrows<InvalidRequestException> { client.startReport(LicenseReportRequest(COMPONENT, "")) }
    }

    @Test
    @DisplayName("files of a RUNNING report are not available yet")
    fun filesOfRunningReportNotAvailable() {
        val started = client.startReport(LicenseReportRequest(SLOW_COMPONENT, VERSION))

        assertEquals(LicenseReportStatus.RUNNING, client.getReport(started.id).status)
        assertThrows<LicenseFilesNotAvailableException> { client.downloadFiles(started.id) }
    }

    @Test
    @DisplayName("a failing generation finishes FAILED with the error and no files")
    fun failingGenerationIsFailed() {
        val started = client.startReport(LicenseReportRequest(BROKEN_COMPONENT, VERSION))

        val failed = awaitFinished(started.id)

        assertEquals(LicenseReportStatus.FAILED, failed.status)
        assertNotNull(failed.error)
        assertNull(failed.summary)
        assertThrows<LicenseFilesNotAvailableException> { client.downloadFiles(started.id) }
    }

    private fun send(request: HttpRequest.Builder): HttpResponse<String> =
        HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString())

    private fun awaitFinished(id: String): LicenseReport {
        val deadline = Instant.now().plus(AWAIT_TIMEOUT)
        while (Instant.now().isBefore(deadline)) {
            client.getReport(id).takeIf { it.status != LicenseReportStatus.RUNNING }?.let { return it }
            TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS)
        }
        error("License report $id did not finish within $AWAIT_TIMEOUT")
    }

    /**
     * The real generator for every component but two: [SLOW_COMPONENT] waits until the test
     * releases it, and [BROKEN_COMPONENT] throws. Records every target it was given.
     */
    class ControllableGenerator(
        private val delegate: LicenseReportGenerator,
    ) : LicenseReportGenerator {
        val targets = CopyOnWriteArrayList<LicenseTarget>()

        @Volatile
        private var slowGate = CountDownLatch(1)

        fun release() {
            slowGate.countDown()
            slowGate = CountDownLatch(1)
        }

        override fun generate(
            target: LicenseTarget,
            policy: LicensePolicy,
        ): GeneratedReport {
            targets += target
            return when (target.componentVersion.component) {
                SLOW_COMPONENT -> slowGate.await(AWAIT_TIMEOUT.seconds, TimeUnit.SECONDS).let { delegate.generate(target, policy) }
                BROKEN_COMPONENT -> error("Xray is unreachable")
                else -> delegate.generate(target, policy)
            }
        }
    }

    /** Stands in for the components registry: two components fail, one is excluded, the rest resolve. */
    class FakeTargetResolver : LicenseTargetResolver {
        override fun resolve(
            component: String,
            version: String,
        ): TargetResolution =
            when (component) {
                UNKNOWN_COMPONENT -> throw ComponentVersionNotFoundException(
                    "Component version $component:$version is not found",
                    IllegalStateException("registry answered 404"),
                )
                REGISTRY_DOWN_COMPONENT -> throw ComponentsRegistryUnavailableException(
                    "Components registry is unavailable",
                    IllegalStateException("connection refused"),
                )
                EXCLUDED_COMPONENT -> TargetResolution.Excluded(ComponentVersion(component, version), EXCLUSION_REASON)
                else -> TargetResolution.Resolved(targetOf(component))
            }
    }

    @TestConfiguration
    class ControllableGeneratorConfig {
        @Bean
        @Primary
        fun controllableGenerator(
            clock: Clock,
            buildProperties: BuildProperties,
        ) = ControllableGenerator(EmptyLicenseReportGenerator(clock, buildProperties))

        @Bean
        @Primary
        fun fakeTargetResolver(): LicenseTargetResolver = FakeTargetResolver()

        @Bean
        @Primary
        fun fakeLicensePolicySource(): LicensePolicySource = FakeLicensePolicySource(REGISTRY_COMMIT)
    }

    private companion object {
        const val COMPONENT = "some-component"
        const val SLOW_COMPONENT = "slow-component"
        const val BROKEN_COMPONENT = "broken-component"
        const val EXCLUDED_COMPONENT = "doc_user_guide"
        const val EXCLUSION_REASON = "Documentation component"
        const val UNKNOWN_COMPONENT = "no-such-component"
        const val REGISTRY_DOWN_COMPONENT = "registry-down-component"
        const val VERSION = "1.2.3"
        const val REGISTRY_COMMIT = "a1b2c3d"
        const val UNKNOWN_ID = "00000000-0000-0000-0000-000000000000"
        const val HTTP_OK = 200
        const val HTTP_ACCEPTED = 202
        const val HTTP_FOUND = 302
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val POLL_INTERVAL_MS = 50L
        val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(10)

        fun targetOf(component: String) =
            LicenseTarget(
                componentVersion = ComponentVersion(component, VERSION),
                artifactVersions = listOf(VERSION),
                buildSystem = "GRADLE",
                labels = emptySet(),
                distribution = DistributionSpec(declared = emptyList(), patterns = emptyList()),
            )
    }
}
