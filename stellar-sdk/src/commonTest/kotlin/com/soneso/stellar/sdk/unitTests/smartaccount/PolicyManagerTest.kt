//
//  PolicyManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.compareScValHostOrder
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.smartaccount.oz.OZPolicyManager
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.xdr.Int128PartsXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [PolicyInstallParams] ScVal encoding.
 *
 * These tests verify that the three policy types (SimpleThreshold, WeightedThreshold,
 * SpendingLimit) produce correct ScVal output compatible with the Soroban smart account
 * contracts. Input validation and error cases are also covered.
 *
 * Network-dependent operations (addPolicy, removePolicy) require a connected
 * OZSmartAccountKit instance and are covered by integration tests instead.
 */
class PolicyManagerTest {

    // MARK: - SimpleThreshold Tests

    @Test
    fun testSimpleThreshold_createsMapWithThresholdKey() {
        val params = PolicyInstallParams.SimpleThreshold(threshold = 2u)
        val scVal = params.toScVal()

        // Verify it produces a Map
        assertIs<SCValXdr.Map>(scVal)

        val entries = extractMapEntries(scVal)
        assertEquals(1, entries.size, "SimpleThreshold map must have exactly 1 entry")

        // Verify the key is "threshold"
        val key = entries[0].key
        assertIs<SCValXdr.Sym>(key)
        assertEquals("threshold", (key as SCValXdr.Sym).value.value)

        // Verify the value is U32(2)
        val value = entries[0].`val`
        assertIs<SCValXdr.U32>(value)
        assertEquals(2u, (value as SCValXdr.U32).value.value)
    }

    @Test
    fun testSimpleThreshold_thresholdOfOne() {
        val params = PolicyInstallParams.SimpleThreshold(threshold = 1u)
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val value = entries[0].`val`
        assertIs<SCValXdr.U32>(value)
        assertEquals(1u, (value as SCValXdr.U32).value.value)
    }

    @Test
    fun testSimpleThreshold_largeThresholdValue() {
        val params = PolicyInstallParams.SimpleThreshold(threshold = UInt.MAX_VALUE)
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val value = entries[0].`val`
        assertIs<SCValXdr.U32>(value)
        assertEquals(UInt.MAX_VALUE, (value as SCValXdr.U32).value.value)
    }

    @Test
    fun testSimpleThreshold_deterministicXdrEncoding() {
        val params1 = PolicyInstallParams.SimpleThreshold(threshold = 5u)
        val params2 = PolicyInstallParams.SimpleThreshold(threshold = 5u)

        val xdr1 = encodeToXdrHex(params1.toScVal())
        val xdr2 = encodeToXdrHex(params2.toScVal())

        assertEquals(xdr1, xdr2, "Identical SimpleThreshold params must produce identical XDR")
    }

    @Test
    fun testSimpleThreshold_differentThresholdsDifferentXdr() {
        val params1 = PolicyInstallParams.SimpleThreshold(threshold = 2u)
        val params2 = PolicyInstallParams.SimpleThreshold(threshold = 3u)

        val xdr1 = encodeToXdrHex(params1.toScVal())
        val xdr2 = encodeToXdrHex(params2.toScVal())

        assertTrue(xdr1 != xdr2, "Different threshold values must produce different XDR")
    }

    // MARK: - WeightedThreshold Tests

