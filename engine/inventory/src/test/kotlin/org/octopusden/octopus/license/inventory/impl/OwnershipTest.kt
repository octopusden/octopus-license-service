package org.octopusden.octopus.license.inventory.impl

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OwnershipTest {
    private val ownership = Ownership(OWNERSHIP)

    @Test
    @DisplayName("a group or dependency id starting with an internal group is ours")
    fun internalGroup() {
        assertTrue(ownership.isInternalGroup("org.example.portal"))
        assertTrue(ownership.isInternalGroup("org.example.portal:portal-plugins:2.4.0"))
        assertFalse(ownership.isInternalGroup("com.google.guava"))
    }

    @Test
    @DisplayName("a file named group.artifact-version.jar after an internal group is ours, as Gradle distributions name bundled jars")
    fun internalFileName() {
        assertTrue(ownership.isInternalFileName("org.example.parser-1.2.jar"))
        assertFalse(ownership.isInternalFileName("org.examples-1.2.jar"))
        assertFalse(ownership.isInternalFileName("netty-common-4.1.107.Final.jar"))
    }

    @Test
    @DisplayName("a repository is ours when it starts with an own prefix and ends with local")
    fun ownRepository() {
        assertTrue(ownership.isOwnRepository("rnd-maven-release-local"))
        assertFalse(ownership.isOwnRepository("rnd-maven-virtual"))
        assertFalse(ownership.isOwnRepository("maven-central-local"))
    }
}
