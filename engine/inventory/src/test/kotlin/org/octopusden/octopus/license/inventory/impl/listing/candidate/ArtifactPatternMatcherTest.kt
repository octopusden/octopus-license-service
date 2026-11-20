package org.octopusden.octopus.license.inventory.impl.listing.candidate

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.target.model.ArtifactPattern

class ArtifactPatternMatcherTest {
    @Test
    @DisplayName("group and artifact must each match fully, not as a substring")
    fun fullMatch() {
        val matcher = ArtifactPatternMatcher(listOf(ArtifactPattern(listOf("org\\.example\\.portal"), listOf("sample-.*"))))

        assertTrue(matcher.matches("org.example.portal", "sample-portal"))
        assertFalse(matcher.matches("org.example.portal.extra", "sample-portal"))
        assertFalse(matcher.matches("org.example.portal", "my-sample-portal"))
    }

    @Test
    @DisplayName("group and artifact must match patterns of the same entry")
    fun sameEntry() {
        val matcher =
            ArtifactPatternMatcher(
                listOf(
                    ArtifactPattern(listOf("org\\.example\\.portal"), listOf("portal-.*")),
                    ArtifactPattern(listOf("org\\.example\\.other"), listOf("sample-.*")),
                ),
            )

        assertFalse(matcher.matches("org.example.portal", "sample-portal"))
    }

    @Test
    @DisplayName("any of an entry's comma-separated patterns may match")
    fun anyPattern() {
        val matcher = ArtifactPatternMatcher(listOf(ArtifactPattern(listOf("org\\.a", "org\\.example\\..*"), listOf("x", "sample-.*"))))

        assertTrue(matcher.matches("org.example.portal", "sample-portal"))
    }

    @Test
    @DisplayName("a Java character class with a literal hyphen, as the registry writes them, compiles")
    fun javaHyphen() {
        assertTrue(
            ArtifactPatternMatcher(
                listOf(ArtifactPattern(listOf("org\\.example\\.[\\w-\\.]+"), listOf(".*"))),
            ).matches("org.example.my-lib.x", "a"),
        )
    }

    @Test
    @DisplayName("a pattern that does not compile matches only the identical string")
    fun invalidPattern() {
        val matcher = ArtifactPatternMatcher(listOf(ArtifactPattern(listOf("org.example(portal"), listOf("sample-portal"))))

        assertTrue(matcher.matches("org.example(portal", "sample-portal"))
        assertFalse(matcher.matches("org.exampleXportal", "sample-portal"))
    }
}
