package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when a transaction fails during execution.
 *
 * The transaction was submitted to the network and processed, but execution failed.
 * Common causes include contract logic errors, insufficient funds, or invalid state.
 *
 * Check [assembledTransaction].[AssembledTransaction.getTransactionResponse]
 * for detailed error information.
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction that failed
 */
class TransactionFailedException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
