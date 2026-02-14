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

class Sep38InfoResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDeserializeFullInfoResponse() {
        val jsonString = """
            {
              "assets": [
                {
                  "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
                },
                {
                  "asset": "stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP"
                },
                {
                  "asset": "iso4217:BRL",
                  "country_codes": ["BR"],
                  "sell_delivery_methods": [
                    {
                      "name": "cash",
                      "description": "Deposit cash BRL at one of our agent locations."
                    },
                    {
                      "name": "ACH",
                      "description": "Send BRL directly to the Anchor's bank account."
                    },
                    {
                      "name": "PIX",
                      "description": "Send BRL directly to the Anchor's bank account."
                    }
                  ],
                  "buy_delivery_methods": [
                    {
                      "name": "cash",
                      "description": "Pick up cash BRL at one of our payout locations."
                    },
                    {
                      "name": "ACH",
                      "description": "Have BRL sent directly to your bank account."
                    },
                    {
                      "name": "PIX",
                      "description": "Have BRL sent directly to the account of your choice."
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38InfoResponse>(jsonString)

        assertEquals(3, response.assets.size)

        val stellarUSDC = response.assets[0]
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", stellarUSDC.asset)
        assertNull(stellarUSDC.sellDeliveryMethods)
        assertNull(stellarUSDC.buyDeliveryMethods)
        assertNull(stellarUSDC.countryCodes)

        val stellarBRL = response.assets[1]
        assertEquals("stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP", stellarBRL.asset)
        assertNull(stellarBRL.sellDeliveryMethods)
        assertNull(stellarBRL.buyDeliveryMethods)
        assertNull(stellarBRL.countryCodes)

        val fiatBRL = response.assets[2]
        assertEquals("iso4217:BRL", fiatBRL.asset)
        assertNotNull(fiatBRL.countryCodes)
        assertEquals(1, fiatBRL.countryCodes!!.size)
        assertEquals("BR", fiatBRL.countryCodes!![0])

        assertNotNull(fiatBRL.sellDeliveryMethods)
        assertEquals(3, fiatBRL.sellDeliveryMethods!!.size)
        assertEquals("cash", fiatBRL.sellDeliveryMethods!![0].name)
        assertEquals("Deposit cash BRL at one of our agent locations.", fiatBRL.sellDeliveryMethods!![0].description)
        assertEquals("ACH", fiatBRL.sellDeliveryMethods!![1].name)
        assertEquals("PIX", fiatBRL.sellDeliveryMethods!![2].name)

        assertNotNull(fiatBRL.buyDeliveryMethods)
        assertEquals(3, fiatBRL.buyDeliveryMethods!!.size)
        assertEquals("cash", fiatBRL.buyDeliveryMethods!![0].name)
        assertEquals("Pick up cash BRL at one of our payout locations.", fiatBRL.buyDeliveryMethods!![0].description)
        assertEquals("ACH", fiatBRL.buyDeliveryMethods!![1].name)
        assertEquals("PIX", fiatBRL.buyDeliveryMethods!![2].name)
    }

    @Test
    fun testDeserializeMinimalInfoResponse() {
        val jsonString = """
            {
              "assets": [
                {
                  "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
                },
                {
                  "asset": "stellar:BRL:GDVKY2GU2DRXWTBEYJJWSFXIGBZV6AZNBVVSUHEPZI54LIS6BA7DVVSP"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38InfoResponse>(jsonString)

        assertEquals(2, response.assets.size)
        response.assets.forEach { asset ->
            assertNull(asset.sellDeliveryMethods)
            assertNull(asset.buyDeliveryMethods)
            assertNull(asset.countryCodes)
        }
    }

    @Test
    fun testDeserializeWithMultipleCountryCodes() {
        val jsonString = """
            {
              "assets": [
                {
                  "asset": "iso4217:USD",
                  "country_codes": ["US", "CA", "MX"],
                  "sell_delivery_methods": [
                    {
                      "name": "WIRE",
                      "description": "International wire transfer"
                    }
                  ],
                  "buy_delivery_methods": [
                    {
                      "name": "WIRE",
                      "description": "International wire transfer"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38InfoResponse>(jsonString)

        assertEquals(1, response.assets.size)
        val asset = response.assets[0]
        assertEquals("iso4217:USD", asset.asset)
        assertNotNull(asset.countryCodes)
        assertEquals(3, asset.countryCodes!!.size)
        assertEquals("US", asset.countryCodes!![0])
        assertEquals("CA", asset.countryCodes!![1])
        assertEquals("MX", asset.countryCodes!![2])
    }

    @Test
    fun testDeserializeWithSingleDeliveryMethod() {
        val jsonString = """
            {
              "assets": [
                {
                  "asset": "iso4217:EUR",
                  "sell_delivery_methods": [
                    {
                      "name": "SEPA",
                      "description": "SEPA bank transfer"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38InfoResponse>(jsonString)

        assertEquals(1, response.assets.size)
        val asset = response.assets[0]
        assertEquals("iso4217:EUR", asset.asset)
        assertNotNull(asset.sellDeliveryMethods)
        assertEquals(1, asset.sellDeliveryMethods!!.size)
        assertEquals("SEPA", asset.sellDeliveryMethods!![0].name)
        assertNull(asset.buyDeliveryMethods)
    }

    @Test
    fun testDeserializeMixedAssetTypes() {
        val jsonString = """
            {
              "assets": [
                {
                  "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
                },
                {
                  "asset": "iso4217:USD"
                },
                {
                  "asset": "iso4217:BRL",
                  "country_codes": ["BR"]
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep38InfoResponse>(jsonString)

        assertEquals(3, response.assets.size)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.assets[0].asset)
        assertEquals("iso4217:USD", response.assets[1].asset)
        assertEquals("iso4217:BRL", response.assets[2].asset)
        assertNotNull(response.assets[2].countryCodes)
    }
}
