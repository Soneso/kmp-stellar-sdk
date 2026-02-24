# Soroban RPC API Reference

Complete guide to Soroban RPC methods with the KMP Stellar SDK.

All code assumes the standard SDK import and a `SorobanServer` instance:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.*
import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.scval.Scv

val server = SorobanServer("https://soroban-testnet.stellar.org:443")
// Public: SorobanServer("https://mainnet.sorobanrpc.com")
```

`SorobanServer` communicates via JSON-RPC 2.0 over HTTP using the Ktor client. It is separate from `HorizonServer`. It implements `AutoCloseable` -- call `server.close()` when done, or use `server.use { ... }`.

All methods are `suspend` functions and must be called from a coroutine scope.

---

## Table of Contents

- [Network and Health Methods](#network-and-health-methods)
  - [getHealth](#gethealth)
  - [getNetwork](#getnetwork)
  - [getLatestLedger](#getlatestledger)
  - [getVersionInfo](#getversioninfo)
  - [getFeeStats](#getfeestats)
- [Get Account Method](#get-account-method)
- [Transaction Methods](#transaction-methods)
  - [simulateTransaction](#simulatetransaction)
  - [prepareTransaction](#preparetransaction)
  - [sendTransaction](#sendtransaction)
  - [getTransaction](#gettransaction)
  - [pollTransaction](#polltransaction)
- [Ledger Query Methods](#ledger-query-methods)
  - [getLedgerEntries](#getledgerentries)
  - [getContractData](#getcontractdata)
  - [getTransactions](#gettransactions)
  - [getLedgers](#getledgers)
  - [getSACBalance](#getsacbalance)
- [Event Methods](#event-methods)
  - [getEvents](#getevents)
- [Contract Introspection Helpers](#contract-introspection-helpers)
- [Error Handling](#error-handling)
- [Method Summary](#method-summary)

---

## Network and Health Methods

### getHealth

Check if the RPC server is operational.

```kotlin
val health = server.getHealth()

if (health.status == "healthy") {
    println("Retention window: ${health.ledgerRetentionWindow} ledgers")
    println("Latest ledger: ${health.latestLedger}")
    println("Oldest ledger: ${health.oldestLedger}")
}
```

**Response fields:** `status` (String), `ledgerRetentionWindow` (Long?), `latestLedger` (Long?), `oldestLedger` (Long?).

```kotlin
// WRONG: health.status == GetHealthResponse.HEALTHY — no constant exists
// CORRECT: health.status == "healthy" — compare against the plain string
```

---

### getNetwork

Retrieve network passphrase, protocol version, and friendbot URL.

```kotlin
val network = server.getNetwork()

println("Passphrase: ${network.passphrase}")
println("Protocol: ${network.protocolVersion}")
if (network.friendbotUrl != null) {
    println("Friendbot: ${network.friendbotUrl}")
}
```

**Response fields:** `passphrase` (String), `friendbotUrl` (String?), `protocolVersion` (Int).

---

### getLatestLedger

Get the most recent ledger known to the server.

```kotlin
val latest = server.getLatestLedger()

println("Sequence: ${latest.sequence}")
println("Hash: ${latest.id}")
println("Protocol version: ${latest.protocolVersion}")
```

**Response fields:** `id` (String), `sequence` (Long), `protocolVersion` (Int).

---

### getVersionInfo

Get RPC server and Captive Core version details.

```kotlin
val info = server.getVersionInfo()

println("RPC version: ${info.version}")
println("Captive Core: ${info.captiveCoreVersion}")
println("Protocol: ${info.protocolVersion}")
```

**Response fields:** `version` (String), `commitHash` (String), `buildTimestamp` (String), `captiveCoreVersion` (String), `protocolVersion` (Int).

---

### getFeeStats

Get fee statistics for recent transactions.

```kotlin
val stats = server.getFeeStats()

