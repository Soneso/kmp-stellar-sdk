//
//  PriceTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.Price
import com.soneso.stellar.sdk.xdr.Int32Xdr
import com.soneso.stellar.sdk.xdr.PriceXdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Price covering the continued-fraction string parser, decimal formatting,
 * XDR conversion, and data class semantics.
 */
class PriceTest {

    // ========== Constructor validation ==========

    @Test
    fun testConstructorZeroDenominatorThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Price(1, 0)
        }
        assertTrue(exception.message!!.contains("denominator"))
    }

    @Test
    fun testConstructorAcceptsNegativeNumeratorAndDenominator() {
        // The constructor only rejects a zero denominator; sign validation is not enforced here.
        val price = Price(-3, -4)
        assertEquals(-3, price.numerator)
        assertEquals(-4, price.denominator)
    }

    // ========== fromString: whole numbers ==========

    @Test
    fun testFromStringWholeNumber() {
        val price = Price.fromString("2")
        assertEquals(2, price.numerator)
        assertEquals(1, price.denominator)
    }

    @Test
    fun testFromStringZero() {
        val price = Price.fromString("0")
        assertEquals(0, price.numerator)
        assertEquals(1, price.denominator)
    }

    // ========== fromString: simple decimals ==========

    @Test
    fun testFromStringOneHalf() {
        val price = Price.fromString("0.5")
        assertEquals(1, price.numerator)
        assertEquals(2, price.denominator)
    }

    @Test
    fun testFromStringThreeHalves() {
        val price = Price.fromString("1.5")
        assertEquals(3, price.numerator)
        assertEquals(2, price.denominator)
    }

    @Test
    fun testFromStringThreeQuarters() {
        val price = Price.fromString("0.75")
        assertEquals(3, price.numerator)
        assertEquals(4, price.denominator)
    }

    // ========== fromString: repeating decimals ==========

    @Test
    fun testFromStringRepeatingDecimalApproximatesOneThird() {
        val price = Price.fromString("0.3333333333")
        assertEquals(1, price.numerator)
        assertEquals(3, price.denominator)
    }

    // ========== fromString: large values ==========

    @Test
    fun testFromStringLargeWholeNumber() {
        val price = Price.fromString("2147483647")
        assertEquals(Int.MAX_VALUE, price.numerator)
        assertEquals(1, price.denominator)
    }

    @Test
    fun testFromStringValueExceedingIntRangeThrows() {
        // A value whose integer part already exceeds Int.MAX_VALUE has no representation as a
        // fraction of two 32-bit integers, so the approximation must report that explicitly.
        val exception = assertFailsWith<IllegalArgumentException> {
            Price.fromString("9999999999")
        }
        val message = exception.message
        assertNotNull(message)
        assertTrue(
            message.contains("9999999999") && message.contains("32-bit"),
            "Unexpected message: $message"
        )
    }

    @Test
    fun testFromStringJustAboveIntMaxThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Price.fromString("2147483648")
        }
        val message = exception.message
        assertNotNull(message)
        assertTrue(
            message.contains("cannot be approximated"),
            "Unexpected message: $message"
        )
    }

    // ========== fromString: invalid input ==========

    @Test
    fun testFromStringBlankThrows() {
        assertFailsWith<IllegalArgumentException> {
            Price.fromString("")
        }
        assertFailsWith<IllegalArgumentException> {
            Price.fromString("   ")
        }
    }

    @Test
    fun testFromStringNonNumericThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Price.fromString("not-a-number")
        }
        assertTrue(exception.message!!.contains("Invalid price format"))
    }

    @Test
    fun testFromStringNegativeThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Price.fromString("-1.5")
        }
        assertTrue(exception.message!!.contains("non-negative"))
    }

    @Test
    fun testFromStringNaNThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Price.fromString("NaN")
        }
        assertTrue(exception.message!!.contains("finite"))
    }

    @Test
    fun testFromStringInfinityThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Price.fromString("Infinity")
        }
        assertTrue(exception.message!!.contains("finite"))
    }

    // ========== toString ==========

    @Test
    fun testToStringWholeDenominatorOne() {
        assertEquals("5", Price(5, 1).toString())
    }

    @Test
    fun testToStringFraction() {
        assertEquals("0.5", Price(1, 2).toString())
    }

    // ========== toXdr / fromXdr ==========

    @Test
    fun testToXdr() {
        val price = Price(3, 4)
        val xdr = price.toXdr()
        assertEquals(3, xdr.n.value)
        assertEquals(4, xdr.d.value)
    }

    @Test
    fun testFromXdr() {
        val xdr = PriceXdr(n = Int32Xdr(7), d = Int32Xdr(11))
        val price = Price.fromXdr(xdr)
        assertEquals(7, price.numerator)
        assertEquals(11, price.denominator)
    }

    @Test
    fun testToXdrFromXdrRoundtrip() {
        val original = Price(22, 7)
        val restored = Price.fromXdr(original.toXdr())
        assertEquals(original, restored)
    }

    // ========== Equality and hashCode ==========

    @Test
    fun testEqualityAndHashCode() {
        val price1 = Price(1, 2)
        val price2 = Price(1, 2)
        val price3 = Price(1, 3)

        assertEquals(price1, price2)
        assertEquals(price1.hashCode(), price2.hashCode())
        assertNotEquals(price1, price3)
    }
}
