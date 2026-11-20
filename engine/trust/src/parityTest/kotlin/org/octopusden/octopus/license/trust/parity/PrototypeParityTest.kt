package org.octopusden.octopus.license.trust.parity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.LicenseEvaluator
import org.octopusden.octopus.license.evaluation.parity.EvaluationParityDiff
import org.octopusden.octopus.license.evaluation.parity.EvaluationParityView
import org.octopusden.octopus.license.inventory.impl.JfrogLicenseInventoryScanner
import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.inventory.parity.ParityAccepted
import org.octopusden.octopus.license.inventory.parity.ParityDiff
import org.octopusden.octopus.license.inventory.parity.ParityView
import org.octopusden.octopus.license.policy.impl.BitbucketLicensePolicySource
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.target.impl.RegistryLicenseTargetResolver
import org.octopusden.octopus.license.target.model.TargetResolution
import org.octopusden.octopus.license.trust.TrustGrader
import java.io.File
import java.time.Duration

/**
 * Resolves, scans, evaluates and grades every component of a prototype output directory against the
 * real components registry, Artifactory, Xray and license registry, and writes each difference to
 * `build/parity/report.md` (D10). Run it right after the prototype, so both read the same state:
 * `./gradlew :license-trust:parityTest`.
 *
 * Environment: the evaluation's (`PROTOTYPE_RUN_DIR`, `COMPONENTS_REGISTRY_URL`, `JFROG_URL`,
 * `JFROG_TOKEN`, `INTERNAL_GROUPS`, `OWN_REPOSITORY_PREFIXES`, `BITBUCKET_URL`, `BITBUCKET_TOKEN`,
 * `LICENSE_REGISTRY_PROJECT`, `LICENSE_REGISTRY_REPOSITORY`, optionally `PARITY_COMPONENTS` and
 * `PARITY_ACCEPTED`, whose differences are reported as accepted and fail nothing).
 */
class PrototypeParityTest {
    private fun env(name: String) = requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name is not set" }

    private fun list(name: String) = env(name).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * @param followsFromEvaluation trust differences on a component whose assessment already differs on
     * purpose ("policy rules" or "deliberate"): those differences move counts grading reads (D10).
     */
    private class Result(
        val component: String,
        val differences: List<Pair<String, String?>>,
        val policyRules: List<String> = emptyList(),
        val deliberate: List<String> = emptyList(),
        val followsFromEvaluation: List<String> = emptyList(),
        val trust: String = "",
    ) {
        val unaccepted = differences.count { it.second == null }

        fun summary() =
            when {
                differences.isEmpty() -> "same"
                unaccepted == 0 -> "${differences.size} accepted"
                else -> "$unaccepted difference(s), ${differences.size - unaccepted} accepted"
            }
    }

    private val resolver by lazy { RegistryLicenseTargetResolver(env("COMPONENTS_REGISTRY_URL"), emptyList(), Duration.ofSeconds(45)) }
    private val scanner by lazy {
        JfrogLicenseInventoryScanner(
            env("JFROG_URL"),
            env("JFROG_TOKEN"),
            OwnershipRules(list("INTERNAL_GROUPS"), list("OWN_REPOSITORY_PREFIXES")),
        )
    }
    private val evaluator = LicenseEvaluator()
    private val grader = TrustGrader()

    @Test
    @DisplayName("the service's inventory, evaluation and trust equal the prototype's, component by component")
    fun parity() {
        val run = File(env("PROTOTYPE_RUN_DIR"), "components")
        val only = System
            .getenv("PARITY_COMPONENTS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
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
        val mapper = ObjectMapper()

        val components =
            run
                .listFiles { file -> file.extension == "json" }!!
                .sortedBy { it.name }
                .filter { only == null || it.nameWithoutExtension in only }
                .map { mapper.readTree(it) }
        val (gradable, ungradable) = components.partition(TrustParityView::gradable)
        val results = gradable.map { compare(it, policy, accepted) }

        val report = File("build/parity/report.md").apply { parentFile.mkdirs() }
        report.writeText(
            buildString {
                appendLine("# Parity with ${run.parentFile}, license registry ${policy.commit}")
                appendLine()
                appendLine("| Component | Result | Trust | Policy rules | Follows from evaluation |")
                appendLine("| --- | --- | --- | --- | --- |")
                results.forEach {
                    appendLine(
                        "| ${it.component} | ${it.summary()} | ${it.trust} | ${it.policyRules.size} | ${it.followsFromEvaluation.size} |",
                    )
                }
                if (ungradable.isNotEmpty()) {
                    appendLine()
                    appendLine(
                        "Not compared, the prototype failed or found no version: " +
                            ungradable.joinToString(", ") { it.path("component").asText() },
                    )
                }
                results
                    .filter { it.differences.size + it.policyRules.size + it.deliberate.size + it.followsFromEvaluation.size > 0 }
                    .forEach { result ->
                        appendLine()
                        appendLine("## ${result.component}")
                        appendLine()
                        result.differences.forEach { (difference, reason) ->
                            appendLine(if (reason == null) "- $difference" else "- accepted ($reason): $difference")
                        }
                        result.policyRules.forEach { appendLine("- policy rules (evaluation D6): $it") }
                        result.deliberate.forEach { appendLine("- deliberate: $it") }
                        result.followsFromEvaluation.forEach { appendLine("- follows from evaluation: $it") }
                    }
            },
        )
        assertEquals(
            emptyList<String>(),
            results.filter { it.unaccepted > 0 }.map { it.component },
            "components with differences not accepted, see ${report.absolutePath}",
        )
    }

    private fun compare(
        component: JsonNode,
        policy: LicensePolicy,
        accepted: ParityAccepted,
    ): Result {
        val name = component.path("component").asText()

        fun judged(differences: List<String>) = differences.map { it to accepted.reasonFor(name, it) }
        return runCatching {
            when (val resolution = resolver.resolve(name, component.path("version").asText())) {
                is TargetResolution.Excluded -> Result(name, judged(listOf("excluded by the service: ${resolution.reason}")))
                is TargetResolution.Resolved -> {
                    // A FULL report's wait: the prototype reads Xray's scan as complete.
                    val inventory = scanner.scan(resolution.target, Duration.ofMinutes(5))
                    val assessment = evaluator.evaluate(inventory, policy)
                    val grade = grader.grade(assessment, resolution.target.labels)
                    val theirs = ParityView.ofPrototype(component)
                    val ours = ParityView.of(inventory)
                    val evaluation =
                        EvaluationParityDiff.diff(
                            EvaluationParityView.ofPrototype(component),
                            EvaluationParityView.of(assessment),
                            (theirs.packages.keys - ours.packages.keys) + (ours.packages.keys - theirs.packages.keys),
                        )
                    val trust = TrustParityDiff.diff(TrustParityView.ofPrototype(component), TrustParityView.of(grade))
                    val excused = evaluation.policyRules.isNotEmpty() || evaluation.deliberate.isNotEmpty()
                    Result(
                        name,
                        judged(
                            ParityDiff.diff(theirs, ours) + evaluation.others +
                                trust.takeUnless { excused }.orEmpty(),
                        ),
                        evaluation.policyRules,
                        evaluation.deliberate,
                        trust.takeIf { excused }.orEmpty(),
                        "${component.path("confidence").asText()} / ${grade.level}",
                    )
                }
            }
        }.getOrElse { Result(name, judged(listOf("failed: ${it.javaClass.simpleName}: ${it.message}"))) }
    }
}
