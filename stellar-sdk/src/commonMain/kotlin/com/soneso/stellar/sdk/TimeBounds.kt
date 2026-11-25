package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.TimePointXdr
import com.soneso.stellar.sdk.xdr.TimeBoundsXdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * TimeBounds represents the time interval that a transaction is valid.
 *
 * The UNIX timestamp (in seconds), determined by ledger time, of a lower and upper bound of when
 * this transaction will be valid. If a transaction is submitted too early or too late, it will fail
 * to make it into the transaction set.
 *
 * Both values are XDR `uint64` values and are stored exactly. The constructor takes values from 0 to
 * `Long.MAX_VALUE`; [ofUnsigned] and [fromXdr] take the full `uint64` range, which a transaction read
 * from XDR can carry, for example a `maxTime` of 2^64-1. [minTime], [maxTime], `copy` and the
 * `componentN` functions throw [IllegalStateException] for a value above `Long.MAX_VALUE`;
 * [minTimeExact] and [maxTimeExact] return every value.
 *
 * @property minTimeExact The UNIX timestamp (in seconds), 0 means no minimum
 * @property maxTimeExact The UNIX timestamp (in seconds), 0 means no maximum
 * @property minTime [minTimeExact] as a `Long`
 * @property maxTime [maxTimeExact] as a `Long`
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/operations-and-transactions#time-bounds">Time Bounds</a>
 */
class TimeBounds private constructor(
    val minTimeExact: ULong,
    val maxTimeExact: ULong,
    // Sets this constructor apart from the public one, which has the same JVM signature.
    @Suppress("UNUSED_PARAMETER") exact: Unit
) {
    init {
        require(maxTimeExact == 0uL || minTimeExact <= maxTimeExact) {
            "minTime must be <= maxTime (unless maxTime is 0), got minTime $minTimeExact and maxTime $maxTimeExact"
        }
    }

    /**
     * @param minTime The UNIX timestamp (in seconds), 0 means no minimum
     * @param maxTime The UNIX timestamp (in seconds), 0 means no maximum
     * @throws IllegalArgumentException if a value is negative, or if [maxTime] is not 0 and is less than [minTime]
     */
    constructor(minTime: Long, maxTime: Long) :
        this(nonNegative(minTime, "minTime"), nonNegative(maxTime, "maxTime"), Unit)

    val minTime: Long get() = toLong(minTimeExact, "minTime")
    val maxTime: Long get() = toLong(maxTimeExact, "maxTime")

    operator fun component1(): Long = minTime
    operator fun component2(): Long = maxTime
    fun copy(minTime: Long = this.minTime, maxTime: Long = this.maxTime): TimeBounds = TimeBounds(minTime, maxTime)

    /**
     * Converts this TimeBounds to its XDR representation.
     *
     * @return The XDR TimeBoundsXdr object
     */
    fun toXdr(): TimeBoundsXdr {
        return TimeBoundsXdr(
            minTime = TimePointXdr(Uint64Xdr(minTimeExact)),
            maxTime = TimePointXdr(Uint64Xdr(maxTimeExact))
        )
    }

    override fun equals(other: Any?): Boolean =
        other is TimeBounds && minTimeExact == other.minTimeExact && maxTimeExact == other.maxTimeExact

    override fun hashCode(): Int = 31 * minTimeExact.hashCode() + maxTimeExact.hashCode()

    override fun toString(): String = "TimeBounds(minTime=$minTimeExact, maxTime=$maxTimeExact)"

    companion object {
        /**
         * Constant representing infinite timeout (no maximum time).
         */
        const val TIMEOUT_INFINITE = 0L

        /**
         * Creates TimeBounds from the exact `uint64` values.
         *
         * @param minTime The UNIX timestamp (in seconds), 0 means no minimum
         * @param maxTime The UNIX timestamp (in seconds), 0 means no maximum
         * @return TimeBounds holding both values exactly
         * @throws IllegalArgumentException if [maxTime] is not 0 and is less than [minTime]
         */
        fun ofUnsigned(minTime: ULong, maxTime: ULong): TimeBounds = TimeBounds(minTime, maxTime, Unit)

        /**
         * A factory method that sets maxTime to the specified duration from now.
         *
         * @param timeout Timeout duration from now
         * @return TimeBounds with minTime=0 and maxTime=now+timeout
         */
        @OptIn(kotlin.time.ExperimentalTime::class)
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
        @OptIn(kotlin.time.ExperimentalTime::class)
        fun expiresAfter(timeoutSeconds: Long): TimeBounds {
            val now = Clock.System.now().epochSeconds
            val endTime = now + timeoutSeconds
            return TimeBounds(0, endTime)
        }

        /**
         * Construct a new TimeBounds object from a TimeBoundsXdr XDR object.
         *
         * @param timeBounds The XDR TimeBoundsXdr object
         * @return TimeBounds object holding the exact values, or null if the input is null
         */
        fun fromXdr(timeBounds: TimeBoundsXdr?): TimeBounds? {
            if (timeBounds == null) return null

            return ofUnsigned(
                minTime = timeBounds.minTime.value.value,
                maxTime = timeBounds.maxTime.value.value
            )
        }

        private fun nonNegative(value: Long, name: String): ULong {
            require(value >= 0) { "$name must be >= 0, got $value" }
            return value.toULong()
        }

        private fun toLong(value: ULong, name: String): Long {
            check(value <= Long.MAX_VALUE.toULong()) { "$name $value exceeds Long.MAX_VALUE; read ${name}Exact" }
            return value.toLong()
        }
    }
}
