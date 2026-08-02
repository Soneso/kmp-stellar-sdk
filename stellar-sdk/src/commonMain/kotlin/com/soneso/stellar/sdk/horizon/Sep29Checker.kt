package com.soneso.stellar.sdk.horizon

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.horizon.exceptions.AccountRequiresMemoException
import com.soneso.stellar.sdk.horizon.exceptions.BadRequestException
import com.soneso.stellar.sdk.horizon.requests.AccountsRequestBuilder
import com.soneso.stellar.sdk.isFatal
import com.soneso.stellar.sdk.xdr.FeeBumpTransactionInnerTxXdr
import com.soneso.stellar.sdk.xdr.MemoXdr
import com.soneso.stellar.sdk.xdr.MuxedAccountXdr
import com.soneso.stellar.sdk.xdr.OperationBodyXdr
import com.soneso.stellar.sdk.xdr.OperationXdr
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import io.ktor.client.*
import io.ktor.http.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * SEP-29 memo required checker.
 *
 * This class implements the memo required check as defined in SEP-0029.
 * It examines transaction operations and validates that accounts requiring memos
 * have a memo present in the transaction.
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md">SEP-0029</a>
 */
internal class Sep29Checker(
    private val httpClient: HttpClient,
    private val serverUri: Url
) {
    companion object {
        /**
         * ACCOUNT_REQUIRES_MEMO_VALUE is the base64 encoding of "1".
         * SEP-29 uses this value to define transaction memo requirements for incoming payments.
         */
        private const val ACCOUNT_REQUIRES_MEMO_VALUE = "MQ=="

        /**
         * ACCOUNT_REQUIRES_MEMO_KEY is the data key name described in SEP-29.
         */
        private const val ACCOUNT_REQUIRES_MEMO_KEY = "config.memo_required"
    }

    /**
     * Checks if a transaction envelope XDR contains operations that require memos.
     *
     * This method performs the following checks:
     * 1. Decodes the transaction envelope XDR
     * 2. Checks if a memo is present
     * 3. If no memo, extracts destination accounts from payment operations
     * 4. For each unique destination, checks if the account requires a memo
     * 5. Throws AccountRequiresMemoException if a memo is required but not present
     *
     * Envelopes that cannot be decoded are skipped without raising an error, so a malformed
     * envelope fails at submission rather than in the check.
     *
     * @param transactionEnvelopeXdr Base64-encoded transaction envelope XDR
     * @throws AccountRequiresMemoException when a transaction is trying to submit an operation
     *         to an account which requires a memo
     */
    suspend fun checkMemoRequired(transactionEnvelopeXdr: String) {
        val transaction = decodeTransaction(transactionEnvelopeXdr) ?: return

        // A transaction carrying a memo already satisfies SEP-29
        if (transaction.memo !is MemoXdr.Void) {
            return
        }

        for ((destination, operationIndex) in destinationsToCheck(transaction.operations)) {
            if (accountRequiresMemo(destination)) {
                throw AccountRequiresMemoException(
                    message = "Destination account requires a memo in the transaction.",
                    accountId = destination,
                    operationIndex = operationIndex
                )
            }
        }
    }

    /**
     * Checks if an account requires a memo by querying the account data.
     *
     * @param accountId The account ID to check
     * @return true if the account requires a memo, false otherwise
     */
    private suspend fun accountRequiresMemo(accountId: String): Boolean {
        return try {
            val accountsRequestBuilder = AccountsRequestBuilder(httpClient, serverUri)
            val account = accountsRequestBuilder.account(accountId)

            // Check if the account has the memo_required data entry set to "1"
            account.data[ACCOUNT_REQUIRES_MEMO_KEY] == ACCOUNT_REQUIRES_MEMO_VALUE
        } catch (e: BadRequestException) {
            // If account doesn't exist (404), it doesn't require a memo
            if (e.code == 404) {
                false
            } else {
                throw e
            }
        }
    }

    /**
     * Decodes the transaction whose operations are subject to the SEP-29 check.
     *
     * Fee bump envelopes carry no memo and no operations of their own, so the inner
     * transaction is returned for them.
     *
     * @param transactionEnvelopeXdr Base64-encoded transaction envelope XDR
     * @return The transaction to inspect, or null when the envelope cannot be decoded
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeTransaction(transactionEnvelopeXdr: String): CheckedTransaction? {
        val envelope = try {
            TransactionEnvelopeXdr.decode(XdrReader(Base64.decode(transactionEnvelopeXdr)))
        } catch (e: Throwable) {
            if (isFatal(e)) throw e
            return null
        }

        return when (envelope) {
            is TransactionEnvelopeXdr.V0 -> CheckedTransaction(
                memo = envelope.value.tx.memo,
                operations = envelope.value.tx.operations
            )
            is TransactionEnvelopeXdr.V1 -> CheckedTransaction(
                memo = envelope.value.tx.memo,
                operations = envelope.value.tx.operations
            )
            is TransactionEnvelopeXdr.FeeBump -> when (val inner = envelope.value.tx.innerTx) {
                is FeeBumpTransactionInnerTxXdr.V1 -> CheckedTransaction(
                    memo = inner.value.tx.memo,
                    operations = inner.value.tx.operations
                )
            }
        }
    }

    /**
     * Collects the destination accounts that SEP-29 applies to, mapped to the index of the
     * operation that first names them.
     *
     * Only operations that move value to a destination account carry such a destination.
     * Muxed destinations are left out: an M... address already encodes the virtual account id
     * that a memo would otherwise convey.
     *
     * @param operations The operations of the transaction under inspection
     * @return Destination account IDs in order of first appearance, each with its operation index
     */
    private fun destinationsToCheck(operations: List<OperationXdr>): Map<String, Int> {
        val destinations = mutableMapOf<String, Int>()
        operations.forEachIndexed { index, operation ->
            val destination = when (val body = operation.body) {
                is OperationBodyXdr.PaymentOp -> body.value.destination
                is OperationBodyXdr.PathPaymentStrictReceiveOp -> body.value.destination
                is OperationBodyXdr.PathPaymentStrictSendOp -> body.value.destination
                is OperationBodyXdr.Destination -> body.value
                else -> null
            }
            if (destination is MuxedAccountXdr.Ed25519) {
                val accountId = StrKey.encodeEd25519PublicKey(destination.value.value)
                destinations.getOrPut(accountId) { index }
            }
        }
        return destinations
    }

    /**
     * The parts of a transaction that the SEP-29 check reads.
     */
    private data class CheckedTransaction(
        val memo: MemoXdr,
        val operations: List<OperationXdr>
    )
}
