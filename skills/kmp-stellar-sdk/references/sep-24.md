# SEP-24: Interactive Deposit and Withdrawal

**Purpose:** Interactive web flows for depositing external assets (fiat, crypto) to receive Stellar tokens, or withdrawing Stellar tokens to an external destination (bank account, crypto wallet, etc.).
**Prerequisites:** Requires JWT from SEP-10 (see `references/sep-10.md`); anchor must publish `TRANSFER_SERVER_SEP0024` in `stellar.toml`
**Package:** `com.soneso.stellar.sdk.sep.sep24`
**Spec:** SEP-0024

## Table of Contents

1. [Full Flow: SEP-10 Auth + SEP-24 Deposit](#1-full-flow-sep-10-auth--sep-24-deposit)
2. [Service Initialization](#2-service-initialization)
3. [Info Endpoint](#3-info-endpoint)
4. [Fee Endpoint (deprecated)](#4-fee-endpoint-deprecated)
5. [Deposit Flow](#5-deposit-flow)
6. [Withdrawal Flow](#6-withdrawal-flow)
7. [Transaction Status Polling](#7-transaction-status-polling)
8. [Transaction History](#8-transaction-history)
9. [Sep24Transaction -- All Fields](#9-sep24transaction----all-fields)
10. [Transaction Statuses](#10-transaction-statuses)
11. [Fee Details](#11-fee-details)
12. [Refund Objects](#12-refund-objects)
13. [Error Handling](#13-error-handling)
14. [Common Pitfalls](#14-common-pitfalls)

---

## 1. Full Flow: SEP-10 Auth + SEP-24 Deposit

SEP-24 requires a JWT from SEP-10 authentication. Here is the complete end-to-end flow:

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep24.Sep24DepositRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionStatus

val anchorDomain = "testanchor.stellar.org"
val network = Network.TESTNET

// Step 1: Authenticate via SEP-10 to get a JWT
val webAuth = WebAuth.fromDomain(anchorDomain, network)
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMCLXPILHSE7GIQST...")
val authToken = webAuth.jwtToken(
    clientAccountId = keyPair.getAccountId(),
    signers = listOf(keyPair)
)
val jwt = authToken.token  // raw JWT string for SEP-24 calls

// Step 2: Initialize SEP-24 service
val sep24 = Sep24Service.fromDomain(anchorDomain)

// Step 3: Check supported assets
val info = sep24.info()
val usdcDeposit = info.depositAssets?.get("USDC")
if (usdcDeposit?.enabled != true) {
    println("USDC deposit not supported")
    return
}

// Step 4: Initiate interactive deposit
val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwt
))

// Step 5: Open response.url in a browser/webview for the user
println("Open URL: ${response.url}")
println("Transaction ID: ${response.id}")

// Step 6: Poll for completion
val tx = sep24.pollTransaction(
    request = Sep24TransactionRequest(jwt = jwt, id = response.id),
    onStatusChange = { println("Status: ${it.status}") }
)

when (tx.getStatusEnum()) {
    Sep24TransactionStatus.COMPLETED -> println("Done! Received: ${tx.amountOut}")
    Sep24TransactionStatus.ERROR -> println("Failed: ${tx.message}")
    else -> println("Final status: ${tx.status}")
}
```

---

## 2. Service Initialization

### From domain (recommended)

`Sep24Service.fromDomain()` fetches the anchor's `stellar.toml`, reads `TRANSFER_SERVER_SEP0024`, and returns a configured service instance. Throws `Sep24Exception` if the field is absent.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service

// Fetches stellar.toml from https://testanchor.stellar.org/.well-known/stellar.toml
// and reads TRANSFER_SERVER_SEP0024
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
```

Signature:
```kotlin
suspend fun fromDomain(
    domain: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
): Sep24Service
```

### Direct construction

Use when you already have the transfer server URL.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service

val sep24 = Sep24Service(
    serviceAddress = "https://api.anchor.com/sep24"
)
```

Constructor:
```kotlin
class Sep24Service(
    serviceAddress: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
)
```

### With custom HTTP client and headers

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import io.ktor.client.*

val client = HttpClient { /* configure */ }
val sep24 = Sep24Service.fromDomain(
    domain = "testanchor.stellar.org",
    httpClient = client,
    httpRequestHeaders = mapOf("User-Agent" to "MyWallet/1.0")
)
```

---

## 3. Info Endpoint

`info()` queries `GET /info` to discover supported assets, fee structures, and feature flags. No authentication required.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

// Optional: pass a language code (RFC 4646, e.g. "en", "de")
val info = sep24.info(lang = "en")
// Or without language:
val info = sep24.info()
```

Signature:
```kotlin
suspend fun info(lang: String? = null): Sep24InfoResponse
```

### Sep24InfoResponse fields

| Field | Type | Description |
|-------|------|-------------|
| `depositAssets` | `Map<String, Sep24AssetInfo>?` | Keyed by asset code; null if absent |
| `withdrawAssets` | `Map<String, Sep24AssetInfo>?` | Keyed by asset code; null if absent |
| `feeEndpoint` | `Sep24FeeEndpointInfo?` | Info about the `/fee` endpoint; null if absent |
| `features` | `Sep24Features?` | Optional features the anchor supports; null if absent |

### Sep24AssetInfo fields

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | `Boolean` | Whether this asset is enabled for the operation |
| `minAmount` | `String?` | Minimum amount; null if no limit |
| `maxAmount` | `String?` | Maximum amount; null if no limit |
| `feeFixed` | `String?` | Fixed fee in units of the asset |
| `feePercent` | `String?` | Percentage fee in percentage points |
| `feeMinimum` | `String?` | Minimum fee in units of the asset |

**Note:** `Sep24AssetInfo` is used for both deposit and withdrawal assets. All amount/fee fields are `String?`, not `Double?`.

### Sep24FeeEndpointInfo fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `enabled` | `Boolean` | -- | Whether the `/fee` endpoint is available |
| `authenticationRequired` | `Boolean` | `false` | Whether JWT is required for `/fee` |

### Sep24Features fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `accountCreation` | `Boolean` | `true` | Anchor can create accounts for users |
| `claimableBalances` | `Boolean` | `false` | Anchor can send deposits as claimable balances |

### Reading info response

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val info = sep24.info()

// Check deposit assets (keyed by asset code)
info.depositAssets?.forEach { (code, assetInfo) ->
    if (assetInfo.enabled) {
        println("Deposit $code: min=${assetInfo.minAmount} max=${assetInfo.maxAmount}")
        assetInfo.feeFixed?.let { println("  Fixed fee: $it") }
        assetInfo.feePercent?.let { println("  Percent fee: $it%") }
        assetInfo.feeMinimum?.let { println("  Min fee: $it") }
    }
}

// Check a specific asset
val usdDeposit = info.depositAssets?.get("USD")
if (usdDeposit != null && usdDeposit.enabled) {
    println("USD deposit enabled")
}

// Check withdraw assets
val usdWithdraw = info.withdrawAssets?.get("USD")
if (usdWithdraw != null && usdWithdraw.enabled) {
    println("USD withdrawal enabled, fee minimum: ${usdWithdraw.feeMinimum}")
}

// Check feature support
info.features?.let { features ->
    println("Account creation: ${features.accountCreation}")
    println("Claimable balances: ${features.claimableBalances}")
}

// Check fee endpoint
info.feeEndpoint?.let { fee ->
    println("Fee endpoint enabled: ${fee.enabled}")
    println("Auth required: ${fee.authenticationRequired}")
}
```

---

## 4. Fee Endpoint (deprecated)

The `/fee` endpoint is deprecated in favor of SEP-38 `GET /price`. Only use it if the anchor's `/info` response indicates it is enabled. Authentication may be required (check `feeEndpoint?.authenticationRequired`).

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24FeeRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24Service

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val info = sep24.info()

if (info.feeEndpoint?.enabled == true) {
    val feeResponse = sep24.fee(Sep24FeeRequest(
        operation = "deposit",     // "deposit" or "withdraw"
        assetCode = "USD",
        amount = "100",            // String, not Double
        jwt = jwtToken,            // required if authenticationRequired is true
        type = "bank_account"      // optional: payment method
    ))
    println("Fee: ${feeResponse.fee}")
}
```

Signature:
```kotlin
suspend fun fee(request: Sep24FeeRequest): Sep24FeeResponse
```

### Sep24FeeRequest constructor parameters

```kotlin
data class Sep24FeeRequest(
    val operation: String,        // "deposit" or "withdraw" (required)
    val assetCode: String,        // asset code, e.g. "USD" (required)
    val amount: String,           // amount as string (required)
    val jwt: String? = null,      // JWT from SEP-10; required if authenticationRequired is true
    val type: String? = null      // payment method type, e.g. "SEPA", "bank_account"
)
```

`Sep24FeeResponse` has a single field: `fee` (`String?`).

**Throws:** `Sep24AuthenticationRequiredException` (403), `Sep24InvalidRequestException` (400), `Sep24ServerErrorException` (5xx).

---

## 5. Deposit Flow

A deposit converts external funds (bank transfer, crypto, etc.) into Stellar tokens sent to the user's account. The anchor returns a URL for the user to complete the process interactively.

`deposit()` posts to `POST /transactions/deposit/interactive`.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24DepositRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24Service

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwtToken   // JWT from SEP-10 authentication -- always required
))

// Open this URL in a browser popup or webview
println("Open URL: ${response.url}")
// Save transaction ID for polling
val transactionId = response.id
// response.type is always "interactive_customer_info_needed"
```

Signature:
```kotlin
suspend fun deposit(request: Sep24DepositRequest): Sep24InteractiveResponse
```

### Sep24DepositRequest constructor parameters

```kotlin
data class Sep24DepositRequest(
    val assetCode: String,                    // asset code to receive; "native" for XLM (required)
    val jwt: String,                          // JWT from SEP-10 authentication (required)
    val assetIssuer: String? = null,          // issuer G... address; omit for "native"
    val sourceAsset: String? = null,          // SEP-38 format asset user sends (e.g. "iso4217:EUR")
    val amount: String? = null,               // amount as string; collected in flow if omitted
    val quoteId: String? = null,              // SEP-38 quote ID for cross-asset deposits
    val account: String? = null,              // destination Stellar/muxed/contract account; defaults to JWT account
    val memo: String? = null,                 // memo to attach; hash type must be base64-encoded
    val memoType: String? = null,             // "text", "id", or "hash"
    val walletName: String? = null,           // wallet display name
    val walletUrl: String? = null,            // wallet URL
    val lang: String? = null,                 // RFC 4646 language (e.g. "en-US")
    val claimableBalanceSupported: Boolean? = null, // true if client supports claimable balances
    val customerId: String? = null,           // SEP-12 customer ID to skip redundant KYC
    val kycFields: Map<String, String>? = null,   // SEP-9 KYC field key-value pairs
    val kycFiles: Map<String, ByteArray>? = null  // SEP-9 KYC file uploads (field name to bytes)
)
```

### Sep24InteractiveResponse fields

| Field | Type | Description |
|-------|------|-------------|
| `type` | `String` | Always `"interactive_customer_info_needed"` |
| `url` | `String` | URL to open in a browser or webview for the user |
| `id` | `String` | Anchor-generated transaction ID for polling |

### Deposit with amount and destination account

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24DepositRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24Service

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USD",
    jwt = jwtToken,
    amount = "100.0",   // String, not Double
    account = "GXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
    memo = "12345",
    memoType = "id",    // "text", "id", or "hash"
    lang = "en-US"
))
println("Open: ${response.url}")
```

### Deposit with SEP-38 quote (cross-asset)

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24DepositRequest

// Get quoteId from SEP-38 service first
val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    sourceAsset = "iso4217:EUR",  // user sends EUR, receives USDC
    quoteId = "quote-abc-123",
    amount = "100.0"              // must match the quote's sell_amount
))
```

### Deposit with KYC pre-fill

Pass KYC data as key-value pairs to pre-fill the anchor's interactive form. Field names follow SEP-9.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24DepositRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24Service

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USD",
    jwt = jwtToken,
    // SEP-9 KYC fields as key-value pairs
    kycFields = mapOf(
        "first_name" to "George",
        "email_address" to "george@example.com",
        "bank_account_number" to "XX18981288373773"
    ),
    // Binary file uploads (e.g. ID photo)
    kycFiles = mapOf(
        "photo_id_front" to idPhotoBytes  // ByteArray of the file
    )
))
```

### Deposit with claimable balance support

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24DepositRequest

val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USD",
    jwt = jwtToken,
    // Tell the anchor the client supports receiving claimable balances.
    // Useful if the account has no trustline for the asset.
    // WRONG: claimableBalanceSupported = "true"  -- field is Boolean?, not String
    // CORRECT: claimableBalanceSupported = true
    claimableBalanceSupported = true
))
// After completion, check tx.claimableBalanceId if the anchor used a claimable balance
```

**Throws:** `Sep24AuthenticationRequiredException` (403), `Sep24InvalidRequestException` (400), `Sep24ServerErrorException` (5xx).

---

## 6. Withdrawal Flow

A withdrawal converts Stellar tokens into external assets sent to a bank account or other destination. After the user completes the interactive flow, the wallet sends a Stellar payment to the anchor's account.

`withdraw()` posts to `POST /transactions/withdraw/interactive`.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import com.soneso.stellar.sdk.sep.sep24.Sep24WithdrawRequest

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

val response = sep24.withdraw(Sep24WithdrawRequest(
    assetCode = "USDC",
    jwt = jwtToken   // JWT from SEP-10 authentication -- always required
))

println("Open URL: ${response.url}")
val transactionId = response.id
```

Signature:
```kotlin
suspend fun withdraw(request: Sep24WithdrawRequest): Sep24InteractiveResponse
```

### Sep24WithdrawRequest constructor parameters

```kotlin
data class Sep24WithdrawRequest(
    val assetCode: String,                    // asset code to withdraw; "native" for XLM (required)
    val jwt: String,                          // JWT from SEP-10 authentication (required)
    val assetIssuer: String? = null,          // issuer G... address; omit for "native"
    val destinationAsset: String? = null,     // SEP-38 format asset user receives (e.g. "iso4217:EUR")
    val amount: String? = null,               // amount as string; collected in flow if omitted
    val quoteId: String? = null,              // SEP-38 quote ID for cross-asset withdrawals
    val account: String? = null,              // source Stellar/muxed/contract account; defaults to JWT account
    @Deprecated val memo: String? = null,     // deprecated: use SEP-10 JWT sub for shared accounts
    @Deprecated val memoType: String? = null, // deprecated: type of deprecated memo field
    val walletName: String? = null,           // wallet display name
    val walletUrl: String? = null,            // wallet URL
    val lang: String? = null,                 // RFC 4646 language for the interactive UI
    val refundMemo: String? = null,           // memo for refund payments; requires refundMemoType
    val refundMemoType: String? = null,       // refund memo type: "text", "id", or "hash"
    val customerId: String? = null,           // SEP-12 customer ID to skip redundant KYC
    val kycFields: Map<String, String>? = null,   // SEP-9 KYC field key-value pairs
    val kycFiles: Map<String, ByteArray>? = null  // SEP-9 KYC file uploads (field name to bytes)
)
```

### Withdrawal with refund memo

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24WithdrawRequest

val response = sep24.withdraw(Sep24WithdrawRequest(
    assetCode = "USD",
    jwt = jwtToken,
    amount = "500.0",
    // Memo the anchor uses if it needs to send a refund payment back
    refundMemo = "refund-ref-123",
    refundMemoType = "text"   // "text", "id", or "hash"
    // Must set both refundMemo and refundMemoType together
))
```

### Withdrawal with SEP-38 quote (cross-asset)

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24WithdrawRequest

val response = sep24.withdraw(Sep24WithdrawRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    destinationAsset = "iso4217:EUR",  // user sends USDC, receives EUR
    quoteId = "quote-xyz-789",
    amount = "500.0"
))
```

### Completing a withdrawal: sending the Stellar payment

After the user completes the interactive flow, poll for `pending_user_transfer_start` status, then send the Stellar payment to the anchor's account.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.Asset
import com.soneso.stellar.sdk.Memo
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.operations.PaymentOperation
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionRequest

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

val txResponse = sep24.transaction(Sep24TransactionRequest(
    jwt = jwtToken,
    id = transactionId
))
val tx = txResponse.transaction

if (tx.status == "pending_user_transfer_start") {
    // withdrawMemo may be null if KYC is not yet complete -- check before sending
    if (tx.withdrawMemo == null) {
        println("KYC not yet verified -- wait before sending payment")
        // Continue polling until withdrawMemo is set
    } else {
        // Read withdrawal payment details from transaction
        val anchorAccount = tx.withdrawAnchorAccount!!  // anchor's Stellar account
        val memo = tx.withdrawMemo!!
        val memoType = tx.withdrawMemoType!!             // "text", "id", or "hash"
        val amount = tx.amountIn!!

        val horizon = HorizonServer("https://horizon-testnet.stellar.org")
        val sourceKeyPair = KeyPair.fromSecretSeed(secretSeed)
        val sourceAccount = horizon.accounts().account(sourceKeyPair.getAccountId())

        val asset = Asset.createNonNativeAsset("USD", issuerAccountId)

        val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
            .addOperation(PaymentOperation(anchorAccount, asset, amount))
            .addMemo(Memo.text(memo))  // adjust for memoType
            .build()

        transaction.sign(sourceKeyPair)
        horizon.submitTransaction(transaction)
    }
}
```

**Throws:** `Sep24AuthenticationRequiredException` (403), `Sep24InvalidRequestException` (400), `Sep24ServerErrorException` (5xx).

---

## 7. Transaction Status Polling

### Single transaction lookup

Use `transaction()` to query a single transaction by ID. Always use the `id` from `deposit()` or `withdraw()` for polling.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionRequest

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

// Query by anchor transaction ID (from deposit/withdraw response)
val response = sep24.transaction(Sep24TransactionRequest(
    jwt = jwtToken,
    id = transactionId               // from Sep24InteractiveResponse.id
))

// OR query by Stellar network transaction hash
// Sep24TransactionRequest(jwt = jwtToken, stellarTransactionId = "abc123...")

// OR query by external system transaction ID
// Sep24TransactionRequest(jwt = jwtToken, externalTransactionId = "BANK-REF-123")

val tx = response.transaction
println("Status: ${tx.status}")
println("Kind: ${tx.kind}")
```

Signature:
```kotlin
suspend fun transaction(request: Sep24TransactionRequest): Sep24TransactionResponse
```

`Sep24TransactionResponse` has a single field: `transaction` (`Sep24Transaction`).

### Sep24TransactionRequest constructor parameters

```kotlin
data class Sep24TransactionRequest(
    val jwt: String,                            // JWT from SEP-10 (required)
    val id: String? = null,                     // anchor's internal transaction ID
    val stellarTransactionId: String? = null,   // Stellar network transaction hash
    val externalTransactionId: String? = null,  // external system transaction ID
    val lang: String? = null                    // RFC 4646 language code
)
```

At least one of `id`, `stellarTransactionId`, or `externalTransactionId` must be set.

**Throws:** `Sep24TransactionNotFoundException` (404), `Sep24AuthenticationRequiredException` (403), `Sep24ServerErrorException` (5xx).

### Built-in polling with pollTransaction()

The SDK provides `pollTransaction()` which continuously queries until a terminal status is reached. This is the recommended approach.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionStatus

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

val tx = sep24.pollTransaction(
    request = Sep24TransactionRequest(jwt = jwtToken, id = transactionId),
    pollIntervalMs = 5000,   // 5 seconds (default)
    maxAttempts = 60,        // 60 attempts = 5 minutes at 5s intervals (default)
    onStatusChange = { tx ->
        println("Status changed to: ${tx.status}")
        tx.statusEta?.let { eta -> println("Estimated time: ${eta}s") }
    }
)

// tx is now in a terminal state
when (tx.getStatusEnum()) {
    Sep24TransactionStatus.COMPLETED -> {
        println("Transaction completed! Amount out: ${tx.amountOut}")
    }
    Sep24TransactionStatus.REFUNDED -> {
        println("Transaction refunded")
        tx.refunds?.let { println("Refunded: ${it.amountRefunded}") }
    }
    Sep24TransactionStatus.ERROR -> {
        println("Error: ${tx.message}")
    }
    else -> println("Final status: ${tx.status}")
}
```

Signature:
```kotlin
suspend fun pollTransaction(
    request: Sep24TransactionRequest,
    pollIntervalMs: Long = 5000,
    maxAttempts: Int = 60,
    onStatusChange: ((Sep24Transaction) -> Unit)? = null
): Sep24Transaction
```

Throws `Sep24Exception` if `maxAttempts` is exceeded without reaching a terminal status.

### Manual polling loop

For more control (e.g., handling `pending_user_transfer_start` for withdrawals):

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionStatus
import com.soneso.stellar.sdk.sep.sep24.exceptions.Sep24AuthenticationRequiredException
import com.soneso.stellar.sdk.sep.sep24.exceptions.Sep24TransactionNotFoundException
import kotlinx.coroutines.delay

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val request = Sep24TransactionRequest(jwt = jwtToken, id = transactionId)

var polling = true
while (polling) {
    try {
        val response = sep24.transaction(request)
        val tx = response.transaction

        println("Status: ${tx.status}")

        if (tx.isTerminal()) {
            polling = false
            if (tx.getStatusEnum() == Sep24TransactionStatus.COMPLETED) {
                println("Transaction completed! Amount out: ${tx.amountOut}")
            } else if (tx.getStatusEnum() == Sep24TransactionStatus.ERROR) {
                println("Error: ${tx.message}")
            }
            continue
        }

        if (tx.status == "pending_user_transfer_start" && tx.kind == "withdrawal") {
            polling = false
            // User must send the Stellar payment now
            // See "Completing a withdrawal" above
            continue
        }

        // Use statusEta hint if provided
        tx.statusEta?.let { eta ->
            if (eta > 0) println("Expected update in $eta seconds")
        }

        delay(5000) // poll every 5 seconds

    } catch (e: Sep24TransactionNotFoundException) {
        polling = false
        println("Transaction not found")
    } catch (e: Sep24AuthenticationRequiredException) {
        polling = false
        println("Re-authenticate and retry")
    } catch (e: Exception) {
        println("Error polling: $e")
        delay(5000)
    }
}
```

---

## 8. Transaction History

`transactions()` returns a list of transactions for the authenticated account, filtered by asset. Queries `GET /transactions`.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionsRequest

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

val response = sep24.transactions(Sep24TransactionsRequest(
    assetCode = "USD",       // required
    jwt = jwtToken           // required
))

for (tx in response.transactions) {
    println("${tx.id}: ${tx.kind} - ${tx.status}")
}
```

Signature:
```kotlin
suspend fun transactions(request: Sep24TransactionsRequest): Sep24TransactionsResponse
```

`Sep24TransactionsResponse` has a single field: `transactions` (`List<Sep24Transaction>`), always a list (never null; may be empty).

### Sep24TransactionsRequest constructor parameters

```kotlin
data class Sep24TransactionsRequest(
    val assetCode: String,            // asset code to filter by (required)
    val jwt: String,                  // JWT from SEP-10 (required)
    val noOlderThan: String? = null,  // ISO 8601 UTC datetime string (e.g. "2024-01-01T00:00:00Z")
    val limit: Int? = null,           // maximum number of transactions to return
    val kind: String? = null,         // "deposit" or "withdrawal"; omit for both
    val pagingId: String? = null,     // returns transactions prior to (exclusive) this ID
    val lang: String? = null          // RFC 4646 language code
)
```

### Transaction history with filters and pagination

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionsRequest

val response = sep24.transactions(Sep24TransactionsRequest(
    assetCode = "USD",
    jwt = jwtToken,
    limit = 10,
    kind = "deposit",                                      // "deposit" or "withdrawal"
    noOlderThan = "2024-01-01T00:00:00Z",                  // ISO 8601 UTC string, not DateTime
    lang = "en"
))

// Pagination: pass the last transaction ID as pagingId for the next page
if (response.transactions.isNotEmpty()) {
    val lastId = response.transactions.last().id

    val page2 = sep24.transactions(Sep24TransactionsRequest(
        assetCode = "USD",
        jwt = jwtToken,
        limit = 10,
        pagingId = lastId   // returns transactions prior to this ID (exclusive)
    ))
}
```

**Throws:** `Sep24AuthenticationRequiredException` (403), `Sep24ServerErrorException` (5xx).

---

## 9. Sep24Transaction -- All Fields

The `Sep24Transaction` object is returned inside `Sep24TransactionResponse.transaction` and each element of `Sep24TransactionsResponse.transactions`.

### Always-present fields

| Kotlin field | JSON key | Type | Description |
|-------------|----------|------|-------------|
| `id` | `id` | `String` | Unique anchor-generated transaction ID |
| `kind` | `kind` | `String` | `"deposit"`, `"withdrawal"`, `"deposit-exchange"`, or `"withdrawal-exchange"` |
| `status` | `status` | `String` | Current processing status |

### Optional fields (all nullable)

| Kotlin field | JSON key | Type | Description |
|-------------|----------|------|-------------|
| `statusEta` | `status_eta` | `Int?` | Estimated seconds until next status change |
| `kycVerified` | `kyc_verified` | `Boolean?` | Whether anchor verified user's KYC |
| `moreInfoUrl` | `more_info_url` | `String?` | URL with additional transaction details |
| `amountIn` | `amount_in` | `String?` | Amount received by anchor (as string) |
| `amountInAsset` | `amount_in_asset` | `String?` | SEP-38 format asset received |
| `amountOut` | `amount_out` | `String?` | Amount sent to user (as string) |
| `amountOutAsset` | `amount_out_asset` | `String?` | SEP-38 format asset sent to user |
| `amountFee` | `amount_fee` | `String?` | Deprecated: use `feeDetails` instead |
| `amountFeeAsset` | `amount_fee_asset` | `String?` | Deprecated: use `feeDetails` instead |
| `feeDetails` | `fee_details` | `Sep24FeeDetails?` | Detailed fee breakdown (see section 11) |
| `quoteId` | `quote_id` | `String?` | SEP-38 quote ID used for this transaction |
| `startedAt` | `started_at` | `String?` | ISO 8601 UTC start timestamp |
| `completedAt` | `completed_at` | `String?` | ISO 8601 UTC completion timestamp |
| `updatedAt` | `updated_at` | `String?` | ISO 8601 UTC last-update timestamp |
| `userActionRequiredBy` | `user_action_required_by` | `String?` | Deadline for user action (ISO 8601 UTC) |
| `stellarTransactionId` | `stellar_transaction_id` | `String?` | Stellar network transaction hash |
| `externalTransactionId` | `external_transaction_id` | `String?` | External system transaction ID |
| `message` | `message` | `String?` | Human-readable status explanation |
| `refunded` | `refunded` | `Boolean?` | Deprecated: use `refunds` and `"refunded"` status instead |
| `refunds` | `refunds` | `Sep24Refunds?` | Refund details if transaction was refunded |
| `from` | `from` | `String?` | Deposit: sender address; Withdrawal: source Stellar address |
| `to` | `to` | `String?` | Deposit: destination Stellar address; Withdrawal: destination address |

### Deposit-only fields

| Kotlin field | JSON key | Type | Description |
|-------------|----------|------|-------------|
| `depositMemo` | `deposit_memo` | `String?` | Memo used in the deposit payment |
| `depositMemoType` | `deposit_memo_type` | `String?` | Memo type for `depositMemo` |
| `claimableBalanceId` | `claimable_balance_id` | `String?` | ID of claimable balance used to send asset |

### Withdrawal-only fields

| Kotlin field | JSON key | Type | Description |
|-------------|----------|------|-------------|
| `withdrawAnchorAccount` | `withdraw_anchor_account` | `String?` | Anchor's Stellar account to send payment to |
| `withdrawMemo` | `withdraw_memo` | `String?` | Memo to include in payment; null if KYC not complete |
| `withdrawMemoType` | `withdraw_memo_type` | `String?` | Memo type for `withdrawMemo` |

### Helper methods on Sep24Transaction

```kotlin
// Get status as a Sep24TransactionStatus enum (null if unrecognized)
val statusEnum: Sep24TransactionStatus? = tx.getStatusEnum()

// Check if transaction is in a terminal state (completed, refunded, expired, error, etc.)
val terminal: Boolean = tx.isTerminal()
```

### Reading transaction fields

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionStatus

val response = sep24.transaction(Sep24TransactionRequest(
    jwt = jwtToken,
    id = transactionId
))
val tx = response.transaction

// Always-present fields
println("ID: ${tx.id}")
println("Kind: ${tx.kind}")
println("Status: ${tx.status}")
tx.moreInfoUrl?.let { println("More info: $it") }
tx.startedAt?.let { println("Started: $it") }

// Amount fields -- strings, not doubles
tx.amountIn?.let { println("Amount in: $it") }
tx.amountOut?.let { println("Amount out: $it") }

// Fee details (preferred over deprecated amountFee)
tx.feeDetails?.let { fee ->
    println("Fee total: ${fee.total} ${fee.asset}")
    fee.breakdown?.forEach { detail ->
        println("  ${detail.name}: ${detail.amount}")
    }
}

// KYC and deadline
if (tx.kycVerified == true) println("KYC verified")
tx.userActionRequiredBy?.let { println("Action required by: $it") }

// Withdrawal payment instructions
if (tx.kind == "withdrawal" && tx.status == "pending_user_transfer_start") {
    if (tx.withdrawMemo != null) {
        println("Send ${tx.amountIn} to ${tx.withdrawAnchorAccount}")
        println("Memo: ${tx.withdrawMemo} (${tx.withdrawMemoType})")
    }
}

// Deposit claimable balance
if (tx.kind == "deposit" && tx.claimableBalanceId != null) {
    println("Claim balance ID: ${tx.claimableBalanceId}")
}
```

---

## 10. Transaction Statuses

The `status` field on `Sep24Transaction`. Use `getStatusEnum()` to convert to the `Sep24TransactionStatus` enum, or `isTerminal()` to check if the transaction has reached a final state.

| Status | Enum | Terminal | Description |
|--------|------|----------|-------------|
| `incomplete` | `INCOMPLETE` | No | User has not completed the interactive flow yet |
| `pending_user_transfer_start` | `PENDING_USER_TRANSFER_START` | No | Waiting for user to send funds |
| `pending_user_transfer_complete` | `PENDING_USER_TRANSFER_COMPLETE` | No | Stellar payment received; off-chain processing pending |
| `pending_external` | `PENDING_EXTERNAL` | No | Waiting for off-chain confirmation (bank transfer, etc.) |
| `pending_anchor` | `PENDING_ANCHOR` | No | Anchor is processing the transaction |
| `pending_stellar` | `PENDING_STELLAR` | No | Waiting for Stellar network confirmation |
| `pending_trust` | `PENDING_TRUST` | No | User must add a trustline for the asset |
| `pending_user` | `PENDING_USER` | No | User must take an action; see `message` or `moreInfoUrl` |
| `on_hold` | `ON_HOLD` | No | Anchor has placed transaction on hold |
| `completed` | `COMPLETED` | Yes | Transaction finished successfully |
| `refunded` | `REFUNDED` | Yes | Transaction was fully or partially refunded |
| `expired` | `EXPIRED` | Yes | Transaction expired before completion |
| `no_market` | `NO_MARKET` | Yes | No market available for the asset pair |
| `too_small` | `TOO_SMALL` | Yes | Amount below the anchor's minimum |
| `too_large` | `TOO_LARGE` | Yes | Amount exceeds the anchor's maximum |
| `error` | `ERROR` | Yes | Transaction failed due to an error |

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionStatus

// Check terminal statuses programmatically
val isTerminal = Sep24TransactionStatus.isTerminal("completed") // true
val isTerminal2 = Sep24TransactionStatus.isTerminal("pending_anchor") // false

// Get enum from string
val status = Sep24TransactionStatus.fromValue("completed") // Sep24TransactionStatus.COMPLETED
val unknown = Sep24TransactionStatus.fromValue("unknown_status") // null

// Terminal status set
val terminals = Sep24TransactionStatus.terminalStatuses
// Set of: COMPLETED, REFUNDED, EXPIRED, ERROR, NO_MARKET, TOO_SMALL, TOO_LARGE
```

---

## 11. Fee Details

When a transaction includes structured fee information, it appears in the `feeDetails` field (preferred over the deprecated `amountFee`/`amountFeeAsset` fields).

### Sep24FeeDetails fields

| Field | Type | Description |
|-------|------|-------------|
| `total` | `String` | Total fee amount |
| `asset` | `String` | Asset of the fee (SEP-38 Asset Identification Format) |
| `breakdown` | `List<Sep24FeeDetail>?` | Optional list of individual fee components |

### Sep24FeeDetail fields

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Name of the fee component (e.g. "Service fee", "Network fee") |
| `amount` | `String` | Amount of this fee component |
| `description` | `String?` | Optional human-readable description |

```kotlin
val tx = sep24.transaction(Sep24TransactionRequest(jwt = jwtToken, id = transactionId)).transaction

tx.feeDetails?.let { fee ->
    println("Total fee: ${fee.total} ${fee.asset}")
    fee.breakdown?.forEach { detail ->
        println("  ${detail.name}: ${detail.amount}")
        detail.description?.let { println("    $it") }
    }
}
```

---

## 12. Refund Objects

When a transaction is refunded (`status == "refunded"` or `refunds != null`), inspect the `refunds` field.

### Sep24Refunds fields

| Field | Type | Description |
|-------|------|-------------|
| `amountRefunded` | `String` | Total refunded to user (in units of `amountInAsset`) |
| `amountFee` | `String` | Total fee charged for all refund payments |
| `payments` | `List<Sep24RefundPayment>` | Individual refund payment records |

### Sep24RefundPayment fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Stellar transaction hash or external reference |
| `idType` | `String` | `"stellar"` or `"external"` |
| `amount` | `String` | Amount refunded by this payment |
| `fee` | `String` | Fee charged for this refund payment |

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionRequest

val response = sep24.transaction(Sep24TransactionRequest(
    jwt = jwtToken,
    id = transactionId
))
val tx = response.transaction

tx.refunds?.let { refunds ->
    println("Total refunded: ${refunds.amountRefunded}")
    println("Refund fees: ${refunds.amountFee}")

    for (payment in refunds.payments) {
        println("Payment ID: ${payment.id}")
        println("  Type: ${payment.idType}")   // "stellar" or "external"
        println("  Amount: ${payment.amount}")
        println("  Fee: ${payment.fee}")
    }
}
```

---

## 13. Error Handling

Four exception types can be thrown by SEP-24 service methods. All extend `Sep24Exception`.

| Exception | HTTP Status | Trigger | Action |
|-----------|-------------|---------|--------|
| `Sep24AuthenticationRequiredException` | 403 | JWT missing, expired, or invalid | Re-authenticate with SEP-10 and retry |
| `Sep24InvalidRequestException` | 400 | Invalid parameters, unsupported asset | Check `exception.message` for anchor error details |
| `Sep24TransactionNotFoundException` | 404 | Transaction ID unknown or not owned by user | Only thrown by `transaction()`, not `transactions()` |
| `Sep24ServerErrorException` | 5xx | Anchor server error | Check `exception.statusCode`; retry with backoff |

`Sep24ServerErrorException` has a `statusCode` property (`Int`) with the HTTP status code.

```kotlin
import com.soneso.stellar.sdk.sep.sep24.Sep24DepositRequest
import com.soneso.stellar.sdk.sep.sep24.Sep24Service
import com.soneso.stellar.sdk.sep.sep24.Sep24TransactionRequest
import com.soneso.stellar.sdk.sep.sep24.exceptions.*

val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

// Deposit error handling
try {
    val response = sep24.deposit(Sep24DepositRequest(
        assetCode = "USD",
        jwt = jwtToken
    ))
    println("Open: ${response.url}")

} catch (e: Sep24AuthenticationRequiredException) {
    // HTTP 403 -- JWT is invalid, expired, or missing
    println("Need to re-authenticate with SEP-10")

} catch (e: Sep24InvalidRequestException) {
    // HTTP 400 -- bad parameters, unsupported asset, etc.
    println("Request error: ${e.message}")

} catch (e: Sep24ServerErrorException) {
    // HTTP 5xx -- anchor server error
    println("Server error (${e.statusCode}): ${e.message}")

} catch (e: Sep24Exception) {
    // Base class for all SEP-24 errors (e.g. fromDomain config errors)
    println("SEP-24 error: ${e.message}")
}

// Transaction lookup error handling
try {
    val response = sep24.transaction(Sep24TransactionRequest(
        jwt = jwtToken,
        id = transactionId
    ))
    println("Status: ${response.transaction.status}")

} catch (e: Sep24TransactionNotFoundException) {
    // HTTP 404 -- ID not found or not owned by authenticated user
    // Only thrown by transaction() (singular), NOT by transactions() (plural)
    // e.transactionId may contain the ID that was not found
    println("Transaction not found")

} catch (e: Sep24AuthenticationRequiredException) {
    println("Re-authenticate and retry")

} catch (e: Sep24ServerErrorException) {
    println("Server error (${e.statusCode}): ${e.message}")
}
```

---

## 14. Common Pitfalls

**Wrong: using Flutter/Dart-style late properties and cascade assignment**

```kotlin
// WRONG: KMP SDK uses Kotlin data classes with constructor parameters, not Dart late fields
// Sep24DepositRequest()
//   ..assetCode = "USDC"
//   ..jwt = jwtToken

// CORRECT: pass all values through the constructor
val request = Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwtToken
)
```

**Wrong: `claimableBalanceSupported` is `Boolean?` in KMP, not `String?`**

```kotlin
// WRONG: in the Flutter SDK, claimableBalanceSupported is String?
// val request = Sep24DepositRequest(assetCode = "USDC", jwt = jwt, claimableBalanceSupported = "true")

// CORRECT: in the KMP SDK, claimableBalanceSupported is Boolean?
val request = Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    claimableBalanceSupported = true  // Boolean, not String
)
```

**Wrong: `noOlderThan` is `String?` in KMP, not `DateTime`**

```kotlin
// WRONG: noOlderThan is not a DateTime object in the KMP SDK
// Sep24TransactionsRequest(assetCode = "USD", jwt = jwt, noOlderThan = DateTime.utc(2024, 1, 1))

// CORRECT: noOlderThan is an ISO 8601 UTC datetime string
val request = Sep24TransactionsRequest(
    assetCode = "USD",
    jwt = jwtToken,
    noOlderThan = "2024-01-01T00:00:00Z"
)
```

**Wrong: `Sep24AssetInfo` amount/fee fields are `String?`, not `Double?`**

```kotlin
// WRONG: comparing asset info amounts as numbers directly
// if (assetInfo.minAmount!! > 100.0) { ... }

// CORRECT: parse to Double for comparison
val minAmount = assetInfo.minAmount?.toDoubleOrNull()
if (minAmount != null && minAmount > 100.0) { /* ... */ }
```

**Wrong: using `transactions()` (plural) for ID-based lookup**

```kotlin
// WRONG: Sep24TransactionsRequest has no 'id' field
// sep24.transactions(Sep24TransactionsRequest(jwt = jwtToken, id = transactionId))

// CORRECT: use transaction() (singular) with Sep24TransactionRequest for ID-based lookup
val response = sep24.transaction(Sep24TransactionRequest(
    jwt = jwtToken,
    id = transactionId
))
```

**Wrong: setting `assetIssuer` for native XLM**

```kotlin
// WRONG: native assets have no issuer
// Sep24DepositRequest(assetCode = "native", jwt = jwt, assetIssuer = "GABC...")

// CORRECT: omit assetIssuer for native
val request = Sep24DepositRequest(
    assetCode = "native",
    jwt = jwtToken
)
```

**Wrong: setting `refundMemo` without `refundMemoType` (or vice versa)**

```kotlin
// WRONG: both fields must be set together
// Sep24WithdrawRequest(assetCode = "USD", jwt = jwt, refundMemo = "ref-123")

// CORRECT: always set both together
val request = Sep24WithdrawRequest(
    assetCode = "USD",
    jwt = jwtToken,
    refundMemo = "ref-123",
    refundMemoType = "text"
)
```

**Wrong: accessing `withdrawMemo` before KYC is complete**

The anchor sets `withdrawMemo` to null until KYC is verified, even when status is `pending_user_transfer_start`. Do not send the Stellar payment if the memo is null.

```kotlin
// WRONG: withdrawMemo may be null even in pending_user_transfer_start
// val memo = tx.withdrawMemo!!  // throws NPE if KYC not yet verified

// CORRECT: always check before sending
if (tx.status == "pending_user_transfer_start") {
    if (tx.withdrawMemo == null) {
        // KYC not yet verified -- open tx.moreInfoUrl or keep polling
        println("Waiting for KYC verification")
    } else {
        // Safe to send the payment
        println("Send to ${tx.withdrawAnchorAccount} with memo ${tx.withdrawMemo}")
    }
}
```

**Wrong: using `authToken` object directly as JWT string**

```kotlin
// WRONG: passing AuthToken object where String is expected
// val response = sep24.deposit(Sep24DepositRequest(assetCode = "USDC", jwt = authToken))

// CORRECT: extract the token string from AuthToken
val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USDC",
    jwt = authToken.token  // .token extracts the raw JWT string
))
```

**Wrong: not handling `Sep24Exception` from `fromDomain()`**

```kotlin
// WRONG: fromDomain() throws Sep24Exception if TRANSFER_SERVER_SEP0024 is missing from stellar.toml
// val sep24 = Sep24Service.fromDomain("some-domain.com")

// CORRECT: handle the case where the domain doesn't support SEP-24
try {
    val sep24 = Sep24Service.fromDomain("some-domain.com")
} catch (e: Sep24Exception) {
    println("Domain does not support SEP-24: ${e.message}")
}
```

**Wrong: comparing `amountIn`, `amountOut` as numbers directly**

These fields are `String?`, not `Double?`. Cast to double only for arithmetic.

```kotlin
// WRONG: these fields are strings
// if (tx.amountIn!! > 100.0) { ... }  // type error

// CORRECT: parse to double for comparison
if (tx.amountIn != null && tx.amountIn!!.toDouble() > 100.0) { /* ... */ }
```

---

## Related SEPs

- SEP-01 (`references/sep-01.md`) -- stellar.toml (`TRANSFER_SERVER_SEP0024` is published here)
- SEP-10 (`references/sep-10.md`) -- Web Authentication for G... accounts (provides the JWT)
- SEP-12 (`references/sep-12.md`) -- KYC API (often used alongside SEP-24)
- SEP-38 -- Anchor RFQ API (quotes for exchange rates; use `quoteId` and `sourceAsset`/`destinationAsset`)
