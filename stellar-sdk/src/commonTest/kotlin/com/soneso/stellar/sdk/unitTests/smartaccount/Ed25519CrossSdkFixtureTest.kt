//
//  Ed25519CrossSdkFixtureTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.Ed25519Signature
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuth
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

// ---------------------------------------------------------------------------
// Cross-SDK byte-equivalence tests
// ---------------------------------------------------------------------------

/**
 * Verifies byte-identical outputs across SDK implementations using a shared
 * deterministic fixture.
 *
 * The fixture contains three rows covering:
 *  - Row 0: single signer, contextRuleIds=[0], verifierAddressAlpha
 *  - Row 1: two rule IDs contextRuleIds=[0,1], same verifier and key
 *  - Row 2: same public key under a different verifier address, contextRuleIds=[0]
 *
 * For each row the test asserts byte equality between the KMP-computed values
 * and the fixture's expected outputs:
 *  1. authPreimageXdrBase64  — XDR encoding of HashIDPreimage::SorobanAuthorization
 *  2. authDigestSha256Hex    — SHA-256(signaturePayload || contextRuleIds.toXDR())
 *  3. authPayloadSignatureScvalXdrBase64 — XDR encoding of the signed credentials payload ScVal
 */
@OptIn(ExperimentalEncodingApi::class)
class Ed25519CrossSdkFixtureTest {

    @Test
    fun test_crossSdkFixture_row0_producesByteIdenticalOutputs() = runTest {
        val rows = loadFixtureRows()
        assertFixtureRow(rows, rowIndex = 0)
    }

    @Test
    fun test_crossSdkFixture_row1_producesByteIdenticalOutputs() = runTest {
        val rows = loadFixtureRows()
        assertFixtureRow(rows, rowIndex = 1)
    }

    @Test
    fun test_crossSdkFixture_row2_producesByteIdenticalOutputs() = runTest {
        val rows = loadFixtureRows()
        assertFixtureRow(rows, rowIndex = 2)
    }

    // ========================================================================
    // Implementation
    // ========================================================================

