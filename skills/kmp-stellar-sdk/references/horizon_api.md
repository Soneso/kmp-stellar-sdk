# Horizon API Reference

The `HorizonServer` class is the main entry point for all Horizon REST API queries. Each factory method returns a fresh request builder with a fluent API.
For method signatures on response objects, see [API Reference](./api_reference.md).

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order
import com.soneso.stellar.sdk.horizon.responses.*
import com.soneso.stellar.sdk.horizon.responses.operations.*
import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.Asset
import com.soneso.stellar.sdk.Price
```

**IMPORTANT: HorizonServer uses factory methods, not properties.**
```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
// WRONG: server.accounts -- HorizonServer does NOT have properties for endpoints
// CORRECT: server.accounts() -- factory methods return fresh request builders
val server = HorizonServer("https://horizon-testnet.stellar.org")
val account = server.accounts().account(accountId)
```

## Table of Contents

- [Common Query Methods](#common-query-methods)
- [Accounts](#accounts)
- [Transactions](#transactions)
- [Operations](#operations)
- [Payments](#payments)
- [Ledgers](#ledgers)
- [Effects](#effects)
- [Offers](#offers)
- [Order Book](#order-book)
- [Trades](#trades)
- [Assets](#assets)
- [Claimable Balances](#claimable-balances)
- [Liquidity Pools](#liquidity-pools)
- [Path Finding](#path-finding)
- [Fee Stats](#fee-stats)
- [Health Check](#health-check)
- [Root](#root)
- [Pagination](#pagination)
- [Error Handling](#error-handling)

## Common Query Methods

All request builders extend `RequestBuilder` and share these pagination methods:

```kotlin
builder.cursor(token: String)          // pagination cursor
builder.limit(number: Int)             // max records (default 10, max 200)
builder.order(direction: Order)        // Order.ASC or Order.DESC
```

Results are returned as `Page<T>` objects:

```kotlin
val signerAccountId = "GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// WRONG: page.records is a direct field -- it is NOT a direct field
// CORRECT: page.records is a convenience property that accesses page.embedded?.records
val page: Page<AccountResponse> = server.accounts().forSigner(signerAccountId).execute()
val records: List<AccountResponse> = page.records  // convenience getter, returns emptyList() if null
```

## Accounts

`server.accounts()` returns `AccountsRequestBuilder`.

```kotlin
// sponsorAccountId: from the previous steps of this flow
val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7" // 64-char hex pool id
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get single account
val account: AccountResponse = server.accounts().account(accountId)
println("Sequence: ${account.sequenceNumber}")
println("Balances: ${account.balances.size}")

// Get account data entry
val data: AccountDataResponse = server.accounts().accountData(accountId, "my_key")
println("Decoded: ${data.decodedString}")
// Also available: data.value (base64), data.decodedValue (ByteArray), data.decodedStringOrNull

// Access account data entries from the account response
// account.data is a Map<String, String> (base64-encoded values)
if (account.data.containsKey("my_key")) {
    val base64Value = account.data["my_key"]
}

// Filter accounts
// WRONG: server.accounts().forAsset(Asset(...)) -- forAsset takes TWO strings, not an Asset object
// CORRECT: server.accounts().forAsset(assetCode, assetIssuer)
val page: Page<AccountResponse> = server.accounts()
    .forSigner(signerAccountId)
    .limit(20)
    .execute()

val page2: Page<AccountResponse> = server.accounts()
    .forAsset("USD", issuerAccountId)
    .execute()

val page3: Page<AccountResponse> = server.accounts()
    .forSponsor(sponsorAccountId)
    .execute()

val page4: Page<AccountResponse> = server.accounts()
    .forLiquidityPool(poolId)
    .execute()
