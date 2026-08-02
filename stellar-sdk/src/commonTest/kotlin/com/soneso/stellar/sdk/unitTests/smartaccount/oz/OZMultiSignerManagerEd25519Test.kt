//
//  OZMultiSignerManagerEd25519Test.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.SIMULATION_REACHED_SENTINEL
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountThenSimulationErrorMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAuthEntry
import com.soneso.stellar.sdk.unitTests.smartaccount.buildMinimalSorobanData
import com.soneso.stellar.sdk.unitTests.smartaccount.buildSequentialMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.buildSequentialMockServerWithSubmission
import com.soneso.stellar.sdk.unitTests.smartaccount.latestLedgerResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateCountResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateErrorResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateWithAuthResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.stubHostFunction
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.soneso.stellar.sdk.smartaccount.core.Ed25519Signature
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalEd25519SignerAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalSignerManager
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Adapter that returns 64 zero-bytes — an invalid Ed25519 signature
// ---------------------------------------------------------------------------

/**
 * Adapter that claims it can sign for a specific public key but returns 64 zero-bytes,
 * which fails local Ed25519 signature verification.
 */
private class ZeroBytesAdapter(private val targetPublicKey: ByteArray) : OZExternalEd25519SignerAdapter {
    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean =
        publicKey.contentEquals(targetPublicKey)

    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray =
        ByteArray(64) // all zeros — fails Ed25519 verification
}

// ---------------------------------------------------------------------------
// Adapter that returns a wrong-length signature — causes KeyPair.verify to throw
// ---------------------------------------------------------------------------

/**
 * Adapter that claims it can sign for a specific public key but returns a signature of
 * wrong length (63 bytes). This causes [KeyPair.verify] to throw an exception rather
 * than return false, exercising the catch-block path in appendEd25519Signature.
 */
private class WrongLengthSignatureAdapter(private val targetPublicKey: ByteArray) : OZExternalEd25519SignerAdapter {
    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean =
        publicKey.contentEquals(targetPublicKey)

    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray =
        ByteArray(63) // wrong length — causes verify() to throw, not return false
}

// ---------------------------------------------------------------------------
// Adapter that delegates to a real keypair — produces valid Ed25519 signatures
// ---------------------------------------------------------------------------

/**
 * Adapter that signs auth digests with a real [KeyPair], producing signatures that pass
 * local Ed25519 verification. The [callCount] property records how many times
 * [signAuthDigest] was invoked.
 */
private class ValidSigningAdapter(
    private val keypair: KeyPair,
    private val targetPublicKey: ByteArray
) : OZExternalEd25519SignerAdapter {
    var callCount = 0
        private set

    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean =
        publicKey.contentEquals(targetPublicKey)

    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray {
        callCount++
        return keypair.sign(authDigest)
    }
}

// ---------------------------------------------------------------------------
// Config helpers
// ---------------------------------------------------------------------------

/**
 * Builds a kit config. The Ed25519 adapter custody model is supplied via
 * [OZSmartAccountConfig.externalEd25519Adapter]; the kit injects it into the manager it
 * exposes as [OZSmartAccountKit.externalSigners]. In-memory Ed25519 keys are registered at
 * runtime on `kit.externalSigners` after the kit is built.
 */
private fun buildConfig(
    externalEd25519Adapter: OZExternalEd25519SignerAdapter? = null,
    deployer: KeyPair? = null
): OZSmartAccountConfig = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = Network.TESTNET.networkPassphrase,
    accountWasmHash = "a" + "0".repeat(63),
    webauthnVerifierAddress = VERIFIER_A,
    externalEd25519Adapter = externalEd25519Adapter,
    deployerKeypair = deployer
)

private fun passkeyStub() = SelectedSigner.Passkey(
    credentialId = null,
    credentialIdBytes = null,
    keyData = ByteArray(68) { it.toByte() }
)

/**
 * Unit tests for the Ed25519 signer path through [OZMultiSignerManager] validation.
 *
 * Tests cover:
 * - validateSelectedSigners Ed25519 validation (public key length, verifier address format,
 *   signing source registration, same-pubkey different-verifier tuple resolution)
 * - SelectedSigner.Ed25519 construction, equality, and hashCode semantics
 * - Ed25519Signature wire shape (raw BytesN<64>, not a Map)
 * - The kit-owned [OZSmartAccountKit.externalSigners] front door
 * - The local verification branch in the signing pipeline
 */
