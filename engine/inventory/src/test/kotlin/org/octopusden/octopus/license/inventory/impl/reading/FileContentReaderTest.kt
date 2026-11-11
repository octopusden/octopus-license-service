package org.octopusden.octopus.license.inventory.impl.reading

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.BUILD_VERSION
import org.octopusden.octopus.license.inventory.impl.BuildLocator
import org.octopusden.octopus.license.inventory.impl.COMPONENT
import org.octopusden.octopus.license.inventory.impl.FakeArtifactStore
import org.octopusden.octopus.license.inventory.impl.FakeBinaryScanner
import org.octopusden.octopus.license.inventory.impl.FakeBuildRecords
import org.octopusden.octopus.license.inventory.impl.GROUP
import org.octopusden.octopus.license.inventory.impl.LibraryTable
import org.octopusden.octopus.license.inventory.impl.OWNERSHIP
import org.octopusden.octopus.license.inventory.impl.Ownership
import org.octopusden.octopus.license.inventory.impl.build
import org.octopusden.octopus.license.inventory.impl.builtFile
import org.octopusden.octopus.license.inventory.impl.dependency
import org.octopusden.octopus.license.inventory.impl.listing.data.ListedFile
import org.octopusden.octopus.license.inventory.impl.module
import org.octopusden.octopus.license.inventory.impl.port.data.BuildDependency
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.impl.stored
import org.octopusden.octopus.license.inventory.model.BuildLookup
import org.octopusden.octopus.license.inventory.model.Evidence.BINARY_SCAN
import org.octopusden.octopus.license.inventory.model.Evidence.BUILD_RECORD
import org.octopusden.octopus.license.inventory.model.Evidence.SBOM
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode.NO_LIBRARIES_IN_FILE
import org.octopusden.octopus.license.inventory.model.FindingCode.UNRESOLVED_INTERNAL_ARCHIVE
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.SelectionReason

class FileContentReaderTest {
    private val records = FakeBuildRecords()
    private val store = FakeArtifactStore()
    private val scanner = FakeBinaryScanner()
    private val table = LibraryTable()
    private val reader =
        FileContentReader(scanner, store, BuildLocator(records), BuildDependencyReader(Ownership(OWNERSHIP)), SbomReader(store))

    private val zipName = "$COMPONENT-$BUILD_VERSION.zip"
    private val zip = stored(zipName)
    private val entry = "$GROUP:$COMPONENT:zip"

    private fun zipBuild(
        vararg dependencies: BuildDependency,
        extraFiles: List<String> = emptyList(),
    ) = build(
        modules = arrayOf(
            module(
                files = listOf(builtFile(zipName)) + extraFiles.map {
                    builtFile(it)
                },
                dependencies = dependencies.toList(),
            ),
        ),
    )

    private fun read(
        build: BuildRecord? = zipBuild(),
        file: StoredFile = zip,
    ) = reader.read(
        ListedFile(
            entry,
            FileKind.MAVEN,
            selectedBy = setOf(SelectionReason.DECLARED),
            stored = file,
            build = build,
            buildFoundBy = BuildLookup.CONVENTION,
            ownId = PackageId.parse("pkg:maven/$GROUP/$COMPONENT@$BUILD_VERSION"),
        ),
        "${COMPONENT}_ee",
        table,
    )

    private fun pkg(
        id: String,
        licenses: Set<String> = emptySet(),
        vararg evidence: org.octopusden.octopus.license.inventory.model.Evidence,
    ) = FoundLibrary(PackageId.parse(id), licenses, evidence.toSet(), setOf(entry))

    /** Our own archive `name`, stored at its Maven path, built by its own build that depends on [dependencies]. */
    private fun ownArchive(
        artifactId: String,
        vararg dependencies: BuildDependency,
        buildName: String = "${artifactId}_ie",
    ): StoredFile {
        val file = stored("$artifactId-$BUILD_VERSION.zip", path = "org/example/portal/$artifactId/$BUILD_VERSION")
        store.add(file)
        records.add(
            build(
                name = buildName,
                number = BUILD_VERSION,
                modules = arrayOf(
                    module(
                        id = "$GROUP:$artifactId:$BUILD_VERSION",
                        files = listOf(builtFile(file.name)),
                        dependencies = dependencies.toList(),
                    ),
                ),
            ),
        )
        return file
    }

    private fun ownDependency(
        artifactId: String,
        scope: String = "runtime",
    ) = dependency("$GROUP:$artifactId:$BUILD_VERSION", scope, type = "zip", sha1 = "sha1-$artifactId-$BUILD_VERSION.zip")

