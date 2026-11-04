package org.octopusden.octopus.license.service.store

import org.octopusden.octopus.license.core.dto.LicenseReport
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds reports in memory for [ttl] after their generation finished, so the caller can download
 * them, then forgets them. A running report is never dropped. Nothing survives a restart: the
 * service is stateless, and a caller whose report is gone starts a new one.
 */
class LicenseReportStore(
    private val clock: Clock,
    private val ttl: Duration,
) {
    private val entries = ConcurrentHashMap<String, Entry>()

    fun add(report: LicenseReport) {
        entries[report.id] = Entry(StoredReport(report, null), finishedAt = null)
    }

    fun finish(
        report: LicenseReport,
        files: ByteArray?,
    ) {
        entries[report.id] = Entry(StoredReport(report, files), finishedAt = clock.instant())
    }

    fun find(id: String): StoredReport? = entries[id]?.takeUnless { it.isExpired(clock.instant()) }?.stored

    fun evictExpired() {
        val now = clock.instant()
        entries.values.removeIf { it.isExpired(now) }
    }

    internal fun ids(): List<String> = entries.keys.sorted()

    private inner class Entry(
        val stored: StoredReport,
        val finishedAt: Instant?,
    ) {
        fun isExpired(now: Instant) = finishedAt != null && !now.isBefore(finishedAt.plus(ttl))
    }
}

class StoredReport(
    val report: LicenseReport,
    val files: ByteArray?,
)
