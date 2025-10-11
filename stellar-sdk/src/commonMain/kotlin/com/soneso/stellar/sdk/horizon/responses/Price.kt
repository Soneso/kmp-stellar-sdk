package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a price in the Stellar network.
 *
 * Note: The numerator (n) and denominator (d) returned by Horizon can exceed int32 ranges.
 *
 * @property numerator The numerator of the price fraction
 * @property denominator The denominator of the price fraction
 */
@Serializable
data class Price(
    @SerialName("n")
    val numerator: Long,

    @SerialName("d")
    val denominator: Long
)
