package org.octopusden.octopus.license.inventory.model

enum class FileKind {
    MAVEN,
    DOCKER,

    /** A declared entry that names neither a Maven artifact nor an image. */
    FILE_SHARE,
}
