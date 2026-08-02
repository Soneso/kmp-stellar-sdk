//
//  OZSignerManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.MockWebAuthnProvider
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildMinimalSorobanData
import com.soneso.stellar.sdk.unitTests.smartaccount.getTransactionSuccessResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.latestLedgerResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.sendTransactionPendingResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateCountResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateWithAuthResponseJson
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import com.soneso.stellar.sdk.smartaccount.oz.AllowCredential
import com.soneso.stellar.sdk.smartaccount.oz.CredentialDeploymentStatus
import com.soneso.stellar.sdk.smartaccount.oz.InMemoryStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountEvent
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Scripted RPC mock server
// ---------------------------------------------------------------------------

/**
 * Creates a [SorobanServer] that answers requests strictly in the order of [responses] and
 * fails once the script is exhausted, so a flow that issues more RPC calls than the test
 * expects fails loudly. Every request body is handed to [onRequest] with its zero-based index.
 */
private fun signerMockServer(
    responses: List<String>,
    onRequest: ((index: Int, body: String) -> Unit)? = null
): SorobanServer {
    var requestIndex = 0
    val mockEngine = MockEngine { request ->
        val index = requestIndex++
        if (onRequest != null) {
            onRequest(index, request.body.toByteArray().decodeToString())
        }
        val body = responses.getOrNull(index)
            ?: error("Unexpected RPC request at index $index; script holds ${responses.size} responses")
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    val httpClient = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
    }
    return SorobanServer("https://soroban-testnet.stellar.org", httpClient)
}

/**
 * Extracts the contract invocation carried by a `simulateTransaction` or `sendTransaction`
 * JSON-RPC request body.
 */
private fun signerInvocation(requestBody: String): InvokeContractArgsXdr {
    val root = Json.parseToJsonElement(requestBody).jsonObject
    val envelopeBase64 = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
    val transaction = Transaction.fromEnvelopeXdr(envelopeBase64, Network.TESTNET)
    val operation = transaction.operations
        .first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
    return (operation.hostFunction as HostFunctionXdr.InvokeContract).value
}

/** Extracts the authorization entries carried by a `sendTransaction` request body. */
private fun signerSubmittedAuth(requestBody: String): List<SorobanAuthorizationEntryXdr> {
    val root = Json.parseToJsonElement(requestBody).jsonObject
    val envelopeBase64 = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
    val transaction = Transaction.fromEnvelopeXdr(envelopeBase64, Network.TESTNET)
    val operation = transaction.operations
        .first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
    return operation.auth
}

/**
 * WebAuthn provider whose registration ceremony fails with an exception that carries no
 * message, mirroring a platform authenticator error surfaced without a description.
 */
private class MessagelessFailureWebAuthnProvider : WebAuthnProvider {
    override suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult = throw IllegalStateException()

    override suspend fun authenticate(
        challenge: ByteArray,
        allowCredentials: List<AllowCredential>?
    ): WebAuthnAuthenticationResult = throw IllegalStateException()
}

/**
 * Unit tests for the [com.soneso.stellar.sdk.smartaccount.oz.OZSignerManager] flows: adding
 * passkey, delegated and Ed25519 signers to a context rule, removing a signer by ID or by
 * value, and the end-to-end passkey registration performed by `addNewPasskeySigner`.
 *
 * The signer value objects themselves are pinned by the core signer tests; these tests pin
 * the contract invocation the manager builds and sends, the input validation that runs before
 * any network round-trip, and the routing between the single-signer and the multi-signer
 * submission pipelines.
 */
class OZSignerManagerTest {

    // ========================================================================
    // Fixtures
    // ========================================================================

    /** The connected smart account contract. */
    private val smartAccount = StrKey.encodeContract(ByteArray(32) { (it + 61).toByte() })

    /** A verifier distinct from the configured WebAuthn verifier ([VERIFIER_A]). */
    private val ed25519Verifier = VERIFIER_B

    private val delegatedAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    private val signerOnRule = DelegatedSigner(StrKey.encodeContract(ByteArray(32) { (it + 71).toByte() }))
    private val otherSigner = DelegatedSigner(StrKey.encodeContract(ByteArray(32) { (it + 72).toByte() }))

