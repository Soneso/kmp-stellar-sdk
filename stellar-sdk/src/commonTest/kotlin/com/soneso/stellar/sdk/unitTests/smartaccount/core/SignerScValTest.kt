//
//  SignerScValTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.core

import com.soneso.stellar.sdk.unitTests.smartaccount.secp256r1Key
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SubmissionMethod
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [DelegatedSigner.toScVal], [ExternalSigner.toScVal],
 * signer equality, hashCode, and [SubmissionMethod] enum coverage.
 */
class SignerScValTest {

    private val validAccountAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
    private val validAccountAddress2 = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
    private val validContractAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val validContractAddress2 = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"

    private fun ed25519Key(): ByteArray = ByteArray(32) { (it + 1).toByte() }
    private fun credentialIdBytes(): ByteArray = ByteArray(32) { (it + 100).toByte() }

    // ========================================================================
    // DelegatedSigner.toScVal()
    // ========================================================================

    @Test
    fun testDelegatedSignerToScVal_isVec() {
        val signer = DelegatedSigner(validAccountAddress)
        val scVal = signer.toScVal()
        assertIs<SCValXdr.Vec>(scVal, "DelegatedSigner.toScVal() must return SCValXdr.Vec")
    }

    @Test
    fun testDelegatedSignerToScVal_hasExactlyTwoElements() {
        val signer = DelegatedSigner(validAccountAddress)
        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value?.value
        assertEquals(2, elements?.size, "DelegatedSigner Vec must have exactly 2 elements")
    }

    @Test
    fun testDelegatedSignerToScVal_firstElementIsSymbolDelegated() {
        val signer = DelegatedSigner(validAccountAddress)
        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value!!.value
        val firstElement = elements[0]
        assertIs<SCValXdr.Sym>(firstElement, "First element must be SCValXdr.Sym")
        assertEquals("Delegated", (firstElement as SCValXdr.Sym).value.value, "Symbol must be \"Delegated\"")
    }

    @Test
    fun testDelegatedSignerToScVal_secondElementIsAddress() {
        val signer = DelegatedSigner(validAccountAddress)
        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value!!.value
        val secondElement = elements[1]
        assertIs<SCValXdr.Address>(secondElement, "Second element must be SCValXdr.Address")
    }

    @Test
    fun testDelegatedSignerToScVal_contractAddress_isVecWithSymbolAndAddress() {
        val signer = DelegatedSigner(validContractAddress)
        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value!!.value
        assertEquals(2, elements.size)
        assertIs<SCValXdr.Sym>(elements[0])
        assertEquals("Delegated", (elements[0] as SCValXdr.Sym).value.value)
        assertIs<SCValXdr.Address>(elements[1])
    }

    @Test
    fun testDelegatedSignerToScVal_deterministicEncoding() {
        val signer1 = DelegatedSigner(validAccountAddress)
        val signer2 = DelegatedSigner(validAccountAddress)
        val vec1 = signer1.toScVal() as SCValXdr.Vec
        val vec2 = signer2.toScVal() as SCValXdr.Vec
        // Both must produce the same structure with same address
        assertEquals(
            (vec1.value!!.value[1] as SCValXdr.Address).value.toString(),
            (vec2.value!!.value[1] as SCValXdr.Address).value.toString()
        )
    }

    // ========================================================================
    // ExternalSigner.toScVal()
    // ========================================================================

    @Test
    fun testExternalSignerToScVal_isVec() {
        val signer = ExternalSigner.ed25519(validContractAddress, ed25519Key())
        val scVal = signer.toScVal()
        assertIs<SCValXdr.Vec>(scVal, "ExternalSigner.toScVal() must return SCValXdr.Vec")
    }

    @Test
    fun testExternalSignerToScVal_hasExactlyThreeElements() {
        val signer = ExternalSigner.ed25519(validContractAddress, ed25519Key())
        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value?.value
        assertEquals(3, elements?.size, "ExternalSigner Vec must have exactly 3 elements")
    }

    @Test
    fun testExternalSignerToScVal_firstElementIsSymbolExternal() {
        val signer = ExternalSigner.ed25519(validContractAddress, ed25519Key())
        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value!!.value
        val firstElement = elements[0]
        assertIs<SCValXdr.Sym>(firstElement, "First element must be SCValXdr.Sym")
        assertEquals("External", (firstElement as SCValXdr.Sym).value.value, "Symbol must be \"External\"")
    }

    @Test
    fun testExternalSignerToScVal_secondElementIsAddress() {
        val signer = ExternalSigner.ed25519(validContractAddress, ed25519Key())
        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value!!.value
        assertIs<SCValXdr.Address>(elements[1], "Second element must be SCValXdr.Address")
    }

