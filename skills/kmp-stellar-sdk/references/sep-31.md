# SEP-31: Cross-Border Payments

**Purpose:** Send payments through a Receiving Anchor to a recipient who receives funds off-chain (bank account, mobile wallet, etc.). The Sending Anchor side is what this SDK implements.
**Prerequisites:** Requires JWT from SEP-10 (see [sep-10.md](sep-10.md)). Often used with SEP-12 (KYC, see [sep-12.md](sep-12.md)) and SEP-38 (quotes, see [sep-38.md](sep-38.md)).
**SDK package:** `com.soneso.stellar.sdk.sep.sep31`

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep31.*
import com.soneso.stellar.sdk.sep.sep31.exceptions.*
import com.soneso.stellar.sdk.sep.sep09.*
import com.soneso.stellar.sdk.sep.sep12.*
```

## Table of Contents

- [How It Works](#how-it-works)
- [Creating the Service](#creating-the-service)
- [GET /info — Discover Assets and KYC Requirements](#get-info)
- [POST /transactions — Initiate a Payment](#post-transactions)
- [GET /transactions/:id — Track Status](#get-transactionsid)
- [PUT /transactions/:id/callback — Register Callback](#put-callback)
- [PATCH /transactions/:id — Update Fields (Deprecated)](#patch-transactions)
- [Complete Payment Flow](#complete-payment-flow)
- [Transaction Statuses](#transaction-statuses)
- [Response Objects](#response-objects)
- [Exception Reference](#exception-reference)
- [Verifying Callback Signatures](#verifying-callback-signatures)
- [Common Pitfalls](#common-pitfalls)

---

## How It Works

1. **Authenticate**: get a JWT via SEP-10 using the Sending Anchor's pre-authorized Stellar account.
2. **Discover**: query `GET /info` to learn supported assets, limits, fees, KYC requirements, and funding methods.
3. **KYC**: register sender and receiver via SEP-12 (when required by the Receiving Anchor).
4. **Quote** (optional): get a locked-in exchange rate via SEP-38 when sending cross-asset.
5. **Initiate**: `POST /transactions` to the Receiving Anchor; receive a transaction ID and Stellar payment instructions.
6. **Pay**: send the Stellar payment to the anchor's account with the exact memo provided.
7. **Track**: poll `GET /transactions/:id` (or register a callback) until `completed` or handle errors.

---

## Creating the Service

`Sep31Service` handles all SEP-31 operations.

### From a domain (recommended)

Loads `DIRECT_PAYMENT_SERVER` from the anchor's `stellar.toml` automatically. HTTPS is enforced.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ConfigurationException

val service: Sep31Service = try {
    Sep31Service.fromDomain("receivinganchor.com")
} catch (e: Sep31ConfigurationException) {
    // Thrown if stellar.toml is unreachable or DIRECT_PAYMENT_SERVER is missing
    error("Cannot reach anchor: ${e.message}")
}
```

Signature:
```kotlin
suspend fun Sep31Service.Companion.fromDomain(
    domain: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null,
): Sep31Service
```

`fromDomain` accepts `host` or `host:port` for the local development case. The `DIRECT_PAYMENT_SERVER` URL from `stellar.toml` must be HTTPS in production; HTTP is accepted only for loopback hosts (`localhost`, `127.0.0.1`, `[::1]`).

### From a direct URL

When the server endpoint is already known:

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service

val service = Sep31Service("https://api.receivinganchor.com/sep31")
```

Constructor signature:
```kotlin
class Sep31Service(
    val serviceUrl: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null,
)
```

### With custom HTTP client and headers

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import io.ktor.client.*
import io.ktor.client.engine.cio.*

val client = HttpClient(CIO) { /* engine config */ }

val service = Sep31Service(
    serviceUrl = "https://api.receivinganchor.com/sep31",
    httpClient = client,
    httpRequestHeaders = mapOf("X-Wallet-Id" to "my-wallet"),
)
```

A caller-supplied `HttpClient` is used verbatim and never closed by the service; close it yourself when done. When `httpClient` is null the service constructs an internal Ktor client with `followRedirects = false`, content negotiation, and a 30 s request timeout.

---

## GET /info

Query the anchor to discover supported assets, limits, fees, required SEP-12 KYC types, and funding methods.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnknownResponseException
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val service = Sep31Service.fromDomain("receivinganchor.com")

val info = try {
    service.info(jwt = jwtToken)
} catch (e: Sep31BadRequestException) {
    error("Bad request: ${e.message}")
} catch (e: Sep31UnknownResponseException) {
    error("Unexpected response (HTTP ${e.statusCode}): ${e.message}")
}

