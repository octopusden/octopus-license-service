package org.octopusden.octopus.license.evaluation.model

/** The verdict on one counted library, first match wins (D4). */
enum class LibraryVerdict {
    /** Ours, as the inventory records it; never judged against the license registry. */
    INTERNAL,

    /** At least one license is on the whitelist or the hidden list. */
    ALLOWED,

    /** Licenses are known, and none is allowed. */
    NOT_ALLOWED,

    /** No license is known. */
    UNKNOWN,

    /** A file nobody could name, known only by its checksum; its licenses are not trusted. */
    UNIDENTIFIED,
}
