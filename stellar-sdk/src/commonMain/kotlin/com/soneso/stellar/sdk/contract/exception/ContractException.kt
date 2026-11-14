package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction

/**
 * Base class of the exceptions in this package except [ContractClientSimulationException].
 *
 * [com.soneso.stellar.sdk.contract.ContractClient] and [AssembledTransaction] also throw
 * other exceptions, such as [IllegalArgumentException] and [IllegalStateException].
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction that caused the error, or null if none did
 * @property cause The underlying cause, if any
 */
open class ContractException(
    message: String,
    val assembledTransaction: AssembledTransaction<*>? = null,
    cause: Throwable? = null
) : Exception(message, cause)
