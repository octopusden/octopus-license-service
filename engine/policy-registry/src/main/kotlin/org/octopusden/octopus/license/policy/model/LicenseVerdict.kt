package org.octopusden.octopus.license.policy.model

/**
 * The license registry's answer for one named library. A file nobody could name is never judged:
 * the license scan calls it unidentified before it asks the policy.
 */
enum class LicenseVerdict {
    /** At least one license is on the whitelist or the hidden list. */
    ALLOWED,

    /** Licenses are known, and none is allowed. */
    NOT_ALLOWED,

    /** No license is known for the library. */
    UNKNOWN,
}
