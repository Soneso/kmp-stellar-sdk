package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.rpc.exception.SimulationFailureDetails
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError.Provenance.DIAGNOSTIC_EVENTS
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError.Provenance.HOST_ERROR
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.unitTests.rpc.responses.RpcReply
import com.soneso.stellar.sdk.unitTests.rpc.responses.ScriptedRpc
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_A
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_A_CONTRACT
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B1
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B1_CONTRACT
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B2
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B2_CONTRACT
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_C1
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_C2
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CONTRACT_ONE
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CONTRACT_TWO
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.MASKED_AUTH
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.assertCarries
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.contractErrorEvent
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.simulateErrorBody
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.suffix
import com.soneso.stellar.sdk.unitTests.smartaccount.MockWebAuthnProvider
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAuthEntry
import com.soneso.stellar.sdk.unitTests.smartaccount.buildMinimalSorobanData
import com.soneso.stellar.sdk.unitTests.smartaccount.latestLedgerResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateCountResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.simulateWithAuthResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.stubHostFunction
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The simulation data that [TransactionException.SimulationFailed] carries on every smart-account
 * path that simulates: the diagnostic events of the failed response and the contract error it
 * reports. The message is the path's failure text, followed by the suffix when the contract error
 * was found in the events.
 * Each path fails before anything is sent.
 */
class SimulationFailureDataTest {

    @AfterTest
    fun resetFriendBotOverride() {
        FriendBot.httpClientOverride = null
    }

    private val walletContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    private val credentialIdBytes = ByteArray(16) { it.toByte() }

    private val credentialId = Util.base64urlEncode(credentialIdBytes)

    private val sorobanData = buildMinimalSorobanData().toXdrBase64()

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** The secp256r1 generator point, a public key that registration accepts. */
    private fun registrationPublicKey(): ByteArray =
        byteArrayOf(0x04) +
            hexBytes("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296") +
            hexBytes("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")

    private fun registeringProvider() = MockWebAuthnProvider().apply {
        registrationResult = WebAuthnRegistrationResult(
            credentialId = credentialIdBytes,
            publicKey = registrationPublicKey(),
            attestationObject = ByteArray(0),
            transports = listOf("internal"),
            deviceType = "multiDevice",
            backedUp = true
        )
    }

