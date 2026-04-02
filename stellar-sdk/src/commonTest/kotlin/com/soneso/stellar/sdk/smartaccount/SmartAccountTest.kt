//
//  SmartAccountTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Layer 1 Unit Tests for Smart Account SDK.
 *
 * Tests cover:
 * - SmartAccountErrors: Error codes and factory methods
 * - SmartAccountTypes: Signer types (Delegated and External)
 * - SmartAccountSignatures: Signature types (WebAuthn, Ed25519, Policy)
 * - SmartAccountUtils: Signature normalization, public key extraction, contract derivation
 * - SmartAccountAuth: Payload hash building, auth entry signing
 *
 * Coverage target: 90%+
 */
class SmartAccountTest {

    // MARK: - Error Tests

    @Test
    fun testErrorCodes_numericValues() {
        // Configuration errors (1xxx)
        assertEquals(1001, SmartAccountErrorCode.INVALID_CONFIG.code)
        assertEquals(1002, SmartAccountErrorCode.MISSING_CONFIG.code)

        // Wallet state errors (2xxx)
        assertEquals(2001, SmartAccountErrorCode.WALLET_NOT_CONNECTED.code)
        assertEquals(2002, SmartAccountErrorCode.WALLET_ALREADY_EXISTS.code)
        assertEquals(2003, SmartAccountErrorCode.WALLET_NOT_FOUND.code)

        // Credential errors (3xxx)
        assertEquals(3001, SmartAccountErrorCode.CREDENTIAL_NOT_FOUND.code)
        assertEquals(3002, SmartAccountErrorCode.CREDENTIAL_ALREADY_EXISTS.code)
        assertEquals(3003, SmartAccountErrorCode.CREDENTIAL_INVALID.code)
        assertEquals(3004, SmartAccountErrorCode.CREDENTIAL_DEPLOYMENT_FAILED.code)

        // WebAuthn errors (4xxx)
        assertEquals(4001, SmartAccountErrorCode.WEBAUTHN_REGISTRATION_FAILED.code)
        assertEquals(4002, SmartAccountErrorCode.WEBAUTHN_AUTHENTICATION_FAILED.code)
        assertEquals(4003, SmartAccountErrorCode.WEBAUTHN_NOT_SUPPORTED.code)
        assertEquals(4004, SmartAccountErrorCode.WEBAUTHN_CANCELLED.code)

        // Transaction errors (5xxx)
        assertEquals(5001, SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED.code)
        assertEquals(5002, SmartAccountErrorCode.TRANSACTION_SIGNING_FAILED.code)
        assertEquals(5003, SmartAccountErrorCode.TRANSACTION_SUBMISSION_FAILED.code)
        assertEquals(5004, SmartAccountErrorCode.TRANSACTION_TIMEOUT.code)

        // Signer errors (6xxx)
        assertEquals(6001, SmartAccountErrorCode.SIGNER_NOT_FOUND.code)
        assertEquals(6002, SmartAccountErrorCode.SIGNER_INVALID.code)

        // Validation errors (7xxx)
        assertEquals(7001, SmartAccountErrorCode.INVALID_ADDRESS.code)
        assertEquals(7002, SmartAccountErrorCode.INVALID_AMOUNT.code)
        assertEquals(7003, SmartAccountErrorCode.INVALID_INPUT.code)

        // Storage errors (8xxx)
        assertEquals(8001, SmartAccountErrorCode.STORAGE_READ_FAILED.code)
        assertEquals(8002, SmartAccountErrorCode.STORAGE_WRITE_FAILED.code)

        // Session errors (9xxx)
        assertEquals(9001, SmartAccountErrorCode.SESSION_EXPIRED.code)
        assertEquals(9002, SmartAccountErrorCode.SESSION_INVALID.code)
    }

    @Test
    fun testConfigurationException_factoryMethods() {
        val error1 = ConfigurationException.invalidConfig("test details")
        assertEquals(SmartAccountErrorCode.INVALID_CONFIG, error1.code)
        assertTrue(error1.message.contains("test details"))

        val error2 = ConfigurationException.missingConfig("testParam")
        assertEquals(SmartAccountErrorCode.MISSING_CONFIG, error2.code)
        assertTrue(error2.message.contains("testParam"))
    }

