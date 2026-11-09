package org.octopusden.octopus.license.service.state.policy

import org.octopusden.octopus.license.policy.model.LicensePolicy
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * The license policy the service judges with, swapped whole on a reload (D7). Empty until the
 * first load succeeds.
 */
class LicensePolicyHolder {
    private val loaded = AtomicReference<LoadedPolicy?>()

    fun current(): LoadedPolicy? = loaded.get()

    fun replace(policy: LoadedPolicy) = loaded.set(policy)
}

data class LoadedPolicy(
    val policy: LicensePolicy,
    val loadedAt: Instant,
)
