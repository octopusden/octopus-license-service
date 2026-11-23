package org.octopusden.octopus.license.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Evaluation is pure (D1, D12): it reads the inventory's and the policy's models, never how they are
 * read, and never the wire or Spring. Read from the sources' imports, so a crossing fails here
 * rather than in review.
 */
class PackageDependencyTest {
    private val root = File("src/main/kotlin/org/octopusden/octopus/license/evaluation")
    private val base = "org.octopusden.octopus.license.evaluation"

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

    @Test
    @DisplayName("the model knows nothing of how it is evaluated")
    fun model() {
        assertEquals(emptyList<String>(), forbiddenImports(sources("model"), listOf("$base.impl")))
    }

    @Test
    @DisplayName("nothing reaches a system, the wire, Spring, or how the inventory and the policy are read")
    fun pure() {
        assertEquals(
            emptyList<String>(),
            forbiddenImports(
                sources("."),
                listOf(
                    "org.octopusden.octopus.infrastructure",
                    "feign",
                    "org.springframework",
                    "org.octopusden.octopus.license.core",
                    "org.octopusden.octopus.license.inventory.impl",
                    "org.octopusden.octopus.license.policy.impl",
                    "java.net",
                    "java.io",
                ),
            ),
        )
    }
}
