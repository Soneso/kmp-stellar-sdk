package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.LiquidityPoolResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiquidityPoolResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val poolJson = """
    {
        "id": "67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9",
        "paging_token": "67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9",
        "fee_bp": 30,
        "type": "constant_product",
        "total_trustlines": 300,
        "total_shares": "5000.0000000",
        "reserves": [
            {
                "amount": "1000.0000000",
                "asset": "native"
            },
            {
                "amount": "2000.0000000",
                "asset": "USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
            }
        ],
        "last_modified_ledger": 7654321,
        "last_modified_time": "2021-06-15T00:00:00Z",
        "_links": {
            "self": {"href": "https://horizon.stellar.org/liquidity_pools/67260c4c"},
            "operations": {"href": "https://horizon.stellar.org/liquidity_pools/67260c4c/operations"},
            "transactions": {"href": "https://horizon.stellar.org/liquidity_pools/67260c4c/transactions"}
        }
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val pool = json.decodeFromString<LiquidityPoolResponse>(poolJson)
        assertEquals("67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9", pool.id)
        assertEquals("67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9", pool.pagingToken)
        assertEquals(30, pool.feeBp)
        assertEquals("constant_product", pool.type)
        assertEquals(300L, pool.totalTrustlines)
        assertEquals("5000.0000000", pool.totalShares)
        assertEquals(7654321L, pool.lastModifiedLedger)
        assertEquals("2021-06-15T00:00:00Z", pool.lastModifiedTime)
    }

    @Test
    fun testReserves() {
        val pool = json.decodeFromString<LiquidityPoolResponse>(poolJson)
        assertEquals(2, pool.reserves.size)
        assertEquals("1000.0000000", pool.reserves[0].amount)
        assertEquals("native", pool.reserves[0].asset)
        assertEquals("2000.0000000", pool.reserves[1].amount)
        assertTrue(pool.reserves[1].asset.contains("USD"))
    }

    @Test
    fun testLinks() {
        val pool = json.decodeFromString<LiquidityPoolResponse>(poolJson)
        assertTrue(pool.links.self.href.contains("liquidity_pools"))
        assertTrue(pool.links.operations?.href?.contains("operations") == true)
        assertTrue(pool.links.transactions?.href?.contains("transactions") == true)
    }

    @Test
    fun testMinimalDeserialization() {
        val minimalJson = """
        {
            "id": "abc123",
            "paging_token": "abc123",
            "type": "constant_product",
            "total_shares": "0.0000000",
            "_links": {
                "self": {"href": "https://example.com/self"}
            }
        }
        """.trimIndent()
        val pool = json.decodeFromString<LiquidityPoolResponse>(minimalJson)
        assertEquals("abc123", pool.id)
        assertNull(pool.feeBp)
        assertNull(pool.totalTrustlines)
        assertTrue(pool.reserves.isEmpty())
        assertNull(pool.lastModifiedLedger)
        assertNull(pool.lastModifiedTime)
        assertNull(pool.links.operations)
        assertNull(pool.links.transactions)
    }

    @Test
    fun testReserveEquality() {
        val r1 = LiquidityPoolResponse.Reserve("100.0", "native")
        val r2 = LiquidityPoolResponse.Reserve("100.0", "native")
        assertEquals(r1, r2)
    }

    @Test
    fun testComprehensiveLiquidityPoolResponseDeserialization() {
        val comprehensiveJson = """
        {
            "id": "67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9",
            "paging_token": "67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9",
            "fee_bp": 30,
            "type": "constant_product",
            "total_trustlines": 42,
            "total_shares": "1000.0000000",
            "reserves": [
                {
                    "amount": "500.0000000",
                    "asset": "native"
                },
                {
                    "amount": "2500.0000000",
                    "asset": "USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
                }
            ],
            "last_modified_ledger": 32069474,
            "last_modified_time": "2021-01-01T12:00:00Z",
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/liquidity_pools/67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9"
                },
                "operations": {
                    "href": "https://horizon.stellar.org/liquidity_pools/67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9/operations",
                    "templated": true
                },
                "transactions": {
                    "href": "https://horizon.stellar.org/liquidity_pools/67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9/transactions",
                    "templated": false
                }
            }
        }
        """.trimIndent()

        val liquidityPool = json.decodeFromString<LiquidityPoolResponse>(comprehensiveJson)

        assertEquals("67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9", liquidityPool.id)
        assertEquals("67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9", liquidityPool.pagingToken)
        assertEquals(30, liquidityPool.feeBp)
        assertEquals("constant_product", liquidityPool.type)
        assertEquals(42L, liquidityPool.totalTrustlines)
        assertEquals("1000.0000000", liquidityPool.totalShares)
        assertEquals(32069474L, liquidityPool.lastModifiedLedger)
        assertEquals("2021-01-01T12:00:00Z", liquidityPool.lastModifiedTime)

        assertEquals(2, liquidityPool.reserves.size)
        assertEquals("500.0000000", liquidityPool.reserves[0].amount)
        assertEquals("native", liquidityPool.reserves[0].asset)
        assertEquals("2500.0000000", liquidityPool.reserves[1].amount)
        assertEquals("USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", liquidityPool.reserves[1].asset)

        assertEquals("https://horizon.stellar.org/liquidity_pools/67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9", liquidityPool.links.self.href)
        assertNull(liquidityPool.links.self.templated)
        assertEquals(true, liquidityPool.links.operations?.templated)
        assertEquals(false, liquidityPool.links.transactions?.templated)
    }

    @Test
    fun testLiquidityPoolResponseWithNullableFields() {
        val nullableJson = """
        {
            "id": "minimal-pool-id",
            "paging_token": "minimal-pool-paging",
            "fee_bp": null,
            "type": "constant_product",
            "total_trustlines": null,
            "total_shares": "0.0000000",
            "reserves": [],
            "last_modified_ledger": null,
            "last_modified_time": null,
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/liquidity_pools/minimal"
                },
                "operations": null,
                "transactions": null
            }
        }
        """.trimIndent()

        val liquidityPool = json.decodeFromString<LiquidityPoolResponse>(nullableJson)

        assertEquals("minimal-pool-id", liquidityPool.id)
        assertEquals("minimal-pool-paging", liquidityPool.pagingToken)
        assertNull(liquidityPool.feeBp)
        assertEquals("constant_product", liquidityPool.type)
        assertNull(liquidityPool.totalTrustlines)
        assertEquals("0.0000000", liquidityPool.totalShares)
        assertTrue(liquidityPool.reserves.isEmpty())
        assertNull(liquidityPool.lastModifiedLedger)
        assertNull(liquidityPool.lastModifiedTime)

        assertEquals("https://horizon.stellar.org/liquidity_pools/minimal", liquidityPool.links.self.href)
        assertNull(liquidityPool.links.operations)
        assertNull(liquidityPool.links.transactions)
    }

    @Test
    fun testLiquidityPoolResponseWithMultipleReserves() {
        val multiReserveJson = """
        {
            "id": "multi-reserve-pool",
            "paging_token": "multi-reserve-paging",
            "fee_bp": 50,
            "type": "constant_product",
            "total_trustlines": 100,
            "total_shares": "10000.0000000",
            "reserves": [
                {
                    "amount": "1000.0000000",
                    "asset": "native"
                },
                {
                    "amount": "5000.0000000",
                    "asset": "EUR:GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU"
                },
                {
                    "amount": "7500.0000000",
                    "asset": "LONGASSETCODE:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
                }
            ],
            "last_modified_ledger": 45000000,
            "last_modified_time": "2022-01-01T15:30:00Z",
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/liquidity_pools/multi-reserve",
                    "templated": false
                },
                "operations": {
                    "href": "https://horizon.stellar.org/liquidity_pools/multi-reserve/operations"
                },
                "transactions": {
                    "href": "https://horizon.stellar.org/liquidity_pools/multi-reserve/transactions",
                    "templated": true
                }
            }
        }
        """.trimIndent()

        val liquidityPool = json.decodeFromString<LiquidityPoolResponse>(multiReserveJson)

        assertEquals("multi-reserve-pool", liquidityPool.id)
        assertEquals(50, liquidityPool.feeBp)
        assertEquals(100L, liquidityPool.totalTrustlines)
        assertEquals("10000.0000000", liquidityPool.totalShares)

        assertEquals(3, liquidityPool.reserves.size)
        assertEquals("1000.0000000", liquidityPool.reserves[0].amount)
        assertEquals("native", liquidityPool.reserves[0].asset)
        assertEquals("5000.0000000", liquidityPool.reserves[1].amount)
        assertEquals("EUR:GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", liquidityPool.reserves[1].asset)
        assertEquals("7500.0000000", liquidityPool.reserves[2].amount)
        assertEquals("LONGASSETCODE:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", liquidityPool.reserves[2].asset)

        assertEquals(false, liquidityPool.links.self.templated)
        assertNull(liquidityPool.links.operations?.templated)
        assertEquals(true, liquidityPool.links.transactions?.templated)
    }

    @Test
    fun testLiquidityPoolResponseInnerClassesDirectConstruction() {
        val reserve1 = LiquidityPoolResponse.Reserve(
            amount = "1000.0000000",
            asset = "native"
        )
        assertEquals("1000.0000000", reserve1.amount)
        assertEquals("native", reserve1.asset)
        
        val reserve2 = LiquidityPoolResponse.Reserve(
            amount = "500.0000000",
            asset = "USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
        )
        assertEquals("500.0000000", reserve2.amount)
        assertEquals("USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", reserve2.asset)

        val links = LiquidityPoolResponse.Links(
            self = Link("https://test.self", true),
            operations = Link("https://test.operations", false),
            transactions = Link("https://test.transactions", null)
        )
        assertEquals("https://test.self", links.self.href)
        assertEquals(true, links.self.templated)
        assertEquals("https://test.operations", links.operations?.href)
        assertEquals(false, links.operations?.templated)
        assertEquals("https://test.transactions", links.transactions?.href)
        assertNull(links.transactions?.templated)
        
        val linksWithNulls = LiquidityPoolResponse.Links(
            self = Link("https://test.only.self", false),
            operations = null,
            transactions = null
        )
        assertEquals("https://test.only.self", linksWithNulls.self.href)
        assertNull(linksWithNulls.operations)
        assertNull(linksWithNulls.transactions)
    }
}
