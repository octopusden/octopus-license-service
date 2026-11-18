package org.octopusden.octopus.license.trust.impl

import org.octopusden.octopus.license.trust.model.TrustReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode

/**
 * Do the package types found match what the component is? A language its labels name with no library
 * of its kind, or a type our sources list completely only through an SBOM (D4). A label or type not
 * in these tables is not matched.
 */
internal object EcosystemRule : ComponentRule {
    private val LABEL_ECOSYSTEMS =
        mapOf(
            "javascript" to setOf("npm"),
            "typescript" to setOf("npm"),
            "python" to setOf("pypi"),
            "go" to setOf("golang"),
            "dart" to setOf("pub"),
            "swift" to setOf("cocoapods", "swift"),
            "objective_c" to setOf("cocoapods", "swift"),
            "c" to setOf("cpp", "conan"),
            "cpp" to setOf("cpp", "conan"),
            "cs" to setOf("nuget"),
        )

    // Seen completely without an SBOM; anything else is compiled into an app where Xray recognises scraps.
    private val COMPLETE_TYPES =
        setOf("maven", "generic", "golang", "npm", "pypi", "huggingfaceml", "model", "helm", "docker", "rpm", "deb", "alpine")

    override fun reasons(subject: GradingSubject) = listOfNotNull(notDetected(subject), unavailable(subject))

    private fun notDetected(subject: GradingSubject): TrustReason? {
        if (subject.read.isEmpty()) return null
        val types = subject.thirdParty.map { it.library.id.type }
        val missing =
            subject.labels
                .filter { it in LABEL_ECOSYSTEMS }
                .sorted()
                .filter { label -> LABEL_ECOSYSTEMS.getValue(label).none { it in types } }
                .groupBy { LABEL_ECOSYSTEMS.getValue(it).sorted().joinToString("/") }
        if (missing.isEmpty()) return null
        // Python's Counter.most_common: by count, a tie in the order the type first appears.
        val detected =
            types
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${it.key} ${it.value}" }
                .ifEmpty { "nothing" }
        val labels = missing.entries.joinToString(", ") { (ecosystem, labels) -> "$ecosystem (label ${labels.joinToString(" ")})" }
        return TrustReason(TrustReasonCode.ECOSYSTEM_NOT_DETECTED, "missing $labels; detected $detected")
    }

    private fun unavailable(subject: GradingSubject): TrustReason? {
        val unlisted = subject.thirdParty
            .map { it.library.id.type }
            .filter { it !in COMPLETE_TYPES }
            .toSortedSet()
        if (unlisted.isEmpty() || subject.sbomSeen) return null
        return TrustReason(TrustReasonCode.FULL_LIST_UNAVAILABLE, "found ${unlisted.joinToString(", ")} libraries, but no SBOM")
    }
}
