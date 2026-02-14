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

class DataEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== DataCreatedEffectResponse ====================

    @Test
    fun testDataCreatedConstruction() {
        val effect = DataCreatedEffectResponse(
            id = "40", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "data_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            name = "myKey", value = "bXlWYWx1ZQ=="
        )
        assertEquals("myKey", effect.name)
        assertEquals("bXlWYWx1ZQ==", effect.value)
    }

    @Test
    fun testDataCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "40",
            "account": "$TEST_ACCOUNT",
            "type": "data_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "name": "myKey",
            "value": "bXlWYWx1ZQ=="
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<DataCreatedEffectResponse>(effect)
        assertEquals("myKey", effect.name)
        assertEquals("bXlWYWx1ZQ==", effect.value)
    }

    @Test
    fun testDataCreatedEquality() {
        val e1 = DataCreatedEffectResponse("40", TEST_ACCOUNT, null, null, "data_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "key", "val")
        val e2 = DataCreatedEffectResponse("40", TEST_ACCOUNT, null, null, "data_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "key", "val")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== DataRemovedEffectResponse ====================

    @Test
    fun testDataRemovedConstruction() {
        val effect = DataRemovedEffectResponse(
            id = "41", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "data_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            name = "myKey"
        )
        assertEquals("myKey", effect.name)
    }

    @Test
    fun testDataRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "41",
            "account": "$TEST_ACCOUNT",
            "type": "data_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "name": "removedKey"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<DataRemovedEffectResponse>(effect)
        assertEquals("removedKey", effect.name)
    }

    @Test
    fun testDataRemovedEquality() {
        val e1 = DataRemovedEffectResponse("41", TEST_ACCOUNT, null, null, "data_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "key")
        val e2 = DataRemovedEffectResponse("41", TEST_ACCOUNT, null, null, "data_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "key")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== DataUpdatedEffectResponse ====================

    @Test
    fun testDataUpdatedConstruction() {
        val effect = DataUpdatedEffectResponse(
            id = "42", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "data_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            name = "myKey", value = "bmV3VmFsdWU="
        )
        assertEquals("myKey", effect.name)
        assertEquals("bmV3VmFsdWU=", effect.value)
    }

    @Test
    fun testDataUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "42",
            "account": "$TEST_ACCOUNT",
            "type": "data_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "name": "myKey",
            "value": "bmV3VmFsdWU="
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<DataUpdatedEffectResponse>(effect)
        assertEquals("myKey", effect.name)
        assertEquals("bmV3VmFsdWU=", effect.value)
    }

    @Test
    fun testDataUpdatedEquality() {
        val e1 = DataUpdatedEffectResponse("42", TEST_ACCOUNT, null, null, "data_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "key", "val")
        val e2 = DataUpdatedEffectResponse("42", TEST_ACCOUNT, null, null, "data_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "key", "val")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== DataSponsorshipCreatedEffectResponse ====================

    @Test
    fun testDataSponsorshipCreatedConstruction() {
        val effect = DataSponsorshipCreatedEffectResponse(
            id = "43", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "data_sponsorship_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            sponsor = TEST_ACCOUNT_2, dataName = "myKey"
        )
        assertEquals(TEST_ACCOUNT_2, effect.sponsor)
        assertEquals("myKey", effect.dataName)
    }

    @Test
    fun testDataSponsorshipCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "43",
            "account": "$TEST_ACCOUNT",
            "type": "data_sponsorship_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "sponsor": "$TEST_ACCOUNT_2",
            "data_name": "myKey"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<DataSponsorshipCreatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.sponsor)
        assertEquals("myKey", effect.dataName)
    }

    @Test
    fun testDataSponsorshipCreatedEquality() {
        val e1 = DataSponsorshipCreatedEffectResponse("43", TEST_ACCOUNT, null, null, "data_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "key")
        val e2 = DataSponsorshipCreatedEffectResponse("43", TEST_ACCOUNT, null, null, "data_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "key")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== DataSponsorshipRemovedEffectResponse ====================

    @Test
    fun testDataSponsorshipRemovedConstruction() {
        val effect = DataSponsorshipRemovedEffectResponse(
            id = "44", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "data_sponsorship_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            formerSponsor = TEST_ACCOUNT_2, dataName = "myKey"
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals("myKey", effect.dataName)
    }

    @Test
    fun testDataSponsorshipRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "44",
            "account": "$TEST_ACCOUNT",
            "type": "data_sponsorship_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "former_sponsor": "$TEST_ACCOUNT_2",
            "data_name": "myKey"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<DataSponsorshipRemovedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals("myKey", effect.dataName)
    }

    @Test
    fun testDataSponsorshipRemovedEquality() {
        val e1 = DataSponsorshipRemovedEffectResponse("44", TEST_ACCOUNT, null, null, "data_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "key")
        val e2 = DataSponsorshipRemovedEffectResponse("44", TEST_ACCOUNT, null, null, "data_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "key")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== DataSponsorshipUpdatedEffectResponse ====================

    @Test
    fun testDataSponsorshipUpdatedConstruction() {
        val effect = DataSponsorshipUpdatedEffectResponse(
            id = "45", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "data_sponsorship_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            formerSponsor = TEST_ACCOUNT_2, newSponsor = TEST_ACCOUNT_3, dataName = "myKey"
        )
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
        assertEquals("myKey", effect.dataName)
    }

    @Test
    fun testDataSponsorshipUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "45",
            "account": "$TEST_ACCOUNT",
            "type": "data_sponsorship_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "former_sponsor": "$TEST_ACCOUNT_2",
            "new_sponsor": "$TEST_ACCOUNT_3",
            "data_name": "myKey"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<DataSponsorshipUpdatedEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.formerSponsor)
        assertEquals(TEST_ACCOUNT_3, effect.newSponsor)
        assertEquals("myKey", effect.dataName)
    }

    @Test
    fun testDataSponsorshipUpdatedEquality() {
        val e1 = DataSponsorshipUpdatedEffectResponse("45", TEST_ACCOUNT, null, null, "data_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3, "key")
        val e2 = DataSponsorshipUpdatedEffectResponse("45", TEST_ACCOUNT, null, null, "data_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3, "key")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy ====================

    @Test
    fun testDataEffectsAreEffectResponse() {
        val created = DataCreatedEffectResponse("40", TEST_ACCOUNT, null, null, "data_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "k", "v")
        val removed = DataRemovedEffectResponse("41", TEST_ACCOUNT, null, null, "data_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "k")
        val updated = DataUpdatedEffectResponse("42", TEST_ACCOUNT, null, null, "data_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "k", "v")
        val sponsorCreated = DataSponsorshipCreatedEffectResponse("43", TEST_ACCOUNT, null, null, "data_sponsorship_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "k")
        val sponsorRemoved = DataSponsorshipRemovedEffectResponse("44", TEST_ACCOUNT, null, null, "data_sponsorship_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, "k")
        val sponsorUpdated = DataSponsorshipUpdatedEffectResponse("45", TEST_ACCOUNT, null, null, "data_sponsorship_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, TEST_ACCOUNT_3, "k")

        assertIs<EffectResponse>(created)
        assertIs<EffectResponse>(removed)
        assertIs<EffectResponse>(updated)
        assertIs<EffectResponse>(sponsorCreated)
        assertIs<EffectResponse>(sponsorRemoved)
        assertIs<EffectResponse>(sponsorUpdated)
    }
}