    @Test
    fun testWeightedThreshold_createsMapWithCorrectKeys() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 50u),
            threshold = 100u
        )
        val scVal = params.toScVal()

        assertIs<SCValXdr.Map>(scVal)

        val entries = extractMapEntries(scVal)
        assertEquals(2, entries.size, "WeightedThreshold map must have exactly 2 entries")

        // First key must be "signer_weights" (alphabetically before "threshold")
        assertEquals("signer_weights", extractSymbolName(entries[0].key))

        // Second key must be "threshold"
        assertEquals("threshold", extractSymbolName(entries[1].key))
    }

    @Test
    fun testWeightedThreshold_thresholdValueIsCorrect() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 50u),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val thresholdEntry = entries.first { extractSymbolName(it.key) == "threshold" }
        val thresholdValue = thresholdEntry.`val`
        assertIs<SCValXdr.U32>(thresholdValue)
        assertEquals(100u, (thresholdValue as SCValXdr.U32).value.value)
    }

    @Test
    fun testWeightedThreshold_signerWeightsInnerMapContainsCorrectEntries() {
        val signer1 = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val signer2 = DelegatedSigner(
            address = "GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                signer1 to 60u,
                signer2 to 40u
            ),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val signerWeightsEntry = entries.first { extractSymbolName(it.key) == "signer_weights" }
        val innerMap = signerWeightsEntry.`val`
        assertIs<SCValXdr.Map>(innerMap)

        val innerEntries = extractMapEntries(innerMap)
        assertEquals(2, innerEntries.size, "Inner signer weights map must have 2 entries")

        // Each value must be U32
        for (entry in innerEntries) {
            assertIs<SCValXdr.U32>(entry.`val`)
        }

        // Verify the weight values are present (order depends on XDR key sorting)
        val weights = innerEntries.map { (it.`val` as SCValXdr.U32).value.value }.toSet()
        assertTrue(60u in weights, "Weight 60 must be present")
        assertTrue(40u in weights, "Weight 40 must be present")
    }

    @Test
    fun testWeightedThreshold_signerWeightsAreSortedInHostOrder() {
        val signer1 = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val signer2 = DelegatedSigner(
            address = "GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"
        )
        val signer3 = DelegatedSigner(
            address = "GB33CUURS5XLLECMLSE2EMMDJBMZSVF27BW6PLS53OFTJMP46CZH3CVG"
        )

        // Insert in reverse order to verify sorting is applied
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                signer3 to 20u,
                signer1 to 50u,
                signer2 to 30u
            ),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val signerWeightsEntry = entries.first { extractSymbolName(it.key) == "signer_weights" }
        val innerEntries = extractMapEntries(signerWeightsEntry.`val`)

        assertEquals(3, innerEntries.size)

        // Verify keys are in the host's ScMap key order
        assertKeysAreInHostOrder(innerEntries)
    }

    @Test
    fun testWeightedThreshold_withExternalSigners() {
        val signer = ExternalSigner(
            verifierAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            keyData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 75u),
            threshold = 75u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        assertEquals(2, entries.size)
        assertEquals("signer_weights", extractSymbolName(entries[0].key))
        assertEquals("threshold", extractSymbolName(entries[1].key))

        val innerEntries = extractMapEntries(entries[0].`val`)
        assertEquals(1, innerEntries.size)

        // The key should be the signer's ScVal representation (a Vec for ExternalSigner)
        val signerKey = innerEntries[0].key
        assertIs<SCValXdr.Vec>(signerKey)

        // The value is the weight
        val weightVal = innerEntries[0].`val`
        assertIs<SCValXdr.U32>(weightVal)
        assertEquals(75u, (weightVal as SCValXdr.U32).value.value)
    }

    @Test
    fun testWeightedThreshold_mixedDelegatedAndExternalSigners() {
        val delegatedSigner = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val externalSigner = ExternalSigner(
            verifierAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            keyData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                delegatedSigner to 60u,
                externalSigner to 40u
            ),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val innerEntries = extractMapEntries(entries[0].`val`)
        assertEquals(2, innerEntries.size)

        // Both signers should be Vec-typed keys (both DelegatedSigner and ExternalSigner produce Vecs)
        for (entry in innerEntries) {
            assertIs<SCValXdr.Vec>(entry.key)
        }

        // Verify keys are in the host's ScMap key order
        assertKeysAreInHostOrder(innerEntries)
    }

    @Test
    fun testWeightedThreshold_emptySignerWeightsThrows() {
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = emptyMap(),
            threshold = 100u
        )

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("at least one signer"),
            "Error message should mention at least one signer requirement"
        )
    }

    @Test
    fun testWeightedThreshold_deterministicRegardlessOfInsertionOrder() {
        val signer1 = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val signer2 = DelegatedSigner(
            address = "GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"
        )

        val paramsA = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer1 to 50u, signer2 to 30u),
            threshold = 80u
        )
        val paramsB = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer2 to 30u, signer1 to 50u),
            threshold = 80u
        )

        val xdrA = encodeToXdrHex(paramsA.toScVal())
        val xdrB = encodeToXdrHex(paramsB.toScVal())

        assertEquals(
            xdrA, xdrB,
            "Same signers in different order must produce identical XDR"
        )
    }

    // MARK: - SpendingLimit Tests

    @Test
    fun testSpendingLimit_createsMapWithCorrectKeys() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L), // 1 XLM
            periodLedgers = 17280u        // ~1 day
        )
        val scVal = params.toScVal()

        assertIs<SCValXdr.Map>(scVal)

        val entries = extractMapEntries(scVal)
        assertEquals(2, entries.size, "SpendingLimit map must have exactly 2 entries")

        // Alphabetical order: "period_ledgers" before "spending_limit"
        assertEquals("period_ledgers", extractSymbolName(entries[0].key))
        assertEquals("spending_limit", extractSymbolName(entries[1].key))
    }

    @Test
    fun testSpendingLimit_periodLedgersIsU32() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L),
            periodLedgers = 17280u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val periodEntry = entries.first { extractSymbolName(it.key) == "period_ledgers" }

        val periodValue = periodEntry.`val`
        assertIs<SCValXdr.U32>(periodValue)
        assertEquals(17280u, (periodValue as SCValXdr.U32).value.value)
    }

    @Test
    fun testSpendingLimit_spendingLimitIsI128() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L), // 1 XLM in stroops
            periodLedgers = 17280u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }

        val limitValue = limitEntry.`val`
        assertIs<SCValXdr.I128>(limitValue)

        val parts = (limitValue as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value, "High part must be 0 for positive values within Long range")
        assertEquals(10_000_000uL, parts.lo.value, "Low part must match the stroops value")
    }

    @Test
    fun testSpendingLimit_largeI128Value() {
        // Test with a large stroops value (e.g. 1 billion XLM = 10^16 stroops)
        val largeStroops = BigInteger.fromLong(10_000_000_000_000_000L) // 1 billion XLM
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = largeStroops,
            periodLedgers = Util.LEDGERS_PER_DAY.toUInt()
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }
        val limitValue = limitEntry.`val`
        assertIs<SCValXdr.I128>(limitValue)

        val parts = (limitValue as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(largeStroops.ulongValue(), parts.lo.value)
    }

    @Test
    fun testSpendingLimit_zeroSpendingLimitThrows() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.ZERO,
            periodLedgers = 17280u
        )

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("greater than zero"),
            "Error message should mention spending limit must be greater than zero"
        )
    }

    @Test
    fun testSpendingLimit_negativeSpendingLimitThrows() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(-100L),
            periodLedgers = 17280u
        )

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("greater than zero"),
            "Error message should mention spending limit must be greater than zero"
        )
    }

    @Test
    fun testSpendingLimit_zeroPeriodLedgersThrows() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L),
            periodLedgers = 0u
        )

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("greater than zero"),
            "Error message should mention period ledgers must be greater than zero"
        )
    }

    @Test
    fun testSpendingLimit_deterministicXdrEncoding() {
        val params1 = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(50_000_000L),
            periodLedgers = 34560u
        )
        val params2 = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(50_000_000L),
            periodLedgers = 34560u
        )

        val xdr1 = encodeToXdrHex(params1.toScVal())
        val xdr2 = encodeToXdrHex(params2.toScVal())

        assertEquals(xdr1, xdr2, "Identical SpendingLimit params must produce identical XDR")
    }

    @Test
    fun testSpendingLimit_oneLedgerPeriod() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.ONE,
            periodLedgers = 1u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)

        val periodEntry = entries.first { extractSymbolName(it.key) == "period_ledgers" }
        assertEquals(1u, (periodEntry.`val` as SCValXdr.U32).value.value)

        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }
        val parts = (limitEntry.`val` as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(1uL, parts.lo.value)
    }

    @Test
    fun testSpendingLimit_maxUIntPeriodLedgers() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1_000_000L),
            periodLedgers = UInt.MAX_VALUE
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val periodEntry = entries.first { extractSymbolName(it.key) == "period_ledgers" }
        assertEquals(UInt.MAX_VALUE, (periodEntry.`val` as SCValXdr.U32).value.value)
    }

    // MARK: - Cross-Policy Type Verification

    @Test
    fun testAllPolicyTypes_produceMapScVal() {
        val simple = PolicyInstallParams.SimpleThreshold(threshold = 2u).toScVal()
        assertIs<SCValXdr.Map>(simple)

        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val weighted = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 50u),
            threshold = 50u
        ).toScVal()
        assertIs<SCValXdr.Map>(weighted)

        val spending = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L),
            periodLedgers = 17280u
        ).toScVal()
        assertIs<SCValXdr.Map>(spending)
    }

    @Test
    fun testAllPolicyTypes_produceDifferentXdr() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )

        val simpleXdr = encodeToXdrHex(
            PolicyInstallParams.SimpleThreshold(threshold = 2u).toScVal()
        )
        val weightedXdr = encodeToXdrHex(
            PolicyInstallParams.WeightedThreshold(
                signerWeights = mapOf(signer to 50u),
                threshold = 50u
            ).toScVal()
        )
        val spendingXdr = encodeToXdrHex(
            PolicyInstallParams.SpendingLimit(
                spendingLimit = BigInteger.fromLong(10_000_000L),
                periodLedgers = 17280u
            ).toScVal()
        )

        assertTrue(simpleXdr != weightedXdr, "SimpleThreshold and WeightedThreshold must differ")
        assertTrue(simpleXdr != spendingXdr, "SimpleThreshold and SpendingLimit must differ")
        assertTrue(weightedXdr != spendingXdr, "WeightedThreshold and SpendingLimit must differ")
    }

    // MARK: - amountToStroops Tests

    @Test
    fun testAmountToStroops_oneXlm() {
        val stroops = Util.amountToStroops("1")
        assertEquals(BigInteger.fromLong(10_000_000L), stroops)
    }

    @Test
    fun testAmountToStroops_fractionalAmount() {
        val stroops = Util.amountToStroops("0.5")
        assertEquals(BigInteger.fromLong(5_000_000L), stroops)
    }

    @Test
    fun testAmountToStroops_largeAmount() {
        val stroops = Util.amountToStroops("1000")
        assertEquals(BigInteger.fromLong(10_000_000_000L), stroops)
    }

    @Test
    fun testAmountToStroops_emptyString() {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("")
        }
    }

    @Test
    fun testAmountToStroops_whitespace() {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("   ")
        }
    }

    @Test
    fun testAmountToStroops_nonNumeric() {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("abc")
        }
    }

    @Test
    fun testAmountToStroops_scientificNotation() {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("1e7")
        }
    }

    @Test
    fun testAmountToStroops_subStroopAmount() {
        // 0.00000001 is less than 1 stroop (0.0000001), rounds to 0
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("0.00000001")
        }
    }

    @Test
    fun testAmountToStroops_decimalPrecision() {
        val stroops = Util.amountToStroops("10.5")
        assertEquals(BigInteger.fromLong(105_000_000L), stroops)
    }

    @Test
    fun testAmountToStroops_maxPrecision() {
        // 0.0000001 = 1 stroop (minimum)
        val stroops = Util.amountToStroops("0.0000001")
        assertEquals(BigInteger.fromLong(1L), stroops)
    }

    // MARK: - stroopsToI128ScVal Tests

    @Test
    fun testStroopsToI128ScVal_basicValue() {
        val scVal = Util.stroopsToI128ScVal(BigInteger.fromLong(10_000_000L))

        assertIs<SCValXdr.I128>(scVal)
        val parts = (scVal as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(10_000_000uL, parts.lo.value)
    }

    @Test
    fun testStroopsToI128ScVal_maxLongValue() {
        val scVal = Util.stroopsToI128ScVal(BigInteger.fromLong(Long.MAX_VALUE))

        assertIs<SCValXdr.I128>(scVal)
        val parts = (scVal as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(Long.MAX_VALUE.toULong(), parts.lo.value)
    }

    // MARK: - Helper Functions

    /**
     * Extracts map entries from an SCValXdr.Map.
     */
    private fun extractMapEntries(scVal: SCValXdr): List<SCMapEntryXdr> {
        assertIs<SCValXdr.Map>(scVal)
        return (scVal as SCValXdr.Map).value?.value ?: emptyList()
    }

    /**
     * Extracts the symbol name from a Sym SCValXdr.
     */
    private fun extractSymbolName(scVal: SCValXdr): String {
        assertIs<SCValXdr.Sym>(scVal)
        return (scVal as SCValXdr.Sym).value.value
    }

    /**
     * Encodes an SCValXdr to its hex string representation.
     */
    private fun encodeToXdrHex(scVal: SCValXdr): String {
        val writer = XdrWriter()
        scVal.encode(writer)
        return writer.toByteArray().toHexString()
    }

    /**
     * Asserts that the keys of the given map entries are in strictly ascending host
     * ScMap key order, as defined by [compareScValHostOrder].
     */
    private fun assertKeysAreInHostOrder(entries: List<SCMapEntryXdr>) {
        for (i in 0 until entries.size - 1) {
            assertTrue(
                compareScValHostOrder(entries[i].key, entries[i + 1].key) < 0,
                "Key at index $i must precede key at index ${i + 1} in host order"
            )
        }
    }

    /**
     * Converts a ByteArray to a lowercase hex string.
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}
