package com.soneso.stellar.sdk.unitTests.contract.bindings

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.bindings.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.unitTests.contract.MOCK_RPC_CONTRACT_ID
import com.soneso.stellar.sdk.unitTests.contract.MOCK_RPC_SERVER_URL
import com.soneso.stellar.sdk.unitTests.contract.MOCK_RPC_SOURCE_ACCOUNT
import com.soneso.stellar.sdk.unitTests.contract.mockRpcServer
import com.soneso.stellar.sdk.xdr.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for the generated [BindingsSpecTestContract] binding.
 *
 * Two layers of coverage, neither of which contacts a live network:
 *  - Pure XDR round-trips through the generated `toSCVal`/`fromSCVal` of every user-defined
 *    type (structs, tuple struct, unions, enums, error enums), plus assertions on the exact
 *    canonical encodings (struct field order, tuple-struct numeric order, union tag-first).
 *  - Mocked-RPC exercises of the generated client surface via a Ktor MockEngine: read-call
 *    invoke round-trips (struct, option some, option none), and an out-of-order `map`
 *    argument whose generated encode must sort entries ascending by key (Soroban rejects
 *    an unsorted ScMap on-chain).
 */
class BindingsSpecTestContractTest {

    companion object {
        // A distinct, valid account address used for address/union round-trips.
        private const val OTHER_ACCOUNT = "GD5KKP3LHUDXLDCGKP55NLEOEHMS3Z4BS6IDDZFCYU3BDXUZTBWL7JNF"
    }

    // ========================================================================
    // Pure round-trips: enums
    // ========================================================================

    @Test
    fun testEnumRoundTripAndValues() {
        assertEquals(11u, BindingsSpecTestContractRoyalCard.Jack.value)
        assertEquals(12u, BindingsSpecTestContractRoyalCard.Queen.value)
        assertEquals(13u, BindingsSpecTestContractRoyalCard.King.value)

        for (card in listOf(
            BindingsSpecTestContractRoyalCard.Jack,
            BindingsSpecTestContractRoyalCard.Queen,
            BindingsSpecTestContractRoyalCard.King
        )) {
            assertEquals(card, BindingsSpecTestContractRoyalCard.fromSCVal(card.toSCVal()))
        }
        // toSCVal encodes as SCV_U32 of the case value.
        assertEquals(12u, (BindingsSpecTestContractRoyalCard.Queen.toSCVal() as SCValXdr.U32).value.value)
        assertEquals(BindingsSpecTestContractRoyalCard.King, BindingsSpecTestContractRoyalCard.fromValue(13u))
        assertFailsWith<IllegalArgumentException> { BindingsSpecTestContractRoyalCard.fromValue(99u) }
    }

    @Test
    fun testErrorEnumRoundTrip() {
        val err = BindingsSpecTestContractError.NumberMustBeOdd
        assertEquals(1u, err.value)
        assertEquals(err, BindingsSpecTestContractError.fromSCVal(err.toSCVal()))
        assertEquals(err, BindingsSpecTestContractError.fromValue(1u))
        // Second error enum in the reference contract (case `elif`).
        val elif = BindingsSpecTestContractFalse.elif
        assertEquals(elif, BindingsSpecTestContractFalse.fromValue(1u))
        assertEquals(elif, BindingsSpecTestContractFalse.fromSCVal(elif.toSCVal()))
        assertEquals(1u, (elif.toSCVal() as SCValXdr.U32).value.value)
    }

    @Test
    fun testKeywordNamedEnumRoundTrip() {
        // The reference contract names this enum and its cases after language keywords
        // to exercise identifier handling.
        for (case in listOf(
            BindingsSpecTestContractimport.not,
            BindingsSpecTestContractimport.elif
        )) {
            assertEquals(case, BindingsSpecTestContractimport.fromSCVal(case.toSCVal()))
        }
        assertEquals(11u, BindingsSpecTestContractimport.not.value)
        assertEquals(BindingsSpecTestContractimport.elif, BindingsSpecTestContractimport.fromValue(12u))
        assertFailsWith<IllegalArgumentException> { BindingsSpecTestContractimport.fromValue(99u) }
    }

