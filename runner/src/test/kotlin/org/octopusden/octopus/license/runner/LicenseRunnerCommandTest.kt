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

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "--output-dir a -- --output-dir b",
            "--output-dir a --component --output-dir=b --version 1",
            "--output-dir a --component --output-dir b",
            "--output-dir a --output-dir",
        ],
    )
    @DisplayName("tokens Clikt does not read as options are not read as the output directory either")
    fun onlyRealOptionsCount(arguments: String) {
        assertEquals(Path.of("a"), LicenseRunnerCommand.outputDirIn(arguments.split(" ").toTypedArray()))
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

    @Test
    @DisplayName("no fail flag: a report with an UNKNOWN library still exits 0")
    fun violationWithoutFlagPasses() {
        serveDoneReportWithUnknownLibrary()

        val result = LicenseRunnerCommand().test(args(url = serverUrl(), outputDir = workDir.resolve("out")))

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(result.stdout.contains("WARNING: $UNKNOWN_LIBRARY is UNKNOWN"), result.stdout)
    }

    @Test
    @DisplayName("--fail-on-unknown fails the build on an UNKNOWN library, after writing the report and the zip (D11)")
    fun failOnUnknownFails() {
        serveDoneReportWithUnknownLibrary()
        val outputDir = workDir.resolve("out")

        val result = LicenseRunnerCommand().test(args(url = serverUrl(), outputDir = outputDir) + "--fail-on-unknown")

        assertEquals(1, result.statusCode, result.stdout)
        assertTrue(result.stdout.contains("ERROR: License check fails the build: $UNKNOWN_LIBRARY is UNKNOWN"), result.stdout)
        assertTrue(Files.exists(outputDir.resolve("license-report.json")))
        assertTrue(Files.exists(outputDir.resolve("$COMPONENT-$VERSION-licenses.zip")))
    }

    @Test
    @DisplayName("--fail-on-unknown: an unavailable service fails a release, never a regular build's check (D11)")
    fun unavailableWithFlag() {
        val release = LicenseRunnerCommand().test(args(url = UNREACHABLE_URL, outputDir = workDir.resolve("a")) + "--fail-on-unknown")
        val check =
            LicenseRunnerCommand().test(
                args(url = UNREACHABLE_URL, outputDir = workDir.resolve("b")) + "--fail-on-unknown" + "--check-only",
            )

        assertEquals(1, release.statusCode, release.stdout)
        assertEquals(0, check.statusCode, check.stdout)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "--fail-on-unknown --timeout forever",
            "--timeout forever --fail-on-disagreement",
            "--fail-on-unknown --check-only=false",
        ],
    )
    @DisplayName("arguments it cannot read fail a release that asked to fail: a typo must not pass it unchecked (review)")
    fun unreadableArgumentsFailRelease(arguments: String) {
        assertTrue(LicenseRunnerCommand.failsOnUnreadableArguments(arguments.split(" ").toTypedArray()))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "--timeout forever",
            "--fail-on-unknown --check-only --timeout forever",
            "--timeout forever -- --fail-on-unknown",
            "--component --fail-on-unknown --timeout forever",
        ],
    )
    @DisplayName("without a fail flag, or in a regular build's check, arguments it cannot read stay a warning")
    fun unreadableArgumentsWarn(arguments: String) {
        assertFalse(LicenseRunnerCommand.failsOnUnreadableArguments(arguments.split(" ").toTypedArray()))
    }

    private fun serveDoneReportWithUnknownLibrary() {
        answer("/rest/api/1/license-reports", report("RUNNING"))
        answer("/rest/api/1/license-reports/$REPORT_ID", REPORT_WITH_UNKNOWN)
        server.createContext("/rest/api/1/license-reports/$REPORT_ID/files") { exchange ->
            exchange.sendResponseHeaders(HTTP_OK, EMPTY_ZIP.size.toLong())
            exchange.responseBody.use { it.write(EMPTY_ZIP) }
        }
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

        const val UNKNOWN_LIBRARY = "pkg:maven/com.example/foo-core@1.0"
        val REPORT_WITH_UNKNOWN =
            """
            {"id":"$REPORT_ID","component":"$COMPONENT","version":"$VERSION","status":"DONE","mode":"FULL",
             "summary":{"inputs":{"licenseRegistryCommit":"a1b2c3d","generatedAt":"2026-11-19T10:00:00Z","serviceVersion":"1.0.0"},
              "trust":{"level":"HIGH","reasons":[],"files":[]},
              "libraryCounts":{"ALLOWED":0,"NOT_ALLOWED":0,"UNKNOWN":1,"UNIDENTIFIED":0},
              "violations":[{"library":"$UNKNOWN_LIBRARY","verdict":"UNKNOWN","licenses":[],"files":["app.jar"],"preliminary":false}],
              "missingLicenseTexts":[]}}
            """.trimIndent()

        fun report(status: String) = """{"id":"$REPORT_ID","component":"$COMPONENT","version":"$VERSION","status":"$status"}"""
    }
}
