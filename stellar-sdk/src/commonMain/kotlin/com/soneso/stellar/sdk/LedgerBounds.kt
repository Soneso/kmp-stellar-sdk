package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.LedgerBoundsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr

/**
 * LedgerBounds are Preconditions of a transaction per
 * <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0021.md#specification">CAP-21</a>
 *
 * Represents constraints on the ledger number at which a transaction is valid.
 *
 * Both values are XDR `uint32` values and are stored exactly. The constructor takes values from 0 to
 * `Int.MAX_VALUE`; [ofUnsigned] and [fromXdr] take the full `uint32` range, which a transaction read
 * from XDR can carry, for example a `maxLedger` of 2^32-1. [minLedger], [maxLedger], `copy` and the
 * `componentN` functions throw [IllegalStateException] for a value above `Int.MAX_VALUE`;
 * [minLedgerExact] and [maxLedgerExact] return every value.
 *
 * @property minLedgerExact Minimum ledger sequence number (0 means no minimum)
 * @property maxLedgerExact Maximum ledger sequence number (0 means no maximum)
 * @property minLedger [minLedgerExact] as an `Int`
 * @property maxLedger [maxLedgerExact] as an `Int`
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/operations-and-transactions#ledger-bounds">LedgerBounds</a>
 */
class LedgerBounds private constructor(
    val minLedgerExact: UInt,
    val maxLedgerExact: UInt,
    // Sets this constructor apart from the public one, which has the same JVM signature.
    @Suppress("UNUSED_PARAMETER") exact: Unit
) {
    init {
        require(maxLedgerExact == 0u || minLedgerExact <= maxLedgerExact) {
            "minLedger cannot be greater than maxLedger (unless maxLedger is 0), " +
                "got minLedger $minLedgerExact and maxLedger $maxLedgerExact"
        }
    }

    /**
     * @param minLedger Minimum ledger sequence number (0 means no minimum)
     * @param maxLedger Maximum ledger sequence number (0 means no maximum)
     * @throws IllegalArgumentException if a value is negative, or if [maxLedger] is not 0 and is less than [minLedger]
     */
    constructor(minLedger: Int, maxLedger: Int) :
        this(nonNegative(minLedger, "minLedger"), nonNegative(maxLedger, "maxLedger"), Unit)

    val minLedger: Int get() = toInt(minLedgerExact, "minLedger")
    val maxLedger: Int get() = toInt(maxLedgerExact, "maxLedger")

    operator fun component1(): Int = minLedger
    operator fun component2(): Int = maxLedger
    fun copy(minLedger: Int = this.minLedger, maxLedger: Int = this.maxLedger): LedgerBounds =
        LedgerBounds(minLedger, maxLedger)

    /**
     * Converts this LedgerBounds to its XDR representation.
     *
     * @return The XDR LedgerBoundsXdr object
     */
    fun toXdr(): LedgerBoundsXdr {
        return LedgerBoundsXdr(
            minLedger = Uint32Xdr(minLedgerExact),
            maxLedger = Uint32Xdr(maxLedgerExact)
        )
    }

    override fun equals(other: Any?): Boolean =
        other is LedgerBounds && minLedgerExact == other.minLedgerExact && maxLedgerExact == other.maxLedgerExact

    override fun hashCode(): Int = 31 * minLedgerExact.hashCode() + maxLedgerExact.hashCode()

    override fun toString(): String = "LedgerBounds(minLedger=$minLedgerExact, maxLedger=$maxLedgerExact)"

    companion object {
        /**
         * Creates LedgerBounds from the exact `uint32` values.
         *
         * @param minLedger Minimum ledger sequence number (0 means no minimum)
         * @param maxLedger Maximum ledger sequence number (0 means no maximum)
         * @return LedgerBounds holding both values exactly
         * @throws IllegalArgumentException if [maxLedger] is not 0 and is less than [minLedger]
         */
        fun ofUnsigned(minLedger: UInt, maxLedger: UInt): LedgerBounds = LedgerBounds(minLedger, maxLedger, Unit)

        /**
         * Creates a new LedgerBounds object from an XDR LedgerBoundsXdr object.
         *
         * @param xdrLedgerBounds XDR LedgerBoundsXdr object to convert
         * @return LedgerBounds object holding the exact values
         */
        fun fromXdr(xdrLedgerBounds: LedgerBoundsXdr): LedgerBounds {
            return ofUnsigned(
                minLedger = xdrLedgerBounds.minLedger.value,
                maxLedger = xdrLedgerBounds.maxLedger.value
            )
        }

        private fun nonNegative(value: Int, name: String): UInt {
            require(value >= 0) { "$name must be >= 0, got $value" }
            return value.toUInt()
        }

        private fun toInt(value: UInt, name: String): Int {
            check(value <= Int.MAX_VALUE.toUInt()) { "$name $value exceeds Int.MAX_VALUE; read ${name}Exact" }
            return value.toInt()
        }
    }
}