```

**AccountResponse key fields:**

| Field | Type | Description |
|-------|------|-------------|
| `accountId` | String | G... public key |
| `sequenceNumber` | Long | Current sequence number |
| `balances` | List\<Balance\> | All asset balances |
| `signers` | List\<Signer\> | Account signers with weights |
| `thresholds` | Thresholds | lowThreshold, medThreshold, highThreshold |
| `flags` | Flags | authRequired, authRevocable, authImmutable, authClawbackEnabled |
| `data` | Map\<String, String\> | Key-value data entries (base64-encoded values) |
| `sponsor` | String? | Sponsoring account ID |
| `homeDomain` | String? | Federation home domain |
| `subentryCount` | Int | Number of sub-entries |

**Balance fields:** `assetType`, `assetCode`, `assetIssuer`, `balance`, `limit`, `buyingLiabilities`, `sellingLiabilities`, `isAuthorized`, `isClawbackEnabled`, `liquidityPoolId`, `sponsor`.

## Transactions

`server.transactions()` returns `TransactionsRequestBuilder`.

```kotlin
// txHash: from the previous steps of this flow
val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072" // 72-char hex id from Horizon
val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7" // 64-char hex pool id
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get single transaction by hash
val tx: TransactionResponse = server.transactions().transaction(txHash)
println("Fee charged: ${tx.feeCharged}")
println("Successful: ${tx.successful}")
// WRONG: tx.memo -- TransactionResponse does NOT have a memo object
// CORRECT: tx.memoType (String) + tx.memoValue (String?) for memo access
println("Memo type: ${tx.memoType}")   // "none", "text", "id", "hash", "return"
println("Memo value: ${tx.memoValue}") // null for "none", otherwise the value

// XDR fields for round-tripping (all nullable -- not always returned by Horizon)
println("Envelope XDR: ${tx.envelopeXdr}")    // base64 transaction envelope
println("Result XDR: ${tx.resultXdr}")         // base64 transaction result
println("Result meta XDR: ${tx.resultMetaXdr}") // base64 result metadata (state changes)
println("Fee meta XDR: ${tx.feeMetaXdr}")      // base64 fee metadata

// Query transactions for an account
val txs: Page<TransactionResponse> = server.transactions()
    .forAccount(accountId)
    .order(Order.DESC)
    .limit(10)
    .execute()

// Inspect transaction memos
for (tx in txs.records) {
    println("Transaction: ${tx.hash}")
    when (tx.memoType) {
        "text" -> println("  Memo (text): ${tx.memoValue}")
        "id" -> println("  Memo (id): ${tx.memoValue}")
        "hash" -> println("  Memo (hash): ${tx.memoValue}")
        "return" -> println("  Memo (return): ${tx.memoValue}")
    }
}

// Include failed transactions
val allTxs: Page<TransactionResponse> = server.transactions()
    .forAccount(accountId)
    .includeFailed(true)
    .execute()

// Filter by ledger, claimable balance, or liquidity pool
server.transactions().forLedger(12345L).execute()
server.transactions().forClaimableBalance(balanceId).execute()
server.transactions().forLiquidityPool(poolId).execute()
```

### Submitting Transactions

```kotlin
import com.soneso.stellar.sdk.horizon.HorizonServer

val server = HorizonServer("https://horizon-testnet.stellar.org")

// Build and sign a transaction, then get XDR envelope
val envelopeXdr = transaction.toEnvelopeXdrBase64()

// Synchronous submit (waits for ledger inclusion)
// WRONG: server.submitTransaction(transaction) -- does NOT accept Transaction objects
// CORRECT: server.submitTransaction(envelopeXdr) -- accepts base64-encoded XDR string
val response: TransactionResponse = server.submitTransaction(envelopeXdr)
println("Hash: ${response.hash}")
println("Ledger: ${response.ledger}")

// Submit with skip memo required check (SEP-29)
val response2: TransactionResponse = server.submitTransaction(envelopeXdr, skipMemoRequiredCheck = true)

// Asynchronous submit (returns immediately with status)
val asyncResponse: SubmitTransactionAsyncResponse = server.submitTransactionAsync(envelopeXdr)
println("Status: ${asyncResponse.txStatus}")  // PENDING, DUPLICATE, TRY_AGAIN_LATER, ERROR
println("Hash: ${asyncResponse.hash}")

// Poll for result after async submission
if (asyncResponse.txStatus == SubmitTransactionAsyncResponse.TransactionStatus.PENDING) {
    kotlinx.coroutines.delay(5000)
    try {
        val tx = server.transactions().transaction(asyncResponse.hash)
        println("Confirmed in ledger: ${tx.ledger}")
    } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
        if (e.code == 404) { /* not yet ingested -- retry later */ }
    }
}
```

### Fee Bump Transaction Response

When querying a fee bump transaction, `TransactionResponse` includes both the outer and inner transaction details:

```kotlin
// feeBumpHash: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")
val tx: TransactionResponse = server.transactions().transaction(feeBumpHash)

