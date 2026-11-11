package org.octopusden.octopus.license.evaluation.parity

/**
 * @param policyRules verdicts that differ although the licenses found and the override agree: the
 * license policy's rules differ from the prototype's `verdict()` on purpose (D6).
 * @param others every other difference.
 */
data class EvaluationDiffs(
    val policyRules: List<String>,
    val others: List<String>,
)

/**
 * Every way the service's evaluation differs from the prototype's, one line each. Only libraries on
 * both sides are compared: one on a single side is the inventory's difference, reported there.
 */
object EvaluationParityDiff {
    fun diff(
        prototype: EvaluationParityView,
        service: EvaluationParityView,
    ): EvaluationDiffs {
        val policyRules = mutableListOf<String>()
        val others = mutableListOf<String>()
        prototype.libraries.keys.intersect(service.libraries.keys).sorted().forEach { id ->
            val theirs = prototype.libraries.getValue(id)
            val ours = service.libraries.getValue(id)
            val sameInputs = theirs.overridden == ours.overridden && theirs.licensesFound == ours.licensesFound
            if (theirs.verdict != ours.verdict) {
                (if (sameInputs) policyRules else others) += "library $id: verdict: prototype ${theirs.verdict}, service ${ours.verdict}"
            }
            if (theirs.licensesFound != ours.licensesFound && theirs.verdict != ours.verdict) {
                others += "library $id: licenses found: prototype ${theirs.licensesFound.sorted()}, service ${ours.licensesFound.sorted()}"
            }
            if (theirs.overridden != ours.overridden) {
                others += "library $id: overridden: prototype ${theirs.overridden}, service ${ours.overridden}"
            }
        }
        return EvaluationDiffs(policyRules, others + skipped(prototype, service) + notAllowed(prototype, service))
    }

    private fun skipped(
        prototype: EvaluationParityView,
        service: EvaluationParityView,
    ) = listOfNotNull(
        "OS packages skipped: prototype ${prototype.osPackages}, service ${service.osPackages}"
            .takeIf { prototype.osPackages != service.osPackages },
        "base-image Python skipped: prototype ${prototype.baseImagePython}, service ${service.baseImagePython}"
            .takeIf { prototype.baseImagePython != service.baseImagePython },
    ) +
        (prototype.skippedPerFile.keys + service.skippedPerFile.keys)
            .filter { prototype.skippedPerFile[it] != service.skippedPerFile[it] }
            .map { "file $it: skipped: prototype ${prototype.skippedPerFile[it] ?: 0}, service ${service.skippedPerFile[it] ?: 0}" }

    // A subject on one side only, for a library both sides judged alike: the rule deriving it differs.
    private fun notAllowed(
        prototype: EvaluationParityView,
        service: EvaluationParityView,
    ): List<String> {
        fun onlyIn(
            side: String,
            subjects: Set<String>,
        ) = subjects
            .filter { prototype.libraries[it]?.verdict.let { verdict -> verdict != null && verdict == service.libraries[it]?.verdict } }
            .sorted()
            .map { "LICENCE_NOT_ALLOWED $it: only in the $side" }

        return onlyIn("prototype", prototype.notAllowed - service.notAllowed) + onlyIn("service", service.notAllowed - prototype.notAllowed)
    }
}
