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

class SequenceAndSponsorshipEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== SequenceBumpedEffectResponse ====================

    @Test
    fun testSequenceBumpedConstruction() {
        val effect = SequenceBumpedEffectResponse(
            id = "50", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "sequence_bumped", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            newSequence = 1234567890123L
        )
        assertEquals(1234567890123L, effect.newSequence)
    }

    @Test
    fun testSequenceBumpedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "50",
            "account": "$TEST_ACCOUNT",
            "type": "sequence_bumped",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "new_seq": 9999999999
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<SequenceBumpedEffectResponse>(effect)
        assertEquals(9999999999L, effect.newSequence)
    }

    @Test
    fun testSequenceBumpedEquality() {
        val e1 = SequenceBumpedEffectResponse("50", TEST_ACCOUNT, null, null, "sequence_bumped", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 123L)
        val e2 = SequenceBumpedEffectResponse("50", TEST_ACCOUNT, null, null, "sequence_bumped", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 123L)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountSponsorshipCreatedEffectResponse ====================

    @Test
    fun testAccountSponsorshipCreatedConstruction() {
        val effect = AccountSponsorshipCreatedEffectResponse(
            id = "51", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_sponsorship_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            sponsor = TEST_ACCOUNT_2
        )
        assertEquals(TEST_ACCOUNT_2, effect.sponsor)
    }

    @Test
    fun testAccountSponsorshipCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "51",
            "account": "$TEST_ACCOUNT",
            "type": "account_sponsorship_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "sponsor": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountSponsorshipCreatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.sponsor)
    }

    @Test
    fun testAccountSponsorshipCreatedEquality() {
        val e1 = AccountSponsorshipCreatedEffectResponse("51", TEST_ACCOUNT, null, null, "account_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2)
        val e2 = AccountSponsorshipCreatedEffectResponse("51", TEST_ACCOUNT, null, null, "account_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountSponsorshipUpdatedEffectResponse ====================

    @Test
    fun testAccountSponsorshipUpdatedConstruction() {
        val effect = AccountSponsorshipUpdatedEffectResponse(
            id = "52", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_sponsorship_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            formerSponsor = TEST_ACCOUNT_2, newSponsor = TEST_ACCOUNT_3
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
    }

    @Test
    fun testAccountSponsorshipUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "52",
            "account": "$TEST_ACCOUNT",
            "type": "account_sponsorship_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "former_sponsor": "$TEST_ACCOUNT_2",
            "new_sponsor": "$TEST_ACCOUNT_3"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountSponsorshipUpdatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
    }

    @Test
    fun testAccountSponsorshipUpdatedEquality() {
        val e1 = AccountSponsorshipUpdatedEffectResponse("52", TEST_ACCOUNT, null, null, "account_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        val e2 = AccountSponsorshipUpdatedEffectResponse("52", TEST_ACCOUNT, null, null, "account_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountSponsorshipRemovedEffectResponse ====================

    @Test
    fun testAccountSponsorshipRemovedConstruction() {
        val effect = AccountSponsorshipRemovedEffectResponse(
            id = "53", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_sponsorship_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            formerSponsor = TEST_ACCOUNT_2
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
    }

    @Test
    fun testAccountSponsorshipRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "53",
            "account": "$TEST_ACCOUNT",
            "type": "account_sponsorship_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "former_sponsor": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountSponsorshipRemovedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
    }

    @Test
    fun testAccountSponsorshipRemovedEquality() {
        val e1 = AccountSponsorshipRemovedEffectResponse("53", TEST_ACCOUNT, null, null, "account_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2)
        val e2 = AccountSponsorshipRemovedEffectResponse("53", TEST_ACCOUNT, null, null, "account_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy ====================

    @Test
    fun testSequenceAndSponsorshipEffectsAreEffectResponse() {
        val sequenceBumped = SequenceBumpedEffectResponse("50", TEST_ACCOUNT, null, null, "sequence_bumped", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 123L)
        val sponsorCreated = AccountSponsorshipCreatedEffectResponse("51", TEST_ACCOUNT, null, null, "account_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2)
        val sponsorUpdated = AccountSponsorshipUpdatedEffectResponse("52", TEST_ACCOUNT, null, null, "account_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        val sponsorRemoved = AccountSponsorshipRemovedEffectResponse("53", TEST_ACCOUNT, null, null, "account_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2)

        assertIs<EffectResponse>(sequenceBumped)
        assertIs<EffectResponse>(sponsorCreated)
        assertIs<EffectResponse>(sponsorUpdated)
        assertIs<EffectResponse>(sponsorRemoved)
    }
}
