package org.octopusden.octopus.license.service.controller.advice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ExceptionInfoHandlerTest {
    @Test
    @DisplayName("a 500 answers a generic message: the exception text can hold an upstream URL and response body")
    fun serverErrorHidesExceptionText() {
        val response = ExceptionInfoHandler().handleUnexpected(
            IllegalStateException("GET https://upstream.example/api/secret returned 502: <html>proxy</html>"),
        )

        assertEquals(INTERNAL_SERVER_ERROR, response.statusCode.value())
        val message = requireNotNull(response.body).error.message
        assertFalse(message.contains("upstream.example"), message)
    }

    private companion object {
        const val INTERNAL_SERVER_ERROR = 500
    }
}
