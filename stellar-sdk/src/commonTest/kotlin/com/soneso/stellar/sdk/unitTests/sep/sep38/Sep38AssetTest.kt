package com.soneso.stellar.sdk.unitTests.sep.sep38

import com.soneso.stellar.sdk.sep.sep38.Sep38Asset
import com.soneso.stellar.sdk.sep.sep38.Sep38DeliveryMethod
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Sep38AssetTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testStellarAssetDeserialization() {
        val assetJson = """
        {
            "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
        }
        """.trimIndent()

        val asset = json.decodeFromString<Sep38Asset>(assetJson)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", asset.asset)
        assertNull(asset.sellDeliveryMethods)
        assertNull(asset.buyDeliveryMethods)
        assertNull(asset.countryCodes)
    }

    @Test
    fun testFiatAssetWithDeliveryMethods() {
        val assetJson = """
        {
            "asset": "iso4217:BRL",
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
            ],
            "country_codes": ["BR"]
        }
        """.trimIndent()

        val asset = json.decodeFromString<Sep38Asset>(assetJson)
        assertEquals("iso4217:BRL", asset.asset)
        
        assertNotNull(asset.sellDeliveryMethods)
        assertEquals(3, asset.sellDeliveryMethods!!.size)
        assertEquals("cash", asset.sellDeliveryMethods!![0].name)
        assertEquals("Deposit cash BRL at one of our agent locations.", asset.sellDeliveryMethods!![0].description)
        assertEquals("ACH", asset.sellDeliveryMethods!![1].name)
        assertEquals("PIX", asset.sellDeliveryMethods!![2].name)
        
        assertNotNull(asset.buyDeliveryMethods)
        assertEquals(3, asset.buyDeliveryMethods!!.size)
        assertEquals("cash", asset.buyDeliveryMethods!![0].name)
        assertEquals("Pick up cash BRL at one of our payout locations.", asset.buyDeliveryMethods!![0].description)
        assertEquals("ACH", asset.buyDeliveryMethods!![1].name)
        assertEquals("PIX", asset.buyDeliveryMethods!![2].name)
        
        assertNotNull(asset.countryCodes)
        assertEquals(1, asset.countryCodes!!.size)
        assertEquals("BR", asset.countryCodes!![0])
    }

    @Test
    fun testAssetWithRegionalRestrictions() {
        val assetJson = """
        {
            "asset": "iso4217:USD",
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
            ],
            "country_codes": ["US", "CA", "MX"]
        }
        """.trimIndent()

        val asset = json.decodeFromString<Sep38Asset>(assetJson)
        assertEquals("iso4217:USD", asset.asset)
        
        assertNotNull(asset.sellDeliveryMethods)
        assertEquals(1, asset.sellDeliveryMethods!!.size)
        assertEquals("WIRE", asset.sellDeliveryMethods!![0].name)
        assertEquals("International wire transfer", asset.sellDeliveryMethods!![0].description)
        
        assertNotNull(asset.buyDeliveryMethods)
        assertEquals(1, asset.buyDeliveryMethods!!.size)
        assertEquals("WIRE", asset.buyDeliveryMethods!![0].name)
        assertEquals("International wire transfer", asset.buyDeliveryMethods!![0].description)
        
        assertNotNull(asset.countryCodes)
        assertEquals(3, asset.countryCodes!!.size)
        assertEquals("US", asset.countryCodes!![0])
        assertEquals("CA", asset.countryCodes!![1])
        assertEquals("MX", asset.countryCodes!![2])
    }

    @Test
    fun testAssetWithSellDeliveryMethodsOnly() {
        val assetJson = """
        {
            "asset": "iso4217:EUR",
            "sell_delivery_methods": [
                {
                    "name": "SEPA",
                    "description": "SEPA bank transfer"
                }
            ]
        }
        """.trimIndent()

        val asset = json.decodeFromString<Sep38Asset>(assetJson)
        assertEquals("iso4217:EUR", asset.asset)
        
        assertNotNull(asset.sellDeliveryMethods)
        assertEquals(1, asset.sellDeliveryMethods!!.size)
        assertEquals("SEPA", asset.sellDeliveryMethods!![0].name)
        assertEquals("SEPA bank transfer", asset.sellDeliveryMethods!![0].description)
        
        assertNull(asset.buyDeliveryMethods)
        assertNull(asset.countryCodes)
    }

    @Test
    fun testAssetWithBuyDeliveryMethodsOnly() {
        val assetJson = """
        {
            "asset": "iso4217:JPY",
            "buy_delivery_methods": [
                {
                    "name": "SWIFT",
                    "description": "SWIFT international wire transfer"
                }
            ]
        }
        """.trimIndent()

        val asset = json.decodeFromString<Sep38Asset>(assetJson)
        assertEquals("iso4217:JPY", asset.asset)
        
        assertNull(asset.sellDeliveryMethods)
        
        assertNotNull(asset.buyDeliveryMethods)
        assertEquals(1, asset.buyDeliveryMethods!!.size)
        assertEquals("SWIFT", asset.buyDeliveryMethods!![0].name)
        assertEquals("SWIFT international wire transfer", asset.buyDeliveryMethods!![0].description)
        
        assertNull(asset.countryCodes)
    }

    @Test
    fun testAssetWithCountryCodesOnly() {
        val assetJson = """
        {
            "asset": "stellar:EURT:GAP5LETOV6YIE62YAM56STDANPRDO7ZFDBGSNHJQIYGGKSMOZAHOOS2S",
            "country_codes": ["DE", "FR", "IT", "ES"]
        }
        """.trimIndent()

        val asset = json.decodeFromString<Sep38Asset>(assetJson)
        assertEquals("stellar:EURT:GAP5LETOV6YIE62YAM56STDANPRDO7ZFDBGSNHJQIYGGKSMOZAHOOS2S", asset.asset)
        
        assertNull(asset.sellDeliveryMethods)
        assertNull(asset.buyDeliveryMethods)
        
        assertNotNull(asset.countryCodes)
        assertEquals(4, asset.countryCodes!!.size)
        assertEquals("DE", asset.countryCodes!![0])
        assertEquals("FR", asset.countryCodes!![1])
        assertEquals("IT", asset.countryCodes!![2])
        assertEquals("ES", asset.countryCodes!![3])
    }

    @Test
    fun testEmptyDeliveryMethodsAndCountryCodes() {
        val assetJson = """
        {
            "asset": "iso4217:GBP",
            "sell_delivery_methods": [],
            "buy_delivery_methods": [],
            "country_codes": []
        }
        """.trimIndent()

        val asset = json.decodeFromString<Sep38Asset>(assetJson)
        assertEquals("iso4217:GBP", asset.asset)
        
        assertNotNull(asset.sellDeliveryMethods)
        assertTrue(asset.sellDeliveryMethods!!.isEmpty())
        
        assertNotNull(asset.buyDeliveryMethods)
        assertTrue(asset.buyDeliveryMethods!!.isEmpty())
        
        assertNotNull(asset.countryCodes)
        assertTrue(asset.countryCodes!!.isEmpty())
    }

    @Test
    fun testAssetSerialization() {
        val asset = Sep38Asset(
            asset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sellDeliveryMethods = listOf(
                Sep38DeliveryMethod("ACH", "Bank transfer"),
                Sep38DeliveryMethod("WIRE", "Wire transfer")
            ),
            buyDeliveryMethods = listOf(
                Sep38DeliveryMethod("ACH", "Bank transfer")
            ),
            countryCodes = listOf("US", "CA")
        )

        val serialized = json.encodeToString(Sep38Asset.serializer(), asset)
        val deserialized = json.decodeFromString<Sep38Asset>(serialized)

        assertEquals(asset.asset, deserialized.asset)
        assertEquals(asset.sellDeliveryMethods?.size, deserialized.sellDeliveryMethods?.size)
        assertEquals(asset.buyDeliveryMethods?.size, deserialized.buyDeliveryMethods?.size)
        assertEquals(asset.countryCodes?.size, deserialized.countryCodes?.size)
    }

    @Test
    fun testAssetEquality() {
        val asset1 = Sep38Asset(
            asset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sellDeliveryMethods = listOf(Sep38DeliveryMethod("ACH", "Bank transfer")),
            buyDeliveryMethods = listOf(Sep38DeliveryMethod("ACH", "Bank transfer")),
            countryCodes = listOf("US")
        )

        val asset2 = Sep38Asset(
            asset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sellDeliveryMethods = listOf(Sep38DeliveryMethod("ACH", "Bank transfer")),
            buyDeliveryMethods = listOf(Sep38DeliveryMethod("ACH", "Bank transfer")),
            countryCodes = listOf("US")
        )

        val asset3 = Sep38Asset(
            asset = "iso4217:USD",
            sellDeliveryMethods = null,
            buyDeliveryMethods = null,
            countryCodes = null
        )

        assertEquals(asset1, asset2)
        assertEquals(asset1.hashCode(), asset2.hashCode())
        assertTrue(asset1 != asset3)
    }

    @Test
    fun testAssetToString() {
        val asset = Sep38Asset(
            asset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sellDeliveryMethods = listOf(Sep38DeliveryMethod("ACH", "Bank transfer")),
            buyDeliveryMethods = null,
            countryCodes = listOf("US")
        )

        val toStringResult = asset.toString()
        assertTrue(toStringResult.contains("stellar:USDC"))
        assertTrue(toStringResult.contains("US"))
    }

    @Test
    fun testVariousAssetFormats() {
        val assetFormats = listOf(
            "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            "iso4217:USD",
            "iso4217:EUR",
            "iso4217:BRL",
            "iso4217:JPY",
            "crypto:BTC",
            "crypto:ETH"
        )

        for (assetFormat in assetFormats) {
            val assetJson = """{"asset": "$assetFormat"}"""
            val asset = json.decodeFromString<Sep38Asset>(assetJson)
            assertEquals(assetFormat, asset.asset)
        }
    }
}