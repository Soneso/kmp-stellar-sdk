# SEP-24: Interactive Anchor/Wallet Asset Transfer

SEP-24 enables interactive deposit and withdrawal flows with Stellar anchors. Users complete the transaction process through an anchor-hosted web interface, allowing anchors to collect KYC information and payment details dynamically.

**Use Cases**:
- Deposit fiat currency (USD, EUR, BRL) to receive Stellar assets
- Withdraw Stellar assets to receive fiat via bank transfer, mobile money, or cash
- On-ramp and off-ramp services for wallets and applications
- KYC-compliant asset transfers with regulatory requirements

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep24.*
import com.soneso.stellar.sdk.sep.sep24.exceptions.*
import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.sep.sep38.*
```

## Quick Start

```kotlin
// Initialize from domain's stellar.toml
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")

// Discover supported assets
val info = sep24.info()
info.depositAssets?.forEach { (code, assetInfo) ->
    if (assetInfo.enabled) {
        println("$code: min=${assetInfo.minAmount}, max=${assetInfo.maxAmount}")
    }
}

// Authenticate via SEP-10 (required for deposits/withdrawals)
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
val jwtToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
).token

// Initiate interactive deposit
val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    account = userKeyPair.getAccountId(),
    amount = "100"
))

println("Transaction ID: ${response.id}")
println("Interactive URL: ${response.url}")
// Display response.url in a webview for user to complete the deposit

// Poll for completion
val tx = sep24.pollTransaction(
    Sep24TransactionRequest(jwt = jwtToken, id = response.id),
    onStatusChange = { println("Status: ${it.status}") }
)
println("Final status: ${tx.status}")
```

## Service Initialization

### From Domain (Recommended)

```kotlin
// Discovers TRANSFER_SERVER_SEP0024 from stellar.toml
```

### Direct Initialization

```kotlin
// Use when endpoint is known
val sep24 = Sep24Service(
    serviceAddress = "https://testanchor.stellar.org/sep24"
)
```

## Endpoints

### Get Info

Discover supported assets, limits, and anchor capabilities. No authentication required.

```kotlin
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val info = sep24.info()

// Check deposit assets
info.depositAssets?.forEach { (code, assetInfo) ->
    if (assetInfo.enabled) {
        println("Deposit $code:")
        println("  Min: ${assetInfo.minAmount}")
        println("  Max: ${assetInfo.maxAmount}")
        println("  Fixed fee: ${assetInfo.feeFixed}")
        println("  Percent fee: ${assetInfo.feePercent}%")
    }
}

// Check withdrawal assets
info.withdrawAssets?.forEach { (code, assetInfo) ->
    if (assetInfo.enabled) {
        println("Withdraw $code: enabled")
    }
}

// Check feature support
info.features?.let { features ->
    println("Claimable balances: ${features.claimableBalances}")
    println("Account creation: ${features.accountCreation}")
}
```

**Response Structure** (`Sep24InfoResponse`):
- `depositAssets` - Map of asset codes to `Sep24AssetInfo` for deposits
- `withdrawAssets` - Map of asset codes to `Sep24AssetInfo` for withdrawals
- `feeEndpoint` - Configuration for legacy /fee endpoint (deprecated)
- `features` - Feature flags (`Sep24Features`)

**Asset Info** (`Sep24AssetInfo`):
- `enabled` - Whether asset is available for the operation
- `minAmount` - Minimum amount allowed
- `maxAmount` - Maximum amount allowed
- `feeFixed` - Fixed fee amount
- `feePercent` - Percentage fee
- `feeMinimum` - Minimum fee regardless of amount

### Initiate Deposit

Start an interactive deposit flow. Returns a URL to display in a webview.

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    account = userKeyPair.getAccountId(),
    amount = "100",
    claimableBalanceSupported = true
))

println("Transaction ID: ${response.id}")
println("Interactive URL: ${response.url}")
// Display response.url in a webview
```

