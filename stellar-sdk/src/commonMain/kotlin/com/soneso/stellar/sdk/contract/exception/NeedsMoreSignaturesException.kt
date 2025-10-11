package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction

/**
 * Exception thrown when a transaction requires additional authorization signatures.
 *
 * Some contract invocations require authorization from specific accounts. These accounts
 * must sign the authorization entries using [AssembledTransaction.signAuthEntries] before
 * the transaction can be submitted.
 *
 * Use [AssembledTransaction.needsNonInvokerSigningBy] to see which accounts need to sign.
 *
 * @property message The error message (includes list of addresses that need to sign)
 * @property assembledTransaction The AssembledTransaction requiring more signatures
 */
class NeedsMoreSignaturesException(
    message: String,
    assembledTransaction: AssembledTransaction<*>
) : ContractException(message, assembledTransaction)
