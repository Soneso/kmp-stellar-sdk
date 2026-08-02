//
//  OZContextRuleManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildMinimalSorobanData
import com.soneso.stellar.sdk.unitTests.smartaccount.buildNoRpcMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.getTransactionSuccessResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.latestLedgerResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.sendTransactionPendingResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateCountResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateErrorResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateWithAuthResponseJson
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountEvent
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsXdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsV2Xdr
import com.soneso.stellar.sdk.xdr.HashXdr
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
import com.soneso.stellar.sdk.xdr.Uint256Xdr
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Scripted RPC mock server
// ---------------------------------------------------------------------------

/**
 * Creates a [SorobanServer] that answers requests strictly in the order of [responses] and
 * fails once the script is exhausted, so a flow issuing more RPC calls than expected is a
 * test failure rather than a silent pass. Every request body is handed to [onRequest]
 * together with its zero-based index.
 */
private fun scriptedMockServer(
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
private fun invokedContract(requestBody: String): InvokeContractArgsXdr {
    val root = Json.parseToJsonElement(requestBody).jsonObject
    val envelopeBase64 = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
    val transaction = Transaction.fromEnvelopeXdr(envelopeBase64, Network.TESTNET)
    val operation = transaction.operations
        .first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
    return (operation.hostFunction as HostFunctionXdr.InvokeContract).value
}

/** Extracts the authorization entries carried by a `sendTransaction` request body. */
private fun submittedAuthEntries(requestBody: String): List<SorobanAuthorizationEntryXdr> {
    val root = Json.parseToJsonElement(requestBody).jsonObject
    val envelopeBase64 = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
    val transaction = Transaction.fromEnvelopeXdr(envelopeBase64, Network.TESTNET)
    val operation = transaction.operations
        .first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
    return operation.auth
}

/**
 * Unit tests for the [com.soneso.stellar.sdk.smartaccount.oz.OZContextRuleManager] flows:
 * the add / update / remove round-trips through the Soroban RPC pipeline, the rule discovery
 * scan, and context rule ID resolution for authorization entries.
 *
 * Parsing of an individual rule ScVal is pinned by [ContextRuleParsingTest]; these tests pin
 * what the manager sends to the network, what it returns, and which events it emits.
 */
class OZContextRuleManagerTest {

    // ========================================================================
    // Fixtures
    // ========================================================================

    /** The connected smart account contract. */
    private val smartAccount = VERIFIER_B

    private val contractX = StrKey.encodeContract(ByteArray(32) { (it + 1).toByte() })
    private val contractY = StrKey.encodeContract(ByteArray(32) { (it + 2).toByte() })
    private val contractZ = StrKey.encodeContract(ByteArray(32) { (it + 3).toByte() })
    private val policyAddress = StrKey.encodeContract(ByteArray(32) { (it + 4).toByte() })

    private val signerA = DelegatedSigner(StrKey.encodeContract(ByteArray(32) { (it + 11).toByte() }))
    private val signerB = DelegatedSigner(StrKey.encodeContract(ByteArray(32) { (it + 12).toByte() }))
    private val signerC = DelegatedSigner(StrKey.encodeContract(ByteArray(32) { (it + 13).toByte() }))
    private val signerD = DelegatedSigner(StrKey.encodeContract(ByteArray(32) { (it + 14).toByte() }))

    private val txHash = "a1721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

    private fun buildConfig(deployer: KeyPair): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = VERIFIER_A,
        deployerKeypair = deployer
    )

    /**
     * Builds an auth entry whose credentials name [address] and whose root invocation targets
     * [targetContract]. When [address] differs from the connected smart account, the
     * single-signer submit pipeline passes the entry through untouched, so the round-trip needs
     * no passkey.
     */
    private fun authEntryFor(
        address: String,
        targetContract: String = VERIFIER_A,
        functionName: String = "noop"
    ): SorobanAuthorizationEntryXdr = SorobanAuthorizationEntryXdr(
        credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = Address(address).toSCAddress(),
                nonce = Int64Xdr(4242L),
                signatureExpirationLedger = Uint32Xdr(0u),
                signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
            )
        ),
        rootInvocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(targetContract).toSCAddress(),
                    functionName = SCSymbolXdr(functionName),
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
    private fun passThroughSubmitResponses(
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
    private fun ruleScVal(
        id: UInt,
        name: String,
        contextType: SCValXdr = Scv.toVec(listOf(Scv.toSymbol("Default"))),
        signers: List<SmartAccountSigner> = emptyList(),
        signerIds: List<UInt> = emptyList(),
        policies: List<String> = emptyList(),
        policyIds: List<UInt> = emptyList(),
        validUntil: SCValXdr = Scv.toVoid()
    ): SCValXdr {
        val entries = listOf(
            "context_type" to contextType,
            "id" to Scv.toUint32(id),
            "name" to Scv.toString(name),
            "policies" to Scv.toVec(policies.map { Scv.toAddress(Address(it).toSCAddress()) }),
            "policy_ids" to Scv.toVec(policyIds.map { Scv.toUint32(it) }),
            "signer_ids" to Scv.toVec(signerIds.map { Scv.toUint32(it) }),
            "signers" to Scv.toVec(signers.map { it.toScVal() }),
            "valid_until" to validUntil
        ).map { (key, value) -> SCMapEntryXdr(key = Scv.toSymbol(key), `val` = value) }
        return SCValXdr.Map(SCMapXdr(entries))
    }

    private fun callContractScVal(address: String): SCValXdr = Scv.toVec(
        listOf(Scv.toSymbol("CallContract"), Scv.toAddress(Address(address).toSCAddress()))
    )

    private fun parsedRule(
        id: UInt,
        contextType: ContextRuleType,
        signers: List<SmartAccountSigner>,
        policies: List<String> = emptyList()
    ): ParsedContextRule = ParsedContextRule(
        id = id,
        contextType = contextType,
        name = "Rule$id",
        signers = signers,
        signerIds = signers.indices.map { it.toUInt() },
        policies = policies,
        policyIds = policies.indices.map { it.toUInt() },
        validUntil = null
    )

    private fun contractFnEntry(vararg contracts: String): SorobanAuthorizationEntryXdr {
        fun invocation(index: Int): SorobanAuthorizedInvocationXdr = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(contracts[index]).toSCAddress(),
                    functionName = SCSymbolXdr("fn$index"),
                    args = emptyList()
                )
            ),
            subInvocations = if (index + 1 < contracts.size) listOf(invocation(index + 1)) else emptyList()
        )
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = invocation(0)
        )
    }

    private fun createContractEntry(executable: ContractExecutableXdr): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.CreateContractHostFn(
                    CreateContractArgsXdr(
                        contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                            ContractIDPreimageFromAddressXdr(
                                address = Address(smartAccount).toSCAddress(),
                                salt = Uint256Xdr(ByteArray(32))
                            )
                        ),
                        executable = executable
                    )
                ),
                subInvocations = emptyList()
            )
        )

    private fun createContractV2Entry(executable: ContractExecutableXdr): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.CreateContractV2HostFn(
                    CreateContractArgsV2Xdr(
                        contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                            ContractIDPreimageFromAddressXdr(
                                address = Address(smartAccount).toSCAddress(),
                                salt = Uint256Xdr(ByteArray(32))
                            )
                        ),
                        executable = executable,
                        constructorArgs = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )

    // ========================================================================
    // addContextRule — invocation built and submitted
    // ========================================================================

    @Test
    fun testAddContextRule_noExpirationSingleSigner_submitsAddContextRuleInvocation() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        var requestCount = 0
        val server = scriptedMockServer(
            passThroughSubmitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            requestCount = index + 1
            if (index == 1) simulateBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val signedEvents = mutableListOf<SmartAccountEvent.TransactionSigned>()
        val submittedEvents = mutableListOf<SmartAccountEvent.TransactionSubmitted>()
        kit.events.on<SmartAccountEvent.TransactionSigned> { signedEvents.add(it) }
        kit.events.on<SmartAccountEvent.TransactionSubmitted> { submittedEvents.add(it) }

        val result = kit.contextRuleManager.addContextRule(
            contextType = ContextRuleType.CallContract(contractX),
            name = "TokenOps",
            validUntil = null,
            signers = listOf(signerA)
        )

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)
        assertEquals(1001u, result.ledger, "Ledger must come from the confirmation poll")
        assertEquals(9, requestCount, "The round-trip must issue exactly nine RPC calls")

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertEquals("add_context_rule", invocation.functionName.value)
        assertEquals(
            smartAccount,
            Address.fromSCAddress(invocation.contractAddress).toString(),
            "The invocation must target the connected smart account"
        )
        assertEquals(5, invocation.args.size, "add_context_rule takes five arguments")

        val contextTypeVec = Scv.fromVec(invocation.args[0])
        assertEquals("CallContract", Scv.fromSymbol(contextTypeVec[0]))
        assertEquals(contractX, Address.fromSCVal(contextTypeVec[1]).toString())
        assertEquals("TokenOps", Scv.fromString(invocation.args[1]))
        assertIs<SCValXdr.Void>(invocation.args[2], "A null validUntil must encode as Option::None (void)")
        assertEquals(
            listOf(signerA.toScVal().toXdrBase64()),
            Scv.fromVec(invocation.args[3]).map { it.toXdrBase64() }
        )
        assertTrue(Scv.fromMap(invocation.args[4]).isEmpty(), "No policies must encode as an empty ScMap")

        assertEquals(1, signedEvents.size, "Exactly one TransactionSigned event must be emitted")
        assertEquals(smartAccount, signedEvents[0].contractId)
        assertNull(
            signedEvents[0].credentialId,
            "The simulated auth entry belongs to another address and is passed through unchanged, " +
                "so no credential signed this transaction"
        )
        assertEquals(listOf(txHash), submittedEvents.map { it.hash })
        assertTrue(submittedEvents[0].success)
    }

    @Test
    fun testAddContextRule_expirationMultipleSignersAndPolicy_encodesAllArguments() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = scriptedMockServer(
            passThroughSubmitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val externalSigner = ExternalSigner(VERIFIER_A, ByteArray(65) { (it + 1).toByte() })
        val installParams = Scv.toUint32(2u)

        val result = kit.contextRuleManager.addContextRule(
            contextType = ContextRuleType.Default,
            name = "Multisig",
            validUntil = 12345678u,
            signers = listOf(signerA, externalSigner),
            policies = mapOf(policyAddress to installParams)
        )

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertEquals("add_context_rule", invocation.functionName.value)
        assertEquals(listOf("Default"), Scv.fromVec(invocation.args[0]).map { Scv.fromSymbol(it) })
        assertEquals("Multisig", Scv.fromString(invocation.args[1]))
        assertEquals(12345678u, Scv.fromUint32(invocation.args[2]), "A set validUntil must encode as U32")

        val encodedSigners = Scv.fromVec(invocation.args[3]).map { it.toXdrBase64() }
        assertEquals(
            listOf(signerA.toScVal().toXdrBase64(), externalSigner.toScVal().toXdrBase64()),
            encodedSigners,
            "Signers must be encoded in the order they were supplied"
        )

        val encodedPolicies = Scv.fromMap(invocation.args[4])
        assertEquals(1, encodedPolicies.size)
        val policyEntry = encodedPolicies.entries.single()
        assertEquals(
            policyAddress,
            Address.fromSCVal(policyEntry.key).toString(),
            "Install params must be keyed by policy address"
        )
        assertEquals(installParams.toXdrBase64(), policyEntry.value.toXdrBase64())
    }

    @Test
    fun testAddContextRule_policyOnlyWithoutSigners_isAccepted() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = scriptedMockServer(
            passThroughSubmitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        // The "at least one signer or one policy" guard rejects a rule only when BOTH are empty.
        val result = kit.contextRuleManager.addContextRule(
            contextType = ContextRuleType.Default,
            name = "PolicyOnly",
            signers = emptyList(),
            policies = mapOf(policyAddress to Scv.toVoid())
        )

        assertTrue(result.success, "A policy-only rule must be accepted; error=${result.error}")

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertTrue(Scv.fromVec(invocation.args[3]).isEmpty(), "Signers must encode as an empty Vec")
        assertEquals(1, Scv.fromMap(invocation.args[4]).size)
    }

    @Test
    fun testAddContextRule_selectedSigners_routesToMultiSignerSubmission() = runTest {
        val deployer = KeyPair.random()
        val walletKeypair = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val walletAddress = walletKeypair.getAccountId()
        val authEntryXdr = authEntryFor(walletAddress, targetContract = smartAccount).toXdrBase64()

        var sendBody: String? = null
        var simulateBody: String? = null
        val server = scriptedMockServer(
            passThroughSubmitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body ->
            if (index == 1) simulateBody = body
            if (index == 7) sendBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.externalSigners.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.contextRuleManager.addContextRule(
            contextType = ContextRuleType.Default,
            name = "MultiSignerRule",
            signers = listOf(signerA),
            selectedSigners = listOf(SelectedSigner.Wallet(walletAddress))
        )

        assertTrue(result.success, "Multi-signer round-trip must succeed; error=${result.error}")
        assertEquals("add_context_rule", invokedContract(assertNotNull(simulateBody)).functionName.value)

        // The multi-signer pipeline stamps an expiration and writes the wallet signature onto the
        // entry; the single-signer pipeline would have passed the entry through untouched.
        val submitted = submittedAuthEntries(assertNotNull(sendBody))
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

    @Test
    fun testAddContextRule_selectedSignerWithoutSigningSource_rejectedByMultiSignerValidation() = runTest {
        val deployer = KeyPair.random()
        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val unregistered = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "Rule",
                signers = listOf(signerA),
                selectedSigners = listOf(SelectedSigner.Wallet(unregistered))
            )
        }
        assertTrue(
            ex.message.contains(unregistered),
            "The multi-signer validation must name the address with no signing source; got: ${ex.message}"
        )
    }

    // ========================================================================
    // getContextRule
    // ========================================================================

    @Test
    fun testGetContextRule_returnsRawRuleScValFromSimulation() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val rule = ruleScVal(id = 5u, name = "Fetched")

        var simulateBody: String? = null
        var requestCount = 0
        val server = scriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            )
        ) { index, body ->
            requestCount = index + 1
            if (index == 1) simulateBody = body
        }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val fetched = kit.contextRuleManager.getContextRule(id = 5u)

        assertEquals(
            rule.toXdrBase64(),
            fetched.toXdrBase64(),
            "The raw rule ScVal must be returned unchanged"
        )
        assertEquals(2, requestCount, "A query must issue only the account lookup and one simulation")

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertEquals("get_context_rule", invocation.functionName.value)
        assertEquals(1, invocation.args.size)
        assertEquals(5u, Scv.fromUint32(invocation.args[0]))
    }

    // ========================================================================
    // getContextRulesCount
    // ========================================================================

    @Test
    fun testGetContextRulesCount_u32Result_returnsCount() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        var simulateBody: String? = null
        val server = scriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(7u)).toXdrBase64(), sorobanDataXdr)
            )
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        assertEquals(7u, kit.contextRuleManager.getContextRulesCount())

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertEquals("get_context_rules_count", invocation.functionName.value)
        assertTrue(invocation.args.isEmpty(), "get_context_rules_count takes no arguments")
    }

    @Test
    fun testGetContextRulesCount_nonU32Result_throwsValidationException() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val server = scriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(Scv.toString("not-a-number").toXdrBase64(), sorobanDataXdr)
            )
        )

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.getContextRulesCount()
        }
        assertTrue(
            ex.message.contains("get_context_rules_count"),
            "The exception must name the query that returned an unexpected type; got: ${ex.message}"
        )
    }

    // ========================================================================
    // getAllContextRules / listContextRules
    // ========================================================================

    @Test
    fun testGetAllContextRules_zeroActiveRules_skipsPerRuleQueries() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        var requestCount = 0
        val server = scriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanDataXdr)
            )
        ) { index, _ -> requestCount = index + 1 }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        assertTrue(kit.contextRuleManager.getAllContextRules().isEmpty())
        assertEquals(2, requestCount, "A zero count must short-circuit before any get_context_rule call")
    }

    @Test
    fun testListContextRules_twoActiveRules_parsesBothAndStopsScanning() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        val rule0 = ruleScVal(
            id = 0u,
            name = "DefaultRule",
            signers = listOf(signerA),
            signerIds = listOf(3u)
        )
        val rule1 = ruleScVal(
            id = 1u,
            name = "TokenRule",
            contextType = callContractScVal(contractX),
            policies = listOf(policyAddress),
            policyIds = listOf(9u),
            validUntil = Scv.toUint32(555u)
        )

        var requestCount = 0
        val ruleQueryIds = mutableListOf<UInt>()
        val server = scriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(2u)).toXdrBase64(), sorobanDataXdr),
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule0.toXdrBase64(), sorobanDataXdr),
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule1.toXdrBase64(), sorobanDataXdr)
            )
        ) { index, body ->
            requestCount = index + 1
            if (index == 3 || index == 5) {
                ruleQueryIds.add(Scv.fromUint32(invokedContract(body).args[0]))
            }
        }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = kit.contextRuleManager.listContextRules()

        assertEquals(2, rules.size)
        assertEquals(listOf(0u, 1u), rules.map { it.id })
        assertEquals(listOf("DefaultRule", "TokenRule"), rules.map { it.name })
        assertIs<ContextRuleType.Default>(rules[0].contextType)
        assertEquals(listOf(signerA), rules[0].signers)
        assertEquals(listOf(3u), rules[0].signerIds)
        assertNull(rules[0].validUntil)
        assertEquals(ContextRuleType.CallContract(contractX), rules[1].contextType)
        assertEquals(listOf(policyAddress), rules[1].policies)
        assertEquals(555u, rules[1].validUntil)
        assertEquals(listOf(0u, 1u), ruleQueryIds, "The scan must query rule IDs in ascending order")
        assertEquals(6, requestCount, "The scan must stop once the active count is reached")
    }

    @Test
    fun testGetAllContextRules_removedRuleLeavesGap_skipsFailedIdAndContinues() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val rule1 = ruleScVal(id = 1u, name = "Survivor")

        val server = scriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(1u)).toXdrBase64(), sorobanDataXdr),
                ledgerEntriesResponseJson(accountXdr),
                simulateErrorResponseJson("HostError: context rule 0 not found"),
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule1.toXdrBase64(), sorobanDataXdr)
            )
        )

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = kit.contextRuleManager.listContextRules()

        assertEquals(1, rules.size, "The removed ID must be skipped, not reported as a rule")
        assertEquals(1u, rules[0].id)
        assertEquals("Survivor", rules[0].name)
    }

    @Test
    fun testGetAllContextRules_maxScanIdBelowActiveCount_stopsAtScanBound() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val rule0 = ruleScVal(id = 0u, name = "OnlyScanned")

        var requestCount = 0
        val server = scriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(2u)).toXdrBase64(), sorobanDataXdr),
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule0.toXdrBase64(), sorobanDataXdr)
            )
        ) { index, _ -> requestCount = index + 1 }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        // Two rules are active but the scan bound admits only ID 0.
        val rules = kit.contextRuleManager.getAllContextRules(maxScanId = 1u)

        assertEquals(1, rules.size)
        assertEquals(4, requestCount, "The scan must not probe IDs at or beyond maxScanId")
    }

    // ========================================================================
    // updateName
    // ========================================================================

    @Test
    fun testUpdateName_submitsUpdateContextRuleNameInvocation() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = scriptedMockServer(
            passThroughSubmitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.contextRuleManager.updateName(id = 7u, name = "RenamedRule")

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertEquals("update_context_rule_name", invocation.functionName.value)
        assertEquals(2, invocation.args.size)
        assertEquals(7u, Scv.fromUint32(invocation.args[0]))
        assertEquals("RenamedRule", Scv.fromString(invocation.args[1]))
    }

    @Test
    fun testUpdateName_selectedSignerWithoutSigningSource_rejectedByMultiSignerValidation() = runTest {
        val deployer = KeyPair.random()
        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val unregistered = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.updateName(
                id = 1u,
                name = "Renamed",
                selectedSigners = listOf(SelectedSigner.Wallet(unregistered))
            )
        }
        assertTrue(
            ex.message.contains(unregistered),
            "The multi-signer validation must name the address with no signing source; got: ${ex.message}"
        )
    }

    // ========================================================================
    // updateValidUntil
    // ========================================================================

    @Test
    fun testUpdateValidUntil_ledgerNumber_encodesU32Argument() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = scriptedMockServer(
            passThroughSubmitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.contextRuleManager.updateValidUntil(id = 3u, validUntil = 999u)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertEquals("update_context_rule_valid_until", invocation.functionName.value)
        assertEquals(2, invocation.args.size)
        assertEquals(3u, Scv.fromUint32(invocation.args[0]))
        assertEquals(999u, Scv.fromUint32(invocation.args[1]))
    }

    @Test
    fun testUpdateValidUntil_null_encodesVoidArgument() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = scriptedMockServer(
            passThroughSubmitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.contextRuleManager.updateValidUntil(id = 3u, validUntil = null)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertEquals("update_context_rule_valid_until", invocation.functionName.value)
        assertEquals(3u, Scv.fromUint32(invocation.args[0]))
        assertIs<SCValXdr.Void>(
            invocation.args[1],
            "Clearing the expiration must encode as Option::None (void)"
        )
    }

    @Test
    fun testUpdateValidUntil_selectedSignerWithoutSigningSource_rejectedByMultiSignerValidation() = runTest {
        val deployer = KeyPair.random()
        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val unregistered = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.updateValidUntil(
                id = 1u,
                validUntil = 10u,
                selectedSigners = listOf(SelectedSigner.Wallet(unregistered))
            )
        }
        assertTrue(
            ex.message.contains(unregistered),
            "The multi-signer validation must name the address with no signing source; got: ${ex.message}"
        )
    }

    // ========================================================================
    // removeContextRule
    // ========================================================================

    @Test
    fun testRemoveContextRule_submitsRemoveContextRuleInvocation() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val authEntryXdr = authEntryFor(VERIFIER_A).toXdrBase64()

        var simulateBody: String? = null
        val server = scriptedMockServer(
            passThroughSubmitResponses(accountXdr, authEntryXdr, sorobanDataXdr)
        ) { index, body -> if (index == 1) simulateBody = body }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val result = kit.contextRuleManager.removeContextRule(id = 9u)

        assertTrue(result.success, "Round-trip must succeed; error=${result.error}")
        assertEquals(txHash, result.hash)

        val invocation = invokedContract(assertNotNull(simulateBody))
        assertEquals("remove_context_rule", invocation.functionName.value)
        assertEquals(1, invocation.args.size)
        assertEquals(9u, Scv.fromUint32(invocation.args[0]))
    }

    @Test
    fun testRemoveContextRule_selectedSignerWithoutSigningSource_rejectedByMultiSignerValidation() = runTest {
        val deployer = KeyPair.random()
        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val unregistered = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.removeContextRule(
                id = 4u,
                selectedSigners = listOf(SelectedSigner.Wallet(unregistered))
            )
        }
        assertTrue(
            ex.message.contains(unregistered),
            "The multi-signer validation must name the address with no signing source; got: ${ex.message}"
        )
    }

    // ========================================================================
    // resolveContextRuleIdsForEntry — context type matching
    // ========================================================================

    @Test
    fun testResolveContextRuleIds_singleMatchingRule_returnsItsId() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = listOf(
            parsedRule(4u, ContextRuleType.CallContract(contractX), listOf(signerA)),
            parsedRule(5u, ContextRuleType.CallContract(contractY), listOf(signerB))
        )

        val ids = kit.contextRuleManager.resolveContextRuleIdsForEntry(
            contractFnEntry(contractX),
            listOf(signerB),
            rules
        )

        assertEquals(listOf(4u), ids, "The only rule matching the invoked contract must win")
    }

    @Test
    fun testResolveContextRuleIds_defaultRule_matchesAnyContractContext() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = listOf(parsedRule(2u, ContextRuleType.Default, listOf(signerA)))

        assertEquals(
            listOf(2u),
            kit.contextRuleManager.resolveContextRuleIdsForEntry(
                contractFnEntry(contractX), listOf(signerA), rules
            )
        )
        assertEquals(
            listOf(2u),
            kit.contextRuleManager.resolveContextRuleIdsForEntry(
                contractFnEntry(contractY), listOf(signerA), rules
            ),
            "A Default rule must fall back for every context type"
        )
    }

    @Test
    fun testResolveContextRuleIds_subInvocations_returnsOneIdPerInvocation() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = listOf(
            parsedRule(1u, ContextRuleType.CallContract(contractX), listOf(signerA)),
            parsedRule(2u, ContextRuleType.CallContract(contractY), listOf(signerA)),
            parsedRule(3u, ContextRuleType.CallContract(contractZ), listOf(signerA))
        )

        val ids = kit.contextRuleManager.resolveContextRuleIdsForEntry(
            contractFnEntry(contractX, contractY, contractZ),
            listOf(signerA),
            rules
        )

        assertEquals(
            listOf(1u, 2u, 3u),
            ids,
            "The nested invocation tree must be walked depth-first, root first"
        )
    }

    @Test
    fun testResolveContextRuleIds_createContractInvocation_matchesWasmHashRule() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val wasmHash = ByteArray(32) { (it + 21).toByte() }
        val rules = listOf(
            parsedRule(6u, ContextRuleType.CreateContract(wasmHash.copyOf()), listOf(signerA)),
            parsedRule(7u, ContextRuleType.CallContract(contractX), listOf(signerA))
        )

        val ids = kit.contextRuleManager.resolveContextRuleIdsForEntry(
            createContractEntry(ContractExecutableXdr.WasmHash(HashXdr(wasmHash))),
            listOf(signerA),
            rules
        )

        assertEquals(listOf(6u), ids)
    }

    @Test
    fun testResolveContextRuleIds_createContractV2Invocation_matchesWasmHashRule() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val wasmHash = ByteArray(32) { (it + 31).toByte() }
        val rules = listOf(
            parsedRule(8u, ContextRuleType.CreateContract(wasmHash.copyOf()), listOf(signerA)),
            parsedRule(9u, ContextRuleType.CreateContract(ByteArray(32) { 0 }), listOf(signerA))
        )

        val ids = kit.contextRuleManager.resolveContextRuleIdsForEntry(
            createContractV2Entry(ContractExecutableXdr.WasmHash(HashXdr(wasmHash))),
            listOf(signerA),
            rules
        )

        assertEquals(listOf(8u), ids, "Only the rule whose WASM hash matches may be selected")
    }

    @Test
    fun testResolveContextRuleIds_createContractWithStellarAssetExecutable_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.resolveContextRuleIdsForEntry(
                createContractEntry(ContractExecutableXdr.Void),
                emptyList(),
                emptyList()
            )
        }
        assertTrue(
            ex.message.contains("Stellar Asset Contract"),
            "The exception must name the Stellar Asset Contract executable; got: ${ex.message}"
        )
    }

    // ========================================================================
    // resolveContextRuleIdsForEntry — signer match tiers
    // ========================================================================

    @Test
    fun testResolveContextRuleIds_exactSignerMatch_winsOverOtherCandidates() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = listOf(
            parsedRule(1u, ContextRuleType.Default, listOf(signerA, signerB)),
            parsedRule(2u, ContextRuleType.CallContract(contractX), listOf(signerA))
        )

        val ids = kit.contextRuleManager.resolveContextRuleIdsForEntry(
            contractFnEntry(contractX),
            listOf(signerA, signerB),
            rules
        )

        assertEquals(
            listOf(1u),
            ids,
            "The rule holding exactly the selected signer set must be chosen"
        )
    }

    @Test
    fun testResolveContextRuleIds_ruleSignersSubsetOfSelected_skipsRulesWithPolicies() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = listOf(
            parsedRule(1u, ContextRuleType.Default, listOf(signerA), policies = listOf(policyAddress)),
            parsedRule(2u, ContextRuleType.CallContract(contractX), listOf(signerA, signerB))
        )

        val ids = kit.contextRuleManager.resolveContextRuleIdsForEntry(
            contractFnEntry(contractX),
            listOf(signerA, signerB, signerC),
            rules
        )

        assertEquals(
            listOf(2u),
            ids,
            "A policy-bearing rule is not eligible for the rule-subset tier"
        )
    }

    @Test
    fun testResolveContextRuleIds_selectedSignersSubsetOfRule_resolvesThresholdRule() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        // Rule 1 holds a signer that was not selected, so neither the exact nor the rule-subset
        // tier matches it. Rule 2 is a threshold rule the user under-signs.
        val rules = listOf(
            parsedRule(1u, ContextRuleType.Default, listOf(signerA, signerD)),
            parsedRule(
                2u,
                ContextRuleType.CallContract(contractX),
                listOf(signerA, signerB, signerC),
                policies = listOf(policyAddress)
            )
        )

        val ids = kit.contextRuleManager.resolveContextRuleIdsForEntry(
            contractFnEntry(contractX),
            listOf(signerA, signerB),
            rules
        )

        assertEquals(
            listOf(2u),
            ids,
            "Selecting a subset of a threshold rule's signers must resolve to that rule"
        )
    }

    @Test
    fun testResolveContextRuleIds_noRuleForContext_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = listOf(parsedRule(1u, ContextRuleType.CallContract(contractY), listOf(signerA)))

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.resolveContextRuleIdsForEntry(
                contractFnEntry(contractX),
                listOf(signerA),
                rules
            )
        }
        assertTrue(
            ex.message.contains("No context rule matches"),
            "The exception must report the missing context rule; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains(contractX),
            "The exception must name the unmatched contract; got: ${ex.message}"
        )
    }

    @Test
    fun testResolveContextRuleIds_selectedSignersMatchTwoRules_throwsAmbiguityException() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = listOf(
            parsedRule(
                11u,
                ContextRuleType.Default,
                listOf(signerA, signerB, signerC),
                policies = listOf(policyAddress)
            ),
            parsedRule(
                12u,
                ContextRuleType.CallContract(contractX),
                listOf(signerA, signerB, signerC),
                policies = listOf(policyAddress)
            )
        )

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.resolveContextRuleIdsForEntry(
                contractFnEntry(contractX),
                listOf(signerA, signerB),
                rules
            )
        }
        assertTrue(
            ex.message.contains("11") && ex.message.contains("12"),
            "The ambiguity error must list every matching rule ID; got: ${ex.message}"
        )
    }

    @Test
    fun testResolveContextRuleIds_noRuleContainsSelectedSigners_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(KeyPair.random()), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", smartAccount)

        val rules = listOf(
            parsedRule(1u, ContextRuleType.Default, listOf(signerA), policies = listOf(policyAddress)),
            parsedRule(
                2u,
                ContextRuleType.CallContract(contractX),
                listOf(signerB),
                policies = listOf(policyAddress)
            )
        )

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.contextRuleManager.resolveContextRuleIdsForEntry(
                contractFnEntry(contractX),
                listOf(signerC),
                rules
            )
        }
        assertTrue(
            ex.message.contains("No context rule contains all selected signers"),
            "The exception must state that no rule holds the selected signers; got: ${ex.message}"
        )
    }

    @Test
    fun testResolveContextRuleIds_withoutPrefetchedRules_fetchesRulesFromNetwork() = runTest {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val rule = ruleScVal(
            id = 3u,
            name = "Fetched",
            contextType = callContractScVal(contractX),
            signers = listOf(signerA),
            signerIds = listOf(0u)
        )

        var requestCount = 0
        val server = scriptedMockServer(
            listOf(
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(1u)).toXdrBase64(), sorobanDataXdr),
                ledgerEntriesResponseJson(accountXdr),
                simulateCountResponseJson(rule.toXdrBase64(), sorobanDataXdr)
            )
        ) { index, _ -> requestCount = index + 1 }

        val kit = OZSmartAccountKit.createWithServer(buildConfig(deployer), server)
        kit.setConnectedState("test-credential-id", smartAccount)

        val ids = kit.contextRuleManager.resolveContextRuleIdsForEntry(
            contractFnEntry(contractX),
            listOf(signerA)
        )

        assertEquals(listOf(3u), ids, "The overload without pre-fetched rules must resolve against on-chain rules")
        assertEquals(4, requestCount, "Rules must be fetched exactly once")
    }
}
