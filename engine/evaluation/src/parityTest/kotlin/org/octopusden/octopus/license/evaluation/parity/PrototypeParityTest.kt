package org.octopusden.octopus.license.evaluation.parity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.impl.JfrogLicenseInventoryScanner
import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.inventory.parity.ParityDiff
import org.octopusden.octopus.license.inventory.parity.ParityView
import org.octopusden.octopus.license.policy.impl.BitbucketLicensePolicySource
import org.octopusden.octopus.license.target.impl.RegistryLicenseTargetResolver
import org.octopusden.octopus.license.target.model.TargetResolution
import java.io.File

/**
 * Resolves, scans and evaluates every component of a prototype output directory against the real
 * components registry, Artifactory, Xray and license registry, and writes each difference to
 * `build/parity/report.md` (D11). Run it right after the prototype, so both read the same state:
 * `./gradlew :license-evaluation:parityTest`.
 *
 * Environment: the inventory's (`PROTOTYPE_RUN_DIR`, `COMPONENTS_REGISTRY_URL`, `JFROG_URL`,
 * `JFROG_TOKEN`, `INTERNAL_GROUPS`, `OWN_REPOSITORY_PREFIXES`, optionally `PARITY_COMPONENTS`), and
 * `BITBUCKET_URL`, `BITBUCKET_TOKEN`, `LICENSE_REGISTRY_PROJECT`, `LICENSE_REGISTRY_REPOSITORY`.
 */
class PrototypeParityTest {
    private fun env(name: String) = requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name is not set" }

    private fun list(name: String) = env(name).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private class Result(
        val component: String,
        val differences: List<String>,
        val policyRules: List<String> = emptyList(),
    )

    @Test
    @DisplayName("the service's inventory and evaluation equal the prototype's, component by component")
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
        val policy =
            BitbucketLicensePolicySource(
                env("BITBUCKET_URL"),
                env("BITBUCKET_TOKEN"),
                env("LICENSE_REGISTRY_PROJECT"),
                env("LICENSE_REGISTRY_REPOSITORY"),
            ).load()
        val evaluator = LicenseEvaluator()
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
                    runCatching {
                        when (val resolution = resolver.resolve(name, version)) {
                            is TargetResolution.Excluded -> Result(name, listOf("excluded by the service: ${resolution.reason}"))
                            is TargetResolution.Resolved -> {
                                val inventory = scanner.scan(resolution.target)
                                val evaluation =
                                    EvaluationParityDiff.diff(
                                        EvaluationParityView.ofPrototype(component),
                                        EvaluationParityView.of(evaluator.evaluate(inventory, policy)),
                                    )
                                Result(
                                    name,
                                    ParityDiff.diff(ParityView.ofPrototype(component), ParityView.of(inventory)) + evaluation.others,
                                    evaluation.policyRules,
                                )
                            }
                        }
                    }.getOrElse { Result(name, listOf("failed: ${it.javaClass.simpleName}: ${it.message}")) }
                }

        val report = File("build/parity/report.md").apply { parentFile.mkdirs() }
        report.writeText(
            buildString {
                appendLine("# Parity with ${run.parentFile}, license registry ${policy.commit}")
                appendLine()
                appendLine("| Component | Result | Policy rules |")
                appendLine("| --- | --- | --- |")
                results.forEach {
                    val result = if (it.differences.isEmpty()) "same" else "${it.differences.size} difference(s)"
                    appendLine("| ${it.component} | $result | ${it.policyRules.size} |")
                }
                results.filter { it.differences.isNotEmpty() || it.policyRules.isNotEmpty() }.forEach { result ->
                    appendLine()
                    appendLine("## ${result.component}")
                    appendLine()
                    result.differences.forEach { appendLine("- $it") }
                    result.policyRules.forEach { appendLine("- policy rules (D6): $it") }
                }
            },
        )
        assertEquals(
            emptyList<String>(),
            results.filter { it.differences.isNotEmpty() }.map { it.component },
            "components that differ, see ${report.absolutePath}",
        )
    }
}
