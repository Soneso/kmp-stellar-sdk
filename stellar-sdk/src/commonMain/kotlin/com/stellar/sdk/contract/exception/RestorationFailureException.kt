package com.stellar.sdk.contract.exception

import com.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when automatic contract state restoration fails.
 *
 * When a contract's archived state needs to be restored before invocation,
 * [AssembledTransaction.simulate] automatically attempts restoration. This exception
 * indicates that the restoration transaction failed.
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction whose restoration failed
 */
class RestorationFailureException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
