package org.octopusden.octopus.license.target

/**
 * Decides whether a component is out of the service's scope, and why. Every rule reads only the
 * component-version call, so an excluded component never costs a second registry call (D4).
 */
internal class ExclusionRules(
    configured: List<ConfiguredExclusion>,
) {
    private val configuredReasons = configured.associate { it.component to it.reason }

    /** The reason of the first rule that holds, in a fixed order, or null when the component is in scope. */
    fun reasonFor(component: RegistryComponent): String? =
        when {
            component.archived -> "Archived component"
            !component.external -> "Internal component: its distribution is not external"
            component.buildSystem == WHISKEY -> "Built with WHISKEY"
            TEST_COMPONENT_LABEL in component.labels -> "Test component (label $TEST_COMPONENT_LABEL)"
            DOCUMENTATION_PREFIXES.any { component.id.startsWith(it, ignoreCase = true) } -> "Documentation component"
            else -> configuredReasons[component.id]
        }

    private companion object {
        const val WHISKEY = "WHISKEY"
        const val TEST_COMPONENT_LABEL = "test-component"
        val DOCUMENTATION_PREFIXES = listOf("doc_", "doc-")
    }
}