**Request Parameters** (`Sep24DepositRequest`):
- `assetCode` - Asset to receive (e.g., "USDC", "native" for XLM)
- `jwt` - SEP-10 authentication token (required)
- `assetIssuer` - Asset issuer (required when multiple assets share the same code)
- `sourceAsset` - Off-chain asset user will send, in SEP-38 format (e.g., "iso4217:USD")
- `amount` - Intended deposit amount
- `quoteId` - SEP-38 quote ID for firm pricing
- `account` - Destination Stellar account (G...), muxed account (M...), or contract (C...)
- `memo` - Memo to attach to the Stellar payment
- `memoType` - Type of memo: "text", "id", or "hash"
- `walletName` - Wallet name shown in interactive UI
- `walletUrl` - Wallet URL for anchor reference
- `lang` - Language code (RFC 4646, e.g., "en", "es")
- `claimableBalanceSupported` - Set true to receive deposits as claimable balances
- `customerId` - SEP-12 customer ID if KYC already completed
- `kycFields` - Pre-filled KYC field values (Map<String, String>)
- `kycFiles` - KYC file uploads (Map<String, ByteArray>)

**Response** (`Sep24InteractiveResponse`):
- `type` - Always "interactive_customer_info_needed"
- `url` - URL to display in webview
- `id` - Transaction ID for polling

### Initiate Withdrawal

Start an interactive withdrawal flow. Returns a URL to display in a webview.

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val response = sep24.withdraw(Sep24WithdrawRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    account = userKeyPair.getAccountId(),
    amount = "50",
    refundMemo = "refund-12345",
    refundMemoType = "text"
))

println("Transaction ID: ${response.id}")
println("Interactive URL: ${response.url}")
// Display response.url in a webview
```

**Request Parameters** (`Sep24WithdrawRequest`):
- `assetCode` - Asset to withdraw (e.g., "USDC", "native" for XLM)
- `jwt` - SEP-10 authentication token (required)
- `assetIssuer` - Asset issuer (required when multiple assets share the same code)
- `destinationAsset` - Off-chain asset user will receive, in SEP-38 format (e.g., "iso4217:USD")
- `amount` - Intended withdrawal amount
- `quoteId` - SEP-38 quote ID for firm pricing
- `account` - Source Stellar account (G...), muxed account (M...), or contract (C...)
- `walletName` - Wallet name shown in interactive UI
- `walletUrl` - Wallet URL for anchor reference
- `lang` - Language code (RFC 4646)
- `refundMemo` - Memo for anchor to use if refund is needed
- `refundMemoType` - Type of refund memo: "text", "id", or "hash"
- `customerId` - SEP-12 customer ID if KYC already completed
- `kycFields` - Pre-filled KYC field values (Map<String, String>)
- `kycFiles` - KYC file uploads (Map<String, ByteArray>)

### Get Transaction

Query a single transaction by its identifier.

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val transactionId = "82fhs729f63dh0v4" // id returned when the transfer was initiated
val response = sep24.transaction(Sep24TransactionRequest(
    jwt = jwtToken,
    id = transactionId
))

val tx = response.transaction
println("Status: ${tx.status}")
println("Kind: ${tx.kind}")
println("Amount in: ${tx.amountIn} ${tx.amountInAsset}")
println("Amount out: ${tx.amountOut} ${tx.amountOutAsset}")
tx.message?.let { println("Message: $it") }
```

Query by Stellar transaction hash:

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val response = sep24.transaction(Sep24TransactionRequest(
    jwt = jwtToken,
    stellarTransactionId = "abc123..."
))
```

Query by external transaction ID:

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val response = sep24.transaction(Sep24TransactionRequest(
    jwt = jwtToken,
    externalTransactionId = "BANK-REF-456"
))
```

**Request Parameters** (`Sep24TransactionRequest`):
- `jwt` - SEP-10 authentication token (required)
- `id` - Anchor's transaction ID (from deposit/withdraw response)
- `stellarTransactionId` - Stellar transaction hash
- `externalTransactionId` - External payment system ID
- `lang` - Language code for returned messages

At least one of `id`, `stellarTransactionId`, or `externalTransactionId` must be provided.

### Get Transactions (History)

Query transaction history for the authenticated account.

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val response = sep24.transactions(Sep24TransactionsRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    kind = "deposit",
    limit = 10
))

response.transactions.forEach { tx ->
    println("${tx.id}: ${tx.status}")
    println("  Started: ${tx.startedAt}")
    println("  Amount: ${tx.amountIn} -> ${tx.amountOut}")
}
```

Filter by date:

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val response = sep24.transactions(Sep24TransactionsRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    noOlderThan = "2024-01-01T00:00:00Z"
))
```

