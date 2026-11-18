package org.octopusden.octopus.license.target.exception

/**
 * The registry answered, but not with anything this module can use: a client error other than
 * not-found, or a body that could not be read. The message names only the status and the
 * component version; the registry's URL and body stay in [cause].
 */
class UnexpectedRegistryAnswerException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
