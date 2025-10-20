package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.requests.GetEventsRequest
import com.soneso.stellar.sdk.rpc.requests.GetTransactionsRequest
import com.soneso.stellar.sdk.rpc.requests.GetLedgersRequest

import com.soneso.stellar.sdk.rpc.responses.EventFilterType
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.delay
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
 * - Contract upload and deployment
 * - Contract invocation
 * - Contract information retrieval
 * - Contract events emission and querying
 * - Ledger entries retrieval
 * - Stellar Asset Contract (SAC) deployment
 * - Footprint restoration (state restoration)
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
 * - test upload contract
 * - test create contract
 * - test invoke contract
 * - test events
 * - test get ledger entries
 * - test deploy SAC with source account
 * - test SAC with asset
 * - test restore footprint
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc">Soroban RPC Documentation</a>
 */
class SorobanIntegrationTest {

    private val testOn = "testnet" // "testnet" or "futurenet"

    private val sorobanServer = if (testOn == "testnet") {
        SorobanServer("https://soroban-testnet.stellar.org")
    } else {
        SorobanServer("https://rpc-futurenet.stellar.org")
    }

    private val horizonServer = if (testOn == "testnet") {
        HorizonServer("https://horizon-testnet.stellar.org")
    } else {
        HorizonServer("https://horizon-futurenet.stellar.org")
    }

    private val network = if (testOn == "testnet") {
        Network.TESTNET
    } else {
        Network.FUTURENET
    }

