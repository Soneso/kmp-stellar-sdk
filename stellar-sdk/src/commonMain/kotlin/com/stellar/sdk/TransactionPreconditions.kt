package com.stellar.sdk

import com.stellar.sdk.xdr.*

/**
 * Preconditions of a transaction per
 * <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0021.md#specification">CAP-21</a>
 *
 * Transaction preconditions allow you to set constraints on when a transaction can be executed.
 * This includes time bounds, ledger bounds, minimum sequence numbers, and required extra signers.
 *
 * @property timeBounds The time bounds for the transaction
 * @property ledgerBounds The ledger bounds for the transaction (V2 only)
 * @property minSequenceNumber The minimum source account sequence number this transaction is valid for (V2 only).
 *                             If null, the transaction is valid when source account's sequence number == tx.sequence - 1
 * @property minSequenceAge The minimum amount of time (in seconds) between source account sequence time and
 *                          the ledger time when this transaction will become valid (V2 only). 0 means unrestricted
 * @property minSequenceLedgerGap The minimum number of ledgers between source account sequence and the ledger
 *                                number when this transaction will become valid (V2 only). 0 means unrestricted
 * @property extraSigners Required extra signers (V2 only). Maximum of 2 extra signers
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/operations-and-transactions#preconditions">Transaction Preconditions</a>
 */
