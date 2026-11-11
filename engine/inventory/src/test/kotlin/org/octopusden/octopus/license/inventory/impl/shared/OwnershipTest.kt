package org.octopusden.octopus.license.inventory.impl.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.OWNERSHIP
import org.octopusden.octopus.license.inventory.model.PackageId

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

    private val sha256 = "cd".repeat(32)
    private val asked = mutableListOf<String>()

    private fun isOurs(
        id: String,
        copyInOwnRepository: Boolean = false,
    ) = ownership.isOurs(PackageId.parse(id)) {
        asked += it
        copyInOwnRepository
    }

    @Test
    @DisplayName("a Maven library is ours by its group, without asking where it is stored (D2)")
    fun oursMaven() {
        assertTrue(isOurs("pkg:maven/org.example.portal/portal-plugins@2.4.0-118"))
        assertTrue(isOurs("pkg:maven/org.example/parser@1.2"))
        assertFalse(isOurs("pkg:maven/org.springframework/spring-core@6.1.4", copyInOwnRepository = true))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    @DisplayName("a checksum-only file named after an internal group is ours, without asking where it is stored (D2)")
    fun oursChecksumOnlyByName() {
        assertTrue(isOurs("generic://sha256:$sha256/org.example.portal.portal-core-2.4.0.jar"))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    @DisplayName("any other checksum-only file is ours exactly when a copy is stored in one of our repositories (D2)")
    fun oursChecksumOnlyByCopy() {
        assertTrue(isOurs("generic://sha256:$sha256/vendor-blob.so", copyInOwnRepository = true))
        assertFalse(isOurs("generic://sha256:$sha256/vendor-blob.so", copyInOwnRepository = false))
        assertEquals(listOf(sha256, sha256), asked)
    }

    @Test
    @DisplayName("an npm, Python or unrecognized package is never ours, and nothing is asked (D2)")
    fun neverOurs() {
        assertFalse(isOurs("pkg:npm/left-pad@1.3.0", copyInOwnRepository = true))
        assertFalse(isOurs("pkg:pypi/org.example@1.0", copyInOwnRepository = true))
        assertFalse(isOurs("generic://org.example.thing", copyInOwnRepository = true))
        assertEquals(emptyList<String>(), asked)
    }
}
