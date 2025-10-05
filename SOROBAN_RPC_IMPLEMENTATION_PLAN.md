# Soroban RPC Implementation Plan

**Date:** October 5, 2025
**Project:** KMP Stellar SDK
**Reference:** Java Stellar SDK at `/Users/chris/projects/Stellar/java-stellar-sdk`

## Overview

This plan outlines the implementation of Soroban RPC functionality for the Kotlin Multiplatform Stellar SDK. Soroban is Stellar's smart contract platform, and the Soroban RPC server provides APIs to interact with smart contracts, simulate transactions, and query ledger state.

## Architecture

### Core Components

1. **SorobanServer** - Main client class for Soroban RPC communication
2. **Request Models** - Data classes representing JSON-RPC requests
3. **Response Models** - Data classes representing JSON-RPC responses
4. **SorobanDataBuilder** - Helper for building SorobanTransactionData structures
5. **Exception Classes** - Soroban-specific exceptions

### Protocol

Soroban RPC uses **JSON-RPC 2.0** over HTTP:
- Request format: `{"jsonrpc": "2.0", "id": "<uuid>", "method": "<method>", "params": {...}}`
- Response format: `{"jsonrpc": "2.0", "id": "<uuid>", "result": {...}}` or `{"jsonrpc": "2.0", "id": "<uuid>", "error": {...}}`

## Implementation Phases

### Phase 1: Foundation (Core Infrastructure)

#### 1.1 Exception Classes
**Location:** `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/rpc/exception/`

- **SorobanRpcException** - For RPC-level errors
  - Properties: `code: Int`, `message: String`, `data: String?`
  - Extends existing exception hierarchy

- **PrepareTransactionException** - For transaction preparation failures
  - Contains `SimulateTransactionResponse` for debugging

**Files to create:**
- `exception/SorobanRpcException.kt`
- `exception/PrepareTransactionException.kt`

#### 1.2 Base Request/Response Models
**Location:** `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/rpc/requests/` and `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/rpc/responses/`

- **SorobanRpcRequest<T>**
  ```kotlin
  @Serializable
  data class SorobanRpcRequest<T>(
      @SerialName("jsonrpc") val jsonRpc: String = "2.0",
      val id: String,
      val method: String,
      val params: T?
  )
  ```

- **SorobanRpcResponse<T>**
  ```kotlin
  @Serializable
  data class SorobanRpcResponse<T>(
      @SerialName("jsonrpc") val jsonRpc: String,
      val id: String,
      val result: T?,
      val error: Error?
  ) {
      @Serializable
      data class Error(
          val code: Int,
          val message: String,
          val data: String?
      )
  }
  ```

**Files to create:**
- `requests/SorobanRpcRequest.kt`
- `responses/SorobanRpcResponse.kt`

### Phase 2: Request Models

**Location:** `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/rpc/requests/`

All request classes should be `@Serializable` data classes:

1. **GetLedgerEntriesRequest**
   - `keys: List<String>` (XDR base64 encoded LedgerKey objects)

2. **GetTransactionRequest**
   - `hash: String` (transaction hash as hex string)

3. **GetTransactionsRequest**
   - `startLedger: Long`
   - `pagination: Pagination?`
   ```kotlin
   @Serializable
   data class Pagination(
       val cursor: String?,
       val limit: Int?
   )
   ```

4. **GetLedgersRequest**
   - `startLedger: Long`
   - `cursor: String?`
   - `limit: Int?`

5. **GetEventsRequest**
   - `startLedger: Long`
   - `filters: List<EventFilter>`
   - `pagination: Pagination?`
   ```kotlin
   @Serializable
   data class EventFilter(
       val type: EventFilterType,
       val contractIds: List<String>?,
       val topics: List<List<String>>?
   )

   enum class EventFilterType {
       @SerialName("contract") CONTRACT,
       @SerialName("system") SYSTEM,
       @SerialName("diagnostic") DIAGNOSTIC
   }
   ```

