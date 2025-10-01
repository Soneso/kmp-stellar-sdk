package com.stellar.sdk.horizon.exceptions

/**
 * Base exception class for SDK-specific errors that are not network-related.
 *
 * This exception is thrown for SDK-level validation errors, such as when
 * an account requires a memo (SEP-0029) or other business logic violations.
 */
open class SdkException(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)
