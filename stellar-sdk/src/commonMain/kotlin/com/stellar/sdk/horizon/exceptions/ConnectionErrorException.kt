package com.stellar.sdk.horizon.exceptions

/**
 * Exception thrown when the request cannot be executed due to cancellation or connectivity problems.
 * This typically occurs when there are network connectivity issues, DNS resolution failures,
 * or the connection is unexpectedly closed.
 */
class ConnectionErrorException(
    message: String? = null,
    cause: Throwable? = null,
    code: Int? = null,
    body: String? = null
) : NetworkException(message, cause, code, body) {

    constructor(cause: Throwable) : this(
        message = "Connection error: ${cause.message}",
        cause = cause,
        code = null,
        body = null
    )
}