6. **SimulateTransactionRequest**
   - `transaction: String` (XDR base64 encoded transaction envelope)
   - `resourceConfig: ResourceConfig?`
   ```kotlin
   @Serializable
   data class ResourceConfig(
       val instructionLeeway: Long?
   )
   ```

7. **SendTransactionRequest**
   - `transaction: String` (XDR base64 encoded transaction envelope)

**Files to create (7 files):**
- `GetLedgerEntriesRequest.kt`
- `GetTransactionRequest.kt`
- `GetTransactionsRequest.kt`
- `GetLedgersRequest.kt`
- `GetEventsRequest.kt`
- `SimulateTransactionRequest.kt`
- `SendTransactionRequest.kt`

### Phase 3: Response Models

**Location:** `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/rpc/responses/`

All response classes should be `@Serializable` data classes with helper methods to parse XDR:

1. **GetHealthResponse**
   - `status: String` ("healthy" or error message)
   - `latestLedger: Long?`
   - `oldestLedger: Long?`

2. **GetFeeStatsResponse**
   - `sorobanInclusionFee: InclusionFee`
   - `inclusionFee: InclusionFee`
   - `latestLedger: Long`
   ```kotlin
   @Serializable
   data class InclusionFee(
       val max: String,
       val min: String,
       val mode: String,
       val p10: String,
       val p20: String,
       val p30: String,
       val p40: String,
       val p50: String,
       val p60: String,
       val p70: String,
       val p80: String,
       val p90: String,
       val p95: String,
       val p99: String,
       val transactionCount: String,
       val ledgerCount: Long
   )
   ```

3. **GetNetworkResponse**
   - `friendbotUrl: String?`
   - `passphrase: String`
   - `protocolVersion: Int`

4. **GetVersionInfoResponse**
   - `version: String`
   - `commitHash: String`
   - `buildTimestamp: String`
   - `captiveCoreVersion: String`
   - `protocolVersion: Int`

5. **GetLatestLedgerResponse**
   - `id: String`
   - `protocolVersion: Int`
   - `sequence: Long`

6. **GetLedgerEntriesResponse**
   - `entries: List<LedgerEntryResult>?`
   - `latestLedger: Long`
   ```kotlin
   @Serializable
   data class LedgerEntryResult(
       val key: String,
       val xdr: String,
       val lastModifiedLedger: Long,
       val liveUntilLedger: Long?
   )
   ```

7. **GetTransactionResponse**
   - `status: GetTransactionStatus`
   - `latestLedger: Long`
   - `latestLedgerCloseTime: Long`
   - `oldestLedger: Long`
   - `oldestLedgerCloseTime: Long`
   - `applicationOrder: Int?`
   - `feeBump: Boolean?`
   - `envelopeXdr: String?`
   - `resultXdr: String?`
   - `resultMetaXdr: String?`
   - `ledger: Long?`
   - `createdAt: Long?`
   - `diagnosticEventsXdr: List<String>?`
   - Helper methods: `parseEnvelopeXdr()`, `parseResultXdr()`, `parseResultMetaXdr()`, `parseDiagnosticEventsXdr()`
   ```kotlin
   enum class GetTransactionStatus {
       @SerialName("SUCCESS") SUCCESS,
       @SerialName("NOT_FOUND") NOT_FOUND,
       @SerialName("FAILED") FAILED
   }
   ```

8. **GetTransactionsResponse**
   - `transactions: List<TransactionInfo>`
   - `latestLedger: Long`
   - `latestLedgerCloseTimestamp: Long`
   - `oldestLedger: Long`
   - `oldestLedgerCloseTimestamp: Long`
   - `cursor: String`

9. **GetLedgersResponse**
   - `ledgers: List<LedgerInfo>`
   - `latestLedger: Long`
   - `latestLedgerCloseTimestamp: Long`
   - `oldestLedger: Long`
   - `oldestLedgerCloseTimestamp: Long`
   - `cursor: String`

