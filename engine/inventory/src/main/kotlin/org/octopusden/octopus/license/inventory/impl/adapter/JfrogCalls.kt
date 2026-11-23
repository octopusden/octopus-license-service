package org.octopusden.octopus.license.inventory.impl.adapter

import feign.FeignException
import feign.RetryableException
import org.octopusden.octopus.infrastructure.artifactory.client.exception.InternalServerError
import org.octopusden.octopus.infrastructure.xray.client.exception.XrayClientException
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import java.io.IOException
import java.io.UncheckedIOException
import java.time.Duration

/**
 * One call to Artifactory or Xray, tried three times with 1 s then 2 s between, as the prototype
 * tried each call, when the system did not answer, broke off its answer, or answered a server
 * error (D13). Any failure left is [InventorySourceUnavailableException]. A client error is not
 * retried, except from Artifactory, whose client reports every error but 404 as a server error.
 */
internal class JfrogCalls(
    private val sleep: (Duration) -> Unit = { Thread.sleep(it) },
) {
    @Suppress("TooGenericExceptionCaught") // Each client fails in its own types; all of them end the scan the same way.
    fun <T> call(
        system: String,
        what: String,
        block: () -> T,
    ): T {
        repeat(ATTEMPTS - 1) { attempt ->
            try {
                return unchecked(block)
            } catch (e: RuntimeException) {
                if (!isTransient(e)) throw unavailable(system, what, e)
                sleep(Duration.ofSeconds(1L shl attempt))
            }
        }
        return try {
            unchecked(block)
        } catch (e: RuntimeException) {
            throw unavailable(system, what, e)
        }
    }

    // A streamed download breaking off surfaces as a bare IOException, which Kotlin lets escape unchecked.
    private fun <T> unchecked(block: () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

    private fun isTransient(e: RuntimeException) =
        when (e) {
            is RetryableException -> true
            is UncheckedIOException -> true
            is InternalServerError -> true
            is XrayClientException -> e.status >= SERVER_ERROR
            is FeignException -> e.status() >= SERVER_ERROR || e.status() < 0 || e.cause is IOException
            else -> false
        }

    private fun unavailable(
        system: String,
        what: String,
        cause: RuntimeException,
    ) = cause as? InventorySourceUnavailableException
        ?: InventorySourceUnavailableException(system, "$system could not answer $what: ${cause.message}", cause)

    companion object {
        const val ARTIFACTORY = "Artifactory"
        const val XRAY = "Xray"
        private const val ATTEMPTS = 3
        private const val SERVER_ERROR = 500
    }
}
