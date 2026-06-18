package com.soneso.stellar.sdk.rpc

import com.soneso.stellar.sdk.Account
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Asset
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MuxedAccount
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.TransactionBuilderAccount
import com.soneso.stellar.sdk.contract.SorobanContractInfo
import com.soneso.stellar.sdk.contract.SorobanContractParser
import com.soneso.stellar.sdk.rpc.exception.AccountNotFoundException
import com.soneso.stellar.sdk.rpc.exception.PrepareTransactionException
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.rpc.requests.*
import com.soneso.stellar.sdk.rpc.responses.*
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import com.soneso.stellar.sdk.platformDelay
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Main client class for connecting to a Soroban RPC instance and making requests.
 *
 * Soroban RPC is Stellar's smart contract platform RPC server that provides APIs to:
 * - Simulate and submit smart contract transactions
 * - Query contract state and ledger entries
 * - Monitor transaction status and events
 * - Fetch network metadata and fee statistics
 *
 * ## JSON-RPC 2.0 Protocol
 *
 * All Soroban RPC methods use JSON-RPC 2.0 over HTTP. This client handles:
 * - Request/response serialization
 * - Error handling and exception mapping
 * - Automatic request ID generation
 * - HTTP connection management
 *
 * ## Basic Usage
 *
 * ```kotlin
 * // Create server instance
 * val server = SorobanServer("https://soroban-testnet.stellar.org:443")
 *
 * // Check server health
 * val health = server.getHealth()
 * println("Server status: ${health.status}")
 *
 * // Get account for transaction building
 * val account = server.getAccount("GABC...")
 *
 * // Simulate a transaction
 * val simulation = server.simulateTransaction(transaction)
 *
 * // Prepare transaction with simulation results
 * val prepared = server.prepareTransaction(transaction)
 *
 * // Sign and submit
 * prepared.sign(keypair)
 * val response = server.sendTransaction(prepared)
 *
 * // Poll for completion
 * val result = server.pollTransaction(response.hash!!)
 *
 * // Close when done
 * server.close()
 * ```
 *
 * ## Transaction Preparation Flow
 *
 * Soroban transactions require simulation before submission to:
 * 1. Calculate resource requirements (CPU, memory, storage)
 * 2. Determine authorization entries needed
 * 3. Estimate resource fees
 *
 * The typical flow is:
 * 1. Build transaction with [com.soneso.stellar.sdk.TransactionBuilder]
 * 2. Call [simulateTransaction] to get resource estimates
 * 3. Call [prepareTransaction] to apply estimates to transaction
 * 4. Sign the prepared transaction
 * 5. Submit with [sendTransaction]
 * 6. Poll for completion with [pollTransaction]
 *
 * Or use the simplified flow:
 * ```kotlin
 * val prepared = server.prepareTransaction(transaction)  // Simulates internally
 * prepared.sign(keypair)
 * val response = server.sendTransaction(prepared)
 * ```
 *
 * ## Resource Management
 *
 * This class implements [AutoCloseable] and should be closed when no longer needed
 * to release HTTP client resources:
 *
 * ```kotlin
 * SorobanServer(url).use { server ->
 *     // Use server
 * }
 * ```
 *
 * ## Error Handling
 *
 * Methods throw:
 * - [SorobanRpcException] - RPC-level errors (server returns error response)
 * - [PrepareTransactionException] - Transaction preparation failures
 * - [kotlinx.coroutines.TimeoutCancellationException] - Request timeout
 * - [Exception] - Network/connection errors
 *
 * @property serverUrl The Soroban RPC server URL (e.g., "https://soroban-testnet.stellar.org:443")
 * @property httpClient The Ktor HTTP client for making requests
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference">Soroban RPC API Reference</a>
 * @see <a href="https://developers.stellar.org/docs/smart-contracts">Stellar Smart Contracts</a>
 */
