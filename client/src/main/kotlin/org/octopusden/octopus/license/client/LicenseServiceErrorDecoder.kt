package org.octopusden.octopus.license.client

import feign.Response
import feign.Util
import feign.codec.ErrorDecoder
import org.octopusden.octopus.license.core.dto.ApiErrorResponse
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.octopusden.octopus.license.core.exception.ServiceUnreachableException
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

internal class LicenseServiceErrorDecoder(
    private val jsonMapper: JsonMapper,
) : ErrorDecoder {
    private val default = ErrorDecoder.Default()

    override fun decode(
        methodKey: String,
        response: Response,
    ): Exception {
        // The body stream can be read once; the default decoder needs it again for FeignException.
        val body = response.body()?.asInputStream()?.use { Util.toByteArray(it) }
        val buffered = response.toBuilder().body(body).build()
        val message = body?.let {
            try {
                jsonMapper.readValue(it, ApiErrorResponse::class.java).error.message
            } catch (_: JacksonException) {
                null
            }
        }
        val status = response.status()
        return when {
            // The license service answers 503 only when it is busy, always with its envelope; any other
            // 502/503/504 comes from something in front of it, whatever its body.
            status == HTTP_SERVICE_UNAVAILABLE && message != null -> ServiceBusyException(message, retryAfter(response))
            status in GATEWAY_ERRORS -> ServiceUnreachableException("$methodKey answered $status, not from the license service")
            message == null -> default.decode(methodKey, buffered)
            status == HTTP_BAD_REQUEST -> InvalidRequestException(message)
            status == HTTP_NOT_FOUND -> NotFoundException(message)
            status == HTTP_CONFLICT -> LicenseFilesNotAvailableException(message)
            else -> default.decode(methodKey, buffered)
        }
    }

    private fun retryAfter(response: Response) =
        response
            .headers()
            .entries
            .firstOrNull { it.key.equals(RETRY_AFTER, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.trim()
            ?.toLongOrNull()
            ?.let(Duration::ofSeconds)

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val RETRY_AFTER = "Retry-After"
        val GATEWAY_ERRORS = setOf(502, HTTP_SERVICE_UNAVAILABLE, 504)
    }
}
