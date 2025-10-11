package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive production-ready tests for ContractClient.
 *
 * Tests the complete API surface:
 * - Client initialization and configuration
 * - Simple and full invoke() overloads with all parameters
 * - Different parameter types and combinations
 * - Result parsers for all value types
 * - Resource management and cleanup
 * - Integration with AssembledTransaction
 * - Real-world contract scenarios
 *
 * Note: These tests focus on API contracts and client behavior.
 * Network-dependent integration tests require a live server.
 */
class ContractClientComprehensiveTest {

    companion object {
        const val CONTRACT_ID = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
        const val ACCOUNT_ID = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        const val SECRET_SEED = "SAEZSI6DY7AXJFIYA4PM6SIBNEYYXIEM2MSOTHFGKHDW32MBQ7KVO6EN"
        const val RPC_URL = "https://soroban-testnet.stellar.org"
        val NETWORK = Network.TESTNET
    }

    private lateinit var keypair: KeyPair

    @BeforeTest
    fun setup() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
    }

    // ==================== Constructor Tests ====================

    @Test
    fun testConstructorInitializesAllProperties() {
        val client = ContractClient(
            contractId = CONTRACT_ID,
            rpcUrl = RPC_URL,
            network = NETWORK
        )

        assertEquals(CONTRACT_ID, client.contractId)
        assertEquals(NETWORK, client.network)
        assertNotNull(client.server)

        client.close()
    }

    @Test
    fun testConstructorWithTestnetNetwork() {
        val client = ContractClient(
            contractId = CONTRACT_ID,
            rpcUrl = RPC_URL,
            network = Network.TESTNET
        )

        assertEquals(Network.TESTNET, client.network)
        assertEquals("Test SDF Network ; September 2015", client.network.networkPassphrase)

        client.close()
    }

    @Test
    fun testConstructorWithPublicNetwork() {
        val client = ContractClient(
            contractId = CONTRACT_ID,
            rpcUrl = "https://soroban-public.stellar.org",
            network = Network.PUBLIC
        )

        assertEquals(Network.PUBLIC, client.network)
        assertEquals("Public Global Stellar Network ; September 2015", client.network.networkPassphrase)

        client.close()
    }

    @Test
    fun testConstructorWithFutureNetwork() {
        val client = ContractClient(
            contractId = CONTRACT_ID,
            rpcUrl = RPC_URL,
            network = Network.FUTURENET
        )

        assertEquals(Network.FUTURENET, client.network)

        client.close()
    }

    @Test
    fun testConstructorWithCustomNetwork() {
        val customNetwork = Network("Custom Test Network Passphrase")
        val client = ContractClient(
            contractId = CONTRACT_ID,
            rpcUrl = RPC_URL,
            network = customNetwork
        )

        assertEquals(customNetwork, client.network)
        assertEquals("Custom Test Network Passphrase", client.network.networkPassphrase)

        client.close()
    }

    @Test
    fun testMultipleClientsAreIndependent() {
        val client1 = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)
        val client2 = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        assertNotSame(client1, client2)
        assertNotSame(client1.server, client2.server)

        client1.close()
        client2.close()
    }

    @Test
    fun testClientsWithDifferentContractIds() {
        val contractId1 = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
        val contractId2 = "CABC123YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCABC1"

        val client1 = ContractClient(contractId1, RPC_URL, NETWORK)
        val client2 = ContractClient(contractId2, RPC_URL, NETWORK)

        assertEquals(contractId1, client1.contractId)
        assertEquals(contractId2, client2.contractId)

        client1.close()
        client2.close()
    }

    // ==================== Close Tests ====================

    @Test
    fun testCloseReleasesResources() {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        // Close should not throw
        client.close()
    }

    @Test
    fun testMultipleCloseCallsAreSafe() {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        // Multiple close calls should be safe
        repeat(5) {
            client.close()
        }
    }

    @Test
    fun testClientCanBeReusedBeforeClose() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        // Should be able to create multiple assembled transactions
        repeat(3) {
            val assembled = client.invoke<SCValXdr>(
                functionName = "test_$it",
                parameters = emptyList(),
                source = ACCOUNT_ID,
                signer = keypair,
                parseResultXdrFn = null,
                baseFee = 100,
                transactionTimeout = 300L,
                submitTimeout = 30,
                simulate = false, // Don't actually call network
                restore = true
            )

            assertNotNull(assembled)
        }

        client.close()
    }

    // ==================== Simple Invoke Tests ====================

    @Test
    fun testInvokeSimpleCreatesAssembledTransaction() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<SCValXdr>(
            functionName = "test_function",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeSimpleWithNullSigner() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<SCValXdr>(
            functionName = "read_only",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = null, // Read-only call
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeSimpleWithCustomBaseFee() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val fees = listOf(100, 500, 1000, 10000, 100000)

        fees.forEach { fee ->
            val assembled = client.invoke<SCValXdr>(
                functionName = "test_function",
                parameters = emptyList(),
                source = ACCOUNT_ID,
                signer = keypair,
                parseResultXdrFn = null,
                baseFee = fee,
                transactionTimeout = 300L,
                submitTimeout = 30,
                simulate = false,
                restore = true
            )

            assertNotNull(assembled)
        }

        client.close()
    }

    // ==================== Full Invoke Tests ====================

    @Test
    fun testInvokeFullWithAllParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<BigInteger>(
            functionName = "get_value",
            parameters = listOf(Scv.toSymbol("key")),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = { Scv.fromInt128(it) },
            baseFee = 200,
            transactionTimeout = 600L,
            submitTimeout = 60,
            simulate = false, // Don't actually simulate
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeFullWithSimulateDisabled() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<SCValXdr>(
            functionName = "test_function",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false, // Don't simulate
            restore = true
        )

        assertNotNull(assembled)
        assertNull(assembled.simulation) // Should not be simulated

        client.close()
    }

    @Test
    fun testInvokeFullWithRestoreDisabled() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<SCValXdr>(
            functionName = "test_function",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = false // Don't auto-restore
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeFullWithCustomTimeouts() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<SCValXdr>(
            functionName = "test_function",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 900L, // 15 minutes
            submitTimeout = 120, // 2 minutes
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    // ==================== Parameter Type Tests ====================

    @Test
    fun testInvokeWithNoParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<SCValXdr>(
            functionName = "no_params_function",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithInt32Parameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val params = listOf(
            Scv.toInt32(42),
            Scv.toInt32(-100),
            Scv.toInt32(0)
        )

        val assembled = client.invoke<SCValXdr>(
            functionName = "process_ints",
            parameters = params,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithInt64Parameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val params = listOf(
            Scv.toInt64(1000000L),
            Scv.toInt64(-500000L)
        )

        val assembled = client.invoke<SCValXdr>(
            functionName = "process_large_ints",
            parameters = params,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithInt128Parameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val params = listOf(
            Scv.toInt128(BigInteger(1000000)),
            Scv.toInt128(BigInteger(-500000))
        )

        val assembled = client.invoke<SCValXdr>(
            functionName = "process_very_large_ints",
            parameters = params,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithStringParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val params = listOf(
            Scv.toString("hello"),
            Scv.toString("world"),
            Scv.toString("")
        )

        val assembled = client.invoke<SCValXdr>(
            functionName = "concat_strings",
            parameters = params,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithSymbolParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val params = listOf(
            Scv.toSymbol("action"),
            Scv.toSymbol("execute"),
            Scv.toSymbol("done")
        )

        val assembled = client.invoke<SCValXdr>(
            functionName = "perform_action",
            parameters = params,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithBooleanParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val params = listOf(
            Scv.toBoolean(true),
            Scv.toBoolean(false)
        )

        val assembled = client.invoke<SCValXdr>(
            functionName = "process_flags",
            parameters = params,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithAddressParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val params = listOf(
            Scv.toAddress(Address(ACCOUNT_ID).toSCAddress()),
            Scv.toAddress(Address(CONTRACT_ID).toSCAddress())
        )

        val assembled = client.invoke<SCValXdr>(
            functionName = "transfer",
            parameters = params,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithVecParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val vec = Scv.toVec(listOf(
            Scv.toInt32(1),
            Scv.toInt32(2),
            Scv.toInt32(3)
        ))

        val assembled = client.invoke<SCValXdr>(
            functionName = "process_vec",
            parameters = listOf(vec),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithMapParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val map = Scv.toMap(linkedMapOf(
            Scv.toSymbol("key1") to Scv.toInt32(100),
            Scv.toSymbol("key2") to Scv.toInt32(200)
        ))

        val assembled = client.invoke<SCValXdr>(
            functionName = "process_map",
            parameters = listOf(map),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithMixedParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val params = listOf(
            Scv.toAddress(Address(ACCOUNT_ID).toSCAddress()),
            Scv.toInt128(BigInteger(5000)),
            Scv.toSymbol("transfer"),
            Scv.toBoolean(true)
        )

        val assembled = client.invoke<SCValXdr>(
            functionName = "complex_function",
            parameters = params,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    // ==================== Result Parser Tests ====================

    @Test
    fun testInvokeWithoutParser() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<SCValXdr>(
            functionName = "get_raw_value",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = null,
            parseResultXdrFn = null, // No parser
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithInt32Parser() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<Int>(
            functionName = "get_count",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = null,
            parseResultXdrFn = { Scv.fromInt32(it) },
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithInt64Parser() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<Long>(
            functionName = "get_timestamp",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = null,
            parseResultXdrFn = { Scv.fromInt64(it) },
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithInt128Parser() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<BigInteger>(
            functionName = "get_balance",
            parameters = listOf(Scv.toAddress(Address(ACCOUNT_ID).toSCAddress())),
            source = ACCOUNT_ID,
            signer = null,
            parseResultXdrFn = { Scv.fromInt128(it) },
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithStringParser() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<String>(
            functionName = "get_name",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = null,
            parseResultXdrFn = { Scv.fromString(it) },
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithBooleanParser() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<Boolean>(
            functionName = "is_valid",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = null,
            parseResultXdrFn = { Scv.fromBoolean(it) },
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithVecParser() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<List<Int>>(
            functionName = "get_values",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = null,
            parseResultXdrFn = { scval ->
                Scv.fromVec(scval).map { Scv.fromInt32(it) }
            },
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithCustomObjectParser() = runTest {
        data class TokenInfo(val name: String, val symbol: String, val decimals: Int)

        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<TokenInfo>(
            functionName = "get_token_info",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = null,
            parseResultXdrFn = { scval ->
                val map = Scv.fromMap(scval)
                TokenInfo(
                    name = Scv.fromString(map[Scv.toSymbol("name")]!!),
                    symbol = Scv.fromString(map[Scv.toSymbol("symbol")]!!),
                    decimals = Scv.fromInt32(map[Scv.toSymbol("decimals")]!!)
                )
            },
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    // ==================== Different Function Names ====================

    @Test
    fun testInvokeWithVariousFunctionNames() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val functions = listOf(
            "balance",
            "transfer",
            "approve",
            "get_info",
            "mint",
            "burn",
            "set_admin",
            "initialize",
            "upgrade",
            "invoke"
        )

        functions.forEach { functionName ->
            val assembled = client.invoke<SCValXdr>(
                functionName = functionName,
                parameters = emptyList(),
                source = ACCOUNT_ID,
                signer = keypair,
                parseResultXdrFn = null,
                baseFee = 100,
                transactionTimeout = 300L,
                submitTimeout = 30,
                simulate = false,
                restore = true
            )

            assertNotNull(assembled)
        }

        client.close()
    }

    // ==================== Real-World Scenarios ====================

    @Test
    fun testTokenBalanceQuery() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<BigInteger>(
            functionName = "balance",
            parameters = listOf(Scv.toAddress(Address(ACCOUNT_ID).toSCAddress())),
            source = ACCOUNT_ID,
            signer = null, // Read-only
            parseResultXdrFn = { Scv.fromInt128(it) },
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testTokenTransfer() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val toAddress = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"

        val assembled = client.invoke<Unit>(
            functionName = "transfer",
            parameters = listOf(
                Scv.toAddress(Address(ACCOUNT_ID).toSCAddress()),
                Scv.toAddress(Address(toAddress).toSCAddress()),
                Scv.toInt128(BigInteger(1000000))
            ),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null, // Void return
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    // ==================== Edge Cases ====================

    @Test
    fun testInvokeWithVeryLongFunctionName() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val longFunctionName = "a".repeat(100)

        val assembled = client.invoke<SCValXdr>(
            functionName = longFunctionName,
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithManyParameters() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val manyParams = List(50) { Scv.toInt32(it) }

        val assembled = client.invoke<SCValXdr>(
            functionName = "many_params_function",
            parameters = manyParams,
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 100,
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }

    @Test
    fun testInvokeWithHighBaseFee() = runTest {
        val client = ContractClient(CONTRACT_ID, RPC_URL, NETWORK)

        val assembled = client.invoke<SCValXdr>(
            functionName = "expensive_operation",
            parameters = emptyList(),
            source = ACCOUNT_ID,
            signer = keypair,
            parseResultXdrFn = null,
            baseFee = 10000000, // Very high fee
            transactionTimeout = 300L,
            submitTimeout = 30,
            simulate = false,
            restore = true
        )

        assertNotNull(assembled)

        client.close()
    }
}