    @Test
    fun testExternalSignerToScVal_thirdElementIsBytesMatchingKeyData() {
        val keyData = ed25519Key()
        val signer = ExternalSigner.ed25519(validContractAddress, keyData)
        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value!!.value
        val thirdElement = elements[2]
        assertIs<SCValXdr.Bytes>(thirdElement, "Third element must be SCValXdr.Bytes")
        assertTrue(
            (thirdElement as SCValXdr.Bytes).value.value.contentEquals(keyData),
            "Third element bytes must match keyData exactly"
        )
    }

    @Test
    fun testExternalSignerToScVal_webAuthn_thirdElementContainsCombinedKeyData() {
        val pubKey = secp256r1Key()
        val credId = credentialIdBytes()
        val signer = ExternalSigner.webAuthn(validContractAddress, pubKey, credId)
        val expectedKeyData = pubKey + credId

        val vec = signer.toScVal() as SCValXdr.Vec
        val elements = vec.value!!.value
        val thirdElement = elements[2] as SCValXdr.Bytes
        assertTrue(
            thirdElement.value.value.contentEquals(expectedKeyData),
            "Third element must contain pubKey + credentialId"
        )
    }

    // ========================================================================
    // DelegatedSigner invalid address
    // ========================================================================

    @Test
    fun testDelegatedSigner_emptyAddress_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("")
        }
    }

    @Test
    fun testDelegatedSigner_invalidAddress_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("invalid")
        }
    }

    @Test
    fun testDelegatedSigner_sMAddress_throws() {
        // S-address (secret seed) is not allowed
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("SCZANGBA5XYAWXDG3WR47HBOKTLYBCBFDMCSFWWL5CRFUFDGCFZGZP5")
        }
    }

    @Test
    fun testDelegatedSigner_gAddressTooShort_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("GABC123")
        }
    }

    @Test
    fun testDelegatedSigner_validGAddress_doesNotThrow() {
        // Should construct without exception
        val signer = DelegatedSigner(validAccountAddress)
        assertEquals(validAccountAddress, signer.address)
    }

    @Test
    fun testDelegatedSigner_validCAddress_doesNotThrow() {
        val signer = DelegatedSigner(validContractAddress)
        assertEquals(validContractAddress, signer.address)
    }

    // ========================================================================
    // ExternalSigner equality and hashCode
    // ========================================================================

    @Test
    fun testExternalSigner_equalInstancesHaveSameHashCode() {
        val keyData = ed25519Key()
        val signerA = ExternalSigner.ed25519(validContractAddress, keyData.copyOf())
        val signerB = ExternalSigner.ed25519(validContractAddress, keyData.copyOf())
        assertEquals(signerA, signerB, "Signers with same verifier address and keyData must be equal")
        assertEquals(signerA.hashCode(), signerB.hashCode(), "Equal signers must have equal hash codes")
    }

    @Test
    fun testExternalSigner_differentKeyData_differentHashCode() {
        val keyA = ByteArray(32) { 0x01 }
        val keyB = ByteArray(32) { 0x02 }
        val signerA = ExternalSigner.ed25519(validContractAddress, keyA)
        val signerB = ExternalSigner.ed25519(validContractAddress, keyB)
        assertNotEquals(signerA, signerB, "Signers with different keyData must not be equal")
        // Hash codes may differ (not guaranteed, but with distinct inputs they should)
        assertNotEquals(signerA.hashCode(), signerB.hashCode(), "Different keyData should produce different hash codes")
    }

    @Test
    fun testExternalSigner_differentVerifierAddress_differentHashCode() {
        val keyData = ed25519Key()
        val signerA = ExternalSigner.ed25519(validContractAddress, keyData.copyOf())
        val signerB = ExternalSigner.ed25519(validContractAddress2, keyData.copyOf())
        assertNotEquals(signerA, signerB)
        assertNotEquals(signerA.hashCode(), signerB.hashCode())
    }

    // ========================================================================
    // SubmissionMethod enum
    // ========================================================================

    @Test
    fun testSubmissionMethod_rpcValueExists() {
        val method = SubmissionMethod.RPC
        assertEquals("RPC", method.name)
    }

    @Test
    fun testSubmissionMethod_relayerValueExists() {
        val method = SubmissionMethod.RELAYER
        assertEquals("RELAYER", method.name)
    }

    @Test
    fun testSubmissionMethod_valueOfRpc() {
        val method = SubmissionMethod.valueOf("RPC")
        assertEquals(SubmissionMethod.RPC, method)
    }

    @Test
    fun testSubmissionMethod_valueOfRelayer() {
        val method = SubmissionMethod.valueOf("RELAYER")
        assertEquals(SubmissionMethod.RELAYER, method)
    }

    @Test
    fun testSubmissionMethod_exactlyTwoValues() {
        val entries = SubmissionMethod.entries
        assertEquals(2, entries.size, "SubmissionMethod must have exactly 2 values: RPC and RELAYER")
    }
}