Paginate results:

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
// First page
val firstPage = sep24.transactions(Sep24TransactionsRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    limit = 10
))

// Next page using last transaction ID as cursor
val lastId = firstPage.transactions.lastOrNull()?.id
val nextPage = sep24.transactions(Sep24TransactionsRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    pagingId = lastId,
    limit = 10
))
```

**Request Parameters** (`Sep24TransactionsRequest`):
- `assetCode` - Asset to query transactions for (required)
- `jwt` - SEP-10 authentication token (required)
- `noOlderThan` - ISO 8601 UTC datetime filter (e.g., "2024-01-15T12:00:00Z")
- `limit` - Maximum number of transactions to return
- `kind` - Filter by type: "deposit" or "withdrawal"
- `pagingId` - Pagination cursor (transaction ID from previous response)
- `lang` - Language code for returned messages

### Poll Transaction

Continuously poll a transaction until it reaches a terminal status.

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val transactionId = "82fhs729f63dh0v4" // id returned when the transfer was initiated
val tx = sep24.pollTransaction(
    request = Sep24TransactionRequest(jwt = jwtToken, id = transactionId),
    pollIntervalMs = 3000,
    maxAttempts = 100,
    onStatusChange = { tx ->
        println("Status changed to: ${tx.status}")
        tx.statusEta?.let { println("Estimated time: ${it}s") }
        tx.message?.let { println("Message: $it") }
    }
)

when (tx.getStatusEnum()) {
    Sep24TransactionStatus.COMPLETED -> {
        println("Success!")
        println("Received: ${tx.amountOut} ${tx.amountOutAsset}")
        tx.stellarTransactionId?.let {
            println("Stellar TX: $it")
        }
    }
    Sep24TransactionStatus.REFUNDED -> {
        println("Transaction refunded")
        tx.refunds?.let { refunds ->
            println("Refunded: ${refunds.amountRefunded}")
            println("Fee: ${refunds.amountFee}")
        }
    }
    Sep24TransactionStatus.ERROR -> {
        println("Transaction failed: ${tx.message}")
    }
    Sep24TransactionStatus.EXPIRED -> {
        println("Transaction expired")
    }
    else -> println("Final status: ${tx.status}")
}
```

**Parameters**:
- `request` - Transaction request with id, stellarTransactionId, or externalTransactionId
- `pollIntervalMs` - Interval between polls (default: 5000ms)
- `maxAttempts` - Maximum poll attempts (default: 60)
- `onStatusChange` - Callback invoked when status changes

**Throws**: `Sep24Exception` if max attempts exceeded without reaching terminal status.

## Transaction Status

SEP-24 transactions progress through various states before reaching a terminal status.

### Pending States

| Status | Description |
|--------|-------------|
| `incomplete` | User has not completed the interactive flow |
| `pending_user_transfer_start` | Waiting for user to send funds (deposit: fiat to anchor, withdrawal: Stellar to anchor) |
| `pending_user_transfer_complete` | Funds received, waiting for processing |
| `pending_external` | Being processed by external system (bank, payment processor) |
| `pending_anchor` | Being processed internally by the anchor |
| `on_hold` | Placed on hold, user may need to contact support |
| `pending_stellar` | Waiting for Stellar network transaction to complete |
| `pending_trust` | Waiting for user to establish trustline (deposit only) |
| `pending_user` | Waiting for user action, check message field |

### Terminal States

| Status | Description |
|--------|-------------|
| `completed` | Transaction completed successfully |
| `refunded` | Transaction refunded to user |
| `expired` | User did not complete required action in time |
| `error` | Error occurred, check message field |
| `no_market` | No market for this asset pair |
| `too_small` | Amount below minimum |
| `too_large` | Amount above maximum |

### Status Helpers

