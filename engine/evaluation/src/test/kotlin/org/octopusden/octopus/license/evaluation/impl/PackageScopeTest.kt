package org.octopusden.octopus.license.evaluation.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.license.evaluation.IMAGE
import org.octopusden.octopus.license.evaluation.ZIP
import org.octopusden.octopus.license.evaluation.image
import org.octopusden.octopus.license.evaluation.library
import org.octopusden.octopus.license.evaluation.zip

class PackageScopeTest {
    private val scope = PackageScope(listOf(zip, image))

    @Test
    @DisplayName("an image layer is dropped, wherever it was found (D3.1)")
    fun imageLayer() {
        assertEquals(Scope.IMAGE_LAYER, scope.of(library("pkg:docker/sample/base@sha256__ab12", foundIn = setOf(IMAGE))))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["pkg:rpm/openssl-libs@3.0.7", "pkg:deb/libc6@2.36", "pkg:alpine/musl@1.2.4"])
    @DisplayName("an rpm, deb or alpine package is an OS package, in an image or not (D3.2)")
    fun osPackage(id: String) {
        assertEquals(Scope.OS_PACKAGE, scope.of(library(id, foundIn = setOf(IMAGE))))
        assertEquals(Scope.OS_PACKAGE, scope.of(library(id, foundIn = setOf(ZIP))))
    }

    @Test
    @DisplayName("a Python package found only in images is base-image Python (D3.3)")
    fun baseImagePython() {
        assertEquals(Scope.BASE_IMAGE_PYTHON, scope.of(library("pkg:pypi/requests@2.31.0", foundIn = setOf(IMAGE))))
    }

    @Test
    @DisplayName("a Python package found in an image and in a zip, or only in a zip, is counted (D3.3)")
    fun countedPython() {
        assertEquals(Scope.COUNTED, scope.of(library("pkg:pypi/attrs@23.2.0", foundIn = setOf(IMAGE, ZIP))))
        assertEquals(Scope.COUNTED, scope.of(library("pkg:pypi/attrs@23.2.0", foundIn = setOf(ZIP))))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "pkg:maven/org.springframework/spring-core@6.1.4",
            "pkg:npm/left-pad@1.3.0",
            "pkg:golang/golang.org/x/net@0.20.0",
            "pkg:pub/http@1.2.0",
            "generic://sha256:cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd/vendor-blob.so",
        ],
    )
    @DisplayName("any other package is counted, in an image or not (D3.4)")
    fun counted(id: String) {
        assertEquals(Scope.COUNTED, scope.of(library(id, foundIn = setOf(IMAGE))))
    }
}
