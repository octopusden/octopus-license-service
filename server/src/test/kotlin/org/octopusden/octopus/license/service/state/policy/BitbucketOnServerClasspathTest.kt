package org.octopusden.octopus.license.service.state.policy

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.policy.exception.LicenseRegistryUnavailableException
import org.octopusden.octopus.license.policy.impl.BitbucketLicensePolicySource
import org.octopusden.octopus.license.policy.model.LicenseVerdict
import java.net.InetSocketAddress

/**
 * The server runs the Bitbucket client on its own Feign 13 and Jackson 2.21, not the Feign 12 and
 * Jackson 2.14 the client is built for (Design, Decision 2). This drives a load and an error
 * through the client on this classpath.
 */
class BitbucketOnServerClasspathTest {
    private val server =
        HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.path.removePrefix(REPO)
                val (status, body) =
                    when {
                        path == "/commits" ->
                            OK to
                                page("""{"id":"a1b2c3d","message":"m","author":{"name":"a"},"authorTimestamp":0,"parents":[]}""")
                        path == "/files" -> OK to page("\"licenses-whitelist.txt\",\"merges.txt\"")
                        path == "/raw/licenses-whitelist.txt" -> OK to "Apache-2.0|MIT"
                        path == "/raw/merges.txt" -> OK to "MIT|The MIT License"
                        else -> UNAUTHORIZED to """{"errors":[{"message":"Authentication failed"}]}"""
                    }
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

    @AfterEach
    fun stop() = server.stop(0)

    private fun source(repository: String = REPOSITORY) =
        BitbucketLicensePolicySource("http://localhost:${server.address.port}", "token", PROJECT, repository)

    @Test
    @DisplayName("a policy loads through the Bitbucket client on the server's classpath")
    fun loads() {
        val policy = source().load()

        assertEquals("a1b2c3d", policy.commit)
        assertEquals(LicenseVerdict.ALLOWED, policy.judge(setOf("The MIT License")).verdict)
    }

    @Test
    @DisplayName("an error answer is an unavailable registry on the server's classpath too")
    fun errorIsUnavailable() {
        assertThrows<LicenseRegistryUnavailableException> { source("other-repository").headCommit() }
    }

    private fun page(values: String) = """{"values":[$values],"size":1,"isLastPage":true,"start":0,"limit":1000,"nextPageStart":null}"""

    private companion object {
        const val PROJECT = "SAMPLE"
        const val REPOSITORY = "sample-license-registry"
        const val REPO = "/rest/api/1.0/projects/$PROJECT/repos/$REPOSITORY"
        const val OK = 200
        const val UNAUTHORIZED = 401
    }
}