data class TransactionPreconditions(
    val timeBounds: TimeBounds? = null,
    val ledgerBounds: LedgerBounds? = null,
    val minSequenceNumber: Long? = null,
    val minSequenceAge: Long = 0,
    val minSequenceLedgerGap: Int = 0,
    val extraSigners: List<SignerKey> = emptyList()
) {

    init {
        require(minSequenceAge >= 0) { "minSequenceAge must be >= 0" }
        require(minSequenceLedgerGap >= 0) { "minSequenceLedgerGap must be >= 0" }
        require(extraSigners.size <= MAX_EXTRA_SIGNERS_COUNT) {
            "Invalid preconditions, too many extra signers, can only have up to $MAX_EXTRA_SIGNERS_COUNT, got ${extraSigners.size}"
        }
        if (minSequenceNumber != null) {
            require(minSequenceNumber >= 0) { "minSequenceNumber must be >= 0" }
        }
    }

    /**
     * Validates the preconditions.
     *
     * @throws IllegalStateException if the preconditions are invalid
     */
    fun validate() {
        // Note: Unlike Java SDK, we make timeBounds optional to support PRECOND_NONE
        // The Java SDK always requires timeBounds, but the protocol allows PRECOND_NONE
    }

    /**
     * Returns true if the preconditions use V2 features.
     *
     * V2 preconditions include ledger bounds, minimum sequence number, minimum sequence age,
     * minimum sequence ledger gap, or extra signers.
     *
     * @return true if using V2 preconditions, false otherwise
     */
    fun hasV2(): Boolean {
        return ledgerBounds != null ||
                minSequenceLedgerGap > 0 ||
                minSequenceAge > 0 ||
                minSequenceNumber != null ||
                extraSigners.isNotEmpty()
    }

    /**
     * Converts this TransactionPreconditions to its XDR representation.
     *
     * @return The XDR PreconditionsXdr object
     */
    fun toXdr(): PreconditionsXdr {
        return if (hasV2()) {
            // Use PRECOND_V2
            val v2 = PreconditionsV2Xdr(
                timeBounds = timeBounds?.toXdr(),
                ledgerBounds = ledgerBounds?.toXdr(),
                minSeqNum = minSequenceNumber?.let { SequenceNumberXdr(Int64Xdr(it)) },
                minSeqAge = DurationXdr(Uint64Xdr(minSequenceAge.toULong())),
                minSeqLedgerGap = Uint32Xdr(minSequenceLedgerGap.toUInt()),
                extraSigners = extraSigners.map { it.toXdr() }
            )
            PreconditionsXdr.V2(v2)
        } else {
            // Use PRECOND_TIME or PRECOND_NONE
            if (timeBounds != null) {
                PreconditionsXdr.TimeBounds(timeBounds.toXdr())
            } else {
                PreconditionsXdr.Void
            }
        }
    }

    /**
     * Builder for creating TransactionPreconditions with a fluent API.
     */
    class Builder {
        private var timeBounds: TimeBounds? = null
        private var ledgerBounds: LedgerBounds? = null
        private var minSequenceNumber: Long? = null
        private var minSequenceAge: Long = 0
        private var minSequenceLedgerGap: Int = 0
        private var extraSigners: MutableList<SignerKey> = mutableListOf()

        /**
         * Sets the time bounds.
         *
         * @param timeBounds The time bounds
         * @return This builder
         */
        fun timeBounds(timeBounds: TimeBounds?): Builder {
            this.timeBounds = timeBounds
            return this
        }

        /**
         * Sets the ledger bounds.
         *
         * @param ledgerBounds The ledger bounds
         * @return This builder
         */
        fun ledgerBounds(ledgerBounds: LedgerBounds?): Builder {
            this.ledgerBounds = ledgerBounds
            return this
        }

        /**
         * Sets the minimum sequence number.
         *
         * @param minSequenceNumber The minimum sequence number
         * @return This builder
         */
        fun minSequenceNumber(minSequenceNumber: Long?): Builder {
            this.minSequenceNumber = minSequenceNumber
            return this
        }

        /**
         * Sets the minimum sequence age.
         *
         * @param minSequenceAge The minimum sequence age in seconds
         * @return This builder
         */
        fun minSequenceAge(minSequenceAge: Long): Builder {
            this.minSequenceAge = minSequenceAge
            return this
        }

        /**
         * Sets the minimum sequence ledger gap.
         *
         * @param minSequenceLedgerGap The minimum sequence ledger gap
         * @return This builder
         */
        fun minSequenceLedgerGap(minSequenceLedgerGap: Int): Builder {
            this.minSequenceLedgerGap = minSequenceLedgerGap
            return this
        }

        /**
         * Sets the extra signers.
         *
         * @param extraSigners The list of extra signers
         * @return This builder
         */
        fun extraSigners(extraSigners: List<SignerKey>): Builder {
            this.extraSigners = extraSigners.toMutableList()
            return this
        }

        /**
         * Adds an extra signer.
         *
         * @param signerKey The signer key to add
         * @return This builder
         */
        fun addExtraSigner(signerKey: SignerKey): Builder {
            this.extraSigners.add(signerKey)
            return this
        }

        /**
         * Builds the TransactionPreconditions.
         *
         * @return The TransactionPreconditions object
         */
        fun build(): TransactionPreconditions {
            return TransactionPreconditions(
                timeBounds = timeBounds,
                ledgerBounds = ledgerBounds,
                minSequenceNumber = minSequenceNumber,
                minSequenceAge = minSequenceAge,
                minSequenceLedgerGap = minSequenceLedgerGap,
                extraSigners = extraSigners.toList()
            )
        }
    }

    companion object {
        /**
         * Maximum number of extra signers allowed.
         */
        const val MAX_EXTRA_SIGNERS_COUNT = 2

        /**
         * Constant representing infinite timeout (no maximum time).
         */
        const val TIMEOUT_INFINITE = 0L

        /**
         * Creates a new builder for TransactionPreconditions.
         *
         * @return A new Builder instance
         */
        fun builder(): Builder = Builder()

        /**
         * Creates a new TransactionPreconditions object from a PreconditionsXdr XDR object.
         *
         * @param preconditions The XDR PreconditionsXdr object to convert
         * @return A new TransactionPreconditions object
         */
        fun fromXdr(preconditions: PreconditionsXdr): TransactionPreconditions {
            return when (preconditions) {
                is PreconditionsXdr.Void -> {
                    TransactionPreconditions()
                }
                is PreconditionsXdr.TimeBounds -> {
                    TransactionPreconditions(
                        timeBounds = TimeBounds.fromXdr(preconditions.value)
                    )
                }
                is PreconditionsXdr.V2 -> {
                    val v2 = preconditions.value
                    TransactionPreconditions(
                        timeBounds = v2.timeBounds?.let { TimeBounds.fromXdr(it) },
                        ledgerBounds = v2.ledgerBounds?.let { LedgerBounds.fromXdr(it) },
                        minSequenceNumber = v2.minSeqNum?.value?.value,
                        minSequenceAge = v2.minSeqAge.value.value.toLong(),
                        minSequenceLedgerGap = v2.minSeqLedgerGap.value.toInt(),
                        extraSigners = v2.extraSigners.map { SignerKey.fromXdr(it) }
                    )
                }
            }
        }
    }
}