val sorobanFee = stats.sorobanInclusionFee
println("Soroban fee p50: ${sorobanFee.p50} stroops")
println("Soroban fee p90: ${sorobanFee.p90} stroops")
println("Soroban fee p99: ${sorobanFee.p99} stroops")

val classicFee = stats.inclusionFee
println("Classic fee p50: ${classicFee.p50} stroops")
```

**Response type:** `GetFeeStatsResponse` with fields `sorobanInclusionFee`, `inclusionFee` (both `FeeDistribution`), `latestLedger` (Long). Each `FeeDistribution` contains percentiles (`p10`, `p20`, ..., `p99`), `min`, `max`, `mode`, `transactionCount`, `ledgerCount`. All fields are `Long`.

```kotlin
// WRONG: stats.sorobanInclusionFee?.p50 — fields are NOT nullable in KMP SDK
// CORRECT: stats.sorobanInclusionFee.p50 — FeeDistribution is non-nullable
// NOTE: The type is FeeDistribution (nested in GetFeeStatsResponse), NOT InclusionFee
```

---

## Get Account Method

`SorobanServer.getAccount()` fetches account info for transaction building, but it **throws** instead of returning null:

```kotlin
// CORRECT: getAccount() returns TransactionBuilderAccount, throws on not found
try {
    val account: TransactionBuilderAccount = server.getAccount(accountId)
    val tx = TransactionBuilder(account, Network.TESTNET)
        .addOperation(op)
        .setBaseFee(100)
        .build()
} catch (e: AccountNotFoundException) {
    println("Account not found: ${e.accountId}")
}

// WRONG: val account: Account? = server.getAccount(accountId) — does NOT return nullable
// CORRECT: it throws AccountNotFoundException if the account doesn't exist
```

**Why `TransactionBuilderAccount` instead of `AccountResponse`?**
- `AccountResponse` is Horizon's rich response with balances, signers, subentries, etc.
- `TransactionBuilderAccount` is a minimal interface with just account ID and sequence number (sufficient for `TransactionBuilder`)
- Soroban RPC doesn't return full account details like Horizon does

```kotlin
// WRONG: server.getAccount(accountId) as AccountResponse — type mismatch
// CORRECT: server.getAccount(accountId) — returns TransactionBuilderAccount
// Use HorizonServer for full account details (balances, signers, etc.)
```

---

## Transaction Methods

### simulateTransaction

Simulate a transaction to estimate resources and preview results. Required before submitting any Soroban transaction.

```kotlin
// Load account for sequence number
val account = server.getAccount(sourceAccountId)

// Build a contract invocation transaction
val invokeOp = InvokeHostFunctionOperation.invokeContractFunction(
    contractAddress = contractId,
    functionName = "hello",
    parameters = listOf(Scv.toSymbol("World"))
)

val tx = TransactionBuilder(account, Network.TESTNET)
    .addOperation(invokeOp)
    .setBaseFee(100)
    .build()

// Simulate (optional: add ResourceConfig for instruction leeway)
val simResponse = server.simulateTransaction(
    transaction = tx,
    resourceConfig = SimulateTransactionRequest.ResourceConfig(
        instructionLeeway = 200000
    )
)

