# SEP-06: Programmatic Deposit and Withdrawal

**Purpose:** Non-interactive deposits and withdrawals through anchors without user-facing web flows. All required information is provided in API requests -- no popups or webviews.
**Prerequisites:** Requires JWT from SEP-10 (see `references/sep-10.md`); anchor must publish `TRANSFER_SERVER` in `stellar.toml`
**Package:** `com.soneso.stellar.sdk.sep.sep06`
**Spec:** SEP-0006

Use SEP-06 when you can collect all user information programmatically. Use SEP-24 (`references/sep-24.md`) when the anchor requires an interactive KYC flow.

## Table of Contents

1. [Service Initialization](#1-service-initialization)
2. [Info Endpoint](#2-info-endpoint)
3. [Deposit Flow](#3-deposit-flow)
4. [Deposit Exchange (cross-asset)](#4-deposit-exchange-cross-asset)
5. [Withdraw Flow](#5-withdraw-flow)
6. [Withdraw Exchange (cross-asset)](#6-withdraw-exchange-cross-asset)
7. [Fee Endpoint](#7-fee-endpoint)
8. [Transaction History](#8-transaction-history)
9. [Single Transaction Status](#9-single-transaction-status)
10. [Patch Transaction](#10-patch-transaction)
11. [Sep06Transaction -- All Fields](#11-sep06transaction----all-fields)
12. [Transaction Statuses and Kinds](#12-transaction-statuses-and-kinds)
13. [Error Handling](#13-error-handling)
14. [Common Pitfalls](#14-common-pitfalls)

---

## 1. Service Initialization

### From domain (recommended)

`Sep06Service.fromDomain()` is a `suspend` function that fetches the anchor's `stellar.toml`, reads the `TRANSFER_SERVER` field, and returns a configured service instance. Throws `Sep06Exception` if `TRANSFER_SERVER` is absent.

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service

// Fetches https://anchor.example.com/.well-known/stellar.toml
// and reads TRANSFER_SERVER
val sep06 = Sep06Service.fromDomain("anchor.example.com")
```

Signature:
```kotlin
suspend fun fromDomain(
    domain: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
): Sep06Service
```

### From URL (direct construction)

`Sep06Service.fromUrl()` is NOT a suspend function -- use when you already have the transfer server URL.

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service

// WRONG: fromUrl() is NOT suspend -- do not use await or launch
// CORRECT: call directly, no coroutine needed
val sep06 = Sep06Service.fromUrl("https://api.anchor.com/sep6")
```

Signature:
```kotlin
fun fromUrl(
    serviceAddress: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
): Sep06Service
```

### With custom HTTP client and headers

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import io.ktor.client.*

val client = HttpClient { /* configure */ }
val sep06 = Sep06Service.fromDomain(
    domain = "anchor.example.com",
    httpClient = client,
    httpRequestHeaders = mapOf("User-Agent" to "MyWallet/1.0")
)
```

---

## 2. Info Endpoint

`info()` queries `GET /info` to discover supported assets, fee structures, and feature flags. JWT is optional but may be required by some anchors.

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service

val sep06 = Sep06Service.fromDomain("anchor.example.com")

// Optional: pass JWT and/or language code
val info = sep06.info(jwt = jwtToken, language = "en")
// Or without arguments:
val info = sep06.info()
```

Signature:
```kotlin
suspend fun info(language: String? = null, jwt: String? = null): Sep06InfoResponse
```

### Sep06InfoResponse fields

| Field | Type | Description |
|-------|------|-------------|
| `deposit` | `Map<String, Sep06DepositAsset>?` | Standard deposits, keyed by asset code |
| `depositExchange` | `Map<String, Sep06DepositExchangeAsset>?` | Cross-asset deposits (SEP-38), keyed by asset code |
| `withdraw` | `Map<String, Sep06WithdrawAsset>?` | Standard withdrawals, keyed by asset code |
| `withdrawExchange` | `Map<String, Sep06WithdrawExchangeAsset>?` | Cross-asset withdrawals (SEP-38), keyed by asset code |
| `fee` | `Sep06FeeEndpointInfo?` | `/fee` endpoint availability |
| `transaction` | `Sep06TransactionEndpointInfo?` | `/transaction` endpoint availability |
| `transactions` | `Sep06TransactionsEndpointInfo?` | `/transactions` endpoint availability |
| `features` | `Sep06FeatureFlags?` | Supported anchor features |

### Sep06DepositAsset fields

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | `Boolean` | Whether deposits are supported |
| `authenticationRequired` | `Boolean?` | JWT required before calling deposit |
| `feeFixed` | `String?` | Fixed fee in asset units |
| `feePercent` | `String?` | Percentage fee |
| `minAmount` | `String?` | Minimum deposit amount |
| `maxAmount` | `String?` | Maximum deposit amount |
| `fields` | `Map<String, Sep06Field>?` | Deprecated: required fields |

`Sep06DepositExchangeAsset` has `enabled`, `authenticationRequired`, and `fields` only (no fee/amount fields).

### Sep06WithdrawAsset fields

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | `Boolean` | Whether withdrawals are supported |
| `authenticationRequired` | `Boolean?` | JWT required before calling withdraw |
| `feeFixed` | `String?` | Fixed fee in asset units |
| `feePercent` | `String?` | Percentage fee |
| `minAmount` | `String?` | Minimum withdrawal amount |
| `maxAmount` | `String?` | Maximum withdrawal amount |
| `types` | `Map<String, Sep06WithdrawType>?` | Withdrawal methods with their required fields |

`Sep06WithdrawExchangeAsset` has `enabled`, `authenticationRequired`, and `types` only (no fee/amount fields).

### Sep06WithdrawType fields

| Field | Type | Description |
|-------|------|-------------|
| `fields` | `Map<String, Sep06Field>?` | Required fields for this withdrawal method |

### Sep06FeeEndpointInfo fields

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | `Boolean?` | Whether the `/fee` endpoint is available |
| `authenticationRequired` | `Boolean?` | JWT required for `/fee` |
| `description` | `String?` | Human-readable fee description |

### Sep06TransactionEndpointInfo / Sep06TransactionsEndpointInfo fields

Both have: `enabled` (`Boolean?`) and `authenticationRequired` (`Boolean?`).

### Sep06FeatureFlags fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `accountCreation` | `Boolean` | `true` | Anchor can create accounts for users |
| `claimableBalances` | `Boolean` | `false` | Anchor can send deposits as claimable balances |

### Sep06Field fields

| Field | Type | Description |
|-------|------|-------------|
| `description` | `String?` | Human-readable label shown to user |
| `optional` | `Boolean?` | Whether field is optional (defaults to false) |
| `choices` | `List<String>?` | Valid values if constrained |

### Reading the info response

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service

val sep06 = Sep06Service.fromDomain("anchor.example.com")
val info = sep06.info()

// Check deposit assets
info.deposit?.forEach { (code, asset) ->
    if (asset.enabled) {
        println("Deposit $code: min=${asset.minAmount} max=${asset.maxAmount}")
        asset.feeFixed?.let { println("  Fixed fee: $it") }
        asset.feePercent?.let { println("  Percent fee: $it%") }
        if (asset.authenticationRequired == true) println("  Auth required")
    }
}

// Check a specific deposit asset
val usdDeposit = info.deposit?.get("USD")
if (usdDeposit != null && usdDeposit.enabled) {
    println("USD deposits enabled, min: ${usdDeposit.minAmount}")
}

// Check withdrawal assets and their supported types
info.withdraw?.forEach { (code, asset) ->
    if (asset.enabled && asset.types != null) {
        asset.types!!.forEach { (typeName, withdrawType) ->
            println("  Withdraw $code via $typeName")
            withdrawType.fields?.forEach { (fieldName, field) ->
                println("    $fieldName: ${field.description} " +
                    "(optional=${field.optional ?: false})")
            }
        }
    }
}

// Check fee endpoint
if (info.fee?.enabled == true) {
    println("Fee endpoint available")
    if (info.fee?.authenticationRequired == true) {
        println("Fee endpoint requires auth")
    }
}

// Check feature flags
println("Account creation: ${info.features?.accountCreation}")
println("Claimable balances: ${info.features?.claimableBalances}")
```

---

## 3. Deposit Flow

A deposit is when the user sends external funds (cash, BTC, bank transfer) to the anchor, and the anchor sends equivalent Stellar tokens to the user's Stellar account. Call `info()` first to check the asset's `minAmount`/`maxAmount` and whether `type` is required by the anchor.

### Sep06DepositRequest -- required and optional fields

```kotlin
Sep06DepositRequest(
    assetCode: String,              // on-chain asset code (must match /info deposit keys)
    account: String,                // Stellar (G...), muxed (M...), or contract (C...) account
    jwt: String,                    // JWT from SEP-10 authentication (required)
    assetIssuer: String? = null,    // required when multiple assets share the same code
    memoType: String? = null,       // text, id, or hash
    memo: String? = null,           // for hash: base64-encoded
    emailAddress: String? = null,   // anchor may use for email updates
    type: String? = null,           // deprecated: use fundingMethod
    fundingMethod: String? = null,  // deposit method: SEPA, SWIFT, bank_account, cash, etc.
    amount: String? = null,         // helps anchor determine KYC requirements
    countryCode: String? = null,    // ISO 3166-1 alpha-3 (e.g. "USA", "DEU")
    claimableBalanceSupported: Boolean? = null,  // true if client supports claimable balances
    customerId: String? = null,     // SEP-12 customer ID
    locationId: String? = null,     // cash drop-off location ID
    walletName: String? = null,     // deprecated
    walletUrl: String? = null,      // deprecated
    lang: String? = null,           // RFC 4646 language code (default "en")
    onChangeCallback: String? = null, // URL for anchor to POST status updates
    extraFields: Map<String, String>? = null  // anchor-specific extra fields
)
```

### Basic deposit request

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06DepositRequest
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.exceptions.*

val sep06 = Sep06Service.fromDomain("anchor.example.com")

val request = Sep06DepositRequest(
    assetCode = "USD",
    account = userAccountId,   // Stellar G... or M... account
    jwt = jwtToken
)

try {
    val response = sep06.deposit(request)

    // how: deprecated terse instructions (prefer instructions map)
    response.how?.let { println("How: $it") }

    // instructions: structured deposit instructions keyed by SEP-9 field names
    response.instructions?.forEach { (key, instruction) ->
        println("$key: ${instruction.value} (${instruction.description})")
    }

    // id: anchor's transaction ID for status polling
    response.id?.let { println("Transaction ID: $it") }

    println("ETA: ${response.eta}s")
    println("Fee fixed: ${response.feeFixed}")
    println("Fee percent: ${response.feePercent}")
    println("Min: ${response.minAmount}  Max: ${response.maxAmount}")

    response.extraInfo?.message?.let { println("Note: $it") }

} catch (e: Sep06CustomerInformationNeededException) {
    // HTTP 403, type=non_interactive_customer_info_needed
    // Submit listed fields via SEP-12, then retry
    println("KYC required: ${e.fields}")

} catch (e: Sep06CustomerInformationStatusException) {
    // HTTP 403, type=customer_info_status
    println("KYC status: ${e.status}")
    e.moreInfoUrl?.let { println("More info: $it") }
    e.eta?.let { println("ETA: ${it}s") }

} catch (e: Sep06AuthenticationRequiredException) {
    // HTTP 403, type=authentication_required
    println("Auth required -- get a JWT via SEP-10 first")
}
```

### Sep06DepositResponse fields

| Field | Type | Description |
|-------|------|-------------|
| `how` | `String?` | Deprecated. Terse deposit instructions |
| `instructions` | `Map<String, Sep06DepositInstruction>?` | Structured deposit instructions (preferred) |
| `id` | `String?` | Anchor's transaction ID |
| `eta` | `Long?` | Estimated seconds to credit |
| `minAmount` | `String?` | Minimum deposit amount |
| `maxAmount` | `String?` | Maximum deposit amount |
| `feeFixed` | `String?` | Fixed fee in deposited asset units |
| `feePercent` | `String?` | Percentage fee |
| `extraInfo` | `Sep06ExtraInfo?` | Additional anchor info; has `message: String?` |

**Sep06DepositInstruction** fields: `value` (`String`), `description` (`String`).

### Deposit with all optional fields

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06DepositRequest
import com.soneso.stellar.sdk.sep.sep06.Sep06Service

val sep06 = Sep06Service.fromDomain("anchor.example.com")

val request = Sep06DepositRequest(
    assetCode = "USD",
    account = userAccountId,
    jwt = jwtToken,
    memoType = "id",
    memo = "12345",
    emailAddress = "user@example.com",
    fundingMethod = "SEPA",
    lang = "en",
    onChangeCallback = "https://wallet.example.com/callback",
    amount = "500.00",
    countryCode = "USA",
    claimableBalanceSupported = true,
    customerId = "cust-123",
    locationId = "loc-456",
    extraFields = mapOf("custom_field" to "value")
)

val response = sep06.deposit(request)
```

---

## 4. Deposit Exchange (cross-asset)

Used when the anchor supports SEP-38 quotes and the user deposits one asset type and receives a different Stellar asset. For example: deposit BRL cash and receive USDC on Stellar.

### Sep06DepositExchangeRequest -- required and optional fields

```kotlin
Sep06DepositExchangeRequest(
    destinationAsset: String,       // on-chain Stellar asset code to receive
    sourceAsset: String,            // off-chain asset in SEP-38 format (e.g. "iso4217:BRL")
    amount: String,                 // amount of source asset to deposit
    account: String,                // Stellar or muxed account to receive the asset
    jwt: String,                    // JWT from SEP-10 (required)
    quoteId: String? = null,        // SEP-38 quote ID to lock in exchange rate
    memoType: String? = null,
    memo: String? = null,
    emailAddress: String? = null,
    type: String? = null,           // deprecated: use fundingMethod
    fundingMethod: String? = null,
    countryCode: String? = null,
    claimableBalanceSupported: Boolean? = null,
    customerId: String? = null,
    locationId: String? = null,
    walletName: String? = null,     // deprecated
    walletUrl: String? = null,      // deprecated
    lang: String? = null,
    onChangeCallback: String? = null,
    extraFields: Map<String, String>? = null
)
```

`depositExchange()` returns `Sep06DepositResponse` (same class as regular deposit).

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06DepositExchangeRequest
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.exceptions.*

val sep06 = Sep06Service.fromDomain("anchor.example.com")

// Deposit BRL (off-chain) and receive USDC on Stellar
val request = Sep06DepositExchangeRequest(
    destinationAsset = "USDC",         // on-chain Stellar asset code
    sourceAsset = "iso4217:BRL",       // SEP-38 format for off-chain fiat
    amount = "480.00",                 // amount in source asset (BRL)
    account = userAccountId,
    jwt = jwtToken,
    quoteId = "quote-id-from-sep38"    // optional: lock in exchange rate
)

try {
    val response = sep06.depositExchange(request)
    println("Transaction ID: ${response.id}")
    response.instructions?.forEach { (key, instr) ->
        println("$key: ${instr.value}")
    }
} catch (e: Sep06CustomerInformationNeededException) {
    println("KYC required: ${e.fields}")
}
```

---

## 5. Withdraw Flow

A withdrawal is when the user sends Stellar tokens to the anchor's account, and the anchor sends equivalent external funds (fiat, crypto) to the user's off-chain destination.

### Sep06WithdrawRequest -- required and optional fields

```kotlin
Sep06WithdrawRequest(
    assetCode: String,              // on-chain asset code (must match /info withdraw keys)
    type: String,                   // withdrawal method: bank_account, crypto, cash, mobile, etc.
    jwt: String,                    // JWT from SEP-10 (required)
    fundingMethod: String? = null,  // replaces deprecated type field for new implementations
    dest: String? = null,           // destination (bank account, IBAN, address, etc.)
    destExtra: String? = null,      // extra info (routing number, BIC, memo, etc.)
    account: String? = null,        // source Stellar or muxed account
    memo: String? = null,           // deprecated when using SEP-10
    memoType: String? = null,       // deprecated: text, id, or hash
    amount: String? = null,
    countryCode: String? = null,
    refundMemo: String? = null,     // memo for refund if withdrawal fails
    refundMemoType: String? = null, // id, text, or hash (required if refundMemo set)
    customerId: String? = null,
    locationId: String? = null,
    walletName: String? = null,     // deprecated
    walletUrl: String? = null,      // deprecated
    lang: String? = null,
    onChangeCallback: String? = null,
    extraFields: Map<String, String>? = null
)
```

### Basic withdraw request

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.Sep06WithdrawRequest
import com.soneso.stellar.sdk.sep.sep06.exceptions.*

val sep06 = Sep06Service.fromDomain("anchor.example.com")

val request = Sep06WithdrawRequest(
    assetCode = "USDC",
    type = "bank_account",
    jwt = jwtToken,
    account = userAccountId,   // source Stellar account
    amount = "500.00"
)

try {
    val response = sep06.withdraw(request)

    // accountId: the anchor's Stellar account -- send your payment HERE
    response.accountId?.let { println("Send payment to: $it") }

    // memo / memoType: MUST be included in the Stellar payment to the anchor
    if (response.memoType != null && response.memo != null) {
        println("Memo (${response.memoType}): ${response.memo}")
    }

    println("Transaction ID: ${response.id}")
    println("ETA: ${response.eta}s")
    println("Fee: ${response.feeFixed}")

    response.extraInfo?.message?.let { println("Note: $it") }

} catch (e: Sep06CustomerInformationNeededException) {
    println("KYC required: ${e.fields}")
} catch (e: Sep06CustomerInformationStatusException) {
    println("KYC status: ${e.status}")
} catch (e: Sep06AuthenticationRequiredException) {
    println("Auth required")
}
```

### Sep06WithdrawResponse fields

| Field | Type | Description |
|-------|------|-------------|
| `accountId` | `String?` | Anchor's Stellar account -- send the payment HERE |
| `memoType` | `String?` | Memo type to attach to the Stellar payment (text, id, hash) |
| `memo` | `String?` | Memo value -- MUST include in the Stellar payment |
| `id` | `String?` | Anchor's transaction ID |
| `eta` | `Long?` | Estimated seconds to credit |
| `minAmount` | `String?` | Minimum withdrawal amount |
| `maxAmount` | `String?` | Maximum withdrawal amount |
| `feeFixed` | `String?` | Fixed fee in withdrawn asset units |
| `feePercent` | `String?` | Percentage fee |
| `extraInfo` | `Sep06ExtraInfo?` | Additional anchor info; has `message: String?` |

---

## 6. Withdraw Exchange (cross-asset)

Used when the anchor supports SEP-38 quotes and the user sends one Stellar asset and receives a different off-chain asset. For example: send USDC on Stellar, receive NGN to bank account.

### Sep06WithdrawExchangeRequest -- required and optional fields

```kotlin
Sep06WithdrawExchangeRequest(
    sourceAsset: String,            // on-chain Stellar asset code to withdraw
    destinationAsset: String,       // off-chain asset in SEP-38 format (e.g. "iso4217:NGN")
    amount: String,                 // amount of source asset to send
    type: String,                   // withdrawal method: bank_account, crypto, cash, etc.
    jwt: String,                    // JWT from SEP-10 (required)
    fundingMethod: String? = null,
    quoteId: String? = null,        // SEP-38 quote ID to lock in exchange rate
    dest: String? = null,
    destExtra: String? = null,
    account: String? = null,
    memo: String? = null,
    memoType: String? = null,
    countryCode: String? = null,
    refundMemo: String? = null,
    refundMemoType: String? = null,
    customerId: String? = null,
    locationId: String? = null,
    walletName: String? = null,     // deprecated
    walletUrl: String? = null,      // deprecated
    lang: String? = null,
    onChangeCallback: String? = null,
    extraFields: Map<String, String>? = null
)
```

`withdrawExchange()` returns `Sep06WithdrawResponse` (same class as regular withdraw).

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.Sep06WithdrawExchangeRequest
import com.soneso.stellar.sdk.sep.sep06.exceptions.*

val sep06 = Sep06Service.fromDomain("anchor.example.com")

// Send USDC on Stellar, receive NGN to bank account
val request = Sep06WithdrawExchangeRequest(
    sourceAsset = "USDC",              // on-chain Stellar asset
    destinationAsset = "iso4217:NGN",  // SEP-38 format for off-chain fiat
    amount = "700",                    // amount in source asset (USDC)
    type = "bank_account",
    jwt = jwtToken,
    quoteId = "quote-id-from-sep38"    // optional: lock in exchange rate
)

try {
    val response = sep06.withdrawExchange(request)
    println("Send to: ${response.accountId}")
    response.memo?.let { println("Memo (${response.memoType}): $it") }
    println("Transaction ID: ${response.id}")
} catch (e: Sep06CustomerInformationNeededException) {
    println("KYC required: ${e.fields}")
}
```

---

## 7. Fee Endpoint

Query the fee before initiating a transfer. Only available when `info.fee?.enabled == true`. Note: the `/fee` endpoint is deprecated in the SEP-6 spec; anchors should use SEP-38 quotes instead.

### Sep06FeeRequest fields

```kotlin
Sep06FeeRequest(
    operation: String,     // "deposit" or "withdraw"
    assetCode: String,     // Stellar asset code
    amount: String,        // amount as String (NOT a number)
    type: String? = null,  // deposit/withdrawal method (SEPA, bank_account, etc.)
    jwt: String? = null    // required if fee.authenticationRequired is true
)
```

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06FeeRequest
import com.soneso.stellar.sdk.sep.sep06.Sep06Service

val sep06 = Sep06Service.fromDomain("anchor.example.com")
val info = sep06.info()

if (info.fee?.enabled == true) {
    val feeRequest = Sep06FeeRequest(
        operation = "deposit",      // "deposit" or "withdraw"
        assetCode = "USD",
        amount = "123.09",          // String, NOT a number
        type = "SEPA",              // optional payment method
        jwt = jwtToken              // required if fee.authenticationRequired is true
    )

    val feeResponse = sep06.fee(feeRequest)
    // fee is String -- total fee in asset units
    println("Fee: ${feeResponse.fee}")
}
```

`Sep06FeeResponse` has a single field: `fee` (`String`).

---

## 8. Transaction History

`transactions()` queries `GET /transactions` for deposits and withdrawals associated with an account.

### Sep06TransactionsRequest fields

```kotlin
Sep06TransactionsRequest(
    assetCode: String,             // asset code to filter by
    account: String,               // Stellar account ID
    jwt: String,                   // JWT from SEP-10 (required)
    noOlderThan: String? = null,   // ISO 8601 UTC datetime (e.g. "2023-01-15T12:00:00Z")
    limit: Int? = null,            // max results
    kind: String? = null,          // "deposit", "withdrawal", or comma-separated list
    pagingId: String? = null,      // pagination: return transactions before this ID
    lang: String? = null
)
```

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.Sep06TransactionsRequest

val sep06 = Sep06Service.fromDomain("anchor.example.com")

val request = Sep06TransactionsRequest(
    assetCode = "USD",
    account = userAccountId,
    jwt = jwtToken,
    noOlderThan = "2025-01-01T00:00:00Z",
    limit = 10,
    kind = "deposit"   // optional filter
)

val response = sep06.transactions(request)
// response.transactions is List<Sep06Transaction>

for (tx in response.transactions) {
    println("ID: ${tx.id}  kind: ${tx.kind}  status: ${tx.status}")
    println("  amountIn: ${tx.amountIn}  amountOut: ${tx.amountOut}")
    println("  startedAt: ${tx.startedAt}  completedAt: ${tx.completedAt}")
}
```

`Sep06TransactionsResponse` has a single field: `transactions` (`List<Sep06Transaction>`).

---

## 9. Single Transaction Status

`transaction()` queries `GET /transaction` to get details and status of a specific transaction.

### Sep06TransactionRequest fields

```kotlin
Sep06TransactionRequest(
    id: String? = null,                      // anchor's transaction ID
    stellarTransactionId: String? = null,    // Stellar network transaction hash
    externalTransactionId: String? = null,   // external system ID
    lang: String? = null,
    jwt: String? = null
)
```

At least one of `id`, `stellarTransactionId`, or `externalTransactionId` must be provided.

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.Sep06TransactionRequest

val sep06 = Sep06Service.fromDomain("anchor.example.com")

// Query by anchor transaction ID
val request = Sep06TransactionRequest(
    id = "82fhs729f63dh0v4",
    jwt = jwtToken
)

val response = sep06.transaction(request)
val tx = response.transaction
println("Status: ${tx.status}")
println("Kind: ${tx.kind}")

// Or query by Stellar transaction hash
val request2 = Sep06TransactionRequest(
    stellarTransactionId = "17a670bc424ff5ce3b386dbfa...",
    jwt = jwtToken
)

// Or query by external transaction ID
val request3 = Sep06TransactionRequest(
    externalTransactionId = "1238234",
    jwt = jwtToken
)
```

`Sep06TransactionResponse` has a single field: `transaction` (`Sep06Transaction`).

### Status polling loop

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.Sep06Transaction
import com.soneso.stellar.sdk.sep.sep06.Sep06TransactionRequest
import kotlinx.coroutines.delay

suspend fun pollForCompletion(
    sep06: Sep06Service,
    txId: String,
    jwt: String
): Sep06Transaction {
    while (true) {
        val response = sep06.transaction(
            Sep06TransactionRequest(id = txId, jwt = jwt)
        )
        val tx = response.transaction

        // Use the helper method to check terminal status
        if (tx.isTerminal()) {
            return tx
        }

        // Use statusEta if provided, otherwise default polling interval
        val waitSeconds = tx.statusEta ?: 5L
        delay(waitSeconds * 1000)
    }
}
```

---

## 10. Patch Transaction

When a transaction reaches `pending_transaction_info_update` status, the anchor needs additional information. Use `patchTransaction()` to supply the requested fields.

### Sep06PatchTransactionRequest

```kotlin
Sep06PatchTransactionRequest(
    id: String,                       // transaction ID to update
    fields: Map<String, String>,      // key-value pairs of fields to update
    jwt: String                       // JWT from SEP-10 (required)
)
```

`patchTransaction()` returns `HttpResponse` (Ktor raw HTTP response). Check `response.status.value == 200` for success.

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06PatchTransactionRequest
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.Sep06TransactionRequest

val sep06 = Sep06Service.fromDomain("anchor.example.com")

// 1. Query the transaction to see what fields are needed
val txResponse = sep06.transaction(
    Sep06TransactionRequest(id = "82fhs729f63dh0v4", jwt = jwtToken)
)
val tx = txResponse.transaction

if (tx.status == "pending_transaction_info_update") {
    // requiredInfoMessage describes what the anchor needs
    tx.requiredInfoMessage?.let { println("Anchor says: $it") }

    // requiredInfoUpdates maps field names to Sep06Field descriptions
    tx.requiredInfoUpdates?.forEach { (fieldName, field) ->
        println("Required: $fieldName -- ${field.description}")
    }

    // 2. Submit the updated fields
    val patchRequest = Sep06PatchTransactionRequest(
        id = tx.id,
        fields = mapOf(
            "dest" to "12345678901234",      // bank account number
            "dest_extra" to "021000021"       // routing number
        ),
        jwt = jwtToken
    )

    val patchResponse = sep06.patchTransaction(patchRequest)
    println("PATCH status: ${patchResponse.status.value}") // 200 = success
}
```

---

## 11. Sep06Transaction -- All Fields

```kotlin
// Required fields (always present)
tx.id                      // String  -- unique anchor-generated ID
tx.kind                    // String  -- "deposit", "deposit-exchange", "withdrawal", "withdrawal-exchange"
tx.status                  // String  -- see Transaction Statuses section

// Helper methods
tx.getStatusEnum()         // Sep06TransactionStatus? -- parsed enum (null if unrecognized)
tx.getKindEnum()           // Sep06TransactionKind? -- parsed enum (null if unrecognized)
tx.isTerminal()            // Boolean -- true if completed, refunded, expired, error, no_market, too_small, too_large

// Status / timing
tx.statusEta               // Long?   -- estimated seconds until status change
tx.moreInfoUrl             // String? -- URL for more account/status info
tx.startedAt               // String? -- UTC ISO 8601
tx.updatedAt               // String? -- UTC ISO 8601 (time of last status change)
tx.completedAt             // String? -- UTC ISO 8601
tx.userActionRequiredBy    // String? -- deadline ISO 8601 for user action

// Amount fields (strings)
tx.amountIn                // String? -- amount received by anchor
tx.amountInAsset           // String? -- SEP-38 format; present for exchange transactions
tx.amountOut               // String? -- amount sent to user
tx.amountOutAsset          // String? -- SEP-38 format; present for exchange transactions
tx.amountFee               // String? -- deprecated; prefer feeDetails
tx.amountFeeAsset          // String? -- deprecated; prefer feeDetails

// Fee details (preferred over amountFee / amountFeeAsset)
tx.feeDetails              // Sep06FeeDetails? -- structured fee breakdown
// tx.feeDetails!!.total: String -- total fee amount
// tx.feeDetails!!.asset: String -- fee asset in SEP-38 format
// tx.feeDetails!!.details: List<Sep06FeeDetail>? -- breakdown
//   Sep06FeeDetail.name: String     (e.g. "ACH fee", "Service fee")
//   Sep06FeeDetail.amount: String
//   Sep06FeeDetail.description: String?

// Quote
tx.quoteId                 // String? -- SEP-38 quote ID if used

// Addresses
tx.from                    // String? -- sent-from address (BTC, IBAN, Stellar, etc.)
tx.to                      // String? -- sent-to address
tx.externalExtra           // String? -- routing number, BIC, etc.
tx.externalExtraText       // String? -- bank name or store name

// Deposit-specific
tx.depositMemo             // String? -- memo used on the Stellar payment
tx.depositMemoType         // String?

// Withdrawal-specific
tx.withdrawAnchorAccount   // String? -- anchor's Stellar account for receiving payment
tx.withdrawMemo            // String? -- memo to include in the Stellar payment to anchor
tx.withdrawMemoType        // String?

// Stellar/external identifiers
tx.stellarTransactionId    // String? -- Stellar transaction hash
tx.externalTransactionId   // String? -- external system transaction ID

// Status messages
tx.message                 // String? -- human-readable explanation of current status

// Refunds
tx.refunded                // Boolean? -- deprecated; use refunds
tx.refunds                 // Sep06Refunds?
// tx.refunds!!.amountRefunded: String -- total refunded
// tx.refunds!!.amountFee: String      -- total refund processing fees
// tx.refunds!!.payments: List<Sep06RefundPayment>
//   Sep06RefundPayment.id: String      (Stellar hash or external ref)
//   Sep06RefundPayment.idType: String  ("stellar" or "external")
//   Sep06RefundPayment.amount: String
//   Sep06RefundPayment.fee: String

// Pending info update (when status = pending_transaction_info_update)
tx.requiredInfoMessage     // String?                     -- explanation of what's needed
tx.requiredInfoUpdates     // Map<String, Sep06Field>?    -- fields to supply via PATCH

// Deposit instructions (appears when status reaches pending_user_transfer_start)
tx.instructions            // Map<String, Sep06DepositInstruction>?

// Claimable balance
tx.claimableBalanceId      // String? -- Claimable Balance ID if deposit used claimable balances
```

---

## 12. Transaction Statuses and Kinds

### Statuses (Sep06TransactionStatus enum)

| Status | Enum Value | Meaning |
|--------|------------|---------|
| `incomplete` | `INCOMPLETE` | Missing required info; user action needed |
| `pending_user_transfer_start` | `PENDING_USER_TRANSFER_START` | Waiting for user to send funds to anchor |
| `pending_user_transfer_complete` | `PENDING_USER_TRANSFER_COMPLETE` | User sent funds; anchor processing |
| `pending_external` | `PENDING_EXTERNAL` | Waiting on external system (bank, crypto network) |
| `pending_anchor` | `PENDING_ANCHOR` | Anchor is processing internally |
| `pending_stellar` | `PENDING_STELLAR` | Stellar transaction pending |
| `pending_trust` | `PENDING_TRUST` | User must add a trustline for the asset |
| `pending_user` | `PENDING_USER` | Anchor waiting for user action (check message) |
| `pending_customer_info_update` | `PENDING_CUSTOMER_INFO_UPDATE` | Anchor needs more KYC info via SEP-12 |
| `pending_transaction_info_update` | `PENDING_TRANSACTION_INFO_UPDATE` | Anchor needs more transaction info -- PATCH |
| `completed` | `COMPLETED` | Successfully completed (terminal) |
| `refunded` | `REFUNDED` | Refunded to user (terminal) |
| `expired` | `EXPIRED` | Timed out without completion (terminal) |
| `no_market` | `NO_MARKET` | No market for conversion (terminal) |
| `too_small` | `TOO_SMALL` | Amount below anchor's minimum (terminal) |
| `too_large` | `TOO_LARGE` | Amount exceeds anchor's maximum (terminal) |
| `error` | `ERROR` | Unrecoverable error (terminal) |

Using the enum:

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06TransactionStatus

// Parse from string
val status = Sep06TransactionStatus.fromValue("completed")
// status == Sep06TransactionStatus.COMPLETED

// Check status categories
status?.isTerminal()   // true
status?.isPending()    // false
status?.isError()      // false

// Check terminal from raw string
Sep06TransactionStatus.isTerminal("error")  // true

// Pre-defined sets
Sep06TransactionStatus.terminalStatuses  // COMPLETED, REFUNDED, EXPIRED, ERROR, NO_MARKET, TOO_SMALL, TOO_LARGE
Sep06TransactionStatus.errorStatuses     // ERROR, NO_MARKET, TOO_SMALL, TOO_LARGE
Sep06TransactionStatus.pendingStatuses   // all PENDING_* statuses
```

### Kinds (Sep06TransactionKind enum)

| Kind | Enum Value | Description |
|------|------------|-------------|
| `deposit` | `DEPOSIT` | Standard deposit |
| `withdrawal` | `WITHDRAWAL` | Standard withdrawal |
| `deposit-exchange` | `DEPOSIT_EXCHANGE` | Deposit with SEP-38 asset exchange |
| `withdrawal-exchange` | `WITHDRAWAL_EXCHANGE` | Withdrawal with SEP-38 asset exchange |

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06TransactionKind

val kind = Sep06TransactionKind.fromValue("deposit-exchange")
kind?.isDeposit()    // true
kind?.isExchange()   // true
kind?.isWithdrawal() // false
```

---

## 13. Error Handling

Six exception types are thrown for different error conditions. All extend `Sep06Exception`.

```kotlin
import com.soneso.stellar.sdk.sep.sep06.Sep06Service
import com.soneso.stellar.sdk.sep.sep06.Sep06DepositRequest
import com.soneso.stellar.sdk.sep.sep06.exceptions.*

val sep06 = Sep06Service.fromDomain("anchor.example.com")

try {
    val response = sep06.deposit(request)

} catch (e: Sep06CustomerInformationNeededException) {
    // HTTP 403, type=non_interactive_customer_info_needed
    // e.fields is List<String> -- SEP-12 field names to submit
    println("KYC fields required: ${e.fields}")
    // Submit listed fields via SEP-12 PUT /customer, then retry

} catch (e: Sep06CustomerInformationStatusException) {
    // HTTP 403, type=customer_info_status
    val status = e.status       // "pending" or "denied"
    val url = e.moreInfoUrl     // String?
    val eta = e.eta             // Long? seconds
    if (status == "denied") {
        println("KYC denied. Details: $url")
    } else if (status == "pending") {
        println("KYC under review. ETA: ${eta}s")
    }

} catch (e: Sep06AuthenticationRequiredException) {
    // HTTP 403, type=authentication_required
    // No JWT provided or JWT is invalid/expired
    println("Auth required -- obtain a JWT via SEP-10 first")

} catch (e: Sep06InvalidRequestException) {
    // HTTP 400 -- invalid parameters
    println("Bad request: ${e.errorMessage}")

} catch (e: Sep06TransactionNotFoundException) {
    // HTTP 404 -- transaction not found
    println("Not found: ${e.transactionId}")

} catch (e: Sep06ServerErrorException) {
    // HTTP 5xx -- anchor server error
    println("Server error (${e.statusCode}): ${e.errorMessage}")
}
```

### Exception reference

| Exception | When thrown | Key properties |
|-----------|-------------|---------------|
| `Sep06AuthenticationRequiredException` | No/invalid JWT (403) | (none) |
| `Sep06CustomerInformationNeededException` | KYC data required (403) | `fields: List<String>` |
| `Sep06CustomerInformationStatusException` | KYC pending or denied (403) | `status: String`, `moreInfoUrl: String?`, `eta: Long?` |
| `Sep06InvalidRequestException` | Bad request (400) | `errorMessage: String` |
| `Sep06TransactionNotFoundException` | Transaction not found (404) | `transactionId: String?` |
| `Sep06ServerErrorException` | Anchor server error (5xx) | `statusCode: Int`, `errorMessage: String?` |

---

## 14. Common Pitfalls

**WRONG: KMP SDK uses `String` for amount and fee fields, not `Double`**

In the Flutter SDK, some response fields use `double`. In the KMP SDK, amounts and fees are consistently `String?` in both requests and responses.

```kotlin
// WRONG: treating response amounts as numeric types
val amount: Double = response.feeFixed  // compile error -- feeFixed is String?

// CORRECT: amounts are strings, convert if needed
val amountStr: String? = response.feeFixed
val amountNum: Double? = response.feeFixed?.toDoubleOrNull()
```

**WRONG: KMP uses `Boolean` for `claimableBalanceSupported`, not `String`**

In the Flutter SDK, `claimableBalanceSupported` is a `String?` ("true"/"false"). In the KMP SDK, it is `Boolean?`.

```kotlin
// WRONG (Flutter pattern): passing as string
Sep06DepositRequest(assetCode = "USD", account = id, jwt = jwt,
    claimableBalanceSupported = "true")  // compile error

// CORRECT: pass as Boolean
Sep06DepositRequest(assetCode = "USD", account = id, jwt = jwt,
    claimableBalanceSupported = true)
```

**WRONG: KMP uses `Sep06FeeRequest.amount` as `String`, not `Double`**

```kotlin
// WRONG: amount is String, not a numeric type
Sep06FeeRequest(operation = "deposit", assetCode = "USD", amount = 123.09)  // compile error

// CORRECT: amount is String
Sep06FeeRequest(operation = "deposit", assetCode = "USD", amount = "123.09")
```

**WRONG: `fromDomain()` is suspend but `fromUrl()` is not**

```kotlin
// WRONG: calling fromDomain() outside a coroutine
val sep06 = Sep06Service.fromDomain("anchor.example.com")  // must be in a suspend function

// CORRECT: fromDomain() is suspend -- call inside a coroutine
suspend fun setup(): Sep06Service {
    return Sep06Service.fromDomain("anchor.example.com")
}

// CORRECT: fromUrl() is NOT suspend -- can call anywhere
val sep06 = Sep06Service.fromUrl("https://api.anchor.com/sep6")
```

**WRONG: forgetting the memo when sending a Stellar payment for a withdrawal**

When `withdraw()` returns, you must build a Stellar payment to `Sep06WithdrawResponse.accountId` and include `Sep06WithdrawResponse.memo` / `Sep06WithdrawResponse.memoType`. Without the memo the anchor cannot match the transaction.

```kotlin
// After calling sep06.withdraw(request):
// response.accountId -- anchor's Stellar account to pay
// response.memo      -- memo value to include
// response.memoType  -- "text", "id", or "hash"
```

**WRONG: `WithdrawAsset.types` maps to `Sep06WithdrawType`, not directly to `Sep06Field`**

```kotlin
// WRONG: types values are Sep06WithdrawType, not Sep06Field
info.withdraw?.get("USDC")?.types?.forEach { (typeName, field) ->
    println(field.description) // compile error -- Sep06WithdrawType has no description
}

// CORRECT: Sep06WithdrawType has a fields property containing the map of Sep06Field
info.withdraw?.get("USDC")?.types?.forEach { (typeName, withdrawType) ->
    println("Type: $typeName")
    withdrawType.fields?.forEach { (fieldName, field) ->
        println("  $fieldName: ${field.description}")
    }
}
```

**WRONG: treating `patchTransaction` response as a typed SDK object**

```kotlin
// WRONG: patchTransaction returns Ktor HttpResponse, not a typed object
val r: Sep06DepositResponse = sep06.patchTransaction(patchRequest) // compile error

// CORRECT: returns io.ktor.client.statement.HttpResponse -- check status
import io.ktor.client.statement.HttpResponse
val response: HttpResponse = sep06.patchTransaction(patchRequest)
println(response.status.value) // 200 = success
```

**WRONG: confusing `Sep06TransactionsRequest.noOlderThan` type with `DateTime`**

```kotlin
// WRONG (Flutter pattern): passing a DateTime object
Sep06TransactionsRequest(
    assetCode = "USD", account = id, jwt = jwt,
    noOlderThan = Clock.System.now()  // compile error -- expects String
)

// CORRECT: pass an ISO 8601 UTC datetime string
Sep06TransactionsRequest(
    assetCode = "USD", account = id, jwt = jwt,
    noOlderThan = "2025-01-01T00:00:00Z"
)
```

**WRONG: `jwt` is required (not optional) on deposit, withdraw, and their exchange variants**

```kotlin
// WRONG: jwt is a required parameter in Sep06DepositRequest
Sep06DepositRequest(assetCode = "USD", account = id)  // compile error -- missing jwt

// CORRECT: always provide jwt
Sep06DepositRequest(assetCode = "USD", account = id, jwt = jwtToken)
```

**WRONG: `type` is required on `Sep06WithdrawRequest` and `Sep06WithdrawExchangeRequest`**

```kotlin
// WRONG: type is required, not optional (even though it is deprecated)
Sep06WithdrawRequest(assetCode = "USDC", jwt = jwt)  // compile error -- missing type

// CORRECT: always provide type (it is still required for compatibility)
Sep06WithdrawRequest(assetCode = "USDC", type = "bank_account", jwt = jwt)
```