    private fun loadFixtureRows(): JsonArray {
        val bytes = TestResourceUtil.readFixtureBytes("ed25519_cross_sdk_fixture.json")
        val jsonString = bytes.decodeToString()
        return Json.parseToJsonElement(jsonString).jsonArray
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun assertFixtureRow(rows: JsonArray, rowIndex: Int) {
        val row = rows[rowIndex].jsonObject

        val inputs = row["inputs"]!!.jsonObject
        val outputs = row["outputs"]!!.jsonObject
        val signerInfo = row["signerInfo"]!!.jsonObject

        val networkPassphrase = inputs["networkPassphrase"]!!.jsonPrimitive.content
        val nonce = inputs["nonce"]!!.jsonPrimitive.content.toLong()
        val signatureExpirationLedger = inputs["signatureExpirationLedger"]!!.jsonPrimitive.int.toUInt()
        val invocationXdrBase64 = inputs["invocationXdrBase64"]!!.jsonPrimitive.content
        val contextRuleIds = inputs["contextRuleIds"]!!.jsonArray
            .map { it.jsonPrimitive.int.toUInt() }

        val expectedPreimageB64 = outputs["authPreimageXdrBase64"]!!.jsonPrimitive.content
        val expectedDigestHex = outputs["authDigestSha256Hex"]!!.jsonPrimitive.content
        val expectedPayloadB64 = outputs["authPayloadSignatureScvalXdrBase64"]!!.jsonPrimitive.content

        val secretKeyHex = signerInfo["secretKeyHex"]!!.jsonPrimitive.content
        val publicKeyHex = signerInfo["publicKeyHex"]!!.jsonPrimitive.content
        val verifierAddress = signerInfo["verifierAddress"]!!.jsonPrimitive.content

        // Derive keypair and verify the public key matches the fixture.
        val rawSeed = Util.hexToBytes(secretKeyHex)
        val keypair = KeyPair.fromSecretSeed(rawSeed)
        val publicKey = keypair.getPublicKey()

        assertEquals(
            publicKeyHex,
            Util.bytesToHex(publicKey),
            "Row $rowIndex: derived public key must match fixture publicKeyHex"
        )

        // ----------------------------------------------------------------
        // Step 1: Decode the invocation from XDR.
        // ----------------------------------------------------------------
        val invocationBytes = Base64.decode(invocationXdrBase64)
        val invocation = SorobanAuthorizedInvocationXdr.decode(XdrReader(invocationBytes))

        // ----------------------------------------------------------------
        // Step 2: Build the auth preimage and encode it.
        // ----------------------------------------------------------------
        val networkId = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())

        val authPreimage = HashIDPreimageSorobanAuthorizationXdr(
            networkId = HashXdr(networkId),
            nonce = Int64Xdr(nonce),
            signatureExpirationLedger = Uint32Xdr(signatureExpirationLedger),
            invocation = invocation
        )
        val preimageWrapper = HashIDPreimageXdr.SorobanAuthorization(authPreimage)

        val preimageWriter = XdrWriter()
        preimageWrapper.encode(preimageWriter)
        val preimageBytes = preimageWriter.toByteArray()
        val actualPreimageB64 = Base64.encode(preimageBytes)

        // ----------------------------------------------------------------
        // Step 3: Compute the auth digest.
        //   signaturePayload = SHA-256(preimageBytes)
        //   authDigest       = SHA-256(signaturePayload || contextRuleIds.toXDR())
        // ----------------------------------------------------------------
        val signaturePayload = getSha256Crypto().hash(preimageBytes)
        val authDigest = SmartAccountAuth.buildAuthDigest(signaturePayload, contextRuleIds)
        val actualDigestHex = Util.bytesToHex(authDigest)

        // ----------------------------------------------------------------
        // Step 4: Sign the auth digest and construct the signed auth entry.
        // ----------------------------------------------------------------
        val rawSignature = keypair.sign(authDigest)

        val ed25519Sig = Ed25519Signature(publicKey = publicKey, signature = rawSignature)
        val ed25519Signer = ExternalSigner.ed25519(
            verifierAddress = verifierAddress,
            publicKey = publicKey
        )

        // Build a minimal auth entry with address credentials (signature = Void).
        val contractAddress = Address(verifierAddress).toSCAddress()
        val credentials = SorobanAddressCredentialsXdr(
            address = contractAddress,
            nonce = Int64Xdr(nonce),
            signatureExpirationLedger = Uint32Xdr(signatureExpirationLedger),
            signature = Scv.toVoid()
        )
        val baseEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(credentials),
            rootInvocation = invocation
        )

        val signedEntry = SmartAccountAuth.signAuthEntry(
            entry = baseEntry,
            signer = ed25519Signer,
            signature = ed25519Sig,
            expirationLedger = signatureExpirationLedger,
            contextRuleIds = contextRuleIds
        )

        // ----------------------------------------------------------------
        // Step 5: Extract the payload ScVal from signed credentials and encode.
        // ----------------------------------------------------------------
        val signedCredentials = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        val payloadScVal: SCValXdr = signedCredentials.signature

        val payloadWriter = XdrWriter()
        payloadScVal.encode(payloadWriter)
        val actualPayloadB64 = Base64.encode(payloadWriter.toByteArray())

        // ----------------------------------------------------------------
        // Assertions with byte-level diff messages
        // ----------------------------------------------------------------
        assertEquals(
            expectedPreimageB64,
            actualPreimageB64,
            "Row $rowIndex: authPreimageXdrBase64 mismatch.\n" +
                "  KMP     : $actualPreimageB64\n" +
                "  Fixture : $expectedPreimageB64"
        )

        assertEquals(
            expectedDigestHex,
            actualDigestHex,
            "Row $rowIndex: authDigestSha256Hex mismatch.\n" +
                "  KMP     : $actualDigestHex\n" +
                "  Fixture : $expectedDigestHex"
        )

        assertEquals(
            expectedPayloadB64,
            actualPayloadB64,
            "Row $rowIndex: authPayloadSignatureScvalXdrBase64 mismatch.\n" +
                "  KMP     : $actualPayloadB64\n" +
                "  Fixture : $expectedPayloadB64"
        )
    }
}
