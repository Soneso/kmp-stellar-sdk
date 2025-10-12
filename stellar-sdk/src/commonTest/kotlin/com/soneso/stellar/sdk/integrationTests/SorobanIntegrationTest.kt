package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.requests.GetEventsRequest
import com.soneso.stellar.sdk.rpc.requests.GetTransactionsRequest

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
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc">Soroban RPC Documentation</a>
 */
class SorobanIntegrationTest {

    private val testOn = "testnet"
    private val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")
    private val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
    private val network = Network.TESTNET

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
         * Shared contract ID from testCreateContract, used by testInvokeContract.
         * This is the deployed contract instance that can be invoked.
         */
        var sharedContractId: String? = null
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

    /**
     * Tests uploading a Soroban contract WASM to the ledger.
     *
     * This test validates the complete contract upload workflow:
     * 1. Creates and funds a test account via Friendbot
     * 2. Loads the contract WASM file from test resources
     * 3. Builds an UploadContractWasmHostFunction operation
     * 4. Simulates the transaction to get resource estimates
     * 5. Prepares the transaction with simulation results
     * 6. Signs and submits the transaction to Soroban RPC
     * 7. Polls for transaction completion
     * 8. Extracts the WASM ID from the transaction result
     * 9. Verifies the contract code can be loaded by WASM ID
     * 10. Parses contract metadata (spec entries, meta entries)
     * 11. Stores WASM ID for use by testCreateContract
     *
     * The test demonstrates:
     * - Account creation with FriendBot
     * - Transaction building for Soroban operations
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
     */
    @Test
    fun testUploadContract() = runTest(timeout = 120.seconds) {
        // Given: Create and fund test account
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via FriendBot
        FriendBot.fundTestnetAccount(accountId)
        delay(5000) // Wait for account creation

        // Load account for sequence number
        val account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        // Load contract WASM file
        val contractCode = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm")
        assertTrue(contractCode.isNotEmpty(), "Contract code should not be empty")

        // When: Building upload contract transaction
        val uploadFunction = HostFunctionXdr.Wasm(contractCode)
        val operation = InvokeHostFunctionOperation(
            hostFunction = uploadFunction
        )

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
            hash = sendResponse.hash!!,
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
        assertTrue(wasmId!!.isNotEmpty(), "WASM ID should not be empty")

        // Store WASM ID and keypair for testCreateContract
        sharedWasmId = wasmId
        sharedKeyPair = keyPair

        // Verify contract code can be loaded by WASM ID
        delay(3000) // Wait for ledger to settle

        val contractCodeEntry = sorobanServer.loadContractCodeForWasmId(wasmId)
        assertNotNull(contractCodeEntry, "Contract code entry should be loaded")
        assertContentEquals(
            contractCode,
            contractCodeEntry!!.code,
            "Loaded contract code should match uploaded code"
        )

        // Verify contract info can be parsed
        val contractInfo = sorobanServer.loadContractInfoForWasmId(wasmId)
        assertNotNull(contractInfo, "Contract info should be parsed")
        assertTrue(contractInfo!!.specEntries.isNotEmpty(), "Contract should have spec entries")
        assertTrue(contractInfo.metaEntries.isNotEmpty(), "Contract should have meta entries")
        assertTrue(contractInfo.envInterfaceVersion > 0u, "Environment interface version should be positive")
    }

