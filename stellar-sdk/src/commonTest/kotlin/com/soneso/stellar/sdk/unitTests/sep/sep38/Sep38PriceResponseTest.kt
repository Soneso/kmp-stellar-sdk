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

class Sep38PriceResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDeserializeWithFeeInSellAsset() {
        val jsonString = """
            {
              "total_price": "5.42",
              "price": "5.00",
              "sell_amount": "542",
              "buy_amount": "100",
              "fee": {
                "total": "42.00",
                "asset": "iso4217:BRL"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PriceResponse>(jsonString)

        assertEquals("5.42", response.totalPrice)
        assertEquals("5.00", response.price)
        assertEquals("542", response.sellAmount)
        assertEquals("100", response.buyAmount)

        assertEquals("42.00", response.fee.total)
        assertEquals("iso4217:BRL", response.fee.asset)
        assertNull(response.fee.details)
    }

    @Test
    fun testDeserializeWithFeeInBuyAsset() {
        val jsonString = """
            {
              "total_price": "5.42",
              "price": "5.00",
              "sell_amount": "542",
              "buy_amount": "100",
              "fee": {
                "total": "8.40",
                "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                "details": [
                  {
                    "name": "Service fee",
                    "amount": "8.40"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PriceResponse>(jsonString)

        assertEquals("5.42", response.totalPrice)
        assertEquals("5.00", response.price)
        assertEquals("542", response.sellAmount)
        assertEquals("100", response.buyAmount)

        assertEquals("8.40", response.fee.total)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.fee.asset)

        assertNotNull(response.fee.details)
        assertEquals(1, response.fee.details!!.size)
        assertEquals("Service fee", response.fee.details!![0].name)
        assertEquals("8.40", response.fee.details!![0].amount)
        assertNull(response.fee.details!![0].description)
    }

    @Test
    fun testDeserializeWithDetailedFeeBreakdown() {
        val jsonString = """
            {
              "total_price": "0.20",
              "price": "0.18",
              "sell_amount": "100",
              "buy_amount": "500",
              "fee": {
                "total": "55.5556",
                "asset": "iso4217:BRL",
                "details": [
                  {
                    "name": "PIX fee",
                    "description": "Fee charged in order to process the outgoing PIX transaction.",
                    "amount": "55.5556"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PriceResponse>(jsonString)

        assertEquals("0.20", response.totalPrice)
        assertEquals("0.18", response.price)
        assertEquals("100", response.sellAmount)
        assertEquals("500", response.buyAmount)

        assertEquals("55.5556", response.fee.total)
        assertEquals("iso4217:BRL", response.fee.asset)

        assertNotNull(response.fee.details)
        assertEquals(1, response.fee.details!!.size)

        val detail = response.fee.details!![0]
        assertEquals("PIX fee", detail.name)
        assertEquals("55.5556", detail.amount)
        assertEquals("Fee charged in order to process the outgoing PIX transaction.", detail.description)
    }

    @Test
    fun testDeserializeWithMultipleFeeDetails() {
        val jsonString = """
            {
              "total_price": "0.20",
              "price": "0.18",
              "sell_amount": "100",
              "buy_amount": "500",
              "fee": {
                "total": "10.00",
                "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                "details": [
                  {
                    "name": "Service fee",
                    "amount": "5.00"
                  },
                  {
                    "name": "PIX fee",
                    "description": "Fee charged in order to process the outgoing BRL PIX transaction.",
                    "amount": "5.00"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PriceResponse>(jsonString)

        assertEquals("0.20", response.totalPrice)
        assertEquals("0.18", response.price)
        assertEquals("100", response.sellAmount)
        assertEquals("500", response.buyAmount)

        assertEquals("10.00", response.fee.total)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.fee.asset)

        assertNotNull(response.fee.details)
        assertEquals(2, response.fee.details!!.size)

        val serviceFee = response.fee.details!![0]
        assertEquals("Service fee", serviceFee.name)
        assertEquals("5.00", serviceFee.amount)
        assertNull(serviceFee.description)

        val pixFee = response.fee.details!![1]
        assertEquals("PIX fee", pixFee.name)
        assertEquals("5.00", pixFee.amount)
        assertEquals("Fee charged in order to process the outgoing BRL PIX transaction.", pixFee.description)
    }

    @Test
    fun testDeserializeWithDecimalPrecision() {
        val jsonString = """
            {
              "total_price": "0.000015",
              "price": "0.000014",
              "sell_amount": "0.0015",
              "buy_amount": "100",
              "fee": {
                "total": "0.0001",
                "asset": "stellar:BTC:GATEMHCCKCY67ZUCKTROYN24ZYT5GK4EQZ65JJLDHKHRUZI3EUEKMTCH"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38PriceResponse>(jsonString)

        assertEquals("0.000015", response.totalPrice)
        assertEquals("0.000014", response.price)
        assertEquals("0.0015", response.sellAmount)
        assertEquals("100", response.buyAmount)
        assertEquals("0.0001", response.fee.total)
    }
}
