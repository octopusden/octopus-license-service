package org.octopusden.octopus.license.client

import com.sun.net.httpserver.HttpServer
import feign.FeignException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.client.impl.ClassicLicenseServiceClient
import org.octopusden.octopus.license.client.impl.LicenseServiceClientParametersProvider
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.Verdict
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceUnavailableException
import java.net.InetSocketAddress
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
    @DisplayName("a DONE report decodes with its summary, ignoring fields the client does not know")
    fun decodesDoneReport() {
        answer(
            "/rest/api/1/license-reports/$REPORT_ID",
            HTTP_OK,
            """
            {"id":"$REPORT_ID","component":"c","version":"1","status":"DONE","newField":true,
             "summary":{"inputs":{"licenseRegistryCommit":"abc123","generatedAt":"2026-11-04T10:00:00Z",
             "serviceVersion":"1.0.0"},"trust":null,"libraryCounts":{"ALLOWED":3},"violations":[]}}
            """.trimIndent(),
        )

        val report = client.getReport(REPORT_ID)

        assertEquals(LicenseReportStatus.DONE, report.status)
        val summary = requireNotNull(report.summary)
        assertEquals(Instant.parse("2026-11-04T10:00:00Z"), summary.inputs.generatedAt)
        assertEquals(mapOf(Verdict.ALLOWED to 3), summary.libraryCounts)
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
    @DisplayName("the error envelope maps 400, 404 and 409 to the shared exception types")
    fun mapsErrorEnvelope() {
        answerError("/rest/api/1/license-reports", HTTP_BAD_REQUEST)
        answerError("/rest/api/1/license-reports/missing", HTTP_NOT_FOUND)
        answerError("/rest/api/1/license-reports/running/files", HTTP_CONFLICT)

        assertThrows<InvalidRequestException> { client.startReport(LicenseReportRequest("c", "1")) }
        assertThrows<NotFoundException> { client.getReport("missing") }
        assertThrows<LicenseFilesNotAvailableException> { client.downloadFiles("running") }
    }

    @Test
    @DisplayName("a 503 with the error envelope means a system the service depends on is unavailable")
    fun mapsServiceUnavailable() {
        answerError("/rest/api/1/license-reports", HTTP_SERVICE_UNAVAILABLE)

        assertThrows<ServiceUnavailableException> { client.startReport(LicenseReportRequest("c", "1")) }
    }

    @Test
    @DisplayName("an answer without the error envelope stays a plain Feign error")
    fun nonEnvelopeErrorStaysFeignError() {
        answer("/rest/api/1/license-reports/$REPORT_ID", HTTP_BAD_GATEWAY, "<html>Bad Gateway</html>")

        assertThrows<FeignException> { client.getReport(REPORT_ID) }
    }

    private fun answerError(
        path: String,
        status: Int,
    ) = answer(path, status, """{"error":{"code":$status,"message":"answered $status"}}""")

    private fun answer(
        path: String,
        status: Int,
        body: String,
    ) {
        server.createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
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
        const val HTTP_BAD_GATEWAY = 502
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
