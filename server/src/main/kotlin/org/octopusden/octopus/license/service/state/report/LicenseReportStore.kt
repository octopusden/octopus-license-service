package org.octopusden.octopus.license.service.state.report

import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds reports in memory for [ttl] after their generation finished, so the caller can download
 * them, then forgets them. A running report is never dropped. Nothing survives a restart: the
 * service is stateless, and a caller whose report is gone starts a new one.
 *
 * Finished reports are kept within [maxStoredReports], and their license files within
 * [maxStoredBytes]: past either, the oldest finished reports go before their TTL ends; for bytes,
 * only those holding files, since dropping one without frees nothing. The report that just finished
 * is always kept, even when it is larger than the byte limit on its own. Running reports are
 * bounded where they are queued, never here.
 */
class LicenseReportStore(
    private val clock: Clock,
    private val ttl: Duration,
    private val maxStoredBytes: Long,
    private val maxStoredReports: Int,
) {
    private val entries = ConcurrentHashMap<String, Entry>()
    private var storedBytes = 0L

    /**
     * Stores [report] unless a report for the same component version and mode is still running;
     * then that one is returned and [report] is not stored, so one component version is generated
     * once at a time per mode. A finished report is never reused: a new request reads the current
     * data.
     */
    @Synchronized
    fun addUnlessRunning(report: LicenseReport): LicenseReport =
        entries.values
            .map { it.stored.report }
            .firstOrNull {
                it.status == LicenseReportStatus.RUNNING &&
                    it.component == report.component &&
                    it.version == report.version &&
                    it.mode == report.mode
            }
            ?: report.also { put(it.id, Entry(StoredReport(it, null), finishedAt = null)) }

    @Synchronized
    fun finish(
        report: LicenseReport,
        files: ByteArray?,
    ) {
        put(report.id, Entry(StoredReport(report, files), finishedAt = clock.instant()))
        evictOldestFinishedBeyondLimits(keep = report.id)
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

    private fun evictOldestFinishedBeyondLimits(keep: String) {
        val oldestFirst =
            entries.entries
                .filter { it.key != keep && it.value.finishedAt != null }
                .sortedBy { it.value.finishedAt }
        var finished = oldestFirst.size + 1
        for ((id, _) in oldestFirst) {
            if (finished <= maxStoredReports) break
            remove(id)
            finished--
        }
        for ((id, entry) in oldestFirst) {
            if (storedBytes <= maxStoredBytes) return
            if (entry.stored.files != null && entries.containsKey(id)) remove(id)
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
