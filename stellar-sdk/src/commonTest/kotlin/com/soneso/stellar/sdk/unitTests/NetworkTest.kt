package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NetworkTest {

    @Test
    fun testNetworkPassphrase() {
        val network = Network("Test Network")
        assertEquals("Test Network", network.networkPassphrase)
    }

    @Test
    fun testNetworkId() = runTest {
        val network = Network.PUBLIC
        val networkId = network.networkId()

        // Verify it's a 32-byte hash
        assertEquals(32, networkId.size)

        // Verify it's deterministic
        val networkId2 = network.networkId()
        assertTrue(networkId.contentEquals(networkId2))
    }

    @Test
    fun testPublicNetwork() = runTest {
        assertEquals("Public Global Stellar Network ; September 2015", Network.PUBLIC.networkPassphrase)

        // Verify network ID is deterministic
        val networkId1 = Network.PUBLIC.networkId()
        val networkId2 = Network.PUBLIC.networkId()
        assertTrue(networkId1.contentEquals(networkId2))
        assertEquals(32, networkId1.size)
    }

    @Test
    fun testTestnetNetwork() = runTest {
        assertEquals("Test SDF Network ; September 2015", Network.TESTNET.networkPassphrase)

        // Verify network ID is deterministic
        val networkId1 = Network.TESTNET.networkId()
        val networkId2 = Network.TESTNET.networkId()
        assertTrue(networkId1.contentEquals(networkId2))
        assertEquals(32, networkId1.size)
    }

    @Test
    fun testFuturenetNetwork() {
        assertEquals("Test SDF Future Network ; October 2022", Network.FUTURENET.networkPassphrase)
    }

    @Test
    fun testStandaloneNetwork() {
        assertEquals("Standalone Network ; February 2017", Network.STANDALONE.networkPassphrase)
    }

    @Test
    fun testSandboxNetwork() {
        assertEquals("Local Sandbox Stellar Network ; September 2022", Network.SANDBOX.networkPassphrase)
    }

    @Test
    fun testCustomNetwork() = runTest {
        val custom = Network("My Custom Network")
        assertEquals("My Custom Network", custom.networkPassphrase)

        // Verify network ID is different from standard networks
        assertFalse(custom.networkId().contentEquals(Network.PUBLIC.networkId()))
        assertFalse(custom.networkId().contentEquals(Network.TESTNET.networkId()))
    }

    @Test
    fun testNetworkEquality() {
        val network1 = Network("Test")
        val network2 = Network("Test")
        val network3 = Network("Different")

        assertEquals(network1, network2)
        assertNotEquals(network1, network3)
        assertEquals(network1.hashCode(), network2.hashCode())
    }

    @Test
    fun testNetworkToString() {
        val network = Network("Test Network")
        assertEquals("Test Network", network.toString())
    }

    @Test
    fun testBlankPassphraseFails() {
        assertFails {
            Network("")
        }

        assertFails {
            Network("   ")
        }
    }

    @Test
    fun testDifferentPassphrasesProduceDifferentIds() = runTest {
        val network1 = Network("Network 1")
        val network2 = Network("Network 2")

        assertFalse(network1.networkId().contentEquals(network2.networkId()))
    }

    @Test
    fun testNetworkIdIsDeterministic() = runTest {
        // Test that the same passphrase always produces the same hash
        val passphrase = "Test Passphrase"
        val network1 = Network(passphrase)
        val network2 = Network(passphrase)

        assertTrue(network1.networkId().contentEquals(network2.networkId()))
    }
}
