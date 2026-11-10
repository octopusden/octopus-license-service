package org.octopusden.octopus.license.inventory.model

/** How much of a released file could be read (D3); neutral words, so no status names a source. */
enum class FileStatus {
    /** Published by a convention build, but neither declared nor matching a pattern; never read. */
    NOT_SELECTED,

    /** A pom, checksum, sources or javadoc file that is not declared; never read. */
    METADATA,

    /** Declared or selected, but not in Artifactory. */
    NOT_FOUND,

    /** A declared entry the service cannot look up. */
    UNSUPPORTED,

    /** A text file the binary scanner does not index; holds no libraries. */
    PLAIN_FILE,

    /** Neither the binary scanner nor a build record could see inside it. */
    NOT_READABLE,

    /** At least one source saw inside it; [ReleasedFile.evidence] says which. */
    READ,
}
