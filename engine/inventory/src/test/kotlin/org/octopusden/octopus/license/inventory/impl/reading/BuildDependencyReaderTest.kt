package org.octopusden.octopus.license.inventory.impl.reading

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.BUILD_VERSION
import org.octopusden.octopus.license.inventory.impl.COMPONENT
import org.octopusden.octopus.license.inventory.impl.GROUP
import org.octopusden.octopus.license.inventory.impl.OWNERSHIP
import org.octopusden.octopus.license.inventory.impl.Ownership
import org.octopusden.octopus.license.inventory.impl.build
import org.octopusden.octopus.license.inventory.impl.builtFile
import org.octopusden.octopus.license.inventory.impl.dependency
import org.octopusden.octopus.license.inventory.impl.module
import org.octopusden.octopus.license.inventory.impl.port.data.BuildDependency
import org.octopusden.octopus.license.inventory.impl.stored
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode.LOCAL_FILE_DEPENDENCY
import org.octopusden.octopus.license.inventory.model.FindingCode.NO_PRODUCING_MODULE
import org.octopusden.octopus.license.inventory.model.PackageId

class BuildDependencyReaderTest {
    private val reader = BuildDependencyReader(Ownership(OWNERSHIP))
    private val zipName = "$COMPONENT-$BUILD_VERSION.zip"
    private val zip = stored(zipName)

    private fun read(
        vararg dependencies: BuildDependency,
        scanned: Set<PackageId> = emptySet(),
    ) = reader.read(
        build(modules = arrayOf(module(files = listOf(builtFile(zipName)), dependencies = dependencies.toList()))),
        zip,
        scanned,
    )

    private fun maven(gav: String) = PackageId.parse("pkg:maven/${gav.replaceFirst(':', '/').replaceFirst(':', '@')}")

    @Test
    @DisplayName("a runtime dependency of the producing module is a Maven package; a test one is not read")
    fun runtimeOnly() {
        val reading =
            read(
                dependency("com.fasterxml.jackson.core:jackson-databind:2.17.0", "runtime"),
                dependency("org.junit.jupiter:junit-jupiter:5.10.2", "test"),
            )

        assertEquals(listOf(maven("com.fasterxml.jackson.core:jackson-databind:2.17.0")), reading.packages)
        assertEquals(emptyList<NestedArchive>(), reading.nested)
    }

    @Test
    @DisplayName("the producing module is the one that published the file, by sha1 or by name")
    fun producingModule() {
        val record =
            build(
                modules =
                    arrayOf(
                        module(
                            id = "$GROUP:other:1",
                            files = listOf(builtFile("other.jar")),
                            dependencies = listOf(dependency("a:b:1", "runtime")),
                        ),
                        module(
                            files = listOf(builtFile("renamed.zip", sha1 = zip.sha1)),
                            dependencies = listOf(dependency("c:d:2", "runtime")),
                        ),
                    ),
            )

        assertEquals(listOf(maven("c:d:2")), reader.read(record, zip, emptySet()).packages)
    }

    @Test
    @DisplayName("no module published the file: NO_PRODUCING_MODULE, and no dependency is read")
    fun noProducingModule() {
        val record =
            build(modules = arrayOf(module(files = listOf(builtFile("other.jar")), dependencies = listOf(dependency("a:b:1", "runtime")))))

        val reading = reader.read(record, zip, emptySet())

        assertEquals(emptyList<PackageId>(), reading.packages)
        assertEquals(listOf(Finding(NO_PRODUCING_MODULE, zipName, "${COMPONENT}_ee/$BUILD_VERSION")), reading.findings)
    }

    @Test
    @DisplayName("prod dependencies of every npm module are packages for any file of the build, even without a producing module")
    fun npm() {
        val record =
            build(
                modules =
                    arrayOf(
                        module(
                            id = "portal-ui:1.0",
                            type = "npm",
                            dependencies =
                                listOf(
                                    dependency("react:18.2.0", "prod"),
                                    dependency("@angular/core:17.3.1", "prod"),
                                    dependency("jest:29.7.0", "dev"),
                                ),
                        ),
                    ),
            )

        assertEquals(
            listOf(PackageId.parse("pkg:npm/react@18.2.0"), PackageId.parse("pkg:npm/@angular/core@17.3.1")),
            reader.read(record, zip, emptySet()).packages,
        )
    }

    @Test
    @DisplayName("Gradle API, Local Groovy and Gradle TestKit ship with Gradle, not with us: skipped")
    fun gradleSupplied() {
        assertEquals(
            emptyList<PackageId>(),
            read(dependency("Gradle API", "runtimeClasspath"), dependency("Local Groovy", "runtimeClasspath")).packages,
        )
        assertEquals(emptyList<Finding>(), read(dependency("Gradle TestKit", "runtimeClasspath")).findings)
    }

    @Test
    @DisplayName("a shipped dependency without Maven coordinates is LOCAL_FILE_DEPENDENCY, to check by hand")
    fun localFile() {
        val reading =
            read(
                dependency("VTS-SDK-6.3.0.jar", "runtime"),
                dependency(":mpsdk-3.5.1-release:", "runtime"),
                dependency("sha256:ab12:file", "runtime"),
            )

        assertEquals(emptyList<PackageId>(), reading.packages)
        assertEquals(
            listOf("VTS-SDK-6.3.0.jar", ":mpsdk-3.5.1-release:", "sha256:ab12:file").map {
                Finding(LOCAL_FILE_DEPENDENCY, it, "shipped file without Maven coordinates; check its license by hand")
            },
            reading.findings,
        )
    }

    @Test
    @DisplayName("a third-party archive is a package, not followed")
    fun thirdPartyArchive() {
        val reading = read(dependency("org.webjars:swagger-ui:5.11.0", "runtime", type = "zip"))

        assertEquals(listOf(maven("org.webjars:swagger-ui:5.11.0")), reading.packages)
        assertEquals(emptyList<NestedArchive>(), reading.nested)
    }

    @Test
    @DisplayName("our own archive under a runtime scope is followed, not a package")
    fun ownArchiveRuntime() {
        val dependency = dependency("$GROUP:portal-plugins:$BUILD_VERSION", "runtime", type = "zip")

        val reading = read(dependency)

        assertEquals(emptyList<PackageId>(), reading.packages)
        assertEquals(listOf(NestedArchive(dependency.id, GROUP, "portal-plugins", BUILD_VERSION, "zip", dependency.sha1)), reading.nested)
    }

    @Test
    @DisplayName("our own archive under a custom configuration ships only when Xray's scan of the file lists it exactly")
    fun ownArchiveByProof() {
        val dependency = dependency("$GROUP:portal-plugins:$BUILD_VERSION", "modpackages", type = "zip")

        assertEquals(emptyList<NestedArchive>(), read(dependency).nested)
        assertEquals(1, read(dependency, scanned = setOf(maven("$GROUP:portal-plugins:$BUILD_VERSION"))).nested.size)
        assertEquals(emptyList<NestedArchive>(), read(dependency, scanned = setOf(maven("$GROUP:portal-plugins:2.4.0-117"))).nested)
    }

    @Test
    @DisplayName("our own archive under a test or debug scope never ships, even when Xray lists it")
    fun ownArchiveTestScope() {
        val dependency = dependency("$GROUP:portal-plugins:$BUILD_VERSION", "modpackages", "testRuntime", type = "zip")

        assertEquals(emptyList<NestedArchive>(), read(dependency, scanned = setOf(maven("$GROUP:portal-plugins:$BUILD_VERSION"))).nested)
    }
}
