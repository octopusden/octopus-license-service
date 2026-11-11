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
        val shared = listOf("$base.impl.Ownership", "$base.impl.BuildLocator", "$base.impl.LibraryTable")

        assertEquals(
            emptyList<String>(),
            forbiddenImports(sources("impl/port"), steps("listing", "reading", "completing", "adapter") + shared + clients),
        )
    }

    @Test
    @DisplayName("what the steps share knows no step")
    fun shared() {
        val shared = sequenceOf("Ownership.kt", "BuildLocator.kt", "LibraryTable.kt").map { File(root, "impl/$it") }
        require(shared.all { it.isFile }) { "a shared class moved" }

        assertEquals(emptyList<String>(), forbiddenImports(shared, steps("listing", "reading", "completing", "adapter") + clients))
    }

    @Test
    @DisplayName("listing knows no later step; reading takes listing's result and nothing of completing; completing knows neither")
    fun stepsPointOneWay() {
        assertEquals(
            emptyList<String>(),
            forbiddenImports(sources("impl/listing"), steps("reading", "completing", "adapter") + clients) +
                forbiddenImports(sources("impl/reading"), steps("completing", "adapter") + clients) +
                forbiddenImports(sources("impl/completing"), steps("listing", "reading", "adapter") + clients),
        )
    }

    @Test
    @DisplayName("the adapters know the ports and the model, never a step")
    fun adapters() {
        assertEquals(emptyList<String>(), forbiddenImports(sources("impl/adapter"), steps("listing", "reading", "completing")))
    }
}