10. **GetEventsResponse**
    - `events: List<EventInfo>`
    - `latestLedger: Long`

11. **SimulateTransactionResponse**
    - `error: String?`
    - `transactionData: String?`
    - `events: List<String>?`
    - `minResourceFee: Long?`
    - `results: List<SimulateHostFunctionResult>?`
    - `restorePreamble: RestorePreamble?`
    - `stateChanges: List<LedgerEntryChange>?`
    - `latestLedger: Long?`
    - Helper methods: `parseTransactionData()`, `parseEvents()`
    ```kotlin
    @Serializable
    data class SimulateHostFunctionResult(
        val auth: List<String>?,
        val xdr: String?
    ) {
        fun parseAuth(): List<SorobanAuthorizationEntry>?
        fun parseXdr(): SCVal?
    }

    @Serializable
    data class RestorePreamble(
        val transactionData: String,
        val minResourceFee: Long
    ) {
        fun parseTransactionData(): SorobanTransactionData
    }

    @Serializable
    data class LedgerEntryChange(
        val type: String,
        val key: String,
        val before: String?,
        val after: String?
    ) {
        fun parseKey(): LedgerKey
        fun parseBefore(): LedgerEntry?
        fun parseAfter(): LedgerEntry?
    }
    ```

12. **SendTransactionResponse**
    - `status: SendTransactionStatus`
    - `hash: String?`
    - `latestLedger: Long?`
    - `latestLedgerCloseTime: Long?`
    - `errorResultXdr: String?`
    - `diagnosticEventsXdr: List<String>?`
    ```kotlin
    enum class SendTransactionStatus {
        @SerialName("PENDING") PENDING,
        @SerialName("DUPLICATE") DUPLICATE,
        @SerialName("TRY_AGAIN_LATER") TRY_AGAIN_LATER,
        @SerialName("ERROR") ERROR
    }
    ```

13. **GetSACBalanceResponse**
    - `balanceEntry: BalanceEntry?`
    - `latestLedger: Long`
    ```kotlin
    @Serializable
    data class BalanceEntry(
        val amount: String,
        val authorized: Boolean,
        val clawback: Boolean,
        val lastModifiedLedgerSeq: Long,
        val liveUntilLedgerSeq: Long?
    )
    ```

**Files to create (13 files):**
- All response models listed above

### Phase 4: SorobanServer Client

**Location:** `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/rpc/`

**Class: SorobanServer**

```kotlin
class SorobanServer(
    private val serverUrl: String,
    private val httpClient: HttpClient = defaultHttpClient()
) : Closeable {

    companion object {
        private const val SUBMIT_TRANSACTION_TIMEOUT = 60_000L // ms
        private const val CONNECT_TIMEOUT = 10_000L // ms

        fun defaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = SUBMIT_TRANSACTION_TIMEOUT
                    connectTimeoutMillis = CONNECT_TIMEOUT
                }
            }
        }
    }

    // Core RPC methods
    suspend fun <T, R> sendRequest(
        method: String,
        params: T?,
        responseType: KType
    ): R

    // Account operations
    suspend fun getAccount(address: String): TransactionBuilderAccount

    // Health and metadata
    suspend fun getHealth(): GetHealthResponse
    suspend fun getFeeStats(): GetFeeStatsResponse
    suspend fun getNetwork(): GetNetworkResponse
    suspend fun getVersionInfo(): GetVersionInfoResponse
    suspend fun getLatestLedger(): GetLatestLedgerResponse

    // Ledger entry operations
    suspend fun getLedgerEntries(keys: Collection<LedgerKey>): GetLedgerEntriesResponse
    suspend fun getContractData(
        contractId: String,
        key: SCVal,
        durability: Durability
    ): GetLedgerEntriesResponse.LedgerEntryResult?

    // Transaction operations
    suspend fun getTransaction(hash: String): GetTransactionResponse
    suspend fun getTransactions(request: GetTransactionsRequest): GetTransactionsResponse
    suspend fun getLedgers(request: GetLedgersRequest): GetLedgersResponse

    // Transaction polling
    suspend fun pollTransaction(
        hash: String,
        maxAttempts: Int = 30,
        sleepStrategy: (Int) -> Long = { 1000L }
    ): GetTransactionResponse

    // Event operations
    suspend fun getEvents(request: GetEventsRequest): GetEventsResponse

    // Simulation and preparation
    suspend fun simulateTransaction(
        transaction: Transaction,
        resourceConfig: SimulateTransactionRequest.ResourceConfig? = null
    ): SimulateTransactionResponse

    suspend fun prepareTransaction(transaction: Transaction): Transaction
    suspend fun prepareTransaction(
        transaction: Transaction,
        simulateResponse: SimulateTransactionResponse
    ): Transaction

    // Transaction submission
    suspend fun sendTransaction(transaction: Transaction): SendTransactionResponse

    // Stellar Asset Contract (SAC) operations
    suspend fun getSACBalance(
        contractId: String,
        asset: Asset,
        network: Network
    ): GetSACBalanceResponse

    override fun close()

    enum class Durability {
        TEMPORARY,
        PERSISTENT
    }
}
```

