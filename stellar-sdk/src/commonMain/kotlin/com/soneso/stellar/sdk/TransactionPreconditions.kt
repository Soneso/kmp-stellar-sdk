package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.*

/**
 * Preconditions of a transaction per
 * <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0021.md#specification">CAP-21</a>
 *
 * Transaction preconditions allow you to set constraints on when a transaction can be executed.
 * This includes time bounds, ledger bounds, minimum sequence numbers, and required extra signers.
 *
 * A value read by [fromXdr] keeps the precondition type it was read with. A value from the constructor,
 * `copy` or the [Builder] gets its type from its property values, as [hasV2] describes.
 *
 * The minimum sequence age and ledger gap are XDR `uint64` and `uint32` values and are stored exactly. The
 * constructor and `copy` take values from 0 to `Long.MAX_VALUE` and `Int.MAX_VALUE`; [ofUnsigned] and [fromXdr]
 * take the full range. [minSequenceAge], [minSequenceLedgerGap], `copy`, `component4` and `component5` throw
 * [IllegalStateException] above that range; [minSequenceAgeExact] and [minSequenceLedgerGapExact] return every
 * value.
 *
 * @property timeBounds The time bounds for the transaction
 * @property ledgerBounds The ledger bounds for the transaction (V2 only)
 * @property minSequenceNumber The minimum source account sequence number this transaction is valid for (V2 only).
 *                             If null, the transaction is valid when source account's sequence number == tx.sequence - 1.
 *                             Any `int64` value is accepted, as the XDR allows
 * @property minSequenceAgeExact The minimum amount of time (in seconds) between source account sequence time
 *                               and the ledger time when this transaction will become valid (V2 only). 0 means
 *                               unrestricted
 * @property minSequenceLedgerGapExact The minimum number of ledgers between source account sequence and the
 *                                     ledger number when this transaction will become valid (V2 only). 0 means
 *                                     unrestricted
 * @property minSequenceAge [minSequenceAgeExact] as a `Long`
 * @property minSequenceLedgerGap [minSequenceLedgerGapExact] as an `Int`
 * @property extraSigners Required extra signers (V2 only). Maximum of 2 extra signers. A signed payload signer
 *                        needs a payload of 1 to 64 bytes, because the network refuses an empty one
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/operations-and-transactions#preconditions">Transaction Preconditions</a>
 */
