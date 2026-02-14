package com.soneso.stellar.sdk.unitTests.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.LINKS_JSON
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_2
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_CREATED_AT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_PAGING_TOKEN
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.testLinks
import kotlinx.serialization.json.Json
import kotlin.test.*

class TradeAndOfferEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== TradeEffectResponse ====================

    @Test
    fun testTradeEffectConstruction() {
        val effect = TradeEffectResponse(
            id = "30", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trade", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            seller = TEST_ACCOUNT_2, sellerMuxed = null, sellerMuxedId = null,
            offerId = 12345L,
            soldAmount = "100.0000000", soldAssetType = "native",
            boughtAmount = "50.0000000", boughtAssetType = "credit_alphanum4",
            boughtAssetCode = "USD", boughtAssetIssuer = TEST_ACCOUNT_2
        )
        assertEquals(TEST_ACCOUNT_2, effect.seller)
        assertNull(effect.sellerMuxed)
        assertNull(effect.sellerMuxedId)
        assertEquals(12345L, effect.offerId)
        assertEquals("100.0000000", effect.soldAmount)
        assertEquals("native", effect.soldAssetType)
        assertNull(effect.soldAssetCode)
        assertNull(effect.soldAssetIssuer)
        assertEquals("50.0000000", effect.boughtAmount)
        assertEquals("credit_alphanum4", effect.boughtAssetType)
        assertEquals("USD", effect.boughtAssetCode)
        assertEquals(TEST_ACCOUNT_2, effect.boughtAssetIssuer)
    }

    @Test
    fun testTradeEffectWithMuxedSeller() {
        val effect = TradeEffectResponse(
            id = "30", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "trade", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            seller = TEST_ACCOUNT_2,
            sellerMuxed = "MAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWAAAAAAAAAAAAAGPQ",
            sellerMuxedId = "9876543210",
            offerId = 99999L,
            soldAmount = "200.0", soldAssetType = "credit_alphanum4",
            soldAssetCode = "EUR", soldAssetIssuer = TEST_ACCOUNT,
            boughtAmount = "300.0", boughtAssetType = "native"
        )
        assertEquals("MAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWAAAAAAAAAAAAAGPQ", effect.sellerMuxed)
        assertEquals("9876543210", effect.sellerMuxedId)
    }

    @Test
    fun testTradeEffectJsonDeserialization() {
        val jsonStr = """
        {
            "id": "30",
            "account": "$TEST_ACCOUNT",
            "type": "trade",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "seller": "$TEST_ACCOUNT_2",
            "offer_id": 12345,
            "sold_amount": "100.0000000",
            "sold_asset_type": "native",
            "bought_amount": "50.0000000",
            "bought_asset_type": "credit_alphanum4",
            "bought_asset_code": "USD",
            "bought_asset_issuer": "$TEST_ACCOUNT_2"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<TradeEffectResponse>(effect)
        assertEquals(TEST_ACCOUNT_2, effect.seller)
        assertEquals(12345L, effect.offerId)
        assertEquals("100.0000000", effect.soldAmount)
        assertEquals("50.0000000", effect.boughtAmount)
        assertEquals("USD", effect.boughtAssetCode)
    }

    @Test
    fun testTradeEffectEquality() {
        val e1 = TradeEffectResponse("30", TEST_ACCOUNT, null, null, "trade", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, null, null, 12345L, "100.0", "native", null, null, "50.0", "native")
        val e2 = TradeEffectResponse("30", TEST_ACCOUNT, null, null, "trade", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, null, null, 12345L, "100.0", "native", null, null, "50.0", "native")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== OfferCreatedEffectResponse ====================

    @Test
    fun testOfferCreatedConstruction() {
        val effect = OfferCreatedEffectResponse(
            id = "31", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "offer_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks()
        )
        assertEquals("31", effect.id)
        assertEquals("offer_created", effect.type)
    }

    @Test
    fun testOfferCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "31",
            "account": "$TEST_ACCOUNT",
            "type": "offer_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<OfferCreatedEffectResponse>(effect)
        assertEquals("offer_created", effect.type)
    }

    @Test
    fun testOfferCreatedEquality() {
        val e1 = OfferCreatedEffectResponse("31", TEST_ACCOUNT, null, null, "offer_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val e2 = OfferCreatedEffectResponse("31", TEST_ACCOUNT, null, null, "offer_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== OfferRemovedEffectResponse ====================

    @Test
    fun testOfferRemovedConstruction() {
        val effect = OfferRemovedEffectResponse(
            id = "32", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "offer_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks()
        )
        assertEquals("32", effect.id)
        assertEquals("offer_removed", effect.type)
    }

    @Test
    fun testOfferRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "32",
            "account": "$TEST_ACCOUNT",
            "type": "offer_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<OfferRemovedEffectResponse>(effect)
    }

    @Test
    fun testOfferRemovedEquality() {
        val e1 = OfferRemovedEffectResponse("32", TEST_ACCOUNT, null, null, "offer_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val e2 = OfferRemovedEffectResponse("32", TEST_ACCOUNT, null, null, "offer_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== OfferUpdatedEffectResponse ====================

    @Test
    fun testOfferUpdatedConstruction() {
        val effect = OfferUpdatedEffectResponse(
            id = "33", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "offer_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks()
        )
        assertEquals("33", effect.id)
        assertEquals("offer_updated", effect.type)
    }

    @Test
    fun testOfferUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "33",
            "account": "$TEST_ACCOUNT",
            "type": "offer_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<OfferUpdatedEffectResponse>(effect)
    }

    @Test
    fun testOfferUpdatedEquality() {
        val e1 = OfferUpdatedEffectResponse("33", TEST_ACCOUNT, null, null, "offer_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val e2 = OfferUpdatedEffectResponse("33", TEST_ACCOUNT, null, null, "offer_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy ====================

    @Test
    fun testTradeAndOfferEffectsAreEffectResponse() {
        val trade = TradeEffectResponse("30", TEST_ACCOUNT, null, null, "trade", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), TEST_ACCOUNT_2, null, null, 12345L, "100.0", "native", null, null, "50.0", "native")
        val offerCreated = OfferCreatedEffectResponse("31", TEST_ACCOUNT, null, null, "offer_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val offerRemoved = OfferRemovedEffectResponse("32", TEST_ACCOUNT, null, null, "offer_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val offerUpdated = OfferUpdatedEffectResponse("33", TEST_ACCOUNT, null, null, "offer_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())

        assertIs<EffectResponse>(trade)
        assertIs<EffectResponse>(offerCreated)
        assertIs<EffectResponse>(offerRemoved)
        assertIs<EffectResponse>(offerUpdated)
    }
}