**Static helper:**
```kotlin
fun assembleTransaction(
    transaction: Transaction,
    simulateResponse: SimulateTransactionResponse
): Transaction
```

**Files to create:**
- `SorobanServer.kt`

### Phase 5: SorobanDataBuilder

**Location:** `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/rpc/`

```kotlin
class SorobanDataBuilder {
    // Constructors
    constructor()
    constructor(sorobanData: String) // from base64 XDR
    constructor(sorobanData: SorobanTransactionData) // from XDR object

    // Builder methods
    fun setResourceFee(fee: Long): SorobanDataBuilder
    fun setResources(resources: Resources): SorobanDataBuilder
    fun setReadOnly(readOnly: Collection<LedgerKey>?): SorobanDataBuilder
    fun setReadWrite(readWrite: Collection<LedgerKey>?): SorobanDataBuilder

    fun build(): SorobanTransactionData

    data class Resources(
        val cpuInstructions: Long,
        val diskReadBytes: Long,
        val writeBytes: Long
    )
}
```

**Files to create:**
- `SorobanDataBuilder.kt`

### Phase 6: Platform-Specific HTTP Client Configuration

Since we're using Ktor for HTTP, most of the implementation will be in commonMain, but we may need platform-specific configuration:

**JVM** (`jvmMain`):
- Uses Ktor CIO engine (already configured)
- No additional platform-specific code needed

**JS** (`jsMain`):
- Uses Ktor JS engine (already configured)
- No additional platform-specific code needed

**Native** (`nativeMain`):
- Uses Ktor Darwin engine (already configured)
- No additional platform-specific code needed

### Phase 7: Testing

#### 7.1 Unit Tests
**Location:** `stellar-sdk/src/commonTest/kotlin/com/stellar/sdk/`

Test files to create:
1. **SorobanServerTest.kt**
   - Mock server tests for all RPC methods
   - Request/response serialization tests
   - Error handling tests
   - Transaction preparation tests

2. **SorobanDataBuilderTest.kt**
   - Test building SorobanTransactionData
   - Test resource configuration
   - Test footprint manipulation

#### 7.2 Integration Tests (Optional)
- Tests against real testnet (can be manual or CI-based)
- Contract invocation examples
- Transaction simulation and submission

#### 7.3 Platform-Specific Tests
- **JVM**: Full test coverage
- **JS**: Browser and Node.js (run individual test classes due to bundling issues)
- **Native**: iOS Simulator and macOS tests

### Phase 8: Documentation and Examples

