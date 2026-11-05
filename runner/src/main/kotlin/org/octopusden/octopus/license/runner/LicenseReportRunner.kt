package org.octopusden.octopus.license.runner

import org.octopusden.octopus.license.client.LicenseServiceClient
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportStatus
import java.time.Clock
import java.time.Duration

/**
 * Starts a report, polls it until it finishes and downloads its files when it is DONE.
 *
 * Never throws for a service problem: an unreachable service, an error answer or a timeout all
 * come back as [RunOutcome.Unavailable], because a build only reports on licenses for now and must
 * not break over them.
 */
class LicenseReportRunner(
    private val client: LicenseServiceClient,
    private val pollInterval: Duration,
    private val timeout: Duration,
    private val clock: Clock,
    private val sleep: (Duration) -> Unit,
) {
    fun run(
        component: String,
        version: String,
    ): RunOutcome =
        runCatching { await(client.startReport(LicenseReportRequest(component, version)).id) }
            .getOrElse { RunOutcome.Unavailable(it.message ?: it.javaClass.name) }

    private fun await(id: String): RunOutcome {
        val deadline = clock.instant().plus(timeout)
        while (true) {
            val report = client.getReport(id)
            if (report.status != LicenseReportStatus.RUNNING) {
                val files = if (report.status == LicenseReportStatus.DONE) client.downloadFiles(id) else null
                return RunOutcome.Finished(report, files)
            }
            if (!clock.instant().isBefore(deadline)) {
                return RunOutcome.Unavailable("License report $id is still running after $timeout")
            }
            sleep(pollInterval)
        }
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
