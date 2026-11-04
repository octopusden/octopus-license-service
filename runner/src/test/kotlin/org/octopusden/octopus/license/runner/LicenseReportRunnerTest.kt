package org.octopusden.octopus.license.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.client.LicenseServiceClient
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LicenseReportRunnerTest {
    private val clock = SteppingClock()

    @Test
    @DisplayName("polls until the report is DONE, then downloads its files")
    fun pollsUntilDoneThenDownloads() {
        val client = ScriptedClient(LicenseReportStatus.RUNNING, LicenseReportStatus.RUNNING, LicenseReportStatus.DONE)

        val outcome = runner(client).run(COMPONENT, VERSION)

        val finished = assertInstanceOf(RunOutcome.Finished::class.java, outcome)
        assertEquals(LicenseReportStatus.DONE, finished.report.status)
        assertEquals(FILES.toList(), finished.files?.toList())
        assertEquals(3, client.polls)
    }

    @Test
    @DisplayName("an EXCLUDED report finishes without a download: it has no files")
    fun excludedReportHasNoDownload() {
        val client = ScriptedClient(LicenseReportStatus.EXCLUDED)

        val outcome = runner(client).run(COMPONENT, VERSION)

        val finished = assertInstanceOf(RunOutcome.Finished::class.java, outcome)
        assertNull(finished.files)
        assertEquals(0, client.downloads)
    }

    @Test
    @DisplayName("a report still RUNNING after the timeout makes the service unavailable")
    fun timeoutIsUnavailable() {
        val client = ScriptedClient(LicenseReportStatus.RUNNING)

        val outcome = runner(client).run(COMPONENT, VERSION)

        assertInstanceOf(RunOutcome.Unavailable::class.java, outcome)
        assertEquals(TIMEOUT.dividedBy(POLL_INTERVAL).toInt() + 1, client.polls)
    }

    @Test
    @DisplayName("a service that cannot be reached is unavailable, not an exception")
    fun unreachableServiceIsUnavailable() {
        val client = object : LicenseServiceClient by ScriptedClient(LicenseReportStatus.DONE) {
            override fun startReport(request: LicenseReportRequest): LicenseReport = error("Connection refused")
        }

        val outcome = runner(client).run(COMPONENT, VERSION)

        val unavailable = assertInstanceOf(RunOutcome.Unavailable::class.java, outcome)
        assertEquals("Connection refused", unavailable.reason)
    }

    private fun runner(client: LicenseServiceClient) = LicenseReportRunner(client, POLL_INTERVAL, TIMEOUT, clock, clock::advance)

    /** Answers the polls with [statuses] in turn, repeating the last one. */
    private class ScriptedClient(
        vararg statuses: LicenseReportStatus,
    ) : LicenseServiceClient {
        private val script = statuses.toList()
        var polls = 0
        var downloads = 0

        override fun startReport(request: LicenseReportRequest) = report(LicenseReportStatus.RUNNING)

        override fun getReport(id: String) = report(script[minOf(polls++, script.lastIndex)])

        override fun downloadFiles(id: String): ByteArray {
            downloads++
            return FILES
        }

        private fun report(status: LicenseReportStatus) = LicenseReport(REPORT_ID, COMPONENT, VERSION, status)
    }

    private class SteppingClock : Clock() {
        private var now = Instant.parse("2026-11-04T10:00:00Z")

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }

    private companion object {
        const val REPORT_ID = "report-1"
        const val COMPONENT = "some-component"
        const val VERSION = "1.2.3"
        val FILES = byteArrayOf(1, 2, 3)
        val POLL_INTERVAL: Duration = Duration.ofSeconds(5)
        val TIMEOUT: Duration = Duration.ofMinutes(1)
    }
}
