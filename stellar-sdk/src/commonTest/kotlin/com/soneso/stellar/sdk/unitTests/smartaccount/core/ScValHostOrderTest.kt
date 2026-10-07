//
//  ScValHostOrderTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.core

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.scval.compareScValHostOrder
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayload
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayloadCodec
import com.soneso.stellar.sdk.smartaccount.oz.OZPolicyManager
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Host-order ScMap key comparator ([compareScValHostOrder]), the [Scv.toMap] builder, and the
 * smart-account signer maps built with them. The Soroban host orders keys by content (Rust
 * slice `Ord`), with length only a tiebreaker on a common prefix, and rejects a map argument
 * whose keys are out of order with `InvalidInput`. [hostOrderVector] lists keys of every
 * comparable type in ascending host order.
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

    // String comparands compare by content, byte for byte, with the shorter value first on
    // a prefix tie.
    @Test
    fun testStringComparands_contentOrder() {
        val a = Scv.toString("apple")
        val b = Scv.toString("banana")
        assertTrue(compareScValHostOrder(a, b) < 0)
        assertTrue(compareScValHostOrder(b, a) > 0)

        val prefix = Scv.toString("app")
        assertTrue(compareScValHostOrder(prefix, a) < 0, "a prefix sorts before its extension")
        assertEquals(0, compareScValHostOrder(a, Scv.toString("apple")))
    }

    // ExecutableTag comparands carry raw tag bytes and compare by content, byte for byte,
    // with the shorter value first on a prefix tie.
    @Test
    fun testExecutableTagComparands_contentOrder() {
        val a = Scv.toExecutableTag("aa")
        val b = Scv.toExecutableTag("b")
        assertTrue(compareScValHostOrder(a, b) < 0, "content order, not length-major order")
        assertTrue(compareScValHostOrder(b, a) > 0)

        val prefix = Scv.toExecutableTag("a")
        assertTrue(compareScValHostOrder(prefix, a) < 0, "a prefix sorts before its extension")
        assertEquals(0, compareScValHostOrder(a, Scv.toExecutableTag("aa")))
    }

    // ExecutableTag comparands with high bytes: the comparison is unsigned and reads the
    // raw bytes, which no Kotlin String can carry.
    @Test
    fun testExecutableTagComparands_unsignedRawBytes() {
        val low = Scv.toExecutableTagBytes(byteArrayOf(0xC0.toByte()))
        val high = Scv.toExecutableTagBytes(byteArrayOf(0xFF.toByte()))
        assertTrue(compareScValHostOrder(low, high) < 0, "0xC0 sorts below 0xFF unsigned")
        assertTrue(compareScValHostOrder(high, low) > 0)

        val ascii = Scv.toExecutableTagBytes(byteArrayOf(0x7F))
        assertTrue(compareScValHostOrder(ascii, low) < 0, "a high byte is not negative")
        assertEquals(0, compareScValHostOrder(low, Scv.toExecutableTagBytes(byteArrayOf(0xC0.toByte()))))
    }

    // Vec comparands: on a shared prefix, the shorter vec sorts first.
    @Test
    fun testVecComparands_prefixShorterFirst() {
        val short = Scv.toVec(listOf(Scv.toSymbol("a")))
        val long = Scv.toVec(listOf(Scv.toSymbol("a"), Scv.toSymbol("b")))
        assertTrue(compareScValHostOrder(short, long) < 0)
        assertTrue(compareScValHostOrder(long, short) > 0)
    }

    // Map comparands with identical keys: the first differing value decides.
    @Test
    fun testMapComparands_valueDecidesOnEqualKeys() {
        val lower = Scv.toMap(linkedMapOf(Scv.toSymbol("k") to Scv.toUint32(1u)))
        val higher = Scv.toMap(linkedMapOf(Scv.toSymbol("k") to Scv.toUint32(2u)))
        assertTrue(compareScValHostOrder(lower, higher) < 0)
        assertTrue(compareScValHostOrder(higher, lower) > 0)
    }

    // Map comparands on a shared entry prefix: the map with fewer entries sorts first.
    @Test
    fun testMapComparands_entryCountTiebreakerOnSharedPrefix() {
        val oneEntry = Scv.toMap(linkedMapOf(Scv.toSymbol("a") to Scv.toUint32(1u)))
        val twoEntries = Scv.toMap(
            linkedMapOf(
                Scv.toSymbol("a") to Scv.toUint32(1u),
                Scv.toSymbol("b") to Scv.toUint32(2u)
            )
        )
        assertTrue(compareScValHostOrder(oneEntry, twoEntries) < 0)
        assertTrue(compareScValHostOrder(twoEntries, oneEntry) > 0)
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

    // A Vec whose backing list is null behaves as an empty Vec: it sorts before a non-empty
    // Vec on either side of the comparison, and two null-backed Vecs are equal.
    @Test
    fun testVecComparands_nullBackingListTreatedAsEmpty() {
        val nullBacked = SCValXdr.Vec(null)
        val oneElement = Scv.toVec(listOf(Scv.toSymbol("a")))
        assertTrue(compareScValHostOrder(nullBacked, oneElement) < 0)
        assertTrue(compareScValHostOrder(oneElement, nullBacked) > 0)
        assertEquals(0, compareScValHostOrder(nullBacked, SCValXdr.Vec(null)))
    }

    // A Map whose backing list is null behaves as an empty Map: it sorts before a non-empty
    // Map on either side of the comparison, and two null-backed Maps are equal.
    @Test
    fun testMapComparands_nullBackingListTreatedAsEmpty() {
        val nullBacked = SCValXdr.Map(null)
        val oneEntry = Scv.toMap(linkedMapOf(Scv.toSymbol("a") to Scv.toUint32(1u)))
        assertTrue(compareScValHostOrder(nullBacked, oneEntry) < 0)
        assertTrue(compareScValHostOrder(oneEntry, nullBacked) > 0)
        assertEquals(0, compareScValHostOrder(nullBacked, SCValXdr.Map(null)))
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

    // ========== Shared host-order vector ==========

    // Every pair i < j of the vector compares as less, in both directions.
    @Test
    fun testHostOrderVector_everyPairOrdered() {
        val keys = hostOrderVector()
        assertEquals(63, keys.size)
        for (i in keys.indices) {
            assertEquals(0, compareScValHostOrder(keys[i], keys[i]), "key ${i + 1} must equal itself")
            for (j in i + 1 until keys.size) {
                assertTrue(compareScValHostOrder(keys[i], keys[j]) < 0, "key ${i + 1} must sort before key ${j + 1}")
                assertTrue(compareScValHostOrder(keys[j], keys[i]) > 0, "key ${j + 1} must sort after key ${i + 1}")
            }
        }
    }

    // Scv.toMap emits the vector's order from a fixed shuffle and rejects a duplicate key.
    @Test
    fun testToMap_sortsIntoHostOrderAndRejectsDuplicateKey() {
        val keys = hostOrderVector()
        val shuffled = LinkedHashMap<SCValXdr, SCValXdr>()
        keys.hostOrderShuffle().forEach { shuffled[it] = Scv.toVoid() }
        val emitted = (Scv.toMap(shuffled) as SCValXdr.Map).value!!.value.map { it.key.toXdrBase64() }
        assertEquals(keys.map { it.toXdrBase64() }, emitted)

        // Built separately, the two keys are distinct Kotlin objects but equal in host order.
        val duplicate = linkedMapOf(bytes(0x01) to Scv.toVoid(), bytes(0x01) to Scv.toUint32(1u))
        assertEquals(2, duplicate.size)
        val error = assertFailsWith<IllegalArgumentException> { Scv.toMap(duplicate) }
        assertTrue(bytes(0x01).toXdrJson() in error.message!!, error.message)
    }

    // ContractInstance: executable first (an external-ref tag by content), then storage
    // (absent first, then entry-wise by content).
    @Test
    fun testContractInstanceComparands_executableThenStorage() {
        fun instance(executable: ContractExecutableXdr, vararg keys: String) = SCValXdr.Instance(
            SCContractInstanceXdr(
                executable,
                if (keys.isEmpty()) null else SCMapXdr(keys.map { SCMapEntryXdr(Scv.toSymbol(it), Scv.toVoid()) })
            )
        )
        fun externalRef(tag: String) = ContractExecutableXdr.ExternalRef(
            ContractExecutableExternalRefXdr(Address(verifier).toSCAddress(), tag.encodeToByteArray())
        )
        val wasm = ContractExecutableXdr.WasmHash(HashXdr(ByteArray(32)))
        val ordered = listOf(
            instance(wasm), instance(wasm, "aa"), instance(wasm, "b"),
            instance(ContractExecutableXdr.Void), instance(externalRef("aa")), instance(externalRef("b"))
        )
        for (i in ordered.indices) {
            for (j in i + 1 until ordered.size) {
                assertTrue(compareScValHostOrder(ordered[i], ordered[j]) < 0, "instance $i must sort before $j")
                assertTrue(compareScValHostOrder(ordered[j], ordered[i]) > 0, "instance $j must sort after $i")
            }
        }
    }

    // I256 with equal hi_hi: hi_lo, then lo_hi, then lo_lo decide, each as an unsigned limb.
    @Test
    fun testI256Comparands_lowerLimbsUnsigned() {
        val max = ULong.MAX_VALUE
        fun i256(hiLo: ULong, loHi: ULong, loLo: ULong) =
            SCValXdr.I256(Int256PartsXdr(Int64Xdr(-1), Uint64Xdr(hiLo), Uint64Xdr(loHi), Uint64Xdr(loLo)))
        val pairs = mapOf(
            "hi_lo" to (i256(1u, 0u, 0u) to i256(max, 0u, 0u)),
            "lo_hi" to (i256(0u, 1u, 0u) to i256(0u, max, 0u)),
            "lo_lo" to (i256(0u, 0u, 1u) to i256(0u, 0u, max))
        )
        for ((limb, pair) in pairs) {
            assertTrue(compareScValHostOrder(pair.first, pair.second) < 0, "$limb: 1 must sort before max")
            assertTrue(compareScValHostOrder(pair.second, pair.first) > 0, "$limb: max must sort after 1")
        }
    }

    // External-ref executables: the owner decides, the tag only between equal owners.
    @Test
    fun testExternalRefExecutables_ownerThenTag() {
        fun instance(ownerFill: Int, tag: String) = SCValXdr.Instance(
            SCContractInstanceXdr(
                ContractExecutableXdr.ExternalRef(
                    ContractExecutableExternalRefXdr(
                        SCAddressXdr.ContractId(ContractIDXdr(HashXdr(ByteArray(32) { ownerFill.toByte() }))),
                        tag.encodeToByteArray()
                    )
                ),
                null
            )
        )
        val ordered = listOf(instance(0x00, "aa"), instance(0x00, "b"), instance(0xff, "aa"), instance(0xff, "b"))
        for (i in ordered.indices) {
            for (j in i + 1 until ordered.size) {
                assertTrue(compareScValHostOrder(ordered[i], ordered[j]) < 0, "executable $i must sort before $j")
                assertTrue(compareScValHostOrder(ordered[j], ordered[i]) > 0, "executable $j must sort after $i")
            }
        }
    }

    // Decoding keeps the wire order: a map whose keys are out of host order re-encodes byte-identically.
    @Test
    fun testDecodedMap_keepsWireOrder() {
        val entries = hostOrderVector().hostOrderShuffle().map { SCMapEntryXdr(it, Scv.toVoid()) }
        val wire = SCValXdr.Map(SCMapXdr(entries)).toXdrBase64()
        assertEquals(wire, SCValXdr.fromXdrBase64(wire).toXdrBase64())
    }
}

/** The cross-SDK host-order vector: 63 keys in ascending Soroban host order. */
internal fun hostOrderVector(): List<SCValXdr> {
    val max = ULong.MAX_VALUE
    fun u64(v: ULong) = Uint64Xdr(v)
    fun u32(v: UInt) = Scv.toUint32(v)
    fun bytes(vararg v: Int) = Scv.toBytes(ByteArray(v.size) { v[it].toByte() })
    fun key32(fill: Int) = ByteArray(32) { fill.toByte() }
    fun map(vararg e: Pair<UInt, UInt>) =
        SCValXdr.Map(SCMapXdr(e.map { SCMapEntryXdr(u32(it.first), u32(it.second)) }))
    fun account(fill: Int) = Scv.toAddress(SCAddressXdr.AccountId(AccountIDXdr(PublicKeyXdr.Ed25519(Uint256Xdr(key32(fill))))))
    fun contract(fill: Int) = Scv.toAddress(SCAddressXdr.ContractId(ContractIDXdr(HashXdr(key32(fill)))))
    fun muxed(id: ULong, fill: Int) =
        Scv.toAddress(SCAddressXdr.MuxedAccount(MuxedEd25519AccountXdr(u64(id), Uint256Xdr(key32(fill)))))
    fun i128(hi: Long, lo: ULong) = SCValXdr.I128(Int128PartsXdr(Int64Xdr(hi), u64(lo)))
    fun nonce(n: Long) = SCValXdr.NonceKey(SCNonceKeyXdr(Int64Xdr(n)))
    return listOf(
        Scv.toBoolean(false), Scv.toBoolean(true), Scv.toVoid(),
        Scv.toError(SCErrorXdr.ContractCode(Uint32Xdr(1u))), Scv.toError(SCErrorXdr.ContractCode(Uint32Xdr(2u))),
        Scv.toError(SCErrorXdr.Code(SCErrorTypeXdr.SCE_WASM_VM, SCErrorCodeXdr.SCEC_INVALID_INPUT)),
        u32(0u), u32(UInt.MAX_VALUE),
        Scv.toInt32(Int.MIN_VALUE), Scv.toInt32(-1), Scv.toInt32(0), Scv.toInt32(1),
        Scv.toUint64(0u), Scv.toUint64(max),
        Scv.toInt64(Long.MIN_VALUE), Scv.toInt64(-1), Scv.toInt64(0),
        Scv.toTimePoint(0u), Scv.toTimePoint(1u), Scv.toDuration(0u),
        SCValXdr.U128(UInt128PartsXdr(u64(0u), u64(1u))), SCValXdr.U128(UInt128PartsXdr(u64(0u), u64(max))),
        SCValXdr.U128(UInt128PartsXdr(u64(1u), u64(0u))),
        i128(-1, max), i128(0, 0u), i128(0, max), i128(1, 0u),
        SCValXdr.U256(UInt256PartsXdr(u64(0u), u64(0u), u64(0u), u64(1u))),
        SCValXdr.U256(UInt256PartsXdr(u64(1u), u64(0u), u64(0u), u64(0u))),
        SCValXdr.I256(Int256PartsXdr(Int64Xdr(-1), u64(max), u64(max), u64(max))),
        SCValXdr.I256(Int256PartsXdr(Int64Xdr(0), u64(0u), u64(0u), u64(0u))),
        bytes(), bytes(0x01), bytes(0x01, 0x00), bytes(0x02), bytes(0xff),
        Scv.toString(""), Scv.toString("a"), Scv.toString("ab"), Scv.toString("b"),
        Scv.toSymbol("A"), Scv.toSymbol("AB"), Scv.toSymbol("B"), Scv.toSymbol("_"), Scv.toSymbol("a"),
        Scv.toVec(emptyList()), Scv.toVec(listOf(u32(1u))), Scv.toVec(listOf(u32(1u), u32(0u))),
        Scv.toVec(listOf(u32(2u))), Scv.toVec(listOf(Scv.toInt32(-1))),
        map(), map(1u to 1u), map(1u to 2u), map(2u to 0u),
        account(0x00), account(0xff), contract(0x00), contract(0xff), muxed(0u, 0xff), muxed(1u, 0x00),
        SCValXdr.Void(SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE),
        nonce(-1), nonce(0)
    )
}

/** The fixed shuffle of [hostOrderVector]: reverse the list, then swap each neighbour pair. */
internal fun <T> List<T>.hostOrderShuffle(): List<T> = reversed().chunked(2).flatMap { it.reversed() }