if (simResponse.error != null) {
    println("Simulation error: ${simResponse.error}")
} else {
    println("Min resource fee: ${simResponse.minResourceFee}")

    // Check if entries need restoration
    if (simResponse.restorePreamble != null) {
        println("Restore required before submission")
    }

    // Get simulation result (return value)
    val result = simResponse.results?.firstOrNull()
    val returnValue = result?.parseXdr()
    println("Return value: $returnValue")

    // Get authorization entries
    val authEntries = result?.parseAuth()
    if (authEntries != null) {
        println("Auth entries: ${authEntries.size}")
    }
}
```

**simulateTransaction parameters:**
- `transaction: Transaction` -- the transaction to simulate
- `resourceConfig: SimulateTransactionRequest.ResourceConfig?` -- optional instruction leeway
- `authMode: SimulateTransactionRequest.AuthMode?` -- optional auth mode (ENFORCE, RECORD, RECORD_ALLOW_NONROOT)

**Response fields:** `error` (String?), `transactionData` (String?), `events` (List\<String\>?), `minResourceFee` (Long?), `results` (List\<SimulateHostFunctionResult\>?), `restorePreamble` (RestorePreamble?), `stateChanges` (List\<LedgerEntryChange\>?), `latestLedger` (Long?).

**SimulateHostFunctionResult:** `auth` (List\<String\>?), `xdr` (String?). Parse with `parseAuth()` and `parseXdr()`.

**RestorePreamble:** `transactionData` (String), `minResourceFee` (Long). Parse with `parseTransactionData()`.

```kotlin
// WRONG: simResponse.sorobanAuth — field does NOT exist in KMP SDK
// CORRECT: simResponse.results?.firstOrNull()?.parseAuth() — auth is in the results
// WRONG: simResponse.resultError — field does NOT exist
// CORRECT: simResponse.error — the error string field
```

---

### prepareTransaction

Convenience method that simulates and applies results in one step. This is the **recommended approach** for most use cases.

```kotlin
// Simple: simulate + apply results in one call
val prepared = server.prepareTransaction(tx)
// prepared is a new Transaction with footprint and fees populated

// Sign and submit
prepared.sign(keyPair)
val sendResponse = server.sendTransaction(prepared)

// Or with existing simulation results:
val simulation = server.simulateTransaction(tx)
val prepared2 = server.prepareTransaction(tx, simulation)
```

`prepareTransaction` throws `PrepareTransactionException` if simulation fails:

```kotlin
try {
    val prepared = server.prepareTransaction(tx)
    prepared.sign(keyPair)
    server.sendTransaction(prepared)
} catch (e: PrepareTransactionException) {
    println("Preparation failed: ${e.message}")
    println("Simulation error: ${e.simulationError}")
}
```

There is also a top-level `assembleTransaction()` function for manual assembly:

```kotlin
import com.soneso.stellar.sdk.rpc.assembleTransaction

val simulation = server.simulateTransaction(tx)
val assembled = assembleTransaction(tx, simulation)
// assembled has footprint, fees, and auth entries applied
```

---

### sendTransaction

Submit a signed transaction to the network. Returns immediately; poll `getTransaction` or use `pollTransaction` for results.

```kotlin
val prepared = server.prepareTransaction(tx)
prepared.sign(keyPair)

val sendResponse = server.sendTransaction(prepared)
println("Status: ${sendResponse.status}")
println("Hash: ${sendResponse.hash}")

if (sendResponse.status == SendTransactionStatus.ERROR) {
    println("Error XDR: ${sendResponse.errorResultXdr}")
    // Parse the error for detailed info
    val errorResult = sendResponse.parseErrorResultXdr()
    println("Error details: $errorResult")
}
```

**Response fields:** `status` (SendTransactionStatus), `hash` (String?), `latestLedger` (Long?), `latestLedgerCloseTime` (Long?), `errorResultXdr` (String?), `diagnosticEventsXdr` (List\<String\>?).

**Status values (SendTransactionStatus enum):** `PENDING`, `DUPLICATE`, `TRY_AGAIN_LATER`, `ERROR`.

```kotlin
// WRONG: sendResponse.status == SendTransactionResponse.STATUS_PENDING — no string constants
// CORRECT: sendResponse.status == SendTransactionStatus.PENDING — use the enum
// WRONG: sendResponse.status == "PENDING" — it's an enum, not a string
// CORRECT: sendResponse.status == SendTransactionStatus.PENDING
```

---

### getTransaction

Poll for the status and result of a submitted transaction.

```kotlin
val txResponse = server.getTransaction(txHash)

