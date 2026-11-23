package org.octopusden.octopus.license.inventory.model

enum class SelectionReason {
    /** In the registry's `GAV` or `docker` field. */
    DECLARED,

    /** Group and artifact match a Maven artifact pattern. */
    PATTERN,

    /** An image of the component's docker build, declared or not. */
    DOCKER_BUILD,
}
