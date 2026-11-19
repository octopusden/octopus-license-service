package org.octopusden.octopus.license.files

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Writing the license files is pure (D1): the server downloads the texts and pushes them in. Read
 * from the sources' imports, so a crossing fails here rather than in review.
 */
class PackageDependencyTest {
    private val root = File("src/main/kotlin/org/octopusden/octopus/license/files")
    private val base = "org.octopusden.octopus.license.files"

    private fun forbiddenImports(
        folder: String,
        forbidden: List<String>,
    ) = File(root, folder)
        .walkTopDown()
        .filter { it.extension == "kt" }
        .also { require(it.any()) { "no sources in $folder" } }
        .flatMap { file ->
            file
                .readLines()
                .filter { it.startsWith("import ") }
                .map { it.removePrefix("import ") }
                .filter { imported -> forbidden.any { imported.startsWith(it) } }
                .map { "${file.relativeTo(root)}: $it" }
        }.toList()

    @Test
    @DisplayName("the model knows nothing of how the files are written")
    fun model() {
        assertEquals(emptyList<String>(), forbiddenImports("model", listOf("$base.impl")))
    }

    @Test
    @DisplayName("nothing reaches a system, the wire, Spring, trust, or how the assessment is made")
    fun pure() {
        assertEquals(
            emptyList<String>(),
            forbiddenImports(
                ".",
                listOf(
                    "org.octopusden.octopus.infrastructure",
                    "feign",
                    "org.springframework",
                    "org.octopusden.octopus.license.core",
                    "org.octopusden.octopus.license.trust",
                    "org.octopusden.octopus.license.inventory.impl",
                    "org.octopusden.octopus.license.policy.impl",
                    "org.octopusden.octopus.license.evaluation.impl",
                    "java.net",
                    "java.io.File",
                    "java.nio.file",
                ),
            ),
        )
    }
}
