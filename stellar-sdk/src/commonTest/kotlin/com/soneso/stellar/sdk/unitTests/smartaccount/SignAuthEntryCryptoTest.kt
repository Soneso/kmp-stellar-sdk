//
//  SignAuthEntryCryptoTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cryptographic verification tests for [SmartAccountAuth.signAuthEntry].
 *
 * These tests verify that the signature produced by [SmartAccountAuth.signAuthEntry]
 * using an Ed25519 [KeyPair] is cryptographically valid: the 64-byte signature in the
 * signed entry can be verified against the payload hash computed by
 * [SmartAccountAuth.buildAuthPayloadHash] using [KeyPair.verify].
 *
 * The contract format for Ed25519 external signers stores the signature inside a double
 * XDR-encoded [SCValXdr.Bytes] value inside a [SCValXdr.Map]. This test unpacks that
 * structure and verifies the raw Ed25519 bytes.
 */
class SignAuthEntryCryptoTest {

    private val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val networkPassphrase = Network.TESTNET.networkPassphrase

    // MARK: - Helpers

    private fun createTestAuthEntry(nonce: Long = 12345L): SorobanAuthorizationEntryXdr {
        val scAddress = com.soneso.stellar.sdk.Address(contractAddress).toSCAddress()
        val invocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = scAddress,
                    functionName = SCSymbolXdr("transfer"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
        val credentials = SorobanAddressCredentialsXdr(
            address = scAddress,
            nonce = Int64Xdr(nonce),
            signatureExpirationLedger = Uint32Xdr(0u),
            signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        )
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(credentials),
            rootInvocation = invocation
        )
    }

    // MARK: - Crypto Verification Tests

    @Test
    fun testSignAuthEntry_ed25519SignatureIsValid() = runTest {
        val keypair = KeyPair.random()
        val expirationLedger = 5000000u
        val authEntry = createTestAuthEntry()

        // Compute the payload hash the same way the contract will
        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        // Sign the payload using the keypair
        val rawSignatureBytes = keypair.sign(payloadHash)
        assertEquals(64, rawSignatureBytes.size, "Ed25519 signature must be 64 bytes")

        // Build an Ed25519Signature wrapping the public key and signature bytes
        val ed25519Sig = Ed25519Signature(
            publicKey = keypair.getPublicKey(),
            signature = rawSignatureBytes
        )

        // Build an ExternalSigner representing this Ed25519 key (using a dummy verifier)
        val signer = ExternalSigner.ed25519(
            verifierAddress = contractAddress,
            publicKey = keypair.getPublicKey()
        )

        val signedEntry = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = ed25519Sig,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        // Unpack the signature from the signed entry
        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        val vec = (credentials.signature as SCValXdr.Vec).value
        assertNotNull(vec)
        assertTrue(vec.value.isNotEmpty())

        val mapEntry = (vec.value[0] as SCValXdr.Map).value
        assertNotNull(mapEntry)
        assertEquals(1, mapEntry.value.size)

        // The value is double-XDR-encoded: SCValXdr.Bytes(XDR_bytes_of_inner_ScVal)
        val outerBytesVal = mapEntry.value[0].`val`
        assertTrue(outerBytesVal is SCValXdr.Bytes, "Signature value must be SCValXdr.Bytes")
        val xdrBytes = (outerBytesVal as SCValXdr.Bytes).value.value

        // The xdrBytes contains XDR of the Ed25519Signature SCVal map. Decode it and
        // extract the raw 64-byte signature under key "signature".
        val innerScVal = SCValXdr.decode(com.soneso.stellar.sdk.xdr.XdrReader(xdrBytes))
        assertTrue(innerScVal is SCValXdr.Map, "Inner ScVal must be a Map")
        val innerMap = (innerScVal as SCValXdr.Map).value
        assertNotNull(innerMap)

        // Find the "signature" entry (alphabetically: public_key first, then signature)
        val sigEntry = innerMap.value.firstOrNull { entry ->
            (entry.key as? SCValXdr.Sym)?.value?.value == "signature"
        }
        assertNotNull(sigEntry, "Must have a 'signature' entry in the Ed25519 map")

        val sigBytes = ((sigEntry.`val` as SCValXdr.Bytes).value).value
        assertEquals(64, sigBytes.size, "Ed25519 signature must be 64 bytes")

        // Verify the extracted signature against the payload hash using the public key
        val isValid = keypair.verify(payloadHash, sigBytes)
        assertTrue(isValid, "Ed25519 signature must verify correctly against the payload hash")
    }

