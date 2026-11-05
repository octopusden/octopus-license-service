package org.octopusden.octopus.license.service.store

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
    private val store = LicenseReportStore(clock, TTL)

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

        fun report(
            status: LicenseReportStatus,
            id: String = REPORT_ID,
        ) = LicenseReport(id, "component", "1.0.0", status)
    }
}
