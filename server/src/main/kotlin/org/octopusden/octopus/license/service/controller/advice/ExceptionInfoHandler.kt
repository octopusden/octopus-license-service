package org.octopusden.octopus.license.service.controller.advice

import org.octopusden.octopus.license.core.dto.ApiError
import org.octopusden.octopus.license.core.dto.ApiErrorResponse
import org.octopusden.octopus.license.core.exception.InvalidRequestException
import org.octopusden.octopus.license.core.exception.LicenseFilesNotAvailableException
import org.octopusden.octopus.license.core.exception.NotFoundException
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ExceptionInfoHandler {
    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException) = errorResponse(HttpStatus.NOT_FOUND, e)

    @ExceptionHandler(LicenseFilesNotAvailableException::class)
    fun handleFilesNotAvailable(e: LicenseFilesNotAvailableException) = errorResponse(HttpStatus.CONFLICT, e)

    @ExceptionHandler(ServiceBusyException::class)
    fun handleBusy(e: ServiceBusyException) =
        errorResponse(HttpStatus.SERVICE_UNAVAILABLE, e) { header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS.toString()) }

    @ExceptionHandler(InvalidRequestException::class, HttpMessageNotReadableException::class)
    fun handleBadRequest(e: Exception) = errorResponse(HttpStatus.BAD_REQUEST, e)

    /**
     * Spring's own exceptions (unknown path, wrong method, unsupported media type) implement
     * [ErrorResponse] and carry their status; only the rest is a server error.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiErrorResponse> {
        if (e is ErrorResponse) {
            return errorResponse(HttpStatus.valueOf(e.statusCode.value()), e)
        }
        log.error(e.message, e)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e)
    }

    private fun errorResponse(
        status: HttpStatus,
        e: Exception,
        headers: ResponseEntity.BodyBuilder.() -> Unit = {},
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity
            .status(status)
            .apply(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiErrorResponse(ApiError(status.value(), e.message ?: status.reasonPhrase)))

    private companion object {
        const val RETRY_AFTER_SECONDS = 60
        val log = LoggerFactory.getLogger(ExceptionInfoHandler::class.java)
    }
}
