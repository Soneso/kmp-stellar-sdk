# Soroban RPC Usage Guide

This guide demonstrates how to use the Soroban RPC client in the Kotlin Multiplatform Stellar SDK to interact with smart contracts on the Stellar network.

## Table of Contents

- [Quick Start](#quick-start)
- [Connection Setup](#connection-setup)
- [Common Usage Patterns](#common-usage-patterns)
  - [Contract Invocation](#contract-invocation)
  - [Transaction Simulation](#transaction-simulation)
  - [Event Querying](#event-querying)
  - [Contract Data Queries](#contract-data-queries)
- [Error Handling](#error-handling)
- [Advanced Topics](#advanced-topics)
  - [Custom HTTP Client](#custom-http-client)
  - [Transaction Polling Strategies](#transaction-polling-strategies)
  - [SAC Token Operations](#sac-token-operations)
- [Platform-Specific Notes](#platform-specific-notes)
- [API Reference](#api-reference)

## Quick Start

```kotlin
import com.stellar.sdk.*
import com.stellar.sdk.rpc.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create Soroban RPC client
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    try {
        // Check server health
        val health = server.getHealth()
        println("Server status: ${health.status}")

        // Get network information
        val network = server.getNetwork()
        println("Network: ${network.passphrase}")

        // Your smart contract operations here...
    } finally {
        server.close()
    }
}
```

## Connection Setup

### Basic Setup

```kotlin
// Connect to testnet
val testnetServer = SorobanServer("https://soroban-testnet.stellar.org")

// Connect to mainnet
val mainnetServer = SorobanServer("https://soroban.stellar.org")

// Connect to futurenet
val futurenetServer = SorobanServer("https://rpc-futurenet.stellar.org")
```

### With Custom HTTP Client

```kotlin
import io.ktor.client.*
import io.ktor.client.plugins.*

val customClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000L // 2 minutes
        connectTimeoutMillis = 30_000L
    }
}

val server = SorobanServer(
    serverUrl = "https://soroban-testnet.stellar.org",
    httpClient = customClient
)
```

## Common Usage Patterns

### Contract Invocation

The SDK provides two approaches for contract invocation: a high-level ContractClient API (recommended) and low-level InvokeHostFunctionOperation for advanced control.

#### High-Level API: ContractClient (Recommended)

The `ContractClient` class provides a simple, type-safe way to interact with smart contracts:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.*
import kotlinx.coroutines.runBlocking

suspend fun invokeContractHighLevel() {
    // Create client and load contract spec from network
    val client = ContractClient.fromNetwork(
        contractId = "CDZJVZWCY4NFGHCCZMX6QW5AK3ET5L3UUAYBVNDYOXDLQXW7PHXGYOBJ",
        rpcUrl = "https://soroban-testnet.stellar.org",
        network = Network.TESTNET
    )

    // Example 1: Read-only call (query balance)
    val balance = client.invoke<Long>(
        functionName = "balance",
        arguments = mapOf("account" to "GABC..."),  // Native Kotlin types
        source = "GABC...",
        signer = null,  // No signer needed for read
        parseResultXdrFn = { Scv.fromInt128(it).toLong() }
    )
    println("Balance: $balance")

    // Example 2: Write call (transfer tokens)
    val sourceKeypair = KeyPair.fromSecretSeed("YOUR_SECRET_KEY")

    client.invoke<Unit>(
        functionName = "transfer",
        arguments = mapOf(
            "from" to sourceKeypair.getAccountId(),
            "to" to "GDEF...",
            "amount" to 1000
        ),
        source = sourceKeypair.getAccountId(),
        signer = sourceKeypair,  // Required for write operations
        parseResultXdrFn = null
    )
    println("Transfer complete!")
}
```

#### Contract Deployment

Deploy new smart contracts with automatic spec loading:

```kotlin
suspend fun deployContract() {
    val sourceKeypair = KeyPair.fromSecretSeed("YOUR_SECRET_KEY")

    // One-step deployment (recommended for single contracts)
    val client = ContractClient.deploy(
        wasmBytes = File("token.wasm").readBytes(),
        constructorArgs = mapOf(
            "admin" to sourceKeypair.getAccountId(),
            "name" to "MyToken",
            "symbol" to "MTK",
            "decimals" to 7
        ),
        source = sourceKeypair.getAccountId(),
        signer = sourceKeypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org"
    )

    println("Contract deployed: ${client.contractId}")

    // Client is ready to use immediately
    val totalSupply = client.invoke<Long>(
        functionName = "totalSupply",
        arguments = emptyMap(),
        source = sourceKeypair.getAccountId(),
        signer = null,
        parseResultXdrFn = { Scv.fromInt128(it).toLong() }
    )
    println("Total supply: $totalSupply")
}

// Two-step deployment (advanced - for WASM reuse)
suspend fun deployMultipleContracts() {
    val sourceKeypair = KeyPair.fromSecretSeed("YOUR_SECRET_KEY")

    // Step 1: Install WASM once
    val wasmId = ContractClient.install(
        wasmBytes = File("token.wasm").readBytes(),
        source = sourceKeypair.getAccountId(),
        signer = sourceKeypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org"
    )

    // Step 2: Deploy multiple instances from same WASM (saves fees)
    val token1 = ContractClient.deployFromWasmId(
        wasmId = wasmId,
        constructorArgs = listOf(  // Note: XDR args for advanced API
            Scv.toString("Token1"),
            Scv.toString("TK1"),
            Scv.toInt32(7)
        ),
        source = sourceKeypair.getAccountId(),
        signer = sourceKeypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org"
    )

    val token2 = ContractClient.deployFromWasmId(
        wasmId = wasmId,
        constructorArgs = listOf(
            Scv.toString("Token2"),
            Scv.toString("TK2"),
            Scv.toInt32(7)
        ),
        source = sourceKeypair.getAccountId(),
        signer = sourceKeypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org"
    )

    println("Deployed ${token1.contractId} and ${token2.contractId}")
}
```

#### Advanced: Manual XDR Control

For power users who need full control over XDR construction:

```kotlin
suspend fun advancedContractInvocation() {
    // Option 1: Use type conversion helpers
    val client = ContractClient.fromNetwork(
        contractId = "CDZJ...",
        rpcUrl = "https://soroban-testnet.stellar.org",
        network = Network.TESTNET
    )

    // Convert native types to XDR for inspection/debugging
    val xdrArgs = client.funcArgsToXdrSCValues(
        functionName = "transfer",
        arguments = mapOf(
            "from" to "GABC...",
            "to" to "GDEF...",
            "amount" to 1000
        )
    )
    println("Converted XDR args: $xdrArgs")

    // Use the converted XDR with advanced API
    val assembled = client.invokeWithXdr(
        functionName = "transfer",
        parameters = xdrArgs,
        source = "GABC...",
        signer = KeyPair.fromSecretSeed("SECRET"),
        parseResultXdrFn = null
    )
    assembled.simulate()
    val result = assembled.signAndSubmit(KeyPair.fromSecretSeed("SECRET"))

    // Option 2: Manual XDR without spec
    val clientNoSpec = ContractClient.withoutSpec(
        contractId = "CDZJ...",
        rpcUrl = "https://soroban-testnet.stellar.org",
        network = Network.TESTNET
    )

    // Manually construct XDR parameters
    val parameters = listOf(
        Scv.toAddress("GABC..."),
        Scv.toAddress("GDEF..."),
        Scv.toInt128(1000L)
    )

    // Get AssembledTransaction for manual lifecycle control
    val assembledManual = clientNoSpec.invokeWithXdr(
        functionName = "transfer",
        parameters = parameters,
        source = "GABC...",
        signer = KeyPair.fromSecretSeed("SECRET"),
        parseResultXdrFn = null
    )

    // Manual control over simulation, signing, and submission
    assembledManual.simulate()
    val finalResult = assembledManual.signAndSubmit(KeyPair.fromSecretSeed("SECRET"))
    println("Transaction result: $finalResult")
}
```

#### Low-Level API: InvokeHostFunctionOperation

For complete control over transaction construction:

```kotlin
suspend fun invokeContractLowLevel() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")
    val network = Network.TESTNET
    val sourceKeypair = KeyPair.fromSecretSeed("YOUR_SECRET_KEY")
    val sourceAccount = server.getAccount(sourceKeypair.getAccountId())

    // Create the InvokeHostFunction operation
    val invokeOperation = InvokeHostFunctionOperation.invokeContractFunction(
        contractId = "CDZJ...",
        functionName = "hello",
        parameters = listOf(SCVal.scvString(SCString("World!")))
    )

    // Build and submit transaction
    val transaction = TransactionBuilder(sourceAccount, network)
        .setBaseFee(Transaction.MIN_BASE_FEE)
        .addOperation(invokeOperation)
        .setTimeout(300)
        .build()

    val simulateResponse = server.simulateTransaction(transaction)
    if (simulateResponse.error != null) {
        throw Exception("Simulation failed: ${simulateResponse.error}")
    }

    val preparedTransaction = server.prepareTransaction(transaction, simulateResponse)
    preparedTransaction.sign(sourceKeypair)

    val submitResponse = server.sendTransaction(preparedTransaction)
    if (submitResponse.status == SendTransactionStatus.PENDING) {
        val result = server.pollTransaction(submitResponse.hash!!)
        println("Transaction status: ${result.status}")
    }

    server.close()
}
```

### ContractClient Options

Configure contract invocation behavior with `ClientOptions`:

```kotlin
import com.soneso.stellar.sdk.contract.ClientOptions

val customOptions = ClientOptions(
    sourceAccountKeyPair = KeyPair.fromAccountId(sourceAccount),
    contractId = contractId,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org",

    // Invoke behavior options
    baseFee = 100,                // Base fee in stroops
    transactionTimeout = 300L,     // Transaction timeout in seconds
    submitTimeout = 30,            // Submit polling timeout in seconds
    simulate = true,               // Auto-simulate before submission
    restore = true,                // Auto-restore expired entries
    autoSubmit = true             // Auto-execute based on call type
)

// Use custom options with invoke
val result = client.invoke<Long>(
    functionName = "balance",
    arguments = mapOf("account" to account),
    source = sourceAccount,
    signer = null,
    options = customOptions
)
```

### Transaction Simulation

Simulate transactions before submission to estimate resource costs:

```kotlin
suspend fun simulateTransaction() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    // Build your transaction
    val transaction = // ... build transaction ...

    // Simulate with default settings
    val simulation = server.simulateTransaction(transaction)

    // Check simulation results
    println("Min resource fee: ${simulation.minResourceFee}")
    println("Transaction data: ${simulation.transactionData}")

    // Simulate with custom resource configuration
    val customSimulation = server.simulateTransaction(
        transaction,
        SimulateTransactionRequest.ResourceConfig(
            instructionLeeway = 1000000L
        )
    )

    // Check for errors
    if (simulation.error != null) {
        println("Simulation error: ${simulation.error}")
        return
    }

    // Use simulation results to prepare transaction
    val preparedTx = server.prepareTransaction(transaction, simulation)

    server.close()
}
```

### Event Querying

Query contract events for monitoring and debugging:

```kotlin
suspend fun queryEvents() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    // Query all contract events from a specific ledger
    val eventsRequest = GetEventsRequest(
        startLedger = 1000000L,
        filters = listOf(
            EventFilter(
                type = EventFilterType.CONTRACT,
                contractIds = listOf("CONTRACT_ID_HERE"),
                topics = null
            )
        ),
        pagination = Pagination(
            cursor = null,
            limit = 100
        )
    )

    val events = server.getEvents(eventsRequest)

    events.events.forEach { event ->
        println("Event ID: ${event.id}")
        println("Contract: ${event.contractId}")
        println("Topics: ${event.topics}")
        println("Value: ${event.value}")
    }

    server.close()
}
```

### Contract Data Queries

Query contract storage directly:

```kotlin
suspend fun queryContractData() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    val contractId = "YOUR_CONTRACT_ID"

    // Create a storage key (example: querying a map entry)
    val key = SCVal.scvSymbol(SCSymbol("balance"))

    // Query persistent storage
    val persistentData = server.getContractData(
        contractId,
        key,
        SorobanServer.Durability.PERSISTENT
    )

    if (persistentData != null) {
        println("Persistent data: ${persistentData.xdr}")
        // Parse the XDR value
        val value = SCVal.fromXdrBase64(persistentData.xdr)
        println("Parsed value: $value")
    }

    // Query temporary storage
    val tempData = server.getContractData(
        contractId,
        key,
        SorobanServer.Durability.TEMPORARY
    )

    server.close()
}
```

## Error Handling

The SDK provides specific exception types for different error scenarios:

```kotlin
import com.stellar.sdk.exception.PrepareTransactionException
import com.stellar.sdk.exception.SorobanRpcException
import com.stellar.sdk.horizon.exceptions.NetworkException

suspend fun handleErrors() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    try {
        val transaction = // ... build transaction ...
        val prepared = server.prepareTransaction(transaction)
        // ... use prepared transaction ...
    } catch (e: PrepareTransactionException) {
        // Transaction preparation failed
        println("Preparation failed: ${e.message}")
        println("Simulation response: ${e.simulateResponse}")
    } catch (e: SorobanRpcException) {
        // RPC-level error
        println("RPC error code: ${e.code}")
        println("RPC error message: ${e.message}")
        println("RPC error data: ${e.data}")
    } catch (e: NetworkException) {
        // Network communication error
        println("Network error: ${e.message}")
    } catch (e: Exception) {
        // Other errors
        println("Unexpected error: ${e.message}")
    } finally {
        server.close()
    }
}
```

## Advanced Topics

### Custom HTTP Client

Configure a custom HTTP client for specific requirements:

```kotlin
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun createCustomClient(): HttpClient {
    return HttpClient {
        // JSON configuration
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = true
            })
        }

        // Timeout configuration
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000L
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = 120_000L
        }

        // Retry configuration
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }

        // Logging (for debugging)
        install(Logging) {
            level = LogLevel.INFO
        }
    }
}

val server = SorobanServer(
    serverUrl = "https://soroban-testnet.stellar.org",
    httpClient = createCustomClient()
)
```

### Transaction Polling Strategies

Implement custom polling strategies for transaction completion:

```kotlin
suspend fun customPolling() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    val txHash = "YOUR_TRANSACTION_HASH"

    // Default polling (30 attempts, 1 second between each)
    val result1 = server.pollTransaction(txHash)

    // Custom polling with more attempts
    val result2 = server.pollTransaction(
        hash = txHash,
        maxAttempts = 60,
        sleepStrategy = { attemptNumber -> 1000L } // 1 second between attempts
    )

    // Exponential backoff strategy
    val result3 = server.pollTransaction(
        hash = txHash,
        maxAttempts = 20,
        sleepStrategy = { attemptNumber ->
            // Start with 1 second, double each time, max 30 seconds
            minOf(1000L * (1 shl attemptNumber), 30_000L)
        }
    )

    // Linear backoff with initial delay
    val result4 = server.pollTransaction(
        hash = txHash,
        maxAttempts = 30,
        sleepStrategy = { attemptNumber ->
            // 500ms initial, increase by 500ms each time
            500L + (500L * attemptNumber)
        }
    )

    server.close()
}
```

### SAC Token Operations

Work with Stellar Asset Contracts (SAC):

```kotlin
suspend fun sacTokenOperations() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")
    val network = Network.TESTNET

    // Define the asset
    val asset = Asset.createNonNativeAsset(
        "USDC",
        "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
    )

    // Get the SAC contract ID for this asset
    val contractId = asset.getContractId(network)

    // Query SAC balance
    val balanceResponse = server.getSACBalance(
        contractId = contractId,
        asset = asset,
        network = network
    )

    if (balanceResponse.balanceEntry != null) {
        val balance = balanceResponse.balanceEntry
        println("Balance: ${balance.amount}")
        println("Authorized: ${balance.authorized}")
        println("Clawback enabled: ${balance.clawback}")
        println("Last modified: ${balance.lastModifiedLedgerSeq}")
    } else {
        println("No balance found")
    }

    server.close()
}
```

## Platform-Specific Notes

### JVM (Android/Desktop)

```kotlin
// On JVM platforms, you can use blocking calls
fun jvmExample() {
    runBlocking {
        val server = SorobanServer("https://soroban-testnet.stellar.org")
        val health = server.getHealth()
        println(health.status)
        server.close()
    }
}
```

### JavaScript (Browser/Node.js)

```kotlin
// In JavaScript, all operations are inherently async
suspend fun jsExample() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    // All RPC operations are suspend functions
    val health = server.getHealth()
    console.log("Health: ${health.status}")

    server.close()
}
```

### iOS/macOS

```kotlin
// On iOS/macOS, use coroutines from the main scope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class SorobanExample {
    private val scope = MainScope()

    fun checkHealth() {
        scope.launch {
            val server = SorobanServer("https://soroban-testnet.stellar.org")
            val health = server.getHealth()
            println("Health: ${health.status}")
            server.close()
        }
    }
}
```

## API Reference

### SorobanServer Methods

#### Core Methods
- `suspend fun getHealth(): GetHealthResponse` - Get server health status
- `suspend fun getNetwork(): GetNetworkResponse` - Get network configuration
- `suspend fun getVersionInfo(): GetVersionInfoResponse` - Get version information
- `suspend fun getLatestLedger(): GetLatestLedgerResponse` - Get latest ledger info
- `suspend fun getFeeStats(): GetFeeStatsResponse` - Get fee statistics

#### Transaction Methods
- `suspend fun simulateTransaction(transaction: Transaction, resourceConfig: ResourceConfig? = null): SimulateTransactionResponse` - Simulate transaction
- `suspend fun prepareTransaction(transaction: Transaction): Transaction` - Prepare transaction with simulation
- `suspend fun sendTransaction(transaction: Transaction): SendTransactionResponse` - Submit transaction
- `suspend fun getTransaction(hash: String): GetTransactionResponse` - Get transaction by hash
- `suspend fun getTransactions(request: GetTransactionsRequest): GetTransactionsResponse` - Query multiple transactions
- `suspend fun pollTransaction(hash: String, maxAttempts: Int = 30, sleepStrategy: (Int) -> Long = { 1000L }): GetTransactionResponse` - Poll for transaction completion

#### Ledger State Methods
- `suspend fun getLedgerEntries(keys: Collection<LedgerKey>): GetLedgerEntriesResponse` - Query ledger entries
- `suspend fun getContractData(contractId: String, key: SCVal, durability: Durability): LedgerEntryResult?` - Query contract storage
- `suspend fun getLedgers(request: GetLedgersRequest): GetLedgersResponse` - Query ledger history

#### Event Methods
- `suspend fun getEvents(request: GetEventsRequest): GetEventsResponse` - Query contract events

#### Account Methods
- `suspend fun getAccount(address: String): TransactionBuilderAccount` - Get account for transaction building

#### SAC Methods
- `suspend fun getSACBalance(contractId: String, asset: Asset, network: Network): GetSACBalanceResponse` - Query SAC token balance

### Helper Classes

#### SorobanDataBuilder
Build Soroban transaction data:

```kotlin
val builder = SorobanDataBuilder()
    .setResourceFee(1000000L)
    .setResources(
        SorobanDataBuilder.Resources(
            cpuInstructions = 10000000L,
            diskReadBytes = 1000L,
            writeBytes = 1000L
        )
    )
    .setReadOnly(listOf(ledgerKey1, ledgerKey2))
    .setReadWrite(listOf(ledgerKey3))

val sorobanData = builder.build()
```

### Exception Types

- `SorobanRpcException` - RPC-level errors with code, message, and data
- `PrepareTransactionException` - Transaction preparation failures with simulation response
- `NetworkException` - Network communication errors

## Links and Resources

- [Soroban RPC API Reference](https://developers.stellar.org/docs/data/rpc/api-reference/methods)
- [Stellar Smart Contracts Documentation](https://developers.stellar.org/docs/smart-contracts)
- [Soroban Examples Repository](https://github.com/stellar/soroban-examples)
- [Stellar Laboratory (Testnet)](https://laboratory.stellar.org/)
- [Soroban Explorer](https://soroban.stellar.org/)

## Example Projects

For complete working examples, see:
- `/stellarSample/shared/` - Shared KMP business logic
- `/stellarSample/androidApp/` - Android app with Soroban integration
- `/stellarSample/iosApp/` - iOS app with Soroban integration
- `/stellarSample/webApp/` - Web app with Soroban integration