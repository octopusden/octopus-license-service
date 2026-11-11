package org.octopusden.octopus.license.inventory.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.port.ScanPorts
import org.octopusden.octopus.license.inventory.model.BuildLookup
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.Evidence.BINARY_SCAN
import org.octopusden.octopus.license.inventory.model.Evidence.BUILD_RECORD
import org.octopusden.octopus.license.inventory.model.Evidence.CHECKSUM_MATCH
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode.NOT_IN_XRAY
import org.octopusden.octopus.license.inventory.model.FindingCode.NOT_SCANNABLE
import org.octopusden.octopus.license.inventory.model.FindingCode.NO_DISTRIBUTION
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.inventory.model.SelectionReason.DECLARED
import org.octopusden.octopus.license.inventory.model.SelectionReason.DOCKER_BUILD
import org.octopusden.octopus.license.inventory.model.SelectionReason.PATTERN
import org.octopusden.octopus.license.target.model.ArtifactPattern
import org.octopusden.octopus.license.target.model.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.model.DeclaredArtifact.MavenArtifact

/** The worked example of the design, end to end over fakes. */
class JfrogLicenseInventoryScannerTest {
    private val records = FakeBuildRecords()
    private val store = FakeArtifactStore()
    private val scanner = FakeBinaryScanner()
    private val database =
        FakeLicenseDatabase(
            mapOf(
                "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0" to setOf("Apache-2.0"),
                "pkg:maven/io.netty/netty-common@4.1.107.Final" to setOf("Apache-2.0"),
            ),
        )
    private val inventoryScanner = JfrogLicenseInventoryScanner({ ScanPorts(records, store, scanner, database) }, OWNERSHIP)

    private val zipName = "$COMPONENT-$BUILD_VERSION.zip"
    private val yamlName = "$COMPONENT-config-$BUILD_VERSION.yaml"
    private val zip = stored(zipName)
    private val yaml = stored(yamlName)
    private val plugins = stored("portal-plugins-$BUILD_VERSION.zip", path = "org/example/portal/portal-plugins/$BUILD_VERSION")
    private val image =
        stored("manifest.json", path = "sample/portal/$BUILD_VERSION", repo = "rnd-docker-release-local", sha1 = "sha1-image")
    private val nettySha256 = "ab".repeat(32)
    private val zipEntry = "$GROUP:$COMPONENT:zip"
    private val imageEntry = "sample/portal:$BUILD_VERSION"

    private val workedTarget =
        target(
            declared = listOf(MavenArtifact(GROUP, COMPONENT, "zip"), DockerImage("sample/portal")),
            patterns = listOf(ArtifactPattern(listOf("org\\.example\\.portal"), listOf("sample-.*"))),
        )

    private fun workedExample() {
        records.add(
            build(
                modules =
                    arrayOf(
                        module(
                            files =
                                listOf(
                                    builtFile(zipName),
                                    builtFile("$COMPONENT-$BUILD_VERSION.pom"),
                                    builtFile(yamlName),
                                    builtFile("helper-tool-$BUILD_VERSION.jar"),
                                ),
                            dependencies =
                                listOf(
                                    dependency("com.fasterxml.jackson.core:jackson-databind:2.17.0", "runtime"),
                                    dependency("org.junit.jupiter:junit-jupiter:5.10.2", "test"),
                                    dependency("$GROUP:portal-plugins:$BUILD_VERSION", "modpackages", type = "zip", sha1 = plugins.sha1),
                                ),
                        ),
                    ),
            ),
        )
        records.add(
            build(
                name = "${COMPONENT}_ee_docker",
                modules =
                    arrayOf(
                        module(
                            id = "portal:$BUILD_VERSION",
                            type = "docker",
                            files = listOf(
                                builtFile("manifest.json", sha1 = "sha1-image", path = "sample/portal/$BUILD_VERSION/manifest.json"),
                            ),
                        ),
                    ),
            ),
        )
        records.add(
            build(
                name = "portal-plugins_ie",
                modules =
                    arrayOf(
                        module(
                            id = "$GROUP:portal-plugins:$BUILD_VERSION",
                            files = listOf(builtFile(plugins.name)),
                            dependencies = listOf(dependency("org.apache.commons:commons-text:1.11.0", "runtime")),
                        ),
                    ),
            ),
        )
        listOf(zip, yaml, plugins, image).forEach { store.add(it) }
        store.add(
            stored(
                "netty-common-4.1.107.Final.jar",
                path = "io/netty/netty-common/4.1.107.Final",
                repo = "maven-central-cache",
                sha256 = nettySha256,
            ),
        )
        scanner.scanned(
            zip,
            "gav://org.springframework:spring-core:6.1.4" to setOf("Apache-2.0"),
            "generic://sha256:$nettySha256/netty-common-4.1.107.Final.jar" to setOf("Unknown"),
            "gav://$GROUP:portal-plugins:$BUILD_VERSION" to setOf("Unknown"),
        )
        scanner.scanned(plugins, "gav://org.apache.commons:commons-text:1.11.0" to setOf("Apache-2.0"))
        scanner.scanned(image, "gav://com.google.guava:guava:32.1.2-jre" to setOf("Apache-2.0"))
    }

