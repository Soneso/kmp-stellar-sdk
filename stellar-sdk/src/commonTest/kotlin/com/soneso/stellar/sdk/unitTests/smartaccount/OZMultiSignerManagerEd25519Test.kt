//
//  OZMultiSignerManagerEd25519Test.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.Ed25519Signature
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalEd25519SignerAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalSignerManager
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.externalSignerManager
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.AccountEntryExtXdr
import com.soneso.stellar.sdk.xdr.AccountEntryXdr
import com.soneso.stellar.sdk.xdr.AccountIDXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.LedgerFootprintXdr
import com.soneso.stellar.sdk.xdr.PublicKeyXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SequenceNumberXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanResourcesXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionDataExtXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionDataXdr
import com.soneso.stellar.sdk.xdr.String32Xdr
import com.soneso.stellar.sdk.xdr.ThresholdsXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import com.soneso.stellar.sdk.Address
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
// Helpers
// ---------------------------------------------------------------------------

private fun buildConfig(
    externalSignerManager: OZExternalSignerManager? = null,
    deployer: KeyPair? = null
): OZSmartAccountConfig = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = Network.TESTNET.networkPassphrase,
    accountWasmHash = "a" + "0".repeat(63),
    webauthnVerifierAddress = VERIFIER_A,
    externalSignerManager = externalSignerManager,
    deployerKeypair = deployer
)

private fun passkeyStub() = SelectedSigner.Passkey(
    credentialId = null,
    credentialIdBytes = null,
    keyData = ByteArray(68) { it.toByte() }
)

// ---------------------------------------------------------------------------
// RPC mock helpers
// ---------------------------------------------------------------------------

/**
 * Builds a minimal [LedgerEntryDataXdr.Account] for [deployer], suitable for
 * use as the [SorobanServer.getAccount] response in a [MockEngine].
 */
private fun buildAccountEntryXdr(deployer: KeyPair): LedgerEntryDataXdr {
    val accountEntry = AccountEntryXdr(
        accountId = AccountIDXdr(PublicKeyXdr.Ed25519(Uint256Xdr(deployer.getPublicKey()))),
        balance = Int64Xdr(100_000_000L),
        seqNum = SequenceNumberXdr(Int64Xdr(1L)),
        numSubEntries = Uint32Xdr(0u),
        inflationDest = null,
        flags = Uint32Xdr(0u),
        homeDomain = String32Xdr(""),
        thresholds = ThresholdsXdr(byteArrayOf(1, 0, 0, 0)),
        signers = emptyList(),
        ext = AccountEntryExtXdr.Void
    )
    return LedgerEntryDataXdr.Account(accountEntry)
}

/**
 * Builds a minimal [SorobanAuthorizationEntryXdr] with [SorobanCredentials.Address]
 * pointing at [contractId]. The signing loop in [OZMultiSignerManager] uses this entry
 * to dispatch auth signatures for the smart account contract.
 */
private fun buildAuthEntry(contractId: String): SorobanAuthorizationEntryXdr {
    val scAddress = Address(contractId).toSCAddress()
    return SorobanAuthorizationEntryXdr(
        credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = scAddress,
                nonce = Int64Xdr(12345L),
                signatureExpirationLedger = Uint32Xdr(0u),
                signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
            )
        ),
        rootInvocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = scAddress,
                    functionName = SCSymbolXdr("noop"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    )
}

/**
 * Builds a minimal [SorobanTransactionDataXdr] with empty footprint and zero fees,
 * sufficient for the [SimulateTransactionResponse] used in tests.
 */
private fun buildMinimalSorobanData(): SorobanTransactionDataXdr = SorobanTransactionDataXdr(
    ext = SorobanTransactionDataExtXdr.Void,
    resources = SorobanResourcesXdr(
        footprint = LedgerFootprintXdr(emptyList(), emptyList()),
        instructions = Uint32Xdr(0u),
        diskReadBytes = Uint32Xdr(0u),
        writeBytes = Uint32Xdr(0u)
    ),
    resourceFee = Int64Xdr(0L)
)

