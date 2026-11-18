package org.octopusden.octopus.license.runner

import com.github.ajalt.clikt.testing.test
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

class LicenseRunnerCommandTest {
    @TempDir
    private lateinit var workDir: Path

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply { start() }

    @AfterEach
    fun stopServer() = server.stop(0)

    @Test
    @DisplayName("a run that gets no report removes the previous run's report and zip, so they are not published again")
    fun previousOutputIsRemoved() {
        val outputDir = Files.createDirectories(workDir.resolve("license-report"))
        val oldReport = Files.writeString(outputDir.resolve("license-report.json"), "{}")
        val oldZip = Files.write(outputDir.resolve("other-component-0.9-licenses.zip"), byteArrayOf(1))

        val result = LicenseRunnerCommand().test(args(url = UNREACHABLE_URL, outputDir = outputDir))

        assertEquals(0, result.statusCode)
        assertFalse(Files.exists(oldReport))
        assertFalse(Files.exists(oldZip))
        assertTrue(result.stdout.contains("WARNING: License service unavailable"), result.stdout)
    }

    @Test
    @DisplayName("a DONE report without a summary is a warning, not a failed step")
    fun doneWithoutSummaryIsWarning() {
        serveDoneReportWithoutSummary()

        val result = LicenseRunnerCommand().test(args(url = serverUrl(), outputDir = workDir.resolve("out")))

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(result.stdout.contains("WARNING: License report for $COMPONENT:$VERSION"), result.stdout)
    }

    @Test
    @DisplayName("an output directory that cannot be written is a warning, not a failed step")
    fun unwritableOutputIsWarning() {
        serveDoneReportWithoutSummary()
        val notADirectory = Files.writeString(workDir.resolve("a-file"), "")

        val result = LicenseRunnerCommand().test(args(url = serverUrl(), outputDir = notADirectory))

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(result.stdout.contains("WARNING: License runner failed"), result.stdout)
    }

    @Test
    @DisplayName("--check-only writes the report but no license files")
    fun checkOnlyWritesNoZip() {
        serveDoneReportWithoutSummary()
        val outputDir = workDir.resolve("out")

        val result = LicenseRunnerCommand().test(args(url = serverUrl(), outputDir = outputDir) + "--check-only")

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(Files.exists(outputDir.resolve("license-report.json")))
        assertFalse(Files.exists(outputDir.resolve("$COMPONENT-$VERSION-licenses.zip")))
    }

    @Test
    @DisplayName("--timeout and --poll-interval take the short form, such as 45m")
    fun shortDurationsAreAccepted() {
        serveDoneReportWithoutSummary()

        val result = LicenseRunnerCommand().test(
            listOf(
                "--url",
                serverUrl(),
                "--component",
                COMPONENT,
                "--version",
                VERSION,
                "--output-dir",
                workDir.resolve("out").toString(),
                "--timeout",
                "45m",
                "--poll-interval",
                "0s",
            ),
        )

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(result.stdout.contains("License report for $COMPONENT:$VERSION"), result.stdout)
    }

    @Test
    @DisplayName("arguments the runner cannot read still remove the previous run's output, so it is not published again")
    fun badArgumentsRemovePreviousOutput() {
        val outputDir = Files.createDirectories(workDir.resolve("report-dir"))
        val oldReport = Files.writeString(outputDir.resolve("license-report.json"), "{}")
        val oldZip = Files.write(outputDir.resolve("$COMPONENT-0.9-licenses.zip"), byteArrayOf(1))
        val originalOut = System.out
        System.setOut(PrintStream(ByteArrayOutputStream(), true))
        try {
            main(arrayOf("--component", COMPONENT, "--version", VERSION, "--output-dir", outputDir.toString(), "--timeout", "forever"))
        } finally {
            System.setOut(originalOut)
        }

        assertFalse(Files.exists(oldReport))
        assertFalse(Files.exists(oldZip))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["--output-dir a --output-dir b", "--output-dir=a --output-dir b", "--output-dir a --output-dir=b"])
    @DisplayName("the output directory read without Clikt is the last one given, as Clikt reads it")
    fun lastOutputDirWins(arguments: String) {
        assertEquals(Path.of("b"), LicenseRunnerCommand.outputDirIn(arguments.split(" ").toTypedArray()))
    }

    @Test
    @DisplayName("arguments the runner cannot read are a warning, never an exception: the build step still passes")
    fun badArgumentsAreWarning() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(output, true))
        try {
            assertDoesNotThrow {
                main(arrayOf("--component", COMPONENT, "--version", VERSION, "--output-dir", workDir.toString(), "--timeout", "forever"))
            }
        } finally {
            System.setOut(originalOut)
        }

        assertTrue(output.toString().contains("WARNING: License runner did not run"), output.toString())
    }

    private fun serveDoneReportWithoutSummary() {
        answer("/rest/api/1/license-reports", report("RUNNING"))
        answer("/rest/api/1/license-reports/$REPORT_ID", report("DONE"))
        server.createContext("/rest/api/1/license-reports/$REPORT_ID/files") { exchange ->
            exchange.sendResponseHeaders(HTTP_OK, EMPTY_ZIP.size.toLong())
            exchange.responseBody.use { it.write(EMPTY_ZIP) }
        }
    }

    private fun answer(
        path: String,
        body: String,
    ) {
        server.createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun serverUrl() = "http://localhost:${server.address.port}"

    private fun args(
        url: String,
        outputDir: Path,
    ) = listOf(
        "--url",
        url,
        "--component",
        COMPONENT,
        "--version",
        VERSION,
        "--output-dir",
        outputDir.toString(),
        "--poll-interval",
        "PT0S",
        // Short, so an unreachable service ends the run instead of being retried for real.
        "--timeout",
        "PT1S",
    )

    private companion object {
        const val REPORT_ID = "report-1"
        const val COMPONENT = "some-component"
        const val VERSION = "1.2.3"
        const val HTTP_OK = 200
        const val UNREACHABLE_URL = "http://localhost:1"
        val EMPTY_ZIP = byteArrayOf(0x50, 0x4b, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        fun report(status: String) = """{"id":"$REPORT_ID","component":"$COMPONENT","version":"$VERSION","status":"$status"}"""
    }
}
