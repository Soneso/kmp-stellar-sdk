package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.SorobanServerFactory
import com.soneso.stellar.sdk.contract.sorobanServerFor
import com.soneso.stellar.sdk.contract.exception.ContractClientSimulationException
import com.soneso.stellar.sdk.contract.exception.RestorationFailureException
import com.soneso.stellar.sdk.contract.exception.SimulationFailedException
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.exception.SimulationFailureDetails
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError.Provenance.DIAGNOSTIC_EVENTS
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.unitTests.rpc.responses.RpcReply
import com.soneso.stellar.sdk.unitTests.rpc.responses.ScriptedRpc
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_A
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_A_CONTRACT
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CONTRACT_ONE
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CONTRACT_TWO
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.MASKED_AUTH
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.assertCarries
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.contractErrorEvent
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.simulateErrorBody
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.suffix
import com.soneso.stellar.sdk.unitTests.smartaccount.contractInstanceEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.ContractCodeEntryExtXdr
import com.soneso.stellar.sdk.xdr.ContractCodeEntryXdr
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractDataEntryXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.ExtensionPointXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The simulation data that the public [ContractClient] entries carry when a simulation fails:
 * the deploy paths throw [ContractClientSimulationException], an `IllegalStateException`, and
 * `invoke` and `buildInvoke` throw [SimulationFailedException]. Each message is the path's failure
 * text, followed by the suffix when the contract error was found in the diagnostic events. The
 * servers the entries create come from the [SorobanServerFactory] in the coroutine context.
 */
class SimulationFailureDataTest {

    private lateinit var signer: KeyPair
    private val source: String get() = signer.getAccountId()

    /** Runs [body] under `runTest` with a fresh [signer]. */
    private fun test(body: suspend () -> Unit): TestResult = runTest {
        signer = KeyPair.random()
        body()
    }

    private val wasmHash = ByteArray(32) { 3 }
    private val wasmId = wasmHash.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    private val createdContractHash = ByteArray(32) { (it + 1).toByte() }
    private val createdContractId = StrKey.encodeContract(createdContractHash)
    private val wasmBytes = byteArrayOf(0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00)
    private val ownerContractId = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
    private val tag = "token-v1"
    private val txHash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"

    private fun rpcResult(result: String) = """{"jsonrpc":"2.0","id":"1","result":$result}"""

    private val account get() = rpcResult(ledgerEntriesResultJson(source))
    private val simulationSuccess get() = rpcResult(simulateResultJson(Scv.toVoid()))
    private val sendPending get() = rpcResult("""{"status":"PENDING","hash":"$txHash","latestLedger":14245}""")

    private fun transactionSuccess(returnValue: SCValXdr) = rpcResult(
        """{"status":"SUCCESS","txHash":"$txHash","latestLedger":14246,""" +
            """"resultMetaXdr":"${successResultMetaBase64(returnValue)}"}"""
    )

    private val createdContract: SCValXdr
        get() = SCValXdr.Address(SCAddressXdr.ContractId(ContractIDXdr(HashXdr(createdContractHash))))

    private val tagEntry: String
        get() {
            val entry = ContractDataEntryXdr(
                ext = ExtensionPointXdr.Void,
                contract = Address(ownerContractId).toSCAddress(),
                key = SCValXdr.ExecutableTag(tag.encodeToByteArray()),
                durability = ContractDataDurabilityXdr.PERSISTENT,
                `val` = SCValXdr.Bytes(SCBytesXdr(wasmHash))
            )
            val xdr = LedgerEntryDataXdr.ContractData(entry).toXdrBase64()
            return rpcResult("""{"entries":[{"key":"AAAAAA==","xdr":"$xdr","lastModifiedLedgerSeq":1}],"latestLedger":14245}""")
        }

    /** The requests of a successful `deployFromWasmId(loadSpec = false)`. */
    private val deployScript: List<String>
        get() = listOf(account, simulationSuccess, sendPending, transactionSuccess(createdContract))

    private val restoreNeeded: String
        get() {
            val txData = minimalSorobanData().toXdrBase64()
            return rpcResult(
                """{"transactionData":"$txData","minResourceFee":"100",""" +
                    """"results":[{"auth":["${writeAuthEntry().toXdrBase64()}"],"xdr":"${Scv.toVoid().toXdrBase64()}"}],""" +
                    """"restorePreamble":{"transactionData":"$txData","minResourceFee":50},"latestLedger":14245}"""
            )
        }

