# SEP-6: Programmatic Deposit and Withdrawal

SEP-6 enables programmatic (non-interactive) deposit and withdrawal flows with Stellar anchors. Unlike SEP-24's interactive web interface, SEP-6 requires all transaction information to be provided through API calls, making it suitable for automated systems and applications that manage KYC separately.

**Use Cases**:
- Automated trading systems requiring programmatic on/off-ramp
- Applications managing KYC through SEP-12 before initiating transfers
- Server-side integrations without user interface
- High-volume transaction processing
- Systems requiring full API control over the transfer flow

**SEP-6 vs SEP-24**:
| Aspect | SEP-6 | SEP-24 |
|--------|-------|--------|
| User interaction | None - fully programmatic | Web-based interactive flow |
| KYC collection | Via SEP-12 API | Within interactive URL |
| Best for | Automated systems, backend services | Wallet apps, user-facing UIs |
| Complexity | Higher (manage KYC separately) | Lower (anchor handles KYC UI) |

## Quick Start

```kotlin
// Initialize from domain's stellar.toml
val sep06 = Sep06Service.fromDomain("testanchor.stellar.org")

// Discover supported assets
val info = sep06.info()
info.deposit?.forEach { (code, assetInfo) ->
    if (assetInfo.enabled) {
        println("$code: min=${assetInfo.minAmount}, max=${assetInfo.maxAmount}")
    }
}

// Authenticate via SEP-10 (required for all operations)
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
val jwtToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
).token

// Initiate deposit
val response = sep06.deposit(Sep06DepositRequest(
    assetCode = "USDC",
    account = userKeyPair.getAccountId(),
    jwt = jwtToken,
    amount = "100"
))

println("Transaction ID: ${response.id}")
response.instructions?.forEach { (field, instruction) ->
    println("$field: ${instruction.value} - ${instruction.description}")
}

// Poll for completion
val tx = sep06.transaction(Sep06TransactionRequest(id = response.id, jwt = jwtToken))
println("Status: ${tx.transaction.status}")
```

## Service Initialization

### From Domain (Recommended)

```kotlin
// Discovers TRANSFER_SERVER from stellar.toml
val sep06 = Sep06Service.fromDomain("testanchor.stellar.org")
```

### Direct Initialization

```kotlin
// Use when endpoint is known
val sep06 = Sep06Service.fromUrl("https://testanchor.stellar.org/sep6")
```

## Authentication

All SEP-6 endpoints except `/info` require a SEP-10 JWT token. Authenticate before initiating deposits or withdrawals.

```kotlin
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

val authResponse = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
)
val jwtToken = authResponse.token

// Use jwtToken in all subsequent SEP-6 requests
```

## Endpoints

### Get Info

Discover supported assets, limits, and anchor capabilities. Authentication is optional.

```kotlin
val info = sep06.info()

// Check deposit assets
info.deposit?.forEach { (code, assetInfo) ->
    if (assetInfo.enabled) {
        println("Deposit $code:")
        println("  Min: ${assetInfo.minAmount}")
        println("  Max: ${assetInfo.maxAmount}")
        println("  Fixed fee: ${assetInfo.feeFixed}")
        println("  Percent fee: ${assetInfo.feePercent}%")
    }
}

// Check withdrawal assets and types
info.withdraw?.forEach { (code, assetInfo) ->
    if (assetInfo.enabled) {
        println("Withdraw $code:")
        assetInfo.types?.forEach { (typeName, typeInfo) ->
            println("  Type: $typeName")
            typeInfo.fields?.forEach { (field, fieldInfo) ->
                println("    - $field: ${fieldInfo.description}")
            }
        }
    }
}

// Check SEP-38 exchange support
info.depositExchange?.forEach { (code, assetInfo) ->
    if (assetInfo.enabled) {
        println("Deposit exchange supported for: $code")
    }
}

// Check feature support
info.features?.let { features ->
    println("Claimable balances: ${features.claimableBalances}")
    println("Account creation: ${features.accountCreation}")
}
```

