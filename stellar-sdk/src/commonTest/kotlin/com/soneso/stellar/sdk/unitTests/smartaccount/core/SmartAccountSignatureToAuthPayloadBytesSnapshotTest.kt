//
//  SmartAccountSignatureToAuthPayloadBytesSnapshotTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.core

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.Ed25519Signature
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.PolicySignature
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuth
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnSignature
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Snapshot regression tests for [SmartAccountSignature.toAuthPayloadBytes].
 *
 * Pins the exact byte output at two levels of granularity:
 *
 * Level 1 — direct output of [toAuthPayloadBytes] for fixed [WebAuthnSignature],
 * [PolicySignature], and [Ed25519Signature] fixtures. Any accidental change to the XDR
 * encoding helper or to the ScVal structure will surface here.
 *
 * Level 2 — full [SmartAccountAuth.signAuthEntry] output bytes for the WebAuthn and Policy
 * fixtures. After the polymorphic dispatch refactor, these must remain byte-identical to the
 * pre-refactor baseline. A divergence indicates the new dispatch path produces different bytes.
 *
 * Hex literals were captured from a first run and pinned. Deliberate changes to the XDR
 * encoding must update these literals.
 */
class SmartAccountSignatureToAuthPayloadBytesSnapshotTest {

    private val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val networkPassphrase = Network.TESTNET.networkPassphrase

    // ========================================================================
    // Pinned hex baselines (captured from first run, stable across runs)
    // ========================================================================

    /**
     * XDR encoding of the 3-field authenticator_data / client_data / signature Map SCVal
     * produced by [WebAuthnSignature.toAuthPayloadBytes] for the fixed fixture.
     */
    private val EXPECTED_WEBAUTHN_L1_HEX = "0000001100000001000000030000000f0000001261757468656e74696361746f725f6461746100000000000d000000250102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2021222324250000000000000f0000000b636c69656e745f64617461000000000d000000640a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d0000000f000000097369676e61747572650000000000000d0000004032333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f7071"

    /**
     * XDR encoding of the empty Map SCVal produced by [PolicySignature.toAuthPayloadBytes].
     */
    private val EXPECTED_POLICY_L1_HEX = "000000110000000100000000"

    /**
     * Raw 64 signature bytes returned by [Ed25519Signature.toAuthPayloadBytes] for the fixed
     * fixture. Ed25519 returns raw bytes without XDR wrapping.
     * Fixture: `ByteArray(64) { (it + 5).toByte() }` — bytes 0x05 through 0x44.
     */
    private val EXPECTED_ED25519_L1_HEX = "05060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f4041424344"

    /**
     * Full [SorobanAuthorizationEntryXdr] XDR produced by [SmartAccountAuth.signAuthEntry]
     * for the WebAuthn fixture.
     */
    private val EXPECTED_WEBAUTHN_L2_HEX = "000000010000000100000000000000000000000000000000000000000000000000000000000000010000000000003039004c4b400000001100000001000000020000000f00000010636f6e746578745f72756c655f6964730000001000000001000000000000000f000000077369676e657273000000001100000001000000010000001000000001000000030000000f0000000845787465726e616c000000120000000100000000000000000000000000000000000000000000000000000000000000010000000d0000002001010101010101010101010101010101010101010101010101010101010101010000000d000001340000001100000001000000030000000f0000001261757468656e74696361746f725f6461746100000000000d000000250102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2021222324250000000000000f0000000b636c69656e745f64617461000000000d000000640a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d0000000f000000097369676e61747572650000000000000d0000004032333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707100000000000000010000000000000000000000000000000000000000000000000000000000000001000000087472616e736665720000000000000000"

    /**
     * Full [SorobanAuthorizationEntryXdr] XDR produced by [SmartAccountAuth.signAuthEntry]
     * for the Policy fixture.
     */
    private val EXPECTED_POLICY_L2_HEX = "000000010000000100000000000000000000000000000000000000000000000000000000000000010000000000003039004c4b400000001100000001000000020000000f00000010636f6e746578745f72756c655f6964730000001000000001000000000000000f000000077369676e657273000000001100000001000000010000001000000001000000030000000f0000000845787465726e616c000000120000000100000000000000000000000000000000000000000000000000000000000000010000000d0000002001010101010101010101010101010101010101010101010101010101010101010000000d0000000c00000011000000010000000000000000000000010000000000000000000000000000000000000000000000000000000000000001000000087472616e736665720000000000000000"

