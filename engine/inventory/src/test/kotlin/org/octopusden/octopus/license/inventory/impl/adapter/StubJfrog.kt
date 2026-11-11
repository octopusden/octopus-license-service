package org.octopusden.octopus.license.inventory.impl.adapter

import com.sun.net.httpserver.HttpServer
import org.octopusden.octopus.infrastructure.artifactory.client.ArtifactoryClassicClient
import org.octopusden.octopus.infrastructure.client.commons.ClientParametersProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.xray.client.XrayClassicClient
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A JFrog platform answering over HTTP from a queue of answers per `METHOD path`, so the published
 * clients are exercised on this classpath. An answer is reused once it is the last one queued.
 */
internal class StubJfrog : AutoCloseable {
    data class Request(
        val method: String,
        val path: String,
        val query: String?,
        val body: String,
    )

    val requests = CopyOnWriteArrayList<Request>()
    private val answers = mutableMapOf<String, ArrayDeque<Pair<Int, String>>>()
    private val server =
        HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                val request =
                    Request(
                        exchange.requestMethod,
                        exchange.requestURI.path,
                        exchange.requestURI.query,
                        exchange.requestBody.use { String(it.readBytes()) },
                    )
                requests += request
                val queue = answers["${request.method} ${request.path}"]
                val (status, body) =
                    when {
                        queue == null -> 404 to """{"errors":[{"status":404,"message":"Not Found"}]}"""
                        queue.size > 1 -> queue.removeFirst()
                        else -> queue.first()
                    }
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                exchange.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) }
            }
            start()
        }

    private val parameters =
        object : ClientParametersProvider {
            override fun getApiUrl() = "http://localhost:${server.address.port}"

            override fun getAuth() = StandardBearerTokenCredentialProvider("token")
        }

    val artifactory = ArtifactoryClassicClient(parameters)
    val xray = XrayClassicClient(parameters)

    fun answer(
        methodAndPath: String,
        vararg answers: Pair<Int, String>,
    ) {
        this.answers[methodAndPath] = ArrayDeque(answers.toList())
    }

    fun requests(methodAndPath: String) = requests.filter { "${it.method} ${it.path}" == methodAndPath }

    override fun close() = server.stop(0)

    companion object {
        const val AQL = "POST /artifactory/api/search/aql"
        const val STATUS = "POST /xray/api/v1/artifact/status"
        const val SUMMARY = "POST /xray/api/v1/summary/artifact"
        const val COMPONENTS = "POST /xray/api/v1/summary/component"
    }
}

/** Calls that retry without waiting, recording the pauses they would have made. */
internal fun noWaitCalls(pauses: MutableList<java.time.Duration> = mutableListOf()) = JfrogCalls { pauses += it }
