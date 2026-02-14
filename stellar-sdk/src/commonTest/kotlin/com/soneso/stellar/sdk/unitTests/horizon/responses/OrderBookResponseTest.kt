package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.AssetTypeCreditAlphaNum4
import com.soneso.stellar.sdk.AssetTypeCreditAlphaNum12
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.horizon.responses.OrderBookResponse
import com.soneso.stellar.sdk.horizon.responses.Price
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderBookResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val orderBookJson = """
    {
        "base": {
            "asset_type": "native"
        },
        "counter": {
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
        },
        "bids": [
            {
                "amount": "100.0000000",
                "price": "0.5000000",
                "price_r": {"n": 1, "d": 2}
            },
            {
                "amount": "200.0000000",
                "price": "0.4500000",
                "price_r": {"n": 9, "d": 20}
            }
        ],
        "asks": [
            {
                "amount": "50.0000000",
                "price": "0.5500000",
                "price_r": {"n": 11, "d": 20}
            }
        ]
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val orderBook = json.decodeFromString<OrderBookResponse>(orderBookJson)
        assertEquals("native", orderBook.baseAsset.assetType)
        assertEquals("credit_alphanum4", orderBook.counterAsset.assetType)
        assertEquals("USD", orderBook.counterAsset.assetCode)
    }

    @Test
    fun testBids() {
        val orderBook = json.decodeFromString<OrderBookResponse>(orderBookJson)
        assertEquals(2, orderBook.bids.size)

        val bid1 = orderBook.bids[0]
        assertEquals("100.0000000", bid1.amount)
        assertEquals("0.5000000", bid1.price)
        assertEquals(1L, bid1.priceR.numerator)
        assertEquals(2L, bid1.priceR.denominator)

        val bid2 = orderBook.bids[1]
        assertEquals("200.0000000", bid2.amount)
        assertEquals("0.4500000", bid2.price)
        assertEquals(9L, bid2.priceR.numerator)
        assertEquals(20L, bid2.priceR.denominator)
    }

    @Test
    fun testAsks() {
        val orderBook = json.decodeFromString<OrderBookResponse>(orderBookJson)
        assertEquals(1, orderBook.asks.size)

        val ask = orderBook.asks[0]
        assertEquals("50.0000000", ask.amount)
        assertEquals("0.5500000", ask.price)
        assertEquals(11L, ask.priceR.numerator)
        assertEquals(20L, ask.priceR.denominator)
    }

    @Test
    fun testEmptyOrderBook() {
        val emptyJson = """
        {
            "base": {"asset_type": "native"},
            "counter": {"asset_type": "native"},
            "asks": [],
            "bids": []
        }
        """.trimIndent()
        val orderBook = json.decodeFromString<OrderBookResponse>(emptyJson)
        assertTrue(orderBook.bids.isEmpty())
        assertTrue(orderBook.asks.isEmpty())
    }

    @Test
    fun testRowEquality() {
        val r1 = OrderBookResponse.Row("100.0", "0.5", com.soneso.stellar.sdk.horizon.responses.Price(1, 2))
        val r2 = OrderBookResponse.Row("100.0", "0.5", com.soneso.stellar.sdk.horizon.responses.Price(1, 2))
        assertEquals(r1, r2)
    }

    @Test
    fun testComprehensiveOrderBookResponseDeserialization() {
        val comprehensiveJson = """
        {
            "base": {
                "asset_type": "native"
            },
            "counter": {
                "asset_type": "credit_alphanum4",
                "asset_code": "USD",
                "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
            },
            "asks": [
                {
                    "amount": "1000.0000000",
                    "price": "2.5000000",
                    "price_r": {"n": 5, "d": 2}
                },
                {
                    "amount": "500.0000000",
                    "price": "2.6000000",
                    "price_r": {"n": 13, "d": 5}
                },
                {
                    "amount": "750.0000000",
                    "price": "2.7500000",
                    "price_r": {"n": 11, "d": 4}
                }
            ],
            "bids": [
                {
                    "amount": "800.0000000",
                    "price": "2.4000000",
                    "price_r": {"n": 12, "d": 5}
                },
                {
                    "amount": "600.0000000",
                    "price": "2.3000000",
                    "price_r": {"n": 23, "d": 10}
                }
            ]
        }
        """.trimIndent()

        val orderBook = json.decodeFromString<OrderBookResponse>(comprehensiveJson)

        assertEquals("native", orderBook.baseAsset.assetType)
        assertNull(orderBook.baseAsset.assetCode)
        assertNull(orderBook.baseAsset.assetIssuer)
        assertEquals("credit_alphanum4", orderBook.counterAsset.assetType)
        assertEquals("USD", orderBook.counterAsset.assetCode)
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", orderBook.counterAsset.assetIssuer)

        val baseSdkAsset = orderBook.base
        assertTrue(baseSdkAsset is AssetTypeNative)
        val counterSdkAsset = orderBook.counter
        assertTrue(counterSdkAsset is AssetTypeCreditAlphaNum4)
        if (counterSdkAsset is AssetTypeCreditAlphaNum4) {
            assertEquals("USD", counterSdkAsset.code)
        }

        assertEquals(3, orderBook.asks.size)
        assertEquals("1000.0000000", orderBook.asks[0].amount)
        assertEquals("2.5000000", orderBook.asks[0].price)
        assertEquals(5L, orderBook.asks[0].priceR.numerator)
        assertEquals(2L, orderBook.asks[0].priceR.denominator)
        assertEquals("500.0000000", orderBook.asks[1].amount)
        assertEquals(13L, orderBook.asks[1].priceR.numerator)
        assertEquals("750.0000000", orderBook.asks[2].amount)
        assertEquals(11L, orderBook.asks[2].priceR.numerator)
        assertEquals(4L, orderBook.asks[2].priceR.denominator)

        assertEquals(2, orderBook.bids.size)
        assertEquals("800.0000000", orderBook.bids[0].amount)
        assertEquals(12L, orderBook.bids[0].priceR.numerator)
        assertEquals("600.0000000", orderBook.bids[1].amount)
        assertEquals(23L, orderBook.bids[1].priceR.numerator)
        assertEquals(10L, orderBook.bids[1].priceR.denominator)
    }

    @Test
    fun testOrderBookResponseWithCredit12Assets() {
        val credit12Json = """
        {
            "base": {
                "asset_type": "credit_alphanum12",
                "asset_code": "LONGASSETCD",
                "asset_issuer": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU"
            },
            "counter": {
                "asset_type": "credit_alphanum4",
                "asset_code": "EUR",
                "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
            },
            "asks": [
                {
                    "amount": "100.0000000",
                    "price": "0.8500000",
                    "price_r": {"n": 17, "d": 20}
                }
            ],
            "bids": [
                {
                    "amount": "200.0000000",
                    "price": "0.8000000",
                    "price_r": {"n": 4, "d": 5}
                }
            ]
        }
        """.trimIndent()

        val orderBook = json.decodeFromString<OrderBookResponse>(credit12Json)

        assertEquals("credit_alphanum12", orderBook.baseAsset.assetType)
        assertEquals("LONGASSETCD", orderBook.baseAsset.assetCode)

        val baseSdkAsset = orderBook.base
        assertTrue(baseSdkAsset is AssetTypeCreditAlphaNum12)
        if (baseSdkAsset is AssetTypeCreditAlphaNum12) {
            assertEquals("LONGASSETCD", baseSdkAsset.code)
            assertEquals("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", baseSdkAsset.issuer)
        }

        assertEquals(1, orderBook.asks.size)
        assertEquals(17L, orderBook.asks[0].priceR.numerator)
        assertEquals(20L, orderBook.asks[0].priceR.denominator)
        assertEquals(1, orderBook.bids.size)
        assertEquals(4L, orderBook.bids[0].priceR.numerator)
        assertEquals(5L, orderBook.bids[0].priceR.denominator)
    }

    @Test
    fun testOrderBookResponseWithExtremePrices() {
        val extremeJson = """
        {
            "base": {
                "asset_type": "credit_alphanum4",
                "asset_code": "BTC",
                "asset_issuer": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
            },
            "counter": {
                "asset_type": "credit_alphanum4",
                "asset_code": "USD",
                "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
            },
            "asks": [
                {
                    "amount": "0.0000001",
                    "price": "99999.9999999",
                    "price_r": {"n": 999999999999, "d": 10000000}
                }
            ],
            "bids": [
                {
                    "amount": "999999.9999999",
                    "price": "0.0000001",
                    "price_r": {"n": 1, "d": 10000000}
                }
            ]
        }
        """.trimIndent()

        val orderBook = json.decodeFromString<OrderBookResponse>(extremeJson)

        assertEquals("0.0000001", orderBook.asks[0].amount)
        assertEquals("99999.9999999", orderBook.asks[0].price)
        assertEquals(999999999999L, orderBook.asks[0].priceR.numerator)
        assertEquals(10000000L, orderBook.asks[0].priceR.denominator)

        assertEquals("999999.9999999", orderBook.bids[0].amount)
        assertEquals("0.0000001", orderBook.bids[0].price)
        assertEquals(1L, orderBook.bids[0].priceR.numerator)
        assertEquals(10000000L, orderBook.bids[0].priceR.denominator)
    }

    @Test
    fun testOrderBookResponseRowDirectConstruction() {
        val row = OrderBookResponse.Row(
            amount = "1000.0000000",
            price = "2.5000000",
            priceR = Price(numerator = 5L, denominator = 2L)
        )

        assertEquals("1000.0000000", row.amount)
        assertEquals("2.5000000", row.price)
        assertEquals(5L, row.priceR.numerator)
        assertEquals(2L, row.priceR.denominator)
    }
}
