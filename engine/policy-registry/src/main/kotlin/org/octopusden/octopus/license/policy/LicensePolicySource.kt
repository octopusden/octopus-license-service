package org.octopusden.octopus.license.policy

import org.octopusden.octopus.license.policy.exception.InvalidLicenseRegistryException
import org.octopusden.octopus.license.policy.exception.LicenseRegistryUnavailableException
import org.octopusden.octopus.license.policy.model.LicensePolicy

/** Reads the license policy from the license registry's default branch. */
interface LicensePolicySource {
    /**
     * The commit at the head of the default branch: a cheap check before a [load].
     *
     * @throws LicenseRegistryUnavailableException
     */
    fun headCommit(): String

    /**
     * Reads every registry file at one commit (D3).
     *
     * @throws LicenseRegistryUnavailableException the registry could not be read.
     * @throws InvalidLicenseRegistryException the files read cannot make a policy.
     */
    fun load(): LicensePolicy
}
