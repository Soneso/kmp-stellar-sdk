package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when sending a transaction to the network fails.
 *
 * This indicates that the transaction was rejected by the network before being
 * queued for consensus. Common causes include invalid signatures, expired transaction,
 * or network communication errors.
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction that failed to send
 */
class SendTransactionFailedException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
