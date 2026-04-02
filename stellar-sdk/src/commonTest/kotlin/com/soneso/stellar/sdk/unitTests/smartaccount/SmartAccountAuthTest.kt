//
//  SmartAccountAuthTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.Ed25519Signature
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.PolicySignature
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuth
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnSignature
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SmartAccountAuth].
 *
 * Covers [SmartAccountAuth.buildSourceAccountAuthPayloadHash],
 * [SmartAccountAuth.addRawSignatureMapEntry], multi-signer accumulation,
 * and edge cases including WebAuthn signatures and cross-function hash consistency.
 */
class SmartAccountAuthTest {

    private val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val networkPassphrase = Network.TESTNET.networkPassphrase
    private val mainnetPassphrase = Network.PUBLIC.networkPassphrase

    // MARK: - Helpers

    private fun createTestInvocation(): SorobanAuthorizedInvocationXdr {
        val scAddress = Address(contractAddress).toSCAddress()
        return SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = scAddress,
                    functionName = SCSymbolXdr("transfer"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    }

    private fun createAddressAuthEntry(nonce: Long = 12345L): SorobanAuthorizationEntryXdr {
        val scAddress = Address(contractAddress).toSCAddress()
        val invocation = createTestInvocation()
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

    private fun createSourceAccountAuthEntry(): SorobanAuthorizationEntryXdr {
        val invocation = createTestInvocation()
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = invocation
        )
    }

    /**
     * Manually constructs the preimage and returns SHA-256(XDR(preimage)).
     * Used as the ground-truth reference in hash correctness tests.
     */
    private suspend fun buildExpectedHash(
        nonce: Long,
        expirationLedger: UInt,
        invocation: SorobanAuthorizedInvocationXdr,
        passphrase: String
    ): ByteArray {
        val networkId = getSha256Crypto().hash(passphrase.encodeToByteArray())
        val preimageInner = HashIDPreimageSorobanAuthorizationXdr(
            networkId = HashXdr(networkId),
            nonce = Int64Xdr(nonce),
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            invocation = invocation
        )
        val preimage = HashIDPreimageXdr.SorobanAuthorization(preimageInner)
        val writer = XdrWriter()
        preimage.encode(writer)
        return getSha256Crypto().hash(writer.toByteArray())
    }

    // MARK: - buildSourceAccountAuthPayloadHash Tests

    @Test
    fun testBuildSourceAccountAuthPayloadHash_resultIs32Bytes() = runTest {
        val entry = createSourceAccountAuthEntry()
        val hash = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = Int64Xdr(99999L),
            expirationLedger = 1000000u,
            networkPassphrase = networkPassphrase
        )
        assertEquals(32, hash.size, "SHA-256 hash must be 32 bytes")
    }