#### 8.1 Update CLAUDE.md
Add Soroban RPC to "Implemented Features" section

#### 8.2 API Documentation
- KDoc comments for all public APIs
- Reference to Stellar documentation links

#### 8.3 Sample App Updates
**Location:** `stellarSample/`

Create Soroban examples demonstrating:
1. Contract invocation
2. Transaction simulation and preparation
3. Event querying
4. SAC balance checking

Update sample apps:
- `androidApp/` - Add Soroban contract interaction UI
- `iosApp/` - Add Soroban contract interaction UI
- `webApp/` - Add Soroban contract interaction demo

## Dependencies

### Existing (Already Available)
- ✅ kotlinx-serialization (JSON parsing)
- ✅ kotlinx-coroutines (async/await)
- ✅ Ktor client (HTTP communication)

### New Dependencies Needed
None - all existing dependencies are sufficient

## Implementation Checklist

### Phase 1: Foundation
- [ ] Create `SorobanRpcException.kt`
- [ ] Create `PrepareTransactionException.kt`
- [ ] Create `SorobanRpcRequest.kt`
- [ ] Create `SorobanRpcResponse.kt`

### Phase 2: Request Models (7 files)
- [ ] Create `GetLedgerEntriesRequest.kt`
- [ ] Create `GetTransactionRequest.kt`
- [ ] Create `GetTransactionsRequest.kt`
- [ ] Create `GetLedgersRequest.kt`
- [ ] Create `GetEventsRequest.kt`
- [ ] Create `SimulateTransactionRequest.kt`
- [ ] Create `SendTransactionRequest.kt`

### Phase 3: Response Models (13 files)
- [ ] Create `GetHealthResponse.kt`
- [ ] Create `GetFeeStatsResponse.kt`
- [ ] Create `GetNetworkResponse.kt`
- [ ] Create `GetVersionInfoResponse.kt`
- [ ] Create `GetLatestLedgerResponse.kt`
- [ ] Create `GetLedgerEntriesResponse.kt`
- [ ] Create `GetTransactionResponse.kt`
- [ ] Create `GetTransactionsResponse.kt`
- [ ] Create `GetLedgersResponse.kt`
- [ ] Create `GetEventsResponse.kt`
- [ ] Create `SimulateTransactionResponse.kt`
- [ ] Create `SendTransactionResponse.kt`
- [ ] Create `GetSACBalanceResponse.kt`

### Phase 4: Core Client
- [ ] Create `SorobanServer.kt` with all RPC methods
- [ ] Implement `assembleTransaction()` helper function
- [ ] Implement JSON-RPC request/response handling
- [ ] Implement error handling and exception mapping

### Phase 5: Helper Classes
- [ ] Create `SorobanDataBuilder.kt`
- [ ] Implement all builder methods

### Phase 6: Platform Configuration
- [ ] Verify JVM HTTP client configuration
- [ ] Verify JS HTTP client configuration
- [ ] Verify Native HTTP client configuration

### Phase 7: Testing
- [ ] Create `SorobanServerTest.kt` with comprehensive tests
- [ ] Create `SorobanDataBuilderTest.kt`
- [ ] Test on JVM platform
- [ ] Test on JS platform (individual test classes)
- [ ] Test on Native platforms (macOS/iOS Simulator)

### Phase 8: Documentation & Examples
- [ ] Update `CLAUDE.md` with Soroban RPC features
- [ ] Add KDoc comments to all public APIs
- [ ] Create Soroban sample code in sample apps
- [ ] Update README with Soroban RPC usage examples

## File Structure Summary

