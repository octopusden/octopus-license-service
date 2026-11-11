package org.octopusden.octopus.license.inventory.impl.listing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.BUILD_VERSION
import org.octopusden.octopus.license.inventory.impl.COMPONENT
import org.octopusden.octopus.license.inventory.impl.FakeArtifactStore
import org.octopusden.octopus.license.inventory.impl.FakeBuildRecords
import org.octopusden.octopus.license.inventory.impl.GROUP
import org.octopusden.octopus.license.inventory.impl.VERSION
import org.octopusden.octopus.license.inventory.impl.build
import org.octopusden.octopus.license.inventory.impl.builtFile
import org.octopusden.octopus.license.inventory.impl.module
import org.octopusden.octopus.license.inventory.impl.shared.BuildLocator
import org.octopusden.octopus.license.inventory.impl.stored
import org.octopusden.octopus.license.inventory.impl.target
import org.octopusden.octopus.license.inventory.model.BuildLookup
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode.ARTIFACT_NOT_FOUND
import org.octopusden.octopus.license.inventory.model.FindingCode.BUILD_NAME_DIFFERS
import org.octopusden.octopus.license.inventory.model.FindingCode.NO_DISTRIBUTION
import org.octopusden.octopus.license.inventory.model.FindingCode.UNSUPPORTED_DISTRIBUTION_ENTRY
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.SelectionReason.DECLARED
import org.octopusden.octopus.license.inventory.model.SelectionReason.DOCKER_BUILD
import org.octopusden.octopus.license.inventory.model.SelectionReason.PATTERN
import org.octopusden.octopus.license.target.model.ArtifactPattern
import org.octopusden.octopus.license.target.model.DeclaredArtifact
import org.octopusden.octopus.license.target.model.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.model.DeclaredArtifact.MavenArtifact

class ReleasedFileListerTest {
    private val records = FakeBuildRecords()
    private val store = FakeArtifactStore()
    private val lister = ReleasedFileLister(records, store, BuildLocator(records))
    private val pattern = ArtifactPattern(listOf("org\\.example\\.portal"), listOf("sample-.*"))
    private val zipName = "$COMPONENT-$BUILD_VERSION.zip"
    private val conventionBuild = build(modules = arrayOf(module(files = listOf(builtFile(zipName)))))

    private fun dockerBuild(vararg images: String) =
        build(
            name = "${COMPONENT}_ee_docker",
            modules =
                arrayOf(
                    module(
                        id = "portal:$BUILD_VERSION",
                        type = "docker",
                        files = images.map { builtFile("manifest.json", sha1 = "sha1-$it", path = "$it/$BUILD_VERSION/manifest.json") },
                    ),
                ),
        )

    private fun manifest(image: String) =
        stored("manifest.json", path = "$image/$BUILD_VERSION", repo = "rnd-docker-release-local", sha1 = "sha1-$image")

    @Test
    @DisplayName("convention builds are looked up at each artifact version, first found wins")
    fun buildAtBuildVersion() {
        records.add(conventionBuild)

        val listing = lister.list(target())

        assertEquals(listOf(conventionBuild), listing.builds)
        assertEquals(
            listOf(
                "${COMPONENT}_ee/$VERSION",
                "${COMPONENT}_ee/$BUILD_VERSION",
                "${COMPONENT}_ee_docker/$VERSION",
                "${COMPONENT}_ee_docker/$BUILD_VERSION",
            ),
            records.asked,
        )
    }

    @Test
    @DisplayName("an implicit component's builds are {component}_ie and {component}_ie_docker")
    fun implicitBuilds() {
        lister.list(target(explicit = false, artifactVersions = listOf(VERSION)))

        assertEquals(listOf("${COMPONENT}_ie/$VERSION", "${COMPONENT}_ie_docker/$VERSION"), records.asked)
    }

