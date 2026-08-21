package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.secureRandomBytes
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableExternalRefXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCSpecEntryXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.tagString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/**
 * Verifies the CAP-85 external reference lifecycle against a live network.
 *
 * Self-contained: installs the owner fixture contract (source in this repo
 * under tools/p28-owner-fixture), deploys it, installs the hello world
 * contract's WASM, writes an executable tag entry naming it, resolves the
 * reference, and deploys a new instance from it with an explicit salt. It
 * verifies the created contract id against [Address.deriveContractId], and
 * checks the instance's executable arm, the loading paths behind the
 * reference, and a live invocation.
 *
 * The body is gated on protocol 28 via [networkProtocolAtLeast]; below that the
 * test prints why it skipped and returns. Futurenet runs protocol 28 already.
 */
class P28ExternalRefIntegrationTest {

    private val testOn = "testnet" // "testnet" or "futurenet"

    private val rpcUrl = if (testOn == "testnet") {
        "https://soroban-testnet.stellar.org"
    } else {
        "https://rpc-futurenet.stellar.org"
    }
    private val sorobanServer = SorobanServer(rpcUrl)
    private val network = if (testOn == "testnet") Network.TESTNET else Network.FUTURENET

    @Test
    fun testExternalRefLifecycle() = runTest(timeout = 600.seconds) {
        if (!networkProtocolAtLeast(sorobanServer, 28)) {
            println("Skipping P28 external-ref test: the network at $rpcUrl runs a protocol below 28")
            return@runTest
        }

        val keyPair = KeyPair.random()
        fundTestAccountAndAwaitVisibility(
            keyPair.getAccountId(),
            rpc = sorobanServer,
            useFuturenet = testOn != "testnet"
        )

        // Install and deploy the owner fixture holding the executable tag entries.
        val owner = ContractClient.deploy(
            wasmBytes = TestResourceUtil.readWasmFile("p28_owner_fixture.wasm"),
            source = keyPair.getAccountId(),
            signer = keyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        assertTrue(owner.getMethodNames().contains("set_executable"))

        // Install the target code and write the tag entry naming it.
        val targetWasmId = ContractClient.install(
            wasmBytes = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm"),
            source = keyPair.getAccountId(),
            signer = keyPair,
            network = network,
            rpcUrl = rpcUrl
        )
        val executableTag = "sdk-e2e-v1"
        owner.invoke<SCValXdr>(
            functionName = "set_executable",
            parameters = listOf(
                Scv.toString(executableTag),
                SCValXdr.Bytes(SCBytesXdr(Util.hexToBytes(targetWasmId)))
            ),
            source = keyPair.getAccountId(),
            signer = keyPair
        )

        realDelay(3000)

        // The reference resolves to the stored wasm hash.
        val ownerAddress = Address(owner.contractId)
        val ref = ContractExecutableExternalRefXdr(
            executableOwner = ownerAddress.toSCAddress(),
            tag = executableTag.encodeToByteArray()
        )
        val resolvedHash = sorobanServer.getExternalRefWasmHash(ref)
        assertEquals(targetWasmId, Util.bytesToHex(resolvedHash))

        // The RPC must accept the executable tag ledger key the resolver builds,
        // answering an empty entry list for a tag no entry exists under. This is
        // asserted on the raw response because getLedgerEntries throws on a
        // rejected request, so reaching the assertion proves acceptance.
        val unusedTag = "no entry exists under this tag"
        val tagKey = LedgerKeyXdr.ContractData(
            LedgerKeyContractDataXdr(
                contract = ownerAddress.toSCAddress(),
                key = SCValXdr.ExecutableTag(unusedTag.encodeToByteArray()),
                durability = ContractDataDurabilityXdr.PERSISTENT
            )
        )
        val response = sorobanServer.getLedgerEntries(listOf(tagKey))
        assertTrue(
            response.entries.isNullOrEmpty(),
            "The RPC should answer no entries for an absent tag"
        )

        // The resolver reports the same absence with its missing-entry exception.
        val unusedRef = ContractExecutableExternalRefXdr(
            executableOwner = ownerAddress.toSCAddress(),
            tag = unusedTag.encodeToByteArray()
        )
        val exception = assertFailsWith<IllegalStateException> {
            sorobanServer.getExternalRefWasmHash(unusedRef)
        }
        assertTrue(
            exception.message?.contains("No executable tag entry found") ?: false,
            "Unexpected message: ${exception.message}"
        )

        // Deploy from the reference with an explicit salt; the created contract id
        // must match the derivation, and the instance must run the target's code.
        val salt = secureRandomBytes(32)
        val predictedContractId = Address.deriveContractId(
            deployer = Address(keyPair.getAccountId()),
            salt = salt,
            network = network
        )
        val client = ContractClient.deployFromExternalRef(
            executableOwner = owner.contractId,
            tag = executableTag,
            source = keyPair.getAccountId(),
            signer = keyPair,
            network = network,
            rpcUrl = rpcUrl,
            salt = salt
        )
        assertEquals(predictedContractId, client.contractId)
        assertTrue(client.getMethodNames().contains("hello"))

        realDelay(3000)

        // The instance entry carries the external reference arm naming owner and
        // tag, and the code loader resolves it to the target's code.
        val instanceEntry = sorobanServer.getContractData(
            contractId = client.contractId,
            key = Scv.toLedgerKeyContractInstance(),
            durability = SorobanServer.Durability.PERSISTENT
        )
        assertNotNull(instanceEntry)
        val contractData = instanceEntry.parseXdr()
        assertIs<com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr.ContractData>(contractData)
        val instance = contractData.value.`val`
        assertIs<SCValXdr.Instance>(instance)
        val executable = instance.value.executable
        assertIs<ContractExecutableXdr.ExternalRef>(executable)
        assertEquals(owner.contractId, Address.fromSCAddress(executable.value.executableOwner).getEncodedAddress())
        assertEquals(executableTag, executable.value.tagString)

        val codeEntry = sorobanServer.loadContractCodeForContractId(client.contractId)
        assertNotNull(codeEntry)
        assertContentEquals(
            TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm"),
            codeEntry.code
        )
        val info = assertNotNull(sorobanServer.loadContractInfoForContractId(client.contractId))
        assertTrue(
            info.specEntries.filterIsInstance<SCSpecEntryXdr.FunctionV0>()
                .any { it.value.name.value == "hello" }
        )

        // The deployed instance is live: it answers as the target contract,
        // whose hello function takes a string parameter.
        val resultXdr = client.invoke<SCValXdr>(
            functionName = "hello",
            parameters = listOf(Scv.toString("friend")),
            source = keyPair.getAccountId(),
            signer = keyPair
        )
        val result = client.funcResToNative("hello", resultXdr) as List<*>
        assertEquals(listOf("Hello", "friend"), result)
    }
}
