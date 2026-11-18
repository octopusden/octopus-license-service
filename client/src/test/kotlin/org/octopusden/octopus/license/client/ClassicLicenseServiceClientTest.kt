package org.octopusden.octopus.license.client

import com.sun.net.httpserver.HttpServer
import feign.FeignException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.client.impl.ClassicLicenseServiceClient
import org.octopusden.octopus.license.client.impl.LicenseServiceClientParametersProvider
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.TrustLevel
import org.octopusden.octopus.license.core.dto.TrustReasonCode
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.octopusden.octopus.license.core.exception.ServiceUnavailableException
import org.octopusden.octopus.license.core.exception.ServiceUnreachableException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant

/**
 * The client against a stub server answering recorded bodies; the server module's API test covers
 * the client against the real service.
 */
class ClassicLicenseServiceClientTest {
    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply { start() }
    private val client = ClassicLicenseServiceClient(
        object : LicenseServiceClientParametersProvider {
            override fun getApiUrl() = "http://localhost:${server.address.port}"
        },
    )

    @AfterEach
    fun stopServer() = server.stop(0)

    @Test
    @DisplayName("a DONE report decodes with its summary and trust, ignoring fields the client does not know")
    fun decodesDoneReport() {
        answer(
            "/rest/api/1/license-reports/$REPORT_ID",
            HTTP_OK,
            """
            {"id":"$REPORT_ID","component":"c","version":"1","status":"DONE","newField":true,
             "summary":{"inputs":{"licenseRegistryCommit":"abc123","generatedAt":"2026-11-04T10:00:00Z",
             "serviceVersion":"1.0.0"},"trust":{"level":"MEDIUM","reasons":[{"code":"NO_BUILD_RECORD","level":"MEDIUM","title":"No build record",
             "detail":"no build for: app.jar","meaning":"m","fix":"f"}],"files":[{"file":"app.jar","level":"MEDIUM",
             "reasons":["NO_BUILD_RECORD"]}]},"libraryCounts":{"ALLOWED":3},"violations":[]}}
            """.trimIndent(),
        )

        val report = client.getReport(REPORT_ID)

        assertEquals(LicenseReportStatus.DONE, report.status)
        val summary = requireNotNull(report.summary)
        assertEquals(Instant.parse("2026-11-04T10:00:00Z"), summary.inputs.generatedAt)
        assertEquals(mapOf(Verdict.ALLOWED to 3), summary.libraryCounts)
        assertEquals(TrustLevel.MEDIUM, summary.trust.level)
        assertEquals(
            listOf(TrustReasonCode.NO_BUILD_RECORD),
            summary.trust.files
                .single()
                .reasons,
        )
    }

    @Test
    @DisplayName("the files come back as the raw zip bytes")
    fun downloadsRawBytes() {
        val zip = byteArrayOf(0x50, 0x4b, 0x05, 0x06)
        server.createContext("/rest/api/1/license-reports/$REPORT_ID/files") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/zip")
            exchange.sendResponseHeaders(HTTP_OK, zip.size.toLong())
            exchange.responseBody.use { it.write(zip) }
        }

        assertEquals(zip.toList(), client.downloadFiles(REPORT_ID).toList())
    }

    @Test
    @DisplayName("the error envelope maps 400, 404, 409 and a 503 with Retry-After to the shared exception types")
    fun mapsErrorEnvelope() {
        answerError("/rest/api/1/license-reports", HTTP_BAD_REQUEST)
        answerError("/rest/api/1/license-reports/missing", HTTP_NOT_FOUND)
        answerError("/rest/api/1/license-reports/running/files", HTTP_CONFLICT)
        answerError("/rest/api/1/license-reports/busy", HTTP_SERVICE_UNAVAILABLE, mapOf("Retry-After" to "60"))

        assertThrows<InvalidRequestException> { client.startReport(LicenseReportRequest("c", "1")) }
        assertThrows<NotFoundException> { client.getReport("missing") }
        assertThrows<LicenseFilesNotAvailableException> { client.downloadFiles("running") }
        assertThrows<ServiceBusyException> { client.getReport("busy") }
    }

    @Test
    @DisplayName("a 503 without Retry-After means a system the service reads is unavailable, not that it is busy")
    fun mapsServiceUnavailable() {
        answerError("/rest/api/1/license-reports", HTTP_SERVICE_UNAVAILABLE)

        assertThrows<ServiceUnavailableException> { client.startReport(LicenseReportRequest("c", "1")) }
    }

    @Test
    @DisplayName("an answer without the error envelope stays a plain Feign error")
    fun nonEnvelopeErrorStaysFeignError() {
        answer("/rest/api/1/license-reports/$REPORT_ID", HTTP_INTERNAL_SERVER_ERROR, "<html>Internal Server Error</html>")

        val error = assertThrows<FeignException> { client.getReport(REPORT_ID) }

        assertEquals("<html>Internal Server Error</html>", error.contentUTF8())
    }

    @Test
    @DisplayName("a gateway error without the envelope is ServiceUnreachableException: it is worth asking again")
    fun gatewayErrorIsUnreachable() {
        answer("/rest/api/1/license-reports/$REPORT_ID", HTTP_BAD_GATEWAY, "<html>Bad Gateway</html>")

        assertThrows<ServiceUnreachableException> { client.getReport(REPORT_ID) }
    }

    @Test
    @DisplayName("a refused connection is ServiceUnreachableException, after one attempt: the caller decides on retries")
    fun refusedConnectionIsUnreachable() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val unreachable = ClassicLicenseServiceClient(
            object : LicenseServiceClientParametersProvider {
                override fun getApiUrl() = "http://localhost:$closedPort"
            },
        )

        val started = System.nanoTime()
        assertThrows<ServiceUnreachableException> { unreachable.getReport(REPORT_ID) }

        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(1), "no hidden retries")
    }

    @Test
    @DisplayName("a busy answer carries the Retry-After the service asked for")
    fun busyCarriesRetryAfter() {
        server.createContext("/rest/api/1/license-reports") { exchange ->
            val bytes = """{"error":{"code":503,"message":"busy"}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Retry-After", "60")
            exchange.sendResponseHeaders(HTTP_SERVICE_UNAVAILABLE, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val error = assertThrows<ServiceBusyException> { client.startReport(LicenseReportRequest("c", "1")) }

        assertEquals(Duration.ofSeconds(RETRY_AFTER_SECONDS), error.retryAfter)
    }

    @Test
    @DisplayName("an enveloped status with no exception type stays a Feign error that keeps the body")
    fun unmappedEnvelopeKeepsBody() {
        answerError("/rest/api/1/license-reports/$REPORT_ID", HTTP_INTERNAL_SERVER_ERROR)

        val error = assertThrows<FeignException> { client.getReport(REPORT_ID) }

        assertTrue(error.contentUTF8().contains("answered $HTTP_INTERNAL_SERVER_ERROR"), error.contentUTF8())
    }

    private fun answerError(
        path: String,
        status: Int,
        headers: Map<String, String> = emptyMap(),
    ) = answer(path, status, """{"error":{"code":$status,"message":"answered $status"}}""", headers)

    private fun answer(
        path: String,
        status: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        server.createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private companion object {
        const val REPORT_ID = "report-1"
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_INTERNAL_SERVER_ERROR = 500
        const val HTTP_BAD_GATEWAY = 502
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val RETRY_AFTER_SECONDS = 60L
    }
}
