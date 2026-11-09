package org.octopusden.octopus.license.policy.impl

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.policy.exception.InvalidLicenseRegistryException
import org.octopusden.octopus.license.policy.exception.LicenseRegistryUnavailableException
import org.octopusden.octopus.license.policy.model.LicenseText
import org.octopusden.octopus.license.policy.model.LicenseVerdict
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Drives the published Bitbucket client over HTTP against a stub that answers the way Bitbucket
 * Server does, so the client is proven on this classpath (D2) and the consistent-commit rule (D3)
 * is exercised end to end.
 */
class BitbucketLicensePolicySourceTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val files = mutableMapOf<String, String>()
    private var heads = ArrayDeque(listOf(COMMIT))
    private var failure: Pair<Int, String>? = null
    private lateinit var server: HttpServer

    @BeforeEach
    fun startBitbucket() {
        files += REGISTRY
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                requests += path
                val (status, body, type) = answer(path)
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", type)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    }

    @AfterEach
    fun stopBitbucket() = server.stop(0)

    private fun answer(path: String): Triple<Int, String, String> {
        failure?.let { (status, body) -> return Triple(status, body, JSON) }
        return when {
            path == "$REPO/commits" && heads.isEmpty() -> Triple(OK, page(""), JSON)
            path == "$REPO/commits" -> {
                val head = if (heads.size > 1) heads.removeFirst() else heads.first()
                Triple(OK, page("""{"id":"$head","message":"m","author":{"name":"a"},"authorTimestamp":0,"parents":[]}"""), JSON)
            }
            path == "$REPO/files" -> Triple(OK, page(files.keys.joinToString(",") { "\"$it\"" }), JSON)
            path.startsWith("$REPO/raw/") ->
                files[path.removePrefix("$REPO/raw/")]?.let { Triple(OK, it, TEXT) } ?: notFound(path)
            else -> notFound(path)
        }
    }

    private fun notFound(path: String) =
        Triple(NOT_FOUND, """{"errors":[{"message":"$path does not exist","exceptionName":"NotFound"}]}""", JSON)

    private fun page(values: String) = """{"values":[$values],"size":1,"isLastPage":true,"start":0,"limit":1000,"nextPageStart":null}"""

    private fun source(url: String = "http://localhost:${server.address.port}") =
        BitbucketLicensePolicySource(url, "token", PROJECT, REPOSITORY)

    @Test
    @DisplayName("a load reads the head commit, the file list and every file, and records the commit")
    fun load() {
        val policy = source().load()

        assertEquals(COMMIT, policy.commit)
        assertEquals(LicenseVerdict.ALLOWED, policy.judge(setOf("The MIT License")).verdict)
        assertEquals(LicenseText.InRegistry("licenses/MIT.txt", "MIT text"), policy.text("MIT"))
        assertTrue(requests.contains("$REPO/raw/licenses/MIT.txt"))
    }

    @Test
    @DisplayName("checking the head reads one commit and no file")
    fun headCommit() {
        assertEquals(COMMIT, source().headCommit())
        assertEquals(listOf("$REPO/commits"), requests)
    }

    @Test
    @DisplayName("D3: a branch that moves during a load is read again, so the files are of one commit")
    fun headMovesOnce() {
        heads = ArrayDeque(listOf("c1", "c2", "c2", "c2"))

        assertEquals("c2", source().load().commit)
    }

    @Test
    @DisplayName("D3: a branch that keeps moving fails the load after three tries")
    fun headKeepsMoving() {
        heads = ArrayDeque(listOf("c1", "c2", "c3", "c4", "c5", "c6", "c7"))

        assertThrows<LicenseRegistryUnavailableException> { source().load() }
    }

    @Test
    @DisplayName("a branch without commits is an unavailable registry")
    fun noCommits() {
        heads = ArrayDeque()

        assertThrows<LicenseRegistryUnavailableException> { source().headCommit() }
    }

    @Test
    @DisplayName("a registry without a whitelist is invalid, not unavailable")
    fun noWhitelist() {
        files.remove("licenses-whitelist.txt")

        assertThrows<InvalidLicenseRegistryException> { source().load() }
    }

    @Test
    @DisplayName("a 401 from Bitbucket is an unavailable registry")
    fun unauthorized() {
        failure = UNAUTHORIZED to """{"errors":[{"message":"Authentication failed","exceptionName":"AuthenticationException"}]}"""

        assertThrows<LicenseRegistryUnavailableException> { source().headCommit() }
    }

    @Test
    @DisplayName("a 503 that is not Bitbucket's JSON is an unavailable registry")
    fun serviceUnavailable() {
        failure = SERVICE_UNAVAILABLE to "<html>maintenance</html>"

        assertThrows<LicenseRegistryUnavailableException> { source().load() }
    }

    @Test
    @DisplayName("a Bitbucket nobody listens on is an unavailable registry")
    fun unreachable() {
        val closedPort = ServerSocket(0).use { it.localPort }

        assertThrows<LicenseRegistryUnavailableException> { source("http://localhost:$closedPort").load() }
    }

    private companion object {
        const val PROJECT = "SAMPLE"
        const val REPOSITORY = "sample-license-registry"
        const val REPO = "/rest/api/1.0/projects/$PROJECT/repos/$REPOSITORY"
        const val COMMIT = "a1b2c3d"
        const val JSON = "application/json"
        const val TEXT = "text/plain"
        const val OK = 200
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        const val SERVICE_UNAVAILABLE = 503

        val REGISTRY =
            mapOf(
                "licenses-whitelist.txt" to "Apache-2.0|MIT",
                "licenses-hidden.txt" to "BORLAND",
                "merges.txt" to "MIT|The MIT License",
                "thirdparty-licenses.properties" to "# overrides\n",
                "licenses.properties" to "MIT=licenses/MIT.txt",
                "licenses/MIT.txt" to "MIT text",
                "templates/third-party-file.ftl" to "not read",
            )
    }
}
