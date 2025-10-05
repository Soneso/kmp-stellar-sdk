package com.stellar.sdk.contract.exception

import com.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when contract state has expired and needs restoration.
 *
 * Soroban contracts store state on the ledger with a time-to-live (TTL). When state
 * expires (is archived), it must be restored before the contract can be invoked.
 *
 * To automatically restore state, set `restore=true` when calling
 * [AssembledTransaction.simulate], or manually call [AssembledTransaction.restoreFootprint].
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction with expired state
 */
class ExpiredStateException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
