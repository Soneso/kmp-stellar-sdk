// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep31

import com.soneso.stellar.sdk.sep.sep31.Sep31FeeDetails
import com.soneso.stellar.sdk.sep.sep31.Sep31InfoResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31Refunds
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionStatus
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Sep31ResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ==================== Fixtures ====================

    // SEP-31 v3.1.0 GET /info — spec example (sep-0031.md L386-400) captured 2026-05-16
    private val infoResponseJson = """
        {
          "receive": {
            "USDC": {
              "quotes_supported": true,
              "quotes_required": false,
              "fee_fixed": 5,
              "fee_percent": 1,
              "min_amount": 0.1,
              "max_amount": 1000,
              "funding_methods": ["SEPA", "SWIFT"],
              "sep12": {
                "sender": { "types": {} },
                "receiver": { "types": {} }
              }
            }
          }
        }
    """.trimIndent()

    // SEP-31 v3.1.0 receive asset with sep12 KYC required — synthesized for fixture purposes captured 2026-05-16
    private val infoWithSep12Json = """
        {
          "receive": {
            "USDC": {
              "quotes_supported": true,
              "fee_fixed": 5,
              "fee_percent": 1,
              "min_amount": 0.1,
              "max_amount": 1000,
              "funding_methods": ["SEPA"],
              "sep12": {
                "sender": {
                  "types": {
                    "sep31-sender": { "description": "Individual sender required to provide KYC" }
                  }
                },
                "receiver": {
                  "types": {
                    "sep31-receiver": { "description": "Individual recipient" }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    // SEP-31 v3.1.0 transaction object — SYNTHESIZED from spec sep-0031.md L786-802 with status="pending_sender" — captured 2026-05-16
    private val pendingSenderTransactionJson = """
        {
          "transaction": {
            "id": "82fhs729f63dh0v4",
            "status": "pending_sender",
            "status_eta": 3600,
            "status_message": "Awaiting Stellar payment from Sending Anchor.",
            "stellar_account_id": "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
            "stellar_memo": "123456789",
            "stellar_memo_type": "id",
            "amount_in": "18.34",
            "amount_out": "18.24",
            "amount_fee": "0.1",
            "started_at": "2017-03-20T17:05:32Z"
          }
        }
    """.trimIndent()

    // SEP-31 v3.1.0 transaction object — SYNTHESIZED merging spec L838-866 (refunds) + L870-891 (quote/fee_details) — captured 2026-05-16
    private val completedTransactionJson = """
        {
          "transaction": {
            "id": "82fhs729f63dh0v4",
            "status": "completed",
            "amount_in": "100.00",
            "amount_in_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            "amount_out": "500.00",
            "amount_out_asset": "iso4217:BRL",
            "amount_fee": "10.00",
            "amount_fee_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            "fee_details": {
              "total": "10.00",
              "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "details": [
                { "name": "Service fee", "amount": "8.00" },
                { "name": "BRL deposit fee", "amount": "2.00" }
              ]
            },
            "quote_id": "de762cda-a193-4961-861e-57b31fed6eb3",
            "started_at": "2017-03-20T17:05:32Z",
            "completed_at": "2017-03-20T17:08:31Z",
            "stellar_transaction_id": "b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020",
            "external_transaction_id": "ABCDEFG1234567890",
            "stellar_account_id": "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
            "stellar_memo": "123456789",
            "stellar_memo_type": "id",
            "refunds": {
              "amount_refunded": "10.00",
              "amount_fee": "5.00",
              "payments": [
                {
                  "id": "54321ab047a193c6fda1c47f5962cbcca8708d79b87089ababd57532c21c5402",
                  "amount": "10.00",
                  "fee": "5.00"
                }
              ]
            }
          }
        }
    """.trimIndent()

    // ==================== Info response ====================

    @Test
    fun infoResponse_validJson_parsesAllFields() = runTest {
        val obj = json.decodeFromString(JsonObject.serializer(), infoResponseJson)
        val response = Sep31InfoResponse.fromJson(obj)

        assertEquals(1, response.receiveAssets.size)
        val usdc = response.receiveAssets["USDC"]
        assertNotNull(usdc)
        assertEquals(true, usdc.quotesSupported)
        assertEquals(false, usdc.quotesRequired)
        assertEquals(5.0, usdc.feeFixed)
        assertEquals(1.0, usdc.feePercent)
        assertEquals(0.1, usdc.minAmount)
        assertEquals(1000.0, usdc.maxAmount)
        val methods = usdc.fundingMethods
        assertNotNull(methods)
        assertEquals(listOf("SEPA", "SWIFT"), methods)
    }

    @Test
    fun infoResponse_emptyReceive_parsesAsEmptyMap() = runTest {
        val emptyJson = """{"receive":{}}"""
        val obj = json.decodeFromString(JsonObject.serializer(), emptyJson)
        val response = Sep31InfoResponse.fromJson(obj)
        assertEquals(emptyMap(), response.receiveAssets)
    }

    @Test
    fun receiveAssetInfo_omittedSep12_parsesWithEmptySenderAndReceiverMaps() = runTest {
        // Spec marks the per-asset `sep12` object as "(Deprecated, optional)" — an
        // anchor that requires no KYC may legitimately omit it entirely. The parser
        // must default to empty sender/receiver maps in that case rather than
        // rejecting the response as malformed.
        val noSep12Json = """
            {
              "receive": {
                "USDC": {
                  "min_amount": 1.0,
                  "max_amount": 100.0,
                  "funding_methods": ["SEPA"]
                }
              }
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), noSep12Json)
        val response = Sep31InfoResponse.fromJson(obj)
        val usdc = response.receiveAssets.getValue("USDC")
        assertEquals(emptyMap(), usdc.sep12Info.senderTypes)
        assertEquals(emptyMap(), usdc.sep12Info.receiverTypes)
        assertEquals(1.0, usdc.minAmount)
        assertEquals(100.0, usdc.maxAmount)
        assertEquals(listOf("SEPA"), usdc.fundingMethods)
    }

    @Test
    fun receiveAssetInfo_fundingMethodsPresent_parsesArray() = runTest {
        val obj = json.decodeFromString(JsonObject.serializer(), infoResponseJson)
        val response = Sep31InfoResponse.fromJson(obj)
        val usdc = response.receiveAssets.getValue("USDC")
        val methods = usdc.fundingMethods
        assertNotNull(methods)
        assertEquals(2, methods.size)
        assertEquals("SEPA", methods[0])
        assertEquals("SWIFT", methods[1])
    }

    @Test
    fun receiveAssetInfo_deprecatedFieldsPresent_parsesAndExposes() = runTest {
        val withDeprecated = """
            {
              "receive": {
                "USDC": {
                  "sender_sep12_type": "sep31-sender",
                  "receiver_sep12_type": "sep31-receiver",
                  "sep12": { "sender": { "types": {} }, "receiver": { "types": {} } }
                }
              }
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), withDeprecated)
        val response = Sep31InfoResponse.fromJson(obj)
        val usdc = response.receiveAssets.getValue("USDC")
        @Suppress("DEPRECATION")
        assertEquals("sep31-sender", usdc.senderSep12Type)
        @Suppress("DEPRECATION")
        assertEquals("sep31-receiver", usdc.receiverSep12Type)
    }

    @Test
    fun receiveAssetInfo_numericFieldAsString_parsesToDouble() = runTest {
        // Anchors sometimes emit quoted-string numbers; doubleOrNull on a lenient
        // parser must still yield a Double.
        val withStringNumeric = """
            {
              "receive": {
                "USDC": {
                  "min_amount": "0.5",
                  "max_amount": "500",
                  "sep12": { "sender": { "types": {} }, "receiver": { "types": {} } }
                }
              }
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), withStringNumeric)
        val response = Sep31InfoResponse.fromJson(obj)
        val usdc = response.receiveAssets.getValue("USDC")
        assertEquals(0.5, usdc.minAmount)
        assertEquals(500.0, usdc.maxAmount)
    }

    // ==================== PostTransactionsResponse ====================

    @Test
    fun postTransactionsResponse_validJson_parsesAllFields() = runTest {
        val responseJson = """
            {
              "id": "11111111-1111-1111-1111-111111111111",
              "stellar_account_id": "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
              "stellar_memo_type": "hash",
              "stellar_memo": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), responseJson)
        val response = Sep31PostTransactionsResponse.fromJson(obj)

        assertEquals("11111111-1111-1111-1111-111111111111", response.id)
        assertEquals("GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H", response.stellarAccountId)
        assertEquals("hash", response.stellarMemoType)
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", response.stellarMemo)
    }

    @Test
    fun postTransactionsResponse_pendingReceiverNoStellarFields_parsesWithNulls() = runTest {
        val minimalJson = """{"id":"abc-123"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), minimalJson)
        val response = Sep31PostTransactionsResponse.fromJson(obj)

        assertEquals("abc-123", response.id)
        assertNull(response.stellarAccountId)
        assertNull(response.stellarMemoType)
        assertNull(response.stellarMemo)
    }

    @Test
    fun postTransactionsResponse_missingId_throwsSep31InvalidResponseException() = runTest {
        val noIdJson = """{"stellar_account_id":"GABC"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), noIdJson)
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31PostTransactionsResponse.fromJson(obj)
        }
    }

    // ==================== TransactionResponse ====================

    @Test
    fun transactionResponse_wrappedShape_unwrapsAndParses() = runTest {
        val obj = json.decodeFromString(JsonObject.serializer(), pendingSenderTransactionJson)
        val response = Sep31TransactionResponse.fromJson(obj)

        assertEquals("82fhs729f63dh0v4", response.id)
        assertEquals("pending_sender", response.status)
        assertEquals(3600L, response.statusEta)
        assertEquals("Awaiting Stellar payment from Sending Anchor.", response.statusMessage)
        assertEquals("GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H", response.stellarAccountId)
        assertEquals("123456789", response.stellarMemo)
        assertEquals("id", response.stellarMemoType)
        assertEquals("18.34", response.amountIn)
        assertEquals("18.24", response.amountOut)
        @Suppress("DEPRECATION")
        assertEquals("0.1", response.amountFee)
        assertEquals("2017-03-20T17:05:32Z", response.startedAt)
    }

    @Test
    fun transactionResponse_flatShape_throwsSep31InvalidResponseException() = runTest {
        // A flat object without the "transaction" wrapper must be rejected per SEP-31 §"GET Transaction".
        val flatJson = """
            {
              "id": "82fhs729f63dh0v4",
              "status": "pending_sender"
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), flatJson)
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31TransactionResponse.fromJson(obj)
        }
    }

    @Test
    fun transactionResponse_missingId_throwsSep31InvalidResponseException() = runTest {
        val noIdJson = """{"transaction":{"status":"pending_sender"}}"""
        val obj = json.decodeFromString(JsonObject.serializer(), noIdJson)
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31TransactionResponse.fromJson(obj)
        }
    }

    @Test
    fun transactionResponse_missingStatus_throwsSep31InvalidResponseException() = runTest {
        val noStatusJson = """{"transaction":{"id":"82fhs729f63dh0v4"}}"""
        val obj = json.decodeFromString(JsonObject.serializer(), noStatusJson)
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31TransactionResponse.fromJson(obj)
        }
    }

    @Test
    fun transactionResponse_withFeeDetails_parsesBreakdown() = runTest {
        val obj = json.decodeFromString(JsonObject.serializer(), completedTransactionJson)
        val response = Sep31TransactionResponse.fromJson(obj)

        val feeDetails = response.feeDetails
        assertNotNull(feeDetails)
        assertEquals("10.00", feeDetails.total)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", feeDetails.asset)
        val details = feeDetails.details
        assertNotNull(details)
        assertEquals(2, details.size)
        assertEquals("Service fee", details[0].name)
        assertEquals("8.00", details[0].amount)
        assertEquals("BRL deposit fee", details[1].name)
        assertEquals("2.00", details[1].amount)
    }

    @Test
    fun transactionResponse_withRefunds_parsesPayments() = runTest {
        val obj = json.decodeFromString(JsonObject.serializer(), completedTransactionJson)
        val response = Sep31TransactionResponse.fromJson(obj)

        val refunds = response.refunds
        assertNotNull(refunds)
        assertEquals("10.00", refunds.amountRefunded)
        assertEquals("5.00", refunds.amountFee)
        assertEquals(1, refunds.payments.size)
        val payment = refunds.payments[0]
        assertEquals("54321ab047a193c6fda1c47f5962cbcca8708d79b87089ababd57532c21c5402", payment.id)
        assertEquals("10.00", payment.amount)
        assertEquals("5.00", payment.fee)
    }

    @Test
    fun transactionResponse_withRequiredInfoUpdates_leafValuesArePrimitives() = runTest {
        val withInfoUpdates = """
            {
              "transaction": {
                "id": "82fhs729f63dh0v4",
                "status": "pending_transaction_info_update",
                "required_info_updates": {
                  "transaction": {
                    "receiver_account_number": { "description": "The receiver's bank account number" },
                    "receiver_routing_number": { "description": "The receiver's routing number" }
                  }
                }
              }
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), withInfoUpdates)
        val response = Sep31TransactionResponse.fromJson(obj)

        val updates = response.requiredInfoUpdates
        assertNotNull(updates)
        assertNoJsonElementLeaves(updates)
    }

    @Test
    fun transactionResponse_completedQuoteShape_parsesAmountInOutAssets() = runTest {
        val obj = json.decodeFromString(JsonObject.serializer(), completedTransactionJson)
        val response = Sep31TransactionResponse.fromJson(obj)

        assertEquals("100.00", response.amountIn)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.amountInAsset)
        assertEquals("500.00", response.amountOut)
        assertEquals("iso4217:BRL", response.amountOutAsset)
        assertEquals("de762cda-a193-4961-861e-57b31fed6eb3", response.quoteId)
        assertEquals("2017-03-20T17:08:31Z", response.completedAt)
        assertEquals("b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020", response.stellarTransactionId)
        assertEquals("ABCDEFG1234567890", response.externalTransactionId)
    }

    // ==================== FeeDetails ====================

    @Test
    fun feeDetails_missingDetails_parsesWithNull() = runTest {
        val feeJson = """{"total":"5.00","asset":"stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), feeJson)
        val feeDetails = Sep31FeeDetails.fromJson(obj)

        assertEquals("5.00", feeDetails.total)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", feeDetails.asset)
        assertNull(feeDetails.details)
    }

    // ==================== Refunds ====================

    @Test
    fun refunds_validJson_parsesAggregateAndPayments() = runTest {
        val refundsJson = """
            {
              "amount_refunded": "10.00",
              "amount_fee": "5.00",
              "payments": [
                {
                  "id": "54321ab047a193c6fda1c47f5962cbcca8708d79b87089ababd57532c21c5402",
                  "amount": "10.00",
                  "fee": "5.00"
                }
              ]
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), refundsJson)
        val refunds = Sep31Refunds.fromJson(obj)

        assertEquals("10.00", refunds.amountRefunded)
        assertEquals("5.00", refunds.amountFee)
        assertEquals(1, refunds.payments.size)
        assertEquals("54321ab047a193c6fda1c47f5962cbcca8708d79b87089ababd57532c21c5402", refunds.payments[0].id)
        assertEquals("10.00", refunds.payments[0].amount)
        assertEquals("5.00", refunds.payments[0].fee)
    }

    // ==================== Sep12 types info ====================

    @Test
    fun infoResponse_withSep12Types_parsesSenderAndReceiverDescriptions() = runTest {
        val obj = json.decodeFromString(JsonObject.serializer(), infoWithSep12Json)
        val response = Sep31InfoResponse.fromJson(obj)
        val usdc = response.receiveAssets.getValue("USDC")
        val senderTypes = usdc.sep12Info.senderTypes
        assertEquals(1, senderTypes.size)
        assertEquals("Individual sender required to provide KYC", senderTypes["sep31-sender"])
        val receiverTypes = usdc.sep12Info.receiverTypes
        assertEquals(1, receiverTypes.size)
        assertEquals("Individual recipient", receiverTypes["sep31-receiver"])
    }

    // ==================== TransactionStatus ====================

    @Test
    fun transactionStatus_fromString_knownValue_returnsEnum() = runTest {
        val status = Sep31TransactionStatus.fromString("pending_sender")
        assertEquals(Sep31TransactionStatus.PENDING_SENDER, status)

        val completed = Sep31TransactionStatus.fromString("completed")
        assertEquals(Sep31TransactionStatus.COMPLETED, completed)
    }

    @Test
    fun transactionStatus_fromString_unknownValue_returnsNull() = runTest {
        val result = Sep31TransactionStatus.fromString("future_new_status")
        assertNull(result)
    }

    @Test
    fun transactionStatus_fromString_caseSensitive_returnsNullForUppercase() = runTest {
        val result = Sep31TransactionStatus.fromString("COMPLETED")
        assertNull(result)
    }
}

/**
 * Recursively asserts that no value within [map] (or within any nested map or list) is a
 * [JsonElement] instance. Fails the test at the first offending leaf.
 */
private fun assertNoJsonElementLeaves(map: Map<String, Any?>) {
    for ((key, value) in map) {
        assertNoJsonElementLeavesValue(key, value)
    }
}

private fun assertNoJsonElementLeavesValue(path: String, value: Any?) {
    if (value is JsonElement) {
        kotlin.test.fail("JsonElement found at path '$path': $value")
    }
    @Suppress("UNCHECKED_CAST")
    when (value) {
        is Map<*, *> -> (value as Map<String, Any?>).forEach { (k, v) ->
            assertNoJsonElementLeavesValue("$path.$k", v)
        }
        is List<*> -> value.forEachIndexed { i, v ->
            assertNoJsonElementLeavesValue("$path[$i]", v)
        }
    }
}
