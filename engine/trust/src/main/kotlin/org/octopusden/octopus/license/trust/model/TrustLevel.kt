package org.octopusden.octopus.license.trust.model

/** Whether a library list is complete. Declared from least to most trusted: the order is the ranking. */
enum class TrustLevel {
    /** Not checked: nothing could be read, so there is no list at all. */
    NONE,

    /** Doubtful: part of the code is invisible to the check, so the list is probably incomplete. */
    LOW,

    /** Mostly right: some libraries are unidentified, have no known license, or may be missed. */
    MEDIUM,

    /** Trusted: nothing suggests anything is missing. */
    HIGH,
    ;

    companion object {
        /** [HIGH] when there is nothing to doubt. */
        fun lowest(levels: Collection<TrustLevel>): TrustLevel = levels.minOrNull() ?: HIGH
    }
}
