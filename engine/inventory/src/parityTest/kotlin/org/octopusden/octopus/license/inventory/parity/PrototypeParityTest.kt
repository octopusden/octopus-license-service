package org.octopusden.octopus.license.inventory.parity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.JfrogLicenseInventoryScanner
import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.target.impl.RegistryLicenseTargetResolver
import org.octopusden.octopus.license.target.model.TargetResolution
import java.io.File

/**
 * Scans every component of a prototype output directory against the real components registry,
 * Artifactory and Xray, and writes each difference to `build/parity/report.md` (D16). Run it right
 * after the prototype, so both read the same state: `./gradlew :license-inventory:parityTest`.
 *
 * Environment: `PROTOTYPE_RUN_DIR` (holding `components/`), `COMPONENTS_REGISTRY_URL`, `JFROG_URL`,
 * `JFROG_TOKEN`, `INTERNAL_GROUPS` and `OWN_REPOSITORY_PREFIXES` (comma-separated), and optionally
 * `PARITY_COMPONENTS` to limit the run.
 */
class PrototypeParityTest {
    private fun env(name: String) = requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name is not set" }

    private fun list(name: String) = env(name).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    @DisplayName("the service's inventory equals the prototype's, component by component")
    fun parity() {
        val run = File(env("PROTOTYPE_RUN_DIR"), "components")
        val only = System
            .getenv("PARITY_COMPONENTS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
        val resolver = RegistryLicenseTargetResolver(env("COMPONENTS_REGISTRY_URL"), emptyList())
        val scanner =
            JfrogLicenseInventoryScanner(
                env("JFROG_URL"),
                env("JFROG_TOKEN"),
                OwnershipRules(list("INTERNAL_GROUPS"), list("OWN_REPOSITORY_PREFIXES")),
            )
        val mapper = ObjectMapper()

        val results =
            run
                .listFiles { file -> file.extension == "json" }!!
                .sortedBy { it.name }
                .filter { only == null || it.nameWithoutExtension in only }
                .map { file ->
                    val component = mapper.readTree(file)
                    val name = component.path("component").asText()
                    val version = component.path("version").asText()
                    name to
                        runCatching {
                            when (val resolution = resolver.resolve(name, version)) {
                                is TargetResolution.Excluded -> listOf("excluded by the service: ${resolution.reason}")
                                is TargetResolution.Resolved ->
                                    ParityDiff.diff(ParityView.ofPrototype(component), ParityView.of(scanner.scan(resolution.target)))
                            }
                        }.getOrElse { listOf("failed: ${it.javaClass.simpleName}: ${it.message}") }
                }

        val report = File("build/parity/report.md").apply { parentFile.mkdirs() }
        report.writeText(
            buildString {
                appendLine("# Parity with ${run.parentFile}")
                appendLine()
                appendLine("| Component | Result |")
                appendLine("| --- | --- |")
                results.forEach { (name, diffs) ->
                    appendLine("| $name | ${if (diffs.isEmpty()) "same" else "${diffs.size} difference(s)"} |")
                }
                results.filter { it.second.isNotEmpty() }.forEach { (name, diffs) ->
                    appendLine()
                    appendLine("## $name")
                    appendLine()
                    diffs.forEach { appendLine("- $it") }
                }
            },
        )
        assertEquals(
            emptyList<String>(),
            results.filter { it.second.isNotEmpty() }.map { it.first },
            "components that differ, see ${report.absolutePath}",
        )
    }
}
