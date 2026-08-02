//
//  WalletOperationsValidationTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.MockWebAuthnProvider
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildConstantResponseMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.buildMinimalSorobanData
import com.soneso.stellar.sdk.unitTests.smartaccount.buildNoRpcMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.contractInstanceEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.emptyLedgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.relayerSuccessWithoutHashJson
import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.currentTimeMillis
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.xdr.toXdrBase64
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for [OZWalletOperations]: the wallet lifecycle end to end, its result types, and the
 * kit connection state it maintains.
 *
 * The wallet lifecycle is exercised against a Ktor [MockEngine]-backed [SorobanServer] injected
 * through [OZSmartAccountKit.createWithServer], with a [MockWebAuthnProvider] standing in for the
 * platform passkey ceremony. No test touches the network.
 *
 * Behaviour pinned here:
 * - [OZWalletOperations.createWallet]: pre-ceremony validation, the passkey ceremony, contract
 *   address derivation, pending-credential storage, connection state and session, the signed
 *   deploy envelope, optional submission with confirmation polling, relayer submission with its
 *   response arms, and failure handling (registration failure, storage failure, build failure
 *   marking the credential FAILED).
 * - [OZWalletOperations.connectWallet]: silent session restore, expired-session handling, stale
 *   session clearing, the WebAuthn prompt path, and the storage / derivation / indexer resolution
 *   cascade including the ambiguous multi-contract outcome.
 * - [OZWalletOperations.authenticatePasskey]: challenge selection, `allowCredentials` enrichment
 *   from stored transports, signature normalization (DER to compact, low-S), and the public-key
 *   lookup with its empty-array fallback.
 * - [OZWalletOperations.deployPendingCredential]: input and credential validation, deferred build,
 *   submission, and credential cleanup.
 * - The deploy build and submit internals: relayer versus RPC fee construction, simulation
 *   failures, submission failures, and confirmation timeout.
 * - [OZWalletOperations.ConnectWalletOptions], [CreateWalletResult], [ConnectWalletResult],
 *   [DeployPendingResult] and [AuthenticatePasskeyResult] construction, equality and hashCode.
 * - [OZSmartAccountKit.disconnect], [OZSmartAccountKit.isConnected],
 *   [OZSmartAccountKit.credentialId], [OZSmartAccountKit.contractId] and
 *   [OZSmartAccountKit.requireConnected].
 *
 * The relayer and indexer clients are injected the same way: each is constructed over its own
 * Ktor [MockEngine] and handed to [OZSmartAccountKit.createWithServer], so the deploy relayer
 * arms and the indexer cascade arms run against scripted responses.
 *
 * Out of reach for a hermetic unit test:
 * - the exception handler wrapping `OZRelayerClient.sendXdr` inside the deploy submission:
 *   `sendXdr` captures every non-fatal failure in its returned `RelayerResponse` and only
 *   propagates coroutine cancellation and fatal platform errors, neither of which is a
 *   meaningful unit-test input.
 */
class WalletOperationsValidationTest {

    // MARK: - Test Fixtures

    private val validContractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"

    private val otherContractAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    private val rpcUrl = "https://soroban-testnet.stellar.org"

    private fun createKit(): OZSmartAccountKit {
        val config = OZSmartAccountConfig(
            rpcUrl = rpcUrl,
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = validContractAddress
        )
        return OZSmartAccountKit.create(config)
    }

    private fun testPublicKey(fill: Byte = 0x42): ByteArray {
        val key = ByteArray(65)
        key[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until 65) key[i] = fill
        return key
    }

    // MARK: - WebAuthn Fixtures

    /** Credential ID the mocked registration and authentication ceremonies return. */
    private val credentialIdBytes = ByteArray(16) { it.toByte() }

    private val credentialIdB64 = Util.base64urlEncode(credentialIdBytes)

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /**
     * Uncompressed secp256r1 public key formed from the curve's generator point. Registration
     * public-key extraction validates that the point lies on the curve, so an arbitrary 65-byte
     * array would be rejected.
     */
    private fun registrationPublicKey(): ByteArray =
        byteArrayOf(0x04) +
            hexBytes("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296") +
            hexBytes("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")

    /**
     * DER signature (r = 0x0102..32, s = 5) whose s is already below the curve half-order, so
     * normalization only re-encodes it into the 64-byte compact form.
     */
    private fun lowSDerSignature(): ByteArray = hexBytes(
        "30440220" +
            "0102030405060708091011121314151617181920212223242526272829303132" +
            "0220" +
            "0000000000000000000000000000000000000000000000000000000000000005"
    )

    /**
     * DER signature with r = 1 and s = n - 5, where n is the secp256r1 group order. s is above the
     * half-order, so low-S normalization must map it to 5.
     */
    private fun highSDerSignature(): ByteArray = hexBytes(
        "3026020101022100" +
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc63254c"
    )

    private fun registeringProvider(
        credentialId: ByteArray = credentialIdBytes
    ): MockWebAuthnProvider = MockWebAuthnProvider().apply {
        registrationResult = WebAuthnRegistrationResult(
            credentialId = credentialId,
            publicKey = registrationPublicKey(),
            attestationObject = ByteArray(0),
            transports = listOf("internal"),
            deviceType = "multiDevice",
            backedUp = true
        )
    }

    private fun authenticatingProvider(
        signature: ByteArray = lowSDerSignature(),
        credentialId: ByteArray = credentialIdBytes
    ): MockWebAuthnProvider = MockWebAuthnProvider().apply {
        authenticationResult = WebAuthnAuthenticationResult(
            credentialId = credentialId,
            authenticatorData = ByteArray(37) { it.toByte() },
            clientDataJSON = """{"type":"webauthn.get","challenge":"unit-test"}""".encodeToByteArray(),
            signature = signature
        )
    }

    // MARK: - Soroban RPC Mocking

    /**
     * A single mocked Soroban RPC reply: the response body and the HTTP status it is served with.
     * A non-2xx status with a non-JSON body models a transport / server failure, which the RPC
     * client surfaces to the caller as an exception.
     */
    private data class RpcReply(val body: String, val status: HttpStatusCode = HttpStatusCode.OK)

