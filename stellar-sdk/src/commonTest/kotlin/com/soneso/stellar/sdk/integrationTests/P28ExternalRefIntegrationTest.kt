package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableExternalRefXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.SCStringXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/**
 * Verifies CAP-85 external reference resolution against a live network.
 *
 * Self-contained: deploys its own contract, checks that the contract code
 * loader still reads it, and checks that a protocol 28 RPC accepts the
 * executable tag ledger key the resolver builds. The full resolution happy
 * path needs an owner contract that writes an executable tag entry, which no
 * fixture provides yet.
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
    fun testExternalRefLedgerKeyAcceptedByRpc() = runTest(timeout = 300.seconds) {
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

        val contractId = ContractClient.deploy(
            wasmBytes = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm"),
            source = keyPair.getAccountId(),
            signer = keyPair,
            network = network,
            rpcUrl = rpcUrl
        ).contractId

        realDelay(3000)

        // The executable dispatch still loads a wasm instance from the live ledger.
        val codeEntry = sorobanServer.loadContractCodeForContractId(contractId)
        assertNotNull(codeEntry, "The deployed contract's code should load by contract id")
        assertTrue(codeEntry.code.isNotEmpty(), "Loaded code should not be empty")

        // The RPC must accept the executable tag ledger key the resolver builds,
        // answering an empty entry list for a tag no entry exists under. This is
        // asserted on the raw response because getLedgerEntries throws on a
        // rejected request, so reaching the assertion proves acceptance.
        val unusedTag = "no entry exists under this tag"
        val tagKey = LedgerKeyXdr.ContractData(
            LedgerKeyContractDataXdr(
                contract = Address(contractId).toSCAddress(),
                key = SCValXdr.ExecutableTag(SCStringXdr(unusedTag)),
                durability = ContractDataDurabilityXdr.PERSISTENT
            )
        )
        val response = sorobanServer.getLedgerEntries(listOf(tagKey))
        assertTrue(
            response.entries.isNullOrEmpty(),
            "The RPC should answer no entries for an absent tag"
        )

        // The resolver reports the same absence with its missing-entry exception.
        val ref = ContractExecutableExternalRefXdr(
            executableOwner = Address(contractId).toSCAddress(),
            tag = SCStringXdr(unusedTag)
        )
        val exception = assertFailsWith<IllegalStateException> {
            sorobanServer.getExternalRefWasmHash(ref)
        }
        assertTrue(
            exception.message?.contains("No executable tag entry found") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }
}
