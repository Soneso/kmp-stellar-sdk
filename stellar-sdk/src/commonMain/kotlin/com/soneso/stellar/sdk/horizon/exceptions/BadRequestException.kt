package com.soneso.stellar.sdk.horizon.exceptions

/**
 * Exception thrown when the server returns a 4xx client error status code.
 * This indicates that the request was malformed or contained invalid parameters.
 *
 * @property code The HTTP status code (4xx)
 * @property body The raw body of the response
 */
class BadRequestException(
    message: String? = null,
    cause: Throwable? = null,
    code: Int? = null,
    body: String? = null
) : NetworkException(message, cause, code, body) {

    constructor(code: Int?, body: String?) : this(
        message = "Bad request (code: $code)",
        cause = null,
        code = code,
        body = body
    )
}