    @Test
    fun testWalletException_factoryMethods() {
        val error1 = WalletException.notConnected()
        assertEquals(SmartAccountErrorCode.WALLET_NOT_CONNECTED, error1.code)

        val error2 = WalletException.alreadyExists("wallet123")
        assertEquals(SmartAccountErrorCode.WALLET_ALREADY_EXISTS, error2.code)
        assertTrue(error2.message.contains("wallet123"))

        val error3 = WalletException.notFound("wallet456")
        assertEquals(SmartAccountErrorCode.WALLET_NOT_FOUND, error3.code)
        assertTrue(error3.message.contains("wallet456"))
    }

    @Test
    fun testCredentialException_factoryMethods() {
        val error1 = CredentialException.notFound("cred123")
        assertEquals(SmartAccountErrorCode.CREDENTIAL_NOT_FOUND, error1.code)
        assertTrue(error1.message.contains("cred123"))

        val error2 = CredentialException.alreadyExists("cred456")
        assertEquals(SmartAccountErrorCode.CREDENTIAL_ALREADY_EXISTS, error2.code)
        assertTrue(error2.message.contains("cred456"))

        val error3 = CredentialException.invalid("bad format")
        assertEquals(SmartAccountErrorCode.CREDENTIAL_INVALID, error3.code)
        assertTrue(error3.message.contains("bad format"))

        val error4 = CredentialException.deploymentFailed("network error")
        assertEquals(SmartAccountErrorCode.CREDENTIAL_DEPLOYMENT_FAILED, error4.code)
        assertTrue(error4.message.contains("network error"))
    }

    @Test
    fun testWebAuthnException_factoryMethods() {
        val error1 = WebAuthnException.registrationFailed("invalid key")
        assertEquals(SmartAccountErrorCode.WEBAUTHN_REGISTRATION_FAILED, error1.code)
        assertTrue(error1.message.contains("invalid key"))

        val error2 = WebAuthnException.authenticationFailed("timeout")
        assertEquals(SmartAccountErrorCode.WEBAUTHN_AUTHENTICATION_FAILED, error2.code)
        assertTrue(error2.message.contains("timeout"))

        val error3 = WebAuthnException.notSupported()
        assertEquals(SmartAccountErrorCode.WEBAUTHN_NOT_SUPPORTED, error3.code)

        val error4 = WebAuthnException.cancelled()
        assertEquals(SmartAccountErrorCode.WEBAUTHN_CANCELLED, error4.code)
    }

    @Test
    fun testTransactionException_factoryMethods() {
        val error1 = TransactionException.simulationFailed("gas limit")
        assertEquals(SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED, error1.code)
        assertTrue(error1.message.contains("gas limit"))

        val error2 = TransactionException.signingFailed("no key")
        assertEquals(SmartAccountErrorCode.TRANSACTION_SIGNING_FAILED, error2.code)
        assertTrue(error2.message.contains("no key"))

        val error3 = TransactionException.submissionFailed("network down")
        assertEquals(SmartAccountErrorCode.TRANSACTION_SUBMISSION_FAILED, error3.code)
        assertTrue(error3.message.contains("network down"))

        val error4 = TransactionException.timeout()
        assertEquals(SmartAccountErrorCode.TRANSACTION_TIMEOUT, error4.code)
    }

    @Test
    fun testSignerException_factoryMethods() {
        val error1 = SignerException.notFound("signer123")
        assertEquals(SmartAccountErrorCode.SIGNER_NOT_FOUND, error1.code)
        assertTrue(error1.message.contains("signer123"))

        val error2 = SignerException.invalid("wrong format")
        assertEquals(SmartAccountErrorCode.SIGNER_INVALID, error2.code)
        assertTrue(error2.message.contains("wrong format"))
    }

    @Test
    fun testValidationException_factoryMethods() {
        val error1 = ValidationException.invalidAddress("GXYZ...")
        assertEquals(SmartAccountErrorCode.INVALID_ADDRESS, error1.code)
        assertTrue(error1.message.contains("GXYZ"))

        val error2 = ValidationException.invalidAmount("100.5", "negative")
        assertEquals(SmartAccountErrorCode.INVALID_AMOUNT, error2.code)
        assertTrue(error2.message.contains("100.5"))
        assertTrue(error2.message.contains("negative"))

        val error3 = ValidationException.invalidInput("fieldName", "too short")
        assertEquals(SmartAccountErrorCode.INVALID_INPUT, error3.code)
        assertTrue(error3.message.contains("fieldName"))
        assertTrue(error3.message.contains("too short"))
    }

