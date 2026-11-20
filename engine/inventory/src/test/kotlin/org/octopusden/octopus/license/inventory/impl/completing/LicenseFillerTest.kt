package org.octopusden.octopus.license.inventory.impl.completing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.FakeLicenseDatabase
import org.octopusden.octopus.license.inventory.impl.shared.LibraryTable
import org.octopusden.octopus.license.inventory.model.Evidence.BUILD_RECORD
import org.octopusden.octopus.license.inventory.model.PackageId

class LicenseFillerTest {
    private val table = LibraryTable()

    private fun found(
        id: String,
        vararg licenses: String,
    ) = table.add(PackageId.parse(id), licenses.toSet(), BUILD_RECORD, "zip")

    @Test
    @DisplayName("Maven and npm packages without a license are asked about, in id order; others are not")
    fun asks() {
        found("pkg:npm/react@18.2.0")
        found("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0")
        found("pkg:maven/org.springframework/spring-core@6.1.4", "Apache-2.0")
        found("pkg:pypi/requests@2.31.0")
        val database = FakeLicenseDatabase(mapOf("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0" to setOf("Apache-2.0")))

        LicenseFiller(database).fill(table)

        assertEquals(listOf(listOf("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0", "pkg:npm/react@18.2.0")), database.asked)
        assertEquals(
            mapOf(
                "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0" to setOf("Apache-2.0"),
                "pkg:maven/org.springframework/spring-core@6.1.4" to setOf("Apache-2.0"),
                "pkg:npm/react@18.2.0" to emptySet(),
                "pkg:pypi/requests@2.31.0" to emptySet(),
            ),
            table.libraries().associate { it.id.toString() to it.licenses },
        )
    }

    @Test
    @DisplayName("nothing to ask: the database is not called")
    fun nothingToAsk() {
        found("pkg:maven/a/b@1", "MIT")
        val database = FakeLicenseDatabase()

        LicenseFiller(database).fill(table)

        assertEquals(emptyList<List<String>>(), database.asked)
    }
}
