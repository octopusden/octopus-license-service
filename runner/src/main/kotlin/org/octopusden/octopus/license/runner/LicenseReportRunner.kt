package org.octopusden.octopus.license.runner

import org.octopusden.octopus.license.client.LicenseServiceClient
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.octopusden.octopus.license.core.exception.ServiceUnreachableException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Starts a report, polls it until it finishes and downloads its files when it is DONE, all within
 * [timeout].
 *
 * What can pass is waited out: an unreachable service or a gateway error is asked again after a
 * wait that doubles up to a minute, a busy service after the wait it asked for, and a report the
 * service no longer knows (it restarted) is started again. Anything else ends the run.
 *
 * Never throws: every way of not getting a report comes back as [RunOutcome.Unavailable], because a
 * build only reports on licenses for now and must not break over them.
 */
class LicenseReportRunner(
    private val client: LicenseServiceClient,
    private val pollInterval: Duration,
    private val timeout: Duration,
    private val clock: Clock,
    private val sleep: (Duration) -> Unit,
) {
    /** In [LicenseReportMode.CHECK] nothing is downloaded: a regular build gets no license files. */
    fun run(
        component: String,
        version: String,
        mode: LicenseReportMode = LicenseReportMode.FULL,
    ): RunOutcome =
        runCatching { Attempt(clock.instant().plus(timeout)).generate(LicenseReportRequest(component, version, mode)) }
            .getOrElse { RunOutcome.Unavailable(it.message ?: it.javaClass.name) }

    private inner class Attempt(
        private val deadline: Instant,
    ) {
        private var backoff = FIRST_BACKOFF

        fun generate(request: LicenseReportRequest): RunOutcome {
            while (true) {
                follow(call { client.startReport(request) }, request.mode)?.let { return it }
            }
        }

        /** The finished outcome, or null when the service no longer knows the report. */
        private fun follow(
            started: LicenseReport,
            mode: LicenseReportMode,
        ): RunOutcome? {
            var report = started
            while (report.status == LicenseReportStatus.RUNNING) {
                pause(pollInterval)
                report = orNullIfGone { call { client.getReport(report.id) } } ?: return null
            }
            if (report.status != LicenseReportStatus.DONE || mode == LicenseReportMode.CHECK) {
                return RunOutcome.Finished(report, null)
            }
            val files = orNullIfGone { call { client.downloadFiles(report.id) } } ?: return null
            return RunOutcome.Finished(report, files)
        }

        private fun <T> orNullIfGone(call: () -> T): T? =
            try {
                call()
            } catch (_: NotFoundException) {
                null
            }

        private fun <T> call(action: () -> T): T {
            while (true) {
                try {
                    return action().also { backoff = FIRST_BACKOFF }
                } catch (e: ServiceBusyException) {
                    pause(e.retryAfter ?: MAX_BACKOFF, e)
                } catch (e: ServiceUnreachableException) {
                    pause(backoff, e)
                    backoff = minOf(backoff.multipliedBy(2), MAX_BACKOFF)
                }
            }
        }

        private fun pause(
            duration: Duration,
            cause: Exception? = null,
        ) {
            if (clock.instant().plus(duration).isAfter(deadline)) {
                throw RunnerTimeoutException(
                    "No license report within $timeout" + (cause?.message?.let { "; last error: $it" } ?: ""),
                )
            }
            sleep(duration)
        }
    }

    private class RunnerTimeoutException(
        message: String,
    ) : RuntimeException(message)

    private companion object {
        val FIRST_BACKOFF: Duration = Duration.ofSeconds(5)
        val MAX_BACKOFF: Duration = Duration.ofMinutes(1)
    }
}

sealed interface RunOutcome {
    /** [files] is the zip of license files; set only when the report is DONE. */
    class Finished(
        val report: LicenseReport,
        val files: ByteArray?,
    ) : RunOutcome

    class Unavailable(
        val reason: String,
    ) : RunOutcome
}