// receiveAssets is a Map<String, Sep31ReceiveAssetInfo>, never null (empty Map for {"receive":{}})
for ((assetCode, assetInfo) in info.receiveAssets) {
    println("Asset: $assetCode")
    println("  Min amount:        ${assetInfo.minAmount ?: "No limit"}")
    println("  Max amount:        ${assetInfo.maxAmount ?: "No limit"}")
    println("  Fixed fee:         ${assetInfo.feeFixed ?: "N/A"}")
    println("  Percent fee:       ${assetInfo.feePercent ?: "N/A"}")
    println("  Quotes supported:  ${assetInfo.quotesSupported ?: false}")
    println("  Quotes required:   ${assetInfo.quotesRequired ?: false}")

    // fundingMethods is List<String>? (e.g. ["bank_account", "cash"])
    assetInfo.fundingMethods?.let { methods ->
        println("  Funding methods:   ${methods.joinToString(", ")}")
    }

    // SEP-12 KYC types required for senders and receivers.
    // sep12Info.senderTypes  : Map<String, String>  (type key -> description)
    // sep12Info.receiverTypes: Map<String, String>  (type key -> description)
    for ((type, description) in assetInfo.sep12Info.senderTypes) {
        println("  Sender type '$type': $description")
    }
    for ((type, description) in assetInfo.sep12Info.receiverTypes) {
        println("  Receiver type '$type': $description")
    }
}
```

Method signature:
```kotlin
suspend fun Sep31Service.info(jwt: String, lang: String? = null): Sep31InfoResponse
```

`lang` is an optional ISO 639-1 language code for human-readable error and field descriptions.

---

## POST /transactions

Initiate a payment. Returns a transaction ID and Stellar payment instructions (account + memo).

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31CustomerInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ForbiddenException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnauthorizedException
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val service = Sep31Service.fromDomain("receivinganchor.com")

// amount, assetCode, fundingMethod are REQUIRED (non-nullable).
val request = Sep31PostTransactionsRequest(
    amount = 100.0,
    assetCode = "USDC",
    fundingMethod = "SWIFT",                                                         // required
    assetIssuer = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",         // optional
    destinationAsset = "iso4217:BRL",                                                // optional, SEP-38 format
    quoteId = quoteId,                                                               // optional, from SEP-38
    senderId = senderId,                                                             // optional, from SEP-12
    receiverId = receiverId,                                                         // optional, from SEP-12
    refundMemo = "refund-12345",                                                     // optional
    refundMemoType = "text",                                                         // optional: "id", "text", "hash"
    lang = "en",                                                                     // optional
)

@Suppress("DEPRECATION") // covers the catch clause for Sep31TransactionInfoNeededException
val response = try {
    service.postTransactions(request = request, jwt = jwtToken)
} catch (e: Sep31CustomerInfoNeededException) {
    // KYC data missing — register via SEP-12 with the type the anchor wants, then retry.
    // e.type is the SEP-12 customer type to register (String?)
    error("KYC needed. Register via SEP-12 with type: ${e.type ?: "<unspecified>"}")
} catch (e: Sep31TransactionInfoNeededException) {
    // Legacy 400 variant (error="transaction_info_needed") — anchor wants inline fields.
    // Deprecated path; new integrations register via SEP-12 instead.
    error("Legacy info-update fields needed: ${e.fields}")
} catch (e: Sep31BadRequestException) {
    error("Bad request: ${e.message}")
} catch (e: Sep31UnauthorizedException) {
    error("JWT invalid or expired (HTTP 401): ${e.message}")
} catch (e: Sep31ForbiddenException) {
    error("Authentication failed (HTTP 403): ${e.message}")
}

// Sep31PostTransactionsResponse fields:
val transactionId = response.id                  // String — always present
val stellarAccount = response.stellarAccountId   // String? — may be null initially; poll if so
val memo = response.stellarMemo                  // String? — value to attach to the Stellar payment
val memoType = response.stellarMemoType          // String? — "id", "text", or "hash"

println("Transaction ID: $transactionId")
if (stellarAccount != null) {
    println("Send to: $stellarAccount with memo ($memoType): $memo")
} else {
    // Poll GET /transactions/:id until status == "pending_sender" to get payment instructions
    println("Payment instructions not yet available — poll for status")
}
```

Method signature:
```kotlin
suspend fun Sep31Service.postTransactions(
    request: Sep31PostTransactionsRequest,
    jwt: String,
): Sep31PostTransactionsResponse
```

Accepts `200 OK` or `201 Created` from the server.

### Sep31PostTransactionsRequest constructor

```kotlin
data class Sep31PostTransactionsRequest(
    val amount: Double,                 // required
    val assetCode: String,              // required
    val fundingMethod: String,          // required
    val assetIssuer: String? = null,
    val destinationAsset: String? = null,
    val quoteId: String? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    @Deprecated("Use SEP-12 PUT /customer + sender_id / receiver_id instead.")
    val fields: Map<String, Any?>? = null,
    val lang: String? = null,
    val refundMemo: String? = null,
    val refundMemoType: String? = null,
)
```

Use named arguments; there are too many optional fields to remember positional order. `fundingMethod` is a wire-format string that must match one of the values exposed in `Sep31ReceiveAssetInfo.fundingMethods` for the chosen asset.

---

## GET /transactions/:id

Fetch the current state of a transaction.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionNotFoundException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnknownResponseException

