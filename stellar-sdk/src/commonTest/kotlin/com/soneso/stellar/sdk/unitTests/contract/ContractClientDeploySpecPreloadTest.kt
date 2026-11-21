package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.SorobanContractParserException
import com.soneso.stellar.sdk.contract.SorobanServerFactory
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.unitTests.rpc.responses.ScriptedRpc
import com.soneso.stellar.sdk.unitTests.smartaccount.contractInstanceEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.emptyLedgerEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.ledgerEntriesResponseJson
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.ContractCodeEntryExtXdr
import com.soneso.stellar.sdk.xdr.ContractCodeEntryXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The spec preload of [ContractClient.deployFromWasmId] and [ContractClient.deploy], over scripts that serve the
 * same code on every read through the [SorobanServerFactory] in the coroutine context.
 */
class ContractClientDeploySpecPreloadTest {

    private lateinit var signer: KeyPair
    private val source: String get() = signer.getAccountId()

    /** Runs [body] under `runTest` with a fresh [signer]. */
    private fun test(body: suspend () -> Unit): TestResult = runTest {
        signer = KeyPair.random()
        body()
    }

    private val wasmHash = ByteArray(32) { 3 }
    private val createdContractHash = ByteArray(32) { (it + 1).toByte() }
    private val createdContractId = StrKey.encodeContract(createdContractHash)
    private val txHash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"

    private fun rpcResult(result: String) = """{"jsonrpc":"2.0","id":"1","result":$result}"""

    private fun codeEntry(code: ByteArray) = ledgerEntriesResponseJson(
        LedgerEntryDataXdr.ContractCode(ContractCodeEntryXdr(ContractCodeEntryExtXdr.Void, HashXdr(wasmHash), code))
            .toXdrBase64()
    )

    /** A transaction: the account fetch, the simulation, the submission and its result [returnValue]. */
    private fun transaction(returnValue: SCValXdr) = listOf(
        rpcResult(ledgerEntriesResultJson(source)),
        rpcResult(simulateResultJson(Scv.toVoid())),
        rpcResult("""{"status":"PENDING","hash":"$txHash","latestLedger":14245}"""),
        rpcResult(
            """{"status":"SUCCESS","txHash":"$txHash","latestLedger":14246,""" +
                """"resultMetaXdr":"${successResultMetaBase64(returnValue)}"}"""
        )
    )

    /** The deployment, then the instance and code reads of [ContractClient.forContract] for the created contract. */
    private fun deploymentAndForContract(code: ByteArray) =
        transaction(SCValXdr.Address(SCAddressXdr.ContractId(ContractIDXdr(HashXdr(createdContractHash))))) + listOf(
            contractInstanceEntriesResponseJson(createdContractId, ContractExecutableXdr.WasmHash(HashXdr(wasmHash))),
            codeEntry(code)
        )

    private fun rpcOf(script: List<String>) = ScriptedRpc(*script.toTypedArray())

    private suspend fun <T> withRpc(rpc: ScriptedRpc, block: suspend () -> T): T =
        withContext(SorobanServerFactory { url -> rpc.server(url) }) { block() }

    private suspend fun deployFromWasmId(): ContractClient = ContractClient.deployFromWasmId(
        wasmId = "03".repeat(32), source = source, signer = signer, network = Network.TESTNET,
        rpcUrl = MOCK_RPC_SERVER_URL, loadSpec = true
    )

    /** Uploads [code] and deploys it under the wasm id the script answers the upload with. */
    private suspend fun deploy(code: ByteArray, constructorArgs: Map<String, Any?>, loadSpec: Boolean) =
        ContractClient.deploy(
            code, constructorArgs, source, signer, Network.TESTNET, MOCK_RPC_SERVER_URL, loadSpec = loadSpec
        )

    private val upload get() = transaction(SCValXdr.Bytes(SCBytesXdr(wasmHash)))
    private val transactionMethods =
        listOf("getLedgerEntries", "simulateTransaction", "sendTransaction", "getTransaction")

    @Test
    fun deployFromWasmId_codeTheParserRejects_failsBeforeTheDeploymentIsSubmitted() = test {
        val rejected = undecodableSpecModule()
        val rpc = rpcOf(listOf(codeEntry(rejected)) + deploymentAndForContract(rejected))

        val failure = assertFailsWith<SorobanContractParserException> { withRpc(rpc) { deployFromWasmId() } }

        assertEquals(UNDECODABLE_SPEC_ERROR, failure.message)
        assertEquals(listOf("getLedgerEntries"), rpc.methods)
    }

    @Test
    fun deploy_codeTheParserRejects_failsAfterTheUploadBeforeTheDeploymentIsSubmitted() = test {
        val rejected = undecodableSpecModule()
        val rpc = rpcOf(upload + codeEntry(rejected) + deploymentAndForContract(rejected))

        val failure = assertFailsWith<SorobanContractParserException> {
            withRpc(rpc) { deploy(rejected, emptyMap(), loadSpec = true) }
        }

        assertEquals(UNDECODABLE_SPEC_ERROR, failure.message)
        assertEquals(transactionMethods + "getLedgerEntries", rpc.methods)
    }

    @Test
    fun deploy_constructorArgumentsWithoutLoadSpec_codeTheParserRejectsFailsBeforeTheDeployment() = test {
        val rejected = undecodableSpecModule()
        val rpc = rpcOf(upload + codeEntry(rejected) + deploymentAndForContract(rejected))

        val failure = assertFailsWith<SorobanContractParserException> {
            withRpc(rpc) { deploy(rejected, mapOf("admin" to source), loadSpec = false) }
        }

        assertEquals(UNDECODABLE_SPEC_ERROR, failure.message)
        assertEquals(transactionMethods + "getLedgerEntries", rpc.methods)
    }

    @Test
    fun deployFromWasmId_noCodeEntryBeforeTheDeployment_loadsTheSpecThroughForContract() = test {
        val hello = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
        val rpc = rpcOf(listOf(emptyLedgerEntriesResponseJson()) + deploymentAndForContract(hello))

        val client = withRpc(rpc) { deployFromWasmId() }

        assertEquals(createdContractId, client.contractId)
        assertTrue("hello" in client.getMethodNames())
        assertEquals(listOf("getLedgerEntries") + transactionMethods + List(2) { "getLedgerEntries" }, rpc.methods)
    }
}
