package com.stellar.sdk.contract.exception

import com.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when transaction simulation fails.
 *
 * This typically indicates an error in the contract invocation parameters,
 * such as invalid arguments, insufficient authorization, or contract logic errors.
 *
 * Check [assembledTransaction].[AssembledTransaction.simulation].error for details.
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction whose simulation failed
 */
class SimulationFailedException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
