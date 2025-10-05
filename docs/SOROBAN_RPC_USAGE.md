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

Here's a complete example of invoking a smart contract function:

```kotlin
import com.stellar.sdk.*
import com.stellar.sdk.rpc.*
import com.stellar.sdk.xdr.*
import kotlinx.coroutines.runBlocking

suspend fun invokeContract() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")
    val network = Network.TESTNET

    // Your account keypair
    val sourceKeypair = KeyPair.fromSecretSeed("YOUR_SECRET_KEY")

    // Get account details
    val sourceAccount = server.getAccount(sourceKeypair.getAccountId())

    // Contract details
    val contractId = "CDZJVZWCY4NFGHCCZMX6QW5AK3ET5L3UUAYBVNDYOXDLQXW7PHXGYOBJ"
    val functionName = "hello"
    val parameters = listOf(
        SCVal.scvString(SCString("World!"))
    )

    // Create the InvokeHostFunction operation
    val invokeOperation = InvokeHostFunctionOperation.invokeContractFunction(
        contractId = contractId,
        functionName = functionName,
        parameters = parameters
    )

    // Build the transaction
    val transaction = TransactionBuilder(sourceAccount, network)
        .setBaseFee(Transaction.MIN_BASE_FEE)
        .addOperation(invokeOperation)
        .setTimeout(300)
        .build()

    // Simulate and prepare the transaction
    val simulateResponse = server.simulateTransaction(transaction)

    if (simulateResponse.error != null) {
        throw Exception("Simulation failed: ${simulateResponse.error}")
    }

    val preparedTransaction = server.prepareTransaction(transaction, simulateResponse)

    // Sign the transaction
    preparedTransaction.sign(sourceKeypair)

    // Submit the transaction
    val submitResponse = server.sendTransaction(preparedTransaction)

    if (submitResponse.status == SendTransactionStatus.PENDING) {
        println("Transaction submitted: ${submitResponse.hash}")

        // Poll for result
        val result = server.pollTransaction(submitResponse.hash!!)

        if (result.status == GetTransactionStatus.SUCCESS) {
            println("Transaction succeeded!")

            // Parse the return value
            val resultMeta = result.parseResultMetaXdr()
            val returnValue = resultMeta?.v3?.sorobanMeta?.returnValue
            println("Contract returned: $returnValue")
        } else {
            println("Transaction failed: ${result.status}")
        }
    } else {
        println("Submit failed: ${submitResponse.status}")
        submitResponse.errorResultXdr?.let {
            println("Error details: $it")
        }
    }

    server.close()
}
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