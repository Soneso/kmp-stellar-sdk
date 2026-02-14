package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.FeeStatsResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FeeStatsResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val feeStatsJson = """
    {
        "last_ledger": 7505182,
        "last_ledger_base_fee": 100,
        "ledger_capacity_usage": "0.97",
        "fee_charged": {
            "min": 100,
            "max": 50000,
            "mode": 200,
            "p10": 100,
            "p20": 100,
            "p30": 150,
            "p40": 200,
            "p50": 200,
            "p60": 250,
            "p70": 300,
            "p80": 500,
            "p90": 1000,
            "p95": 5000,
            "p99": 25000
        },
        "max_fee": {
            "min": 200,
            "max": 100000,
            "mode": 500,
            "p10": 200,
            "p20": 300,
            "p30": 400,
            "p40": 500,
            "p50": 600,
            "p60": 700,
            "p70": 1000,
            "p80": 5000,
            "p90": 10000,
            "p95": 50000,
            "p99": 100000
        }
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val feeStats = json.decodeFromString<FeeStatsResponse>(feeStatsJson)
        assertEquals(7505182L, feeStats.lastLedger)
        assertEquals(100L, feeStats.lastLedgerBaseFee)
        assertEquals("0.97", feeStats.ledgerCapacityUsage)
    }

    @Test
    fun testFeeCharged() {
        val feeStats = json.decodeFromString<FeeStatsResponse>(feeStatsJson)
        val fc = feeStats.feeCharged
        assertEquals(100L, fc.min)
        assertEquals(50000L, fc.max)
        assertEquals(200L, fc.mode)
        assertEquals(100L, fc.p10)
        assertEquals(100L, fc.p20)
        assertEquals(150L, fc.p30)
        assertEquals(200L, fc.p40)
        assertEquals(200L, fc.p50)
        assertEquals(250L, fc.p60)
        assertEquals(300L, fc.p70)
        assertEquals(500L, fc.p80)
        assertEquals(1000L, fc.p90)
        assertEquals(5000L, fc.p95)
        assertEquals(25000L, fc.p99)
    }

    @Test
    fun testMaxFee() {
        val feeStats = json.decodeFromString<FeeStatsResponse>(feeStatsJson)
        val mf = feeStats.maxFee
        assertEquals(200L, mf.min)
        assertEquals(100000L, mf.max)
        assertEquals(500L, mf.mode)
        assertEquals(200L, mf.p10)
        assertEquals(300L, mf.p20)
        assertEquals(400L, mf.p30)
        assertEquals(500L, mf.p40)
        assertEquals(600L, mf.p50)
        assertEquals(700L, mf.p60)
        assertEquals(1000L, mf.p70)
        assertEquals(5000L, mf.p80)
        assertEquals(10000L, mf.p90)
        assertEquals(50000L, mf.p95)
        assertEquals(100000L, mf.p99)
    }

    @Test
    fun testFeeDistributionEquality() {
        val d1 = FeeStatsResponse.FeeDistribution(100, 200, 150, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 195)
        val d2 = FeeStatsResponse.FeeDistribution(100, 200, 150, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 195)
        assertEquals(d1, d2)
    }

    @Test
    fun testComprehensiveFeeStatsResponseDeserialization() {
        val comprehensiveJson = """
        {
            "last_ledger": 32069474,
            "last_ledger_base_fee": 100,
            "ledger_capacity_usage": "0.97",
            "fee_charged": {
                "min": 100,
                "max": 50000,
                "mode": 100,
                "p10": 100,
                "p20": 100,
                "p30": 100,
                "p40": 100,
                "p50": 500,
                "p60": 1000,
                "p70": 2000,
                "p80": 5000,
                "p90": 10000,
                "p95": 20000,
                "p99": 50000
            },
            "max_fee": {
                "min": 100,
                "max": 100000,
                "mode": 1000,
                "p10": 500,
                "p20": 1000,
                "p30": 1000,
                "p40": 2000,
                "p50": 5000,
                "p60": 10000,
                "p70": 15000,
                "p80": 25000,
                "p90": 50000,
                "p95": 75000,
                "p99": 100000
            }
        }
        """.trimIndent()

        val feeStats = json.decodeFromString<FeeStatsResponse>(comprehensiveJson)

        assertEquals(32069474L, feeStats.lastLedger)
        assertEquals(100L, feeStats.lastLedgerBaseFee)
        assertEquals("0.97", feeStats.ledgerCapacityUsage)

        assertEquals(100L, feeStats.feeCharged.min)
        assertEquals(50000L, feeStats.feeCharged.max)
        assertEquals(100L, feeStats.feeCharged.mode)
        assertEquals(100L, feeStats.feeCharged.p10)
        assertEquals(100L, feeStats.feeCharged.p20)
        assertEquals(100L, feeStats.feeCharged.p30)
        assertEquals(100L, feeStats.feeCharged.p40)
        assertEquals(500L, feeStats.feeCharged.p50)
        assertEquals(1000L, feeStats.feeCharged.p60)
        assertEquals(2000L, feeStats.feeCharged.p70)
        assertEquals(5000L, feeStats.feeCharged.p80)
        assertEquals(10000L, feeStats.feeCharged.p90)
        assertEquals(20000L, feeStats.feeCharged.p95)
        assertEquals(50000L, feeStats.feeCharged.p99)

        assertEquals(100L, feeStats.maxFee.min)
        assertEquals(100000L, feeStats.maxFee.max)
        assertEquals(1000L, feeStats.maxFee.mode)
        assertEquals(500L, feeStats.maxFee.p10)
        assertEquals(1000L, feeStats.maxFee.p20)
        assertEquals(1000L, feeStats.maxFee.p30)
        assertEquals(2000L, feeStats.maxFee.p40)
        assertEquals(5000L, feeStats.maxFee.p50)
        assertEquals(10000L, feeStats.maxFee.p60)
        assertEquals(15000L, feeStats.maxFee.p70)
        assertEquals(25000L, feeStats.maxFee.p80)
        assertEquals(50000L, feeStats.maxFee.p90)
        assertEquals(75000L, feeStats.maxFee.p95)
        assertEquals(100000L, feeStats.maxFee.p99)
    }

    @Test
    fun testFeeStatsResponseWithLowCapacityUsage() {
        val lowCapacityJson = """
        {
            "last_ledger": 45000000,
            "last_ledger_base_fee": 100,
            "ledger_capacity_usage": "0.15",
            "fee_charged": {
                "min": 100,
                "max": 200,
                "mode": 100,
                "p10": 100,
                "p20": 100,
                "p30": 100,
                "p40": 100,
                "p50": 100,
                "p60": 100,
                "p70": 100,
                "p80": 100,
                "p90": 100,
                "p95": 150,
                "p99": 200
            },
            "max_fee": {
                "min": 100,
                "max": 1000,
                "mode": 100,
                "p10": 100,
                "p20": 100,
                "p30": 100,
                "p40": 100,
                "p50": 100,
                "p60": 100,
                "p70": 200,
                "p80": 500,
                "p90": 800,
                "p95": 900,
                "p99": 1000
            }
        }
        """.trimIndent()

        val feeStats = json.decodeFromString<FeeStatsResponse>(lowCapacityJson)

        assertEquals(45000000L, feeStats.lastLedger)
        assertEquals(100L, feeStats.lastLedgerBaseFee)
        assertEquals("0.15", feeStats.ledgerCapacityUsage)

        assertEquals(100L, feeStats.feeCharged.min)
        assertEquals(200L, feeStats.feeCharged.max)
        assertEquals(100L, feeStats.feeCharged.mode)
        assertEquals(150L, feeStats.feeCharged.p95)
        assertEquals(200L, feeStats.feeCharged.p99)

        assertEquals(100L, feeStats.maxFee.min)
        assertEquals(1000L, feeStats.maxFee.max)
        assertEquals(200L, feeStats.maxFee.p70)
        assertEquals(500L, feeStats.maxFee.p80)
        assertEquals(800L, feeStats.maxFee.p90)
        assertEquals(900L, feeStats.maxFee.p95)
        assertEquals(1000L, feeStats.maxFee.p99)
    }

    @Test
    fun testFeeStatsResponseWithHighCapacityUsage() {
        val highCapacityJson = """
        {
            "last_ledger": 99999999,
            "last_ledger_base_fee": 10000,
            "ledger_capacity_usage": "1.00",
            "fee_charged": {
                "min": 10000,
                "max": 1000000,
                "mode": 50000,
                "p10": 15000,
                "p20": 25000,
                "p30": 35000,
                "p40": 45000,
                "p50": 50000,
                "p60": 60000,
                "p70": 75000,
                "p80": 100000,
                "p90": 250000,
                "p95": 500000,
                "p99": 1000000
            },
            "max_fee": {
                "min": 10000,
                "max": 5000000,
                "mode": 100000,
                "p10": 25000,
                "p20": 50000,
                "p30": 75000,
                "p40": 100000,
                "p50": 150000,
                "p60": 200000,
                "p70": 300000,
                "p80": 500000,
                "p90": 1000000,
                "p95": 2500000,
                "p99": 5000000
            }
        }
        """.trimIndent()

        val feeStats = json.decodeFromString<FeeStatsResponse>(highCapacityJson)

        assertEquals(99999999L, feeStats.lastLedger)
        assertEquals(10000L, feeStats.lastLedgerBaseFee)
        assertEquals("1.00", feeStats.ledgerCapacityUsage)

        assertEquals(10000L, feeStats.feeCharged.min)
        assertEquals(1000000L, feeStats.feeCharged.max)
        assertEquals(50000L, feeStats.feeCharged.mode)
        assertEquals(500000L, feeStats.feeCharged.p95)
        assertEquals(1000000L, feeStats.feeCharged.p99)

        assertEquals(10000L, feeStats.maxFee.min)
        assertEquals(5000000L, feeStats.maxFee.max)
        assertEquals(100000L, feeStats.maxFee.mode)
        assertEquals(2500000L, feeStats.maxFee.p95)
        assertEquals(5000000L, feeStats.maxFee.p99)
    }

    @Test
    fun testFeeStatsResponseWithZeroCapacityUsage() {
        val zeroCapacityJson = """
        {
            "last_ledger": 1,
            "last_ledger_base_fee": 100,
            "ledger_capacity_usage": "0.00",
            "fee_charged": {
                "min": 100,
                "max": 100,
                "mode": 100,
                "p10": 100,
                "p20": 100,
                "p30": 100,
                "p40": 100,
                "p50": 100,
                "p60": 100,
                "p70": 100,
                "p80": 100,
                "p90": 100,
                "p95": 100,
                "p99": 100
            },
            "max_fee": {
                "min": 100,
                "max": 100,
                "mode": 100,
                "p10": 100,
                "p20": 100,
                "p30": 100,
                "p40": 100,
                "p50": 100,
                "p60": 100,
                "p70": 100,
                "p80": 100,
                "p90": 100,
                "p95": 100,
                "p99": 100
            }
        }
        """.trimIndent()

        val feeStats = json.decodeFromString<FeeStatsResponse>(zeroCapacityJson)

        assertEquals(1L, feeStats.lastLedger)
        assertEquals(100L, feeStats.lastLedgerBaseFee)
        assertEquals("0.00", feeStats.ledgerCapacityUsage)

        assertEquals(100L, feeStats.feeCharged.min)
        assertEquals(100L, feeStats.feeCharged.max)
        assertEquals(100L, feeStats.feeCharged.mode)
        assertEquals(100L, feeStats.feeCharged.p50)
        assertEquals(100L, feeStats.feeCharged.p99)

        assertEquals(100L, feeStats.maxFee.min)
        assertEquals(100L, feeStats.maxFee.max)
        assertEquals(100L, feeStats.maxFee.mode)
        assertEquals(100L, feeStats.maxFee.p50)
        assertEquals(100L, feeStats.maxFee.p99)
    }

    @Test
    fun testFeeStatsResponseFeeDistributionDirectConstruction() {
        val feeDistribution = FeeStatsResponse.FeeDistribution(
            min = 100L,
            max = 10000L,
            mode = 500L,
            p10 = 200L,
            p20 = 300L,
            p30 = 400L,
            p40 = 450L,
            p50 = 500L,
            p60 = 600L,
            p70 = 750L,
            p80 = 1000L,
            p90 = 2000L,
            p95 = 5000L,
            p99 = 10000L
        )

        assertEquals(100L, feeDistribution.min)
        assertEquals(10000L, feeDistribution.max)
        assertEquals(500L, feeDistribution.mode)
        assertEquals(200L, feeDistribution.p10)
        assertEquals(300L, feeDistribution.p20)
        assertEquals(400L, feeDistribution.p30)
        assertEquals(450L, feeDistribution.p40)
        assertEquals(500L, feeDistribution.p50)
        assertEquals(600L, feeDistribution.p60)
        assertEquals(750L, feeDistribution.p70)
        assertEquals(1000L, feeDistribution.p80)
        assertEquals(2000L, feeDistribution.p90)
        assertEquals(5000L, feeDistribution.p95)
        assertEquals(10000L, feeDistribution.p99)
    }
}
