package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.ClientOptions
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for the spec-free contract-invocation surface of [ContractClient]: the
 * positional `List<SCValXdr>` [ContractClient.invoke] / [ContractClient.buildInvoke]
 * overloads and the [ContractClient.forContractWithoutSpec] factory.
 *
 * A method-dispatching Ktor MockEngine ([mockRpcServer]) drives the real build/simulate
 * path so the positional overloads exercise their full public semantics (simulate,
 * read/write auto-detection, signer-required-for-write check) with no live server
 * contacted.
 */
class ContractClientPositionalInvokeTest {

    private fun specFreeClient(server: SorobanServer): ContractClient =
        ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, contractSpec = null)

    // ========================================================================
    // Positional invoke: read path
    // ========================================================================

    @Test
    fun testPositionalInvokeReadPathReturnsParsedValue() = runTest {
        // Empty auth -> read call -> result() returns the simulated, parsed value.
        mockRpcServer(simulateReturnValue = SCValXdr.B(true)).use { server ->
            val client = specFreeClient(server)
            val result: Boolean = client.invoke(
                functionName = "hello",
                parameters = listOf(Scv.toUint64(1234UL)),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null,
                parseResultXdrFn = { (it as SCValXdr.B).value }
            )
            assertTrue(result, "read call must return the parsed simulated value")
        }
    }

    @Test
    fun testPositionalInvokeReadPathRawScValWhenNoParser() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toUint32(42u)).use { server ->
            val client = specFreeClient(server)
            val result: SCValXdr = client.invoke(
                functionName = "hello",
                parameters = emptyList(),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null
            )
            assertTrue(result is SCValXdr.U32)
            assertEquals(42u, (result as SCValXdr.U32).value.value)
        }
    }

    // ========================================================================
    // Positional buildInvoke: un-submitted assembled transaction
    // ========================================================================

    @Test
    fun testPositionalBuildInvokeReturnsUnsubmittedAssembled() = runTest {
        mockRpcServer(simulateReturnValue = SCValXdr.B(true)).use { server ->
            val client = specFreeClient(server)
            val tx = client.buildInvoke(
                functionName = "hello",
                parameters = listOf(Scv.toUint64(1234UL)),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null,
                parseResultXdrFn = { (it as SCValXdr.B).value }
            )
            // Simulated (default options.simulate == true) but not submitted.
            assertNotNull(tx.simulation, "buildInvoke must simulate by default")
            assertNotNull(tx.builtTransaction, "prepared transaction must be present after simulate")
            assertNull(tx.sendTransactionResponse, "buildInvoke must not submit")
            assertNull(tx.getTransactionResponse, "buildInvoke must not submit")
            // Result is still retrievable from the simulation for a read call.
            assertTrue(tx.result())
        }
    }

    @Test
    fun testPositionalBuildInvokeWithSimulateDisabledDoesNotSimulate() = runTest {
        mockRpcServer(simulateReturnValue = SCValXdr.B(true)).use { server ->
            val client = specFreeClient(server)
            val options = ClientOptions(
                sourceAccountKeyPair = KeyPair.fromAccountId(MOCK_RPC_SOURCE_ACCOUNT),
                contractId = MOCK_RPC_CONTRACT_ID,
                network = Network.TESTNET,
                rpcUrl = MOCK_RPC_SERVER_URL,
                simulate = false
            )
            val tx = client.buildInvoke(
                functionName = "hello",
                parameters = emptyList(),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null,
                parseResultXdrFn = { (it as SCValXdr.B).value },
                options = options
            )
            assertNull(tx.simulation, "simulate=false must skip simulation")
            assertNotNull(tx.raw, "raw builder is available for manual control")
        }
    }

    // ========================================================================
    // Positional invoke: write call requires a signer
    // ========================================================================

    @Test
    fun testPositionalInvokeWriteCallWithoutSignerThrows() = runTest {
        // Non-empty auth -> write call. With autoSubmit (default) and no signer, invoke throws.
        mockRpcServer(simulateReturnValue = Scv.toVoid(), authEntries = listOf(writeAuthEntry())).use { server ->
            val client = specFreeClient(server)
            val ex = assertFailsWith<IllegalArgumentException> {
                client.invoke<SCValXdr>(
                    functionName = "hello",
                    parameters = emptyList(),
                    source = MOCK_RPC_SOURCE_ACCOUNT,
                    signer = null
                )
            }
            assertTrue(
                ex.message?.contains("Signer required for write call") == true,
                "message must identify the missing-signer condition: ${ex.message}"
            )
        }
    }

    @Test
    fun testPositionalInvokeWriteCallWithAutoSubmitDisabledReturnsSimulatedResult() = runTest {
        // autoSubmit=false: a write call is not submitted; result() from simulation is returned
        // even without a signer.
        mockRpcServer(simulateReturnValue = SCValXdr.B(false), authEntries = listOf(writeAuthEntry())).use { server ->
            val client = specFreeClient(server)
            val options = ClientOptions(
                sourceAccountKeyPair = KeyPair.fromAccountId(MOCK_RPC_SOURCE_ACCOUNT),
                contractId = MOCK_RPC_CONTRACT_ID,
                network = Network.TESTNET,
                rpcUrl = MOCK_RPC_SERVER_URL,
                autoSubmit = false
            )
            val result: Boolean = client.invoke(
                functionName = "hello",
                parameters = emptyList(),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null,
                parseResultXdrFn = { (it as SCValXdr.B).value },
                options = options
            )
            assertFalse(result)
        }
    }

    @Test
    fun testPositionalInvokeWriteCallSignsAndSubmitsReturningParsedResult() = runTest {
        // autoSubmit (default) + a signer + a write call (non-empty auth): invoke signs and
        // submits, then parses the return value from the getTransaction result meta.
        // The simulation reports false while the submitted transaction's meta reports true, so a
        // true result can only have come from the submit path, not the simulation.
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        mockRpcServer(
            simulateReturnValue = SCValXdr.B(false),
            authEntries = listOf(writeAuthEntry()),
            sourceAccount = source,
            submittedReturnValue = SCValXdr.B(true)
        ).use { server ->
            val client = specFreeClient(server)
            val result: Boolean = client.invoke(
                functionName = "hello",
                parameters = emptyList(),
                source = source,
                signer = signer,
                parseResultXdrFn = { (it as SCValXdr.B).value }
            )
            assertTrue(result, "write call must return the value parsed from the submitted transaction meta")
        }
    }

    // ========================================================================
    // Spec-free factory
    // ========================================================================

    @Test
    fun testForContractWithoutSpecConstructsWithoutRpc() {
        // No runTest / no server interaction: construction performs no IO.
        val client = ContractClient.forContractWithoutSpec(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET)
        assertNull(client.getContractSpec(), "spec-free client must report no spec")
        assertTrue(client.getMethodNames().isEmpty(), "no spec -> no method names")
        assertEquals(MOCK_RPC_CONTRACT_ID, client.contractId)
        client.close()
    }

    @Test
    fun testSpecFreeClientMapInvokeThrowsIllegalState() = runTest {
        // The Map-based overload requires a spec; it must throw before any RPC is attempted.
        val client = ContractClient.forContractWithoutSpec(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET)
        assertFailsWith<IllegalStateException> {
            client.invoke<SCValXdr>(
                functionName = "hello",
                arguments = emptyMap(),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null
            )
        }
        assertFailsWith<IllegalStateException> {
            client.buildInvoke<SCValXdr>(
                functionName = "hello",
                arguments = emptyMap(),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null
            )
        }
        client.close()
    }

    @Test
    fun testSpecFreePositionalInvokeWorksWhenMapInvokeWouldThrow() = runTest {
        // Same client shape as production spec-free usage: the List overload dispatches to
        // the spec-free path and round-trips, while the Map overload dispatches to the
        // spec-backed path and throws. Explicitly typed locals pin the overload resolution.
        mockRpcServer(simulateReturnValue = Scv.toUint32(7u)).use { server ->
            val client = specFreeClient(server)

            val listArgs: List<SCValXdr> = listOf(Scv.toUint64(1UL))
            val positional: UInt = client.invoke(
                functionName = "hello",
                parameters = listArgs,
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null,
                parseResultXdrFn = { (it as SCValXdr.U32).value.value }
            )
            assertEquals(7u, positional)

            val mapArgs: Map<String, Any?> = mapOf("a" to 1)
            assertFailsWith<IllegalStateException> {
                client.invoke<UInt>(
                    functionName = "hello",
                    arguments = mapArgs,
                    source = MOCK_RPC_SOURCE_ACCOUNT,
                    signer = null,
                    parseResultXdrFn = { (it as SCValXdr.U32).value.value }
                )
            }
        }
    }
}
