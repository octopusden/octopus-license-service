package org.octopusden.octopus.license.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.client.LicenseServiceClient
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.dto.LicenseReportStatus.DONE
import org.octopusden.octopus.license.core.dto.LicenseReportStatus.EXCLUDED
import org.octopusden.octopus.license.core.dto.LicenseReportStatus.RUNNING
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.octopusden.octopus.license.core.exception.ServiceUnreachableException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LicenseReportRunnerTest {
    private val clock = SteppingClock()
    private val sleeps = mutableListOf<Duration>()

    @Test
    @DisplayName("polls until the report is DONE, then downloads its files")
    fun pollsUntilDoneThenDownloads() {
        val client = ScriptedClient(pollAnswers = listOf(status(RUNNING), status(RUNNING), status(DONE)))

        val finished = assertInstanceOf(RunOutcome.Finished::class.java, runner(client).run(COMPONENT, VERSION))

        assertEquals(DONE, finished.report.status)
        assertEquals(FILES.toList(), finished.files?.toList())
        assertEquals(3, client.polls)
    }

    @Test
    @DisplayName("a quick check asks for CHECK and downloads nothing: regular builds get no license files")
    fun checkOnlyDownloadsNothing() {
        val client = ScriptedClient()

        val finished = assertInstanceOf(RunOutcome.Finished::class.java, runner(client).run(COMPONENT, VERSION, LicenseReportMode.CHECK))

        assertEquals(LicenseReportMode.CHECK, client.lastRequest?.mode)
        assertNull(finished.files)
        assertEquals(0, client.downloads)
    }

    @Test
    @DisplayName("a start answer that is already final needs no poll: an EXCLUDED report is complete")
    fun finalStartAnswerNeedsNoPoll() {
        val client = ScriptedClient(startAnswers = listOf(status(EXCLUDED)))

        val finished = assertInstanceOf(RunOutcome.Finished::class.java, runner(client).run(COMPONENT, VERSION))

        assertEquals(EXCLUDED, finished.report.status)
        assertEquals(0, client.polls)
        assertEquals(0, client.downloads)
    }

    @Test
    @DisplayName("an EXCLUDED report finishes without a download: it has no files")
    fun excludedReportHasNoDownload() {
        val client = ScriptedClient(pollAnswers = listOf(status(EXCLUDED)))

        val finished = assertInstanceOf(RunOutcome.Finished::class.java, runner(client).run(COMPONENT, VERSION))

        assertNull(finished.files)
        assertEquals(0, client.downloads)
    }

    @Test
    @DisplayName("a report still RUNNING when --timeout is used up makes the service unavailable")
    fun timeoutIsUnavailable() {
        val client = ScriptedClient(pollAnswers = listOf(status(RUNNING)))

        assertInstanceOf(RunOutcome.Unavailable::class.java, runner(client).run(COMPONENT, VERSION))
        assertEquals(TIMEOUT.dividedBy(POLL_INTERVAL).toInt(), client.polls)
    }

    @Test
    @DisplayName("a network error while polling is asked again after a wait, not the end of the run")
    fun unreachableWhilePollingIsRetried() {
        val client = ScriptedClient(pollAnswers = listOf(unreachable(), status(DONE)))

        assertInstanceOf(RunOutcome.Finished::class.java, runner(client).run(COMPONENT, VERSION))
        assertEquals(listOf(POLL_INTERVAL, FIRST_BACKOFF), sleeps)
    }

    @Test
    @DisplayName("a busy start waits the Retry-After the service asked for, then starts again")
    fun busyStartWaitsRetryAfter() {
        val retryAfter = Duration.ofSeconds(60)
        val client = ScriptedClient(startAnswers = listOf(busy(retryAfter), status(RUNNING)), pollAnswers = listOf(status(DONE)))

        assertInstanceOf(RunOutcome.Finished::class.java, runner(client, timeout = Duration.ofMinutes(5)).run(COMPONENT, VERSION))
        assertEquals(2, client.starts)
        assertEquals(retryAfter, sleeps.first())
    }

    @Test
    @DisplayName("a report the service no longer knows is started again: the service restarted")
    fun goneReportIsStartedAgain() {
        val client = ScriptedClient(pollAnswers = listOf(notFound(), status(DONE)))

        assertInstanceOf(RunOutcome.Finished::class.java, runner(client).run(COMPONENT, VERSION))
        assertEquals(2, client.starts)
    }

    @Test
    @DisplayName("a rejected request is given up at once: asking again gets the same answer")
    fun invalidRequestIsNotRetried() {
        val client = ScriptedClient(startAnswers = listOf({ throw InvalidRequestException("Component must not be blank") }))

        val unavailable = assertInstanceOf(RunOutcome.Unavailable::class.java, runner(client).run(COMPONENT, VERSION))

        assertEquals("Component must not be blank", unavailable.reason)
        assertEquals(1, client.starts)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    @DisplayName("waits between failed attempts double up to a minute, and all of them stop at --timeout")
    fun backoffDoublesAndStopsAtTimeout() {
        val client = ScriptedClient(startAnswers = listOf(unreachable()))

        assertInstanceOf(RunOutcome.Unavailable::class.java, runner(client, timeout = Duration.ofMinutes(3)).run(COMPONENT, VERSION))
        assertEquals(listOf(5L, 10L, 20L, 40L, 60L), sleeps.take(5).map { it.seconds })
        assertTrue(sleeps.fold(Duration.ZERO, Duration::plus) <= Duration.ofMinutes(3), "slept $sleeps")
    }

    private fun runner(
        client: LicenseServiceClient,
        timeout: Duration = TIMEOUT,
    ) = LicenseReportRunner(client, POLL_INTERVAL, timeout, clock) {
        sleeps += it
        clock.advance(it)
    }

    /**
     * Answers each call with the next scripted answer, repeating the last one. Starts default to
     * RUNNING, polls to DONE.
     */
    private class ScriptedClient(
        private val startAnswers: List<() -> LicenseReport> = listOf(status(RUNNING)),
        private val pollAnswers: List<() -> LicenseReport> = listOf(status(DONE)),
    ) : LicenseServiceClient {
        var starts = 0
        var polls = 0
        var downloads = 0
        var lastRequest: LicenseReportRequest? = null

        override fun startReport(request: LicenseReportRequest): LicenseReport {
            lastRequest = request
            return startAnswers.answer(starts++)
        }

        override fun getReport(id: String) = pollAnswers.answer(polls++)

        override fun downloadFiles(id: String): ByteArray {
            downloads++
            return FILES
        }

        private fun List<() -> LicenseReport>.answer(call: Int) = this[minOf(call, lastIndex)]()
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
        val FIRST_BACKOFF: Duration = Duration.ofSeconds(5)
        val TIMEOUT: Duration = Duration.ofMinutes(1)

        fun status(status: LicenseReportStatus): () -> LicenseReport = { LicenseReport(REPORT_ID, COMPONENT, VERSION, status) }

        fun unreachable(): () -> LicenseReport = { throw ServiceUnreachableException("Connection refused") }

        fun busy(retryAfter: Duration): () -> LicenseReport = { throw ServiceBusyException("busy", retryAfter) }

        fun notFound(): () -> LicenseReport = { throw NotFoundException("License report '$REPORT_ID' is not found or has expired") }
    }
}