    @Test
    fun testStorageException_factoryMethods() {
        val error1 = StorageException.readFailed("key123")
        assertEquals(SmartAccountErrorCode.STORAGE_READ_FAILED, error1.code)
        assertTrue(error1.message.contains("key123"))

        val error2 = StorageException.writeFailed("key456")
        assertEquals(SmartAccountErrorCode.STORAGE_WRITE_FAILED, error2.code)
        assertTrue(error2.message.contains("key456"))
    }

    @Test
    fun testSessionException_factoryMethods() {
        val error1 = SessionException.expired()
        assertEquals(SmartAccountErrorCode.SESSION_EXPIRED, error1.code)

        val error2 = SessionException.expired("session123")
        assertEquals(SmartAccountErrorCode.SESSION_EXPIRED, error2.code)
        assertTrue(error2.message.contains("session123"))

        val error3 = SessionException.invalid("corrupted data")
        assertEquals(SmartAccountErrorCode.SESSION_INVALID, error3.code)
        assertTrue(error3.message.contains("corrupted data"))
    }

    @Test
    fun testSmartAccountException_toString() {
        val error = ValidationException.InvalidAddress("Test address error")
        val string = error.toString()

        assertTrue(string.contains("SmartAccountException"))
        assertTrue(string.contains("7001"))
        assertTrue(string.contains("Test address error"))
    }

    @Test
    fun testSmartAccountException_withCause() {
        val cause = RuntimeException("Root cause")
        val error = TransactionException.SigningFailed("Signing failed", cause)

        assertEquals(cause, error.cause)
        assertTrue(error.toString().contains("Root cause"))
    }

    // MARK: - Signer Types Tests

    @Test
    fun testDelegatedSigner_validGAddress() {
        val address = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        val signer = DelegatedSigner(address)

        assertEquals(address, signer.address)
        assertEquals("delegated:$address", signer.uniqueKey)

        val scVal = signer.toScVal()
        // Verify structure: Vec([Symbol("Delegated"), Address(...)])
        assertTrue(scVal is com.soneso.stellar.sdk.xdr.SCValXdr.Vec)
        val vec = (scVal as com.soneso.stellar.sdk.xdr.SCValXdr.Vec).value
        assertEquals(2, vec?.value?.size)
    }

    @Test
    fun testDelegatedSigner_validCAddress() {
        val address = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val signer = DelegatedSigner(address)

        assertEquals(address, signer.address)
        assertEquals("delegated:$address", signer.uniqueKey)

        val scVal = signer.toScVal()
        assertTrue(scVal is com.soneso.stellar.sdk.xdr.SCValXdr.Vec)
    }

