package org.octopusden.octopus.license.inventory.model

/**
 * Where a library's license names came from. Only these give names: a build record names packages
 * without licenses, and a checksum match only renames a file.
 */
enum class LicenseSource {
    /** Xray's scan of a stored file. */
    BINARY_SCAN,

    /** An SBOM file the build published. */
    SBOM,

    /** Xray's license database, asked by coordinates when no other source named any (D9). */
    LICENSE_DATABASE,
}