**Response Structure** (`Sep06InfoResponse`):
- `deposit` - Map of asset codes to `Sep06DepositAsset` for standard deposits
- `depositExchange` - Map of asset codes to `Sep06DepositExchangeAsset` for SEP-38 deposits
- `withdraw` - Map of asset codes to `Sep06WithdrawAsset` for standard withdrawals
- `withdrawExchange` - Map of asset codes to `Sep06WithdrawExchangeAsset` for SEP-38 withdrawals
- `fee` - Configuration for legacy /fee endpoint (deprecated)
- `features` - Feature flags (`Sep06FeatureFlags`)

### Initiate Deposit

Start a programmatic deposit flow. Returns deposit instructions.

```kotlin
val response = sep06.deposit(Sep06DepositRequest(
    assetCode = "USDC",
    account = userKeyPair.getAccountId(),
    jwt = jwtToken,
    amount = "100",
    claimableBalanceSupported = true
))

println("Transaction ID: ${response.id}")
println("ETA: ${response.eta} seconds")

// Display deposit instructions to user
response.instructions?.forEach { (field, instruction) ->
    println("$field: ${instruction.value}")
    println("  ${instruction.description}")
}

// Legacy 'how' field (deprecated, use instructions instead)
response.how?.let { println("Instructions: $it") }
```

**Request Parameters** (`Sep06DepositRequest`):
- `assetCode` - Asset to receive (required)
- `account` - Destination Stellar account G..., muxed account M..., or contract C... (required)
- `jwt` - SEP-10 authentication token (required)
- `assetIssuer` - Asset issuer (required when multiple assets share the same code)
- `memoType` - Memo type for Stellar payment: "text", "id", or "hash"
- `memo` - Memo value for Stellar payment
- `emailAddress` - Email for status updates
- `fundingMethod` - Method of funding (replaces deprecated `type`)
- `amount` - Intended deposit amount
- `countryCode` - ISO 3166-1 alpha-3 country code
- `claimableBalanceSupported` - Set true to receive deposits as claimable balances
- `customerId` - SEP-12 customer ID if KYC already completed
- `locationId` - Location ID for cash deposits
- `lang` - Language code (RFC 4646, e.g., "en", "es")
- `onChangeCallback` - URL for transaction status callbacks
- `extraFields` - Additional anchor-specific fields

**Response** (`Sep06DepositResponse`):
- `id` - Transaction ID for tracking
- `instructions` - Map of SEP-9 field names to deposit instructions
- `how` - Deprecated: Terse deposit instructions
- `eta` - Estimated time in seconds
- `minAmount` - Minimum deposit amount
- `maxAmount` - Maximum deposit amount
- `feeFixed` - Fixed fee amount
- `feePercent` - Percentage fee
- `extraInfo` - Additional messages from anchor

### Initiate Withdrawal

Start a programmatic withdrawal flow. Returns anchor account and memo for Stellar payment.

```kotlin
val response = sep06.withdraw(Sep06WithdrawRequest(
    assetCode = "USDC",
    type = "bank_account",
    jwt = jwtToken,
    dest = "1234567890",
    destExtra = "021000021",
    amount = "100",
    refundMemo = "refund-12345",
    refundMemoType = "text"
))

println("Transaction ID: ${response.id}")
println("Send to: ${response.accountId}")
println("Memo: ${response.memo} (${response.memoType})")
println("ETA: ${response.eta} seconds")

// Build Stellar transaction to send withdrawal
// val payment = PaymentOperation.Builder(response.accountId, asset, amount)
//     .build()
```