    @Test
    fun testDelegatedSigner_invalidPrefix() {
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("MABCD1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ234567890ABC")
        }
    }

    @Test
    fun testDelegatedSigner_invalidLength() {
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("GA7Q")
        }
    }

    @Test
    fun testDelegatedSigner_invalidChecksum() {
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGX")
        }
    }

    @Test
    fun testExternalSigner_validCreation() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val keyData = ByteArray(65) { 0x42 }

        val signer = ExternalSigner(verifier, keyData)

        assertEquals(verifier, signer.verifierAddress)
        assertTrue(keyData.contentEquals(signer.keyData))

        val scVal = signer.toScVal()
        // Verify structure: Vec([Symbol("External"), Address(...), Bytes(...)])
        assertTrue(scVal is com.soneso.stellar.sdk.xdr.SCValXdr.Vec)
        val vec = (scVal as com.soneso.stellar.sdk.xdr.SCValXdr.Vec).value
        assertEquals(3, vec?.value?.size)
    }

    @Test
    fun testExternalSigner_emptyKeyDataThrows() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"

        assertFailsWith<ValidationException> {
            ExternalSigner(verifier, ByteArray(0))
        }
    }

    @Test
    fun testExternalSigner_invalidVerifierPrefix() {
        assertFailsWith<ValidationException.InvalidAddress> {
            ExternalSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ", ByteArray(32))
        }
    }

    @Test
    fun testExternalSigner_webAuthnFactory_valid() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until 65) {
            publicKey[i] = 0xAA.toByte()
        }
        val credentialId = ByteArray(16) { 0xBB.toByte() }

        val signer = ExternalSigner.webAuthn(verifier, publicKey, credentialId)

        assertEquals(65 + 16, signer.keyData.size)
        assertTrue(signer.uniqueKey.startsWith("external:$verifier:"))
    }

    @Test
    fun testExternalSigner_webAuthnFactory_wrongKeySize() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val publicKey = ByteArray(33) // Wrong size

        assertFailsWith<ValidationException> {
            ExternalSigner.webAuthn(verifier, publicKey, ByteArray(16))
        }
    }

    @Test
    fun testExternalSigner_webAuthnFactory_wrongPrefix() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val publicKey = ByteArray(65)
        publicKey[0] = 0x03 // Compressed prefix instead of 0x04

        assertFailsWith<ValidationException> {
            ExternalSigner.webAuthn(verifier, publicKey, ByteArray(16))
        }
    }

    @Test
    fun testExternalSigner_webAuthnFactory_emptyCredentialId() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX

        assertFailsWith<ValidationException> {
            ExternalSigner.webAuthn(verifier, publicKey, ByteArray(0))
        }
    }

    @Test
    fun testExternalSigner_ed25519Factory_valid() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val publicKey = ByteArray(32) { 0xCC.toByte() }

        val signer = ExternalSigner.ed25519(verifier, publicKey)

        assertEquals(32, signer.keyData.size)
    }

    @Test
    fun testExternalSigner_ed25519Factory_wrongKeySize() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val publicKey = ByteArray(33) // Wrong size

        assertFailsWith<ValidationException> {
            ExternalSigner.ed25519(verifier, publicKey)
        }
    }

    @Test
    fun testExternalSigner_uniqueKeyDifferentiation() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val keyData1 = ByteArray(32) { 0xAA.toByte() }
        val keyData2 = ByteArray(32) { 0xBB.toByte() }

        val signer1 = ExternalSigner(verifier, keyData1)
        val signer2 = ExternalSigner(verifier, keyData2)

        assertNotEquals(signer1.uniqueKey, signer2.uniqueKey)
    }

    @Test
    fun testExternalSigner_equality() {
        val verifier = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val keyData = ByteArray(32) { 0xAA.toByte() }

        val signer1 = ExternalSigner(verifier, keyData)
        val signer2 = ExternalSigner(verifier, keyData.copyOf())

        assertEquals(signer1, signer2)
        assertEquals(signer1.hashCode(), signer2.hashCode())
    }

    // MARK: - Signature Types Tests

    @Test
    fun testWebAuthnSignature_valid() {
        val authenticatorData = ByteArray(37) { 0x11 }
        val clientData = ByteArray(80) { 0x22 }
        val signature = ByteArray(64) { 0x33 }

        val sig = WebAuthnSignature(authenticatorData, clientData, signature)

        assertTrue(authenticatorData.contentEquals(sig.authenticatorData))
        assertTrue(clientData.contentEquals(sig.clientData))
        assertTrue(signature.contentEquals(sig.signature))
    }

    @Test
    fun testWebAuthnSignature_wrongSignatureSize() {
        val authenticatorData = ByteArray(37) { 0x11 }
        val clientData = ByteArray(80) { 0x22 }
        val signature = ByteArray(63) { 0x33 } // Wrong size

        assertFailsWith<ValidationException> {
            WebAuthnSignature(authenticatorData, clientData, signature)
        }
    }

    @Test
    fun testWebAuthnSignature_toScVal_alphabeticalOrder() {
        val authenticatorData = ByteArray(37) { 0x11 }
        val clientData = ByteArray(80) { 0x22 }
        val signature = ByteArray(64) { 0x33 }

        val sig = WebAuthnSignature(authenticatorData, clientData, signature)
        val scVal = sig.toScVal()

        // Verify it's a map
        assertTrue(scVal is com.soneso.stellar.sdk.xdr.SCValXdr.Map)
        val map = (scVal as com.soneso.stellar.sdk.xdr.SCValXdr.Map).value
        assertEquals(3, map?.value?.size)

        // Verify keys are in alphabetical order
        val key1 = (map?.value?.get(0)?.key as com.soneso.stellar.sdk.xdr.SCValXdr.Sym).value?.value
        val key2 = (map?.value?.get(1)?.key as com.soneso.stellar.sdk.xdr.SCValXdr.Sym).value?.value
        val key3 = (map?.value?.get(2)?.key as com.soneso.stellar.sdk.xdr.SCValXdr.Sym).value?.value

        assertEquals("authenticator_data", key1)
        assertEquals("client_data", key2)
        assertEquals("signature", key3)

        // Verify alphabetical order
        assertTrue(key1!! < key2!!)
        assertTrue(key2 < key3!!)
    }

    @Test
    fun testWebAuthnSignature_equality() {
        val authenticatorData = ByteArray(37) { 0x11 }
        val clientData = ByteArray(80) { 0x22 }
        val signature = ByteArray(64) { 0x33 }

        val sig1 = WebAuthnSignature(authenticatorData, clientData, signature)
        val sig2 = WebAuthnSignature(
            authenticatorData.copyOf(),
            clientData.copyOf(),
            signature.copyOf()
        )

        assertEquals(sig1, sig2)
        assertEquals(sig1.hashCode(), sig2.hashCode())
    }

    @Test
    fun testEd25519Signature_valid() {
        val publicKey = ByteArray(32) { 0x44 }
        val signature = ByteArray(64) { 0x55 }

        val sig = Ed25519Signature(publicKey, signature)

        assertTrue(publicKey.contentEquals(sig.publicKey))
        assertTrue(signature.contentEquals(sig.signature))
    }

    @Test
    fun testEd25519Signature_wrongPublicKeySize() {
        val publicKey = ByteArray(31) { 0x44 } // Wrong size
        val signature = ByteArray(64) { 0x55 }

        assertFailsWith<ValidationException> {
            Ed25519Signature(publicKey, signature)
        }
    }

    @Test
    fun testEd25519Signature_wrongSignatureSize() {
        val publicKey = ByteArray(32) { 0x44 }
        val signature = ByteArray(63) { 0x55 } // Wrong size

        assertFailsWith<ValidationException> {
            Ed25519Signature(publicKey, signature)
        }
    }

    @Test
    fun testEd25519Signature_toScVal_alphabeticalOrder() {
        val publicKey = ByteArray(32) { 0x44 }
        val signature = ByteArray(64) { 0x55 }

        val sig = Ed25519Signature(publicKey, signature)
        val scVal = sig.toScVal()

        // Verify it's a map
        assertTrue(scVal is com.soneso.stellar.sdk.xdr.SCValXdr.Map)
        val map = (scVal as com.soneso.stellar.sdk.xdr.SCValXdr.Map).value
        assertEquals(2, map?.value?.size)

        // Verify keys are in alphabetical order
        val key1 = (map?.value?.get(0)?.key as com.soneso.stellar.sdk.xdr.SCValXdr.Sym).value?.value
        val key2 = (map?.value?.get(1)?.key as com.soneso.stellar.sdk.xdr.SCValXdr.Sym).value?.value

        assertEquals("public_key", key1)
        assertEquals("signature", key2)

        // Verify alphabetical order
        assertTrue(key1!! < key2!!)
    }

    @Test
    fun testEd25519Signature_equality() {
        val publicKey = ByteArray(32) { 0x44 }
        val signature = ByteArray(64) { 0x55 }

        val sig1 = Ed25519Signature(publicKey, signature)
        val sig2 = Ed25519Signature(publicKey.copyOf(), signature.copyOf())

        assertEquals(sig1, sig2)
        assertEquals(sig1.hashCode(), sig2.hashCode())
    }

    @Test
    fun testPolicySignature_returnsEmptyMap() {
        val scVal = PolicySignature.toScVal()

        // Verify it's a map
        assertTrue(scVal is com.soneso.stellar.sdk.xdr.SCValXdr.Map)
        val map = (scVal as com.soneso.stellar.sdk.xdr.SCValXdr.Map).value
        assertEquals(0, map?.value?.size)
    }

    // MARK: - SmartAccountUtils Tests

    @Test
    fun testNormalizeSignature_highS() {
        // Test Vector 1: High-S normalization
        val derSignature = hexToBytes(
            "3045022001020304050607080910111213141516171819202122232425262728293031320221" +
            "00ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550"
        )

        val expected = hexToBytes(
            "0102030405060708091011121314151617181920212223242526272829303132" +
            "0000000000000000000000000000000000000000000000000000000000000001"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size)
        assertEquals(expected.toHex(), result.toHex())
    }

    @Test
    fun testNormalizeSignature_alreadyLowS() {
        // Test Vector 1b: Already low-S (no normalization)
        val derSignature = hexToBytes(
            "30440220010203040506070809101112131415161718192021222324252627282930313202" +
            "200000000000000000000000000000000000000000000000000000000000000005"
        )

        val expected = hexToBytes(
            "0102030405060708091011121314151617181920212223242526272829303132" +
            "0000000000000000000000000000000000000000000000000000000000000005"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size)
        assertEquals(expected.toHex(), result.toHex())
    }

    @Test
    fun testNormalizeSignature_malformedDER() {
        val invalidDer = hexToBytes("31450220010203040506070809")

        assertFailsWith<ValidationException> {
            SmartAccountUtils.normalizeSignature(invalidDer)
        }
    }

    @Test
    fun testNormalizeSignature_shortSignature() {
        val shortDer = hexToBytes("300102")

        assertFailsWith<ValidationException> {
            SmartAccountUtils.normalizeSignature(shortDer)
        }
    }

    @Test
    fun testNormalizeSignature_truncated() {
        val truncatedDer = hexToBytes("3045022001020304050607080910111213141516")

        assertFailsWith<ValidationException> {
            SmartAccountUtils.normalizeSignature(truncatedDer)
        }
    }

    @Test
    fun testExtractPublicKeyFromRegistration_validCOSE() {
        // Create valid COSE structure
        val cosePrefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )
        // secp256r1 generator point G (SEC 2 / FIPS 186-4) — a known-valid on-curve point.
        val xCoord = byteArrayOf(
            0x6B.toByte(), 0x17.toByte(), 0xD1.toByte(), 0xF2.toByte(),
            0xE1.toByte(), 0x2C.toByte(), 0x42.toByte(), 0x47.toByte(),
            0xF8.toByte(), 0xBC.toByte(), 0xE6.toByte(), 0xE5.toByte(),
            0x63.toByte(), 0xA4.toByte(), 0x40.toByte(), 0xF2.toByte(),
            0x77.toByte(), 0x03.toByte(), 0x7D.toByte(), 0x81.toByte(),
            0x2D.toByte(), 0xEB.toByte(), 0x33.toByte(), 0xA0.toByte(),
            0xF4.toByte(), 0xA1.toByte(), 0x39.toByte(), 0x45.toByte(),
            0xD8.toByte(), 0x98.toByte(), 0xC2.toByte(), 0x96.toByte()
        )
        val separator = byteArrayOf(0x22, 0x58, 0x20.toByte())
        val yCoord = byteArrayOf(
            0x4F.toByte(), 0xE3.toByte(), 0x42.toByte(), 0xE2.toByte(),
            0xFE.toByte(), 0x1A.toByte(), 0x7F.toByte(), 0x9B.toByte(),
            0x8E.toByte(), 0xE7.toByte(), 0xEB.toByte(), 0x4A.toByte(),
            0x7C.toByte(), 0x0F.toByte(), 0x9E.toByte(), 0x16.toByte(),
            0x2B.toByte(), 0xCE.toByte(), 0x33.toByte(), 0x57.toByte(),
            0x6B.toByte(), 0x31.toByte(), 0x5E.toByte(), 0xCE.toByte(),
            0xCB.toByte(), 0xB6.toByte(), 0x40.toByte(), 0x68.toByte(),
            0x37.toByte(), 0xBF.toByte(), 0x51.toByte(), 0xF5.toByte()
        )

        val attestationData = ByteArray(10) + cosePrefix + xCoord + separator + yCoord + ByteArray(10)

        val publicKey = SmartAccountUtils.extractPublicKeyFromRegistration(attestationObject = attestationData)

        assertEquals(65, publicKey.size)
        assertEquals(SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX, publicKey[0])
        assertTrue(xCoord.contentEquals(publicKey.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(publicKey.copyOfRange(33, 65)))
    }

    @Test
    fun testExtractPublicKeyFromRegistration_missingCOSEPrefix() {
        val attestationData = ByteArray(100) { 0xFF.toByte() }

        assertFailsWith<ValidationException> {
            SmartAccountUtils.extractPublicKeyFromRegistration(attestationObject = attestationData)
        }
    }

    @Test
    fun testExtractPublicKeyFromRegistration_truncatedData() {
        val cosePrefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )
        val attestationData = cosePrefix + ByteArray(10) // Not enough data

        assertFailsWith<ValidationException> {
            SmartAccountUtils.extractPublicKeyFromRegistration(attestationObject = attestationData)
        }
    }

    @Test
    fun testGetContractSalt_returns32Bytes() = runTest {
        val credentialId = ByteArray(16) { 0x42 }

        val salt = SmartAccountUtils.getContractSalt(credentialId)

        assertEquals(32, salt.size)
    }

    @Test
    fun testGetContractSalt_deterministic() = runTest {
        val credentialId = ByteArray(16) { 0x42 }

        val salt1 = SmartAccountUtils.getContractSalt(credentialId)
        val salt2 = SmartAccountUtils.getContractSalt(credentialId)

        assertTrue(salt1.contentEquals(salt2))
    }

    @Test
    fun testDeriveContractAddress_validInputs() = runTest {
        val credentialId = ByteArray(16) { 0x42 }
        val deployerKey = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        val networkPassphrase = Network.TESTNET.networkPassphrase

        val contractAddress = SmartAccountUtils.deriveContractAddress(
            credentialId,
            deployerKey,
            networkPassphrase
        )

        assertTrue(contractAddress.startsWith("C"))
        assertEquals(56, contractAddress.length)
    }

    @Test
    fun testDeriveContractAddress_invalidDeployerKey() = runTest {
        val credentialId = ByteArray(16) { 0x42 }
        val invalidKey = "INVALID_KEY"
        val networkPassphrase = Network.TESTNET.networkPassphrase

        assertFailsWith<ValidationException.InvalidAddress> {
            SmartAccountUtils.deriveContractAddress(
                credentialId,
                invalidKey,
                networkPassphrase
            )
        }
    }

    @Test
    fun testDeriveContractAddress_deterministic() = runTest {
        val credentialId = ByteArray(16) { 0x42 }
        val deployerKey = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        val networkPassphrase = Network.TESTNET.networkPassphrase

        val address1 = SmartAccountUtils.deriveContractAddress(
            credentialId,
            deployerKey,
            networkPassphrase
        )
        val address2 = SmartAccountUtils.deriveContractAddress(
            credentialId,
            deployerKey,
            networkPassphrase
        )

        assertEquals(address1, address2)
    }

    // MARK: - SmartAccountAuth Tests

    @Test
    fun testBuildAuthPayloadHash_returns32Bytes() = runTest {
        val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val nonce: Long = 12345
        val expirationLedger: UInt = 1000000u

        val authEntry = createTestAuthEntry(contractAddress, nonce)

        val hash = SmartAccountAuth.buildAuthPayloadHash(
            authEntry,
            expirationLedger,
            Network.TESTNET.networkPassphrase
        )

        assertEquals(32, hash.size)
    }

    @Test
    fun testBuildAuthPayloadHash_deterministic() = runTest {
        val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val nonce: Long = 12345
        val expirationLedger: UInt = 1000000u

        val authEntry = createTestAuthEntry(contractAddress, nonce)

        val hash1 = SmartAccountAuth.buildAuthPayloadHash(
            authEntry,
            expirationLedger,
            Network.TESTNET.networkPassphrase
        )
        val hash2 = SmartAccountAuth.buildAuthPayloadHash(
            authEntry,
            expirationLedger,
            Network.TESTNET.networkPassphrase
        )

        assertTrue(hash1.contentEquals(hash2))
    }

    @Test
    fun testBuildAuthPayloadHash_nonAddressCredentials() = runTest {
        val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val expirationLedger: UInt = 1000000u

        val scAddress = com.soneso.stellar.sdk.Address(contractAddress).toSCAddress()
        val invocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = scAddress,
                    functionName = SCSymbolXdr("test"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )

        val authEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = invocation
        )

        assertFailsWith<TransactionException> {
            SmartAccountAuth.buildAuthPayloadHash(
                authEntry,
                expirationLedger,
                Network.TESTNET.networkPassphrase
            )
        }
    }

    @Test
    fun testSignAuthEntry_setsExpiration() = runTest {
        val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val nonce: Long = 12345
        val expirationLedger: UInt = 1000000u

        val authEntry = createTestAuthEntry(contractAddress, nonce)

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        val credentialId = ByteArray(16) { 0xBB.toByte() }
        val signer = ExternalSigner.webAuthn(contractAddress, publicKey, credentialId)

        val signature = WebAuthnSignature(
            ByteArray(37) { 0x11 },
            ByteArray(80) { 0x22 },
            ByteArray(64) { 0x33 }
        )

        val signedEntry = SmartAccountAuth.signAuthEntry(
            authEntry,
            signer,
            signature,
            expirationLedger
        )

        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        assertEquals(expirationLedger, credentials.signatureExpirationLedger.value)
    }

    @Test
    fun testSignAuthEntry_createsSignatureMap() = runTest {
        val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val nonce: Long = 12345
        val expirationLedger: UInt = 1000000u

        val authEntry = createTestAuthEntry(contractAddress, nonce)

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        val credentialId = ByteArray(16) { 0xBB.toByte() }
        val signer = ExternalSigner.webAuthn(contractAddress, publicKey, credentialId)

        val signature = WebAuthnSignature(
            ByteArray(37) { 0x11 },
            ByteArray(80) { 0x22 },
            ByteArray(64) { 0x33 }
        )

        val signedEntry = SmartAccountAuth.signAuthEntry(
            authEntry,
            signer,
            signature,
            expirationLedger
        )

        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        assertTrue(credentials.signature is com.soneso.stellar.sdk.xdr.SCValXdr.Vec)

        val vec = (credentials.signature as com.soneso.stellar.sdk.xdr.SCValXdr.Vec).value
        assertFalse(vec?.value.isNullOrEmpty())

        val firstElement = vec?.value?.get(0)
        assertTrue(firstElement is com.soneso.stellar.sdk.xdr.SCValXdr.Map)

        val map = (firstElement as com.soneso.stellar.sdk.xdr.SCValXdr.Map).value
        assertEquals(1, map?.value?.size)
    }

    @Test
    fun testSignAuthEntry_doubleXDREncoding() = runTest {
        val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val nonce: Long = 12345
        val expirationLedger: UInt = 1000000u

        val authEntry = createTestAuthEntry(contractAddress, nonce)

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        val credentialId = ByteArray(16) { 0xBB.toByte() }
        val signer = ExternalSigner.webAuthn(contractAddress, publicKey, credentialId)

        val signature = WebAuthnSignature(
            ByteArray(37) { 0x11 },
            ByteArray(80) { 0x22 },
            ByteArray(64) { 0x33 }
        )

        val signedEntry = SmartAccountAuth.signAuthEntry(
            authEntry,
            signer,
            signature,
            expirationLedger
        )

        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        val vec = (credentials.signature as com.soneso.stellar.sdk.xdr.SCValXdr.Vec).value
        val map = (vec?.value?.get(0) as com.soneso.stellar.sdk.xdr.SCValXdr.Map).value
        val sigValue = map?.value?.get(0)?.`val`

        // Signature value should be Bytes containing XDR
        assertTrue(sigValue is com.soneso.stellar.sdk.xdr.SCValXdr.Bytes)
    }

    @Test
    fun testSignAuthEntry_nonAddressCredentials() = runTest {
        val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val expirationLedger: UInt = 1000000u

        val scAddress = com.soneso.stellar.sdk.Address(contractAddress).toSCAddress()
        val invocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = scAddress,
                    functionName = SCSymbolXdr("test"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )

        val authEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = invocation
        )

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        val credentialId = ByteArray(16) { 0xBB.toByte() }
        val signer = ExternalSigner.webAuthn(contractAddress, publicKey, credentialId)

        val signature = WebAuthnSignature(
            ByteArray(37) { 0x11 },
            ByteArray(80) { 0x22 },
            ByteArray(64) { 0x33 }
        )

        assertFailsWith<TransactionException> {
            SmartAccountAuth.signAuthEntry(
                authEntry,
                signer,
                signature,
                expirationLedger
            )
        }
    }

    // MARK: - Helper Functions

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("\n", "")
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }

    private fun createTestAuthEntry(contractAddress: String, nonce: Long): SorobanAuthorizationEntryXdr {
        val scAddress = com.soneso.stellar.sdk.Address(contractAddress).toSCAddress()
        val invocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = scAddress,
                    functionName = SCSymbolXdr("test_fn"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )

        val credentials = SorobanAddressCredentialsXdr(
            address = scAddress,
            nonce = Int64Xdr(nonce),
            signatureExpirationLedger = Uint32Xdr(0u),
            signature = com.soneso.stellar.sdk.xdr.SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        )

        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(credentials),
            rootInvocation = invocation
        )
    }
}
