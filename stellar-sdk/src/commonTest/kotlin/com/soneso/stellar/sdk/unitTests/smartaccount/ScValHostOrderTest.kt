//
//  ScValHostOrderTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayload
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayloadCodec
import com.soneso.stellar.sdk.smartaccount.core.compareScValHostOrder
import com.soneso.stellar.sdk.smartaccount.oz.OZPolicyManager
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Host-order ScMap key comparator ([compareScValHostOrder]) and its effect on the
 * smart-account signer maps. The Soroban host orders keys by content (Rust slice `Ord`),
 * with length only a tiebreaker on a common prefix. Sorting by the length-major XDR-byte
 * encoding instead diverges for variable-length keys whose lengths differ, producing a map
 * the host rejects with `InvalidInput`; this suite pins the correct order.
 */
class ScValHostOrderTest {

    private val verifier = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"
    private val verifierOther = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    private fun bytes(vararg v: Int): SCValXdr = Scv.toBytes(ByteArray(v.size) { v[it].toByte() })

    /** Builds an external signer: keyData = pub(65, first byte varied) + credId(len, zeros). */
    private fun externalSigner(pubFirstByte: Int, credIdLen: Int, verifierAddress: String = verifier): ExternalSigner {
        val pub = ByteArray(65) { if (it == 0) pubFirstByte.toByte() else 0x01 }
        val credId = ByteArray(credIdLen) { 0x00 }
        return ExternalSigner(verifierAddress, pub + credId)
    }

    /** Builds an external signer's ScVal key. */
    private fun signer(pubFirstByte: Int, credIdLen: Int, verifierAddress: String = verifier): SCValXdr =
        externalSigner(pubFirstByte, credIdLen, verifierAddress).toScVal()