when (txResponse.status) {
    GetTransactionStatus.SUCCESS -> {
        val returnValue = txResponse.getResultValue()
        println("Return value: $returnValue")
        println("Ledger: ${txResponse.ledger}")
    }
    GetTransactionStatus.NOT_FOUND -> {
        println("Transaction not yet processed")
    }
    GetTransactionStatus.FAILED -> {
        println("Transaction failed: ${txResponse.resultXdr}")
    }
}
```

**Response fields:** `status` (GetTransactionStatus), `txHash` (String?), `latestLedger` (Long?), `envelopeXdr` (String?), `resultXdr` (String?), `resultMetaXdr` (String?), `ledger` (Long?), `createdAt` (Long?), `applicationOrder` (Int?), `feeBump` (Boolean?).

**Status values (GetTransactionStatus enum):** `SUCCESS`, `NOT_FOUND`, `FAILED`.

**Helper methods on GetTransactionResponse:**
- `getResultValue()` -- returns the `SCValXdr` return value from contract invocations (null for classic operations or failed transactions)
- `getCreatedContractId()` -- returns the contract ID (C... string) if the transaction deployed a contract
- `getWasmId()` -- returns the wasm hash (hex string) if the transaction uploaded code
- `parseEnvelopeXdr()` -- returns `TransactionEnvelopeXdr?`
- `parseResultXdr()` -- returns `TransactionResultXdr?`
- `parseResultMetaXdr()` -- returns `TransactionMetaXdr?`

```kotlin
// WRONG: txResponse.status == GetTransactionResponse.STATUS_SUCCESS — no string constants
// CORRECT: txResponse.status == GetTransactionStatus.SUCCESS — use the enum
// WRONG: txResponse.status == "SUCCESS" — it's an enum, not a string
// CORRECT: txResponse.status == GetTransactionStatus.SUCCESS
```

---

### pollTransaction

Built-in polling method that repeatedly calls `getTransaction` until the transaction reaches a final state (SUCCESS or FAILED) or max attempts is reached. This is the **recommended approach** instead of writing a manual polling loop.

```kotlin
// Poll with defaults (30 attempts, 1 second between each)
val result = server.pollTransaction(sendResponse.hash!!)

when (result.status) {
    GetTransactionStatus.SUCCESS -> {
        println("Success! Return value: ${result.getResultValue()}")
    }
    GetTransactionStatus.FAILED -> {
        println("Failed: ${result.resultXdr}")
    }
    GetTransactionStatus.NOT_FOUND -> {
        println("Still pending after max attempts")
    }
}

// Custom polling strategy (60 attempts with exponential backoff)
val result2 = server.pollTransaction(
    hash = sendResponse.hash!!,
    maxAttempts = 60,
    sleepStrategy = { attempt -> (attempt * 500).toLong() }
)
```

**Parameters:**
- `hash: String` -- transaction hash (hex string)
- `maxAttempts: Int` -- default 30
- `sleepStrategy: (Int) -> Long` -- function from attempt number to sleep duration in milliseconds (default: `{ 1000L }`)

```kotlin
// WRONG: writing a manual polling loop with delay()
// CORRECT: server.pollTransaction(hash) — the SDK provides this built-in
// NOTE: pollTransaction swallows transient RPC errors and keeps polling
```

---

## Ledger Query Methods

### getLedgerEntries

Read specific ledger entries by their XDR-encoded keys. Takes `Collection<LedgerKeyXdr>` (typed XDR objects, not base64 strings).

```kotlin
// Build a ledger key for contract data
val ledgerKey = LedgerKeyXdr.ContractData(
    LedgerKeyContractDataXdr(
        contract = Address(contractId).toSCAddress(),
        key = Scv.toSymbol("counter"),
        durability = ContractDataDurabilityXdr.PERSISTENT
    )
)

val response = server.getLedgerEntries(listOf(ledgerKey))

