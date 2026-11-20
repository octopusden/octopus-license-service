package org.octopusden.octopus.license.inventory.parity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ParityAcceptedTest {
    private val signature = "file lib-1.0.jar.asc: kind: prototype RELEASED, service METADATA"

    @Test
    @DisplayName("an accepted difference is found by component and exact text, with its reason")
    fun accepted() {
        val accepted =
            ParityAccepted.parse(
                listOf(
                    "# checked on run 14",
                    "",
                    "sample-portal: $signature  # signature by last extension",
                ),
            )

        assertEquals("signature by last extension", accepted.reasonFor("sample-portal", signature))
        assertNull(accepted.reasonFor("other-component", signature))
        assertNull(accepted.reasonFor("sample-portal", "$signature, again"))
    }

    @Test
    @DisplayName("a difference containing ' # ' keeps it; the reason is after the last one")
    fun hashInDifference() {
        val accepted = ParityAccepted.parse(listOf("c: package a # b: only in the service # SBOM purls decoded"))

        assertEquals("SBOM purls decoded", accepted.reasonFor("c", "package a # b: only in the service"))
    }

    @Test
    @DisplayName("a line without a reason or a component fails, naming the line")
    fun malformed() {
        val noReason = assertThrows<IllegalArgumentException> { ParityAccepted.parse(listOf("#", "c: $signature")) }
        val noComponent = assertThrows<IllegalArgumentException> { ParityAccepted.parse(listOf("nothing here # reason")) }

        assertEquals(true, noReason.message!!.startsWith("line 2:"))
        assertEquals(true, noComponent.message!!.startsWith("line 1:"))
    }
}