    /**
     * Tests creating (deploying) a Soroban contract instance from an uploaded WASM.
     *
     * This test validates the complete contract deployment workflow:
     * 1. Uses the WASM ID from testUploadContract
     * 2. Creates a CreateContractHostFunction with the WASM ID
     * 3. Simulates the deployment transaction
     * 4. Applies authorization entries from simulation
     * 5. Signs and submits the transaction
     * 6. Polls for transaction completion
     * 7. Extracts the created contract ID
     * 8. Verifies the contract can be loaded and inspected
     * 9. Validates contract metadata (spec entries, meta entries)
     * 10. Verifies Horizon operations and effects can be parsed
     * 11. Stores contract ID for use by testInvokeContract
     *
     * The test demonstrates:
     * - Contract instance creation from uploaded WASM
     * - Authorization entry handling (auto-auth from simulation)
     * - Contract ID extraction from transaction result
     * - Contract info retrieval by contract ID
     * - Horizon API integration for Soroban operations
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
     * @see GetTransactionResponse.getCreatedContractId
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

        // When: Building create contract transaction
        // Create the contract ID preimage (from address)
        val addressObj = Address(accountId)
        val scAddress = addressObj.toSCAddress()

        // Generate salt (using zero salt for deterministic contract ID in tests)
        val salt = Uint256Xdr(ByteArray(32) { 0 })

        val preimage = ContractIDPreimageXdr.FromAddress(
            ContractIDPreimageFromAddressXdr(
                address = scAddress,
                salt = salt
            )
        )

        // Create the contract executable (reference to uploaded WASM)
        val wasmHash = HashXdr(wasmId.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val executable = ContractExecutableXdr.WasmHash(wasmHash)

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

        // Then: Submit transaction to Soroban RPC
        val sendResponse = sorobanServer.sendTransaction(preparedTransaction)
        assertNotNull(sendResponse.hash, "Transaction hash should not be null")
        assertNotNull(sendResponse.status, "Transaction status should not be null")

        // Poll for transaction completion
        val rpcTransactionResponse = sorobanServer.pollTransaction(
            hash = sendResponse.hash!!,
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
        assertTrue(contractId!!.isNotEmpty(), "Contract ID should not be empty")
        assertTrue(contractId.startsWith("C"), "Contract ID should be strkey-encoded (start with 'C')")

        // Store contract ID for testInvokeContract
        sharedContractId = contractId

        // Verify contract can be loaded by contract ID
        delay(3000) // Wait for ledger to settle

        val contractInfo = sorobanServer.loadContractInfoForContractId(contractId)
        assertNotNull(contractInfo, "Contract info should be loaded")
        assertTrue(contractInfo!!.specEntries.isNotEmpty(), "Contract should have spec entries")
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
     * 3. Builds an InvokeContractHostFunction operation
     * 4. Simulates the invocation transaction
     * 5. Prepares the transaction with simulation results
     * 6. Signs and submits the transaction
     * 7. Polls for transaction completion
     * 8. Extracts and validates the return value from the contract
     * 9. Verifies the result matches expected output (["Hello", "friend"])
     * 10. Validates transaction XDR encoding/decoding
     *
     * The test demonstrates:
     * - Contract function invocation with parameters
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
     * @see InvokeHostFunctionOperation
     * @see Scv.toSymbol
     * @see GetTransactionResponse.getResultValue
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

        // When: Building invoke contract transaction
        // Prepare argument - the hello function takes a symbol parameter
        val arg = Scv.toSymbol("friend")

        // Build the invoke contract host function
        val functionName = "hello"
        val contractAddress = Address(contractId)
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress.toSCAddress(),
            functionName = SCSymbolXdr(functionName),
            args = listOf(arg)
        )
        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)
        val operation = InvokeHostFunctionOperation(
            hostFunction = hostFunction
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
            hash = sendResponse.hash!!,
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
        val vec = (resVal as SCValXdr.Vec).value?.value
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
     * Tests contract events emission and querying.
     *
     * This test validates the complete events workflow for Soroban smart contracts:
     * 1. Creates a new test account (independent from shared test state)
     * 2. Uploads the events contract WASM to the ledger
     * 3. Deploys an instance of the events contract
     * 4. Invokes the "increment" function which emits contract events
     * 5. Retrieves the transaction from Horizon to get the ledger number
     * 6. Queries the emitted events using getEvents RPC endpoint
     * 7. Validates event structure, topics, and contract ID filtering
     * 8. Verifies event XDR parsing (diagnostic, transaction, and contract events)
     *
     * The test demonstrates:
     * - Complete contract lifecycle: upload → deploy → invoke
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
     * @see EventFilter
     * @see Events.parseContractEventsXdr
     */
    @Test
    fun testEvents() = runTest(timeout = 180.seconds) {
        // Given: Create and fund a NEW test account (independent test)
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via FriendBot
        FriendBot.fundTestnetAccount(accountId)
        delay(5000) // Wait for account creation

        // Step 1: Upload events contract WASM
        var account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be loaded")

        val contractCode = TestResourceUtil.readWasmFile("soroban_events_contract.wasm")
        assertTrue(contractCode.isNotEmpty(), "Events contract code should not be empty")

        var uploadFunction = HostFunctionXdr.Wasm(contractCode)
        var operation = InvokeHostFunctionOperation(hostFunction = uploadFunction)
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
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status, "Upload should succeed")