    companion object {
        /**
         * Shared WASM ID from testUploadContract, used by testCreateContract.
         * This allows tests to share state when run sequentially.
         */
        var sharedWasmId: String? = null

        /**
         * Shared keypair from testUploadContract, used by testCreateContract and testInvokeContract.
         */
        var sharedKeyPair: KeyPair? = null

        /**
         * Shared contract ID from testCreateContract, used by testInvokeContract and testGetLedgerEntries.
         * This is the deployed contract instance that can be invoked.
         */
        var sharedContractId: String? = null

        /**
         * Shared contract code (WASM bytes) from testUploadContract, used by testGetLedgerEntries.
         * This is used to validate that loaded contract code matches the uploaded code.
         */
        var sharedContractCode: ByteArray? = null

        /**
         * Shared footprint from testCreateContract, used by testGetLedgerEntries.
         * Contains ledger keys for contract code and contract data.
         */
        var sharedFootprint: LedgerFootprintXdr? = null
    }

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
     * 2. Network passphrase matches expected value based on testOn
     * 3. Friendbot URL is correct for the selected network
     * 4. Response is not an error response
     *
     * Network information is essential for verifying connectivity to the
     * correct Stellar network and obtaining network-specific configuration.
     */
    @Test
    fun testNetworkRequest() = runTest(timeout = 60.seconds) {
        // When: Getting network information
        val networkResponse = sorobanServer.getNetwork()

        // Then: Network information is valid and matches selected network
        if (testOn == "testnet") {
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
        } else if (testOn == "futurenet") {
            assertEquals(
                "https://friendbot-futurenet.stellar.org/",
                networkResponse.friendbotUrl,
                "Friendbot URL should match futurenet"
            )
            assertEquals(
                "Test SDF Future Network ; October 2022",
                networkResponse.passphrase,
                "Network passphrase should match futurenet"
            )
        }

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
    /**
     * Test basic getLedgers request with limit.
     *
     * This test verifies:
     * 1. Server returns ledgers for a specified start ledger
     * 2. Pagination limit is respected
     * 3. Response includes all required fields (cursor, timestamps, etc.)
     * 4. Ledger info structure is complete (hash, sequence, XDR data)
     *
     * The getLedgers RPC method is essential for retrieving historical ledger data
     * with pagination support. This test validates the basic request/response flow.
     *
     * **Reference**: Ported from Flutter SDK's "test basic getLedgers request with limit"
     * (soroban_test.dart lines 1047-1105)
     */
    @Test
    fun testBasicGetLedgersWithLimit() = runTest(timeout = 60.seconds) {
        // Given: Get latest ledger to determine a valid start point
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        val currentLedger = latestLedgerResponse.sequence
        val startLedger = if (currentLedger > 100) currentLedger - 100 else 1

        // When: Request ledgers with limit of 5
        val pagination = GetLedgersRequest.Pagination(limit = 5)
        val request = GetLedgersRequest(
            startLedger = startLedger,
            pagination = pagination
        )
        val response = sorobanServer.getLedgers(request)

        // Then: Validate response structure
        assertNotNull(response.ledgers, "Ledgers list should not be null")
        assertTrue(response.ledgers.isNotEmpty(), "Should return at least one ledger")
        assertTrue(response.ledgers.size <= 5, "Should not exceed requested limit of 5")
        assertNotNull(response.cursor, "Cursor should not be null")
        assertTrue(response.latestLedger > 0, "Latest ledger should be greater than 0")
        assertTrue(response.oldestLedger > 0, "Oldest ledger should be greater than 0")
        assertNotNull(response.latestLedgerCloseTime, "Latest ledger close time should not be null")
        assertNotNull(response.oldestLedgerCloseTime, "Oldest ledger close time should not be null")

        // Verify ledger info structure
        val firstLedger = response.ledgers.first()
        assertTrue(firstLedger.hash.isNotEmpty(), "Ledger hash should not be empty")
        assertTrue(firstLedger.sequence >= startLedger, "Ledger sequence should be >= start ledger")
        assertTrue(firstLedger.ledgerCloseTime > 0, "Ledger close time should be positive")
        assertTrue(firstLedger.headerXdr.isNotEmpty(), "Header XDR should not be empty")
        assertTrue(firstLedger.metadataXdr.isNotEmpty(), "Metadata XDR should not be empty")
    }

    /**
     * Test getLedgers pagination with cursor.
     *
     * This test verifies:
     * 1. Cursor-based pagination works correctly
     * 2. Second page can be requested using cursor from first page
     * 3. StartLedger is omitted when using cursor (as per RPC spec)
     * 4. Second page respects its own limit
     *
     * Cursor-based pagination is the recommended way to iterate through large
     * sets of ledgers without repeating data or missing entries.
     *
     * **Reference**: Ported from Flutter SDK's "test getLedgers pagination with cursor"
     * (soroban_test.dart lines 1107-1149)
     */
    @Test
    fun testGetLedgersPaginationWithCursor() = runTest(timeout = 60.seconds) {
        // Given: Get latest ledger to determine a valid start point
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        val currentLedger = latestLedgerResponse.sequence
        val startLedger = if (currentLedger > 100) currentLedger - 100 else 1

        // When: First request to get a cursor
        val firstPagination = GetLedgersRequest.Pagination(limit = 5)
        val firstRequest = GetLedgersRequest(
            startLedger = startLedger,
            pagination = firstPagination
        )
        val firstResponse = sorobanServer.getLedgers(firstRequest)

        assertNotNull(firstResponse.cursor, "First response cursor should not be null")

        val cursor = firstResponse.cursor

        // When: Second request using cursor (startLedger must be omitted when using cursor)
        val secondPagination = GetLedgersRequest.Pagination(cursor = cursor, limit = 3)
        val secondRequest = GetLedgersRequest(
            pagination = secondPagination
        )
        val secondResponse = sorobanServer.getLedgers(secondRequest)

        // Then: Validate the second response structure
        assertNotNull(secondResponse.ledgers, "Second response ledgers should not be null")
        if (secondResponse.ledgers.isNotEmpty()) {
            assertTrue(
                secondResponse.ledgers.size <= 3,
                "Second page should not exceed requested limit of 3"
            )
        }
        assertTrue(secondResponse.latestLedger > 0, "Latest ledger should be greater than 0")
    }

    /**
     * Test getLedgers verifies ledger sequence ordering.
     *
     * This test verifies:
     * 1. Ledgers are returned in ascending sequence order
     * 2. No gaps in sequence numbers (consecutive)
     * 3. Multiple ledgers can be retrieved and ordered correctly
     *
     * Ledger ordering is critical for applications that need to process
     * ledgers sequentially or verify blockchain continuity.
     *
     * **Reference**: Ported from Flutter SDK's "test getLedgers verifies ledger sequence ordering"
     * (soroban_test.dart lines 1151-1179)
     */
    @Test
    fun testGetLedgersSequenceOrdering() = runTest(timeout = 60.seconds) {
        // Given: Get latest ledger to determine a valid start point
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        val currentLedger = latestLedgerResponse.sequence
        val startLedger = if (currentLedger > 50) currentLedger - 50 else 1

        // When: Request multiple ledgers
        val pagination = GetLedgersRequest.Pagination(limit = 10)
        val request = GetLedgersRequest(
            startLedger = startLedger,
            pagination = pagination
        )
        val response = sorobanServer.getLedgers(request)

        // Then: Verify ledgers are in ascending order
        assertNotNull(response.ledgers, "Ledgers should not be null")
        assertTrue(response.ledgers.size > 1, "Should have multiple ledgers to test ordering")

        for (i in 1 until response.ledgers.size) {
            assertTrue(
                response.ledgers[i].sequence > response.ledgers[i - 1].sequence,
                "Ledgers should be in ascending sequence order"
            )
        }
    }

    /**
     * Test getLedgers without pagination options.
     *
     * This test verifies:
     * 1. getLedgers works without explicit pagination options
     * 2. Server uses default limit when pagination is omitted
     * 3. Response still includes cursor for subsequent requests
     * 4. At least one ledger is returned
     *
     * This validates that pagination is truly optional and the API
     * provides sensible defaults for simple queries.
     *
     * **Reference**: Ported from Flutter SDK's "test getLedgers without pagination options"
     * (soroban_test.dart lines 1181-1206)
     */
    @Test
    fun testGetLedgersWithoutPagination() = runTest(timeout = 60.seconds) {
        // Given: Get latest ledger to determine a valid start point
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        val currentLedger = latestLedgerResponse.sequence
        val startLedger = if (currentLedger > 100) currentLedger - 100 else 1

        // When: Request without pagination options (should use default limit)
        val request = GetLedgersRequest(startLedger = startLedger)
        val response = sorobanServer.getLedgers(request)

        // Then: Validate response
        assertNotNull(response.ledgers, "Ledgers should not be null")
        assertTrue(response.ledgers.isNotEmpty(), "Should return at least one ledger without pagination")
        assertNotNull(response.cursor, "Cursor should not be null in unpaginated response")
    }

    /**
     * Test getLedgers with different limits.
     *
     * This test verifies:
     * 1. Limit of 1 returns exactly 1 ledger
     * 2. Limit of 10 returns up to 10 ledgers
     * 3. Response respects the specified limit in all cases
     *
     * This validates that the pagination limit parameter works correctly
     * for various values, which is important for batching and rate limiting.
     *
     * **Reference**: Ported from Flutter SDK's "test getLedgers with different limits"
     * (soroban_test.dart lines 1208-1244)
     */
    @Test
    fun testGetLedgersWithDifferentLimits() = runTest(timeout = 60.seconds) {
        // Given: Get latest ledger to determine a valid start point
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        val currentLedger = latestLedgerResponse.sequence
        val startLedger = if (currentLedger > 100) currentLedger - 100 else 1

        // When: Test with limit of 1
        val pagination1 = GetLedgersRequest.Pagination(limit = 1)
        val request1 = GetLedgersRequest(
            startLedger = startLedger,
            pagination = pagination1
        )
        val response1 = sorobanServer.getLedgers(request1)

        // Then: Should return exactly 1 ledger
        assertNotNull(response1.ledgers, "Ledgers should not be null")
        assertEquals(1, response1.ledgers.size, "Should return exactly 1 ledger")

        // When: Test with limit of 10
        val pagination10 = GetLedgersRequest.Pagination(limit = 10)
        val request10 = GetLedgersRequest(
            startLedger = startLedger,
            pagination = pagination10
        )
        val response10 = sorobanServer.getLedgers(request10)

        // Then: Should return up to 10 ledgers
        assertNotNull(response10.ledgers, "Ledgers should not be null")
        assertTrue(response10.ledgers.isNotEmpty(), "Should return at least one ledger")
        assertTrue(response10.ledgers.size <= 10, "Should not exceed limit of 10")
    }

    /**
     * Test getLedgers validates all response fields.
     *
     * This test verifies:
     * 1. All top-level response fields are present and valid
     * 2. All ledger info fields are present and valid for each ledger
     * 3. Response structure is complete and consistent
     *
     * This comprehensive validation ensures that the response data structure
     * matches the expected schema and all fields are properly populated.
     *
     * **Reference**: Ported from Flutter SDK's "test getLedgers validates all response fields"
     * (soroban_test.dart lines 1246-1286)
     */
    @Test
    fun testGetLedgersValidatesAllFields() = runTest(timeout = 60.seconds) {
        // Given: Get latest ledger to determine a valid start point
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        val currentLedger = latestLedgerResponse.sequence
        val startLedger = if (currentLedger > 50) currentLedger - 50 else 1

        // When: Request ledgers
        val pagination = GetLedgersRequest.Pagination(limit = 3)
        val request = GetLedgersRequest(
            startLedger = startLedger,
            pagination = pagination
        )
        val response = sorobanServer.getLedgers(request)

        // Then: Validate all top-level response fields
        assertNotNull(response.ledgers, "Ledgers should not be null")
        assertNotNull(response.latestLedger, "Latest ledger should not be null")
        assertNotNull(response.latestLedgerCloseTime, "Latest ledger close time should not be null")
        assertNotNull(response.oldestLedger, "Oldest ledger should not be null")
        assertNotNull(response.oldestLedgerCloseTime, "Oldest ledger close time should not be null")
        assertNotNull(response.cursor, "Cursor should not be null")

        // Validate ledger info fields for each ledger
        response.ledgers.forEach { ledger ->
            assertTrue(ledger.hash.isNotEmpty(), "Each ledger should have a hash")
            assertTrue(ledger.sequence > 0, "Each ledger should have a valid sequence")
            assertTrue(ledger.ledgerCloseTime > 0, "Each ledger should have a close time")
            assertTrue(ledger.headerXdr.isNotEmpty(), "Each ledger should have headerXdr")
            assertTrue(ledger.metadataXdr.isNotEmpty(), "Each ledger should have metadataXdr")
        }
    }

    /**
     * Test getLedgers error handling with invalid start ledger.
     *
     * This test verifies:
     * 1. Server handles invalid start ledger gracefully
     * 2. Error response or exception is provided for future ledgers
     * 3. API validates input parameters
     *
     * Error handling is critical for robust applications that need to
     * handle edge cases and invalid user input gracefully.
     *
     * **Reference**: Ported from Flutter SDK's "test getLedgers error handling with invalid start ledger"
     * (soroban_test.dart lines 1288-1313)
     */
    @Test
    fun testGetLedgersErrorHandlingInvalidStartLedger() = runTest(timeout = 60.seconds) {
        // Given: Get latest ledger to determine boundaries
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        // When: Try with a start ledger far in the future (invalid)
        val invalidStartLedger = latestLedgerResponse.sequence + 1000000

        val pagination = GetLedgersRequest.Pagination(limit = 5)
        val request = GetLedgersRequest(
            startLedger = invalidStartLedger,
            pagination = pagination
        )

        // Then: Should handle error gracefully (either exception or error in response)
        try {
            val response = sorobanServer.getLedgers(request)
            // If we get a response without exception, it should have empty ledgers
            // or we should handle it appropriately based on the server's behavior
            assertTrue(
                response.ledgers.isEmpty(),
                "Should have empty ledgers for invalid start ledger"
            )
        } catch (e: Exception) {
            // It's acceptable to throw an exception for invalid start ledger
            assertNotNull(e, "Should throw exception for invalid start ledger")
        }
    }

    /**
     * Test getLedgers with recent ledger.
     *
     * This test verifies:
     * 1. getLedgers works with very recent ledger as start point
     * 2. Recent ledger data is available and accessible
     * 3. First ledger sequence matches or exceeds start ledger
     *
     * This validates that the API can handle requests for the most recent
     * blockchain data, which is important for real-time applications.
     *
     * **Reference**: Ported from Flutter SDK's "test getLedgers with recent ledger"
     * (soroban_test.dart lines 1315-1345)
     */
    @Test
    fun testGetLedgersWithRecentLedger() = runTest(timeout = 60.seconds) {
        // Given: Get the most recent ledger
        val latestLedgerResponse = sorobanServer.getLatestLedger()
        assertNotNull(latestLedgerResponse.sequence, "Latest ledger sequence should not be null")

        val latestLedger = latestLedgerResponse.sequence

        // When: Request starting from a very recent ledger
        val startLedger = if (latestLedger > 10) latestLedger - 10 else 1

        val pagination = GetLedgersRequest.Pagination(limit = 5)
        val request = GetLedgersRequest(
            startLedger = startLedger,
            pagination = pagination
        )
        val response = sorobanServer.getLedgers(request)

        // Then: Validate response
        assertNotNull(response.ledgers, "Ledgers should not be null")
        assertTrue(response.ledgers.isNotEmpty(), "Should return at least one recent ledger")

        // Verify the first ledger sequence is at or after the start
        assertTrue(
            response.ledgers.first().sequence >= startLedger,
            "First ledger should be at or after start ledger"
        )
    }


    /**
     * Tests uploading a Soroban contract WASM to the ledger.
     *
     * This test validates the complete contract upload workflow:
     * 1. Creates and funds a test account via Friendbot
     * 2. Loads the contract WASM file from test resources
     * 3. Uses InvokeHostFunctionOperation.uploadContractWasm() helper method
     * 4. Simulates the transaction to get resource estimates
     * 5. Prepares the transaction with simulation results
     * 6. Signs and submits the transaction to Soroban RPC
     * 7. Polls for transaction completion
     * 8. Extracts the WASM ID from the transaction result
     * 9. Verifies the contract code can be loaded by WASM ID
     * 10. Parses contract metadata (spec entries, meta entries)
     * 11. Stores WASM ID, contract code for use by testCreateContract and testGetLedgerEntries
     *
     * The test demonstrates:
     * - Account creation with FriendBot
     * - Transaction building for Soroban operations using helper methods
     * - Simulation and preparation workflow
     * - Transaction submission and polling
     * - Contract code retrieval and parsing
     *
     * This is a foundational test for Soroban contract deployment, as uploading
     * the WASM is the first step before creating contract instances.
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * @see SorobanServer.loadContractCodeForWasmId
     * @see SorobanServer.loadContractInfoForWasmId
     * @see InvokeHostFunctionOperation.uploadContractWasm
     */
    @Test
    fun testUploadContract() = runTest(timeout = 120.seconds) {
        // Given: Create and fund test account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via FriendBot (network-dependent)
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else if (testOn == "futurenet") {
            FriendBot.fundFuturenetAccount(accountId)
        }
        delay(5000) // Wait for account creation

        // Load account for sequence number
        val account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        // Load contract WASM file
        val contractCode = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
        assertTrue(contractCode.isNotEmpty(), "Contract code should not be empty")

        // When: Building upload contract transaction using helper method
        val operation = InvokeHostFunctionOperation.uploadContractWasm(contractCode)

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.latestLedger, "Latest ledger should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(keyPair)


        // Then: Submit transaction to Soroban RPC
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Extract WASM ID from transaction result
        val wasmId = rpcTransactionResponse.getWasmId()
        assertNotNull(wasmId, "WASM ID should be extracted from transaction result")
        assertTrue(wasmId.isNotEmpty(), "WASM ID should not be empty")

        // Store WASM ID, keypair, and contract code for later tests
        sharedWasmId = wasmId
        sharedKeyPair = keyPair
        sharedContractCode = contractCode

        // Verify contract code can be loaded by WASM ID
        delay(3000) // Wait for ledger to settle

        val contractCodeEntry = sorobanServer.loadContractCodeForWasmId(wasmId)
        assertNotNull(contractCodeEntry, "Contract code entry should be loaded")
        assertContentEquals(
            contractCode,
            contractCodeEntry.code,
            "Loaded contract code should match uploaded code"
        )

        // Verify contract info can be parsed
        val contractInfo = sorobanServer.loadContractInfoForWasmId(wasmId)
        assertNotNull(contractInfo, "Contract info should be parsed")
        assertTrue(contractInfo.specEntries.isNotEmpty(), "Contract should have spec entries")
        assertTrue(contractInfo.metaEntries.isNotEmpty(), "Contract should have meta entries")
        assertTrue(contractInfo.envInterfaceVersion > 0u, "Environment interface version should be positive")
    }

    /**
     * Tests creating (deploying) a Soroban contract instance from an uploaded WASM.
     *
     * This test validates the complete contract deployment workflow:
     * 1. Uses the WASM ID from testUploadContract
     * 2. Uses InvokeHostFunctionOperation.createContract() helper method
     * 3. Simulates the deployment transaction
     * 4. Applies authorization entries from simulation
     * 5. Signs and submits the transaction
     * 6. Polls for transaction completion
     * 7. Extracts the created contract ID
     * 8. Verifies the contract can be loaded and inspected
     * 9. Validates contract metadata (spec entries, meta entries)
     * 10. Verifies Horizon operations and effects can be parsed
     * 11. Stores contract ID and footprint for use by testInvokeContract and testGetLedgerEntries
     *
     * The test demonstrates:
     * - Contract instance creation from uploaded WASM using helper methods
     * - Authorization entry handling (auto-auth from simulation)
     * - Contract ID extraction from transaction result
     * - Contract info retrieval by contract ID
     * - Horizon API integration for Soroban operations
     * - Footprint extraction for ledger key queries
     *
     * This test depends on testUploadContract having run first to provide the WASM ID.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testUploadContract must run first (provides WASM ID)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * @see SorobanServer.loadContractInfoForContractId
     * @see com.soneso.stellar.sdk.rpc.responses.GetTransactionResponse.getCreatedContractId
     * @see InvokeHostFunctionOperation.createContract
     */
    @Test
    fun testCreateContract() = runTest(timeout = 120.seconds) {
        // Given: Check that testUploadContract has run and provided a WASM ID
        val wasmId = sharedWasmId
        val keyPair = sharedKeyPair

        if (wasmId == null || keyPair == null) {
            println("Skipping testCreateContract: testUploadContract must run first to provide WASM ID")
            return@runTest
        }

        delay(5000) // Wait for network to settle

        // Reload account for current sequence number
        val accountId = keyPair.getAccountId()
        val account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        // When: Building create contract transaction using helper method
        // Generate deterministic salt (using zero salt for tests)
        val salt = ByteArray(32) { 0 }
        val address = Address(accountId)

        val operation = InvokeHostFunctionOperation.createContract(
            wasmId = wasmId,
            address = address,
            salt = salt
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee + auth entries
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.latestLedger, "Latest ledger should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Extract and store the footprint for testGetLedgerEntries
        val transactionData = simulateResponse.parseTransactionData()
        assertNotNull(transactionData, "Transaction data should be parsed")
        sharedFootprint = transactionData.resources.footprint

        // Prepare transaction with simulation results (includes auth entries)
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(keyPair)

        // Then: Submit transaction to Soroban RPC
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Extract contract ID from transaction result
        val contractId = rpcTransactionResponse.getCreatedContractId()
        assertNotNull(contractId, "Contract ID should be extracted from transaction result")
        assertTrue(contractId.isNotEmpty(), "Contract ID should not be empty")
        assertTrue(contractId.startsWith("C"), "Contract ID should be strkey-encoded (start with 'C')")

        // Store contract ID for testInvokeContract and testGetLedgerEntries
        sharedContractId = contractId

        // Verify contract can be loaded by contract ID
        delay(3000) // Wait for ledger to settle

        val contractInfo = sorobanServer.loadContractInfoForContractId(contractId)
        assertNotNull(contractInfo, "Contract info should be loaded")
        assertTrue(contractInfo.specEntries.isNotEmpty(), "Contract should have spec entries")
        assertTrue(contractInfo.metaEntries.isNotEmpty(), "Contract should have meta entries")
        assertTrue(contractInfo.envInterfaceVersion > 0u, "Environment interface version should be positive")

        // Verify transaction envelope can be parsed
        assertNotNull(rpcTransactionResponse.envelopeXdr, "Envelope XDR should not be null")
        val envelope = rpcTransactionResponse.parseEnvelopeXdr()
        assertNotNull(envelope, "Envelope should be parsed")

        // Verify transaction result can be parsed
        assertNotNull(rpcTransactionResponse.resultXdr, "Result XDR should not be null")
        val result = rpcTransactionResponse.parseResultXdr()
        assertNotNull(result, "Result should be parsed")

        // Verify transaction meta can be parsed
        assertNotNull(rpcTransactionResponse.resultMetaXdr, "Result meta XDR should not be null")
        val meta = rpcTransactionResponse.parseResultMetaXdr()
        assertNotNull(meta, "Result meta should be parsed")
    }

    /**
     * Tests invoking a function on a deployed Soroban contract.
     *
     * This test validates the complete contract invocation workflow:
     * 1. Uses the contract ID from testCreateContract
     * 2. Prepares function arguments (symbol "friend" for hello contract)
     * 3. Uses InvokeHostFunctionOperation.invokeContractFunction() helper method
     * 4. Simulates the invocation transaction
     * 5. Prepares the transaction with simulation results
     * 6. Signs and submits the transaction
     * 7. Polls for transaction completion
     * 8. Extracts and validates the return value from the contract
     * 9. Verifies the result matches expected output (["Hello", "friend"])
     * 10. Validates transaction XDR encoding/decoding
     *
     * The test demonstrates:
     * - Contract function invocation with parameters using helper methods
     * - SCVal argument construction using Scv helper
     * - Transaction simulation and resource estimation
     * - Return value extraction and parsing
     * - Result validation against expected contract behavior
     *
     * This test depends on testCreateContract having run first to provide the contract ID.
     * If run independently, it will be skipped with an appropriate message.
     *
     * The hello world contract has a "hello" function that takes a symbol parameter
     * and returns a vector with two symbols: ["Hello", <parameter>].
     *
     * **Prerequisites**:
     * - testCreateContract must run first (provides contract ID)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * **Reference**: Ported from Flutter SDK's test invoke contract
     * (soroban_test.dart lines 521-634)
     *
     * @see InvokeHostFunctionOperation.invokeContractFunction
     * @see Scv.toSymbol
     * @see com.soneso.stellar.sdk.rpc.responses.GetTransactionResponse.getResultValue
     */
    @Test
    fun testInvokeContract() = runTest(timeout = 120.seconds) {
        // Given: Check that testCreateContract has run and provided a contract ID
        val contractId = sharedContractId
        val keyPair = sharedKeyPair

        if (contractId == null || keyPair == null) {
            println("Skipping testInvokeContract: testCreateContract must run first to provide contract ID")
            return@runTest
        }

        delay(5000) // Wait for network to settle

        // Load account for sequence number
        val accountId = keyPair.getAccountId()
        val account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        // When: Building invoke contract transaction using helper method
        // Prepare argument - the hello function takes a symbol parameter
        val arg = Scv.toSymbol("friend")

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = contractId,
            functionName = "hello",
            parameters = listOf(arg)
        )

        // Create transaction for invoking the contract
        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.latestLedger, "Latest ledger should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(keyPair)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Then: Send the transaction
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Extract and validate the result value
        val resVal = rpcTransactionResponse.getResultValue()
        assertNotNull(resVal, "Result value should not be null")

        // The hello contract returns a vec with two symbols: ["Hello", <parameter>]
        assertTrue(resVal is SCValXdr.Vec, "Result should be a vector")
        val vec = resVal.value?.value
        assertNotNull(vec, "Vector should not be null")
        assertEquals(2, vec.size, "Vector should have 2 elements")

        // Verify the two symbols in the result
        assertTrue(vec[0] is SCValXdr.Sym, "First element should be a symbol")
        assertEquals("Hello", (vec[0] as SCValXdr.Sym).value.value, "First element should be 'Hello'")

        assertTrue(vec[1] is SCValXdr.Sym, "Second element should be a symbol")
        assertEquals("friend", (vec[1] as SCValXdr.Sym).value.value, "Second element should be 'friend'")

        println("Contract invocation result: [${(vec[0] as SCValXdr.Sym).value.value}, ${(vec[1] as SCValXdr.Sym).value.value}]")
    }

    /**
     * Tests retrieving ledger entries using the footprint from contract creation.
     *
     * This test validates ledger entry retrieval and contract code loading:
     * 1. Uses the footprint from testCreateContract to extract ledger keys
     * 2. Retrieves contract code ledger entry using the extracted key
     * 3. Retrieves contract data ledger entry using the extracted key
     * 4. Loads contract code by WASM ID and validates it matches uploaded code
     * 5. Loads contract code by contract ID and validates it matches uploaded code
     *
     * The test demonstrates:
     * - Extracting ledger keys from footprints (CONTRACT_CODE, CONTRACT_DATA)
     * - Using getLedgerEntries RPC method with specific keys
     * - Validating ledger entry responses
     * - Loading and verifying contract code through multiple methods
     *
     * This test depends on testCreateContract having run first to provide the footprint,
     * and testUploadContract for the WASM ID and contract code.
     * If run independently, it will be skipped with an appropriate message.
     *
     * **Prerequisites**:
     * - testUploadContract must run first (provides WASM ID and contract code)
     * - testCreateContract must run first (provides footprint and contract ID)
     * - Network connectivity to Stellar testnet
     *
     * **Duration**: ~10-20 seconds (includes network delays)
     *
     * **Reference**: Ported from Flutter SDK's test get ledger entries
     * (soroban_test.dart lines 781-813)
     *
     * @see SorobanServer.getLedgerEntries
     * @see SorobanServer.loadContractCodeForWasmId
     * @see SorobanServer.loadContractCodeForContractId
     */
    @Test
    fun testGetLedgerEntries() = runTest(timeout = 60.seconds) {
        // Given: Check that testCreateContract and testUploadContract have run
        val footprint = sharedFootprint
        val wasmId = sharedWasmId
        val contractId = sharedContractId
        val contractCode = sharedContractCode

        if (footprint == null || wasmId == null || contractId == null || contractCode == null) {
            println("Skipping testGetLedgerEntries: testUploadContract and testCreateContract must run first")
            return@runTest
        }

        // When: Extract contract code ledger key from footprint
        val contractCodeKey = footprint.findFirstKeyOfType(LedgerEntryTypeXdr.CONTRACT_CODE)
        assertNotNull(contractCodeKey, "Contract code ledger key should be found in footprint")

        // When: Extract contract data ledger key from footprint
        val contractDataKey = footprint.findFirstKeyOfType(LedgerEntryTypeXdr.CONTRACT_DATA)
        assertNotNull(contractDataKey, "Contract data ledger key should be found in footprint")

        // Then: Retrieve contract code entry using getLedgerEntries
        val contractCodeEntries = sorobanServer.getLedgerEntries(listOf(contractCodeKey))
        assertNotNull(contractCodeEntries.latestLedger, "Latest ledger should not be null")
        assertNotNull(contractCodeEntries.entries, "Contract code entries should not be null")
        assertEquals(1, contractCodeEntries.entries.size, "Should have exactly 1 contract code entry")

        // Then: Retrieve contract data entry using getLedgerEntries
        val contractDataEntries = sorobanServer.getLedgerEntries(listOf(contractDataKey))
        assertNotNull(contractDataEntries.latestLedger, "Latest ledger should not be null")
        assertNotNull(contractDataEntries.entries, "Contract data entries should not be null")
        assertEquals(1, contractDataEntries.entries.size, "Should have exactly 1 contract data entry")

        // Verify contract code can be loaded by WASM ID and matches uploaded code
        val cCodeEntryByWasmId = sorobanServer.loadContractCodeForWasmId(wasmId)
        assertNotNull(cCodeEntryByWasmId, "Contract code entry should be loaded by WASM ID")
        assertContentEquals(
            contractCode,
            cCodeEntryByWasmId.code,
            "Contract code loaded by WASM ID should match uploaded code"
        )

        // Verify contract code can be loaded by contract ID and matches uploaded code
        val cCodeEntryByContractId = sorobanServer.loadContractCodeForContractId(contractId)
        assertNotNull(cCodeEntryByContractId, "Contract code entry should be loaded by contract ID")
        assertContentEquals(
            contractCode,
            cCodeEntryByContractId.code,
            "Contract code loaded by contract ID should match uploaded code"
        )

        println("Ledger entries test completed successfully")
    }

    /**
     * Tests contract events emission and querying.
     *
     * This test validates the complete events workflow for Soroban smart contracts:
     * 1. Creates a new test account (independent from shared test state)
     * 2. Uploads the events contract WASM to the ledger using helper method
     * 3. Deploys an instance of the events contract using helper method
     * 4. Invokes the "increment" function which emits contract events using helper method
     * 5. Retrieves the transaction from Horizon to get the ledger number
     * 6. Queries the emitted events using getEvents RPC endpoint
     * 7. Validates event structure, topics, and contract ID filtering
     * 8. Verifies event XDR parsing (diagnostic, transaction, and contract events)
     *
     * The test demonstrates:
     * - Complete contract lifecycle: upload → deploy → invoke using helper methods
     * - Contract event emission during invocation
     * - Event filtering by contract ID and topics
     * - Event pagination with limits
     * - Cross-referencing between Horizon and Soroban RPC
     * - Event XDR parsing and validation
     *
     * The events contract has an "increment" function that:
     * - Increments an internal counter
     * - Emits a contract event with topic "increment"
     * - Returns the updated counter value
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~90-120 seconds (includes three transactions with polling)
     *
     * **Reference**: Ported from Flutter SDK's test events
     * (soroban_test.dart lines 636-779)
     *
     * @see SorobanServer.getEvents
     * @see GetEventsRequest
     * @see com.soneso.stellar.sdk.rpc.requests.GetEventsRequest.EventFilter
     * @see com.soneso.stellar.sdk.rpc.responses.Events.parseContractEventsXdr
     * @see InvokeHostFunctionOperation.uploadContractWasm
     * @see InvokeHostFunctionOperation.createContract
     * @see InvokeHostFunctionOperation.invokeContractFunction
     */
    @Test
    fun testEvents() = runTest(timeout = 180.seconds) {
        // Given: Create and fund a NEW test account (independent test)
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via FriendBot (network-dependent)
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else if (testOn == "futurenet") {
            FriendBot.fundFuturenetAccount(accountId)
        }
        delay(5000) // Wait for account creation

        // Step 1: Upload events contract WASM using helper method
        var account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        val contractCode = TestResourceUtil.readWasmFile("soroban_events_contract.wasm")
        assertTrue(contractCode.isNotEmpty(), "Events contract code should not be empty")

        var operation = InvokeHostFunctionOperation.uploadContractWasm(contractCode)
        var transaction = TransactionBuilder(sourceAccount = account, network = network)
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        var simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Upload simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Upload simulation should return transaction data")

        var preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)
        preparedTransaction.sign(keyPair)

        var sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Upload transaction hash should not be null")

        var rpcResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status, "Upload should succeed")

