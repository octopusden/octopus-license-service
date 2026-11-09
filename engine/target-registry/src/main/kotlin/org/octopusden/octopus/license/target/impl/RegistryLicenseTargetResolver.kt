package org.octopusden.octopus.license.target.impl

import feign.FeignException
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClientUrlProvider
import org.octopusden.octopus.components.registry.core.dto.DetailedComponent
import org.octopusden.octopus.components.registry.core.exceptions.NotFoundException
import org.octopusden.octopus.components.registry.core.exceptions.RepositoryNotPreparedException
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.exception.ComponentVersionNotFoundException
import org.octopusden.octopus.license.target.exception.ComponentsRegistryUnavailableException
import org.octopusden.octopus.license.target.impl.describer.ExclusionRules
import org.octopusden.octopus.license.target.impl.describer.LicenseTargetDescriber
import org.octopusden.octopus.license.target.impl.describer.RegistryArtifactPattern
import org.octopusden.octopus.license.target.impl.describer.RegistryComponent
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.ConfiguredExclusion
import org.octopusden.octopus.license.target.model.TargetResolution
import org.octopusden.releng.versions.VersionNames

/**
 * Reads the components registry and nothing else: the component version first, and the Maven
 * artifact patterns and version names only for a component the [ExclusionRules] keep.
 */
class RegistryLicenseTargetResolver(
    registryUrl: String,
    exclusions: List<ConfiguredExclusion>,
) : LicenseTargetResolver {
    private val client: ComponentsRegistryServiceClient =
        ClassicComponentsRegistryServiceClient(
            object : ClassicComponentsRegistryServiceClientUrlProvider {
                override fun getApiUrl() = registryUrl
            },
        )

    private val exclusionRules = ExclusionRules(exclusions)

    override fun resolve(
        component: String,
        version: String,
    ): TargetResolution =
        askingRegistry(component, version) {
            val facts = client.getDetailedComponent(component, version).toFacts(version)
            exclusionRules.reasonFor(facts)?.let { TargetResolution.Excluded(ComponentVersion(component, version), it) }
                ?: TargetResolution.Resolved(
                    LicenseTargetDescriber.describe(
                        facts,
                        client.getComponentArtifactsParameters(component).mapValues { (_, pattern) ->
                            RegistryArtifactPattern(pattern.groupPattern, pattern.artifactPattern)
                        },
                        client.getVersionNames().let { VersionNames(it.serviceBranch, it.service, it.minor) },
                    ),
                )
        }

    private fun <T> askingRegistry(
        component: String,
        version: String,
        call: () -> T,
    ): T =
        try {
            call()
        } catch (e: NotFoundException) {
            throw ComponentVersionNotFoundException("Component version $component:$version is not found", e)
        } catch (e: RepositoryNotPreparedException) {
            throw ComponentsRegistryUnavailableException("Components registry is still loading its repository: ${e.message}", e)
        } catch (e: FeignException) {
            // status() is -1 when no answer came back at all: refused, reset or timed out.
            if (e.status() >= SERVER_ERROR || e.status() < 0) {
                throw ComponentsRegistryUnavailableException("Components registry is unavailable: ${e.message}", e)
            }
            throw e
        }

    private fun DetailedComponent.toFacts(version: String) =
        RegistryComponent(
            id = id,
            version = version,
            archived = archived,
            external = distribution?.external ?: false,
            buildSystem = buildSystem.name,
            labels = labels,
            gav = distribution?.gav,
            docker = distribution?.docker,
            buildVersion = detailedComponentVersion.buildVersion.version,
        )

    private companion object {
        const val SERVER_ERROR = 500
    }
}
