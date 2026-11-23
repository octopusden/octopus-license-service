package org.octopusden.octopus.license.inventory.impl.adapter.artifactory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.stored

class RepositoryPreferenceTest {
    @Test
    @DisplayName("a release local repository wins over any other local one, which wins over caches and virtuals")
    fun releaseLocalFirst() {
        val copies =
            listOf(
                stored("a.jar", repo = "maven-central-cache"),
                stored("a.jar", repo = "rnd-maven-dev-local"),
                stored("a.jar", repo = "rnd-maven-release-local"),
            )

        assertEquals("rnd-maven-release-local", RepositoryPreference.preferred(copies)?.repo)
    }

    @Test
    @DisplayName("within one rank, the repository name decides")
    fun byName() {
        val copies = listOf(stored("a.jar", repo = "zeta-local"), stored("a.jar", repo = "alpha-local"))

        assertEquals("alpha-local", RepositoryPreference.preferred(copies)?.repo)
    }

    @Test
    @DisplayName("no copy, no preference")
    fun none() {
        assertEquals(null, RepositoryPreference.preferred(emptyList()))
    }
}