**Request Parameters** (`Sep06WithdrawRequest`):
- `assetCode` - Asset to withdraw (required)
- `type` - Withdrawal type: "bank_account", "crypto", "cash", "mobile" (required, deprecated - use `fundingMethod`)
- `jwt` - SEP-10 authentication token (required)
- `fundingMethod` - Method of delivery (replaces deprecated `type`)
- `dest` - Destination account (bank account, crypto address, etc.)
- `destExtra` - Additional destination info (routing number, BIC, etc.)
- `account` - Source Stellar account
- `amount` - Intended withdrawal amount
- `countryCode` - ISO 3166-1 alpha-3 country code
- `refundMemo` - Memo for refunds if needed
- `refundMemoType` - Type of refund memo
- `customerId` - SEP-12 customer ID
- `locationId` - Location ID for cash pickups
- `lang` - Language code (RFC 4646)
- `onChangeCallback` - URL for transaction status callbacks
- `extraFields` - Additional anchor-specific fields

**Response** (`Sep06WithdrawResponse`):
- `accountId` - Stellar account to send payment to
- `memoType` - Memo type for Stellar payment
- `memo` - Memo value for Stellar payment
- `id` - Transaction ID for tracking
- `eta` - Estimated time in seconds
- `minAmount` - Minimum withdrawal amount
- `maxAmount` - Maximum withdrawal amount
- `feeFixed` - Fixed fee amount
- `feePercent` - Percentage fee
- `extraInfo` - Additional messages from anchor

### Deposit Exchange (SEP-38)

Deposit with asset conversion using SEP-38 quotes. Allows depositing one off-chain asset and receiving a different Stellar asset.

```kotlin
// First, get a quote from SEP-38
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
val quote = quoteService.postQuote(
    Sep38QuoteRequest(
        context = "sep6",
        sellAsset = "iso4217:USD",
        buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
        sellAmount = "100"
    ),
    jwtToken
)
println("Quote: ${quote.sellAmount} USD -> ${quote.buyAmount} USDC")
println("Rate: ${quote.price}, expires: ${quote.expiresAt}")

// Use quote in deposit-exchange
val response = sep06.depositExchange(Sep06DepositExchangeRequest(
    destinationAsset = "USDC",
    sourceAsset = "iso4217:USD",
    amount = quote.sellAmount,
    account = userKeyPair.getAccountId(),
    jwt = jwtToken,
    quoteId = quote.id
))

println("Transaction ID: ${response.id}")
response.instructions?.forEach { (field, instruction) ->
    println("$field: ${instruction.value}")
}
```

**Request Parameters** (`Sep06DepositExchangeRequest`):
- `destinationAsset` - On-chain asset code to receive (required)
- `sourceAsset` - Off-chain asset in SEP-38 format, e.g., "iso4217:USD" (required)
- `amount` - Amount of source asset to deposit (required)
- `account` - Destination Stellar account (required)
- `jwt` - SEP-10 authentication token (required)
- `quoteId` - SEP-38 quote ID for firm pricing
- All other parameters same as `Sep06DepositRequest`

### Withdraw Exchange (SEP-38)

Withdraw with asset conversion using SEP-38 quotes. Allows sending one Stellar asset and receiving a different off-chain asset.

```kotlin
// First, get a quote from SEP-38
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
val quote = quoteService.postQuote(
    Sep38QuoteRequest(
        context = "sep6",
        sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
        buyAsset = "iso4217:EUR",
        sellAmount = "100"
    ),
    jwtToken
)
println("Quote: ${quote.sellAmount} USDC -> ${quote.buyAmount} EUR")

// Use quote in withdraw-exchange
val response = sep06.withdrawExchange(Sep06WithdrawExchangeRequest(
    sourceAsset = "USDC",
    destinationAsset = "iso4217:EUR",
    amount = quote.sellAmount,
    type = "bank_account",
    jwt = jwtToken,
    quoteId = quote.id,
    dest = "DE89370400440532013000"
))

println("Transaction ID: ${response.id}")
println("Send to: ${response.accountId}")
println("Memo: ${response.memo} (${response.memoType})")
```

