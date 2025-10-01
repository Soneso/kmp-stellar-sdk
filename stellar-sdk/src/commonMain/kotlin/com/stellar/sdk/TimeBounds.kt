package com.stellar.sdk

import com.stellar.sdk.xdr.TimePointXdr
import com.stellar.sdk.xdr.TimeBoundsXdr
import com.stellar.sdk.xdr.Uint64Xdr
import kotlinx.datetime.Clock
import kotlin.time.Duration

/**
 * TimeBounds represents the time interval that a transaction is valid.
 *
 * The UNIX timestamp (in seconds), determined by ledger time, of a lower and upper bound of when
 * this transaction will be valid. If a transaction is submitted too early or too late, it will fail
 * to make it into the transaction set.
 *
 * @property minTime The UNIX timestamp (in seconds), 0 means no minimum
 * @property maxTime The UNIX timestamp (in seconds), 0 means no maximum
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/operations-and-transactions#time-bounds">Time Bounds</a>
 */
data class TimeBounds(
    val minTime: Long,
    val maxTime: Long
) {
    init {
        require(minTime >= 0) { "minTime must be >= 0" }
        require(maxTime >= 0) { "maxTime must be >= 0" }
        require(maxTime == 0L || minTime <= maxTime) {
            "minTime must be <= maxTime (unless maxTime is 0)"
        }
    }

    /**
     * Converts this TimeBounds to its XDR representation.
     *
     * @return The XDR TimeBoundsXdr object
     */
    fun toXdr(): TimeBoundsXdr {
        return TimeBoundsXdr(
            minTime = TimePointXdr(Uint64Xdr(minTime.toULong())),
            maxTime = TimePointXdr(Uint64Xdr(maxTime.toULong()))
        )
    }

    companion object {
        /**
         * Constant representing infinite timeout (no maximum time).
         */
        const val TIMEOUT_INFINITE = 0L

        /**
         * A factory method that sets maxTime to the specified duration from now.
         *
         * @param timeout Timeout duration from now
         * @return TimeBounds with minTime=0 and maxTime=now+timeout
         */
        fun expiresAfter(timeout: Duration): TimeBounds {
            val now = Clock.System.now().epochSeconds
            val endTime = now + timeout.inWholeSeconds
            return TimeBounds(0, endTime)
        }

        /**
         * A factory method that sets maxTime to the specified seconds from now.
         *
         * @param timeoutSeconds Timeout in seconds
         * @return TimeBounds with minTime=0 and maxTime=now+timeoutSeconds
         */
        fun expiresAfter(timeoutSeconds: Long): TimeBounds {
            val now = Clock.System.now().epochSeconds
            val endTime = now + timeoutSeconds
            return TimeBounds(0, endTime)
        }

        /**
         * Construct a new TimeBounds object from a TimeBoundsXdr XDR object.
         *
         * @param timeBounds The XDR TimeBoundsXdr object
         * @return TimeBounds object, or null if the input is null
         */
        fun fromXdr(timeBounds: TimeBoundsXdr?): TimeBounds? {
            if (timeBounds == null) return null

            return TimeBounds(
                minTime = timeBounds.minTime.value.value.toLong(),
                maxTime = timeBounds.maxTime.value.value.toLong()
            )
        }
    }
}
