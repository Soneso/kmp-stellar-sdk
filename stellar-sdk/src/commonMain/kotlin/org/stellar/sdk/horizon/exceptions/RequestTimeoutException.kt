package org.stellar.sdk.horizon.exceptions

/**
 * Exception thrown when a request times out.
 * This can occur when Horizon returns a Timeout status or when a connection timeout occurs.
 */
class RequestTimeoutException(
    message: String? = null,
    cause: Throwable? = null,
    code: Int? = null,
    body: String? = null
) : NetworkException(message, cause, code, body) {

    constructor(cause: Throwable) : this(
        message = "Request timeout: ${cause.message}",
        cause = cause,
        code = null,
        body = null
    )
}