**Request Parameters** (`Sep06WithdrawExchangeRequest`):
- `sourceAsset` - On-chain asset code to send (required)
- `destinationAsset` - Off-chain asset in SEP-38 format, e.g., "iso4217:EUR" (required)
- `amount` - Amount of source asset to send (required)
- `type` - Withdrawal type (required, deprecated - use `fundingMethod`)
- `jwt` - SEP-10 authentication token (required)
- `quoteId` - SEP-38 quote ID for firm pricing
- All other parameters same as `Sep06WithdrawRequest`

### Get Transaction

Query a single transaction by its identifier.

```kotlin
val response = sep06.transaction(Sep06TransactionRequest(
    id = transactionId,
    jwt = jwtToken
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
val response = sep06.transaction(Sep06TransactionRequest(
    stellarTransactionId = "abc123...",
    jwt = jwtToken
))
```

Query by external transaction ID:

```kotlin
val response = sep06.transaction(Sep06TransactionRequest(
    externalTransactionId = "BANK-REF-456",
    jwt = jwtToken
))
```

**Request Parameters** (`Sep06TransactionRequest`):
- `id` - Anchor's transaction ID (from deposit/withdraw response)
- `stellarTransactionId` - Stellar transaction hash
- `externalTransactionId` - External payment system ID
- `lang` - Language code for returned messages
- `jwt` - SEP-10 authentication token

At least one of `id`, `stellarTransactionId`, or `externalTransactionId` must be provided.

### Get Transactions (History)

Query transaction history for the authenticated account.

```kotlin
val response = sep06.transactions(Sep06TransactionsRequest(
    assetCode = "USDC",
    account = userKeyPair.getAccountId(),
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
val response = sep06.transactions(Sep06TransactionsRequest(
    assetCode = "USDC",
    account = userKeyPair.getAccountId(),
    jwt = jwtToken,
    noOlderThan = "2024-01-01T00:00:00Z"
))
```

Paginate results:

```kotlin
// First page
val firstPage = sep06.transactions(Sep06TransactionsRequest(
    assetCode = "USDC",
    account = userKeyPair.getAccountId(),
    jwt = jwtToken,
    limit = 10
))

// Next page using last transaction ID as cursor
val lastId = firstPage.transactions.lastOrNull()?.id
val nextPage = sep06.transactions(Sep06TransactionsRequest(
    assetCode = "USDC",
    account = userKeyPair.getAccountId(),
    jwt = jwtToken,
    pagingId = lastId,
    limit = 10
))
```

**Request Parameters** (`Sep06TransactionsRequest`):
- `assetCode` - Asset to query transactions for (required)
- `account` - Stellar account (required)
- `jwt` - SEP-10 authentication token (required)
- `noOlderThan` - ISO 8601 UTC datetime filter
- `limit` - Maximum number of transactions to return
- `kind` - Filter by type: "deposit", "withdrawal", or comma-separated list
- `pagingId` - Pagination cursor (transaction ID from previous response)
- `lang` - Language code for returned messages

### Patch Transaction

Update a transaction with additional information when requested by the anchor.

```kotlin
// Transaction requires additional info (status: pending_transaction_info_update)
val tx = sep06.transaction(Sep06TransactionRequest(id = transactionId, jwt = jwtToken))
    .transaction

if (tx.status == "pending_transaction_info_update") {
    println("Required fields:")
    tx.requiredInfoUpdates?.forEach { (field, info) ->
        println("  $field: ${info.description}")
    }

    // Provide the required information
    val httpResponse = sep06.patchTransaction(Sep06PatchTransactionRequest(
        id = transactionId,
        fields = mapOf(
            "dest" to "DE89370400440532013000",
            "dest_extra" to "COBADEFFXXX"
        ),
        jwt = jwtToken
    ))

    if (httpResponse.status.value == 200) {
        println("Transaction updated successfully")
    }
}
```

**Request Parameters** (`Sep06PatchTransactionRequest`):
- `id` - Transaction ID to update (required)
- `fields` - Map of field names to values (required)
- `jwt` - SEP-10 authentication token (required)

### Get Fee (Deprecated)

