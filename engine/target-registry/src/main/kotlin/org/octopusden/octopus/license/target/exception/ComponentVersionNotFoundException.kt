package org.octopusden.octopus.license.target.exception

/** No answer exists for the request, as opposed to an exclusion, which is an answer (D3). */
class ComponentVersionNotFoundException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
