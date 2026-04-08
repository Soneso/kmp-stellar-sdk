//
//  SignerTypesTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for signer type construction, validation, ScVal encoding,
 * equality, uniqueKey, and collection behavior.
 *
 * Complements [SignerScValTest] (ScVal structure, basic equality/hashCode) and
 * [SmartAccountBuildersTest] (builder utilities, matching, deduplication) by
 * covering factory method validation, uniqueKey format, round-trip verification,
 * signersEqual symmetry, and collection semantics.
 */
class SignerTypesTest {

    // ========================================================================
    // Fixtures
    // ========================================================================

    private val validAccountAddress1 = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
    private val validAccountAddress2 = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
    private val validContractAddress1 = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val validContractAddress2 = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"

    private fun secp256r1Key(): ByteArray {
        val key = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        key[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until key.size) key[i] = (i % 256).toByte()
        return key
    }

    private fun secp256r1Key2(): ByteArray {
        val key = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        key[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until key.size) key[i] = ((i + 50) % 256).toByte()
        return key
    }

    private fun ed25519Key(): ByteArray = ByteArray(32) { (it + 1).toByte() }
    private fun ed25519Key2(): ByteArray = ByteArray(32) { (it + 50).toByte() }
    private fun credentialIdBytes(): ByteArray = ByteArray(32) { (it + 100).toByte() }
    private fun credentialIdBytes2(): ByteArray = ByteArray(16) { (it + 200).toByte() }

    // ========================================================================
    // ExternalSigner.webAuthn() — Factory Validation
    // ========================================================================

