package com.soneso.sample

import com.stellar.sdk.*
import com.stellar.sdk.rpc.*
import com.stellar.sdk.scval.Scv
import com.stellar.sdk.xdr.*



/**
 * Demonstrates Soroban smart contract interactions using the KMP Stellar SDK.
 *
 * This class shows how to:
 * - Connect to Soroban RPC (testnet)
 * - Invoke contract functions
 * - Simulate transactions
 * - Query contract data and events
 * - Handle transaction results
 *
 * The examples use the Stellar testnet "hello world" contract which is publicly available
 * and doesn't require authentication, making it perfect for demonstrations.
 */
class SorobanDemo {

    companion object {
        /**
         * Testnet Soroban RPC endpoint
         */
        const val TESTNET_RPC_URL = "https://soroban-testnet.stellar.org:443"

        /**
         * The hello world contract deployed on testnet.
         * This contract has a "hello" function that takes a string parameter
         * and returns a vector of strings ["Hello", <param>].
         *
         * Source: https://github.com/stellar/soroban-examples/tree/main/hello_world
         */
        const val HELLO_CONTRACT_ID = "CDZJVZWCY4NFGHCCZMX6QW5AK3ET5L3UUAYBVNDYOXDLQXW7PHXGYOBJ"

        /**
         * Function name in the hello world contract
         */
        const val HELLO_FUNCTION = "hello"
    }

    /**
     * Helper function to create an SCAddress from a contract ID string (C... format).
     */
    private fun contractIdToSCAddress(contractId: String): SCAddressXdr {
        val contractBytes = StrKey.decodeContract(contractId)
        return SCAddressXdr.ContractId(
            ContractIDXdr(HashXdr(contractBytes))
        )
    }

    /**
     * Demonstrates reading contract data without submitting a transaction.
     *
     * This example:
     * 1. Connects to Soroban RPC testnet
     * 2. Gets network information
     * 3. Gets latest ledger info
     * 4. Gets health status
     *
     * @return Demo result with network information
     */
    suspend fun demonstrateNetworkInfo(): SorobanDemoResult {
        val steps = mutableListOf<String>()
        val startTime = currentTimeMillis()

        try {
            steps.add("Connecting to Soroban RPC testnet...")
            val server = SorobanServer(TESTNET_RPC_URL)

            try {
                steps.add("Fetching network information...")
                val network = server.getNetwork()
                steps.add("Network passphrase: ${network.passphrase}")
                steps.add("Protocol version: ${network.protocolVersion}")

                steps.add("Fetching latest ledger...")
                val latestLedger = server.getLatestLedger()
                steps.add("Latest ledger: ${latestLedger.sequence}")
                steps.add("Ledger hash: ${latestLedger.hash}")

                steps.add("Checking server health...")
                val health = server.getHealth()
                steps.add("Health status: ${health.status}")

                val duration = currentTimeMillis() - startTime
                return SorobanDemoResult(
                    success = true,
                    message = "Successfully connected to Soroban testnet",
                    steps = steps,
                    duration = duration,
                    data = mapOf(
                        "passphrase" to network.passphrase,
                        "protocol" to network.protocolVersion.toString(),
                        "latestLedger" to latestLedger.sequence.toString(),
                        "health" to health.status
                    )
                )
            } finally {
                server.close()
            }
        } catch (e: Exception) {
            steps.add("Error: ${e.message}")
            val duration = currentTimeMillis() - startTime
            return SorobanDemoResult(
                success = false,
                message = "Failed to connect to network: ${e.message}",
                steps = steps,
                duration = duration
            )
        }
    }

