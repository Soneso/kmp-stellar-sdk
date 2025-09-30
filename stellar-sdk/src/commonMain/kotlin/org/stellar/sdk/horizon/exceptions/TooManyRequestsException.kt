package org.stellar.sdk.horizon.exceptions

/**
 * Exception thrown when the server returns a 429 Too Many Requests status code.
 * This indicates that the client has sent too many requests in a given amount of time.
 *
 * @property code The HTTP status code (429)
 * @property body The raw body of the response
 */
class TooManyRequestsException(
    message: String? = null,
    cause: Throwable? = null,
    code: Int? = null,
    body: String? = null
) : NetworkException(message, cause, code, body) {

    constructor(code: Int?, body: String?) : this(
        message = "Too many requests (code: $code)",
        cause = null,
        code = code,
        body = body
    )
}