Query fee for an operation. This endpoint is deprecated; use SEP-38 quotes instead.

```kotlin
val feeResponse = sep06.fee(Sep06FeeRequest(
    operation = "deposit",
    assetCode = "USDC",
    amount = "100"
))
println("Fee: ${feeResponse.fee}")
```

## Transaction Status

SEP-6 transactions progress through various states before reaching a terminal status.

### Pending States

| Status | Description |
|--------|-------------|
| `incomplete` | Anchor has not received all required information |
| `pending_user_transfer_start` | Waiting for user to send funds |
| `pending_user_transfer_complete` | Funds received, waiting for processing |
| `pending_external` | Being processed by external system |
| `pending_anchor` | Being processed internally by anchor |
| `pending_stellar` | Waiting for Stellar network transaction |
| `pending_trust` | Waiting for user to establish trustline |
| `pending_user` | Waiting for user action, check message field |
| `pending_customer_info_update` | Waiting for KYC update via SEP-12 |
| `pending_transaction_info_update` | Waiting for transaction info via PATCH |

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
val tx = sep06.transaction(Sep06TransactionRequest(id = txId, jwt = jwtToken))
    .transaction

// Get status as enum
val status = tx.getStatusEnum()
when (status) {
    Sep06TransactionStatus.COMPLETED -> handleSuccess(tx)
    Sep06TransactionStatus.PENDING_USER_TRANSFER_START -> promptUserToSendFunds(tx)
    Sep06TransactionStatus.PENDING_TRUST -> promptUserToEstablishTrustline(tx)
    Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE -> {
        // Provide additional info via patchTransaction
        tx.requiredInfoUpdates?.let { fields ->
            println("Required fields: ${fields.keys.joinToString()}")
        }
    }
    null -> handleUnknownStatus(tx.status)
    else -> handlePendingStatus(tx)
}

// Check if terminal (no more status changes expected)
if (tx.isTerminal()) {
    println("Transaction reached final state: ${tx.status}")
}

// Check terminal status directly
if (Sep06TransactionStatus.isTerminal(tx.status)) {
    // Handle completion
}

// Get transaction kind
val kind = tx.getKindEnum()
when (kind) {
    Sep06TransactionKind.DEPOSIT -> println("Standard deposit")
    Sep06TransactionKind.DEPOSIT_EXCHANGE -> println("Deposit with exchange")
    Sep06TransactionKind.WITHDRAWAL -> println("Standard withdrawal")
    Sep06TransactionKind.WITHDRAWAL_EXCHANGE -> println("Withdrawal with exchange")
    null -> println("Unknown kind: ${tx.kind}")
}
```

### Polling for Completion

```kotlin
suspend fun pollTransaction(
    sep06: Sep06Service,
    transactionId: String,
    jwt: String,
    pollIntervalMs: Long = 5000,
    maxAttempts: Int = 60,
    onStatusChange: (Sep06Transaction) -> Unit = {}
): Sep06Transaction {
    var lastStatus: String? = null

    repeat(maxAttempts) {
        val tx = sep06.transaction(Sep06TransactionRequest(id = transactionId, jwt = jwt))
            .transaction

        if (tx.status != lastStatus) {
            lastStatus = tx.status
            onStatusChange(tx)
        }

        if (tx.isTerminal()) {
            return tx
        }

        delay(pollIntervalMs)
    }

    throw Sep06Exception("Polling timeout: transaction did not reach terminal status")
}

// Usage
val finalTx = pollTransaction(
    sep06 = sep06,
    transactionId = depositResponse.id!!,
    jwt = jwtToken,
    onStatusChange = { tx ->
        println("Status: ${tx.status}")
        tx.statusEta?.let { println("ETA: ${it}s") }
        tx.message?.let { println("Message: $it") }
    }
)

