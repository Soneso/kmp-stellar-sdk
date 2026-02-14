package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.TradeAggregationResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TradeAggregationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val tradeAggJson = """
    {
        "timestamp": 1609459200000,
        "trade_count": 150,
        "base_volume": "5000.0000000",
        "counter_volume": "2500.0000000",
        "avg": "0.5000000",
        "high": "0.6000000",
        "high_r": {"n": 3, "d": 5},
        "low": "0.4000000",
        "low_r": {"n": 2, "d": 5},
        "open": "0.4500000",
        "open_r": {"n": 9, "d": 20},
        "close": "0.5500000",
        "close_r": {"n": 11, "d": 20}
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val agg = json.decodeFromString<TradeAggregationResponse>(tradeAggJson)
        assertEquals(1609459200000L, agg.timestamp)
        assertEquals(150, agg.tradeCount)
        assertEquals("5000.0000000", agg.baseVolume)
        assertEquals("2500.0000000", agg.counterVolume)
        assertEquals("0.5000000", agg.avg)
    }

    @Test
    fun testOhlcValues() {
        val agg = json.decodeFromString<TradeAggregationResponse>(tradeAggJson)
        assertEquals("0.6000000", agg.high)
        assertEquals(3L, agg.highR.numerator)
        assertEquals(5L, agg.highR.denominator)

        assertEquals("0.4000000", agg.low)
        assertEquals(2L, agg.lowR.numerator)
        assertEquals(5L, agg.lowR.denominator)

        assertEquals("0.4500000", agg.open)
        assertEquals(9L, agg.openR.numerator)
        assertEquals(20L, agg.openR.denominator)

        assertEquals("0.5500000", agg.close)
        assertEquals(11L, agg.closeR.numerator)
        assertEquals(20L, agg.closeR.denominator)
    }

    @Test
    fun testDataClassEquality() {
        val a1 = json.decodeFromString<TradeAggregationResponse>(tradeAggJson)
        val a2 = json.decodeFromString<TradeAggregationResponse>(tradeAggJson)
        assertEquals(a1, a2)
    }

    @Test
    fun testComprehensiveTradeAggregationResponseDeserialization() {
        val comprehensiveJson = """
        {
            "timestamp": 1609459200000,
            "trade_count": 250,
            "base_volume": "12500.0000000",
            "counter_volume": "31250.0000000",
            "avg": "2.5000000",
            "high": "2.7500000",
            "high_r": {"n": 11, "d": 4},
            "low": "2.2500000",
            "low_r": {"n": 9, "d": 4},
            "open": "2.4000000",
            "open_r": {"n": 12, "d": 5},
            "close": "2.6000000",
            "close_r": {"n": 13, "d": 5}
        }
        """.trimIndent()

        val tradeAggregation = json.decodeFromString<TradeAggregationResponse>(comprehensiveJson)

        assertEquals(1609459200000L, tradeAggregation.timestamp)
        assertEquals(250, tradeAggregation.tradeCount)
        assertEquals("12500.0000000", tradeAggregation.baseVolume)
        assertEquals("31250.0000000", tradeAggregation.counterVolume)
        assertEquals("2.5000000", tradeAggregation.avg)
        assertEquals("2.7500000", tradeAggregation.high)
        assertEquals("2.2500000", tradeAggregation.low)
        assertEquals("2.4000000", tradeAggregation.open)
        assertEquals("2.6000000", tradeAggregation.close)

        assertEquals(11L, tradeAggregation.highR.numerator)
        assertEquals(4L, tradeAggregation.highR.denominator)
        assertEquals(9L, tradeAggregation.lowR.numerator)
        assertEquals(4L, tradeAggregation.lowR.denominator)
        assertEquals(12L, tradeAggregation.openR.numerator)
        assertEquals(5L, tradeAggregation.openR.denominator)
        assertEquals(13L, tradeAggregation.closeR.numerator)
        assertEquals(5L, tradeAggregation.closeR.denominator)
    }

    @Test
    fun testTradeAggregationResponseWithZeroValues() {
        val zeroJson = """
        {
            "timestamp": 0,
            "trade_count": 0,
            "base_volume": "0.0000000",
            "counter_volume": "0.0000000",
            "avg": "0.0000000",
            "high": "0.0000000",
            "high_r": {"n": 0, "d": 1},
            "low": "0.0000000",
            "low_r": {"n": 0, "d": 1},
            "open": "0.0000000",
            "open_r": {"n": 0, "d": 1},
            "close": "0.0000000",
            "close_r": {"n": 0, "d": 1}
        }
        """.trimIndent()

        val tradeAggregation = json.decodeFromString<TradeAggregationResponse>(zeroJson)

        assertEquals(0L, tradeAggregation.timestamp)
        assertEquals(0, tradeAggregation.tradeCount)
        assertEquals("0.0000000", tradeAggregation.baseVolume)
        assertEquals("0.0000000", tradeAggregation.counterVolume)
        assertEquals("0.0000000", tradeAggregation.avg)

        assertEquals(0L, tradeAggregation.highR.numerator)
        assertEquals(1L, tradeAggregation.highR.denominator)
        assertEquals(0L, tradeAggregation.lowR.numerator)
        assertEquals(0L, tradeAggregation.openR.numerator)
        assertEquals(0L, tradeAggregation.closeR.numerator)
    }

    @Test
    fun testTradeAggregationResponseWithLargeValues() {
        val largeJson = """
        {
            "timestamp": 9223372036854775807,
            "trade_count": 2147483647,
            "base_volume": "999999999.9999999",
            "counter_volume": "888888888.8888888",
            "avg": "1.1234567",
            "high": "5.9999999",
            "high_r": {"n": 9223372036854775807, "d": 1000000000000000000},
            "low": "0.0000001",
            "low_r": {"n": 1, "d": 9223372036854775807},
            "open": "3.3333333",
            "open_r": {"n": 33333333, "d": 10000000},
            "close": "4.7777777",
            "close_r": {"n": 47777777, "d": 10000000}
        }
        """.trimIndent()

        val tradeAggregation = json.decodeFromString<TradeAggregationResponse>(largeJson)

        assertEquals(9223372036854775807L, tradeAggregation.timestamp)
        assertEquals(2147483647, tradeAggregation.tradeCount)
        assertEquals("999999999.9999999", tradeAggregation.baseVolume)
        assertEquals("888888888.8888888", tradeAggregation.counterVolume)

        assertEquals(9223372036854775807L, tradeAggregation.highR.numerator)
        assertEquals(1000000000000000000L, tradeAggregation.highR.denominator)
        assertEquals(1L, tradeAggregation.lowR.numerator)
        assertEquals(9223372036854775807L, tradeAggregation.lowR.denominator)
    }

    @Test
    fun testTradeAggregationResponseEdgeCaseScenarios() {
        val singleTradeJson = """
        {
            "timestamp": 1672531200000,
            "trade_count": 1,
            "base_volume": "1.0000000",
            "counter_volume": "1.0000000",
            "avg": "1.0000000",
            "high": "1.0000000",
            "high_r": {"n": 1, "d": 1},
            "low": "1.0000000",
            "low_r": {"n": 1, "d": 1},
            "open": "1.0000000",
            "open_r": {"n": 1, "d": 1},
            "close": "1.0000000",
            "close_r": {"n": 1, "d": 1}
        }
        """.trimIndent()

        val tradeAggregation = json.decodeFromString<TradeAggregationResponse>(singleTradeJson)

        assertEquals(1672531200000L, tradeAggregation.timestamp)
        assertEquals(1, tradeAggregation.tradeCount)
        assertEquals("1.0000000", tradeAggregation.high)
        assertEquals("1.0000000", tradeAggregation.low)
        assertEquals("1.0000000", tradeAggregation.open)
        assertEquals("1.0000000", tradeAggregation.close)

        assertEquals(1L, tradeAggregation.highR.numerator)
        assertEquals(1L, tradeAggregation.highR.denominator)
        assertEquals(1L, tradeAggregation.lowR.numerator)
        assertEquals(1L, tradeAggregation.lowR.denominator)
        assertEquals(1L, tradeAggregation.openR.numerator)
        assertEquals(1L, tradeAggregation.openR.denominator)
        assertEquals(1L, tradeAggregation.closeR.numerator)
        assertEquals(1L, tradeAggregation.closeR.denominator)
    }
}
