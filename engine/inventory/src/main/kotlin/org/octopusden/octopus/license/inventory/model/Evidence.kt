package org.octopusden.octopus.license.inventory.model

/** A source a package or a file was seen by. */
enum class Evidence {
    /** A binary scanner looked inside the stored file (Xray today). */
    BINARY_SCAN,

    /** An SBOM file the build published. */
    SBOM,

    /** A dependency the build recorded. */
    BUILD_RECORD,

    /** Named by a copy of its checksum stored under Maven coordinates. */
    CHECKSUM_MATCH,
}
