package com.stellar.sdk.horizon.exceptions

/**
 * Represents an exception that occurs during network operations in the Stellar SDK.
 *
 * This exception is thrown in the following cases:
 * - When the server returns a non-2xx status code
 * - When the error field in the information returned by the server is not empty
 * - When the required resources are not found on the server, such as when an account does not exist
 * - When a request times out
 * - When a request cannot be executed due to cancellation or connectivity problems, etc.
 *
 * @property code The HTTP status code of the response, or null if not applicable
 * @property body The raw body of the response, or null if not available
 */
open class NetworkException(
    message: String? = null,
    cause: Throwable? = null,
    val code: Int? = null,
    val body: String? = null
) : Exception(message, cause) {

    constructor(code: Int?, body: String?) : this(
        message = "Network error occurred (code: $code)",
        cause = null,
        code = code,
        body = body
    )

    constructor(cause: Throwable, code: Int?, body: String?) : this(
        message = cause.message,
        cause = cause,
        code = code,
        body = body
    )
}
