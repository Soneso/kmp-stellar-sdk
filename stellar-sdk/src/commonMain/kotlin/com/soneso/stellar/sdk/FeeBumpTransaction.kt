package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.*
import kotlin.io.encoding.Base64

/**
 * Represents a [Fee Bump Transaction](https://github.com/stellar/stellar-protocol/blob/master/core/cap-0015.md) in the Stellar network.
 *
 * Fee bump transactions allow you to increase the fee of a previously submitted transaction
 * that may be stuck in the queue due to insufficient fees. This is useful when network fees
 * spike unexpectedly or when you need to prioritize a transaction.
 *
 * ## How Fee Bumps Work
 *
 * A fee bump transaction wraps an existing transaction (the "inner transaction") and specifies
 * a new, higher fee. The fee bump transaction has its own source account (the "fee source")
 * which pays the additional fee. This allows a different account to sponsor the fee increase.
 *
 * ## Important Rules
 *
 * 1. The inner transaction must NOT already be a fee bump transaction
 * 2. The new fee must be higher than the inner transaction's fee
 * 3. The inner transaction can have any number of operations (1-100)
 * 4. The fee bump adds one additional "operation" for fee calculation purposes
 * 5. Both the inner transaction and fee bump transaction require signatures
 *
 * ## Fee Calculation
 *
 * When using base fee (recommended):
 * ```
 * maxFee = (baseFee × (numInnerOperations + 1)) + sorobanResourceFee
 * ```
 *
 * The "+1" accounts for the fee bump operation itself.
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Original transaction stuck due to low fee
 * val originalTx = TransactionBuilder(sourceAccount, Network.PUBLIC)
 *     .addOperation(PaymentOperation(...))
 *     .setBaseFee(100)
 *     .build()
 * originalTx.sign(sourceKeypair)
 *
 * // Create fee bump to increase priority
 * val feeBump = FeeBumpTransaction.createWithBaseFee(
 *     feeSource = "GFEE...",  // Account paying the additional fee
 *     baseFee = 1000,          // New base fee (must be higher)
 *     innerTransaction = originalTx
 * )
 *
 * // Sign with fee source account
 * feeBump.sign(feeSourceKeypair)
 *
 * // Submit the fee bump transaction
 * horizonServer.submitTransaction(feeBump)
 * ```
 *
 * ## Network Behavior
 *
 * - If the inner transaction has already been applied, the fee bump will fail
 * - If the inner transaction is in the queue, it will be replaced by the fee bump
 * - The fee source account must have sufficient balance to cover the fee increase
 *
 * @property feeSource The account paying for the transaction fee (G... or M... address)
 * @property fee The maximum fee willing to be paid for this transaction (in stroops)
 * @property innerTransaction The inner transaction being wrapped
 *
 * @see <a href="https://developers.stellar.org/docs/encyclopedia/fee-bump-transactions">Fee-bump Transactions</a>
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0015.md">CAP-15: Fee-Bump Transactions</a>
 */