if (!response.entries.isNullOrEmpty()) {
    val entry = response.entries!!.first()
    println("Last modified: ${entry.lastModifiedLedger}")
    println("Expires at ledger: ${entry.liveUntilLedger}")

    // Parse the XDR data
    val data = entry.parseXdr()
    when (data) {
        is LedgerEntryDataXdr.ContractData -> {
            println("Value: ${data.value.`val`}")
        }
        else -> println("Unexpected entry type")
    }
}
```

**Response type:** `GetLedgerEntriesResponse` with `entries` (List\<LedgerEntryResult\>?) and `latestLedger` (Long).

Each `LedgerEntryResult` has:
- `key` (String) -- base64-encoded XDR of the LedgerKey
- `xdr` (String) -- base64-encoded XDR of the LedgerEntryData
- `lastModifiedLedger` (Long) -- JSON field name is `lastModifiedLedgerSeq`
- `liveUntilLedger` (Long?) -- JSON field name is `liveUntilLedgerSeq`
- `parseKey()` -- returns `LedgerKeyXdr`
- `parseXdr()` -- returns `LedgerEntryDataXdr`

```kotlin
// WRONG: server.getLedgerEntries(listOf(ledgerKey.toBase64EncodedXdrString()))
//   — takes Collection<LedgerKeyXdr>, NOT List<String>
// CORRECT: server.getLedgerEntries(listOf(ledgerKey))

// WRONG: entry.ledgerEntryDataXdr — no such property
// CORRECT: entry.parseXdr() — parse the xdr field into LedgerEntryDataXdr

// WRONG: entry.lastModifiedLedgerSeq — Kotlin property name differs from JSON
// CORRECT: entry.lastModifiedLedger — the Kotlin property name

// NOTE: The inner type is LedgerEntryResult (not LedgerEntry)
```

**Entry types:** `Account`, `ContractData`, `ContractCode`, `Trustline`.

**Account entry example:**

```kotlin
// Build a ledger key for an account entry
val accountLedgerKey = LedgerKeyXdr.Account(
    LedgerKeyAccountXdr(
        accountId = KeyPair.fromAccountId(accountId).getXdrAccountId()
    )
)

val accountResponse = server.getLedgerEntries(listOf(accountLedgerKey))

if (!accountResponse.entries.isNullOrEmpty()) {
    val accountEntry = accountResponse.entries!!.first()
    val data = accountEntry.parseXdr()
    when (data) {
        is LedgerEntryDataXdr.Account -> {
            val acct = data.value
            println("Balance (stroops): ${acct.balance.value}")
            println("Sequence number: ${acct.seqNum.value.value}")
            println("Subentries: ${acct.numSubEntries.value}")
        }
        else -> println("Unexpected entry type")
    }
}

// WRONG: acct.seqNum.value — returns SequenceNumberXdr wrapping Int64Xdr, NOT a Long
// CORRECT: acct.seqNum.value.value — SequenceNumber wraps Int64Xdr which wraps Long
```

---

### getContractData

Convenience method to read a single contract data entry.

```kotlin
val entry = server.getContractData(
    contractId = contractId,
    key = Scv.toSymbol("counter"),
    durability = SorobanServer.Durability.PERSISTENT
)

if (entry != null) {
    val data = entry.parseXdr()
    when (data) {
        is LedgerEntryDataXdr.ContractData -> {
            println("Counter value: ${data.value.`val`}")
        }
        else -> {}
    }
} else {
    println("Entry not found")
}
```

**Parameters:**
- `contractId: String` -- the contract address (C... format)
- `key: SCValXdr` -- the contract data key
- `durability: SorobanServer.Durability` -- `TEMPORARY` or `PERSISTENT`

**Returns:** `GetLedgerEntriesResponse.LedgerEntryResult?` (null if not found).

```kotlin
// WRONG: server.getContractData(contractId, key, XdrContractDataDurability.PERSISTENT)
//   — durability param is SorobanServer.Durability, NOT an XDR type
// CORRECT: server.getContractData(contractId, key, SorobanServer.Durability.PERSISTENT)
```

---

### getTransactions

Retrieve a paginated list of transactions from ledger history.

```kotlin
val request = GetTransactionsRequest(
    startLedger = 1000,
    pagination = GetTransactionsRequest.Pagination(limit = 50)
)

