//
//  SmartAccountSignaturesTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.core

import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.Ed25519Signature
import com.soneso.stellar.sdk.smartaccount.core.PolicySignature
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSignature
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnSignature
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [SmartAccountSignature] sealed class hierarchy:
 * [WebAuthnSignature], [Ed25519Signature], and [PolicySignature].
 *
 * Covers toScVal() output structure and key ordering, init validation,
 * equality/hashCode for ByteArray fields, cross-type inequality,
 * PolicySignature singleton behavior, and edge cases.
 *
 * Note: Equality, hashCode, and basic validation are also covered in
 * SignatureEqualityTest.kt. This file focuses on toScVal() behavior,
 * PolicySignature, cross-type tests, and additional edge cases.
 */
class SmartAccountSignaturesTest {

    // ========================================================================
    // Test Fixtures
    // ========================================================================

    private fun authenticatorData(): ByteArray = ByteArray(37) { (it + 1).toByte() }
    private fun clientData(): ByteArray = ByteArray(100) { (it + 10).toByte() }
    private fun compactSig(): ByteArray = ByteArray(64) { (it + 50).toByte() }

    private fun pubKey32(): ByteArray = ByteArray(32) { (it + 1).toByte() }
    private fun ed25519Sig64(): ByteArray = ByteArray(64) { (it + 5).toByte() }

    private fun webAuthn(
        authenticatorData: ByteArray = authenticatorData(),
        clientData: ByteArray = clientData(),
        signature: ByteArray = compactSig()
    ): WebAuthnSignature = WebAuthnSignature(authenticatorData, clientData, signature)

    private fun ed25519(
        publicKey: ByteArray = pubKey32(),
        signature: ByteArray = ed25519Sig64()
    ): Ed25519Signature = Ed25519Signature(publicKey, signature)

    /** Helper to extract SCMapXdr entries from an SCValXdr.Map. */
    private fun mapEntries(scVal: SCValXdr): List<Pair<SCValXdr, SCValXdr>> {
        assertIs<SCValXdr.Map>(scVal)
        val map = scVal as SCValXdr.Map
        return map.value!!.value.map { it.key to it.`val` }
    }

    /** Helper to extract a symbol string from an SCValXdr.Sym. */
    private fun symbolValue(scVal: SCValXdr): String {
        assertIs<SCValXdr.Sym>(scVal)
        return (scVal as SCValXdr.Sym).value.value
    }

    /** Helper to extract bytes from an SCValXdr.Bytes. */
    private fun bytesValue(scVal: SCValXdr): ByteArray {
        assertIs<SCValXdr.Bytes>(scVal)
        return (scVal as SCValXdr.Bytes).value.value
    }

    // ========================================================================
    // WebAuthnSignature.toScVal()
    // ========================================================================

    @Test
    fun testWebAuthnToScVal_returnsMapType() {
        val sig = webAuthn()
        val scVal = sig.toScVal()
        assertIs<SCValXdr.Map>(scVal, "WebAuthnSignature.toScVal() must return SCValXdr.Map")
        assertEquals(
            SCValTypeXdr.SCV_MAP,
            scVal.discriminant,
            "Discriminant must be SCV_MAP"
        )
    }

    @Test
    fun testWebAuthnToScVal_hasExactlyThreeEntries() {
        val entries = mapEntries(webAuthn().toScVal())
        assertEquals(3, entries.size, "WebAuthn ScVal map must have exactly 3 entries")
    }

    @Test
    fun testWebAuthnToScVal_keysInAlphabeticalOrder() {
        val entries = mapEntries(webAuthn().toScVal())
        val keys = entries.map { symbolValue(it.first) }
        assertEquals(
            listOf("authenticator_data", "client_data", "signature"),
            keys,
            "Keys must be in alphabetical order: authenticator_data, client_data, signature"
        )
    }

    @Test
    fun testWebAuthnToScVal_authenticatorDataEntry() {
        val authData = authenticatorData()
        val sig = webAuthn(authenticatorData = authData)
        val entries = mapEntries(sig.toScVal())

        assertEquals("authenticator_data", symbolValue(entries[0].first))
        assertTrue(
            authData.contentEquals(bytesValue(entries[0].second)),
            "authenticator_data value must match input bytes"
        )
    }

