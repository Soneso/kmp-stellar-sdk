package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.LedgerBoundsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr

/**
 * LedgerBounds are Preconditions of a transaction per
 * <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0021.md#specification">CAP-21</a>
 *
 * Represents constraints on the ledger number at which a transaction is valid.
 *
 * @property minLedger Minimum ledger sequence number (0 means no minimum)
 * @property maxLedger Maximum ledger sequence number (0 means no maximum)
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/operations-and-transactions#ledger-bounds">LedgerBounds</a>
 */
data class LedgerBounds(
    val minLedger: Int,
    val maxLedger: Int
) {
    init {
        require(minLedger >= 0) { "minLedger must be >= 0" }
        require(maxLedger >= 0) { "maxLedger must be >= 0" }
        require(maxLedger == 0 || minLedger <= maxLedger) {
            "minLedger cannot be greater than maxLedger (unless maxLedger is 0)"
        }
    }

    /**
     * Converts this LedgerBounds to its XDR representation.
     *
     * @return The XDR LedgerBoundsXdr object
     */
    fun toXdr(): LedgerBoundsXdr {
        return LedgerBoundsXdr(
            minLedger = Uint32Xdr(minLedger.toUInt()),
            maxLedger = Uint32Xdr(maxLedger.toUInt())
        )
    }

    companion object {
        /**
         * Creates a new LedgerBounds object from an XDR LedgerBoundsXdr object.
         *
         * @param xdrLedgerBounds XDR LedgerBoundsXdr object to convert
         * @return LedgerBounds object
         */
        fun fromXdr(xdrLedgerBounds: LedgerBoundsXdr): LedgerBounds {
            return LedgerBounds(
                minLedger = xdrLedgerBounds.minLedger.value.toInt(),
                maxLedger = xdrLedgerBounds.maxLedger.value.toInt()
            )
        }
    }
}