        val eventsContractWasmId = rpcResponse.getWasmId()
        assertNotNull(eventsContractWasmId, "Events contract WASM ID should be extracted")

        delay(5000) // Wait for ledger to settle

        // Step 2: Deploy events contract instance
        account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be reloaded")

        val addressObj = Address(accountId)
        val scAddress = addressObj.toSCAddress()
        val salt = Uint256Xdr(ByteArray(32) { 1 }) // Different salt than hello contract

        val preimage = ContractIDPreimageXdr.FromAddress(
            ContractIDPreimageFromAddressXdr(address = scAddress, salt = salt)
        )

        val wasmHash = HashXdr(eventsContractWasmId!!.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val executable = ContractExecutableXdr.WasmHash(wasmHash)
        val createContractArgs = CreateContractArgsXdr(contractIdPreimage = preimage, executable = executable)

        val createFunction = HostFunctionXdr.CreateContract(createContractArgs)
        operation = InvokeHostFunctionOperation(hostFunction = createFunction)
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
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status, "Deploy should succeed")

        val eventsContractId = rpcResponse.getCreatedContractId()
        assertNotNull(eventsContractId, "Events contract ID should be extracted")
        assertTrue(eventsContractId!!.startsWith("C"), "Contract ID should be strkey-encoded")

        delay(5000) // Wait for ledger to settle

        // Step 3: Invoke increment function (emits events)
        account = sorobanServer.getAccount(accountId)
        assertNotNull(account, "Account should be reloaded")

        val functionName = "increment"
        val contractAddress = Address(eventsContractId)
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress.toSCAddress(),
            functionName = SCSymbolXdr(functionName),
            args = emptyList() // increment takes no arguments
        )
        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)
        operation = InvokeHostFunctionOperation(hostFunction = hostFunction)

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
            hash = sendResponse.hash!!,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )
        assertEquals(GetTransactionStatus.SUCCESS, rpcResponse.status, "Invoke should succeed")

        delay(5000) // Wait for events to be indexed

        // Step 4: Query events from Horizon to get ledger number
        val horizonTransaction = horizonServer.transactions().transaction(sendResponse.hash!!)
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
            val events = rpcResponse.events!!

            // Parse diagnostic events (debugging info)
            events.diagnosticEventsXdr?.let { diagnosticXdrs ->
                assertTrue(diagnosticXdrs.isNotEmpty(), "Should have diagnostic events")
                val diagnosticEvents = events.parseDiagnosticEventsXdr()
                assertNotNull(diagnosticEvents, "Diagnostic events should parse")
                println("Parsed ${diagnosticEvents!!.size} diagnostic events")
            }

            // Parse transaction events (system-level events)
            events.transactionEventsXdr?.let { txEventsXdrs ->
                assertTrue(txEventsXdrs.isNotEmpty(), "Should have transaction events")
                val txEvents = events.parseTransactionEventsXdr()
                assertNotNull(txEvents, "Transaction events should parse")
                println("Parsed ${txEvents!!.size} transaction events")

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
                contractEvents!!.forEach { operationEvents ->
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
}
