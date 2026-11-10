package org.octopusden.octopus.license.target.model

/**
 * What the components registry declares a component version releases. It is the input of the
 * license scan, never a wire type: it carries more than callers are shown, and it says nothing
 * about which concrete files exist in Artifactory.
 *
 * @param artifactVersions the versions released files may carry: the requested version, then the
 * registry's build version when it differs.
 * @param explicit whether the component's distribution is explicit; its builds are named after it.
 */
data class LicenseTarget(
    val componentVersion: ComponentVersion,
    val artifactVersions: List<String>,
    val buildSystem: String,
    val labels: Set<String>,
    val explicit: Boolean,
    val distribution: DistributionSpec,
)
