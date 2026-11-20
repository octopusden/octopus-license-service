package org.octopusden.octopus.license.inventory.impl.reading.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ShippingScopesTest {
    @ParameterizedTest(name = "{0} ships: {1}")
    @CsvSource(
        "compile, true",
        "runtime, true",
        "runtimeClasspath, true",
        "embed, true",
        "boricaProductionReleaseRuntimeClasspath, true",
        "fullReleaseEmbed, true",
        "mdes2Embed, true",
        "fullDebugEmbed, false",
        "debugRuntimeClasspath, false",
        "testReleaseRuntimeClasspath, false",
        "test, false",
        "provided, false",
        "modpackages, false",
        "compileClasspath, false",
        "releaseCompileClasspath, false",
    )
    @DisplayName("a scope ships when it is a runtime scope, or an Android release runtime or embed configuration")
    fun ships(
        scope: String,
        ships: Boolean,
    ) {
        assertEquals(ships, ShippingScopes.ships(scope))
    }

    @ParameterizedTest(name = "{0} is test or debug: {1}")
    @CsvSource("test, true", "testRuntimeClasspath, true", "fullDebugEmbed, true", "DEBUG, true", "modpackages, false", "provided, false")
    @DisplayName("a scope mentioning test or debug, in any case, never ships by proof")
    fun testOrDebug(
        scope: String,
        expected: Boolean,
    ) {
        assertEquals(expected, ShippingScopes.isTestOrDebug(scope))
    }
}
