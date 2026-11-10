package org.octopusden.octopus.license.inventory.impl.listing

import org.octopusden.octopus.license.target.model.ArtifactPattern
import java.util.regex.PatternSyntaxException

/**
 * Whether a Maven group and artifact belong to the component by its Maven artifact patterns: both
 * fully match patterns of the same entry. The registry stores Java regular expressions, so they
 * compile here as they do there; one that does not compile matches only its own text.
 */
internal class ArtifactPatternMatcher(
    patterns: List<ArtifactPattern>,
) {
    private val entries = patterns.map { entry -> entry.groups.map(::matcher) to entry.artifacts.map(::matcher) }

    fun matches(
        group: String,
        artifact: String,
    ) = entries.any { (groups, artifacts) -> groups.any { it(group) } && artifacts.any { it(artifact) } }

    private fun matcher(pattern: String): (String) -> Boolean =
        try {
            Regex(pattern).let { regex -> { value -> regex.matches(value) } }
        } catch (_: PatternSyntaxException) {
            { value -> value == pattern }
        }
}