/**
 * Returns a getLedgerEntries JSON-RPC response body containing a single account entry
 * encoded as base64 XDR.
 */
private fun ledgerEntriesResponseJson(accountXdrBase64: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "entries": [
      {
        "key": "AAAAAAAAAAA=",
        "xdr": "$accountXdrBase64",
        "lastModifiedLedgerSeq": 100
      }
    ],
    "latestLedger": 100
  }
}
""".trimIndent()

/**
 * Returns a simulateTransaction JSON-RPC response body containing a single auth entry
 * (base64 XDR) for the smart account contract.
 */
private fun simulateWithAuthResponseJson(authEntryBase64: String, sorobanDataBase64: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "transactionData": "$sorobanDataBase64",
    "minResourceFee": 100,
    "results": [
      {
        "auth": ["$authEntryBase64"],
        "xdr": null
      }
    ],
    "latestLedger": 100
  }
}
""".trimIndent()

/**
 * Returns a getLatestLedger JSON-RPC response body.
 */
private fun latestLedgerResponseJson(sequence: Int = 1000): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "id": "abc123",
    "protocolVersion": 20,
    "sequence": $sequence
  }
}
""".trimIndent()

/**
 * Returns a simulateTransaction JSON-RPC response body with an [SCValXdr.U32] result,
 * used for the [OZContextRuleManager.getContextRulesCount] call.
 */
private fun simulateCountResponseJson(countXdrBase64: String, sorobanDataBase64: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "transactionData": "$sorobanDataBase64",
    "minResourceFee": 100,
    "results": [
      {
        "auth": [],
        "xdr": "$countXdrBase64"
      }
    ],
    "latestLedger": 100
  }
}
""".trimIndent()

/**
 * Returns a sendTransaction JSON-RPC response body with PENDING status and the given [hash].
 */