val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val transactionId = "82fhs729f63dh0v4" // id returned when the transfer was initiated
// jwtToken: from the previous steps of this flow

val service = Sep31Service.fromDomain("receivinganchor.com")

val tx = try {
    service.getTransaction(id = transactionId, jwt = jwtToken)
} catch (e: Sep31TransactionNotFoundException) {
    error("Transaction not found (HTTP 404)")
} catch (e: Sep31BadRequestException) {
    error("Bad request: ${e.message}")
} catch (e: Sep31UnknownResponseException) {
    error("Unexpected response (HTTP ${e.statusCode}): ${e.message}")
}

// Core status fields
println("Status:           ${tx.status}")
println("Status ETA:       ${tx.statusEta ?: "N/A"} seconds")
println("Status message:   ${tx.statusMessage ?: "N/A"}")

// Amount fields (all String?)
println("Amount in:        ${tx.amountIn ?: "N/A"}")
println("Amount in asset:  ${tx.amountInAsset ?: "N/A"}")     // SEP-38 format when quoted
println("Amount out:       ${tx.amountOut ?: "N/A"}")
println("Amount out asset: ${tx.amountOutAsset ?: "N/A"}")    // SEP-38 format when quoted
@Suppress("DEPRECATION")
println("Amount fee:       ${tx.amountFee ?: "N/A"}")          // DEPRECATED — use feeDetails
@Suppress("DEPRECATION")
println("Amount fee asset: ${tx.amountFeeAsset ?: "N/A"}")     // DEPRECATED — use feeDetails

// Timestamps (UTC ISO 8601 strings)
println("Started at:       ${tx.startedAt ?: "N/A"}")
println("Updated at:       ${tx.updatedAt ?: "N/A"}")
println("Completed at:     ${tx.completedAt ?: "N/A"}")

// Payment identifiers
println("Stellar tx ID:    ${tx.stellarTransactionId ?: "N/A"}")
println("External tx ID:   ${tx.externalTransactionId ?: "N/A"}")

// Payment destination (populated when status becomes pending_sender)
println("Stellar account:  ${tx.stellarAccountId ?: "N/A"}")
println("Stellar memo:     ${tx.stellarMemo ?: "N/A"}")
println("Memo type:        ${tx.stellarMemoType ?: "N/A"}")

// Quote ID if SEP-38 was used
println("Quote ID:         ${tx.quoteId ?: "N/A"}")

// Structured fee breakdown (preferred over the deprecated amountFee fields)
tx.feeDetails?.let { fees ->
    println("Total fee: ${fees.total} ${fees.asset}")
    for (detail in fees.details ?: emptyList()) {
        println("  ${detail.name}: ${detail.amount}${detail.description?.let { " ($it)" } ?: ""}")
    }
}
```

Method signature:
```kotlin
suspend fun Sep31Service.getTransaction(id: String, jwt: String): Sep31TransactionResponse
```

### Polling for payment instructions

If `stellarAccountId` is `null` in the POST response, the anchor is still preparing the transaction. Poll until `status == "pending_sender"`:

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionStatus
import kotlinx.coroutines.delay
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val transactionId = "82fhs729f63dh0v4" // id returned when the transfer was initiated

val service = Sep31Service.fromDomain("receivinganchor.com")

var stellarAccount: String? = null
var memo: String? = null
var memoType: String? = null
var waitMillis = 5_000L
val maxWaitMillis = 60_000L

while (stellarAccount == null) {
    delay(waitMillis)

    val tx = service.getTransaction(id = transactionId, jwt = jwtToken)

    when (Sep31TransactionStatus.fromString(tx.status)) {
        Sep31TransactionStatus.PENDING_SENDER -> {
            stellarAccount = tx.stellarAccountId
            memo = tx.stellarMemo
            memoType = tx.stellarMemoType
            break
        }
        Sep31TransactionStatus.ERROR -> {
            error("Transaction failed: ${tx.statusMessage ?: "unknown error"}")
        }
        Sep31TransactionStatus.EXPIRED -> {
            error("Transaction expired before payment instructions were issued")
        }
        else -> {
            // statusEta is in seconds; respect it when provided, otherwise back off.
            waitMillis = tx.statusEta?.times(1_000L)?.coerceAtLeast(5_000L)
                ?: (waitMillis * 2).coerceAtMost(maxWaitMillis)
        }
    }
}
```