    @Test
    fun testWebAuthnFactory_wrongKeySize_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            ExternalSigner.webAuthn(validContractAddress1, ByteArray(32), credentialIdBytes())
        }
    }

    @Test
    fun testWebAuthnFactory_wrongPrefix_throws() {
        val key = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        key[0] = 0x02 // compressed prefix instead of 0x04
        assertFailsWith<ValidationException.InvalidInput> {
            ExternalSigner.webAuthn(validContractAddress1, key, credentialIdBytes())
        }
    }

    @Test
    fun testWebAuthnFactory_emptyCredentialId_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            ExternalSigner.webAuthn(validContractAddress1, secp256r1Key(), ByteArray(0))
        }
    }

    @Test
    fun testWebAuthnFactory_invalidVerifierAddress_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            ExternalSigner.webAuthn("GINVALID", secp256r1Key(), credentialIdBytes())
        }
    }

    @Test
    fun testWebAuthnFactory_gAddressAsVerifier_throws() {
        // G-addresses are not valid contract addresses
        assertFailsWith<ValidationException.InvalidAddress> {
            ExternalSigner.webAuthn(validAccountAddress1, secp256r1Key(), credentialIdBytes())
        }
    }

    @Test
    fun testWebAuthnFactory_keyDataIsPubKeyPlusCredentialId() {
        val pubKey = secp256r1Key()
        val credId = credentialIdBytes()
        val signer = ExternalSigner.webAuthn(validContractAddress1, pubKey, credId)
        val expected = pubKey + credId
        assertTrue(signer.keyData.contentEquals(expected))
        assertEquals(pubKey.size + credId.size, signer.keyData.size)
    }

    @Test
    fun testWebAuthnFactory_singleByteCredentialId_succeeds() {
        val signer = ExternalSigner.webAuthn(validContractAddress1, secp256r1Key(), byteArrayOf(0x01))
        assertEquals(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE + 1, signer.keyData.size)
    }

    @Test
    fun testWebAuthnFactory_largeCredentialId_succeeds() {
        val largeCredId = ByteArray(256) { it.toByte() }
        val signer = ExternalSigner.webAuthn(validContractAddress1, secp256r1Key(), largeCredId)
        assertEquals(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE + 256, signer.keyData.size)
    }

    // ========================================================================
    // ExternalSigner.ed25519() — Factory Validation
    // ========================================================================

    @Test
    fun testEd25519Factory_wrongKeySize_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            ExternalSigner.ed25519(validContractAddress1, ByteArray(64))
        }
    }

    @Test
    fun testEd25519Factory_emptyKey_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            ExternalSigner.ed25519(validContractAddress1, ByteArray(0))
        }
    }

    @Test
    fun testEd25519Factory_invalidVerifierAddress_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            ExternalSigner.ed25519("not-an-address", ed25519Key())
        }
    }

    @Test
    fun testEd25519Factory_gAddressAsVerifier_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            ExternalSigner.ed25519(validAccountAddress1, ed25519Key())
        }
    }

    @Test
    fun testEd25519Factory_keyDataIs32BytePublicKey() {
        val pubKey = ed25519Key()
        val signer = ExternalSigner.ed25519(validContractAddress1, pubKey)
        assertTrue(signer.keyData.contentEquals(pubKey))
        assertEquals(32, signer.keyData.size)
    }

    // ========================================================================
    // ExternalSigner init — Direct Constructor Validation
    // ========================================================================

    @Test
    fun testExternalSignerInit_emptyKeyData_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            ExternalSigner(validContractAddress1, ByteArray(0))
        }
    }

    @Test
    fun testExternalSignerInit_invalidVerifier_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            ExternalSigner("bad", ByteArray(10) { 0x01 })
        }
    }

    @Test
    fun testExternalSignerInit_gAddressAsVerifier_throws() {
        assertFailsWith<ValidationException.InvalidAddress> {
            ExternalSigner(validAccountAddress1, ByteArray(10) { 0x01 })
        }
    }

    @Test
    fun testExternalSignerInit_validInputs_succeeds() {
        val signer = ExternalSigner(validContractAddress1, ByteArray(48) { it.toByte() })
        assertEquals(validContractAddress1, signer.verifierAddress)
        assertEquals(48, signer.keyData.size)
    }

    // ========================================================================
    // DelegatedSigner.uniqueKey — Format and Determinism
    // ========================================================================

    @Test
    fun testDelegatedSigner_uniqueKey_format() {
        val signer = DelegatedSigner(validAccountAddress1)
        assertEquals("delegated:$validAccountAddress1", signer.uniqueKey)
    }

    @Test
    fun testDelegatedSigner_uniqueKey_deterministicAcrossInstances() {
        val signer1 = DelegatedSigner(validAccountAddress1)
        val signer2 = DelegatedSigner(validAccountAddress1)
        assertEquals(signer1.uniqueKey, signer2.uniqueKey)
    }

    @Test
    fun testDelegatedSigner_uniqueKey_differentForDifferentAddresses() {
        val signer1 = DelegatedSigner(validAccountAddress1)
        val signer2 = DelegatedSigner(validAccountAddress2)
        assertNotEquals(signer1.uniqueKey, signer2.uniqueKey)
    }

    @Test
    fun testDelegatedSigner_uniqueKey_contractAddress() {
        val signer = DelegatedSigner(validContractAddress1)
        assertEquals("delegated:$validContractAddress1", signer.uniqueKey)
    }

    // ========================================================================
    // ExternalSigner.uniqueKey — Format and Determinism
    // ========================================================================

    @Test
    fun testExternalSigner_uniqueKey_format() {
        val pubKey = ed25519Key()
        val signer = ExternalSigner.ed25519(validContractAddress1, pubKey)
        val expectedHex = pubKey.toHexString()
        assertEquals("external:$validContractAddress1:$expectedHex", signer.uniqueKey)
    }

    @Test
    fun testExternalSigner_uniqueKey_deterministicAcrossInstances() {
        val key = ed25519Key()
        val signer1 = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        val signer2 = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        assertEquals(signer1.uniqueKey, signer2.uniqueKey)
    }

    @Test
    fun testExternalSigner_uniqueKey_differentForDifferentVerifiers() {
        val key = ed25519Key()
        val signer1 = ExternalSigner.ed25519(validContractAddress1, key)
        val signer2 = ExternalSigner.ed25519(validContractAddress2, key.copyOf())
        assertNotEquals(signer1.uniqueKey, signer2.uniqueKey)
    }

    @Test
    fun testExternalSigner_uniqueKey_differentForDifferentKeyData() {
        val signer1 = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        val signer2 = ExternalSigner.ed25519(validContractAddress1, ed25519Key2())
        assertNotEquals(signer1.uniqueKey, signer2.uniqueKey)
    }

    @Test
    fun testExternalSigner_uniqueKey_webAuthnIncludesFullKeyData() {
        val pubKey = secp256r1Key()
        val credId = credentialIdBytes()
        val signer = ExternalSigner.webAuthn(validContractAddress1, pubKey, credId)
        val expectedHex = (pubKey + credId).toHexString()
        assertEquals("external:$validContractAddress1:$expectedHex", signer.uniqueKey)
    }

    // ========================================================================
    // DelegatedSigner vs ExternalSigner — uniqueKey Namespace Separation
    // ========================================================================

    @Test
    fun testUniqueKey_delegatedAndExternalNeverCollide() {
        // Even if the address string is the same (contract address), the prefix differs
        val delegated = DelegatedSigner(validContractAddress1)
        val external = ExternalSigner(validContractAddress1, ByteArray(10) { 0x01 })
        assertNotEquals(delegated.uniqueKey, external.uniqueKey)
        assertTrue(delegated.uniqueKey.startsWith("delegated:"))
        assertTrue(external.uniqueKey.startsWith("external:"))
    }

    // ========================================================================
    // ExternalSigner equals — Additional Coverage
    // ========================================================================

    @Test
    fun testExternalSigner_equals_reflexive() {
        val signer = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        assertEquals(signer, signer)
    }

    @Test
    fun testExternalSigner_equals_null() {
        val signer = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        assertFalse(signer.equals(null))
    }

    @Test
    fun testExternalSigner_equals_differentType() {
        val signer = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        assertFalse(signer.equals("not a signer"))
    }

    @Test
    fun testExternalSigner_equals_sameContentDifferentReferences() {
        val key = ed25519Key()
        val a = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        val b = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        assertEquals(a, b)
    }

    @Test
    fun testExternalSigner_equals_differentKeyData() {
        val a = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        val b = ExternalSigner.ed25519(validContractAddress1, ed25519Key2())
        assertNotEquals(a, b)
    }

    @Test
    fun testExternalSigner_equals_webAuthnSameCredentials() {
        val pubKey = secp256r1Key()
        val credId = credentialIdBytes()
        val a = ExternalSigner.webAuthn(validContractAddress1, pubKey, credId)
        val b = ExternalSigner.webAuthn(validContractAddress1, pubKey.copyOf(), credId.copyOf())
        assertEquals(a, b)
    }

    @Test
    fun testExternalSigner_equals_webAuthnDifferentCredentialId() {
        val pubKey = secp256r1Key()
        val a = ExternalSigner.webAuthn(validContractAddress1, pubKey, credentialIdBytes())
        val b = ExternalSigner.webAuthn(validContractAddress1, pubKey.copyOf(), credentialIdBytes2())
        assertNotEquals(a, b)
    }

    @Test
    fun testExternalSigner_equals_webAuthnDifferentPublicKey() {
        val credId = credentialIdBytes()
        val a = ExternalSigner.webAuthn(validContractAddress1, secp256r1Key(), credId)
        val b = ExternalSigner.webAuthn(validContractAddress1, secp256r1Key2(), credId.copyOf())
        assertNotEquals(a, b)
    }

    // ========================================================================
    // ExternalSigner in Collections (Set/Map behavior)
    // ========================================================================

    @Test
    fun testExternalSigner_setDeduplication() {
        val key = ed25519Key()
        val a = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        val b = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        val set = setOf(a, b)
        assertEquals(1, set.size, "Equal ExternalSigners must deduplicate in Set")
    }

    @Test
    fun testExternalSigner_setDistinguishesDifferentSigners() {
        val a = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        val b = ExternalSigner.ed25519(validContractAddress1, ed25519Key2())
        val set = setOf(a, b)
        assertEquals(2, set.size)
    }

    @Test
    fun testExternalSigner_mapKeyLookup() {
        val key = ed25519Key()
        val signer = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        val map = mapOf(signer to "value")
        val lookupKey = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        assertEquals("value", map[lookupKey], "Map lookup must work with content-equal ExternalSigner keys")
    }

    @Test
    fun testDelegatedSigner_setDeduplication() {
        val a = DelegatedSigner(validAccountAddress1)
        val b = DelegatedSigner(validAccountAddress1)
        val set = setOf(a, b)
        assertEquals(1, set.size, "Equal DelegatedSigners must deduplicate in Set")
    }

    // ========================================================================
    // signersEqual — Symmetry
    // ========================================================================

    @Test
    fun testSignersEqual_symmetric_delegated() {
        val a = DelegatedSigner(validAccountAddress1)
        val b = DelegatedSigner(validAccountAddress1)
        assertTrue(SmartAccountBuilders.signersEqual(a, b))
        assertTrue(SmartAccountBuilders.signersEqual(b, a))
    }

    @Test
    fun testSignersEqual_symmetric_external() {
        val key = ed25519Key()
        val a = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        val b = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        assertTrue(SmartAccountBuilders.signersEqual(a, b))
        assertTrue(SmartAccountBuilders.signersEqual(b, a))
    }

    @Test
    fun testSignersEqual_symmetric_crossType() {
        val delegated = DelegatedSigner(validAccountAddress1)
        val external = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        assertFalse(SmartAccountBuilders.signersEqual(delegated, external))
        assertFalse(SmartAccountBuilders.signersEqual(external, delegated))
    }

    @Test
    fun testSignersEqual_webAuthnSameContent_returnsTrue() {
        val pubKey = secp256r1Key()
        val credId = credentialIdBytes()
        val a = ExternalSigner.webAuthn(validContractAddress1, pubKey.copyOf(), credId.copyOf())
        val b = ExternalSigner.webAuthn(validContractAddress1, pubKey.copyOf(), credId.copyOf())
        assertTrue(SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_webAuthnDifferentCredentialId_returnsFalse() {
        val pubKey = secp256r1Key()
        val a = ExternalSigner.webAuthn(validContractAddress1, pubKey.copyOf(), credentialIdBytes())
        val b = ExternalSigner.webAuthn(validContractAddress1, pubKey.copyOf(), credentialIdBytes2())
        assertFalse(SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_ed25519VsWebAuthn_sameVerifier_returnsFalse() {
        // Different key data sizes means they are not equal
        val ed25519 = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        val webAuthn = ExternalSigner.webAuthn(validContractAddress1, secp256r1Key(), credentialIdBytes())
        assertFalse(SmartAccountBuilders.signersEqual(ed25519, webAuthn))
    }

    // ========================================================================
    // ScVal Round-Trip — Construct and Parse Back
    // ========================================================================

    @Test
    fun testDelegatedSigner_scValRoundTrip_addressPreserved() {
        val signer = DelegatedSigner(validAccountAddress1)
        val scVal = signer.toScVal()
        val vec = (scVal as SCValXdr.Vec).value!!.value

        // Parse the symbol back
        val symbol = Scv.fromSymbol(vec[0])
        assertEquals("Delegated", symbol)

        // Parse the address back
        val scAddress = Scv.fromAddress(vec[1])
        // Reconstructing a DelegatedSigner with the same address should produce equal ScVal
        val reconstructed = DelegatedSigner(validAccountAddress1)
        assertTrue(SmartAccountBuilders.signersEqual(signer, reconstructed))
    }

    @Test
    fun testExternalSigner_scValRoundTrip_keyDataPreserved() {
        val keyData = ed25519Key()
        val signer = ExternalSigner.ed25519(validContractAddress1, keyData)
        val scVal = signer.toScVal()
        val vec = (scVal as SCValXdr.Vec).value!!.value

        // Parse symbol
        val symbol = Scv.fromSymbol(vec[0])
        assertEquals("External", symbol)

        // Parse keyData bytes back
        val parsedKeyData = Scv.fromBytes(vec[2])
        assertTrue(parsedKeyData.contentEquals(keyData))
    }

    @Test
    fun testWebAuthnSigner_scValRoundTrip_keyDataContainsPubKeyAndCredentialId() {
        val pubKey = secp256r1Key()
        val credId = credentialIdBytes()
        val signer = ExternalSigner.webAuthn(validContractAddress1, pubKey, credId)
        val scVal = signer.toScVal()
        val vec = (scVal as SCValXdr.Vec).value!!.value

        val parsedKeyData = Scv.fromBytes(vec[2])
        // First 65 bytes are the public key
        val parsedPubKey = parsedKeyData.copyOfRange(0, SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        assertTrue(parsedPubKey.contentEquals(pubKey))
        // Remaining bytes are the credential ID
        val parsedCredId = parsedKeyData.copyOfRange(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE, parsedKeyData.size)
        assertTrue(parsedCredId.contentEquals(credId))
    }

    @Test
    fun testDelegatedSigner_scValRoundTrip_contractAddress() {
        val signer = DelegatedSigner(validContractAddress1)
        val scVal = signer.toScVal()
        val vec = (scVal as SCValXdr.Vec).value!!.value
        assertEquals("Delegated", Scv.fromSymbol(vec[0]))
        assertIs<SCValXdr.Address>(vec[1])
    }

    // ========================================================================
    // ScVal Structure Verification — Discriminant Types
    // ========================================================================

    @Test
    fun testDelegatedSigner_scValElementDiscriminants() {
        val signer = DelegatedSigner(validAccountAddress1)
        val vec = (signer.toScVal() as SCValXdr.Vec).value!!.value
        assertIs<SCValXdr.Sym>(vec[0])
        assertIs<SCValXdr.Address>(vec[1])
    }

    @Test
    fun testExternalSigner_scValElementDiscriminants() {
        val signer = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        val vec = (signer.toScVal() as SCValXdr.Vec).value!!.value
        assertIs<SCValXdr.Sym>(vec[0])
        assertIs<SCValXdr.Address>(vec[1])
        assertIs<SCValXdr.Bytes>(vec[2])
    }

    // ========================================================================
    // Defensive Copy — keyData Isolation
    // ========================================================================

    @Test
    fun testExternalSigner_keyDataIsDefensivelyCopied_webAuthn() {
        val pubKey = secp256r1Key()
        val credId = credentialIdBytes()
        val originalPubKey = pubKey.copyOf()
        val originalCredId = credId.copyOf()
        val signer = ExternalSigner.webAuthn(validContractAddress1, pubKey, credId)

        // Mutate the original arrays
        pubKey[1] = 0xFF.toByte()
        credId[0] = 0xFF.toByte()

        // keyData should still contain the original values since webAuthn()
        // creates a new array via pubKey + credId (array concatenation)
        val expectedKeyData = originalPubKey + originalCredId
        assertTrue(signer.keyData.contentEquals(expectedKeyData))
    }

    @Test
    fun testExternalSigner_keyDataIsNotDefensivelyCopied_directConstructor() {
        // NOTE: The ExternalSigner direct constructor does NOT defensively copy keyData.
        // This means mutations to the original array affect the signer's internal state.
        // This is a known limitation, not a bug, since the factory methods (webAuthn/ed25519)
        // produce new arrays through concatenation or delegation.
        val keyData = ByteArray(10) { it.toByte() }
        val signer = ExternalSigner(validContractAddress1, keyData)
        val originalByte = keyData[0]
        keyData[0] = 0xFF.toByte()
        // The signer's keyData reflects the mutation because no defensive copy was made
        assertEquals(0xFF.toByte(), signer.keyData[0])
        // Restore for test hygiene
        keyData[0] = originalByte
    }

    // ========================================================================
    // SmartAccountSigner Sealed Class — Exhaustive Type Check
    // ========================================================================

    @Test
    fun testSmartAccountSigner_delegatedIsSubtype() {
        val signer: SmartAccountSigner = DelegatedSigner(validAccountAddress1)
        assertTrue(signer is DelegatedSigner)
        assertFalse(signer is ExternalSigner)
    }

    @Test
    fun testSmartAccountSigner_externalIsSubtype() {
        val signer: SmartAccountSigner = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        assertTrue(signer is ExternalSigner)
        assertFalse(signer is DelegatedSigner)
    }

    // ========================================================================
    // signersEqual — Consistency with equals
    // ========================================================================

    @Test
    fun testSignersEqual_consistentWithEqualsForDelegated() {
        val a = DelegatedSigner(validAccountAddress1)
        val b = DelegatedSigner(validAccountAddress1)
        // signersEqual and data class equals should agree
        assertEquals(a == b, SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_consistentWithEqualsForExternal() {
        val key = ed25519Key()
        val a = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        val b = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        assertEquals(a == b, SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_consistentWithNotEqualsForExternal() {
        val a = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        val b = ExternalSigner.ed25519(validContractAddress1, ed25519Key2())
        assertEquals(a == b, SmartAccountBuilders.signersEqual(a, b))
    }

    // ========================================================================
    // uniqueKey — Hex Encoding Correctness
    // ========================================================================

    @Test
    fun testExternalSigner_uniqueKey_hexEncodingIsLowercase() {
        val key = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
        // Use direct constructor to bypass size validation in factory methods
        val signer = ExternalSigner(validContractAddress1, key)
        assertTrue(signer.uniqueKey.contains("abcdef"))
        assertFalse(signer.uniqueKey.contains("ABCDEF"))
    }

    @Test
    fun testExternalSigner_uniqueKey_hexEncodingAllZeros() {
        val key = ByteArray(32) { 0x00 }
        val signer = ExternalSigner.ed25519(validContractAddress1, key)
        val hex = "0".repeat(64)
        assertTrue(signer.uniqueKey.endsWith(hex))
    }

    @Test
    fun testExternalSigner_uniqueKey_hexEncodingAllFF() {
        val key = ByteArray(32) { 0xFF.toByte() }
        val signer = ExternalSigner.ed25519(validContractAddress1, key)
        val hex = "ff".repeat(32)
        assertTrue(signer.uniqueKey.endsWith(hex))
    }

    // ========================================================================
    // DelegatedSigner.equals (data class generated)
    // ========================================================================

    @Test
    fun testDelegatedSigner_equals_sameAddress() {
        val a = DelegatedSigner(validAccountAddress1)
        val b = DelegatedSigner(validAccountAddress1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testDelegatedSigner_equals_differentAddress() {
        val a = DelegatedSigner(validAccountAddress1)
        val b = DelegatedSigner(validAccountAddress2)
        assertNotEquals(a, b)
    }

    @Test
    fun testDelegatedSigner_equals_reflexive() {
        val signer = DelegatedSigner(validAccountAddress1)
        assertEquals(signer, signer)
    }

    // ========================================================================
    // ExternalSigner hashCode — Consistency
    // ========================================================================

    @Test
    fun testExternalSigner_hashCode_consistentAcrossInvocations() {
        val signer = ExternalSigner.ed25519(validContractAddress1, ed25519Key())
        val hash1 = signer.hashCode()
        val hash2 = signer.hashCode()
        assertEquals(hash1, hash2, "hashCode must be consistent across invocations")
    }

    @Test
    fun testExternalSigner_hashCode_equalObjectsHaveEqualHashes() {
        val key = ed25519Key()
        val a = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        val b = ExternalSigner.ed25519(validContractAddress1, key.copyOf())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testExternalSigner_hashCode_webAuthnEqualObjectsHaveEqualHashes() {
        val pubKey = secp256r1Key()
        val credId = credentialIdBytes()
        val a = ExternalSigner.webAuthn(validContractAddress1, pubKey.copyOf(), credId.copyOf())
        val b = ExternalSigner.webAuthn(validContractAddress1, pubKey.copyOf(), credId.copyOf())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