// Inner (original) transaction details
val inner: TransactionResponse.InnerTransaction? = tx.innerTransaction
if (inner != null) {
    println("Inner TX hash: ${inner.hash}")
    println("Inner TX max fee: ${inner.maxFee}")
}

// Fee bump wrapper details
val feeBump: TransactionResponse.FeeBumpTransaction? = tx.feeBumpTransaction
if (feeBump != null) {
    println("Fee bump hash: ${feeBump.hash}")
}
```

## Operations

`server.operations()` returns `OperationsRequestBuilder`.

```kotlin
// operationId, txHash: from the previous steps of this flow
val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072" // 72-char hex id from Horizon
val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7" // 64-char hex pool id
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get single operation
// WRONG: server.operations().operation("123") -- operation() takes a Long, not a String
// CORRECT: server.operations().operation(123L)
val op: OperationResponse = server.operations().operation(operationId)

// Query operations with filters
val ops: Page<OperationResponse> = server.operations()
    .forAccount(accountId)
    .order(Order.DESC)
    .limit(25)
    .execute()

server.operations().forLedger(12345L).execute()
server.operations().forTransaction(txHash).execute()
server.operations().forClaimableBalance(balanceId).execute()
server.operations().forLiquidityPool(poolId).execute()

// Include failed operations
server.operations().forAccount(accountId).includeFailed(true).execute()

// Include transaction data in response
// WRONG: server.operations().join("transactions") -- no join() method
// CORRECT: server.operations().includeTransactions(true)
server.operations().forAccount(accountId).includeTransactions(true).execute()
```

## Payments

`server.payments()` returns `PaymentsRequestBuilder`. Returns payment-type operations (payment, create_account, path_payment, account_merge).

```kotlin
// txHash: from the previous steps of this flow
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")
val payments: Page<OperationResponse> = server.payments()
    .forAccount(accountId)
    .order(Order.DESC)
    .limit(10)
    .execute()

// Type-check responses using sealed class subtypes
for (op in payments.records) {
    when (op) {
        is PaymentOperationResponse -> {
            println("Payment: ${op.amount} ${op.assetCode ?: "XLM"}")
            println("From: ${op.from}")
            println("To: ${op.to}")
        }
        is CreateAccountOperationResponse -> {
            println("Account created: ${op.account}")
            println("Starting balance: ${op.startingBalance}")
        }
        is AccountMergeOperationResponse -> {
            println("Account merged into: ${op.into}")
        }
        else -> { /* PathPaymentStrictReceive, PathPaymentStrictSend */ }
    }
}

// Filter by ledger or transaction
server.payments().forLedger(12345L).execute()
server.payments().forTransaction(txHash).execute()

// Include failed + include transaction data
server.payments().forAccount(accountId).includeFailed(true).includeTransactions(true).execute()
```

## Ledgers

`server.ledgers()` returns `LedgersRequestBuilder`.

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get single ledger
// WRONG: server.ledgers().ledger(12345) -- ledger() takes a Long, not an Int
// CORRECT: server.ledgers().ledger(12345L)
val ledger: LedgerResponse = server.ledgers().ledger(12345L)
println("Closed at: ${ledger.closedAt}")
println("Transaction count: ${ledger.successfulTransactionCount}")
// WRONG: ledger.baseFee -- this field does NOT exist
// CORRECT: ledger.baseFeeInStroops (String value in stroops)
println("Base fee: ${ledger.baseFeeInStroops}")
println("Base reserve: ${ledger.baseReserveInStroops}")

// List ledgers
val ledgers: Page<LedgerResponse> = server.ledgers()
    .order(Order.DESC)
    .limit(10)
    .execute()
```

## Effects

`server.effects()` returns `EffectsRequestBuilder`.

