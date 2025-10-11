package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.Int32Xdr
import com.soneso.stellar.sdk.xdr.PriceXdr
import kotlin.jvm.JvmStatic
import kotlin.math.abs

/**
 * Represents a price in Stellar, expressed as a fraction.
 *
 * Prices in Stellar are represented as rational numbers (fractions) with a 32-bit numerator
 * and 32-bit denominator. This representation allows for exact price calculations without
 * floating-point rounding errors.
 *
 * ## Usage
 *
 * ```kotlin
 * // Create a price directly from numerator and denominator
 * val price = Price(1, 2)  // Represents 0.5
 *
 * // Parse from a decimal string
 * val price2 = Price.fromString("1.5")  // Approximates to fraction 3/2
 *
 * // Convert to decimal string
 * println(price.toString())  // "0.5"
 * ```
 *
 * ## Important Notes
 *
 * - When parsing from strings, the function approximates decimal values to fractions
 * - Values that cannot be represented as fractions with 32-bit integers may lose precision
 * - For exact pricing, prefer creating Price objects directly from known numerator/denominator
 *
 * @property numerator The numerator of the price fraction
 * @property denominator The denominator of the price fraction
 * @throws IllegalArgumentException if denominator is zero
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/stellar-data-structures/operations-and-transactions#price">Price in Stellar</a>
 */
data class Price(
    val numerator: Int,
    val denominator: Int
) {
    init {
        require(denominator != 0) { "Price denominator cannot be zero" }
    }

    /**
     * Converts this Price to its XDR representation.
     *
     * @return The XDR Price object
     */
    fun toXdr(): PriceXdr {
        return PriceXdr(
            n = Int32Xdr(numerator),
            d = Int32Xdr(denominator)
        )
    }

    /**
     * Returns the decimal representation of this price.
     *
     * @return A string representation of the price as a decimal number
     */
    override fun toString(): String {
        if (denominator == 1) {
            return numerator.toString()
        }
        // Use double for decimal representation
        val decimal = numerator.toDouble() / denominator.toDouble()
        return decimal.toString()
    }

    companion object {
        /**
         * Approximates a decimal price string to a fraction.
         *
         * This function uses continued fractions algorithm to approximate the decimal value
         * to a fraction with 32-bit numerator and denominator.
         *
         * **Warning**: This function can give unexpected results for values that cannot be
         * represented accurately as fractions within the 32-bit integer constraints.
         * It's safer to create Price objects directly using the constructor when you know
         * the exact fraction.
         *
         * @param price The decimal price as a string (e.g., "1.5", "0.75")
         * @return A Price object approximating the decimal value
         * @throws IllegalArgumentException if the price string is invalid
         *
         * ## Example
         * ```kotlin
         * val price = Price.fromString("1.5")
         * println("${price.numerator}/${price.denominator}")  // 3/2
         * ```
         */
        @JvmStatic
        fun fromString(price: String): Price {
            require(price.isNotBlank()) { "Price string cannot be blank" }

            // Parse the decimal value
            val number = try {
                price.toDouble()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid price format: '$price'", e)
            }

            require(number.isFinite()) { "Price must be a finite number" }
            require(number >= 0) { "Price must be non-negative" }

            // Special case for zero and whole numbers
            if (number == 0.0) {
                return Price(0, 1)
            }

            // Use continued fractions algorithm to approximate the decimal to a fraction
            // This follows the Java SDK implementation
            val maxInt = Int.MAX_VALUE.toDouble()
            var x = abs(number)
            val fractions = mutableListOf<Pair<Double, Double>>()
            fractions.add(0.0 to 1.0)
            fractions.add(1.0 to 0.0)

            var i = 2
            while (true) {
                if (x > maxInt) {
                    break
                }

                val a = kotlin.math.floor(x)
                val f = x - a

                val h = a * fractions[i - 1].first + fractions[i - 2].first
                val k = a * fractions[i - 1].second + fractions[i - 2].second

                if (h > maxInt || k > maxInt) {
                    break
                }

                fractions.add(h to k)

                if (f == 0.0) {
                    break
                }

                x = 1.0 / f
                i++

                // Safety limit to prevent infinite loops
                if (i > 100) {
                    break
                }
            }

            val result = fractions.last()
            return Price(
                numerator = result.first.toInt(),
                denominator = result.second.toInt()
            )
        }

        /**
         * Creates a Price from its XDR representation.
         *
         * @param price The XDR Price object
         * @return A Price instance
         */
        @JvmStatic
        fun fromXdr(price: PriceXdr): Price {
            return Price(
                numerator = price.n.value,
                denominator = price.d.value
            )
        }
    }
}
