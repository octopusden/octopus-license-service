package org.octopusden.octopus.license.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.client.impl.ClassicLicenseServiceClient
import org.octopusden.octopus.license.client.impl.LicenseServiceClientParametersProvider
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.exception.ServiceUnavailableException
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.service.state.policy.LicensePolicyRefresherTest.FakeLicensePolicySource
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.octopus.license.target.model.TargetResolution
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** The service while the license registry has never been read: Bitbucket is down from the start. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "components-registry-service.url=http://localhost:1",
        "bitbucket.host=http://localhost:1",
        "bitbucket.token=token",
        "artifactory.url=http://localhost:1",
        "artifactory.token=token",
        "license-service.license-registry.project=SAMPLE",
        "license-service.license-registry.repository=sample-license-registry",
        "license-service.ownership.internal-groups=org.example",
        "license-service.ownership.own-repository-prefixes=rnd-",
    ],
)
class LicensePolicyNotLoadedApiTest {
    @LocalServerPort
    private var port: Int = 0

    private val client by lazy {
        ClassicLicenseServiceClient(
            object : LicenseServiceClientParametersProvider {
                override fun getApiUrl() = "http://localhost:$port"
            },
        )
    }

    @Test
    @DisplayName("D7: before the first load, starting a report answers 503, a ServiceUnavailableException for the client")
    fun startBeforeFirstLoad() {
        val response =
            HttpClient.newHttpClient().send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/rest/api/1/license-reports"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"component":"sample-app","version":"1.0"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(HTTP_SERVICE_UNAVAILABLE, response.statusCode())
        assertTrue(response.body().startsWith("""{"error":{"code":503,"""), response.body())
        assertThrows<ServiceUnavailableException> { client.startReport(LicenseReportRequest("sample-app", "1.0")) }
    }

    @Test
    @DisplayName("D7: before the first load, the license registry health is down")
    fun healthDownBeforeFirstLoad() {
        val response =
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://localhost:$port/actuator/health/licenseRegistry")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(HTTP_SERVICE_UNAVAILABLE, response.statusCode(), response.body())
        assertTrue(response.body().contains(""""status":"DOWN""""), response.body())
    }

    @Test
    @DisplayName("D7: before the first load the pod is alive but not ready, so OKD keeps it and sends it no traffic")
    fun aliveButNotReadyBeforeFirstLoad() {
        assertEquals(HTTP_SERVICE_UNAVAILABLE, status("/actuator/health/readiness"))
        assertEquals(HTTP_OK, status("/actuator/health/liveness"))
    }

    private fun status(path: String) =
        HttpClient
            .newHttpClient()
            .send(
                HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            ).statusCode()

    @TestConfiguration
    class RegistryDownConfig {
        @Bean
        @Primary
        fun downLicensePolicySource(): LicensePolicySource = FakeLicensePolicySource().apply { down = true }

        @Bean
        @Primary
        fun anyTargetResolver(): LicenseTargetResolver =
            object : LicenseTargetResolver {
                override fun resolve(
                    component: String,
                    version: String,
                ) = TargetResolution.Resolved(
                    LicenseTarget(
                        componentVersion = ComponentVersion(component, version),
                        artifactVersions = listOf(version),
                        buildSystem = "GRADLE",
                        labels = emptySet(),
                        explicit = true,
                        distribution = DistributionSpec(emptyList(), emptyList()),
                    ),
                )
            }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