    /**
     * Demonstrates simulating a contract invocation without executing it.
     *
     * This example:
     * 1. Connects to Soroban RPC
     * 2. Creates an invoke host function operation
     * 3. Builds a transaction
     * 4. Simulates the transaction to see what it would do
     *
     * Note: This doesn't require a funded account since we're only simulating.
     *
     * @param inputName The name to pass to the hello function
     * @return Demo result with simulation results
     */
    suspend fun demonstrateSimulation(inputName: String = "World"): SorobanDemoResult {
        val steps = mutableListOf<String>()
        val startTime = currentTimeMillis()

        try {
            steps.add("Creating simulation demo for input: $inputName")
            steps.add("Connecting to Soroban RPC...")
            val server = SorobanServer(TESTNET_RPC_URL)

            try {
                // Create a temporary keypair (we don't need a real account for simulation)
                steps.add("Creating temporary keypair for simulation...")
                val sourceKeypair = KeyPair.random()
                steps.add("Using account: ${sourceKeypair.getAccountId()}")

                // Build the contract invocation
                steps.add("Building contract invocation...")
                steps.add("Contract: $HELLO_CONTRACT_ID")
                steps.add("Function: $HELLO_FUNCTION")
                steps.add("Parameter: \"$inputName\"")

                // Create the contract address
                val contractAddress = contractIdToSCAddress(HELLO_CONTRACT_ID)

                // Build function arguments
                val functionName = Scv.toSymbol(HELLO_FUNCTION)
                val args = listOf(Scv.toString(inputName))

                // Create InvokeContractArgs
                val invokeContractArgs = InvokeContractArgsXdr(
                    contractAddress = contractAddress,
                    functionName = functionName,
                    args = args
                )

                // Create host function
                val hostFunction = HostFunctionXdr.InvokeContract(invokeContractArgs)

                // Create the operation
                val operation = InvokeHostFunctionOperation(
                    hostFunction = hostFunction,
                    auth = emptyList()
                )

                // For simulation, we need a dummy account
                // Use a simple implementation since we're not actually submitting
                val source = object : TransactionBuilderAccount {
                    override val accountId: String = sourceKeypair.getAccountId()
                    override val keypair: KeyPair = sourceKeypair
                    private var _sequence = 0L
                    override val sequenceNumber: Long get() = _sequence
                    override fun setSequenceNumber(seqNum: Long) { _sequence = seqNum }
                    override fun getIncrementedSequenceNumber(): Long = _sequence + 1
                    override fun incrementSequenceNumber() { _sequence++ }
                }

                steps.add("Building transaction...")
                val transaction = Transaction(
                    sourceAccount = source,
                    fee = 100L,
                    sequenceNumber = 0L,
                    operations = listOf(operation),
                    memo = Memo.NONE,
                    preconditions = null,
                    sorobanData = null,
                    network = Network.TESTNET
                )

                steps.add("Simulating transaction...")
                val simulation = server.simulateTransaction(transaction)

                // Check simulation results
                if (simulation.error != null) {
                    steps.add("Simulation error: ${simulation.error}")
                    val duration = currentTimeMillis() - startTime
                    return SorobanDemoResult(
                        success = false,
                        message = "Simulation failed: ${simulation.error}",
                        steps = steps,
                        duration = duration
                    )
                }

                steps.add("Simulation successful!")
                steps.add("Cost: ${simulation.cost?.cpuInsns ?: 0} CPU instructions")
                steps.add("Memory: ${simulation.cost?.memBytes ?: 0} bytes")
                steps.add("Min resource fee: ${simulation.minResourceFee ?: 0} stroops")

                // Parse the result
                val results = simulation.results
                if (!results.isNullOrEmpty()) {
                    val result = results[0]
                    steps.add("Result XDR: ${result.xdr}")

                    // Try to parse the return value
                    try {
                        val returnValue = Scv.fromXdrBase64(result.xdr)
                        steps.add("Return value type: ${returnValue::class.simpleName}")

                        // The hello contract returns a vector of strings
                        val vec = Scv.fromVec(returnValue)
                        val strings = vec.map { Scv.fromString(it) }
                        steps.add("Return value: ${strings.joinToString(", ")}")
                    } catch (e: Exception) {
                        steps.add("Could not parse return value: ${e.message}")
                    }
                }

                val duration = currentTimeMillis() - startTime
                return SorobanDemoResult(
                    success = true,
                    message = "Successfully simulated contract invocation",
                    steps = steps,
                    duration = duration,
                    data = mapOf(
                        "cpuInsns" to (simulation.cost?.cpuInsns?.toString() ?: "0"),
                        "memBytes" to (simulation.cost?.memBytes?.toString() ?: "0"),
                        "minResourceFee" to (simulation.minResourceFee?.toString() ?: "0"),
                        "latestLedger" to (simulation.latestLedger?.toString() ?: "0")
                    )
                )
            } finally {
                server.close()
            }
        } catch (e: Exception) {
            steps.add("Error: ${e.message}")
            val duration = currentTimeMillis() - startTime
            return SorobanDemoResult(
                success = false,
                message = "Simulation failed: ${e.message}",
                steps = steps,
                duration = duration
            )
        }
    }

