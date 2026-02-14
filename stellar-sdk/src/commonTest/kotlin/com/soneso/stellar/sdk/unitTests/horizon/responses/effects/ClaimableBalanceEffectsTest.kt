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

class ClaimableBalanceEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testBalanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"

    // ==================== ClaimableBalanceCreatedEffectResponse ====================

    @Test
    fun testClaimableBalanceCreatedConstruction() {
        val effect = ClaimableBalanceCreatedEffectResponse(
            id = "60", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "claimable_balance_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            asset = "native", amount = "100.0000000", balanceId = testBalanceId
        )
        assertEquals("native", effect.asset)
        assertEquals("100.0000000", effect.amount)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "60",
            "account": "$TEST_ACCOUNT",
            "type": "claimable_balance_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "asset": "USD:$TEST_ACCOUNT_2",
            "amount": "500.0000000",
            "balance_id": "$testBalanceId"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ClaimableBalanceCreatedEffectResponse>(effect)
        assertEquals("USD:$TEST_ACCOUNT_2", effect.asset)
        assertEquals("500.0000000", effect.amount)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceCreatedEquality() {
        val e1 = ClaimableBalanceCreatedEffectResponse("60", TEST_ACCOUNT, null, null, "claimable_balance_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        val e2 = ClaimableBalanceCreatedEffectResponse("60", TEST_ACCOUNT, null, null, "claimable_balance_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== ClaimableBalanceClaimantCreatedEffectResponse ====================

    @Test
    fun testClaimableBalanceClaimantCreatedConstruction() {
        val effect = ClaimableBalanceClaimantCreatedEffectResponse(
            id = "61", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "claimable_balance_claimant_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            asset = "native", amount = "100.0000000", balanceId = testBalanceId,
            predicate = """{"unconditional":true}"""
        )
        assertEquals("native", effect.asset)
        assertEquals("100.0000000", effect.amount)
        assertEquals(testBalanceId, effect.balanceId)
        assertEquals("""{"unconditional":true}""", effect.predicate)
    }

    @Test
    fun testClaimableBalanceClaimantCreatedNullPredicate() {
        val effect = ClaimableBalanceClaimantCreatedEffectResponse(
            id = "61", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "claimable_balance_claimant_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            asset = "native", amount = "100.0000000", balanceId = testBalanceId
        )
        assertNull(effect.predicate)
    }

    @Test
    fun testClaimableBalanceClaimantCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "61",
            "account": "$TEST_ACCOUNT",
            "type": "claimable_balance_claimant_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "asset": "native",
            "amount": "100.0000000",
            "balance_id": "$testBalanceId"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ClaimableBalanceClaimantCreatedEffectResponse>(effect)
        assertEquals("native", effect.asset)
        assertEquals("100.0000000", effect.amount)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceClaimantCreatedEquality() {
        val e1 = ClaimableBalanceClaimantCreatedEffectResponse("61", TEST_ACCOUNT, null, null, "claimable_balance_claimant_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        val e2 = ClaimableBalanceClaimantCreatedEffectResponse("61", TEST_ACCOUNT, null, null, "claimable_balance_claimant_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== ClaimableBalanceClaimedEffectResponse ====================

    @Test
    fun testClaimableBalanceClaimedConstruction() {
        val effect = ClaimableBalanceClaimedEffectResponse(
            id = "62", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "claimable_balance_claimed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            asset = "native", amount = "100.0000000", balanceId = testBalanceId
        )
        assertEquals("native", effect.asset)
        assertEquals("100.0000000", effect.amount)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceClaimedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "62",
            "account": "$TEST_ACCOUNT",
            "type": "claimable_balance_claimed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "asset": "native",
            "amount": "100.0000000",
            "balance_id": "$testBalanceId"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ClaimableBalanceClaimedEffectResponse>(effect)
        assertEquals("native", effect.asset)
    }

    @Test
    fun testClaimableBalanceClaimedEquality() {
        val e1 = ClaimableBalanceClaimedEffectResponse("62", TEST_ACCOUNT, null, null, "claimable_balance_claimed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        val e2 = ClaimableBalanceClaimedEffectResponse("62", TEST_ACCOUNT, null, null, "claimable_balance_claimed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== ClaimableBalanceClawedBackEffectResponse ====================

    @Test
    fun testClaimableBalanceClawedBackConstruction() {
        val effect = ClaimableBalanceClawedBackEffectResponse(
            id = "63", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "claimable_balance_clawed_back", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            balanceId = testBalanceId
        )
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceClawedBackJsonDeserialization() {
        val jsonStr = """
        {
            "id": "63",
            "account": "$TEST_ACCOUNT",
            "type": "claimable_balance_clawed_back",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "balance_id": "$testBalanceId"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ClaimableBalanceClawedBackEffectResponse>(effect)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceClawedBackEquality() {
        val e1 = ClaimableBalanceClawedBackEffectResponse("63", TEST_ACCOUNT, null, null, "claimable_balance_clawed_back", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testBalanceId)
        val e2 = ClaimableBalanceClawedBackEffectResponse("63", TEST_ACCOUNT, null, null, "claimable_balance_clawed_back", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testBalanceId)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== ClaimableBalanceSponsorshipCreatedEffectResponse ====================

    @Test
    fun testClaimableBalanceSponsorshipCreatedConstruction() {
        val effect = ClaimableBalanceSponsorshipCreatedEffectResponse(
            id = "64", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "claimable_balance_sponsorship_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            sponsor = TEST_ACCOUNT_2, balanceId = testBalanceId
        )
        assertEquals(TEST_ACCOUNT_2, effect.sponsor)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceSponsorshipCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "64",
            "account": "$TEST_ACCOUNT",
            "type": "claimable_balance_sponsorship_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "sponsor": "$TEST_ACCOUNT_2",
            "balance_id": "$testBalanceId"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ClaimableBalanceSponsorshipCreatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.sponsor)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceSponsorshipCreatedEquality() {
        val e1 = ClaimableBalanceSponsorshipCreatedEffectResponse("64", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, testBalanceId)
        val e2 = ClaimableBalanceSponsorshipCreatedEffectResponse("64", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, testBalanceId)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== ClaimableBalanceSponsorshipUpdatedEffectResponse ====================

    @Test
    fun testClaimableBalanceSponsorshipUpdatedConstruction() {
        val effect = ClaimableBalanceSponsorshipUpdatedEffectResponse(
            id = "65", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "claimable_balance_sponsorship_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            formerSponsor = TEST_ACCOUNT_2, newSponsor = TEST_ACCOUNT_3, balanceId = testBalanceId
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceSponsorshipUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "65",
            "account": "$TEST_ACCOUNT",
            "type": "claimable_balance_sponsorship_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "former_sponsor": "$TEST_ACCOUNT_2",
            "new_sponsor": "$TEST_ACCOUNT_3",
            "balance_id": "$testBalanceId"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ClaimableBalanceSponsorshipUpdatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
    }

    @Test
    fun testClaimableBalanceSponsorshipUpdatedEquality() {
        val e1 = ClaimableBalanceSponsorshipUpdatedEffectResponse("65", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3, testBalanceId)
        val e2 = ClaimableBalanceSponsorshipUpdatedEffectResponse("65", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3, testBalanceId)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== ClaimableBalanceSponsorshipRemovedEffectResponse ====================

    @Test
    fun testClaimableBalanceSponsorshipRemovedConstruction() {
        val effect = ClaimableBalanceSponsorshipRemovedEffectResponse(
            id = "66", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "claimable_balance_sponsorship_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            formerSponsor = TEST_ACCOUNT_2, balanceId = testBalanceId
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(testBalanceId, effect.balanceId)
    }

    @Test
    fun testClaimableBalanceSponsorshipRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "66",
            "account": "$TEST_ACCOUNT",
            "type": "claimable_balance_sponsorship_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "former_sponsor": "$TEST_ACCOUNT_2",
            "balance_id": "$testBalanceId"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ClaimableBalanceSponsorshipRemovedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
    }

    @Test
    fun testClaimableBalanceSponsorshipRemovedEquality() {
        val e1 = ClaimableBalanceSponsorshipRemovedEffectResponse("66", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, testBalanceId)
        val e2 = ClaimableBalanceSponsorshipRemovedEffectResponse("66", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, testBalanceId)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy ====================

    @Test
    fun testClaimableBalanceEffectsAreEffectResponse() {
        val created = ClaimableBalanceCreatedEffectResponse("60", TEST_ACCOUNT, null, null, "claimable_balance_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        val claimantCreated = ClaimableBalanceClaimantCreatedEffectResponse("61", TEST_ACCOUNT, null, null, "claimable_balance_claimant_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        val claimed = ClaimableBalanceClaimedEffectResponse("62", TEST_ACCOUNT, null, null, "claimable_balance_claimed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", "100.0", testBalanceId)
        val clawedBack = ClaimableBalanceClawedBackEffectResponse("63", TEST_ACCOUNT, null, null, "claimable_balance_clawed_back", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testBalanceId)
        val sponsorCreated = ClaimableBalanceSponsorshipCreatedEffectResponse("64", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, testBalanceId)
        val sponsorUpdated = ClaimableBalanceSponsorshipUpdatedEffectResponse("65", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3, testBalanceId)
        val sponsorRemoved = ClaimableBalanceSponsorshipRemovedEffectResponse("66", TEST_ACCOUNT, null, null, "claimable_balance_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, testBalanceId)

        assertIs<EffectResponse>(created)
        assertIs<EffectResponse>(claimantCreated)
        assertIs<EffectResponse>(claimed)
        assertIs<EffectResponse>(clawedBack)
        assertIs<EffectResponse>(sponsorCreated)
        assertIs<EffectResponse>(sponsorUpdated)
        assertIs<EffectResponse>(sponsorRemoved)
    }
}
