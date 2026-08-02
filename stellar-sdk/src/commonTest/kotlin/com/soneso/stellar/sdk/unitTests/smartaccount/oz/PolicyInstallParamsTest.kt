//
//  PolicyInstallParamsTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.extractSymbolName
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.compareScValHostOrder
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.oz.OZPolicyManager
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [PolicyInstallParams] validation logic, ScVal structure correctness,
 * and edge cases not covered by [PolicyManagerTest] or [ScMapKeySortingTest].
 *
 * Tests focus on:
 * - Validation error messages and exception types
 * - Boundary values (threshold=1, UInt.MAX_VALUE, I128 overflow region)
 * - WeightedThreshold zero threshold validation (gap in existing tests)
 * - SpendingLimit with values exceeding Long.MAX_VALUE (hi bits in I128)
 * - sortMapByKeyXdr stability and correctness with duplicate-length symbol keys
 * - Data class equality and copy behavior for PolicyInstallParams variants
 */
class PolicyInstallParamsTest {

    // ========================================================================
    // SimpleThreshold — validation error messages
    // ========================================================================

    @Test
    fun testSimpleThreshold_zeroThreshold_exceptionMessage() {
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            PolicyInstallParams.SimpleThreshold(threshold = 0u).toScVal()
        }
        assertTrue(
            exception.message!!.contains("Threshold must be greater than zero"),
            "Error message must state that threshold must be greater than zero, got: ${exception.message}"
        )
    }

    @Test
    fun testSimpleThreshold_zeroThreshold_exceptionFieldName() {
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            PolicyInstallParams.SimpleThreshold(threshold = 0u).toScVal()
        }
        assertTrue(
            exception.message!!.contains("threshold"),
            "Error message must reference the 'threshold' field"
        )
    }

    // ========================================================================
    // SimpleThreshold — data class properties
    // ========================================================================

    @Test
    fun testSimpleThreshold_dataClassEquality() {
        val a = PolicyInstallParams.SimpleThreshold(threshold = 5u)
        val b = PolicyInstallParams.SimpleThreshold(threshold = 5u)
        assertEquals(a, b, "Same threshold value must produce equal data class instances")
    }

    @Test
    fun testSimpleThreshold_dataClassInequality() {
        val a = PolicyInstallParams.SimpleThreshold(threshold = 5u)
        val b = PolicyInstallParams.SimpleThreshold(threshold = 6u)
        assertNotEquals(a, b, "Different threshold values must produce unequal instances")
    }

    @Test
    fun testSimpleThreshold_copy() {
        val original = PolicyInstallParams.SimpleThreshold(threshold = 3u)
        val modified = original.copy(threshold = 7u)
        assertEquals(7u, modified.threshold)

        // Original must not be changed
        assertEquals(3u, original.threshold)
    }

    // ========================================================================
    // WeightedThreshold — zero threshold validation (gap in existing tests)
    // ========================================================================

    @Test
    fun testWeightedThreshold_zeroThreshold_throws() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 50u),
            threshold = 0u
        )
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("Threshold must be greater than zero"),
            "Zero threshold in WeightedThreshold must be rejected with clear message"
        )
    }

    @Test
    fun testWeightedThreshold_zeroThreshold_checkedBeforeEmptySigners() {
        // When both threshold=0 and signerWeights is empty, threshold check runs first
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = emptyMap(),
            threshold = 0u
        )
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("Threshold must be greater than zero"),
            "Threshold validation must execute before signer weights validation"
        )
    }

    // ========================================================================
    // WeightedThreshold — threshold boundary values
    // ========================================================================

    @Test
    fun testWeightedThreshold_thresholdOfOne() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 1u),
            threshold = 1u
        )
        val scVal = params.toScVal()
        val entries = extractMapEntries(scVal)
        val thresholdEntry = entries.first { extractSymbolName(it.key) == "threshold" }
        assertEquals(1u, (thresholdEntry.`val` as SCValXdr.U32).value.value)
    }

    @Test
    fun testWeightedThreshold_maxUIntThreshold() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to UInt.MAX_VALUE),
            threshold = UInt.MAX_VALUE
        )
        val scVal = params.toScVal()
        val entries = extractMapEntries(scVal)
        val thresholdEntry = entries.first { extractSymbolName(it.key) == "threshold" }
        assertEquals(UInt.MAX_VALUE, (thresholdEntry.`val` as SCValXdr.U32).value.value)
    }

    // ========================================================================
    // WeightedThreshold — signer weights structure with many signers
    // ========================================================================

    @Test
    fun testWeightedThreshold_fiveSigners_allSortedInHostOrder() {
        // Mix of DelegatedSigners and ExternalSigners to get 5 signers
        val signers: List<SmartAccountSigner> = listOf(
            DelegatedSigner("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"),
            DelegatedSigner("GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"),
            DelegatedSigner("GB33CUURS5XLLECMLSE2EMMDJBMZSVF27BW6PLS53OFTJMP46CZH3CVG"),
            ExternalSigner(
                verifierAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
                keyData = byteArrayOf(0x01, 0x02, 0x03)
            ),
            ExternalSigner(
                verifierAddress = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA",
                keyData = byteArrayOf(0x04, 0x05, 0x06)
            ),
        )

        // Deliberate reverse order insertion
        val weights: Map<SmartAccountSigner, UInt> = mapOf(
            signers[4] to 10u,
            signers[2] to 20u,
            signers[0] to 30u,
            signers[3] to 25u,
            signers[1] to 15u,
        )

        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = weights,
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val signerWeightsEntry = entries.first { extractSymbolName(it.key) == "signer_weights" }
        val innerEntries = extractMapEntries(signerWeightsEntry.`val`)

        assertEquals(5, innerEntries.size, "All 5 signers must be present")

        // Verify strict host ScMap key ordering
        for (i in 0 until innerEntries.size - 1) {
            assertTrue(
                compareScValHostOrder(innerEntries[i].key, innerEntries[i + 1].key) < 0,
                "Signer key at index $i must precede key at index ${i + 1} in host order"
            )
        }

        // Verify all weight values are preserved
        val allWeights = innerEntries.map { (it.`val` as SCValXdr.U32).value.value }.toSet()
        assertEquals(setOf(10u, 15u, 20u, 25u, 30u), allWeights)
    }

    @Test
    fun testWeightedThreshold_singleSigner_weightEqualsThreshold() {
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 100u),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val innerEntries = extractMapEntries(entries[0].`val`)
        assertEquals(1, innerEntries.size)
        assertEquals(100u, (innerEntries[0].`val` as SCValXdr.U32).value.value)
    }

    // ========================================================================
    // WeightedThreshold — error message content
    // ========================================================================

    @Test
    fun testWeightedThreshold_emptySigners_exceptionMessage() {
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = emptyMap(),
            threshold = 10u
        )
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
        assertTrue(
            exception.message!!.contains("at least one signer with weight"),
            "Error message must mention requirement for at least one signer"
        )
    }

    // ========================================================================
    // SpendingLimit — large BigInteger exceeding Long.MAX_VALUE
    // ========================================================================

    @Test
    fun testSpendingLimit_valueExceedingLongMaxValue() {
        // Long.MAX_VALUE = 9,223,372,036,854,775,807
        // 2^63 fits in ULong (lo part), hi should still be 0
        val beyondLong = BigInteger.fromLong(Long.MAX_VALUE) + BigInteger.fromLong(1)
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = beyondLong,
            periodLedgers = 17280u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }
        val limitValue = limitEntry.`val`
        assertIs<SCValXdr.I128>(limitValue)

        val parts = (limitValue as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value, "2^63 fits in ULong, hi must be 0")
        assertEquals(Long.MAX_VALUE.toULong() + 1uL, parts.lo.value)
    }

    @Test
    fun testSpendingLimit_veryLargeValueRequiringHiBits() {
        // ULong.MAX_VALUE + 1 = 2^64, which requires hi=1, lo=0
        val beyondULong = BigInteger.parseString("18446744073709551616") // 2^64
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = beyondULong,
            periodLedgers = 720u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }
        val parts = (limitEntry.`val` as SCValXdr.I128).value

        assertEquals(1L, parts.hi.value, "2^64 must set hi=1")
        assertEquals(0uL, parts.lo.value, "2^64 must set lo=0")
    }

    @Test
    fun testSpendingLimit_i128MaxPositiveValue() {
        // I128 max positive = 2^127 - 1
        val i128Max = BigInteger.parseString("170141183460469231731687303715884105727")
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = i128Max,
            periodLedgers = 1u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }
        assertIs<SCValXdr.I128>(limitEntry.`val`)

        val parts = (limitEntry.`val` as SCValXdr.I128).value
        assertEquals(Long.MAX_VALUE, parts.hi.value, "I128 max hi must be Long.MAX_VALUE")
        assertEquals(ULong.MAX_VALUE, parts.lo.value, "I128 max lo must be ULong.MAX_VALUE")
    }

    // ========================================================================
    // SpendingLimit — validation error message content
    // ========================================================================

    @Test
    fun testSpendingLimit_zeroLimit_exceptionMessageContent() {
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            PolicyInstallParams.SpendingLimit(
                spendingLimit = BigInteger.ZERO,
                periodLedgers = 17280u
            ).toScVal()
        }
        assertTrue(
            exception.message!!.contains("Spending limit must be greater than zero"),
            "Error message must state spending limit requirement"
        )
    }

    @Test
    fun testSpendingLimit_zeroPeriod_exceptionMessageContent() {
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            PolicyInstallParams.SpendingLimit(
                spendingLimit = BigInteger.fromLong(10_000_000L),
                periodLedgers = 0u
            ).toScVal()
        }
        assertTrue(
            exception.message!!.contains("Period ledgers must be greater than zero"),
            "Error message must state period ledgers requirement"
        )
    }

    @Test
    fun testSpendingLimit_negativeLimit_exceptionMessageIncludesValue() {
        val negativeValue = BigInteger.fromLong(-500L)
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            PolicyInstallParams.SpendingLimit(
                spendingLimit = negativeValue,
                periodLedgers = 17280u
            ).toScVal()
        }
        assertTrue(
            exception.message!!.contains("-500"),
            "Error message must include the invalid value"
        )
    }

    // ========================================================================
    // SpendingLimit — boundary period values
    // ========================================================================

    @Test
    fun testSpendingLimit_singleLedgerPeriod_minimumValues() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.ONE,
            periodLedgers = 1u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        val periodEntry = entries.first { extractSymbolName(it.key) == "period_ledgers" }
        val limitEntry = entries.first { extractSymbolName(it.key) == "spending_limit" }

        assertEquals(1u, (periodEntry.`val` as SCValXdr.U32).value.value)

        val parts = (limitEntry.`val` as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(1uL, parts.lo.value)
    }

    // ========================================================================
    // SpendingLimit — data class properties
    // ========================================================================

    @Test
    fun testSpendingLimit_dataClassEquality() {
        val a = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1_000_000L),
            periodLedgers = 720u
        )
        val b = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1_000_000L),
            periodLedgers = 720u
        )
        assertEquals(a, b)
    }

    @Test
    fun testSpendingLimit_dataClassInequality_differentLimit() {
        val a = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1_000_000L),
            periodLedgers = 720u
        )
        val b = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(2_000_000L),
            periodLedgers = 720u
        )
        assertNotEquals(a, b)
    }

    @Test
    fun testSpendingLimit_dataClassInequality_differentPeriod() {
        val a = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1_000_000L),
            periodLedgers = 720u
        )
        val b = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1_000_000L),
            periodLedgers = 1440u
        )
        assertNotEquals(a, b)
    }

    // ========================================================================
    // sortMapByKeyXdr — additional edge cases
    // ========================================================================

    @Test
    fun testSortMapByKeyXdr_reversedSymbolKeysOfSameLength() {
        // Keys of same length: host order is by string content (after common prefix)
        val map = linkedMapOf(
            Scv.toSymbol("zzz") to Scv.toUint32(1u),
            Scv.toSymbol("aaa") to Scv.toUint32(2u),
            Scv.toSymbol("mmm") to Scv.toUint32(3u)
        )
        val sorted = OZPolicyManager.sortMapByKeyXdr(map)
        val keys = sorted.keys.toList()

        assertEquals("aaa", extractSymbolName(keys[0]))
        assertEquals("mmm", extractSymbolName(keys[1]))
        assertEquals("zzz", extractSymbolName(keys[2]))
    }

    @Test
    fun testSortMapByKeyXdr_symbolKeysOfDifferentLengths() {
        // The host orders symbols by content, byte for byte, with length only a tiebreaker
        // on a common prefix. So "a" (a prefix of "aaa") sorts before "aaa", and both sort
        // before "bb" (first byte 0x62 > 0x61) — length does not come first.
        val map = linkedMapOf(
            Scv.toSymbol("bb") to Scv.toUint32(1u),     // 0x62 0x62
            Scv.toSymbol("a") to Scv.toUint32(2u),      // 0x61
            Scv.toSymbol("aaa") to Scv.toUint32(3u)     // 0x61 0x61 0x61
        )
        val sorted = OZPolicyManager.sortMapByKeyXdr(map)
        val keys = sorted.keys.toList()

        // Host order: "a" < "aaa" (prefix, shorter first) < "bb" (0x62 > 0x61)
        assertEquals("a", extractSymbolName(keys[0]))
        assertEquals("aaa", extractSymbolName(keys[1]))
        assertEquals("bb", extractSymbolName(keys[2]))
    }

    @Test
    fun testSortMapByKeyXdr_u32Keys() {
        // U32 keys: discriminant is always SCV_U32, so sorting is by the 4-byte value
        val map = linkedMapOf(
            Scv.toUint32(300u) to Scv.toVoid(),
            Scv.toUint32(100u) to Scv.toVoid(),
            Scv.toUint32(200u) to Scv.toVoid()
        )
        val sorted = OZPolicyManager.sortMapByKeyXdr(map)
        val keys = sorted.keys.toList()

        assertEquals(100u, (keys[0] as SCValXdr.U32).value.value)
        assertEquals(200u, (keys[1] as SCValXdr.U32).value.value)
        assertEquals(300u, (keys[2] as SCValXdr.U32).value.value)
    }

    @Test
    fun testSortMapByKeyXdr_doesNotModifyOriginal() {
        val original = linkedMapOf(
            Scv.toSymbol("z") to Scv.toUint32(1u),
            Scv.toSymbol("a") to Scv.toUint32(2u)
        )
        val originalKeys = original.keys.toList()

        OZPolicyManager.sortMapByKeyXdr(original)

        // Verify original map is not mutated
        val afterKeys = original.keys.toList()
        assertEquals(originalKeys.size, afterKeys.size)
        assertEquals(
            extractSymbolName(originalKeys[0]),
            extractSymbolName(afterKeys[0]),
            "Original map must not be modified by sortMapByKeyXdr"
        )
    }

    // ========================================================================
    // scValToXdrBytes — deterministic encoding
    // ========================================================================

    @Test
    fun testScValToXdrBytes_sameInputProducesSameBytes() {
        val scVal = Scv.toSymbol("test")
        val bytes1 = OZPolicyManager.scValToXdrBytes(scVal)
        val bytes2 = OZPolicyManager.scValToXdrBytes(scVal)
        assertTrue(bytes1.contentEquals(bytes2), "Same ScVal must produce identical XDR bytes")
    }

    @Test
    fun testScValToXdrBytes_differentInputProducesDifferentBytes() {
        val a = OZPolicyManager.scValToXdrBytes(Scv.toSymbol("alpha"))
        val b = OZPolicyManager.scValToXdrBytes(Scv.toSymbol("beta"))
        assertTrue(!a.contentEquals(b), "Different ScVal inputs must produce different XDR bytes")
    }

    @Test
    fun testScValToXdrBytes_nonEmptyOutput() {
        val bytes = OZPolicyManager.scValToXdrBytes(Scv.toVoid())
        assertTrue(bytes.isNotEmpty(), "XDR encoding of Void must produce non-empty bytes")
    }

    // ========================================================================
    // Cross-type — sealed class polymorphism
    // ========================================================================

    @Test
    fun testPolicyInstallParams_isSealed() {
        val simple: PolicyInstallParams = PolicyInstallParams.SimpleThreshold(threshold = 1u)
        val weighted: PolicyInstallParams = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                DelegatedSigner("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7") to 1u
            ),
            threshold = 1u
        )
        val spending: PolicyInstallParams = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.ONE,
            periodLedgers = 1u
        )

        assertIs<PolicyInstallParams.SimpleThreshold>(simple)
        assertIs<PolicyInstallParams.WeightedThreshold>(weighted)
        assertIs<PolicyInstallParams.SpendingLimit>(spending)
    }

    @Test
    fun testPolicyInstallParams_whenExpression_exhaustive() {
        // Verify when expression covers all variants (compile-time check)
        val params: PolicyInstallParams = PolicyInstallParams.SimpleThreshold(threshold = 2u)
        val typeName = when (params) {
            is PolicyInstallParams.SimpleThreshold -> "simple"
            is PolicyInstallParams.WeightedThreshold -> "weighted"
            is PolicyInstallParams.SpendingLimit -> "spending"
        }
        assertEquals("simple", typeName)
    }

    // ========================================================================
    // Polymorphic toScVal dispatch
    // ========================================================================

    @Test
    fun testToScVal_polymorphicDispatchThroughBaseType() {
        // toScVal is declared on the sealed base, so a caller holding a
        // PolicyInstallParams reference can encode without knowing the variant
        val params: List<PolicyInstallParams> = listOf(
            PolicyInstallParams.SimpleThreshold(threshold = 2u),
            PolicyInstallParams.SpendingLimit(
                spendingLimit = BigInteger.fromLong(10_000_000L),
                periodLedgers = 17280u
            )
        )

        for (p in params) {
            val scVal: SCValXdr = p.toScVal()
            assertIs<SCValXdr.Map>(scVal)
        }
    }

    // ========================================================================
    // Typed addPolicy overload
    // ========================================================================

    @Test
    fun testAddPolicyTypedOverload_invalidParams_throwBeforeConnectionCheck() = runTest {
        // The typed overload encodes the params first, so invalid parameters are
        // rejected even on a disconnected kit (offline)
        val kit = OZSmartAccountKit.create(buildOfflineConfig())

        assertFailsWith<ValidationException.InvalidInput> {
            kit.policyManager.addPolicy(
                contextRuleId = 0u,
                policyAddress = TEST_CONTRACT_ADDRESS,
                installParams = PolicyInstallParams.SimpleThreshold(threshold = 0u)
            )
        }
    }

    @Test
    fun testAddPolicyTypedOverload_validParams_delegatesToScValOverload() = runTest {
        // Valid params encode successfully; the call then fails at the
        // connected-state check inside the SCValXdr overload, proving delegation
        val kit = OZSmartAccountKit.create(buildOfflineConfig())

        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.addPolicy(
                contextRuleId = 0u,
                policyAddress = TEST_CONTRACT_ADDRESS,
                installParams = PolicyInstallParams.SimpleThreshold(threshold = 2u)
            )
        }
    }

    private val TEST_CONTRACT_ADDRESS = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"

    private fun buildOfflineConfig(): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = TEST_CONTRACT_ADDRESS
    )

    // ========================================================================
    // Helper Functions
    // ========================================================================

    private fun extractMapEntries(scVal: SCValXdr): List<SCMapEntryXdr> {
        assertIs<SCValXdr.Map>(scVal)
        return (scVal as SCValXdr.Map).value?.value ?: emptyList()
    }
}
