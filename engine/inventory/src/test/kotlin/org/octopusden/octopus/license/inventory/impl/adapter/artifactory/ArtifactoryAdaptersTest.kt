package org.octopusden.octopus.license.inventory.impl.adapter.artifactory

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.impl.adapter.StubJfrog
import org.octopusden.octopus.license.inventory.impl.adapter.StubJfrog.Companion.AQL
import org.octopusden.octopus.license.inventory.impl.adapter.noWaitCalls
import org.octopusden.octopus.license.inventory.impl.port.data.BuildDependency
import org.octopusden.octopus.license.inventory.impl.port.data.BuildModule
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.BuiltFile
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.BuildRef
import java.time.Duration

class ArtifactoryAdaptersTest {
    private val jfrog = StubJfrog()
    private val pauses = mutableListOf<Duration>()
    private val records = ArtifactoryBuildRecords(jfrog.artifactory, noWaitCalls(pauses))
    private val store = ArtifactoryArtifactStore(jfrog.artifactory, noWaitCalls(pauses))

    @AfterEach
    fun stop() = jfrog.close()

    @Test
    @DisplayName("build-info is read from the build-info repository, with each module's files, paths and dependency scopes")
    fun buildInfo() {
        jfrog.answer(
            "GET /artifactory/api/build/sample-portal_ee_docker/2.4.0-118",
            200 to
                """{"buildInfo":{"name":"sample-portal_ee_docker","number":"2.4.0-118","agent":{"name":"jfrog-cli-go","version":"2.89.0"},
                   "modules":[{"id":"portal:2.4.0-118","type":"docker",
                     "artifacts":[{"name":"manifest.json","sha1":"38ea","path":"sample/portal/2.4.0-118/manifest.json"}],
                     "dependencies":[{"id":"sha256:abc","sha1":"7f2c"},{"id":"a:b:1","type":"jar","sha1":"aa","scopes":["runtime"]}]}]}}""",
        )

        assertEquals(
            BuildRecord(
                BuildRef("sample-portal_ee_docker", "2.4.0-118"),
                "jfrog-cli-go",
                listOf(
                    BuildModule(
                        "portal:2.4.0-118",
                        "docker",
                        listOf(BuiltFile("manifest.json", "38ea", "sample/portal/2.4.0-118/manifest.json")),
                        listOf(
                            BuildDependency("sha256:abc", null, "7f2c", emptyList()),
                            BuildDependency("a:b:1", "jar", "aa", listOf("runtime")),
                        ),
                    ),
                ),
            ),
            records.find("sample-portal_ee_docker", "2.4.0-118"),
        )
        assertEquals("buildRepo=artifactory-build-info", jfrog.requests.single().query)
    }

    @Test
    @DisplayName("missing build-info and a server error are both no build, never a failure")
    fun noBuild() {
        assertEquals(null, records.find("gone", "1"))
        jfrog.answer("GET /artifactory/api/build/broken/1", 500 to "")
        assertEquals(null, records.find("broken", "1"))
    }

    @Test
    @DisplayName("builds that published a sha1 come from an AQL builds query")
    fun buildsBySha1() {
        jfrog.answer(AQL, 200 to """{"results":[{"build.name":"other_build","build.number":"12","build.repo":"artifactory-build-info"}]}""")

        assertEquals(listOf(BuildRef("other_build", "12")), records.findByPublishedSha1("0a1b"))
        assertEquals("""builds.find({"module.artifact.sha1":"0a1b"}).include("name","number","repo")""", jfrog.requests.single().body)
    }

    @Test
    @DisplayName("a file by sha1: the preferred copy, with its checksums and properties")
    fun bySha1() {
        jfrog.answer(
            AQL,
            200 to
                """{"results":[
                  {"repo":"maven-central-cache","path":"org/example/lib/1.0","name":"lib-1.0.jar","actual_sha1":"0a1b","sha256":"9f8e"},
                  {"repo":"rnd-maven-release-local","path":"org/example/lib/1.0","name":"lib-1.0.jar","actual_sha1":"0a1b","sha256":"9f8e",
                   "properties":[{"key":"build.name","value":"lib_ee"},{"key":"build.number","value":"1.0"}]}]}""",
        )

        assertEquals(
            StoredFile(
                "rnd-maven-release-local",
                "org/example/lib/1.0",
                "lib-1.0.jar",
                "0a1b",
                "9f8e",
                mapOf(
                    "build.name" to "lib_ee",
                    "build.number" to "1.0",
                ),
            ),
            store.bySha1("0a1b"),
        )
        assertEquals(
            """items.find({"actual_sha1":"0a1b"}).include("repo","path","name","actual_sha1","sha256","property")""",
            jfrog.requests.single().body,
        )
    }

    @Test
    @DisplayName("a file by path and name; none found is null")
    fun byPath() {
        jfrog.answer(AQL, 200 to """{"results":[]}""")

        assertEquals(null, store.byPath("sample/portal/2.4.0", "manifest.json"))
        assertEquals(
            """items.find({"path":"sample/portal/2.4.0","name":"manifest.json"})""" +
                """.include("repo","path","name","actual_sha1","sha256","property")""",
            jfrog.requests.single().body,
        )
    }

    @Test
    @DisplayName("every copy with a sha256, in the order Artifactory answers")
    fun bySha256() {
        jfrog.answer(AQL, 200 to """{"results":[{"repo":"b-cache","path":"p","name":"n"},{"repo":"a-local","path":"p","name":"n"}]}""")

        assertEquals(listOf("b-cache", "a-local"), store.bySha256("9f8e").map { it.repo })
    }

    @Test
    @DisplayName("a file's bytes are downloaded by its full path")
    fun read() {
        jfrog.answer("GET /artifactory/rnd-maven-release-local/org/example/bom.json", 200 to """{"components":[]}""")

        assertArrayEquals(
            """{"components":[]}""".toByteArray(),
            store.read(StoredFile("rnd-maven-release-local", "org/example", "bom.json", null, null)),
        )
    }

    @Test
    @DisplayName("a server error is tried three times, 1 s then 2 s apart, then Artifactory is unavailable")
    fun unavailable() {
        jfrog.answer(AQL, 503 to "<html>maintenance</html>")

        val error = assertThrows<InventorySourceUnavailableException> { store.bySha1("0a1b") }

        assertEquals("Artifactory", error.system)
        assertEquals(3, jfrog.requests.size)
        assertEquals(listOf(Duration.ofSeconds(1), Duration.ofSeconds(2)), pauses)
    }

    @Test
    @DisplayName("a server that recovers on the second try answers normally")
    fun recovers() {
        jfrog.answer(AQL, 502 to "", 200 to """{"results":[{"repo":"a-local","path":"p","name":"n"}]}""")

        assertEquals("a-local", store.bySha1("0a1b")?.repo)
    }

    @Test
    @DisplayName("a client error fails the scan too; the Artifactory client reports it as a server error, so it is tried three times")
    fun clientError() {
        jfrog.answer(AQL, 400 to """{"errors":[{"status":400,"message":"bad query"}]}""")

        assertThrows<InventorySourceUnavailableException> { store.bySha1("0a1b") }
        assertEquals(3, jfrog.requests.size)
    }
}