when (finalTx.getStatusEnum()) {
    Sep06TransactionStatus.COMPLETED -> {
        println("Success! Received: ${finalTx.amountOut}")
        finalTx.stellarTransactionId?.let { println("Stellar TX: $it") }
    }
    Sep06TransactionStatus.REFUNDED -> {
        println("Refunded: ${finalTx.refunds?.amountRefunded}")
    }
    Sep06TransactionStatus.ERROR -> {
        println("Error: ${finalTx.message}")
    }
    else -> println("Final status: ${finalTx.status}")
}
```

## Error Handling

```kotlin
try {
    val response = sep06.deposit(request)
} catch (e: Sep06AuthenticationRequiredException) {
    // JWT missing, expired, or invalid (403)
    // Re-authenticate via SEP-10
    val newJwt = webAuth.jwtToken(accountId, signers).token
    // Retry with new token
} catch (e: Sep06CustomerInformationNeededException) {
    // Additional KYC fields required (403)
    // Submit fields via SEP-12
    println("Required fields: ${e.fields.joinToString()}")
    // Collect fields and submit via SEP-12 CustomerService
} catch (e: Sep06CustomerInformationStatusException) {
    // KYC status prevents transaction (403)
    when (e.status) {
        "pending" -> {
            println("KYC verification in progress")
            e.eta?.let { println("ETA: ${it}s") }
            e.moreInfoUrl?.let { println("More info: $it") }
            // Wait and retry later
        }
        "denied" -> {
            println("KYC verification denied")
            e.moreInfoUrl?.let { println("More info: $it") }
            // Cannot proceed
        }
    }
} catch (e: Sep06InvalidRequestException) {
    // Invalid parameters (400)
    println("Bad request: ${e.errorMessage}")
    // Check asset support, amount limits, etc.
} catch (e: Sep06TransactionNotFoundException) {
    // Transaction not found (404)
    println("Transaction not found")
} catch (e: Sep06ServerErrorException) {
    // Anchor server error (5xx)
    println("Server error (${e.statusCode}): ${e.errorMessage}")
    // Retry with exponential backoff
} catch (e: Sep06Exception) {
    // Other SEP-6 errors
    println("SEP-6 error: ${e.message}")
}
```

**Exception Types**:
- `Sep06AuthenticationRequiredException` - JWT token missing, expired, or invalid
- `Sep06CustomerInformationNeededException` - Additional KYC fields required (contains `fields` list)
- `Sep06CustomerInformationStatusException` - KYC status prevents transaction (contains `status`, `moreInfoUrl`, `eta`)
- `Sep06InvalidRequestException` - Invalid request parameters (contains `errorMessage`)
- `Sep06TransactionNotFoundException` - Transaction ID does not exist
- `Sep06ServerErrorException` - Anchor server error (contains `statusCode`, `errorMessage`)
- `Sep06Exception` - Base exception for other errors

## Complete Integration Example

Full workflow from authentication to transaction completion:

```kotlin
// 1. Initialize services
val sep06 = Sep06Service.fromDomain("testanchor.stellar.org")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

// 2. User keypair (in production, load from secure storage)
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")
val accountId = userKeyPair.getAccountId()

// 3. Discover supported assets and validate
val info = sep06.info()
val usdcInfo = info.deposit?.get("USDC")
if (usdcInfo?.enabled != true) {
    throw IllegalStateException("USDC deposits not supported")
}

// 4. Authenticate via SEP-10
val jwtToken = webAuth.jwtToken(
    clientAccountId = accountId,
    signers = listOf(userKeyPair)
).token

// 5. Initiate deposit
val depositResponse = try {
    sep06.deposit(Sep06DepositRequest(
        assetCode = "USDC",
        account = accountId,
        jwt = jwtToken,
        amount = "100",
        claimableBalanceSupported = true
    ))
} catch (e: Sep06CustomerInformationNeededException) {
    // Handle KYC requirements
    println("KYC required: ${e.fields.joinToString()}")
    // Submit KYC via SEP-12, then retry
    throw e
}

println("Transaction ID: ${depositResponse.id}")

// 6. Display deposit instructions
depositResponse.instructions?.forEach { (field, instruction) ->
    println("$field: ${instruction.value}")
    println("  ${instruction.description}")
}

