@file:Suppress("DEPRECATION")
package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.*
import com.soneso.stellar.sdk.horizon.responses.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Comprehensive test for the OperationResponseSerializer polymorphic dispatch.
 * Tests that each known operation type string is correctly deserialized to the right subclass.
 */
class OperationResponseSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Helper to make a minimal JSON for a given type with type-specific required fields
    private fun makeJson(type: String, extraFields: String = ""): String {
        val extra = if (extraFields.isNotEmpty()) ",$extraFields" else ""
        return """
        {
            ${OperationTestHelpers.baseFieldsJson(type)}$extra
        }
        """
    }

    @Test
    fun testCreateAccount() {
        val jsonStr = makeJson("create_account", """
            "account": "${OperationTestHelpers.TEST_ACCOUNT}",
            "funder": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "starting_balance": "100.0000000"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<CreateAccountOperationResponse>(response)
        assertEquals("create_account", response.type)
    }

    @Test
    fun testAccountMerge() {
        val jsonStr = makeJson("account_merge", """
            "account": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "into": "${OperationTestHelpers.TEST_ACCOUNT}"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<AccountMergeOperationResponse>(response)
    }

    @Test
    fun testPayment() {
        val jsonStr = makeJson("payment", """
            "amount": "10.0",
            "asset_type": "native",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<PaymentOperationResponse>(response)
    }

    @Test
    fun testPathPaymentStrictReceive() {
        val jsonStr = makeJson("path_payment_strict_receive", """
            "amount": "10.0",
            "source_amount": "5.0",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}",
            "asset_type": "native",
            "source_asset_type": "native",
            "source_max": "11.0",
            "path": []
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<PathPaymentStrictReceiveOperationResponse>(response)
    }

    @Test
    fun testPathPaymentStrictSend() {
        val jsonStr = makeJson("path_payment_strict_send", """
            "amount": "10.0",
            "source_amount": "5.0",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}",
            "asset_type": "native",
            "source_asset_type": "native",
            "destination_min": "9.0",
            "path": []
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<PathPaymentStrictSendOperationResponse>(response)
    }

    @Test
    fun testChangeTrust() {
        val jsonStr = makeJson("change_trust", """
            "trustor": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "limit": "1000.0"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<ChangeTrustOperationResponse>(response)
    }

    @Test
    fun testAllowTrust() {
        val jsonStr = makeJson("allow_trust", """
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "trustee": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "authorize": true
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<AllowTrustOperationResponse>(response)
    }

    @Test
    fun testSetTrustLineFlags() {
        val jsonStr = makeJson("set_trust_line_flags", """
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<SetTrustLineFlagsOperationResponse>(response)
    }

    @Test
    fun testManageSellOffer() {
        val jsonStr = makeJson("manage_sell_offer", """
            "offer_id": 1,
            "amount": "10.0",
            "price": "1.5",
            "price_r": {"n": 3, "d": 2},
            "buying_asset_type": "native",
            "selling_asset_type": "native"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<ManageSellOfferOperationResponse>(response)
    }

    @Test
    fun testManageBuyOffer() {
        val jsonStr = makeJson("manage_buy_offer", """
            "offer_id": 1,
            "amount": "10.0",
            "price": "1.5",
            "price_r": {"n": 3, "d": 2},
            "buying_asset_type": "native",
            "selling_asset_type": "native"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<ManageBuyOfferOperationResponse>(response)
    }

    @Test
    fun testCreatePassiveSellOffer() {
        val jsonStr = makeJson("create_passive_sell_offer", """
            "offer_id": 1,
            "amount": "10.0",
            "price": "1.5",
            "price_r": {"n": 3, "d": 2},
            "buying_asset_type": "native",
            "selling_asset_type": "native"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<CreatePassiveSellOfferOperationResponse>(response)
    }

    @Test
    fun testSetOptions() {
        val jsonStr = makeJson("set_options", """
            "home_domain": "stellar.org"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<SetOptionsOperationResponse>(response)
    }

    @Test
    fun testManageData() {
        val jsonStr = makeJson("manage_data", """
            "name": "key",
            "value": "val"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<ManageDataOperationResponse>(response)
    }

    @Test
    fun testBumpSequence() {
        val jsonStr = makeJson("bump_sequence", """
            "bump_to": 123456
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<BumpSequenceOperationResponse>(response)
    }

    @Test
    fun testCreateClaimableBalance() {
        val jsonStr = makeJson("create_claimable_balance", """
            "asset": "native",
            "amount": "100.0",
            "claimants": [{"destination": "${OperationTestHelpers.TEST_ACCOUNT}", "predicate": {"unconditional": true}}]
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<CreateClaimableBalanceOperationResponse>(response)
    }

    @Test
    fun testClaimClaimableBalance() {
        val jsonStr = makeJson("claim_claimable_balance", """
            "balance_id": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072",
            "claimant": "${OperationTestHelpers.TEST_ACCOUNT}"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<ClaimClaimableBalanceOperationResponse>(response)
    }

    @Test
    fun testClawbackClaimableBalance() {
        val jsonStr = makeJson("clawback_claimable_balance", """
            "balance_id": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<ClawbackClaimableBalanceOperationResponse>(response)
    }

    @Test
    fun testClawback() {
        val jsonStr = makeJson("clawback", """
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "amount": "100.0",
            "from": "${OperationTestHelpers.TEST_ACCOUNT}"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<ClawbackOperationResponse>(response)
    }

    @Test
    fun testBeginSponsoringFutureReserves() {
        val jsonStr = makeJson("begin_sponsoring_future_reserves", """
            "sponsored_id": "${OperationTestHelpers.TEST_ACCOUNT}"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<BeginSponsoringFutureReservesOperationResponse>(response)
    }

    @Test
    fun testEndSponsoringFutureReserves() {
        val jsonStr = makeJson("end_sponsoring_future_reserves", """
            "begin_sponsor": "${OperationTestHelpers.TEST_ACCOUNT}"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<EndSponsoringFutureReservesOperationResponse>(response)
    }

    @Test
    fun testRevokeSponsorship() {
        val jsonStr = makeJson("revoke_sponsorship", """
            "account_id": "${OperationTestHelpers.TEST_ACCOUNT}"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<RevokeSponsorshipOperationResponse>(response)
    }

    @Test
    fun testLiquidityPoolDeposit() {
        val jsonStr = makeJson("liquidity_pool_deposit", """
            "liquidity_pool_id": "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7",
            "reserves_max": [{"asset": "native", "amount": "100.0"}],
            "min_price": "0.5",
            "min_price_r": {"n": 1, "d": 2},
            "max_price": "2.0",
            "max_price_r": {"n": 2, "d": 1},
            "reserves_deposited": [{"asset": "native", "amount": "80.0"}],
            "shares_received": "100.0"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<LiquidityPoolDepositOperationResponse>(response)
    }

    @Test
    fun testLiquidityPoolWithdraw() {
        val jsonStr = makeJson("liquidity_pool_withdraw", """
            "liquidity_pool_id": "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7",
            "reserves_min": [{"asset": "native", "amount": "100.0"}],
            "reserves_received": [{"asset": "native", "amount": "120.0"}],
            "shares": "200.0"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<LiquidityPoolWithdrawOperationResponse>(response)
    }

    @Test
    fun testInvokeHostFunction() {
        val jsonStr = makeJson("invoke_host_function", """
            "function": "HostFunctionTypeInvokeContract"
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<InvokeHostFunctionOperationResponse>(response)
    }

    @Test
    fun testExtendFootprintTTL() {
        val jsonStr = makeJson("extend_footprint_ttl", """
            "extend_to": 500000
        """.trimIndent())
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<ExtendFootprintTTLOperationResponse>(response)
    }

    @Test
    fun testRestoreFootprint() {
        val jsonStr = makeJson("restore_footprint")
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<RestoreFootprintOperationResponse>(response)
    }

    @Test
    fun testInflation() {
        val jsonStr = makeJson("inflation")
        val response = json.decodeFromString<OperationResponse>(jsonStr)
        assertIs<InflationOperationResponse>(response)
    }

    @Test
    fun testUnknownTypeThrows() {
        val jsonStr = makeJson("unknown_type_xyz")
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<OperationResponse>(jsonStr)
        }
    }

    @Test
    fun testMissingTypeThrows() {
        val jsonStr = """
        {
            "id": "12345",
            "source_account": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "paging_token": "12345",
            "created_at": "2023-01-15T12:00:00Z",
            "transaction_hash": "abc123",
            "transaction_successful": true,
            ${OperationTestHelpers.LINKS_JSON}
        }
        """
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<OperationResponse>(jsonStr)
        }
    }

    @Test
    fun testBaseFieldsPreservedAcrossAllTypes() {
        // Verify that base fields are always accessible from the polymorphic type
        val jsonStr = makeJson("inflation")
        val response = json.decodeFromString<OperationResponse>(jsonStr)

        assertEquals(OperationTestHelpers.TEST_ID, response.id)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.sourceAccount)
        assertEquals(OperationTestHelpers.TEST_PAGING_TOKEN, response.pagingToken)
        assertEquals(OperationTestHelpers.TEST_CREATED_AT, response.createdAt)
        assertEquals(OperationTestHelpers.TEST_TX_HASH, response.transactionHash)
        assertTrue(response.transactionSuccessful)
        assertEquals("inflation", response.type)
        assertNotNull(response.links)
        assertNull(response.transaction)
    }

    @Test
    fun testLinksDeserialization() {
        val jsonStr = makeJson("inflation")
        val response = json.decodeFromString<OperationResponse>(jsonStr)

        assertEquals("https://horizon.stellar.org/operations/12345/effects", response.links.effects.href)
        assertEquals("https://horizon.stellar.org/operations/12345", response.links.self.href)
        assertEquals("https://horizon.stellar.org/transactions/abc123", response.links.transaction.href)
    }

    // =====================================================================
    // Base-property coverage tests (merged from OperationBasePropertiesCoverageTest)
    // =====================================================================

    @Test
    fun testAccountMergeOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("account_merge")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "account": "${OperationTestHelpers.TEST_ACCOUNT}",
            "account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "into": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "into_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "into_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val r = json.decodeFromString<AccountMergeOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "account_merge")
        
        // Specific properties
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.account)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.accountMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.accountMuxedId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, r.into)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.intoMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.intoMuxedId)
    }

    @Test
    fun testAllowTrustOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("allow_trust")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "trustee": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "trustee_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "trustee_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "authorize": true,
            "authorize_to_maintain_liabilities": false
        }
        """
        val r = json.decodeFromString<AllowTrustOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "allow_trust")
        
        // Specific properties
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.trustor)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, r.trustee)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.trusteeMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.trusteeMuxedId)
        assertEquals("credit_alphanum4", r.assetType)
        assertEquals("USD", r.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.assetIssuer)
        assertEquals(true, r.authorize)
        assertEquals(false, r.authorizeToMaintainLiabilities)
    }

    @Test
    fun testBeginSponsoringFutureReservesOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("begin_sponsoring_future_reserves")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "sponsored_id": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val r = json.decodeFromString<BeginSponsoringFutureReservesOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "begin_sponsoring_future_reserves")
        
        // Specific properties
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.sponsoredId)
    }

    @Test
    fun testBumpSequenceOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("bump_sequence")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "bump_to": "999"
        }
        """
        val r = json.decodeFromString<BumpSequenceOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "bump_sequence")
        
        // Specific properties
        assertEquals(999L, r.bumpTo)
    }

    @Test
    fun testChangeTrustOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("change_trust")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "trustor_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "trustor_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "trustee": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "limit": "1000.0000000",
            "liquidity_pool_id": "test_pool_id"
        }
        """
        val r = json.decodeFromString<ChangeTrustOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "change_trust")
        
        // Specific properties
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.trustor)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.trustorMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.trustorMuxedId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, r.trustee)
        assertEquals("credit_alphanum4", r.assetType)
        assertEquals("USD", r.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.assetIssuer)
        assertEquals("1000.0000000", r.limit)
        assertEquals("test_pool_id", r.liquidityPoolId)
    }

    @Test
    fun testClaimClaimableBalanceOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("claim_claimable_balance")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "balance_id": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072",
            "claimant": "${OperationTestHelpers.TEST_ACCOUNT}",
            "claimant_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "claimant_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val r = json.decodeFromString<ClaimClaimableBalanceOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "claim_claimable_balance")
        
        // Specific properties
        assertEquals("00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072", r.balanceId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.claimant)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.claimantMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.claimantMuxedId)
    }

    @Test
    fun testClawbackClaimableBalanceOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("clawback_claimable_balance")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "balance_id": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"
        }
        """
        val r = json.decodeFromString<ClawbackClaimableBalanceOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "clawback_claimable_balance")
        
        // Specific properties
        assertEquals("00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072", r.balanceId)
    }

    @Test
    fun testClawbackOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("clawback")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "amount": "100.0000000",
            "from": "${OperationTestHelpers.TEST_ACCOUNT}",
            "from_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "from_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val r = json.decodeFromString<ClawbackOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "clawback")
        
        // Specific properties
        assertEquals("credit_alphanum4", r.assetType)
        assertEquals("USD", r.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.assetIssuer)
        assertEquals("100.0000000", r.amount)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.from)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.fromMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.fromMuxedId)
    }

    @Test
    fun testCreateAccountOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_account")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "account": "${OperationTestHelpers.TEST_ACCOUNT}",
            "funder": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "funder_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "funder_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "starting_balance": "10.0000000"
        }
        """
        val r = json.decodeFromString<CreateAccountOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "create_account")
        
        // Specific properties
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.account)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, r.funder)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.funderMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.funderMuxedId)
        assertEquals("10.0000000", r.startingBalance)
    }

    @Test
    fun testCreateClaimableBalanceOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_claimable_balance")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "sponsor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "asset": "native",
            "amount": "100.0000000",
            "claimants": [
                {
                    "destination": "${OperationTestHelpers.TEST_ACCOUNT_2}",
                    "predicate": {
                        "unconditional": true
                    }
                }
            ]
        }
        """
        val r = json.decodeFromString<CreateClaimableBalanceOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "create_claimable_balance")
        
        // Specific properties
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.sponsor)
        assertEquals("native", r.asset)
        assertEquals("100.0000000", r.amount)
        assertNotNull(r.claimants)
        assertEquals(1, r.claimants.size)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, r.claimants[0].destination)
        assertNotNull(r.claimants[0].predicate)
    }

    @Test
    fun testCreatePassiveSellOfferOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_passive_sell_offer")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "offer_id": "12345",
            "amount": "100.0000000",
            "price": "0.5000000",
            "price_r": {
                "n": "1",
                "d": "2"
            },
            "buying_asset_type": "credit_alphanum4",
            "buying_asset_code": "USD",
            "buying_asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "selling_asset_type": "native"
        }
        """
        val r = json.decodeFromString<CreatePassiveSellOfferOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "create_passive_sell_offer")
        
        // Specific properties
        assertEquals(12345L, r.offerId)
        assertEquals("100.0000000", r.amount)
        assertEquals("0.5000000", r.price)
        assertNotNull(r.priceR)
        assertEquals(1L, r.priceR.numerator)
        assertEquals(2L, r.priceR.denominator)
        assertEquals("credit_alphanum4", r.buyingAssetType)
        assertEquals("USD", r.buyingAssetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.buyingAssetIssuer)
        assertEquals("native", r.sellingAssetType)
        assertNull(r.sellingAssetCode)
        assertNull(r.sellingAssetIssuer)
    }

    @Test
    fun testEndSponsoringFutureReservesOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("end_sponsoring_future_reserves")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "begin_sponsor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "begin_sponsor_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "begin_sponsor_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val r = json.decodeFromString<EndSponsoringFutureReservesOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "end_sponsoring_future_reserves")
        
        // Specific properties
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.beginSponsor)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.beginSponsorMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.beginSponsorMuxedId)
    }

    @Test
    fun testExtendFootprintTTLOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("extend_footprint_ttl")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "extend_to": "100000"
        }
        """
        val r = json.decodeFromString<ExtendFootprintTTLOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "extend_footprint_ttl")
        
        // Specific properties
        assertEquals(100000L, r.extendTo)
    }

    @Test
    fun testInflationOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("inflation")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val r = json.decodeFromString<InflationOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "inflation")
        
        // No specific properties for InflationOperationResponse
    }

    @Test
    fun testInvokeHostFunctionOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("invoke_host_function")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "function": "HostFunctionTypeInvokeContract",
            "parameters": [
                {
                    "type": "Address",
                    "value": "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFCT4"
                }
            ],
            "address": "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFCT4",
            "salt": "7573657220736565643a206d7920757365722073656564",
            "asset_balance_changes": [
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "USD",
                    "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
                    "type": "transfer",
                    "from": "${OperationTestHelpers.TEST_ACCOUNT}",
                    "to": "${OperationTestHelpers.TEST_ACCOUNT_2}",
                    "amount": "100.0000000"
                }
            ]
        }
        """
        val r = json.decodeFromString<InvokeHostFunctionOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "invoke_host_function")
        
        // Specific properties
        assertEquals("HostFunctionTypeInvokeContract", r.function)
        assertNotNull(r.parameters)
        assertEquals(1, r.parameters!!.size)
        // Access HostFunctionParameter properties
        assertEquals("Address", r.parameters!![0].type)
        assertEquals("CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFCT4", r.parameters!![0].value)
        assertEquals("CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFCT4", r.address)
        assertEquals("7573657220736565643a206d7920757365722073656564", r.salt)
        assertNotNull(r.assetBalanceChanges)
        assertEquals(1, r.assetBalanceChanges!!.size)
        // Access AssetContractBalanceChange properties
        val change = r.assetBalanceChanges!![0]
        assertEquals("credit_alphanum4", change.assetType)
        assertEquals("USD", change.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, change.assetIssuer)
        assertEquals("transfer", change.type)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, change.from)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, change.to)
        assertEquals("100.0000000", change.amount)
        assertNull(change.destinationMuxedId)
    }

    @Test
    fun testLiquidityPoolDepositOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("liquidity_pool_deposit")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "liquidity_pool_id": "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7",
            "reserves_max": [
                {
                    "asset": "native",
                    "amount": "1000.0000000"
                },
                {
                    "asset": "USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    "amount": "2000.0000000"
                }
            ],
            "min_price": "0.5000000",
            "min_price_r": {
                "n": "1",
                "d": "2"
            },
            "max_price": "2.0000000",
            "max_price_r": {
                "n": "2",
                "d": "1"
            },
            "reserves_deposited": [
                {
                    "asset": "native",
                    "amount": "500.0000000"
                },
                {
                    "asset": "USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    "amount": "1000.0000000"
                }
            ],
            "shares_received": "707.1067812"
        }
        """
        val r = json.decodeFromString<LiquidityPoolDepositOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "liquidity_pool_deposit")
        
        // Specific properties
        assertEquals("dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7", r.liquidityPoolId)
        assertNotNull(r.reservesMax)
        assertEquals(2, r.reservesMax.size)
        // Access AssetAmount properties
        assertEquals("native", r.reservesMax[0].asset)
        assertEquals("1000.0000000", r.reservesMax[0].amount)
        assertEquals("USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", r.reservesMax[1].asset)
        assertEquals("2000.0000000", r.reservesMax[1].amount)
        assertEquals("0.5000000", r.minPrice)
        assertNotNull(r.minPriceR)
        assertEquals(1L, r.minPriceR.numerator)
        assertEquals(2L, r.minPriceR.denominator)
        assertEquals("2.0000000", r.maxPrice)
        assertNotNull(r.maxPriceR)
        assertEquals(2L, r.maxPriceR.numerator)
        assertEquals(1L, r.maxPriceR.denominator)
        assertNotNull(r.reservesDeposited)
        assertEquals(2, r.reservesDeposited.size)
        assertEquals("native", r.reservesDeposited[0].asset)
        assertEquals("500.0000000", r.reservesDeposited[0].amount)
        assertEquals("707.1067812", r.sharesReceived)
    }

    @Test
    fun testLiquidityPoolWithdrawOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("liquidity_pool_withdraw")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "liquidity_pool_id": "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7",
            "reserves_min": [
                {
                    "asset": "native",
                    "amount": "400.0000000"
                },
                {
                    "asset": "USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    "amount": "800.0000000"
                }
            ],
            "reserves_received": [
                {
                    "asset": "native",
                    "amount": "500.0000000"
                },
                {
                    "asset": "USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    "amount": "1000.0000000"
                }
            ],
            "shares": "707.1067812"
        }
        """
        val r = json.decodeFromString<LiquidityPoolWithdrawOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "liquidity_pool_withdraw")
        
        // Specific properties
        assertEquals("dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7", r.liquidityPoolId)
        assertNotNull(r.reservesMin)
        assertEquals(2, r.reservesMin.size)
        assertEquals("native", r.reservesMin[0].asset)
        assertEquals("400.0000000", r.reservesMin[0].amount)
        assertNotNull(r.reservesReceived)
        assertEquals(2, r.reservesReceived.size)
        assertEquals("native", r.reservesReceived[0].asset)
        assertEquals("500.0000000", r.reservesReceived[0].amount)
        assertEquals("707.1067812", r.shares)
    }

    @Test
    fun testManageBuyOfferOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("manage_buy_offer")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "offer_id": "12345",
            "amount": "100.0000000",
            "price": "0.5000000",
            "price_r": {
                "n": "1",
                "d": "2"
            },
            "buying_asset_type": "credit_alphanum4",
            "buying_asset_code": "USD",
            "buying_asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "selling_asset_type": "native"
        }
        """
        val r = json.decodeFromString<ManageBuyOfferOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "manage_buy_offer")
        
        // Specific properties
        assertEquals(12345L, r.offerId)
        assertEquals("100.0000000", r.amount)
        assertEquals("0.5000000", r.price)
        assertNotNull(r.priceR)
        assertEquals(1L, r.priceR.numerator)
        assertEquals(2L, r.priceR.denominator)
        assertEquals("credit_alphanum4", r.buyingAssetType)
        assertEquals("USD", r.buyingAssetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.buyingAssetIssuer)
        assertEquals("native", r.sellingAssetType)
        assertNull(r.sellingAssetCode)
        assertNull(r.sellingAssetIssuer)
    }

    @Test
    fun testManageDataOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("manage_data")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "name": "test_data",
            "value": "dGVzdCB2YWx1ZQ=="
        }
        """
        val r = json.decodeFromString<ManageDataOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "manage_data")
        
        // Specific properties
        assertEquals("test_data", r.name)
        assertEquals("dGVzdCB2YWx1ZQ==", r.value)
    }

    @Test
    fun testManageSellOfferOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("manage_sell_offer")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "offer_id": "12345",
            "amount": "100.0000000",
            "price": "0.5000000",
            "price_r": {
                "n": "1",
                "d": "2"
            },
            "buying_asset_type": "credit_alphanum4",
            "buying_asset_code": "USD",
            "buying_asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "selling_asset_type": "native"
        }
        """
        val r = json.decodeFromString<ManageSellOfferOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "manage_sell_offer")
        
        // Specific properties
        assertEquals(12345L, r.offerId)
        assertEquals("100.0000000", r.amount)
        assertEquals("0.5000000", r.price)
        assertNotNull(r.priceR)
        assertEquals(1L, r.priceR.numerator)
        assertEquals(2L, r.priceR.denominator)
        assertEquals("credit_alphanum4", r.buyingAssetType)
        assertEquals("USD", r.buyingAssetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.buyingAssetIssuer)
        assertEquals("native", r.sellingAssetType)
        assertNull(r.sellingAssetCode)
        assertNull(r.sellingAssetIssuer)
    }

    @Test
    fun testPathPaymentStrictReceiveOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("path_payment_strict_receive")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "amount": "100.0000000",
            "source_amount": "50.0000000",
            "from": "${OperationTestHelpers.TEST_ACCOUNT}",
            "from_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "from_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "to_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "to_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "source_asset_type": "native",
            "path": [
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "EUR",
                    "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}"
                }
            ],
            "source_max": "60.0000000"
        }
        """
        val r = json.decodeFromString<PathPaymentStrictReceiveOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "path_payment_strict_receive")
        
        // Base path payment properties
        assertPathPaymentBaseProperties(r)
        
        // Specific properties
        assertEquals("60.0000000", r.sourceMax)
    }

    @Test
    fun testPathPaymentStrictSendOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("path_payment_strict_send")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "amount": "100.0000000",
            "source_amount": "50.0000000",
            "from": "${OperationTestHelpers.TEST_ACCOUNT}",
            "from_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "from_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "to_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "to_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "source_asset_type": "native",
            "path": [
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "EUR",
                    "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}"
                }
            ],
            "destination_min": "95.0000000"
        }
        """
        val r = json.decodeFromString<PathPaymentStrictSendOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "path_payment_strict_send")
        
        // Base path payment properties
        assertPathPaymentBaseProperties(r)
        
        // Specific properties
        assertEquals("95.0000000", r.destinationMin)
    }

    @Test
    fun testPaymentOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("payment")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "amount": "100.0000000",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "from": "${OperationTestHelpers.TEST_ACCOUNT}",
            "from_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "from_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "to_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "to_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val r = json.decodeFromString<PaymentOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "payment")
        
        // Specific properties
        assertEquals("100.0000000", r.amount)
        assertEquals("credit_alphanum4", r.assetType)
        assertEquals("USD", r.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.assetIssuer)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.from)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.fromMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.fromMuxedId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, r.to)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.toMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.toMuxedId)
    }

    @Test
    fun testRestoreFootprintOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("restore_footprint")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val r = json.decodeFromString<RestoreFootprintOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "restore_footprint")
        
        // No specific properties for RestoreFootprintOperationResponse
    }

    @Test
    fun testRevokeSponsorshipOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("revoke_sponsorship")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "account_id": "${OperationTestHelpers.TEST_ACCOUNT}",
            "claimable_balance_id": "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072",
            "data_account_id": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "data_name": "test_data",
            "offer_id": "12345",
            "trustline_account_id": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "trustline_asset": "USD:${OperationTestHelpers.TEST_ACCOUNT_3}",
            "signer_account_id": "${OperationTestHelpers.TEST_ACCOUNT}",
            "signer_key": "test_signer_key"
        }
        """
        val r = json.decodeFromString<RevokeSponsorshipOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "revoke_sponsorship")
        
        // Specific properties
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.accountId)
        assertEquals("00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072", r.claimableBalanceId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, r.dataAccountId)
        assertEquals("test_data", r.dataName)
        assertEquals(12345L, r.offerId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.trustlineAccountId)
        assertEquals("USD:${OperationTestHelpers.TEST_ACCOUNT_3}", r.trustlineAsset)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.signerAccountId)
        assertEquals("test_signer_key", r.signerKey)
    }

    @Test
    fun testSetOptionsOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("set_options")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "low_threshold": 1,
            "med_threshold": 2,
            "high_threshold": 3,
            "inflation_dest": "${OperationTestHelpers.TEST_ACCOUNT}",
            "home_domain": "stellar.org",
            "signer_key": "test_signer",
            "signer_weight": 1,
            "master_key_weight": 1,
            "clear_flags": [2, 4],
            "clear_flags_s": ["AUTH_REQUIRED", "AUTH_REVOCABLE"],
            "set_flags": [1, 8],
            "set_flags_s": ["AUTH_REQUIRED_FLAG", "AUTH_CLAWBACK_ENABLED_FLAG"]
        }
        """
        val r = json.decodeFromString<SetOptionsOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "set_options")
        
        // Specific properties
        assertEquals(1, r.lowThreshold)
        assertEquals(2, r.medThreshold)
        assertEquals(3, r.highThreshold)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.inflationDestination)
        assertEquals("stellar.org", r.homeDomain)
        assertEquals("test_signer", r.signerKey)
        assertEquals(1, r.signerWeight)
        assertEquals(1, r.masterKeyWeight)
        assertNotNull(r.clearFlags)
        assertEquals(listOf(2, 4), r.clearFlags)
        assertNotNull(r.clearFlagStrings)
        assertEquals(listOf("AUTH_REQUIRED", "AUTH_REVOCABLE"), r.clearFlagStrings)
        assertNotNull(r.setFlags)
        assertEquals(listOf(1, 8), r.setFlags)
        assertNotNull(r.setFlagStrings)
        assertEquals(listOf("AUTH_REQUIRED_FLAG", "AUTH_CLAWBACK_ENABLED_FLAG"), r.setFlagStrings)
    }

    @Test
    fun testSetTrustLineFlagsOperationCoverage() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("set_trust_line_flags")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_3}",
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "clear_flags": [2, 4],
            "clear_flags_s": ["AUTHORIZED", "AUTHORIZED_TO_MAINTAIN_LIABILITIES"],
            "set_flags": [1],
            "set_flags_s": ["AUTHORIZED_FLAG"]
        }
        """
        val r = json.decodeFromString<SetTrustLineFlagsOperationResponse>(jsonString)
        
        // Base properties
        assertBaseProperties(r, "set_trust_line_flags")
        
        // Specific properties
        assertEquals("credit_alphanum4", r.assetType)
        assertEquals("USD", r.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.assetIssuer)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.trustor)
        assertNotNull(r.clearFlags)
        assertEquals(listOf(2, 4), r.clearFlags)
        assertNotNull(r.clearFlagStrings)
        assertEquals(listOf("AUTHORIZED", "AUTHORIZED_TO_MAINTAIN_LIABILITIES"), r.clearFlagStrings)
        assertNotNull(r.setFlags)
        assertEquals(listOf(1), r.setFlags)
        assertNotNull(r.setFlagStrings)
        assertEquals(listOf("AUTHORIZED_FLAG"), r.setFlagStrings)
    }

    // =====================================================================
    // Private helpers (merged from OperationBasePropertiesCoverageTest)
    // =====================================================================

    /**
     * Helper method to assert all base properties common to all OperationResponse classes.
     * This ensures that all base property getters are executed for code coverage.
     */
    private fun assertBaseProperties(r: OperationResponse, expectedType: String) {
        // Base properties from OperationResponse
        assertEquals(OperationTestHelpers.TEST_ID, r.id)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, r.sourceAccount)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.sourceAccountMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.sourceAccountMuxedId)
        assertEquals(OperationTestHelpers.TEST_PAGING_TOKEN, r.pagingToken)
        assertEquals(OperationTestHelpers.TEST_CREATED_AT, r.createdAt)
        assertEquals(OperationTestHelpers.TEST_TX_HASH, r.transactionHash)
        assertTrue(r.transactionSuccessful)
        assertEquals(expectedType, r.type)
        
        // Links properties (accessing all 5 links to ensure Links class coverage)
        assertNotNull(r.links)
        assertNotNull(r.links.self)
        assertNotNull(r.links.effects)
        assertNotNull(r.links.precedes)
        assertNotNull(r.links.succeeds)
        assertNotNull(r.links.transaction)
        assertEquals("https://horizon.stellar.org/operations/12345", r.links.self.href)
        assertEquals("https://horizon.stellar.org/operations/12345/effects", r.links.effects.href)
        assertEquals("https://horizon.stellar.org/effects?cursor=12345&order=asc", r.links.precedes.href)
        assertEquals("https://horizon.stellar.org/effects?cursor=12345&order=desc", r.links.succeeds.href)
        assertEquals("https://horizon.stellar.org/transactions/abc123", r.links.transaction.href)
        
        assertNull(r.transaction)
    }

    /**
     * Helper method to assert all path payment base properties.
     * This ensures that all PathPaymentBaseOperationResponse property getters are executed.
     */
    private fun assertPathPaymentBaseProperties(r: PathPaymentBaseOperationResponse) {
        assertEquals("100.0000000", r.amount)
        assertEquals("50.0000000", r.sourceAmount)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, r.from)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.fromMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.fromMuxedId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, r.to)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, r.toMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, r.toMuxedId)
        assertEquals("credit_alphanum4", r.assetType)
        assertEquals("USD", r.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, r.assetIssuer)
        assertEquals("native", r.sourceAssetType)
        assertNull(r.sourceAssetCode)
        assertNull(r.sourceAssetIssuer)
        assertNotNull(r.path)
        assertEquals(1, r.path.size)
        // Access PathAsset properties
        val pathAsset = r.path[0]
        assertEquals("credit_alphanum4", pathAsset.assetType)
        assertEquals("EUR", pathAsset.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_3, pathAsset.assetIssuer)
    }
}
