package org.octopusden.octopus.license.policy.exception

/** The license registry's files cannot make a policy at all, such as a registry that allows nothing. */
class InvalidLicenseRegistryException(
    message: String,
) : RuntimeException(message)