    // ========================================================================
    // Pure round-trips: structs
    // ========================================================================

    @Test
    fun testStructRoundTripAndFieldOrder() {
        val s = BindingsSpecTestContractSimpleStruct(a = 7u, b = true, c = "hello")
        val encoded = s.toSCVal()
        assertEquals(s, BindingsSpecTestContractSimpleStruct.fromSCVal(encoded))

        // Encodes as SCV_MAP with symbol keys in declared field order: a, b, c.
        val entries = (encoded as SCValXdr.Map).value!!.value
        val keys = entries.map { (it.key as SCValXdr.Sym).value.value }
        assertEquals(listOf("a", "b", "c"), keys)
    }

    @Test
    fun testKeywordFieldStructRoundTrip() {
        // Struct `True` carries a field named `def`; the SCV_MAP symbol key keeps the
        // raw contract-spec name.
        val s = BindingsSpecTestContractTrue(def = 9u)
        val encoded = s.toSCVal()
        assertEquals(s, BindingsSpecTestContractTrue.fromSCVal(encoded))
        val entries = (encoded as SCValXdr.Map).value!!.value
        assertEquals("def", (entries.single().key as SCValXdr.Sym).value.value)
        assertEquals(9u, (entries.single().`val` as SCValXdr.U32).value.value)
    }

    @Test
    fun testTupleStructRoundTripAndVecEncoding() {
        val inner = BindingsSpecTestContractSimpleStruct(a = 1u, b = false, c = "x")
        val t = BindingsSpecTestContractTupleStruct(value0 = inner, value1 = BindingsSpecTestContractSimpleEnum.Second)
        val encoded = t.toSCVal()
        assertEquals(t, BindingsSpecTestContractTupleStruct.fromSCVal(encoded))
        // Tuple struct encodes as SCV_VEC with fields ordered numerically: element 0 is
        // the struct (an SCV_MAP), element 1 the union (an SCV_VEC with its tag first).
        val elements = (encoded as SCValXdr.Vec).value!!.value
        assertEquals(2, elements.size)
        assertEquals(inner, BindingsSpecTestContractSimpleStruct.fromSCVal(elements[0]))
        assertEquals("Second", ((elements[1] as SCValXdr.Vec).value!!.value[0] as SCValXdr.Sym).value.value)
    }

    // ========================================================================
    // Pure round-trips: unions
    // ========================================================================

    @Test
    fun testFieldlessEnumUnionRoundTrip() {
        // SimpleEnum is a fieldless Rust enum -> encoded as an SCV_VEC union with only a tag.
        for (case in listOf(
            BindingsSpecTestContractSimpleEnum.First,
            BindingsSpecTestContractSimpleEnum.Second,
            BindingsSpecTestContractSimpleEnum.Third
        )) {
            assertEquals(case, BindingsSpecTestContractSimpleEnum.fromSCVal(case.toSCVal()))
        }
        val vec = (BindingsSpecTestContractSimpleEnum.First.toSCVal() as SCValXdr.Vec).value!!.value
        assertEquals("First", (vec[0] as SCValXdr.Sym).value.value)
        assertEquals(1, vec.size)
    }

    @Test
    fun testComplexUnionRoundTripAllArms() {
        val struct = BindingsSpecTestContractSimpleStruct(a = 3u, b = true, c = "s")
        val cases = listOf(
            BindingsSpecTestContractComplexEnum.Void,
            BindingsSpecTestContractComplexEnum.Struct(struct),
            BindingsSpecTestContractComplexEnum.Enum(BindingsSpecTestContractSimpleEnum.Third),
            BindingsSpecTestContractComplexEnum.Tuple(
                BindingsSpecTestContractTupleStruct(struct, BindingsSpecTestContractSimpleEnum.First)
            ),
            BindingsSpecTestContractComplexEnum.Asset(Address(OTHER_ACCOUNT), BigInteger.fromInt(1_000_000))
        )
        for (case in cases) {
            assertEquals(case, BindingsSpecTestContractComplexEnum.fromSCVal(case.toSCVal()))
        }
    }

