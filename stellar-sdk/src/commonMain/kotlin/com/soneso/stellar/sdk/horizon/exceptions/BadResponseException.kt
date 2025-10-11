package com.soneso.stellar.sdk.horizon.exceptions

/**
 * Exception thrown when the server returns a 5xx server error status code.
 * This indicates that the server encountered an error while processing the request.
 *
 * @property code The HTTP status code (5xx)
 * @property body The raw body of the response
 */
class BadResponseException(
    message: String? = null,
    cause: Throwable? = null,
    code: Int? = null,
    body: String? = null
) : NetworkException(message, cause, code, body) {

    constructor(code: Int?, body: String?) : this(
        message = "Bad response from server (code: $code)",
        cause = null,
        code = code,
        body = body
    )
}
