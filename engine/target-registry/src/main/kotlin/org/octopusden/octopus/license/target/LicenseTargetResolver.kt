package org.octopusden.octopus.license.target

import org.octopusden.octopus.license.target.exception.ComponentVersionNotFoundException
import org.octopusden.octopus.license.target.exception.ComponentsRegistryUnavailableException
import org.octopusden.octopus.license.target.model.TargetResolution

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
