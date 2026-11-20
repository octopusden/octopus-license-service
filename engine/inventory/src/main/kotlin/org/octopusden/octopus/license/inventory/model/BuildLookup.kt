package org.octopusden.octopus.license.inventory.model

/** How a file's build was found. */
enum class BuildLookup {
    /** Named after the component, `{component}_{e|i}e[_docker]`. */
    CONVENTION,

    /** The file's `build.name` and `build.number` properties. */
    PROPERTIES,

    /** A build that published the file's sha1. */
    CHECKSUM,
}