```kotlin
val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7" // 64-char hex pool id
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val operationId = 123456789L
val txHash = "3389e9f0f1a65f19736cacf544c2e825313e8447f569233bb8db39aa607c8889"
val server = HorizonServer("https://horizon-testnet.stellar.org")
val effects: Page<EffectResponse> = server.effects()
    .forAccount(accountId)
    .limit(20)
    .execute()

// Filter by ledger, operation, transaction, or liquidity pool.
// Effects have no claimable-balance filter; query the balance's operations or
// transactions (forClaimableBalance there) and read effects per operation.
// WRONG: server.effects().forOperation("123") -- forOperation() takes a Long, not a String
// CORRECT: server.effects().forOperation(123L)
server.effects().forLedger(12345L).execute()
server.effects().forOperation(operationId).execute()
server.effects().forTransaction(txHash).execute()
server.effects().forLiquidityPool(poolId).execute()
```

## Offers

`server.offers()` returns `OffersRequestBuilder`.

```kotlin
// offerId, sellerAccountId, sponsorAccountId: from the previous steps of this flow
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get single offer
// WRONG: server.offers().offer("12345") -- offer() takes a Long, not a String
// CORRECT: server.offers().offer(12345L)
val offer: OfferResponse = server.offers().offer(offerId)

// Filter offers by seller/account
val offers: Page<OfferResponse> = server.offers()
    .forSeller(sellerAccountId)
    .execute()

// forAccount is an alias for forSeller
server.offers().forAccount(accountId).execute()

// Filter by buying/selling asset -- takes raw type/code/issuer parameters
// WRONG: server.offers().forBuyingAsset(myAsset) with only one arg when using strings
// CORRECT: pass assetType, assetCode, assetIssuer as separate strings
server.offers().forBuyingAsset("credit_alphanum4", "USD", issuerAccountId).execute()
server.offers().forSellingAsset("native").execute()

// Or pass an Asset object directly
server.offers().forBuyingAsset(AssetTypeCreditAlphaNum4("USD", issuerAccountId)).execute()
server.offers().forSellingAsset(AssetTypeNative).execute()

// Filter by sponsor
server.offers().forSponsor(sponsorAccountId).execute()
```

## Order Book

`server.orderBook()` returns `OrderBookRequestBuilder`.

Query parameters define the market from the **offer creator's perspective**:
- `sellingAsset` = what offers are SELLING
- `buyingAsset` = what offers want to BUY

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Example: Query the USD/XLM market (USD priced in XLM)
// Using raw asset type/code/issuer parameters:
val orderBook: OrderBookResponse = server.orderBook()
    .sellingAsset("credit_alphanum4", "USD", issuerAccountId)
    .buyingAsset("native")
    .execute()

// Or using Asset objects:
val orderBook2: OrderBookResponse = server.orderBook()
    .sellingAsset(AssetTypeCreditAlphaNum4("USD", issuerAccountId))
    .buyingAsset(AssetTypeNative)
    .execute()

// Asks: offers selling USD (asking for XLM)
for (ask in orderBook.asks) {
    println("Ask: ${ask.amount} USD @ ${ask.price} XLM each")
}

// Bids: offers buying USD (bidding with XLM)
for (bid in orderBook.bids) {
    println("Bid: ${bid.amount} USD @ ${bid.price} XLM each")
}

// NOTE: OrderBookRequestBuilder does NOT support cursor(), limit(), or order()
// Calling them throws UnsupportedOperationException
```

## Trades

`server.trades()` returns `TradesRequestBuilder`.

```kotlin
// offerId: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")
val trades: Page<TradeResponse> = server.trades()
    .forAccount(accountId)
    .execute()

// Filter by offer or liquidity pool
// WRONG: server.trades().forOfferId("12345") -- forOfferId takes Long?, not String
// CORRECT: server.trades().forOfferId(12345L)
server.trades().forOfferId(offerId).execute()
server.trades().forLiquidityPool(poolId).execute()

// Filter by base/counter asset pair (raw parameters)
server.trades()
    .forBaseAsset("native")
    .forCounterAsset("credit_alphanum4", "USD", issuerAccountId)
    .execute()

// Filter by trade type
server.trades().forTradeType("orderbook").execute()
server.trades().forTradeType("liquidity_pool").execute()