class SorobanServer(
    private val serverUrl: String,
    private val httpClient: HttpClient = defaultHttpClient()
) : AutoCloseable {

    companion object {
        /**
         * Default timeout for transaction submission requests in milliseconds.
         */
        private const val SUBMIT_TRANSACTION_TIMEOUT = 60_000L

        /**
         * Default connection timeout in milliseconds.
         */
        private const val CONNECT_TIMEOUT = 10_000L

        /**
         * Creates a default HTTP client configured for Soroban RPC.
         *
         * The client is configured with:
         * - JSON content negotiation with lenient parsing
         * - Request timeout of 60 seconds
         * - Connection timeout of 10 seconds
         * - Client identification headers (X-Client-Name, X-Client-Version)
         *
         * Client identification headers help Stellar server operators track SDK usage
         * and identify SDK-specific issues.
         *
         * @return Configured HttpClient instance
         */
        fun defaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = false
                        encodeDefaults = false
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = SUBMIT_TRANSACTION_TIMEOUT
                    connectTimeoutMillis = CONNECT_TIMEOUT
                }
                install(DefaultRequest) {
                    header("X-Client-Name", "kmp-stellar-sdk")
                    header("X-Client-Version", com.soneso.stellar.sdk.Util.getSdkVersion())
                }
            }
        }
    }

    /**
     * Generates a unique request ID for JSON-RPC requests.
     *
     * Uses UUID v4 for guaranteed uniqueness across concurrent requests.
     *
     * @return A unique request ID string
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun generateRequestId(): String {
        return Uuid.random().toString()
    }

    /**
     * Sends a JSON-RPC request to the Soroban RPC server.
     *
     * This is the core method that handles:
     * - Request serialization
     * - HTTP POST to server
     * - Response deserialization
     * - Error detection and exception throwing
     *
     * @param T The type of the request parameters
     * @param R The type of the response result
     * @param method The JSON-RPC method name
     * @param params The method parameters (null for parameter-less methods)
     * @return The result object from the response
     * @throws SorobanRpcException If the server returns an error
     * @throws Exception If network/connection errors occur
     */
    private suspend inline fun <reified T, reified R> sendRequest(
        method: String,
        params: T?
    ): R {
        val requestId = generateRequestId()

        // Build JSON-RPC request manually to ensure correct serialization
        val requestJson = buildString {
            append("{")
            append("\"jsonrpc\":\"2.0\",")
            append("\"id\":\"$requestId\",")
            append("\"method\":\"$method\"")
            if (params != null) {
                // Serialize params to JSON using reified type parameter
                val json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = false
                }
                val paramsJson = json.encodeToString(kotlinx.serialization.serializer<T>(), params)
                append(",\"params\":$paramsJson")
            }
            append("}")
        }

        try {
            val response: SorobanRpcResponse<R> = httpClient.post(serverUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestJson)
            }.body()

            // Check for RPC error
            if (response.error != null) {
                throw SorobanRpcException(
                    errorCode = response.error.code,
                    errorMessage = response.error.message,
                    data = response.error.data
                )
            }

            // Return result (should always be non-null if no error)
            return response.result
                ?: throw IllegalStateException("Response missing result field (method: $method, id: $requestId)")

        } catch (e: SorobanRpcException) {
            // Re-throw Soroban RPC exceptions as-is
            throw e
        } catch (e: SerializationException) {
            // JSON parsing failed
            throw IllegalArgumentException("Failed to parse response for method $method", e)
        }
    }

    /**
     * Fetches a minimal set of current info about a Stellar account.
     *
     * This is needed to get the current sequence number for building transactions with
     * [com.soneso.stellar.sdk.TransactionBuilder]. The account data is fetched from ledger entries.
     *
     * ## Example
     *
     * ```kotlin
     * val account = server.getAccount("GABC...")
     * val transaction = TransactionBuilder(account, Network.TESTNET)
     *     .addOperation(...)
     *     .build()
     * ```
     *
     * @param address The account address to load (G... format, muxed accounts supported)
     * @return A [TransactionBuilderAccount] containing the sequence number and address
     * @throws IllegalArgumentException If the account address is invalid
     * @throws AccountNotFoundException If the account does not exist
     * @throws SorobanRpcException If the RPC request fails
     * @throws Exception If network errors occur
     *
     * @see TransactionBuilderAccount
     * @see Account
     */
    suspend fun getAccount(address: String): TransactionBuilderAccount {
        // Parse muxed account to get underlying account ID
        val muxedAccount = MuxedAccount(address)
        val accountId = muxedAccount.accountId

        // Create ledger key for account entry
        val keypair = KeyPair.fromAccountId(accountId)
        val ledgerKeyAccount = LedgerKeyAccountXdr(
            accountId = keypair.getXdrAccountId()
        )
        val ledgerKey = LedgerKeyXdr.Account(ledgerKeyAccount)

        // Fetch ledger entry
        val response = getLedgerEntries(listOf(ledgerKey))
        val entries = response.entries

        if (entries.isNullOrEmpty()) {
            throw AccountNotFoundException(accountId)
        }

        // Parse account data from XDR
        val ledgerEntryData = LedgerEntryDataXdr.fromXdrBase64(entries[0].xdr)
        val accountEntry = when (ledgerEntryData) {
            is LedgerEntryDataXdr.Account -> ledgerEntryData.value
            else -> throw IllegalStateException("Expected Account entry, got ${ledgerEntryData.discriminant}")
        }
        val sequenceNumber = accountEntry.seqNum.value.value

        return Account(address, sequenceNumber)
    }

    /**
     * Performs a general node health check.
     *
     * Returns the health status of the RPC server, including ledger sync information.
     * Use this to verify server connectivity before making other requests.
     *
     * ## Example
     *
     * ```kotlin
     * val health = server.getHealth()
     * if (health.status == "healthy") {
     *     println("Latest ledger: ${health.latestLedger}")
     * }
     * ```
     *
     * @return Health status including latest/oldest ledger info
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getHealth">getHealth documentation</a>
     */
    suspend fun getHealth(): GetHealthResponse {
        return sendRequest<Unit?, GetHealthResponse>("getHealth", null)
    }

    /**
     * Gets statistics for charged inclusion fees.
     *
     * Returns fee distribution statistics for both regular and Soroban transactions.
     * Use this to estimate appropriate fees for your transactions.
     *
     * ## Example
     *
     * ```kotlin
     * val feeStats = server.getFeeStats()
     * val p50 = feeStats.sorobanInclusionFee.p50.toLong()
     * println("Median Soroban fee: $p50 stroops")
     * ```
     *
     * @return Fee statistics including percentile distribution
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getFeeStats">getFeeStats documentation</a>
     */
    suspend fun getFeeStats(): GetFeeStatsResponse {
        return sendRequest<Unit?, GetFeeStatsResponse>("getFeeStats", null)
    }

    /**
     * Fetches metadata about the network.
     *
     * Returns network information including passphrase, protocol version, and friendbot URL.
     * Essential for verifying you're connected to the correct network.
     *
     * ## Example
     *
     * ```kotlin
     * val network = server.getNetwork()
     * println("Network: ${network.passphrase}")
     * println("Protocol: ${network.protocolVersion}")
     * ```
     *
     * @return Network metadata including passphrase and protocol version
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getNetwork">getNetwork documentation</a>
     */
    suspend fun getNetwork(): GetNetworkResponse {
        return sendRequest<Unit?, GetNetworkResponse>("getNetwork", null)
    }

    /**
     * Fetches version information about the RPC server and Captive Core.
     *
     * Returns version strings, commit hashes, and build timestamps for both
     * the RPC server and its embedded Stellar Core instance.
     *
     * @return Version information for RPC and Captive Core
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getVersionInfo">getVersionInfo documentation</a>
     */
    suspend fun getVersionInfo(): GetVersionInfoResponse {
        return sendRequest<Unit?, GetVersionInfoResponse>("getVersionInfo", null)
    }

    /**
     * Fetches the latest ledger metadata.
     *
     * Returns information about the most recent ledger including sequence number,
     * hash, and protocol version. Useful for determining current network state.
     *
     * @return Latest ledger metadata
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getLatestLedger">getLatestLedger documentation</a>
     */
    suspend fun getLatestLedger(): GetLatestLedgerResponse {
        return sendRequest<Unit?, GetLatestLedgerResponse>("getLatestLedger", null)
    }

    /**
     * Reads the current value of ledger entries directly.
     *
     * Allows direct inspection of ledger state including contracts, contract code,
     * account data, and other ledger entries. This is a low-level API - prefer
     * higher-level methods like [getAccount] or [getContractData] when possible.
     *
     * ## Example
     *
     * ```kotlin
     * val keys = listOf(ledgerKey1, ledgerKey2)
     * val response = server.getLedgerEntries(keys)
     * response.entries?.forEach { entry ->
     *     println("Key: ${entry.key}")
     *     println("XDR: ${entry.xdr}")
     * }
     * ```
     *
     * @param keys Collection of ledger keys to fetch (at least one required)
     * @return Ledger entries response with current values
     * @throws IllegalArgumentException If keys collection is empty
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getLedgerEntries">getLedgerEntries documentation</a>
     */
    suspend fun getLedgerEntries(keys: Collection<LedgerKeyXdr>): GetLedgerEntriesResponse {
        require(keys.isNotEmpty()) { "At least one key must be provided" }

        // Convert LedgerKey objects to base64-encoded XDR strings
        val xdrKeys = keys.map { key -> key.toXdrBase64() }

        val request = GetLedgerEntriesRequest(xdrKeys)
        return sendRequest("getLedgerEntries", request)
    }

    /**
     * Reads contract data for a specific key.
     *
     * Convenience method for fetching contract storage entries. This is a higher-level
     * alternative to [getLedgerEntries] specifically for contract data.
     *
     * ## Example
     *
     * ```kotlin
     * val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
     * val key = SCValXdr.Symbol("balance")
     * val entry = server.getContractData(contractId, key, Durability.PERSISTENT)
     * entry?.let {
     *     println("Value: ${it.xdr}")
     * }
     * ```
     *
     * @param contractId The contract address (C... format)
     * @param key The contract data key
     * @param durability Storage durability (TEMPORARY or PERSISTENT)
     * @return Ledger entry result if found, null otherwise
     * @throws SorobanRpcException If the RPC request fails
     */
    suspend fun getContractData(
        contractId: String,
        key: SCValXdr,
        durability: Durability
    ): GetLedgerEntriesResponse.LedgerEntryResult? {
        // Convert durability enum to XDR
        val contractDataDurability = when (durability) {
            Durability.TEMPORARY -> ContractDataDurabilityXdr.TEMPORARY
            Durability.PERSISTENT -> ContractDataDurabilityXdr.PERSISTENT
        }

        // Parse contract address
        val address = Address(contractId)

        // Create ledger key for contract data
        val ledgerKeyContractData = LedgerKeyContractDataXdr(
            contract = address.toSCAddress(),
            key = key,
            durability = contractDataDurability
        )
        val ledgerKey = LedgerKeyXdr.ContractData(ledgerKeyContractData)

        // Fetch ledger entry
        val response = getLedgerEntries(listOf(ledgerKey))
        val entries = response.entries

        if (entries.isNullOrEmpty()) {
            return null
        }

        return entries[0]
    }

    /**
     * Fetches the details of a submitted transaction.
     *
     * Returns transaction status, result, and metadata. Use this to check if a
     * submitted transaction has been included in a ledger.
     *
     * ## Status Values
     *
     * - SUCCESS: Transaction succeeded and is in a ledger
     * - FAILED: Transaction failed and is in a ledger
     * - NOT_FOUND: Transaction not found (pending or too old)
     *
     * ## Example
     *
     * ```kotlin
     * val response = server.getTransaction(hash)
     * when (response.status) {
     *     GetTransactionStatus.SUCCESS -> println("Success!")
     *     GetTransactionStatus.FAILED -> println("Failed: ${response.resultXdr}")
     *     GetTransactionStatus.NOT_FOUND -> println("Not yet in ledger")
     * }
     * ```
     *
     * @param hash Transaction hash as hex string
     * @return Transaction details including status and results
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getTransaction">getTransaction documentation</a>
     */
    suspend fun getTransaction(hash: String): GetTransactionResponse {
        val request = GetTransactionRequest(hash)
        return sendRequest("getTransaction", request)
    }

    /**
     * Polls for transaction completion.
     *
     * Repeatedly calls [getTransaction] until the transaction reaches a final state
     * (SUCCESS or FAILED) or the maximum attempts is reached.
     *
     * ## Example
     *
     * ```kotlin
     * // Poll with defaults (30 attempts, 1 second between)
     * val result = server.pollTransaction(hash)
     *
     * // Custom polling strategy
     * val result = server.pollTransaction(
     *     hash = hash,
     *     maxAttempts = 60,
     *     sleepStrategy = { attempt -> (attempt * 500).toLong() } // Exponential backoff
     * )
     * ```
     *
     * @param hash Transaction hash as hex string
     * @param maxAttempts Maximum number of polling attempts (default: 30)
     * @param sleepStrategy Function mapping attempt number to sleep duration in milliseconds
     * @return Final transaction response (may still be NOT_FOUND if max attempts reached)
     * @throws IllegalArgumentException If maxAttempts is less than or equal to 0
     * @throws SorobanRpcException If any RPC request fails
     */
    suspend fun pollTransaction(
        hash: String,
        maxAttempts: Int = 30,
        sleepStrategy: (Int) -> Long = { 1000L }
    ): GetTransactionResponse {
        require(maxAttempts > 0) { "maxAttempts must be greater than 0" }

        var attempts = 0
        var lastResponse: GetTransactionResponse? = null

        while (attempts < maxAttempts) {
            try {
                val response = getTransaction(hash)
                lastResponse = response

                // Check if transaction reached final state
                if (response.status != GetTransactionStatus.NOT_FOUND) {
                    return response
                }
            } catch (e: Exception) {
                // Ignore temporary RPC errors and keep polling (matches Flutter SDK behavior)
                // This handles network glitches, rate limiting, and other transient issues
                // without stopping the polling loop
            }

            attempts++
            if (attempts < maxAttempts) {
                val sleepTime = sleepStrategy(attempts)
                // Use platformDelay to ensure real wall-clock delay.
                // Plain delay() is skipped by kotlinx.coroutines.test.runTest
                // virtual time, and on JS Dispatchers.Default shares the same
                // event loop so withContext(Dispatchers.Default) doesn't escape it.
                platformDelay(sleepTime)
            }
        }

        return lastResponse!!
    }

    /**
     * Gets a paginated list of transactions.
     *
     * Returns detailed transaction information starting from a specific ledger.
     * Results can be paginated using the cursor from the response.
     *
     * @param request The request parameters including start ledger and pagination
     * @return Paginated transaction list with cursor for next page
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getTransactions">getTransactions documentation</a>
     */
    suspend fun getTransactions(request: GetTransactionsRequest): GetTransactionsResponse {
        return sendRequest("getTransactions", request)
    }

    /**
     * Gets a paginated list of ledgers.
     *
     * Returns detailed ledger information starting from a specific ledger.
     * Results can be paginated using the cursor parameter.
     *
     * @param request The request parameters including start ledger, cursor, and limit
     * @return Paginated ledger list with cursor for next page
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getLedgers">getLedgers documentation</a>
     */
    suspend fun getLedgers(request: GetLedgersRequest): GetLedgersResponse {
        return sendRequest("getLedgers", request)
    }

    /**
     * Fetches all events matching the given filters.
     *
     * Returns contract events emitted by smart contracts. Essential for monitoring
     * contract activity and debugging contract behavior.
     *
     * ## Example
     *
     * ```kotlin
     * val request = GetEventsRequest(
     *     startLedger = 1000,
     *     filters = listOf(
     *         EventFilter(
     *             type = EventFilterType.CONTRACT,
     *             contractIds = listOf("CCJZ5D..."),
     *             topics = listOf(listOf("transfer"))
     *         )
     *     )
     * )
     * val events = server.getEvents(request)
     * ```
     *
     * @param request The event filter parameters
     * @return List of matching events
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getEvents">getEvents documentation</a>
     */
    suspend fun getEvents(request: GetEventsRequest): GetEventsResponse {
        return sendRequest("getEvents", request)
    }

    /**
     * Simulates a transaction to preview its effects.
     *
     * This is essential for Soroban transactions to:
     * - Calculate resource requirements (CPU, memory, storage)
     * - Determine authorization entries needed
     * - Estimate resource fees
     * - Preview contract invocation results
     *
     * The transaction should contain exactly one operation of type:
     * - InvokeHostFunctionOperation
     * - ExtendFootprintTTLOperation
     * - RestoreFootprintOperation
     *
     * Any existing footprint in the transaction is ignored during simulation.
     *
     * ## Example
     *
     * ```kotlin
     * val simulation = server.simulateTransaction(transaction)
     * if (simulation.error != null) {
     *     println("Simulation failed: ${simulation.error}")
     * } else {
     *     println("Min resource fee: ${simulation.minResourceFee}")
     *     println("Results: ${simulation.results}")
     * }
     * ```
     *
     * @param transaction The transaction to simulate
     * @param resourceConfig Optional resource configuration for additional headroom
     * @param authMode Optional authorization mode (ENFORCE, RECORD, RECORD_ALLOW_NONROOT)
     * @return Simulation results including costs, footprint, and results
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/simulateTransaction">simulateTransaction documentation</a>
     */
    suspend fun simulateTransaction(
        transaction: Transaction,
        resourceConfig: SimulateTransactionRequest.ResourceConfig? = null,
        authMode: SimulateTransactionRequest.AuthMode? = null
    ): SimulateTransactionResponse {
        val transactionXdr = transaction.toEnvelopeXdr().toXdrBase64()
        val request = SimulateTransactionRequest(
            transaction = transactionXdr,
            resourceConfig = resourceConfig,
            authMode = authMode
        )
        return sendRequest("simulateTransaction", request)
    }

    /**
     * Prepares a transaction by simulating it and applying resource estimates.
     *
     * This is a convenience method that:
     * 1. Calls [simulateTransaction] to get resource estimates
     * 2. Calls [prepareTransaction] with the simulation results
     *
     * The returned transaction is ready for signing and submission. The fee will be
     * updated to include estimated resource fees.
     *
     * ## Example
     *
     * ```kotlin
     * val prepared = server.prepareTransaction(transaction)
     * prepared.sign(keypair)
     * server.sendTransaction(prepared)
     * ```
     *
     * @param transaction The transaction to prepare
     * @return A copy of the transaction with footprint and fees populated
     * @throws PrepareTransactionException If simulation fails
     * @throws SorobanRpcException If the RPC request fails
     */
    suspend fun prepareTransaction(transaction: Transaction): Transaction {
        val simulation = simulateTransaction(transaction)
        return prepareTransaction(transaction, simulation)
    }

    /**
     * Prepares a transaction using existing simulation results.
     *
     * Applies simulation results to the transaction without re-simulating.
     * Use this when you've already called [simulateTransaction] and want to
     * inspect results before preparing.
     *
     * The method:
     * - Validates simulation succeeded (no error)
     * - Applies resource footprint from simulation
     * - Updates authorization entries (if applicable)
     * - Calculates total fee (base fee + resource fee)
     *
     * @param transaction The transaction to prepare
     * @param simulateResponse The simulation results to apply
     * @return A copy of the transaction with simulation results applied
     * @throws PrepareTransactionException If simulation contains an error
     */
    suspend fun prepareTransaction(
        transaction: Transaction,
        simulateResponse: SimulateTransactionResponse
    ): Transaction {
        // Check for simulation error
        if (simulateResponse.error != null) {
            throw PrepareTransactionException(
                message = "Simulation failed: ${simulateResponse.error}",
                simulationError = simulateResponse.error
            )
        }

        // Delegate to static helper
        return assembleTransaction(transaction, simulateResponse)
    }

    /**
     * Submits a transaction to the Stellar network.
     *
     * Unlike Horizon, Soroban RPC does not wait for transaction completion.
     * It validates and enqueues the transaction, then returns immediately.
     * Use [getTransaction] or [pollTransaction] to check transaction status.
     *
     * ## Example
     *
     * ```kotlin
     * val response = server.sendTransaction(transaction)
     * when (response.status) {
     *     SendTransactionStatus.PENDING -> {
     *         // Poll for completion
     *         val result = server.pollTransaction(response.hash!!)
     *     }
     *     SendTransactionStatus.DUPLICATE -> println("Already submitted")
     *     SendTransactionStatus.ERROR -> println("Error: ${response.errorResultXdr}")
     * }
     * ```
     *
     * @param transaction The signed transaction to submit
     * @return Submission response with status and transaction hash
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/sendTransaction">sendTransaction documentation</a>
     */
    suspend fun sendTransaction(transaction: Transaction): SendTransactionResponse {
        val transactionXdr = transaction.toEnvelopeXdr().toXdrBase64()
        val request = SendTransactionRequest(transactionXdr)
        return sendRequest("sendTransaction", request)
    }

    /**
     * Fetches the balance of a Stellar Asset Contract (SAC).
     *
     * Stellar Asset Contracts are smart contracts that wrap classic Stellar assets,
     * allowing them to be used in Soroban smart contracts. This method retrieves
     * the balance of a specific contract for a given asset.
     *
     * ## Example
     *
     * ```kotlin
     * val contractId = "GABC..."
     * val asset = Asset.native()
     * val response = server.getSACBalance(contractId, asset, Network.TESTNET)
     * response.balanceEntry?.let {
     *     println("Balance: ${it.amount}")
     *     println("Authorized: ${it.authorized}")
     * }
     * ```
     *
     * @param contractId The contract address holding the asset
     * @param asset The Stellar asset to check balance for
     * @param network The network (needed for asset contract ID calculation)
     * @return Balance information if the contract holds the asset
     * @throws IllegalArgumentException If contractId is not a valid contract address
     * @throws SorobanRpcException If the RPC request fails
     *
     * @see <a href="https://developers.stellar.org/docs/tokens/stellar-asset-contract">Stellar Asset Contract documentation</a>
     */
    suspend fun getSACBalance(
        contractId: String,
        asset: Asset,
        network: Network
    ): GetSACBalanceResponse {
        require(StrKey.isValidContract(contractId)) {
            "Invalid contract ID: $contractId"
        }

        // Build the ledger key for the balance entry
        val assetContractAddress = Address(asset.getContractId(network))
        val balanceKey = Scv.toVec(
            listOf(
                Scv.toSymbol("Balance"),
                Address(contractId).toSCVal()
            )
        )

        val ledgerKey = LedgerKeyXdr.ContractData(
            LedgerKeyContractDataXdr(
                contract = assetContractAddress.toSCAddress(),
                key = balanceKey,
                durability = ContractDataDurabilityXdr.PERSISTENT
            )
        )

        // Fetch the ledger entry
        val response = getLedgerEntries(listOf(ledgerKey))
        val entries = response.entries

        if (entries.isNullOrEmpty()) {
            return GetSACBalanceResponse(
                latestLedger = response.latestLedger,
                balanceEntry = null
            )
        }

        // Parse the balance data from XDR
        val entry = entries[0]
        val ledgerEntryData = LedgerEntryDataXdr.fromXdrBase64(entry.xdr)
        val contractData = when (ledgerEntryData) {
            is LedgerEntryDataXdr.ContractData -> ledgerEntryData.value
            else -> throw IllegalStateException("Expected ContractData entry, got ${ledgerEntryData.discriminant}")
        }

        // Extract balance information from the map
        val balanceMap = Scv.fromMap(contractData.`val`)
        val amountKey = Scv.toSymbol("amount")
        val authorizedKey = Scv.toSymbol("authorized")
        val clawbackKey = Scv.toSymbol("clawback")

        // Convert Int128 to string for amount (Scv.fromInt128 returns BigInteger directly)
        val amountBigInt = Scv.fromInt128(balanceMap[amountKey]!!)

        val balanceEntry = GetSACBalanceResponse.BalanceEntry(
            amount = amountBigInt.toString(),
            authorized = Scv.fromBoolean(balanceMap[authorizedKey]!!),
            clawback = Scv.fromBoolean(balanceMap[clawbackKey]!!),
            lastModifiedLedgerSeq = entry.lastModifiedLedger,
            liveUntilLedgerSeq = entry.liveUntilLedger
        )

        return GetSACBalanceResponse(
            latestLedger = response.latestLedger,
            balanceEntry = balanceEntry
        )
    }

    /**
     * Loads the contract code entry (including WASM bytecode) for a given WASM ID.
     *
     * This method fetches the contract code from the ledger by its hash (WASM ID).
     * The WASM ID is typically obtained from the transaction response after uploading
     * a contract.
     *
     * ## Example
     *
     * ```kotlin
     * val wasmId = "abc123..."  // Hex-encoded hash
     * val codeEntry = server.loadContractCodeForWasmId(wasmId)
     * codeEntry?.let {
     *     println("Contract code size: ${it.code.dataValue.size} bytes")
     * }
     * ```
     *
     * @param wasmId The contract WASM ID as a hex-encoded hash string
     * @return The contract code entry if found, null otherwise
     * @throws SorobanRpcException If the RPC request fails
     * @throws IllegalArgumentException If wasmId is not a valid hex string
     *
     * @see loadContractCodeForContractId
     * @see loadContractInfoForWasmId
     */
    suspend fun loadContractCodeForWasmId(wasmId: String): ContractCodeEntryXdr? {
        // Create ledger key for contract code
        val ledgerKey = LedgerKeyXdr.ContractCode(
            LedgerKeyContractCodeXdr(
                hash = HashXdr(com.soneso.stellar.sdk.Util.hexToBytes(wasmId))
            )
        )

        // Fetch ledger entry
        val response = getLedgerEntries(listOf(ledgerKey))
        val entries = response.entries

        if (entries.isNullOrEmpty()) {
            return null
        }

        // Parse contract code from XDR
        val ledgerEntryData = LedgerEntryDataXdr.fromXdrBase64(entries[0].xdr)
        return when (ledgerEntryData) {
            is LedgerEntryDataXdr.ContractCode -> ledgerEntryData.value
            else -> throw IllegalStateException("Expected ContractCode entry, got ${ledgerEntryData.discriminant}")
        }
    }

    /**
     * Loads the contract code entry (including WASM bytecode) for a given contract ID.
     *
     * This method:
     * 1. Fetches the contract instance to get the WASM hash
     * 2. Fetches the contract code using the WASM hash
     *
     * ## Example
     *
     * ```kotlin
     * val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
     * val codeEntry = server.loadContractCodeForContractId(contractId)
     * codeEntry?.let {
     *     println("Contract code size: ${it.code.dataValue.size} bytes")
     * }
     * ```
     *
     * @param contractId The contract address (C... format)
     * @return The contract code entry if found, null otherwise
     * @throws SorobanRpcException If the RPC request fails
     * @throws IllegalArgumentException If contractId is not a valid contract address
     *
     * @see loadContractCodeForWasmId
     * @see loadContractInfoForContractId
     */
    suspend fun loadContractCodeForContractId(contractId: String): ContractCodeEntryXdr? {
        // Create ledger key for contract instance
        val ledgerKey = LedgerKeyXdr.ContractData(
            LedgerKeyContractDataXdr(
                contract = Address(contractId).toSCAddress(),
                key = Scv.toLedgerKeyContractInstance(),
                durability = ContractDataDurabilityXdr.PERSISTENT
            )
        )

        // Fetch contract instance
        val response = getLedgerEntries(listOf(ledgerKey))
        val entries = response.entries

        if (entries.isNullOrEmpty()) {
            return null
        }

        // Parse contract data to get WASM hash
        val ledgerEntryData = LedgerEntryDataXdr.fromXdrBase64(entries[0].xdr)
        val contractData = when (ledgerEntryData) {
            is LedgerEntryDataXdr.ContractData -> ledgerEntryData.value
            else -> throw IllegalStateException("Expected ContractData entry, got ${ledgerEntryData.discriminant}")
        }

        // Extract WASM hash from contract instance
        val instance = when (contractData.`val`) {
            is SCValXdr.Instance -> contractData.`val`.value
            else -> throw IllegalStateException("Expected Instance SCVal, got ${contractData.`val`.discriminant}")
        }

        val wasmHash = when (instance.executable) {
            is ContractExecutableXdr.WasmHash -> instance.executable.value.value
            else -> return null
        }

        // Convert hash to hex string
        val wasmId = com.soneso.stellar.sdk.Util.bytesToHex(wasmHash)

        // Load contract code by WASM ID
        return loadContractCodeForWasmId(wasmId)
    }

    /**
     * Loads contract information (Environment Meta, Contract Spec, Contract Meta) for a given WASM ID.
     *
     * This method:
     * 1. Fetches the contract code using [loadContractCodeForWasmId]
     * 2. Parses the WASM bytecode to extract contract metadata
     *
     * The returned information includes:
     * - Environment interface version (protocol version)
     * - Contract specification entries (functions, structs, events, etc.)
     * - Contract metadata (key-value pairs for application/tooling use)
     *
     * ## Example
     *
     * ```kotlin
     * val wasmId = "abc123..."  // From transaction response
     * val contractInfo = server.loadContractInfoForWasmId(wasmId)
     * contractInfo?.let {
     *     println("Protocol version: ${it.envInterfaceVersion}")
     *     println("Functions: ${it.specEntries.size}")
     * }
     * ```
     *
     * @param wasmId The contract WASM ID as a hex-encoded hash string
     * @return The parsed contract information if found, null if the contract code doesn't exist
     * @throws SorobanRpcException If the RPC request fails
     * @throws com.soneso.stellar.sdk.contract.SorobanContractParserException If the WASM bytecode cannot be parsed
     * @throws IllegalArgumentException If wasmId is not a valid hex string
     *
     * @see loadContractCodeForWasmId
     * @see loadContractInfoForContractId
     * @see SorobanContractInfo
     */
    suspend fun loadContractInfoForWasmId(wasmId: String): SorobanContractInfo? {
        val contractCodeEntry = loadContractCodeForWasmId(wasmId) ?: return null
        val byteCode = contractCodeEntry.code
        return SorobanContractParser.parseContractByteCode(byteCode)
    }

    /**
     * Loads contract information (Environment Meta, Contract Spec, Contract Meta) for a given contract ID.
     *
     * This method:
     * 1. Fetches the contract instance and code using [loadContractCodeForContractId]
     * 2. Parses the WASM bytecode to extract contract metadata
     *
     * The returned information includes:
     * - Environment interface version (protocol version)
     * - Contract specification entries (functions, structs, events, etc.)
     * - Contract metadata (key-value pairs for application/tooling use)
     *
     * ## Example
     *
     * ```kotlin
     * val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
     * val contractInfo = server.loadContractInfoForContractId(contractId)
     * contractInfo?.let {
     *     println("Protocol version: ${it.envInterfaceVersion}")
     *     println("Spec entries: ${it.specEntries.size}")
     *     println("Metadata: ${it.metaEntries}")
     * }
     * ```
     *
     * @param contractId The contract address (C... format)
     * @return The parsed contract information if found, null if the contract doesn't exist
     * @throws SorobanRpcException If the RPC request fails
     * @throws com.soneso.stellar.sdk.contract.SorobanContractParserException If the WASM bytecode cannot be parsed
     * @throws IllegalArgumentException If contractId is not a valid contract address
     *
     * @see loadContractCodeForContractId
     * @see loadContractInfoForWasmId
     * @see SorobanContractInfo
     */
    suspend fun loadContractInfoForContractId(contractId: String): SorobanContractInfo? {
        val contractCodeEntry = loadContractCodeForContractId(contractId) ?: return null
        val byteCode = contractCodeEntry.code
        return SorobanContractParser.parseContractByteCode(byteCode)
    }

    /**
     * Closes the HTTP client and releases resources.
     *
     * Should be called when the server instance is no longer needed.
     * After calling close(), no more requests can be made.
     */
    override fun close() {
        httpClient.close()
    }

    /**
     * Durability level for contract data storage.
     *
     * Soroban supports two durability levels for contract state:
     * - **TEMPORARY**: Cheaper storage that expires unless refreshed
     * - **PERSISTENT**: More expensive storage that persists indefinitely
     *
     * @see <a href="https://developers.stellar.org/docs/learn/smart-contract-internals/state-archival">State Archival documentation</a>
     */
    enum class Durability {
        /**
         * Temporary storage that requires periodic TTL extension.
         * Less expensive but data will be archived if not refreshed.
         */
        TEMPORARY,

        /**
         * Persistent storage that remains available indefinitely.
         * More expensive but no TTL maintenance required.
         */
        PERSISTENT
    }
}

