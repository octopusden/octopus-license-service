package org.octopusden.octopus.license.core.exception

/**
 * Errors the license service answers with. The server maps each type to an HTTP status and the
 * client maps the status back, so callers on both sides catch the same types.
 */
sealed class LicenseServiceException(
    message: String,
) : RuntimeException(message)

class NotFoundException(
    message: String,
) : LicenseServiceException(message)

/** The report exists but has no license files: it is still running, or it is excluded or failed. */
class LicenseFilesNotAvailableException(
    message: String,
) : LicenseServiceException(message)

class InvalidRequestException(
    message: String,
) : LicenseServiceException(message)
