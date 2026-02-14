package com.soneso.stellar.sdk.unitTests.sep.sep38

import com.soneso.stellar.sdk.sep.sep38.Sep38DeliveryMethod
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Sep38DeliveryMethodTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testBankTransferDeliveryMethod() {
        val methodJson = """
        {
            "name": "ACH",
            "description": "Send USD directly to the Anchor's bank account via ACH"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("ACH", method.name)
        assertEquals("Send USD directly to the Anchor's bank account via ACH", method.description)
    }

    @Test
    fun testMobileMoneyDeliveryMethod() {
        val methodJson = """
        {
            "name": "PIX",
            "description": "Send BRL directly to the account of your choice via PIX"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("PIX", method.name)
        assertEquals("Send BRL directly to the account of your choice via PIX", method.description)
    }

    @Test
    fun testCashPickupDeliveryMethod() {
        val methodJson = """
        {
            "name": "cash",
            "description": "Pick up cash BRL at one of our payout locations"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("cash", method.name)
        assertEquals("Pick up cash BRL at one of our payout locations", method.description)
    }

    @Test
    fun testWireTransferDeliveryMethod() {
        val methodJson = """
        {
            "name": "WIRE",
            "description": "International wire transfer"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("WIRE", method.name)
        assertEquals("International wire transfer", method.description)
    }

    @Test
    fun testSepaDeliveryMethod() {
        val methodJson = """
        {
            "name": "SEPA",
            "description": "SEPA bank transfer within the European Union"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("SEPA", method.name)
        assertEquals("SEPA bank transfer within the European Union", method.description)
    }

    @Test
    fun testSwiftDeliveryMethod() {
        val methodJson = """
        {
            "name": "SWIFT",
            "description": "SWIFT international wire transfer"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("SWIFT", method.name)
        assertEquals("SWIFT international wire transfer", method.description)
    }

    @Test
    fun testCryptoDeliveryMethod() {
        val methodJson = """
        {
            "name": "crypto",
            "description": "Cryptocurrency transfer to your wallet address"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("crypto", method.name)
        assertEquals("Cryptocurrency transfer to your wallet address", method.description)
    }

    @Test
    fun testPaypalDeliveryMethod() {
        val methodJson = """
        {
            "name": "paypal",
            "description": "PayPal payment to your account"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("paypal", method.name)
        assertEquals("PayPal payment to your account", method.description)
    }

    @Test
    fun testLongDescriptionDeliveryMethod() {
        val longDescription = "This is a very long description for a delivery method that includes " +
                "multiple details about how the transfer will work, what information is needed, " +
                "processing times, fees, and other relevant information for the end user."
        
        val methodJson = """
        {
            "name": "complex_method",
            "description": "$longDescription"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("complex_method", method.name)
        assertEquals(longDescription, method.description)
    }

    @Test
    fun testDeliveryMethodSerialization() {
        val method = Sep38DeliveryMethod(
            name = "ACH",
            description = "Send USD directly to the Anchor's bank account via ACH"
        )

        val serialized = json.encodeToString(Sep38DeliveryMethod.serializer(), method)
        val deserialized = json.decodeFromString<Sep38DeliveryMethod>(serialized)

        assertEquals(method.name, deserialized.name)
        assertEquals(method.description, deserialized.description)
    }

    @Test
    fun testDeliveryMethodEquality() {
        val method1 = Sep38DeliveryMethod("ACH", "Bank transfer via ACH")
        val method2 = Sep38DeliveryMethod("ACH", "Bank transfer via ACH")
        val method3 = Sep38DeliveryMethod("WIRE", "Wire transfer")

        assertEquals(method1, method2)
        assertEquals(method1.hashCode(), method2.hashCode())
        assertTrue(method1 != method3)
    }

    @Test
    fun testDeliveryMethodToString() {
        val method = Sep38DeliveryMethod("PIX", "Instant payment via PIX")
        val toStringResult = method.toString()
        assertTrue(toStringResult.contains("PIX"))
        assertTrue(toStringResult.contains("Instant payment via PIX"))
    }

    @Test
    fun testEmptyNameAndDescription() {
        val methodJson = """
        {
            "name": "",
            "description": ""
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("", method.name)
        assertEquals("", method.description)
    }

    @Test
    fun testUnicodeCharactersInDescription() {
        val methodJson = """
        {
            "name": "international",
            "description": "International transfer supporting €, ¥, £, and other currencies"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("international", method.name)
        assertEquals("International transfer supporting €, ¥, £, and other currencies", method.description)
    }

    @Test
    fun testSpecialCharactersInName() {
        val methodJson = """
        {
            "name": "method_with_underscores-and-dashes.and.dots",
            "description": "A method name with various special characters"
        }
        """.trimIndent()

        val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
        assertEquals("method_with_underscores-and-dashes.and.dots", method.name)
        assertEquals("A method name with various special characters", method.description)
    }

    @Test
    fun testVariousDeliveryMethodNames() {
        val methodNames = listOf(
            "ACH",
            "WIRE",
            "SEPA",
            "SWIFT",
            "PIX",
            "cash",
            "crypto",
            "paypal",
            "venmo",
            "zelle",
            "interac",
            "e_transfer",
            "mobile_money",
            "bank_deposit"
        )

        for (methodName in methodNames) {
            val methodJson = """
            {
                "name": "$methodName",
                "description": "Description for $methodName method"
            }
            """.trimIndent()

            val method = json.decodeFromString<Sep38DeliveryMethod>(methodJson)
            assertEquals(methodName, method.name)
            assertEquals("Description for $methodName method", method.description)
        }
    }

    @Test
    fun testDeliveryMethodInList() {
        val methodsJson = """
        [
            {
                "name": "ACH",
                "description": "Bank transfer via ACH"
            },
            {
                "name": "WIRE",
                "description": "International wire transfer"
            },
            {
                "name": "cash",
                "description": "Cash pickup at agent location"
            }
        ]
        """.trimIndent()

        val methods = json.decodeFromString<List<Sep38DeliveryMethod>>(methodsJson)
        assertEquals(3, methods.size)
        assertEquals("ACH", methods[0].name)
        assertEquals("Bank transfer via ACH", methods[0].description)
        assertEquals("WIRE", methods[1].name)
        assertEquals("International wire transfer", methods[1].description)
        assertEquals("cash", methods[2].name)
        assertEquals("Cash pickup at agent location", methods[2].description)
    }
}