    private val txHash = "c3942a4c83fbc8d5e8e4a7e2f6e2c715a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9"

    private fun buildConfig(
        deployer: KeyPair,
        webauthnProvider: WebAuthnProvider? = null,
        storage: InMemoryStorageAdapter? = null
    ): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = VERIFIER_A,
        deployerKeypair = deployer,
        webauthnProvider = webauthnProvider,
        storage = storage ?: InMemoryStorageAdapter()
    )

    /**
     * Builds an auth entry whose credentials name [address] and whose root invocation targets
     * [targetContract]. When [address] differs from the connected smart account, the
     * single-signer submit pipeline passes the entry through untouched, so the round-trip
     * needs no passkey.
     */
    private fun authEntryFor(
        address: String,
        targetContract: String = VERIFIER_A
    ): SorobanAuthorizationEntryXdr = SorobanAuthorizationEntryXdr(
        credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = Address(address).toSCAddress(),
                nonce = Int64Xdr(9911L),
                signatureExpirationLedger = Uint32Xdr(0u),
                signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
            )
        ),
        rootInvocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(targetContract).toSCAddress(),
                    functionName = SCSymbolXdr("noop"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    )

    /**
     * The nine scripted responses of a single-signer submit round-trip: deployer lookup,
     * simulation, latest ledger, the two-call `get_context_rules_count` query (zero rules),
     * deployer re-fetch, re-simulation, submission, and one confirmation poll.
     */
    private fun submitResponses(
        accountXdr: String,
        authEntryXdr: String,
        sorobanDataXdr: String
    ): List<String> = listOf(
        ledgerEntriesResponseJson(accountXdr),
        simulateWithAuthResponseJson(authEntryXdr, sorobanDataXdr),
        latestLedgerResponseJson(),
        ledgerEntriesResponseJson(accountXdr),
        simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanDataXdr),
        ledgerEntriesResponseJson(accountXdr),
        simulateWithAuthResponseJson(authEntryXdr, sorobanDataXdr),
        sendTransactionPendingResponseJson(txHash),
        getTransactionSuccessResponseJson(txHash)
    )

    /** Builds a context rule Map ScVal in the on-chain field layout. */
    private fun contextRuleScVal(
        id: UInt,
        name: String,
        signers: List<SmartAccountSigner>,
        signerIds: List<UInt>
    ): SCValXdr {
        val entries = listOf(
            "context_type" to Scv.toVec(listOf(Scv.toSymbol("Default"))),
            "id" to Scv.toUint32(id),
            "name" to Scv.toString(name),
            "policies" to Scv.toVec(emptyList()),
            "policy_ids" to Scv.toVec(emptyList()),
            "signer_ids" to Scv.toVec(signerIds.map { Scv.toUint32(it) }),
            "signers" to Scv.toVec(signers.map { it.toScVal() }),
            "valid_until" to Scv.toVoid()
        ).map { (key, value) -> SCMapEntryXdr(key = Scv.toSymbol(key), `val` = value) }
        return SCValXdr.Map(SCMapXdr(entries))
    }

    /** Decodes an on-chain signer Vec into its discriminant, address and key data. */
    private fun decodeSigner(scVal: SCValXdr): Triple<String, String, ByteArray?> {
        val vec = Scv.fromVec(scVal)
        val discriminant = Scv.fromSymbol(vec[0])
        val address = Address.fromSCVal(vec[1]).toString()
        val keyData = if (vec.size > 2) Scv.fromBytes(vec[2]) else null
        return Triple(discriminant, address, keyData)
    }

    private suspend fun connectedKit(
        deployer: KeyPair,
        server: SorobanServer,
        webauthnProvider: WebAuthnProvider? = null,
        storage: InMemoryStorageAdapter? = null
    ): OZSmartAccountKit {
        val kit = OZSmartAccountKit.createWithServer(
            buildConfig(deployer, webauthnProvider, storage),
            server
        )
        kit.setConnectedState("test-credential-id", smartAccount)
        return kit
    }

    // ========================================================================
    // addPasskey
    // ========================================================================

    @Test
    fun testAddPasskey_submitsAddSignerWithWebAuthnSigner() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        var requestCount = 0
        val server = signerMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            requestCount = index + 1
            if (index == 1) simulateBody = body
        }

        val kit = connectedKit(deployer, server)

        val publicKey = MockWebAuthnProvider.testPublicKey(seed = 3)
        val credentialId = MockWebAuthnProvider.testCredentialId(seed = 3)

        val result = kit.signerManager.addPasskey(
            contextRuleId = 2u,
            publicKey = publicKey,
            credentialId = credentialId
        )

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)
        assertEquals(1001u, result.ledger, "Ledger must come from the confirmation poll")
        assertEquals(9, requestCount, "The round-trip must issue exactly nine RPC calls")

        val invocation = signerInvocation(assertNotNull(simulateBody))
        assertEquals("add_signer", invocation.functionName.value)
        assertEquals(
            smartAccount,
            Address.fromSCAddress(invocation.contractAddress).toString(),
            "The invocation must target the connected smart account"
        )
        assertEquals(2, invocation.args.size, "add_signer takes two arguments")
        assertEquals(2u, Scv.fromUint32(invocation.args[0]))

        val (discriminant, verifier, keyData) = decodeSigner(invocation.args[1])
        assertEquals("External", discriminant, "A passkey is added as an external signer")
        assertEquals(VERIFIER_A, verifier, "The configured WebAuthn verifier must be used")
        assertTrue(
            publicKey.plus(credentialId).contentEquals(keyData),
            "The key data must be the public key followed by the credential ID"
        )
    }

    @Test
    fun testAddPasskey_publicKeyWithoutUncompressedPrefix_throwsBeforeAnyRpcCall() = runTest {
        val kit = connectedKit(KeyPair.random(), signerMockServer(emptyList()))

        // Correct length, but a compressed-point prefix the WebAuthn verifier cannot consume.
        val publicKey = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) { 0x11 }
        publicKey[0] = 0x03

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.signerManager.addPasskey(
                contextRuleId = 0u,
                publicKey = publicKey,
                credentialId = ByteArray(16) { 0x01 }
            )
        }
        assertTrue(
            exception.message.contains("0x03"),
            "The exception must report the rejected prefix; got: ${exception.message}"
        )
    }

    @Test
    fun testAddPasskey_emptyCredentialId_throwsBeforeAnyRpcCall() = runTest {
        val kit = connectedKit(KeyPair.random(), signerMockServer(emptyList()))

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.signerManager.addPasskey(
                contextRuleId = 0u,
                publicKey = MockWebAuthnProvider.testPublicKey(),
                credentialId = ByteArray(0)
            )
        }
        assertTrue(
            exception.message.contains("Credential ID cannot be empty"),
            "The exception must name the empty credential ID; got: ${exception.message}"
        )
    }

    // ========================================================================
    // addDelegated
    // ========================================================================

    @Test
    fun testAddDelegated_submitsAddSignerWithDelegatedSigner() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = signerMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = connectedKit(deployer, server)

        val result = kit.signerManager.addDelegated(contextRuleId = 1u, address = delegatedAddress)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)

        val invocation = signerInvocation(assertNotNull(simulateBody))
        assertEquals("add_signer", invocation.functionName.value)
        // ABI: add_signer(context_rule_id: u32, signer: Signer)
        assertEquals(2, invocation.args.size, "add_signer takes two arguments")
        assertEquals(SCValTypeXdr.SCV_U32, invocation.args[0].discriminant, "arg 0 is the context rule id")
        assertEquals(1u, Scv.fromUint32(invocation.args[0]))
        assertEquals(SCValTypeXdr.SCV_VEC, invocation.args[1].discriminant, "arg 1 is the encoded signer")

        val (discriminant, address, keyData) = decodeSigner(invocation.args[1])
        assertEquals("Delegated", discriminant)
        assertEquals(delegatedAddress, address)
        assertEquals(null, keyData, "A delegated signer carries no key data")
    }

    @Test
    fun testAddDelegated_selectedSigners_routesToMultiSignerSubmission() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val walletAddress = walletKeypair.getAccountId()
        val authEntryXdr = authEntryFor(walletAddress, targetContract = smartAccount).toXdrBase64()

        var simulateBody: String? = null
        var sendBody: String? = null
        val server = signerMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            if (index == 1) simulateBody = body
            if (index == 7) sendBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.externalSigners.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.signerManager.addDelegated(
            contextRuleId = 0u,
            address = delegatedAddress,
            selectedSigners = listOf(SelectedSigner.Wallet(walletAddress))
        )

        assertTrue(result.success, "Multi-signer round-trip must succeed; error=${result.error}")
        assertEquals("add_signer", signerInvocation(assertNotNull(simulateBody)).functionName.value)

        // The multi-signer pipeline stamps an expiration and writes the wallet signature onto
        // the entry; the single-signer pipeline would have passed the entry through untouched.
        val submitted = signerSubmittedAuth(assertNotNull(sendBody))
        assertEquals(1, submitted.size)
        val credentials = (submitted[0].credentials as SorobanCredentialsXdr.Address).value
        assertTrue(
            credentials.signatureExpirationLedger.value > 0u,
            "The submitted entry must carry a stamped expiration"
        )
        assertTrue(
            credentials.signature !is SCValXdr.Void,
            "The submitted entry must carry the wallet signature"
        )
    }

    // ========================================================================
    // addEd25519
    // ========================================================================

    @Test
    fun testAddEd25519_submitsAddSignerWithSuppliedVerifier() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = signerMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = connectedKit(deployer, server)

        val publicKey = KeyPair.random().getPublicKey()

        val result = kit.signerManager.addEd25519(
            contextRuleId = 5u,
            verifierAddress = ed25519Verifier,
            publicKey = publicKey
        )

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val invocation = signerInvocation(assertNotNull(simulateBody))
        assertEquals("add_signer", invocation.functionName.value)
        assertEquals(5u, Scv.fromUint32(invocation.args[0]))

        val (discriminant, verifier, keyData) = decodeSigner(invocation.args[1])
        assertEquals("External", discriminant)
        assertEquals(
            ed25519Verifier,
            verifier,
            "The Ed25519 verifier must come from the argument, not from the WebAuthn config"
        )
        assertTrue(
            publicKey.contentEquals(keyData),
            "The key data must be the bare 32-byte Ed25519 public key"
        )
    }

    // ========================================================================
    // removeSigner by ID
    // ========================================================================

    @Test
    fun testRemoveSignerById_submitsRemoveSignerInvocation() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        var requestCount = 0
        val server = signerMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            requestCount = index + 1
            if (index == 1) simulateBody = body
        }

        val kit = connectedKit(deployer, server)

        val result = kit.signerManager.removeSigner(contextRuleId = 3u, signerId = 8u)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)
        assertEquals(9, requestCount, "The round-trip must issue exactly nine RPC calls")

        val invocation = signerInvocation(assertNotNull(simulateBody))
        assertEquals("remove_signer", invocation.functionName.value)
        assertEquals(
            smartAccount,
            Address.fromSCAddress(invocation.contractAddress).toString(),
            "The invocation must target the connected smart account"
        )
        // ABI: remove_signer(context_rule_id: u32, signer_id: u32) - the signer is named by
        // its id, not by an encoded Signer value
        assertEquals(2, invocation.args.size, "remove_signer takes two arguments")
        assertEquals(SCValTypeXdr.SCV_U32, invocation.args[0].discriminant, "arg 0 is the context rule id")
        assertEquals(3u, Scv.fromUint32(invocation.args[0]))
        assertEquals(SCValTypeXdr.SCV_U32, invocation.args[1].discriminant, "arg 1 is the signer id")
        assertEquals(8u, Scv.fromUint32(invocation.args[1]))
    }

    @Test
    fun testRemoveSignerById_selectedSigners_routesToMultiSignerSubmission() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val walletAddress = walletKeypair.getAccountId()
        val authEntryXdr = authEntryFor(walletAddress, targetContract = smartAccount).toXdrBase64()

        var simulateBody: String? = null
        var sendBody: String? = null
        val server = signerMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            if (index == 1) simulateBody = body
            if (index == 7) sendBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.externalSigners.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.signerManager.removeSigner(
            contextRuleId = 0u,
            signerId = 2u,
            selectedSigners = listOf(SelectedSigner.Wallet(walletAddress))
        )

        assertTrue(result.success, "Multi-signer round-trip must succeed; error=${result.error}")
        assertEquals("remove_signer", signerInvocation(assertNotNull(simulateBody)).functionName.value)

        val submitted = signerSubmittedAuth(assertNotNull(sendBody))
        assertEquals(1, submitted.size)
        val credentials = (submitted[0].credentials as SorobanCredentialsXdr.Address).value
        assertTrue(
            credentials.signatureExpirationLedger.value > 0u,
            "The submitted entry must carry a stamped expiration"
        )
        assertTrue(
            credentials.signature !is SCValXdr.Void,
            "The submitted entry must carry the wallet signature"
        )
    }

    // ========================================================================
    // removeSigner by signer value
    // ========================================================================

    @Test
    fun testRemoveSignerBySigner_resolvesSignerIdFromContextRule() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()
        val rule = contextRuleScVal(
            id = 4u,
            name = "Recovery",
            signers = listOf(otherSigner, signerOnRule),
            signerIds = listOf(1u, 12u)
        )

        var ruleQueryBody: String? = null
        var simulateBody: String? = null
        val server = signerMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            ) + submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            if (index == 1) ruleQueryBody = body
            if (index == 3) simulateBody = body
        }

        val kit = connectedKit(deployer, server)

        val result = kit.signerManager.removeSigner(contextRuleId = 4u, signer = signerOnRule)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val ruleQuery = signerInvocation(assertNotNull(ruleQueryBody))
        assertEquals("get_context_rule", ruleQuery.functionName.value)
        assertEquals(4u, Scv.fromUint32(ruleQuery.args[0]), "The rule holding the signer must be fetched")

        val invocation = signerInvocation(assertNotNull(simulateBody))
        assertEquals("remove_signer", invocation.functionName.value)
        assertEquals(4u, Scv.fromUint32(invocation.args[0]))
        assertEquals(
            12u,
            Scv.fromUint32(invocation.args[1]),
            "The signer ID must be the one aligned with the matched signer"
        )
    }

    @Test
    fun testRemoveSignerBySigner_matchesExternalSignerByVerifierAndKeyData() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        val passkeySigner = ExternalSigner.webAuthn(
            verifierAddress = VERIFIER_A,
            publicKey = MockWebAuthnProvider.testPublicKey(seed = 5),
            credentialId = MockWebAuthnProvider.testCredentialId(seed = 5)
        )
        val rule = contextRuleScVal(
            id = 0u,
            name = "Default",
            signers = listOf(signerOnRule, passkeySigner),
            signerIds = listOf(0u, 7u)
        )

        var simulateBody: String? = null
        val server = signerMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            ) + submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 3) simulateBody = body }

        val kit = connectedKit(deployer, server)

        // A structurally identical signer instance must resolve to the on-chain entry.
        val equivalent = ExternalSigner(
            verifierAddress = VERIFIER_A,
            keyData = passkeySigner.keyData.copyOf()
        )
        val result = kit.signerManager.removeSigner(contextRuleId = 0u, signer = equivalent)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val invocation = signerInvocation(assertNotNull(simulateBody))
        assertEquals("remove_signer", invocation.functionName.value)
        assertEquals(7u, Scv.fromUint32(invocation.args[1]))
    }

    @Test
    fun testRemoveSignerBySigner_signerNotOnContextRule_throwsValidationException() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val rule = contextRuleScVal(
            id = 6u,
            name = "Default",
            signers = listOf(otherSigner),
            signerIds = listOf(0u)
        )

        val server = signerMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            )
        )

        val kit = connectedKit(deployer, server)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.signerManager.removeSigner(contextRuleId = 6u, signer = signerOnRule)
        }
        assertTrue(
            exception.message.contains("Signer not found on context rule 6"),
            "The exception must name the context rule that was searched; got: ${exception.message}"
        )
    }

    @Test
    fun testRemoveSignerBySigner_signerIdsShorterThanSigners_throwsValidationException() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        // A rule whose signer_ids vector does not cover every signer: the match resolves to
        // index 1, for which no ID exists.
        val rule = contextRuleScVal(
            id = 2u,
            name = "Default",
            signers = listOf(otherSigner, signerOnRule),
            signerIds = listOf(0u)
        )

        val server = signerMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            )
        )

        val kit = connectedKit(deployer, server)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.signerManager.removeSigner(contextRuleId = 2u, signer = signerOnRule)
        }
        assertTrue(
            exception.message.contains("index 1") && exception.message.contains("only 1 entries"),
            "The exception must report the misaligned index and ID count; got: ${exception.message}"
        )
    }

    // ========================================================================
    // addNewPasskeySigner
    // ========================================================================

    @Test
    fun testAddNewPasskeySigner_registersPasskeyAndAddsItOnChain() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = signerMockServer(
            submitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val provider = MockWebAuthnProvider()
        val registration = WebAuthnRegistrationResult(
            credentialId = MockWebAuthnProvider.testCredentialId(seed = 9),
            publicKey = MockWebAuthnProvider.testPublicKey(seed = 9),
            attestationObject = MockWebAuthnProvider.testAttestationObject(seed = 9),
            transports = listOf("internal"),
            deviceType = "multiDevice",
            backedUp = true
        )
        provider.registrationResult = registration

        val storage = InMemoryStorageAdapter()
        val kit = connectedKit(deployer, server, webauthnProvider = provider, storage = storage)

        val createdEvents = mutableListOf<SmartAccountEvent.CredentialCreated>()
        kit.events.on<SmartAccountEvent.CredentialCreated> { createdEvents.add(it) }

        val result = kit.signerManager.addNewPasskeySigner(
            contextRuleId = 1u,
            userName = "Recovery Passkey"
        )

        // The registration ceremony ran once with fresh 32-byte random material.
        assertEquals(1, provider.registerCallCount)
        assertEquals("Recovery Passkey", provider.lastRegisterUserName)
        assertEquals(32, assertNotNull(provider.lastRegisterChallenge).size)
        assertEquals(32, assertNotNull(provider.lastRegisterUserId).size)
        assertTrue(
            !assertNotNull(provider.lastRegisterChallenge)
                .contentEquals(assertNotNull(provider.lastRegisterUserId)),
            "The challenge and the user ID must be independently generated"
        )

        // The returned result combines the credential with the on-chain outcome.
        val expectedCredentialId = Util.base64urlEncode(registration.credentialId)
        assertEquals(expectedCredentialId, result.credentialId)
        assertTrue(
            registration.publicKey.contentEquals(result.publicKey),
            "The returned public key must be the one the authenticator produced"
        )
        assertTrue(result.transactionResult.success, "error=${result.transactionResult.error}")
        assertEquals(txHash, result.transactionResult.hash)

        // The credential was persisted as pending and announced before the on-chain step.
        assertEquals(listOf(expectedCredentialId), createdEvents.map { it.credential.credentialId })
        val stored = assertNotNull(storage.get(expectedCredentialId))
        assertEquals(smartAccount, stored.contractId)
        assertEquals(CredentialDeploymentStatus.PENDING, stored.deploymentStatus)

        // The on-chain step added exactly the freshly registered passkey.
        val invocation = signerInvocation(assertNotNull(simulateBody))
        assertEquals("add_signer", invocation.functionName.value)
        assertEquals(1u, Scv.fromUint32(invocation.args[0]))
        val (discriminant, verifier, keyData) = decodeSigner(invocation.args[1])
        assertEquals("External", discriminant)
        assertEquals(VERIFIER_A, verifier)
        assertTrue(
            registration.publicKey.plus(registration.credentialId).contentEquals(keyData),
            "The added signer must carry the newly registered public key and credential ID"
        )
    }

    @Test
    fun testAddNewPasskeySigner_registrationFailsWithoutMessage_reportsUnknownError() = runTest {
        val kit = connectedKit(
            KeyPair.random(),
            signerMockServer(emptyList()),
            webauthnProvider = MessagelessFailureWebAuthnProvider()
        )

        val exception = assertFailsWith<WebAuthnException.RegistrationFailed> {
            kit.signerManager.addNewPasskeySigner(contextRuleId = 0u, userName = "Recovery Passkey")
        }
        assertTrue(
            exception.message.contains("Unknown error"),
            "A message-less authenticator failure must still produce a described error; " +
                "got: ${exception.message}"
        )
    }
}
