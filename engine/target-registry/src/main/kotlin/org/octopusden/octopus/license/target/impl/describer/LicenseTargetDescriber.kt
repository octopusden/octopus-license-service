package org.octopusden.octopus.license.target.impl.describer

import org.octopusden.octopus.license.target.model.ArtifactPattern
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.octopus.license.target.model.UnreadableRange
import org.octopusden.releng.versions.NumericVersionFactory
import org.octopusden.releng.versions.ParseVersionException
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
        val ranges = artifactPatterns.mapValues { (range, _) -> parse(rangeFactory, range) }
        return LicenseTarget(
            componentVersion = ComponentVersion(component.id, component.version),
            artifactVersions = listOfNotNull(component.version, component.buildVersion).distinct(),
            buildSystem = component.buildSystem,
            labels = component.labels,
            distribution = DistributionSpec(
                declared = DistributionParser.parse(component.gav, component.docker),
                patterns = artifactPatterns
                    .filterKeys { range -> ranges.getValue(range).getOrNull()?.containsVersion(version) == true }
                    .values
                    .map { ArtifactPattern(split(it.groupPattern), split(it.artifactPattern)) },
                unreadableRanges = ranges.mapNotNull { (range, parsed) ->
                    parsed.exceptionOrNull()?.let { UnreadableRange(range, it.message ?: it.javaClass.simpleName) }
                },
            ),
        )
    }

    // One bad key must not fail every version of the component; it is reported instead (D7).
    private fun parse(
        rangeFactory: VersionRangeFactory,
        range: String,
    ) = try {
        Result.success(rangeFactory.create(range))
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    } catch (e: ParseVersionException) {
        Result.failure(e)
    }

    private fun split(patterns: String?) =
        patterns
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
