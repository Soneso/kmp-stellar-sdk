package com.stellar.sdk.horizon.exceptions

/**
 * Exception thrown when the server returns an unknown or unexpected status code.
 * This typically occurs when the server returns a status code that is not handled by the SDK.
 *
 * @property code The HTTP status code
 * @property body The raw body of the response
 */
class UnknownResponseException(
    message: String? = null,
    cause: Throwable? = null,
    code: Int? = null,
    body: String? = null
) : NetworkException(message, cause, code, body) {

    constructor(code: Int?, body: String?) : this(
        message = "Unknown response from server (code: $code)",
        cause = null,
        code = code,
        body = body
    )
}
