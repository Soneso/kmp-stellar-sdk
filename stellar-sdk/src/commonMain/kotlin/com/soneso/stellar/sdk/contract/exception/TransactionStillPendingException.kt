package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when a transaction is still pending after the timeout period.
 *
 * The transaction was successfully sent to the network but did not complete within
 * the configured timeout. The transaction may still complete later.
 *
 * Check [assembledTransaction].[AssembledTransaction.sendTransactionResponse].hash
 * to manually poll for the transaction result.
 *
 * @property message The error message (includes timeout duration)
 * @property assembledTransaction The AssembledTransaction that timed out
 */
class TransactionStillPendingException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