    private fun kit(
        rpc: ScriptedRpc,
        deployer: KeyPair,
        provider: WebAuthnProvider? = null,
        unconfined: Boolean = false
    ): OZSmartAccountKit = OZSmartAccountKit.createWithServer(
        config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            deployerKeypair = deployer,
            webauthnProvider = provider
        ),
        sorobanServer = rpc.server(unconfined = unconfined)
    )

    private suspend fun connectedKit(
        rpc: ScriptedRpc,
        deployer: KeyPair,
        unconfined: Boolean = false
    ): OZSmartAccountKit = kit(rpc, deployer, unconfined = unconfined).also {
        it.setConnectedState(credentialId, walletContract)
    }

    private fun account(owner: KeyPair): String = ledgerEntriesResponseJson(buildAccountEntryXdr(owner).toXdrBase64())

    private fun simulationValue(value: SCValXdr): String =
        """{"jsonrpc":"2.0","id":"1","result":{"transactionData":"$sorobanData","minResourceFee":100,""" +
            """"results":[{"auth":[],"xdr":"${value.toXdrBase64()}"}],"latestLedger":100}}"""

    private val simulationWithoutAuth: String get() = simulationValue(Scv.toVoid())

    private fun capture(case: String, rpc30_0_0: Boolean = false) =
        SimulationFixtures.response(case, rpc30_0_0)

    // ========== createWallet and deployPendingCredential ==========

    @Test
    fun createWallet_maskedConstructorFailure_carriesTheReportedContractError() = runTest {
        for (rpc30_0_0 in listOf(false, true)) {
            val deployer = KeyPair.random()
            val rpc = ScriptedRpc(account(deployer), SimulationFixtures.body(CASE_A, rpc30_0_0))
            val kit = kit(rpc, deployer, registeringProvider())

            val failure = assertFailsWith<TransactionException.SimulationFailed> {
                kit.walletOperations.createWallet()
            }

            val simulation = capture(CASE_A, rpc30_0_0)
            assertCarries(
                failure,
                "Transaction simulation failed: Simulation error: ${simulation.error}" +
                    suffix(3201u, CASE_A_CONTRACT),
                simulation.events!!,
                ReportedContractError(3201u, DIAGNOSTIC_EVENTS, CASE_A_CONTRACT)
            )
            assertEquals(
                OZContractError(3201, "SimpleThresholdError", "InvalidThreshold"),
                ContractErrorCodes.decodeFromSimulationFailure(failure)
            )
            assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
        }
    }

    @Test
    fun createWallet_contractErrorHeader_messageHasNoSuffix() = runTest {
        for ((case, code) in listOf(CASE_C1 to 3200u, CASE_C2 to 8u)) {
            val deployer = KeyPair.random()
            val rpc = ScriptedRpc(account(deployer), SimulationFixtures.body(case))
            val kit = kit(rpc, deployer, registeringProvider())

            val failure = assertFailsWith<TransactionException.SimulationFailed> {
                kit.walletOperations.createWallet()
            }

            val simulation = capture(case)
            assertCarries(
                failure,
                "Transaction simulation failed: Simulation error: ${simulation.error}",
                simulation.events!!,
                ReportedContractError(code, HOST_ERROR, null)
            )
        }
    }

    @Test
    fun createWallet_transportFailure_carriesNoSimulationData() = runTest {
        val deployer = KeyPair.random()
        val rpc = ScriptedRpc(
            listOf(RpcReply(account(deployer)), RpcReply("upstream unavailable", HttpStatusCode.InternalServerError))
        )
        val kit = kit(rpc, deployer, registeringProvider())

        val failure = assertFailsWith<TransactionException.SimulationFailed> {
            kit.walletOperations.createWallet()
        }

        assertTrue(failure.message.startsWith("Transaction simulation failed: Failed to simulate deployment transaction: "))
        assertNotNull(failure.cause, "the transport failure stays the cause")
        assertEquals(emptyList(), failure.diagnosticEventsXdr)
        assertNull(failure.reportedContractError)
        assertNull(ContractErrorCodes.decodeFromSimulationFailure(failure))
    }

    @Test
    fun deployPendingCredential_maskedConstructorFailure_carriesTheReportedContractError() = runTest {
        val deployer = KeyPair.random()
        val rpc = ScriptedRpc(account(deployer), SimulationFixtures.body(CASE_A))
        val kit = kit(rpc, deployer)
        kit.getStorage().save(
            StoredCredential(
                credentialId = credentialId,
                publicKey = registrationPublicKey(),
                contractId = SmartAccountUtils.deriveContractAddress(
                    credentialId = credentialIdBytes,
                    deployerPublicKey = deployer.getAccountId(),
                    networkPassphrase = Network.TESTNET.networkPassphrase
                ),
                deploymentStatus = CredentialDeploymentStatus.PENDING
            )
        )

        val failure = assertFailsWith<TransactionException.SimulationFailed> {
            kit.walletOperations.deployPendingCredential(credentialId = credentialId)
        }

        val simulation = capture(CASE_A)
        assertCarries(
            failure,
            "Transaction simulation failed: Simulation error: ${simulation.error}" + suffix(3201u, CASE_A_CONTRACT),
            simulation.events!!,
            ReportedContractError(3201u, DIAGNOSTIC_EVENTS, CASE_A_CONTRACT)
        )
        assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
    }

    // ========== submit ==========

    @Test
    fun submit_maskedInitialSimulation_carriesTheReportedContractError() = runTest {
        val deployer = KeyPair.random()
        val rpc = ScriptedRpc(account(deployer), SimulationFixtures.body(CASE_B2))
        val kit = connectedKit(rpc, deployer)

        val failure = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.submit(hostFunction = stubHostFunction(walletContract), auth = emptyList())
        }

        val simulation = capture(CASE_B2)
        assertCarries(
            failure,
            "Transaction simulation failed: Simulation error: ${simulation.error}" + suffix(3016u, CASE_B2_CONTRACT),
            simulation.events!!,
            ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CASE_B2_CONTRACT)
        )
        assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
    }

    @Test
    fun submit_maskedReSimulation_carriesTheSignerError() = runTest {
        val cases = listOf(
            Triple(CASE_B2, ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CASE_B2_CONTRACT), "UnauthorizedSigner"),
            Triple(CASE_B1, ReportedContractError(3114u, DIAGNOSTIC_EVENTS, CASE_B1_CONTRACT), "ChallengeInvalid")
        )
        for ((case, reported, name) in cases) {
            for (rpc30_0_0 in listOf(false, true)) {
                val deployer = KeyPair.random()
                val rpc = ScriptedRpc(
                    account(deployer),
                    simulationWithoutAuth,
                    account(deployer),
                    SimulationFixtures.body(case, rpc30_0_0)
                )
                val kit = connectedKit(rpc, deployer)

                val failure = assertFailsWith<TransactionException.SimulationFailed> {
                    kit.transactionOperations.submit(hostFunction = stubHostFunction(walletContract), auth = emptyList())
                }

                val simulation = capture(case, rpc30_0_0)
                assertCarries(
                    failure,
                    "Transaction simulation failed: Re-simulation error: ${simulation.error}" +
                        suffix(reported.code, reported.contractId),
                    simulation.events!!,
                    reported
                )
                assertEquals(name, ContractErrorCodes.decodeFromSimulationFailure(failure)?.name, "$case $rpc30_0_0")
                assertEquals(
                    listOf("getLedgerEntries", "simulateTransaction", "getLedgerEntries", "simulateTransaction"),
                    rpc.methods
                )
            }
        }
    }

    private suspend fun reSimulationFailure(error: String, events: List<String>): TransactionException.SimulationFailed {
        val deployer = KeyPair.random()
        val rpc = ScriptedRpc(account(deployer), simulationWithoutAuth, account(deployer), simulateErrorBody(error, events))
        val kit = connectedKit(rpc, deployer)
        val failure = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.submit(hostFunction = stubHostFunction(walletContract), auth = emptyList())
        }
        assertEquals(0, rpc.count("sendTransaction"))
        return failure
    }

    @Test
    fun submit_undecodableEvents_theEarliestReportWins() = runTest {
        val events = listOf(
            "%%%",
            SimulationFixtures.truncated(contractErrorEvent(3201u)),
            contractErrorEvent(3114u, CONTRACT_ONE),
            "AAAA",
            contractErrorEvent(3016u, CONTRACT_TWO)
        )

        val failure = reSimulationFailure(MASKED_AUTH, events)

        assertCarries(
            failure,
            "Transaction simulation failed: Re-simulation error: $MASKED_AUTH" + suffix(3114u, CONTRACT_ONE),
            events,
            ReportedContractError(3114u, DIAGNOSTIC_EVENTS, CONTRACT_ONE)
        )
    }

    @Test
    fun submit_eventWithoutContractId_suffixWithoutContract() = runTest {
        val events = listOf(contractErrorEvent(3016u, contractId = null))

        val failure = reSimulationFailure(MASKED_AUTH, events)

        assertCarries(
            failure,
            "Transaction simulation failed: Re-simulation error: $MASKED_AUTH" +
                " (contract error in diagnostic events: Error(Contract, #3016))",
            events,
            ReportedContractError(3016u, DIAGNOSTIC_EVENTS, null)
        )
    }

    @Test
    fun submit_twoContractsWithDuplicates_keepsTheServedEvents() = runTest {
        val events = listOf(
            contractErrorEvent(3114u, CONTRACT_ONE),
            contractErrorEvent(3016u, CONTRACT_TWO),
            contractErrorEvent(3114u, CONTRACT_ONE),
            contractErrorEvent(3016u, CONTRACT_TWO)
        )

        val failure = reSimulationFailure(MASKED_AUTH, events)

        assertEquals(events, failure.diagnosticEventsXdr)
        assertEquals(ReportedContractError(3114u, DIAGNOSTIC_EVENTS, CONTRACT_ONE), failure.reportedContractError)
    }

    @Test
    fun submit_decoderReadsOnlyTheReportedError() = runTest {
        // A 30.0.0 text whose event log names #3016 while the earliest event reports 9999.
        val text = "$MASKED_AUTH\n\nEvent log (newest first):\n" +
            "   0: [Diagnostic Event] contract:$CONTRACT_TWO, topics:[error, Error(Contract, #3016)], data:\"failed\"\n"
        val unauthorized = OZContractError(3016, "SmartAccountError", "UnauthorizedSigner")

        val unknownFirst = reSimulationFailure(
            text,
            listOf(contractErrorEvent(9999u, CONTRACT_ONE), contractErrorEvent(3016u, CONTRACT_TWO))
        )
        assertEquals(ReportedContractError(9999u, DIAGNOSTIC_EVENTS, CONTRACT_ONE), unknownFirst.reportedContractError)
        assertNull(ContractErrorCodes.decodeFromSimulationFailure(unknownFirst))
        assertEquals(unauthorized, ContractErrorCodes.decodeFromMessage(unknownFirst.message))

        val markerOnly = reSimulationFailure(
            text,
            listOf(SimulationFixtures.diagnosticEvent(topics = listOf(SCValXdr.Sym(SCSymbolXdr("fn_call")))))
        )
        assertEquals("Transaction simulation failed: Re-simulation error: $text", markerOnly.message)
        assertNull(markerOnly.reportedContractError)
        assertNull(ContractErrorCodes.decodeFromSimulationFailure(markerOnly))
        assertEquals(unauthorized, ContractErrorCodes.decodeFromMessage(markerOnly.message))

        val oldConstructor = TransactionException.SimulationFailed("HostError: Error(Contract, #3016)")
        assertNull(oldConstructor.reportedContractError)
        assertNull(ContractErrorCodes.decodeFromSimulationFailure(oldConstructor))
    }

    @Test
    fun submit_rpcFailures_propagateWithoutSimulationData() = runTest {
        val rpcError = """{"jsonrpc":"2.0","id":"1","error":{"code":-32602,"message":"invalid params"}}"""
        val missingResult = """{"jsonrpc":"2.0","id":"1"}"""
        val cases = listOf(
            RpcReply(rpcError),
            RpcReply("upstream unavailable", HttpStatusCode.InternalServerError),
            RpcReply(missingResult)
        )
        val failures = cases.map { simulateReply ->
            val deployer = KeyPair.random()
            val rpc = ScriptedRpc(listOf(RpcReply(account(deployer)), simulateReply))
            val kit = connectedKit(rpc, deployer)
            val failure = failureOf {
                kit.transactionOperations.submit(hostFunction = stubHostFunction(walletContract), auth = emptyList())
            }
            assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
            failure
        }

        assertEquals(-32602, assertIs<SorobanRpcException>(failures[0]).errorCode)
        val transport = assertIs<IllegalArgumentException>(failures[1])
        assertTrue(transport.message!!.startsWith("Failed to parse response for method simulateTransaction"))
        assertFalse(transport is SimulationFailureDetails)
        val missing = assertIs<IllegalStateException>(failures[2])
        assertTrue(missing.message!!.startsWith("Response missing result field (method: simulateTransaction"))
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (e: Throwable) {
            return e
        }
        throw AssertionError("Expected the call to fail")
    }

    // ========== fundWallet ==========

    private fun friendbotSuccess(): HttpClient = HttpClient(MockEngine { _ ->
        respond(
            content = ByteReadChannel("""{"successful":true}"""),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    })

    private val fundedBalance: String
        get() = simulationValue(Scv.toInt128(BigInteger.fromLong(1_000L * Util.STROOPS_PER_XLM)))

    @Test
    fun fundWallet_maskedTransferSimulation_carriesTheReportedContractError() = runTest {
        FriendBot.httpClientOverride = friendbotSuccess()
        val deployer = KeyPair.random()
        val temp = account(KeyPair.random())
        val rpc = ScriptedRpc(temp, temp, account(deployer), fundedBalance, SimulationFixtures.body(CASE_B2))
        val kit = connectedKit(rpc, deployer, unconfined = true)

        val failure = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = CONTRACT_TWO)
        }

        val simulation = capture(CASE_B2)
        assertCarries(
            failure,
            "Transaction simulation failed: Failed to simulate funding transfer: ${simulation.error}" +
                suffix(3016u, CASE_B2_CONTRACT),
            simulation.events!!,
            ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CASE_B2_CONTRACT)
        )
        assertEquals(0, rpc.count("sendTransaction"))
        assertEquals(5, rpc.methods.size)
    }

    @Test
    fun fundWallet_maskedReSimulation_carriesTheReportedContractError() = runTest {
        FriendBot.httpClientOverride = friendbotSuccess()
        val deployer = KeyPair.random()
        val temp = account(KeyPair.random())
        val rpc = ScriptedRpc(
            temp,
            temp,
            account(deployer),
            fundedBalance,
            simulationWithoutAuth,
            latestLedgerResponseJson(),
            temp,
            SimulationFixtures.body(CASE_B2)
        )
        val kit = connectedKit(rpc, deployer, unconfined = true)

        val failure = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.fundWallet(nativeTokenContract = CONTRACT_TWO)
        }

        val simulation = capture(CASE_B2)
        assertCarries(
            failure,
            "Transaction simulation failed: Re-simulation error: ${simulation.error}" + suffix(3016u, CASE_B2_CONTRACT),
            simulation.events!!,
            ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CASE_B2_CONTRACT)
        )
        assertEquals(0, rpc.count("sendTransaction"))
        assertEquals(8, rpc.methods.size)
    }

    // ========== Context rule reads ==========

    @Test
    fun contextRuleReads_maskedSimulation_carryTheReportedContractError() = runTest {
        val reads: List<suspend (OZSmartAccountKit) -> Unit> = listOf(
            { it.contextRuleManager.getContextRule(id = 1u) },
            { it.contextRuleManager.getContextRulesCount() }
        )
        val simulation = capture(CASE_B2)
        for (read in reads) {
            val deployer = KeyPair.random()
            val rpc = ScriptedRpc(account(deployer), SimulationFixtures.body(CASE_B2))
            val kit = connectedKit(rpc, deployer)

            val failure = assertFailsWith<TransactionException.SimulationFailed> { read(kit) }

            assertCarries(
                failure,
                "Transaction simulation failed: Simulation error: ${simulation.error}" + suffix(3016u, CASE_B2_CONTRACT),
                simulation.events!!,
                ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CASE_B2_CONTRACT)
            )
            assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
        }
    }

    // ========== submitWithMultipleSigners ==========

    private suspend fun multiSignerFailure(script: (KeyPair) -> List<String>): Pair<TransactionException.SimulationFailed, ScriptedRpc> {
        val deployer = KeyPair.random()
        val rpc = ScriptedRpc(script(deployer).map { RpcReply(it) })
        val kit = kit(rpc, deployer)
        val publicKey = kit.externalSigners.addEd25519FromRawKey(Random.nextBytes(32), VERIFIER_A)
        kit.setConnectedState("test-credential-id", VERIFIER_B)
        val failure = assertFailsWith<TransactionException.SimulationFailed> {
            kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = stubHostFunction(VERIFIER_B),
                selectedSigners = listOf(SelectedSigner.Ed25519(verifierAddress = VERIFIER_A, publicKey = publicKey)),
                resolveContextRuleIds = { _, _ -> emptyList() }
            )
        }
        return failure to rpc
    }

    @Test
    fun submitWithMultipleSigners_maskedInitialSimulation_carriesTheReportedContractError() = runTest {
        val (failure, rpc) = multiSignerFailure { deployer ->
            listOf(account(deployer), SimulationFixtures.body(CASE_B1))
        }

        val simulation = capture(CASE_B1)
        assertCarries(
            failure,
            "Transaction simulation failed: Simulation error: ${simulation.error}" + suffix(3114u, CASE_B1_CONTRACT),
            simulation.events!!,
            ReportedContractError(3114u, DIAGNOSTIC_EVENTS, CASE_B1_CONTRACT)
        )
        assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
    }

    @Test
    fun submitWithMultipleSigners_maskedReSimulation_carriesTheSignerError() = runTest {
        for (rpc30_0_0 in listOf(false, true)) {
            val (failure, rpc) = multiSignerFailure { deployer ->
                listOf(
                    account(deployer),
                    simulateWithAuthResponseJson(buildAuthEntry(VERIFIER_B).toXdrBase64(), sorobanData),
                    latestLedgerResponseJson(),
                    account(deployer),
                    simulateCountResponseJson(SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64(), sorobanData),
                    account(deployer),
                    SimulationFixtures.body(CASE_B1, rpc30_0_0)
                )
            }

            val simulation = capture(CASE_B1, rpc30_0_0)
            assertCarries(
                failure,
                "Transaction simulation failed: Re-simulation error: ${simulation.error}" +
                    suffix(3114u, CASE_B1_CONTRACT),
                simulation.events!!,
                ReportedContractError(3114u, DIAGNOSTIC_EVENTS, CASE_B1_CONTRACT)
            )
            assertEquals(0, rpc.count("sendTransaction"))
            assertEquals(7, rpc.methods.size)
        }
    }

    // ========== SimulationFailed and the factory ==========

    @Test
    fun simulationFailed_constructorsAndFactoryWithoutEvents_carryNoData() {
        val cause = IllegalStateException("boom")
        val created = listOf(
            TransactionException.SimulationFailed("m") to "m",
            TransactionException.SimulationFailed("m", cause) to "m",
            TransactionException.simulationFailed("r") to "Transaction simulation failed: r",
            TransactionException.simulationFailed("r", cause) to "Transaction simulation failed: r"
        )
        for ((failure, message) in created) {
            assertEquals(message, failure.message)
            assertEquals(emptyList(), failure.diagnosticEventsXdr)
            assertNull(failure.reportedContractError)
        }
        assertSame(cause, created[1].first.cause)
        assertSame(cause, created[3].first.cause)
    }

    @Test
    fun simulationFailed_factoryWithData_appendsTheSuffixOnlyForEventProvenance() {
        val events = listOf(contractErrorEvent(3016u))
        val fromEvents = TransactionException.simulationFailed(
            "Simulation error: $MASKED_AUTH",
            events,
            ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CONTRACT_ONE)
        )
        assertEquals(
            "Transaction simulation failed: Simulation error: $MASKED_AUTH" + suffix(3016u, CONTRACT_ONE),
            fromEvents.message
        )
        val fromHeader = TransactionException.simulationFailed(
            "Simulation error: HostError: Error(Contract, #8)",
            events,
            ReportedContractError(8u, HOST_ERROR, null)
        )
        assertEquals("Transaction simulation failed: Simulation error: HostError: Error(Contract, #8)", fromHeader.message)
        val withoutReport = TransactionException.simulationFailed("Simulation error: x", events, null)
        assertEquals("Transaction simulation failed: Simulation error: x", withoutReport.message)
        assertEquals(TransactionException.SimulationFailed::class, fromEvents::class)
        assertEquals(SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED, fromEvents.code)
        assertNull(fromEvents.cause)

        val cause = IllegalStateException("transport")
        val reported = ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CONTRACT_ONE)
        val withCause = TransactionException.simulationFailed("Simulation error: $MASKED_AUTH", events, reported, cause)
        assertSame(cause, withCause.cause)
        assertEquals(events, withCause.diagnosticEventsXdr)
        assertEquals(reported, withCause.reportedContractError)
        assertSame(cause, TransactionException.SimulationFailed("m", events, reported, cause).cause)
    }

    @Test
    fun simulationFailed_keepsAnImmutableCopyOfTheEvents() {
        val reported = ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CONTRACT_ONE)
        val source = mutableListOf(contractErrorEvent(3016u), "AAAA")
        val expected = source.toList()
        val failures = listOf(
            TransactionException.simulationFailed("r", source, reported),
            TransactionException.SimulationFailed("m", source, reported)
        )

        source.add("added")
        source.clear()

        for (failure in failures) {
            assertEquals(expected, failure.diagnosticEventsXdr)
            assertFalse(failure.diagnosticEventsXdr is MutableList<*>)
            assertEquals(reported, failure.reportedContractError)
        }
        assertEquals("m", failures[1].message, "the constructor stores the message as given")
    }
}
