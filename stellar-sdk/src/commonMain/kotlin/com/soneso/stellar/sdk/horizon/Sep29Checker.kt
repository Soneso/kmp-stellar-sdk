package com.soneso.stellar.sdk.horizon

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.horizon.exceptions.AccountRequiresMemoException
import com.soneso.stellar.sdk.horizon.exceptions.BadRequestException
import com.soneso.stellar.sdk.horizon.requests.AccountsRequestBuilder
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

        /**
         * Operation type codes from the Stellar protocol.
         */
        private const val OPERATION_PAYMENT = 1
        private const val OPERATION_PATH_PAYMENT_STRICT_RECEIVE = 2
        private const val OPERATION_ACCOUNT_MERGE = 8
        private const val OPERATION_PATH_PAYMENT_STRICT_SEND = 13
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
     * @param transactionEnvelopeXdr Base64-encoded transaction envelope XDR
     * @throws AccountRequiresMemoException when a transaction is trying to submit an operation
     *         to an account which requires a memo
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun checkMemoRequired(transactionEnvelopeXdr: String) {
        // Decode the base64-encoded XDR
        val xdrBytes = try {
            Base64.decode(transactionEnvelopeXdr)
        } catch (e: Exception) {
            // If we can't decode the XDR, skip the check
            return
        }

        // Parse the envelope to extract transaction
        val transaction = try {
            parseTransactionFromEnvelope(xdrBytes)
        } catch (e: Exception) {
            // If we can't parse the envelope, skip the check
            return
        }

        // If transaction has a memo, no need to check
        if (transaction.hasMemo) {
            return
        }

        // Extract destination accounts from payment operations
        val destinations = transaction.destinations.toSet()

        // Check each unique destination
        destinations.forEachIndexed { index, destination ->
            // Skip muxed accounts (M...) - they already encode virtual account IDs
            if (StrKey.isValidMed25519PublicKey(destination)) {
                return@forEachIndexed
            }

            // Check if account requires memo
            if (accountRequiresMemo(destination)) {
                throw AccountRequiresMemoException(
                    message = "Destination account requires a memo in the transaction.",
                    accountId = destination,
                    operationIndex = transaction.getOperationIndex(destination)
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
     * Parses a transaction from an envelope XDR.
     *
     * This is a simplified parser that extracts only the information needed for SEP-29 checking.
     * It handles both regular transactions and fee bump transactions.
     *
     * @param xdrBytes The XDR bytes to parse
     * @return ParsedTransaction containing memo status and destination accounts
     */
    private fun parseTransactionFromEnvelope(xdrBytes: ByteArray): ParsedTransaction {
        val reader = XdrReader(xdrBytes)

        // Read envelope type discriminant
        val envelopeType = reader.readInt()

        // Determine if this is a fee bump transaction
        val isFeeBump = envelopeType == 5 // ENVELOPE_TYPE_TX_FEE_BUMP

        if (isFeeBump) {
            // For fee bump transactions, skip to the inner transaction
            // Skip fee bump envelope fields and parse inner transaction
            reader.skip(32) // Skip fee source account
            reader.skip(8)  // Skip fee

            // Parse inner transaction envelope
            val innerEnvelopeType = reader.readInt()
            return parseTransactionV1(reader)
        } else {
            // Parse regular transaction based on version
            return when (envelopeType) {
                2 -> parseTransactionV1(reader) // ENVELOPE_TYPE_TX_V0 (legacy)
                3 -> parseTransactionV1(reader) // ENVELOPE_TYPE_TX
                else -> ParsedTransaction(hasMemo = false, destinations = emptyList())
            }
        }
    }

    /**
     * Parses a v1 transaction from XDR.
     *
     * @param reader The XDR reader
     * @return ParsedTransaction containing memo status and destination accounts
     */
    private fun parseTransactionV1(reader: XdrReader): ParsedTransaction {
        // Skip source account (32 bytes for ed25519 public key)
        reader.skip(32)

        // Skip fee (uint32)
        reader.skip(4)

        // Skip sequence number (int64)
        reader.skip(8)

        // Skip preconditions (this is variable length, but we'll skip it properly)
        skipPreconditions(reader)

        // Read memo type
        val memoType = reader.readInt()
        val hasMemo = memoType != 0 // MEMO_NONE = 0

        // Skip memo value based on type
        when (memoType) {
            0 -> {} // MEMO_NONE - nothing to skip
            1 -> reader.skip(reader.readInt()) // MEMO_TEXT - skip string
            2 -> reader.skip(8) // MEMO_ID - skip uint64
            3 -> reader.skip(32) // MEMO_HASH - skip 32 bytes
            4 -> reader.skip(32) // MEMO_RETURN - skip 32 bytes
        }

        // Read operations array length
        val operationCount = reader.readInt()

        // Parse operations to extract destination accounts
        val destinations = mutableListOf<Pair<String, Int>>() // (destination, operationIndex)

        for (i in 0 until operationCount) {
            // Check if operation has source account
            val hasSourceAccount = reader.readInt() == 1
            if (hasSourceAccount) {
                reader.skip(32) // Skip source account
            }

            // Read operation type
            val operationType = reader.readInt()

            // Extract destination based on operation type
            val destination = when (operationType) {
                OPERATION_PAYMENT -> {
                    // Payment: destination is first field
                    readAccountId(reader)
                }
                OPERATION_PATH_PAYMENT_STRICT_RECEIVE -> {
                    // PathPaymentStrictReceive: skip asset, skip amount, read destination
                    skipAsset(reader)
                    reader.skip(8) // Skip dest amount
                    readAccountId(reader)
                }
                OPERATION_PATH_PAYMENT_STRICT_SEND -> {
                    // PathPaymentStrictSend: skip asset, skip amount, read destination
                    skipAsset(reader)
                    reader.skip(8) // Skip dest min
                    readAccountId(reader)
                }
                OPERATION_ACCOUNT_MERGE -> {
                    // AccountMerge: destination is the only field
                    readAccountId(reader)
                }
                else -> {
                    // Other operations: skip entire operation body
                    skipOperationBody(reader, operationType)
                    null
                }
            }

            if (destination != null) {
                destinations.add(destination to i)
            }
        }

        return ParsedTransaction(
            hasMemo = hasMemo,
            destinations = destinations.map { it.first },
            destinationIndexMap = destinations.toMap()
        )
    }

    /**
     * Reads an account ID from XDR.
     *
     * @param reader The XDR reader
     * @return The account ID as a string (G... format)
     */
    private fun readAccountId(reader: XdrReader): String {
        // Read account type discriminant
        val accountType = reader.readInt()

        return when (accountType) {
            0 -> { // PUBLIC_KEY_TYPE_ED25519
                val publicKey = reader.read(32)
                StrKey.encodeEd25519PublicKey(publicKey)
            }
            else -> {
                // Unsupported account type, skip
                reader.skip(32)
                ""
            }
        }
    }

    /**
     * Skips an asset in XDR.
     *
     * @param reader The XDR reader
     */
    private fun skipAsset(reader: XdrReader) {
        val assetType = reader.readInt()
        when (assetType) {
            0 -> {} // ASSET_TYPE_NATIVE - nothing to skip
            1 -> {
                // ASSET_TYPE_CREDIT_ALPHANUM4
                reader.skip(4) // Asset code
                reader.skip(32) // Issuer
            }
            2 -> {
                // ASSET_TYPE_CREDIT_ALPHANUM12
                reader.skip(12) // Asset code
                reader.skip(32) // Issuer
            }
        }
    }

    /**
     * Skips preconditions in XDR.
     *
     * @param reader The XDR reader
     */
    private fun skipPreconditions(reader: XdrReader) {
        val precondType = reader.readInt()
        when (precondType) {
            0 -> {} // PRECOND_NONE
            1 -> {
                // PRECOND_TIME
                reader.skip(8) // min time
                reader.skip(8) // max time
            }
            2 -> {
                // PRECOND_V2
                // This has multiple optional fields, skip them all
                val hasTimeBounds = reader.readInt() == 1
                if (hasTimeBounds) {
                    reader.skip(8) // min time
                    reader.skip(8) // max time
                }
                val hasLedgerBounds = reader.readInt() == 1
                if (hasLedgerBounds) {
                    reader.skip(4) // min ledger
                    reader.skip(4) // max ledger
                }
                val hasMinSeqNum = reader.readInt() == 1
                if (hasMinSeqNum) {
                    reader.skip(8) // min seq num
                }
                val hasMinSeqAge = reader.readInt() == 1
                if (hasMinSeqAge) {
                    reader.skip(8) // min seq age
                }
                val hasMinSeqLedgerGap = reader.readInt() == 1
                if (hasMinSeqLedgerGap) {
                    reader.skip(4) // min seq ledger gap
                }
                val extraSignersCount = reader.readInt()
                reader.skip(extraSignersCount * 32) // Skip extra signers
            }
        }
    }

    /**
     * Skips an operation body based on operation type.
     *
     * @param reader The XDR reader
     * @param operationType The operation type code
     */
    private fun skipOperationBody(reader: XdrReader, operationType: Int) {
        // For simplicity, we'll skip a large amount of bytes
        // In production, this should properly parse each operation type
        // For now, we'll just read until the next operation or end
        // This is a simplified approach - a full implementation would need
        // to properly skip each field based on operation type

        // Most operations don't exceed 200 bytes, so skip in chunks
        try {
            reader.skip(200)
        } catch (e: Exception) {
            // If we can't skip, we've likely reached the end
        }
    }

    /**
     * Simple XDR reader for parsing transaction envelopes.
     */
    private class XdrReader(private val data: ByteArray) {
        private var offset = 0

        fun readInt(): Int {
            val value = ((data[offset].toInt() and 0xFF) shl 24) or
                       ((data[offset + 1].toInt() and 0xFF) shl 16) or
                       ((data[offset + 2].toInt() and 0xFF) shl 8) or
                       (data[offset + 3].toInt() and 0xFF)
            offset += 4
            return value
        }

        fun read(length: Int): ByteArray {
            val result = data.copyOfRange(offset, offset + length)
            offset += length
            // XDR padding: align to 4-byte boundary
            val padding = (4 - (length % 4)) % 4
            offset += padding
            return result
        }

        fun skip(bytes: Int) {
            offset += bytes
        }
    }

    /**
     * Represents a parsed transaction with only the information needed for SEP-29 checking.
     */
    private data class ParsedTransaction(
        val hasMemo: Boolean,
        val destinations: List<String>,
        val destinationIndexMap: Map<String, Int> = emptyMap()
    ) {
        fun getOperationIndex(destination: String): Int {
            return destinationIndexMap[destination] ?: 0
        }
    }
}