// 7. Poll for completion
var lastStatus: String? = null
val maxAttempts = 120
val pollInterval = 5000L

repeat(maxAttempts) { attempt ->
    val tx = sep06.transaction(Sep06TransactionRequest(
        id = depositResponse.id,
        jwt = jwtToken
    )).transaction

    if (tx.status != lastStatus) {
        lastStatus = tx.status
        when (tx.getStatusEnum()) {
            Sep06TransactionStatus.PENDING_USER_TRANSFER_START -> {
                println("Waiting for you to send funds")
                tx.instructions?.forEach { (field, instruction) ->
                    println("  $field: ${instruction.value}")
                }
            }
            Sep06TransactionStatus.PENDING_EXTERNAL -> {
                println("Processing with external provider...")
            }
            Sep06TransactionStatus.PENDING_STELLAR -> {
                println("Sending to Stellar network...")
            }
            Sep06TransactionStatus.PENDING_TRUST -> {
                println("Please add trustline for USDC")
            }
            Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE -> {
                println("Additional info required:")
                tx.requiredInfoUpdates?.forEach { (field, info) ->
                    println("  $field: ${info.description}")
                }
            }
            else -> {
                println("Status: ${tx.status}")
            }
        }
    }

    if (tx.isTerminal()) {
        // Handle final status
        when (tx.getStatusEnum()) {
            Sep06TransactionStatus.COMPLETED -> {
                println("Deposit completed!")
                println("Received: ${tx.amountOut} ${tx.amountOutAsset}")
                tx.stellarTransactionId?.let {
                    println("Stellar transaction: $it")
                }
                tx.claimableBalanceId?.let {
                    println("Claimable balance: $it")
                }
            }
            Sep06TransactionStatus.REFUNDED -> {
                println("Deposit was refunded")
                tx.refunds?.let { refunds ->
                    println("Amount refunded: ${refunds.amountRefunded}")
                    refunds.payments.forEach { payment ->
                        println("  ${payment.idType}: ${payment.id} - ${payment.amount}")
                    }
                }
            }
            Sep06TransactionStatus.ERROR -> {
                println("Deposit failed: ${tx.message}")
            }
            Sep06TransactionStatus.EXPIRED -> {
                println("Deposit expired - please try again")
            }
            else -> {
                println("Unexpected final status: ${tx.status}")
            }
        }
        return@repeat
    }

    delay(pollInterval)
}
```

## Advanced Topics

### Claimable Balance Support

Anchors can send deposits as claimable balances, allowing users to receive funds without a pre-existing trustline.

```kotlin
val response = sep06.deposit(Sep06DepositRequest(
    assetCode = "USDC",
    account = userKeyPair.getAccountId(),
    jwt = jwtToken,
    claimableBalanceSupported = true
))

// Later, check if deposit was sent as claimable balance
val tx = sep06.transaction(Sep06TransactionRequest(id = response.id, jwt = jwtToken))
    .transaction

tx.claimableBalanceId?.let { balanceId ->
    println("Claimable balance created: $balanceId")
    // Claim the balance after establishing trustline
}
```

### Callback Notifications

Receive transaction status updates via webhook instead of polling.

```kotlin
val response = sep06.deposit(Sep06DepositRequest(
    assetCode = "USDC",
    account = userKeyPair.getAccountId(),
    jwt = jwtToken,
    onChangeCallback = "https://myapp.example.com/stellar/callback"
))

// Anchor will POST status updates to your callback URL
// Callback payload matches Sep06Transaction structure
```

### Handling Required Info Updates

When a transaction requires additional information:

```kotlin
val tx = sep06.transaction(Sep06TransactionRequest(id = txId, jwt = jwtToken))
    .transaction