    @Test
    fun testBuildSourceAccountAuthPayloadHash_differentNoncesProduceDifferentHashes() = runTest {
        val entry = createSourceAccountAuthEntry()
        val hash1 = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = Int64Xdr(111L),
            expirationLedger = 1000000u,
            networkPassphrase = networkPassphrase
        )
        val hash2 = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = Int64Xdr(222L),
            expirationLedger = 1000000u,
            networkPassphrase = networkPassphrase
        )
        assertTrue(
            !hash1.contentEquals(hash2),
            "Different nonces must produce different hashes"
        )
    }

    @Test
    fun testBuildSourceAccountAuthPayloadHash_differentExpirationProducesDifferentHash() = runTest {
        val entry = createSourceAccountAuthEntry()
        val nonce = Int64Xdr(12345L)
        val hash1 = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = nonce,
            expirationLedger = 1000000u,
            networkPassphrase = networkPassphrase
        )
        val hash2 = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = nonce,
            expirationLedger = 2000000u,
            networkPassphrase = networkPassphrase
        )
        assertTrue(
            !hash1.contentEquals(hash2),
            "Different expiration ledgers must produce different hashes"
        )
    }

    @Test
    fun testBuildSourceAccountAuthPayloadHash_differentNetworkPassphraseProducesDifferentHash() = runTest {
        val entry = createSourceAccountAuthEntry()
        val nonce = Int64Xdr(12345L)
        val expirationLedger = 1000000u
        val hashTestnet = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = nonce,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        val hashMainnet = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = nonce,
            expirationLedger = expirationLedger,
            networkPassphrase = mainnetPassphrase
        )
        assertTrue(
            !hashTestnet.contentEquals(hashMainnet),
            "Different network passphrases must produce different hashes"
        )
    }

    @Test
    fun testBuildSourceAccountAuthPayloadHash_isConsistent() = runTest {
        val entry = createSourceAccountAuthEntry()
        val nonce = Int64Xdr(77777L)
        val expirationLedger = 5000000u
        val hash1 = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = nonce,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        val hash2 = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = nonce,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        assertTrue(hash1.contentEquals(hash2), "Same inputs must produce identical hashes")
    }

    @Test
    fun testBuildSourceAccountAuthPayloadHash_matchesManualPreimageConstruction() = runTest {
        val nonce = 42000L
        val expirationLedger = 3000000u
        val entry = createSourceAccountAuthEntry()

        val actual = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = entry,
            nonce = Int64Xdr(nonce),
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val expected = buildExpectedHash(
            nonce = nonce,
            expirationLedger = expirationLedger,
            invocation = entry.rootInvocation,
            passphrase = networkPassphrase
        )

        assertTrue(
            actual.contentEquals(expected),
            "Hash must match manual preimage construction"
        )
    }

    // MARK: - buildAuthPayloadHash Error-Path Tests

    @Test
    fun testBuildAuthPayloadHash_throwsOnVoidCredentials() = runTest {
        val entry = createSourceAccountAuthEntry()
        assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuth.buildAuthPayloadHash(
                entry = entry,
                expirationLedger = 1000000u,
                networkPassphrase = networkPassphrase
            )
        }
    }

    // MARK: - buildAuthPayloadHash vs buildSourceAccountAuthPayloadHash consistency

    @Test
    fun testBuildAuthPayloadHash_andBuildSourceAccountAuthPayloadHash_sameInputsProduceSameHash() = runTest {
        val nonce = 55555L
        val expirationLedger = 4000000u

        // Address-credentials entry with the test nonce
        val addressEntry = createAddressAuthEntry(nonce = nonce)

        val hashFromAddressEntry = SmartAccountAuth.buildAuthPayloadHash(
            entry = addressEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        // Source-account entry (different entry object, same invocation)
        val sourceAccountEntry = createSourceAccountAuthEntry()

        val hashFromSourceAccountEntry = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
            entry = sourceAccountEntry,
            nonce = Int64Xdr(nonce),
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        assertTrue(
            hashFromAddressEntry.contentEquals(hashFromSourceAccountEntry),
            "buildAuthPayloadHash and buildSourceAccountAuthPayloadHash must produce the same hash " +
                "when given the same nonce, expiration, invocation, and network passphrase"
        )
    }

    // MARK: - addRawSignatureMapEntry Tests

    @Test
    fun testAddRawSignatureMapEntry_addsEntryToVoidSignatureEntry() {
        val entry = createAddressAuthEntry()
        val delegatedSigner = DelegatedSigner("GBVG2QOHHFBVHAEGNF4XRUCAPAGWDROONM2LC4BK4ECCQ5RTQOO64VBW")
        val signerKey = delegatedSigner.toScVal()
        val signatureValue = Scv.toBytes(ByteArray(0))

        val result = SmartAccountAuth.addRawSignatureMapEntry(
            entry = entry,
            signerKey = signerKey,
            signatureValue = signatureValue
        )

        val credentials = (result.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val outerEntries = outerMap.value!!.value
        assertEquals(2, outerEntries.size, "AuthPayload must have exactly 2 entries")

        val signersEntry = outerEntries.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(1, signersMap.size, "Signers map must contain exactly one entry")
    }

    @Test
    fun testAddRawSignatureMapEntry_mapEntryHasCorrectKeyAndValue() {
        val entry = createAddressAuthEntry()
        val valueBytes = ByteArray(16) { (it + 1).toByte() }
        val delegatedSigner = DelegatedSigner("GBVG2QOHHFBVHAEGNF4XRUCAPAGWDROONM2LC4BK4ECCQ5RTQOO64VBW")
        val signerKey = delegatedSigner.toScVal()
        val signatureValue = Scv.toBytes(valueBytes)

        val result = SmartAccountAuth.addRawSignatureMapEntry(
            entry = entry,
            signerKey = signerKey,
            signatureValue = signatureValue
        )

        val credentials = (result.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val signersEntry = outerMap.value!!.value.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(1, signersMap.size)

        val entryValue = signersMap[0].`val` as? SCValXdr.Bytes
        assertNotNull(entryValue, "Map entry value must be SCValXdr.Bytes")
        assertTrue(entryValue.value.value.contentEquals(valueBytes), "Value bytes must match")
    }

    @Test
    fun testAddRawSignatureMapEntry_secondCallProducesTwoEntries() {
        val entry = createAddressAuthEntry()
        val signer1 = DelegatedSigner("GBVG2QOHHFBVHAEGNF4XRUCAPAGWDROONM2LC4BK4ECCQ5RTQOO64VBW")
        val signer2 = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
        val key1 = signer1.toScVal()
        val key2 = signer2.toScVal()
        val voidValue = Scv.toBytes(ByteArray(0))

        val entryWith1 = SmartAccountAuth.addRawSignatureMapEntry(
            entry = entry,
            signerKey = key1,
            signatureValue = voidValue
        )
        val entryWith2 = SmartAccountAuth.addRawSignatureMapEntry(
            entry = entryWith1,
            signerKey = key2,
            signatureValue = voidValue
        )

        val credentials = (entryWith2.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val signersEntry = outerMap.value!!.value.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(2, signersMap.size, "Signers map must contain two entries after two addRawSignatureMapEntry calls")
    }

    @Test
    fun testAddRawSignatureMapEntry_mapEntriesAreSortedByXdrEncodedKey() {
        val entry = createAddressAuthEntry()
        val voidValue = Scv.toBytes(ByteArray(0))

        // Add two different delegated signers — order inserted should not determine final order
        val signer1 = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
        val signer2 = DelegatedSigner("GBVG2QOHHFBVHAEGNF4XRUCAPAGWDROONM2LC4BK4ECCQ5RTQOO64VBW")

        val entryWithFirst = SmartAccountAuth.addRawSignatureMapEntry(
            entry = entry,
            signerKey = signer1.toScVal(),
            signatureValue = voidValue
        )
        val entryWithBoth = SmartAccountAuth.addRawSignatureMapEntry(
            entry = entryWithFirst,
            signerKey = signer2.toScVal(),
            signatureValue = voidValue
        )

        val credentials = (entryWithBoth.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val signersEntry = outerMap.value!!.value.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(2, signersMap.size)

        // Compute XDR hex keys for both entries and verify ascending order
        fun xdrHex(scVal: SCValXdr): String {
            val w = XdrWriter()
            scVal.encode(w)
            return w.toByteArray().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        }
        val firstKeyHex = xdrHex(signersMap[0].key)
        val secondKeyHex = xdrHex(signersMap[1].key)
        assertTrue(firstKeyHex < secondKeyHex, "Signers map entries must be sorted by XDR-encoded key hex in strictly ascending order (distinct entries)")
    }

    @Test
    fun testAddRawSignatureMapEntry_throwsOnSourceAccountCredentials() {
        val entry = createSourceAccountAuthEntry()
        val signerKey = Scv.toBytes(ByteArray(32) { 0xAB.toByte() })
        val signatureValue = Scv.toBytes(ByteArray(0))

        assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuth.addRawSignatureMapEntry(
                entry = entry,
                signerKey = signerKey,
                signatureValue = signatureValue
            )
        }
    }

    @Test
    fun testSignAuthEntry_throwsOnVoidCredentials() = runTest {
        val entry = createSourceAccountAuthEntry()
        val keypair = KeyPair.random()
        val dummyPublicKey = keypair.getPublicKey()
        val dummySignatureBytes = keypair.sign(ByteArray(32))
        val dummySig = Ed25519Signature(publicKey = dummyPublicKey, signature = dummySignatureBytes)
        val dummySigner = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = dummyPublicKey)

        assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuth.signAuthEntry(
                entry = entry,
                signer = dummySigner,
                signature = dummySig,
                expirationLedger = 1000000u
            )
        }
    }

    @Test
    fun testAddRawSignatureMapEntry_doesNotMutateOriginalEntry() {
        val entry = createAddressAuthEntry()
        val delegatedSigner = DelegatedSigner("GBVG2QOHHFBVHAEGNF4XRUCAPAGWDROONM2LC4BK4ECCQ5RTQOO64VBW")
        val signerKey = delegatedSigner.toScVal()
        val signatureValue = Scv.toBytes(ByteArray(0))

        SmartAccountAuth.addRawSignatureMapEntry(
            entry = entry,
            signerKey = signerKey,
            signatureValue = signatureValue
        )

        // Original entry's signature must still be Void
        val originalCredentials = (entry.credentials as SorobanCredentialsXdr.Address).value
        assertTrue(
            originalCredentials.signature is SCValXdr.Void,
            "Original entry signature must not be mutated"
        )
    }

    @Test
    fun testAddRawSignatureMapEntry_rawBytesAreStoredAsScvBytes() {
        val entry = createAddressAuthEntry()
        val rawBytes = ByteArray(64) { (it * 3).toByte() }
        val delegatedSigner = DelegatedSigner("GBVG2QOHHFBVHAEGNF4XRUCAPAGWDROONM2LC4BK4ECCQ5RTQOO64VBW")
        val signerKey = delegatedSigner.toScVal()
        val signatureValue = Scv.toBytes(rawBytes)

        val result = SmartAccountAuth.addRawSignatureMapEntry(
            entry = entry,
            signerKey = signerKey,
            signatureValue = signatureValue
        )

        val credentials = (result.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val signersEntry = outerMap.value!!.value.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        val storedValue = signersMap[0].`val` as? SCValXdr.Bytes
        assertNotNull(storedValue, "Stored value must be SCValXdr.Bytes")
        assertTrue(storedValue.value.value.contentEquals(rawBytes), "Raw bytes must be preserved exactly")
    }

    // MARK: - Multi-Signer Accumulation Tests

    @Test
    fun testSignAuthEntry_twoSignersAccumulateCorrectly() = runTest {
        val keypair1 = KeyPair.random()
        val keypair2 = KeyPair.random()
        val expirationLedger = 5000000u
        val authEntry = createAddressAuthEntry()

        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val sig1 = Ed25519Signature(publicKey = keypair1.getPublicKey(), signature = keypair1.sign(payloadHash))
        val sig2 = Ed25519Signature(publicKey = keypair2.getPublicKey(), signature = keypair2.sign(payloadHash))

        val signer1 = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair1.getPublicKey())
        val signer2 = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair2.getPublicKey())

        val entryAfterFirst = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer1,
            signature = sig1,
            expirationLedger = expirationLedger
        )

        val entryAfterSecond = SmartAccountAuth.signAuthEntry(
            entry = entryAfterFirst,
            signer = signer2,
            signature = sig2,
            expirationLedger = expirationLedger
        )

        val credentials = (entryAfterSecond.credentials as SorobanCredentialsXdr.Address).value
        assertEquals(expirationLedger, credentials.signatureExpirationLedger.value, "Expiration ledger must be set on credentials")
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val outerEntries = outerMap.value!!.value
        assertEquals(2, outerEntries.size, "AuthPayload must have exactly 2 entries")
        val signersEntry = outerEntries.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(2, signersMap.size, "Both signatures must be present in the signers map")
    }

    @Test
    fun testSignAuthEntry_twoSignersResultIsSortedByXdrEncodedKey() = runTest {
        val keypair1 = KeyPair.random()
        val keypair2 = KeyPair.random()
        val expirationLedger = 5000000u
        val authEntry = createAddressAuthEntry()

        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val sig1 = Ed25519Signature(publicKey = keypair1.getPublicKey(), signature = keypair1.sign(payloadHash))
        val sig2 = Ed25519Signature(publicKey = keypair2.getPublicKey(), signature = keypair2.sign(payloadHash))

        val signer1 = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair1.getPublicKey())
        val signer2 = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair2.getPublicKey())

        val entryAfterFirst = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer1,
            signature = sig1,
            expirationLedger = expirationLedger
        )
        val entryAfterSecond = SmartAccountAuth.signAuthEntry(
            entry = entryAfterFirst,
            signer = signer2,
            signature = sig2,
            expirationLedger = expirationLedger
        )

        val credentials = (entryAfterSecond.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val signersEntry = outerMap.value!!.value.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(2, signersMap.size)

        fun xdrHex(scVal: SCValXdr): String {
            val w = XdrWriter()
            scVal.encode(w)
            return w.toByteArray().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        }
        val firstKeyHex = xdrHex(signersMap[0].key)
        val secondKeyHex = xdrHex(signersMap[1].key)
        assertTrue(firstKeyHex < secondKeyHex, "Two-signer map must be sorted by XDR-encoded key hex in strictly ascending order (distinct entries)")
    }

    @Test
    fun testSignAuthEntry_followedByAddRawSignatureMapEntry_bothEntriesPresent() = runTest {
        val keypair = KeyPair.random()
        val expirationLedger = 5000000u
        val authEntry = createAddressAuthEntry()

        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val sig = Ed25519Signature(publicKey = keypair.getPublicKey(), signature = keypair.sign(payloadHash))
        val signer = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair.getPublicKey())

        val signedEntry = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = sig,
            expirationLedger = expirationLedger
        )

        // Add a raw placeholder entry for a delegated signer (different from the keypair-based signer)
        val rawDelegatedSigner = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
        val rawKey = rawDelegatedSigner.toScVal()
        val rawValue = Scv.toBytes(ByteArray(0))
        val entryWithBoth = SmartAccountAuth.addRawSignatureMapEntry(
            entry = signedEntry,
            signerKey = rawKey,
            signatureValue = rawValue
        )

        val credentials = (entryWithBoth.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val signersEntry = outerMap.value!!.value.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(2, signersMap.size, "Entry from signAuthEntry and raw entry must both be present")
    }

    // MARK: - PolicySignature + DelegatedSigner Tests

    @Test
    fun testSignAuthEntry_policySignatureWithDelegatedSignerHasCorrectStructure() = runTest {
        val expirationLedger = 5000000u
        val authEntry = createAddressAuthEntry()

        // A delegated signer uses a valid G-address or C-address.
        val delegatedAddress = "GBVG2QOHHFBVHAEGNF4XRUCAPAGWDROONM2LC4BK4ECCQ5RTQOO64VBW"
        val delegatedSigner = DelegatedSigner(address = delegatedAddress)

        val signedEntry = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = delegatedSigner,
            signature = PolicySignature,
            expirationLedger = expirationLedger
        )

        // Verify credentials structure
        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        assertEquals(expirationLedger, credentials.signatureExpirationLedger.value, "Expiration ledger must be set")

        // Verify outer Map (AuthPayload) structure
        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val outerEntries = outerMap.value!!.value
        assertEquals(2, outerEntries.size, "AuthPayload must have exactly 2 entries")

        val contextRuleIdsEntry = outerEntries.first { (it.key as? SCValXdr.Sym)?.value?.value == "context_rule_ids" }
        val contextRuleIdsVec = contextRuleIdsEntry.`val` as? SCValXdr.Vec
        assertNotNull(contextRuleIdsVec, "context_rule_ids must be a Vec")

        val signersEntry = outerEntries.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(1, signersMap.size, "Signers map must contain exactly one entry")

        // Verify the value is double-XDR-encoded empty map (PolicySignature.toScVal() = empty map)
        val outerBytes = signersMap[0].`val` as? SCValXdr.Bytes
        assertNotNull(outerBytes, "Signature value must be SCValXdr.Bytes (outer double-encoding)")

        val innerScVal = SCValXdr.decode(XdrReader(outerBytes.value.value))
        assertTrue(innerScVal is SCValXdr.Map, "Inner ScVal must be a Map (PolicySignature encodes as empty map)")

        val innerMap = (innerScVal as SCValXdr.Map).value
        assertNotNull(innerMap, "Inner map must not be null")
        assertEquals(0, innerMap.value.size, "PolicySignature must encode as an empty map")
    }

    // MARK: - WebAuthn Signature Type Tests

    @Test
    fun testSignAuthEntry_webAuthnSignatureTypeIsStoredCorrectly() = runTest {
        val expirationLedger = 5000000u
        val authEntry = createAddressAuthEntry()

        // Build valid test payload: use a random Ed25519 keypair to provide a 64-byte
        // stand-in for the WebAuthn ECDSA compact signature field
        val keypair = KeyPair.random()
        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val authenticatorData = ByteArray(37) { it.toByte() }  // Typical authenticator data size
        val clientData = ByteArray(100) { (it + 10).toByte() }
        val compactSig = keypair.sign(payloadHash).copyOf(64)   // Ed25519 is 64 bytes; use as compact sig stand-in

        val webAuthnSig = WebAuthnSignature(
            authenticatorData = authenticatorData,
            clientData = clientData,
            signature = compactSig
        )

        // Use an ExternalSigner with 65-byte uncompressed secp256r1 key placeholder
        val fakeP256Key = ByteArray(65).also { it[0] = 0x04 }
        val credentialId = ByteArray(16) { it.toByte() }
        val webAuthnSigner = ExternalSigner.webAuthn(
            verifierAddress = contractAddress,
            publicKey = fakeP256Key,
            credentialId = credentialId
        )

        val signedEntry = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = webAuthnSigner,
            signature = webAuthnSig,
            expirationLedger = expirationLedger
        )

        // Verify the entry structure is well-formed
        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        assertEquals(expirationLedger, credentials.signatureExpirationLedger.value)

        val outerMap = credentials.signature as? SCValXdr.Map
        assertNotNull(outerMap, "Signature must be a Map (AuthPayload)")
        val signersEntry = outerMap.value!!.value.first { (it.key as? SCValXdr.Sym)?.value?.value == "signers" }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(1, signersMap.size, "Signers map must have exactly one entry")

        // The value must be a double-XDR-encoded SCVal::Bytes
        val outerBytes = signersMap[0].`val` as? SCValXdr.Bytes
        assertNotNull(outerBytes, "Signature value must be SCValXdr.Bytes (outer)")
        val innerScVal = SCValXdr.decode(XdrReader(outerBytes.value.value))
        assertTrue(innerScVal is SCValXdr.Map, "Inner ScVal must be a Map (WebAuthn signature map)")

        // Confirm the inner map has the three expected keys in alphabetical order
        val innerMap = (innerScVal as SCValXdr.Map).value!!.value
        assertEquals(3, innerMap.size, "WebAuthn signature map must have 3 entries")
        assertEquals("authenticator_data", (innerMap[0].key as SCValXdr.Sym).value.value)
        assertEquals("client_data",        (innerMap[1].key as SCValXdr.Sym).value.value)
        assertEquals("signature",          (innerMap[2].key as SCValXdr.Sym).value.value)

        // Verify bytes round-trip correctly
        val storedAuthData = (innerMap[0].`val` as SCValXdr.Bytes).value.value
        val storedClientData = (innerMap[1].`val` as SCValXdr.Bytes).value.value
        val storedSig = (innerMap[2].`val` as SCValXdr.Bytes).value.value

        assertTrue(storedAuthData.contentEquals(authenticatorData), "authenticator_data bytes must be preserved")
        assertTrue(storedClientData.contentEquals(clientData), "client_data bytes must be preserved")
        assertTrue(storedSig.contentEquals(compactSig), "signature bytes must be preserved")
    }

    // MARK: - buildAuthDigest Tests

    @Test
    fun testBuildAuthDigest_changesWithDifferentRuleIds() = runTest {
        val signaturePayload = ByteArray(32) { it.toByte() }

        val digest1 = SmartAccountAuth.buildAuthDigest(signaturePayload, listOf(1u))
        val digest2 = SmartAccountAuth.buildAuthDigest(signaturePayload, listOf(2u))
        val digest3 = SmartAccountAuth.buildAuthDigest(signaturePayload, listOf(1u, 2u))

        assertEquals(32, digest1.size, "Auth digest must be 32 bytes")
        assertTrue(!digest1.contentEquals(digest2), "Different rule IDs must produce different digests")
        assertTrue(!digest1.contentEquals(digest3), "Different number of rule IDs must produce different digests")
    }

    @Test
    fun testBuildAuthDigest_isConsistent() = runTest {
        val signaturePayload = ByteArray(32) { it.toByte() }
        val ruleIds = listOf(1u, 2u, 3u)

        val digest1 = SmartAccountAuth.buildAuthDigest(signaturePayload, ruleIds)
        val digest2 = SmartAccountAuth.buildAuthDigest(signaturePayload, ruleIds)

        assertTrue(digest1.contentEquals(digest2), "Same inputs must produce identical digests")
    }

    @Test
    fun testBuildAuthDigest_emptyRuleIdsProducesDifferentDigestThanNonEmpty() = runTest {
        val signaturePayload = ByteArray(32) { it.toByte() }

        val digestEmpty = SmartAccountAuth.buildAuthDigest(signaturePayload, emptyList())
        val digestNonEmpty = SmartAccountAuth.buildAuthDigest(signaturePayload, listOf(0u))

        assertTrue(!digestEmpty.contentEquals(digestNonEmpty), "Empty and non-empty rule IDs must differ")
    }

    // MARK: - contextRuleIds Codec Tests

    @Test
    fun testSignAuthEntry_contextRuleIdsArePreservedInPayload() = runTest {
        val keypair = KeyPair.random()
        val expirationLedger = 5000000u
        val authEntry = createAddressAuthEntry()
        val contextRuleIds = listOf(3u, 7u)

        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val sig = Ed25519Signature(publicKey = keypair.getPublicKey(), signature = keypair.sign(payloadHash))
        val signer = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair.getPublicKey())

        val signedEntry = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = sig,
            expirationLedger = expirationLedger,
            contextRuleIds = contextRuleIds
        )

        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as SCValXdr.Map
        val contextRuleIdsEntry = outerMap.value!!.value.first {
            (it.key as? SCValXdr.Sym)?.value?.value == "context_rule_ids"
        }
        val ruleIdsVec = (contextRuleIdsEntry.`val` as SCValXdr.Vec).value!!.value
        assertEquals(2, ruleIdsVec.size)
        assertEquals(3u, (ruleIdsVec[0] as SCValXdr.U32).value.value)
        assertEquals(7u, (ruleIdsVec[1] as SCValXdr.U32).value.value)
    }

    @Test
    fun testSignAuthEntry_emptyContextRuleIdsProducesEmptyVec() = runTest {
        val keypair = KeyPair.random()
        val expirationLedger = 5000000u
        val authEntry = createAddressAuthEntry()

        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val sig = Ed25519Signature(publicKey = keypair.getPublicKey(), signature = keypair.sign(payloadHash))
        val signer = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair.getPublicKey())

        // No contextRuleIds passed — defaults to empty
        val signedEntry = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = sig,
            expirationLedger = expirationLedger
        )

        val credentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as SCValXdr.Map
        val contextRuleIdsEntry = outerMap.value!!.value.first {
            (it.key as? SCValXdr.Sym)?.value?.value == "context_rule_ids"
        }
        val ruleIdsVec = (contextRuleIdsEntry.`val` as SCValXdr.Vec).value!!.value
        assertEquals(0, ruleIdsVec.size, "Empty contextRuleIds should produce empty Vec")
    }

    @Test
    fun testSignAuthEntry_secondSignerPreservesContextRuleIds() = runTest {
        val keypair1 = KeyPair.random()
        val keypair2 = KeyPair.random()
        val expirationLedger = 5000000u
        val authEntry = createAddressAuthEntry()
        val contextRuleIds = listOf(5u)

        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = authEntry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        val sig1 = Ed25519Signature(publicKey = keypair1.getPublicKey(), signature = keypair1.sign(payloadHash))
        val signer1 = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair1.getPublicKey())

        val entryAfterFirst = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer1,
            signature = sig1,
            expirationLedger = expirationLedger,
            contextRuleIds = contextRuleIds
        )

        // Second signer does NOT pass contextRuleIds — they should be preserved from the first call
        val sig2 = Ed25519Signature(publicKey = keypair2.getPublicKey(), signature = keypair2.sign(payloadHash))
        val signer2 = ExternalSigner.ed25519(verifierAddress = contractAddress, publicKey = keypair2.getPublicKey())

        val entryAfterSecond = SmartAccountAuth.signAuthEntry(
            entry = entryAfterFirst,
            signer = signer2,
            signature = sig2,
            expirationLedger = expirationLedger
        )

        val credentials = (entryAfterSecond.credentials as SorobanCredentialsXdr.Address).value
        val outerMap = credentials.signature as SCValXdr.Map

        val contextRuleIdsEntry = outerMap.value!!.value.first {
            (it.key as? SCValXdr.Sym)?.value?.value == "context_rule_ids"
        }
        val ruleIdsVec = (contextRuleIdsEntry.`val` as SCValXdr.Vec).value!!.value
        assertEquals(1, ruleIdsVec.size)
        assertEquals(5u, (ruleIdsVec[0] as SCValXdr.U32).value.value)

        val signersEntry = outerMap.value!!.value.first {
            (it.key as? SCValXdr.Sym)?.value?.value == "signers"
        }
        val signersMap = (signersEntry.`val` as SCValXdr.Map).value!!.value
        assertEquals(2, signersMap.size, "Both signers must be present")
    }
}