```kotlin
// handlePendingStatus, handleSuccess, handleUnknownStatus, promptUserToEstablishTrustline, promptUserToSendFunds, txId: from the previous steps of this flow
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val tx = sep24.transaction(Sep24TransactionRequest(jwt = jwtToken, id = txId)).transaction

// Get status as enum
val status = tx.getStatusEnum()
when (status) {
    Sep24TransactionStatus.COMPLETED -> handleSuccess(tx)
    Sep24TransactionStatus.PENDING_USER_TRANSFER_START -> promptUserToSendFunds(tx)
    Sep24TransactionStatus.PENDING_TRUST -> promptUserToEstablishTrustline(tx)
    null -> handleUnknownStatus(tx.status)
    else -> handlePendingStatus(tx)
}

// Check if terminal (no more status changes expected)
if (tx.isTerminal()) {
    println("Transaction reached final state: ${tx.status}")
}

// Check terminal status set directly
if (Sep24TransactionStatus.isTerminal(tx.status)) {
    // Handle completion
}
```

## Error Handling

```kotlin
// request, signers: from the previous steps of this flow
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
try {
    val response = sep24.deposit(request)
} catch (e: Sep24AuthenticationRequiredException) {
    // JWT missing, expired, or invalid (403)
    // Re-authenticate via SEP-10
    val newJwt = webAuth.jwtToken(accountId, signers).token
    // Retry with new token
} catch (e: Sep24InvalidRequestException) {
    // Invalid parameters (400)
    println("Bad request: ${e.message}")
    // Check asset support, amount limits, etc.
} catch (e: Sep24TransactionNotFoundException) {
    // Transaction not found (404)
    println("Transaction not found")
} catch (e: Sep24ServerErrorException) {
    // Anchor server error (5xx)
    println("Server error (${e.statusCode}): ${e.message}")
    // Retry with exponential backoff
} catch (e: Sep24Exception) {
    // Other SEP-24 errors
    println("SEP-24 error: ${e.message}")
}
```

**Exception Types**:
- `Sep24AuthenticationRequiredException` - JWT token missing, expired, or invalid
- `Sep24InvalidRequestException` - Invalid request parameters (asset not supported, amount out of range)
- `Sep24TransactionNotFoundException` - Transaction ID does not exist
- `Sep24ServerErrorException` - Anchor server error (includes `statusCode` property)
- `Sep24Exception` - Base exception for other errors

## Complete Integration Example

Full workflow from authentication to transaction completion:

```kotlin
val network = Network.TESTNET
// 1. Initialize services
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

// 2. User keypair (in production, load from secure storage)
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val accountId = userKeyPair.getAccountId()

// 3. Discover supported assets and validate
val info = sep24.info()
val usdcInfo = info.depositAssets?.get("USDC")
if (usdcInfo?.enabled != true) {
    throw IllegalStateException("USDC deposits not supported")
}

// 4. Authenticate via SEP-10
val jwtToken = webAuth.jwtToken(
    clientAccountId = accountId,
    signers = listOf(userKeyPair)
).token

// 5. Initiate deposit
val depositResponse = sep24.deposit(Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    account = accountId,
    amount = "100",
    claimableBalanceSupported = true,
    walletName = "MyWallet",
    walletUrl = "https://mywallet.example.com"
))

println("Transaction ID: ${depositResponse.id}")
println("Interactive URL: ${depositResponse.url}")

// 6. Display URL in webview for user interaction
// displayWebView(depositResponse.url)

// 7. Poll for completion
val finalTx = sep24.pollTransaction(
    request = Sep24TransactionRequest(jwt = jwtToken, id = depositResponse.id),
    pollIntervalMs = 5000,
    maxAttempts = 120,
    onStatusChange = { tx ->
        when (tx.getStatusEnum()) {
            Sep24TransactionStatus.PENDING_USER_TRANSFER_START -> {
                println("Waiting for you to send funds")
                tx.depositMemo?.let { memo ->
                    println("Use memo: $memo (${tx.depositMemoType})")
                }
            }
            Sep24TransactionStatus.PENDING_EXTERNAL -> {
                println("Processing with external provider...")
            }
            Sep24TransactionStatus.PENDING_STELLAR -> {
                println("Sending to Stellar network...")
            }
            Sep24TransactionStatus.PENDING_TRUST -> {
                println("Please add trustline for USDC")
            }
            else -> {
                println("Status: ${tx.status}")
            }
        }
    }
)

// 8. Handle final status
when (finalTx.getStatusEnum()) {
    Sep24TransactionStatus.COMPLETED -> {
        println("Deposit completed!")
        println("Received: ${finalTx.amountOut} ${finalTx.amountOutAsset}")
        finalTx.stellarTransactionId?.let {
            println("Stellar transaction: $it")
        }
        finalTx.claimableBalanceId?.let {
            println("Claimable balance: $it")
        }
    }
    Sep24TransactionStatus.REFUNDED -> {
        println("Deposit was refunded")
        finalTx.refunds?.let { refunds ->
            println("Amount refunded: ${refunds.amountRefunded}")
            refunds.payments.forEach { payment ->
                println("  ${payment.idType}: ${payment.id} - ${payment.amount}")
            }
        }
    }
    Sep24TransactionStatus.ERROR -> {
        println("Deposit failed: ${finalTx.message}")
    }
    Sep24TransactionStatus.EXPIRED -> {
        println("Deposit expired - please try again")
    }
    else -> {
        println("Unexpected final status: ${finalTx.status}")
    }
}
```

