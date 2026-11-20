package org.octopusden.octopus.license.service.service.impl

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads the license texts the registry gives only as a URL, as the license plugins did. A text
 * that could not be downloaded is left out: the files list it as missing, the report still finishes.
 *
 * A downloaded text is kept for the life of the process, a failure is not, so the next report tries
 * again (license files design, D7). Few licenses are given by URL, so the cache needs no bound.
 */
class LicenseTextDownloader(
    private val timeout: Duration,
    private val meterRegistry: MeterRegistry,
) {
    private val client =
        HttpClient
            .newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    private val downloaded = ConcurrentHashMap<String, ByteArray>()

    /** @return URL to bytes, for the URLs that could be downloaded only. */
    fun download(urls: Set<String>): Map<String, ByteArray> = urls.mapNotNull { url -> text(url)?.let { url to it } }.toMap()

    private fun text(url: String): ByteArray? {
        downloaded[url]?.let {
            count("cached")
            return it
        }
        return fetch(url)
            ?.also {
                downloaded[url] = it
                count("downloaded")
            }
            ?: null.also { count("failed") }
    }

    private fun fetch(url: String): ByteArray? =
        try {
            val response =
                client.send(
                    HttpRequest
                        .newBuilder(URI.create(url))
                        .timeout(timeout)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray(),
                )
            if (response.statusCode() in SUCCESS) {
                response.body()
            } else {
                null.also { log.warn("License text at {} answered {}", url, response.statusCode()) }
            }
        } catch (e: IOException) {
            null.also { log.warn("License text at {} could not be downloaded: {}", url, e.toString()) }
        } catch (e: IllegalArgumentException) {
            null.also { log.warn("License text URL {} is not valid: {}", url, e.message) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null.also { log.warn("License text download of {} interrupted", url) }
        }

    private fun count(outcome: String) = meterRegistry.counter(DOWNLOADS_METRIC, "outcome", outcome).increment()

    private companion object {
        const val DOWNLOADS_METRIC = "license.texts.downloads"
        val SUCCESS = 200..299
        val log = LoggerFactory.getLogger(LicenseTextDownloader::class.java)
    }
}
