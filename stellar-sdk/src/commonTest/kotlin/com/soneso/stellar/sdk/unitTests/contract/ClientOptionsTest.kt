package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ClientOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for ContractClient.ClientOptions data class.
 *
 * Tests cover:
 * - Default values
 * - Custom values
 * - Data class equality/copy
 * - Property accessors
 */
class ClientOptionsTest {

    companion object {
        const val CONTRACT_ID = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
        const val RPC_URL = "https://soroban-testnet.stellar.org:443"
        // Use a known test account ID to avoid suspend KeyPair.random()
        const val TEST_ACCOUNT_ID = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
    }

    private val testKeyPair = KeyPair.fromAccountId(TEST_ACCOUNT_ID)

    // ========== Default Values ==========

    @Test
    fun testDefaultBaseFee() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertEquals(100, options.baseFee)
    }

    @Test
    fun testDefaultTransactionTimeout() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertEquals(300L, options.transactionTimeout)
    }

    @Test
    fun testDefaultSubmitTimeout() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertEquals(30, options.submitTimeout)
    }

    @Test
    fun testDefaultSimulate() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertTrue(options.simulate)
    }

    @Test
    fun testDefaultRestore() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertTrue(options.restore)
    }

    @Test
    fun testDefaultAutoSubmit() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertTrue(options.autoSubmit)
    }

    // ========== Custom Values ==========

    @Test
    fun testCustomBaseFee() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            baseFee = 500
        )
        assertEquals(500, options.baseFee)
    }

    @Test
    fun testCustomTransactionTimeout() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            transactionTimeout = 600L
        )
        assertEquals(600L, options.transactionTimeout)
    }

    @Test
    fun testCustomSubmitTimeout() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            submitTimeout = 60
        )
        assertEquals(60, options.submitTimeout)
    }

    @Test
    fun testSimulateDisabled() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            simulate = false
        )
        assertFalse(options.simulate)
    }

    @Test
    fun testRestoreDisabled() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            restore = false
        )
        assertFalse(options.restore)
    }

    @Test
    fun testAutoSubmitDisabled() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            autoSubmit = false
        )
        assertFalse(options.autoSubmit)
    }

    // ========== Property Accessors ==========

    @Test
    fun testSourceAccountKeyPair() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertSame(testKeyPair, options.sourceAccountKeyPair)
    }

    @Test
    fun testContractId() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertEquals(CONTRACT_ID, options.contractId)
    }

    @Test
    fun testNetwork() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertSame(Network.TESTNET, options.network)
    }

    @Test
    fun testNetworkPublic() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.PUBLIC,
            rpcUrl = "https://soroban.stellar.org:443"
        )
        assertSame(Network.PUBLIC, options.network)
    }

    @Test
    fun testRpcUrl() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertEquals(RPC_URL, options.rpcUrl)
    }

    // ========== Data Class Features ==========

    @Test
    fun testDataClassEquality() {
        val options1 = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            baseFee = 200,
            simulate = false
        )
        val options2 = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            baseFee = 200,
            simulate = false
        )
        assertEquals(options1, options2)
    }

    @Test
    fun testDataClassInequality() {
        val options1 = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            baseFee = 100
        )
        val options2 = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL,
            baseFee = 200
        )
        assertNotEquals(options1, options2)
    }

    @Test
    fun testDataClassCopy() {
        val original = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        val copy = original.copy(baseFee = 500, simulate = false)

        assertEquals(500, copy.baseFee)
        assertFalse(copy.simulate)
        // Other values preserved
        assertEquals(CONTRACT_ID, copy.contractId)
        assertEquals(RPC_URL, copy.rpcUrl)
        assertTrue(copy.restore)
        assertTrue(copy.autoSubmit)
    }

    @Test
    fun testDataClassHashCodeConsistency() {
        val options1 = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        val options2 = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.TESTNET,
            rpcUrl = RPC_URL
        )
        assertEquals(options1.hashCode(), options2.hashCode())
    }

    @Test
    fun testAllCustomValues() {
        val options = ClientOptions(
            sourceAccountKeyPair = testKeyPair,
            contractId = CONTRACT_ID,
            network = Network.PUBLIC,
            rpcUrl = "https://custom.rpc.url:443",
            baseFee = 1000,
            transactionTimeout = 900L,
            submitTimeout = 120,
            simulate = false,
            restore = false,
            autoSubmit = false
        )

        assertEquals(1000, options.baseFee)
        assertEquals(900L, options.transactionTimeout)
        assertEquals(120, options.submitTimeout)
        assertFalse(options.simulate)
        assertFalse(options.restore)
        assertFalse(options.autoSubmit)
        assertEquals("https://custom.rpc.url:443", options.rpcUrl)
    }
}