    @Test
    @DisplayName("the worked example: every file listed in order with its status")
    fun files() {
        workedExample()

        val files = inventoryScanner.scan(workedTarget).releasedFiles

        assertEquals(
            listOf(
                ReleasedFile(
                    zipEntry,
                    FileKind.MAVEN,
                    setOf(DECLARED, PATTERN),
                    zip.fullPath,
                    BuildRef("${COMPONENT}_ee", BUILD_VERSION),
                    BuildLookup.CONVENTION,
                    "jfrog-cli-go",
                    FileStatus.READ,
                    setOf(BINARY_SCAN, BUILD_RECORD),
                ),
                ReleasedFile(
                    "$GROUP:$COMPONENT:pom",
                    FileKind.MAVEN,
                    setOf(PATTERN),
                    null,
                    BuildRef("${COMPONENT}_ee", BUILD_VERSION),
                    BuildLookup.CONVENTION,
                    null,
                    FileStatus.METADATA,
                ),
                ReleasedFile(
                    "$GROUP:$COMPONENT-config:yaml",
                    FileKind.MAVEN,
                    setOf(PATTERN),
                    yaml.fullPath,
                    BuildRef("${COMPONENT}_ee", BUILD_VERSION),
                    BuildLookup.CONVENTION,
                    "jfrog-cli-go",
                    FileStatus.PLAIN_FILE,
                ),
                ReleasedFile(
                    "$GROUP:helper-tool:jar",
                    FileKind.MAVEN,
                    emptySet(),
                    null,
                    BuildRef("${COMPONENT}_ee", BUILD_VERSION),
                    BuildLookup.CONVENTION,
                    null,
                    FileStatus.NOT_SELECTED,
                ),
                ReleasedFile(
                    imageEntry,
                    FileKind.DOCKER,
                    setOf(DOCKER_BUILD, DECLARED),
                    image.fullPath,
                    BuildRef("${COMPONENT}_ee_docker", BUILD_VERSION),
                    BuildLookup.CONVENTION,
                    "jfrog-cli-go",
                    FileStatus.READ,
                    setOf(BINARY_SCAN, BUILD_RECORD),
                ),
            ),
            files,
        )
    }

    @Test
    @DisplayName("the worked example: packages merged across files, the checksum-only jar named, missing licenses filled")
    fun packages() {
        workedExample()

        val packages = inventoryScanner.scan(workedTarget).libraries

        assertEquals(
            listOf(
                FoundLibrary(
                    pkg("com.fasterxml.jackson.core/jackson-databind@2.17.0"),
                    setOf("Apache-2.0"),
                    setOf(BUILD_RECORD),
                    setOf(zipEntry),
                    false,
                ),
                FoundLibrary(pkg("com.google.guava/guava@32.1.2-jre"), setOf("Apache-2.0"), setOf(BINARY_SCAN), setOf(imageEntry), false),
                FoundLibrary(
                    pkg("io.netty/netty-common@4.1.107.Final"),
                    setOf("Apache-2.0"),
                    setOf(BINARY_SCAN, CHECKSUM_MATCH),
                    setOf(zipEntry),
                    false,
                ),
                FoundLibrary(
                    pkg("org.apache.commons/commons-text@1.11.0"),
                    setOf("Apache-2.0"),
                    setOf(BINARY_SCAN, BUILD_RECORD),
                    setOf(zipEntry),
                    false,
                ),
                FoundLibrary(
                    pkg("org.example.portal/portal-plugins@$BUILD_VERSION"),
                    setOf("Unknown"),
                    setOf(BINARY_SCAN),
                    setOf(zipEntry),
                    true,
                ),
                FoundLibrary(pkg("org.springframework/spring-core@6.1.4"), setOf("Apache-2.0"), setOf(BINARY_SCAN), setOf(zipEntry), false),
            ),
            packages,
        )
        assertEquals(
            listOf(listOf("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0", "pkg:maven/io.netty/netty-common@4.1.107.Final")),
            database.asked,
        )
    }

    @Test
    @DisplayName("the worked example: findings once each, in file order")
    fun findings() {
        workedExample()

        assertEquals(
            listOf(Finding(NOT_SCANNABLE, "$GROUP:$COMPONENT-config:yaml", "plain file, Xray does not index this type")),
            inventoryScanner.scan(workedTarget).findings,
        )
    }

    @Test
    @DisplayName("a declared entry written twice is listed once, and an unindexed file is read from its build")
    fun findingsOnce() {
        records.add(
            build(modules = arrayOf(module(files = listOf(builtFile(zipName)), dependencies = listOf(dependency("a:b:1", "runtime"))))),
        )
        store.add(zip)

        val inventory = inventoryScanner.scan(
            target(listOf(MavenArtifact(GROUP, COMPONENT, "zip"), MavenArtifact(GROUP, COMPONENT, "zip"))),
        )

        assertEquals(listOf(Finding(NOT_IN_XRAY, zipEntry, "Xray has not indexed this file")), inventory.findings)
        assertEquals(setOf(BUILD_RECORD), inventory.releasedFiles.single().evidence)
    }

    @Test
    @DisplayName("nothing to read: NO_DISTRIBUTION, no packages, and the license database is not asked")
    fun noDistribution() {
        val inventory = inventoryScanner.scan(target())

        assertEquals(listOf(NO_DISTRIBUTION), inventory.findings.map { it.code })
        assertEquals(emptyList<FoundLibrary>(), inventory.libraries)
        assertEquals(emptyList<List<String>>(), database.asked)
    }

    @Test
    @DisplayName("within one scan each build, file lookup and Xray scan is asked once; a new scan asks again")
    fun memoized() {
        workedExample()

        inventoryScanner.scan(workedTarget)

        assertEquals(records.asked.distinct(), records.asked)
        assertEquals(store.asked.distinct(), store.asked)
        assertEquals(scanner.asked.distinct(), scanner.asked)

        val asked = scanner.asked.size
        inventoryScanner.scan(workedTarget)
        assertEquals(2 * asked, scanner.asked.size)
    }

    private fun pkg(nameAtVersion: String) =
        org.octopusden.octopus.license.inventory.model.PackageId
            .parse("pkg:maven/$nameAtVersion")
}
