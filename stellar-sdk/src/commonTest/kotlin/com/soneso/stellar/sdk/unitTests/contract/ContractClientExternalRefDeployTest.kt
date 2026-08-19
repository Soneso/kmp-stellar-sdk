//
//  ContractClientExternalRefDeployTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.ContractCodeEntryXdr
import com.soneso.stellar.sdk.xdr.ContractCodeEntryExtXdr
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractDataEntryXdr
import com.soneso.stellar.sdk.xdr.ExtensionPointXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractCodeXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.OperationBodyXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCStringXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.xdr.fromXdrBase64
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [ContractClient.deployFromExternalRef], the external-ref create
 * operation builder and [Address.deriveContractId].
 *
 * The deploy flow resolves the CAP-85 external reference before the transaction
 * is built and loads the contract spec from the resolved wasm code entry. All
 * RPC calls are served by a Ktor MockEngine; no network access. The submitted
 * transaction envelope is decoded to verify the external-ref create host
 * function it carries.
 */
class ContractClientExternalRefDeployTest {

    private val ownerContractId = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
    private val executableTag = "token-v1"
    private val wasmHashBytes = ByteArray(32) { 3 }
    private val createdContractHash = ByteArray(32) { (it + 1).toByte() }
    private val createdContractId = StrKey.encodeContract(createdContractHash)
    private val fixedSalt = ByteArray(32) { 0x11 }
    private val helloWasm = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")

    private fun tagKeyB64(): String {
        val key = LedgerKeyXdr.ContractData(
            LedgerKeyContractDataXdr(
                contract = Address(ownerContractId).toSCAddress(),
                key = SCValXdr.ExecutableTag(SCStringXdr(executableTag)),
                durability = ContractDataDurabilityXdr.PERSISTENT
            )
        )
        return key.toXdrBase64()
    }

    private fun codeKeyB64(): String {
        val key = LedgerKeyXdr.ContractCode(
            LedgerKeyContractCodeXdr(hash = HashXdr(wasmHashBytes))
        )
        return key.toXdrBase64()
    }

    private fun tagEntryB64(): String {
        val entry = ContractDataEntryXdr(
            ext = ExtensionPointXdr.Void,
            contract = Address(ownerContractId).toSCAddress(),
            key = SCValXdr.ExecutableTag(SCStringXdr(executableTag)),
            durability = ContractDataDurabilityXdr.PERSISTENT,
            `val` = SCValXdr.Bytes(SCBytesXdr(wasmHashBytes))
        )
        return LedgerEntryDataXdr.ContractData(entry).toXdrBase64()
    }

    private fun codeEntryB64(): String {
        val entry = ContractCodeEntryXdr(
            ext = ContractCodeEntryExtXdr.Void,
            hash = HashXdr(wasmHashBytes),
            code = helloWasm
        )
        return LedgerEntryDataXdr.ContractCode(entry).toXdrBase64()
    }

    private fun entriesResultJson(entryXdrB64: String): String = """
        {
          "entries": [ { "key": "AAAAAA==", "xdr": "$entryXdrB64", "lastModifiedLedgerSeq": 1 } ],
          "latestLedger": 14245
        }
    """.trimIndent()

    private val emptyEntriesResultJson = """{ "entries": [], "latestLedger": 14245 }"""