val response = server.getTransactions(request)
for (tx in response.transactions) {
    println("TX: ${tx.txHash}, Status: ${tx.status}, Ledger: ${tx.ledger}")
}

// Paginate with cursor
val nextPage = GetTransactionsRequest(
    pagination = GetTransactionsRequest.Pagination(cursor = response.cursor)
)
// val page2 = server.getTransactions(nextPage)
```

**Response type:** `GetTransactionsResponse` with `transactions` (List\<TransactionInfo\>), `latestLedger`, `oldestLedger`, `cursor` (String).

Each `TransactionInfo` has: `status` (TransactionStatus -- SUCCESS or FAILED), `txHash`, `applicationOrder`, `feeBump`, `envelopeXdr`, `resultXdr`, `resultMetaXdr`, `ledger`, `createdAt`, `events` (Events?). Parse methods: `parseEnvelopeXdr()`, `parseResultXdr()`, `parseResultMetaXdr()`.

```kotlin
// WRONG: GetTransactionsRequest(startLedger = 1000, paginationOptions = ...)
//   — the field is called `pagination`, NOT `paginationOptions`
// CORRECT: GetTransactionsRequest(startLedger = 1000, pagination = ...)

// WRONG: GetTransactionsRequest.Pagination(limit = 50) on a cursor page with startLedger set
//   — startLedger and cursor cannot both be set
// CORRECT: Omit startLedger when using cursor
```

---

### getLedgers

Retrieve a paginated list of ledgers from ledger history.

```kotlin
val request = GetLedgersRequest(
    startLedger = 1000,
    pagination = GetLedgersRequest.Pagination(limit = 10)
)

val response = server.getLedgers(request)
for (ledger in response.ledgers) {
    println("Ledger ${ledger.sequence}: hash=${ledger.hash}, closed at=${ledger.ledgerCloseTime}")
}

// Parse XDR data
val header = response.ledgers.first().parseHeaderXdr()
val metadata = response.ledgers.first().parseMetadataXdr()

// Paginate with cursor
val nextPage = GetLedgersRequest(
    pagination = GetLedgersRequest.Pagination(cursor = response.cursor)
)
```

**Response type:** `GetLedgersResponse` with `ledgers` (List\<LedgerInfo\>), `latestLedger`, `oldestLedger`, `cursor`.

Each `LedgerInfo` has: `hash`, `sequence`, `ledgerCloseTime`, `headerXdr`, `metadataXdr`. Parse methods: `parseHeaderXdr()`, `parseMetadataXdr()`.

---

### getSACBalance

Fetch the balance of a Stellar Asset Contract (SAC) for a given contract address.

```kotlin
val balanceResponse = server.getSACBalance(
    contractId = contractId,
    asset = AssetTypeNative,
    network = Network.TESTNET
)

balanceResponse.balanceEntry?.let { entry ->
    println("Balance: ${entry.amount}")
    println("Authorized: ${entry.authorized}")
    println("Clawback: ${entry.clawback}")
    println("Amount as Long: ${entry.getAmountAsLong()}")
} ?: println("No balance found")
```

**Parameters:**
- `contractId: String` -- the contract address holding the asset (must be a valid C... address)
- `asset: Asset` -- the Stellar asset to check balance for
- `network: Network` -- needed for asset contract ID calculation

**Response type:** `GetSACBalanceResponse` with `balanceEntry` (BalanceEntry?) and `latestLedger` (Long).

`BalanceEntry` fields: `amount` (String), `authorized` (Boolean), `clawback` (Boolean), `lastModifiedLedgerSeq` (Long), `liveUntilLedgerSeq` (Long?). Helper methods: `getAmountAsLong()`, `isTemporary()`, `willBeArchivedBy(ledgerSeq)`.

---

## Event Methods

### getEvents

Retrieve contract events within a ledger range with optional filters.

```kotlin
import com.soneso.stellar.sdk.rpc.requests.GetEventsRequest

