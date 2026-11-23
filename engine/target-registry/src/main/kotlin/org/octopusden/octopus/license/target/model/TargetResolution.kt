package org.octopusden.octopus.license.target.model

sealed interface TargetResolution {
    data class Resolved(
        val target: LicenseTarget,
    ) : TargetResolution

    data class Excluded(
        val componentVersion: ComponentVersion,
        val reason: String,
    ) : TargetResolution
}
