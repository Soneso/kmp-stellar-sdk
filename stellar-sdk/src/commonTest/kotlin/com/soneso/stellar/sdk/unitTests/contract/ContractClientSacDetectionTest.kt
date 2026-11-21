//
//  ContractClientSacDetectionTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.SACClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.unitTests.rpc.responses.RpcReply
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.ContractCodeEntryExtXdr
import com.soneso.stellar.sdk.xdr.ContractCodeEntryXdr
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractDataEntryXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableExternalRefXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ExtensionPointXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractCodeXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCContractInstanceXdr
import com.soneso.stellar.sdk.xdr.SCSpecEntryXdr
import com.soneso.stellar.sdk.xdr.SCSpecFunctionV0Xdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Tests for the Stellar Asset Contract detection of [ContractClient.forContract], driven
 * through [ContractClient.forContractInternal] with a MockEngine server, and for
 * [ContractClient.forSpecEntries]. The mock answers each getLedgerEntries request from the
 * request's index and the ledger keys it carries, and records those keys, so each test pins the
 * complete request sequence; a request of any other method throws in the mock and fails the test.
 */
class ContractClientSacDetectionTest {

    /** The XLM Stellar Asset Contract on testnet. */
    private val sacId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val wasmContractId = MOCK_RPC_CONTRACT_ID
    private val ownerContractId = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
    private val executableTag = "token-v1".encodeToByteArray()
    private val wasmHash = ByteArray(32) { 3 }
    private val helloWasm = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")

    private val wasmExecutable = ContractExecutableXdr.WasmHash(HashXdr(wasmHash))

    private val externalRefExecutable = ContractExecutableXdr.ExternalRef(
        ContractExecutableExternalRefXdr(executableOwner = Address(ownerContractId).toSCAddress(), tag = executableTag)
    )

    private fun specNotFound(contractId: String) =
        "Contract spec not found for contract $contractId. Ensure the contract is deployed and has a valid spec."

    /** A recorded getLedgerEntries request for the one ledger key [key]: its list of keys. */
    private fun ledgerRequest(key: String): List<String> = listOf(key)

    private fun instanceKeyB64(contractId: String): String = LedgerKeyXdr.ContractData(
        LedgerKeyContractDataXdr(
            contract = Address(contractId).toSCAddress(),
            key = Scv.toLedgerKeyContractInstance(),
            durability = ContractDataDurabilityXdr.PERSISTENT
        )
    ).toXdrBase64()

    private fun contractDataB64(contractId: String, key: SCValXdr, value: SCValXdr): String =
        LedgerEntryDataXdr.ContractData(
            ContractDataEntryXdr(
                ext = ExtensionPointXdr.Void,
                contract = Address(contractId).toSCAddress(),
                key = key,
                durability = ContractDataDurabilityXdr.PERSISTENT,
                `val` = value
            )
        ).toXdrBase64()

    private fun instanceEntryB64(contractId: String, executable: ContractExecutableXdr): String = contractDataB64(
        contractId,
        Scv.toLedgerKeyContractInstance(),
        SCValXdr.Instance(SCContractInstanceXdr(executable = executable, storage = null))
    )

    private val codeKeyB64: String =
        LedgerKeyXdr.ContractCode(LedgerKeyContractCodeXdr(hash = HashXdr(wasmHash))).toXdrBase64()

    private fun codeEntryB64(code: ByteArray): String = LedgerEntryDataXdr.ContractCode(
        ContractCodeEntryXdr(ext = ContractCodeEntryExtXdr.Void, hash = HashXdr(wasmHash), code = code)
    ).toXdrBase64()

    private val tagKeyB64: String = LedgerKeyXdr.ContractData(
        LedgerKeyContractDataXdr(
            contract = Address(ownerContractId).toSCAddress(),
            key = SCValXdr.ExecutableTag(executableTag),
            durability = ContractDataDurabilityXdr.PERSISTENT
        )
    ).toXdrBase64()

