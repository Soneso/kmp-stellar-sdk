//
//  ScMapKeySortingTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSharedUtils
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that ScMap keys are sorted lexicographically by XDR byte representation
 * when building policy parameters.
 *
 * sortMapByKeyXdr() sorts by the raw XDR-encoded bytes of each key. For symbol
 * keys, XDR encoding is: [4-byte discriminant][4-byte length][string bytes][padding].
 * This means symbols of different lengths sort by length first, not alphabetically.
 * For same-length symbols, the sort is alphabetical.
 *
 * Struct-like maps (e.g., WeightedThreshold outer keys) use alphabetical field
 * ordering as mandated by the Soroban Rust SDK's #[contracttype] derive macro.
 */
class ScMapKeySortingTest {

    // MARK: - Utility Function Tests

    @Test
    fun testCompareByteArraysLexicographically() {
        // Verify the utility function correctly compares byte arrays

        // Same bytes
        val a = byteArrayOf(0x01, 0x02, 0x03)
        val b = byteArrayOf(0x01, 0x02, 0x03)
        val sortedAB = listOf(a, b).sortedWith { x, y ->
            compareByteArraysForTest(x, y)
        }
        assertEquals(0, compareByteArraysForTest(a, b))

        // First < Second (differs at index 1)
        val c = byteArrayOf(0x01, 0x02, 0x03)
        val d = byteArrayOf(0x01, 0x03, 0x03)
        assertTrue(compareByteArraysForTest(c, d) < 0)
        assertTrue(compareByteArraysForTest(d, c) > 0)

        // Shorter array < longer array when prefix matches
        val e = byteArrayOf(0x01, 0x02)
        val f = byteArrayOf(0x01, 0x02, 0x03)
        assertTrue(compareByteArraysForTest(e, f) < 0)
        assertTrue(compareByteArraysForTest(f, e) > 0)

        // Unsigned byte comparison (0xFF > 0x01)
        val g = byteArrayOf(0x01)
        val h = byteArrayOf(0xFF.toByte())
        assertTrue(compareByteArraysForTest(g, h) < 0)
    }

    @Test
    fun testSortMapByKeyXdrWithSymbolKeys() {
        // Symbol keys are sorted by their XDR byte encoding. XDR encodes symbols as:
        //   [4-byte discriminant (0x0000000f)] [4-byte length] [string bytes] [padding]
        // So symbols sort by length first (shorter length prefix = smaller XDR bytes),
        // then alphabetically within the same length.
        //
        // "alpha"  (5 chars) -> XDR: 0000000f 00000005 616c7068 61000000
        // "zebra"  (5 chars) -> XDR: 0000000f 00000005 7a656272 61000000
        // "middle" (6 chars) -> XDR: 0000000f 00000006 6d696464 6c650000
        //
        // XDR sort order: alpha (5) < zebra (5) < middle (6)
        val unsorted = linkedMapOf(
            Scv.toSymbol("zebra") to Scv.toUint32(1u),
            Scv.toSymbol("alpha") to Scv.toUint32(2u),
            Scv.toSymbol("middle") to Scv.toUint32(3u)
        )

        val sorted = SmartAccountSharedUtils.sortMapByKeyXdr(unsorted)
        val keys = sorted.keys.toList()

        assertEquals(3, keys.size)
        assertEquals("alpha", extractSymbolName(keys[0]))
        assertEquals("zebra", extractSymbolName(keys[1]))
        assertEquals("middle", extractSymbolName(keys[2]))
    }

    @Test
    fun testSortMapByKeyXdrWithAddressKeys() {
        // Address keys sorted by their XDR byte representation
        // Contract addresses (C...) encode as SC_ADDRESS_TYPE_CONTRACT
        val addr1 = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        val addr2 = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"
        val addr3 = "CCK4LNH73QFN6KSRCP7ZBF4ISLXHZDMZGCMC3ETCMMUPNGQJZCPHVZQ3"

        val unsorted = linkedMapOf(
            Scv.toAddress(Address(addr1).toSCAddress()) to Scv.toVoid(),
            Scv.toAddress(Address(addr2).toSCAddress()) to Scv.toVoid(),
            Scv.toAddress(Address(addr3).toSCAddress()) to Scv.toVoid()
        )

        val sorted = SmartAccountSharedUtils.sortMapByKeyXdr(unsorted)
        val sortedKeys = sorted.keys.toList()

        // Verify entries are sorted by XDR bytes
        for (i in 0 until sortedKeys.size - 1) {
            val currentXdr = SmartAccountSharedUtils.scValToXdrBytes(sortedKeys[i])
            val nextXdr = SmartAccountSharedUtils.scValToXdrBytes(sortedKeys[i + 1])
            val hexCurrent = currentXdr.toHexString()
            val hexNext = nextXdr.toHexString()
            assertTrue(
                hexCurrent < hexNext,
                "Key at index $i (hex=$hexCurrent) must be < key at index ${i + 1} (hex=$hexNext)"
            )
        }
    }

