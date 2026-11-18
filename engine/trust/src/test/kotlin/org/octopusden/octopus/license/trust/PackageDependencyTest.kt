package org.octopusden.octopus.license.trust

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Trust is pure (D1, D11): it reads the assessment and the inventory's model, never how they are
 * made, and never the wire or Spring. Read from the sources' imports, so a crossing fails here
 * rather than in review.
 */
class PackageDependencyTest {
    private val root = File("src/main/kotlin/org/octopusden/octopus/license/trust")
    private val base = "org.octopusden.octopus.license.trust"

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
    @DisplayName("the model knows nothing of how it is graded")
    fun model() {
        assertEquals(emptyList<String>(), forbiddenImports(sources("model"), listOf("$base.impl")))
    }

    @Test
    @DisplayName("nothing reaches a system, the wire, Spring, or how the assessment is made")
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
                    "org.octopusden.octopus.license.evaluation.impl",
                    "java.net",
                    "java.io",
                ),
            ),
        )
    }
}