    @Test
    @DisplayName("a library merged into a fat jar comes from build-info only, with no license")
    fun fatJar() {
        scanner.scanned(zip, "gav://org.springframework:spring-core:6.1.4" to setOf("Apache-2.0"))

        val reading = read(zipBuild(dependency("com.fasterxml.jackson.core:jackson-databind:2.17.0", "runtime")))

        assertEquals(FileStatus.READ, reading.status)
        assertEquals(
            listOf(
                pkg("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0", emptySet(), BUILD_RECORD),
                pkg("pkg:maven/org.springframework/spring-core@6.1.4", setOf("Apache-2.0"), BINARY_SCAN),
            ),
            table.libraries(),
        )
    }

    @Test
    @DisplayName("one library from Xray and the build is one package with both evidences and Xray's licenses")
    fun twoSources() {
        scanner.scanned(zip, "gav://org.springframework:spring-core:6.1.4" to setOf("Apache-2.0"))

        read(zipBuild(dependency("org.springframework:spring-core:6.1.4", "runtime")))

        assertEquals(
            listOf(pkg("pkg:maven/org.springframework/spring-core@6.1.4", setOf("Apache-2.0"), BINARY_SCAN, BUILD_RECORD)),
            table.libraries(),
        )
    }

    @Test
    @DisplayName("an SBOM of any module of the build adds its components; one that cannot be read is skipped")
    fun sboms() {
        scanner.scanned(zip)
        val sbom = stored("portal-ui.cdx.json", sha1 = "sha1-portal-ui.cdx.json")
        store.add(sbom, """{"components":[{"purl":"pkg:npm/lodash@4.17.21","licenses":[{"license":{"id":"MIT"}}]}]}""")
        store.add(stored("broken-bom.json"))
        val record =
            build(
                modules =
                    arrayOf(
                        module(files = listOf(builtFile(zipName))),
                        module(
                            id = "portal-ui:1.0",
                            type = "npm",
                            files = listOf(builtFile("portal-ui.cdx.json"), builtFile("broken-bom.json")),
                        ),
                    ),
            )

        read(record)

        assertEquals(listOf(pkg("pkg:npm/lodash@4.17.21", setOf("MIT"), SBOM)), table.libraries())
    }

    @Test
    @DisplayName("a library in several SBOMs of one build keeps the last file's licenses (D10.8)")
    fun lastSbomWins() {
        scanner.scanned(zip)
        store.add(stored("a.cdx.json"), """{"components":[{"purl":"pkg:npm/lodash@4.17.21","licenses":[{"license":{"id":"MIT"}}]}]}""")
        store.add(stored("b.cdx.json"), """{"components":[{"purl":"pkg:npm/lodash@4.17.21"}]}""")

        read(zipBuild(extraFiles = listOf("a.cdx.json", "b.cdx.json")))

        assertEquals(emptySet<String>(), table.libraries().single().licenses)
    }

    @Test
    @DisplayName("a plain file is not read from its build: no SBOM is downloaded")
    fun plainFileNotRead() {
        val yaml = stored("$COMPONENT-config-$BUILD_VERSION.yaml")
        store.add(stored("a.cdx.json"), """{"components":[{"purl":"pkg:npm/lodash@4.17.21"}]}""")

        val reading = read(zipBuild(extraFiles = listOf("a.cdx.json")), yaml)

        assertEquals(FileStatus.PLAIN_FILE, reading.status)
        assertEquals(emptyList<FoundLibrary>(), table.libraries())
    }

    @Test
    @DisplayName("neither Xray nor a build: NOT_READABLE and nothing found")
    fun notReadable() {
        assertEquals(FileStatus.NOT_READABLE, read(build = null).status)
        assertEquals(emptyList<FoundLibrary>(), table.libraries())
    }

    @Test
    @DisplayName("our plugin zip under a custom configuration, proven by Xray, is followed; its libraries count for the outer zip")
    fun nestedByProof() {
        ownArchive("portal-plugins", dependency("org.apache.commons:commons-text:1.11.0", "runtime"))
        scanner.scanned(zip, "gav://$GROUP:portal-plugins:$BUILD_VERSION" to setOf("Unknown"))
        scanner.scanned(
            stored("portal-plugins-$BUILD_VERSION.zip", path = "org/example/portal/portal-plugins/$BUILD_VERSION"),
            "gav://org.apache.commons:commons-text:1.11.0" to setOf("Apache-2.0"),
        )

        read(zipBuild(ownDependency("portal-plugins", scope = "modpackages")))

        assertEquals(
            listOf(
                pkg("pkg:maven/org.apache.commons/commons-text@1.11.0", setOf("Apache-2.0"), BINARY_SCAN, BUILD_RECORD),
                pkg("pkg:maven/$GROUP/portal-plugins@$BUILD_VERSION", setOf("Unknown"), BINARY_SCAN),
            ),
            table.libraries(),
        )
    }