    private val tagEntryB64: String =
        contractDataB64(ownerContractId, SCValXdr.ExecutableTag(executableTag), SCValXdr.Bytes(SCBytesXdr(wasmHash)))

    /**
     * A [SorobanServer] over a MockEngine that serves getLedgerEntries only. [entryFor] maps the
     * index of the request and the requested ledger key to the base64 ledger entry data it
     * returns, or to null for no entry. The request at [rpcErrorAt] gets a JSON-RPC error.
     * The ledger keys of every getLedgerEntries request are appended to [requests]; a request of
     * any other method throws in the mock and fails the test.
     */
    private fun ledgerServer(
        requests: MutableList<List<String>>,
        rpcErrorAt: Int? = null,
        entryFor: (index: Int, key: String) -> String?
    ): SorobanServer {
        val client = jsonRpcClient { body ->
            val method = body["method"]!!.jsonPrimitive.content
            check(method == "getLedgerEntries") { "unexpected JSON-RPC method: $method" }
            val keys = body["params"]!!.jsonObject["keys"]!!.jsonArray.map { it.jsonPrimitive.content }
            val index = requests.size
            requests.add(keys)
            if (index == rpcErrorAt) {
                RpcReply(
                    """{ "jsonrpc": "2.0", "id": "1", "error": { "code": -32603, "message": "ledger read failed" } }"""
                )
            } else {
                val entries = keys.mapNotNull { key ->
                    entryFor(index, key)?.let { """{ "key": "$key", "xdr": "$it", "lastModifiedLedgerSeq": 1 }""" }
                }
                RpcReply(
                    """{ "jsonrpc": "2.0", "id": "1", "result": """ +
                        """{ "entries": [ ${entries.joinToString(",")} ], "latestLedger": 14245 } }"""
                )
            }
        }
        return SorobanServer(MOCK_RPC_SERVER_URL, client)
    }

    /**
     * Opens [contractId] through [ContractClient.forContractInternal] and returns its method names.
     * The spec loads through [server]; the client builds its own server for the refusing URL, so a
     * request through it fails offline.
     */
    private suspend fun methodNamesOf(contractId: String, server: SorobanServer): Set<String> {
        val client = ContractClient.forContractInternal(contractId, REFUSING_RPC_URL, Network.TESTNET, server)
        assertEquals(contractId, client.contractId)
        val names = client.getMethodNames()
        client.close()
        server.close()
        return names
    }

    /** The [IllegalStateException] that opening [contractId] throws. */
    private suspend fun failureOf(contractId: String, server: SorobanServer): IllegalStateException {
        val failure = assertFailsWith<IllegalStateException> {
            ContractClient.forContractInternal(contractId, REFUSING_RPC_URL, Network.TESTNET, server)
        }
        server.close()
        return failure
    }

    // ==================== Contracts that open ====================

    @Test
    fun testStellarAssetContractOpensWithTheEmbeddedSpec() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(sacId)
        val sacInstance = instanceEntryB64(sacId, ContractExecutableXdr.Void)
        val server = ledgerServer(requests) { _, key ->
            check(key == instanceKey) { "unexpected ledger key: $key" }
            sacInstance
        }

        val names = methodNamesOf(sacId, server)

