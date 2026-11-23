package org.octopusden.octopus.license.core.dto

/** The error envelope of the Octopus REST API Guidelines: `{"error": {"code": 404, "message": "..."}}`. */
data class ApiErrorResponse(
    val error: ApiError,
)

data class ApiError(
    val code: Int,
    val message: String,
)