// Trade aggregations (OHLCV candlestick data)
// WRONG: server.tradeAggregations(Asset.NATIVE, usdAsset, ...) -- does NOT take Asset objects
// CORRECT: pass raw assetType/assetCode/assetIssuer strings
val candles: Page<TradeAggregationResponse> = server.tradeAggregations(
    baseAssetType = "native",
    baseAssetCode = null,
    baseAssetIssuer = null,
    counterAssetType = "credit_alphanum4",
    counterAssetCode = "USD",
    counterAssetIssuer = issuerAccountId,
    startTime = 1609459200000L,   // start time (ms since epoch)
    endTime = 1609545600000L,     // end time (ms since epoch)
    resolution = 3600000L,        // resolution (ms, 1 hour)
    offset = 0L                   // offset (ms, default 0)
).execute()

for (candle in candles.records) {
    println("Time: ${candle.timestamp}, Open: ${candle.open}, High: ${candle.high}, Low: ${candle.low}, Close: ${candle.close}")
    println("Volume: base=${candle.baseVolume}, counter=${candle.counterVolume}, trades=${candle.tradeCount}")
}
```

## Assets

`server.assets()` returns `AssetsRequestBuilder`.

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// WRONG: server.assets().assetCode("USD") -- method is forAssetCode, not assetCode
// CORRECT: server.assets().forAssetCode("USD")
val assets: Page<AssetResponse> = server.assets()
    .forAssetCode("USD")
    .forAssetIssuer(issuerAccountId)
    .execute()

for (asset in assets.records) {
    println("${asset.assetCode}:${asset.assetIssuer}")
    println("  Authorized accounts: ${asset.accounts.authorized}")
    println("  Authorized balance: ${asset.balances.authorized}")
    println("  Contract ID: ${asset.contractId}")
    println("  Flags: authRequired=${asset.flags.authRequired}, authRevocable=${asset.flags.authRevocable}")
}
```

## Claimable Balances

`server.claimableBalances()` returns `ClaimableBalancesRequestBuilder`.

```kotlin
// claimantAccountId, sponsorAccountId: from the previous steps of this flow
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get single claimable balance
val balance: ClaimableBalanceResponse =
    server.claimableBalances().claimableBalance(balanceId)
// WRONG: balance.balanceId -- ClaimableBalanceResponse does NOT have balanceId
// CORRECT: balance.id -- returns the claimable balance ID string
println("ID: ${balance.id}")
// WRONG: balance.asset -- ClaimableBalanceResponse does NOT have an asset property
// CORRECT: balance.assetString -- returns the asset in canonical string form (e.g. "native" or "CODE:ISSUER")
println("Asset: ${balance.assetString}")
println("Amount: ${balance.amount}")

// Filter claimable balances
val page: Page<ClaimableBalanceResponse> =
    server.claimableBalances().forClaimant(claimantAccountId).execute()

// Filter by asset (raw type/code/issuer)
server.claimableBalances()
    .forAsset("credit_alphanum4", "USD", issuerAccountId)
    .execute()

server.claimableBalances().forSponsor(sponsorAccountId).execute()
```

## Liquidity Pools

`server.liquidityPools()` returns `LiquidityPoolsRequestBuilder`.

```kotlin
val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7" // 64-char hex pool id
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get single pool
val pool: LiquidityPoolResponse =
    server.liquidityPools().liquidityPool(poolId)
println("Total shares: ${pool.totalShares}")
println("Reserves: ${pool.reserves.map { "${it.asset}: ${it.amount}" }}")

// Filter by reserve assets (canonical string format: "native" or "CODE:ISSUER")
// WRONG: server.liquidityPools().forReserveAssets(listOf(...)) -- method is forReserves with varargs
// CORRECT: server.liquidityPools().forReserves("native", "USD:$issuerAccountId")
val pools: Page<LiquidityPoolResponse> = server.liquidityPools()
    .forReserves("native", "USD:$issuerAccountId")
    .execute()

// Filter by account
server.liquidityPools().forAccount(accountId).execute()
```

## Path Finding

