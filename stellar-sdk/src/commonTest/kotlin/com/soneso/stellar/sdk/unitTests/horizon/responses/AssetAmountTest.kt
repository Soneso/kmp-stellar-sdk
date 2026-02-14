package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.AssetAmount
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetAmountTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDeserialization() {
        val assetAmountJson = """{"asset": "native", "amount": "100.0000000"}"""
        val assetAmount = json.decodeFromString<AssetAmount>(assetAmountJson)
        assertEquals("native", assetAmount.asset)
        assertEquals("100.0000000", assetAmount.amount)
    }

    @Test
    fun testCreditAssetDeserialization() {
        val assetAmountJson = """{"asset": "USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", "amount": "50.5000000"}"""
        val assetAmount = json.decodeFromString<AssetAmount>(assetAmountJson)
        assertEquals("USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", assetAmount.asset)
        assertEquals("50.5000000", assetAmount.amount)
    }

    @Test
    fun testDataClassEquality() {
        val a1 = AssetAmount("native", "100.0")
        val a2 = AssetAmount("native", "100.0")
        assertEquals(a1, a2)
        assertEquals(a1.hashCode(), a2.hashCode())
    }

    @Test
    fun testZeroAmount() {
        val assetAmountJson = """{"asset": "native", "amount": "0.0000000"}"""
        val assetAmount = json.decodeFromString<AssetAmount>(assetAmountJson)
        assertEquals("0.0000000", assetAmount.amount)
    }
}
