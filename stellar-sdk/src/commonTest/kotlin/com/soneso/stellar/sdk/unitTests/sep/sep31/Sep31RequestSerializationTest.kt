// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep31

import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sep31RequestSerializationTest {

    // ==================== toJson ====================

    @Test
    fun toJson_minimalRequest_includesOnlyRequiredKeys() = runTest {
        val request = Sep31PostTransactionsRequest(
            amount = 50.0,
            assetCode = "USDC",
            fundingMethod = "SWIFT",
        )
        val map = request.toJson()

        assertEquals(50.0, map["amount"])
        assertEquals("USDC", map["asset_code"])
        assertEquals("SWIFT", map["funding_method"])
        // Per SEP-31, the three required keys are amount, asset_code, funding_method.
        assertEquals(3, map.size)
    }

    @Test
    @Suppress("DEPRECATION")
    fun toJson_allNullOptionals_omitsEveryOptionalKey() = runTest {
        // funding_method is required (non-nullable) and therefore not part of the
        // "all optional null" matrix; only the truly optional fields are exercised here.
        val request = Sep31PostTransactionsRequest(
            amount = 100.0,
            assetCode = "USDC",
            fundingMethod = "SWIFT",
            assetIssuer = null,
            destinationAsset = null,
            quoteId = null,
            senderId = null,
            receiverId = null,
            fields = null,
            lang = null,
            refundMemo = null,
            refundMemoType = null,
        )
        @Suppress("DEPRECATION")
        val map = request.toJson()

        assertFalse(map.containsKey("asset_issuer"), "asset_issuer must be absent when null")
        assertFalse(map.containsKey("destination_asset"), "destination_asset must be absent when null")
        assertFalse(map.containsKey("quote_id"), "quote_id must be absent when null")
        assertFalse(map.containsKey("sender_id"), "sender_id must be absent when null")
        assertFalse(map.containsKey("receiver_id"), "receiver_id must be absent when null")
        assertFalse(map.containsKey("fields"), "fields must be absent when null")
        assertFalse(map.containsKey("lang"), "lang must be absent when null")
        assertFalse(map.containsKey("refund_memo"), "refund_memo must be absent when null")
        assertFalse(map.containsKey("refund_memo_type"), "refund_memo_type must be absent when null")
        // funding_method is required — must always be present, even when every optional is null.
        assertTrue(map.containsKey("funding_method"), "funding_method must be present (required)")
    }

    @Test
    fun toJson_quoteIdAndDestinationAsset_bothPresent_serializesBoth() = runTest {
        val request = Sep31PostTransactionsRequest(
            amount = 200.0,
            assetCode = "USDC",
            fundingMethod = "SWIFT",
            quoteId = "de762cda-a193-4961-861e-57b31fed6eb3",
            destinationAsset = "iso4217:BRL",
        )
        val map = request.toJson()

        assertEquals("de762cda-a193-4961-861e-57b31fed6eb3", map["quote_id"])
        assertEquals("iso4217:BRL", map["destination_asset"])
    }

    @Test
    fun toJson_refundMemoWithType_serializesBoth() = runTest {
        val request = Sep31PostTransactionsRequest(
            amount = 100.0,
            assetCode = "USDC",
            fundingMethod = "SWIFT",
            refundMemo = "REFUND-MEMO-123",
            refundMemoType = "text",
        )
        val map = request.toJson()

        assertEquals("REFUND-MEMO-123", map["refund_memo"])
        assertEquals("text", map["refund_memo_type"])
    }

    @Test
    fun toJson_fundingMethodAlwaysSerializesAsSnakeCase() = runTest {
        // Per SEP-31, funding_method is required and must always appear in the
        // serialized body. Verify both the snake_case key and the value pass-through.
        val request = Sep31PostTransactionsRequest(
            amount = 100.0,
            assetCode = "USDC",
            fundingMethod = "SEPA",
        )
        val map = request.toJson()

        assertTrue(map.containsKey("funding_method"), "funding_method key must be present")
        assertEquals("SEPA", map["funding_method"])
    }

    @Test
    fun toJson_deprecatedFields_serializesAsTopLevelFields() = runTest {
        @Suppress("DEPRECATION")
        val request = Sep31PostTransactionsRequest(
            amount = 100.0,
            assetCode = "USDC",
            fundingMethod = "SWIFT",
            fields = mapOf(
                "transaction" to mapOf(
                    "receiver_account_number" to "0987654321",
                ),
            ),
        )
        @Suppress("DEPRECATION")
        val map = request.toJson()

        assertTrue(map.containsKey("fields"), "fields key must be present")
        @Suppress("UNCHECKED_CAST")
        val fieldsMap = map["fields"] as? Map<String, Any?>
        assertFalse(fieldsMap == null, "fields must be a map")
        assertTrue(fieldsMap!!.containsKey("transaction"))
    }

    @Test
    fun toJson_preservesInsertionOrder_deterministicOutput() = runTest {
        val request = Sep31PostTransactionsRequest(
            amount = 100.0,
            assetCode = "USDC",
            fundingMethod = "SWIFT",
            assetIssuer = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            senderId = "11111111-1111-1111-1111-111111111111",
            receiverId = "22222222-2222-2222-2222-222222222222",
        )
        val map = request.toJson()
        val keys = map.keys.toList()

        // amount, asset_code, funding_method, asset_issuer, sender_id, receiver_id — in that order.
        assertEquals("amount", keys[0])
        assertEquals("asset_code", keys[1])
        assertEquals("funding_method", keys[2])
        assertEquals("asset_issuer", keys[3])
        assertEquals("sender_id", keys[4])
        assertEquals("receiver_id", keys[5])
    }
}
