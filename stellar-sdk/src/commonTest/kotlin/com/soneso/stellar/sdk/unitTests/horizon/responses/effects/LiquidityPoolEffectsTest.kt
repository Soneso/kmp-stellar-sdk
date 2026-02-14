package com.soneso.stellar.sdk.unitTests.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.AssetAmount
import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.LINKS_JSON
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_2
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_CREATED_AT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_PAGING_TOKEN
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.testLinks
import kotlinx.serialization.json.Json
import kotlin.test.*

class LiquidityPoolEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testPoolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"

    private fun testPool() = LiquidityPool(
        id = testPoolId,
        feeBP = 30,
        type = "constant_product",
        totalTrustlines = 100L,
        totalShares = "1000.0000000",
        reserves = listOf(
            AssetAmount(asset = "native", amount = "5000.0000000"),
            AssetAmount(asset = "USD:$TEST_ACCOUNT_2", amount = "2500.0000000")
        )
    )

    // ==================== LiquidityPool (data class) ====================

    @Test
    fun testLiquidityPoolConstruction() {
        val pool = testPool()
        assertEquals(testPoolId, pool.id)
        assertEquals(30, pool.feeBP)
        assertEquals("constant_product", pool.type)
        assertEquals(100L, pool.totalTrustlines)
        assertEquals("1000.0000000", pool.totalShares)
        assertEquals(2, pool.reserves.size)
        assertEquals("native", pool.reserves[0].asset)
        assertEquals("5000.0000000", pool.reserves[0].amount)
    }

    @Test
    fun testLiquidityPoolEquality() {
        val p1 = testPool()
        val p2 = testPool()
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    // ==================== LiquidityPoolClaimableAssetAmount ====================

    @Test
    fun testLiquidityPoolClaimableAssetAmountConstruction() {
        val amount = LiquidityPoolClaimableAssetAmount(
            asset = "native",
            amount = "100.0",
            claimableBalanceId = "balance123"
        )
        assertEquals("native", amount.asset)
        assertEquals("100.0", amount.amount)
        assertEquals("balance123", amount.claimableBalanceId)
    }

    @Test
    fun testLiquidityPoolClaimableAssetAmountEquality() {
        val a1 = LiquidityPoolClaimableAssetAmount("native", "100.0", "balance123")
        val a2 = LiquidityPoolClaimableAssetAmount("native", "100.0", "balance123")
        assertEquals(a1, a2)
        assertEquals(a1.hashCode(), a2.hashCode())
    }

    // ==================== LiquidityPoolCreatedEffectResponse ====================

    @Test
    fun testLiquidityPoolCreatedConstruction() {
        val effect = LiquidityPoolCreatedEffectResponse(
            id = "70", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "liquidity_pool_created", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            liquidityPool = testPool()
        )
        assertEquals(testPoolId, effect.liquidityPool.id)
        assertEquals(30, effect.liquidityPool.feeBP)
    }

    @Test
    fun testLiquidityPoolCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "70",
            "account": "$TEST_ACCOUNT",
            "type": "liquidity_pool_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "liquidity_pool": {
                "id": "$testPoolId",
                "fee_bp": 30,
                "type": "constant_product",
                "total_trustlines": 100,
                "total_shares": "1000.0000000",
                "reserves": [
                    { "asset": "native", "amount": "5000.0000000" },
                    { "asset": "USD:$TEST_ACCOUNT_2", "amount": "2500.0000000" }
                ]
            }
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<LiquidityPoolCreatedEffectResponse>(effect)
        assertEquals(testPoolId, effect.liquidityPool.id)
        assertEquals(30, effect.liquidityPool.feeBP)
        assertEquals("constant_product", effect.liquidityPool.type)
        assertEquals(100L, effect.liquidityPool.totalTrustlines)
        assertEquals(2, effect.liquidityPool.reserves.size)
    }

    @Test
    fun testLiquidityPoolCreatedEquality() {
        val e1 = LiquidityPoolCreatedEffectResponse("70", TEST_ACCOUNT, null, null, "liquidity_pool_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool())
        val e2 = LiquidityPoolCreatedEffectResponse("70", TEST_ACCOUNT, null, null, "liquidity_pool_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool())
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== LiquidityPoolRemovedEffectResponse ====================

    @Test
    fun testLiquidityPoolRemovedConstruction() {
        val effect = LiquidityPoolRemovedEffectResponse(
            id = "71", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "liquidity_pool_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            liquidityPoolId = testPoolId
        )
        assertEquals(testPoolId, effect.liquidityPoolId)
    }

    @Test
    fun testLiquidityPoolRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "71",
            "account": "$TEST_ACCOUNT",
            "type": "liquidity_pool_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "liquidity_pool_id": "$testPoolId"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<LiquidityPoolRemovedEffectResponse>(effect)
        assertEquals(testPoolId, effect.liquidityPoolId)
    }

    @Test
    fun testLiquidityPoolRemovedEquality() {
        val e1 = LiquidityPoolRemovedEffectResponse("71", TEST_ACCOUNT, null, null, "liquidity_pool_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPoolId)
        val e2 = LiquidityPoolRemovedEffectResponse("71", TEST_ACCOUNT, null, null, "liquidity_pool_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPoolId)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== LiquidityPoolDepositedEffectResponse ====================

    @Test
    fun testLiquidityPoolDepositedConstruction() {
        val reserves = listOf(
            AssetAmount(asset = "native", amount = "100.0000000"),
            AssetAmount(asset = "USD:$TEST_ACCOUNT_2", amount = "50.0000000")
        )
        val effect = LiquidityPoolDepositedEffectResponse(
            id = "72", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "liquidity_pool_deposited", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            liquidityPool = testPool(),
            reservesDeposited = reserves,
            sharesReceived = "70.7106781"
        )
        assertEquals(2, effect.reservesDeposited.size)
        assertEquals("100.0000000", effect.reservesDeposited[0].amount)
        assertEquals("70.7106781", effect.sharesReceived)
    }

    @Test
    fun testLiquidityPoolDepositedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "72",
            "account": "$TEST_ACCOUNT",
            "type": "liquidity_pool_deposited",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "liquidity_pool": {
                "id": "$testPoolId",
                "fee_bp": 30,
                "type": "constant_product",
                "total_trustlines": 100,
                "total_shares": "1000.0000000",
                "reserves": [
                    { "asset": "native", "amount": "5000.0000000" },
                    { "asset": "USD:$TEST_ACCOUNT_2", "amount": "2500.0000000" }
                ]
            },
            "reserves_deposited": [
                { "asset": "native", "amount": "100.0000000" },
                { "asset": "USD:$TEST_ACCOUNT_2", "amount": "50.0000000" }
            ],
            "shares_received": "70.7106781"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<LiquidityPoolDepositedEffectResponse>(effect)
        assertEquals(2, effect.reservesDeposited.size)
        assertEquals("70.7106781", effect.sharesReceived)
    }

    @Test
    fun testLiquidityPoolDepositedEquality() {
        val reserves = listOf(AssetAmount("native", "100.0"))
        val e1 = LiquidityPoolDepositedEffectResponse("72", TEST_ACCOUNT, null, null, "liquidity_pool_deposited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), reserves, "70.0")
        val e2 = LiquidityPoolDepositedEffectResponse("72", TEST_ACCOUNT, null, null, "liquidity_pool_deposited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), reserves, "70.0")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== LiquidityPoolWithdrewEffectResponse ====================

    @Test
    fun testLiquidityPoolWithdrewConstruction() {
        val reserves = listOf(
            AssetAmount(asset = "native", amount = "50.0000000"),
            AssetAmount(asset = "USD:$TEST_ACCOUNT_2", amount = "25.0000000")
        )
        val effect = LiquidityPoolWithdrewEffectResponse(
            id = "73", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "liquidity_pool_withdrew", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            liquidityPool = testPool(),
            reservesReceived = reserves,
            sharesRedeemed = "35.3553390"
        )
        assertEquals(2, effect.reservesReceived.size)
        assertEquals("35.3553390", effect.sharesRedeemed)
    }

    @Test
    fun testLiquidityPoolWithdrewJsonDeserialization() {
        val jsonStr = """
        {
            "id": "73",
            "account": "$TEST_ACCOUNT",
            "type": "liquidity_pool_withdrew",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "liquidity_pool": {
                "id": "$testPoolId",
                "fee_bp": 30,
                "type": "constant_product",
                "total_trustlines": 100,
                "total_shares": "1000.0000000",
                "reserves": [
                    { "asset": "native", "amount": "5000.0000000" },
                    { "asset": "USD:$TEST_ACCOUNT_2", "amount": "2500.0000000" }
                ]
            },
            "reserves_received": [
                { "asset": "native", "amount": "50.0000000" },
                { "asset": "USD:$TEST_ACCOUNT_2", "amount": "25.0000000" }
            ],
            "shares_redeemed": "35.3553390"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<LiquidityPoolWithdrewEffectResponse>(effect)
        assertEquals(2, effect.reservesReceived.size)
        assertEquals("35.3553390", effect.sharesRedeemed)
    }

    @Test
    fun testLiquidityPoolWithdrewEquality() {
        val reserves = listOf(AssetAmount("native", "50.0"))
        val e1 = LiquidityPoolWithdrewEffectResponse("73", TEST_ACCOUNT, null, null, "liquidity_pool_withdrew", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), reserves, "35.0")
        val e2 = LiquidityPoolWithdrewEffectResponse("73", TEST_ACCOUNT, null, null, "liquidity_pool_withdrew", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), reserves, "35.0")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== LiquidityPoolTradeEffectResponse ====================

    @Test
    fun testLiquidityPoolTradeConstruction() {
        val effect = LiquidityPoolTradeEffectResponse(
            id = "74", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "liquidity_pool_trade", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            liquidityPool = testPool(),
            sold = AssetAmount(asset = "native", amount = "10.0000000"),
            bought = AssetAmount(asset = "USD:$TEST_ACCOUNT_2", amount = "5.0000000")
        )
        assertEquals("native", effect.sold.asset)
        assertEquals("10.0000000", effect.sold.amount)
        assertEquals("USD:$TEST_ACCOUNT_2", effect.bought.asset)
        assertEquals("5.0000000", effect.bought.amount)
    }

    @Test
    fun testLiquidityPoolTradeJsonDeserialization() {
        val jsonStr = """
        {
            "id": "74",
            "account": "$TEST_ACCOUNT",
            "type": "liquidity_pool_trade",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "liquidity_pool": {
                "id": "$testPoolId",
                "fee_bp": 30,
                "type": "constant_product",
                "total_trustlines": 100,
                "total_shares": "1000.0000000",
                "reserves": [
                    { "asset": "native", "amount": "5000.0000000" },
                    { "asset": "USD:$TEST_ACCOUNT_2", "amount": "2500.0000000" }
                ]
            },
            "sold": { "asset": "native", "amount": "10.0000000" },
            "bought": { "asset": "USD:$TEST_ACCOUNT_2", "amount": "5.0000000" }
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<LiquidityPoolTradeEffectResponse>(effect)
        assertEquals("native", effect.sold.asset)
        assertEquals("10.0000000", effect.sold.amount)
        assertEquals("USD:$TEST_ACCOUNT_2", effect.bought.asset)
    }

    @Test
    fun testLiquidityPoolTradeEquality() {
        val sold = AssetAmount("native", "10.0")
        val bought = AssetAmount("USD", "5.0")
        val e1 = LiquidityPoolTradeEffectResponse("74", TEST_ACCOUNT, null, null, "liquidity_pool_trade", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), sold, bought)
        val e2 = LiquidityPoolTradeEffectResponse("74", TEST_ACCOUNT, null, null, "liquidity_pool_trade", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), sold, bought)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== LiquidityPoolRevokedEffectResponse ====================

    @Test
    fun testLiquidityPoolRevokedConstruction() {
        val revokedReserves = listOf(
            LiquidityPoolClaimableAssetAmount(asset = "native", amount = "50.0", claimableBalanceId = "cb1"),
            LiquidityPoolClaimableAssetAmount(asset = "USD:$TEST_ACCOUNT_2", amount = "25.0", claimableBalanceId = "cb2")
        )
        val effect = LiquidityPoolRevokedEffectResponse(
            id = "75", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "liquidity_pool_revoked", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            liquidityPool = testPool(),
            reservesRevoked = revokedReserves,
            sharesRevoked = "35.0"
        )
        assertEquals(2, effect.reservesRevoked.size)
        assertEquals("native", effect.reservesRevoked[0].asset)
        assertEquals("50.0", effect.reservesRevoked[0].amount)
        assertEquals("cb1", effect.reservesRevoked[0].claimableBalanceId)
        assertEquals("35.0", effect.sharesRevoked)
    }

    @Test
    fun testLiquidityPoolRevokedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "75",
            "account": "$TEST_ACCOUNT",
            "type": "liquidity_pool_revoked",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "liquidity_pool": {
                "id": "$testPoolId",
                "fee_bp": 30,
                "type": "constant_product",
                "total_trustlines": 100,
                "total_shares": "1000.0000000",
                "reserves": [
                    { "asset": "native", "amount": "5000.0000000" },
                    { "asset": "USD:$TEST_ACCOUNT_2", "amount": "2500.0000000" }
                ]
            },
            "reserves_revoked": [
                { "asset": "native", "amount": "50.0", "claimable_balance_id": "cb1" },
                { "asset": "USD:$TEST_ACCOUNT_2", "amount": "25.0", "claimable_balance_id": "cb2" }
            ],
            "shares_revoked": "35.0"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<LiquidityPoolRevokedEffectResponse>(effect)
        assertEquals(2, effect.reservesRevoked.size)
        assertEquals("cb1", effect.reservesRevoked[0].claimableBalanceId)
        assertEquals("35.0", effect.sharesRevoked)
    }

    @Test
    fun testLiquidityPoolRevokedEquality() {
        val reserves = listOf(LiquidityPoolClaimableAssetAmount("native", "50.0", "cb1"))
        val e1 = LiquidityPoolRevokedEffectResponse("75", TEST_ACCOUNT, null, null, "liquidity_pool_revoked", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), reserves, "35.0")
        val e2 = LiquidityPoolRevokedEffectResponse("75", TEST_ACCOUNT, null, null, "liquidity_pool_revoked", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), reserves, "35.0")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy ====================

    @Test
    fun testLiquidityPoolEffectsAreEffectResponse() {
        val created = LiquidityPoolCreatedEffectResponse("70", TEST_ACCOUNT, null, null, "liquidity_pool_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool())
        val removed = LiquidityPoolRemovedEffectResponse("71", TEST_ACCOUNT, null, null, "liquidity_pool_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPoolId)
        val deposited = LiquidityPoolDepositedEffectResponse("72", TEST_ACCOUNT, null, null, "liquidity_pool_deposited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), listOf(), "0.0")
        val withdrew = LiquidityPoolWithdrewEffectResponse("73", TEST_ACCOUNT, null, null, "liquidity_pool_withdrew", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), listOf(), "0.0")
        val trade = LiquidityPoolTradeEffectResponse("74", TEST_ACCOUNT, null, null, "liquidity_pool_trade", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), AssetAmount("native", "10.0"), AssetAmount("USD", "5.0"))
        val revoked = LiquidityPoolRevokedEffectResponse("75", TEST_ACCOUNT, null, null, "liquidity_pool_revoked", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), testPool(), listOf(), "0.0")

        assertIs<EffectResponse>(created)
        assertIs<EffectResponse>(removed)
        assertIs<EffectResponse>(deposited)
        assertIs<EffectResponse>(withdrew)
        assertIs<EffectResponse>(trade)
        assertIs<EffectResponse>(revoked)
    }
}
