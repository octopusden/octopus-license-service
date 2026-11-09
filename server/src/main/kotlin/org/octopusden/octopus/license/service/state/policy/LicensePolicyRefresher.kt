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
 * `unchanged`, `unavailable` or `invalid`. Readiness cannot show a failing refresh once a policy
 * is loaded, so the counter is what an alert watches.
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

    @Scheduled(fixedDelayString = "\${license-service.license-registry.retry-interval:PT1M}")
    fun refresh() {
        val current = holder.current()
        val now = clock.instant()
        if (current != null && lastCheck?.let { now < it + refreshInterval } == true) {
            return
        }
        lastCheck = now
        try {
            if (current == null || source.headCommit() != current.policy.commit) {
                load(now)
                count("loaded")
            } else {
                count("unchanged")
            }
        } catch (e: LicenseRegistryUnavailableException) {
            count("unavailable")
            log.warn("Cannot read the license registry; keeping commit {}", current?.policy?.commit, e)
        } catch (e: InvalidLicenseRegistryException) {
            count("invalid")
            log.error("The license registry cannot make a policy; keeping commit {}", current?.policy?.commit, e)
        }
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
        private val log = LoggerFactory.getLogger(LicensePolicyRefresher::class.java)
    }
}