    @Test
    fun testSortMapEntriesByKeyXdr() {
        // Entries are sorted by XDR bytes of the key. XDR encodes symbols with a
        // length prefix, so different-length symbols sort by length first.
        //
        // "z_last"   (6 chars) -> length prefix 00000006
        // "a_first"  (7 chars) -> length prefix 00000007
        // "m_middle" (8 chars) -> length prefix 00000008
        //
        // XDR sort order: z_last (6) < a_first (7) < m_middle (8)
        val entries = listOf(
            SCMapEntryXdr(key = Scv.toSymbol("z_last"), `val` = Scv.toUint32(1u)),
            SCMapEntryXdr(key = Scv.toSymbol("a_first"), `val` = Scv.toUint32(2u)),
            SCMapEntryXdr(key = Scv.toSymbol("m_middle"), `val` = Scv.toUint32(3u))
        )

        val sorted = SmartAccountSharedUtils.sortMapEntriesByKeyXdr(entries)

        assertEquals(3, sorted.size)
        assertEquals("z_last", extractSymbolName(sorted[0].key))
        assertEquals("a_first", extractSymbolName(sorted[1].key))
        assertEquals("m_middle", extractSymbolName(sorted[2].key))
    }

    // MARK: - SimpleThreshold Policy Tests

    @Test
    fun testSimpleThresholdMapHasSingleKey() {
        // SimpleThreshold has only one key ("threshold"), trivially sorted
        val params = PolicyInstallParams.SimpleThreshold(threshold = 2u)
        val scVal = params.toScVal()

        // Extract the map entries
        val entries = extractMapEntries(scVal)
        assertEquals(1, entries.size)
        assertEquals("threshold", extractSymbolName(entries[0].key))
    }

    @Test
    fun testSimpleThresholdXdrEncoding() {
        // Verify the XDR encoding is deterministic
        val params1 = PolicyInstallParams.SimpleThreshold(threshold = 3u)
        val params2 = PolicyInstallParams.SimpleThreshold(threshold = 3u)

        val xdr1 = encodeToXdrHex(params1.toScVal())
        val xdr2 = encodeToXdrHex(params2.toScVal())

        assertEquals(xdr1, xdr2, "Same SimpleThreshold params must produce identical XDR")
    }

    // MARK: - WeightedThreshold Policy Tests