    @Test
    @DisplayName("the worked example: files of the convention build listed in build-info order, with selection and status")
    fun workedExample() {
        records.add(
            build(
                modules =
                    arrayOf(
                        module(
                            files =
                                listOf(
                                    builtFile(zipName),
                                    builtFile("$COMPONENT-$BUILD_VERSION.pom"),
                                    builtFile("$COMPONENT-config-$BUILD_VERSION.yaml"),
                                    builtFile("helper-tool-$BUILD_VERSION.jar"),
                                ),
                        ),
                    ),
            ),
        )
        store.add(stored(zipName))
        store.add(stored("$COMPONENT-config-$BUILD_VERSION.yaml"))

        val files = lister.list(target(listOf(MavenArtifact(GROUP, COMPONENT, "zip")), listOf(pattern))).files

        assertEquals(
            listOf(
                Triple("$GROUP:$COMPONENT:zip", setOf(DECLARED, PATTERN), null),
                Triple("$GROUP:$COMPONENT:pom", setOf(PATTERN), FileStatus.METADATA),
                Triple("$GROUP:$COMPONENT-config:yaml", setOf(PATTERN), null),
                Triple("$GROUP:helper-tool:jar", emptySet(), FileStatus.NOT_SELECTED),
            ),
            files.map { Triple(it.entry, it.selectedBy, it.skip) },
        )
        val zip = files.first()
        assertEquals(stored(zipName), zip.stored)
        assertEquals(BuildLookup.CONVENTION, zip.buildFoundBy)
        assertEquals(PackageId.parse("pkg:maven/$GROUP/$COMPONENT@$BUILD_VERSION"), zip.ownId)
        assertEquals(null, files.last().stored)
    }

    @Test
    @DisplayName("a selected file missing from Artifactory is NOT_FOUND, naming the build that listed it")
    fun selectedMissing() {
        records.add(conventionBuild)

        val file = lister.list(target(patterns = listOf(pattern))).files.single()

        assertEquals(FileStatus.NOT_FOUND, file.skip)
        assertEquals(
            listOf(Finding(ARTIFACT_NOT_FOUND, "$GROUP:$COMPONENT:zip", "listed in ${COMPONENT}_ee/$BUILD_VERSION but not in Artifactory")),
            file.findings,
        )
    }

    @Test
    @DisplayName("an image of the docker build is listed and selected even when the docker field is empty")
    fun imageNotDeclared() {
        records.add(dockerBuild("sample/portal"))
        store.add(manifest("sample/portal"))

        val file = lister.list(target()).files.single()

        assertEquals("sample/portal:$BUILD_VERSION", file.entry)
        assertEquals(FileKind.DOCKER, file.kind)
        assertEquals(setOf(DOCKER_BUILD), file.selectedBy)
        assertEquals(false, file.declared)
        assertEquals(manifest("sample/portal"), file.stored)
    }

    @Test
    @DisplayName("an image missing from Artifactory is NOT_FOUND")
    fun imageMissing() {
        records.add(dockerBuild("sample/portal"))

        val file = lister.list(target()).files.single()

        assertEquals(FileStatus.NOT_FOUND, file.skip)
        assertEquals(
            listOf(Finding(ARTIFACT_NOT_FOUND, "sample/portal:$BUILD_VERSION", "image in build-info but not in Artifactory")),
            file.findings,
        )
    }

    @Test
    @DisplayName("an image declared without a tag is declared by the build's image, and not looked up again as a leftover")
    fun declaredImageWithoutTag() {
        records.add(dockerBuild("sample/portal"))
        store.add(manifest("sample/portal"))

        val files = lister.list(target(listOf(DockerImage("sample/portal")))).files

        assertEquals(listOf("sample/portal:$BUILD_VERSION" to true), files.map { it.entry to it.declared })
    }

    @Test
    @DisplayName("a declared file built by another build is found by its properties, with BUILD_NAME_DIFFERS")
    fun leftoverByProperties() {
        val tar = stored(
            "native-lib-$VERSION.tar.gz",
            path = "org/example/lib/native-lib/$VERSION",
            properties = mapOf(
                "build.name" to "shared-native-build",
                "build.number" to "7.1.3",
            ),
        )
        store.add(tar)
        records.add(build(name = "shared-native-build", number = "7.1.3"))

        val file = lister.list(target(listOf(MavenArtifact("org.example.lib", "native-lib", "tar.gz")))).files.single()

        assertEquals("org.example.lib:native-lib:tar.gz", file.entry)
        assertEquals(setOf(DECLARED), file.selectedBy)
        assertEquals(tar, file.stored)
        assertEquals("shared-native-build", file.build?.ref?.name)
        assertEquals(BuildLookup.PROPERTIES, file.buildFoundBy)
        assertEquals(PackageId.parse("pkg:maven/org.example.lib/native-lib@$VERSION"), file.ownId)
        assertEquals(
            listOf(Finding(BUILD_NAME_DIFFERS, "org.example.lib:native-lib:tar.gz", "shared-native-build (convention ${COMPONENT}_ee)")),
            file.findings,
        )
    }