    /**
     * Demonstrates querying contract events.
     *
     * This shows how to retrieve events emitted by smart contracts.
     *
     * @return Demo result with event information
     */
    suspend fun demonstrateEventQuery(): SorobanDemoResult {
        val steps = mutableListOf<String>()
        val startTime = currentTimeMillis()

        try {
            steps.add("Querying contract events...")
            steps.add("Connecting to Soroban RPC...")
            val server = SorobanServer(TESTNET_RPC_URL)

            try {
                // Get latest ledger to establish a query range
                val latestLedger = server.getLatestLedger()
                val startLedger = if (latestLedger.sequence > 100) latestLedger.sequence - 100 else 1

                steps.add("Querying events from ledger $startLedger to ${latestLedger.sequence}")

                // Query recent events
                val request = GetEventsRequest(
                    startLedger = startLedger,
                    filters = emptyList(), // Get all events
                    pagination = GetEventsRequest.PaginationOptions(limit = 10)
                )

                val events = server.getEvents(request)
                steps.add("Found ${events.events?.size ?: 0} events")

                events.events?.take(3)?.forEachIndexed { index, event ->
                    steps.add("Event ${index + 1}:")
                    steps.add("  Ledger: ${event.ledger}")
                    steps.add("  Type: ${event.type}")
                    steps.add("  Contract ID: ${event.contractId ?: "N/A"}")
                }

                val duration = currentTimeMillis() - startTime
                return SorobanDemoResult(
                    success = true,
                    message = "Successfully queried contract events",
                    steps = steps,
                    duration = duration,
                    data = mapOf(
                        "eventCount" to (events.events?.size?.toString() ?: "0"),
                        "latestLedger" to latestLedger.sequence.toString()
                    )
                )
            } finally {
                server.close()
            }
        } catch (e: Exception) {
            steps.add("Error: ${e.message}")
            val duration = currentTimeMillis() - startTime
            return SorobanDemoResult(
                success = false,
                message = "Failed to query events: ${e.message}",
                steps = steps,
                duration = duration
            )
        }
    }

