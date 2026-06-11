//
//  SmartAccountBuildersTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [SmartAccountBuilders] utility functions.
 *
 * Covers signer key derivation, deduplication, type description, equality,
 * credential/address matching, type predicates, and address parsing.
 * All tests are pure in-memory with no network dependencies.
 */
class SmartAccountBuildersTest {

    // ========================================================================
    // Test Fixtures
    // ========================================================================

    private val validAccountAddress1 = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
    private val validAccountAddress2 = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
    private val validContractAddress1 = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val validContractAddress2 = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"

    /** 65-byte uncompressed secp256r1 public key (0x04 prefix). */
    private fun secp256r1PublicKey(): ByteArray {
        val key = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        key[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until key.size) key[i] = (i % 256).toByte()
        return key
    }

    /** 32-byte Ed25519 public key. */
    private fun ed25519PublicKey(): ByteArray = ByteArray(32) { (it + 1).toByte() }

    /** Arbitrary credential ID bytes. */
    private fun credentialIdBytes(): ByteArray = ByteArray(32) { (it + 100).toByte() }

    private fun delegatedSigner(address: String = validAccountAddress1) = DelegatedSigner(address)

    private fun webAuthnSigner(
        verifierAddress: String = validContractAddress1,
        publicKey: ByteArray = secp256r1PublicKey(),
        credentialId: ByteArray = credentialIdBytes()
    ) = ExternalSigner.webAuthn(verifierAddress, publicKey, credentialId)

    private fun ed25519Signer(
        verifierAddress: String = validContractAddress1,
        publicKey: ByteArray = ed25519PublicKey()
    ) = ExternalSigner.ed25519(verifierAddress, publicKey)

    // ========================================================================
    // getSignerKey
    // ========================================================================

    @Test
    fun testGetSignerKey_delegatedSigner_returnsUniqueKeyWithDelegatedPrefix() {
        val signer = delegatedSigner()
        val key = SmartAccountBuilders.getSignerKey(signer)
        assertEquals("delegated:$validAccountAddress1", key)
    }

    @Test
    fun testGetSignerKey_externalSigner_returnsUniqueKeyWithExternalPrefixAndHexKeyData() {
        val pubKey = ed25519PublicKey()
        val signer = ed25519Signer(publicKey = pubKey)
        val key = SmartAccountBuilders.getSignerKey(signer)
        val expectedHex = pubKey.toHexString()
        assertEquals("external:$validContractAddress1:$expectedHex", key)
    }

    @Test
    fun testGetSignerKey_matchesDelegatedSignerUniqueKey() {
        val signer = delegatedSigner()
        assertEquals(signer.uniqueKey, SmartAccountBuilders.getSignerKey(signer))
    }

    @Test
    fun testGetSignerKey_matchesExternalSignerUniqueKey() {
        val signer = webAuthnSigner()
        assertEquals(signer.uniqueKey, SmartAccountBuilders.getSignerKey(signer))
    }

    // ========================================================================
    // collectUniqueSigners
    // ========================================================================

