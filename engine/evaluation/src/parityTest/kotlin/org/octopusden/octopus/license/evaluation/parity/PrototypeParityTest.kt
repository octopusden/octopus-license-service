package org.octopusden.octopus.license.evaluation.parity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.inventory.impl.JfrogLicenseInventoryScanner
import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.inventory.parity.ParityAccepted
import org.octopusden.octopus.license.inventory.parity.ParityDiff
import org.octopusden.octopus.license.inventory.parity.ParityView
import org.octopusden.octopus.license.policy.impl.BitbucketLicensePolicySource
import org.octopusden.octopus.license.target.impl.RegistryLicenseTargetResolver
import org.octopusden.octopus.license.target.model.TargetResolution
import java.io.File
import java.time.Duration

/**
 * Resolves, scans and evaluates every component of a prototype output directory against the real
 * components registry, Artifactory, Xray and license registry, and writes each difference to
 * `build/parity/report.md` (D11). Run it right after the prototype, so both read the same state:
 * `./gradlew :license-evaluation:parityTest`.
 *
 * Environment: the inventory's (`PROTOTYPE_RUN_DIR`, `COMPONENTS_REGISTRY_URL`, `JFROG_URL`,
 * `JFROG_TOKEN`, `INTERNAL_GROUPS`, `OWN_REPOSITORY_PREFIXES`, optionally `PARITY_COMPONENTS` and
 * `PARITY_ACCEPTED`, whose differences are reported as accepted and fail nothing), and
 * `BITBUCKET_URL`, `BITBUCKET_TOKEN`, `LICENSE_REGISTRY_PROJECT`, `LICENSE_REGISTRY_REPOSITORY`.
 */
class PrototypeParityTest {
    private fun env(name: String) = requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name is not set" }

    private fun list(name: String) = env(name).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private class Result(
        val component: String,
        val differences: List<Pair<String, String?>>,
        val policyRules: List<String> = emptyList(),
        val deliberate: List<String> = emptyList(),
    ) {
        val unaccepted = differences.count { it.second == null }

        fun summary() =
            when {
                differences.isEmpty() -> "same"
                unaccepted == 0 -> "${differences.size} accepted"
                else -> "$unaccepted difference(s), ${differences.size - unaccepted} accepted"
            }
    }

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
        val resolver = RegistryLicenseTargetResolver(env("COMPONENTS_REGISTRY_URL"), emptyList(), Duration.ofSeconds(45))
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
        val accepted =
            System.getenv("PARITY_ACCEPTED")?.takeIf { it.isNotBlank() }?.let { ParityAccepted.parse(File(it).readLines()) }
                ?: ParityAccepted.NONE
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

                    fun result(
                        differences: List<String>,
                        evaluation: EvaluationDiffs? = null,
                    ) = Result(
                        name,
                        differences.map { it to accepted.reasonFor(name, it) },
                        evaluation?.policyRules.orEmpty(),
                        evaluation?.deliberate.orEmpty(),
                    )
                    runCatching {
                        when (val resolution = resolver.resolve(name, version)) {
                            is TargetResolution.Excluded -> result(listOf("excluded by the service: ${resolution.reason}"))
                            is TargetResolution.Resolved -> {
                                // A FULL report's wait: the prototype reads Xray's scan as complete.
                                val inventory = scanner.scan(resolution.target, Duration.ofMinutes(5))
                                val evaluation =
                                    EvaluationParityDiff.diff(
                                        EvaluationParityView.ofPrototype(component),
                                        EvaluationParityView.of(evaluator.evaluate(inventory, policy)),
                                    )
                                result(
                                    ParityDiff.diff(ParityView.ofPrototype(component), ParityView.of(inventory)) + evaluation.others,
                                    evaluation,
                                )
                            }
                        }
                    }.getOrElse { result(listOf("failed: ${it.javaClass.simpleName}: ${it.message}")) }
                }

        val report = File("build/parity/report.md").apply { parentFile.mkdirs() }
        report.writeText(
            buildString {
                appendLine("# Parity with ${run.parentFile}, license registry ${policy.commit}")
                appendLine()
                appendLine("| Component | Result | Policy rules |")
                appendLine("| --- | --- | --- |")
                results.forEach { appendLine("| ${it.component} | ${it.summary()} | ${it.policyRules.size} |") }
                results.filter { it.differences.size + it.policyRules.size + it.deliberate.size > 0 }.forEach { result ->
                    appendLine()
                    appendLine("## ${result.component}")
                    appendLine()
                    result.differences.forEach { (difference, reason) ->
                        appendLine(if (reason == null) "- $difference" else "- accepted ($reason): $difference")
                    }
                    result.policyRules.forEach { appendLine("- policy rules (D6): $it") }
                    result.deliberate.forEach { appendLine("- deliberate: $it") }
                }
            },
        )
        assertEquals(
            emptyList<String>(),
            results.filter { it.unaccepted > 0 }.map { it.component },
            "components with differences not accepted, see ${report.absolutePath}",
        )
    }
}
