package org.octopusden.octopus.license.core.exception

import java.time.Duration

/**
 * Errors the license service answers with. The server maps each type to an HTTP status and the
 * client maps the status back, so callers on both sides catch the same types. A cause stays on
 * the server side: only the message crosses the wire.
 */
sealed class LicenseServiceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class NotFoundException(
    message: String,
    cause: Throwable? = null,
) : LicenseServiceException(message, cause)

/** The report exists but has no license files: it is still running, or it is excluded or failed. */
class LicenseFilesNotAvailableException(
    message: String,
) : LicenseServiceException(message)

class InvalidRequestException(
    message: String,
) : LicenseServiceException(message)

/**
 * A system the service reads, such as the components registry, could not be asked; asking again
 * later may work. Answered as `503` without `Retry-After`, which tells it apart from [ServiceBusyException].
 */
class ServiceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : LicenseServiceException(message, cause)

/**
 * Every generation thread is busy and the queue is full; start the report again later.
 * [retryAfter] is the wait the service asked for, when the client read one from the answer.
 */
class ServiceBusyException(
    message: String,
    val retryAfter: Duration? = null,
) : LicenseServiceException(message)

/**
 * The service could not be reached, or a gateway in front of it answered instead (502, 503 or 504
 * without the service's error envelope). Raised by the client only; asking again may succeed.
 */
class ServiceUnreachableException(
    message: String,
    cause: Throwable? = null,
) : LicenseServiceException(message, cause)