    @Test
    fun testSignAuthEntry_differentKeyPairDoesNotVerify() = runTest {
        val signingKeypair = KeyPair.random()
        val unrelatedKeypair = KeyPair.random()
        val expirationLedger = 9999999u
        val authEntry = createTestAuthEntry(nonce = 99999L)

        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val rawSignatureBytes = signingKeypair.sign(payloadHash)
        val ed25519Sig = Ed25519Signature(
            publicKey = signingKeypair.getPublicKey(),
            signature = rawSignatureBytes
        )
        val signer = ExternalSigner.ed25519(
            verifierAddress = contractAddress,
            publicKey = signingKeypair.getPublicKey()
        )

        val signedEntry = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = ed25519Sig,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        // Unpack signature bytes
        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        val vec = (credentials.signature as SCValXdr.Vec).value!!
        val mapEntry = (vec.value[0] as SCValXdr.Map).value!!
        val xdrBytes = ((mapEntry.value[0].`val`) as SCValXdr.Bytes).value.value
        val innerMap = (SCValXdr.decode(com.soneso.stellar.sdk.xdr.XdrReader(xdrBytes)) as SCValXdr.Map).value!!
        val sigEntry = innerMap.value.first { (it.key as? SCValXdr.Sym)?.value?.value == "signature" }
        val sigBytes = (sigEntry.`val` as SCValXdr.Bytes).value.value

        // Verify against the signing keypair: should be valid
        assertTrue(signingKeypair.verify(payloadHash, sigBytes))

        // Verify against an unrelated keypair: must NOT be valid
        val verifiedByWrongKey = unrelatedKeypair.verify(payloadHash, sigBytes)
        assertTrue(!verifiedByWrongKey, "Signature must not verify with a different keypair")
    }

    @Test
    fun testSignAuthEntry_payloadHashChangesWithDifferentNonce() = runTest {
        val expirationLedger = 1000000u

        val entry1 = createTestAuthEntry(nonce = 111L)
        val entry2 = createTestAuthEntry(nonce = 222L)

        val hash1 = SmartAccountAuth.buildAuthPayloadHash(entry1, expirationLedger, networkPassphrase)
        val hash2 = SmartAccountAuth.buildAuthPayloadHash(entry2, expirationLedger, networkPassphrase)

        assertTrue(!hash1.contentEquals(hash2), "Different nonces must produce different payload hashes")
    }

    @Test
    fun testSignAuthEntry_payloadHashChangesWithDifferentExpiration() = runTest {
        val entry = createTestAuthEntry()

        val hash1 = SmartAccountAuth.buildAuthPayloadHash(entry, 1000000u, networkPassphrase)
        val hash2 = SmartAccountAuth.buildAuthPayloadHash(entry, 2000000u, networkPassphrase)

        assertTrue(!hash1.contentEquals(hash2), "Different expiration ledgers must produce different payload hashes")
    }

    @Test
    fun testSignAuthEntry_payloadHashIs32Bytes() = runTest {
        val entry = createTestAuthEntry()
        val hash = SmartAccountAuth.buildAuthPayloadHash(entry, 1000000u, networkPassphrase)
        assertEquals(32, hash.size, "SHA-256 hash must be 32 bytes")
    }

    @Test
    fun testSignAuthEntry_doesNotMutateOriginalEntry() = runTest {
        val keypair = KeyPair.random()
        val expirationLedger = 1000000u
        val authEntry = createTestAuthEntry()

        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(authEntry, expirationLedger, networkPassphrase)
        val rawSig = keypair.sign(payloadHash)
        val ed25519Sig = Ed25519Signature(keypair.getPublicKey(), rawSig)
        val signer = ExternalSigner.ed25519(contractAddress, keypair.getPublicKey())

        SmartAccountAuth.signAuthEntry(authEntry, signer, ed25519Sig, expirationLedger, networkPassphrase)

        // Original entry expiration must still be 0 (the value we set in createTestAuthEntry)
        val originalCredentials = (authEntry.credentials as SorobanCredentialsXdr.Address).value
        assertEquals(0u, originalCredentials.signatureExpirationLedger.value,
            "Original entry must not be mutated by signAuthEntry")
    }
}
