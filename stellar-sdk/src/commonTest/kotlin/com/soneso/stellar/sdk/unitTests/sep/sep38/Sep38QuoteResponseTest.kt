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

class Sep38QuoteResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDeserializeFirmQuoteWithAllFields() {
        val jsonString = """
            {
              "id": "de762cda-a193-4961-861e-57b31fed6eb3",
              "expires_at": "2021-04-30T07:42:23",
              "total_price": "5.42",
              "price": "5.00",
              "sell_asset": "iso4217:BRL",
              "sell_amount": "542",
              "sell_delivery_method": "PIX",
              "buy_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "buy_amount": "100",
              "buy_delivery_method": "bank_account",
              "fee": {
                "total": "42.00",
                "asset": "iso4217:BRL",
                "details": [
                  {
                    "name": "PIX fee",
                    "description": "Fee charged in order to process the outgoing PIX transaction.",
                    "amount": "12.00"
                  },
                  {
                    "name": "Brazilian conciliation fee",
                    "description": "Fee charged in order to process conciliation costs with intermediary banks.",
                    "amount": "15.00"
                  },
                  {
                    "name": "Service fee",
                    "amount": "15.00"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38QuoteResponse>(jsonString)

        assertEquals("de762cda-a193-4961-861e-57b31fed6eb3", response.id)
        assertEquals("2021-04-30T07:42:23", response.expiresAt)
        assertEquals("5.42", response.totalPrice)
        assertEquals("5.00", response.price)
        assertEquals("iso4217:BRL", response.sellAsset)
        assertEquals("542", response.sellAmount)
        assertEquals("PIX", response.sellDeliveryMethod)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.buyAsset)
        assertEquals("100", response.buyAmount)
        assertEquals("bank_account", response.buyDeliveryMethod)

        assertEquals("42.00", response.fee.total)
        assertEquals("iso4217:BRL", response.fee.asset)

        assertNotNull(response.fee.details)
        assertEquals(3, response.fee.details!!.size)

        val pixFee = response.fee.details!![0]
        assertEquals("PIX fee", pixFee.name)
        assertEquals("12.00", pixFee.amount)
        assertEquals("Fee charged in order to process the outgoing PIX transaction.", pixFee.description)

        val conciliationFee = response.fee.details!![1]
        assertEquals("Brazilian conciliation fee", conciliationFee.name)
        assertEquals("15.00", conciliationFee.amount)
        assertEquals("Fee charged in order to process conciliation costs with intermediary banks.", conciliationFee.description)

        val serviceFee = response.fee.details!![2]
        assertEquals("Service fee", serviceFee.name)
        assertEquals("15.00", serviceFee.amount)
        assertNull(serviceFee.description)
    }

    @Test
    fun testDeserializeQuoteWithoutDeliveryMethods() {
        val jsonString = """
            {
              "id": "abc123-def456-ghi789",
              "expires_at": "2021-05-01T10:00:00Z",
              "total_price": "1.01",
              "price": "1.00",
              "sell_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "sell_amount": "101",
              "buy_asset": "stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP",
              "buy_amount": "100",
              "fee": {
                "total": "1.00",
                "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38QuoteResponse>(jsonString)

        assertEquals("abc123-def456-ghi789", response.id)
        assertEquals("2021-05-01T10:00:00Z", response.expiresAt)
        assertEquals("1.01", response.totalPrice)
        assertEquals("1.00", response.price)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.sellAsset)
        assertEquals("101", response.sellAmount)
        assertNull(response.sellDeliveryMethod)
        assertEquals("stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP", response.buyAsset)
        assertEquals("100", response.buyAmount)
        assertNull(response.buyDeliveryMethod)

        assertEquals("1.00", response.fee.total)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.fee.asset)
        assertNull(response.fee.details)
    }

    @Test
    fun testDeserializeQuoteWithSellDeliveryMethodOnly() {
        val jsonString = """
            {
              "id": "quote-001",
              "expires_at": "2021-06-15T14:30:00",
              "total_price": "542.00",
              "price": "500.00",
              "sell_asset": "iso4217:USD",
              "sell_amount": "542",
              "sell_delivery_method": "WIRE",
              "buy_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "buy_amount": "500",
              "fee": {
                "total": "42.00",
                "asset": "iso4217:USD"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38QuoteResponse>(jsonString)

        assertEquals("quote-001", response.id)
        assertEquals("WIRE", response.sellDeliveryMethod)
        assertNull(response.buyDeliveryMethod)
    }

    @Test
    fun testDeserializeQuoteWithBuyDeliveryMethodOnly() {
        val jsonString = """
            {
              "id": "quote-002",
              "expires_at": "2021-07-20T18:45:00",
              "total_price": "0.20",
              "price": "0.18",
              "sell_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "sell_amount": "100",
              "buy_asset": "iso4217:EUR",
              "buy_amount": "500",
              "buy_delivery_method": "SEPA",
              "fee": {
                "total": "10.00",
                "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38QuoteResponse>(jsonString)

        assertEquals("quote-002", response.id)
        assertNull(response.sellDeliveryMethod)
        assertEquals("SEPA", response.buyDeliveryMethod)
    }

    @Test
    fun testDeserializeQuoteWithISO8601Timestamp() {
        val jsonString = """
            {
              "id": "uuid-test-123",
              "expires_at": "2021-04-30T07:42:23.123Z",
              "total_price": "1.00",
              "price": "1.00",
              "sell_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "sell_amount": "100",
              "buy_asset": "iso4217:USD",
              "buy_amount": "100",
              "fee": {
                "total": "0.00",
                "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38QuoteResponse>(jsonString)

        assertEquals("uuid-test-123", response.id)
        assertEquals("2021-04-30T07:42:23.123Z", response.expiresAt)
    }
}