// Get events from a specific contract
val filter = GetEventsRequest.EventFilter(
    type = GetEventsRequest.EventFilterType.CONTRACT,
    contractIds = listOf(contractId),
    topics = listOf(
        listOf(
            Scv.toSymbol("transfer").toXdrBase64(),
            "**"  // match any remaining topics
        )
    )
)

val request = GetEventsRequest(
    startLedger = 1000,
    filters = listOf(filter),
    pagination = GetEventsRequest.Pagination(limit = 100)
)

val response = server.getEvents(request)
for (event in response.events) {
    println("Event: ${event.id} at ledger ${event.ledger}")
    println("Type: ${event.type}")
    println("Contract: ${event.contractId}")
    println("TX hash: ${event.transactionHash}")

    // Parse topic SCVals
    val topics = event.parseTopic()
    println("Topics: $topics")

    // Parse value SCVal
    val value = event.parseValue()
    println("Value: $value")
}

// Paginate with cursor
if (response.cursor != null) {
    val nextPage = GetEventsRequest(
        filters = listOf(filter),
        pagination = GetEventsRequest.Pagination(cursor = response.cursor)
    )
    // val page2 = server.getEvents(nextPage)
}
```

**GetEventsRequest:** `startLedger` (Long?), `endLedger` (Long?), `filters` (List\<EventFilter\>, max 5), `pagination` (Pagination?). When using cursor, `startLedger` and `endLedger` must be omitted (null).

**EventFilter:** `type` (EventFilterType?), `contractIds` (List\<String\>?, max 5), `topics` (List\<List\<String\>\>?, max 5 topic filters).

**Event filter types (GetEventsRequest.EventFilterType):**
- `CONTRACT` -- events emitted by contract code
- `SYSTEM` -- system-level events (e.g., TTL extensions)
- Note: Diagnostic events are included by default when type is omitted

**Response event type (GetEventsResponse.EventInfo):** There is also a separate `EventFilterType` enum in the `responses` package used in responses.

**Topic filter wildcards:**
- `"*"` -- matches exactly one topic segment at that position
- `"**"` -- matches zero or more remaining segments (only as last element)
- Non-wildcard values must be base64-encoded SCVal XDR strings: `Scv.toSymbol("name").toXdrBase64()`

```kotlin
// WRONG: EventFilter(type = "contract", ...) — type is an enum, not a string
// CORRECT: EventFilter(type = GetEventsRequest.EventFilterType.CONTRACT, ...)

// WRONG: TopicFilter([...]) — no such class in KMP SDK
// CORRECT: topics = listOf(listOf("*", Scv.toSymbol("transfer").toXdrBase64()))
//   — topics is List<List<String>>, using raw strings and XDR base64

// WRONG: GetEventsRequest(startLedger = 1000, filters = listOf(filter), paginationOptions = ...)
// CORRECT: GetEventsRequest(startLedger = 1000, filters = listOf(filter), pagination = ...)
```

---

## Contract Introspection Helpers

`SorobanServer` includes convenience methods for loading contract bytecode and metadata:

```kotlin
// Load contract bytecode by deployed contract ID
val codeEntry: ContractCodeEntryXdr? = server.loadContractCodeForContractId(contractId)
if (codeEntry != null) {
    println("Code size: ${codeEntry.code.size} bytes")
}

// Load contract bytecode by WASM hash
val codeEntry2: ContractCodeEntryXdr? = server.loadContractCodeForWasmId(wasmId)

// Load parsed contract info (functions, types, events)
val info: SorobanContractInfo? = server.loadContractInfoForContractId(contractId)
// or by WASM hash: server.loadContractInfoForWasmId(wasmId)
if (info != null) {
    println("Spec entries: ${info.specEntries.size}")
    println("Meta entries: ${info.metaEntries}")
}
```

For full introspection details (enumerating parameters, UDTs, events), see [Soroban Contracts](./soroban_contracts.md).

---

## Error Handling

`SorobanServer` methods throw exceptions instead of returning error response objects:

```kotlin
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.rpc.exception.PrepareTransactionException
import com.soneso.stellar.sdk.rpc.exception.AccountNotFoundException

