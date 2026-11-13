package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when automatic contract state restoration fails.
 *
 * When a contract's archived state needs to be restored before invocation,
 * [AssembledTransaction.simulate] automatically attempts restoration. This exception
 * indicates that the restoration transaction failed.
 *
 * The failure of the restoration, such as a [SimulationFailedException] of the restore
 * simulation, is the [cause] when the SDK throws this exception.
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction whose restoration failed
 */
class RestorationFailureException : ContractException {

    /**
     * Creates the exception without a cause.
     */
    constructor(message: String, assembledTransaction: AssembledTransaction<*>) :
        super(message, assembledTransaction)

    /**
     * Creates the exception with the [cause] of the failed restoration.
     */
    constructor(message: String, assembledTransaction: AssembledTransaction<*>, cause: Throwable) :
        super(message, assembledTransaction, cause)
}
