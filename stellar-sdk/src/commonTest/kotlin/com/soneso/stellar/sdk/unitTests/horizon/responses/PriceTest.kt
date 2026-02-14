package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Price
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PriceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDeserialization() {
        val priceJson = """{"n": 1, "d": 2}"""
        val price = json.decodeFromString<Price>(priceJson)
        assertEquals(1L, price.numerator)
        assertEquals(2L, price.denominator)
    }

    @Test
    fun testLargeValues() {
        val priceJson = """{"n": 2147483648, "d": 4294967296}"""
        val price = json.decodeFromString<Price>(priceJson)
        assertEquals(2147483648L, price.numerator)
        assertEquals(4294967296L, price.denominator)
    }

    @Test
    fun testEquality() {
        val p1 = Price(1, 2)
        val p2 = Price(1, 2)
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    @Test
    fun testInequality() {
        val p1 = Price(1, 2)
        val p2 = Price(2, 1)
        assertNotEquals(p1, p2)
    }

    @Test
    fun testZeroDenominator() {
        val priceJson = """{"n": 1, "d": 0}"""
        val price = json.decodeFromString<Price>(priceJson)
        assertEquals(0L, price.denominator)
    }

    @Test
    fun testCopy() {
        val price = Price(3, 7)
        val copy = price.copy(numerator = 5)
        assertEquals(5L, copy.numerator)
        assertEquals(7L, copy.denominator)
    }
}
