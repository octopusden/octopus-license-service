package org.octopusden.octopus.license.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.client.LicenseServiceClient
import org.octopusden.octopus.license.client.impl.ClassicLicenseServiceClient
import org.octopusden.octopus.license.client.impl.LicenseServiceClientParametersProvider
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.service.service.GeneratedReport
import org.octopusden.octopus.license.service.service.LicenseReportGenerator
import org.octopusden.octopus.license.service.service.impl.EmptyLicenseReportGenerator
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LicenseReportApiTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var buildProperties: BuildProperties

    @Autowired
    private lateinit var generator: ControllableGenerator

    private val client: LicenseServiceClient by lazy {
        ClassicLicenseServiceClient(
            object : LicenseServiceClientParametersProvider {
                override fun getApiUrl() = "http://localhost:$port"
            },
        )
    }

    @AfterEach
    fun releaseSlowGenerations() = generator.release()

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
     * releases it, and [BROKEN_COMPONENT] throws.
     */
    class ControllableGenerator(
        private val delegate: LicenseReportGenerator,
    ) : LicenseReportGenerator {
        @Volatile
        private var slowGate = CountDownLatch(1)

        fun release() {
            slowGate.countDown()
            slowGate = CountDownLatch(1)
        }

        override fun generate(
            component: String,
            version: String,
        ): GeneratedReport =
            when (component) {
                SLOW_COMPONENT -> slowGate.await(AWAIT_TIMEOUT.seconds, TimeUnit.SECONDS).let { delegate.generate(component, version) }
                BROKEN_COMPONENT -> error("Xray is unreachable")
                else -> delegate.generate(component, version)
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
    }

    private companion object {
        const val COMPONENT = "some-component"
        const val SLOW_COMPONENT = "slow-component"
        const val BROKEN_COMPONENT = "broken-component"
        const val VERSION = "1.2.3"
        const val UNKNOWN_ID = "00000000-0000-0000-0000-000000000000"
        const val HTTP_ACCEPTED = 202
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val POLL_INTERVAL_MS = 50L
        val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