        // The loader reads the instance and stops at the asset executable; the detection
        // reads the instance once more and needs no code entry.
        assertEquals(listOf(ledgerRequest(instanceKey), ledgerRequest(instanceKey)), requests)
        assertEquals(SAC_FUNCTION_NAMES.toSet(), names)
    }

    @Test
    fun testWasmContractLoadsItsSpecFromTheCode() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(wasmContractId)
        val server = ledgerServer(requests) { _, key ->
            when (key) {
                instanceKey -> instanceEntryB64(wasmContractId, wasmExecutable)
                codeKeyB64 -> codeEntryB64(helloWasm)
                else -> error("unexpected ledger key: $key")
            }
        }

        val names = methodNamesOf(wasmContractId, server)

        assertEquals(listOf(ledgerRequest(instanceKey), ledgerRequest(codeKeyB64)), requests)
        assertEquals(setOf("hello"), names)
    }

    @Test
    fun testExternalReferenceContractLoadsItsSpecFromTheResolvedCode() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(wasmContractId)
        val server = ledgerServer(requests) { _, key ->
            when (key) {
                instanceKey -> instanceEntryB64(wasmContractId, externalRefExecutable)
                tagKeyB64 -> tagEntryB64
                codeKeyB64 -> codeEntryB64(helloWasm)
                else -> error("unexpected ledger key: $key")
            }
        }

        val names = methodNamesOf(wasmContractId, server)

        assertEquals(
            listOf(ledgerRequest(instanceKey), ledgerRequest(tagKeyB64), ledgerRequest(codeKeyB64)),
            requests
        )
        assertEquals(setOf("hello"), names)
    }

    // ==================== Contracts that do not open ====================

    @Test
    fun testMissingInstanceReadsTheInstanceTwiceAndThrowsSpecNotFound() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(wasmContractId)
        val server = ledgerServer(requests) { _, _ -> null }

        val failure = failureOf(wasmContractId, server)

        assertEquals(specNotFound(wasmContractId), failure.message)
        assertEquals(listOf(ledgerRequest(instanceKey), ledgerRequest(instanceKey)), requests)
    }

    @Test
    fun testWasmWithoutItsCodeEntryReadsTheInstanceAgainAndThrowsSpecNotFound() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(wasmContractId)
        val server = ledgerServer(requests) { _, key ->
            if (key == instanceKey) instanceEntryB64(wasmContractId, wasmExecutable) else null
        }

        val failure = failureOf(wasmContractId, server)

        assertEquals(specNotFound(wasmContractId), failure.message)
        assertEquals(
            listOf(ledgerRequest(instanceKey), ledgerRequest(codeKeyB64), ledgerRequest(instanceKey)),
            requests
        )
    }

    @Test
    fun testExternalReferenceWithoutItsCodeEntryReadsTheInstanceAgainAndThrowsSpecNotFound() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(wasmContractId)
        val server = ledgerServer(requests) { _, key ->
            when (key) {
                instanceKey -> instanceEntryB64(wasmContractId, externalRefExecutable)
                tagKeyB64 -> tagEntryB64
                else -> null
            }
        }

        val failure = failureOf(wasmContractId, server)

        assertEquals(specNotFound(wasmContractId), failure.message)
        // The loader reads the instance, resolves the tag, and finds no code entry; the
        // detection then reads the instance once more and finds no asset executable.
        assertEquals(
            listOf(
                ledgerRequest(instanceKey),
                ledgerRequest(tagKeyB64),
                ledgerRequest(codeKeyB64),
                ledgerRequest(instanceKey)
            ),
            requests
        )
    }

    @Test
    fun testCodeWithUndecodableSpecEntryThrowsTheParserErrorWithoutAFurtherRequest() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(wasmContractId)
        val server = ledgerServer(requests) { _, key ->
            when (key) {
                instanceKey -> instanceEntryB64(wasmContractId, wasmExecutable)
                codeKeyB64 -> codeEntryB64(undecodableSpecModule())
                else -> error("unexpected ledger key: $key")
            }
        }

        val failure = failureOf(wasmContractId, server)

        assertEquals("Failed to load contract spec for $wasmContractId: $UNDECODABLE_SPEC_ERROR", failure.message)
        assertEquals(listOf(ledgerRequest(instanceKey), ledgerRequest(codeKeyB64)), requests)
    }

    @Test
    fun testDetectionReadOfAnAccountEntryThrowsSpecNotFound() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(sacId)
        val server = ledgerServer(requests) { index, _ -> if (index == 0) null else accountEntryBase64() }

        val failure = failureOf(sacId, server)

        assertEquals(specNotFound(sacId), failure.message)
        assertEquals(listOf(ledgerRequest(instanceKey), ledgerRequest(instanceKey)), requests)
    }

    @Test
    fun testDetectionReadOfContractDataWithoutAnInstanceThrowsSpecNotFound() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(sacId)
        val server = ledgerServer(requests) { index, _ ->
            if (index == 0) null else contractDataB64(sacId, Scv.toLedgerKeyContractInstance(), Scv.toUint32(1u))
        }

        val failure = failureOf(sacId, server)

        assertEquals(specNotFound(sacId), failure.message)
        assertEquals(listOf(ledgerRequest(instanceKey), ledgerRequest(instanceKey)), requests)
    }

    @Test
    fun testJsonRpcErrorOnTheDetectionReadIsWrapped() = runTest {
        val requests = mutableListOf<List<String>>()
        val instanceKey = instanceKeyB64(sacId)
        val server = ledgerServer(requests, rpcErrorAt = 1) { _, _ -> null }

        val failure = failureOf(sacId, server)

        assertEquals(
            "Failed to load contract spec for $sacId: Soroban RPC error (-32603): ledger read failed",
            failure.message
        )
        assertIs<SorobanRpcException>(failure.cause)
        assertEquals(listOf(ledgerRequest(instanceKey), ledgerRequest(instanceKey)), requests)
    }

    // ==================== forSpecEntries ====================

    @Test
    fun testForSpecEntriesSendsNoRequestAndCarriesTheGivenEntries() = runTest {
        val requests = mutableListOf<List<String>>()
        val server = ledgerServer(requests) { _, key -> error("unexpected ledger key: $key") }
        val entries = listOf(
            SCSpecEntryXdr.FunctionV0(
                SCSpecFunctionV0Xdr(doc = "", name = SCSymbolXdr("probe"), inputs = emptyList(), outputs = emptyList())
            )
        )

        val client = ContractClient.forSpecEntries(
            wasmContractId, REFUSING_RPC_URL, Network.TESTNET, entries, readOnly = false, server = server
        )

        assertEquals(wasmContractId, client.contractId)
        assertEquals(REFUSING_RPC_URL, client.rpcUrl)
        assertEquals(Network.TESTNET, client.network)
        assertEquals(setOf("probe"), client.getMethodNames())
        assertEquals(emptyList(), requests)
        client.close()
    }

    @Test
    fun testForSpecEntriesDefaultsToAServerForTheRpcUrl() = runTest {
        // The default server has a real engine; a request to the refusing URL would fail.
        val client = ContractClient.forSpecEntries(
            wasmContractId, REFUSING_RPC_URL, Network.TESTNET, SACClient.specEntries(), readOnly = true
        )

        assertEquals(wasmContractId, client.contractId)
        assertEquals(REFUSING_RPC_URL, client.rpcUrl)
        assertEquals(SAC_FUNCTION_NAMES.toSet(), client.getMethodNames())
        client.close()
    }

    @Test
    fun testForSpecEntriesRejectsAMuxedContractId() = runTest {
        val requests = mutableListOf<List<String>>()
        val server = ledgerServer(requests) { _, key -> error("unexpected ledger key: $key") }

        val failure = assertFailsWith<IllegalArgumentException> {
            ContractClient.forSpecEntries(
                MOCK_RPC_MUXED_CONTRACT_ID, REFUSING_RPC_URL, Network.TESTNET, emptyList(), readOnly = true, server = server
            )
        }

        assertEquals(
            "The contract to invoke must be a contract (C...) address, got a muxed contract (W...) address: " +
                "WA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAAAAAAAAAPCIA6IG; use its base contract (C...) address",
            failure.message
        )
        assertEquals(emptyList(), requests)
        server.close()
    }
}
