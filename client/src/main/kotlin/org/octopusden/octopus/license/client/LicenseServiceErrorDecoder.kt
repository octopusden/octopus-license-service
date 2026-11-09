package org.octopusden.octopus.license.client

import feign.Response
import feign.Util
import feign.codec.ErrorDecoder
import org.octopusden.octopus.license.core.dto.ApiErrorResponse
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.octopusden.octopus.license.core.exception.ServiceUnavailableException
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

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
        } ?: return default.decode(methodKey, buffered)
        return when (response.status()) {
            HTTP_BAD_REQUEST -> InvalidRequestException(message)
            HTTP_NOT_FOUND -> NotFoundException(message)
            HTTP_CONFLICT -> LicenseFilesNotAvailableException(message)
            // Only a busy service says when to come back; any other 503 is a system it reads.
            HTTP_SERVICE_UNAVAILABLE ->
                if (response.headers().containsKey(RETRY_AFTER)) ServiceBusyException(message) else ServiceUnavailableException(message)
            else -> default.decode(methodKey, buffered)
        }
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val RETRY_AFTER = "Retry-After"
    }
}