```
stellar-sdk/src/
├── commonMain/kotlin/com/stellar/sdk/rpc/
│   ├── SorobanServer.kt (new)
│   ├── SorobanDataBuilder.kt (new)
│   ├── exception/
│   │   ├── SorobanRpcException.kt (new)
│   │   └── PrepareTransactionException.kt (new)
│   ├── requests/
│   │   ├── SorobanRpcRequest.kt (new)
│   │   ├── GetLedgerEntriesRequest.kt (new)
│   │   ├── GetTransactionRequest.kt (new)
│   │   ├── GetTransactionsRequest.kt (new)
│   │   ├── GetLedgersRequest.kt (new)
│   │   ├── GetEventsRequest.kt (new)
│   │   ├── SimulateTransactionRequest.kt (new)
│   │   └── SendTransactionRequest.kt (new)
│   └── responses/
│       ├── SorobanRpcResponse.kt (new)
│       ├── GetHealthResponse.kt (new)
│       ├── GetFeeStatsResponse.kt (new)
│       ├── GetNetworkResponse.kt (new)
│       ├── GetVersionInfoResponse.kt (new)
│       ├── GetLatestLedgerResponse.kt (new)
│       ├── GetLedgerEntriesResponse.kt (new)
│       ├── GetTransactionResponse.kt (new)
│       ├── GetTransactionsResponse.kt (new)
│       ├── GetLedgersResponse.kt (new)
│       ├── GetEventsResponse.kt (new)
│       ├── SimulateTransactionResponse.kt (new)
│       ├── SendTransactionResponse.kt (new)
│       ├── GetSACBalanceResponse.kt (new)
│       └── Events.kt (new)
└── commonTest/kotlin/com/stellar/sdk/rpc/
    ├── SorobanServerTest.kt (new)
    └── SorobanDataBuilderTest.kt (new)
```

**Total new files:** ~37 files

## API Design Principles

1. **Suspend Functions**: All network operations use `suspend` functions for proper async support
2. **Null Safety**: Use Kotlin's null-safety features extensively
3. **Type Safety**: Strong typing for all XDR objects and enums
4. **Helper Methods**: Provide convenience methods for parsing XDR strings to typed objects
5. **Immutability**: All response models are immutable data classes
6. **Error Handling**: Comprehensive exception handling with specific exception types
7. **Platform Consistency**: Same API across all platforms (JVM, JS, Native)

## Reference Documentation

- [Soroban RPC API Reference](https://developers.stellar.org/docs/data/rpc/api-reference/methods)
- [Stellar Smart Contracts](https://developers.stellar.org/docs/smart-contracts)
- [Java SDK Implementation](file:///Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/SorobanServer.java)

## Notes

- **XDR Handling**: Most Soroban data is transmitted as base64-encoded XDR. Response models should provide helper methods to parse these strings into typed XDR objects.
- **Transaction Preparation**: The `prepareTransaction` flow is critical for Soroban - it simulates the transaction, calculates resource requirements, and populates auth entries.
- **Resource Fees**: Soroban transactions have additional resource fees on top of base fees. The SDK must calculate total fees correctly.
- **Async Initialization**: JS platform may require async setup, so all operations should already use suspend functions.
- **Testing Strategy**: Due to JS bundling issues, individual test classes should be run separately on JS platform.

## Success Criteria

- ✅ All 37+ files created and implemented
- ✅ All Soroban RPC methods functional
- ✅ Comprehensive test coverage (>80%)
- ✅ Tests passing on JVM, JS (individual classes), and Native platforms
- ✅ Sample apps updated with Soroban examples
- ✅ Documentation complete and accurate
- ✅ API matches Java SDK capabilities where applicable
- ✅ Production-ready code quality

## Estimated Effort

- **Phase 1-2:** 2-3 hours (Foundation + Request Models)
- **Phase 3:** 4-5 hours (Response Models with XDR parsing)
- **Phase 4:** 6-8 hours (SorobanServer implementation)
- **Phase 5:** 1-2 hours (SorobanDataBuilder)
- **Phase 6:** 1 hour (Platform verification)
- **Phase 7:** 4-6 hours (Comprehensive testing)
- **Phase 8:** 2-3 hours (Documentation and examples)

**Total:** 20-28 hours of development time
