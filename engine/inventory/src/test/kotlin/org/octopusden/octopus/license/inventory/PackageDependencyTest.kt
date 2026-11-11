package org.octopusden.octopus.license.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The module's packages depend one way: model ← port ← shared ← the three steps ← the scanner, with
 * the adapters beside them. Read from the sources' imports, so a new import that crosses a boundary
 * fails here rather than in review.
 */
class PackageDependencyTest {
    private val root = File("src/main/kotlin/org/octopusden/octopus/license/inventory")
    private val base = "org.octopusden.octopus.license.inventory"
    private val clients = listOf("org.octopusden.octopus.infrastructure", "feign")

    private fun forbiddenImports(
        files: Sequence<File>,
        forbidden: List<String>,
    ) = files
        .flatMap { file ->
            file
                .readLines()
                .filter { it.startsWith("import ") }
                .map { it.removePrefix("import ") }
                .filter { imported -> forbidden.any { imported.startsWith(it) } }
                .map { "${file.relativeTo(root)}: $it" }
        }.toList()

    private fun sources(folder: String) =
        File(root, folder).walkTopDown().filter { it.extension == "kt" }.also { require(it.any()) { "no sources in $folder" } }

    private fun steps(vararg names: String) = names.map { "$base.impl.$it" }

    @Test
    @DisplayName("the model knows nothing of how it is built")
    fun model() {
        assertEquals(emptyList<String>(), forbiddenImports(sources("model"), listOf("$base.impl") + clients))
    }

    @Test
    @DisplayName("the ports know the model only")
    fun ports() {
        assertEquals(
            emptyList<String>(),
            forbiddenImports(sources("impl/port"), steps("shared", "listing", "reading", "completing", "adapter") + clients),
        )
    }

    @Test
    @DisplayName("what the steps share knows no step")
    fun shared() {
        assertEquals(
            emptyList<String>(),
            forbiddenImports(sources("impl/shared"), steps("listing", "reading", "completing", "adapter") + clients),
        )
    }

    @Test
    @DisplayName("listing knows no later step; reading takes listing's data and nothing of completing; completing knows neither")
    fun stepsPointOneWay() {
        val listingButItsData =
            steps("listing.candidate", "listing.ReleasedFileLister", "listing.DeclaredLeftoverLocator", "listing.ConventionBuilds")

        assertEquals(
            emptyList<String>(),
            forbiddenImports(sources("impl/listing"), steps("reading", "completing", "adapter") + clients) +
                forbiddenImports(sources("impl/reading"), steps("completing", "adapter") + listingButItsData + clients) +
                forbiddenImports(sources("impl/completing"), steps("listing", "reading", "adapter") + clients),
        )
    }

    @Test
    @DisplayName("the adapters know the ports and the model, never a step")
    fun adapters() {
        assertEquals(emptyList<String>(), forbiddenImports(sources("impl/adapter"), steps("listing", "reading", "completing")))
    }
}
