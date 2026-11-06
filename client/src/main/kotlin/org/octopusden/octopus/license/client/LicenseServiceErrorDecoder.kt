package org.octopusden.octopus.license.client

import feign.Response
import feign.codec.ErrorDecoder
import org.octopusden.octopus.license.core.dto.ApiErrorResponse
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
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
        val message = response.body()?.asInputStream()?.use { body ->
            try {
                jsonMapper.readValue(body, ApiErrorResponse::class.java).error.message
            } catch (_: JacksonException) {
                null
            }
        } ?: return default.decode(methodKey, response)
        return when (response.status()) {
            HTTP_BAD_REQUEST -> InvalidRequestException(message)
            HTTP_NOT_FOUND -> NotFoundException(message)
            HTTP_CONFLICT -> LicenseFilesNotAvailableException(message)
            HTTP_SERVICE_UNAVAILABLE -> ServiceUnavailableException(message)
            else -> default.decode(methodKey, response)
        }
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
