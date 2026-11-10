package org.octopusden.octopus.license.inventory.impl.listing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.BUILD_VERSION
import org.octopusden.octopus.license.inventory.impl.COMPONENT
import org.octopusden.octopus.license.inventory.impl.build
import org.octopusden.octopus.license.inventory.impl.builtFile
import org.octopusden.octopus.license.inventory.impl.module

class CandidateExtractorTest {
    @Test
    @DisplayName("each published file of a Maven or Gradle module is a candidate entry, in build-info order")
    fun mavenEntries() {
        val record =
            build(
                modules =
                    arrayOf(
                        module(
                            files =
                                listOf(
                                    builtFile("sample-portal-$BUILD_VERSION.zip"),
                                    builtFile("sample-portal-config-$BUILD_VERSION.yaml"),
                                    builtFile("sample-portal-$BUILD_VERSION-sources.jar"),
                                ),
                        ),
                    ),
            )

        assertEquals(
            listOf(
                "org.example.portal:sample-portal:zip",
                "org.example.portal:sample-portal-config:yaml",
                "org.example.portal:sample-portal:jar:sources",
            ),
            CandidateExtractor.extract(record).map { it.entry },
        )
    }

    @Test
    @DisplayName("a Maven candidate keeps the module's version, which locates the file later")
    fun mavenVersion() {
        val candidate =
            CandidateExtractor
                .extract(build(modules = arrayOf(module(files = listOf(builtFile("sample-portal-$BUILD_VERSION.zip"))))))
                .single() as MavenCandidate

        assertEquals(MavenCandidate::class, candidate::class)
        assertEquals(BUILD_VERSION, candidate.version)
    }

    @Test
    @DisplayName("a docker module gives one image per manifest.json, named by its folder; layers and list.manifest.json are not listed")
    fun dockerImages() {
        val record =
            build(
                name = "${COMPONENT}_ee_docker",
                modules =
                    arrayOf(
                        module(
                            id = "portal:$BUILD_VERSION",
                            type = "docker",
                            files =
                                listOf(
                                    builtFile("manifest.json", path = "sample/portal/$BUILD_VERSION/manifest.json"),
                                    builtFile("sha256__435dcf", path = "sample/portal/$BUILD_VERSION/sha256__435dcf"),
                                    builtFile("list.manifest.json", path = "sample/portal/list.manifest.json"),
                                ),
                        ),
                    ),
            )

        val candidates = CandidateExtractor.extract(record)

        assertEquals(listOf("sample/portal:$BUILD_VERSION"), candidates.map { it.entry })
        assertEquals(
            ImageCandidate(
                "sample/portal",
                BUILD_VERSION,
                record,
                builtFile("manifest.json", path = "sample/portal/$BUILD_VERSION/manifest.json"),
            ),
            candidates.single(),
        )
    }

    @Test
    @DisplayName("a manifest.json in a module of another type is still an image")
    fun manifestOutsideDockerModule() {
        val record =
            build(
                modules = arrayOf(
                    module(type = "generic", files = listOf(builtFile("manifest.json", path = "sample/batch/1.0/manifest.json"))),
                ),
            )

        assertEquals(listOf("sample/batch:1.0"), CandidateExtractor.extract(record).map { it.entry })
    }

    @Test
    @DisplayName("a docker module's file without a recorded path names no image and is not listed")
    fun dockerWithoutPath() {
        val record = build(modules = arrayOf(module(type = "docker", files = listOf(builtFile("manifest.json", path = null)))))

        assertEquals(emptyList<Candidate>(), CandidateExtractor.extract(record))
    }
}
