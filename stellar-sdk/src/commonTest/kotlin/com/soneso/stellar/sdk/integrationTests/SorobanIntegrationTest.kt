package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.requests.GetTransactionsRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Soroban RPC server operations.
 *
 * These tests verify the SDK's Soroban RPC integration against a live Stellar testnet.
 * They cover:
 * - Server health checks
 * - Version information queries
 * - Fee statistics queries
 * - Network configuration queries
 * - Latest ledger information
 * - Transaction history queries with pagination
 *
 * **Test Network**: All tests use Stellar testnet Soroban RPC server.
 *
 * ## Running Tests
 *
 * These tests require network access to Soroban testnet RPC:
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "SorobanIntegrationTest"
 * ```
 *
 * ## Ported From
 *
 * These tests are ported from the Flutter Stellar SDK's soroban_test.dart:
 * - test server health
 * - test server version info
 * - test server fee stats
 * - test network request
 * - test get latest ledger
 * - test server get transactions
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc">Soroban RPC Documentation</a>
 */
class SorobanIntegrationTest {

    private val testOn = "testnet"
    private val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")

    /**
     * Test server health check endpoint.
     *
     * This test verifies:
     * 1. Server responds to health check requests
     * 2. Health status is "healthy"
     * 3. Response includes ledger retention window information
     * 4. Latest and oldest ledger numbers are returned
     *
     * The health check is essential for monitoring RPC server availability
     * and understanding the range of ledgers available for queries.
     */
    @Test
    fun testServerHealth() = runTest(timeout = 60.seconds) {
        // When: Getting server health status
        val healthResponse = sorobanServer.getHealth()

        // Then: Health response is valid
        assertEquals("healthy", healthResponse.status, "Server status should be healthy")
        assertNotNull(healthResponse.ledgerRetentionWindow, "Ledger retention window should not be null")
        assertNotNull(healthResponse.latestLedger, "Latest ledger should not be null")
        assertNotNull(healthResponse.oldestLedger, "Oldest ledger should not be null")

        // Additional validation
        assertTrue(healthResponse.latestLedger > 0, "Latest ledger should be greater than 0")
        assertTrue(healthResponse.oldestLedger > 0, "Oldest ledger should be greater than 0")
        assertTrue(
            healthResponse.latestLedger >= healthResponse.oldestLedger,
            "Latest ledger should be >= oldest ledger"
        )
        assertTrue(
            healthResponse.ledgerRetentionWindow > 0,
            "Ledger retention window should be positive"
        )
    }

    /**
     * Test server version information endpoint.
     *
     * This test verifies:
     * 1. Server returns version information
     * 2. Response includes RPC version string
     * 3. Response includes commit hash for traceability
     * 4. Build timestamp is provided
     * 5. Captive Core version is included
     * 6. Protocol version is returned
     *
     * Version information is crucial for debugging issues and ensuring
     * compatibility between SDK and server versions.
     */
    @Test
    fun testServerVersionInfo() = runTest(timeout = 60.seconds) {
        // When: Getting server version information
        val response = sorobanServer.getVersionInfo()

        // Then: All version fields are populated
        assertNotNull(response.version, "Version should not be null")
        assertNotNull(response.commitHash, "Commit hash should not be null")
        assertNotNull(response.buildTimestamp, "Build timestamp should not be null")
        assertNotNull(response.captiveCoreVersion, "Captive core version should not be null")
        assertNotNull(response.protocolVersion, "Protocol version should not be null")

        // Additional validation
        assertTrue(response.version.isNotEmpty(), "Version should not be empty")
        assertTrue(response.commitHash.isNotEmpty(), "Commit hash should not be empty")
        assertTrue(response.buildTimestamp.isNotEmpty(), "Build timestamp should not be empty")
        assertTrue(response.captiveCoreVersion.isNotEmpty(), "Captive core version should not be empty")
        assertTrue(response.protocolVersion > 0, "Protocol version should be positive")
    }

    /**
     * Test fee statistics endpoint.
     *
     * This test verifies:
     * 1. Server returns fee statistics
     * 2. Soroban inclusion fee stats are provided (percentiles, min, max, mode)
     * 3. Regular inclusion fee stats are provided
     * 4. Latest ledger reference is included
     *
     * Fee statistics help applications estimate appropriate fees for transactions
     * by providing distribution data from recent ledgers.
     */
    @Test
    fun testServerFeeStats() = runTest(timeout = 60.seconds) {
        // When: Getting fee statistics
        val response = sorobanServer.getFeeStats()

        // Then: Fee statistics are populated
        assertNotNull(response.sorobanInclusionFee, "Soroban inclusion fee should not be null")
        assertNotNull(response.inclusionFee, "Inclusion fee should not be null")
        assertNotNull(response.latestLedger, "Latest ledger should not be null")

        // Validate soroban inclusion fee structure
        assertTrue(response.sorobanInclusionFee.max >= 0, "Max fee should be non-negative")
        assertTrue(response.sorobanInclusionFee.min >= 0, "Min fee should be non-negative")
        assertTrue(
            response.sorobanInclusionFee.max >= response.sorobanInclusionFee.min,
            "Max fee should be >= min fee"
        )

        // Validate regular inclusion fee structure
        assertTrue(response.inclusionFee.max >= 0, "Max inclusion fee should be non-negative")
        assertTrue(response.inclusionFee.min >= 0, "Min inclusion fee should be non-negative")
        assertTrue(
            response.inclusionFee.max >= response.inclusionFee.min,
            "Max inclusion fee should be >= min inclusion fee"
        )

        // Validate latest ledger
        assertTrue(response.latestLedger > 0, "Latest ledger should be greater than 0")
    }

    /**
     * Test network configuration endpoint.
     *
     * This test verifies:
     * 1. Server returns network information
     * 2. Network passphrase matches expected testnet value
     * 3. Friendbot URL is correct for testnet
     * 4. Response is not an error response
     *
     * Network information is essential for verifying connectivity to the
     * correct Stellar network and obtaining network-specific configuration.
     */
    @Test
    fun testNetworkRequest() = runTest(timeout = 60.seconds) {
        // When: Getting network information
        val networkResponse = sorobanServer.getNetwork()

        // Then: Network information is valid and matches testnet
        assertEquals(
            "https://friendbot.stellar.org/",
            networkResponse.friendbotUrl,
            "Friendbot URL should match testnet"
        )
        assertEquals(
            "Test SDF Network ; September 2015",
            networkResponse.passphrase,
            "Network passphrase should match testnet"
        )

        // Additional validation
        assertNotNull(networkResponse.protocolVersion, "Protocol version should not be null")
        assertTrue(networkResponse.protocolVersion > 0, "Protocol version should be positive")
    }

    /**
     * Test latest ledger information endpoint.
     *
     * This test verifies:
     * 1. Server returns latest ledger information
     * 2. Response is not an error response
     * 3. Ledger ID (hash) is provided
     * 4. Protocol version is included
     * 5. Ledger sequence number is returned
     *
     * Latest ledger information is used to determine the current state of
     * the network and for anchoring queries to specific ledger ranges.
     */
    @Test
    fun testGetLatestLedger() = runTest(timeout = 60.seconds) {
        // When: Getting latest ledger information
        val latestLedgerResponse = sorobanServer.getLatestLedger()

        // Then: Latest ledger information is populated
        assertNotNull(latestLedgerResponse.id, "Ledger ID should not be null")
        assertNotNull(latestLedgerResponse.protocolVersion, "Protocol version should not be null")
        assertNotNull(latestLedgerResponse.sequence, "Ledger sequence should not be null")

        // Additional validation
        assertTrue(latestLedgerResponse.id.isNotEmpty(), "Ledger ID should not be empty")
        assertTrue(latestLedgerResponse.protocolVersion > 0, "Protocol version should be positive")
        assertTrue(latestLedgerResponse.sequence > 0, "Ledger sequence should be greater than 0")
    }

    /**
     * Test transaction history queries with pagination.
     *
     * This test verifies:
     * 1. Server returns transactions for a ledger range
     * 2. Pagination limit is respected
     * 3. Response includes cursor for next page
     * 4. Latest and oldest ledger info is included
     * 5. Cursor-based pagination works correctly
     * 6. Second page returns expected number of results
     *
     * Transaction queries are essential for monitoring contract activity,
     * auditing operations, and building transaction history interfaces.
     *
     * The test uses a recent ledger range to ensure transactions are available.
     */
    @Test
    fun testServerGetTransactions() = runTest(timeout = 60.seconds) {
        // Given: Get current ledger to calculate valid start ledger
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        // Calculate start ledger (200 ledgers before current)
        val startLedger = latestLedgerResponse.sequence - 200

        // When: Requesting first page of transactions with limit
        val pagination = GetTransactionsRequest.Pagination(limit = 2)
        val request = GetTransactionsRequest(
            startLedger = startLedger,
            pagination = pagination
        )
        val response = sorobanServer.getTransactions(request)

        // Then: First page response is valid
        assertNotNull(response.transactions, "Transactions list should not be null")
        assertNotNull(response.latestLedger, "Latest ledger should not be null")
        assertNotNull(response.oldestLedger, "Oldest ledger should not be null")
        assertNotNull(response.oldestLedgerCloseTimestamp, "Oldest ledger close timestamp should not be null")
        assertNotNull(response.cursor, "Cursor should not be null")

        val transactions = response.transactions
        assertTrue(transactions.isNotEmpty(), "Should have at least one transaction")
        assertTrue(transactions.size <= 2, "Should not exceed limit of 2")

        // Validate transaction structure
        transactions.forEach { tx ->
            assertNotNull(tx.status, "Transaction status should not be null")
            assertNotNull(tx.ledger, "Transaction ledger should not be null")
        }

        // When: Requesting second page using cursor (no startLedger when using cursor)
        val pagination2 = GetTransactionsRequest.Pagination(cursor = response.cursor, limit = 2)
        val request2 = GetTransactionsRequest(
            pagination = pagination2
        )
        val response2 = sorobanServer.getTransactions(request2)

        // Then: Second page response is valid
        assertNotNull(response2.transactions, "Second page transactions should not be null")
        val transactions2 = response2.transactions
        assertEquals(2, transactions2.size, "Second page should have exactly 2 transactions")

        // Additional validation
        assertTrue(response.latestLedger > 0, "Latest ledger should be positive")
        assertTrue(response.oldestLedger > 0, "Oldest ledger should be positive")
        assertTrue(
            response.latestLedger >= response.oldestLedger,
            "Latest ledger should be >= oldest ledger"
        )
    }
}
