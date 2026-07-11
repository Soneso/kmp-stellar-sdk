package com.soneso.stellar.sdk.integrationTests.contract.bindings

import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.bindings.*
import com.soneso.stellar.sdk.integrationTests.realDelay
import com.soneso.stellar.sdk.util.TestResourceUtil
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Testnet integration test for the generated [OptionShapesContract] binding.
 *
 * Deploys the option-shapes contract WASM in-test (checked into commonTest/resources/wasm/),
 * then drives the generated client against a live Soroban RPC, exercising every generated
 * function of this contract: `opt_tuple`, `opt_strukt`, `opt_map`, `opt_union`, and `default`.
 * It proves that the on-chain host accepts the generated optional encodings (SCV_VOID for
 * absent values inside tuples, struct fields, map values, and union payloads) and that an
 * out-of-order optional-valued map argument is sorted before submission.
 *
 * Lives under integrationTests/ so it runs with the default test task and is excluded when
 * `-PexcludeIntegrationTests` is passed (the CI unit run).
 */
class OptionShapesContractIntegrationTest {

    private val rpcUrl = "https://soroban-testnet.stellar.org"
    private val network = Network.TESTNET

    @Test
    fun testGeneratedBindingRoundTripsAgainstTestnet() = runTest(timeout = 180.seconds) {
        // Fund a fresh source account.
        val sourceKeyPair = KeyPair.random()
        val source = sourceKeyPair.getAccountId()
        FriendBot.fundTestnetAccount(source)
        realDelay(5000)

        // Deploy the contract; keep the client spec-free (the binding embeds types).
        val wasm = TestResourceUtil.readWasmFile("soroban_bindings_option_shapes_contract.wasm")
        assertTrue(wasm.isNotEmpty(), "option-shapes contract WASM must not be empty")
        val deployed = ContractClient.deploy(
            wasmBytes = wasm,
            constructorArgs = emptyMap(),
            source = source,
            signer = sourceKeyPair,
            network = network,
            rpcUrl = rpcUrl,
            loadSpec = false
        )
        val contractId = deployed.contractId
        println("Deployed option-shapes contract: $contractId")

        val c = OptionShapesContract.forContract(contractId, rpcUrl, network)

        // --- tuple with an optional element: some + none ---
        val optTupleSome: Pair<UInt?, UInt> = Pair(3u, 9u)
        assertEquals(optTupleSome, c.optTuple(optTupleSome, source, signer = null), "opt_tuple some must round-trip")
        val optTupleNone: Pair<UInt?, UInt> = Pair(null, 9u)
        assertEquals(optTupleNone, c.optTuple(optTupleNone, source, signer = null), "opt_tuple none must round-trip")

        // --- struct with an optional field: some + none ---
        val maybeSome = OptionShapesContractMaybeStruct(flag = 1u, maybe = 2u)
        assertEquals(maybeSome, c.optStrukt(maybeSome, source, signer = null), "opt_strukt some must round-trip")
        val maybeNone = OptionShapesContractMaybeStruct(flag = 1u, maybe = null)
        assertEquals(maybeNone, c.optStrukt(maybeNone, source, signer = null), "opt_strukt none must round-trip")

        // --- map<u32,option<u32>> with mixed some/none values and out-of-order keys:
        //     the host rejects an unsorted ScMap, so the generated encode must sort ---
        val optMapArg: Map<UInt, UInt?> = linkedMapOf(5u to null, 1u to 10u, 3u to null)
        assertEquals(
            mapOf<UInt, UInt?>(1u to 10u, 3u to null, 5u to null),
            c.optMap(optMapArg, source, signer = null),
            "map with optional values must round-trip"
        )

        // --- union with an optional payload: Nothing, Maybe(some), Maybe(null) ---
        assertEquals(
            OptionShapesContractOptUnion.Nothing,
            c.optUnion(OptionShapesContractOptUnion.Nothing, source, signer = null)
        )
        val maybeArmSome = OptionShapesContractOptUnion.Maybe(9u)
        assertEquals(maybeArmSome, c.optUnion(maybeArmSome, source, signer = null))
        val maybeArmNone = OptionShapesContractOptUnion.Maybe(null)
        assertEquals(
            maybeArmNone,
            c.optUnion(maybeArmNone, source, signer = null),
            "Maybe(null) encodes [tag, void] and must round-trip distinct from Nothing"
        )

        // --- `default`: not a Kotlin keyword, so the generated method name stays unescaped;
        //     its `value` parameter is backtick-escaped ---
        assertEquals(64u, c.default(`value` = 64u, source = source, signer = null), "default must round-trip")

        c.client.close()
        deployed.close()
        println("OptionShapesContract testnet round-trips passed")
    }
}