try {
    val health = server.getHealth()
} catch (e: SorobanRpcException) {
    // JSON-RPC level error from the server
    println("RPC error code: ${e.errorCode}")
    println("RPC error message: ${e.message}")
    println("RPC error data: ${e.data}")
}

try {
    val account = server.getAccount(accountId)
} catch (e: AccountNotFoundException) {
    println("Account not found: ${e.accountId}")
}

try {
    val prepared = server.prepareTransaction(tx)
} catch (e: PrepareTransactionException) {
    println("Prepare failed: ${e.message}")
    println("Simulation error: ${e.simulationError}")
}
```

```kotlin
// WRONG: if (health.isErrorResponse) { ... } — no such property
// CORRECT: methods throw SorobanRpcException on error; use try/catch

// WRONG: health.error?.message — response objects don't expose error fields
// CORRECT: catch SorobanRpcException to handle RPC errors
```

### Common RPC Error Codes

| Code | Meaning |
|------|---------|
| -32700 | Parse error |
| -32600 | Invalid request |
| -32601 | Method not found |
| -32602 | Invalid params |
| -32603 | Internal error |

---

## Method Summary

| RPC Method | SDK Method | Response Class |
|------------|-----------|----------------|
| `getHealth` | `getHealth()` | `GetHealthResponse` |
| `getNetwork` | `getNetwork()` | `GetNetworkResponse` |
| `getFeeStats` | `getFeeStats()` | `GetFeeStatsResponse` |
| `getVersionInfo` | `getVersionInfo()` | `GetVersionInfoResponse` |
| `getLatestLedger` | `getLatestLedger()` | `GetLatestLedgerResponse` |
| `getLedgerEntries` | `getLedgerEntries(Collection<LedgerKeyXdr>)` | `GetLedgerEntriesResponse` |
| `getTransaction` | `getTransaction(String)` | `GetTransactionResponse` |
| `getTransactions` | `getTransactions(GetTransactionsRequest)` | `GetTransactionsResponse` |
| `getLedgers` | `getLedgers(GetLedgersRequest)` | `GetLedgersResponse` |
| `getEvents` | `getEvents(GetEventsRequest)` | `GetEventsResponse` |
| `simulateTransaction` | `simulateTransaction(Transaction, ResourceConfig?, AuthMode?)` | `SimulateTransactionResponse` |
| `sendTransaction` | `sendTransaction(Transaction)` | `SendTransactionResponse` |

**Helper methods** (not direct RPC calls):
- `getAccount(String)` -- returns `TransactionBuilderAccount`, throws `AccountNotFoundException`
- `getContractData(String, SCValXdr, SorobanServer.Durability)` -- returns `LedgerEntryResult?`
- `getSACBalance(String, Asset, Network)` -- returns `GetSACBalanceResponse`
- `prepareTransaction(Transaction)` -- simulates + applies results, throws `PrepareTransactionException`
- `prepareTransaction(Transaction, SimulateTransactionResponse)` -- applies existing simulation results
- `pollTransaction(String, Int, (Int) -> Long)` -- polls until final state
- `loadContractCodeForContractId(String)` -- returns `ContractCodeEntryXdr?`
- `loadContractCodeForWasmId(String)` -- returns `ContractCodeEntryXdr?`
- `loadContractInfoForContractId(String)` -- returns `SorobanContractInfo?`
- `loadContractInfoForWasmId(String)` -- returns `SorobanContractInfo?`

**Top-level function:**
- `assembleTransaction(Transaction, SimulateTransactionResponse)` -- applies simulation results without a server instance

**Utility class:**
- `SorobanDataBuilder` -- builds `SorobanTransactionDataXdr` for manual footprint construction (extend TTL, restore operations)
