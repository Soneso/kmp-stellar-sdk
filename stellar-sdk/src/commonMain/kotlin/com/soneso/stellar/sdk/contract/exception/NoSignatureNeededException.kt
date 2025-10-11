package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when attempting to sign a read-only transaction.
 *
 * Read-only contract calls (those without authorization requirements or write operations)
 * do not need to be signed or submitted. Use [AssembledTransaction.result] to get the
 * simulated result directly.
 *
 * To override this check and sign/submit anyway, set `force=true` when calling
 * [AssembledTransaction.sign].
 *
 * @property message The error message
 * @property assembledTransaction The read-only AssembledTransaction
 */
class NoSignatureNeededException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