    @Test
    @DisplayName("a declared file without build properties gets the build that published its sha1")
    fun leftoverByChecksum() {
        val tar = stored("native-lib-$BUILD_VERSION.tar.gz", path = "org/example/lib/native-lib/$BUILD_VERSION")
        store.add(tar)
        records.add(
            build(name = "other_build", number = "12", modules = arrayOf(module(files = listOf(builtFile("x.tar.gz", sha1 = tar.sha1))))),
        )

        val file = lister.list(target(listOf(MavenArtifact("org.example.lib", "native-lib", "tar.gz")))).files.single()

        assertEquals("other_build/12", file.build?.ref.toString())
        assertEquals(BuildLookup.CHECKSUM, file.buildFoundBy)
    }

    @Test
    @DisplayName("a declared file at no artifact version is NOT_FOUND, naming the versions tried")
    fun leftoverMissing() {
        val file = lister.list(target(listOf(MavenArtifact(GROUP, COMPONENT, "zip", "dist")))).files.single()

        assertEquals("$GROUP:$COMPONENT:zip:dist", file.entry)
        assertEquals(FileStatus.NOT_FOUND, file.skip)
        assertEquals(
            listOf(Finding(ARTIFACT_NOT_FOUND, "$GROUP:$COMPONENT:zip:dist", "tried versions $VERSION, $BUILD_VERSION")),
            file.findings,
        )
        assertEquals(
            listOf(
                "path:org/example/portal/$COMPONENT/$VERSION/$COMPONENT-$VERSION-dist.zip",
                "path:org/example/portal/$COMPONENT/$BUILD_VERSION/$COMPONENT-$BUILD_VERSION-dist.zip",
            ),
            store.asked,
        )
    }

    @Test
    @DisplayName("an unsupported entry is listed UNSUPPORTED and nothing is looked up; a file share has no detail")
    fun unsupported() {
        val files =
            lister
                .list(
                    target(
                        listOf(
                            DeclaredArtifact.Unsupported("file:///share/sample/bundle.zip", "a file share or URL, not a Maven artifact"),
                            DeclaredArtifact.Unsupported("org.example:only-two", "expected group:artifact:extension[:classifier]"),
                        ),
                    ),
                ).files

        assertEquals(listOf(FileKind.FILE_SHARE, FileKind.MAVEN), files.map { it.kind })
        assertEquals(listOf(FileStatus.UNSUPPORTED, FileStatus.UNSUPPORTED), files.map { it.skip })
        assertEquals(
            listOf(
                Finding(UNSUPPORTED_DISTRIBUTION_ENTRY, "file:///share/sample/bundle.zip", ""),
                Finding(UNSUPPORTED_DISTRIBUTION_ENTRY, "org.example:only-two", "unsupported GAV format 'org.example:only-two'"),
            ),
            files.flatMap { it.findings },
        )
        assertEquals(emptyList<String>(), store.asked)
    }

    @Test
    @DisplayName("a leftover image without a tag is listed at the artifact version it was found at")
    fun leftoverImageWithoutTag() {
        store.add(manifest("sample/batch"))

        val file = lister.list(target(listOf(DockerImage("sample/batch")))).files.single()

        assertEquals("sample/batch:$BUILD_VERSION", file.entry)
        assertEquals(FileKind.DOCKER, file.kind)
    }

    @Test
    @DisplayName("listing order: the convention build, its docker build, then leftovers, GAV entries before images")
    fun order() {
        records.add(conventionBuild)
        records.add(dockerBuild("sample/portal"))
        store.add(stored(zipName))
        store.add(manifest("sample/portal"))

        val files =
            lister.list(target(listOf(MavenArtifact(GROUP, "other", "zip"), DockerImage("sample/batch")), listOf(pattern))).files

        assertEquals(
            listOf("$GROUP:$COMPONENT:zip", "sample/portal:$BUILD_VERSION", "$GROUP:other:zip", "sample/batch"),
            files.map { it.entry },
        )
    }

    @Test
    @DisplayName("no convention build and nothing declared: NO_DISTRIBUTION naming the builds looked for")
    fun noDistributionNoBuild() {
        assertEquals(
            listOf(
                Finding(
                    NO_DISTRIBUTION,
                    COMPONENT,
                    "no build ${COMPONENT}_ee(_docker)/$VERSION and no artifact in the registry GAV or docker field",
                ),
            ),
            lister.list(target()).findings,
        )
    }

    @Test
    @DisplayName("builds that publish nothing selected: NO_DISTRIBUTION; a NOT_FOUND file counts as something to read")
    fun noDistributionWithBuild() {
        records.add(conventionBuild)

        assertEquals(
            listOf(
                Finding(NO_DISTRIBUTION, COMPONENT, "the builds publish nothing that matches the registry GAV or maven-artifacts pattern"),
            ),
            lister.list(target()).findings,
        )
        assertEquals(emptyList<Finding>(), lister.list(target(patterns = listOf(pattern))).findings)
    }
}
