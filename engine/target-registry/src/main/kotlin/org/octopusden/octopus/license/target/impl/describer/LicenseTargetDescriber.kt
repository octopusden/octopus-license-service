package org.octopusden.octopus.license.target.impl.describer

import org.octopusden.octopus.license.target.model.ArtifactPattern
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.releng.versions.NumericVersionFactory
import org.octopusden.releng.versions.VersionNames
import org.octopusden.releng.versions.VersionRangeFactory

/**
 * Turns what the registry said about a component version into its [LicenseTarget]. Pure: the
 * caller fetches, this only reads. Exclusion is decided before, by [ExclusionRules], so an
 * excluded component never needs the patterns or version names this takes.
 */
internal object LicenseTargetDescriber {
    /**
     * @param artifactPatterns the registry's `maven-artifacts` answer: version range to patterns.
     * @param versionNames the registry's version names, so ranges parse exactly as the registry parses them.
     */
    fun describe(
        component: RegistryComponent,
        artifactPatterns: Map<String, RegistryArtifactPattern>,
        versionNames: VersionNames,
    ): LicenseTarget {
        val version = NumericVersionFactory(versionNames).create(component.version)
        val rangeFactory = VersionRangeFactory(versionNames)
        return LicenseTarget(
            componentVersion = ComponentVersion(component.id, component.version),
            artifactVersions = listOfNotNull(component.version, component.buildVersion).distinct(),
            buildSystem = component.buildSystem,
            labels = component.labels,
            explicit = component.explicit,
            distribution = DistributionSpec(
                declared = DistributionParser.parse(component.gav, component.docker),
                patterns = artifactPatterns
                    .filterKeys { range -> rangeFactory.create(range).containsVersion(version) }
                    .values
                    .map { ArtifactPattern(split(it.groupPattern), split(it.artifactPattern)) },
            ),
        )
    }

    private fun split(patterns: String?) =
        patterns
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