    /**
     * Demonstrates the complete contract invocation flow.
     *
     * This shows the full process of:
     * 1. Building a contract invocation transaction
     * 2. Simulating to get resource requirements
     * 3. Preparing the transaction with simulation results
     * 4. Showing what would be needed to sign and submit
     *
     * Note: This doesn't actually submit the transaction since that would require
     * a funded testnet account. It demonstrates the preparation steps.
     *
     * @param inputName The name to pass to the hello function
     * @return Demo result with preparation details
     */
    suspend fun demonstrateFullInvocationFlow(inputName: String = "Stellar"): SorobanDemoResult {
        val steps = mutableListOf<String>()
        val startTime = currentTimeMillis()

        try {
            steps.add("Demonstrating full contract invocation flow")
            steps.add("Input parameter: $inputName")
            steps.add("")

            steps.add("Step 1: Connect to Soroban RPC")
            val server = SorobanServer(TESTNET_RPC_URL)

            try {
                steps.add("Step 2: Create keypair (in production, use a funded account)")
                val sourceKeypair = KeyPair.random()
                steps.add("Account ID: ${sourceKeypair.getAccountId()}")
                steps.add("")

                steps.add("Step 3: Build the contract invocation")
                val contractAddress = contractIdToSCAddress(HELLO_CONTRACT_ID)
                val functionName = Scv.toSymbol(HELLO_FUNCTION)
                val args = listOf(Scv.toString(inputName))

                val invokeContractArgs = InvokeContractArgsXdr(
                    contractAddress = contractAddress,
                    functionName = functionName,
                    args = args
                )

                val hostFunction = HostFunctionXdr.InvokeContract(invokeContractArgs)
                val operation = InvokeHostFunctionOperation(
                    hostFunction = hostFunction,
                    auth = emptyList()
                )

                steps.add("Contract: $HELLO_CONTRACT_ID")
                steps.add("Function: $HELLO_FUNCTION")
                steps.add("Arguments: [\"$inputName\"]")
                steps.add("")

                steps.add("Step 4: Create dummy account for simulation")
                val source = object : TransactionBuilderAccount {
                    override val accountId: String = sourceKeypair.getAccountId()
                    override val keypair: KeyPair = sourceKeypair
                    private var _sequence = 0L
                    override val sequenceNumber: Long get() = _sequence
                    override fun setSequenceNumber(seqNum: Long) { _sequence = seqNum }
                    override fun getIncrementedSequenceNumber(): Long = _sequence + 1
                    override fun incrementSequenceNumber() { _sequence++ }
                }

                steps.add("Step 5: Build initial transaction")
                val unpreparedTx = Transaction(
                    sourceAccount = source,
                    fee = 100L,
                    sequenceNumber = 0L,
                    operations = listOf(operation),
                    memo = Memo.NONE,
                    preconditions = null,
                    sorobanData = null,
                    network = Network.TESTNET
                )
                steps.add("Initial fee: ${unpreparedTx.fee} stroops")
                steps.add("")

                steps.add("Step 6: Simulate transaction")
                val simulation = server.simulateTransaction(unpreparedTx)

                if (simulation.error != null) {
                    throw Exception("Simulation failed: ${simulation.error}")
                }

                steps.add("Simulation successful!")
                steps.add("CPU instructions: ${simulation.cost?.cpuInsns ?: 0}")
                steps.add("Memory bytes: ${simulation.cost?.memBytes ?: 0}")
                steps.add("Min resource fee: ${simulation.minResourceFee ?: 0} stroops")
                steps.add("")

                steps.add("Step 7: Prepare transaction with simulation results")
                val preparedTx = server.prepareTransaction(unpreparedTx, simulation)
                steps.add("Updated fee: ${preparedTx.fee} stroops (includes resource fees)")
                steps.add("Transaction is now ready to sign and submit")
                steps.add("")

                steps.add("Step 8: What happens next (in production):")
                steps.add("  - Sign the transaction: preparedTx.sign(sourceKeypair)")
                steps.add("  - Submit: server.sendTransaction(preparedTx)")
                steps.add("  - Poll for result: server.pollTransaction(hash)")
                steps.add("  - Parse return value from transaction result")
                steps.add("")

                // Parse expected result
                val results = simulation.results
                if (!results.isNullOrEmpty()) {
                    steps.add("Expected return value:")
                    try {
                        val returnValue = Scv.fromXdrBase64(results[0].xdr)
                        val vec = Scv.fromVec(returnValue)
                        val strings = vec.map { Scv.fromString(it) }
                        steps.add("  ${strings.joinToString(", ") { "\"$it\"" }}")
                    } catch (e: Exception) {
                        steps.add("  Could not parse: ${e.message}")
                    }
                }

                val duration = currentTimeMillis() - startTime
                return SorobanDemoResult(
                    success = true,
                    message = "Transaction prepared successfully (ready to sign and submit)",
                    steps = steps,
                    duration = duration,
                    data = mapOf(
                        "unpreparedFee" to unpreparedTx.fee.toString(),
                        "preparedFee" to preparedTx.fee.toString(),
                        "cpuInsns" to (simulation.cost?.cpuInsns?.toString() ?: "0"),
                        "memBytes" to (simulation.cost?.memBytes?.toString() ?: "0")
                    )
                )
            } finally {
                server.close()
            }
        } catch (e: Exception) {
            steps.add("Error: ${e.message}")
            val duration = currentTimeMillis() - startTime
            return SorobanDemoResult(
                success = false,
                message = "Failed: ${e.message}",
                steps = steps,
                duration = duration
            )
        }
    }

    /**
     * Platform-specific time implementation.
     * This will be provided via expect/actual pattern.
     */
    private fun currentTimeMillis(): Long {
        // Placeholder - will use platform-specific implementation
        return 0L
    }
}

/**
 * Result of a Soroban demo operation.
 */
data class SorobanDemoResult(
    val success: Boolean,
    val message: String,
    val steps: List<String>,
    val duration: Long,
    val data: Map<String, String> = emptyMap()
)
