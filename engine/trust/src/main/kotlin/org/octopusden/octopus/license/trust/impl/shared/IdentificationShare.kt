package org.octopusden.octopus.license.trust.impl.shared

/**
 * When a share of third-party libraries is too many to trust. One place, so the component and each file
 * can never disagree on "too many" (D7). The share is compared in floating point, as the prototype does.
 */
internal object IdentificationShare {
    private const val SHARE = 0.1
    private const val MIN_UNIDENTIFIED = 10
    private const val MIN_UNKNOWN = 5

    fun tooManyUnidentified(
        count: Int,
        total: Int,
    ) = count >= MIN_UNIDENTIFIED && count >= SHARE * total

    fun tooManyUnknown(
        count: Int,
        total: Int,
    ) = count >= MIN_UNKNOWN && count >= SHARE * total
}