    /**
     * A [SorobanServer] backed by a Ktor [MockEngine] that answers every JSON-RPC request from
     * [reply], which receives the zero-based request index and the JSON-RPC method name. The
     * method sequence is recorded so tests can assert which round-trips a flow actually made.
     *
     * [unconfinedDispatch] runs the engine's request handler on
     * [kotlinx.coroutines.Dispatchers.Unconfined] instead of the engine default. The
     * contract-visibility poll wraps its RPC round-trip in a `withTimeoutOrNull`, and under
     * `runTest`'s virtual clock a handler dispatched off the test scheduler races the automatic
     * advance to the timeout deadline. Running the handler unconfined keeps the round-trip on the
     * coroutine already suspended under the test scheduler, so the probe's success is observable.
     */
    private class MockRpc(
        unconfinedDispatch: Boolean = false,
        reply: MockRpc.(index: Int, method: String) -> RpcReply
    ) {

        val calls = mutableListOf<String>()

        /** Requests the script did not expect, recorded for assertion after the call returns. */
        val unexpectedRequests = mutableListOf<String>()

        val server: SorobanServer

        init {
            val handler: MockRequestHandler = { request ->
                val method = METHOD_PATTERN
                    .find(request.body.toByteArray().decodeToString())
                    ?.groupValues?.get(1) ?: ""
                // Record before replying so a request is visible in [calls] even when the
                // handler itself raises.
                val index = calls.size
                calls.add(method)
                val response = reply(this@MockRpc, index, method)
                respond(
                    content = ByteReadChannel(response.body),
                    status = response.status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val engine = if (unconfinedDispatch) {
                MockEngine(MockEngineConfig().apply {
                    dispatcher = Dispatchers.Unconfined
                    addHandler(handler)
                })
            } else {
                MockEngine(handler)
            }
            server = SorobanServer(
                "https://soroban-testnet.stellar.org",
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                                encodeDefaults = false
                            }
                        )
                    }
                }
            )
        }

        fun callCount(method: String): Int = calls.count { it == method }

        /**
         * Records an out-of-script request and answers it with an empty result.
         *
         * `kotlin.test.fail` must not be used inside a handler: [SorobanServer] wraps any
         * non-[Exception] [Throwable] raised by the engine into a connection error, so an
         * `AssertionError` thrown here is swallowed by the production error handling and the
         * test still passes. Tests assert on [assertNoUnexpectedRequests] after the call
         * under test returns, where an assertion failure cannot be intercepted.
         */
        fun unexpected(index: Int, reason: String): RpcReply {
            unexpectedRequests.add("request $index: $reason")
            return RpcReply(EMPTY_ENTRIES_BODY)
        }

        fun assertNoUnexpectedRequests() {
            assertTrue(
                unexpectedRequests.isEmpty(),
                "The flow made RPC requests it must not make: $unexpectedRequests (full " +
                    "sequence: $calls)"
            )
        }

        private companion object {
            val METHOD_PATTERN = Regex("\"method\"\\s*:\\s*\"([^\"]+)\"")

            const val EMPTY_ENTRIES_BODY =
                """{"jsonrpc":"2.0","id":"test","result":{"entries":[],"latestLedger":100}}"""
        }
    }

    // MARK: - Relayer and Indexer Mocking

    /**
     * An [OZRelayerClient] over a Ktor [MockEngine] that answers every submission with
     * [responseBody]. Each request payload is recorded so tests can assert which submission mode
     * the deploy used and what it sent.
     */
    private class MockRelayer(
        responseBody: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        val requestBodies = mutableListOf<String>()

        val client: OZRelayerClient

        init {
            val engine = MockEngine { request ->
                requestBodies.add(request.body.toByteArray().decodeToString())
                respond(
                    content = ByteReadChannel(responseBody),
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            client = OZRelayerClient(
                relayerUrl = "https://relayer.example.com",
                injectedClient = HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; isLenient = true })
                    }
                }
            )
        }

        /** The single recorded payload, parsed as a JSON object. */
        fun singleRequestJson() = Json.parseToJsonElement(requestBodies.single()).jsonObject
    }

    /**
     * An [OZIndexerClient] over a Ktor [MockEngine] that answers every lookup with [responseBody].
     * Requested URLs are recorded so tests can assert the lookup key the SDK derived.
     */
    private class MockIndexer(
        responseBody: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        val requestedUrls = mutableListOf<String>()

        val client: OZIndexerClient

        init {
            val engine = MockEngine { request ->
                requestedUrls.add(request.url.toString())
                respond(
                    content = ByteReadChannel(responseBody),
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            client = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; isLenient = true })
                    }
                }
            )
        }
    }

    /** A relayer success body carrying [hash], matching the nested `data` wrapper the service uses. */
    private fun relayerSuccessJson(hash: String = deployTxHash): String =
        """{"success":true,"data":{"transactionId":"relayer-tx-1","hash":"$hash","status":"PENDING"}}"""

    private fun relayerErrorJson(error: String, code: String): String =
        """{"success":false,"error":"$error","code":"$code"}"""

    /** A `lookupByCredentialId` body listing [contractIds] as the contracts holding the passkey. */
    private fun indexerLookupJson(credentialId: String, contractIds: List<String>): String {
        val contracts = contractIds.joinToString(",") { contractId ->
            """
            {
              "contract_id": "$contractId",
              "context_rule_count": 1,
              "external_signer_count": 1,
              "delegated_signer_count": 0,
              "native_signer_count": 0,
              "first_seen_ledger": 100,
              "last_seen_ledger": 200,
              "context_rule_ids": [0]
            }
            """.trimIndent()
        }
        return """{"credentialId":"$credentialId","contracts":[$contracts],"count":${contractIds.size}}"""
    }

    /** Resource fee the mocked simulation reports; distinct from the base fee so fees are unambiguous. */
    private val simulatedMinResourceFee = 12_345L

    private val deployTxHash = "b1721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

    private val transportFailure = RpcReply("upstream unavailable", HttpStatusCode.InternalServerError)

    private fun deployerAccountJson(deployer: KeyPair): String =
        ledgerEntriesResponseJson(buildAccountEntryXdr(deployer).toXdrBase64())

    private fun deploySimulationJson(minResourceFee: Long? = simulatedMinResourceFee): String {
        val feeField = minResourceFee?.let { "\"minResourceFee\": $it," } ?: ""
        return """
        {
          "jsonrpc": "2.0",
          "id": "test-id",
          "result": {
            "transactionData": "${buildMinimalSorobanData().toXdrBase64()}",
            $feeField
            "results": [ { "auth": [], "xdr": null } ],
            "latestLedger": 100
          }
        }
        """.trimIndent()
    }

    private fun simulationErrorJson(error: String): String =
        """{"jsonrpc":"2.0","id":"test-id","result":{"error":"$error","latestLedger":100}}"""

    private fun sendPendingJson(hash: String = deployTxHash): String =
        """{"jsonrpc":"2.0","id":"test-id","result":{"status":"PENDING","hash":"$hash",""" +
            """"latestLedger":1001,"latestLedgerCloseTime":1700000000}}"""

    private fun sendErrorResultJson(errorResultXdr: String): String =
        """{"jsonrpc":"2.0","id":"test-id","result":{"status":"ERROR",""" +
            """"errorResultXdr":"$errorResultXdr","latestLedger":1001,"latestLedgerCloseTime":1700000000}}"""

    private fun sendWithoutHashJson(): String =
        """{"jsonrpc":"2.0","id":"test-id","result":{"status":"PENDING",""" +
            """"latestLedger":1001,"latestLedgerCloseTime":1700000000}}"""

    private fun transactionSuccessJson(): String =
        """{"jsonrpc":"2.0","id":"test-id","result":{"status":"SUCCESS","latestLedger":1002,""" +
            """"latestLedgerCloseTime":1700000010,"oldestLedger":900,"oldestLedgerCloseTime":1699990000,""" +
            """"ledger":1001,"createdAt":1700000000}}"""

    private fun transactionNotFoundJson(): String =
        """{"jsonrpc":"2.0","id":"test-id","result":{"status":"NOT_FOUND","latestLedger":1002,""" +
            """"latestLedgerCloseTime":1700000010,"oldestLedger":900,"oldestLedgerCloseTime":1699990000}}"""

    private fun transactionFailedJson(resultXdr: String): String =
        """{"jsonrpc":"2.0","id":"test-id","result":{"status":"FAILED","resultXdr":"$resultXdr",""" +
            """"latestLedger":1002,"latestLedgerCloseTime":1700000010,"oldestLedger":900,""" +
            """"oldestLedgerCloseTime":1699990000,"ledger":1001,"createdAt":1700000000}}"""

    // MARK: - Storage Fixtures

    /**
     * Storage adapter that returns the saved session verbatim, including once it has expired.
     *
     * The adapters bundled with the SDK drop expired sessions inside `getSession()`. This one
     * models a third-party adapter that leaves the expiry decision to the caller, which is what
     * drives [OZWalletOperations.connectWallet]'s own expired-session handling.
     */
    private class RawSessionStorage : StorageAdapter by InMemoryStorageAdapter() {
        private var stored: StoredSession? = null

        override suspend fun saveSession(session: StoredSession) {
            stored = session
        }

        override suspend fun getSession(): StoredSession? = stored

        override suspend fun clearSession() {
            stored = null
        }
    }

    /** Storage adapter whose credential reads fail, modelling a broken persistence backend. */
    private class FailingReadStorage : StorageAdapter by InMemoryStorageAdapter() {
        override suspend fun get(credentialId: String): StoredCredential? =
            throw IllegalStateException("storage backend unavailable")
    }

    /** Storage adapter whose session reads fail, modelling a corrupted session record. */
    private class FailingSessionReadStorage : StorageAdapter by InMemoryStorageAdapter() {
        override suspend fun getSession(): StoredSession? =
            throw IllegalStateException("session record unreadable")
    }

    // MARK: - Kit Builders

    private fun mockConfig(
        provider: WebAuthnProvider? = null,
        deployer: KeyPair? = null,
        storage: StorageAdapter = InMemoryStorageAdapter()
    ) = OZSmartAccountConfig(
        rpcUrl = rpcUrl,
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = validContractAddress,
        deployerKeypair = deployer,
        webauthnProvider = provider,
        storage = storage
    )

    private fun mockKit(
        server: SorobanServer,
        provider: WebAuthnProvider? = null,
        deployer: KeyPair? = null,
        storage: StorageAdapter = InMemoryStorageAdapter(),
        relayerClient: OZRelayerClient? = null,
        indexerClient: OZIndexerClient? = null
    ): OZSmartAccountKit = OZSmartAccountKit.createWithServer(
        config = mockConfig(provider, deployer, storage),
        sorobanServer = server,
        relayerClient = relayerClient,
        indexerClient = indexerClient
    )

    private suspend fun derivedContractId(
        deployer: KeyPair,
        credentialId: ByteArray = credentialIdBytes
    ): String = SmartAccountUtils.deriveContractAddress(
        credentialId = credentialId,
        deployerPublicKey = deployer.getAccountId(),
        networkPassphrase = Network.TESTNET.networkPassphrase
    )

    // ========================================================================
    // createWallet() Pre-Network Validation
    // ========================================================================

    @Test
    fun testCreateWallet_noWebAuthnProvider_throwsNotSupported() = runTest {
        val kit = createKit()
        val exception = assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet()
        }
        assertTrue(
            exception.message!!.contains("No WebAuthnProvider configured"),
            "Exception message should mention missing provider"
        )
    }

    @Test
    fun testCreateWallet_noWebAuthnProvider_withCustomUserName() = runTest {
        val kit = createKit()
        val exception = assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet(userName = "Alice")
        }
        assertNotNull(exception.message)
    }

    @Test
    fun testCreateWallet_noWebAuthnProvider_withAutoSubmit() = runTest {
        // WebAuthn check happens before autoSubmit logic
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
    }

    @Test
    fun testCreateWallet_noWebAuthnProvider_withAutoFundAndToken() = runTest {
        // WebAuthn check happens before autoFund validation
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet(
                autoFund = true,
                nativeTokenContract = validContractAddress
            )
        }
    }

    @Test
    fun testCreateWallet_autoFundWithoutToken_throwsBeforeCeremony() = runTest {
        // autoFund without a native token contract is rejected before the passkey ceremony, so a
        // misconfigured call never leaves an orphaned credential behind.
        val provider = registeringProvider()
        val kit = mockKit(buildNoRpcMockServer(), provider)

        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.createWallet(autoFund = true, nativeTokenContract = null)
        }
        assertEquals(0, provider.registerCallCount, "autoFund validation must precede the passkey ceremony")
        assertFalse(kit.isConnected)
    }

    // ========================================================================
    // createWallet() Deploy Flow
    // ========================================================================

    @Test
    fun testCreateWallet_autoSubmitFalse_signsDeployAndKeepsCredentialPending() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> unexpected(index, "the flow must stop after its scripted round-trips")
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val credentialCreated = mutableListOf<SmartAccountEvent.CredentialCreated>()
        val walletConnected = mutableListOf<SmartAccountEvent.WalletConnected>()
        kit.events.on<SmartAccountEvent.CredentialCreated> { credentialCreated.add(it) }
        kit.events.on<SmartAccountEvent.WalletConnected> { walletConnected.add(it) }

        val result = kit.walletOperations.createWallet(userName = "Alice", autoSubmit = false)

        val expectedContractId = derivedContractId(deployer)
        assertEquals(credentialIdB64, result.credentialId)
        assertEquals(expectedContractId, result.contractId)
        assertContentEquals(registrationPublicKey(), result.publicKey)
        assertEquals("Alice", result.nickname)
        assertNull(result.transactionHash, "autoSubmit = false must not submit the deploy transaction")

        // The deploy envelope is always produced, assembled from the simulation and deployer-signed.
        val signed = Transaction.fromEnvelopeXdr(result.signedTransactionXdr, Network.TESTNET)
        assertEquals(deployer.getAccountId(), signed.sourceAccount)
        assertEquals(
            AbstractTransaction.MIN_BASE_FEE + simulatedMinResourceFee,
            signed.fee,
            "without a relayer the fee is the classic fee plus the simulated resource fee"
        )
        assertEquals(1, signed.signatures.size, "the deploy transaction must carry the deployer signature")

        // Connection state and session are established before deployment.
        assertTrue(kit.isConnected)
        assertEquals(credentialIdB64, kit.credentialId)
        assertEquals(expectedContractId, kit.contractId)
        val session = assertNotNull(kit.getStorage().getSession())
        assertEquals(credentialIdB64, session.credentialId)
        assertEquals(expectedContractId, session.contractId)

        // The credential remains available for a later deployPendingCredential retry.
        val stored = assertNotNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(CredentialDeploymentStatus.PENDING, stored.deploymentStatus)
        assertEquals(expectedContractId, stored.contractId)
        assertEquals(listOf("internal"), stored.transports, "registration transports must be persisted")
        assertEquals("multiDevice", stored.deviceType)
        assertEquals(true, stored.backedUp)
        assertTrue(stored.isPrimary, "the wallet-creation passkey is the primary credential")
        assertEquals("Alice", stored.nickname)

        assertEquals(1, credentialCreated.size)
        assertEquals(credentialIdB64, credentialCreated.single().credential.credentialId)
        assertEquals(1, walletConnected.size)
        assertEquals(expectedContractId, walletConnected.single().contractId)
        assertEquals(credentialIdB64, walletConnected.single().credentialId)
    }

    @Test
    fun testCreateWallet_autoSubmitTrue_submitsConfirmsAndDropsCredential() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val expectedContractId = derivedContractId(deployer)
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                3 -> RpcReply(transactionSuccessJson())
                // The post-deploy credential cleanup re-checks the contract on-chain.
                else -> RpcReply(contractInstanceEntriesResponseJson(expectedContractId))
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val result = kit.walletOperations.createWallet(userName = "Bob", autoSubmit = true)

        assertEquals(deployTxHash, result.transactionHash)
        assertEquals(expectedContractId, result.contractId)
        assertTrue(result.signedTransactionXdr.isNotEmpty())
        assertEquals(1, rpc.callCount("sendTransaction"))
        assertEquals(1, rpc.callCount("getTransaction"), "a SUCCESS on the first poll ends the loop")

        assertNull(
            kit.credentialManager.getCredential(credentialIdB64),
            "a confirmed deployment removes the transitional credential from storage"
        )
        assertTrue(kit.isConnected)
        assertEquals(expectedContractId, kit.contractId)
    }

    @Test
    fun testCreateWallet_confirmationPollRetriesUntilSuccess() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val expectedContractId = derivedContractId(deployer)
        var getTransactionCalls = 0
        val rpc = MockRpc { index, method ->
            when {
                index == 0 -> RpcReply(deployerAccountJson(deployer))
                index == 1 -> RpcReply(deploySimulationJson())
                index == 2 -> RpcReply(sendPendingJson())
                method == "getTransaction" -> {
                    getTransactionCalls++
                    if (getTransactionCalls < 3) RpcReply(transactionNotFoundJson())
                    else RpcReply(transactionSuccessJson())
                }
                else -> RpcReply(contractInstanceEntriesResponseJson(expectedContractId))
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val result = kit.walletOperations.createWallet(autoSubmit = true)

        assertEquals(deployTxHash, result.transactionHash)
        assertEquals(
            3,
            getTransactionCalls,
            "NOT_FOUND keeps the confirmation loop polling until the transaction lands"
        )
    }

    @Test
    fun testCreateWallet_registrationFailure_wrappedAsRegistrationFailed() = runTest {
        val provider = MockWebAuthnProvider().apply {
            registrationException = WebAuthnException.cancelled()
        }
        val kit = mockKit(buildNoRpcMockServer(), provider)

        val exception = assertFailsWith<WebAuthnException.RegistrationFailed> {
            kit.walletOperations.createWallet()
        }
        assertTrue(
            exception.message.contains("User cancelled WebAuthn operation"),
            "the wrapper must carry the provider's failure detail; got: ${exception.message}"
        )
        assertFalse(kit.isConnected, "a failed ceremony must not leave the kit connected")
        assertTrue(kit.credentialManager.getAllCredentials().isEmpty())
    }

    @Test
    fun testCreateWallet_credentialStorageFailure_wrappedAsStorageException() = runTest {
        // A storage backend that fails to read cannot support the duplicate check inside
        // createPendingCredential; the raw failure is normalized to StorageException.WriteFailed
        // naming the credential it could not persist.
        val provider = registeringProvider()
        val kit = mockKit(buildNoRpcMockServer(), provider, storage = FailingReadStorage())

        val exception = assertFailsWith<StorageException.WriteFailed> {
            kit.walletOperations.createWallet()
        }
        assertTrue(
            exception.message.contains(credentialIdB64),
            "the storage failure must name the credential key; got: ${exception.message}"
        )
        assertEquals(1, provider.registerCallCount)
        assertFalse(kit.isConnected, "storage failure must abort before the connected state is set")
    }

    @Test
    fun testCreateWallet_deployBuildFailure_marksCredentialFailed() = runTest {
        // The deployer-account fetch is the first network step of the deploy build. An RPC-less
        // server fails it, and the credential is marked FAILED so deployPendingCredential can retry.
        val provider = registeringProvider()
        val kit = mockKit(buildNoRpcMockServer(), provider)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet()
        }
        assertTrue(
            exception.message.contains("Failed to fetch deployer account"),
            "the build failure must name the failing step; got: ${exception.message}"
        )

        val stored = assertNotNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(CredentialDeploymentStatus.FAILED, stored.deploymentStatus)
        assertNotNull(stored.deploymentError, "the failure reason must be recorded on the credential")
    }

    @Test
    fun testCreateWallet_simulationTransportFailure_wrappedAsSimulationFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                else -> transportFailure
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.SimulationFailed> {
            kit.walletOperations.createWallet()
        }
        assertTrue(
            exception.message.contains("Failed to simulate deployment transaction"),
            "a transport failure during simulation must surface as a simulation failure; got: ${exception.message}"
        )
        assertEquals(
            CredentialDeploymentStatus.FAILED,
            assertNotNull(kit.credentialManager.getCredential(credentialIdB64)).deploymentStatus
        )
    }

    @Test
    fun testCreateWallet_simulationReturnsError_throwsSimulationFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                else -> RpcReply(simulationErrorJson("HostError: contract wasm not found"))
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.SimulationFailed> {
            kit.walletOperations.createWallet()
        }
        assertTrue(
            exception.message.contains("contract wasm not found"),
            "the simulation error text must reach the caller; got: ${exception.message}"
        )
    }

    // ========================================================================
    // createWallet() Submission Method Selection
    // ========================================================================

    @Test
    fun testCreateWallet_forceRelayer_feeIsResourceFeeOnly() = runTest {
        // The relayer wraps the deploy in a fee bump carrying the outer fee, so the inner
        // transaction fee must be exactly the simulated resource fee rather than the assembled
        // classic-plus-resource total.
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> unexpected(index, "the flow must stop after its scripted round-trips")
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val result = kit.walletOperations.createWallet(
            autoSubmit = false,
            forceMethod = SubmissionMethod.RELAYER
        )

        val signed = Transaction.fromEnvelopeXdr(result.signedTransactionXdr, Network.TESTNET)
        assertEquals(simulatedMinResourceFee, signed.fee)
        assertEquals(1, signed.signatures.size, "the rebuilt relayer transaction must still be signed")
    }

    @Test
    fun testCreateWallet_forceRelayer_simulationWithoutResourceFee_throwsSubmissionFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                else -> RpcReply(deploySimulationJson(minResourceFee = null))
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet(
                autoSubmit = false,
                forceMethod = SubmissionMethod.RELAYER
            )
        }
        assertTrue(
            exception.message.contains("min resource fee"),
            "the relayer fee rebuild must report the missing resource fee; got: ${exception.message}"
        )
        assertEquals(
            CredentialDeploymentStatus.FAILED,
            assertNotNull(kit.credentialManager.getCredential(credentialIdB64)).deploymentStatus
        )
    }

    @Test
    fun testCreateWallet_forceRelayer_withoutRelayerClient_throwsSubmissionFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> unexpected(index, "submission must not reach the RPC when the relayer is selected")
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet(
                autoSubmit = true,
                forceMethod = SubmissionMethod.RELAYER
            )
        }
        assertTrue(
            exception.message.contains("no relayer is configured"),
            "forcing the relayer without one configured must say so; got: ${exception.message}"
        )
        assertEquals(
            CredentialDeploymentStatus.PENDING,
            assertNotNull(kit.credentialManager.getCredential(credentialIdB64)).deploymentStatus,
            "a submission that never started must leave the credential retryable rather than FAILED"
        )
    }

    @Test
    fun testCreateWallet_forceRpc_submitsThroughRpc() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val expectedContractId = derivedContractId(deployer)
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                3 -> RpcReply(transactionSuccessJson())
                else -> RpcReply(contractInstanceEntriesResponseJson(expectedContractId))
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val result = kit.walletOperations.createWallet(
            autoSubmit = true,
            forceMethod = SubmissionMethod.RPC
        )

        assertEquals(deployTxHash, result.transactionHash)
        val signed = Transaction.fromEnvelopeXdr(result.signedTransactionXdr, Network.TESTNET)
        assertEquals(
            AbstractTransaction.MIN_BASE_FEE + simulatedMinResourceFee,
            signed.fee,
            "an explicitly forced RPC submission keeps the assembled classic-plus-resource fee"
        )
    }

    // ========================================================================
    // createWallet() Relayer Submission
    //
    // A configured relayer is selected without any forceMethod, and the deploy is handed over
    // as a signed envelope (relayer XDR mode) rather than sent to the RPC.
    // ========================================================================

    @Test
    fun testCreateWallet_relayerConfigured_submitsSignedEnvelopeAndConfirms() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val expectedContractId = derivedContractId(deployer)
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(transactionSuccessJson())
                else -> RpcReply(contractInstanceEntriesResponseJson(expectedContractId))
            }
        }
        val relayer = MockRelayer(relayerSuccessJson())
        val kit = mockKit(rpc.server, provider, deployer, relayerClient = relayer.client)

        val result = kit.walletOperations.createWallet(userName = "Rita", autoSubmit = true)

        assertEquals(deployTxHash, result.transactionHash, "the hash reported by the relayer is returned")
        assertEquals(expectedContractId, result.contractId)
        assertEquals(
            0,
            rpc.callCount("sendTransaction"),
            "a configured relayer is auto-detected, so the deploy never reaches the RPC submit endpoint"
        )
        assertEquals(1, rpc.callCount("getTransaction"), "confirmation still polls the RPC for the relayed hash")

        // The relayer receives the signed envelope under "xdr" — the fee-bump mode — not the
        // host function and auth entries.
        val payload = relayer.singleRequestJson()
        assertNull(payload["func"], "the deploy uses the signed-envelope relayer mode")
        val submittedXdr = assertNotNull(payload["xdr"]).jsonPrimitive.content
        assertEquals(
            result.signedTransactionXdr,
            submittedXdr,
            "the relayer must receive exactly the envelope reported back to the caller"
        )
        val submitted = Transaction.fromEnvelopeXdr(submittedXdr, Network.TESTNET)
        assertEquals(
            simulatedMinResourceFee,
            submitted.fee,
            "the relayer wraps the inner transaction in a fee bump, so its fee is the resource fee alone"
        )
        assertEquals(1, submitted.signatures.size, "the relayer fee-bumps an already deployer-signed envelope")

        assertNull(
            kit.credentialManager.getCredential(credentialIdB64),
            "a relayed deployment confirmed on-chain removes the transitional credential"
        )
        assertTrue(kit.isConnected)
    }

    @Test
    fun testCreateWallet_relayerRejectsSubmission_marksCredentialFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> unexpected(index, "a rejected relayer submission must not fall back to the RPC")
            }
        }
        val relayer = MockRelayer(
            relayerErrorJson("relayer channel pool exhausted", RelayerErrorCodes.POOL_CAPACITY)
        )
        val kit = mockKit(rpc.server, provider, deployer, relayerClient = relayer.client)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
        assertTrue(
            exception.message.contains("relayer channel pool exhausted"),
            "the relayer's own error text must reach the caller; got: ${exception.message}"
        )

        val stored = assertNotNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(
            CredentialDeploymentStatus.FAILED,
            stored.deploymentStatus,
            "a rejected relayer submission is a failed deployment attempt"
        )
        assertTrue(
            assertNotNull(stored.deploymentError).contains("relayer channel pool exhausted"),
            "the recorded failure reason must carry the relayer error; got: ${stored.deploymentError}"
        )
    }

    @Test
    fun testCreateWallet_relayerAcceptsWithoutHash_throwsSubmissionFailed() = runTest {
        // A relayer that reports success but withholds the hash leaves the caller unable to
        // confirm the deployment, so the submission is treated as failed.
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> unexpected(index, "confirmation must not start without a transaction hash")
            }
        }
        val relayer = MockRelayer(relayerSuccessWithoutHashJson())
        val kit = mockKit(rpc.server, provider, deployer, relayerClient = relayer.client)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
        assertTrue(
            exception.message.contains("No transaction hash returned from relayer"),
            "got: ${exception.message}"
        )
        assertEquals(
            CredentialDeploymentStatus.PENDING,
            assertNotNull(kit.credentialManager.getCredential(credentialIdB64)).deploymentStatus,
            "the relayer accepted the submission, so the credential stays retryable rather than FAILED"
        )
    }

    @Test
    fun testCreateWallet_relayerConfiguredButForcedRpc_leavesRelayerUntouched() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val expectedContractId = derivedContractId(deployer)
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                3 -> RpcReply(transactionSuccessJson())
                else -> RpcReply(contractInstanceEntriesResponseJson(expectedContractId))
            }
        }
        val relayer = MockRelayer(relayerSuccessJson())
        val kit = mockKit(rpc.server, provider, deployer, relayerClient = relayer.client)

        val result = kit.walletOperations.createWallet(
            autoSubmit = true,
            forceMethod = SubmissionMethod.RPC
        )

        assertEquals(deployTxHash, result.transactionHash)
        assertEquals(1, rpc.callCount("sendTransaction"))
        assertTrue(relayer.requestBodies.isEmpty(), "forceMethod must override the configured relayer")
        val signed = Transaction.fromEnvelopeXdr(result.signedTransactionXdr, Network.TESTNET)
        assertEquals(
            AbstractTransaction.MIN_BASE_FEE + simulatedMinResourceFee,
            signed.fee,
            "forcing the RPC also selects the classic-plus-resource fee, not the relayer fee shape"
        )
    }

    // ========================================================================
    // createWallet() Submission Failures
    // ========================================================================

    @Test
    fun testCreateWallet_sendTransactionTransportFailure_marksCredentialFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> transportFailure
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
        assertTrue(
            exception.message.contains("Failed to send deployment transaction"),
            "got: ${exception.message}"
        )
        val stored = assertNotNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(CredentialDeploymentStatus.FAILED, stored.deploymentStatus)
        assertTrue(
            assertNotNull(stored.deploymentError).contains("Failed to send transaction"),
            "the recorded reason must identify the send step; got: ${stored.deploymentError}"
        )
    }

    @Test
    fun testCreateWallet_sendTransactionReturnsErrorResult_marksCredentialFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val errorResultXdr = "AAAAAAAAAGT////7AAAAAA=="
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> RpcReply(sendErrorResultJson(errorResultXdr))
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
        assertTrue(
            exception.message.contains(errorResultXdr),
            "the rejected submission must surface the error result XDR; got: ${exception.message}"
        )
        val stored = assertNotNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(CredentialDeploymentStatus.FAILED, stored.deploymentStatus)
        assertTrue(assertNotNull(stored.deploymentError).contains(errorResultXdr))
    }

    @Test
    fun testCreateWallet_submissionWithoutHash_throwsSubmissionFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> RpcReply(sendWithoutHashJson())
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
        assertTrue(
            exception.message.contains("No transaction hash returned from submission"),
            "got: ${exception.message}"
        )
    }

    @Test
    fun testCreateWallet_confirmationReportsFailed_marksCredentialFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val resultXdr = "AAAAAAAAAGT////9AAAAAA=="
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                else -> RpcReply(transactionFailedJson(resultXdr))
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
        assertTrue(
            exception.message.contains(resultXdr),
            "an on-chain failure must surface its result XDR; got: ${exception.message}"
        )
        assertEquals(1, rpc.callCount("getTransaction"), "FAILED ends the poll immediately")
        val stored = assertNotNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(CredentialDeploymentStatus.FAILED, stored.deploymentStatus)
        assertEquals(resultXdr, stored.deploymentError)
    }

    @Test
    fun testCreateWallet_confirmationNeverLands_throwsTimeoutAndMarksCredentialFailed() = runTest {
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                else -> RpcReply(transactionNotFoundJson())
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.Timeout> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
        assertTrue(
            exception.message.contains("Deployment confirmation timed out"),
            "got: ${exception.message}"
        )
        assertEquals(10, rpc.callCount("getTransaction"), "the confirmation loop makes exactly 10 attempts")
        val stored = assertNotNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(CredentialDeploymentStatus.FAILED, stored.deploymentStatus)
    }

    @Test
    fun testCreateWallet_confirmationTransportFailures_retryThenTimeOut() = runTest {
        // A polling round-trip that fails at the transport level is retried rather than aborting
        // the deployment; only exhausting all attempts surfaces the timeout.
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                else -> transportFailure
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        assertFailsWith<TransactionException.Timeout> {
            kit.walletOperations.createWallet(autoSubmit = true)
        }
        assertEquals(
            10,
            rpc.callCount("getTransaction"),
            "each transport failure is retried until the attempt budget is exhausted"
        )
        assertEquals(
            CredentialDeploymentStatus.FAILED,
            assertNotNull(kit.credentialManager.getCredential(credentialIdB64)).deploymentStatus
        )
    }

    // ========================================================================
    // createWallet() Auto-Fund
    // ========================================================================

    @Test
    fun testCreateWallet_autoFund_waitsForContractVisibilityBeforeFunding() = runTest {
        // Funding simulates against the freshly deployed contract, so the deployed instance must be
        // visible to the RPC before funding starts. The native token address here is deliberately
        // malformed: fundWallet rejects it on its own address check before any Friendbot call, so
        // the failure that does surface identifies which step ran first. The RPC never reports the
        // instance, and the call fails on the visibility budget rather than on the token address —
        // pinning that the wait precedes funding.
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                3 -> RpcReply(transactionSuccessJson())
                else -> RpcReply(emptyLedgerEntriesResponseJson())
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<TransactionException.Timeout> {
            kit.walletOperations.createWallet(
                autoSubmit = true,
                autoFund = true,
                nativeTokenContract = "not-a-contract-address"
            )
        }
        assertEquals(
            deployedContractNotVisibleMessage(derivedContractId(deployer)),
            exception.message,
            "the timeout must name the contract the visibility wait was waiting on"
        )
        assertEquals(
            CredentialDeploymentStatus.PENDING,
            assertNotNull(kit.credentialManager.getCredential(credentialIdB64)).deploymentStatus,
            "the deploy itself succeeded, so the credential is neither deleted nor marked FAILED"
        )
    }

    @Test
    fun testCreateWallet_autoFund_contractVisibleToRpc_proceedsToFunding() = runTest {
        // The counterpart of the timeout case: once the RPC reports the deployed contract
        // instance, the wait ends and funding starts. The malformed native token address makes
        // funding fail on its own address check, so the surfacing failure identifies that the
        // flow moved past the visibility wait rather than timing out inside it.
        val deployer = KeyPair.random()
        val provider = registeringProvider()
        val expectedContractId = derivedContractId(deployer)
        val rpc = MockRpc(unconfinedDispatch = true) { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                3 -> RpcReply(transactionSuccessJson())
                else -> RpcReply(contractInstanceEntriesResponseJson(expectedContractId))
            }
        }
        val kit = mockKit(rpc.server, provider, deployer)

        val exception = assertFailsWith<ValidationException.InvalidAddress> {
            kit.walletOperations.createWallet(
                autoSubmit = true,
                autoFund = true,
                nativeTokenContract = "not-a-contract-address"
            )
        }
        assertTrue(
            exception.message.contains("nativeTokenContract"),
            "funding must have started and rejected its own argument; got: ${exception.message}"
        )
        assertEquals(
            1,
            rpc.callCount("getLedgerEntries") - 1,
            "exactly one contract-instance probe follows the deployer account fetch"
        )
        assertEquals(
            CredentialDeploymentStatus.PENDING,
            assertNotNull(kit.credentialManager.getCredential(credentialIdB64)).deploymentStatus,
            "funding failed after a successful deploy, so the credential is left retryable"
        )
    }

    // ========================================================================
    // authenticatePasskey() Pre-Network Validation
    // ========================================================================

    @Test
    fun testAuthenticatePasskey_noWebAuthnProvider_throwsNotSupported() = runTest {
        val kit = createKit()
        val exception = assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.authenticatePasskey()
        }
        assertTrue(
            exception.message!!.contains("No WebAuthnProvider configured"),
            "Exception message should mention missing provider"
        )
    }

    @Test
    fun testAuthenticatePasskey_noWebAuthnProvider_withChallenge() = runTest {
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.authenticatePasskey(challenge = ByteArray(32))
        }
    }

    @Test
    fun testAuthenticatePasskey_noWebAuthnProvider_withCredentialIds() = runTest {
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.authenticatePasskey(
                credentialIds = listOf("cred-1", "cred-2")
            )
        }
    }

    // ========================================================================
    // connectWallet() Default Options (No Session)
    // ========================================================================

    @Test
    fun testConnectWallet_defaultOptions_noSession_returnsNull() = runTest {
        val kit = createKit()
        val result = kit.walletOperations.connectWallet()
        assertNull(result, "connectWallet with default options and no session should return null")
    }

    @Test
    fun testConnectWallet_promptFalse_noSession_returnsNull() = runTest {
        val kit = createKit()
        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(prompt = false)
        )
        assertNull(result)
    }

    @Test
    fun testConnectWallet_freshTrue_noWebAuthnProvider_throwsNotSupported() = runTest {
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(fresh = true)
            )
        }
    }

    @Test
    fun testConnectWallet_promptTrue_noSession_noWebAuthnProvider_throwsNotSupported() = runTest {
        val kit = createKit()
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(prompt = true)
            )
        }
    }

    @Test
    fun testConnectWallet_contractIdWithoutCredentialId_throwsValidation() = runTest {
        val kit = createKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(
                    contractId = validContractAddress
                )
            )
        }
    }

    // ========================================================================
    // connectWallet() Session Restore
    // ========================================================================

    @Test
    fun testConnectWallet_validSession_verifiesContractAndRestoresConnection() = runTest {
        val kit = mockKit(buildConstantResponseMockServer(contractInstanceEntriesResponseJson(validContractAddress)))
        val now = currentTimeMillis()
        kit.getStorage().saveSession(
            StoredSession(
                credentialId = "saved-credential",
                contractId = validContractAddress,
                connectedAt = now,
                expiresAt = now + 3_600_000L
            )
        )
        val connectedEvents = mutableListOf<SmartAccountEvent.WalletConnected>()
        kit.events.on<SmartAccountEvent.WalletConnected> { connectedEvents.add(it) }

        val result = kit.walletOperations.connectWallet()

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals("saved-credential", connected.credentialId)
        assertEquals(validContractAddress, connected.contractId)
        assertTrue(connected.restoredFromSession, "a silent restore must be flagged as session-restored")
        assertTrue(kit.isConnected)
        assertEquals(validContractAddress, kit.contractId)
        assertEquals(1, connectedEvents.size)
        assertEquals(validContractAddress, connectedEvents.single().contractId)
    }

    @Test
    fun testConnectWallet_staleSession_contractNotOnChain_clearsSessionAndReturnsNull() = runTest {
        // The saved contract is no longer on-chain (the deploy never landed). The stale session is
        // dropped and, with prompt = false, the call reports "no session" rather than failing.
        val kit = mockKit(buildConstantResponseMockServer(emptyLedgerEntriesResponseJson()))
        val now = currentTimeMillis()
        kit.getStorage().saveSession(
            StoredSession(
                credentialId = "saved-credential",
                contractId = validContractAddress,
                connectedAt = now,
                expiresAt = now + 3_600_000L
            )
        )

        val result = kit.walletOperations.connectWallet()

        assertNull(result)
        assertNull(kit.getStorage().getSession(), "a session pointing at a missing contract must be cleared")
        assertFalse(kit.isConnected)
    }

    @Test
    fun testConnectWallet_expiredSession_emitsSessionExpiredAndClearsIt() = runTest {
        // The RPC-less server proves the expired session short-circuits before any contract check.
        val storage = RawSessionStorage()
        val kit = mockKit(buildNoRpcMockServer(), storage = storage)
        val now = currentTimeMillis()
        storage.saveSession(
            StoredSession(
                credentialId = "expired-credential",
                contractId = validContractAddress,
                connectedAt = now - 7_200_000L,
                expiresAt = now - 1_000L
            )
        )
        val expiredEvents = mutableListOf<SmartAccountEvent.SessionExpired>()
        kit.events.on<SmartAccountEvent.SessionExpired> { expiredEvents.add(it) }

        val result = kit.walletOperations.connectWallet()

        assertNull(result)
        assertEquals(1, expiredEvents.size, "an expired session must be reported to the application")
        assertEquals("expired-credential", expiredEvents.single().credentialId)
        assertEquals(validContractAddress, expiredEvents.single().contractId)
        assertNull(storage.getSession(), "the expired session must be removed")
        assertFalse(kit.isConnected)
    }

    @Test
    fun testConnectWallet_sessionReadFailure_treatedAsNoSession() = runTest {
        // An unreadable session record must degrade to "no session" rather than failing the
        // connect, so a corrupted store never locks the user out of the login flow.
        val kit = mockKit(buildNoRpcMockServer(), storage = FailingSessionReadStorage())

        assertNull(kit.walletOperations.connectWallet())
        assertFalse(kit.isConnected)
    }

    // ========================================================================
    // connectWallet() WebAuthn Prompt and Resolution Cascade
    // ========================================================================

    @Test
    fun testConnectWallet_promptTrue_credentialReadFailure_fallsThroughToDerivation() = runTest {
        // A failing credential store must not abort the cascade: resolution continues with the
        // deterministic address derived under the configured deployer.
        val deployer = KeyPair.random()
        val provider = authenticatingProvider()
        val kit = mockKit(
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(validContractAddress)),
            provider,
            deployer,
            storage = FailingReadStorage()
        )

        val result = kit.walletOperations.connectWallet(OZWalletOperations.ConnectWalletOptions(prompt = true))

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(derivedContractId(deployer), connected.contractId)
        assertTrue(kit.isConnected)
    }

    @Test
    fun testConnectWallet_promptTrue_authenticationFailure_wrappedAsAuthenticationFailed() = runTest {
        val provider = MockWebAuthnProvider().apply {
            authenticationException = WebAuthnException.cancelled()
        }
        val kit = mockKit(buildNoRpcMockServer(), provider)

        val exception = assertFailsWith<WebAuthnException.AuthenticationFailed> {
            kit.walletOperations.connectWallet(OZWalletOperations.ConnectWalletOptions(prompt = true))
        }
        assertTrue(
            exception.message.contains("User cancelled WebAuthn operation"),
            "the wrapper must carry the provider's failure detail; got: ${exception.message}"
        )
        assertFalse(kit.isConnected)
    }

    @Test
    fun testConnectWallet_promptTrue_storedCredentialFailed_throwsWithRecoveryHint() = runTest {
        val provider = authenticatingProvider()
        val kit = mockKit(buildNoRpcMockServer(), provider)
        kit.getStorage().save(
            StoredCredential(
                credentialId = credentialIdB64,
                publicKey = testPublicKey(),
                contractId = validContractAddress,
                deploymentStatus = CredentialDeploymentStatus.FAILED,
                deploymentError = "insufficient deployer balance"
            )
        )

        val exception = assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet(OZWalletOperations.ConnectWalletOptions(prompt = true))
        }
        assertTrue(exception.message.contains(credentialIdB64), "got: ${exception.message}")
        assertTrue(
            exception.message.contains("deployPendingCredential()"),
            "a FAILED credential must point the caller at the retry entry point; got: ${exception.message}"
        )
        assertFalse(kit.isConnected, "an unresolved credential must not set the connected state")
    }

    @Test
    fun testConnectWallet_promptTrue_storedCredentialPending_verifiesOnChainAndConnects() = runTest {
        val provider = authenticatingProvider()
        val kit = mockKit(
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(otherContractAddress)),
            provider
        )
        kit.getStorage().save(
            StoredCredential(
                credentialId = credentialIdB64,
                publicKey = testPublicKey(),
                contractId = otherContractAddress,
                deploymentStatus = CredentialDeploymentStatus.PENDING
            )
        )

        val result = kit.walletOperations.connectWallet(OZWalletOperations.ConnectWalletOptions(prompt = true))

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(credentialIdB64, connected.credentialId)
        assertEquals(otherContractAddress, connected.contractId, "the stored contract wins the cascade")
        assertFalse(connected.restoredFromSession)
        assertNull(
            kit.credentialManager.getCredential(credentialIdB64),
            "the transitional credential is dropped once the contract is confirmed on-chain"
        )
        val session = assertNotNull(kit.getStorage().getSession())
        assertEquals(otherContractAddress, session.contractId)
        assertEquals(credentialIdB64, session.credentialId)
        assertEquals(1, provider.authenticateCallCount)
        assertNull(
            provider.lastAuthenticateAllowCredentials,
            "the connect ceremony must not filter credentials"
        )
    }

    @Test
    fun testConnectWallet_promptTrue_derivedContractOnChain_connects() = runTest {
        val deployer = KeyPair.random()
        val provider = authenticatingProvider()
        val kit = mockKit(
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(validContractAddress)),
            provider,
            deployer
        )

        val result = kit.walletOperations.connectWallet(OZWalletOperations.ConnectWalletOptions(prompt = true))

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(
            derivedContractId(deployer),
            connected.contractId,
            "with no stored credential the address derived under the configured deployer is used"
        )
        assertEquals(credentialIdB64, connected.credentialId)
        assertTrue(kit.isConnected)
    }

    @Test
    fun testConnectWallet_promptTrue_derivedContractMissing_withoutIndexer_throwsNotFound() = runTest {
        val deployer = KeyPair.random()
        val provider = authenticatingProvider()
        val kit = mockKit(buildConstantResponseMockServer(emptyLedgerEntriesResponseJson()), provider, deployer)

        val exception = assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet(OZWalletOperations.ConnectWalletOptions(prompt = true))
        }
        assertTrue(exception.message.contains(credentialIdB64), "got: ${exception.message}")
        assertTrue(
            exception.message.contains("no indexer is configured"),
            "falling off the end of the cascade must explain the missing discovery source; got: ${exception.message}"
        )
        assertFalse(kit.isConnected)
    }

    // ========================================================================
    // connectWallet() Indexer Discovery (prompt path)
    //
    // The indexer is consulted only when the address derived under the configured deployer holds
    // no contract, which is the shape of a passkey added as a signer to an existing wallet.
    // ========================================================================

    @Test
    fun testConnectWallet_promptTrue_indexerReturnsSingleContract_connects() = runTest {
        val deployer = KeyPair.random()
        val provider = authenticatingProvider()
        // The derived address is empty on-chain, so the cascade falls through to the indexer; the
        // contract it names is then verified on-chain before the connection is established.
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(emptyLedgerEntriesResponseJson())
                else -> RpcReply(contractInstanceEntriesResponseJson(otherContractAddress))
            }
        }
        val indexer = MockIndexer(indexerLookupJson(credentialIdB64, listOf(otherContractAddress)))
        val kit = mockKit(rpc.server, provider, deployer, indexerClient = indexer.client)

        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(prompt = true)
        )

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(
            otherContractAddress,
            connected.contractId,
            "the single indexed contract wins over the derived address that holds no contract"
        )
        assertEquals(credentialIdB64, connected.credentialId)
        assertFalse(connected.restoredFromSession)
        assertTrue(kit.isConnected)
        assertEquals(otherContractAddress, kit.contractId)

        // The indexer API keys lookups by the hex form of the credential ID.
        assertTrue(
            indexer.requestedUrls.single().endsWith("/api/lookup/${Util.bytesToHex(credentialIdBytes)}"),
            "the credential ID must be hex-encoded for the indexer; got: ${indexer.requestedUrls.single()}"
        )

        val session = assertNotNull(kit.getStorage().getSession())
        assertEquals(otherContractAddress, session.contractId)
    }

    @Test
    fun testConnectWallet_promptTrue_indexerReturnsNoContracts_throwsNotFound() = runTest {
        val deployer = KeyPair.random()
        val provider = authenticatingProvider()
        val rpc = MockRpc { _, _ -> RpcReply(emptyLedgerEntriesResponseJson()) }
        val indexer = MockIndexer(indexerLookupJson(credentialIdB64, emptyList()))
        val kit = mockKit(rpc.server, provider, deployer, indexerClient = indexer.client)

        val exception = assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet(OZWalletOperations.ConnectWalletOptions(prompt = true))
        }
        assertTrue(
            exception.message.contains("No contract found for credential $credentialIdB64"),
            "an empty indexer result ends the cascade; got: ${exception.message}"
        )
        assertFalse(kit.isConnected)
    }

    @Test
    fun testConnectWallet_promptTrue_indexerReturnsMultipleContracts_returnsAmbiguous() = runTest {
        // Several wallets list the same passkey as a signer. The SDK does not choose for the
        // user: it reports the candidates and leaves the connection state untouched.
        val deployer = KeyPair.random()
        val provider = authenticatingProvider()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(emptyLedgerEntriesResponseJson())
                else -> unexpected(index, "an ambiguous result must not verify any candidate on-chain")
            }
        }
        val indexer = MockIndexer(
            indexerLookupJson(credentialIdB64, listOf(otherContractAddress, validContractAddress))
        )
        val kit = mockKit(rpc.server, provider, deployer, indexerClient = indexer.client)

        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(prompt = true)
        )

        val ambiguous = assertIs<ConnectWalletResult.Ambiguous>(result)
        assertEquals(credentialIdB64, ambiguous.credentialId)
        assertEquals(listOf(otherContractAddress, validContractAddress), ambiguous.candidates)
        assertFalse(kit.isConnected, "an ambiguous result must not establish a connection")
        assertNull(kit.getStorage().getSession(), "no session is saved for an unresolved connection")
    }

    @Test
    fun testConnectWallet_freshTrue_ignoresValidSessionAndReauthenticates() = runTest {
        // A valid session points at one contract while the cascade resolves a different one, so the
        // resulting contract shows whether the session was consulted.
        val deployer = KeyPair.random()
        val provider = authenticatingProvider()
        val kit = mockKit(
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(validContractAddress)),
            provider,
            deployer
        )
        val now = currentTimeMillis()
        kit.getStorage().saveSession(
            StoredSession(
                credentialId = "saved-credential",
                contractId = otherContractAddress,
                connectedAt = now,
                expiresAt = now + 3_600_000L
            )
        )

        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(fresh = true)
        )

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(1, provider.authenticateCallCount, "fresh = true always runs the passkey ceremony")
        assertEquals(
            derivedContractId(deployer),
            connected.contractId,
            "fresh = true must resolve through the cascade rather than restoring the saved session"
        )
        assertFalse(connected.restoredFromSession)
    }

    // ========================================================================
    // connectWallet() Direct Credential Connect
    // ========================================================================

    @Test
    fun testConnectWallet_credentialIdOnly_storedCredentialFailed_throwsWithRecoveryHint() = runTest {
        val kit = mockKit(buildNoRpcMockServer())
        kit.getStorage().save(
            StoredCredential(
                credentialId = credentialIdB64,
                publicKey = testPublicKey(),
                contractId = validContractAddress,
                deploymentStatus = CredentialDeploymentStatus.FAILED
            )
        )

        val exception = assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
            )
        }
        assertTrue(exception.message.contains("deployPendingCredential()"), "got: ${exception.message}")
        assertFalse(kit.isConnected)
    }

    @Test
    fun testConnectWallet_credentialIdOnly_storedCredentialPending_connects() = runTest {
        val kit = mockKit(buildConstantResponseMockServer(contractInstanceEntriesResponseJson(otherContractAddress)))
        kit.getStorage().save(
            StoredCredential(
                credentialId = credentialIdB64,
                publicKey = testPublicKey(),
                contractId = otherContractAddress,
                deploymentStatus = CredentialDeploymentStatus.PENDING
            )
        )

        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
        )

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(otherContractAddress, connected.contractId)
        assertEquals(credentialIdB64, connected.credentialId)
        assertNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(otherContractAddress, kit.contractId)
    }

    @Test
    fun testConnectWallet_credentialIdOnly_derivedContractOnChain_connects() = runTest {
        val deployer = KeyPair.random()
        val kit = mockKit(
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(validContractAddress)),
            deployer = deployer
        )

        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
        )

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(derivedContractId(deployer), connected.contractId)
    }

    @Test
    fun testConnectWallet_credentialIdOnly_credentialReadFailure_fallsThroughToDerivation() = runTest {
        val deployer = KeyPair.random()
        val kit = mockKit(
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(validContractAddress)),
            deployer = deployer,
            storage = FailingReadStorage()
        )

        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
        )

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(
            derivedContractId(deployer),
            connected.contractId,
            "an unreadable credential store must not stop the direct-connect cascade"
        )
    }

    @Test
    fun testConnectWallet_credentialIdOnly_malformedBase64Url_throwsValidation() = runTest {
        // Derivation needs the raw credential bytes; an undecodable credential ID is a caller
        // error, reported as such rather than as "wallet not found".
        val kit = mockKit(buildNoRpcMockServer())

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(credentialId = "not base64url!!")
            )
        }
        assertTrue(
            exception.message.contains("Base64URL"),
            "the failure must name the malformed encoding; got: ${exception.message}"
        )
    }

    @Test
    fun testConnectWallet_credentialIdOnly_derivedContractMissing_withoutIndexer_throwsNotFound() = runTest {
        val deployer = KeyPair.random()
        val kit = mockKit(buildConstantResponseMockServer(emptyLedgerEntriesResponseJson()), deployer = deployer)

        val exception = assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
            )
        }
        assertTrue(exception.message.contains("no indexer is configured"), "got: ${exception.message}")
    }

    @Test
    fun testConnectWallet_credentialIdOnly_indexerReturnsSingleContract_connects() = runTest {
        val deployer = KeyPair.random()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(emptyLedgerEntriesResponseJson())
                else -> RpcReply(contractInstanceEntriesResponseJson(otherContractAddress))
            }
        }
        val indexer = MockIndexer(indexerLookupJson(credentialIdB64, listOf(otherContractAddress)))
        val kit = mockKit(rpc.server, deployer = deployer, indexerClient = indexer.client)

        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
        )

        val connected = assertIs<ConnectWalletResult.Connected>(result)
        assertEquals(otherContractAddress, connected.contractId)
        assertEquals(credentialIdB64, connected.credentialId)
        assertEquals(otherContractAddress, kit.contractId)
        assertEquals(
            1,
            indexer.requestedUrls.size,
            "the direct-connect cascade consults the indexer exactly once"
        )
    }

    @Test
    fun testConnectWallet_credentialIdOnly_indexerReturnsNoContracts_throwsNotFound() = runTest {
        val deployer = KeyPair.random()
        val rpc = MockRpc { _, _ -> RpcReply(emptyLedgerEntriesResponseJson()) }
        val indexer = MockIndexer(indexerLookupJson(credentialIdB64, emptyList()))
        val kit = mockKit(rpc.server, deployer = deployer, indexerClient = indexer.client)

        val exception = assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
            )
        }
        assertTrue(
            exception.message.contains("No contract found for credential $credentialIdB64"),
            "got: ${exception.message}"
        )
        assertFalse(kit.isConnected)
    }

    @Test
    fun testConnectWallet_credentialIdOnly_indexerReturnsMultipleContracts_returnsAmbiguous() = runTest {
        val deployer = KeyPair.random()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(emptyLedgerEntriesResponseJson())
                else -> unexpected(index, "an ambiguous result must not verify any candidate on-chain")
            }
        }
        val indexer = MockIndexer(
            indexerLookupJson(credentialIdB64, listOf(validContractAddress, otherContractAddress))
        )
        val kit = mockKit(rpc.server, deployer = deployer, indexerClient = indexer.client)
        kit.getStorage().save(
            StoredCredential(
                credentialId = "another-credential",
                publicKey = testPublicKey(),
                contractId = validContractAddress
            )
        )

        val result = kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
        )

        val ambiguous = assertIs<ConnectWalletResult.Ambiguous>(result)
        assertEquals(credentialIdB64, ambiguous.credentialId)
        assertEquals(listOf(validContractAddress, otherContractAddress), ambiguous.candidates)
        assertFalse(kit.isConnected)
        // The deletion the Ambiguous arm must skip targets the looked-up credential, so an
        // unrelated key proves nothing; the observable effect is that no session is written.
        assertNull(
            kit.getStorage().getSession(),
            "an ambiguous result must not save a session"
        )
        assertNotNull(
            kit.getStorage().get("another-credential"),
            "an ambiguous result must not touch stored credentials"
        )
    }

    @Test
    fun testConnectWallet_credentialIdOnly_indexerRequestFails_propagatesIndexerException() = runTest {
        // An indexer that is unreachable makes the lookup inconclusive, which is reported as an
        // indexer failure rather than laundered into "no wallet found".
        val deployer = KeyPair.random()
        val rpc = MockRpc { _, _ -> RpcReply(emptyLedgerEntriesResponseJson()) }
        val indexer = MockIndexer("""{"error":"upstream unavailable"}""", HttpStatusCode.BadGateway)
        val kit = mockKit(rpc.server, deployer = deployer, indexerClient = indexer.client)

        val exception = assertFailsWith<IndexerException.RequestFailed> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(credentialId = credentialIdB64)
            )
        }
        assertTrue(exception.message.contains("502"), "got: ${exception.message}")
        assertFalse(kit.isConnected)
    }

    @Test
    fun testConnectWallet_explicitContractId_notOnChain_throwsNotFound() = runTest {
        // An explicit contractId bypasses the cascade, so the end-of-flow on-chain check is the
        // only thing standing between the caller and a connection to a non-existent contract.
        val kit = mockKit(buildConstantResponseMockServer(emptyLedgerEntriesResponseJson()))

        val exception = assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(
                    credentialId = credentialIdB64,
                    contractId = otherContractAddress
                )
            )
        }
        assertTrue(exception.message.contains(otherContractAddress), "got: ${exception.message}")
        assertFalse(kit.isConnected)
    }

    // ========================================================================
    // authenticatePasskey() Ceremony
    // ========================================================================

    @Test
    fun testAuthenticatePasskey_defaultChallenge_normalizesSignatureAndLeavesKitUnconnected() = runTest {
        val provider = authenticatingProvider()
        val kit = mockKit(buildNoRpcMockServer(), provider)

        val result = kit.walletOperations.authenticatePasskey()

        assertEquals(32, assertNotNull(provider.lastAuthenticateChallenge).size, "a random 32-byte challenge is generated")
        assertNull(provider.lastAuthenticateAllowCredentials, "no credential filter without credentialIds")
        assertEquals(credentialIdB64, result.credentialId)
        assertContentEquals(
            hexBytes("0102030405060708091011121314151617181920212223242526272829303132") +
                hexBytes("0000000000000000000000000000000000000000000000000000000000000005"),
            result.signature.signature,
            "an already low-S DER signature is re-encoded into the 64-byte compact form"
        )
        assertContentEquals(ByteArray(37) { it.toByte() }, result.signature.authenticatorData)
        assertTrue(result.publicKey.isEmpty(), "an unknown credential yields the empty public-key fallback")
        assertFalse(kit.isConnected, "standalone authentication must not change the connection state")
    }

    @Test
    fun testAuthenticatePasskey_explicitChallenge_isForwardedToProvider() = runTest {
        val provider = authenticatingProvider()
        val kit = mockKit(buildNoRpcMockServer(), provider)
        val challenge = ByteArray(32) { 0x11 }

        kit.walletOperations.authenticatePasskey(challenge = challenge)

        assertContentEquals(challenge, provider.lastAuthenticateChallenge)
    }

    @Test
    fun testAuthenticatePasskey_highSSignature_isNormalizedToLowS() = runTest {
        val provider = authenticatingProvider(signature = highSDerSignature())
        val kit = mockKit(buildNoRpcMockServer(), provider)

        val result = kit.walletOperations.authenticatePasskey()

        assertContentEquals(
            ByteArray(31) + byteArrayOf(0x01) + ByteArray(31) + byteArrayOf(0x05),
            result.signature.signature,
            "s = n - 5 is above the curve half-order and must be normalized to 5"
        )
    }

    @Test
    fun testAuthenticatePasskey_credentialIds_carryStoredTransportHints() = runTest {
        val knownId = Util.base64urlEncode(ByteArray(8) { (it + 3).toByte() })
        val unknownId = Util.base64urlEncode(ByteArray(8) { (it + 9).toByte() })
        val provider = authenticatingProvider()
        val kit = mockKit(buildNoRpcMockServer(), provider)
        kit.getStorage().save(
            StoredCredential(
                credentialId = knownId,
                publicKey = testPublicKey(),
                contractId = validContractAddress,
                transports = listOf("usb", "nfc")
            )
        )

        kit.walletOperations.authenticatePasskey(credentialIds = listOf(knownId, unknownId))

        val allowCredentials = assertNotNull(provider.lastAuthenticateAllowCredentials)
        assertEquals(2, allowCredentials.size)
        assertContentEquals(Util.base64urlDecode(knownId), allowCredentials[0].id)
        assertEquals(
            listOf("usb", "nfc"),
            allowCredentials[0].transports,
            "stored transports let the platform route to the right authenticator"
        )
        assertContentEquals(Util.base64urlDecode(unknownId), allowCredentials[1].id)
        assertNull(
            allowCredentials[1].transports,
            "a credential absent from local storage falls back to the platform defaults"
        )
    }

    @Test
    fun testAuthenticatePasskey_storedCredential_returnsItsPublicKey() = runTest {
        val provider = authenticatingProvider()
        val kit = mockKit(buildNoRpcMockServer(), provider)
        val storedKey = testPublicKey(fill = 0x5A)
        kit.getStorage().save(
            StoredCredential(
                credentialId = credentialIdB64,
                publicKey = storedKey,
                contractId = validContractAddress
            )
        )

        val result = kit.walletOperations.authenticatePasskey()

        assertContentEquals(storedKey, result.publicKey)
    }

    @Test
    fun testAuthenticatePasskey_authenticationFailure_wrappedAsAuthenticationFailed() = runTest {
        val provider = MockWebAuthnProvider().apply {
            authenticationException = WebAuthnException.cancelled()
        }
        val kit = mockKit(buildNoRpcMockServer(), provider)

        val exception = assertFailsWith<WebAuthnException.AuthenticationFailed> {
            kit.walletOperations.authenticatePasskey()
        }
        assertTrue(
            exception.message.contains("User cancelled WebAuthn operation"),
            "got: ${exception.message}"
        )
    }

    @Test
    fun testAuthenticatePasskey_nonDerSignature_throwsValidation() = runTest {
        // WebAuthn ES256 assertions are DER-encoded; anything else cannot be normalized.
        val provider = authenticatingProvider(signature = ByteArray(64) { it.toByte() })
        val kit = mockKit(buildNoRpcMockServer(), provider)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.authenticatePasskey()
        }
        assertTrue(
            exception.message.contains("DER signature format"),
            "the failure must identify the malformed signature encoding; got: ${exception.message}"
        )
    }

    // ========================================================================
    // ConnectWalletOptions Data Class
    // ========================================================================

    @Test
    fun testConnectWalletOptions_defaultValues() {
        val options = OZWalletOperations.ConnectWalletOptions()
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withPrompt() {
        val options = OZWalletOperations.ConnectWalletOptions(prompt = true)
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
        assertTrue(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withFresh() {
        val options = OZWalletOperations.ConnectWalletOptions(fresh = true)
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertTrue(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withCredentialIdOnly() {
        val options = OZWalletOperations.ConnectWalletOptions(credentialId = "test-credential")
        assertEquals("test-credential", options.credentialId)
        assertNull(options.contractId, "A credential id alone leaves the contract id unset")
        assertFalse(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withCredentialIdAndContractId() {
        val options = OZWalletOperations.ConnectWalletOptions(
            credentialId = "cred-abc",
            contractId = validContractAddress
        )
        assertEquals("cred-abc", options.credentialId)
        assertEquals(validContractAddress, options.contractId)
        assertFalse(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withAllFields() {
        val options = OZWalletOperations.ConnectWalletOptions(
            credentialId = "cred-abc",
            contractId = validContractAddress,
            fresh = true,
            prompt = true
        )
        assertEquals("cred-abc", options.credentialId)
        assertEquals(validContractAddress, options.contractId)
        assertTrue(options.fresh)
        assertTrue(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_equality() {
        val a = OZWalletOperations.ConnectWalletOptions(credentialId = "x", prompt = true)
        val b = OZWalletOperations.ConnectWalletOptions(credentialId = "x", prompt = true)
        val c = OZWalletOperations.ConnectWalletOptions(credentialId = "y", prompt = true)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun testConnectWalletOptions_copy() {
        val original = OZWalletOperations.ConnectWalletOptions(fresh = true)
        val copied = original.copy(prompt = true)
        assertTrue(copied.fresh)
        assertTrue(copied.prompt)
        assertNull(copied.credentialId)
    }

    // ========================================================================
    // CreateWalletResult Data Class
    // ========================================================================

    @Test
    fun testCreateWalletResult_construction_defaultOptionalFields() {
        val pk = testPublicKey()
        val result = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = pk,
            signedTransactionXdr = "AAAA..."
        )
        assertEquals("cred-1", result.credentialId)
        assertEquals(validContractAddress, result.contractId)
        assertTrue(pk.contentEquals(result.publicKey))
        assertEquals("AAAA...", result.signedTransactionXdr)
        assertNull(result.transactionHash)
        assertNull(result.nickname)
    }

    @Test
    fun testCreateWalletResult_constructionWithAllFields() {
        val pk = testPublicKey()
        val result = CreateWalletResult(
            credentialId = "cred-2",
            contractId = validContractAddress,
            publicKey = pk,
            signedTransactionXdr = "BBBB...",
            transactionHash = "hash-abc",
            nickname = "Alice"
        )
        assertEquals("cred-2", result.credentialId)
        assertEquals("hash-abc", result.transactionHash)
        assertEquals("Alice", result.nickname)
    }

    @Test
    fun testCreateWalletResult_equality_sameData() {
        val pk = testPublicKey()
        val a = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = pk.copyOf(),
            signedTransactionXdr = "XDR"
        )
        val b = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = pk.copyOf(),
            signedTransactionXdr = "XDR"
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testCreateWalletResult_equality_differentPublicKey() {
        val a = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = testPublicKey(fill = 0x01),
            signedTransactionXdr = "XDR"
        )
        val b = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = testPublicKey(fill = 0x02),
            signedTransactionXdr = "XDR"
        )
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_equality_differentContractId() {
        val pk = testPublicKey()
        val a = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR")
        val b = CreateWalletResult("c", otherContractAddress, pk.copyOf(), "XDR")
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_equality_differentSignedTransactionXdr() {
        val pk = testPublicKey()
        val a = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR-1")
        val b = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR-2")
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_hashCode_includesOptionalFields() {
        val pk = testPublicKey()
        val withoutOptionals = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR")
        val withHash = withoutOptionals.copy(transactionHash = "hash-1")
        val withHashAndNickname = withoutOptionals.copy(transactionHash = "hash-1", nickname = "Alice")

        assertEquals(
            withHashAndNickname.hashCode(),
            CreateWalletResult(
                credentialId = "c",
                contractId = validContractAddress,
                publicKey = pk.copyOf(),
                signedTransactionXdr = "XDR",
                transactionHash = "hash-1",
                nickname = "Alice"
            ).hashCode(),
            "equal results must hash equally with the optional fields populated"
        )
        assertNotEquals(
            withoutOptionals.hashCode(),
            withHash.hashCode(),
            "transactionHash must contribute to the hash"
        )
        assertNotEquals(
            withHash.hashCode(),
            withHashAndNickname.hashCode(),
            "nickname must contribute to the hash"
        )
    }

    @Test
    fun testCreateWalletResult_equality_differentCredentialId() {
        val pk = testPublicKey()
        val a = CreateWalletResult("a", validContractAddress, pk.copyOf(), "XDR")
        val b = CreateWalletResult("b", validContractAddress, pk.copyOf(), "XDR")
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_equality_differentTransactionHash() {
        val pk = testPublicKey()
        val a = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR", transactionHash = "h1")
        val b = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR", transactionHash = "h2")
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_equality_differentNickname() {
        val pk = testPublicKey()
        val a = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR", nickname = "Alice")
        val b = CreateWalletResult("c", validContractAddress, pk.copyOf(), "XDR", nickname = "Bob")
        assertNotEquals(a, b)
    }

    @Test
    fun testCreateWalletResult_copy() {
        val pk = testPublicKey()
        val original = CreateWalletResult(
            credentialId = "cred-1",
            contractId = validContractAddress,
            publicKey = pk,
            signedTransactionXdr = "XDR"
        )
        val copied = original.copy(transactionHash = "new-hash", nickname = "Bob")
        assertEquals("new-hash", copied.transactionHash)
        assertEquals("Bob", copied.nickname)
        assertEquals("cred-1", copied.credentialId)
    }

    @Test
    fun testCreateWalletResult_equality_notEqualToNull() {
        val result = CreateWalletResult("c", validContractAddress, testPublicKey(), "XDR")
        assertFalse(result.equals(null))
    }

    @Test
    fun testCreateWalletResult_equality_notEqualToOtherType() {
        val result = CreateWalletResult("c", validContractAddress, testPublicKey(), "XDR")
        assertFalse(result.equals("not a result"))
    }

    @Test
    fun testCreateWalletResult_equality_sameInstance() {
        val result = CreateWalletResult("c", validContractAddress, testPublicKey(), "XDR")
        assertTrue(result.equals(result))
    }

    // ========================================================================
    // ConnectWalletResult Sealed Type
    // ========================================================================

    @Test
    fun testConnectWalletResult_connected_construction() {
        val result = ConnectWalletResult.Connected(
            credentialId = "cred-abc",
            contractId = validContractAddress,
            restoredFromSession = false
        )
        assertEquals("cred-abc", result.credentialId)
        assertEquals(validContractAddress, result.contractId)
        assertFalse(result.restoredFromSession)
    }

    @Test
    fun testConnectWalletResult_connected_restoredFromSession() {
        val result = ConnectWalletResult.Connected(
            credentialId = "cred-abc",
            contractId = validContractAddress,
            restoredFromSession = true
        )
        assertTrue(result.restoredFromSession)
    }

    @Test
    fun testConnectWalletResult_connected_equality() {
        val a = ConnectWalletResult.Connected("c", validContractAddress, false)
        val b = ConnectWalletResult.Connected("c", validContractAddress, false)
        val c = ConnectWalletResult.Connected("c", validContractAddress, true)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun testConnectWalletResult_connected_copy() {
        val original = ConnectWalletResult.Connected("c", validContractAddress, false)
        val copied = original.copy(restoredFromSession = true)
        assertTrue(copied.restoredFromSession)
        assertEquals("c", copied.credentialId)
    }

    @Test
    fun testConnectWalletResult_ambiguous_construction() {
        val candidates = listOf(otherContractAddress, validContractAddress)
        val result = ConnectWalletResult.Ambiguous(
            credentialId = "cred-abc",
            candidates = candidates
        )
        assertEquals("cred-abc", result.credentialId)
        assertEquals(candidates, result.candidates)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun testConnectWalletResult_sealed_when_exhaustive() {
        // Compile-time exhaustiveness check: when on the sealed type must
        // cover both arms. If a future change adds a new arm, this test
        // fails to compile until the arm is handled here, surfacing the
        // need to update every consumer.
        val results: List<ConnectWalletResult> = listOf(
            ConnectWalletResult.Connected("c", validContractAddress, false),
            ConnectWalletResult.Ambiguous("c", listOf(validContractAddress))
        )
        for (r in results) {
            val handled: String = when (r) {
                is ConnectWalletResult.Connected -> "connected:${r.contractId}"
                is ConnectWalletResult.Ambiguous -> "ambiguous:${r.candidates.size}"
            }
            assertTrue(handled.isNotEmpty())
        }
    }

    // ========================================================================
    // DeployPendingResult Data Class
    // ========================================================================

    @Test
    fun testDeployPendingResult_construction_defaultOptionalFields() {
        val result = DeployPendingResult(
            contractId = validContractAddress,
            signedTransactionXdr = "signed-xdr"
        )
        assertEquals(validContractAddress, result.contractId)
        assertEquals("signed-xdr", result.signedTransactionXdr)
        assertNull(result.transactionHash)
    }

    @Test
    fun testDeployPendingResult_withTransactionHash() {
        val result = DeployPendingResult(
            contractId = validContractAddress,
            signedTransactionXdr = "signed-xdr",
            transactionHash = "hash-123"
        )
        assertEquals("hash-123", result.transactionHash)
    }

    @Test
    fun testDeployPendingResult_equality() {
        val a = DeployPendingResult(validContractAddress, "xdr-1")
        val b = DeployPendingResult(validContractAddress, "xdr-1")
        val c = DeployPendingResult(validContractAddress, "xdr-2")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun testDeployPendingResult_copy() {
        val original = DeployPendingResult(validContractAddress, "xdr")
        val copied = original.copy(transactionHash = "h")
        assertEquals("h", copied.transactionHash)
        assertEquals("xdr", copied.signedTransactionXdr)
    }

    // ========================================================================
    // AuthenticatePasskeyResult Data Class
    // ========================================================================

    @Test
    fun testAuthenticatePasskeyResult_equality_sameData() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37) { 0x01 },
            clientData = ByteArray(10) { 0x02 },
            signature = ByteArray(64) { 0x03 }
        )
        val pk = testPublicKey()
        val a = AuthenticatePasskeyResult("cred", sig, pk.copyOf())
        val b = AuthenticatePasskeyResult("cred", sig, pk.copyOf())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_differentPublicKey() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37) { 0x01 },
            clientData = ByteArray(10) { 0x02 },
            signature = ByteArray(64) { 0x03 }
        )
        val a = AuthenticatePasskeyResult("cred", sig, testPublicKey(fill = 0x01))
        val b = AuthenticatePasskeyResult("cred", sig, testPublicKey(fill = 0x02))
        assertNotEquals(a, b)
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_differentSignature() {
        val pk = testPublicKey()
        val a = AuthenticatePasskeyResult(
            "cred",
            WebAuthnSignature(
                authenticatorData = ByteArray(37) { 0x01 },
                clientData = ByteArray(10) { 0x02 },
                signature = ByteArray(64) { 0x03 }
            ),
            pk.copyOf()
        )
        val b = AuthenticatePasskeyResult(
            "cred",
            WebAuthnSignature(
                authenticatorData = ByteArray(37) { 0x01 },
                clientData = ByteArray(10) { 0x02 },
                signature = ByteArray(64) { 0x04 }
            ),
            pk.copyOf()
        )
        assertNotEquals(a, b, "results differing only in the signature bytes must not compare equal")
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_differentCredentialId() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37) { 0x01 },
            clientData = ByteArray(10) { 0x02 },
            signature = ByteArray(64) { 0x03 }
        )
        val pk = testPublicKey()
        val a = AuthenticatePasskeyResult("cred-1", sig, pk.copyOf())
        val b = AuthenticatePasskeyResult("cred-2", sig, pk.copyOf())
        assertNotEquals(a, b)
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_notEqualToOtherType() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37),
            clientData = ByteArray(10),
            signature = ByteArray(64)
        )
        val result = AuthenticatePasskeyResult("cred", sig, testPublicKey())
        assertFalse(result.equals("not a result"))
    }

    @Test
    fun testAuthenticatePasskeyResult_equality_notEqualToNull() {
        val sig = WebAuthnSignature(
            authenticatorData = ByteArray(37),
            clientData = ByteArray(10),
            signature = ByteArray(64)
        )
        val result = AuthenticatePasskeyResult("cred", sig, testPublicKey())
        assertFalse(result.equals(null))
    }

    @Test
    fun testAuthenticatePasskeyResult_fieldAccess() {
        val authData = ByteArray(37) { 0x0A }
        val clientData = ByteArray(10) { 0x0B }
        val sigBytes = ByteArray(64) { 0x0C }
        val sig = WebAuthnSignature(
            authenticatorData = authData,
            clientData = clientData,
            signature = sigBytes
        )
        val pk = testPublicKey(fill = 0x77)
        val result = AuthenticatePasskeyResult("my-cred", sig, pk)
        assertEquals("my-cred", result.credentialId)
        assertEquals(sig, result.signature)
        assertTrue(pk.contentEquals(result.publicKey))
    }

    // ========================================================================
    // Kit Connection State: isConnected / credentialId / contractId
    // ========================================================================

    @Test
    fun testKit_initialState_notConnected() {
        val kit = createKit()
        assertFalse(kit.isConnected)
        assertNull(kit.credentialId)
        assertNull(kit.contractId)
    }

    @Test
    fun testKit_afterSetConnectedState() = runTest {
        val kit = createKit()
        kit.setConnectedState("my-credential", validContractAddress)
        assertTrue(kit.isConnected)
        assertEquals("my-credential", kit.credentialId)
        assertEquals(validContractAddress, kit.contractId)
    }

    @Test
    fun testKit_setConnectedState_overwritesPrevious() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-1", validContractAddress)
        assertTrue(kit.isConnected)
        assertEquals("cred-1", kit.credentialId)

        val otherContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        kit.setConnectedState("cred-2", otherContract)
        assertTrue(kit.isConnected)
        assertEquals("cred-2", kit.credentialId)
        assertEquals(otherContract, kit.contractId)
    }

    // ========================================================================
    // disconnect()
    // ========================================================================

    @Test
    fun testDisconnect_afterConnectedState_clearsState() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-x", validContractAddress)
        assertTrue(kit.isConnected)

        kit.disconnect()
        assertFalse(kit.isConnected)
        assertNull(kit.credentialId)
        assertNull(kit.contractId)
    }

    @Test
    fun testDisconnect_whenNotConnected_doesNotThrow() = runTest {
        val kit = createKit()
        assertFalse(kit.isConnected)
        kit.disconnect()
        assertFalse(kit.isConnected)
    }

    @Test
    fun testDisconnect_doubleDisconnect_doesNotThrow() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-x", validContractAddress)
        kit.disconnect()
        kit.disconnect()
        assertFalse(kit.isConnected)
    }

    @Test
    fun testDisconnect_emitsEvent_whenConnected() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-x", validContractAddress)

        var emittedEvent: SmartAccountEvent.WalletDisconnected? = null
        kit.events.on<SmartAccountEvent.WalletDisconnected> { event ->
            emittedEvent = event
        }

        kit.disconnect()
        assertNotNull(emittedEvent)
        assertEquals(validContractAddress, emittedEvent!!.contractId)
    }

    @Test
    fun testDisconnect_doesNotEmitEvent_whenNotConnected() = runTest {
        val kit = createKit()
        var emittedEvent: SmartAccountEvent.WalletDisconnected? = null
        kit.events.on<SmartAccountEvent.WalletDisconnected> { event ->
            emittedEvent = event
        }

        kit.disconnect()
        assertNull(emittedEvent, "Should not emit WalletDisconnected when not connected")
    }

    @Test
    fun testDisconnect_clearsSession() = runTest {
        val kit = createKit()
        // setConnectedState only mutates in-memory state, so the session has to be written
        // explicitly — otherwise there is nothing for disconnect() to clear and the test
        // would pass even if it never touched storage.
        val now = currentTimeMillis()
        kit.getStorage().saveSession(
            StoredSession(
                credentialId = "cred-x",
                contractId = validContractAddress,
                connectedAt = now,
                expiresAt = now + 3_600_000L
            )
        )
        kit.setConnectedState("cred-x", validContractAddress)

        kit.disconnect()

        assertNull(kit.getStorage().getSession(), "disconnect must remove the stored session")
        // With the session gone, a silent reconnect has nothing to restore.
        assertNull(kit.walletOperations.connectWallet())
    }

    // ========================================================================
    // requireConnected()
    // ========================================================================

    @Test
    fun testRequireConnected_whenNotConnected_throwsNotConnected() = runTest {
        val kit = createKit()
        val exception = assertFailsWith<WalletException.NotConnected> {
            kit.requireConnected()
        }
        assertTrue(exception.message!!.contains("No wallet connected"))
    }

    @Test
    fun testRequireConnected_whenConnected_returnsPair() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-abc", validContractAddress)
        val (credId, ctId) = kit.requireConnected()
        assertEquals("cred-abc", credId)
        assertEquals(validContractAddress, ctId)
    }

    @Test
    fun testRequireConnected_afterDisconnect_throwsNotConnected() = runTest {
        val kit = createKit()
        kit.setConnectedState("cred-abc", validContractAddress)
        kit.disconnect()
        assertFailsWith<WalletException.NotConnected> {
            kit.requireConnected()
        }
    }

    // ========================================================================
    // deployPendingCredential() Validation
    // ========================================================================

    @Test
    fun testDeployPendingCredential_autoFundWithoutToken_throwsValidation() = runTest {
        val kit = createKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "cred-abc",
                autoFund = true,
                nativeTokenContract = null
            )
        }
    }

    @Test
    fun testDeployPendingCredential_credentialNotFound_throwsCredentialException() = runTest {
        val kit = createKit()
        assertFailsWith<CredentialException.NotFound> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "nonexistent-cred",
                autoSubmit = false
            )
        }
    }

    @Test
    fun testDeployPendingCredential_credentialMissingPublicKey_throwsInvalid() = runTest {
        val kit = createKit()
        // Bypass createPendingCredential validation by saving directly to storage
        kit.getStorage().save(
            StoredCredential(
                credentialId = "cred-empty-pk",
                publicKey = ByteArray(0),
                contractId = validContractAddress
            )
        )
        assertFailsWith<CredentialException.Invalid> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "cred-empty-pk",
                autoSubmit = false
            )
        }
    }

    @Test
    fun testDeployPendingCredential_credentialMissingContractId_throwsInvalid() = runTest {
        val kit = createKit()
        // Bypass createPendingCredential validation by saving directly to storage
        kit.getStorage().save(
            StoredCredential(
                credentialId = "cred-no-contract",
                publicKey = testPublicKey(),
                contractId = null
            )
        )
        assertFailsWith<CredentialException.Invalid> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "cred-no-contract",
                autoSubmit = false
            )
        }
    }

    @Test
    fun testDeployPendingCredential_credentialEmptyContractId_throwsInvalid() = runTest {
        val kit = createKit()
        // Bypass createPendingCredential validation by saving directly to storage
        kit.getStorage().save(
            StoredCredential(
                credentialId = "cred-empty-contract",
                publicKey = testPublicKey(),
                contractId = ""
            )
        )
        assertFailsWith<CredentialException.Invalid> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "cred-empty-contract",
                autoSubmit = false
            )
        }
    }

    @Test
    fun testDeployPendingCredential_autoFundValidation_beforeCredentialLookup() = runTest {
        // autoFund validation happens before credential lookup
        val kit = createKit()
        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "any-cred",
                autoFund = true,
                nativeTokenContract = null
            )
        }
    }

    @Test
    fun testDeployPendingCredential_malformedBase64UrlCredentialId_throwsInvalid() = runTest {
        // The deploy salt is derived from the raw credential bytes, so a credential ID that is not
        // decodable Base64URL cannot produce a deploy transaction.
        val kit = mockKit(buildNoRpcMockServer())
        kit.getStorage().save(
            StoredCredential(
                credentialId = "not base64url!!",
                publicKey = testPublicKey(),
                contractId = validContractAddress
            )
        )

        val exception = assertFailsWith<CredentialException.Invalid> {
            kit.walletOperations.deployPendingCredential(credentialId = "not base64url!!")
        }
        assertTrue(
            exception.message.contains("Base64URL"),
            "the failure must name the malformed encoding; got: ${exception.message}"
        )
    }

    // ========================================================================
    // deployPendingCredential() Deploy Flow
    // ========================================================================

    /** Seeds storage with a deployable pending credential and returns the derived contract address. */
    private suspend fun seedPendingCredential(kit: OZSmartAccountKit, deployer: KeyPair): String {
        val contractId = derivedContractId(deployer)
        kit.getStorage().save(
            StoredCredential(
                credentialId = credentialIdB64,
                publicKey = registrationPublicKey(),
                contractId = contractId,
                deploymentStatus = CredentialDeploymentStatus.PENDING
            )
        )
        return contractId
    }

    @Test
    fun testDeployPendingCredential_autoSubmitFalse_returnsSignedXdrWithoutSubmitting() = runTest {
        val deployer = KeyPair.random()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                else -> unexpected(index, "autoSubmit = false must stop after simulation")
            }
        }
        val kit = mockKit(rpc.server, deployer = deployer)
        val contractId = seedPendingCredential(kit, deployer)

        val result = kit.walletOperations.deployPendingCredential(
            credentialId = credentialIdB64,
            autoSubmit = false
        )

        assertEquals(contractId, result.contractId)
        assertNull(result.transactionHash)
        val signed = Transaction.fromEnvelopeXdr(result.signedTransactionXdr, Network.TESTNET)
        assertEquals(AbstractTransaction.MIN_BASE_FEE + simulatedMinResourceFee, signed.fee)
        assertEquals(deployer.getAccountId(), signed.sourceAccount)
        assertEquals(0, rpc.callCount("sendTransaction"))

        // The retry entry point also establishes the connection, so the kit is usable immediately.
        assertTrue(kit.isConnected)
        assertEquals(contractId, kit.contractId)
        assertEquals(credentialIdB64, kit.credentialId)
        assertEquals(contractId, assertNotNull(kit.getStorage().getSession()).contractId)
        assertNotNull(
            kit.credentialManager.getCredential(credentialIdB64),
            "an unsubmitted deploy leaves the credential in storage for a later retry"
        )
    }

    @Test
    fun testDeployPendingCredential_autoSubmitTrue_submitsAndDropsCredential() = runTest {
        val deployer = KeyPair.random()
        val contractInstance = contractInstanceEntriesResponseJson(derivedContractId(deployer))
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                3 -> RpcReply(transactionSuccessJson())
                else -> RpcReply(contractInstance)
            }
        }
        val kit = mockKit(rpc.server, deployer = deployer)
        val contractId = seedPendingCredential(kit, deployer)

        val result = kit.walletOperations.deployPendingCredential(credentialId = credentialIdB64)

        assertEquals(contractId, result.contractId)
        assertEquals(deployTxHash, result.transactionHash)
        assertTrue(result.signedTransactionXdr.isNotEmpty())
        assertNull(
            kit.credentialManager.getCredential(credentialIdB64),
            "a confirmed retry removes the transitional credential"
        )
    }

    @Test
    fun testDeployPendingCredential_buildFailure_marksCredentialFailed() = runTest {
        val deployer = KeyPair.random()
        val kit = mockKit(buildNoRpcMockServer(), deployer = deployer)
        seedPendingCredential(kit, deployer)

        val exception = assertFailsWith<TransactionException.SubmissionFailed> {
            kit.walletOperations.deployPendingCredential(credentialId = credentialIdB64)
        }
        assertTrue(
            exception.message.contains("Failed to fetch deployer account"),
            "got: ${exception.message}"
        )
        val stored = assertNotNull(kit.credentialManager.getCredential(credentialIdB64))
        assertEquals(CredentialDeploymentStatus.FAILED, stored.deploymentStatus)
        assertNotNull(stored.deploymentError)
    }

    @Test
    fun testDeployPendingCredential_autoFund_waitsForContractVisibilityBeforeFunding() = runTest {
        // As in createWallet, funding waits for the deployed instance to reach the RPC. The token
        // address is deliberately malformed so funding can never start; the visibility timeout is
        // what surfaces, pinning the ordering of the two steps.
        val deployer = KeyPair.random()
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                3 -> RpcReply(transactionSuccessJson())
                else -> RpcReply(emptyLedgerEntriesResponseJson())
            }
        }
        val kit = mockKit(rpc.server, deployer = deployer)
        val contractId = seedPendingCredential(kit, deployer)

        val exception = assertFailsWith<TransactionException.Timeout> {
            kit.walletOperations.deployPendingCredential(
                credentialId = credentialIdB64,
                autoFund = true,
                nativeTokenContract = "not-a-contract-address"
            )
        }
        assertEquals(deployedContractNotVisibleMessage(contractId), exception.message)
    }

    @Test
    fun testDeployPendingCredential_autoFund_contractVisibleToRpc_proceedsToFunding() = runTest {
        // Once the RPC reports the instance the wait ends and funding starts; the malformed token
        // address makes funding reject its own argument, which is what surfaces.
        val deployer = KeyPair.random()
        val expectedContractId = derivedContractId(deployer)
        val rpc = MockRpc(unconfinedDispatch = true) { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(sendPendingJson())
                3 -> RpcReply(transactionSuccessJson())
                else -> RpcReply(contractInstanceEntriesResponseJson(expectedContractId))
            }
        }
        val kit = mockKit(rpc.server, deployer = deployer)
        seedPendingCredential(kit, deployer)

        val exception = assertFailsWith<ValidationException.InvalidAddress> {
            kit.walletOperations.deployPendingCredential(
                credentialId = credentialIdB64,
                autoFund = true,
                nativeTokenContract = "not-a-contract-address"
            )
        }
        assertTrue(
            exception.message.contains("nativeTokenContract"),
            "funding must have started and rejected its own argument; got: ${exception.message}"
        )
        assertNotNull(
            kit.credentialManager.getCredential(credentialIdB64),
            "the credential is deleted only after funding completes"
        )
    }

    @Test
    fun testDeployPendingCredential_relayerConfigured_submitsSignedEnvelopeAndDropsCredential() = runTest {
        val deployer = KeyPair.random()
        val contractInstance = contractInstanceEntriesResponseJson(derivedContractId(deployer))
        val rpc = MockRpc { index, _ ->
            when (index) {
                0 -> RpcReply(deployerAccountJson(deployer))
                1 -> RpcReply(deploySimulationJson())
                2 -> RpcReply(transactionSuccessJson())
                // A confirmed deploy drops the pending credential, and that cleanup re-checks
                // the contract on-chain before removing it.
                3 -> RpcReply(contractInstance)
                else -> unexpected(index, "a relayed deploy must not reach the RPC submit endpoint")
            }
        }
        val relayer = MockRelayer(relayerSuccessJson())
        val kit = mockKit(rpc.server, deployer = deployer, relayerClient = relayer.client)
        val contractId = seedPendingCredential(kit, deployer)

        val result = kit.walletOperations.deployPendingCredential(credentialId = credentialIdB64)

        rpc.assertNoUnexpectedRequests()
        assertEquals(
            listOf("getLedgerEntries", "simulateTransaction", "getTransaction", "getLedgerEntries"),
            rpc.calls,
            "a relayed deploy is submitted to the relayer, confirmed via getTransaction, then " +
                "cleans up the pending credential"
        )
        assertEquals(contractId, result.contractId)
        assertEquals(deployTxHash, result.transactionHash)
        assertEquals(0, rpc.callCount("sendTransaction"))

        val submittedXdr = assertNotNull(relayer.singleRequestJson()["xdr"]).jsonPrimitive.content
        assertEquals(result.signedTransactionXdr, submittedXdr)
        assertEquals(
            simulatedMinResourceFee,
            Transaction.fromEnvelopeXdr(submittedXdr, Network.TESTNET).fee,
            "a retried deploy also carries the resource-fee-only shape the relayer fee bump expects"
        )
        assertNull(
            kit.credentialManager.getCredential(credentialIdB64),
            "a confirmed retry removes the pending credential"
        )
    }
}
