package org.octopusden.octopus.license.service.service.impl

import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LicenseTextDownloaderTest {
    private val calls = AtomicInteger()
    private val slowCalls = AtomicInteger()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
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
            createContext("/stalled-body") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("partial".toByteArray())
                exchange.responseBody.flush()
                Thread.sleep(SLOW_MILLIS)
                exchange.close()
            }
            createContext("/slow") { exchange ->
                slowCalls.incrementAndGet()
                Thread.sleep(SLOW_MILLIS)
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val meterRegistry = SimpleMeterRegistry()
    private var now = Instant.parse("2026-11-23T10:00:00Z")
    private val downloader = LicenseTextDownloader(Duration.ofMillis(TIMEOUT_MILLIS), RETRY_AFTER, meterRegistry) { now }

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
    @DisplayName("a body that stalls after the headers is cut at the timeout and counted failed (D7)")
    fun stalledBody() {
        val started = System.nanoTime()

        val texts = downloader.download(setOf("$base/stalled-body"))

        assertEquals(emptyMap<String, ByteArray>(), texts)
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofMillis(SLOW_MILLIS), "waited for the stalled body")
        assertEquals(1.0, downloads("failed"))
    }

    @Test
    @DisplayName("reports asking for one URL at once share one download: the host is asked once (review)")
    fun concurrentShareOneDownload() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (1..4).map { pool.submit<Map<String, ByteArray>> { downloader.download(setOf("$base/slow")) } }.map { it.get() }

            assertEquals(List(4) { emptyMap<String, ByteArray>() }, results)
            assertEquals(1, slowCalls.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    @DisplayName("an unexpected error in the download still releases the reports waiting for it (review)")
    fun waitersReleasedOnError() {
        val failing =
            LicenseTextDownloader(Duration.ofMillis(TIMEOUT_MILLIS), RETRY_AFTER, meterRegistry) {
                if (slowCalls.get() > 0) error("clock broke") else now
            }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val owner = pool.submit<Any> { runCatching { failing.download(setOf("$base/slow")) } }
            while (slowCalls.get() == 0) Thread.onSpinWait()
            val waiter = pool.submit<Any> { runCatching { failing.download(setOf("$base/slow")) } }

            owner.get(SLOW_MILLIS, TimeUnit.MILLISECONDS)
            waiter.get(SLOW_MILLIS, TimeUnit.MILLISECONDS)
        } finally {
            pool.shutdownNow()
        }
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
    @DisplayName("a failure is remembered for a while: reports meanwhile do not ask the host and are counted skipped (review)")
    fun failureRemembered() {
        downloader.download(setOf("$base/missing"))
        now = now.plus(RETRY_AFTER).minusSeconds(1)

        assertEquals(emptyMap<String, ByteArray>(), downloader.download(setOf("$base/missing")))
        assertEquals(1, calls.get())
        assertEquals(1.0, downloads("skipped"))
    }

    @Test
    @DisplayName("once the while is over, the next report asks the host again")
    fun failureRetried() {
        downloader.download(setOf("$base/missing"))
        now = now.plus(RETRY_AFTER)

        downloader.download(setOf("$base/missing"))

        assertEquals(2, calls.get())
    }

    private companion object {
        const val TERMS = "GWT terms"
        const val TIMEOUT_MILLIS = 500L
        val RETRY_AFTER: Duration = Duration.ofMinutes(5)
        const val SLOW_MILLIS = 2_000L
    }
}
