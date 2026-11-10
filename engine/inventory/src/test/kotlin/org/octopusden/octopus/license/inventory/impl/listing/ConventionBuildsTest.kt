package org.octopusden.octopus.license.inventory.impl.listing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.target

class ConventionBuildsTest {
    @Test
    @DisplayName("an explicit external component builds as {component}_ee, its images as {component}_ee_docker")
    fun explicitComponent() {
        assertEquals(listOf("sample-portal_ee", "sample-portal_ee_docker"), ConventionBuilds.names(target(explicit = true)))
    }

    @Test
    @DisplayName("an implicit component builds as {component}_ie: internal components never reach the scan (D4)")
    fun implicitComponent() {
        assertEquals("sample-portal_ie", ConventionBuilds.name(target(explicit = false)))
    }
}
