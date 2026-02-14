// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep38

import com.soneso.stellar.sdk.sep.sep38.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Sep38PricesResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDeserializeBuyAssetsResponse() {
        val jsonString = """
            {
              "buy_assets": [
                {
                  "asset": "iso4217:BRL",
                  "price": "0.18",
                  "decimals": 2
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PricesResponse>(jsonString)

        assertNotNull(response.buyAssets)
        assertEquals(1, response.buyAssets!!.size)
        assertNull(response.sellAssets)

        val buyAsset = response.buyAssets!![0]
        assertEquals("iso4217:BRL", buyAsset.asset)
        assertEquals("0.18", buyAsset.price)
        assertEquals(2, buyAsset.decimals)
    }

    @Test
    fun testDeserializeSellAssetsResponse() {
        val jsonString = """
            {
              "sell_assets": [
                {
                  "asset": "iso4217:BRL",
                  "price": "5.42",
                  "decimals": 2
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PricesResponse>(jsonString)

        assertNull(response.buyAssets)
        assertNotNull(response.sellAssets)
        assertEquals(1, response.sellAssets!!.size)

        val sellAsset = response.sellAssets!![0]
        assertEquals("iso4217:BRL", sellAsset.asset)
        assertEquals("5.42", sellAsset.price)
        assertEquals(2, sellAsset.decimals)
    }

    @Test
    fun testDeserializeMultipleBuyAssets() {
        val jsonString = """
            {
              "buy_assets": [
                {
                  "asset": "iso4217:BRL",
                  "price": "0.18",
                  "decimals": 2
                },
                {
                  "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                  "price": "5.42",
                  "decimals": 7
                },
                {
                  "asset": "iso4217:USD",
                  "price": "1.00",
                  "decimals": 2
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PricesResponse>(jsonString)

        assertNotNull(response.buyAssets)
        assertEquals(3, response.buyAssets!!.size)
        assertNull(response.sellAssets)

        assertEquals("iso4217:BRL", response.buyAssets!![0].asset)
        assertEquals("0.18", response.buyAssets!![0].price)
        assertEquals(2, response.buyAssets!![0].decimals)

        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.buyAssets!![1].asset)
        assertEquals("5.42", response.buyAssets!![1].price)
        assertEquals(7, response.buyAssets!![1].decimals)

        assertEquals("iso4217:USD", response.buyAssets!![2].asset)
        assertEquals("1.00", response.buyAssets!![2].price)
        assertEquals(2, response.buyAssets!![2].decimals)
    }

    @Test
    fun testDeserializeMultipleSellAssets() {
        val jsonString = """
            {
              "sell_assets": [
                {
                  "asset": "iso4217:BRL",
                  "price": "5.42",
                  "decimals": 2
                },
                {
                  "asset": "iso4217:EUR",
                  "price": "1.08",
                  "decimals": 2
                },
                {
                  "asset": "stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP",
                  "price": "5.00",
                  "decimals": 7
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PricesResponse>(jsonString)

        assertNull(response.buyAssets)
        assertNotNull(response.sellAssets)
        assertEquals(3, response.sellAssets!!.size)

        assertEquals("iso4217:BRL", response.sellAssets!![0].asset)
        assertEquals("5.42", response.sellAssets!![0].price)
        assertEquals(2, response.sellAssets!![0].decimals)

        assertEquals("iso4217:EUR", response.sellAssets!![1].asset)
        assertEquals("1.08", response.sellAssets!![1].price)
        assertEquals(2, response.sellAssets!![1].decimals)

        assertEquals("stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP", response.sellAssets!![2].asset)
        assertEquals("5.00", response.sellAssets!![2].price)
        assertEquals(7, response.sellAssets!![2].decimals)
    }

    @Test
    fun testDeserializeWithHighPrecisionDecimals() {
        val jsonString = """
            {
              "buy_assets": [
                {
                  "asset": "stellar:BTC:GATEMHCCKCY67ZUCKTROYN24ZYT5GK4EQZ65JJLDHKHRUZI3EUEKMTCH",
                  "price": "0.000023456789",
                  "decimals": 12
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PricesResponse>(jsonString)

        assertNotNull(response.buyAssets)
        assertEquals(1, response.buyAssets!!.size)

        val buyAsset = response.buyAssets!![0]
        assertEquals("stellar:BTC:GATEMHCCKCY67ZUCKTROYN24ZYT5GK4EQZ65JJLDHKHRUZI3EUEKMTCH", buyAsset.asset)
        assertEquals("0.000023456789", buyAsset.price)
        assertEquals(12, buyAsset.decimals)
    }
}