`Sep31TransactionStatus.fromString(value)` returns `null` for unknown values **and for values that differ only in case** (`"completed"` resolves, `"COMPLETED"` does not). The SDK keeps `Sep31TransactionResponse.status` as a raw `String` so future spec additions are accepted without an SDK release. See the [Transaction Statuses](#transaction-statuses) table below for the meaning of each value.

### Resolving `pending_customer_info_update`

When polling surfaces `PENDING_CUSTOMER_INFO_UPDATE`, the Receiving Anchor needs additional KYC for the registered customer scoped to this transaction. Resolve via SEP-12: pass the `transactionId` to both `getCustomerInfo` (to discover which fields are missing) and `putCustomerInfo` (to submit them). Scoping by `transactionId` returns only the fields the anchor needs for this specific transaction, not the customer's global KYC state.

```kotlin
// jwtToken, quoteId, receiverId, senderId: from the previous steps of this flow
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep12.CustomerStatus
import com.soneso.stellar.sdk.sep.sep12.GetCustomerInfoRequest
import com.soneso.stellar.sdk.sep.sep12.KYCService
import com.soneso.stellar.sdk.sep.sep12.PutCustomerInfoRequest

suspend fun resolveCustomerInfoUpdate(
    kyc: KYCService,
    customerId: String,
    transactionId: String,
    jwt: String,
) {
    // Scope the GET by transactionId so the anchor returns only the fields
    // it needs for THIS transaction.
    val needs = kyc.getCustomerInfo(
        GetCustomerInfoRequest(jwt = jwt, id = customerId, transactionId = transactionId),
    )
    if (needs.status != CustomerStatus.NEEDS_INFO) return

    // Collect the missing values from the user. Production code reads from
    // the application's UI or storage; the example hardcodes for brevity.
    val collected = NaturalPersonKYCFields(
        address = "123 Main Street",
        city = "San Francisco",
        addressCountryCode = "USA",
    )
    kyc.putCustomerInfo(
        PutCustomerInfoRequest(
            jwt = jwt,
            id = customerId,
            transactionId = transactionId,
            kycFields = StandardKYCFields(naturalPersonKYCFields = collected),
        ),
    )
}
```

After the SEP-12 round-trip completes, the Receiving Anchor re-evaluates the transaction and advances its status. Resume polling (or wait for a callback) to observe the next state. See [SEP-12](sep-12.md) for the field catalog, document uploads, and verification codes.

---

## PUT /callback

Register a URL for status-change notifications so polling is not required.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionCallbackNotSupportedException
val transactionId = "82fhs729f63dh0v4" // id returned when the transfer was initiated

val service = Sep31Service.fromDomain("receivinganchor.com")

try {
    service.putTransactionCallback(
        id = transactionId,
        callbackUrl = "https://myanchor.com/callbacks/sep31",   // must be HTTPS (loopback HTTP allowed for dev)
        jwt = jwtToken,
    )
    println("Callback registered")
} catch (e: Sep31TransactionCallbackNotSupportedException) {
    // HTTP 404 — anchor does not support callbacks; fall back to polling.
    // Note: the anchor may return 404 either because callbacks are unsupported
    // OR because the transaction id is unknown. Treat both the same in practice.
    println("Callbacks not supported — use polling instead")
} catch (e: Sep31BadRequestException) {
    println("Bad request: ${e.message}")
}
```

Method signature:
```kotlin
suspend fun Sep31Service.putTransactionCallback(
    id: String,
    callbackUrl: String,
    jwt: String,
): Unit
```

Returns `Unit` on `204 No Content`. The SDK validates `callbackUrl` before sending: HTTPS is required, with HTTP allowed only for loopback authorities.

For verifying inbound callback signatures, see [Verifying Callback Signatures](#verifying-callback-signatures) below.

---

## PATCH /transactions

**Deprecated.** Sends updated transaction fields to the anchor when status is `pending_transaction_info_update`. Use SEP-12 `PUT /customer` for all new integrations.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionNotFoundException
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val transactionId = "82fhs729f63dh0v4" // id returned when the transfer was initiated

val service = Sep31Service.fromDomain("receivinganchor.com")

// The fields Map is sent as {"fields": <your map>} in the request body — the SDK wraps it.
val fields: Map<String, Any?> = mapOf(
    "transaction" to mapOf(
        "receiver_bank_account" to "12345678901234",
        "receiver_routing_number" to "021000021",
    ),
)

try {
    @Suppress("DEPRECATION")
    val updated = service.patchTransaction(
        id = transactionId,
        fields = fields,
        jwt = jwtToken,
    )
    println("New status after update: ${updated.status}")
} catch (e: Sep31TransactionNotFoundException) {
    println("Transaction not found")
} catch (e: Sep31BadRequestException) {
    println("Bad request (update not requested or invalid fields): ${e.message}")
}
```

Method signature (annotated `@Deprecated`):
```kotlin
@Deprecated("Use SEP-12 PUT /customer to update KYC fields instead.")
suspend fun Sep31Service.patchTransaction(
    id: String,
    fields: Map<String, Any?>,
    jwt: String,
): Sep31TransactionResponse
```

The KMP SDK returns the parsed response (per spec, the PATCH body matches `GET /transactions/:id`), so a follow-up `getTransaction` call is unnecessary.

---

## Complete Payment Flow

End-to-end example combining SEP-10, SEP-12, and SEP-31:

```kotlin
// jwtToken, transactionId: from the previous steps of this flow
import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.Account
import com.soneso.stellar.sdk.AssetTypeCreditAlphaNum4
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MemoHash
import com.soneso.stellar.sdk.MemoId
import com.soneso.stellar.sdk.MemoText
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.PaymentOperation
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep12.GetCustomerInfoRequest
import com.soneso.stellar.sdk.sep.sep12.KYCService
import com.soneso.stellar.sdk.sep.sep12.PutCustomerInfoRequest
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionStatus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay

@OptIn(ExperimentalEncodingApi::class)
suspend fun fullPaymentFlow(sendingAnchorSeed: String) {
    val domain = "receivinganchor.com"
    
    // Step 1: Authenticate via SEP-10
    val sendingKeyPair = KeyPair.fromSecretSeed(sendingAnchorSeed)
    val webAuth = WebAuth.fromDomain(domain = domain, network = Network.TESTNET)
    val authToken = webAuth.jwtToken(
        clientAccountId = sendingKeyPair.getAccountId(),
        signers = listOf(sendingKeyPair),
    )
    val jwt = authToken.token
    
    // Step 2: Query /info to learn KYC types
    val sep31 = Sep31Service.fromDomain(domain)
    val info = sep31.info(jwt = jwt)
    val usdc = info.receiveAssets["USDC"] ?: error("Anchor does not accept USDC")
    
    // Step 3: Register sender via SEP-12
    val kyc = KYCService.fromDomain(domain = domain)
    
    val senderId = kyc.putCustomerInfo(
        PutCustomerInfoRequest(
            jwt = jwt,
            type = usdc.sep12Info.senderTypes.keys.first(),
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Jane",
                    lastName = "Sender",
                    emailAddress = "jane@example.com",
                ),
            ),
        )
    ).id
    
    // Step 4: Register receiver via SEP-12
    val receiverId = kyc.putCustomerInfo(
        PutCustomerInfoRequest(
            jwt = jwt,
            type = usdc.sep12Info.receiverTypes.keys.first(),
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Bob",
                    lastName = "Receiver",
                ),
            ),
            customFields = mapOf(
                "bank_account_number" to "1234567890",
                "bank_routing_number" to "021000021",
            ),
        )
    ).id
    
    // Step 5: Initiate the transaction
    val postResponse = sep31.postTransactions(
        request = Sep31PostTransactionsRequest(
            amount = 100.0,
            assetCode = "USDC",
            fundingMethod = usdc.fundingMethods?.first() ?: "SWIFT",
            assetIssuer = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            destinationAsset = "iso4217:BRL",
            senderId = senderId,
            receiverId = receiverId,
            refundMemo = "REFUND-INV-42",
            refundMemoType = "text",
        ),
        jwt = jwt,
    )
    val transactionId = postResponse.id
    
    // Step 6: Poll for payment instructions if not immediately available
    var stellarAccount = postResponse.stellarAccountId
    var memo = postResponse.stellarMemo
    var memoType = postResponse.stellarMemoType
    
    if (stellarAccount == null) {
        var waitMillis = 5_000L
        while (stellarAccount == null) {
            delay(waitMillis)
            val tx = sep31.getTransaction(id = transactionId, jwt = jwt)
            when (Sep31TransactionStatus.fromString(tx.status)) {
                Sep31TransactionStatus.PENDING_SENDER -> {
                    stellarAccount = tx.stellarAccountId
                    memo = tx.stellarMemo
                    memoType = tx.stellarMemoType
                }
                Sep31TransactionStatus.ERROR -> error("Transaction error: ${tx.statusMessage}")
                Sep31TransactionStatus.EXPIRED -> error("Transaction expired before payment instructions")
                else -> { waitMillis = (waitMillis * 2).coerceAtMost(60_000L) }
            }
        }
    }
    
    // Step 7: Send the Stellar payment with the EXACT memo from the anchor.
    //         Memo type comes from the anchor and determines which Memo subtype to construct.
    val horizon = HorizonServer("https://horizon-testnet.stellar.org")
    val senderAccountResponse = horizon.accounts().account(sendingKeyPair.getAccountId())
    
    val usdcAsset = AssetTypeCreditAlphaNum4(
        code = "USDC",
        issuer = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    )
    
    val payment = PaymentOperation(
        destination = stellarAccount!!,
        asset = usdcAsset,
        amount = "100",
    )
    
    val memoValue = memo ?: error("Anchor did not provide a memo")
    val memoObj = when (memoType) {
        "id" -> MemoId(memoValue.toULong())          // MemoId takes ULong, not Long
        "text" -> MemoText(memoValue)
        // Per SEP-31, stellar_memo for memo_type "hash" is base64-encoded (32 raw bytes after
        // decoding). MemoHash(String) parses hex, so decode the base64 string to ByteArray first.
        "hash" -> MemoHash(Base64.decode(memoValue))
        else -> error("Unknown memo type: $memoType")
    }
    
    val tx = TransactionBuilder(
        sourceAccount = Account(sendingKeyPair.getAccountId(), senderAccountResponse.sequenceNumber),
        network = Network.TESTNET,
    )
        .addOperation(payment)
        .addMemo(memoObj)
        .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
        .setTimeout(120)
        .build()
    
    tx.sign(sendingKeyPair)
    val submitted = horizon.submitTransaction(tx.toEnvelopeXdrBase64())
    check(submitted.successful) { "Payment failed: ${submitted.resultXdr}" }
    println("Payment submitted: ${submitted.hash}")
    
    // Step 8: Track until terminal status
    while (true) {
        delay(10_000L)
        val current = sep31.getTransaction(id = transactionId, jwt = jwt)
        println("Status: ${current.status}")
        val status = Sep31TransactionStatus.fromString(current.status)
        if (status == Sep31TransactionStatus.COMPLETED ||
            status == Sep31TransactionStatus.REFUNDED ||
            status == Sep31TransactionStatus.EXPIRED ||
            status == Sep31TransactionStatus.ERROR
        ) break
    }
}
```

---

## Transaction Statuses

| Status (enum constant) | Wire value | Meaning |
|---|---|---|
| `PENDING_SENDER` | `pending_sender` | Awaiting Stellar payment from Sending Anchor |
| `PENDING_STELLAR` | `pending_stellar` | Stellar payment received, confirming on network |
| `PENDING_CUSTOMER_INFO_UPDATE` | `pending_customer_info_update` | Anchor needs more / corrected KYC; query SEP-12 for required fields |
| `PENDING_TRANSACTION_INFO_UPDATE` | `pending_transaction_info_update` | Anchor needs updated inline fields (deprecated; use SEP-12) |
| `PENDING_RECEIVER` | `pending_receiver` | Being processed by Receiving Anchor |
| `PENDING_EXTERNAL` | `pending_external` | Submitted to external payment network, awaiting confirmation |
| `COMPLETED` | `completed` | Funds delivered to Receiving Client |
| `REFUNDED` | `refunded` | Funds refunded to Sending Anchor (see `tx.refunds`) |
| `EXPIRED` | `expired` | Transaction abandoned or SEP-38 quote expired before payment |
| `ERROR` | `error` | Error occurred. Check `tx.statusMessage` |

`tx.status` is a raw `String` on the response object; use `Sep31TransactionStatus.fromString(tx.status)` for typed dispatch. Returns `null` for unknown statuses.

---

## Response Objects

Field signatures listed here; consult the SDK KDoc for full semantics. Non-obvious behaviours are commented inline.

### Sep31InfoResponse

```kotlin
val receiveAssets: Map<String, Sep31ReceiveAssetInfo>   // keyed by asset code, never null (may be empty)
```

### Sep31ReceiveAssetInfo

```kotlin
val sep12Info: Sep31Sep12TypesInfo                      // always set; empty maps when anchor requires no KYC
val minAmount: Double?
val maxAmount: Double?
val feeFixed: Double?
val feePercent: Double?
val quotesSupported: Boolean?
val quotesRequired: Boolean?
val fundingMethods: List<String>?                       // supported payment rails (e.g., "SWIFT", "bank_account")
@Deprecated val senderSep12Type: String?                // legacy single-type field, superseded by sep12Info
@Deprecated val receiverSep12Type: String?              // legacy single-type field, superseded by sep12Info
@Deprecated val fields: Map<String, Any?>?              // legacy inline fields, superseded by SEP-12
```

### Sep31Sep12TypesInfo

```kotlin
val senderTypes: Map<String, String>                    // type key -> human-readable description
val receiverTypes: Map<String, String>                  // type key -> human-readable description
```

### Sep31PostTransactionsResponse

```kotlin
val id: String                                          // always present
val stellarAccountId: String?                           // may be null initially; poll until populated
val stellarMemoType: String?                            // "id", "text", or "hash"
val stellarMemo: String?
```

### Sep31TransactionResponse

```kotlin
val id: String
val status: String                                      // raw string; use Sep31TransactionStatus.fromString()
val statusEta: Long?
val statusMessage: String?
val amountIn: String?
val amountInAsset: String?                              // SEP-38 format when quote or destination_asset used
val amountOut: String?
val amountOutAsset: String?                             // SEP-38 format when quote or destination_asset used
@Deprecated val amountFee: String?                      // use feeDetails instead
@Deprecated val amountFeeAsset: String?                 // use feeDetails instead
val feeDetails: Sep31FeeDetails?
val quoteId: String?
val stellarAccountId: String?
val stellarMemoType: String?
val stellarMemo: String?
val startedAt: String?                                  // UTC ISO 8601
val updatedAt: String?                                  // UTC ISO 8601
val completedAt: String?                                // UTC ISO 8601
val stellarTransactionId: String?
val externalTransactionId: String?
@Deprecated val refunded: Boolean?                      // use refunds instead
val refunds: Sep31Refunds?
val requiredInfoMessage: String?
val requiredInfoUpdates: Map<String, Any?>?             // populated on pending_transaction_info_update
```

### Sep31FeeDetails / Sep31FeeDetailsDetails

```kotlin
class Sep31FeeDetails(
    val total: String,
    val asset: String,                                   // SEP-38 format asset identifier
    val details: List<Sep31FeeDetailsDetails>?,
)

class Sep31FeeDetailsDetails(
    val name: String,
    val amount: String,
    val description: String?,
)
```

### Sep31Refunds / Sep31RefundPayment

```kotlin
class Sep31Refunds(
    val amountRefunded: String,                          // in units of amount_in_asset
    val amountFee: String,
    val payments: List<Sep31RefundPayment>,
)

class Sep31RefundPayment(
    val id: String,                                      // Stellar transaction hash of the refund
    val amount: String,
    val fee: String,
)
```

Usage:

```kotlin
val jwt = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val transactionId = "82fhs729f63dh0v4" // id returned when the transfer was initiated
val tx = sep31.getTransaction(id = transactionId, jwt = jwt)
if (tx.status == "refunded") {
    val r = tx.refunds ?: error("Anchor reported 'refunded' but no refunds object present")
    println("Total refunded: ${r.amountRefunded}")
    println("Refund fees:    ${r.amountFee}")
    for (p in r.payments) {
        println("  Stellar TX: ${p.id}  amount: ${p.amount}  fee: ${p.fee}")
    }
}
```

---

## Exception Reference

All exceptions extend `Sep31Exception` and live in `com.soneso.stellar.sdk.sep.sep31.exceptions`.

| Exception | HTTP status | When thrown | Key fields |
|---|---|---|---|
| `Sep31ConfigurationException` | n/a | `fromDomain()` when stellar.toml is unreachable or `DIRECT_PAYMENT_SERVER` is missing/malformed | `message`, `cause` |
| `Sep31BadRequestException` | 400 | Any method: malformed request, invalid parameters | `message`, `statusCode = 400`, `rawResponseBody` |
| `Sep31CustomerInfoNeededException` | 400 | `postTransactions()`: anchor needs SEP-12 KYC. Subtype of bad-request, signaled by `error = "customer_info_needed"`. | `type` (SEP-12 type to register), `error`, `rawResponseBody` |
| `Sep31TransactionInfoNeededException` | 400 | `postTransactions()`: deprecated inline fields needed | `fields`, `error`, `rawResponseBody` |
| `Sep31UnauthorizedException` | 401 | Any protected method: JWT missing, invalid, or expired | `message`, `statusCode = 401`, `rawResponseBody` |
| `Sep31ForbiddenException` | 403 | Any protected method: JWT lacks permission (anchors may return 403 instead of 401 for invalid JWT) | `message`, `statusCode = 403`, `rawResponseBody` |
| `Sep31TransactionNotFoundException` | 404 | `getTransaction()`, `patchTransaction()`: unknown ID | `message`, `statusCode = 404`, `rawResponseBody` |
| `Sep31TransactionCallbackNotSupportedException` | 404 | `putTransactionCallback()`: anchor does not support callbacks OR transaction id is unknown | `message`, `statusCode = 404`, `rawResponseBody` |
| `Sep31InvalidResponseException` | (various) | Any method: server returned malformed JSON, wrong content-type, oversized body, or missing required field | `message`, `statusCode` (default 200), `rawResponseBody` |
| `Sep31UnknownResponseException` | other | Any method: unexpected HTTP status the SDK does not map | `message`, `statusCode`, `responseBody`, `rawResponseBody` |

JWT-shaped tokens are redacted in user-facing exception messages but preserved verbatim in the `rawResponseBody` field so the application can implement custom diagnostics. The redaction prevents accidental JWT logging through plain `.message`.

---

## Verifying Callback Signatures

When the Receiving Anchor delivers status updates to your registered callback URL, each `POST` carries a `Signature` (or legacy `X-Stellar-Signature`) header signed with the anchor's `SIGNING_KEY` from its stellar.toml. The shared `CallbackSignatureVerifier` covers both SEP-12 and SEP-31 webhooks.

```kotlin
// request: from the previous steps of this flow
import com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier
import com.soneso.stellar.sdk.sep.sep01.StellarToml

// Resolve the anchor's SIGNING_KEY once, then cache the verifier per registered URL.
val toml = StellarToml.fromDomain("receivinganchor.com")
val signingKey = toml.generalInformation.signingKey
    ?: error("Anchor stellar.toml missing SIGNING_KEY")

val verifier = CallbackSignatureVerifier(
    signingKey = signingKey,
    registeredCallbackUrl = "https://myanchor.com/callbacks/sep31",
    // freshnessSeconds defaults to 120 to match the spec recommendation;
    // raise only to absorb test-environment clock skew (max 600).
)

// In your HTTP handler:
val result = verifier.verify(
    signatureHeader = request.header("Signature"),
    xStellarSignatureHeader = request.header("X-Stellar-Signature"),
    body = request.bodyAsText(),
)

when (result) {
    CallbackSignatureVerifier.Result.Valid -> {
        // Authentic; process the webhook payload.
    }
    CallbackSignatureVerifier.Result.MissingHeader -> {
        // Neither header was present.
    }
    CallbackSignatureVerifier.Result.MalformedHeader -> {
        // Header shape was wrong or base64/Ed25519 layer rejected the signature bytes.
    }
    is CallbackSignatureVerifier.Result.Stale -> {
        // Timestamp outside freshness window.
        // result.ageSeconds is signed: positive = past, negative = future-dated.
        // Both are rejected; the sign exists for logging only.
    }
    CallbackSignatureVerifier.Result.SignatureMismatch -> {
        // Header was well-formed but cryptographic verification failed.
    }
}
```

The verifier:
- Pins the canonical host from the registered callback URL (port stripped) so a forwarded `Host` header cannot redirect signature scope.
- Enforces HTTPS for the registered URL, with HTTP allowed only for loopback authorities (development).
- Applies a two-sided freshness window (`|now - signedTimestamp| <= freshnessSeconds`) to defend against future-dated forgery and replay equally.

---

## Common Pitfalls

**Memo is the payment routing key. Use the exact value from the anchor:**

```kotlin
val account = Account("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", 1L)
// WRONG: signing without a memo, or guessing
val tx = TransactionBuilder(sourceAccount = account, network = Network.TESTNET)
    .addOperation(payment)
    // missing .addMemo(...) — anchor cannot match the payment to your transaction
    .setTimeout(120)
    .build()

// CORRECT: construct Memo from the anchor-provided memoType + memo value
val memoValue = response.stellarMemo ?: error("Anchor did not provide a memo")
val memoObj = when (response.stellarMemoType) {
    "id" -> MemoId(memoValue.toULong())          // MemoId takes ULong, not Long
    "text" -> MemoText(memoValue)
    "hash" -> MemoHash(Base64.decode(memoValue)) // memo is base64-encoded; MemoHash(String) parses hex
    else -> error("Unknown memo type: ${response.stellarMemoType}")
}
    .addOperation(payment)
    .addMemo(memoObj)  // required — do not omit
    .setTimeout(120)
    .build()
```

**`stellarAccountId` may be null after POST. Always check before sending payment:**

```kotlin
// response: from the previous steps of this flow
// WRONG: sending immediately without checking
val destination = response.stellarAccountId!!  // crashes when anchor delays the instruction

// CORRECT: poll GET /transactions/:id until status == "pending_sender"
if (response.stellarAccountId == null) {
    // see "Polling for payment instructions" above
}
```

**`postTransactions` is not retried by the SDK:**

The anchor's `POST /transactions` is not idempotent at the protocol level; re-sending the same request after a network blip can create duplicate transaction IDs. Wrap calls in your own retry layer only after you've designed deduplication (e.g., use a client-side request UUID and store the returned transaction ID before any retry).

**The Stellar payment source account does NOT need to match the SEP-10 account:**

The Receiving Anchor matches payments by memo, not by source account. The SEP-10-authenticated account is used only to authenticate API calls. The Stellar payment can come from any account that holds the asset.

**`Sep31CustomerInfoNeededException.type` is the SEP-12 type, not a customer ID:**

```kotlin
val jwt = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val kycService = KYCService.fromDomain("testanchor.stellar.org")
// WRONG: passing e.type as a customer ID
val senderId = e.type  // this is a type key like "sep31-sender", not an ID

// CORRECT: pass e.type as the SEP-12 PutCustomerInfoRequest.type, then register and use the returned ID
val req = PutCustomerInfoRequest(
    jwt = jwt,
    type = e.type,
    kycFields = StandardKYCFields(naturalPersonKYCFields = NaturalPersonKYCFields(/* … */)),
)
val senderId = kycService.putCustomerInfo(req).id
```

**`patchTransaction` wraps the fields map automatically. Don't pre-wrap:**

```kotlin
val jwt = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
// id, jwt, service: from the previous steps of this flow
// WRONG: wrapping the fields map yourself (the SDK wraps it in {"fields": ...} too — double wrapping)
@Suppress("DEPRECATION")
service.patchTransaction(
    id = id,
    fields = mapOf("fields" to mapOf("transaction" to mapOf("xxx" to "yyy"))),
    jwt = jwt,
)
// produces {"fields": {"fields": {"transaction": {"xxx": "yyy"}}}}

// CORRECT: pass the inner map directly — the SDK adds the "fields" wrapper
@Suppress("DEPRECATION")
service.patchTransaction(
    id = id,
    fields = mapOf("transaction" to mapOf("xxx" to "yyy")),
    jwt = jwt,
)
// produces {"fields": {"transaction": {"xxx": "yyy"}}}
```

**Quote expiration: send the Stellar payment before the SEP-38 quote expires:**

If you obtained a `quoteId` via SEP-38, the anchor will reject (or recompute against the current rate) once `expiresAt` passes. Submit the Stellar payment immediately after `POST /transactions` returns; do not wait on additional UI confirmations after the quote ticks past expiry.

---

## Related SEPs

- [SEP-01](sep-01.md): stellar.toml discovery (provides `DIRECT_PAYMENT_SERVER` consumed by `fromDomain()`)
- [SEP-10](sep-10.md): Web Authentication (provides the JWT required for all SEP-31 requests)
- [SEP-12](sep-12.md): KYC API (register sender and receiver before initiating transactions)
- [SEP-38](sep-38.md): Anchor RFQ API (get firm exchange rate quotes for `quoteId`)
