//
//  SignatureEqualityTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.core

import com.soneso.stellar.sdk.smartaccount.core.Ed25519Signature
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [WebAuthnSignature] and [Ed25519Signature] equality, hashCode,
 * and init validation.
 */
class SignatureEqualityTest {

    // ========================================================================
    // Test Fixtures
    // ========================================================================

    private fun authenticatorData(): ByteArray = ByteArray(37) { (it + 1).toByte() }
    private fun clientData(): ByteArray = ByteArray(100) { (it + 10).toByte() }
    private fun compactSig(): ByteArray = ByteArray(64) { (it + 50).toByte() }

    private fun pubKey(): ByteArray = ByteArray(32) { (it + 1).toByte() }
    private fun ed25519Sig(): ByteArray = ByteArray(64) { (it + 5).toByte() }

    private fun webAuthn(
        authenticatorData: ByteArray = authenticatorData(),
        clientData: ByteArray = clientData(),
        signature: ByteArray = compactSig()
    ): WebAuthnSignature = WebAuthnSignature(authenticatorData, clientData, signature)

    private fun ed25519(
        publicKey: ByteArray = pubKey(),
        signature: ByteArray = ed25519Sig()
    ): Ed25519Signature = Ed25519Signature(publicKey, signature)

    // ========================================================================
    // WebAuthnSignature Equality
    // ========================================================================

    @Test
    fun testWebAuthnSignature_identicalFields_equals() {
        val authData = authenticatorData()
        val clientD = clientData()
        val sig = compactSig()
        val a = webAuthn(authData.copyOf(), clientD.copyOf(), sig.copyOf())
        val b = webAuthn(authData.copyOf(), clientD.copyOf(), sig.copyOf())
        assertTrue(a == b, "Two WebAuthnSignatures with identical byte arrays must be equal")
    }

    @Test
    fun testWebAuthnSignature_sameInstance_equals() {
        val sig = webAuthn()
        assertTrue(sig == sig, "Same instance must equal itself")
    }

    @Test
    fun testWebAuthnSignature_differentAuthenticatorData_notEqual() {
        val a = webAuthn(authenticatorData = ByteArray(37) { 0x01 })
        val b = webAuthn(authenticatorData = ByteArray(37) { 0x02 })
        assertFalse(a == b, "Different authenticatorData must produce inequality")
    }

    @Test
    fun testWebAuthnSignature_differentClientData_notEqual() {
        val a = webAuthn(clientData = ByteArray(100) { 0x01 })
        val b = webAuthn(clientData = ByteArray(100) { 0x02 })
        assertFalse(a == b, "Different clientDataJSON must produce inequality")
    }

    @Test
    fun testWebAuthnSignature_differentSignature_notEqual() {
        val a = webAuthn(signature = ByteArray(64) { 0x01 })
        val b = webAuthn(signature = ByteArray(64) { 0x02 })
        assertFalse(a == b, "Different signature bytes must produce inequality")
    }

    @Test
    fun testWebAuthnSignature_notEqualToNull() {
        val a = webAuthn()
        assertFalse(a.equals(null), "WebAuthnSignature must not equal null")
    }

    @Test
    fun testWebAuthnSignature_notEqualToDifferentType() {
        val a = webAuthn()
        assertFalse(a.equals("not a signature"), "WebAuthnSignature must not equal different type")
    }

    // ========================================================================
    // WebAuthnSignature hashCode
    // ========================================================================

    @Test
    fun testWebAuthnSignature_equalObjects_sameHashCode() {
        val authData = authenticatorData()
        val clientD = clientData()
        val sig = compactSig()
        val a = webAuthn(authData.copyOf(), clientD.copyOf(), sig.copyOf())
        val b = webAuthn(authData.copyOf(), clientD.copyOf(), sig.copyOf())
        assertEquals(a.hashCode(), b.hashCode(), "Equal WebAuthnSignatures must have equal hash codes")
    }

    @Test
    fun testWebAuthnSignature_differentAuthenticatorData_differentHashCode() {
        val a = webAuthn(authenticatorData = ByteArray(37) { 0x01 })
        val b = webAuthn(authenticatorData = ByteArray(37) { 0x02 })
        assertNotEquals(a.hashCode(), b.hashCode(), "Different authenticatorData should produce different hash codes")
    }

