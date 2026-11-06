package org.octopusden.octopus.license.target

/** Answers what a component version releases, or why the service does not cover it. */
interface LicenseTargetResolver {
    /**
     * @throws ComponentVersionNotFoundException the registry knows no such component or version.
     * @throws ComponentsRegistryUnavailableException the registry could not be asked.
     */
    fun resolve(
        component: String,
        version: String,
    ): TargetResolution
}

/** No answer exists for the request, as opposed to an exclusion, which is an answer (D3). */
class ComponentVersionNotFoundException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

class ComponentsRegistryUnavailableException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
