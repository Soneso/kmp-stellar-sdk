//
//  SmartAccountAuthArmAwarenessTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.core

import com.soneso.stellar.sdk.unitTests.smartaccount.createTestInvocation
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.Ed25519Signature
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuth
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayloadCodec
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationWithAddressXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsWithDelegatesXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Arm-awareness tests for [SmartAccountAuth].
 *
 * Verifies that the payload-hash and signing paths select the preimage type by the
 * credential arm (legacy ENVELOPE_TYPE_SOROBAN_AUTHORIZATION for ADDRESS, address-bound
 * ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS for ADDRESS_V2 and
 * ADDRESS_WITH_DELEGATES), and that signing preserves the incoming arm.
 */
class SmartAccountAuthArmAwarenessTest {

    private val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val networkPassphrase = Network.TESTNET.networkPassphrase

    private fun baseCredentials(nonce: Long): SorobanAddressCredentialsXdr =
        SorobanAddressCredentialsXdr(
            address = Address(contractAddress).toSCAddress(),
            nonce = Int64Xdr(nonce),
            signatureExpirationLedger = Uint32Xdr(0u),
            signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        )

    private fun addressEntry(nonce: Long = 12345L): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(baseCredentials(nonce)),
            rootInvocation = createTestInvocation(contractAddress)
        )

    private fun addressV2Entry(nonce: Long = 12345L): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressV2(baseCredentials(nonce)),
            rootInvocation = createTestInvocation(contractAddress)
        )

    private fun withDelegatesEntry(nonce: Long = 12345L): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressWithDelegates(
                SorobanAddressCredentialsWithDelegatesXdr(
                    addressCredentials = baseCredentials(nonce),
                    delegates = emptyList()
                )
            ),
            rootInvocation = createTestInvocation(contractAddress)
        )

    private fun voidEntry(): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = createTestInvocation(contractAddress)
        )

    private suspend fun hashOf(preimage: HashIDPreimageXdr): ByteArray {
        val writer = XdrWriter()
        preimage.encode(writer)
        return getSha256Crypto().hash(writer.toByteArray())
    }

    private suspend fun expectedLegacyHash(
        nonce: Long,
        expirationLedger: UInt,
        invocation: SorobanAuthorizedInvocationXdr
    ): ByteArray {
        val networkId = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())
        return hashOf(
            HashIDPreimageXdr.SorobanAuthorization(
                HashIDPreimageSorobanAuthorizationXdr(
                    networkId = HashXdr(networkId),
                    nonce = Int64Xdr(nonce),
                    signatureExpirationLedger = Uint32Xdr(expirationLedger),
                    invocation = invocation
                )
            )
        )
    }

    private suspend fun expectedWithAddressHash(
        nonce: Long,
        expirationLedger: UInt,
        invocation: SorobanAuthorizedInvocationXdr
    ): ByteArray {
        val networkId = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())
        return hashOf(
            HashIDPreimageXdr.SorobanAuthorizationWithAddress(
                HashIDPreimageSorobanAuthorizationWithAddressXdr(
                    networkId = HashXdr(networkId),
                    nonce = Int64Xdr(nonce),
                    signatureExpirationLedger = Uint32Xdr(expirationLedger),
                    address = Address(contractAddress).toSCAddress(),
                    invocation = invocation
                )
            )
        )
    }

    // MARK: - (a) Legacy ADDRESS hash unchanged

    @Test
    fun testBuildAuthPayloadHash_legacyAddressMatchesLegacyPreimage() = runTest {
        val nonce = 42000L
        val expirationLedger = 3_000_000u
        val entry = addressEntry(nonce)

        val actual = SmartAccountAuth.buildAuthPayloadHash(
            entry = entry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        val expected = expectedLegacyHash(nonce, expirationLedger, entry.rootInvocation)

        assertTrue(
            actual.contentEquals(expected),
            "Legacy ADDRESS payload hash must match the legacy SOROBAN_AUTHORIZATION preimage"
        )
    }

    // MARK: - (b) V2 / WITH_DELEGATES use the address-bound preimage

    @Test
    fun testBuildAuthPayloadHash_addressV2MatchesWithAddressPreimage() = runTest {
        val nonce = 42000L
        val expirationLedger = 3_000_000u
        val entry = addressV2Entry(nonce)

        val actual = SmartAccountAuth.buildAuthPayloadHash(
            entry = entry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        val expected = expectedWithAddressHash(nonce, expirationLedger, entry.rootInvocation)

        assertTrue(
            actual.contentEquals(expected),
            "ADDRESS_V2 payload hash must match the SOROBAN_AUTHORIZATION_WITH_ADDRESS preimage"
        )
    }

    @Test
    fun testBuildAuthPayloadHash_v2DiffersFromLegacyForSameInputs() = runTest {
        val nonce = 42000L
        val expirationLedger = 3_000_000u

        val legacyHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = addressEntry(nonce),
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        val v2Hash = SmartAccountAuth.buildAuthPayloadHash(
            entry = addressV2Entry(nonce),
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        assertTrue(
            !legacyHash.contentEquals(v2Hash),
            "V2 (address-bound) hash must differ from the legacy hash for identical nonce/expiration/invocation"
        )
    }

    @Test
    fun testBuildAuthPayloadHash_withDelegatesMatchesWithAddressPreimage() = runTest {
        val nonce = 99000L
        val expirationLedger = 4_000_000u
        val entry = withDelegatesEntry(nonce)

        val actual = SmartAccountAuth.buildAuthPayloadHash(
            entry = entry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        val expected = expectedWithAddressHash(nonce, expirationLedger, entry.rootInvocation)

        assertTrue(
            actual.contentEquals(expected),
            "ADDRESS_WITH_DELEGATES payload hash must match the SOROBAN_AUTHORIZATION_WITH_ADDRESS preimage " +
                "bound to the top-level credential address"
        )
    }

    @Test
    fun testBuildAuthPayloadHash_v2AndWithDelegatesShareTheSameHash() = runTest {
        val nonce = 55000L
        val expirationLedger = 5_000_000u

        val v2Hash = SmartAccountAuth.buildAuthPayloadHash(
            entry = addressV2Entry(nonce),
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        val withDelegatesHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = withDelegatesEntry(nonce),
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )

        assertTrue(
            v2Hash.contentEquals(withDelegatesHash),
            "V2 and WITH_DELEGATES must produce the same address-bound hash for identical top-level credentials"
        )
    }

    // MARK: - (b) signAuthEntry preserves the arm

    private suspend fun signWithFreshKey(
        entry: SorobanAuthorizationEntryXdr,
        expirationLedger: UInt
    ): SorobanAuthorizationEntryXdr {
        val keypair = KeyPair.random()
        val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
            entry = entry,
            expirationLedger = expirationLedger,
            networkPassphrase = networkPassphrase
        )
        val signature = Ed25519Signature(
            publicKey = keypair.getPublicKey(),
            signature = keypair.sign(payloadHash)
        )
        val signer = ExternalSigner.ed25519(
            verifierAddress = contractAddress,
            publicKey = keypair.getPublicKey()
        )
        return SmartAccountAuth.signAuthEntry(
            entry = entry,
            signer = signer,
            signature = signature,
            expirationLedger = expirationLedger
        )
    }

    @Test
    fun testSignAuthEntry_legacyAddressArmIsPreserved() = runTest {
        val signed = signWithFreshKey(addressEntry(), 5_000_000u)
        assertTrue(
            signed.credentials is SorobanCredentialsXdr.Address,
            "Signing a legacy ADDRESS entry must keep the ADDRESS arm"
        )
        val creds = (signed.credentials as SorobanCredentialsXdr.Address).value
        assertEquals(5_000_000u, creds.signatureExpirationLedger.value)
        assertTrue(creds.signature is SCValXdr.Map, "Signature payload must be written as a Map")
    }

    @Test
    fun testSignAuthEntry_addressV2ArmIsPreserved() = runTest {
        val signed = signWithFreshKey(addressV2Entry(), 5_000_000u)
        assertTrue(
            signed.credentials is SorobanCredentialsXdr.AddressV2,
            "Signing an ADDRESS_V2 entry must keep the ADDRESS_V2 arm"
        )
        val creds = (signed.credentials as SorobanCredentialsXdr.AddressV2).value
        assertEquals(5_000_000u, creds.signatureExpirationLedger.value)
        assertTrue(creds.signature is SCValXdr.Map, "Signature payload must be written as a Map")
    }

    @Test
    fun testSignAuthEntry_withDelegatesArmAndDelegatesArePreserved() = runTest {
        val signed = signWithFreshKey(withDelegatesEntry(), 5_000_000u)
        assertTrue(
            signed.credentials is SorobanCredentialsXdr.AddressWithDelegates,
            "Signing an ADDRESS_WITH_DELEGATES entry must keep the ADDRESS_WITH_DELEGATES arm"
        )
        val value = (signed.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        assertEquals(0, value.delegates.size, "Delegates array must be preserved")
        assertEquals(5_000_000u, value.addressCredentials.signatureExpirationLedger.value)
    }

    // MARK: - (c) Void fails fast

    @Test
    fun testBuildAuthPayloadHash_voidFailsFast() = runTest {
        assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuth.buildAuthPayloadHash(
                entry = voidEntry(),
                expirationLedger = 1_000_000u,
                networkPassphrase = networkPassphrase
            )
        }
    }

    @Test
    fun testSignAuthEntry_voidFailsFast() = runTest {
        val keypair = KeyPair.random()
        val signer = ExternalSigner.ed25519(
            verifierAddress = contractAddress,
            publicKey = keypair.getPublicKey()
        )
        val signature = Ed25519Signature(
            publicKey = keypair.getPublicKey(),
            signature = keypair.sign(ByteArray(32))
        )
        assertFailsWith<TransactionException.SigningFailed> {
            SmartAccountAuth.signAuthEntry(
                entry = voidEntry(),
                signer = signer,
                signature = signature,
                expirationLedger = 1_000_000u
            )
        }
    }

    @Test
    fun testAddRawSignatureMapEntry_addressV2ArmIsPreserved() {
        val signerKey = DelegatedSigner("GBVG2QOHHFBVHAEGNF4XRUCAPAGWDROONM2LC4BK4ECCQ5RTQOO64VBW").toScVal()
        val result = SmartAccountAuth.addRawSignatureMapEntry(
            entry = addressV2Entry(),
            signerKey = signerKey,
            signatureValue = Scv.toBytes(ByteArray(0))
        )
        val creds = result.credentials
        assertTrue(
            creds is SorobanCredentialsXdr.AddressV2,
            "addRawSignatureMapEntry must preserve the ADDRESS_V2 arm"
        )
        // The signer was actually appended: the payload starts empty (void signature)
        // and now carries exactly the one signer entry that was added.
        val payload = SmartAccountAuthPayloadCodec.read(creds.value.signature)
        assertEquals(1, payload.signers.size, "the added signer must be present in the payload")
    }
}