    @Test
    fun testWeightedThresholdOuterKeysAreSorted() {
        // Outer map keys are struct field names: "signer_weights" and "threshold".
        // The Soroban Rust SDK's #[contracttype] macro encodes struct fields sorted
        // alphabetically by field name. Since 's' < 't', "signer_weights" comes first.
        //
        // Note: This is alphabetical string ordering (as used by the Soroban host's
        // ScVal Ord implementation for Symbol), not XDR byte ordering. For symbols
        // of different lengths, XDR byte ordering would differ from alphabetical
        // ordering because XDR includes a length prefix.
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer to 50u),
            threshold = 100u
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        assertEquals(2, entries.size)
        assertEquals("signer_weights", extractSymbolName(entries[0].key))
        assertEquals("threshold", extractSymbolName(entries[1].key))
    }

    @Test
    fun testWeightedThresholdSignerWeightsMapIsSortedByXdrHex() {
        // Create multiple signers that would be in wrong order without sorting.
        // DelegatedSigners use G-addresses; their XDR encoding includes the address bytes.
        // All three G-addresses are valid StrKey-encoded Ed25519 public keys.
        val signer1 = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val signer2 = DelegatedSigner(
            address = "GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"
        )
        val signer3 = DelegatedSigner(
            address = "GB33CUURS5XLLECMLSE2EMMDJBMZSVF27BW6PLS53OFTJMP46CZH3CVG"
        )

        // Pass signers in reverse order to ensure sorting is applied
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                signer3 to 20u,
                signer1 to 50u,
                signer2 to 30u
            ),
            threshold = 100u
        )
        val scVal = params.toScVal()

        // Extract the signer_weights inner map
        val outerEntries = extractMapEntries(scVal)
        val signerWeightsEntry = outerEntries.first { extractSymbolName(it.key) == "signer_weights" }
        val innerEntries = extractMapEntries(signerWeightsEntry.`val`)

        assertEquals(3, innerEntries.size)

        // Verify inner map keys are sorted by XDR hex
        assertKeysAreSortedByXdrHex(innerEntries)
    }

    @Test
    fun testWeightedThresholdWithExternalSignersIsSorted() {
        // ExternalSigner keys have different structure (Vec with verifier address + key data)
        // These should also be sorted by XDR hex
        val signer1 = ExternalSigner(
            verifierAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            keyData = byteArrayOf(0x01, 0x02, 0x03)
        )
        val signer2 = ExternalSigner(
            verifierAddress = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA",
            keyData = byteArrayOf(0x04, 0x05, 0x06)
        )

        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                signer1 to 60u,
                signer2 to 40u
            ),
            threshold = 100u
        )
        val scVal = params.toScVal()

        // Extract the signer_weights inner map
        val outerEntries = extractMapEntries(scVal)
        val signerWeightsEntry = outerEntries.first { extractSymbolName(it.key) == "signer_weights" }
        val innerEntries = extractMapEntries(signerWeightsEntry.`val`)

        assertEquals(2, innerEntries.size)

        // Verify inner map keys are sorted by XDR hex
        assertKeysAreSortedByXdrHex(innerEntries)
    }

    @Test
    fun testWeightedThresholdXdrIsDeterministic() {
        // Same inputs in different order must produce identical XDR output.
        // Uses valid G-addresses.
        val signer1 = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val signer2 = DelegatedSigner(
            address = "GBGWONUYEPTSADFMLRQSPRAPTWMGX5PMQXXHGSBVRF2KLUNVZT57SLVW"
        )

        val paramsOrderA = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer1 to 50u, signer2 to 30u),
            threshold = 80u
        )
        val paramsOrderB = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer2 to 30u, signer1 to 50u),
            threshold = 80u
        )

        val xdrA = encodeToXdrHex(paramsOrderA.toScVal())
        val xdrB = encodeToXdrHex(paramsOrderB.toScVal())

        assertEquals(xdrA, xdrB,
            "WeightedThreshold with same signers in different order must produce identical XDR"
        )
    }

    // MARK: - SpendingLimit Policy Tests

    @Test
    fun testSpendingLimitKeysAreSorted() {
        // SpendingLimit has keys "period_ledgers" and "spending_limit"
        // Both are 14 chars, so XDR byte ordering and alphabetical ordering are equivalent
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(10_000_000L), // 1 XLM in stroops
            periodLedgers = 17280u        // ~1 day
        )
        val scVal = params.toScVal()

        val entries = extractMapEntries(scVal)
        assertEquals(2, entries.size)
        assertEquals("period_ledgers", extractSymbolName(entries[0].key))
        assertEquals("spending_limit", extractSymbolName(entries[1].key))

        // Verify XDR hex ordering (same as alphabetical for same-length keys)
        assertKeysAreSortedByXdrHex(entries)
    }

    @Test
    fun testSpendingLimitXdrEncoding() {
        // Verify deterministic encoding
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

        assertEquals(xdr1, xdr2, "Same SpendingLimit params must produce identical XDR")
    }

    // MARK: - Policies Map Sorting (Address Keys)

    @Test
    fun testPoliciesMapAddressKeysAreSortedByXdrHex() {
        // Simulate the policies map construction from OZContextRuleManager.addContextRule()
        // with multiple policy addresses in unsorted order.
        // All C-addresses are valid StrKey-encoded contract IDs.
        val policyAddr1 = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        val policyAddr2 = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"
        val policyAddr3 = "CCK4LNH73QFN6KSRCP7ZBF4ISLXHZDMZGCMC3ETCMMUPNGQJZCPHVZQ3"

        // Build the map the same way OZContextRuleManager does
        val policiesMap = LinkedHashMap<SCValXdr, SCValXdr>()
        for (address in listOf(policyAddr1, policyAddr2, policyAddr3)) {
            val policyAddress = Address(address).toSCAddress()
            policiesMap[Scv.toAddress(policyAddress)] = Scv.toVoid()
        }

        // Sort using the same utility
        val sortedMap = SmartAccountSharedUtils.sortMapByKeyXdr(policiesMap)

        // Verify all entries are present
        assertEquals(3, sortedMap.size)

        // Verify keys are sorted by XDR hex
        val sortedKeys = sortedMap.keys.toList()
        for (i in 0 until sortedKeys.size - 1) {
            val currentXdr = SmartAccountSharedUtils.scValToXdrBytes(sortedKeys[i])
            val nextXdr = SmartAccountSharedUtils.scValToXdrBytes(sortedKeys[i + 1])
            val hexCurrent = currentXdr.toHexString()
            val hexNext = nextXdr.toHexString()
            assertTrue(
                hexCurrent < hexNext,
                "Policy address key at index $i (hex=$hexCurrent) must be < " +
                        "key at index ${i + 1} (hex=$hexNext)"
            )
        }
    }

    @Test
    fun testPoliciesMapSortingIsDeterministic() {
        // Same policy addresses in different insertion order must produce same XDR
        val addr1 = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        val addr2 = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"

        val mapOrderA = LinkedHashMap<SCValXdr, SCValXdr>()
        mapOrderA[Scv.toAddress(Address(addr1).toSCAddress())] = Scv.toVoid()
        mapOrderA[Scv.toAddress(Address(addr2).toSCAddress())] = Scv.toVoid()

        val mapOrderB = LinkedHashMap<SCValXdr, SCValXdr>()
        mapOrderB[Scv.toAddress(Address(addr2).toSCAddress())] = Scv.toVoid()
        mapOrderB[Scv.toAddress(Address(addr1).toSCAddress())] = Scv.toVoid()

        val sortedA = SmartAccountSharedUtils.sortMapByKeyXdr(mapOrderA)
        val sortedB = SmartAccountSharedUtils.sortMapByKeyXdr(mapOrderB)

        val xdrA = encodeToXdrHex(Scv.toMap(sortedA))
        val xdrB = encodeToXdrHex(Scv.toMap(sortedB))

        assertEquals(xdrA, xdrB,
            "Same policy addresses in different order must produce identical XDR after sorting"
        )
    }

    // MARK: - Mixed Key Type Sorting

    @Test
    fun testSortingWithDifferentScValKeyTypes() {
        // Ensure sorting works across different SCVal discriminant types
        // SCVal discriminant is encoded as a 4-byte int, so different types will
        // naturally sort by their discriminant value
        val unsorted = linkedMapOf(
            Scv.toSymbol("symbol_key") to Scv.toUint32(1u),    // discriminant = SCV_SYMBOL
            Scv.toUint32(42u) to Scv.toUint32(2u),             // discriminant = SCV_U32
            Scv.toBytes(byteArrayOf(0x01)) to Scv.toUint32(3u) // discriminant = SCV_BYTES
        )

        val sorted = SmartAccountSharedUtils.sortMapByKeyXdr(unsorted)
        val sortedKeys = sorted.keys.toList()

        assertEquals(3, sortedKeys.size)

        // Verify all keys are sorted by their XDR hex representation
        for (i in 0 until sortedKeys.size - 1) {
            val currentHex = encodeToXdrHex(sortedKeys[i])
            val nextHex = encodeToXdrHex(sortedKeys[i + 1])
            assertTrue(
                currentHex < nextHex,
                "Key at index $i (hex=$currentHex) must be < key at index ${i + 1} (hex=$nextHex)"
            )
        }
    }

    // MARK: - Edge Cases

    @Test
    fun testSortEmptyMap() {
        val empty = linkedMapOf<SCValXdr, SCValXdr>()
        val sorted = SmartAccountSharedUtils.sortMapByKeyXdr(empty)
        assertEquals(0, sorted.size)
    }

    @Test
    fun testSortSingleEntryMap() {
        val single = linkedMapOf(
            Scv.toSymbol("only") to Scv.toUint32(1u)
        )
        val sorted = SmartAccountSharedUtils.sortMapByKeyXdr(single)
        assertEquals(1, sorted.size)
        assertEquals("only", extractSymbolName(sorted.keys.first()))
    }

    @Test
    fun testSortAlreadySortedMap() {
        // Map that is already in correct XDR order should remain the same.
        // All keys have same length (3 chars), so XDR order = alphabetical.
        val alreadySorted = linkedMapOf(
            Scv.toSymbol("aaa") to Scv.toUint32(1u),
            Scv.toSymbol("bbb") to Scv.toUint32(2u),
            Scv.toSymbol("ccc") to Scv.toUint32(3u)
        )
        val sorted = SmartAccountSharedUtils.sortMapByKeyXdr(alreadySorted)
        val keys = sorted.keys.toList()

        assertEquals("aaa", extractSymbolName(keys[0]))
        assertEquals("bbb", extractSymbolName(keys[1]))
        assertEquals("ccc", extractSymbolName(keys[2]))
    }

    @Test
    fun testSortPreservesValues() {
        // Verify that sorting does not lose or swap values.
        // All keys have same length (1 char), so XDR order = alphabetical.
        val map = linkedMapOf(
            Scv.toSymbol("z") to Scv.toUint32(100u),
            Scv.toSymbol("a") to Scv.toUint32(200u),
            Scv.toSymbol("m") to Scv.toUint32(300u)
        )
        val sorted = SmartAccountSharedUtils.sortMapByKeyXdr(map)
        val entries = sorted.entries.toList()

        assertEquals("a", extractSymbolName(entries[0].key))
        assertEquals(200u, extractUint32Value(entries[0].value))

        assertEquals("m", extractSymbolName(entries[1].key))
        assertEquals(300u, extractUint32Value(entries[1].value))

        assertEquals("z", extractSymbolName(entries[2].key))
        assertEquals(100u, extractUint32Value(entries[2].value))
    }

    // MARK: - Helper Functions

    /**
     * Extracts map entries from an SCValXdr.Map.
     */
    private fun extractMapEntries(scVal: SCValXdr): List<SCMapEntryXdr> {
        assertTrue(scVal is SCValXdr.Map, "Expected SCValXdr.Map, got ${scVal.discriminant}")
        return (scVal as SCValXdr.Map).value?.value ?: emptyList()
    }

    /**
     * Extracts the symbol name from a Sym SCValXdr.
     */
    private fun extractSymbolName(scVal: SCValXdr): String {
        assertTrue(scVal is SCValXdr.Sym, "Expected SCValXdr.Sym, got ${scVal.discriminant}")
        return (scVal as SCValXdr.Sym).value.value
    }

    /**
     * Extracts the u32 value from an SCValXdr.U32.
     */
    private fun extractUint32Value(scVal: SCValXdr): UInt {
        assertTrue(scVal is SCValXdr.U32, "Expected SCValXdr.U32, got ${scVal.discriminant}")
        return (scVal as SCValXdr.U32).value.value
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
     * Asserts that the keys of the given map entries are sorted
     * lexicographically by their XDR hex representation.
     */
    private fun assertKeysAreSortedByXdrHex(entries: List<SCMapEntryXdr>) {
        for (i in 0 until entries.size - 1) {
            val currentXdr = SmartAccountSharedUtils.scValToXdrBytes(entries[i].key)
            val nextXdr = SmartAccountSharedUtils.scValToXdrBytes(entries[i + 1].key)
            val hexCurrent = currentXdr.toHexString()
            val hexNext = nextXdr.toHexString()
            assertTrue(
                hexCurrent < hexNext,
                "Key at index $i (hex=$hexCurrent) must be < key at index ${i + 1} (hex=$hexNext)"
            )
        }
    }

    /**
     * Compares two byte arrays lexicographically with unsigned byte comparison.
     * Mirrors the logic in SmartAccountSharedUtils.compareByteArraysLexicographically.
     */
    private fun compareByteArraysForTest(a: ByteArray, b: ByteArray): Int {
        val minLength = minOf(a.size, b.size)
        for (i in 0 until minLength) {
            val aByte = a[i].toInt() and 0xFF
            val bByte = b[i].toInt() and 0xFF
            if (aByte != bByte) {
                return aByte - bByte
            }
        }
        return a.size - b.size
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