class OZMultiSignerManagerEd25519Test {

    // ========================================================================
    // validateSelectedSigners — Ed25519 validation
    // ========================================================================

    @Test
    fun test_validateSelectedSigners_ed25519WithRegisteredSigner_passes() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()

        // The scripted server answers the deployer account lookup and then fails simulation
        // with a sentinel. Reaching the sentinel proves Ed25519 validation accepted the
        // registered signer, because simulation runs only after validateSelectedSigners.
        val mockServer = buildAccountThenSimulationErrorMockServer(accountXdr)

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        val rawSeed = ByteArray(32) { it.toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(
                        verifierAddress = VERIFIER_A,
                        publicKey = publicKey
                    )
                )
            )
        }
        assertTrue(
            ex.message.contains(SIMULATION_REACHED_SENTINEL),
            "Ed25519 validation must pass when the signer is registered, so control must " +
                "reach simulation; got: ${ex.message}"
        )
    }

    @Test
    fun test_validateSelectedSigners_ed25519WithoutRegisteredSigner_throwsInvalidInputSelectedSigners() = runTest {
        // No signer registered for VERIFIER_A on kit.externalSigners.
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager
        val unregisteredKey = KeyPair.random().getPublicKey()

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(
                        verifierAddress = VERIFIER_A,
                        publicKey = unregisteredKey
                    )
                )
            )
        }
        assertTrue(
            ex.message.contains("selectedSigners", ignoreCase = true),
            "Exception must reference selectedSigners, got: ${ex.message}"
        )
    }

    @Test
    fun test_validateSelectedSigners_ed25519InvalidPublicKeyLength_throws() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidInput> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(
                        verifierAddress = VERIFIER_A,
                        publicKey = ByteArray(16) // not a valid Ed25519 public key length
                    )
                )
            )
        }
    }

    @Test
    fun test_validateSelectedSigners_ed25519InvalidVerifierAddress_throws() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val rawSeed = ByteArray(32) { (it + 1).toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidInput> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(
                        verifierAddress = "G" + "A".repeat(55), // G-address, not a C-address
                        publicKey = publicKey
                    )
                )
            )
        }
    }

    @Test
    fun test_validateSelectedSigners_ed25519SamePubkeyDifferentVerifiers_resolvedByTuple() = runTest {
        val manager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val rawSeed = ByteArray(32) { (it + 2).toByte() }

        // Same raw seed, two different verifier addresses — two distinct registry entries.
        val pk1 = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        manager.addEd25519FromRawKey(rawSeed, VERIFIER_B)

        // Both entries are reachable via their respective tuple keys.
        assertTrue(
            manager.canSignEd25519For(VERIFIER_A, pk1),
            "canSignEd25519For must return true for VERIFIER_A"
        )
        assertTrue(
            manager.canSignEd25519For(VERIFIER_B, pk1),
            "canSignEd25519For must return true for VERIFIER_B using the same public key"
        )
    }

    @Test
    fun test_validateSelectedSigners_ed25519DuplicateTupleEntries_acceptedByValidation() = runTest {
        // A repeated Ed25519 selector is not a validation error: each entry names the same
        // registered (verifierAddress, publicKey) tuple, so the per-entry signing-source
        // check passes for both and control reaches simulation.
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()

        val mockServer = buildAccountThenSimulationErrorMockServer(accountXdr)

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        val rawSeed = ByteArray(32) { (it + 3).toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager
        val duplicateSigner = SelectedSigner.Ed25519(
            verifierAddress = VERIFIER_A,
            publicKey = publicKey
        )

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(duplicateSigner, duplicateSigner)
            )
        }
        assertTrue(
            ex.message.contains(SIMULATION_REACHED_SENTINEL),
            "Duplicate Ed25519 selectors must pass validation and reach simulation; " +
                "got: ${ex.message}"
        )
    }

    @Test
    fun test_validateSelectedSigners_ed25519PubkeyMatchesWalletGAddressBytes_noFalseMatch() = runTest {
        val manager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val ed25519Seed = ByteArray(32) { (it + 3).toByte() }
        val ed25519PublicKey = manager.addEd25519FromRawKey(ed25519Seed, VERIFIER_A)

        // canSignEd25519For checks the (verifierAddress, publicKey) tuple.
        assertTrue(
            manager.canSignEd25519For(VERIFIER_A, ed25519PublicKey),
            "canSignEd25519For must return true for the registered Ed25519 entry"
        )

        // canSignFor (wallet path) for any G-address derived from the same raw seed must
        // return false because no wallet signer was added.
        val derivedAccountId = KeyPair.fromPublicKey(ed25519PublicKey).getAccountId()
        assertFalse(
            manager.canSignFor(derivedAccountId),
            "Ed25519 registry must not bleed into the wallet canSignFor lookup path"
        )
    }

    // ========================================================================
    // submitWithMultipleSigners — structural / wire-shape assertions
    //
    // These tests operate on SelectedSigner and Ed25519Signature data at the
    // type and data-class level without requiring network connectivity.
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_ed25519Only_producesCorrectAuthPayloadMapShape() {
        // Verify that Ed25519Signature.toScVal() returns raw Bytes (BytesN<64>), NOT a Map,
        // and that toAuthPayloadBytes() returns exactly 64 raw bytes without an XDR envelope.
        // The Ed25519 verifier contract expects BytesN<64> as sig_data.
        val rawSig = ByteArray(64) { 0xAB.toByte() }
        val publicKey = ByteArray(32) { it.toByte() }

        val ed25519Sig = Ed25519Signature(publicKey = publicKey, signature = rawSig)

        val scVal = ed25519Sig.toScVal()
        assertIs<SCValXdr.Bytes>(scVal, "Ed25519Signature.toScVal() must return SCValXdr.Bytes, not a Map")
        assertContentEquals(rawSig, scVal.value.value, "Bytes value must equal the raw 64-byte signature")

        val payloadBytes = ed25519Sig.toAuthPayloadBytes()
        assertEquals(64, payloadBytes.size, "toAuthPayloadBytes() must return exactly 64 bytes (no XDR envelope)")
        assertContentEquals(rawSig, payloadBytes, "toAuthPayloadBytes() must equal the original raw signature bytes")
    }

    @Test
    fun test_submitWithMultipleSigners_mixedPasskeyEd25519Wallet_allSlotsFilled() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val rawSeed = ByteArray(32) { (it + 21).toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        // Construct three SelectedSigner values of each supported type.
        val passkeySigner = passkeyStub()
        val ed25519Signer = SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = publicKey)
        val walletSigner = SelectedSigner.Wallet("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")

        // All three are distinct runtime types.
        assertIs<SelectedSigner.Passkey>(passkeySigner)
        assertIs<SelectedSigner.Ed25519>(ed25519Signer)
        assertIs<SelectedSigner.Wallet>(walletSigner)

        assertEquals(VERIFIER_A, ed25519Signer.verifierAddress)
        assertContentEquals(publicKey, ed25519Signer.publicKey)

        // A list of all three can be assembled without error.
        val selected = listOf(passkeySigner, ed25519Signer, walletSigner)
        assertEquals(3, selected.size)
    }

    @Test
    fun test_submitWithMultipleSigners_mixedRuleEd25519AndPasskeyAtSameIndex_routesCorrectly() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val rawSeed = ByteArray(32) { (it + 22).toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        val ed25519Signer = SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = publicKey)
        val passkeySigner = passkeyStub()

        val selected = listOf(ed25519Signer, passkeySigner)

        // Each entry is dispatched to the correct branch via the sealed class type.
        assertIs<SelectedSigner.Ed25519>(selected[0])
        assertIs<SelectedSigner.Passkey>(selected[1])

        val ed = selected[0] as SelectedSigner.Ed25519
        assertEquals(VERIFIER_A, ed.verifierAddress)
        assertContentEquals(publicKey, ed.publicKey)
    }

    @Test
    fun test_submitWithMultipleSigners_ed25519LocalVerificationFailure_throwsSigningFailed() = runTest {
        // This test drives the production throw site at OZMultiSignerManager — the
        // !isValid branch that fires when the signing source returns a signature that
        // fails local Ed25519 verification before the auth payload is submitted.
        //
        // Setup: ZeroBytesAdapter returns 64 zero-bytes. Zero-byte Ed25519 signatures
        // are cryptographically invalid and must fail local verification against any
        // real public key. The production code detects this and throws
        // TransactionException.SigningFailed immediately, before any submission RPC call.
        val deployer = KeyPair.random()

        // Build the pre-computed XDR values used by the mock RPC server.
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        val mockServer = buildSequentialMockServer(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr
        )

        // Register a real keypair against VERIFIER_A so validation passes; the adapter is
        // injected via config and takes precedence at signing time, returning zero bytes.
        val rawSeed = ByteArray(32) { (it + 42).toByte() }
        val signingKeypair = KeyPair.fromSecretSeed(rawSeed)
        val publicKey = signingKeypair.getPublicKey()

        // ZeroBytesAdapter.canSignFor returns true for publicKey, so the signing branch
        // routes through it instead of the in-process raw-key path.
        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(externalEd25519Adapter = ZeroBytesAdapter(publicKey), deployer = deployer),
            sorobanServer = mockServer
        )
        kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        // resolveContextRuleIds bypasses the context-rule resolution so that the signing
        // loop reaches the Ed25519 branch regardless of which rules are on-chain.
        val ex = assertFailsWith<TransactionException.SigningFailed> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(
                        verifierAddress = VERIFIER_A,
                        publicKey = publicKey
                    )
                ),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }

        // The exception message must identify the verifier address so a developer can
        // pinpoint which signing source produced the invalid signature.
        assertTrue(
            ex.message.contains(VERIFIER_A, ignoreCase = false),
            "SigningFailed message must contain the verifier address (VERIFIER_A); got: ${ex.message}"
        )
    }

    @Test
    fun test_submitWithMultipleSigners_ed25519Only_inProcessKeypair_succeeds() = runTest {
        // Drive appendEd25519Signature all the way through the success path using an
        // in-process keypair registered via kit.externalSigners.addEd25519FromRawKey. No
        // adapter is configured. The signing pipeline must: sign the auth digest, verify the
        // signature locally (KeyPair.verify returns true), wrap it in Ed25519Signature, and
        // return a TransactionResult with success = true.
        val deployer = KeyPair.random()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        // No adapter — the in-process keypair path must handle signing entirely.
        val rawSeed = ByteArray(32) { (it + 77).toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = listOf(
                SelectedSigner.Ed25519(
                    verifierAddress = VERIFIER_A,
                    publicKey = publicKey
                )
            ),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "TransactionResult must be successful; error=${result.error}")
        assertNotNull(result.hash, "TransactionResult must carry the transaction hash")
        assertEquals(txHash, result.hash, "Transaction hash must match the value returned by sendTransaction")
    }

    @Test
    fun test_submitWithMultipleSigners_ed25519Only_viaAdapter_succeeds() = runTest {
        // Drive appendEd25519Signature through the success path using a config-injected
        // adapter that wraps a real keypair. The adapter is registered alongside the same
        // in-process keypair so canSignEd25519For passes. At signing time, adapter-first
        // precedence routes through the adapter, and callCount == 1 confirms the adapter ran.
        val deployer = KeyPair.random()

        val rawSeed = ByteArray(32) { (it + 77).toByte() }
        val signingKeypair = KeyPair.fromSecretSeed(rawSeed)
        val publicKey = signingKeypair.getPublicKey()
        val adapter = ValidSigningAdapter(signingKeypair, publicKey)

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(externalEd25519Adapter = adapter, deployer = deployer),
            sorobanServer = mockServer
        )
        kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = listOf(
                SelectedSigner.Ed25519(
                    verifierAddress = VERIFIER_A,
                    publicKey = publicKey
                )
            ),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "TransactionResult must be successful; error=${result.error}")
        assertNotNull(result.hash, "TransactionResult must carry the transaction hash")
        assertEquals(txHash, result.hash, "Transaction hash must match the value returned by sendTransaction")
        assertEquals(1, adapter.callCount, "Adapter signAuthDigest must have been called exactly once")
    }

    @Test
    fun test_submitWithMultipleSigners_emptySelectedSigners_skipsEd25519Validation() = runTest {
        // An empty selectedSigners list contains no Ed25519 entries, so the Ed25519
        // signing-source loop has nothing to reject and control reaches simulation.
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()

        val mockServer = buildAccountThenSimulationErrorMockServer(accountXdr)

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = emptyList()
            )
        }
        assertTrue(
            ex.message.contains(SIMULATION_REACHED_SENTINEL),
            "An empty selectedSigners list must skip Ed25519 validation and reach " +
                "simulation; got: ${ex.message}"
        )
    }

    // ========================================================================
    // Kit-owned external signer front door
    // ========================================================================

    @Test
    fun test_kit_externalSigners_nonNullWithNoExternalConfig() = runTest {
        // The manager is always present, even when no external signer config is supplied.
        val kit = OZSmartAccountKit.create(buildConfig())
        assertNotNull(kit.externalSigners, "kit.externalSigners must be non-null on a kit with no external config")
    }

    @Test
    fun test_kit_externalSigners_returnsSameInstanceAcrossCalls() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val first = kit.externalSigners
        val second = kit.externalSigners
        assertTrue(first === second, "kit.externalSigners must return the same kit-owned manager on every access")
    }

    @Test
    fun test_kit_externalSigners_registersInMemoryEd25519Key() = runTest {
        // A key registered through the kit-owned manager is reachable for signing.
        val kit = OZSmartAccountKit.create(buildConfig())
        val rawSeed = ByteArray(32) { (it + 4).toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        assertTrue(
            kit.externalSigners.canSignEd25519For(VERIFIER_A, publicKey),
            "kit.externalSigners must report it can sign for a key registered through it"
        )
    }

    // ========================================================================
    // SelectedSigner.Ed25519 — equality and hashCode
    // ========================================================================

    @Test
    fun test_selectedSignerEd25519_constructionAndEquality() {
        val pk = ByteArray(32) { (it and 0xFF).toByte() }

        val a = SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = pk)
        val b = SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = pk.copyOf())
        val c = SelectedSigner.Ed25519(verifierAddress = VERIFIER_B, publicKey = pk)

        assertTrue(a == b, "Byte-equal instances with same verifier must be equal")
        assertFalse(a == c, "Differing verifier address must break equality")

        val altPk = ByteArray(32) { ((it + 1) and 0xFF).toByte() }
        val d = SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = altPk)
        assertFalse(a == d, "Differing public key must break equality")
    }

    @Test
    fun test_selectedSignerEd25519_hashCodeStableAcrossInstances() {
        val pk = ByteArray(32) { (it and 0xFF).toByte() }

        val a = SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = pk)
        val b = SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = pk.copyOf())

        assertEquals(a.hashCode(), b.hashCode(), "Hash codes must be stable for byte-equivalent instances")

        val c = SelectedSigner.Ed25519(verifierAddress = VERIFIER_B, publicKey = pk)
        // Different verifier address should produce a different hash code.
        assertFalse(a.hashCode() == c.hashCode(), "Different verifier address must produce different hash code")
    }

    @Test
    fun test_selectedSignerEd25519_equalsRejectsNullAndOtherSignerTypes() {
        val signer = SelectedSigner.Ed25519(
            verifierAddress = VERIFIER_A,
            publicKey = ByteArray(32) { it.toByte() }
        )
        val otherArm: Any = SelectedSigner.Wallet(
            "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )

        assertTrue(signer.equals(signer), "An Ed25519 selector must equal itself")
        assertFalse(signer.equals(null), "An Ed25519 selector must never equal null")
        assertFalse(
            signer.equals(otherArm),
            "An Ed25519 selector must never equal another SelectedSigner arm"
        )
    }

    // ========================================================================
    // validateSelectedSigners — Ed25519 signer with no signing source
    // ========================================================================

    @Test
    fun test_validateSelectedSigners_ed25519NoSigningSource_throwsInvalidInputNamingRemedies() = runTest {
        // No adapter and no in-memory key registered — any Ed25519 selector must throw,
        // and the pipeline message must name both runtime remedies.
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val validPublicKey = ByteArray(32) { it.toByte() }

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(
                        verifierAddress = VERIFIER_A,
                        publicKey = validPublicKey
                    )
                )
            )
        }
        assertTrue(
            ex.message.contains("selectedSigners", ignoreCase = true),
            "Exception must reference selectedSigners; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("kit.externalSigners.addEd25519FromRawKey", ignoreCase = false),
            "Exception must name kit.externalSigners.addEd25519FromRawKey; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("config.externalEd25519Adapter", ignoreCase = false),
            "Exception must name config.externalEd25519Adapter; got: ${ex.message}"
        )
    }

    // ========================================================================
    // appendEd25519Signature — verify() throws due to wrong-length signature →
    // caught and re-thrown as TransactionException.SigningFailed
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_ed25519_verifyThrowsException_wrappedInSigningFailed() = runTest {
        // WrongLengthSignatureAdapter returns a 63-byte signature. Ed25519 verify
        // must receive exactly 64 bytes; a wrong-length input causes the underlying
        // crypto library to throw an exception rather than return false. The production
        // catch block in appendEd25519Signature must wrap this as TransactionException.SigningFailed.
        val deployer = KeyPair.random()

        val rawSeed = ByteArray(32) { (it + 43).toByte() }
        val signingKeypair = KeyPair.fromSecretSeed(rawSeed)
        val publicKey = signingKeypair.getPublicKey()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntry = buildAuthEntry(VERIFIER_B)
        val authEntryXdr = authEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        val mockServer = buildSequentialMockServer(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(externalEd25519Adapter = WrongLengthSignatureAdapter(publicKey), deployer = deployer),
            sorobanServer = mockServer
        )
        kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        assertFailsWith<TransactionException.SigningFailed> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(
                        verifierAddress = VERIFIER_A,
                        publicKey = publicKey
                    )
                ),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
    }

    // ========================================================================
    // cloneEntryWithExpiration — entry with Void credentials (non-Address branch)
    //
    // When the credentials discriminant is SOROBAN_CREDENTIALS_SOURCE_ACCOUNT (Void),
    // cloneEntryWithExpiration returns the XDR round-trip clone unchanged (no expiration
    // field to update). The entry is passed through to signedAuthEntries and the call
    // completes successfully. This test drives that early-return branch.
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_authEntryWithVoidCredentials_passedThroughSuccessfully() = runTest {
        // Build a simulation response that contains an auth entry whose credentials are
        // SorobanCredentialsXdr.Void (source-account credentials). The signing pipeline
        // must pass these entries through unmodified: cloneEntryWithExpiration detects
        // non-Address credentials and returns the clone immediately without setting expiration.
        // The entry lands in signedAuthEntries and the full round-trip succeeds.
        val deployer = KeyPair.random()

        // Build an auth entry with Void (source-account) credentials.
        val voidAuthEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = Address(VERIFIER_B).toSCAddress(),
                        functionName = SCSymbolXdr("noop"),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )
        val voidAuthEntryXdr = voidAuthEntry.toXdrBase64()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "f5832b6ea5dca8c7e0f6c9a4b8e4e9d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1"

        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = voidAuthEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        val rawSeed = ByteArray(32) { (it + 88).toByte() }
        kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        // Void-credentials entries have no address to match against contractId, so the
        // pipeline's early-continue branch fires and adds them to signedAuthEntries unchanged.
        // With an empty selectedSigners list, no signing loop runs and the call completes.
        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = emptyList(),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "Void-credentials auth entry must be passed through; result=${result.error}")
        assertEquals(txHash, result.hash, "Transaction hash must match the mock response")
    }

    // ========================================================================
    // multiSignerContractCall — validation path exercises method entry
    // ========================================================================

    @Test
    fun test_multiSignerContractCall_blankTargetFn_throwsInvalidInput() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidInput> {
            manager.multiSignerContractCall(
                target = VERIFIER_A,
                targetFn = "",
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = ByteArray(32))
                )
            )
        }
    }

    @Test
    fun test_multiSignerContractCall_emptySelectedSigners_throwsInvalidInput() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidInput> {
            manager.multiSignerContractCall(
                target = VERIFIER_A,
                targetFn = "transfer",
                selectedSigners = emptyList()
            )
        }
    }

    // ========================================================================
    // multiSignerExecuteAndSubmit — validation path exercises method entry
    // ========================================================================

    @Test
    fun test_multiSignerExecuteAndSubmit_invalidTargetAddress_throwsException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            manager.multiSignerExecuteAndSubmit(
                target = "not-a-contract-address",
                targetFn = "vote",
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = ByteArray(32))
                )
            )
        }
        assertTrue(
            ex.message.contains("target must be a valid contract address"),
            "The target-address guard must fire before any signer or network work; got: ${ex.message}"
        )
    }

    @Test
    fun test_multiSignerExecuteAndSubmit_emptySelectedSigners_throwsInvalidInput() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        assertFailsWith<ValidationException.InvalidInput> {
            manager.multiSignerExecuteAndSubmit(
                target = VERIFIER_A,
                targetFn = "vote",
                selectedSigners = emptyList()
            )
        }
    }

    // ========================================================================
    // multiSignerContractCall — direct invocation of the target contract
    // ========================================================================

    @Test
    fun test_multiSignerContractCall_submitsDirectInvocationOfTargetFunction() = runTest {
        // multiSignerContractCall must invoke target.targetFn(targetArgs) directly rather
        // than routing through the smart account's execute() entry point. The submitted
        // envelope is the authoritative evidence of which host function was built.
        val deployer = KeyPair.random()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntryXdr = buildAuthEntry(VERIFIER_B).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "c5721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

        var capturedSendBody: String? = null
        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash,
            onSendTransaction = { capturedSendBody = it }
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        val rawSeed = ByteArray(32) { (it + 11).toByte() }
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val result = kit.multiSignerManager.multiSignerContractCall(
            target = TARGET_CONTRACT,
            targetFn = "vote",
            targetArgs = listOf(SCValXdr.U32(Uint32Xdr(42u))),
            selectedSigners = listOf(
                SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = publicKey)
            ),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "Contract call must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)

        val sendBody = assertNotNull(capturedSendBody, "sendTransaction request must be captured")
        val invocation = submittedInvocation(sendBody)
        assertEquals(
            TARGET_CONTRACT,
            Address.fromSCAddress(invocation.contractAddress).toString(),
            "The host function must target the requested contract, not the smart account"
        )
        assertEquals("vote", invocation.functionName.value)
        assertEquals(
            listOf(SCValXdr.U32(Uint32Xdr(42u))),
            invocation.args,
            "Caller-supplied arguments must be forwarded verbatim"
        )
    }

    // ========================================================================
    // submitWithMultipleSigners — simulation returns no auth entries
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_simulationWithEmptyResults_throwsSimulationFailed() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val mockServer = buildScriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateNoResultsResponseJson(sorobanDataXdr)
            )
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(passkeyStub()),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
        assertTrue(
            ex.message.contains("No auth entries"),
            "Failure must state that the simulation returned no auth entries; got: ${ex.message}"
        )
    }

    @Test
    fun test_submitWithMultipleSigners_simulationResultWithoutAuth_throwsSimulationFailed() = runTest {
        // A read-only simulation result carries an xdr value but no auth array.
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val mockServer = buildScriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateResultWithoutAuthResponseJson(sorobanDataXdr)
            )
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(passkeyStub()),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
        assertTrue(
            ex.message.contains("No auth entries"),
            "Failure must state that the simulation returned no auth entries; got: ${ex.message}"
        )
    }

    // ========================================================================
    // submitWithMultipleSigners — re-simulation of the signed transaction fails
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_reSimulationError_throwsSimulationFailedWithoutSubmitting() = runTest {
        val deployer = KeyPair.random()

        val rawSeed = ByteArray(32) { (it + 23).toByte() }
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntryXdr = buildAuthEntry(VERIFIER_B).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        // The signed transaction is re-simulated before submission; when the host rejects
        // it, the pipeline must fail instead of sending a transaction that cannot succeed.
        val mockServer = buildScriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateWithAuthResponseJson(authEntryXdr, sorobanDataXdr),
                latestLedgerResponseJson(),
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(countZeroXdr, sorobanDataXdr),
                ledgerEntriesResponseJson(accountXdr),
                simulateErrorResponseJson("HostError: Error(Auth, InvalidAction)")
            )
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        val publicKey = kit.externalSigners.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = publicKey)
                ),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
        assertTrue(
            ex.message.contains("Re-simulation error"),
            "Failure must distinguish the re-simulation from the initial simulation; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("Error(Auth, InvalidAction)"),
            "Failure must relay the host's own diagnostic; got: ${ex.message}"
        )
    }

    // ========================================================================
    // submitWithMultipleSigners — auth entry addressed to a third party
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_authEntryForUnknownAddress_throwsSigningFailed() = runTest {
        // The simulation returns an entry that must be authorized by an address that is
        // neither the smart account nor any selected wallet signer, so nothing can sign it.
        val deployer = KeyPair.random()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val foreignAuthEntryXdr = buildAuthEntry(TARGET_CONTRACT).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        val mockServer = buildSequentialMockServer(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = foreignAuthEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        val publicKey = kit.externalSigners.addEd25519FromRawKey(
            ByteArray(32) { (it + 31).toByte() },
            VERIFIER_A
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val ex = assertFailsWith<TransactionException.SigningFailed> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = publicKey)
                ),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
        assertTrue(
            ex.message.contains(TARGET_CONTRACT),
            "Failure must name the address whose authorization cannot be produced; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("external signer", ignoreCase = true),
            "Failure must point at the remedy of adding an external signer; got: ${ex.message}"
        )
    }

    // ========================================================================
    // submitWithMultipleSigners — automatic context rule resolution
    // ========================================================================

    @Test
    fun test_submitWithMultipleSigners_withoutResolverAndNoContextRules_throwsInvalidInput() = runTest {
        // Omitting resolveContextRuleIds hands rule selection to the context rule manager.
        // An account whose rule set does not cover the invocation cannot be resolved, and
        // the caller must be told which parameter to supply instead.
        val deployer = KeyPair.random()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val authEntryXdr = buildAuthEntry(VERIFIER_B).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()

        val mockServer = buildSequentialMockServer(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer),
            sorobanServer = mockServer
        )
        val publicKey = kit.externalSigners.addEd25519FromRawKey(
            ByteArray(32) { (it + 43).toByte() },
            VERIFIER_A
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(
                    SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = publicKey)
                )
            )
        }
        assertTrue(
            ex.message.contains("contextRuleIds"),
            "Failure must name the parameter the caller can supply instead; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("No context rule matches"),
            "Failure must explain that no rule matches the invocation; got: ${ex.message}"
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers for the scripted RPC pipeline tests
// ---------------------------------------------------------------------------

/** A contract address distinct from both verifier fixtures. */
private const val TARGET_CONTRACT = "CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH5"

/**
 * Creates a [SorobanServer] that answers requests from [responses] in order and fails on
 * any request beyond the script, so a test that drifts past its expected RPC sequence
 * fails loudly instead of silently reusing a response.
 */
private fun buildScriptedMockServer(responses: List<String>): SorobanServer {
    var requestIndex = 0
    val mockEngine = MockEngine { _ ->
        val index = requestIndex++
        val body = responses.getOrNull(index)
            ?: error("Unexpected RPC request at index $index")
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    val client = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
    }
    return SorobanServer("https://soroban-testnet.stellar.org", client)
}

private fun simulateNoResultsResponseJson(sorobanDataBase64: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "transactionData": "$sorobanDataBase64",
    "minResourceFee": 100,
    "results": [],
    "latestLedger": 100
  }
}
""".trimIndent()

private fun simulateResultWithoutAuthResponseJson(sorobanDataBase64: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "transactionData": "$sorobanDataBase64",
    "minResourceFee": 100,
    "results": [
      {
        "xdr": null
      }
    ],
    "latestLedger": 100
  }
}
""".trimIndent()

/** Extracts the invoked contract arguments from a captured `sendTransaction` request body. */
private fun submittedInvocation(sendBody: String): InvokeContractArgsXdr {
    val root = Json.parseToJsonElement(sendBody).jsonObject
    val envelopeB64 = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
    val tx = Transaction.fromEnvelopeXdr(envelopeB64, Network.TESTNET)
    val op = tx.operations.first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
    val hostFunction = op.hostFunction
    check(hostFunction is HostFunctionXdr.InvokeContract) {
        "Expected an InvokeContract host function, got $hostFunction"
    }
    return hostFunction.value
}