class TransactionPreconditions private constructor(
    val timeBounds: TimeBounds?,
    val ledgerBounds: LedgerBounds?,
    val minSequenceNumber: Long?,
    val minSequenceAgeExact: ULong,
    val minSequenceLedgerGapExact: UInt,
    val extraSigners: List<SignerKey>,
    // Sets this constructor apart from the public one, which has the same JVM signature.
    @Suppress("UNUSED_PARAMETER") exact: Unit
) {
    /** @throws IllegalArgumentException if a value is out of its range or an extra signer is invalid */
    constructor(
        timeBounds: TimeBounds? = null,
        ledgerBounds: LedgerBounds? = null,
        minSequenceNumber: Long? = null,
        minSequenceAge: Long = 0,
        minSequenceLedgerGap: Int = 0,
        extraSigners: List<SignerKey> = emptyList()
    ) : this(
        timeBounds, ledgerBounds, minSequenceNumber,
        minSequenceAge.also { require(it >= 0) { "minSequenceAge must be >= 0" } }.toULong(),
        minSequenceLedgerGap.also { require(it >= 0) { "minSequenceLedgerGap must be >= 0" } }.toUInt(),
        extraSigners, Unit
    )

    /** Creates empty preconditions; Java calls it as `new TransactionPreconditions()`. */
    constructor() : this(null, null, null, 0uL, 0u, emptyList(), Unit)

    /** True only for a value that [fromXdr] read from a `PRECOND_V2` precondition. */
    private var decodedAsV2 = false

    val minSequenceAge: Long
        get() = minSequenceAgeExact.also {
            check(it <= Long.MAX_VALUE.toULong()) {
                "minSequenceAge $it exceeds Long.MAX_VALUE; read minSequenceAgeExact"
            }
        }.toLong()

    val minSequenceLedgerGap: Int
        get() = minSequenceLedgerGapExact.also {
            check(it <= Int.MAX_VALUE.toUInt()) {
                "minSequenceLedgerGap $it exceeds Int.MAX_VALUE; read minSequenceLedgerGapExact"
            }
        }.toInt()

    init {
        require(extraSigners.size <= MAX_EXTRA_SIGNERS_COUNT) {
            "Invalid preconditions, too many extra signers, can only have up to $MAX_EXTRA_SIGNERS_COUNT, got ${extraSigners.size}"
        }
        require(extraSigners.none { it is SignerKey.Ed25519SignedPayload && it.payload.isEmpty() }) {
            "Invalid preconditions, an extra signed payload signer needs a payload of 1 to " +
                "${SignerKey.SIGNED_PAYLOAD_MAX_PAYLOAD_LENGTH} bytes, got an empty one"
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
     * Returns true if [toXdr] writes these preconditions as `PRECOND_V2`.
     *
     * That holds for a value that [fromXdr] read from a `PRECOND_V2` precondition, and for a value with
     * ledger bounds, a minimum sequence number, a minimum sequence age or ledger gap above 0, or extra signers.
     *
     * @return true if using V2 preconditions, false otherwise
     */
    fun hasV2(): Boolean {
        return decodedAsV2 ||
                ledgerBounds != null ||
                minSequenceLedgerGapExact > 0u ||
                minSequenceAgeExact > 0uL ||
                minSequenceNumber != null ||
                extraSigners.isNotEmpty()
    }

    /**
     * Converts this TransactionPreconditions to its XDR representation.
     *
     * The type is `PRECOND_V2` when [hasV2] is true, so a value read from a `PRECOND_V2` precondition is
     * written back as `PRECOND_V2`. Otherwise it is `PRECOND_TIME` with time bounds and `PRECOND_NONE` without.
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
                minSeqAge = DurationXdr(Uint64Xdr(minSequenceAgeExact)),
                minSeqLedgerGap = Uint32Xdr(minSequenceLedgerGapExact),
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

    /** Compares the six properties and [hasV2], so two equal values encode to the same precondition bytes. */
    override fun equals(other: Any?): Boolean =
        other is TransactionPreconditions &&
            timeBounds == other.timeBounds &&
            ledgerBounds == other.ledgerBounds &&
            minSequenceNumber == other.minSequenceNumber &&
            minSequenceAgeExact == other.minSequenceAgeExact &&
            minSequenceLedgerGapExact == other.minSequenceLedgerGapExact &&
            extraSigners == other.extraSigners &&
            hasV2() == other.hasV2()

    override fun hashCode(): Int =
        listOf(
            timeBounds, ledgerBounds, minSequenceNumber, minSequenceAgeExact, minSequenceLedgerGapExact, extraSigners,
            hasV2()
        ).hashCode()

    override fun toString(): String =
        "TransactionPreconditions(timeBounds=$timeBounds, ledgerBounds=$ledgerBounds, " +
            "minSequenceNumber=$minSequenceNumber, minSequenceAge=$minSequenceAgeExact, " +
            "minSequenceLedgerGap=$minSequenceLedgerGapExact, extraSigners=$extraSigners)"

    operator fun component1(): TimeBounds? = timeBounds
    operator fun component2(): LedgerBounds? = ledgerBounds
    operator fun component3(): Long? = minSequenceNumber
    operator fun component4(): Long = minSequenceAge
    operator fun component5(): Int = minSequenceLedgerGap
    operator fun component6(): List<SignerKey> = extraSigners

    fun copy(
        timeBounds: TimeBounds? = this.timeBounds,
        ledgerBounds: LedgerBounds? = this.ledgerBounds,
        minSequenceNumber: Long? = this.minSequenceNumber,
        minSequenceAge: Long = this.minSequenceAge,
        minSequenceLedgerGap: Int = this.minSequenceLedgerGap,
        extraSigners: List<SignerKey> = this.extraSigners
    ): TransactionPreconditions = TransactionPreconditions(
        timeBounds, ledgerBounds, minSequenceNumber, minSequenceAge, minSequenceLedgerGap, extraSigners
    )

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
         * Creates preconditions with the exact `uint64` minimum sequence age and `uint32` minimum sequence ledger gap.
         *
         * @throws IllegalArgumentException if an extra signer is invalid
         */
        fun ofUnsigned(
            timeBounds: TimeBounds? = null,
            ledgerBounds: LedgerBounds? = null,
            minSequenceNumber: Long? = null,
            minSequenceAge: ULong = 0uL,
            minSequenceLedgerGap: UInt = 0u,
            extraSigners: List<SignerKey> = emptyList()
        ): TransactionPreconditions = TransactionPreconditions(
            timeBounds, ledgerBounds, minSequenceNumber, minSequenceAge, minSequenceLedgerGap, extraSigners, Unit
        )

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
                    ofUnsigned(
                        timeBounds = v2.timeBounds?.let { TimeBounds.fromXdr(it) },
                        ledgerBounds = v2.ledgerBounds?.let { LedgerBounds.fromXdr(it) },
                        minSequenceNumber = v2.minSeqNum?.value?.value,
                        minSequenceAge = v2.minSeqAge.value.value,
                        minSequenceLedgerGap = v2.minSeqLedgerGap.value,
                        extraSigners = v2.extraSigners.map { SignerKey.fromXdr(it) }
                    ).apply { decodedAsV2 = true }
                }
            }
        }
    }
}