```kotlin
// receiverAccountId, senderAccountId: from the previous steps of this flow
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Strict receive: find paths to receive exact amount
// Uses raw asset type/code/issuer parameters
val paths: Page<PathResponse> = server.strictReceivePaths()
    .sourceAccount(senderAccountId)
    .destinationAsset("credit_alphanum4", "USD", issuerAccountId)
    .destinationAmount("100.0")
    .execute()

for (path in paths.records) {
    println("Send ${path.sourceAmount} (${path.sourceAssetType}) -> Receive ${path.destinationAmount}")
    println("Path: ${path.path.map { it.assetCode ?: "XLM" }}")
}

// Strict receive with source assets instead of source account
val paths2: Page<PathResponse> = server.strictReceivePaths()
    .sourceAssets(listOf(
        Triple("native", null, null),
        Triple("credit_alphanum4", "USD", issuerAccountId)
    ))
    .destinationAsset("credit_alphanum4", "EUR", issuerAccountId)
    .destinationAmount("50.0")
    .execute()

// Strict send: find paths sending exact amount
val paths3: Page<PathResponse> = server.strictSendPaths()
    .sourceAsset("native")
    .sourceAmount("50.0")
    .destinationAccount(receiverAccountId)
    .execute()

// Strict send with destination assets instead of destination account
val paths4: Page<PathResponse> = server.strictSendPaths()
    .sourceAsset("credit_alphanum4", "USD", issuerAccountId)
    .sourceAmount("100.0")
    .destinationAssets(listOf(
        Triple("credit_alphanum4", "EUR", issuerAccountId),
        Triple("credit_alphanum4", "GBP", issuerAccountId)
    ))
    .execute()
```

## Fee Stats

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
val feeStats: FeeStatsResponse = server.feeStats().execute()
println("Last ledger: ${feeStats.lastLedger}")
println("Base fee: ${feeStats.lastLedgerBaseFee}")
println("Capacity: ${feeStats.ledgerCapacityUsage}")

// Fee charged and max fee have percentile breakdowns (min, max, mode, p10-p99)
// WRONG: feeStats.feeCharged.min is a String -- it is NOT a String
// CORRECT: feeStats.feeCharged.min is a Long (stroops)
println("Fee charged (min): ${feeStats.feeCharged.min}")
println("Fee charged (mode): ${feeStats.feeCharged.mode}")
println("Fee charged (p50): ${feeStats.feeCharged.p50}")
println("Fee charged (p99): ${feeStats.feeCharged.p99}")

println("Max fee (min): ${feeStats.maxFee.min}")
println("Max fee (p99): ${feeStats.maxFee.p99}")

// NOTE: FeeStatsRequestBuilder does NOT support cursor(), limit(), or order()
```

## Health Check

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
val health: HealthResponse = server.health().execute()

if (health.isHealthy) {
    println("Server is healthy")
} else {
    println("Server is experiencing issues")
    if (!health.databaseConnected) println("Database is not connected")
    if (!health.coreUp) println("Stellar Core is not up")
    if (!health.coreSynced) println("Stellar Core is not synced")
}

// NOTE: HealthRequestBuilder does NOT support cursor(), limit(), or order()
```

## Root

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
val root: RootResponse = server.root().execute()
println("Horizon version: ${root.horizonVersion}")
println("Core version: ${root.stellarCoreVersion}")
println("Network: ${root.networkPassphrase}")
println("Protocol version: ${root.currentProtocolVersion}")
```

## Pagination

Navigate through paginated results using the `Page` object.

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")
// First page
val page: Page<TransactionResponse> = server.transactions()
    .forAccount(accountId)
    .order(Order.DESC)
    .limit(10)
    .execute()

// Process records
for (tx in page.records) {
    println("TX: ${tx.hash}")
}

// Option 1: Fetch next page using getNextPage (requires passing httpClient)
val nextPage: Page<TransactionResponse>? = page.getNextPage<TransactionResponse>(server.httpClient)
if (nextPage != null) {
    for (tx in nextPage.records) {
        println("TX: ${tx.hash}")
    }
}

// Option 2: Re-execute with cursor from last record's pagingToken
if (page.records.isNotEmpty()) {
    val nextPage2: Page<TransactionResponse> = server.transactions()
        .forAccount(accountId)
        .order(Order.DESC)
        .limit(10)
        .cursor(page.records.last().pagingToken)
        .execute()
}
```

## Error Handling

For error handling patterns (Horizon HTTP errors, transaction submission errors, rate limiting), see [Troubleshooting Guide](./troubleshooting.md).