class FeeBumpTransaction private constructor(
    val feeSource: String,
    val fee: Long,
    val innerTransaction: Transaction,
    network: Network
) : AbstractTransaction(network) {

    init {
        require(
            StrKey.isValidEd25519PublicKey(feeSource) ||
            StrKey.isValidMed25519PublicKey(feeSource)
        ) {
            "Invalid fee source account: $feeSource"
        }
        require(fee >= 0) {
            "Fee must be non-negative, got $fee"
        }
        require(fee >= innerTransaction.fee) {
            "Fee bump fee ($fee) must be greater than or equal to inner transaction fee (${innerTransaction.fee})"
        }
    }

    /**
     * Converts this fee bump transaction to its XDR representation.
     *
     * @return The XDR FeeBumpTransaction object
     */
    private fun toXdr(): FeeBumpTransactionXdr {
        val v1Envelope = TransactionV1EnvelopeXdr(
            tx = innerTransaction.toV1Xdr(),
            signatures = innerTransaction.signatures.map { it.toXdr() }
        )

        val innerXdr = FeeBumpTransactionInnerTxXdr.V1(v1Envelope)

        return FeeBumpTransactionXdr(
            feeSource = MuxedAccount(feeSource).toXdr(),
            fee = Int64Xdr(fee),
            innerTx = innerXdr,
            ext = FeeBumpTransactionExtXdr.Void
        )
    }

    /**
     * Returns the signature base for this fee bump transaction.
     *
     * The signature base is the data that must be signed by the fee source account.
     * It includes the network ID and the transaction data.
     *
     * @return The bytes to sign
     */
    override fun signatureBase(): ByteArray {
        val taggedTransaction = TransactionSignaturePayloadTaggedTransactionXdr.FeeBump(
            toXdr()
        )
        return getTransactionSignatureBase(taggedTransaction, network)
    }

    /**
     * Generates the transaction envelope XDR object for submission to the network.
     *
     * @return The XDR TransactionEnvelope containing this fee bump transaction and its signatures
     */
    override fun toEnvelopeXdr(): TransactionEnvelopeXdr {
        val feeBumpEnvelope = FeeBumpTransactionEnvelopeXdr(
            tx = toXdr(),
            signatures = signatures.map { it.toXdr() }
        )

        return TransactionEnvelopeXdr.FeeBump(feeBumpEnvelope)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FeeBumpTransaction

        return signatureBase().contentEquals(other.signatureBase())
    }

    override fun hashCode(): Int {
        return signatureBase().contentHashCode()
    }

    companion object {
        /**
         * Creates a new FeeBumpTransaction with an exact fee amount.
         *
         * This method allows you to specify the exact total fee for the transaction.
         * Generally, it's better to use [createWithBaseFee] which calculates the fee
         * based on the number of operations.
         *
         * @param feeSource The account paying for the transaction fee (G... or M... address)
         * @param fee The maximum fee willing to pay for this transaction (in stroops)
         * @param innerTransaction The inner transaction to wrap
         * @return A new FeeBumpTransaction
         * @throws IllegalArgumentException if the fee is less than the inner transaction's fee
         */
        fun createWithFee(
            feeSource: String,
            fee: Long,
            innerTransaction: Transaction
        ): FeeBumpTransaction {
            return FeeBumpTransaction(feeSource, fee, innerTransaction, innerTransaction.network)
        }

        /**
         * Creates a new FeeBumpTransaction with a base fee per operation.
         *
         * This is the recommended way to create fee bump transactions. The total fee is
         * calculated as:
         * ```
         * maxFee = (baseFee × (numInnerOperations + 1)) + sorobanResourceFee
         * ```
         *
         * The "+1" accounts for the fee bump operation itself.
         *
         * ## Validation
         *
         * - The base fee must be at least [MIN_BASE_FEE] (100 stroops)
         * - The base fee must be higher than or equal to the inner transaction's base fee
         * - The calculated total fee must not overflow a 64-bit signed integer
         *
         * ## V0 Transaction Conversion
         *
         * If the inner transaction is a V0 envelope, it will be automatically converted
         * to a V1 envelope (required for fee bumps).
         *
         * @param feeSource The account paying for the transaction fee (G... or M... address)
         * @param baseFee The maximum fee willing to pay per operation in the inner transaction (in stroops)
         * @param innerTransaction The inner transaction to wrap
         * @return A new FeeBumpTransaction
         * @throws IllegalArgumentException if baseFee is invalid or results in fee overflow
         */
        fun createWithBaseFee(
            feeSource: String,
            baseFee: Long,
            innerTransaction: Transaction
        ): FeeBumpTransaction {
            require(baseFee >= MIN_BASE_FEE) {
                "baseFee cannot be smaller than MIN_BASE_FEE ($MIN_BASE_FEE): $baseFee"
            }

            // Calculate inner transaction's base fee (excluding Soroban resource fee)
            val innerSorobanResourceFee = innerTransaction.sorobanData?.resourceFee?.value ?: 0L
            val innerBaseFee = innerTransaction.fee - innerSorobanResourceFee
            val numOperations = innerTransaction.operations.size

            val actualInnerBaseFee = if (numOperations > 0) {
                // Calculate ceiling division to get per-operation base fee
                (innerBaseFee + numOperations - 1) / numOperations
            } else {
                innerBaseFee
            }

            require(baseFee >= actualInnerBaseFee) {
                "Base fee ($baseFee) cannot be lower than inner transaction base fee ($actualInnerBaseFee)"
            }

            // Calculate total fee: (baseFee * (operations + 1)) + sorobanResourceFee
            // The +1 accounts for the fee bump operation itself
            val calculatedFee = (baseFee * (numOperations + 1)) + innerSorobanResourceFee

            require(calculatedFee >= 0) {
                "Fee calculation overflow: baseFee=$baseFee, operations=$numOperations, sorobanFee=$innerSorobanResourceFee"
            }

            // Convert V0 transactions to V1 if needed
            val tx = if (innerTransaction.envelopeType == EnvelopeTypeXdr.ENVELOPE_TYPE_TX_V0) {
                // Rebuild as V1 transaction
                val account = Account(innerTransaction.sourceAccount, innerTransaction.sequenceNumber - 1)
                val rebuiltTx = TransactionBuilder(account, innerTransaction.network)
                    .setBaseFee(innerTransaction.fee)
                    .apply {
                        innerTransaction.operations.forEach { addOperation(it) }
                    }
                    .addMemo(innerTransaction.memo)
                    .addPreconditions(
                        TransactionPreconditions(
                            timeBounds = innerTransaction.preconditions.timeBounds,
                            ledgerBounds = null,
                            minSequenceNumber = null,
                            minSequenceAge = 0,
                            minSequenceLedgerGap = 0,
                            extraSigners = emptyList()
                        )
                    )
                    .build()

                // Copy signatures from original transaction
                rebuiltTx.signatures.addAll(innerTransaction.signatures)
                rebuiltTx
            } else {
                innerTransaction
            }

            return FeeBumpTransaction(feeSource, calculatedFee, tx, tx.network)
        }

        /**
         * Decodes a FeeBumpTransaction from its XDR envelope representation.
         *
         * @param envelope The XDR FeeBumpTransactionEnvelope
         * @param network The network this transaction is for
         * @return The decoded FeeBumpTransaction with signatures
         */
        fun fromFeeBumpTransactionEnvelope(
            envelope: FeeBumpTransactionEnvelopeXdr,
            network: Network
        ): FeeBumpTransaction {
            val innerTx = when (val inner = envelope.tx.innerTx) {
                is FeeBumpTransactionInnerTxXdr.V1 -> {
                    Transaction.fromV1EnvelopeXdr(inner.value, network)
                }
            }

            val feeSource = MuxedAccount.fromXdr(envelope.tx.feeSource).address
            val fee = envelope.tx.fee.value

            val feeBump = FeeBumpTransaction(feeSource, fee, innerTx, network)

            // Add signatures from envelope
            envelope.signatures.forEach { xdrSig ->
                feeBump.signatures.add(com.soneso.stellar.sdk.DecoratedSignature.fromXdr(xdrSig))
            }

            return feeBump
        }

        /**
         * Decodes a FeeBumpTransaction from a base64-encoded XDR envelope.
         *
         * @param envelope The base64-encoded XDR envelope
         * @param network The network this transaction is for
         * @return The decoded FeeBumpTransaction
         * @throws IllegalArgumentException if the envelope is not a fee bump transaction
         */
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        fun fromEnvelopeXdrBase64(envelope: String, network: Network): FeeBumpTransaction {
            val bytes = Base64.decode(envelope)
            val reader = XdrReader(bytes)
            val xdr = TransactionEnvelopeXdr.decode(reader)

            return when (xdr) {
                is TransactionEnvelopeXdr.FeeBump -> fromFeeBumpTransactionEnvelope(xdr.value, network)
                else -> throw IllegalArgumentException("Envelope is not a fee bump transaction")
            }
        }
    }
}
