package com.stellar.sdk.contract.exception

import com.stellar.sdk.contract.AssembledTransaction

/**
 * Base exception class for all contract-related errors.
 *
 * All exceptions thrown by [com.stellar.sdk.contract.ContractClient] and
 * [com.stellar.sdk.contract.AssembledTransaction] extend this class.
 *
 * The exception retains a reference to the [AssembledTransaction] that caused
 * the error, allowing access to transaction state for debugging.
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction that caused the error
 * @property cause The underlying cause, if any
 */
open class ContractException(
    message: String,
    val assembledTransaction: AssembledTransaction<*>? = null,
    cause: Throwable? = null
) : Exception(message, cause)
