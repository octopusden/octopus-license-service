package org.octopusden.octopus.license.target.impl.describer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.target.model.ArtifactPattern
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.model.DeclaredArtifact.MavenArtifact
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.releng.versions.VersionNames

class LicenseTargetDescriberTest {
    private val versionNames = VersionNames("serviceCBranch", "serviceC", "minorC")

    private fun describe(
        component: RegistryComponent = registryComponent(),
        patterns: Map<String, RegistryArtifactPattern> = emptyMap(),
    ) = LicenseTargetDescriber.describe(component, patterns, versionNames)

    @Test
    @DisplayName("the worked example: declared artifacts, the pattern whose range holds the version, both versions")
    fun workedExample() {
        val target = describe(
            registryComponent(
                labels = setOf("java"),
                gav = "org.example.sample:sample-app:zip,org.example.sample:sample-app-config:zip:config",
                docker = "sample/sample-app",
                buildVersion = "1.4.12-0034",
            ),
            mapOf(
                "[1.0,)" to RegistryArtifactPattern("org.example.sample", "sample-.*"),
                "(,1.0)" to RegistryArtifactPattern("org.octopusden.legacy", "legacy-.*"),
            ),
        )
        assertEquals(
            LicenseTarget(
                componentVersion = ComponentVersion("sample-app", "1.4.12"),
                artifactVersions = listOf("1.4.12", "1.4.12-0034"),
                buildSystem = "GRADLE",
                labels = setOf("java"),
                explicit = true,
                distribution = DistributionSpec(
                    declared = listOf(
                        MavenArtifact("org.example.sample", "sample-app", "zip"),
                        MavenArtifact("org.example.sample", "sample-app-config", "zip", "config"),
                        DockerImage("sample/sample-app"),
                    ),
                    patterns = listOf(ArtifactPattern(listOf("org.example.sample"), listOf("sample-.*"))),
                ),
            ),
            target,
        )
    }

    @Test
    @DisplayName("an explicit component's target is explicit")
    fun explicitComponent() {
        assertEquals(true, describe(registryComponent(explicit = true)).explicit)
    }

    @Test
    @DisplayName("an implicit component's target is not explicit, which names its builds {component}_ie")
    fun implicitComponent() {
        assertEquals(false, describe(registryComponent(explicit = false)).explicit)
    }

    @Test
    @DisplayName("no range holding the version leaves no patterns, and the target still resolves")
    fun noRangeHolds() {
        val target = describe(patterns = mapOf("[2.0,)" to RegistryArtifactPattern("org.a", "a")))
        assertEquals(emptyList<ArtifactPattern>(), target.distribution.patterns)
    }

    @Test
    @DisplayName("every entry whose range holds the version is kept")
    fun overlappingRanges() {
        val target = describe(
            patterns = mapOf(
                "[1.0,2.0)" to RegistryArtifactPattern("org.a", "a"),
                "[1.4,)" to RegistryArtifactPattern("org.b", "b"),
            ),
        )
        assertEquals(
            setOf(ArtifactPattern(listOf("org.a"), listOf("a")), ArtifactPattern(listOf("org.b"), listOf("b"))),
            target.distribution.patterns.toSet(),
        )
    }

    @Test
    @DisplayName("comma-separated group and artifact patterns are split and trimmed")
    fun splitPatterns() {
        val target = describe(patterns = mapOf("[1.0,)" to RegistryArtifactPattern("org.a, org.b", " x-.* ")))
        assertEquals(listOf(ArtifactPattern(listOf("org.a", "org.b"), listOf("x-.*"))), target.distribution.patterns)
    }

    @Test
    @DisplayName("a build version equal to the requested version is listed once")
    fun sameBuildVersion() {
        assertEquals(listOf("1.4.12"), describe(registryComponent(buildVersion = "1.4.12")).artifactVersions)
    }

    @Test
    @DisplayName("without a build version only the requested version is listed")
    fun noBuildVersion() {
        assertEquals(listOf("1.4.12"), describe(registryComponent(buildVersion = null)).artifactVersions)
    }
}
