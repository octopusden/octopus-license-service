package org.octopusden.octopus.license.service.service.impl

import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class LicenseTextDownloaderTest {
    private val calls = AtomicInteger()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/terms.html") { exchange ->
                calls.incrementAndGet()
                val body = TERMS.toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/missing") { exchange ->
                calls.incrementAndGet()
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            createContext("/moved") { exchange ->
                exchange.responseHeaders.add("Location", "/terms.html")
                exchange.sendResponseHeaders(301, -1)
                exchange.close()
            }
            createContext("/slow") { exchange ->
                Thread.sleep(SLOW_MILLIS)
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val meterRegistry = SimpleMeterRegistry()
    private val downloader = LicenseTextDownloader(Duration.ofMillis(TIMEOUT_MILLIS), meterRegistry)

    @AfterEach
    fun stop() = server.stop(0)

    private fun downloads(outcome: String) =
        meterRegistry
            .find("license.texts.downloads")
            .tag("outcome", outcome)
            .counter()
            ?.count() ?: 0.0

    @Test
    @DisplayName("a reachable text is returned as its bytes and counted downloaded")
    fun downloaded() {
        val texts = downloader.download(setOf("$base/terms.html"))

        assertEquals(mapOf("$base/terms.html" to TERMS), texts.mapValues { String(it.value) })
        assertEquals(1.0, downloads("downloaded"))
    }

    @Test
    @DisplayName("a redirect is followed, as a browser and the plugin follow it")
    fun redirect() {
        assertEquals(
            TERMS,
            downloader
                .download(setOf("$base/moved"))
                .values
                .single()
                .let(::String),
        )
    }

    @Test
    @DisplayName("an error answer, a timeout and a host that does not answer are left out and counted failed (D7)")
    fun failed() {
        val texts = downloader.download(setOf("$base/missing", "$base/slow", "http://127.0.0.1:1/none"))

        assertEquals(emptyMap<String, ByteArray>(), texts)
        assertEquals(3.0, downloads("failed"))
    }

    @Test
    @DisplayName("a downloaded text is kept, so a later report asks the host nothing and is counted cached (D7)")
    fun cached() {
        downloader.download(setOf("$base/terms.html"))
        val again = downloader.download(setOf("$base/terms.html"))

        assertEquals(1, calls.get())
        assertEquals(TERMS, String(again.values.single()))
        assertEquals(1.0, downloads("cached"))
    }

    @Test
    @DisplayName("a failure is not kept: the next report asks again (D7)")
    fun failureNotCached() {
        downloader.download(setOf("$base/missing"))
        downloader.download(setOf("$base/missing"))

        assertEquals(2, calls.get())
    }

    private companion object {
        const val TERMS = "GWT terms"
        const val TIMEOUT_MILLIS = 500L
        const val SLOW_MILLIS = 2_000L
    }
}