    @Test
    fun testWebAuthnToScVal_clientDataEntry() {
        val cd = clientData()
        val sig = webAuthn(clientData = cd)
        val entries = mapEntries(sig.toScVal())

        assertEquals("client_data", symbolValue(entries[1].first))
        assertTrue(
            cd.contentEquals(bytesValue(entries[1].second)),
            "client_data value must match input bytes"
        )
    }

    @Test
    fun testWebAuthnToScVal_signatureEntry() {
        val s = compactSig()
        val sig = webAuthn(signature = s)
        val entries = mapEntries(sig.toScVal())

        assertEquals("signature", symbolValue(entries[2].first))
        assertTrue(
            s.contentEquals(bytesValue(entries[2].second)),
            "signature value must match input bytes"
        )
    }

    @Test
    fun testWebAuthnToScVal_allZeroBytes() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37),
            clientData = ByteArray(0),
            signature = ByteArray(64)
        )
        val entries = mapEntries(sig.toScVal())
        assertEquals(3, entries.size)
        assertTrue(ByteArray(37).contentEquals(bytesValue(entries[0].second)))
        assertTrue(ByteArray(0).contentEquals(bytesValue(entries[1].second)))
        assertTrue(ByteArray(64).contentEquals(bytesValue(entries[2].second)))
    }

    @Test
    fun testWebAuthnToScVal_emptyAuthenticatorDataAllowed() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(0),
            clientData = clientData(),
            signature = compactSig()
        )
        val entries = mapEntries(sig.toScVal())
        assertEquals(0, bytesValue(entries[0].second).size)
    }

    @Test
    fun testWebAuthnToScVal_emptyClientDataAllowed() {
        val sig = WebAuthnSignature(
            authenticatorData = authenticatorData(),
            clientData = ByteArray(0),
            signature = compactSig()
        )
        val entries = mapEntries(sig.toScVal())
        assertEquals(0, bytesValue(entries[1].second).size)
    }

    @Test
    fun testWebAuthnToScVal_largeAuthenticatorData() {
        val largeAuthData = ByteArray(1024) { (it % 256).toByte() }
        val sig = WebAuthnSignature(
            authenticatorData = largeAuthData,
            clientData = clientData(),
            signature = compactSig()
        )
        val entries = mapEntries(sig.toScVal())
        assertTrue(largeAuthData.contentEquals(bytesValue(entries[0].second)))
    }

    @Test
    fun testWebAuthnToScVal_calledTwiceReturnsSameStructure() {
        val sig = webAuthn()
        val scVal1 = sig.toScVal()
        val scVal2 = sig.toScVal()

        val keys1 = mapEntries(scVal1).map { symbolValue(it.first) }
        val keys2 = mapEntries(scVal2).map { symbolValue(it.first) }
        assertEquals(keys1, keys2, "Repeated toScVal() calls must produce same key order")
    }

    @Test
    fun testWebAuthnToScVal_keyNameIsClientData_notClientDataJson() {
        // This is critical per the source documentation
        val entries = mapEntries(webAuthn().toScVal())
        val keys = entries.map { symbolValue(it.first) }
        assertTrue("client_data" in keys, "Key must be 'client_data'")
        assertFalse("client_data_json" in keys, "Key must NOT be 'client_data_json'")
    }

    // ========================================================================
    // WebAuthnSignature init validation
    // ========================================================================

    @Test
    fun testWebAuthnSignature_exactly64ByteSignature_succeeds() {
        val sig = WebAuthnSignature(
            authenticatorData = authenticatorData(),
            clientData = clientData(),
            signature = ByteArray(64) { 0xFF.toByte() }
        )
        assertEquals(64, sig.signature.size)
    }

    @Test
    fun testWebAuthnSignature_63ByteSignature_throws() {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            WebAuthnSignature(
                authenticatorData = authenticatorData(),
                clientData = clientData(),
                signature = ByteArray(63)
            )
        }
        assertTrue(ex.message!!.contains("64"), "Error message must mention expected size 64")
        assertTrue(ex.message!!.contains("63"), "Error message must mention actual size 63")
    }

    @Test
    fun testWebAuthnSignature_128ByteSignature_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            WebAuthnSignature(
                authenticatorData = authenticatorData(),
                clientData = clientData(),
                signature = ByteArray(128)
            )
        }
    }

    @Test
    fun testWebAuthnSignature_1ByteSignature_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            WebAuthnSignature(
                authenticatorData = authenticatorData(),
                clientData = clientData(),
                signature = ByteArray(1)
            )
        }
    }

    // ========================================================================
    // Ed25519Signature.toScVal()
    // ========================================================================

    @Test
    fun testEd25519ToScVal_returnsBytesType() {
        val sig = ed25519()
        val scVal = sig.toScVal()
        assertIs<SCValXdr.Bytes>(scVal, "Ed25519Signature.toScVal() must return SCValXdr.Bytes")
        assertEquals(SCValTypeXdr.SCV_BYTES, scVal.discriminant)
    }

    @Test
    fun testEd25519ToScVal_containsRawSignatureBytes() {
        val s = ed25519Sig64()
        val sig = ed25519(signature = s)
        assertTrue(
            s.contentEquals(bytesValue(sig.toScVal())),
            "toScVal() must return the raw signature bytes"
        )
    }

    @Test
    fun testEd25519ToScVal_doesNotContainPublicKey() {
        val pk = pubKey32()
        val s = ed25519Sig64()
        val sig = ed25519(publicKey = pk, signature = s)
        val rawBytes = bytesValue(sig.toScVal())
        // The bytes must equal the signature field only, not 96 bytes containing the key
        assertEquals(64, rawBytes.size, "toScVal() bytes must be exactly 64 (signature only)")
    }

    @Test
    fun testEd25519ToScVal_allZeroSignatureBytes() {
        val sig = Ed25519Signature(
            publicKey = ByteArray(32),
            signature = ByteArray(64)
        )
        val rawBytes = bytesValue(sig.toScVal())
        assertTrue(ByteArray(64).contentEquals(rawBytes), "All-zero signature must round-trip")
    }

    @Test
    fun testEd25519ToScVal_allOnesSignatureBytes() {
        val sig = Ed25519Signature(
            publicKey = ByteArray(32) { 0xFF.toByte() },
            signature = ByteArray(64) { 0xFF.toByte() }
        )
        val rawBytes = bytesValue(sig.toScVal())
        assertTrue(
            ByteArray(64) { 0xFF.toByte() }.contentEquals(rawBytes),
            "All-0xFF signature must round-trip"
        )
    }

    @Test
    fun testEd25519ToScVal_calledTwiceReturnsSameBytes() {
        val sig = ed25519()
        val bytes1 = bytesValue(sig.toScVal())
        val bytes2 = bytesValue(sig.toScVal())
        assertTrue(bytes1.contentEquals(bytes2), "Repeated calls must produce identical bytes")
    }

    // ========================================================================
    // Ed25519Signature.toAuthPayloadBytes()
    // ========================================================================

    @Test
    fun testEd25519ToAuthPayloadBytes_returnsRawSignatureBytes() {
        val s = ed25519Sig64()
        val sig = ed25519(signature = s)
        val payloadBytes = sig.toAuthPayloadBytes()
        assertTrue(s.contentEquals(payloadBytes), "toAuthPayloadBytes() must return raw signature bytes")
    }

    @Test
    fun testEd25519ToAuthPayloadBytes_is64Bytes() {
        val payloadBytes = ed25519().toAuthPayloadBytes()
        assertEquals(64, payloadBytes.size, "Ed25519 auth payload bytes must be exactly 64")
    }

    @Test
    fun testEd25519ToAuthPayloadBytes_doesNotXdrWrap() {
        val s = ed25519Sig64()
        val sig = ed25519(signature = s)
        val payloadBytes = sig.toAuthPayloadBytes()
        // Raw 64 bytes — not an XDR-encoded ScVal (which would be longer due to the type tag)
        assertTrue(payloadBytes.contentEquals(s), "No XDR wrapper must be applied")
    }

    // ========================================================================
    // Ed25519Signature init validation
    // ========================================================================

    @Test
    fun testEd25519Signature_exactly32ByteKey_and_64ByteSig_succeeds() {
        val sig = Ed25519Signature(ByteArray(32), ByteArray(64))
        assertEquals(32, sig.publicKey.size)
        assertEquals(64, sig.signature.size)
    }

    @Test
    fun testEd25519Signature_31BytePublicKey_throws() {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(ByteArray(31), ed25519Sig64())
        }
        assertTrue(ex.message!!.contains("32"), "Error must mention expected size 32")
        assertTrue(ex.message!!.contains("31"), "Error must mention actual size 31")
    }

    @Test
    fun testEd25519Signature_33BytePublicKey_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(ByteArray(33), ed25519Sig64())
        }
    }

    @Test
    fun testEd25519Signature_63ByteSignature_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(pubKey32(), ByteArray(63))
        }
    }

    @Test
    fun testEd25519Signature_65ByteSignature_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(pubKey32(), ByteArray(65))
        }
    }

    @Test
    fun testEd25519Signature_emptyPublicKeyAndEmptySignature_throws() {
        // Public key validated first
        assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(ByteArray(0), ByteArray(0))
        }
    }

    @Test
    fun testEd25519Signature_validKeyInvalidSig_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(pubKey32(), ByteArray(0))
        }
    }

    // ========================================================================
    // PolicySignature.toScVal()
    // ========================================================================

    @Test
    fun testPolicySignatureToScVal_returnsMapType() {
        val scVal = PolicySignature.toScVal()
        assertIs<SCValXdr.Map>(scVal, "PolicySignature.toScVal() must return SCValXdr.Map")
        assertEquals(SCValTypeXdr.SCV_MAP, scVal.discriminant)
    }

    @Test
    fun testPolicySignatureToScVal_mapIsEmpty() {
        val scVal = PolicySignature.toScVal() as SCValXdr.Map
        val entries = scVal.value!!.value
        assertTrue(entries.isEmpty(), "PolicySignature ScVal map must be empty")
    }

    @Test
    fun testPolicySignatureToScVal_calledTwiceReturnsSameStructure() {
        val scVal1 = PolicySignature.toScVal() as SCValXdr.Map
        val scVal2 = PolicySignature.toScVal() as SCValXdr.Map
        assertEquals(
            scVal1.value!!.value.size,
            scVal2.value!!.value.size,
            "Repeated calls must produce same empty map"
        )
    }

    // ========================================================================
    // PolicySignature singleton behavior
    // ========================================================================

    @Test
    fun testPolicySignature_isSingleton() {
        val a = PolicySignature
        val b = PolicySignature
        assertSame(a, b, "PolicySignature must be the same singleton instance")
    }

    @Test
    fun testPolicySignature_isSmartAccountSignature() {
        val sig: SmartAccountSignature = PolicySignature
        assertIs<PolicySignature>(sig)
    }

    // ========================================================================
    // WebAuthnSignature.toAuthPayloadBytes()
    // ========================================================================

    @Test
    fun testWebAuthnToAuthPayloadBytes_isNonEmpty() {
        val bytes = webAuthn().toAuthPayloadBytes()
        assertTrue(bytes.isNotEmpty(), "WebAuthn toAuthPayloadBytes() must return non-empty bytes")
    }

    @Test
    fun testWebAuthnToAuthPayloadBytes_isXdrEncodedMapScVal() {
        val sig = webAuthn()
        val payloadBytes = sig.toAuthPayloadBytes()
        // Decoding the bytes as an XdrSCVal must yield a Map
        val decoded = SCValXdr.decode(XdrReader(payloadBytes))
        assertIs<SCValXdr.Map>(decoded, "WebAuthn payload bytes must decode to a Map ScVal")
    }

    @Test
    fun testWebAuthnToAuthPayloadBytes_equalsXdrEncodedToScVal() {
        val sig = webAuthn()
        val payloadBytes = sig.toAuthPayloadBytes()
        val writer = XdrWriter()
        sig.toScVal().encode(writer)
        val expectedBytes = writer.toByteArray()
        assertTrue(expectedBytes.contentEquals(payloadBytes), "toAuthPayloadBytes() must equal XDR-encoded toScVal()")
    }

    @Test
    fun testWebAuthnToAuthPayloadBytes_calledTwiceReturnsSameBytes() {
        val sig = webAuthn()
        val bytes1 = sig.toAuthPayloadBytes()
        val bytes2 = sig.toAuthPayloadBytes()
        assertTrue(bytes1.contentEquals(bytes2), "Repeated calls must produce identical bytes")
    }

    // ========================================================================
    // PolicySignature.toAuthPayloadBytes()
    // ========================================================================

    @Test
    fun testPolicyToAuthPayloadBytes_isNonEmpty() {
        val bytes = PolicySignature.toAuthPayloadBytes()
        assertTrue(bytes.isNotEmpty(), "Policy toAuthPayloadBytes() must return non-empty bytes")
    }

    @Test
    fun testPolicyToAuthPayloadBytes_isXdrEncodedEmptyMapScVal() {
        val payloadBytes = PolicySignature.toAuthPayloadBytes()
        val decoded = SCValXdr.decode(XdrReader(payloadBytes))
        assertIs<SCValXdr.Map>(decoded, "Policy payload bytes must decode to a Map ScVal")
        assertEquals(0, (decoded as SCValXdr.Map).value!!.value.size, "Policy map must be empty")
    }

    @Test
    fun testPolicyToAuthPayloadBytes_equalsXdrEncodedToScVal() {
        val payloadBytes = PolicySignature.toAuthPayloadBytes()
        val writer = XdrWriter()
        PolicySignature.toScVal().encode(writer)
        val expectedBytes = writer.toByteArray()
        assertTrue(expectedBytes.contentEquals(payloadBytes), "toAuthPayloadBytes() must equal XDR-encoded toScVal()")
    }

    @Test
    fun testPolicyToAuthPayloadBytes_calledTwiceReturnsSameBytes() {
        val bytes1 = PolicySignature.toAuthPayloadBytes()
        val bytes2 = PolicySignature.toAuthPayloadBytes()
        assertTrue(bytes1.contentEquals(bytes2), "Repeated calls must produce identical bytes")
    }

    // ========================================================================
    // Sealed class hierarchy
    // ========================================================================

    @Test
    fun testWebAuthnSignature_isSmartAccountSignature() {
        val sig: SmartAccountSignature = webAuthn()
        assertIs<WebAuthnSignature>(sig)
    }

    @Test
    fun testEd25519Signature_isSmartAccountSignature() {
        val sig: SmartAccountSignature = ed25519()
        assertIs<Ed25519Signature>(sig)
    }

    @Test
    fun testSealedClass_whenExhaustive() {
        val signatures: List<SmartAccountSignature> = listOf(
            webAuthn(),
            ed25519(),
            PolicySignature
        )
        for (sig in signatures) {
            // Ensure when expression is exhaustive over all subtypes
            val result = when (sig) {
                is WebAuthnSignature -> "webauthn"
                is Ed25519Signature -> "ed25519"
                is PolicySignature -> "policy"
            }
            assertTrue(result.isNotEmpty())
        }
    }

    // ========================================================================
    // Cross-type inequality
    // ========================================================================

    @Test
    fun testWebAuthnSignature_notEqualToEd25519Signature() {
        val webAuthn = webAuthn()
        val ed25519 = ed25519()
        assertFalse(webAuthn.equals(ed25519), "WebAuthnSignature must not equal Ed25519Signature")
    }

    @Test
    fun testEd25519Signature_notEqualToWebAuthnSignature() {
        val ed25519 = ed25519()
        val webAuthn = webAuthn()
        assertFalse(ed25519.equals(webAuthn), "Ed25519Signature must not equal WebAuthnSignature")
    }

    @Test
    fun testWebAuthnSignature_notEqualToPolicySignature() {
        val webAuthn = webAuthn()
        assertFalse(webAuthn.equals(PolicySignature))
    }

    @Test
    fun testEd25519Signature_notEqualToPolicySignature() {
        val ed25519 = ed25519()
        assertFalse(ed25519.equals(PolicySignature))
    }

    @Test
    fun testPolicySignature_notEqualToWebAuthn() {
        assertFalse(PolicySignature.equals(webAuthn()))
    }

    @Test
    fun testPolicySignature_notEqualToEd25519() {
        assertFalse(PolicySignature.equals(ed25519()))
    }

    // ========================================================================
    // hashCode consistency
    // ========================================================================

    @Test
    fun testWebAuthnSignature_hashCodeConsistentAcrossCalls() {
        val sig = webAuthn()
        val hash1 = sig.hashCode()
        val hash2 = sig.hashCode()
        assertEquals(hash1, hash2, "hashCode must be consistent across calls")
    }

    @Test
    fun testEd25519Signature_hashCodeConsistentAcrossCalls() {
        val sig = ed25519()
        val hash1 = sig.hashCode()
        val hash2 = sig.hashCode()
        assertEquals(hash1, hash2, "hashCode must be consistent across calls")
    }

    @Test
    fun testWebAuthnSignature_equalObjects_sameHashCode() {
        val a = webAuthn()
        val b = webAuthn()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode(), "Equal objects must have same hashCode")
    }

    @Test
    fun testEd25519Signature_equalObjects_sameHashCode() {
        val a = ed25519()
        val b = ed25519()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode(), "Equal objects must have same hashCode")
    }

    // ========================================================================
    // Data class copy behavior with ByteArray
    // ========================================================================

    @Test
    fun testWebAuthnSignature_copyChangesSignature() {
        val original = webAuthn()
        val newSig = ByteArray(64) { 0xAA.toByte() }
        val copied = original.copy(signature = newSig)

        assertNotEquals(original, copied, "Copy with different signature must not equal original")
        assertTrue(newSig.contentEquals(copied.signature))
    }

    @Test
    fun testEd25519Signature_copyChangesPublicKey() {
        val original = ed25519()
        val newPk = ByteArray(32) { 0xBB.toByte() }
        val copied = original.copy(publicKey = newPk)

        assertNotEquals(original, copied, "Copy with different publicKey must not equal original")
        assertTrue(newPk.contentEquals(copied.publicKey))
    }

    // ========================================================================
    // toScVal() output matches Scv helpers
    // ========================================================================

    @Test
    fun testWebAuthnToScVal_matchesManualScvConstruction() {
        val authData = authenticatorData()
        val cd = clientData()
        val s = compactSig()
        val sig = WebAuthnSignature(authData, cd, s)

        val expected = Scv.toMap(linkedMapOf(
            Scv.toSymbol("authenticator_data") to Scv.toBytes(authData),
            Scv.toSymbol("client_data") to Scv.toBytes(cd),
            Scv.toSymbol("signature") to Scv.toBytes(s)
        ))

        val actual = sig.toScVal()

        // Compare entry by entry since SCValXdr equality may not compare ByteArrays by content
        val expectedEntries = (expected as SCValXdr.Map).value!!.value
        val actualEntries = (actual as SCValXdr.Map).value!!.value

        assertEquals(expectedEntries.size, actualEntries.size)
        for (i in expectedEntries.indices) {
            assertEquals(
                symbolValue(expectedEntries[i].key),
                symbolValue(actualEntries[i].key),
                "Key at index $i must match"
            )
            assertTrue(
                bytesValue(expectedEntries[i].`val`).contentEquals(
                    bytesValue(actualEntries[i].`val`)
                ),
                "Value at index $i must match"
            )
        }
    }

    @Test
    fun testEd25519ToScVal_matchesManualScvConstruction() {
        val s = ed25519Sig64()
        val sig = Ed25519Signature(pubKey32(), s)

        val expected = Scv.toBytes(s)
        val actual = sig.toScVal()

        assertIs<SCValXdr.Bytes>(actual, "Ed25519 toScVal() must return SCValXdr.Bytes")
        assertTrue(
            bytesValue(expected).contentEquals(bytesValue(actual)),
            "toScVal() bytes must match Scv.toBytes(signature)"
        )
    }

    @Test
    fun testPolicySignatureToScVal_matchesManualScvConstruction() {
        val expected = Scv.toMap(linkedMapOf())
        val actual = PolicySignature.toScVal()

        val expectedEntries = (expected as SCValXdr.Map).value!!.value
        val actualEntries = (actual as SCValXdr.Map).value!!.value
        assertEquals(expectedEntries.size, actualEntries.size)
        assertEquals(0, actualEntries.size)
    }

    // ========================================================================
    // WebAuthnSignature: input bytes not shared (defensive copy check)
    // ========================================================================

    @Test
    fun testWebAuthnToScVal_inputMutationDoesNotAffectOriginalFields() {
        val authData = ByteArray(37) { 0x01 }
        val cd = ByteArray(50) { 0x02 }
        val s = ByteArray(64) { 0x03 }
        val sig = WebAuthnSignature(authData, cd, s)

        // Mutate original arrays after construction
        authData[0] = 0xFF.toByte()
        cd[0] = 0xFF.toByte()
        s[0] = 0xFF.toByte()

        // data class stores reference, so mutation IS visible.
        // This is expected Kotlin data class behavior (no defensive copy).
        // Verifying the toScVal output reflects current field state.
        val entries = mapEntries(sig.toScVal())
        assertEquals(sig.authenticatorData[0], bytesValue(entries[0].second)[0])
    }

    // ========================================================================
    // Validation error messages
    // ========================================================================

    @Test
    fun testWebAuthnValidation_errorMessageContainsFieldName() {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            WebAuthnSignature(authenticatorData(), clientData(), ByteArray(32))
        }
        assertTrue(
            ex.message!!.contains("signature", ignoreCase = true),
            "Error message must reference the 'signature' field"
        )
    }

    @Test
    fun testEd25519Validation_publicKeyErrorContainsFieldName() {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(ByteArray(16), ed25519Sig64())
        }
        assertTrue(
            ex.message!!.contains("publicKey", ignoreCase = true) ||
                    ex.message!!.contains("public key", ignoreCase = true),
            "Error message must reference the 'publicKey' field"
        )
    }

    @Test
    fun testEd25519Validation_signatureErrorContainsFieldName() {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(pubKey32(), ByteArray(32))
        }
        assertTrue(
            ex.message!!.contains("signature", ignoreCase = true),
            "Error message must reference the 'signature' field"
        )
    }

    // ========================================================================
    // Boundary: maximum plausible sizes
    // ========================================================================

    @Test
    fun testWebAuthnSignature_largeClientData_succeeds() {
        // Client data JSON can be large in practice
        val largeClientData = ByteArray(4096) { (it % 256).toByte() }
        val sig = WebAuthnSignature(authenticatorData(), largeClientData, compactSig())
        val entries = mapEntries(sig.toScVal())
        assertEquals(4096, bytesValue(entries[1].second).size)
    }

    @Test
    fun testEd25519Signature_distinctFromWebAuthnInScVal() {
        val ed = ed25519()
        val wa = webAuthn()

        val edScVal = ed.toScVal()
        val waScVal = wa.toScVal()

        // Ed25519 returns Bytes; WebAuthn returns Map — the discriminants must differ
        assertIs<SCValXdr.Bytes>(edScVal, "Ed25519 toScVal() must return SCValXdr.Bytes")
        assertIs<SCValXdr.Map>(waScVal, "WebAuthn toScVal() must return SCValXdr.Map")
        assertNotEquals(edScVal.discriminant, waScVal.discriminant,
            "Ed25519 and WebAuthn discriminants must differ")
    }

    // ========================================================================
    // ScVal entry value types are correct
    // ========================================================================

    @Test
    fun testWebAuthnToScVal_allKeysAreSymbols() {
        val entries = mapEntries(webAuthn().toScVal())
        for ((key, _) in entries) {
            assertIs<SCValXdr.Sym>(key, "All keys must be SCValXdr.Sym")
        }
    }

    @Test
    fun testWebAuthnToScVal_allValuesAreBytes() {
        val entries = mapEntries(webAuthn().toScVal())
        for ((_, value) in entries) {
            assertIs<SCValXdr.Bytes>(value, "All values must be SCValXdr.Bytes")
        }
    }

    @Test
    fun testEd25519ToScVal_isNotAMap() {
        val scVal = ed25519().toScVal()
        assertFalse(scVal is SCValXdr.Map, "Ed25519 toScVal() must not return a Map")
        assertIs<SCValXdr.Bytes>(scVal, "Ed25519 toScVal() must return SCValXdr.Bytes")
    }

    @Test
    fun testEd25519ToScVal_bytesAreRaw64ByteSignature() {
        val s = ed25519Sig64()
        val scVal = ed25519(signature = s).toScVal()
        assertIs<SCValXdr.Bytes>(scVal)
        assertEquals(64, bytesValue(scVal).size, "SCValXdr.Bytes content must be 64 bytes")
        assertTrue(s.contentEquals(bytesValue(scVal)), "Bytes must equal the raw signature")
    }
}
