package org.octopusden.octopus.license.target

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.target.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.DeclaredArtifact.MavenArtifact
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Drives the published registry client over HTTP against answers recorded from the components
 * registry (identifying values replaced), so the client is proven on this classpath (D5).
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
        RegistryLicenseTargetResolver(url, listOf(ConfiguredExclusion("cards_manuals", "Documentation component")))

    private fun answer(
        path: String,
        resource: String,
    ) {
        answers["$API$path"] = OK to requireNotNull(javaClass.getResource("/registry/$resource")).readText()
    }

    @Test
    @DisplayName("a component in scope is described from three registry calls")
    fun resolved() {
        answer("/components/dmp-bundle/versions/1.4.12", "dmp-bundle-1.4.12.json")
        answer("/components/dmp-bundle/maven-artifacts", "dmp-bundle-maven-artifacts.json")
        answer("/common/version-names", "version-names.json")

        val resolution = resolver().resolve("dmp-bundle", "1.4.12")

        assertEquals(
            TargetResolution.Resolved(
                LicenseTarget(
                    componentVersion = ComponentVersion("dmp-bundle", "1.4.12"),
                    artifactVersions = listOf("1.4.12", "1.4.12-0034"),
                    buildSystem = "GRADLE",
                    labels = emptySet(),
                    distribution = DistributionSpec(
                        declared = listOf(
                            MavenArtifact("org.example.dmp", "dmp-bundle", "zip"),
                            MavenArtifact("org.example.dmp", "dmp-bundle-config", "yaml"),
                            DockerImage("dmp/dmp-bundle"),
                        ),
                        patterns = listOf(ArtifactPattern(listOf("org.example.dmp"), listOf("dmp-bundle"))),
                    ),
                ),
            ),
            resolution,
        )
        assertEquals(3, requests.size)
    }

    @Test
    @DisplayName("D4: an excluded component costs exactly one registry call")
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
    @DisplayName("D3: a 404 from the registry is a not-found error")
    fun notFound() {
        assertThrows<ComponentVersionNotFoundException> { resolver().resolve("no-such-component", "1.0") }
    }

    @Test
    @DisplayName("D3: a 503 from the registry is an unavailable error")
    fun serviceUnavailable() {
        answers["$API/components/dmp-bundle/versions/1.4.12"] = SERVICE_UNAVAILABLE to """{"errorMessage":"down"}"""
        assertThrows<ComponentsRegistryUnavailableException> { resolver().resolve("dmp-bundle", "1.4.12") }
    }

    @Test
    @DisplayName("D3: a registry nobody listens on is an unavailable error")
    fun unreachable() {
        val closedPort = ServerSocket(0).use { it.localPort }
        assertThrows<ComponentsRegistryUnavailableException> {
            resolver("http://localhost:$closedPort").resolve("dmp-bundle", "1.4.12")
        }
    }

    private companion object {
        const val API = "/rest/api/2"
        const val OK = 200
        const val NOT_FOUND = 404
        const val SERVICE_UNAVAILABLE = 503
    }
}
