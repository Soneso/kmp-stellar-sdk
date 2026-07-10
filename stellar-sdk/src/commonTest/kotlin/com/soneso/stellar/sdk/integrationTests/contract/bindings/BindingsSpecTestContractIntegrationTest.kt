package com.soneso.stellar.sdk.integrationTests.contract.bindings

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MuxedAccount
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.bindings.*
import com.soneso.stellar.sdk.contract.exception.SimulationFailedException
import com.soneso.stellar.sdk.integrationTests.realDelay
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Testnet integration test for the generated [BindingsSpecTestContract] binding.
 *
 * Deploys the reference contract WASM in-test (checked into commonTest/resources/wasm/), then
 * drives the generated client against a live Soroban RPC. It exercises the type surface that
 * unit tests cannot fully prove — in particular that the on-chain host accepts the generated
 * encodings (e.g. a map argument with keys inserted out of order must be sorted, else the host
 * rejects it with "ScMap was not sorted by key").
 *
 * Lives under integrationTests/ so it runs with the default test task and is excluded when
 * `-PexcludeIntegrationTests` is passed (the CI unit run).
 */
class BindingsSpecTestContractIntegrationTest {

    private val rpcUrl = "https://soroban-testnet.stellar.org"
    private val network = Network.TESTNET

    @Test
    fun testGeneratedBindingRoundTripsAgainstTestnet() = runTest(timeout = 600.seconds) {
        // Fund a fresh source account.
        val sourceKeyPair = KeyPair.random()
        val source = sourceKeyPair.getAccountId()
        FriendBot.fundTestnetAccount(source)
        realDelay(5000)

        // Deploy the reference contract; keep the client spec-free (the binding embeds types).
        val wasm = TestResourceUtil.readWasmFile("soroban_bindings_spec_test_contract.wasm")
        assertTrue(wasm.isNotEmpty(), "reference contract WASM must not be empty")
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
        println("Deployed spec-test contract: $contractId")

        val c = BindingsSpecTestContract.forContract(contractId, rpcUrl, network)

        // --- 32-bit integers ---
        assertEquals(4_000_000_000u, c.u32(4_000_000_000u, source, signer = null), "u32 > Int.MAX must round-trip")
        assertEquals(-2_000_000_000, c.i32(-2_000_000_000, source, signer = null), "negative i32 must round-trip")

        // --- 64-bit integers beyond the JS-safe range and negatives ---
        val bigU64: ULong = 9_007_199_254_740_993uL // 2^53 + 1
        assertEquals(bigU64, c.u64(bigU64, source, signer = null), "u64 > 2^53 must round-trip")

        val negI64 = -9_007_199_254_740_993L
        assertEquals(negI64, c.i64(negI64, source, signer = null), "negative i64 must round-trip")

        // --- 128/256-bit integers (BigInteger end to end, at the type extremes) ---
        val u128Max = BigInteger.parseString("340282366920938463463374607431768211455")
        assertEquals(u128Max, c.u128(u128Max, source, signer = null), "u128 max must round-trip")
        val i128Min = BigInteger.parseString("-170141183460469231731687303715884105728")
        assertEquals(i128Min, c.i128(i128Min, source, signer = null), "i128 min must round-trip")
        val u256Max = BigInteger.parseString(
            "115792089237316195423570985008687907853269984665640564039457584007913129639935"
        )
        assertEquals(u256Max, c.u256(u256Max, source, signer = null), "u256 max must round-trip")
        val i256Min = BigInteger.parseString(
            "-57896044618658097711785492504343953926634992332820282019728792003956564819968"
        )
        assertEquals(i256Min, c.i256(i256Min, source, signer = null), "i256 min must round-trip")

        // --- timepoint / duration (ULong) ---
        assertEquals(1_700_000_000uL, c.timepoint(1_700_000_000uL, source, signer = null))
        assertEquals(3600uL, c.duration(3600uL, source, signer = null))

        // --- bool, string, symbol ---
        assertTrue(c.boolean(true, source, signer = null), "boolean must round-trip")
        assertFalse(c.not(true, source, signer = null), "not negates its argument")
        assertEquals("hello", c.hello("hello", source, signer = null), "symbol must round-trip")
        assertEquals("a string", c.string("a string", source, signer = null), "string must round-trip")

        // --- bytes / fixed-length bytes ---
        val bytes = byteArrayOf(0, 1, 2, 127, -1, -128)
        assertContentEquals(bytes, c.bytes(bytes, source, signer = null), "bytes must round-trip")
        val nineBytes = ByteArray(9) { it.toByte() }
        assertContentEquals(nineBytes, c.bytesN(nineBytes, source, signer = null), "BytesN<9> must round-trip")

        // --- vec ---
        assertEquals(listOf(3u, 1u, 2u), c.vec(listOf(3u, 1u, 2u), source, signer = null), "vec must round-trip in order")

        // --- map<u32,bool> with keys inserted OUT of order: host rejects unsorted maps ---
        val outOfOrder = linkedMapOf(7u to true, 1u to false, 2u to true)
        val mapResult = c.map(outOfOrder, source, signer = null)
        assertEquals(mapOf(1u to false, 2u to true, 7u to true), mapResult, "map must round-trip")

        // --- tuple arg + return ---
        val tuple = Pair("greeting", 5u)
        assertEquals(tuple, c.tuple(tuple, source, signer = null), "tuple must round-trip")

        // --- option some + none ---
        assertEquals(42u, c.option(42u, source, signer = null), "option some must round-trip")
        assertNull(c.option(null, source, signer = null), "option none must round-trip")

        // --- struct, struct helper, tuple structs ---
        val struct = BindingsSpecTestContractSimpleStruct(a = 11u, b = true, c = "sym")
        assertEquals(struct, c.strukt(struct, source, signer = null), "struct must round-trip")
        assertEquals(
            listOf("Hello", struct.c),
            c.struktHel(struct, source, signer = null),
            "strukt_hel returns [Hello, strukt.c]"
        )
        val tupleStruct = BindingsSpecTestContractTupleStruct(struct, BindingsSpecTestContractSimpleEnum.First)
        assertEquals(tupleStruct, c.tupleStrukt(tupleStruct, source, signer = null), "tuple struct must round-trip")
        val nested = Pair(struct, BindingsSpecTestContractSimpleEnum.Third)
        assertEquals(nested, c.tupleStruktNested(nested, source, signer = null), "nested tuple must round-trip")

        // --- enums ---
        assertEquals(
            BindingsSpecTestContractRoyalCard.King,
            c.card(BindingsSpecTestContractRoyalCard.King, source, signer = null),
            "enum must round-trip"
        )
        assertEquals(
            BindingsSpecTestContractSimpleEnum.Second,
            c.simple(BindingsSpecTestContractSimpleEnum.Second, source, signer = null),
            "fieldless union must round-trip"
        )

        // --- union: all five arms ---
        assertEquals(
            BindingsSpecTestContractComplexEnum.Void,
            c.complex(BindingsSpecTestContractComplexEnum.Void, source, signer = null)
        )
        val structArm = BindingsSpecTestContractComplexEnum.Struct(struct)
        assertEquals(structArm, c.complex(structArm, source, signer = null))
        val enumArm = BindingsSpecTestContractComplexEnum.Enum(BindingsSpecTestContractSimpleEnum.Third)
        assertEquals(enumArm, c.complex(enumArm, source, signer = null))
        val tupleArm = BindingsSpecTestContractComplexEnum.Tuple(tupleStruct)
        assertEquals(tupleArm, c.complex(tupleArm, source, signer = null))
        val assetArm = BindingsSpecTestContractComplexEnum.Asset(Address(source), BigInteger.fromInt(1_000_000))
        assertEquals(assetArm, c.complex(assetArm, source, signer = null))

        // --- address and muxed address round-trips ---
        val addr = Address(source)
        assertEquals(addr, c.address(addr, source, signer = null), "address must round-trip")
        val muxed = Address(MuxedAccount(source, 123_456_789uL).address)
        assertEquals(muxed, c.muxedAddress(muxed, source, signer = null), "muxed address must round-trip")

        // --- void returns and multi-arg ---
        c.void(source, signer = null)
        c.emptyTuple(source, signer = null)
        assertEquals(7u, c.multiArgs(7u, true, source, signer = null), "multi_args returns a when b is true")
        assertEquals(0u, c.multiArgs(7u, false, source, signer = null), "multi_args returns 0 when b is false")

        // --- error-enum path: odd succeeds, even fails the simulation with the contract error ---
        assertEquals(7u, c.u32FailOnEven(7u, source, signer = null))
        val simulationFailure = assertFailsWith<SimulationFailedException>(
            "even input must surface the NumberMustBeOdd contract error"
        ) {
            c.u32FailOnEven(8u, source, signer = null)
        }
        assertTrue(
            simulationFailure.message!!.contains("Error(Contract, #1)"),
            "simulation error identifies contract error #1 (NumberMustBeOdd): ${simulationFailure.message}"
        )

        // --- keyword-named methods: `val` and `from` (param `finally`) ---
        val valResult: SCValXdr = c.`val`(5u, Scv.toUint32(99u), source, signer = null)
        assertEquals(99u, (valResult as SCValXdr.U32).value.value, "`val` echoes the passed Val arg")
        assertEquals("bye", c.from("bye", source, signer = null), "`from` must round-trip its symbol")

        c.client.close()
        deployed.close()
        println("BindingsSpecTestContract testnet round-trips passed")
    }
}