/**
 * Assembles a transaction by applying simulation results.
 *
 * This is a static helper function that applies simulation results to a transaction
 * without requiring a SorobanServer instance. Useful when working with pre-fetched
 * simulation results.
 *
 * The function:
 * 1. Validates the transaction is a Soroban transaction
 * 2. Calculates total fee (classic fee + resource fee)
 * 3. Updates operation auth entries (for InvokeHostFunctionOperation)
 * 4. Applies sorobanData from simulation
 *
 * ## Example
 *
 * ```kotlin
 * val simulation = server.simulateTransaction(tx)
 * val prepared = assembleTransaction(tx, simulation)
 * ```
 *
 * @param transaction The original transaction
 * @param simulateResponse The simulation results to apply
 * @return A new transaction with simulation results applied
 * @throws IllegalArgumentException If transaction is not a Soroban transaction
 * @throws IllegalArgumentException If simulation results are invalid for the operation
 */
fun assembleTransaction(
    transaction: Transaction,
    simulateResponse: SimulateTransactionResponse
): Transaction {
    require(transaction.isSorobanTransaction()) {
        "unsupported transaction: must contain exactly one InvokeHostFunctionOperation, ExtendFootprintTTLOperation, or RestoreFootprintOperation"
    }

    // Calculate classic fee (excluding resource fees from existing soroban data)
    var classicFeeNum = transaction.fee
    transaction.sorobanData?.let { sorobanData ->
        classicFeeNum -= sorobanData.resourceFee.value
    }

    // Add minimum resource fee from simulation
    val minResourceFeeNum = simulateResponse.minResourceFee ?: 0L
    val totalFee = classicFeeNum + minResourceFeeNum

    // Get the operation (should be exactly one)
    var operation = transaction.operations[0]

    // For InvokeHostFunctionOperation, update auth entries if needed
    if (operation is InvokeHostFunctionOperation) {
        // If the operation is an InvokeHostFunctionOperation, we need to update the auth entries
        // if existing entries are empty and the simulation result contains auth entries.
        if (simulateResponse.results == null || simulateResponse.results.size != 1) {
            throw IllegalArgumentException(
                "invalid simulateTransactionResponse: results must contain exactly one element if the operation is an InvokeHostFunctionOperation"
            )
        }

        val simulateHostFunctionResult = simulateResponse.results[0]

        // Check if we need to update auth entries
        val existingEntries = operation.auth
        if (existingEntries.isEmpty() &&
            simulateHostFunctionResult.auth != null &&
            simulateHostFunctionResult.auth.isNotEmpty()
        ) {
            // Parse auth entries from base64-encoded XDR strings
            val authorizationEntries = simulateHostFunctionResult.auth.map { authXdr ->
                SorobanAuthorizationEntryXdr.fromXdrBase64(authXdr)
            }

            // Create new operation with updated auth entries
            operation = InvokeHostFunctionOperation(
                hostFunction = operation.hostFunction,
                auth = authorizationEntries
            ).apply {
                sourceAccount = operation.sourceAccount
            }
        }
    }

    // Parse soroban data from simulation
    val sorobanData = SorobanTransactionDataXdr.fromXdrBase64(simulateResponse.transactionData!!)

    // Create new transaction with updated values
    return Transaction(
        sourceAccount = transaction.sourceAccount,
        fee = totalFee,
        sequenceNumber = transaction.sequenceNumber,
        operations = listOf(operation),
        memo = transaction.memo,
        preconditions = transaction.preconditions,
        sorobanData = sorobanData,
        network = transaction.network
    )
}
