package com.stellar.sdk

import com.stellar.sdk.xdr.*

/**
 * Represents a transaction in the Stellar network.
 *
 * A transaction is a grouping of operations that are executed together atomically. All operations
 * must succeed for the transaction to succeed. If any operation fails, all effects are rolled back.
 *
 * ## Transaction Structure
 *
 * A transaction consists of:
 * - **Source Account**: The account that originates the transaction and pays the fee
 * - **Fee**: Total fee in stroops (baseFee * operations.size)
 * - **Sequence Number**: Must be exactly sourceAccount.sequence + 1
 * - **Operations**: List of 1-100 operations to execute
 * - **Memo**: Optional memo (up to 28 bytes for text memos)
 * - **Preconditions**: Optional time bounds, ledger bounds, etc.
 * - **Signatures**: List of signatures authorizing the transaction
 *
 * ## Building Transactions
 *
 * Use [TransactionBuilder] to construct transactions:
 *
 * ```kotlin
 * val sourceAccount = Account("GABC...", 1234567890)
 * val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
 *     .addOperation(
 *         PaymentOperation(
 *             destination = "GDEF...",
 *             asset = AssetTypeNative,
 *             amount = "10.0"
 *         )
 *     )
 *     .addMemo(MemoText("Payment for services"))
 *     .setTimeout(300) // 5 minutes
 *     .setBaseFee(100)
 *     .build()
 *
 * // Sign with source account keypair
 * transaction.sign(sourceKeypair)
 *
 * // Submit to Horizon
 * val response = horizonServer.submitTransaction(transaction)
 * ```
 *
 * ## Fee Calculation
 *
 * The transaction fee is calculated as: `baseFee * operations.size`
 *
 * The minimum base fee is 100 stroops (0.00001 XLM), so:
 * - 1 operation: 100 stroops = 0.00001 XLM
 * - 10 operations: 1,000 stroops = 0.0001 XLM
 * - 100 operations: 10,000 stroops = 0.001 XLM
 *
 * ## Sequence Numbers
 *
 * Each account has a sequence number that must be incremented with each transaction. The transaction's
 * sequence number must be exactly `sourceAccount.sequence + 1`. After successful execution, the account's
 * sequence number becomes the transaction's sequence number.
 *
 * @property sourceAccount The source account for this transaction (G... or M... address)
 * @property fee Total transaction fee in stroops
 * @property sequenceNumber The sequence number for this transaction
 * @property operations List of operations (1-100)
 * @property memo Transaction memo (default MemoNone)
 * @property preconditions Transaction preconditions (time/ledger bounds, etc.)
 * @property sorobanData Soroban transaction data for smart contract operations (optional)
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions">Transactions</a>
 * @see TransactionBuilder
 */
