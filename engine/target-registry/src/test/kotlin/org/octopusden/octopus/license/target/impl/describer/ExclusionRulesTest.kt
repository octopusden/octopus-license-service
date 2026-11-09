package org.octopusden.octopus.license.target.impl.describer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.target.model.ConfiguredExclusion

class ExclusionRulesTest {
    private val rules = ExclusionRules(listOf(ConfiguredExclusion(listOf("test_user_guides"), "Documentation component")))

    @Test
    @DisplayName("an external, non-archived Gradle component with no special label is not excluded")
    fun ordinaryComponent() {
        assertNull(rules.reasonFor(registryComponent()))
    }

    @Test
    @DisplayName("an archived component is excluded as archived")
    fun archived() {
        assertEquals("Archived component", rules.reasonFor(registryComponent(archived = true)))
    }

    @Test
    @DisplayName("a component whose distribution is not external is excluded as internal")
    fun internal() {
        assertEquals(
            "Internal component: its distribution is not external",
            rules.reasonFor(registryComponent(external = false)),
        )
    }

    @Test
    @DisplayName("a WHISKEY component is excluded")
    fun whiskey() {
        assertEquals("Built with WHISKEY", rules.reasonFor(registryComponent(buildSystem = "WHISKEY")))
    }

    @Test
    @DisplayName("a component the registry flags as a test component is excluded")
    fun testComponent() {
        assertEquals("Test component", rules.reasonFor(registryComponent(testComponent = true)))
    }

    @Test
    @DisplayName("the label test-component alone does not exclude: the registry's flag decides")
    fun testComponentLabelIgnored() {
        assertNull(rules.reasonFor(registryComponent(labels = setOf("test-component"))))
    }

    @ParameterizedTest
    @ValueSource(strings = ["doc_guide", "doc-guide", "DOC-User-Guide", "Doc_Sample"])
    @DisplayName("a component named doc_ or doc- in any case is a documentation component")
    fun documentationByName(id: String) {
        assertEquals("Documentation component", rules.reasonFor(registryComponent(id = id)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["docker-tools", "doc", "mydoc_x", "documentation-service"])
    @DisplayName("a name that only contains doc is not a documentation component")
    fun notDocumentationByName(id: String) {
        assertNull(rules.reasonFor(registryComponent(id = id)))
    }

    @Test
    @DisplayName("a configured component is excluded with the configured reason")
    fun configured() {
        val rules = ExclusionRules(listOf(ConfiguredExclusion(listOf("legacy-plugin-app"), "Applies the old license Gradle plugin")))
        assertEquals("Applies the old license Gradle plugin", rules.reasonFor(registryComponent(id = "legacy-plugin-app")))
    }

    @Test
    @DisplayName("every component of one configured entry is excluded with that entry's reason")
    fun configuredSharedReason() {
        val rules =
            ExclusionRules(listOf(ConfiguredExclusion(listOf("legacy-app-a", "legacy-app-b"), "Applies the old license Gradle plugin")))
        assertEquals("Applies the old license Gradle plugin", rules.reasonFor(registryComponent(id = "legacy-app-a")))
        assertEquals("Applies the old license Gradle plugin", rules.reasonFor(registryComponent(id = "legacy-app-b")))
    }

    @Test
    @DisplayName("configured exclusions match the component id exactly")
    fun configuredExact() {
        assertNull(rules.reasonFor(registryComponent(id = "test_user_guides_v2")))
    }

    @Test
    @DisplayName("when several rules hold, the first in order gives the reason")
    fun firstRuleWins() {
        assertEquals(
            "Archived component",
            rules.reasonFor(registryComponent(id = "doc_x", archived = true, buildSystem = "WHISKEY")),
        )
    }
}