    private val restoreSimulationSuccess: String
        get() = rpcResult("""{"transactionData":"${minimalSorobanData().toXdrBase64()}","minResourceFee":"100","latestLedger":14245}""")

    private val caseA = SimulationFixtures.response(CASE_A)
    private val caseAReported = ReportedContractError(3201u, DIAGNOSTIC_EVENTS, CASE_A_CONTRACT)

    private suspend fun <T> withRpc(rpc: ScriptedRpc, block: suspend () -> T): T =
        withContext(SorobanServerFactory { url -> rpc.server(url) }) { block() }

    private suspend fun deployedClient(): ContractClient = ContractClient.deployFromWasmId(
        wasmId = wasmId,
        source = source,
        signer = signer,
        network = Network.TESTNET,
        rpcUrl = MOCK_RPC_SERVER_URL,
        loadSpec = false
    )

    private suspend fun invoke(client: ContractClient) {
        client.invoke<SCValXdr>("hello", emptyList<SCValXdr>(), source, signer)
    }

    private suspend fun buildInvoke(client: ContractClient) {
        client.buildInvoke<SCValXdr>("hello", emptyList<SCValXdr>(), source, signer)
    }

    // ========== Deploy paths ==========

    @Test
    fun install_maskedSimulation_throwsAnIllegalStateExceptionWithData() = test {
        val events = listOf(contractErrorEvent(3016u, CONTRACT_ONE))
        val rpc = ScriptedRpc(account, simulateErrorBody(MASKED_AUTH, events))

        val failure = assertFailsWith<IllegalStateException> {
            withRpc(rpc) { ContractClient.install(wasmBytes, source, signer, Network.TESTNET, MOCK_RPC_SERVER_URL) }
        }

        val simulationFailure = assertIs<ContractClientSimulationException>(failure)
        assertCarries(
            simulationFailure,
            "WASM upload simulation failed: $MASKED_AUTH" + suffix(3016u, CONTRACT_ONE),
            events,
            ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CONTRACT_ONE)
        )
        assertEquals(MASKED_AUTH, simulationFailure.simulationError)
        assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
    }

    @Test
    fun deploy_maskedUploadSimulation_throwsAnIllegalStateExceptionWithData() = test {
        val events = listOf(contractErrorEvent(3016u, CONTRACT_ONE))
        val rpc = ScriptedRpc(account, simulateErrorBody(MASKED_AUTH, events))

        val failure = assertFailsWith<IllegalStateException> {
            withRpc(rpc) {
                ContractClient.deploy(
                    wasmBytes = wasmBytes,
                    source = source,
                    signer = signer,
                    network = Network.TESTNET,
                    rpcUrl = MOCK_RPC_SERVER_URL,
                    loadSpec = false
                )
            }
        }

        assertCarries(
            assertIs<ContractClientSimulationException>(failure),
            "WASM upload simulation failed: $MASKED_AUTH" + suffix(3016u, CONTRACT_ONE),
            events,
            ReportedContractError(3016u, DIAGNOSTIC_EVENTS, CONTRACT_ONE)
        )
        assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
    }

    @Test
    fun deploy_maskedDeploySimulation_throwsAnIllegalStateExceptionWithData() = test {
        val rpc = ScriptedRpc(
            account,
            simulationSuccess,
            sendPending,
            transactionSuccess(SCValXdr.Bytes(SCBytesXdr(wasmHash))),
            account,
            SimulationFixtures.body(CASE_A)
        )

        val failure = assertFailsWith<IllegalStateException> {
            withRpc(rpc) {
                ContractClient.deploy(
                    wasmBytes = wasmBytes,
                    source = source,
                    signer = signer,
                    network = Network.TESTNET,
                    rpcUrl = MOCK_RPC_SERVER_URL,
                    loadSpec = false
                )
            }
        }

        val simulationFailure = assertIs<ContractClientSimulationException>(failure)
        assertCarries(
            simulationFailure,
            "Contract deployment simulation failed: ${caseA.error}" + suffix(3201u, CASE_A_CONTRACT),
            caseA.events!!,
            caseAReported
        )
        assertEquals(caseA.error, simulationFailure.simulationError)
        assertEquals(
            listOf(
                "getLedgerEntries", "simulateTransaction", "sendTransaction", "getTransaction",
                "getLedgerEntries", "simulateTransaction"
            ),
            rpc.methods
        )
    }

    @Test
    fun deployFromWasmId_maskedSimulation_throwsAnIllegalStateExceptionWithData() = test {
        val rpc = ScriptedRpc(account, SimulationFixtures.body(CASE_A))

        val failure = assertFailsWith<IllegalStateException> { withRpc(rpc) { deployedClient() } }

        assertCarries(
            assertIs<ContractClientSimulationException>(failure),
            "Contract deployment simulation failed: ${caseA.error}" + suffix(3201u, CASE_A_CONTRACT),
            caseA.events!!,
            caseAReported
        )
        assertEquals(listOf("getLedgerEntries", "simulateTransaction"), rpc.methods)
    }

    @Test
    fun deployFromExternalRef_bothOverloads_maskedSimulation_throwAnIllegalStateExceptionWithData() = test {
        val deployments: List<suspend () -> ContractClient> = listOf(
            {
                ContractClient.deployFromExternalRef(
                    executableOwner = ownerContractId,
                    tag = tag,
                    source = source,
                    signer = signer,
                    network = Network.TESTNET,
                    rpcUrl = MOCK_RPC_SERVER_URL,
                    loadSpec = false
                )
            },
            {
                ContractClient.deployFromExternalRef(
                    executableOwner = ownerContractId,
                    tag = tag.encodeToByteArray(),
                    source = source,
                    signer = signer,
                    network = Network.TESTNET,
                    rpcUrl = MOCK_RPC_SERVER_URL,
                    loadSpec = false
                )
            }
        )
        for (deployment in deployments) {
            val rpc = ScriptedRpc(tagEntry, account, SimulationFixtures.body(CASE_A))

            val failure = assertFailsWith<IllegalStateException> { withRpc(rpc) { deployment() } }

            assertCarries(
                assertIs<ContractClientSimulationException>(failure),
                "Contract deployment simulation failed: ${caseA.error}" + suffix(3201u, CASE_A_CONTRACT),
                caseA.events!!,
                caseAReported
            )
            assertEquals(listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction"), rpc.methods)
        }
    }

    // ========== invoke and buildInvoke ==========

    private suspend fun invokeFailure(
        simulateReply: RpcReply,
        call: suspend (ContractClient) -> Unit = { invoke(it) }
    ): Pair<Throwable, ScriptedRpc> {
        val script = deployScript.map { RpcReply(it) } + RpcReply(account) + RpcReply(account) + simulateReply
        val rpc = ScriptedRpc(script)
        val failure = withRpc(rpc) {
            val client = deployedClient()
            assertEquals(createdContractId, client.contractId)
            try {
                call(client)
                null
            } catch (e: Throwable) {
                e
            }
        } ?: throw AssertionError("Expected the invocation to fail")
        assertEquals(script.size, rpc.methods.size, "requests: ${rpc.methods}")
        assertEquals(1, rpc.count("sendTransaction"), "only the deployment is sent")
        return failure to rpc
    }

    @Test
    fun invokeAndBuildInvoke_maskedConstructorFailure_carryTheReportedContractError() = test {
        for (call in listOf<suspend (ContractClient) -> Unit>({ invoke(it) }, { buildInvoke(it) })) {
            for (rpc30_0_0 in listOf(false, true)) {
                val simulation = SimulationFixtures.response(CASE_A, rpc30_0_0)
                val (failure, _) = invokeFailure(RpcReply(SimulationFixtures.body(CASE_A, rpc30_0_0)), call)

                assertCarries(
                    assertIs<SimulationFailedException>(failure),
                    "Transaction simulation failed: ${simulation.error}" + suffix(3201u, CASE_A_CONTRACT),
                    simulation.events!!,
                    caseAReported
                )
            }
        }
    }

    @Test
    fun invoke_rpc30_0_0Text_suffixFollowsTheEventLog_andWithoutEventsTheMessageHasNoSuffix() = test {
        val text = SimulationFixtures.response(CASE_A, rpc30_0_0 = true).error!!
        assertTrue(text.endsWith("]\n"))

        val (withEvents, _) = invokeFailure(RpcReply(SimulationFixtures.body(CASE_A, rpc30_0_0 = true)))
        assertEquals(
            "Transaction simulation failed: $text" + suffix(3201u, CASE_A_CONTRACT),
            withEvents.message
        )

        val (withoutEvents, _) = invokeFailure(RpcReply(simulateErrorBody(text, events = null)))
        assertCarries(
            assertIs<SimulationFailedException>(withoutEvents),
            "Transaction simulation failed: $text",
            emptyList(),
            null
        )
    }

    @Test
    fun invoke_twoContractsWithDuplicates_keepsTheServedEvents() = test {
        val events = listOf(
            contractErrorEvent(3114u, CONTRACT_ONE),
            contractErrorEvent(3016u, CONTRACT_TWO),
            contractErrorEvent(3114u, CONTRACT_ONE),
            contractErrorEvent(3016u, CONTRACT_TWO)
        )

        val (failure, _) = invokeFailure(RpcReply(simulateErrorBody(MASKED_AUTH, events)))

        val simulationFailure = assertIs<SimulationFailedException>(failure)
        assertEquals(events, simulationFailure.diagnosticEventsXdr)
        assertEquals(ReportedContractError(3114u, DIAGNOSTIC_EVENTS, CONTRACT_ONE), simulationFailure.reportedContractError)
    }

    @Test
    fun invoke_rpcFailures_propagateWithoutSimulationData() = test {
        val (rpcError, _) = invokeFailure(
            RpcReply("""{"jsonrpc":"2.0","id":"1","error":{"code":-32602,"message":"invalid params"}}""")
        )
        assertEquals(-32602, assertIs<SorobanRpcException>(rpcError).errorCode)

        val (transport, _) = invokeFailure(RpcReply("upstream unavailable", HttpStatusCode.InternalServerError))
        assertIs<IllegalArgumentException>(transport)
        assertTrue(transport.message!!.startsWith("Failed to parse response for method simulateTransaction"))
        assertFalse(transport is SimulationFailureDetails)

        val (missing, _) = invokeFailure(RpcReply("""{"jsonrpc":"2.0","id":"1"}"""))
        assertIs<IllegalStateException>(missing)
        assertTrue(missing.message!!.startsWith("Response missing result field (method: simulateTransaction"))
    }

    // ========== Spec-loading entries ==========

    private val codeEntry: String
        get() {
            val code = ContractCodeEntryXdr(
                ext = ContractCodeEntryExtXdr.Void,
                hash = HashXdr(wasmHash),
                code = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
            )
            return ledgerEntriesResponseJson(LedgerEntryDataXdr.ContractCode(code).toXdrBase64())
        }

    @Test
    fun specLoadingEntries_requestOnlyThroughTheFactory() = test {
        val forContract = ScriptedRpc(
            contractInstanceEntriesResponseJson(createdContractId),
            codeEntry,
            account,
            account,
            SimulationFixtures.body(CASE_A)
        )
        val failure = assertFailsWith<SimulationFailedException> {
            withRpc(forContract) {
                val client = ContractClient.forContract(createdContractId, MOCK_RPC_SERVER_URL, Network.TESTNET)
                assertTrue("hello" in client.getMethodNames())
                invoke(client)
            }
        }
        assertEquals(caseAReported, failure.reportedContractError)
        assertEquals(5, forContract.methods.size, "requests: ${forContract.methods}")

        val fromWasmId = ScriptedRpc(*(listOf(codeEntry) + deployScript).toTypedArray())
        val deployedFromWasmId = withRpc(fromWasmId) {
            ContractClient.deployFromWasmId(
                wasmId = wasmId,
                source = source,
                signer = signer,
                network = Network.TESTNET,
                rpcUrl = MOCK_RPC_SERVER_URL
            )
        }
        assertTrue("hello" in deployedFromWasmId.getMethodNames())
        assertEquals(5, fromWasmId.methods.size, "requests: ${fromWasmId.methods}")

        val deployed = ScriptedRpc(
            *(listOf(account, simulationSuccess, sendPending, transactionSuccess(SCValXdr.Bytes(SCBytesXdr(wasmHash))), codeEntry) +
                deployScript).toTypedArray()
        )
        val client = withRpc(deployed) {
            ContractClient.deploy(
                wasmBytes = wasmBytes,
                source = source,
                signer = signer,
                network = Network.TESTNET,
                rpcUrl = MOCK_RPC_SERVER_URL
            )
        }
        assertEquals(createdContractId, client.contractId)
        assertTrue("hello" in client.getMethodNames())
        assertEquals(9, deployed.methods.size, "requests: ${deployed.methods}")
    }

    @Test
    fun sorobanServerFactory_isScopedToItsCoroutine() = test {
        val urls = mutableListOf<String>()
        val made = mutableListOf<SorobanServer>()
        val rpc = ScriptedRpc()

        val inside = withContext(SorobanServerFactory { url -> rpc.server(url).also { urls += url; made += it } }) {
            sorobanServerFor(MOCK_RPC_SERVER_URL)
        }
        val outside = sorobanServerFor(MOCK_RPC_SERVER_URL)

        assertEquals(listOf(MOCK_RPC_SERVER_URL), urls)
        assertSame(made.single(), inside)
        assertNotSame(inside, outside)
        assertEquals(1, made.size, "a call outside the context does not use the factory")
        inside.close()
        outside.close()
    }

    // ========== Restore through invoke ==========

    @Test
    fun invoke_restoreSimulationFails_restorationFailureCarriesTheSimulationFailure() = test {
        val script = deployScript + listOf(account, account, restoreNeeded, account, SimulationFixtures.body(CASE_A))
        val rpc = ScriptedRpc(*script.toTypedArray())

        val failure = assertFailsWith<RestorationFailureException> {
            withRpc(rpc) { invoke(deployedClient()) }
        }

        assertEquals("Failed to restore contract data.", failure.message)
        assertCarries(
            assertIs<SimulationFailedException>(failure.cause),
            "Transaction simulation failed: ${caseA.error}" + suffix(3201u, CASE_A_CONTRACT),
            caseA.events!!,
            caseAReported
        )
        assertEquals(script.size, rpc.methods.size, "requests: ${rpc.methods}")
        assertEquals(1, rpc.count("sendTransaction"), "the restore is not sent")
    }

    @Test
    fun invoke_restoreSimulationRequestFails_restorationFailureCarriesTheRequestFailure() = test {
        val script = deployScript + listOf(
            account,
            account,
            restoreNeeded,
            account,
            """{"jsonrpc":"2.0","id":"1","error":{"code":-32602,"message":"invalid params"}}"""
        )
        val rpc = ScriptedRpc(*script.toTypedArray())

        val failure = assertFailsWith<RestorationFailureException> {
            withRpc(rpc) { invoke(deployedClient()) }
        }

        val cause = assertIs<SorobanRpcException>(failure.cause)
        assertEquals(-32602, cause.errorCode)
        assertEquals("Failed to restore contract data: ${cause.message}", failure.message)
        assertEquals(script.size, rpc.methods.size, "requests: ${rpc.methods}")
        assertEquals(1, rpc.count("sendTransaction"), "the restore is not sent")
    }

    @Test
    fun invoke_restoreSucceedsAndTheResimulationFails_carriesTheReportedContractError() = test {
        val script = deployScript + listOf(
            account,
            account,
            restoreNeeded,
            account,
            restoreSimulationSuccess,
            sendPending,
            transactionSuccess(Scv.toVoid()),
            account,
            SimulationFixtures.body(CASE_A)
        )
        val rpc = ScriptedRpc(*script.toTypedArray())

        val failure = assertFailsWith<SimulationFailedException> {
            withRpc(rpc) { invoke(deployedClient()) }
        }

        assertCarries(
            failure,
            "Transaction simulation failed: ${caseA.error}" + suffix(3201u, CASE_A_CONTRACT),
            caseA.events!!,
            caseAReported
        )
        assertEquals(script.size, rpc.methods.size, "requests: ${rpc.methods}")
        assertEquals(2, rpc.count("sendTransaction"), "the deployment and the restore are sent, the invocation is not")
    }
}
