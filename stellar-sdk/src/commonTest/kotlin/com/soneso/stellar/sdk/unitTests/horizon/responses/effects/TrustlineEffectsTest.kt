@file:Suppress("DEPRECATION")

package com.soneso.stellar.sdk.unitTests.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.LINKS_JSON
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_2
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_3
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_CREATED_AT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_PAGING_TOKEN
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.testLinks
import kotlinx.serialization.json.Json
import kotlin.test.*

class TrustlineEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== TrustlineCreatedEffectResponse ====================

    @Test
    fun testTrustlineCreatedConstruction() {
        val effect = TrustlineCreatedEffectResponse(
            id = "20", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            limit = "1000.0000000", assetType = "credit_alphanum4",
            assetCode = "USD", assetIssuer = TEST_ACCOUNT_2
        )
        assertEquals("1000.0000000", effect.limit)
        assertEquals("credit_alphanum4", effect.assetType)
        assertEquals("USD", effect.assetCode)
        assertEquals(TEST_ACCOUNT_2, effect.assetIssuer)
        assertNull(effect.liquidityPoolId)
    }

    @Test
    fun testTrustlineCreatedWithLiquidityPool() {
        val effect = TrustlineCreatedEffectResponse(
            id = "20", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            limit = "1000.0", assetType = "liquidity_pool_shares",
            liquidityPoolId = "pool123"
        )
        assertEquals("liquidity_pool_shares", effect.assetType)
        assertEquals("pool123", effect.liquidityPoolId)
        assertNull(effect.assetCode)
        assertNull(effect.assetIssuer)
    }

    @Test
    fun testTrustlineCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "20",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "limit": "1000.0000000",
            "asset_type": "credit_alphanum4",
            "asset_code": "EUR",
            "asset_issuer": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineCreatedEffectResponse>(effect)
        assertIs<TrustlineCUDResponse>(effect)
        assertEquals("EUR", effect.assetCode)
        assertEquals("1000.0000000", effect.limit)
    }

    @Test
    fun testTrustlineCreatedEquality() {
        val e1 = TrustlineCreatedEffectResponse("20", TEST_ACCOUNT, null, null, "trustline_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "1000.0", "native")
        val e2 = TrustlineCreatedEffectResponse("20", TEST_ACCOUNT, null, null, "trustline_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "1000.0", "native")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineRemovedEffectResponse ====================

    @Test
    fun testTrustlineRemovedConstruction() {
        val effect = TrustlineRemovedEffectResponse(
            id = "21", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            limit = "0.0000000", assetType = "credit_alphanum4",
            assetCode = "USD", assetIssuer = TEST_ACCOUNT_2
        )
        assertEquals("0.0000000", effect.limit)
        assertEquals("USD", effect.assetCode)
    }

    @Test
    fun testTrustlineRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "21",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "limit": "0.0000000",
            "asset_type": "credit_alphanum12",
            "asset_code": "LONGASSETCODE",
            "asset_issuer": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineRemovedEffectResponse>(effect)
        assertIs<TrustlineCUDResponse>(effect)
        assertEquals("LONGASSETCODE", effect.assetCode)
    }

    @Test
    fun testTrustlineRemovedEquality() {
        val e1 = TrustlineRemovedEffectResponse("21", TEST_ACCOUNT, null, null, "trustline_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "0.0", "native")
        val e2 = TrustlineRemovedEffectResponse("21", TEST_ACCOUNT, null, null, "trustline_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "0.0", "native")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineUpdatedEffectResponse ====================

    @Test
    fun testTrustlineUpdatedConstruction() {
        val effect = TrustlineUpdatedEffectResponse(
            id = "22", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            limit = "5000.0000000", assetType = "credit_alphanum4",
            assetCode = "USD", assetIssuer = TEST_ACCOUNT_2
        )
        assertEquals("5000.0000000", effect.limit)
    }

    @Test
    fun testTrustlineUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "22",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "limit": "5000.0000000",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineUpdatedEffectResponse>(effect)
        assertIs<TrustlineCUDResponse>(effect)
        assertEquals("5000.0000000", effect.limit)
    }

    @Test
    fun testTrustlineUpdatedEquality() {
        val e1 = TrustlineUpdatedEffectResponse("22", TEST_ACCOUNT, null, null, "trustline_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "5000.0", "native")
        val e2 = TrustlineUpdatedEffectResponse("22", TEST_ACCOUNT, null, null, "trustline_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "5000.0", "native")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineAuthorizedEffectResponse ====================

    @Test
    fun testTrustlineAuthorizedConstruction() {
        val effect = TrustlineAuthorizedEffectResponse(
            id = "23", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_authorized", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            trustor = TEST_ACCOUNT_2, assetType = "credit_alphanum4", assetCode = "USD"
        )
        assertEquals(TEST_ACCOUNT_2, effect.trustor)
        assertEquals("credit_alphanum4", effect.assetType)
        assertEquals("USD", effect.assetCode)
    }

    @Test
    fun testTrustlineAuthorizedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "23",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_authorized",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "trustor": "$TEST_ACCOUNT_2",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineAuthorizedEffectResponse>(effect)
        assertIs<TrustlineAuthorizationResponse>(effect)
    }

    @Test
    fun testTrustlineAuthorizedEquality() {
        val e1 = TrustlineAuthorizedEffectResponse("23", TEST_ACCOUNT, null, null, "trustline_authorized", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "USD")
        val e2 = TrustlineAuthorizedEffectResponse("23", TEST_ACCOUNT, null, null, "trustline_authorized", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "USD")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineDeauthorizedEffectResponse ====================

    @Test
    fun testTrustlineDeauthorizedConstruction() {
        val effect = TrustlineDeauthorizedEffectResponse(
            id = "24", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_deauthorized", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            trustor = TEST_ACCOUNT_2, assetType = "credit_alphanum4", assetCode = "EUR"
        )
        assertEquals(TEST_ACCOUNT_2, effect.trustor)
        assertEquals("EUR", effect.assetCode)
    }

    @Test
    fun testTrustlineDeauthorizedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "24",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_deauthorized",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "trustor": "$TEST_ACCOUNT_2",
            "asset_type": "credit_alphanum4",
            "asset_code": "EUR"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineDeauthorizedEffectResponse>(effect)
        assertIs<TrustlineAuthorizationResponse>(effect)
    }

    @Test
    fun testTrustlineDeauthorizedEquality() {
        val e1 = TrustlineDeauthorizedEffectResponse("24", TEST_ACCOUNT, null, null, "trustline_deauthorized", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "EUR")
        val e2 = TrustlineDeauthorizedEffectResponse("24", TEST_ACCOUNT, null, null, "trustline_deauthorized", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "EUR")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineAuthorizedToMaintainLiabilitiesEffectResponse ====================

    @Test
    fun testTrustlineAuthorizedToMaintainLiabilitiesConstruction() {
        val effect = TrustlineAuthorizedToMaintainLiabilitiesEffectResponse(
            id = "25", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_authorized_to_maintain_liabilities", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            trustor = TEST_ACCOUNT_2, assetType = "credit_alphanum4", assetCode = "GBP"
        )
        assertEquals(TEST_ACCOUNT_2, effect.trustor)
        assertEquals("GBP", effect.assetCode)
    }

    @Test
    fun testTrustlineAuthorizedToMaintainLiabilitiesJsonDeserialization() {
        val jsonStr = """
        {
            "id": "25",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_authorized_to_maintain_liabilities",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "trustor": "$TEST_ACCOUNT_2",
            "asset_type": "credit_alphanum4",
            "asset_code": "GBP"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineAuthorizedToMaintainLiabilitiesEffectResponse>(effect)
        assertIs<TrustlineAuthorizationResponse>(effect)
    }

    @Test
    fun testTrustlineAuthorizedToMaintainLiabilitiesEquality() {
        val e1 = TrustlineAuthorizedToMaintainLiabilitiesEffectResponse("25", TEST_ACCOUNT, null, null, "trustline_authorized_to_maintain_liabilities", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "GBP")
        val e2 = TrustlineAuthorizedToMaintainLiabilitiesEffectResponse("25", TEST_ACCOUNT, null, null, "trustline_authorized_to_maintain_liabilities", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "GBP")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineFlagsUpdatedEffectResponse ====================

    @Test
    fun testTrustlineFlagsUpdatedConstruction() {
        val effect = TrustlineFlagsUpdatedEffectResponse(
            id = "26", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_flags_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            trustor = TEST_ACCOUNT_2, assetType = "credit_alphanum4",
            assetCode = "USD", assetIssuer = TEST_ACCOUNT_3,
            authorizedFlag = true, authorizedToMaintainLiabilitiesFlag = false,
            clawbackEnabledFlag = true
        )
        assertEquals(TEST_ACCOUNT_2, effect.trustor)
        assertEquals("USD", effect.assetCode)
        assertEquals(TEST_ACCOUNT_3, effect.assetIssuer)
        assertEquals(true, effect.authorizedFlag)
        assertEquals(false, effect.authorizedToMaintainLiabilitiesFlag)
        assertEquals(true, effect.clawbackEnabledFlag)
    }

    @Test
    fun testTrustlineFlagsUpdatedNullFlags() {
        val effect = TrustlineFlagsUpdatedEffectResponse(
            id = "26", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_flags_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            trustor = TEST_ACCOUNT_2, assetType = "credit_alphanum4"
        )
        assertNull(effect.authorizedFlag)
        assertNull(effect.authorizedToMaintainLiabilitiesFlag)
        assertNull(effect.clawbackEnabledFlag)
        assertNull(effect.assetCode)
        assertNull(effect.assetIssuer)
    }

    @Test
    fun testTrustlineFlagsUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "26",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_flags_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "trustor": "$TEST_ACCOUNT_2",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "$TEST_ACCOUNT_3",
            "authorized_flag": true,
            "authorized_to_maintain_liabilites_flag": false,
            "clawback_enabled_flag": true
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineFlagsUpdatedEffectResponse>(effect)
        assertEquals(true, effect.authorizedFlag)
        assertEquals(false, effect.authorizedToMaintainLiabilitiesFlag)
        assertEquals(true, effect.clawbackEnabledFlag)
    }

    @Test
    fun testTrustlineFlagsUpdatedEquality() {
        val e1 = TrustlineFlagsUpdatedEffectResponse("26", TEST_ACCOUNT, null, null, "trustline_flags_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "native")
        val e2 = TrustlineFlagsUpdatedEffectResponse("26", TEST_ACCOUNT, null, null, "trustline_flags_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "native")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineSponsorshipCreatedEffectResponse ====================

    @Test
    fun testTrustlineSponsorshipCreatedConstruction() {
        val effect = TrustlineSponsorshipCreatedEffectResponse(
            id = "27", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_sponsorship_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            assetType = "credit_alphanum4", asset = "USD:$TEST_ACCOUNT_2",
            sponsor = TEST_ACCOUNT_3
        )
        assertEquals("credit_alphanum4", effect.assetType)
        assertEquals("USD:$TEST_ACCOUNT_2", effect.asset)
        assertEquals(TEST_ACCOUNT_3, effect.sponsor)
        assertNull(effect.liquidityPoolId)
    }

    @Test
    fun testTrustlineSponsorshipCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "27",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_sponsorship_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "asset_type": "credit_alphanum4",
            "asset": "USD:$TEST_ACCOUNT_2",
            "sponsor": "$TEST_ACCOUNT_3"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineSponsorshipCreatedEffectResponse>(effect)
        assertEquals("USD:$TEST_ACCOUNT_2", effect.asset)
        assertEquals(TEST_ACCOUNT_3, effect.sponsor)
    }

    @Test
    fun testTrustlineSponsorshipCreatedEquality() {
        val e1 = TrustlineSponsorshipCreatedEffectResponse("27", TEST_ACCOUNT, null, null, "trustline_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "credit_alphanum4", "USD", null, TEST_ACCOUNT_3)
        val e2 = TrustlineSponsorshipCreatedEffectResponse("27", TEST_ACCOUNT, null, null, "trustline_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "credit_alphanum4", "USD", null, TEST_ACCOUNT_3)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineSponsorshipRemovedEffectResponse ====================

    @Test
    fun testTrustlineSponsorshipRemovedConstruction() {
        val effect = TrustlineSponsorshipRemovedEffectResponse(
            id = "28", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_sponsorship_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            assetType = "credit_alphanum4", asset = "USD:$TEST_ACCOUNT_2",
            formerSponsor = TEST_ACCOUNT_3
        )
        assertEquals(TEST_ACCOUNT_3, effect.formerSponsor)
    }

    @Test
    fun testTrustlineSponsorshipRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "28",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_sponsorship_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "asset_type": "credit_alphanum4",
            "asset": "USD:$TEST_ACCOUNT_2",
            "former_sponsor": "$TEST_ACCOUNT_3"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineSponsorshipRemovedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_3, effect.formerSponsor)
    }

    @Test
    fun testTrustlineSponsorshipRemovedEquality() {
        val e1 = TrustlineSponsorshipRemovedEffectResponse("28", TEST_ACCOUNT, null, null, "trustline_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "credit_alphanum4", "USD", null, TEST_ACCOUNT_3)
        val e2 = TrustlineSponsorshipRemovedEffectResponse("28", TEST_ACCOUNT, null, null, "trustline_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "credit_alphanum4", "USD", null, TEST_ACCOUNT_3)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== TrustlineSponsorshipUpdatedEffectResponse ====================

    @Test
    fun testTrustlineSponsorshipUpdatedConstruction() {
        val effect = TrustlineSponsorshipUpdatedEffectResponse(
            id = "29", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trustline_sponsorship_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            assetType = "credit_alphanum4", asset = "USD:$TEST_ACCOUNT_2",
            formerSponsor = TEST_ACCOUNT_2, newSponsor = TEST_ACCOUNT_3
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
    }

    @Test
    fun testTrustlineSponsorshipUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "29",
            "account": "$TEST_ACCOUNT",
            "type": "trustline_sponsorship_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "asset_type": "credit_alphanum4",
            "asset": "USD:$TEST_ACCOUNT_2",
            "former_sponsor": "$TEST_ACCOUNT_2",
            "new_sponsor": "$TEST_ACCOUNT_3"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TrustlineSponsorshipUpdatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
    }

    @Test
    fun testTrustlineSponsorshipUpdatedEquality() {
        val e1 = TrustlineSponsorshipUpdatedEffectResponse("29", TEST_ACCOUNT, null, null, "trustline_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "credit_alphanum4", "USD", null, TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        val e2 = TrustlineSponsorshipUpdatedEffectResponse("29", TEST_ACCOUNT, null, null, "trustline_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "credit_alphanum4", "USD", null, TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy ====================

    @Test
    fun testTrustlineCUDTypeHierarchy() {
        val created = TrustlineCreatedEffectResponse("20", TEST_ACCOUNT, null, null, "trustline_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "1000.0", "native")
        val removed = TrustlineRemovedEffectResponse("21", TEST_ACCOUNT, null, null, "trustline_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "0.0", "native")
        val updated = TrustlineUpdatedEffectResponse("22", TEST_ACCOUNT, null, null, "trustline_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "5000.0", "native")

        assertIs<TrustlineCUDResponse>(created)
        assertIs<TrustlineCUDResponse>(removed)
        assertIs<TrustlineCUDResponse>(updated)
        assertIs<EffectResponse>(created)
    }

    @Test
    fun testTrustlineAuthorizationTypeHierarchy() {
        val authorized = TrustlineAuthorizedEffectResponse("23", TEST_ACCOUNT, null, null, "trustline_authorized", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "USD")
        val deauthorized = TrustlineDeauthorizedEffectResponse("24", TEST_ACCOUNT, null, null, "trustline_deauthorized", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "EUR")
        val maintainLiabilities = TrustlineAuthorizedToMaintainLiabilitiesEffectResponse("25", TEST_ACCOUNT, null, null, "trustline_authorized_to_maintain_liabilities", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "credit_alphanum4", "GBP")

        assertIs<TrustlineAuthorizationResponse>(authorized)
        assertIs<TrustlineAuthorizationResponse>(deauthorized)
        assertIs<TrustlineAuthorizationResponse>(maintainLiabilities)
        assertIs<EffectResponse>(authorized)
    }
}
