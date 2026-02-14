// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep38

import com.soneso.stellar.sdk.sep.sep38.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Sep38QuoteRequestTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    @Test
    fun testSerializeWithSellAmount() {
        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sellAmount = "542",
            sellDeliveryMethod = "PIX",
            countryCode = "BR"
        )

        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"context\":\"sep6\""))
        assertTrue(jsonString.contains("\"sell_asset\":\"iso4217:BRL\""))
        assertTrue(jsonString.contains("\"buy_asset\":\"stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN\""))
        assertTrue(jsonString.contains("\"sell_amount\":\"542\""))
        assertTrue(jsonString.contains("\"sell_delivery_method\":\"PIX\""))
        assertTrue(jsonString.contains("\"country_code\":\"BR\""))
    }

    @Test
    fun testSerializeWithBuyAmount() {
        val request = Sep38QuoteRequest(
            context = "sep31",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            buyAmount = "100",
            sellDeliveryMethod = "PIX",
            countryCode = "BR"
        )

        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"context\":\"sep31\""))
        assertTrue(jsonString.contains("\"buy_amount\":\"100\""))
        assertTrue(jsonString.contains("\"sell_delivery_method\":\"PIX\""))
    }

    @Test
    fun testSerializeWithAllOptionalFields() {
        val request = Sep38QuoteRequest(
            context = "sep24",
            sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            buyAsset = "iso4217:USD",
            sellAmount = "100",
            expireAfter = "2021-04-30T07:42:23Z",
            buyDeliveryMethod = "bank_account",
            countryCode = "US"
        )

        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"context\":\"sep24\""))
        assertTrue(jsonString.contains("\"sell_amount\":\"100\""))
        assertTrue(jsonString.contains("\"expire_after\":\"2021-04-30T07:42:23Z\""))
        assertTrue(jsonString.contains("\"buy_delivery_method\":\"bank_account\""))
        assertTrue(jsonString.contains("\"country_code\":\"US\""))
    }

    @Test
    fun testSerializeWithOnlySellDeliveryMethod() {
        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:EUR",
            buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sellAmount = "500",
            sellDeliveryMethod = "SEPA"
        )

        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"sell_delivery_method\":\"SEPA\""))
    }

    @Test
    fun testSerializeMinimalRequest() {
        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            buyAsset = "stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP",
            sellAmount = "100"
        )

        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"context\":\"sep6\""))
        assertTrue(jsonString.contains("\"sell_asset\":\"stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN\""))
        assertTrue(jsonString.contains("\"buy_asset\":\"stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP\""))
        assertTrue(jsonString.contains("\"sell_amount\":\"100\""))
    }

    @Test
    fun testDeserializeAndRoundtrip() {
        val originalRequest = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sellAmount = "542",
            sellDeliveryMethod = "PIX",
            countryCode = "BR"
        )

        val jsonString = json.encodeToString(originalRequest)
        val deserializedRequest = json.decodeFromString<Sep38QuoteRequest>(jsonString)

        assertEquals(originalRequest.context, deserializedRequest.context)
        assertEquals(originalRequest.sellAsset, deserializedRequest.sellAsset)
        assertEquals(originalRequest.buyAsset, deserializedRequest.buyAsset)
        assertEquals(originalRequest.sellAmount, deserializedRequest.sellAmount)
        assertEquals(originalRequest.sellDeliveryMethod, deserializedRequest.sellDeliveryMethod)
        assertEquals(originalRequest.countryCode, deserializedRequest.countryCode)
    }

    @Test
    fun testSerializeSnakeCaseKeys() {
        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            buyAmount = "100",
            expireAfter = "2021-05-01T00:00:00Z",
            sellDeliveryMethod = "PIX",
            buyDeliveryMethod = "bank_account",
            countryCode = "BR"
        )

        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("sell_asset"))
        assertTrue(jsonString.contains("buy_asset"))
        assertTrue(jsonString.contains("buy_amount"))
        assertTrue(jsonString.contains("expire_after"))
        assertTrue(jsonString.contains("sell_delivery_method"))
        assertTrue(jsonString.contains("buy_delivery_method"))
        assertTrue(jsonString.contains("country_code"))
    }
}
