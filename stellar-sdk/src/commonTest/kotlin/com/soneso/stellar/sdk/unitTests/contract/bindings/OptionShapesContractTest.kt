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
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for the generated [OptionShapesContract] binding.
 *
 * Two layers of coverage, neither of which contacts a live network:
 *  - Pure XDR round-trips through the generated `toSCVal`/`fromSCVal` of every user-defined
 *    type of this contract ([OptionShapesContractMaybeStruct], [OptionShapesContractOptUnion]),
 *    with assertions on the exact wire shapes: a null optional struct field encodes as SCV_VOID
 *    under its own symbol key, and a union arm with a null optional payload keeps its payload
 *    slot as SCV_VOID so it stays distinguishable from a payload-less arm.
 *  - A mocked-RPC exercise via a Ktor MockEngine: an out-of-order `Map<UInt, UInt?>` argument
 *    whose generated encode must sort entries ascending by key (Soroban rejects an unsorted
 *    ScMap on-chain) while encoding null values as SCV_VOID.
 */
class OptionShapesContractTest {

    // ========================================================================
    // Pure round-trips: struct with an optional field
    // ========================================================================

    @Test
    fun testMaybeStructRoundTripAndFieldOrder() {
        val some = OptionShapesContractMaybeStruct(flag = 1u, maybe = 7u)
        val someEncoded = some.toSCVal()
        assertEquals(some, OptionShapesContractMaybeStruct.fromSCVal(someEncoded))

        // Encodes as SCV_MAP with symbol keys in declared field order: flag, maybe.
        val someEntries = (someEncoded as SCValXdr.Map).value!!.value
        assertEquals(listOf("flag", "maybe"), someEntries.map { (it.key as SCValXdr.Sym).value.value })

        // A null optional field encodes as SCV_VOID under its own key and round-trips.
        val none = OptionShapesContractMaybeStruct(flag = 1u, maybe = null)
        val noneEncoded = none.toSCVal()
        assertEquals(none, OptionShapesContractMaybeStruct.fromSCVal(noneEncoded))
        val noneEntries = (noneEncoded as SCValXdr.Map).value!!.value
        assertEquals(listOf("flag", "maybe"), noneEntries.map { (it.key as SCValXdr.Sym).value.value })
        assertEquals(SCValTypeXdr.SCV_VOID, noneEntries[1].`val`.discriminant)
    }

    // ========================================================================
    // Pure round-trips: union with an optional payload
    // ========================================================================

    @Test
    fun testOptUnionRoundTripAllArms() {
        for (case in listOf(
            OptionShapesContractOptUnion.Nothing,
            OptionShapesContractOptUnion.Maybe(5u),
            OptionShapesContractOptUnion.Maybe(null)
        )) {
            assertEquals(case, OptionShapesContractOptUnion.fromSCVal(case.toSCVal()))
        }
        // Maybe(null) keeps its payload slot as SCV_VOID, so it stays distinguishable from
        // the payload-less Nothing arm (an SCV_VEC holding only the tag symbol).
        val maybeNullVec = (OptionShapesContractOptUnion.Maybe(null).toSCVal() as SCValXdr.Vec).value!!.value
        assertEquals(2, maybeNullVec.size)
        assertEquals("Maybe", (maybeNullVec[0] as SCValXdr.Sym).value.value)
        assertEquals(SCValTypeXdr.SCV_VOID, maybeNullVec[1].discriminant)
        val nothingVec = (OptionShapesContractOptUnion.Nothing.toSCVal() as SCValXdr.Vec).value!!.value
        assertEquals(1, nothingVec.size)
        assertEquals("Nothing", (nothingVec[0] as SCValXdr.Sym).value.value)
        assertFailsWith<IllegalArgumentException> {
            OptionShapesContractOptUnion.fromSCVal(Scv.toVec(listOf(Scv.toSymbol("bogus"))))
        }
    }

    // ========================================================================
    // Mocked invoke scaffolding (shared mock RPC lives in MockSorobanRpcHelpers)
    // ========================================================================

    /** Wrap a mock-backed [ContractClient] in the generated binding via its internal constructor. */
    private fun binding(server: SorobanServer): OptionShapesContract =
        OptionShapesContract(
            ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, contractSpec = null)
        )

    // ========================================================================
    // Optional-valued map argument must be encoded sorted with SCV_VOID for null
    // ========================================================================

    @Test
    fun testOptMapArgumentIsEncodedSortedWithVoidForNull() = runTest {
        // Keys inserted out of order with mixed some/null values; the host rejects an
        // unsorted ScMap, so the generated encode must sort ascending by key and carry
        // null values as SCV_VOID. Inspect the actual SCV_MAP the built transaction carries.
        val outOfOrder: Map<UInt, UInt?> = linkedMapOf(5u to null, 1u to 10u, 3u to null)
        mockRpcServer(simulateReturnValue = Scv.toMap(LinkedHashMap())).use { server ->
            val tx = binding(server).buildOptMapTx(outOfOrder, MOCK_RPC_SOURCE_ACCOUNT, signer = null)

            val op = tx.builtTransaction!!.operations.first() as InvokeHostFunctionOperation
            val args = (op.hostFunction as HostFunctionXdr.InvokeContract).value.args
            val entries = (args.first() as SCValXdr.Map).value!!.value

            assertEquals(
                listOf(1u, 3u, 5u),
                entries.map { (it.key as SCValXdr.U32).value.value },
                "map entries must be sorted ascending by key"
            )
            assertEquals(10u, (entries[0].`val` as SCValXdr.U32).value.value)
            assertEquals(SCValTypeXdr.SCV_VOID, entries[1].`val`.discriminant)
            assertEquals(SCValTypeXdr.SCV_VOID, entries[2].`val`.discriminant)
        }
    }
}