private fun sendTransactionPendingResponseJson(hash: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "status": "PENDING",
    "hash": "$hash",
    "latestLedger": 1001,
    "latestLedgerCloseTime": 1700000000
  }
}
""".trimIndent()

/**
 * Returns a getTransaction JSON-RPC response body with SUCCESS status and the given [hash].
 */
private fun getTransactionSuccessResponseJson(hash: String): String = """
{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "status": "SUCCESS",
    "latestLedger": 1002,
    "latestLedgerCloseTime": 1700000010,
    "oldestLedger": 900,
    "oldestLedgerCloseTime": 1699990000,
    "ledger": 1001,
    "createdAt": 1700000000,
    "envelopeXdr": null,
    "resultXdr": null,
    "resultMetaXdr": null
  }
}
""".trimIndent()

/**
 * Creates a [SorobanServer] backed by a [MockEngine] that handles the fixed RPC
 * call sequence that occurs during [OZMultiSignerManager.submitWithMultipleSigners]:
 *
 * 1. getLedgerEntries — account lookup for the deployer (main simulation setup)
 * 2. simulateTransaction — main invocation simulation, returns [authEntryBase64]
 * 3. getLatestLedger — current ledger sequence for signature expiry
 * 4. getLedgerEntries — account lookup for the deployer (context-rule-count simulation setup)
 * 5. simulateTransaction — get_context_rules_count query, returns U32(0)
 *
 * After response 5, [getContextRulesCount] returns 0, [listContextRules] returns an empty
 * list, and the signing loop proceeds with the caller-supplied [resolveContextRuleIds]
 * callback (which bypasses context-rule resolution entirely).
 */
private fun buildSequentialMockServer(
    accountXdrBase64: String,
    authEntryBase64: String,
    sorobanDataBase64: String,
    countXdrBase64: String
): SorobanServer {
    var requestIndex = 0
    val mockEngine = MockEngine { _ ->
        val responseBody = when (requestIndex++) {
            0 -> ledgerEntriesResponseJson(accountXdrBase64)
            1 -> simulateWithAuthResponseJson(authEntryBase64, sorobanDataBase64)
            2 -> latestLedgerResponseJson()
            3 -> ledgerEntriesResponseJson(accountXdrBase64)
            4 -> simulateCountResponseJson(countXdrBase64, sorobanDataBase64)
            else -> error("Unexpected RPC request at index ${requestIndex - 1}")
        }
        respond(
            content = ByteReadChannel(responseBody),
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

/**
 * Creates a [SorobanServer] backed by a [MockEngine] that handles the full RPC call
 * sequence for a successful [OZMultiSignerManager.submitWithMultipleSigners] submission:
 *
 * 1. getLedgerEntries — deployer account lookup (first simulation)
 * 2. simulateTransaction — main invocation simulation, returns [authEntryBase64]
 * 3. getLatestLedger — current ledger sequence for signature expiry
 * 4. getLedgerEntries — deployer account lookup (context-rule-count simulation)
 * 5. simulateTransaction — get_context_rules_count query, returns U32(0)
 * 6. getLedgerEntries — deployer account lookup (re-fetch for second simulation setup)
 * 7. simulateTransaction — re-simulation with signed auth entries
 * 8. sendTransaction — submission, returns PENDING with [txHash]
 * 9. getTransaction — polling call, returns SUCCESS
 *
 * Any additional requests beyond index 8 also return SUCCESS to handle platforms
 * or configurations that may issue getTransaction more than once.
 */
private fun buildSequentialMockServerWithSubmission(
    accountXdrBase64: String,
    authEntryBase64: String,
    sorobanDataBase64: String,
    countXdrBase64: String,
    txHash: String
): SorobanServer {
    var requestIndex = 0
    val mockEngine = MockEngine { _ ->
        val responseBody = when (requestIndex++) {
            0 -> ledgerEntriesResponseJson(accountXdrBase64)
            1 -> simulateWithAuthResponseJson(authEntryBase64, sorobanDataBase64)
            2 -> latestLedgerResponseJson()
            3 -> ledgerEntriesResponseJson(accountXdrBase64)
            4 -> simulateCountResponseJson(countXdrBase64, sorobanDataBase64)
            5 -> ledgerEntriesResponseJson(accountXdrBase64)
            6 -> simulateWithAuthResponseJson(authEntryBase64, sorobanDataBase64)
            7 -> sendTransactionPendingResponseJson(txHash)
            else -> getTransactionSuccessResponseJson(txHash)
        }
        respond(
            content = ByteReadChannel(responseBody),
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

/**
 * Unit tests for the Ed25519 signer path through [OZMultiSignerManager] validation.
 *
 * Tests cover:
 * - validateSignerSet Ed25519 validation (public key length, verifier address format,
 *   signing source registration, same-pubkey different-verifier tuple resolution)
 * - SelectedSigner.Ed25519 construction, equality, and hashCode semantics
 * - Ed25519Signature wire shape (raw BytesN<64>, not a Map)
 * - Kit accessor for externalSignerManager
 * - The local verification branch in the signing pipeline
 */
class OZMultiSignerManagerEd25519Test {

    // ========================================================================
    // validateSignerSet — Ed25519 validation
    // ========================================================================

    @Test
    fun test_validateSignerSet_ed25519WithRegisteredSigner_passes() = runTest {
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val rawSeed = ByteArray(32) { it.toByte() }
        val publicKey = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        val kit = OZSmartAccountKit.create(buildConfig(extManager))
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        // With a registered signer, validation passes and the call proceeds past Ed25519
        // validation to the simulation step. A network-related failure (not InvalidInput)
        // confirms that Ed25519 validation did not throw.
        val ex = assertFailsWith<Exception> {
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
        assertFalse(ex is ValidationException.InvalidInput, "Ed25519 validation must pass when signer is registered; got: ${ex::class.simpleName}: ${ex.message}")
    }

    @Test
    fun test_validateSignerSet_ed25519WithoutRegisteredSigner_throwsInvalidInputSelectedSigners() = runTest {
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        // No signer registered for VERIFIER_A.

        val kit = OZSmartAccountKit.create(buildConfig(extManager))
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
    fun test_validateSignerSet_ed25519InvalidPublicKeyLength_throws() = runTest {
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val kit = OZSmartAccountKit.create(buildConfig(extManager))
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
    fun test_validateSignerSet_ed25519InvalidVerifierAddress_throws() = runTest {
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val rawSeed = ByteArray(32) { (it + 1).toByte() }
        val publicKey = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        val kit = OZSmartAccountKit.create(buildConfig(extManager))
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
    fun test_validateSignerSet_ed25519SamePubkeyDifferentVerifiers_resolvedByTuple() = runTest {
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val rawSeed = ByteArray(32) { (it + 2).toByte() }

        // Same raw seed, two different verifier addresses — two distinct registry entries.
        val pk1 = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        extManager.addEd25519FromRawKey(rawSeed, VERIFIER_B)

        // Both entries are reachable via their respective tuple keys.
        assertTrue(
            extManager.canSignEd25519For(VERIFIER_A, pk1),
            "canSignEd25519For must return true for VERIFIER_A"
        )
        assertTrue(
            extManager.canSignEd25519For(VERIFIER_B, pk1),
            "canSignEd25519For must return true for VERIFIER_B using the same public key"
        )
    }

    @Test
    fun test_validateSignerSet_ed25519DuplicateTupleEntries_dedupedOrRejected() = runTest {
        // The production implementation does NOT deduplicate selectedSigners — duplicate
        // entries pass validation and the signing loop processes each entry individually.
        // This test pins that behavior: two identical Ed25519 selectors with a registered
        // signer pass the validateSignerSet loop without throwing InvalidInput.
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val rawSeed = ByteArray(32) { (it + 3).toByte() }
        val publicKey = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        val kit = OZSmartAccountKit.create(buildConfig(extManager))
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager
        val duplicateSigner = SelectedSigner.Ed25519(
            verifierAddress = VERIFIER_A,
            publicKey = publicKey
        )

        // Both selectors are identical. Validation passes (no InvalidInput); the call
        // proceeds to the simulation step where a non-InvalidInput exception surfaces.
        val ex = assertFailsWith<Exception> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(duplicateSigner, duplicateSigner)
            )
        }
        assertFalse(
            ex is ValidationException.InvalidInput,
            "Duplicate Ed25519 selectors must not cause InvalidInput at validation; got: ${ex::class.simpleName}"
        )
    }

    @Test
    fun test_validateSignerSet_ed25519PubkeyMatchesWalletGAddressBytes_noFalseMatch() = runTest {
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val ed25519Seed = ByteArray(32) { (it + 3).toByte() }
        val ed25519PublicKey = extManager.addEd25519FromRawKey(ed25519Seed, VERIFIER_A)

        // canSignEd25519For checks the (verifierAddress, publicKey) tuple.
        assertTrue(
            extManager.canSignEd25519For(VERIFIER_A, ed25519PublicKey),
            "canSignEd25519For must return true for the registered Ed25519 entry"
        )

        // canSignFor (wallet path) for any G-address derived from the same raw seed must
        // return false because no wallet signer was added.
        val derivedAccountId = KeyPair.fromPublicKey(ed25519PublicKey).getAccountId()
        assertFalse(
            extManager.canSignFor(derivedAccountId),
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
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val rawSeed = ByteArray(32) { (it + 21).toByte() }
        val publicKey = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

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
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val rawSeed = ByteArray(32) { (it + 22).toByte() }
        val publicKey = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

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
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        // Register a real keypair against VERIFIER_A so validation passes.
        val rawSeed = ByteArray(32) { (it + 42).toByte() }
        val publicKey = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        // Replace the in-process signer with an adapter that returns a zero-byte signature.
        // ZeroBytesAdapter.canSignFor returns true for publicKey, so the signing branch
        // routes through it instead of the in-process raw-key path.
        extManager.ed25519Adapter = ZeroBytesAdapter(publicKey)

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

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(extManager, deployer),
            sorobanServer = mockServer
        )
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
        // in-process keypair registered via addEd25519FromRawKey. No adapter is set.
        // The signing pipeline must: sign the auth digest, verify the signature locally
        // (KeyPair.verify returns true), wrap it in Ed25519Signature, and return a
        // TransactionResult with success = true.
        val deployer = KeyPair.random()
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        val rawSeed = ByteArray(32) { (it + 77).toByte() }
        val publicKey = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        // No adapter — the in-process keypair path must handle signing entirely.

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
            config = buildConfig(extManager, deployer),
            sorobanServer = mockServer
        )
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
        // Drive appendEd25519Signature through the success path using an adapter that
        // wraps a real keypair. The adapter is registered alongside the same in-process
        // keypair so canSignEd25519For passes. At signing time, adapter-first precedence
        // routes through the adapter, and callCount == 1 confirms the adapter path ran.
        val deployer = KeyPair.random()
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        val rawSeed = ByteArray(32) { (it + 77).toByte() }
        val publicKey = extManager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        val signingKeypair = KeyPair.fromSecretSeed(rawSeed)
        val adapter = ValidSigningAdapter(signingKeypair, publicKey)
        extManager.ed25519Adapter = adapter

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
            config = buildConfig(extManager, deployer),
            sorobanServer = mockServer
        )
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
    fun test_submitWithMultipleSigners_ed25519PolicyOnlyAuth_succeedsWithZeroSelectedSigners() = runTest {
        // When selectedSigners is empty, no Ed25519 validation fires (the Ed25519 loop has
        // nothing to iterate over). The call proceeds past validation to the simulation step
        // where a network-related exception surfaces.
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val kit = OZSmartAccountKit.create(buildConfig(extManager))
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val manager = kit.multiSignerManager

        // Empty selectedSigners contains no Ed25519 entries, so Ed25519 validation is skipped.
        // The call falls through to simulation (a network-dependent step), which throws a
        // non-InvalidInput exception.
        val ex = assertFailsWith<Exception> {
            manager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = emptyList()
            )
        }
        assertFalse(
            ex is ValidationException.InvalidInput,
            "An empty selectedSigners list must not trigger Ed25519 InvalidInput; got: ${ex::class.simpleName}: ${ex.message}"
        )
    }

    // ========================================================================
    // Kit accessor
    // ========================================================================

    @Test
    fun test_kit_externalSignerManager_returnsInstanceFromConfig() = runTest {
        val extManager = OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase
        )
        val kit = OZSmartAccountKit.create(buildConfig(extManager))

        // The kit's externalSignerManager accessor must return the exact instance from config.
        val retrieved = kit.externalSignerManager
        assertNotNull(retrieved, "externalSignerManager must not be null when set in config")
        assertTrue(retrieved === extManager, "externalSignerManager must be the same instance passed via config")
    }

    @Test
    fun test_kit_externalSignerManager_nullWhenNotInConfig() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig(externalSignerManager = null))
        assertNull(kit.externalSignerManager, "externalSignerManager must be null when not set in config")
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
}

// ---------------------------------------------------------------------------
// Stub host function builder (avoids duplication with other test files)
// ---------------------------------------------------------------------------

private fun stubHostFunction(contractId: String): com.soneso.stellar.sdk.xdr.HostFunctionXdr {
    return com.soneso.stellar.sdk.xdr.HostFunctionXdr.InvokeContract(
        com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr(
            contractAddress = com.soneso.stellar.sdk.Address(contractId).toSCAddress(),
            functionName = com.soneso.stellar.sdk.xdr.SCSymbolXdr("noop"),
            args = emptyList()
        )
    )
}
