package org.octopusden.octopus.license.inventory.impl.listing

import org.octopusden.octopus.license.target.model.LicenseTarget

/**
 * The builds named after a component: `{component}_{e|i}e`, then its docker build. The second
 * letter is always `e`: an internal component is excluded before the scan (D4).
 */
internal object ConventionBuilds {
    private const val DOCKER_SUFFIX = "_docker"

    fun name(target: LicenseTarget) = "${target.componentVersion.component}_${if (target.explicit) 'e' else 'i'}e"

    fun names(target: LicenseTarget) = name(target).let { listOf(it, it + DOCKER_SUFFIX) }
}
