package org.octopusden.octopus.license.service.policy

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

/**
 * `licenseRegistry`: down until the first policy is loaded, up from then on, even while later
 * reloads fail, since the service keeps judging with the policy it has (D7). The details say which
 * commit is loaded and what the registry should fix.
 */
@Component("licenseRegistry")
class LicensePolicyHealthIndicator(
    private val holder: LicensePolicyHolder,
) : HealthIndicator {
    override fun health(): Health =
        holder.current()?.let { (policy, loadedAt) ->
            Health
                .up()
                .withDetail("commit", policy.commit)
                .withDetail("loadedAt", loadedAt.toString())
                .withDetail("licensesWithoutText", policy.licensesWithoutText)
                .withDetail("problems", policy.problems)
                .build()
        } ?: Health.down().withDetail("reason", "The license registry has not been loaded yet").build()
}
