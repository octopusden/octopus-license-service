package org.octopusden.octopus.license.core.exception

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

/** A system the service reads, such as the components registry, could not be asked; retrying later may work. */
class ServiceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : LicenseServiceException(message, cause)