    /** Length-major comparison of raw XDR encodings, used to pin where the two orders diverge. */
    private fun compareRawBytes(a: ByteArray, b: ByteArray): Int {
        val shared = minOf(a.size, b.size)
        for (i in 0 until shared) {
            val cmp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size.compareTo(b.size)
    }

    // Bytes compare by content, not length: [0x01,0x02] < [0xFF].
    @Test
    fun testBytes_contentBeforeLength() {
        val a = bytes(0xFF)
        val b = bytes(0x01, 0x02)
        assertTrue(compareScValHostOrder(b, a) < 0, "b (0x01..) must sort before a (0xFF)")
        assertTrue(compareScValHostOrder(a, b) > 0)
    }

    // Prefix tiebreaker: the shorter value sorts first.
    @Test
    fun testBytes_prefixShorterFirst() {
        val a = bytes(0x01)
        val b = bytes(0x01, 0x00)
        assertTrue(compareScValHostOrder(a, b) < 0)
        assertTrue(compareScValHostOrder(b, a) > 0)
    }

    // Two same-verifier signers with different-length keyData: host order is by content
    // and is the opposite of the length-major XDR-byte order.
    @Test
    fun testSameVerifierSigners_hostOrderDivergesFromLengthMajor() {
        val signerA = signer(pubFirstByte = 0x02, credIdLen = 16) // keyData 81 bytes, pub greater
        val signerB = signer(pubFirstByte = 0x01, credIdLen = 20) // keyData 85 bytes, pub smaller

        // Host order: B before A (pubB 0x01 < pubA 0x02 in the first byte; length irrelevant).
        assertTrue(compareScValHostOrder(signerB, signerA) < 0)
        assertTrue(compareScValHostOrder(signerA, signerB) > 0)

        // Length-major XDR-byte order puts the shorter signerA first — the opposite of
        // the host. Asserting the disagreement pins the divergence at exactly the inputs
        // where the two orders differ.
        val xdrA = OZPolicyManager.scValToXdrBytes(signerA)
        val xdrB = OZPolicyManager.scValToXdrBytes(signerB)
        assertTrue(xdrA.size < xdrB.size)
        assertTrue(compareRawBytes(xdrA, xdrB) < 0, "length-major XDR-byte order puts signerA first")
        assertTrue(compareScValHostOrder(signerA, signerB) > 0, "host order puts signerA last")
    }

    // The weighted-threshold signer_weights map sorts the two signers in host order [B, A].
    @Test
    fun testSignerWeightsMap_hostOrder() {
        val signerA = signer(0x02, 16)
        val signerB = signer(0x01, 20)
        val map = linkedMapOf(
            signerA to Scv.toUint32(1u),
            signerB to Scv.toUint32(1u)
        )
        val sorted = OZPolicyManager.sortMapByKeyXdr(map).keys.toList()
        assertEquals(2, sorted.size)
        assertEquals(signerB, sorted[0])
        assertEquals(signerA, sorted[1])
    }

    // Same-length, different content: ordered by content, not spuriously reordered.
    @Test
    fun testSameLengthSigners_contentOrder() {
        val low = signer(0x01, 16)
        val high = signer(0x02, 16)
        val map = linkedMapOf(
            high to Scv.toUint32(1u),
            low to Scv.toUint32(1u)
        )
        val sorted = OZPolicyManager.sortMapByKeyXdr(map).keys.toList()
        assertEquals(low, sorted[0])
        assertEquals(high, sorted[1])
    }

    // 3+ signers with mixed lengths: the full sort is a strict total order in host order.
    @Test
    fun testManySigners_strictTotalOrder() {
        val s1 = signer(0x01, 16)
        val s2 = signer(0x02, 40)
        val s3 = signer(0x03, 8)
        val s4 = signer(0x02, 12)
        val map = linkedMapOf(
            s3 to Scv.toUint32(1u),
            s1 to Scv.toUint32(1u),
            s4 to Scv.toUint32(1u),
            s2 to Scv.toUint32(1u)
        )
        val sorted = OZPolicyManager.sortMapByKeyXdr(map).keys.toList()
        assertEquals(4, sorted.size)
        assertEquals(s1, sorted[0]) // first pub byte 0x01 is smallest
        assertEquals(s3, sorted[3]) // first pub byte 0x03 is largest
        for (i in 0 until sorted.size - 1) {
            assertTrue(
                compareScValHostOrder(sorted[i], sorted[i + 1]) < 0,
                "adjacent keys must be strictly increasing in host order"
            )
        }
    }

    // Two signers on different verifiers with identical keyData: order is decided by the
    // Address element of the Vec key, not the trailing Bytes.
    @Test
    fun testDifferentVerifiers_addressDecides() {
        val signerX = signer(0x01, 16, verifierAddress = verifier)
        val signerY = signer(0x01, 16, verifierAddress = verifierOther)
        val addrCmp = compareScValHostOrder(
            Scv.toAddress(Address(verifier).toSCAddress()),
            Scv.toAddress(Address(verifierOther).toSCAddress())
        )
        val signerCmp = compareScValHostOrder(signerX, signerY)
        assertTrue(addrCmp != 0, "the two verifier addresses must differ")
        assertTrue(
            (signerCmp < 0) == (addrCmp < 0),
            "signer order must follow the verifier Address element, not the identical keyData"
        )
    }

    // Map comparands compare entry-wise (first differing key/value decides), not by entry count.
    @Test
    fun testMapComparands_entryWiseNotEntryCount() {
        val oneEntry = Scv.toMap(linkedMapOf(Scv.toSymbol("b") to Scv.toUint32(1u)))
        val twoEntries = Scv.toMap(
            linkedMapOf(
                Scv.toSymbol("a") to Scv.toUint32(1u),
                Scv.toSymbol("c") to Scv.toUint32(2u)
            )
        )
        // Entry-wise: the two-entry map's first key "a" sorts before "b", so it comes first
        // despite having more entries (entry count is only the tiebreaker on a shared prefix).
        assertTrue(compareScValHostOrder(twoEntries, oneEntry) < 0)
        assertTrue(compareScValHostOrder(oneEntry, twoEntries) > 0)
    }

    // The auth-payload write path emits the signers map in host order for two same-verifier
    // signers with different-length key data.
    @Test
    fun testAuthPayloadWrite_signersMapInHostOrder() {
        val signerA = externalSigner(pubFirstByte = 0x02, credIdLen = 16) // shorter keyData, greater pub
        val signerB = externalSigner(pubFirstByte = 0x01, credIdLen = 20) // longer keyData, smaller pub
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(
                signerA to ByteArray(64) { 0x07 },
                signerB to ByteArray(64) { 0x08 }
            ),
            contextRuleIds = listOf(0u)
        )

        val written = SmartAccountAuthPayloadCodec.write(payload)
        val outerEntries = (written as SCValXdr.Map).value?.value ?: emptyList()
        val signersEntry = outerEntries.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signerKeys = ((signersEntry.`val` as SCValXdr.Map).value?.value ?: emptyList()).map { it.key }

        assertEquals(2, signerKeys.size)
        // SCVal equality is reference-based for ByteArray-carrying leaves, so compare the
        // XDR encodings instead of the instances.
        assertContentEquals(
            OZPolicyManager.scValToXdrBytes(signerB.toScVal()),
            OZPolicyManager.scValToXdrBytes(signerKeys[0]),
            "smaller pubkey content must sort first despite longer keyData"
        )
        assertContentEquals(
            OZPolicyManager.scValToXdrBytes(signerA.toScVal()),
            OZPolicyManager.scValToXdrBytes(signerKeys[1])
        )
    }
}
