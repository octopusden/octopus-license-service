package org.octopusden.octopus.license.evaluation.impl

import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.ReleasedFile

internal enum class Scope {
    COUNTED,

    /** Comes with the operating system of an image; skipped and counted. */
    OS_PACKAGE,

    /** Xray cannot tell base-image Python (yum, subscription-manager) from an application's. */
    BASE_IMAGE_PYTHON,

    /** Not a package; counted nowhere. */
    IMAGE_LAYER,
}

/** Whether a library counts, in the prototype's order (D3). Knows the released files to tell an image apart. */
internal class PackageScope(
    releasedFiles: List<ReleasedFile>,
) {
    private val images = releasedFiles.filter { it.kind == FileKind.DOCKER }.map { it.entry }.toSet()

    fun of(library: FoundLibrary) =
        when (library.id.type) {
            DOCKER -> Scope.IMAGE_LAYER
            in OS_PACKAGE_TYPES -> Scope.OS_PACKAGE
            PYPI if library.foundIn.all { it in images } -> Scope.BASE_IMAGE_PYTHON
            else -> Scope.COUNTED
        }

    private companion object {
        const val DOCKER = "docker"
        const val PYPI = "pypi"
        val OS_PACKAGE_TYPES = setOf("rpm", "deb", "alpine")
    }
}
