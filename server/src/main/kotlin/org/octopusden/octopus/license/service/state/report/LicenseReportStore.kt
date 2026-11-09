package org.octopusden.octopus.license.service.state.report

import org.octopusden.octopus.license.core.dto.LicenseReport
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds reports in memory for [ttl] after their generation finished, so the caller can download
 * them, then forgets them. A running report is never dropped. Nothing survives a restart: the
 * service is stateless, and a caller whose report is gone starts a new one.
 *
 * The license files of finished reports are kept within [maxStoredBytes]: past it, the oldest
 * finished reports go before their TTL ends. The report that just finished is always kept, even
 * when it is larger than the limit on its own.
 */
class LicenseReportStore(
    private val clock: Clock,
    private val ttl: Duration,
    private val maxStoredBytes: Long,
) {
    private val entries = ConcurrentHashMap<String, Entry>()
    private var storedBytes = 0L

    @Synchronized
    fun add(report: LicenseReport) {
        put(report.id, Entry(StoredReport(report, null), finishedAt = null))
    }

    @Synchronized
    fun finish(
        report: LicenseReport,
        files: ByteArray?,
    ) {
        put(report.id, Entry(StoredReport(report, files), finishedAt = clock.instant()))
        evictOldestFinishedBeyondLimit(keep = report.id)
    }

    fun find(id: String): StoredReport? = entries[id]?.takeUnless { it.isExpired(clock.instant()) }?.stored

    @Synchronized
    fun remove(id: String) {
        entries.remove(id)?.let { storedBytes -= it.size }
    }

    @Synchronized
    fun evictExpired() {
        val now = clock.instant()
        entries.filterValues { it.isExpired(now) }.keys.forEach(::remove)
    }

    internal fun ids(): List<String> = entries.keys.sorted()

    @Synchronized
    internal fun storedBytes() = storedBytes

    private fun put(
        id: String,
        entry: Entry,
    ) {
        storedBytes += entry.size - (entries.put(id, entry)?.size ?: 0)
    }

    private fun evictOldestFinishedBeyondLimit(keep: String) {
        if (storedBytes <= maxStoredBytes) return
        val oldestFirst = entries.entries
            .filter { it.key != keep && it.value.finishedAt != null }
            .sortedBy { it.value.finishedAt }
            .map { it.key }
        for (id in oldestFirst) {
            if (storedBytes <= maxStoredBytes) return
            remove(id)
        }
    }

    private inner class Entry(
        val stored: StoredReport,
        val finishedAt: Instant?,
    ) {
        val size: Long get() = stored.files?.size?.toLong() ?: 0

        fun isExpired(now: Instant) = finishedAt != null && !now.isBefore(finishedAt.plus(ttl))
    }
}

class StoredReport(
    val report: LicenseReport,
    val files: ByteArray?,
)