    // ========================================================================
    // Fixed fixtures
    // ========================================================================

    private fun webAuthnFixture(): WebAuthnSignature = WebAuthnSignature(
        authenticatorData = ByteArray(37) { (it + 1).toByte() },
        clientData = ByteArray(100) { (it + 10).toByte() },
        signature = ByteArray(64) { (it + 50).toByte() }
    )

    private fun policyFixture(): PolicySignature = PolicySignature

    private fun createTestAuthEntry(): SorobanAuthorizationEntryXdr {
        val scAddress = Address(contractAddress).toSCAddress()
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
            nonce = Int64Xdr(12345L),
            signatureExpirationLedger = Uint32Xdr(0u),
            signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        )
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(credentials),
            rootInvocation = invocation
        )
    }

    // ========================================================================
    // Level 1 — direct toAuthPayloadBytes() snapshot
    // ========================================================================

    /**
     * Pins the hex output of [WebAuthnSignature.toAuthPayloadBytes] against a captured baseline.
     *
     * The expected hex encodes the 3-field authenticator_data / client_data / signature Map SCVal.
     * Any regression in [encodeScValToBytes] or the Map field ordering will break this test.
     */
    @Test
    fun testWebAuthnToAuthPayloadBytes_snapshotHex() {
        val sig = webAuthnFixture()
        val actualBytes = sig.toAuthPayloadBytes()
        val actualHex = Util.bytesToHex(actualBytes)

        // Structural sanity: bytes must decode back to a Map ScVal
        val decoded = SCValXdr.decode(com.soneso.stellar.sdk.xdr.XdrReader(actualBytes))
        assertTrue(decoded is SCValXdr.Map, "WebAuthn auth payload bytes must decode to Map")

        assertEquals(
            EXPECTED_WEBAUTHN_L1_HEX,
            actualHex,
            "WebAuthn toAuthPayloadBytes() hex must match pinned baseline"
        )
    }

    /**
     * Pins the hex output of [PolicySignature.toAuthPayloadBytes] against a captured baseline.
     *
     * The expected hex encodes an empty Map SCVal.
     */
    @Test
    fun testPolicyToAuthPayloadBytes_snapshotHex() {
        val actualBytes = policyFixture().toAuthPayloadBytes()
        val actualHex = Util.bytesToHex(actualBytes)

        // Structural sanity: bytes must decode back to an empty Map ScVal
        val decoded = SCValXdr.decode(com.soneso.stellar.sdk.xdr.XdrReader(actualBytes))
        assertTrue(decoded is SCValXdr.Map, "Policy auth payload bytes must decode to Map")
        assertEquals(0, (decoded as SCValXdr.Map).value!!.value.size, "Policy map must be empty")

        assertEquals(
            EXPECTED_POLICY_L1_HEX,
            actualHex,
            "Policy toAuthPayloadBytes() hex must match pinned baseline"
        )
    }

    /**
     * Pins the hex output of [Ed25519Signature.toAuthPayloadBytes] against a captured baseline.
     *
     * Ed25519 returns raw signature bytes without XDR wrapping. The baseline is the literal
     * byte sequence 0x05..0x44 (fixture: `ByteArray(64) { (it + 5).toByte() }`).
     */
    @Test
    fun testEd25519ToAuthPayloadBytes_snapshotHex() {
        val rawSig = ByteArray(64) { (it + 5).toByte() }
        val sig = Ed25519Signature(
            publicKey = ByteArray(32) { (it + 1).toByte() },
            signature = rawSig
        )
        val actualBytes = sig.toAuthPayloadBytes()
        val actualHex = Util.bytesToHex(actualBytes)

        // Ed25519 must return the raw bytes without any XDR wrapping
        assertTrue(rawSig.contentEquals(actualBytes), "Ed25519 toAuthPayloadBytes() must equal the raw signature field")
        assertEquals(64, actualBytes.size, "Ed25519 payload bytes must be exactly 64")

        assertEquals(
            EXPECTED_ED25519_L1_HEX,
            actualHex,
            "Ed25519 toAuthPayloadBytes() hex must match pinned baseline"
        )
    }

    // ========================================================================
    // Level 2 — full signAuthEntry output snapshot
    // ========================================================================

    /**
     * Pins the full XDR output of [SmartAccountAuth.signAuthEntry] for a [WebAuthnSignature].
     *
     * Asserts both determinism (two calls produce identical bytes) and byte stability against
     * the pinned baseline. A divergence from the baseline indicates the polymorphic dispatch
     * path through [toAuthPayloadBytes] produces different bytes than the pre-refactor inline
     * encoding.
     */
    @Test
    fun testSignAuthEntry_webauthn_snapshotIsStable() = runTest {
        val sig = webAuthnFixture()
        val expirationLedger = 5000000u
        val authEntry = createTestAuthEntry()
        val verifierAddress = contractAddress
        val signer = ExternalSigner(
            verifierAddress = verifierAddress,
            keyData = ByteArray(32) { 0x01 }
        )

        val signedEntry1 = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = sig,
            expirationLedger = expirationLedger
        )
        val signedEntry2 = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = sig,
            expirationLedger = expirationLedger
        )

        val writer1 = XdrWriter()
        signedEntry1.encode(writer1)
        val bytes1 = writer1.toByteArray()

        val writer2 = XdrWriter()
        signedEntry2.encode(writer2)
        val bytes2 = writer2.toByteArray()

        // Determinism check
        assertTrue(bytes1.contentEquals(bytes2), "signAuthEntry must produce stable output for WebAuthn")
        assertTrue(bytes1.size > 50, "Signed entry XDR must be non-trivial")

        // Byte-stability against pinned baseline
        assertEquals(
            EXPECTED_WEBAUTHN_L2_HEX,
            Util.bytesToHex(bytes1),
            "signAuthEntry WebAuthn output must match pinned baseline"
        )
    }

    /**
     * Pins the full XDR output of [SmartAccountAuth.signAuthEntry] for a [PolicySignature].
     *
     * Asserts both determinism and byte stability against the pinned baseline.
     */
    @Test
    fun testSignAuthEntry_policy_snapshotIsStable() = runTest {
        val sig = policyFixture()
        val expirationLedger = 5000000u
        val authEntry = createTestAuthEntry()
        val verifierAddress = contractAddress
        val signer = ExternalSigner(
            verifierAddress = verifierAddress,
            keyData = ByteArray(32) { 0x01 }
        )

        val signedEntry1 = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = sig,
            expirationLedger = expirationLedger
        )
        val signedEntry2 = SmartAccountAuth.signAuthEntry(
            entry = authEntry,
            signer = signer,
            signature = sig,
            expirationLedger = expirationLedger
        )

        val writer1 = XdrWriter()
        signedEntry1.encode(writer1)
        val bytes1 = writer1.toByteArray()

        val writer2 = XdrWriter()
        signedEntry2.encode(writer2)
        val bytes2 = writer2.toByteArray()

        // Determinism check
        assertTrue(bytes1.contentEquals(bytes2), "signAuthEntry must produce stable output for Policy")
        assertTrue(bytes1.size > 50, "Signed entry XDR must be non-trivial")

        // Byte-stability against pinned baseline
        assertEquals(
            EXPECTED_POLICY_L2_HEX,
            Util.bytesToHex(bytes1),
            "signAuthEntry Policy output must match pinned baseline"
        )
    }

    /**
     * Verifies that [WebAuthnSignature.toAuthPayloadBytes] produces the same bytes as
     * XDR-encoding [WebAuthnSignature.toScVal] — the shared [encodeScValToBytes] helper must
     * be equivalent to a direct XDR encode round-trip.
     */
    @Test
    fun testWebAuthnToAuthPayloadBytes_matchesDirectXdrEncode() {
        val sig = webAuthnFixture()

        val writer = XdrWriter()
        sig.toScVal().encode(writer)
        val directBytes = writer.toByteArray()

        val payloadBytes = sig.toAuthPayloadBytes()
        assertTrue(
            directBytes.contentEquals(payloadBytes),
            "toAuthPayloadBytes() must equal direct XDR-encode of toScVal()"
        )
    }

    /**
     * Verifies that [PolicySignature.toAuthPayloadBytes] produces the same bytes as
     * XDR-encoding [PolicySignature.toScVal].
     */
    @Test
    fun testPolicyToAuthPayloadBytes_matchesDirectXdrEncode() {
        val writer = XdrWriter()
        PolicySignature.toScVal().encode(writer)
        val directBytes = writer.toByteArray()

        val payloadBytes = PolicySignature.toAuthPayloadBytes()
        assertTrue(
            directBytes.contentEquals(payloadBytes),
            "toAuthPayloadBytes() must equal direct XDR-encode of toScVal()"
        )
    }
}