## SEP-38 Quote Integration

Use SEP-38 to lock in exchange rates before initiating a deposit or withdrawal:

```kotlin
// 1. Initialize services
val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

// 2. Authenticate
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val jwtToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
).token

// 3. Get firm quote for the exchange
val quote = quoteService.postQuote(
    Sep38QuoteRequest(
        context = "sep24",
        sellAsset = "iso4217:USD",
        buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
        sellAmount = "100"
    ),
    jwtToken
)
println("Quote: ${quote.sellAmount} USD -> ${quote.buyAmount} USDC")
println("Rate: ${quote.price}, expires: ${quote.expiresAt}")

// 4. Use quote in deposit request
val response = sep24.deposit(Sep24DepositRequest(
    assetCode = "USDC",
    jwt = jwtToken,
    account = userKeyPair.getAccountId(),
    quoteId = quote.id,
    amount = quote.sellAmount,
    sourceAsset = "iso4217:USD"
))

// 5. Complete interactive flow and poll
// Anchor will honor the quoted rate if completed before expiration
```

## API Reference

**Main Class**:
- `Sep24Service` - SEP-24 interactive deposit/withdrawal client

**Factory Methods**:
- `Sep24Service.fromDomain(domain)` - Initialize from stellar.toml
- `Sep24Service(serviceAddress)` - Direct initialization

**Methods**:
- `info(lang?)` - Get supported assets and capabilities
- `deposit(request)` - Initiate interactive deposit
- `withdraw(request)` - Initiate interactive withdrawal
- `transaction(request)` - Get single transaction
- `transactions(request)` - Get transaction history
- `pollTransaction(request, pollIntervalMs?, maxAttempts?, onStatusChange?)` - Poll until terminal
- `fee(request)` - Get fee (deprecated, use SEP-38)

**Request Classes**:
- `Sep24DepositRequest` - Deposit parameters
- `Sep24WithdrawRequest` - Withdrawal parameters
- `Sep24TransactionRequest` - Single transaction query
- `Sep24TransactionsRequest` - Transaction history query
- `Sep24FeeRequest` - Fee query (deprecated)

**Response Classes**:
- `Sep24InfoResponse` - Anchor capabilities
- `Sep24InteractiveResponse` - Deposit/withdraw response with URL
- `Sep24TransactionResponse` - Single transaction wrapper
- `Sep24TransactionsResponse` - Transaction list wrapper
- `Sep24Transaction` - Transaction details
- `Sep24FeeResponse` - Fee amount (deprecated)

**Supporting Classes**:
- `Sep24AssetInfo` - Asset configuration (limits, fees)
- `Sep24Features` - Feature flags (claimable balances, account creation)
- `Sep24FeeDetails` - Fee breakdown
- `Sep24FeeDetail` - Individual fee component
- `Sep24Refunds` - Refund information
- `Sep24RefundPayment` - Individual refund payment
- `Sep24TransactionStatus` - Transaction status enum

**Exception Types**:
- `Sep24Exception` - Base exception
- `Sep24AuthenticationRequiredException` - JWT invalid (403)
- `Sep24InvalidRequestException` - Bad request (400)
- `Sep24TransactionNotFoundException` - Not found (404)
- `Sep24ServerErrorException` - Server error (5xx)

**Specification**: [SEP-24: Interactive Anchor/Wallet Asset Transfer](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0024.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep24`

**Last Updated**: 2026-01-12
