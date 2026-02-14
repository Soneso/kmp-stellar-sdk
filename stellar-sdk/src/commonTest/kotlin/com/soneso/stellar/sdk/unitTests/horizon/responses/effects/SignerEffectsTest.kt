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

class SignerEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== SignerCreatedEffectResponse ====================

    @Test
    fun testSignerCreatedConstruction() {
        val effect = SignerCreatedEffectResponse(
            id = "10", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "signer_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            weight = 1, publicKey = TEST_ACCOUNT_2
        )
        assertEquals(1, effect.weight)
        assertEquals(TEST_ACCOUNT_2, effect.publicKey)
    }

    @Test
    fun testSignerCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "10",
            "account": "$TEST_ACCOUNT",
            "type": "signer_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "weight": 5,
            "public_key": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<SignerCreatedEffectResponse>(effect)
        assertIs<SignerEffectResponse>(effect)
        assertEquals(5, effect.weight)
        assertEquals(TEST_ACCOUNT_2, effect.publicKey)
    }

    @Test
    fun testSignerCreatedEquality() {
        val e1 = SignerCreatedEffectResponse("10", TEST_ACCOUNT, null, null, "signer_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 1, TEST_ACCOUNT_2)
        val e2 = SignerCreatedEffectResponse("10", TEST_ACCOUNT, null, null, "signer_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 1, TEST_ACCOUNT_2)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== SignerRemovedEffectResponse ====================

    @Test
    fun testSignerRemovedConstruction() {
        val effect = SignerRemovedEffectResponse(
            id = "11", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "signer_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            weight = 0, publicKey = TEST_ACCOUNT_2
        )
        assertEquals(0, effect.weight)
        assertEquals(TEST_ACCOUNT_2, effect.publicKey)
    }

    @Test
    fun testSignerRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "11",
            "account": "$TEST_ACCOUNT",
            "type": "signer_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "weight": 0,
            "public_key": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<SignerRemovedEffectResponse>(effect)
        assertIs<SignerEffectResponse>(effect)
        assertEquals(0, effect.weight)
    }

    @Test
    fun testSignerRemovedEquality() {
        val e1 = SignerRemovedEffectResponse("11", TEST_ACCOUNT, null, null, "signer_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 0, TEST_ACCOUNT_2)
        val e2 = SignerRemovedEffectResponse("11", TEST_ACCOUNT, null, null, "signer_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 0, TEST_ACCOUNT_2)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== SignerUpdatedEffectResponse ====================

    @Test
    fun testSignerUpdatedConstruction() {
        val effect = SignerUpdatedEffectResponse(
            id = "12", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "signer_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            weight = 3, publicKey = TEST_ACCOUNT_2
        )
        assertEquals(3, effect.weight)
        assertEquals(TEST_ACCOUNT_2, effect.publicKey)
    }

    @Test
    fun testSignerUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "12",
            "account": "$TEST_ACCOUNT",
            "type": "signer_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "weight": 3,
            "public_key": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<SignerUpdatedEffectResponse>(effect)
        assertIs<SignerEffectResponse>(effect)
        assertEquals(3, effect.weight)
    }

    @Test
    fun testSignerUpdatedEquality() {
        val e1 = SignerUpdatedEffectResponse("12", TEST_ACCOUNT, null, null, "signer_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 3, TEST_ACCOUNT_2)
        val e2 = SignerUpdatedEffectResponse("12", TEST_ACCOUNT, null, null, "signer_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 3, TEST_ACCOUNT_2)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy ====================

    @Test
    fun testSignerEffectsTypeHierarchy() {
        val created = SignerCreatedEffectResponse("10", TEST_ACCOUNT, null, null, "signer_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 1, TEST_ACCOUNT_2)
        val removed = SignerRemovedEffectResponse("11", TEST_ACCOUNT, null, null, "signer_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 0, TEST_ACCOUNT_2)
        val updated = SignerUpdatedEffectResponse("12", TEST_ACCOUNT, null, null, "signer_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 3, TEST_ACCOUNT_2)

        assertIs<SignerEffectResponse>(created)
        assertIs<SignerEffectResponse>(removed)
        assertIs<SignerEffectResponse>(updated)
        assertIs<EffectResponse>(created)
        assertIs<EffectResponse>(removed)
        assertIs<EffectResponse>(updated)
    }

    // ==================== SignerSponsorshipCreatedEffectResponse ====================

    @Test
    fun testSignerSponsorshipCreatedConstruction() {
        val effect = SignerSponsorshipCreatedEffectResponse(
            id = "13", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "signer_sponsorship_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            sponsor = TEST_ACCOUNT_2, signer = TEST_ACCOUNT_3
        )
        assertEquals(TEST_ACCOUNT_2, effect.sponsor)
        assertEquals(TEST_ACCOUNT_3, effect.signer)
    }

    @Test
    fun testSignerSponsorshipCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "13",
            "account": "$TEST_ACCOUNT",
            "type": "signer_sponsorship_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "sponsor": "$TEST_ACCOUNT_2",
            "signer": "$TEST_ACCOUNT_3"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<SignerSponsorshipCreatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.sponsor)
        assertEquals(TEST_ACCOUNT_3, effect.signer)
    }

    @Test
    fun testSignerSponsorshipCreatedEquality() {
        val e1 = SignerSponsorshipCreatedEffectResponse("13", TEST_ACCOUNT, null, null, "signer_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        val e2 = SignerSponsorshipCreatedEffectResponse("13", TEST_ACCOUNT, null, null, "signer_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== SignerSponsorshipRemovedEffectResponse ====================

    @Test
    fun testSignerSponsorshipRemovedConstruction() {
        val effect = SignerSponsorshipRemovedEffectResponse(
            id = "14", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "signer_sponsorship_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            formerSponsor = TEST_ACCOUNT_2, signer = TEST_ACCOUNT_3
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.signer)
    }

    @Test
    fun testSignerSponsorshipRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "14",
            "account": "$TEST_ACCOUNT",
            "type": "signer_sponsorship_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "former_sponsor": "$TEST_ACCOUNT_2",
            "signer": "$TEST_ACCOUNT_3"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<SignerSponsorshipRemovedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.signer)
    }

    @Test
    fun testSignerSponsorshipRemovedEquality() {
        val e1 = SignerSponsorshipRemovedEffectResponse("14", TEST_ACCOUNT, null, null, "signer_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        val e2 = SignerSponsorshipRemovedEffectResponse("14", TEST_ACCOUNT, null, null, "signer_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== SignerSponsorshipUpdatedEffectResponse ====================

    @Test
    fun testSignerSponsorshipUpdatedConstruction() {
        val effect = SignerSponsorshipUpdatedEffectResponse(
            id = "15", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "signer_sponsorship_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            formerSponsor = TEST_ACCOUNT_2, newSponsor = TEST_ACCOUNT_3, signer = TEST_ACCOUNT
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
        assertEquals(TEST_ACCOUNT, effect.signer)
    }

    @Test
    fun testSignerSponsorshipUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "15",
            "account": "$TEST_ACCOUNT",
            "type": "signer_sponsorship_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "former_sponsor": "$TEST_ACCOUNT_2",
            "new_sponsor": "$TEST_ACCOUNT_3",
            "signer": "$TEST_ACCOUNT"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<SignerSponsorshipUpdatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
        assertEquals(TEST_ACCOUNT, effect.signer)
    }

    @Test
    fun testSignerSponsorshipUpdatedEquality() {
        val e1 = SignerSponsorshipUpdatedEffectResponse("15", TEST_ACCOUNT, null, null, "signer_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3, TEST_ACCOUNT)
        val e2 = SignerSponsorshipUpdatedEffectResponse("15", TEST_ACCOUNT, null, null, "signer_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3, TEST_ACCOUNT)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }
}
