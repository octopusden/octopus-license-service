package org.octopusden.octopus.license.target.impl

import com.fasterxml.jackson.databind.ObjectMapper
import feign.FeignException
import feign.Retryer
import feign.codec.DecodeException
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClientUrlProvider
import org.octopusden.octopus.components.registry.core.dto.DetailedComponent
import org.octopusden.octopus.components.registry.core.exceptions.NotFoundException
import org.octopusden.octopus.components.registry.core.exceptions.RepositoryNotPreparedException
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.exception.ComponentVersionNotFoundException
import org.octopusden.octopus.license.target.exception.ComponentsRegistryUnavailableException
import org.octopusden.octopus.license.target.exception.UnexpectedRegistryAnswerException
import org.octopusden.octopus.license.target.impl.describer.ExclusionRules
import org.octopusden.octopus.license.target.impl.describer.LicenseTargetDescriber
import org.octopusden.octopus.license.target.impl.describer.RegistryArtifactPattern
import org.octopusden.octopus.license.target.impl.describer.RegistryComponent
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.ConfiguredExclusion
import org.octopusden.octopus.license.target.model.TargetResolution
import org.octopusden.releng.versions.VersionNames
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Reads the components registry and nothing else: the component version first, and the Maven
 * artifact patterns and version names only for a component the [ExclusionRules] keep.
 *
 * @param deadline how long [resolve] waits for the registry before it reports it unavailable. A caller
 * waits on the resolution, so it must stay below that caller's own read timeout.
 */
class RegistryLicenseTargetResolver(
    registryUrl: String,
    exclusions: List<ConfiguredExclusion>,
    private val deadline: Duration,
) : LicenseTargetResolver {
    // A caller waits on this per request; the client's default of 5 attempts can hold it for minutes
    // when the registry hangs, and its read timeout cannot be set from here.
    private val client: ComponentsRegistryServiceClient =
        ClassicComponentsRegistryServiceClient(
            object : ClassicComponentsRegistryServiceClientUrlProvider {
                override fun getApiUrl() = registryUrl
            },
            ClassicComponentsRegistryServiceClient.configureObjectMapper(ObjectMapper()),
            Retryer.Default(RETRY_PERIOD_MS, RETRY_MAX_PERIOD_MS, MAX_ATTEMPTS),
        )

    private val exclusionRules = ExclusionRules(exclusions)

    // The registry client's read timeout cannot be set, so the deadline is kept by waiting on another
    // thread. A call past it is abandoned, not stopped: it ends when the client's own timeout does.
    private val calls = Executors.newVirtualThreadPerTaskExecutor()

    override fun resolve(
        component: String,
        version: String,
    ): TargetResolution {
        val resolution = calls.submit<TargetResolution> { resolveNow(component, version) }
        return try {
            resolution.get(deadline.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            resolution.cancel(true)
            throw ComponentsRegistryUnavailableException("Components registry did not answer within $deadline", e)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun resolveNow(
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
            throw ComponentsRegistryUnavailableException("Components registry is still loading its repository", e)
        } catch (e: DecodeException) {
            throw UnexpectedRegistryAnswerException(
                "Components registry answered $component:$version with a body that could not be read",
                e,
            )
        } catch (e: FeignException) {
            // status() is -1 when no answer came back at all: refused, reset or timed out. Feign's message
            // names the registry's URL, which callers must not see; it stays in the cause for the log.
            if (e.status() >= SERVER_ERROR || e.status() < 0) {
                throw ComponentsRegistryUnavailableException("Components registry is unavailable", e)
            }
            throw UnexpectedRegistryAnswerException("Components registry answered ${e.status()} for $component:$version", e)
        }

    private fun DetailedComponent.toFacts(version: String) =
        RegistryComponent(
            id = id,
            version = version,
            archived = archived,
            external = distribution?.external ?: false,
            buildSystem = buildSystem.name,
            testComponent = testComponent,
            labels = labels,
            gav = distribution?.gav,
            docker = distribution?.docker,
            buildVersion = detailedComponentVersion.buildVersion.version,
        )

    private companion object {
        const val SERVER_ERROR = 500
        const val RETRY_PERIOD_MS = 100L
        const val RETRY_MAX_PERIOD_MS = 500L
        const val MAX_ATTEMPTS = 2
    }
}