    @Test
    fun testWebAuthnSignature_differentSignatureBytes_differentHashCode() {
        val a = webAuthn(signature = ByteArray(64) { 0x01 })
        val b = webAuthn(signature = ByteArray(64) { 0x02 })
        assertNotEquals(a.hashCode(), b.hashCode(), "Different signature bytes should produce different hash codes")
    }

    // ========================================================================
    // WebAuthnSignature validation
    // ========================================================================

    @Test
    fun testWebAuthnSignature_signatureNot64Bytes_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            WebAuthnSignature(
                authenticatorData = authenticatorData(),
                clientData = clientData(),
                signature = ByteArray(32) // wrong size
            )
        }
    }

    @Test
    fun testWebAuthnSignature_emptySignature_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            WebAuthnSignature(
                authenticatorData = authenticatorData(),
                clientData = clientData(),
                signature = ByteArray(0)
            )
        }
    }

    @Test
    fun testWebAuthnSignature_65ByteSignature_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            WebAuthnSignature(
                authenticatorData = authenticatorData(),
                clientData = clientData(),
                signature = ByteArray(65)
            )
        }
    }

    // ========================================================================
    // Ed25519Signature Equality
    // ========================================================================

    @Test
    fun testEd25519Signature_identicalFields_equals() {
        val pk = pubKey()
        val sig = ed25519Sig()
        val a = ed25519(pk.copyOf(), sig.copyOf())
        val b = ed25519(pk.copyOf(), sig.copyOf())
        assertTrue(a == b, "Two Ed25519Signatures with identical byte arrays must be equal")
    }

    @Test
    fun testEd25519Signature_sameInstance_equals() {
        val sig = ed25519()
        assertTrue(sig == sig, "Same instance must equal itself")
    }

    @Test
    fun testEd25519Signature_differentPublicKey_notEqual() {
        val a = ed25519(publicKey = ByteArray(32) { 0x01 })
        val b = ed25519(publicKey = ByteArray(32) { 0x02 })
        assertFalse(a == b, "Different publicKey must produce inequality")
    }

    @Test
    fun testEd25519Signature_differentSignature_notEqual() {
        val a = ed25519(signature = ByteArray(64) { 0x01 })
        val b = ed25519(signature = ByteArray(64) { 0x02 })
        assertFalse(a == b, "Different signature bytes must produce inequality")
    }

    @Test
    fun testEd25519Signature_notEqualToNull() {
        val a = ed25519()
        assertFalse(a.equals(null), "Ed25519Signature must not equal null")
    }

    @Test
    fun testEd25519Signature_notEqualToDifferentType() {
        val a = ed25519()
        assertFalse(a.equals("not a signature"), "Ed25519Signature must not equal different type")
    }

    // ========================================================================
    // Ed25519Signature hashCode
    // ========================================================================

    @Test
    fun testEd25519Signature_equalObjects_sameHashCode() {
        val pk = pubKey()
        val sig = ed25519Sig()
        val a = ed25519(pk.copyOf(), sig.copyOf())
        val b = ed25519(pk.copyOf(), sig.copyOf())
        assertEquals(a.hashCode(), b.hashCode(), "Equal Ed25519Signatures must have equal hash codes")
    }

    @Test
    fun testEd25519Signature_differentPublicKey_differentHashCode() {
        val a = ed25519(publicKey = ByteArray(32) { 0x01 })
        val b = ed25519(publicKey = ByteArray(32) { 0x02 })
        assertNotEquals(a.hashCode(), b.hashCode(), "Different publicKey should produce different hash codes")
    }

    @Test
    fun testEd25519Signature_differentSignatureBytes_differentHashCode() {
        val a = ed25519(signature = ByteArray(64) { 0x01 })
        val b = ed25519(signature = ByteArray(64) { 0x02 })
        assertNotEquals(a.hashCode(), b.hashCode(), "Different signature bytes should produce different hash codes")
    }

    // ========================================================================
    // Ed25519Signature validation
    // ========================================================================

    @Test
    fun testEd25519Signature_publicKeyNot32Bytes_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(
                publicKey = ByteArray(16), // wrong size
                signature = ed25519Sig()
            )
        }
    }

    @Test
    fun testEd25519Signature_signatureNot64Bytes_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(
                publicKey = pubKey(),
                signature = ByteArray(32) // wrong size
            )
        }
    }

    @Test
    fun testEd25519Signature_emptyPublicKey_throws() {
        assertFailsWith<ValidationException.InvalidInput> {
            Ed25519Signature(
                publicKey = ByteArray(0),
                signature = ed25519Sig()
            )
        }
    }
}