if (tx.getStatusEnum() == Sep06TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE) {
    println("Additional information required:")
    println("Message: ${tx.requiredInfoMessage}")

    tx.requiredInfoUpdates?.forEach { (field, info) ->
        println("Field: $field")
        println("  Description: ${info.description}")
        println("  Optional: ${info.optional ?: false}")
        info.choices?.let { choices ->
            println("  Choices: ${choices.joinToString()}")
        }
    }

    // Collect and submit the required fields
    val fields = mapOf(
        "dest" to "1234567890",
        "dest_extra" to "021000021"
    )

    val patchResponse = sep06.patchTransaction(Sep06PatchTransactionRequest(
        id = txId,
        fields = fields,
        jwt = jwtToken
    ))
}
```

## API Reference

**Main Class**:
- `Sep06Service` - SEP-6 programmatic deposit/withdrawal client

**Factory Methods**:
- `Sep06Service.fromDomain(domain)` - Initialize from stellar.toml
- `Sep06Service.fromUrl(serviceAddress)` - Direct initialization

**Methods**:
- `info(lang?, jwt?)` - Get supported assets and capabilities
- `deposit(request)` - Initiate programmatic deposit
- `depositExchange(request)` - Initiate deposit with SEP-38 exchange
- `withdraw(request)` - Initiate programmatic withdrawal
- `withdrawExchange(request)` - Initiate withdrawal with SEP-38 exchange
- `transaction(request)` - Get single transaction
- `transactions(request)` - Get transaction history
- `patchTransaction(request)` - Update transaction with additional info
- `fee(request)` - Get fee (deprecated, use SEP-38)

**Request Classes**:
- `Sep06DepositRequest` - Deposit parameters
- `Sep06DepositExchangeRequest` - Deposit exchange parameters
- `Sep06WithdrawRequest` - Withdrawal parameters
- `Sep06WithdrawExchangeRequest` - Withdrawal exchange parameters
- `Sep06TransactionRequest` - Single transaction query
- `Sep06TransactionsRequest` - Transaction history query
- `Sep06PatchTransactionRequest` - Transaction update
- `Sep06FeeRequest` - Fee query (deprecated)

**Response Classes**:
- `Sep06InfoResponse` - Anchor capabilities
- `Sep06DepositResponse` - Deposit response with instructions
- `Sep06WithdrawResponse` - Withdrawal response with anchor account
- `Sep06TransactionResponse` - Single transaction wrapper
- `Sep06TransactionsResponse` - Transaction list wrapper
- `Sep06Transaction` - Transaction details
- `Sep06FeeResponse` - Fee amount (deprecated)

**Supporting Classes**:
- `Sep06DepositAsset` - Deposit asset configuration
- `Sep06DepositExchangeAsset` - Deposit exchange configuration
- `Sep06WithdrawAsset` - Withdrawal asset configuration
- `Sep06WithdrawExchangeAsset` - Withdrawal exchange configuration
- `Sep06WithdrawType` - Withdrawal type with fields
- `Sep06Field` - Field definition (description, optional, choices)
- `Sep06DepositInstruction` - Deposit instruction (value, description)
- `Sep06FeatureFlags` - Feature flags (claimable balances, account creation)
- `Sep06FeeDetails` - Fee breakdown
- `Sep06FeeDetail` - Individual fee component
- `Sep06Refunds` - Refund information
- `Sep06RefundPayment` - Individual refund payment
- `Sep06TransactionStatus` - Transaction status enum
- `Sep06TransactionKind` - Transaction kind enum

**Exception Types**:
- `Sep06Exception` - Base exception
- `Sep06AuthenticationRequiredException` - JWT invalid (403)
- `Sep06CustomerInformationNeededException` - KYC fields needed (403)
- `Sep06CustomerInformationStatusException` - KYC status prevents transaction (403)
- `Sep06InvalidRequestException` - Bad request (400)
- `Sep06TransactionNotFoundException` - Not found (404)
- `Sep06ServerErrorException` - Server error (5xx)

**Specification**: [SEP-6: Deposit and Withdrawal API](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0006.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep06`

**Last Updated**: 2026-01-14
