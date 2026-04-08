//
//  SmartAccountAuthPayloadTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayload
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayloadCodec
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SmartAccountAuthPayload] data class and [SmartAccountAuthPayloadCodec].
 *
 * Covers read, write, upsertSigner, signerFromScVal, and round-trip serialization.
 */
class SmartAccountAuthPayloadTest {

    private val validAccountAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
    private val validAccountAddress2 = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
    private val validContractAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    private fun keyData(): ByteArray = ByteArray(32) { (it + 1).toByte() }
    private fun sigBytes(): ByteArray = ByteArray(64) { (it + 10).toByte() }
    private fun sigBytes2(): ByteArray = ByteArray(64) { (it + 20).toByte() }

    // ========================================================================
    // SmartAccountAuthPayload data class
    // ========================================================================

    @Test
    fun testPayloadConstruction_emptySignersAndRuleIds() {
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = emptyList()
        )
        assertTrue(payload.signers.isEmpty())
        assertTrue(payload.contextRuleIds.isEmpty())
    }

    @Test
    fun testPayloadConstruction_withSignersAndRuleIds() {
        val signer = DelegatedSigner(validAccountAddress)
        val sig = sigBytes()
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(signer to sig),
            contextRuleIds = listOf(1u, 2u, 3u)
        )
        assertEquals(1, payload.signers.size)
        assertContentEquals(sig, payload.signers[signer])
        assertEquals(listOf(1u, 2u, 3u), payload.contextRuleIds)
    }

    @Test
    fun testPayloadSignersMap_isMutable() {
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = emptyList()
        )
        val signer = DelegatedSigner(validAccountAddress)
        payload.signers[signer] = sigBytes()
        assertEquals(1, payload.signers.size)
    }

    // ========================================================================
    // SmartAccountAuthPayloadCodec.read()
    // ========================================================================

    @Test
    fun testRead_voidReturnsEmptyPayload() {
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toVoid())
        assertTrue(payload.signers.isEmpty())
        assertTrue(payload.contextRuleIds.isEmpty())
    }

    @Test
    fun testRead_nonMapNonVoidThrows() {
        val scVal = Scv.toUint32(42u)
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.read(scVal)
        }
        assertTrue(ex.message!!.contains("AuthPayload"))
    }

    @Test
    fun testRead_symbolScValThrows() {
        val scVal = Scv.toSymbol("hello")
        assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.read(scVal)
        }
    }

    @Test
    fun testRead_bytesScValThrows() {
        val scVal = Scv.toBytes(byteArrayOf(1, 2, 3))
        assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.read(scVal)
        }
    }

    @Test
    fun testRead_vecScValThrows() {
        val scVal = Scv.toVec(listOf(Scv.toUint32(1u)))
        assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.read(scVal)
        }
    }

    @Test
    fun testRead_emptyMapReturnsEmptyPayload() {
        val emptyMap = Scv.toMap(linkedMapOf())
        val payload = SmartAccountAuthPayloadCodec.read(emptyMap)
        assertTrue(payload.signers.isEmpty())
        assertTrue(payload.contextRuleIds.isEmpty())
    }

    @Test
    fun testRead_contextRuleIdsOnly() {
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("context_rule_ids") to Scv.toVec(
                listOf(Scv.toUint32(10u), Scv.toUint32(20u))
            )
        )
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertEquals(listOf(10u, 20u), payload.contextRuleIds)
        assertTrue(payload.signers.isEmpty())
    }

    @Test
    fun testRead_signersOnly_delegatedSigner() {
        val signer = DelegatedSigner(validAccountAddress)
        val sig = sigBytes()

        val signersMap = linkedMapOf<SCValXdr, SCValXdr>(
            signer.toScVal() to Scv.toBytes(sig)
        )
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("signers") to Scv.toMap(signersMap)
        )

        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertEquals(1, payload.signers.size)
        assertTrue(payload.contextRuleIds.isEmpty())

        val parsedSigner = payload.signers.keys.first()
        assertIs<DelegatedSigner>(parsedSigner)
        assertEquals(validAccountAddress, parsedSigner.address)
        assertContentEquals(sig, payload.signers[parsedSigner])
    }

    @Test
    fun testRead_signersOnly_externalSigner() {
        val kd = keyData()
        val signer = ExternalSigner(validContractAddress, kd)
        val sig = sigBytes()

        val signersMap = linkedMapOf<SCValXdr, SCValXdr>(
            signer.toScVal() to Scv.toBytes(sig)
        )
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("signers") to Scv.toMap(signersMap)
        )

        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertEquals(1, payload.signers.size)

        val parsedSigner = payload.signers.keys.first()
        assertIs<ExternalSigner>(parsedSigner)
        assertEquals(validContractAddress, parsedSigner.verifierAddress)
        assertContentEquals(kd, parsedSigner.keyData)
    }

    @Test
    fun testRead_multipleSigners() {
        val signer1 = DelegatedSigner(validAccountAddress)
        val signer2 = DelegatedSigner(validAccountAddress2)
        val sig1 = sigBytes()
        val sig2 = sigBytes2()

        val signersMap = linkedMapOf<SCValXdr, SCValXdr>(
            signer1.toScVal() to Scv.toBytes(sig1),
            signer2.toScVal() to Scv.toBytes(sig2)
        )
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("signers") to Scv.toMap(signersMap)
        )

        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertEquals(2, payload.signers.size)
    }

    @Test
    fun testRead_signerWithNonBytesValueThrows() {
        val signer = DelegatedSigner(validAccountAddress)
        val signersMap = linkedMapOf<SCValXdr, SCValXdr>(
            signer.toScVal() to Scv.toUint32(99u)
        )
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("signers") to Scv.toMap(signersMap)
        )

        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        }
        assertTrue(ex.message!!.contains("not encoded as Bytes"))
    }

    @Test
    fun testRead_contextRuleIdsWithNonU32ElementsAreSkipped() {
        val vec = Scv.toVec(
            listOf(Scv.toUint32(5u), Scv.toSymbol("not_a_u32"), Scv.toUint32(10u))
        )
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("context_rule_ids") to vec
        )
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertEquals(listOf(5u, 10u), payload.contextRuleIds)
    }

    @Test
    fun testRead_unknownKeysAreIgnored() {
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("unknown_field") to Scv.toUint32(42u),
            Scv.toSymbol("context_rule_ids") to Scv.toVec(listOf(Scv.toUint32(1u)))
        )
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertEquals(listOf(1u), payload.contextRuleIds)
        assertTrue(payload.signers.isEmpty())
    }

    @Test
    fun testRead_nonSymbolKeysAreSkipped() {
        // Directly construct an SCValXdr.Map with a non-Symbol key entry
        // to exercise the `if (key !is SCValXdr.Sym) continue` branch
        val entries = listOf(
            SCMapEntryXdr(
                key = Scv.toUint32(999u),
                `val` = Scv.toVec(listOf(Scv.toUint32(5u)))
            ),
            SCMapEntryXdr(
                key = Scv.toSymbol("context_rule_ids"),
                `val` = Scv.toVec(listOf(Scv.toUint32(7u)))
            )
        )
        val mapScVal = SCValXdr.Map(SCMapXdr(entries))
        val payload = SmartAccountAuthPayloadCodec.read(mapScVal)
        assertEquals(listOf(7u), payload.contextRuleIds)
        assertTrue(payload.signers.isEmpty())
    }

    @Test
    fun testRead_emptyContextRuleIdsVec() {
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("context_rule_ids") to Scv.toVec(emptyList())
        )
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertTrue(payload.contextRuleIds.isEmpty())
    }

    @Test
    fun testRead_contextRuleIdsNotVecIsIgnored() {
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("context_rule_ids") to Scv.toUint32(42u)
        )
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertTrue(payload.contextRuleIds.isEmpty())
    }

    @Test
    fun testRead_signersNotMapIsIgnored() {
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("signers") to Scv.toVec(emptyList())
        )
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertTrue(payload.signers.isEmpty())
    }

    @Test
    fun testRead_singleContextRuleId() {
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("context_rule_ids") to Scv.toVec(listOf(Scv.toUint32(42u)))
        )
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertEquals(listOf(42u), payload.contextRuleIds)
    }

    @Test
    fun testRead_contextRuleIdBoundaryValues() {
        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("context_rule_ids") to Scv.toVec(
                listOf(Scv.toUint32(0u), Scv.toUint32(UInt.MAX_VALUE))
            )
        )
        val payload = SmartAccountAuthPayloadCodec.read(Scv.toMap(outerMap))
        assertEquals(listOf(0u, UInt.MAX_VALUE), payload.contextRuleIds)
    }

    // ========================================================================
    // SmartAccountAuthPayloadCodec.write()
    // ========================================================================

    @Test
    fun testWrite_emptyPayload() {
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = emptyList()
        )
        val scVal = SmartAccountAuthPayloadCodec.write(payload)
        assertIs<SCValXdr.Map>(scVal)

        val entries = (scVal as SCValXdr.Map).value?.value
        assertNotNull(entries)
        assertEquals(2, entries.size)

        // First entry: context_rule_ids
        val ruleIdsKey = entries[0].key
        assertIs<SCValXdr.Sym>(ruleIdsKey)
        assertEquals("context_rule_ids", (ruleIdsKey as SCValXdr.Sym).value.value)

        val ruleIdsVal = entries[0].`val`
        assertIs<SCValXdr.Vec>(ruleIdsVal)
        assertTrue((ruleIdsVal as SCValXdr.Vec).value?.value?.isEmpty() == true)

        // Second entry: signers
        val signersKey = entries[1].key
        assertIs<SCValXdr.Sym>(signersKey)
        assertEquals("signers", (signersKey as SCValXdr.Sym).value.value)
    }

    @Test
    fun testWrite_withContextRuleIds() {
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = listOf(1u, 2u, 3u)
        )
        val scVal = SmartAccountAuthPayloadCodec.write(payload)
        val entries = (scVal as SCValXdr.Map).value!!.value

        val ruleIdsVec = entries[0].`val` as SCValXdr.Vec
        val elements = ruleIdsVec.value!!.value
        assertEquals(3, elements.size)
        assertEquals(1u, (elements[0] as SCValXdr.U32).value.value)
        assertEquals(2u, (elements[1] as SCValXdr.U32).value.value)
        assertEquals(3u, (elements[2] as SCValXdr.U32).value.value)
    }

    @Test
    fun testWrite_withSingleDelegatedSigner() {
        val signer = DelegatedSigner(validAccountAddress)
        val sig = sigBytes()
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(signer to sig),
            contextRuleIds = emptyList()
        )
        val scVal = SmartAccountAuthPayloadCodec.write(payload)
        val entries = (scVal as SCValXdr.Map).value!!.value
        assertEquals(2, entries.size)

        val signersMap = entries[1].`val` as SCValXdr.Map
        val signerEntries = signersMap.value!!.value
        assertEquals(1, signerEntries.size)

        val signerKey = signerEntries[0].key
        assertIs<SCValXdr.Vec>(signerKey)

        val sigValue = signerEntries[0].`val`
        assertIs<SCValXdr.Bytes>(sigValue)
        assertContentEquals(sig, (sigValue as SCValXdr.Bytes).value.value)
    }

    @Test
    fun testWrite_withSingleExternalSigner() {
        val kd = keyData()
        val signer = ExternalSigner(validContractAddress, kd)
        val sig = sigBytes()
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(signer to sig),
            contextRuleIds = listOf(5u)
        )
        val scVal = SmartAccountAuthPayloadCodec.write(payload)
        val entries = (scVal as SCValXdr.Map).value!!.value

        val ruleIdsVec = entries[0].`val` as SCValXdr.Vec
        assertEquals(1, ruleIdsVec.value!!.value.size)
        assertEquals(5u, (ruleIdsVec.value!!.value[0] as SCValXdr.U32).value.value)

        val signersMap = entries[1].`val` as SCValXdr.Map
        assertEquals(1, signersMap.value!!.value.size)
    }

    @Test
    fun testWrite_outputMapHasCorrectFieldOrder() {
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = listOf(1u)
        )
        val scVal = SmartAccountAuthPayloadCodec.write(payload)
        val entries = (scVal as SCValXdr.Map).value!!.value
        assertEquals(2, entries.size)
        assertEquals("context_rule_ids", (entries[0].key as SCValXdr.Sym).value.value)
        assertEquals("signers", (entries[1].key as SCValXdr.Sym).value.value)
    }

    // ========================================================================
    // Round-trip: write then read
    // ========================================================================

    @Test
    fun testRoundTrip_emptyPayload() {
        val original = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = emptyList()
        )
        val scVal = SmartAccountAuthPayloadCodec.write(original)
        val restored = SmartAccountAuthPayloadCodec.read(scVal)

        assertTrue(restored.signers.isEmpty())
        assertTrue(restored.contextRuleIds.isEmpty())
    }

    @Test
    fun testRoundTrip_contextRuleIdsOnly() {
        val original = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = listOf(0u, 100u, UInt.MAX_VALUE)
        )
        val scVal = SmartAccountAuthPayloadCodec.write(original)
        val restored = SmartAccountAuthPayloadCodec.read(scVal)

        assertEquals(original.contextRuleIds, restored.contextRuleIds)
        assertTrue(restored.signers.isEmpty())
    }

    @Test
    fun testRoundTrip_delegatedSignerWithContextRuleIds() {
        val signer = DelegatedSigner(validAccountAddress)
        val sig = sigBytes()
        val original = SmartAccountAuthPayload(
            signers = mutableMapOf(signer to sig),
            contextRuleIds = listOf(1u, 2u)
        )
        val scVal = SmartAccountAuthPayloadCodec.write(original)
        val restored = SmartAccountAuthPayloadCodec.read(scVal)

        assertEquals(original.contextRuleIds, restored.contextRuleIds)
        assertEquals(1, restored.signers.size)

        val restoredSigner = restored.signers.keys.first()
        assertIs<DelegatedSigner>(restoredSigner)
        assertEquals(validAccountAddress, restoredSigner.address)
        assertContentEquals(sig, restored.signers[restoredSigner])
    }

    @Test
    fun testRoundTrip_externalSignerWithContextRuleIds() {
        val kd = keyData()
        val signer = ExternalSigner(validContractAddress, kd)
        val sig = sigBytes()
        val original = SmartAccountAuthPayload(
            signers = mutableMapOf(signer to sig),
            contextRuleIds = listOf(10u)
        )
        val scVal = SmartAccountAuthPayloadCodec.write(original)
        val restored = SmartAccountAuthPayloadCodec.read(scVal)

        assertEquals(original.contextRuleIds, restored.contextRuleIds)
        assertEquals(1, restored.signers.size)

        val restoredSigner = restored.signers.keys.first()
        assertIs<ExternalSigner>(restoredSigner)
        assertEquals(validContractAddress, restoredSigner.verifierAddress)
        assertContentEquals(kd, restoredSigner.keyData)
        assertContentEquals(sig, restored.signers[restoredSigner])
    }

    @Test
    fun testRoundTrip_multipleSignersMixed() {
        val delegated = DelegatedSigner(validAccountAddress)
        val external = ExternalSigner(validContractAddress, keyData())
        val sig1 = sigBytes()
        val sig2 = sigBytes2()
        val original = SmartAccountAuthPayload(
            signers = mutableMapOf(delegated to sig1, external to sig2),
            contextRuleIds = listOf(1u, 2u, 3u)
        )
        val scVal = SmartAccountAuthPayloadCodec.write(original)
        val restored = SmartAccountAuthPayloadCodec.read(scVal)

        assertEquals(original.contextRuleIds, restored.contextRuleIds)
        assertEquals(2, restored.signers.size)

        val delegatedSigners = restored.signers.keys.filterIsInstance<DelegatedSigner>()
        val externalSigners = restored.signers.keys.filterIsInstance<ExternalSigner>()
        assertEquals(1, delegatedSigners.size)
        assertEquals(1, externalSigners.size)
    }

    // ========================================================================
    // SmartAccountAuthPayloadCodec.upsertSigner()
    // ========================================================================

    @Test
    fun testUpsertSigner_addToEmptyPayload() {
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = emptyList()
        )
        val signer = DelegatedSigner(validAccountAddress)
        val sig = sigBytes()

        SmartAccountAuthPayloadCodec.upsertSigner(payload, signer, sig)

        assertEquals(1, payload.signers.size)
        assertContentEquals(sig, payload.signers.values.first())
    }

    @Test
    fun testUpsertSigner_addSecondDistinctSigner() {
        val signer1 = DelegatedSigner(validAccountAddress)
        val signer2 = DelegatedSigner(validAccountAddress2)
        val sig1 = sigBytes()
        val sig2 = sigBytes2()

        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(signer1 to sig1),
            contextRuleIds = emptyList()
        )
        SmartAccountAuthPayloadCodec.upsertSigner(payload, signer2, sig2)

        assertEquals(2, payload.signers.size)
    }

    @Test
    fun testUpsertSigner_replacesExistingDelegatedSigner() {
        val signer = DelegatedSigner(validAccountAddress)
        val oldSig = sigBytes()
        val newSig = sigBytes2()

        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(signer to oldSig),
            contextRuleIds = emptyList()
        )
        SmartAccountAuthPayloadCodec.upsertSigner(
            payload, DelegatedSigner(validAccountAddress), newSig
        )

        assertEquals(1, payload.signers.size)
        assertContentEquals(newSig, payload.signers.values.first())
    }

    @Test
    fun testUpsertSigner_replacesExistingExternalSigner() {
        val kd = keyData()
        val signer = ExternalSigner(validContractAddress, kd)
        val oldSig = sigBytes()
        val newSig = sigBytes2()

        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(signer to oldSig),
            contextRuleIds = emptyList()
        )
        SmartAccountAuthPayloadCodec.upsertSigner(
            payload, ExternalSigner(validContractAddress, kd), newSig
        )

        assertEquals(1, payload.signers.size)
        assertContentEquals(newSig, payload.signers.values.first())
    }

    @Test
    fun testUpsertSigner_doesNotReplaceDifferentSignerType() {
        val delegated = DelegatedSigner(validAccountAddress)
        val external = ExternalSigner(validContractAddress, keyData())
        val sig1 = sigBytes()
        val sig2 = sigBytes2()

        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(delegated to sig1),
            contextRuleIds = emptyList()
        )
        SmartAccountAuthPayloadCodec.upsertSigner(payload, external, sig2)

        assertEquals(2, payload.signers.size)
    }

    @Test
    fun testUpsertSigner_preservesContextRuleIds() {
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = listOf(1u, 2u, 3u)
        )
        SmartAccountAuthPayloadCodec.upsertSigner(
            payload, DelegatedSigner(validAccountAddress), sigBytes()
        )
        assertEquals(listOf(1u, 2u, 3u), payload.contextRuleIds)
    }

    @Test
    fun testUpsertSigner_multipleUpsertsOnSameSigner() {
        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(),
            contextRuleIds = emptyList()
        )
        val signer = DelegatedSigner(validAccountAddress)
        val sig1 = ByteArray(64) { 1 }
        val sig2 = ByteArray(64) { 2 }
        val sig3 = ByteArray(64) { 3 }

        SmartAccountAuthPayloadCodec.upsertSigner(payload, signer, sig1)
        assertEquals(1, payload.signers.size)

        SmartAccountAuthPayloadCodec.upsertSigner(payload, DelegatedSigner(validAccountAddress), sig2)
        assertEquals(1, payload.signers.size)

        SmartAccountAuthPayloadCodec.upsertSigner(payload, DelegatedSigner(validAccountAddress), sig3)
        assertEquals(1, payload.signers.size)
        assertContentEquals(sig3, payload.signers.values.first())
    }

    // ========================================================================
    // SmartAccountAuthPayloadCodec.signerFromScVal()
    // ========================================================================

    @Test
    fun testSignerFromScVal_delegatedSigner() {
        val signer = DelegatedSigner(validAccountAddress)
        val scVal = signer.toScVal()
        val parsed = SmartAccountAuthPayloadCodec.signerFromScVal(scVal)

        assertIs<DelegatedSigner>(parsed)
        assertEquals(validAccountAddress, parsed.address)
    }

    @Test
    fun testSignerFromScVal_delegatedSignerWithContractAddress() {
        val signer = DelegatedSigner(validContractAddress)
        val scVal = signer.toScVal()
        val parsed = SmartAccountAuthPayloadCodec.signerFromScVal(scVal)

        assertIs<DelegatedSigner>(parsed)
        assertEquals(validContractAddress, parsed.address)
    }

    @Test
    fun testSignerFromScVal_externalSigner() {
        val kd = keyData()
        val signer = ExternalSigner(validContractAddress, kd)
        val scVal = signer.toScVal()
        val parsed = SmartAccountAuthPayloadCodec.signerFromScVal(scVal)

        assertIs<ExternalSigner>(parsed)
        assertEquals(validContractAddress, parsed.verifierAddress)
        assertContentEquals(kd, parsed.keyData)
    }

    @Test
    fun testSignerFromScVal_nonVecThrows() {
        val scVal = Scv.toSymbol("Delegated")
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("not a Vec"))
    }

    @Test
    fun testSignerFromScVal_emptyVecThrows() {
        val scVal = Scv.toVec(emptyList())
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("empty"))
    }

    @Test
    fun testSignerFromScVal_firstElementNotSymbolThrows() {
        val scVal = Scv.toVec(listOf(
            Scv.toUint32(1u),
            Scv.toAddress(Address(validAccountAddress).toSCAddress())
        ))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("not a Symbol"))
    }

    @Test
    fun testSignerFromScVal_unknownTypeTagThrows() {
        val scVal = Scv.toVec(listOf(
            Scv.toSymbol("Unknown"),
            Scv.toAddress(Address(validAccountAddress).toSCAddress())
        ))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("Unknown signer type tag"))
    }

    @Test
    fun testSignerFromScVal_delegatedWithTooFewElementsThrows() {
        val scVal = Scv.toVec(listOf(Scv.toSymbol("Delegated")))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("at least 2 elements"))
    }

    @Test
    fun testSignerFromScVal_delegatedSecondElementNotAddressThrows() {
        val scVal = Scv.toVec(listOf(
            Scv.toSymbol("Delegated"),
            Scv.toUint32(42u)
        ))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("not an Address"))
    }

    @Test
    fun testSignerFromScVal_externalWithTooFewElementsThrows() {
        val scVal = Scv.toVec(listOf(
            Scv.toSymbol("External"),
            Scv.toAddress(Address(validContractAddress).toSCAddress())
        ))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("at least 3 elements"))
    }

    @Test
    fun testSignerFromScVal_externalSecondElementNotAddressThrows() {
        val scVal = Scv.toVec(listOf(
            Scv.toSymbol("External"),
            Scv.toUint32(1u),
            Scv.toBytes(keyData())
        ))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("second element is not an Address"))
    }

    @Test
    fun testSignerFromScVal_externalThirdElementNotBytesThrows() {
        val scVal = Scv.toVec(listOf(
            Scv.toSymbol("External"),
            Scv.toAddress(Address(validContractAddress).toSCAddress()),
            Scv.toUint32(42u)
        ))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("third element is not Bytes"))
    }

    @Test
    fun testSignerFromScVal_externalWithOnlySymbolThrows() {
        val scVal = Scv.toVec(listOf(Scv.toSymbol("External")))
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuthPayloadCodec.signerFromScVal(scVal)
        }
        assertTrue(ex.message!!.contains("at least 3 elements"))
    }

    // ========================================================================
    // Write: signer sorting by XDR-encoded key bytes
    // ========================================================================

    @Test
    fun testWrite_signersSortedDeterministically() {
        val signer1 = DelegatedSigner(validAccountAddress)
        val signer2 = DelegatedSigner(validAccountAddress2)
        val sig1 = sigBytes()
        val sig2 = sigBytes2()

        // Insert in one order
        val payload1 = SmartAccountAuthPayload(
            signers = mutableMapOf(signer1 to sig1, signer2 to sig2),
            contextRuleIds = emptyList()
        )
        val scVal1 = SmartAccountAuthPayloadCodec.write(payload1)

        // Insert in reverse order
        val payload2 = SmartAccountAuthPayload(
            signers = mutableMapOf(signer2 to sig2, signer1 to sig1),
            contextRuleIds = emptyList()
        )
        val scVal2 = SmartAccountAuthPayloadCodec.write(payload2)

        // Both should produce the same signer entry order
        val entries1 = ((scVal1 as SCValXdr.Map).value!!.value[1].`val` as SCValXdr.Map).value!!.value
        val entries2 = ((scVal2 as SCValXdr.Map).value!!.value[1].`val` as SCValXdr.Map).value!!.value

        assertEquals(entries1.size, entries2.size)
        for (i in entries1.indices) {
            val key1Vec = (entries1[i].key as SCValXdr.Vec).value!!.value
            val key2Vec = (entries2[i].key as SCValXdr.Vec).value!!.value
            val addr1 = key1Vec[1] as SCValXdr.Address
            val addr2 = key2Vec[1] as SCValXdr.Address
            assertEquals(
                Address.fromSCAddress(addr1.value).getEncodedAddress(),
                Address.fromSCAddress(addr2.value).getEncodedAddress()
            )
        }
    }

    // ========================================================================
    // Full integration: write -> read -> verify, upsert -> write -> read
    // ========================================================================

    @Test
    fun testFullRoundTrip_complexPayload() {
        val delegated1 = DelegatedSigner(validAccountAddress)
        val delegated2 = DelegatedSigner(validAccountAddress2)
        val external1 = ExternalSigner(validContractAddress, keyData())
        val sig1 = ByteArray(64) { 1 }
        val sig2 = ByteArray(64) { 2 }
        val sig3 = ByteArray(64) { 3 }
        val ruleIds = listOf(0u, 1u, 100u, UInt.MAX_VALUE)

        val original = SmartAccountAuthPayload(
            signers = mutableMapOf(
                delegated1 to sig1,
                delegated2 to sig2,
                external1 to sig3
            ),
            contextRuleIds = ruleIds
        )

        val scVal = SmartAccountAuthPayloadCodec.write(original)
        val restored = SmartAccountAuthPayloadCodec.read(scVal)

        assertEquals(ruleIds, restored.contextRuleIds)
        assertEquals(3, restored.signers.size)

        val restoredDelegated = restored.signers.keys.filterIsInstance<DelegatedSigner>()
        val restoredExternal = restored.signers.keys.filterIsInstance<ExternalSigner>()
        assertEquals(2, restoredDelegated.size)
        assertEquals(1, restoredExternal.size)

        val addresses = restoredDelegated.map { it.address }.toSet()
        assertTrue(validAccountAddress in addresses)
        assertTrue(validAccountAddress2 in addresses)
        assertEquals(validContractAddress, restoredExternal[0].verifierAddress)
    }

    @Test
    fun testUpsertThenWriteAndRead_replacedSignerNotPresent() {
        val signer = DelegatedSigner(validAccountAddress)
        val oldSig = ByteArray(64) { 0xAA.toByte() }
        val newSig = ByteArray(64) { 0xBB.toByte() }

        val payload = SmartAccountAuthPayload(
            signers = mutableMapOf(signer to oldSig),
            contextRuleIds = listOf(1u)
        )

        SmartAccountAuthPayloadCodec.upsertSigner(
            payload, DelegatedSigner(validAccountAddress), newSig
        )

        val scVal = SmartAccountAuthPayloadCodec.write(payload)
        val restored = SmartAccountAuthPayloadCodec.read(scVal)

        assertEquals(1, restored.signers.size)
        val restoredSigner = restored.signers.keys.first()
        assertIs<DelegatedSigner>(restoredSigner)
        assertEquals(validAccountAddress, restoredSigner.address)
        assertContentEquals(newSig, restored.signers[restoredSigner])
    }
}