    @Test
    fun testKeywordCaseUnionRoundTrip() {
        // Union `None` has case tags named after language keywords in the reference
        // contract; the SCV_VEC tag symbols keep the raw contract-spec names.
        for (case in listOf(
            BindingsSpecTestContractNone.elif,
            BindingsSpecTestContractNone.nonlocal,
            BindingsSpecTestContractNone.not
        )) {
            assertEquals(case, BindingsSpecTestContractNone.fromSCVal(case.toSCVal()))
        }
        val vec = (BindingsSpecTestContractNone.nonlocal.toSCVal() as SCValXdr.Vec).value!!.value
        assertEquals(1, vec.size)
        assertEquals("nonlocal", (vec[0] as SCValXdr.Sym).value.value)
        assertFailsWith<IllegalArgumentException> {
            BindingsSpecTestContractNone.fromSCVal(Scv.toVec(listOf(Scv.toSymbol("bogus"))))
        }
    }

    @Test
    fun testUnionTagIsFirstVecElement() {
        val asset = BindingsSpecTestContractComplexEnum.Asset(Address(OTHER_ACCOUNT), BigInteger.fromInt(5))
        val vec = (asset.toSCVal() as SCValXdr.Vec).value!!.value
        assertEquals("Asset", (vec[0] as SCValXdr.Sym).value.value, "case tag symbol must be first")
        assertEquals(3, vec.size, "Asset carries a tag plus two values")
    }

    // ========================================================================
    // Mocked invoke scaffolding (shared mock RPC lives in MockSorobanRpcHelpers)
    // ========================================================================

    /** Wrap a mock-backed [ContractClient] in the generated binding via its internal constructor. */
    private fun binding(server: SorobanServer): BindingsSpecTestContract =
        BindingsSpecTestContract(
            ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, contractSpec = null)
        )

    // ========================================================================
    // Mocked invoke through the generated client
    // ========================================================================

    @Test
    fun testMockedInvokeRoundTripsStruct() = runTest {
        val expected = BindingsSpecTestContractSimpleStruct(a = 42u, b = true, c = "world")
        mockRpcServer(simulateReturnValue = expected.toSCVal()).use { server ->
            // Read call (empty auth): the generated method encodes the arg, invokes, and decodes.
            val result = binding(server).strukt(expected, MOCK_RPC_SOURCE_ACCOUNT, signer = null)
            assertEquals(expected, result)
        }
    }

    @Test
    fun testMockedInvokeRoundTripsOptionSome() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toUint32(9u)).use { server ->
            val result = binding(server).option(9u, MOCK_RPC_SOURCE_ACCOUNT, signer = null)
            assertEquals(9u, result)
        }
    }

    @Test
    fun testMockedInvokeRoundTripsOptionNone() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val result = binding(server).option(null, MOCK_RPC_SOURCE_ACCOUNT, signer = null)
            assertNull(result)
        }
    }

    // ========================================================================
    // Out-of-order map argument must be encoded sorted ascending by key
    // ========================================================================

    @Test
    fun testMapArgumentIsEncodedSortedByKey() = runTest {
        // Keys inserted out of order; the host rejects an unsorted ScMap, so the generated
        // encode must sort. Inspect the actual SCV_MAP the built transaction carries.
        val outOfOrder = linkedMapOf(7u to false, 1u to true, 2u to false, 5u to true)
        mockRpcServer(simulateReturnValue = Scv.toMap(LinkedHashMap())).use { server ->
            val tx = binding(server).buildMapTx(outOfOrder, MOCK_RPC_SOURCE_ACCOUNT, signer = null)

            val op = tx.builtTransaction!!.operations.first() as InvokeHostFunctionOperation
            val args = (op.hostFunction as HostFunctionXdr.InvokeContract).value.args
            val mapArg = args.first() as SCValXdr.Map
            val keys = mapArg.value!!.value.map { (it.key as SCValXdr.U32).value.value }

            assertEquals(listOf(1u, 2u, 5u, 7u), keys, "map entries must be sorted ascending by key")
        }
    }
}
