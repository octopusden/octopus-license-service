package org.octopusden.octopus.license.service.state.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class LicenseReportStoreTest {
    private val clock = MutableClock(Instant.parse("2026-11-04T10:00:00Z"))
    private val store = LicenseReportStore(clock, TTL, MAX_STORED_BYTES)

    @Test
    @DisplayName("a finished report is kept until the TTL has passed since it finished")
    fun finishedReportKeptUntilTtl() {
        store.add(report(LicenseReportStatus.RUNNING))
        clock.advance(Duration.ofMinutes(5))
        store.finish(report(LicenseReportStatus.DONE), FILES)

        clock.advance(TTL.minusSeconds(1))

        assertEquals(LicenseReportStatus.DONE, store.find(REPORT_ID)?.report?.status)
    }

    @Test
    @DisplayName("a finished report is gone once the TTL has passed since it finished")
    fun finishedReportDroppedAfterTtl() {
        store.add(report(LicenseReportStatus.RUNNING))
        store.finish(report(LicenseReportStatus.DONE), FILES)

        clock.advance(TTL)

        assertNull(store.find(REPORT_ID))
    }

    @Test
    @DisplayName("a running report never expires: the TTL counts from the end of the generation")
    fun runningReportNeverExpires() {
        store.add(report(LicenseReportStatus.RUNNING))

        clock.advance(TTL.multipliedBy(3))

        assertNotNull(store.find(REPORT_ID))
    }

    @Test
    @DisplayName("evictExpired drops only the reports whose TTL has passed")
    fun evictExpiredDropsOnlyExpired() {
        store.add(report(LicenseReportStatus.RUNNING, id = "old"))
        store.finish(report(LicenseReportStatus.DONE, id = "old"), FILES)
        clock.advance(TTL)
        store.add(report(LicenseReportStatus.RUNNING, id = "new"))
        store.finish(report(LicenseReportStatus.FAILED, id = "new"), null)

        store.evictExpired()

        assertEquals(listOf("new"), store.ids())
    }

    @Test
    @DisplayName("finish keeps the license files with the report")
    fun finishKeepsFiles() {
        store.add(report(LicenseReportStatus.RUNNING))
        store.finish(report(LicenseReportStatus.DONE), FILES)

        assertEquals(FILES.toList(), store.find(REPORT_ID)?.files?.toList())
    }

    @Test
    @DisplayName("over the byte limit, the oldest finished reports go first and running ones stay")
    fun byteLimitDropsOldestFinishedFirst() {
        store.add(report(LicenseReportStatus.RUNNING, id = "running"))
        finish("oldest", ByteArray(4))
        clock.advance(Duration.ofMinutes(1))
        finish("older", ByteArray(4))
        clock.advance(Duration.ofMinutes(1))

        finish("newest", ByteArray(4))

        assertEquals(listOf("newest", "older", "running"), store.ids())
        assertEquals(8, store.storedBytes())
    }

    @Test
    @DisplayName("a report larger than the byte limit on its own is still kept: it is the one just asked for")
    fun oversizedReportIsKept() {
        finish("small", ByteArray(2))

        finish("huge", ByteArray(MAX_STORED_BYTES.toInt() * 2))

        assertEquals(listOf("huge"), store.ids())
    }

    @Test
    @DisplayName("expired and removed reports give their bytes back")
    fun evictionReleasesBytes() {
        finish("expiring", ByteArray(4))
        clock.advance(TTL)
        store.evictExpired()
        finish("removed", ByteArray(4))

        store.remove("removed")

        assertEquals(0, store.storedBytes())
        assertEquals(emptyList<String>(), store.ids())
    }

    private fun finish(
        id: String,
        files: ByteArray,
    ) {
        store.add(report(LicenseReportStatus.RUNNING, id = id))
        store.finish(report(LicenseReportStatus.DONE, id = id), files)
    }

    private class MutableClock(
        private var now: Instant,
    ) : Clock() {
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?): Clock = this
    }

    private companion object {
        const val REPORT_ID = "report-1"
        val TTL: Duration = Duration.ofHours(1)
        val FILES = byteArrayOf(1, 2, 3)
        const val MAX_STORED_BYTES = 10L

        fun report(
            status: LicenseReportStatus,
            id: String = REPORT_ID,
        ) = LicenseReport(id, "component", "1.0.0", status)
    }
}
