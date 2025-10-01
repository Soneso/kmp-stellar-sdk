# Horizon API Implementation Plan

Based on the Java SDK architecture, implement a KMP Horizon client with the following structure:

## Phase 1: Core Foundation

### 1.1 HTTP Client Layer (commonMain + platform actuals)
- **HorizonServer** class (main entry point)
  - Constructor with server URI
  - Platform-specific HTTP clients (Ktor already configured)
  - Submit timeout handling (60s + 5s buffer)

### 1.2 Base Request Builder (commonMain)
- **RequestBuilder** abstract class
  - URL building with segments
  - Common query parameters: `cursor`, `limit`, `order`
  - Asset encoding helpers
  - Generic GET/POST execution methods

### 1.3 Response Models (commonMain)
- **Response** base class with HAL links
- **Page<T>** for paginated responses
  - `records`, `links` (next/prev)
  - `getNextPage()` method
- Start with essential responses:
  - AccountResponse
  - TransactionResponse
  - OperationResponse (base + subtypes)
  - EffectResponse (base + subtypes)
  - LedgerResponse

### 1.4 Exception Hierarchy (commonMain)
- **NetworkException** (base)
- **BadRequestException** (4xx)
- **BadResponseException** (5xx)
- **TooManyRequestsException** (429)
- **RequestTimeoutException**
- **ConnectionErrorException**
- **UnknownResponseException**

## Phase 2: Request Builders (Priority Order)

### 2.1 Essential Endpoints
1. **AccountsRequestBuilder**
   - `account(id)` - single account
   - `forSigner()`, `forAsset()`, `forLiquidityPool()`, `forSponsor()`
   - `execute()` - paginated list

2. **TransactionsRequestBuilder**
   - `transaction(hash)` - single transaction
   - `forAccount()`, `forLedger()`, `forLiquidityPool()`
   - `execute()` - paginated list
   - `includeSuccessful()`

3. **OperationsRequestBuilder**
   - `operation(id)` - single operation
   - `forAccount()`, `forLedger()`, `forTransaction()`
   - `execute()` - paginated list
   - `includeFailed()`

4. **EffectsRequestBuilder**
   - Similar pattern to Operations

5. **PaymentsRequestBuilder**
   - Subset of operations (payment-related only)

### 2.2 Additional Endpoints (Next Priority)
- **LedgersRequestBuilder**
- **OffersRequestBuilder**
- **TradesRequestBuilder**
- **AssetsRequestBuilder**
- **ClaimableBalancesRequestBuilder**
- **LiquidityPoolsRequestBuilder**

### 2.3 Specialized Endpoints
- **OrderBookRequestBuilder**
- **PathsRequestBuilder** (strict send/receive)
- **TradeAggregationsRequestBuilder**
- **FeeStatsRequestBuilder**
- **RootRequestBuilder** (server info)

## Phase 3: Transaction Submission

### 3.1 Submit Transaction
- `submitTransaction(transaction, skipMemoCheck)`
- `submitTransactionXdr(base64)`
- SEP-29 memo required check
- POST to `/transactions` endpoint

### 3.2 Async Submission
- `submitTransactionAsync()` - fire and forget

## Phase 4: Streaming Support (SSE)

### 4.1 Server-Sent Events
- **SSEStream<T>** class
  - Auto-reconnect with timeout
  - Cursor management
  - EventListener callbacks
- Implement for all streamable endpoints

## Phase 5: Advanced Features

### 5.1 Helper Methods
- `loadAccount(address)` - fetch + create Account object
- Transaction envelope helpers

### 5.2 SEP Support
- SEP-29: Memo required checking
- Client identification interceptor

## Implementation Notes

### Serialization
- Use kotlinx.serialization (already configured)
- `@Serializable` data classes for all responses
- Custom serializers for polymorphic types (operations/effects)

### Platform HTTP Clients
- **JVM**: Ktor CIO (already configured)
- **JS**: Ktor JS (already configured)
- **Native**: Ktor Darwin (already configured)

### Package Structure
```
com.stellar.sdk/
  horizon/
    HorizonServer.kt
    requests/
      RequestBuilder.kt
      AccountsRequestBuilder.kt
      TransactionsRequestBuilder.kt
      ...
    responses/
      Response.kt
      Page.kt
      AccountResponse.kt
      TransactionResponse.kt
      ...
    exceptions/
      NetworkException.kt
      BadRequestException.kt
      ...
```

### Testing Strategy
- Unit tests with mocked HTTP responses
- Integration tests against Horizon testnet
- Test all platforms (JVM, JS, Native)

## Recommended Approach

**Start small and iterate:**
1. HorizonServer + AccountsRequestBuilder + AccountResponse
2. Test on all platforms
3. Add TransactionsRequestBuilder + submit
4. Expand to other endpoints
5. Add streaming support last

This allows validating the architecture early with a minimal working implementation.

## Java SDK Reference Locations

Key files to reference from `/Users/chris/projects/Stellar/java-stellar-sdk`:

- **Main Server**: `src/main/java/org/stellar/sdk/Server.java` (655 lines)
- **Base Request Builder**: `src/main/java/org/stellar/sdk/requests/RequestBuilder.java`
- **Example Request Builder**: `src/main/java/org/stellar/sdk/requests/AccountsRequestBuilder.java`
- **Page Response**: `src/main/java/org/stellar/sdk/responses/Page.java`
- **Account Response**: `src/main/java/org/stellar/sdk/responses/AccountResponse.java`
- **SSE Streaming**: `src/main/java/org/stellar/sdk/requests/SSEStream.java`
- **Exceptions**: `src/main/java/org/stellar/sdk/exception/`
- **Request Builders**: 31 files in `src/main/java/org/stellar/sdk/requests/`
- **Response Models**: 132 files in `src/main/java/org/stellar/sdk/responses/`