class Transaction internal constructor(
    val sourceAccount: String,
    val fee: Long,
    val sequenceNumber: Long,
    val operations: List<Operation>,
    val memo: Memo,
    val preconditions: TransactionPreconditions,
    val sorobanData: SorobanTransactionDataXdr?,
    network: Network
) : AbstractTransaction(network) {

    // Internal envelope type for backward compatibility (defaults to V1)
    internal var envelopeType: EnvelopeTypeXdr = EnvelopeTypeXdr.ENVELOPE_TYPE_TX

    init {
        require(operations.isNotEmpty()) {
            "At least one operation required"
        }
        require(operations.size <= MAX_OPERATIONS) {
            "Maximum $MAX_OPERATIONS operations allowed, got ${operations.size}"
        }
        require(fee >= 0) {
            "Fee must be non-negative, got $fee"
        }
        require(sequenceNumber >= 0) {
            "Sequence number must be non-negative, got $sequenceNumber"
        }
    }

    /**
     * Returns the time bounds defined for the transaction.
     *
     * @return TimeBounds or null if no time bounds are set
     */
    fun getTimeBounds(): TimeBounds? = preconditions.timeBounds

    /**
     * Returns true if this is a Soroban transaction.
     *
     * A Soroban transaction has exactly one operation that is one of:
     * - InvokeHostFunctionOperation
     * - ExtendFootprintTTLOperation
     * - RestoreFootprintOperation
     *
     * @return true if this is a Soroban transaction
     */
    fun isSorobanTransaction(): Boolean {
        if (operations.size != 1) {
            return false
        }
        // TODO: Check operation types once they're implemented
        // For now, check if sorobanData is present
        return sorobanData != null
    }

    /**
     * Returns the signature base - the data that must be signed.
     *
     * The signature base consists of:
     * - Network ID hash (32 bytes)
     * - Transaction envelope type (ENVELOPE_TYPE_TX)
     * - Transaction XDR
     *
     * @return The signature base bytes
     */
    override fun signatureBase(): ByteArray {
        val taggedTransaction = TransactionSignaturePayloadTaggedTransactionXdr.Tx(
            value = toV1Xdr()
        )
        return getTransactionSignatureBase(taggedTransaction, network)
    }

    /**
     * Converts this transaction to V0 XDR format (legacy).
     *
     * V0 transactions only support Ed25519 public keys (not muxed accounts) and only time bounds.
     *
     * @return The TransactionV0Xdr object
     */
    internal fun toV0Xdr(): TransactionV0Xdr {
        // Extract Ed25519 public key from source account
        val sourceAccountEd25519 = run {
            val muxedAccount = MuxedAccount(sourceAccount)
            val accountId = muxedAccount.ed25519AccountId
            val publicKey = StrKey.decodeEd25519PublicKey(accountId)
            Uint256Xdr(publicKey)
        }

        return TransactionV0Xdr(
            sourceAccountEd25519 = sourceAccountEd25519,
            fee = Uint32Xdr(fee.toUInt()),
            seqNum = SequenceNumberXdr(Int64Xdr(sequenceNumber)),
            timeBounds = preconditions.timeBounds?.toXdr(),
            memo = memo.toXdr(),
            operations = operations.map { it.toXdr() },
            ext = TransactionV0ExtXdr.Void
        )
    }

    /**
     * Converts this transaction to V1 XDR format (current).
     *
     * V1 transactions support muxed accounts and all precondition types.
     *
     * @return The TransactionXdr object
     */
    internal fun toV1Xdr(): TransactionXdr {
        val ext = if (sorobanData != null) {
            TransactionExtXdr.SorobanData(sorobanData)
        } else {
            TransactionExtXdr.Void
        }

        return TransactionXdr(
            sourceAccount = MuxedAccount(sourceAccount).toXdr(),
            fee = Uint32Xdr(fee.toUInt()),
            seqNum = SequenceNumberXdr(Int64Xdr(sequenceNumber)),
            cond = preconditions.toXdr(),
            memo = memo.toXdr(),
            operations = operations.map { it.toXdr() },
            ext = ext
        )
    }

    /**
     * Converts this transaction to its XDR envelope representation.
     *
     * The envelope includes the transaction data and all signatures.
     *
     * @return The TransactionEnvelopeXdr object
     */
    override fun toEnvelopeXdr(): TransactionEnvelopeXdr {
        val decoratedSignatures = signatures.map { it.toXdr() }

        return when (envelopeType) {
            EnvelopeTypeXdr.ENVELOPE_TYPE_TX -> {
                TransactionEnvelopeXdr.V1(
                    TransactionV1EnvelopeXdr(
                        tx = toV1Xdr(),
                        signatures = decoratedSignatures
                    )
                )
            }
            EnvelopeTypeXdr.ENVELOPE_TYPE_TX_V0 -> {
                TransactionEnvelopeXdr.V0(
                    TransactionV0EnvelopeXdr(
                        tx = toV0Xdr(),
                        signatures = decoratedSignatures
                    )
                )
            }
            else -> {
                throw IllegalStateException("Invalid envelope type: $envelopeType")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Transaction

        return signatureBase().contentEquals(other.signatureBase())
    }

    override fun hashCode(): Int {
        return signatureBase().contentHashCode()
    }

    override fun toString(): String {
        return "Transaction(hash=${hashHex()}, sourceAccount=$sourceAccount, fee=$fee, " +
                "sequenceNumber=$sequenceNumber, operations=${operations.size}, " +
                "signatures=${signatures.size})"
    }

    companion object {
        /**
         * Maximum number of operations allowed in a single transaction.
         */
        const val MAX_OPERATIONS = 100

        /**
         * Creates a Transaction from a V0 transaction envelope.
         *
         * V0 envelopes use Ed25519 public keys (not muxed accounts).
         *
         * @param envelope The V0 envelope XDR
         * @param network The network this transaction is for
         * @return The Transaction instance
         */
        internal fun fromV0EnvelopeXdr(envelope: TransactionV0EnvelopeXdr, network: Network): Transaction {
            val tx = envelope.tx

            // Convert Ed25519 public key to account ID
            val sourceAccount = StrKey.encodeEd25519PublicKey(tx.sourceAccountEd25519.value)

            // Parse operations
            val operations = tx.operations.map { Operation.fromXdr(it) }

            // V0 only supports time bounds, not full preconditions
            val preconditions = TransactionPreconditions(
                timeBounds = tx.timeBounds?.let { TimeBounds.fromXdr(it) }
            )

            val transaction = Transaction(
                sourceAccount = sourceAccount,
                fee = tx.fee.value.toLong(),
                sequenceNumber = tx.seqNum.value.value,
                operations = operations,
                memo = Memo.fromXdr(tx.memo),
                preconditions = preconditions,
                sorobanData = null,
                network = network
            )

            // Set envelope type to V0 for proper serialization
            transaction.envelopeType = EnvelopeTypeXdr.ENVELOPE_TYPE_TX_V0

            // Add signatures
            envelope.signatures.forEach { sig ->
                transaction.signatures.add(DecoratedSignature.fromXdr(sig))
            }

            return transaction
        }

        /**
         * Creates a Transaction from a V1 transaction envelope.
         *
         * V1 envelopes support muxed accounts and full preconditions.
         *
         * @param envelope The V1 envelope XDR
         * @param network The network this transaction is for
         * @return The Transaction instance
         */
        internal fun fromV1EnvelopeXdr(envelope: TransactionV1EnvelopeXdr, network: Network): Transaction {
            val tx = envelope.tx

            // Parse source account (may be muxed)
            val sourceAccount = MuxedAccount.fromXdr(tx.sourceAccount).accountId

            // Parse operations
            val operations = tx.operations.map { Operation.fromXdr(it) }

            // Parse Soroban data if present
            val sorobanData = when (tx.ext) {
                is TransactionExtXdr.SorobanData -> tx.ext.value
                else -> null
            }

            val transaction = Transaction(
                sourceAccount = sourceAccount,
                fee = tx.fee.value.toLong(),
                sequenceNumber = tx.seqNum.value.value,
                operations = operations,
                memo = Memo.fromXdr(tx.memo),
                preconditions = TransactionPreconditions.fromXdr(tx.cond),
                sorobanData = sorobanData,
                network = network
            )

            // V1 envelope type is the default
            transaction.envelopeType = EnvelopeTypeXdr.ENVELOPE_TYPE_TX

            // Add signatures
            envelope.signatures.forEach { sig ->
                transaction.signatures.add(DecoratedSignature.fromXdr(sig))
            }

            return transaction
        }

        /**
         * Creates a Transaction from a base64-encoded transaction envelope XDR.
         *
         * @param envelope The base64-encoded envelope
         * @param network The network this transaction is for
         * @return The Transaction instance
         * @throws IllegalArgumentException if the envelope is malformed
         */
        fun fromEnvelopeXdr(envelope: String, network: Network): Transaction {
            val abstractTx = AbstractTransaction.fromEnvelopeXdr(envelope, network)
            if (abstractTx !is Transaction) {
                throw IllegalArgumentException("Envelope does not contain a Transaction")
            }
            return abstractTx
        }

        /**
         * Creates a Transaction from transaction envelope XDR bytes.
         *
         * @param envelopeBytes The XDR envelope bytes
         * @param network The network this transaction is for
         * @return The Transaction instance
         * @throws IllegalArgumentException if the envelope is malformed
         */
        fun fromEnvelopeXdr(envelopeBytes: ByteArray, network: Network): Transaction {
            val abstractTx = AbstractTransaction.fromEnvelopeXdr(envelopeBytes, network)
            if (abstractTx !is Transaction) {
                throw IllegalArgumentException("Envelope does not contain a Transaction")
            }
            return abstractTx
        }
    }
}