    @Test
    @DisplayName("the same archive without Xray's proof is neither followed nor found")
    fun nestedWithoutProof() {
        ownArchive("portal-plugins", dependency("org.apache.commons:commons-text:1.11.0", "runtime"))
        scanner.scanned(zip)

        read(zipBuild(ownDependency("portal-plugins", scope = "modpackages")))

        assertEquals(emptyList<FoundLibrary>(), table.libraries())
    }

    @Test
    @DisplayName("a followed archive Xray finds no library in is data: only Xray's list counts, its build is ignored")
    fun dataArchive() {
        ownArchive("portal-model", dependency("com.google.guava:guava:32.1.2-jre", "runtime"))
        scanner.scanned(zip)
        scanner.scanned(
            stored("portal-model-$BUILD_VERSION.zip", path = "org/example/portal/portal-model/$BUILD_VERSION"),
            "gav://$GROUP:portal-model:$BUILD_VERSION" to setOf("Unknown"),
        )

        val reading = read(zipBuild(ownDependency("portal-model")))

        assertEquals(listOf(PackageId.parse("pkg:maven/$GROUP/portal-model@$BUILD_VERSION")), table.libraries().map { it.id })
        assertEquals(
            listOf(Finding(NO_LIBRARIES_IN_FILE, "portal-model-$BUILD_VERSION.zip", "inner archive without libraries, build-info ignored")),
            reading.findings,
        )
    }

    @Test
    @DisplayName("a followed archive is located at its Maven path before its sha1, which can be its sources'")
    fun nestedByPathFirst() {
        ownArchive("portal-plugins", dependency("org.apache.commons:commons-text:1.11.0", "runtime"))
        store.add(stored("portal-plugins-$BUILD_VERSION-sources.zip", sha1 = "sha1-sources"))
        scanner.scanned(zip)
        scanner.scanned(
            stored("portal-plugins-$BUILD_VERSION.zip", path = "org/example/portal/portal-plugins/$BUILD_VERSION"),
            "gav://x:y:1" to setOf("MIT"),
        )

        read(zipBuild(dependency("$GROUP:portal-plugins:$BUILD_VERSION", "runtime", type = "zip", sha1 = "sha1-sources")))

        assertEquals(
            setOf("pkg:maven/x/y@1", "pkg:maven/org.apache.commons/commons-text@1.11.0"),
            table
                .libraries()
                .map {
                    it.id.toString()
                }.toSet(),
        )
    }

    @Test
    @DisplayName("an archive with no file or no build is UNRESOLVED_INTERNAL_ARCHIVE, left to Xray's scan")
    fun unresolved() {
        scanner.scanned(zip)

        val reading = read(zipBuild(ownDependency("portal-gone")))

        assertEquals(
            listOf(Finding(UNRESOLVED_INTERNAL_ARCHIVE, "$GROUP:portal-gone:$BUILD_VERSION", "covered by the Xray scan only")),
            reading.findings,
        )
    }

    @Test
    @DisplayName("archives nested twelve deep: the one at depth 11 is read, the one at depth 12 is not followed")
    fun depthLimit() {
        scanner.scanned(zip)
        (1..12).forEach { depth ->
            val next = if (depth < 12) arrayOf(ownDependency("level-${depth + 1}")) else arrayOf(dependency("lib:at-12:1", "runtime"))
            val file = ownArchive("level-$depth", *next, dependency("lib:at-$depth:1", "runtime"))
            scanner.scanned(file, "gav://lib:x-$depth:1" to setOf("MIT"))
        }

        read(zipBuild(ownDependency("level-1")))

        val found = table.libraries().map { it.id.toString() }.toSet()
        assertEquals(true, "pkg:maven/lib/at-11@1" in found)
        assertEquals(false, "pkg:maven/lib/at-12@1" in found)
    }

    @Test
    @DisplayName("two archives depending on each other are each followed once")
    fun cycle() {
        scanner.scanned(zip)
        val a = ownArchive("cycle-a", ownDependency("cycle-b"), dependency("lib:in-a:1", "runtime"))
        val b = ownArchive("cycle-b", ownDependency("cycle-a"), dependency("lib:in-b:1", "runtime"))
        scanner.scanned(a, "gav://x:a:1" to setOf("MIT"))
        scanner.scanned(b, "gav://x:b:1" to setOf("MIT"))

        read(zipBuild(ownDependency("cycle-a")))

        assertEquals(1, scanner.asked.count { it == b.fullPath })
        assertEquals(
            setOf("pkg:maven/lib/in-a@1", "pkg:maven/lib/in-b@1"),
            table
                .libraries()
                .map {
                    it.id.toString()
                }.filter { it.contains("/in-") }
                .toSet(),
        )
    }
}
