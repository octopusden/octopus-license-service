package org.octopusden.octopus.license.policy.exception

class LicenseRegistryUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
