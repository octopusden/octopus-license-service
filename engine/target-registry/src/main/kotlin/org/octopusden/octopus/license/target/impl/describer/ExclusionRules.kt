package org.octopusden.octopus.license.target.impl.describer

import org.octopusden.octopus.license.target.model.ConfiguredExclusion

/**
 * Decides whether a component is out of the service's scope, and why. Every rule reads only the
 * component-version call, so an excluded component never costs a second registry call.
 */
internal class ExclusionRules(
    configured: List<ConfiguredExclusion>,
) {
    private val configuredReasons = configured.flatMap { exclusion -> exclusion.components.map { it to exclusion.reason } }.toMap()

    /** The reason of the first rule that holds, in a fixed order, or null when the component is in scope. */
    fun reasonFor(component: RegistryComponent): String? =
        when {
            component.archived -> "Archived component"
            !component.external -> "Internal component: its distribution is not external"
            component.buildSystem == WHISKEY -> "Built with WHISKEY"
            component.testComponent -> "Test component"
            DOCUMENTATION_PREFIXES.any { component.id.startsWith(it, ignoreCase = true) } -> "Documentation component"
            else -> configuredReasons[component.id]
        }

    private companion object {
        const val WHISKEY = "WHISKEY"
        val DOCUMENTATION_PREFIXES = listOf("doc_", "doc-")
    }
}
