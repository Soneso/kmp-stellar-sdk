// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep24

import com.soneso.stellar.sdk.sep.sep24.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

/**
 * Tests for the SEP-24 response models: their wire field names, optional-field
 * defaults, and the transaction status helpers exposed on [Sep24Transaction].
 *
 * Each model is built through its primary constructor, serialized, checked against
 * the field names SEP-24 defines, and decoded back. A property whose @SerialName
 * disagrees with the specification breaks either the key assertion or the round trip.
 */
class Sep24ResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun encodedKeys(encoded: String): Set<String> =
        json.parseToJsonElement(encoded).jsonObject.keys

    // ========== Info Response ==========

    @Test
    fun testInfoResponseSerialForm() = runTest {
        val original = Sep24InfoResponse(
            depositAssets = mapOf(
                "USDC" to Sep24AssetInfo(
                    enabled = true,
                    minAmount = "1",
                    maxAmount = "10000",
                    feeFixed = "5",
                    feePercent = "1",
                    feeMinimum = "0.1"
                )
            ),
            withdrawAssets = mapOf(
                "USDC" to Sep24AssetInfo(
                    enabled = false,
                    minAmount = "10",
                    maxAmount = "5000"
                )
            ),
            feeEndpoint = Sep24FeeEndpointInfo(enabled = true, authenticationRequired = true),
            features = Sep24Features(accountCreation = false, claimableBalances = true)
        )

        val encoded = json.encodeToString(original)

        assertEquals(setOf("deposit", "withdraw", "fee", "features"), encodedKeys(encoded))
        assertEquals(original, json.decodeFromString<Sep24InfoResponse>(encoded))

        val depositKeys = json.parseToJsonElement(encoded)
            .jsonObject["deposit"]!!.jsonObject["USDC"]!!.jsonObject.keys
        assertEquals(
            setOf("enabled", "min_amount", "max_amount", "fee_fixed", "fee_percent", "fee_minimum"),
            depositKeys
        )

        val feeKeys = json.parseToJsonElement(encoded).jsonObject["fee"]!!.jsonObject.keys
        assertEquals(setOf("enabled", "authentication_required"), feeKeys)

        val featureKeys = json.parseToJsonElement(encoded).jsonObject["features"]!!.jsonObject.keys
        assertEquals(setOf("account_creation", "claimable_balances"), featureKeys)
    }

    @Test
    fun testInfoResponseOmittedSections() = runTest {
        val decoded = json.decodeFromString<Sep24InfoResponse>("{}")

        assertNull(decoded.depositAssets)
        assertNull(decoded.withdrawAssets)
        assertNull(decoded.feeEndpoint)
        assertNull(decoded.features)
        assertEquals(Sep24InfoResponse(), decoded)
    }

    @Test
    fun testAssetInfoOptionalFieldsDefaultToNull() = runTest {
        val decoded = json.decodeFromString<Sep24AssetInfo>("""{"enabled": true}""")

        assertTrue(decoded.enabled)
        assertNull(decoded.minAmount)
        assertNull(decoded.maxAmount)
        assertNull(decoded.feeFixed)
        assertNull(decoded.feePercent)
        assertNull(decoded.feeMinimum)
        assertEquals(Sep24AssetInfo(enabled = true), decoded)
    }

    @Test
    fun testFeeEndpointInfoAuthenticationDefaultsToFalse() = runTest {
        val decoded = json.decodeFromString<Sep24FeeEndpointInfo>("""{"enabled": true}""")

        assertTrue(decoded.enabled)
        assertFalse(decoded.authenticationRequired)
        assertEquals(Sep24FeeEndpointInfo(enabled = true), decoded)
    }

    @Test
    fun testFeaturesDefaults() = runTest {
        val decoded = json.decodeFromString<Sep24Features>("{}")

        assertTrue(decoded.accountCreation)
        assertFalse(decoded.claimableBalances)
        assertEquals(Sep24Features(), decoded)
    }

    // ========== Interactive And Fee Responses ==========

    @Test
    fun testInteractiveResponseSerialForm() = runTest {
        val original = Sep24InteractiveResponse(
            type = "interactive_customer_info_needed",
            url = "https://anchor.example.com/sep24/interactive?token=abc",
            id = "82fhs729f63dh0v4"
        )

        val encoded = json.encodeToString(original)

        assertEquals(setOf("type", "url", "id"), encodedKeys(encoded))
        assertEquals(original, json.decodeFromString<Sep24InteractiveResponse>(encoded))
    }

    @Test
    fun testFeeResponseSerialForm() = runTest {
        val original = Sep24FeeResponse(fee = "5.42")

        val encoded = json.encodeToString(original)

        assertEquals(setOf("fee"), encodedKeys(encoded))
        assertEquals(original, json.decodeFromString<Sep24FeeResponse>(encoded))

        assertNull(json.decodeFromString<Sep24FeeResponse>("{}").fee)
    }

    // ========== Transaction ==========

    @Test
    @Suppress("DEPRECATION")
    fun testTransactionSerialFormWithAllFields() = runTest {
        val original = Sep24Transaction(
            id = "82fhs729f63dh0v4",
            kind = "deposit",
            status = "pending_user_transfer_start",
            statusEta = 3600,
            kycVerified = true,
            moreInfoUrl = "https://anchor.example.com/tx/82fhs729f63dh0v4",
            amountIn = "18.34",
            amountInAsset = "iso4217:USD",
            amountOut = "18.24",
            amountOutAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            amountFee = "0.1",
            amountFeeAsset = "iso4217:USD",
            feeDetails = Sep24FeeDetails(
                total = "0.1",
                asset = "iso4217:USD",
                breakdown = listOf(
                    Sep24FeeDetail(
                        name = "Service fee",
                        amount = "0.1",
                        description = "Charged by the anchor"
                    )
                )
            ),
            quoteId = "de762cda-a193-4961-861e-57b31fed6eb3",
            startedAt = "2021-04-30T07:00:00Z",
            completedAt = "2021-04-30T09:00:00Z",
            updatedAt = "2021-04-30T08:00:00Z",
            userActionRequiredBy = "2021-04-30T10:00:00Z",
            stellarTransactionId = "17a670bc424ff5ce3b386dbfaae9990b66a2a37b4fbe51547e8794962a3f9e6a",
            externalTransactionId = "2dd16cb409513026fbe7defc0c6f826c2d2c65c3da993f747d09bf7dafd31093",
            message = "Deposit is being processed",
            refunded = false,
            refunds = Sep24Refunds(
                amountRefunded = "10",
                amountFee = "5",
                payments = listOf(
                    Sep24RefundPayment(
                        id = "104201",
                        idType = "external",
                        amount = "10",
                        fee = "5"
                    )
                )
            ),
            from = "GDRHDSTZ4PK6VI3WL224XBJFEB6CUATD3RRTRDFHDCJHFDFJKLMNOPQR",
            to = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            depositMemo = "12345",
            depositMemoType = "id",
            claimableBalanceId =
                "00000000178826fbfe339e1f5c53417c6fedfe2c05e8bec14303143ec46b38981b09c3f9",
            withdrawAnchorAccount = "GCTTGO5ABSTHABXWL2FMHPZ2XFOZDXJYJN5CKFRKXMPAAWZW3Y3JZ3JK",
            withdrawMemo = "67890",
            withdrawMemoType = "text"
        )

        val encoded = json.encodeToString(original)

        assertEquals(
            setOf(
                "id", "kind", "status", "status_eta", "kyc_verified", "more_info_url",
                "amount_in", "amount_in_asset", "amount_out", "amount_out_asset",
                "amount_fee", "amount_fee_asset", "fee_details", "quote_id",
                "started_at", "completed_at", "updated_at", "user_action_required_by",
                "stellar_transaction_id", "external_transaction_id", "message",
                "refunded", "refunds", "from", "to",
                "deposit_memo", "deposit_memo_type", "claimable_balance_id",
                "withdraw_anchor_account", "withdraw_memo", "withdraw_memo_type"
            ),
            encodedKeys(encoded)
        )

        val decoded = json.decodeFromString<Sep24Transaction>(encoded)
        assertEquals(original, decoded)
        assertEquals(Sep24TransactionStatus.PENDING_USER_TRANSFER_START, decoded.getStatusEnum())
        assertFalse(decoded.isTerminal())
    }

    @Test
    fun testTransactionOptionalFieldsDefaultToNull() = runTest {
        val minimal = Sep24Transaction(
            id = "tx-minimal",
            kind = "withdrawal",
            status = "completed"
        )

        val encoded = json.encodeToString(minimal)

        assertEquals(setOf("id", "kind", "status"), encodedKeys(encoded))

        val decoded = json.decodeFromString<Sep24Transaction>(encoded)
        assertEquals(minimal, decoded)
        assertNull(decoded.statusEta)
        assertNull(decoded.kycVerified)
        assertNull(decoded.feeDetails)
        assertNull(decoded.refunds)
        assertNull(decoded.withdrawAnchorAccount)
        assertTrue(decoded.isTerminal())
        assertEquals(Sep24TransactionStatus.COMPLETED, decoded.getStatusEnum())
    }

    @Test
    fun testTransactionUnrecognizedStatusHasNoEnum() = runTest {
        val transaction = Sep24Transaction(
            id = "tx-unknown-status",
            kind = "deposit",
            status = "pending_anchor_review"
        )

        assertNull(transaction.getStatusEnum())
        assertFalse(transaction.isTerminal())
    }

    @Test
    fun testTransactionWrapperSerialForms() = runTest {
        val transaction = Sep24Transaction(
            id = "tx-wrapper",
            kind = "deposit",
            status = "incomplete"
        )

        val single = Sep24TransactionResponse(transaction = transaction)
        val singleEncoded = json.encodeToString(single)
        assertEquals(setOf("transaction"), encodedKeys(singleEncoded))
        assertEquals(single, json.decodeFromString<Sep24TransactionResponse>(singleEncoded))

        val list = Sep24TransactionsResponse(transactions = listOf(transaction))
        val listEncoded = json.encodeToString(list)
        assertEquals(setOf("transactions"), encodedKeys(listEncoded))
        assertEquals(list, json.decodeFromString<Sep24TransactionsResponse>(listEncoded))
    }

    // ========== Fees And Refunds ==========

    @Test
    fun testFeeDetailsSerialForm() = runTest {
        val original = Sep24FeeDetails(
            total = "4.42",
            asset = "iso4217:USD",
            breakdown = listOf(
                Sep24FeeDetail(
                    name = "Service fee",
                    amount = "1.00",
                    description = "Processing fee"
                ),
                Sep24FeeDetail(name = "Network fee", amount = "3.42")
            )
        )

        val encoded = json.encodeToString(original)

        assertEquals(setOf("total", "asset", "breakdown"), encodedKeys(encoded))

        val decoded = json.decodeFromString<Sep24FeeDetails>(encoded)
        assertEquals(original, decoded)
        assertNull(decoded.breakdown!![1].description)

        val breakdownKeys = json.parseToJsonElement(encoded)
            .jsonObject["breakdown"]!!.jsonArray[0].jsonObject.keys
        assertEquals(setOf("name", "amount", "description"), breakdownKeys)
    }

    @Test
    fun testFeeDetailsWithoutBreakdown() = runTest {
        val decoded = json.decodeFromString<Sep24FeeDetails>(
            """{"total": "1.00", "asset": "iso4217:USD"}"""
        )

        assertEquals("1.00", decoded.total)
        assertEquals("iso4217:USD", decoded.asset)
        assertNull(decoded.breakdown)
    }

    @Test
    fun testRefundsSerialForm() = runTest {
        val original = Sep24Refunds(
            amountRefunded = "10",
            amountFee = "5",
            payments = listOf(
                Sep24RefundPayment(
                    id = "b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020",
                    idType = "stellar",
                    amount = "10",
                    fee = "5"
                )
            )
        )

        val encoded = json.encodeToString(original)

        assertEquals(setOf("amount_refunded", "amount_fee", "payments"), encodedKeys(encoded))
        assertEquals(original, json.decodeFromString<Sep24Refunds>(encoded))

        val paymentKeys = json.parseToJsonElement(encoded)
            .jsonObject["payments"]!!.jsonArray[0].jsonObject.keys
        assertEquals(setOf("id", "id_type", "amount", "fee"), paymentKeys)
    }
}
