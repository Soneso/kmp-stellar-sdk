package com.stellar.sdk.contract.exception

import com.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when attempting an operation that requires simulation before the
 * transaction has been simulated.
 *
 * Operations like signing, submitting, or checking authorization requirements all
 * require that [AssembledTransaction.simulate] be called first.
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction that has not yet been simulated
 */
class NotYetSimulatedException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
