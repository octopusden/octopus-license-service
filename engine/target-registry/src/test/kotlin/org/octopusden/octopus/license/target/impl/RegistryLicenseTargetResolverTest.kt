package org.octopusden.octopus.license.target.impl

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.target.exception.ComponentVersionNotFoundException
import org.octopusden.octopus.license.target.exception.ComponentsRegistryUnavailableException
import org.octopusden.octopus.license.target.model.ArtifactPattern
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.ConfiguredExclusion
import org.octopusden.octopus.license.target.model.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.model.DeclaredArtifact.MavenArtifact
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.octopus.license.target.model.TargetResolution
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Drives the published registry client over HTTP against answers recorded from the components
 * registry (identifying values replaced), so the client is proven on this classpath.
 */
class RegistryLicenseTargetResolverTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, Pair<Int, String>>()
    private lateinit var server: HttpServer

    @BeforeEach
    fun startRegistry() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                requests += path
                val (status, body) = answers[path] ?: (NOT_FOUND to """{"errorMessage":"$path is not found"}""")
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    }

    @AfterEach
    fun stopRegistry() = server.stop(0)

    private fun resolver(url: String = "http://localhost:${server.address.port}") =
        RegistryLicenseTargetResolver(url, listOf(ConfiguredExclusion(listOf("test_user_guides"), "Documentation component")))

    private fun answer(
        path: String,
        resource: String,
    ) {
        answers["$API$path"] = OK to requireNotNull(javaClass.getResource("/registry/$resource")).readText()
    }

    @Test
    @DisplayName("a component in scope is described from three registry calls")
    fun resolved() {
        answer("/components/sample-app/versions/1.4.12", "sample-app-1.4.12.json")
        answer("/components/sample-app/maven-artifacts", "sample-app-maven-artifacts.json")
        answer("/common/version-names", "version-names.json")

        val resolution = resolver().resolve("sample-app", "1.4.12")

        assertEquals(
            TargetResolution.Resolved(
                LicenseTarget(
                    componentVersion = ComponentVersion("sample-app", "1.4.12"),
                    artifactVersions = listOf("1.4.12", "1.4.12-0034"),
                    buildSystem = "GRADLE",
                    labels = emptySet(),
                    distribution = DistributionSpec(
                        declared = listOf(
                            MavenArtifact("org.example.sample", "sample-app", "zip"),
                            MavenArtifact("org.example.sample", "sample-app-config", "yaml"),
                            DockerImage("sample/sample-app"),
                        ),
                        patterns = listOf(ArtifactPattern(listOf("org.example.sample"), listOf("sample-app"))),
                    ),
                ),
            ),
            resolution,
        )
        assertEquals(3, requests.size)
    }

    @Test
    @DisplayName("an excluded component costs exactly one registry call")
    fun excluded() {
        answer("/components/whiskey-component/versions/1.0", "whiskey-component-1.0.json")

        val resolution = resolver().resolve("whiskey-component", "1.0")

        assertEquals(
            TargetResolution.Excluded(ComponentVersion("whiskey-component", "1.0"), "Built with WHISKEY"),
            resolution,
        )
        assertEquals(listOf("$API/components/whiskey-component/versions/1.0"), requests)
    }

    @Test
    @DisplayName("the registry's testComponent flag excludes the component")
    fun testComponentFlag() {
        val body = requireNotNull(javaClass.getResource("/registry/sample-app-1.4.12.json")).readText()
        answers["$API/components/sample-app/versions/1.4.12"] = OK to body.replace("\"testComponent\": false", "\"testComponent\": true")

        assertEquals(
            TargetResolution.Excluded(ComponentVersion("sample-app", "1.4.12"), "Test component"),
            resolver().resolve("sample-app", "1.4.12"),
        )
    }

    @Test
    @DisplayName("a 404 from the registry is a not-found error")
    fun notFound() {
        assertThrows<ComponentVersionNotFoundException> { resolver().resolve("no-such-component", "1.0") }
    }

    @Test
    @DisplayName("a 503 from the registry is retried once, then an unavailable error")
    fun serviceUnavailable() {
        answers["$API/components/sample-app/versions/1.4.12"] = SERVICE_UNAVAILABLE to """{"errorMessage":"down"}"""
        assertThrows<ComponentsRegistryUnavailableException> { resolver().resolve("sample-app", "1.4.12") }
        assertEquals(2, requests.size)
    }

    @Test
    @DisplayName("a registry still loading its repository (425) is an unavailable error")
    fun repositoryNotPrepared() {
        answers["$API/components/sample-app/versions/1.4.12"] = TOO_EARLY to """{"errorMessage":"repository is not prepared"}"""
        assertThrows<ComponentsRegistryUnavailableException> { resolver().resolve("sample-app", "1.4.12") }
    }

    @Test
    @DisplayName("a registry nobody listens on is an unavailable error")
    fun unreachable() {
        val closedPort = ServerSocket(0).use { it.localPort }
        assertThrows<ComponentsRegistryUnavailableException> {
            resolver("http://localhost:$closedPort").resolve("sample-app", "1.4.12")
        }
    }

    private companion object {
        const val API = "/rest/api/2"
        const val OK = 200
        const val NOT_FOUND = 404
        const val TOO_EARLY = 425
        const val SERVICE_UNAVAILABLE = 503
    }
}