    @Test
    fun testCollectUniqueSigners_emptyList_returnsEmpty() {
        val result = SmartAccountBuilders.collectUniqueSigners(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testCollectUniqueSigners_singleSigner_returnsSameSigner() {
        val signer = delegatedSigner()
        val result = SmartAccountBuilders.collectUniqueSigners(listOf(signer))
        assertEquals(1, result.size)
        assertEquals(signer, result[0])
    }

    @Test
    fun testCollectUniqueSigners_duplicateDelegatedSigners_deduplicatesToOne() {
        val signer1 = DelegatedSigner(validAccountAddress1)
        val signer2 = DelegatedSigner(validAccountAddress1)
        val result = SmartAccountBuilders.collectUniqueSigners(listOf(signer1, signer2))
        assertEquals(1, result.size)
        assertEquals(validAccountAddress1, (result[0] as DelegatedSigner).address)
    }

    @Test
    fun testCollectUniqueSigners_duplicateExternalSigners_deduplicatesToOne() {
        val signer1 = ed25519Signer()
        val signer2 = ed25519Signer()
        val result = SmartAccountBuilders.collectUniqueSigners(listOf(signer1, signer2))
        assertEquals(1, result.size)
    }

    @Test
    fun testCollectUniqueSigners_differentSigners_allKept() {
        val s1 = delegatedSigner(validAccountAddress1)
        val s2 = delegatedSigner(validAccountAddress2)
        val s3 = ed25519Signer()
        val result = SmartAccountBuilders.collectUniqueSigners(listOf(s1, s2, s3))
        assertEquals(3, result.size)
    }

    @Test
    fun testCollectUniqueSigners_preservesOrderFirstOccurrence() {
        val first = DelegatedSigner(validAccountAddress1)
        val duplicate = DelegatedSigner(validAccountAddress1)
        val second = DelegatedSigner(validAccountAddress2)
        val result = SmartAccountBuilders.collectUniqueSigners(listOf(first, duplicate, second))
        assertEquals(2, result.size)
        assertEquals(validAccountAddress1, (result[0] as DelegatedSigner).address)
        assertEquals(validAccountAddress2, (result[1] as DelegatedSigner).address)
    }

    @Test
    fun testCollectUniqueSigners_mixedDuplicates_preservesInsertionOrder() {
        val s1 = delegatedSigner(validAccountAddress1)
        val s2 = ed25519Signer(verifierAddress = validContractAddress1)
        val s3 = delegatedSigner(validAccountAddress1) // duplicate of s1
        val s4 = webAuthnSigner(verifierAddress = validContractAddress2)
        val result = SmartAccountBuilders.collectUniqueSigners(listOf(s1, s2, s3, s4))
        assertEquals(3, result.size)
        assertEquals(s1.uniqueKey, result[0].uniqueKey)
        assertEquals(s2.uniqueKey, result[1].uniqueKey)
        assertEquals(s4.uniqueKey, result[2].uniqueKey)
    }

    // ========================================================================
    // describeSignerType
    // ========================================================================

    @Suppress("DEPRECATION")
    @Test
    fun testDescribeSignerType_delegatedSigner_returnsStellarAccount() {
        val signer = delegatedSigner()
        assertEquals("Stellar Account", SmartAccountBuilders.describeSignerType(signer))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testDescribeSignerType_webAuthnSigner_returnsPasskeyWebAuthn() {
        val signer = webAuthnSigner()
        // WebAuthn key data is 65 (pubkey) + credentialId bytes > 65 bytes total
        assertTrue(signer.keyData.size > SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        assertEquals("Passkey (WebAuthn)", SmartAccountBuilders.describeSignerType(signer))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testDescribeSignerType_ed25519Signer_returnsEd25519() {
        val signer = ed25519Signer()
        assertEquals(32, signer.keyData.size)
        assertEquals("Ed25519", SmartAccountBuilders.describeSignerType(signer))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testDescribeSignerType_externalSignerWithOtherKeySize_returnsExternalVerifier() {
        // Key data that is neither >65 bytes nor 32 bytes (e.g. 48 bytes)
        val oddKeyData = ByteArray(48) { it.toByte() }
        val signer = ExternalSigner(validContractAddress1, oddKeyData)
        assertEquals("External Verifier", SmartAccountBuilders.describeSignerType(signer))
    }

    // ========================================================================
    // signersEqual
    // ========================================================================

    @Test
    fun testSignersEqual_sameDelegatedSigner_returnsTrue() {
        val a = DelegatedSigner(validAccountAddress1)
        val b = DelegatedSigner(validAccountAddress1)
        assertTrue(SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_differentDelegatedSigners_returnsFalse() {
        val a = DelegatedSigner(validAccountAddress1)
        val b = DelegatedSigner(validAccountAddress2)
        assertFalse(SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_sameExternalSigner_returnsTrue() {
        val pubKey = ed25519PublicKey()
        val a = ExternalSigner.ed25519(validContractAddress1, pubKey)
        val b = ExternalSigner.ed25519(validContractAddress1, pubKey.copyOf())
        assertTrue(SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_externalSignersDifferentVerifier_returnsFalse() {
        val pubKey = ed25519PublicKey()
        val a = ExternalSigner.ed25519(validContractAddress1, pubKey)
        val b = ExternalSigner.ed25519(validContractAddress2, pubKey.copyOf())
        assertFalse(SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_externalSignersDifferentKeyData_returnsFalse() {
        val keyA = ByteArray(32) { 0x01 }
        val keyB = ByteArray(32) { 0x02 }
        val a = ExternalSigner.ed25519(validContractAddress1, keyA)
        val b = ExternalSigner.ed25519(validContractAddress1, keyB)
        assertFalse(SmartAccountBuilders.signersEqual(a, b))
    }

    @Test
    fun testSignersEqual_delegatedVsExternal_returnsFalse() {
        val delegated = delegatedSigner()
        val external = ed25519Signer()
        assertFalse(SmartAccountBuilders.signersEqual(delegated, external))
    }

    @Test
    fun testSignersEqual_externalVsDelegated_returnsFalse() {
        val external = ed25519Signer()
        val delegated = delegatedSigner()
        assertFalse(SmartAccountBuilders.signersEqual(external, delegated))
    }

    // ========================================================================
    // signerMatchesCredentialId
    // ========================================================================

    @Test
    fun testSignerMatchesCredentialId_matchingCredentialId_returnsTrue() {
        val credBytes = credentialIdBytes()
        val credentialIdStr = Util.base64urlEncode(credBytes)
        val signer = webAuthnSigner(credentialId = credBytes)
        assertTrue(SmartAccountBuilders.signerMatchesCredentialId(signer, credentialIdStr))
    }

    @Test
    fun testSignerMatchesCredentialId_nonMatchingCredentialId_returnsFalse() {
        val credBytes = credentialIdBytes()
        val otherCredBytes = ByteArray(32) { 0xFF.toByte() }
        val credentialIdStr = Util.base64urlEncode(otherCredBytes)
        val signer = webAuthnSigner(credentialId = credBytes)
        assertFalse(SmartAccountBuilders.signerMatchesCredentialId(signer, credentialIdStr))
    }

    @Test
    fun testSignerMatchesCredentialId_delegatedSigner_returnsFalse() {
        val signer = delegatedSigner()
        val credentialIdStr = Util.base64urlEncode(credentialIdBytes())
        assertFalse(SmartAccountBuilders.signerMatchesCredentialId(signer, credentialIdStr))
    }

    @Test
    fun testSignerMatchesCredentialId_ed25519ExternalSigner_returnsFalse() {
        // Ed25519 signers have 32-byte key data; no credential ID portion
        val signer = ed25519Signer()
        val credentialIdStr = Util.base64urlEncode(credentialIdBytes())
        assertFalse(SmartAccountBuilders.signerMatchesCredentialId(signer, credentialIdStr))
    }

    @Test
    fun testSignerMatchesCredentialId_emptyCredentialIdString_returnsFalse() {
        val signer = webAuthnSigner()
        // An empty string cannot match a non-empty encoded credential ID
        assertFalse(SmartAccountBuilders.signerMatchesCredentialId(signer, ""))
    }

    // ========================================================================
    // signerMatchesAddress
    // ========================================================================

    @Test
    fun testSignerMatchesAddress_delegatedSignerMatchingAddress_returnsTrue() {
        val signer = DelegatedSigner(validAccountAddress1)
        assertTrue(SmartAccountBuilders.signerMatchesAddress(signer, validAccountAddress1))
    }

    @Test
    fun testSignerMatchesAddress_delegatedSignerDifferentAddress_returnsFalse() {
        val signer = DelegatedSigner(validAccountAddress1)
        assertFalse(SmartAccountBuilders.signerMatchesAddress(signer, validAccountAddress2))
    }

    @Test
    fun testSignerMatchesAddress_externalSigner_returnsFalse() {
        // signerMatchesAddress only matches DelegatedSigners; ExternalSigner always returns false
        val signer = ed25519Signer(verifierAddress = validContractAddress1)
        assertFalse(SmartAccountBuilders.signerMatchesAddress(signer, validContractAddress1))
    }

    @Test
    fun testSignerMatchesAddress_webAuthnSigner_returnsFalse() {
        val signer = webAuthnSigner(verifierAddress = validContractAddress1)
        assertFalse(SmartAccountBuilders.signerMatchesAddress(signer, validContractAddress1))
    }

    // ========================================================================
    // isDelegatedSigner / isExternalSigner
    // ========================================================================

    @Test
    fun testIsDelegatedSigner_withDelegatedSigner_returnsTrue() {
        val signer = delegatedSigner()
        assertTrue(SmartAccountBuilders.isDelegatedSigner(signer))
    }

    @Test
    fun testIsDelegatedSigner_withExternalSigner_returnsFalse() {
        val signer = ed25519Signer()
        assertFalse(SmartAccountBuilders.isDelegatedSigner(signer))
    }

    @Test
    fun testIsExternalSigner_withExternalSigner_returnsTrue() {
        val signer = ed25519Signer()
        assertTrue(SmartAccountBuilders.isExternalSigner(signer))
    }

    @Test
    fun testIsExternalSigner_withDelegatedSigner_returnsFalse() {
        val signer = delegatedSigner()
        assertFalse(SmartAccountBuilders.isExternalSigner(signer))
    }

    @Test
    fun testIsDelegatedSigner_webAuthnSigner_returnsFalse() {
        val signer = webAuthnSigner()
        assertFalse(SmartAccountBuilders.isDelegatedSigner(signer))
    }

    @Test
    fun testIsExternalSigner_webAuthnSigner_returnsTrue() {
        val signer = webAuthnSigner()
        assertTrue(SmartAccountBuilders.isExternalSigner(signer))
    }

    // ========================================================================
    // signerMatchesCredential (ByteArray overload)
    // ========================================================================

    @Test
    fun testSignerMatchesCredential_matchingCredentialBytes_returnsTrue() {
        val credBytes = credentialIdBytes()
        val signer = webAuthnSigner(credentialId = credBytes)
        assertTrue(SmartAccountBuilders.signerMatchesCredential(signer, credBytes))
    }

    @Test
    fun testSignerMatchesCredential_nonMatchingCredentialBytes_returnsFalse() {
        val credBytes = credentialIdBytes()
        val otherCredBytes = ByteArray(32) { 0xFF.toByte() }
        val signer = webAuthnSigner(credentialId = credBytes)
        assertFalse(SmartAccountBuilders.signerMatchesCredential(signer, otherCredBytes))
    }

    @Test
    fun testSignerMatchesCredential_delegatedSigner_returnsFalse() {
        val signer = delegatedSigner()
        assertFalse(SmartAccountBuilders.signerMatchesCredential(signer, credentialIdBytes()))
    }

    @Test
    fun testSignerMatchesCredential_ed25519ExternalSigner_returnsFalse() {
        // Ed25519 key data is 32 bytes; no credential ID portion exists
        val signer = ed25519Signer()
        assertFalse(SmartAccountBuilders.signerMatchesCredential(signer, credentialIdBytes()))
    }

    // ========================================================================
    // getCredentialIdFromSigner
    // ========================================================================

    @Test
    fun testGetCredentialIdFromSigner_delegatedSigner_returnsNull() {
        val signer = delegatedSigner()
        kotlin.test.assertNull(SmartAccountBuilders.getCredentialIdFromSigner(signer))
    }

    @Test
    fun testGetCredentialIdFromSigner_ed25519ExternalSigner_returnsNull() {
        // 32-byte key data is <= 65, so no credential ID
        val signer = ed25519Signer()
        kotlin.test.assertNull(SmartAccountBuilders.getCredentialIdFromSigner(signer))
    }

    @Test
    fun testGetCredentialIdFromSigner_exactly65ByteKeyData_returnsNull() {
        // Boundary: keyData.size == 65 satisfies <= 65, so credential ID is absent
        val signer = ExternalSigner(validContractAddress1, ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE))
        kotlin.test.assertNull(SmartAccountBuilders.getCredentialIdFromSigner(signer))
    }

    @Test
    fun testGetCredentialIdFromSigner_webAuthnSigner_returnsCredentialIdBytes() {
        val credBytes = credentialIdBytes()
        val signer = webAuthnSigner(credentialId = credBytes)
        val result = SmartAccountBuilders.getCredentialIdFromSigner(signer)
        kotlin.test.assertNotNull(result)
        assertTrue(result.contentEquals(credBytes))
    }

    @Test
    fun testGetCredentialIdFromSigner_webAuthnSigner_returnsOnlyCredentialIdPortion() {
        val pubKey = secp256r1PublicKey()
        val credBytes = credentialIdBytes()
        val signer = webAuthnSigner(publicKey = pubKey, credentialId = credBytes)
        val result = SmartAccountBuilders.getCredentialIdFromSigner(signer)
        kotlin.test.assertNotNull(result)
        assertEquals(credBytes.size, result.size)
        assertTrue(result.contentEquals(credBytes))
    }

    // ========================================================================
    // getPublicKeyFromSigner
    // ========================================================================

    @Test
    fun testGetPublicKeyFromSigner_delegatedSigner_returnsNull() {
        val signer = delegatedSigner()
        kotlin.test.assertNull(SmartAccountBuilders.getPublicKeyFromSigner(signer))
    }

    @Test
    fun testGetPublicKeyFromSigner_ed25519ExternalSigner_returnsNull() {
        // 32-byte key data is <= 65, so there is no public-key prefix to extract
        val signer = ed25519Signer()
        kotlin.test.assertNull(SmartAccountBuilders.getPublicKeyFromSigner(signer))
    }

    @Test
    fun testGetPublicKeyFromSigner_exactly65ByteKeyData_returnsNull() {
        // Boundary: keyData.size == 65 satisfies <= 65, so no credential ID follows
        // and the signer is not treated as a WebAuthn signer
        val signer = ExternalSigner(validContractAddress1, ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE))
        kotlin.test.assertNull(SmartAccountBuilders.getPublicKeyFromSigner(signer))
    }

    @Test
    fun testGetPublicKeyFromSigner_webAuthnSigner_returns65BytePublicKey() {
        val pubKey = secp256r1PublicKey()
        val signer = webAuthnSigner(publicKey = pubKey)
        val result = SmartAccountBuilders.getPublicKeyFromSigner(signer)
        kotlin.test.assertNotNull(result)
        assertEquals(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE, result.size)
        assertTrue(result.contentEquals(pubKey))
    }

    @Test
    fun testGetPublicKeyFromSigner_webAuthnSigner_returnsOnlyPublicKeyPortion() {
        val pubKey = secp256r1PublicKey()
        val credBytes = credentialIdBytes()
        val signer = webAuthnSigner(publicKey = pubKey, credentialId = credBytes)
        val result = SmartAccountBuilders.getPublicKeyFromSigner(signer)
        kotlin.test.assertNotNull(result)
        assertEquals(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE, result.size)
        assertTrue(result.contentEquals(pubKey))
    }

    @Test
    fun testGetPublicKeyFromSigner_complementsCredentialId() {
        // publicKey + credentialId from the two accessors reassemble the full keyData
        val pubKey = secp256r1PublicKey()
        val credBytes = credentialIdBytes()
        val signer = webAuthnSigner(publicKey = pubKey, credentialId = credBytes)
        val extractedKey = SmartAccountBuilders.getPublicKeyFromSigner(signer)
        val extractedCred = SmartAccountBuilders.getCredentialIdFromSigner(signer)
        kotlin.test.assertNotNull(extractedKey)
        kotlin.test.assertNotNull(extractedCred)
        assertTrue((extractedKey + extractedCred).contentEquals(pubKey + credBytes))
    }

    // ========================================================================
    // getCredentialIdStringFromSigner
    // ========================================================================

    @Test
    fun testGetCredentialIdStringFromSigner_delegatedSigner_returnsNull() {
        val signer = delegatedSigner()
        kotlin.test.assertNull(SmartAccountBuilders.getCredentialIdStringFromSigner(signer))
    }

    @Test
    fun testGetCredentialIdStringFromSigner_ed25519ExternalSigner_returnsNull() {
        val signer = ed25519Signer()
        kotlin.test.assertNull(SmartAccountBuilders.getCredentialIdStringFromSigner(signer))
    }

    @Test
    fun testGetCredentialIdStringFromSigner_exactly65ByteKeyData_returnsNull() {
        val signer = ExternalSigner(validContractAddress1, ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE))
        kotlin.test.assertNull(SmartAccountBuilders.getCredentialIdStringFromSigner(signer))
    }

    @Test
    fun testGetCredentialIdStringFromSigner_webAuthnSigner_returnsBase64urlEncodedString() {
        val credBytes = credentialIdBytes()
        val signer = webAuthnSigner(credentialId = credBytes)
        val result = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
        kotlin.test.assertNotNull(result)
        assertEquals(Util.base64urlEncode(credBytes), result)
    }

    @Test
    fun testGetCredentialIdStringFromSigner_webAuthnSigner_roundTripsWithMatchFunction() {
        val credBytes = credentialIdBytes()
        val signer = webAuthnSigner(credentialId = credBytes)
        val credentialIdStr = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
        kotlin.test.assertNotNull(credentialIdStr)
        assertTrue(SmartAccountBuilders.signerMatchesCredentialId(signer, credentialIdStr))
    }

    // ========================================================================
    // describeSignerType — 65-byte boundary
    // ========================================================================

    @Suppress("DEPRECATION")
    @Test
    fun testDescribeSignerType_exactly65ByteKeyData_returnsExternalVerifier() {
        // keyData.size == 65: the check is > 65, so this does NOT match WebAuthn.
        // Also not 32 bytes, so falls through to "External Verifier".
        val signer = ExternalSigner(validContractAddress1, ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE))
        assertEquals("External Verifier", SmartAccountBuilders.describeSignerType(signer))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testDescribeSignerType_66ByteKeyData_returnsPasskeyWebAuthn() {
        // One byte above the boundary: size > 65, so classified as WebAuthn.
        val signer = ExternalSigner(validContractAddress1, ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE + 1))
        assertEquals("Passkey (WebAuthn)", SmartAccountBuilders.describeSignerType(signer))
    }
}
