package org.octopusden.octopus.license.service.service.impl

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Downloads the license texts the registry gives only as a URL, as the license plugins did. A text
 * that could not be downloaded is left out: the files list it as missing, the report still finishes.
 *
 * A downloaded text is kept for the life of the process. A failure is kept for [failureRetryAfter],
 * so one unreachable host costs one timeout per while, not one per report (license files design,
 * D7). Few licenses are given by URL, so neither needs a bound.
 */
class LicenseTextDownloader(
    private val timeout: Duration,
    private val failureRetryAfter: Duration,
    private val meterRegistry: MeterRegistry,
    private val now: () -> Instant = Instant::now,
) {
    private val client =
        HttpClient
            .newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    private val downloaded = ConcurrentHashMap<String, ByteArray>()
    private val failedAt = ConcurrentHashMap<String, Instant>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<ByteArray?>>()

    /** @return URL to bytes, for the URLs that could be downloaded only. */
    fun download(urls: Set<String>): Map<String, ByteArray> = urls.mapNotNull { url -> text(url)?.let { url to it } }.toMap()

    // Reports that ask for one URL at once wait for one download, so an unreachable host costs one
    // timeout per while, not one per generation thread; only the one that downloads counts it.
    private fun text(url: String): ByteArray? {
        known(url)?.let { return it.text }
        val mine = CompletableFuture<ByteArray?>()
        inFlight.putIfAbsent(url, mine)?.let { running ->
            count("shared")
            return running.join()
        }
        try {
            // A download may have finished between the check above and taking the URL.
            val text = known(url)?.let { return it.text.also(mine::complete) } ?: fetch(url)
            publish(url, text)
            mine.complete(text)
            return text
        } finally {
            // Waiters are released even on an unexpected error; the URL is let go only after its
            // result is published, so no caller starts a second download in between.
            mine.complete(null)
            inFlight.remove(url, mine)
        }
    }

    private fun known(url: String): Known? {
        downloaded[url]?.let {
            count("cached")
            return Known(it)
        }
        val failed = failedAt[url] ?: return null
        if (now() >= failed.plus(failureRetryAfter)) return null
        count("skipped")
        return Known(null)
    }

    private fun publish(
        url: String,
        text: ByteArray?,
    ) {
        if (text != null) {
            downloaded[url] = text
            failedAt.remove(url)
            count("downloaded")
        } else {
            failedAt[url] = now()
            count("failed")
        }
    }

    private class Known(
        val text: ByteArray?,
    )

    // The request's own timeout stops at the headers; the deadline here covers the body too, so a
    // host that stalls mid-body cannot hold a generation thread.
    private fun fetch(url: String): ByteArray? {
        val call =
            try {
                client.sendAsync(
                    HttpRequest
                        .newBuilder(URI.create(url))
                        .timeout(timeout)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray(),
                )
            } catch (e: IllegalArgumentException) {
                log.warn("License text URL {} is not valid: {}", url, e.message)
                return null
            }
        return try {
            val response = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (response.statusCode() in SUCCESS) {
                response.body()
            } else {
                null.also { log.warn("License text at {} answered {}", url, response.statusCode()) }
            }
        } catch (e: TimeoutException) {
            call.cancel(true)
            null.also { log.warn("License text at {} not downloaded within {}: {}", url, timeout, e.toString()) }
        } catch (e: ExecutionException) {
            null.also { log.warn("License text at {} could not be downloaded: {}", url, e.cause.toString()) }
        } catch (e: InterruptedException) {
            call.cancel(true)
            Thread.currentThread().interrupt()
            null.also { log.warn("License text download of {} interrupted", url) }
        }
    }

    private fun count(outcome: String) = meterRegistry.counter(DOWNLOADS_METRIC, "outcome", outcome).increment()

    private companion object {
        const val DOWNLOADS_METRIC = "license.texts.downloads"
        val SUCCESS = 200..299
        val log = LoggerFactory.getLogger(LicenseTextDownloader::class.java)
    }
}
