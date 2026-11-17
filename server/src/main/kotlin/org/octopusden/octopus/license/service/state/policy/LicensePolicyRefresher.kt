package org.octopusden.octopus.license.service.state.policy

import io.micrometer.core.instrument.MeterRegistry
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.policy.exception.InvalidLicenseRegistryException
import org.octopusden.octopus.license.policy.exception.LicenseRegistryUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Keeps [LicensePolicyHolder] current (D7). It ticks every retry interval: until a policy is loaded
 * every tick loads; after that, once per [refreshInterval] it asks for the head commit only, and
 * reloads only when it moved. A failure is logged and keeps the policy the holder has.
 *
 * Every refresh that asks Bitbucket is counted in [REFRESHES_METRIC] by outcome: `loaded`,
 * `unchanged`, `unavailable`, `invalid`, or `failed` for an error nobody expected, so no refresh
 * escapes the counter. Readiness cannot show a failing refresh once a policy is loaded, so the
 * counter is what an alert watches.
 *
 * [PROBLEMS_METRIC] is the number of registry entries the loaded policy skipped. A skipped
 * override loads as `loaded`, yet changes verdicts, so an alert watches this number too.
 */
class LicensePolicyRefresher(
    private val source: LicensePolicySource,
    private val holder: LicensePolicyHolder,
    private val refreshInterval: Duration,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
) {
    @Volatile
    private var lastCheck: Instant? = null

    init {
        meterRegistry.gauge(PROBLEMS_METRIC, holder) {
            it
                .current()
                ?.policy
                ?.problems
                ?.size
                ?.toDouble() ?: 0.0
        }
    }

    @Scheduled(fixedDelayString = "\${license-service.license-registry.retry-interval:PT1M}")
    fun refresh() {
        val current = holder.current()
        val now = clock.instant()
        if (current != null && lastCheck?.let { now < it + refreshInterval } == true) {
            return
        }
        lastCheck = now
        val kept = current?.policy?.commit
        val outcome =
            runCatching {
                if (current == null || source.headCommit() != current.policy.commit) {
                    load(now)
                    "loaded"
                } else {
                    "unchanged"
                }
            }.getOrElse { e ->
                when (e) {
                    is Error -> throw e
                    is LicenseRegistryUnavailableException ->
                        "unavailable".also { log.warn("Cannot read the license registry; keeping commit {}", kept, e) }
                    is InvalidLicenseRegistryException ->
                        "invalid".also { log.error("The license registry cannot make a policy; keeping commit {}", kept, e) }
                    else -> "failed".also { log.error("Refreshing the license policy failed; keeping commit {}", kept, e) }
                }
            }
        count(outcome)
    }

    private fun count(outcome: String) = meterRegistry.counter(REFRESHES_METRIC, "outcome", outcome).increment()

    private fun load(now: Instant) {
        val policy = source.load()
        holder.replace(LoadedPolicy(policy, now))
        log.info(
            "Loaded the license policy at commit {}: {} problem(s), allowed licenses without text: {}",
            policy.commit,
            policy.problems.size,
            policy.licensesWithoutText,
        )
        policy.problems.forEach { log.warn("License registry problem at {}: {}", policy.commit, it) }
    }

    companion object {
        const val REFRESHES_METRIC = "license.policy.refreshes"
        const val PROBLEMS_METRIC = "license.policy.problems"
        private val log = LoggerFactory.getLogger(LicensePolicyRefresher::class.java)
    }
}