    /**
     * A [SorobanServer] over a MockEngine serving the full external-ref deploy
     * flow. getLedgerEntries requests are dispatched on the exact ledger key
     * they carry: the owner's executable tag key, the code key under the
     * resolved hash, or the source account fetch. When [tagEntryExists] is
     * false the tag request is answered with no entries. Every request body is
     * appended to [capturedBodies].
     */
    private fun externalRefDeployMockServer(
        sourceAccount: String,
        capturedBodies: MutableList<String>,
        tagEntryExists: Boolean = true
    ): SorobanServer {
        val tagKey = tagKeyB64()
        val codeKey = codeKeyB64()
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            capturedBodies.add(body)
            val resultJson = when {
                "\"getLedgerEntries\"" in body && tagKey in body ->
                    if (tagEntryExists) entriesResultJson(tagEntryB64()) else emptyEntriesResultJson
                "\"getLedgerEntries\"" in body && codeKey in body ->
                    entriesResultJson(codeEntryB64())
                "\"getLedgerEntries\"" in body -> ledgerEntriesResultJson(sourceAccount)
                "\"simulateTransaction\"" in body -> simulateResultJson(Scv.toVoid())
                "\"sendTransaction\"" in body ->
                    """{ "status": "PENDING", "hash": "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304", "latestLedger": 14245 }"""
                "\"getTransaction\"" in body -> """
                    {
                      "status": "SUCCESS",
                      "latestLedger": 14246,
                      "resultMetaXdr": "${successResultMetaBase64(
                          SCValXdr.Address(SCAddressXdr.ContractId(ContractIDXdr(HashXdr(createdContractHash))))
                      )}"
                    }
                """.trimIndent()
                else -> error("unrouted JSON-RPC request: $body")
            }
            respond(
                content = ByteReadChannel("""{ "jsonrpc": "2.0", "id": "1", "result": $resultJson }"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false })
            }
        }
        return SorobanServer(MOCK_RPC_SERVER_URL, client)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun hostFunctionToB64(hostFunction: HostFunctionXdr): String {
        val writer = XdrWriter()
        hostFunction.encode(writer)
        return Base64.encode(writer.toByteArray())
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun hostFunctionFromB64(base64: String): HostFunctionXdr =
        HostFunctionXdr.decode(XdrReader(Base64.decode(base64)))

    /** The host function of the invoke operation in the submitted envelope. */
    private fun submittedHostFunction(capturedBodies: List<String>): HostFunctionXdr {
        val sendBody = capturedBodies.single { "\"sendTransaction\"" in it }
        val envelopeB64 = Json.parseToJsonElement(sendBody)
            .jsonObject["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
        val envelope = TransactionEnvelopeXdr.fromXdrBase64(envelopeB64)
        assertIs<TransactionEnvelopeXdr.V1>(envelope)
        val body = envelope.value.tx.operations.single().body
        assertIs<OperationBodyXdr.InvokeHostFunctionOp>(body)
        return body.value.hostFunction
    }

    private fun methodOf(body: String): String? = when {
        "\"getLedgerEntries\"" in body -> "getLedgerEntries"
        "\"simulateTransaction\"" in body -> "simulate"
        "\"sendTransaction\"" in body -> "send"
        "\"getTransaction\"" in body -> "getTx"
        else -> null
    }

    // ==================== deployFromExternalRef ====================

    @Test
    fun testDeployResolvesAndPinsTheEnvelope() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val captured = mutableListOf<String>()

        val client = ContractClient.deployFromExternalRefInternal(
            executableOwner = ownerContractId,
            tag = executableTag,
            constructorArgs = listOf(Scv.toUint32(7u)),
            source = source,
            signer = signer,
            network = Network.TESTNET,
            rpcUrl = MOCK_RPC_SERVER_URL,
            salt = fixedSalt,
            loadSpec = true,
            server = externalRefDeployMockServer(source, captured)
        )

        assertEquals(createdContractId, client.contractId)
        assertTrue(client.getMethodNames().contains("hello"))

        // The resolution asks for the owner's exact tag key, the preload for the
        // code key under the resolved hash, and both run before submission.
        assertTrue(tagKeyB64() in captured[0])
        assertTrue(codeKeyB64() in captured[1])
        val order = captured.mapNotNull { methodOf(it) }
        assertTrue(order.indexOf("send") > order.lastIndexOf("getLedgerEntries"))

        val hostFunction = submittedHostFunction(captured)
        assertIs<HostFunctionXdr.CreateContractV2>(hostFunction)
        val executable = hostFunction.value.executable
        assertIs<com.soneso.stellar.sdk.xdr.ContractExecutableXdr.ExternalRef>(executable)
        assertEquals(ownerContractId, Address.fromSCAddress(executable.value.executableOwner).getEncodedAddress())
        assertEquals(executableTag, executable.value.tag.value)
        val preimage = hostFunction.value.contractIdPreimage
        assertIs<com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr.FromAddress>(preimage)
        assertTrue(preimage.value.salt.value.contentEquals(fixedSalt))
        assertEquals(1, hostFunction.value.constructorArgs.size)
        assertEquals(7u, (hostFunction.value.constructorArgs[0] as SCValXdr.U32).value.value)
    }

    @Test
    fun testDeployWithoutConstructorArgsUsesCreateContract() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val captured = mutableListOf<String>()

        val client = ContractClient.deployFromExternalRefInternal(
            executableOwner = ownerContractId,
            tag = executableTag,
            constructorArgs = emptyList(),
            source = source,
            signer = signer,
            network = Network.TESTNET,
            rpcUrl = MOCK_RPC_SERVER_URL,
            salt = fixedSalt,
            loadSpec = true,
            server = externalRefDeployMockServer(source, captured)
        )

        assertEquals(createdContractId, client.contractId)

        val hostFunction = submittedHostFunction(captured)
        assertIs<HostFunctionXdr.CreateContract>(hostFunction)
        val executable = hostFunction.value.executable
        assertIs<com.soneso.stellar.sdk.xdr.ContractExecutableXdr.ExternalRef>(executable)
        assertEquals(ownerContractId, Address.fromSCAddress(executable.value.executableOwner).getEncodedAddress())
        assertEquals(executableTag, executable.value.tag.value)
    }

    @Test
    fun testDeployThrowsNamingOwnerAndTagWhenTagEntryMissing() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val captured = mutableListOf<String>()

        val e = assertFailsWith<IllegalStateException> {
            ContractClient.deployFromExternalRefInternal(
                executableOwner = ownerContractId,
                tag = executableTag,
                constructorArgs = emptyList(),
                source = source,
                signer = signer,
                network = Network.TESTNET,
                rpcUrl = MOCK_RPC_SERVER_URL,
                salt = fixedSalt,
                loadSpec = true,
                server = externalRefDeployMockServer(source, captured, tagEntryExists = false)
            )
        }
        assertTrue(e.message!!.contains(ownerContractId), "unexpected message: ${e.message}")
        assertTrue(e.message!!.contains(executableTag), "unexpected message: ${e.message}")
        // The failure happened before anything else was requested.
        assertEquals(1, captured.size)
    }

    @Test
    fun testDeployRejectsNonContractOwnerBeforeAnyRequest() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val captured = mutableListOf<String>()
        val accountOwner = KeyPair.random().getAccountId()

        val e = assertFailsWith<IllegalArgumentException> {
            ContractClient.deployFromExternalRefInternal(
                executableOwner = accountOwner,
                tag = executableTag,
                constructorArgs = emptyList(),
                source = source,
                signer = signer,
                network = Network.TESTNET,
                rpcUrl = MOCK_RPC_SERVER_URL,
                salt = fixedSalt,
                loadSpec = true,
                server = externalRefDeployMockServer(source, captured)
            )
        }
        assertTrue(e.message!!.contains("is not a contract"), "unexpected message: ${e.message}")
        assertTrue(captured.isEmpty())
    }

    // ==================== Builder ====================

    @Test
    fun testBuilderCreateContractArmRoundTrips() {
        val op = InvokeHostFunctionOperation.createContractFromExternalRef(
            executableOwner = Address(ownerContractId),
            tag = executableTag,
            address = Address(MOCK_RPC_SOURCE_ACCOUNT),
            salt = fixedSalt
        )

        val hostFunction = op.hostFunction
        assertIs<HostFunctionXdr.CreateContract>(hostFunction)
        val executable = hostFunction.value.executable
        assertIs<com.soneso.stellar.sdk.xdr.ContractExecutableXdr.ExternalRef>(executable)
        assertEquals(ownerContractId, Address.fromSCAddress(executable.value.executableOwner).getEncodedAddress())
        assertEquals(executableTag, executable.value.tag.value)
        val preimage = hostFunction.value.contractIdPreimage
        assertIs<com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr.FromAddress>(preimage)
        assertTrue(preimage.value.salt.value.contentEquals(fixedSalt))

        // Byte-identical re-encode through the XDR layer.
        val encoded = hostFunctionToB64(hostFunction)
        assertEquals(encoded, hostFunctionToB64(hostFunctionFromB64(encoded)))
    }

    @Test
    fun testBuilderCreateContractV2ArmRoundTripsAndTagSurvivesByteSplice() {
        val op = InvokeHostFunctionOperation.createContractFromExternalRef(
            executableOwner = Address(ownerContractId),
            tag = executableTag,
            address = Address(MOCK_RPC_SOURCE_ACCOUNT),
            constructorArgs = listOf(Scv.toUint32(7u)),
            salt = fixedSalt
        )

        val hostFunction = op.hostFunction
        assertIs<HostFunctionXdr.CreateContractV2>(hostFunction)
        assertEquals(1, hostFunction.value.constructorArgs.size)

        val encoded = hostFunctionToB64(hostFunction)
        assertEquals(encoded, hostFunctionToB64(hostFunctionFromB64(encoded)))

        // The tag lives byte for byte in the encoded XDR: splicing its bytes and
        // decoding reads back the spliced tag.
        val writer = XdrWriter()
        hostFunction.encode(writer)
        val needle = executableTag.encodeToByteArray()
        val replacement = "token-v2".encodeToByteArray()
        val spliced = replaceOnce(writer.toByteArray(), needle, replacement)
        val decoded = HostFunctionXdr.decode(XdrReader(spliced))
        assertIs<HostFunctionXdr.CreateContractV2>(decoded)
        val decodedExecutable = decoded.value.executable
        assertIs<com.soneso.stellar.sdk.xdr.ContractExecutableXdr.ExternalRef>(decodedExecutable)
        assertEquals("token-v2", decodedExecutable.value.tag.value)
    }

    @Test
    fun testBuilderRejectsWrongSaltLength() {
        for (length in intArrayOf(31, 64)) {
            val e = assertFailsWith<IllegalArgumentException> {
                InvokeHostFunctionOperation.createContractFromExternalRef(
                    executableOwner = Address(ownerContractId),
                    tag = executableTag,
                    address = Address(MOCK_RPC_SOURCE_ACCOUNT),
                    salt = ByteArray(length) { 0x11 }
                )
            }
            assertTrue(e.message!!.contains("Salt must be 32 bytes, got $length"),
                "unexpected message: ${e.message}")
        }
    }

    /** Replaces the single occurrence of [needle] in [bytes] with [replacement]. */
    private fun replaceOnce(bytes: ByteArray, needle: ByteArray, replacement: ByteArray): ByteArray {
        var found = -1
        var count = 0
        for (start in 0..bytes.size - needle.size) {
            var match = true
            for (i in needle.indices) {
                if (bytes[start + i] != needle[i]) {
                    match = false
                    break
                }
            }
            if (match) {
                count++
                found = start
            }
        }
        assertEquals(1, count, "The needle must occur exactly once in the fixture bytes")
        return bytes.copyOfRange(0, found) + replacement +
            bytes.copyOfRange(found + needle.size, bytes.size)
    }

    // ==================== deriveContractId ====================

    @Test
    fun testDeriveContractIdMatchesTheSharedCrossSdkVectors() = runTest {
        // Shared vectors pinned across the Soneso SDKs; independently computed
        // (js-stellar-base and a hand-encoded preimage), never from this
        // implementation.
        val salt = ByteArray(32) { 0x11 }
        val accountDeployer = Address("GABAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEJXA")

        assertEquals(
            "CADHEGA2PTPG6IJXYA62AY6JUQLQWOHRYX7A6FAZ4GL747OLBDQU7QXR",
            Address.deriveContractId(accountDeployer, salt, Network.TESTNET)
        )
        assertEquals(
            "CC22JLXJCLGHDQDBHJXTOEAFRM7GIS3D7CBQXR7M22HMWYNPUUPH6GKV",
            Address.deriveContractId(accountDeployer, salt, Network.PUBLIC)
        )

        val contractDeployer = Address.fromContract(
            com.soneso.stellar.sdk.Util.hexToBytes(
                "3f0918bf77f7e30fe942e4bc2ce903ffa2d80e7f3e1f82ba58877f0eb73df0b7"
            )
        )

        assertEquals(
            "CBU52TQFSCXOGX5OOE6QQKBRD3XNXAS2MRLI2XFKLGEODECDADE32QYU",
            Address.deriveContractId(contractDeployer, salt, Network.TESTNET)
        )
        assertEquals(
            "CAXJ2CGFFIGYDFF55V64NWWUASL7IMAZTVVECYOKOVQWF4KQGXP2HH3U",
            Address.deriveContractId(contractDeployer, salt, Network.PUBLIC)
        )
    }

    @Test
    fun testDeriveContractIdRejectsWrongSaltLength() = runTest {
        val deployer = Address("GABAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEJXA")

        // 31 for the off-by-one, 64 for the likeliest real mistake: a hex
        // string's byte length where raw bytes are expected.
        for (length in intArrayOf(31, 64)) {
            val e = assertFailsWith<IllegalArgumentException> {
                Address.deriveContractId(deployer, ByteArray(length) { 0x11 }, Network.TESTNET)
            }
            assertTrue(e.message!!.contains("must be exactly 32 bytes, got $length"),
                "unexpected message: ${e.message}")
        }
    }
}
