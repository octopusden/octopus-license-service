package org.octopusden.octopus.license.inventory.exception

/** [system] is the one that could not be asked: `Artifactory` or `Xray`. */
class InventorySourceUnavailableException(
    val system: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