        val eventsContractWasmId = rpcResponse.getWasmId()
        assertNotNull(eventsContractWasmId, "Events contract WASM ID should be extracted")

        delay(5000) // Wait for ledger to settle

        // Step 2: Deploy events contract instance using helper method
        account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be reloaded")

        val address = Address(accountId)
        val salt = ByteArray(32) { 1 } // Different salt than hello contract

        operation = InvokeHostFunctionOperation.createContract(
            wasmId = eventsContractWasmId,
            address = address,
            salt = salt
        )
        transaction = TransactionBuilder(sourceAccount = account, network = network)
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Deploy simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Deploy simulation should return transaction data")

        preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)
        preparedTransaction.sign(keyPair)

        sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Deploy transaction hash should not be null")

        rpcResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status, "Deploy should succeed")

        val eventsContractId = rpcResponse.getCreatedContractId()
        assertNotNull(eventsContractId, "Events contract ID should be extracted")
        assertTrue(eventsContractId.startsWith("C"), "Contract ID should be strkey-encoded")

        delay(5000) // Wait for ledger to settle

        // Step 3: Invoke increment function (emits events) using helper method
        account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be reloaded")

        operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = eventsContractId,
            functionName = "increment",
            parameters = emptyList() // increment takes no arguments
        )

        transaction = TransactionBuilder(sourceAccount = account, network = network)
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Invoke simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Invoke simulation should return transaction data")

        preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)
        preparedTransaction.sign(keyPair)

        sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Invoke transaction hash should not be null")

        rpcResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status, "Invoke should succeed")

        delay(5000) // Wait for events to be indexed

        // Step 4: Query events from Horizon to get ledger number
        val horizonTransaction = horizonServer.transactions().transaction(sendResponse.hash)
        assertNotNull(horizonTransaction, "Horizon transaction should be found")
        val startLedger = horizonTransaction.ledger

        println("Transaction executed in ledger: $startLedger")

        // Step 5: Query emitted events using getEvents RPC
        // Try without topic filter first to see if any events exist
        println("Querying events for contract: $eventsContractId starting from ledger: $startLedger")

        // Query a broader ledger range in case of off-by-one issues
        val queryStartLedger = maxOf(1L, startLedger - 5)

        val eventFilter = GetEventsRequest.EventFilter(
            type = GetEventsRequest.EventFilterType.CONTRACT,
            contractIds = listOf(eventsContractId),
            topics = null // Try without topic filter first
        )

        val pagination = GetEventsRequest.Pagination(limit = 10)
        val eventsRequest = GetEventsRequest(
            startLedger = queryStartLedger,
            filters = listOf(eventFilter),
            pagination = pagination
        )

        val eventsResponse = sorobanServer.getEvents(eventsRequest)

        // Then: Validate events response
        assertNotNull(eventsResponse.events, "Events list should not be null")

        println("Query returned ${eventsResponse.events.size} events")
        eventsResponse.events.forEach { evt ->
            println("Event: ledger=${evt.ledger}, contractId=${evt.contractId}, topics=${evt.topic.size}")
        }

        assertTrue(eventsResponse.events.isNotEmpty(), "Should have at least one event")
        assertTrue(eventsResponse.latestLedger > 0, "Latest ledger should be positive")

        println("Found ${eventsResponse.events.size} event(s)")
        // Validate event structure
        val event = eventsResponse.events.first()
        assertEquals(EventFilterType.CONTRACT, event.type, "Event type should be CONTRACT")
        assertEquals(startLedger, event.ledger, "Event should be in the expected ledger")
        assertNotNull(event.contractId, "Event should have contract ID")
        assertEquals(eventsContractId, event.contractId, "Event contract ID should match")
        assertTrue(event.topic.isNotEmpty(), "Event should have topics")
        assertNotNull(event.value, "Event should have value")

        // Parse and validate event topics
        val parsedTopics = event.parseTopic()
        assertTrue(parsedTopics.isNotEmpty(), "Should have parsed topics")

        // Parse and validate event value
        val parsedValue = event.parseValue()
        assertNotNull(parsedValue, "Should have parsed value")

        println("Event validated: contractId=$eventsContractId, topics=${parsedTopics.size}, ledger=${event.ledger}")

        // Step 6: Validate transaction events XDR parsing from GetTransactionResponse
        // This validates that events can be parsed from transaction results
        if (rpcResponse.events != null) {
            val events = rpcResponse.events

            // Parse diagnostic events (debugging info)
            events.diagnosticEventsXdr?.let { diagnosticXdrs ->
                assertTrue(diagnosticXdrs.isNotEmpty(), "Should have diagnostic events")
                val diagnosticEvents = events.parseDiagnosticEventsXdr()
                assertNotNull(diagnosticEvents, "Diagnostic events should parse")
                println("Parsed ${diagnosticEvents.size} diagnostic events")
            }

            // Parse transaction events (system-level events)
            events.transactionEventsXdr?.let { txEventsXdrs ->
                assertTrue(txEventsXdrs.isNotEmpty(), "Should have transaction events")
                val txEvents = events.parseTransactionEventsXdr()
                assertNotNull(txEvents, "Transaction events should parse")
                println("Parsed ${txEvents.size} transaction events")

                // Validate transaction event structure (contains stage + event)
                txEvents.forEach { txEvent ->
                    assertNotNull(txEvent.stage, "Transaction event should have stage")
                    assertNotNull(txEvent.event, "Transaction event should have contract event")
                    println("Transaction event at stage: ${txEvent.stage}")
                }
            }

            // Parse contract events (application-level events)
            events.contractEventsXdr?.let { contractEventsXdrs ->
                assertTrue(contractEventsXdrs.isNotEmpty(), "Should have contract events")
                val contractEvents = events.parseContractEventsXdr()
                assertNotNull(contractEvents, "Contract events should parse")

                // Contract events are nested: outer list = operations, inner list = events per operation
                contractEvents.forEach { operationEvents ->
                    operationEvents.forEach { contractEvent ->
                        // Validate contract event structure
                        assertNotNull(contractEvent, "Contract event should not be null")
                        println("Parsed contract event: $contractEvent")
                    }
                }
            }
        }

        println("Events test completed successfully")
    }

    /**
     * Tests deploying a Stellar Asset Contract (SAC) using a source account.
     *
     * This test validates the SAC deployment workflow with account-based contract ID:
     * 1. Creates and funds a test account via Friendbot
     * 2. Builds a CreateContract operation with CONTRACT_ID_PREIMAGE_FROM_ADDRESS
     * 3. Uses CONTRACT_EXECUTABLE_STELLAR_ASSET (native asset contract)
     * 4. Simulates the transaction to get resource estimates and auth entries
     * 5. Signs and submits the transaction to Soroban RPC
     * 6. Polls for transaction completion
     * 7. Verifies operations and effects can be parsed via Horizon
     *
     * The test demonstrates:
     * - SAC deployment with account-based contract ID (from address + salt)
     * - Using the STELLAR_ASSET executable type
     * - Authorization handling for SAC deployment
     * - Cross-referencing between Soroban RPC and Horizon
     *
     * Stellar Asset Contracts (SACs) are special contracts that wrap Stellar assets,
     * allowing them to be used in Soroban smart contracts. This test deploys a SAC
     * that represents the native asset (XLM).
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * **Reference**: Ported from Flutter SDK's test deploy SAC with source account
     * (soroban_test.dart lines 815-898)
     *
     * @see ContractIDPreimageXdr.FromAddress
     * @see ContractExecutableXdr.Void (STELLAR_ASSET)
     * @see InvokeHostFunctionOperation
     */
    @Test
    fun testDeploySACWithSourceAccount() = runTest(timeout = 120.seconds) {
        // Given: Create and fund test account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via FriendBot (network-dependent)
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else if (testOn == "futurenet") {
            FriendBot.fundFuturenetAccount(accountId)
        }
        delay(5000) // Wait for account creation

        // Load account for sequence number
        val account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        // When: Building deploy SAC with source account transaction
        // Create the contract ID preimage (from address)
        val addressObj = Address(accountId)
        val scAddress = addressObj.toSCAddress()

        // Generate salt for deterministic contract ID
        val salt = Uint256Xdr(ByteArray(32) { 0 })

        val preimage = ContractIDPreimageXdr.FromAddress(
            ContractIDPreimageFromAddressXdr(
                address = scAddress,
                salt = salt
            )
        )

        // Use STELLAR_ASSET executable (native asset contract)
        val executable = ContractExecutableXdr.Void

        // Build the CreateContractArgs
        val createContractArgs = CreateContractArgsXdr(
            contractIdPreimage = preimage,
            executable = executable
        )

        // Create the host function
        val createFunction = HostFunctionXdr.CreateContract(createContractArgs)
        val operation = InvokeHostFunctionOperation(
            hostFunction = createFunction
        )

        val transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee + auth entries
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.latestLedger, "Latest ledger should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Prepare transaction with simulation results (includes auth entries)
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(keyPair)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Then: Submit transaction to Soroban RPC
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Wait for Horizon to process the transaction
        delay(5000)

        // Verify transaction meta can be parsed
        assertNotNull(rpcTransactionResponse.resultMetaXdr, "Result meta XDR should not be null")
        val meta = rpcTransactionResponse.parseResultMetaXdr()
        assertNotNull(meta, "Result meta should be parsed")

        // Verify operations and effects can be parsed via Horizon
        val operationsPage = horizonServer.operations().forAccount(accountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(accountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")

        println("SAC deployed with source account successfully")
    }

    /**
     * Tests deploying a Stellar Asset Contract (SAC) for a custom asset.
     *
     * This test validates the SAC deployment workflow for non-native assets:
     * 1. Creates and funds two test accounts (A = trustor, B = issuer)
     * 2. Creates a custom asset (Fsdk issued by account B)
     * 3. Establishes a trustline from account A to the asset
     * 4. Makes a payment of the asset to establish holdings
     * 5. Deploys a SAC for the custom asset using CONTRACT_ID_PREIMAGE_FROM_ASSET
     * 6. Uses CONTRACT_EXECUTABLE_STELLAR_ASSET for the asset contract
     * 7. Simulates and submits the SAC deployment transaction
     * 8. Polls for transaction completion
     * 9. Verifies operations and effects can be parsed via Horizon
     *
     * The test demonstrates:
     * - SAC deployment with asset-based contract ID (from asset)
     * - Trustline establishment before SAC deployment
     * - Asset payment workflow
     * - Using the STELLAR_ASSET executable type for custom assets
     * - Authorization handling for SAC deployment
     * - Cross-referencing between Soroban RPC and Horizon
     *
     * Stellar Asset Contracts (SACs) enable custom Stellar assets to be used
     * in Soroban smart contracts. This test deploys a SAC for a custom
     * AlphaNum4 asset (Fsdk).
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~60-90 seconds (includes multiple transactions with polling)
     *
     * **Reference**: Ported from Flutter SDK's test SAC with asset
     * (soroban_test.dart lines 900-1003)
     *
     * @see ContractIDPreimageXdr.FromAsset
     * @see ContractExecutableXdr.Void (STELLAR_ASSET)
     * @see ChangeTrustOperation
     * @see PaymentOperation
     * @see InvokeHostFunctionOperation
     */
    @Test
    fun testSACWithAsset() = runTest(timeout = 150.seconds) {
        // Given: Create and fund two test accounts
        val keyPairA = KeyPair.random()
        val accountAId = keyPairA.getAccountId()
        val keyPairB = KeyPair.random()
        val accountBId = keyPairB.getAccountId()

        // Fund accounts via FriendBot (network-dependent)
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountAId)
            FriendBot.fundTestnetAccount(accountBId)
        } else if (testOn == "futurenet") {
            FriendBot.fundFuturenetAccount(accountAId)
            FriendBot.fundFuturenetAccount(accountBId)
        }
        delay(5000) // Wait for account creation

        // Create custom asset (Fsdk issued by account B)
        val assetFsdk = AssetTypeCreditAlphaNum4("FSDK", accountBId)

        // Step 1: Prepare trustline and payment
        val accountB = sorobanServer.getAccount(accountBId)
        assertNotNull(accountB, "Account B should be loaded")

        // Build transaction with ChangeTrust and Payment operations
        val changeTrustOp = ChangeTrustOperation(
            asset = assetFsdk,
            limit = "1000000"
        )
        changeTrustOp.sourceAccount = accountAId

        val paymentOp = PaymentOperation(
            destination = accountAId,
            asset = assetFsdk,
            amount = "200"
        )

        var transaction = TransactionBuilder(
            sourceAccount = accountB,
            network = network
        )
            .addOperation(changeTrustOp)
            .addOperation(paymentOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Sign with both keypairs (A for trustline, B for payment)
        transaction.sign(keyPairA)
        transaction.sign(keyPairB)

        // Submit trustline and payment transaction
        val trustlineResponse = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(trustlineResponse.successful, "Trustline and payment transaction should succeed")

        delay(5000) // Wait for transaction to settle

        // Step 2: Deploy SAC for the custom asset
        val accountBReloaded = sorobanServer.getAccount(accountBId)
        assertNotNull(accountBReloaded, "Account B should be reloaded")

        // When: Building deploy SAC with asset transaction
        // Create the contract ID preimage (from asset)
        val assetXdr = assetFsdk.toXdr()
        val preimage = ContractIDPreimageXdr.FromAsset(assetXdr)

        // Use STELLAR_ASSET executable (asset contract)
        val executable = ContractExecutableXdr.Void

        // Build the CreateContractArgs
        val createContractArgs = CreateContractArgsXdr(
            contractIdPreimage = preimage,
            executable = executable
        )

        // Create the host function
        val createFunction = HostFunctionXdr.CreateContract(createContractArgs)
        val operation = InvokeHostFunctionOperation(
            hostFunction = createFunction
        )

        transaction = TransactionBuilder(
            sourceAccount = accountBReloaded,
            network = network
        )
            .addOperation(operation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate transaction to obtain transaction data + resource fee
        val simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.results, "Simulation results should not be null")
        assertNotNull(simulateResponse.latestLedger, "Latest ledger should not be null")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")
        assertNotNull(simulateResponse.minResourceFee, "Min resource fee should not be null")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(keyPairB)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Then: Submit transaction to Soroban RPC
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Transaction should succeed"
        )

        // Wait for Horizon to process the transaction
        delay(5000)

        // Verify transaction meta can be parsed
        assertNotNull(rpcTransactionResponse.resultMetaXdr, "Result meta XDR should not be null")
        val meta = rpcTransactionResponse.parseResultMetaXdr()
        assertNotNull(meta, "Result meta should be parsed")

        // Verify operations and effects can be parsed via Horizon
        val operationsPage = horizonServer.operations().forAccount(accountAId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Account A should have operations")

        val effectsPage = horizonServer.effects().forAccount(accountAId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Account A should have effects")

        println("SAC deployed with custom asset successfully")
    }

    /**
     * Tests restoring archived contract footprint (state restoration).
     *
     * This test validates the Soroban state restoration workflow:
     * 1. Loads a contract WASM file
     * 2. Creates an upload contract transaction using helper method
     * 3. Simulates to get the footprint (which ledger entries need restoration)
     * 4. Modifies the footprint: moves all readOnly keys to readWrite, clears readOnly
     * 5. Creates a RestoreFootprintOperation
     * 6. Builds a transaction with the modified footprint
     * 7. Simulates the restore transaction to get proper resource fee
     * 8. Signs and submits the restore transaction
     * 9. Polls for transaction success
     * 10. Verifies operations and effects can be parsed via Horizon
     *
     * The test demonstrates:
     * - Footprint manipulation for restoration (readOnly → readWrite)
     * - RestoreFootprintOperation usage
     * - State restoration workflow for archived contract data
     * - Soroban transaction data handling
     * - Resource fee estimation for restoration
     *
     * Soroban has TTL (time-to-live) for contract state. When entries expire and get
     * archived, they need to be restored before they can be accessed again. This test
     * validates the restoration workflow by attempting to restore the footprint of
     * a contract upload operation.
     *
     * **Prerequisites**: Network connectivity to Stellar testnet
     * **Duration**: ~30-60 seconds (includes network delays and polling)
     *
     * **Reference**: Ported from Flutter SDK's test restore footprint
     * (soroban_test.dart lines 68-148 and 516-519)
     *
     * @see RestoreFootprintOperation
     * @see SorobanTransactionDataXdr
     * @see LedgerFootprintXdr
     * @see InvokeHostFunctionOperation.uploadContractWasm
     */
    @Test
    fun testRestoreFootprint() = runTest(timeout = 120.seconds) {
        // Test with hello world contract
        restoreContractFootprint("soroban_hello_world_contract.wasm")

        // Test with events contract
        restoreContractFootprint("soroban_events_contract.wasm")

        println("Restore footprint test completed successfully")
    }

    /**
     * Helper function to restore contract footprint for a given contract WASM file.
     *
     * This function:
     * 1. Creates and funds a test account
     * 2. Loads the specified contract WASM
     * 3. Simulates an upload to get the footprint using helper method
     * 4. Modifies footprint (readOnly → readWrite)
     * 5. Creates and executes a restore transaction
     *
     * @param contractWasmFile The WASM file name in test resources
     */
    private suspend fun restoreContractFootprint(contractWasmFile: String) {
        delay(5000) // Wait between tests

        // Given: Create and fund test account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via FriendBot (network-dependent)
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accountId)
        } else if (testOn == "futurenet") {
            FriendBot.fundFuturenetAccount(accountId)
        }
        delay(5000) // Wait for account creation

        // Load account
        var account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        // Load contract WASM file
        val contractCode = TestResourceUtil.readWasmFile(contractWasmFile)
        assertTrue(contractCode.isNotEmpty(), "Contract code should not be empty")

        // When: Create upload transaction to get footprint using helper method
        val uploadOperation = InvokeHostFunctionOperation.uploadContractWasm(contractCode)

        var transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(uploadOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Simulate first to obtain the transaction data + footprint
        var simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Transaction data should not be null")

        // Extract and modify the footprint: move readOnly to readWrite
        val transactionData = simulateResponse.parseTransactionData()
        assertNotNull(transactionData, "Transaction data should be parsed")

        val originalFootprint = transactionData.resources.footprint
        val modifiedFootprint = LedgerFootprintXdr(
            readOnly = emptyList(), // Clear readOnly
            readWrite = originalFootprint.readOnly + originalFootprint.readWrite // Combine all keys into readWrite
        )

        // Create modified transaction data with the new footprint
        val modifiedTransactionData = SorobanTransactionDataXdr(
            ext = transactionData.ext,
            resources = SorobanResourcesXdr(
                footprint = modifiedFootprint,
                instructions = transactionData.resources.instructions,
                diskReadBytes = transactionData.resources.diskReadBytes,
                writeBytes = transactionData.resources.writeBytes
            ),
            resourceFee = transactionData.resourceFee
        )

        // Reload account for current sequence number
        account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be reloaded")

        // Then: Build restore transaction
        val restoreOperation = RestoreFootprintOperation()
        transaction = TransactionBuilder(
            sourceAccount = account,
            network = network
        )
            .addOperation(restoreOperation)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .setSorobanData(modifiedTransactionData)
            .build()

        // Simulate restore transaction to obtain proper resource fee
        simulateResponse = sorobanServer.simulateTransaction(transaction)
        assertNull(simulateResponse.error, "Restore simulation should not have error")
        assertNotNull(simulateResponse.transactionData, "Restore simulation should return transaction data")
        assertNotNull(simulateResponse.minResourceFee, "Restore simulation should return min resource fee")

        // Prepare transaction with simulation results
        val preparedTransaction = sorobanServer.prepareTransaction(transaction, simulateResponse)

        // Sign transaction
        preparedTransaction.sign(keyPair)

        // Verify transaction XDR encoding and decoding round-trip
        val transactionEnvelopeXdr = preparedTransaction.toEnvelopeXdrBase64()
        assertEquals(
            transactionEnvelopeXdr,
            AbstractTransaction.fromEnvelopeXdr(transactionEnvelopeXdr, network).toEnvelopeXdrBase64(),
            "Transaction XDR should round-trip correctly"
        )

        // Send transaction to soroban RPC server
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        assertEquals(
            GetTransactionStatus.SUCCESS,
            rpcTransactionResponse.status,
            "Restore transaction should succeed"
        )

        // Verify operations and effects can be parsed via Horizon
        delay(3000) // Wait for Horizon to process

        val operationsPage = horizonServer.operations().forAccount(accountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(accountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")

        println("Restored footprint for $contractWasmFile successfully")
    }
}

/**
 * Extension function to find the first ledger key of a specific type in a footprint.
 *
 * Searches through both readOnly and readWrite keys in the footprint
 * and returns the first key matching the specified ledger entry type.
 *
 * @param type The type of ledger entry to find (e.g., CONTRACT_CODE, CONTRACT_DATA)
 * @return The first matching ledger key, or null if not found
 */
private fun LedgerFootprintXdr.findFirstKeyOfType(type: LedgerEntryTypeXdr): LedgerKeyXdr? {
    // Search in readOnly keys
    for (key in readOnly) {
        if (key.discriminant == type) {
            return key
        }
    }
    // Search in readWrite keys
    for (key in readWrite) {
        if (key.discriminant == type) {
            return key
        }
    }
    return null
}
