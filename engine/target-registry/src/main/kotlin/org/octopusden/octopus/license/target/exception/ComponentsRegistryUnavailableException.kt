package org.octopusden.octopus.license.target.exception

class ComponentsRegistryUnavailableException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
