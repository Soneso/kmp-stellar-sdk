// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep31

import com.soneso.stellar.sdk.sep.sep31.Sep31FeeDetails
import com.soneso.stellar.sdk.sep.sep31.Sep31FeeDetailsDetails
import com.soneso.stellar.sdk.sep.sep31.Sep31InfoResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31RefundPayment
import com.soneso.stellar.sdk.sep.sep31.Sep31Refunds
import com.soneso.stellar.sdk.sep.sep31.Sep31Sep12TypesInfo
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionStatus
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ==================== Refunds defensive paths ====================

    @Test
    fun refunds_missingAmountRefunded_throwsSep31InvalidResponseException() = runTest {
        val refundsJson = """{"amount_fee":"5.00","payments":[]}"""
        val obj = json.decodeFromString(JsonObject.serializer(), refundsJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31Refunds.fromJson(obj)
        }
        assertTrue(ex.message?.contains("amount_refunded") == true)
    }

    @Test
    fun refunds_missingAmountFee_throwsSep31InvalidResponseException() = runTest {
        val refundsJson = """{"amount_refunded":"10.00","payments":[]}"""
        val obj = json.decodeFromString(JsonObject.serializer(), refundsJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31Refunds.fromJson(obj)
        }
        assertTrue(ex.message?.contains("amount_fee") == true)
    }

    @Test
    fun refunds_missingPayments_throwsSep31InvalidResponseException() = runTest {
        val refundsJson = """{"amount_refunded":"10.00","amount_fee":"5.00"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), refundsJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31Refunds.fromJson(obj)
        }
        assertTrue(ex.message?.contains("payments") == true)
    }

    @Test
    fun refunds_paymentsElementNotObject_throwsSep31InvalidResponseException() = runTest {
        // payments array contains a string instead of a JSON object.
        val refundsJson = """
            {
              "amount_refunded": "10.00",
              "amount_fee": "5.00",
              "payments": ["not-an-object"]
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), refundsJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31Refunds.fromJson(obj)
        }
        assertTrue(ex.message?.contains("Malformed") == true)
    }

    @Test
    fun refunds_amountRefundedNotPrimitive_wrapsAsSep31InvalidResponseException() = runTest {
        // amount_refunded is a JSON object instead of a primitive. The `jsonPrimitive`
        // accessor throws IllegalArgumentException, which the outer IAE catch arm
        // rewraps as Sep31InvalidResponseException.
        val refunds = buildJsonObject {
            put("amount_refunded", buildJsonObject {})
            put("amount_fee", "5.00")
        }
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31Refunds.fromJson(refunds)
        }
        assertTrue(
            ex.message?.contains("Malformed SEP-31 refunds") == true,
            "outer IAE catch must rewrap with the 'Malformed SEP-31 refunds' prefix; was: ${ex.message}",
        )
    }

    // ==================== RefundPayment defensive paths ====================

    @Test
    fun refundPayment_missingId_throwsSep31InvalidResponseException() = runTest {
        val paymentJson = """{"amount":"10.00","fee":"5.00"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), paymentJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31RefundPayment.fromJson(obj)
        }
        assertTrue(ex.message?.contains("'id'") == true)
    }

    @Test
    fun refundPayment_missingAmount_throwsSep31InvalidResponseException() = runTest {
        val paymentJson = """{"id":"abc","fee":"5.00"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), paymentJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31RefundPayment.fromJson(obj)
        }
        assertTrue(ex.message?.contains("'amount'") == true)
    }

    @Test
    fun refundPayment_missingFee_throwsSep31InvalidResponseException() = runTest {
        val paymentJson = """{"id":"abc","amount":"10.00"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), paymentJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31RefundPayment.fromJson(obj)
        }
        assertTrue(ex.message?.contains("'fee'") == true)
    }

    @Test
    fun refundPayment_idNotPrimitive_wrapsAsSep31InvalidResponseException() = runTest {
        // `id` is a JSON object instead of a primitive. The `jsonPrimitive` accessor
        // raises IllegalArgumentException, which the outer IAE catch arm rewraps.
        val payment = buildJsonObject {
            put("id", buildJsonObject {})
            put("amount", "10.00")
            put("fee", "5.00")
        }
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31RefundPayment.fromJson(payment)
        }
        assertTrue(
            ex.message?.contains("Malformed SEP-31 refund payment") == true,
            "outer IAE catch must rewrap with the 'Malformed SEP-31 refund payment' prefix; was: ${ex.message}",
        )
    }

    // ==================== FeeDetails defensive paths ====================

    @Test
    fun feeDetails_missingTotal_throwsSep31InvalidResponseException() = runTest {
        val feeJson = """{"asset":"stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), feeJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31FeeDetails.fromJson(obj)
        }
        assertTrue(ex.message?.contains("'total'") == true)
    }

    @Test
    fun feeDetails_missingAsset_throwsSep31InvalidResponseException() = runTest {
        val feeJson = """{"total":"5.00"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), feeJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31FeeDetails.fromJson(obj)
        }
        assertTrue(ex.message?.contains("'asset'") == true)
    }

    @Test
    fun feeDetails_detailsElementNotObject_throwsSep31InvalidResponseException() = runTest {
        // details array contains a string instead of an object.
        val feeJson = """
            {
              "total": "5.00",
              "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "details": ["not-an-object"]
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), feeJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31FeeDetails.fromJson(obj)
        }
        assertTrue(ex.message?.contains("Malformed") == true)
    }

    @Test
    fun feeDetails_totalNotPrimitive_wrapsAsSep31InvalidResponseException() = runTest {
        // `total` is a JSON object instead of a primitive. The `jsonPrimitive`
        // accessor raises IllegalArgumentException; the outer IAE catch rewraps.
        val fee = buildJsonObject {
            put("total", buildJsonObject {})
            put("asset", "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
        }
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31FeeDetails.fromJson(fee)
        }
        assertTrue(
            ex.message?.contains("Malformed SEP-31 fee details") == true,
            "outer IAE catch must rewrap with the 'Malformed SEP-31 fee details' prefix; was: ${ex.message}",
        )
    }

    @Test
    fun feeDetails_detailsLineItemMissingField_throwsSep31InvalidResponseException() = runTest {
        // A line item missing its `amount` field bubbles up through the outer map call
        // because Sep31FeeDetailsDetails.fromJson throws Sep31InvalidResponseException
        // and that is re-thrown by the outer catch block.
        val feeJson = """
            {
              "total": "5.00",
              "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
              "details": [{"name":"Service fee"}]
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), feeJson)
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31FeeDetails.fromJson(obj)
        }
    }

    // ==================== FeeDetailsDetails defensive paths ====================

    @Test
    fun feeDetailsDetails_validJson_parsesNameAmountAndDescription() = runTest {
        val lineJson = """{"name":"Service fee","amount":"8.00","description":"Anchor service charge"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), lineJson)
        val line = Sep31FeeDetailsDetails.fromJson(obj)
        assertEquals("Service fee", line.name)
        assertEquals("8.00", line.amount)
        assertEquals("Anchor service charge", line.description)
    }

    @Test
    fun feeDetailsDetails_missingName_throwsSep31InvalidResponseException() = runTest {
        val lineJson = """{"amount":"8.00"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), lineJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31FeeDetailsDetails.fromJson(obj)
        }
        assertTrue(ex.message?.contains("'name'") == true)
    }

    @Test
    fun feeDetailsDetails_missingAmount_throwsSep31InvalidResponseException() = runTest {
        val lineJson = """{"name":"Service fee"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), lineJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31FeeDetailsDetails.fromJson(obj)
        }
        assertTrue(ex.message?.contains("'amount'") == true)
    }

    @Test
    fun feeDetailsDetails_nameNotPrimitive_wrapsAsSep31InvalidResponseException() = runTest {
        // `name` is a JSON object instead of a primitive — jsonPrimitive throws IAE
        // which the outer IAE catch rewraps with the 'Malformed' prefix for line items.
        val line = buildJsonObject {
            put("name", buildJsonObject {})
            put("amount", "5.00")
        }
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31FeeDetailsDetails.fromJson(line)
        }
        assertTrue(
            ex.message?.contains("Malformed SEP-31 fee details line item") == true,
            "outer IAE catch must rewrap with the 'Malformed' line-item prefix; was: ${ex.message}",
        )
    }

    @Test
    fun feeDetailsDetails_noDescription_parsesWithNullDescription() = runTest {
        val lineJson = """{"name":"Service fee","amount":"8.00"}"""
        val obj = json.decodeFromString(JsonObject.serializer(), lineJson)
        val line = Sep31FeeDetailsDetails.fromJson(obj)
        assertEquals("Service fee", line.name)
        assertEquals("8.00", line.amount)
        assertNull(line.description)
    }

    // ==================== InfoResponse defensive paths ====================

    @Test
    fun infoResponse_missingReceive_returnsEmptyMap() = runTest {
        val noReceiveJson = """{}"""
        val obj = json.decodeFromString(JsonObject.serializer(), noReceiveJson)
        val response = Sep31InfoResponse.fromJson(obj)
        assertEquals(emptyMap(), response.receiveAssets)
    }

    @Test
    fun infoResponse_receiveEntryNotObject_throwsSep31InvalidResponseException() = runTest {
        // Asset entry under `receive` is a string instead of a JSON object.
        val badJson = """{"receive":{"USDC":"not-an-object"}}"""
        val obj = json.decodeFromString(JsonObject.serializer(), badJson)
        val ex = assertFailsWith<Sep31InvalidResponseException> {
            Sep31InfoResponse.fromJson(obj)
        }
        assertTrue(ex.message?.contains("Malformed entry") == true)
        assertTrue(ex.message?.contains("USDC") == true)
    }

    @Test
    fun receiveAssetInfo_deprecatedFieldsMapPresent_parsedToPrimitiveLeafMap() = runTest {
        // The deprecated `fields` map in `receive[<code>]` must be parsed into a primitive-leaf
        // `Map<String, Any?>` via jsonElementToAny. This exercises the `fieldsObject?.let { obj ->
        // jsonElementToAny(obj) as Map<String, Any?> }` branch.
        val withFields = """
            {
              "receive": {
                "USDC": {
                  "fields": {
                    "transaction": {
                      "receiver_account_number": {
                        "description": "The receiver's bank account number"
                      }
                    }
                  },
                  "sep12": { "sender": { "types": {} }, "receiver": { "types": {} } }
                }
              }
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), withFields)
        val response = Sep31InfoResponse.fromJson(obj)
        val usdc = response.receiveAssets.getValue("USDC")
        @Suppress("DEPRECATION")
        val fields = usdc.fields
        assertNotNull(fields)
        assertNoJsonElementLeaves(fields)
    }

    @Test
    fun infoResponse_receiveEntryMalformedSep12_throwsSep31InvalidResponseException() = runTest {
        // sep12 object exists but `sender` is a string instead of an object, which
        // forces extractTypes to fall through to the safe-cast branch (returns empty),
        // but if the underlying tree is corrupted enough to surface IllegalArgumentException
        // from kotlinx-serialization, the outer fromJson catches it and rethrows.
        // Here we cover the standard malformed-receive path: a primitive at receive[code].
        val badJson = """{"receive":{"USDC":true}}"""
        val obj = json.decodeFromString(JsonObject.serializer(), badJson)
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31InfoResponse.fromJson(obj)
        }
    }

    // ==================== PostTransactionsResponse defensive paths ====================

    @Test
    fun postTransactionsResponse_idFieldWrongType_throwsSep31InvalidResponseException() = runTest {
        // `id` field is present but is an object instead of a primitive. `jsonPrimitive`
        // throws IllegalArgumentException, which the outer catch block rewraps as
        // Sep31InvalidResponseException.
        val badResponse = buildJsonObject {
            put("id", buildJsonObject {})
        }
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31PostTransactionsResponse.fromJson(badResponse)
        }
    }

    // ==================== Sep31Sep12TypesInfo defensive paths ====================

    @Test
    fun sep12TypesInfo_emptyJson_returnsEmptyMaps() = runTest {
        // No sender / receiver keys at all - extractTypes returns emptyMap on each side.
        val emptyJson = """{}"""
        val obj = json.decodeFromString(JsonObject.serializer(), emptyJson)
        val info = Sep31Sep12TypesInfo.fromJson(obj)
        assertEquals(emptyMap(), info.senderTypes)
        assertEquals(emptyMap(), info.receiverTypes)
    }

    @Test
    fun sep12TypesInfo_senderTypesNotObject_skipsAndReturnsEmpty() = runTest {
        // sender.types is a string; extractTypes returns emptyMap for that side.
        val badJson = """{"sender":{"types":"not-an-object"},"receiver":{"types":{}}}"""
        val obj = json.decodeFromString(JsonObject.serializer(), badJson)
        val info = Sep31Sep12TypesInfo.fromJson(obj)
        assertEquals(emptyMap(), info.senderTypes)
        assertEquals(emptyMap(), info.receiverTypes)
    }

    @Test
    fun sep12TypesInfo_typeEntryNotObject_silentlySkipped() = runTest {
        // A type entry whose value is a primitive (not an object) is silently skipped.
        val badJson = """
            {
              "sender": {
                "types": {
                  "sep31-sender": "not-an-object",
                  "valid-type": { "description": "valid description" }
                }
              }
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), badJson)
        val info = Sep31Sep12TypesInfo.fromJson(obj)
        assertEquals(1, info.senderTypes.size)
        assertEquals("valid description", info.senderTypes["valid-type"])
    }

    @Test
    fun sep12TypesInfo_typeMissingDescription_silentlySkipped() = runTest {
        // A type whose description field is absent or non-string is silently skipped.
        val badJson = """
            {
              "sender": {
                "types": {
                  "no-desc-type": {},
                  "valid-type": { "description": "valid description" }
                }
              }
            }
        """.trimIndent()
        val obj = json.decodeFromString(JsonObject.serializer(), badJson)
        val info = Sep31Sep12TypesInfo.fromJson(obj)
        assertEquals(1, info.senderTypes.size)
        assertEquals("valid description", info.senderTypes["valid-type"])
    }

    // ==================== TransactionResponse defensive paths ====================

    @Test
    fun transactionResponse_idFieldWrongType_throwsSep31InvalidResponseException() = runTest {
        // `id` field is present but is a nested object. `jsonPrimitive` raises
        // IllegalArgumentException which propagates through the outer catch and
        // is rewrapped as Sep31InvalidResponseException.
        val tx = buildJsonObject {
            put("transaction", buildJsonObject {
                put("id", buildJsonObject {})
                put("status", "pending_sender")
            })
        }
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31TransactionResponse.fromJson(tx)
        }
    }

    @Test
    fun transactionResponse_feeDetailsNested_propagatesNestedFailureAsSep31InvalidResponseException() = runTest {
        // The transaction is otherwise valid but the nested fee_details object is missing
        // required fields. The inner fromJson throws Sep31InvalidResponseException, which
        // the outer try/catch re-throws unchanged.
        val tx = buildJsonObject {
            put("transaction", buildJsonObject {
                put("id", "tx-1")
                put("status", "completed")
                put("fee_details", buildJsonObject {
                    put("total", "5.00")
                    // asset is missing
                })
            })
        }
        assertFailsWith<Sep31InvalidResponseException> {
            Sep31TransactionResponse.fromJson(tx)
        }
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
