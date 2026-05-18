# SEP-31: Cross-Border Payments

## Overview

SEP-31 defines an API a Sending Anchor uses to deliver a cross-border payment through a Receiving Anchor. The Sending Anchor authenticates with SEP-10, discovers the Receiving Anchor's supported assets through `GET /info`, optionally collects SEP-12 KYC and a SEP-38 firm quote, then `POST /transactions` to obtain the on-chain Stellar account, memo, and memo type that route the payment to a single transaction record on the Receiving Anchor.

This SDK implements the **Sending Anchor** side of the protocol — that is, the client of the Receiving Anchor's `DIRECT_PAYMENT_SERVER`. There is no Receiving Anchor server scaffolding in this SDK.

**Use Cases**:
- Build a wallet that initiates remittance payments through a Receiving Anchor
- Integrate a Sending Anchor backend that converts incoming user funds into Stellar payments routed to a partner anchor
- Track lifecycle status (including refunds) of cross-border transactions originated through SEP-31

## Quick Example

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import com.soneso.stellar.sdk.sep.sep31.Sep31Service

suspend fun sendingAnchorQuickExample() {
    // 1. Discover DIRECT_PAYMENT_SERVER from the Receiving Anchor's stellar.toml.
    val sep31 = Sep31Service.fromDomain("anchor.example.org")

    // 2. Acquire a SEP-10 JWT for the authenticated Sending Anchor account.
    //    See docs/sep/sep-10.md for the WebAuth.jwtToken flow that yields this string.
    val jwt = "eyJ..."

    // 3. Discover supported assets and limits.
    val info = sep31.info(jwt = jwt)
    val usdc = info.receiveAssets["USDC"]
        ?: error("Receiving Anchor does not accept USDC")
    println("USDC accepted: min=${usdc.minAmount} max=${usdc.maxAmount}")

    // 4. Initiate the transaction. fundingMethod is required by SEP-31 v3.1.0 and
    //    must match a value advertised in usdc.fundingMethods. senderId / receiverId
    //    are SEP-12 customer ids collected from PUT /customer prior to this call
    //    when the anchor requires KYC.
    val request = Sep31PostTransactionsRequest(
        amount = 100.0,
        assetCode = "USDC",
        fundingMethod = "SWIFT",
        senderId = "11111111-1111-1111-1111-111111111111",
        receiverId = "22222222-2222-2222-2222-222222222222",
    )
    val post = sep31.postTransactions(request, jwt)

    // 5. Send the Stellar payment with the exact memo returned by the anchor.
    //    The Receiving Anchor matches incoming payments to the transaction record
    //    using only this memo. Sending the wrong memo routes funds elsewhere.
    println("Pay ${request.amount} ${request.assetCode} to ${post.stellarAccountId}")
    println("Memo: ${post.stellarMemo} (type=${post.stellarMemoType})")

    // 6. Poll for status.
    val tx = sep31.getTransaction(post.id, jwt)
    println("Transaction status: ${tx.status}")
}
```

## Creating the service

The SDK exposes two construction paths. Prefer [fromDomain] when the Receiving Anchor publishes a stellar.toml; fall back to the direct constructor for closed integrations.

### fromDomain (preferred)

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service

suspend fun createService() {
    // Reads DIRECT_PAYMENT_SERVER from https://anchor.example.org/.well-known/stellar.toml
    // and constructs an Sep31Service against that URL. HTTPS is enforced.
    val sep31 = Sep31Service.fromDomain("anchor.example.org")
}
```

### Direct URL

```kotlin
suspend fun createServiceDirect() {
    // Use when DIRECT_PAYMENT_SERVER is known out-of-band and TOML discovery is unwanted.
    // Constructor throws IllegalArgumentException for non-HTTPS URLs, except http://
    // against the loopback authorities localhost, 127.0.0.1, [::1] (each optionally
    // with a port). See "HTTP client defaults" below for the rationale.
    val sep31 = Sep31Service("https://anchor.example.org/sep31")
}
```

Both forms accept an optional `httpClient: HttpClient` and `httpRequestHeaders: Map<String, String>` for advanced setups (custom TLS pinning, additional headers). A caller-supplied client is used as-is and is never closed by the service.

### HTTPS enforcement and the loopback carve-out

The SDK requires HTTPS at four boundaries: the constructor `serviceUrl`, the `stellar.toml` fetch performed by `fromDomain`, the resolved `DIRECT_PAYMENT_SERVER` from that TOML, and the `callbackUrl` passed to `putTransactionCallback`. Every other host must use HTTPS. As a development convenience, `http://` is accepted only against the three IETF loopback authorities — `localhost`, `127.0.0.1`, and `[::1]`, each optionally with a `:port`. This lets you point the service at a local Anchor Platform instance (typically `http://localhost:8080`) without standing up a TLS-terminating proxy.

```kotlin
// Production deployment — TOML fetched over https, service URL over https.
val anchor = Sep31Service.fromDomain("anchor.example.org")

// Local development — TOML fetched over http://localhost:8080/.well-known/stellar.toml,
// resolved DIRECT_PAYMENT_SERVER may also use http://localhost.
val localFromToml = Sep31Service.fromDomain("localhost:8080")

// Local development without TOML discovery — direct construction.
val localDirect = Sep31Service("http://localhost:8080/sep31")
```

Lookalike authorities are rejected at every layer: `localhost.evil.com`, `user@localhost`, `127.0.0.1.evil.com`, and any non-loopback host all raise `IllegalArgumentException` (or `Sep31ConfigurationException` for the `fromDomain` path). Host comparison is case-insensitive (`LOCALHOST` is accepted); scheme comparison is case-sensitive (`HTTP://localhost` is rejected).

### HTTP client defaults

When no `httpClient` is supplied, the SDK builds an internal Ktor client with these defaults:

- **Connect timeout: 10 s, request timeout: 30 s.** Anchor calls that hang past these values surface as `kotlinx.coroutines.TimeoutCancellationException` (or the platform Ktor equivalent) rather than blocking the calling coroutine indefinitely.
- **Redirects are not followed (`followRedirects = false`).** A 3xx response from the anchor surfaces as `Sep31UnknownResponseException` instead of being silently chased. The SEP-31 service URL comes from `stellar.toml`'s `DIRECT_PAYMENT_SERVER` and is expected to be stable; a redirect indicates either a misconfiguration that the anchor operator should fix, or a cross-origin destination that could leak the JWT bearer token (Ktor's default redirect handler re-attaches `Authorization` headers).
- **Content-Type is enforced.** Responses with anything other than `application/json` or `application/problem+json` surface as `Sep31InvalidResponseException`. This catches anchors that return HTML error pages, plaintext CDN errors, or login portals.
- **Response bodies are size-capped.** `info` is capped at 2 MB; transaction-shaped responses are capped at 256 KB. Oversized bodies surface as `Sep31InvalidResponseException` before being loaded into memory.

The SEP-31 spec does not mandate any of these defaults; they are SDK policy aimed at failing fast on misbehaving anchors. Override them by passing a configured `HttpClient`:

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createServiceWithCustomClient(): Sep31Service {
    val customClient = HttpClient {
        // Longer request timeout for anchors with slow KYC backends.
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 120_000
        }
        // Required if your client will parse SEP-31 JSON responses.
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        // Application policy decision: follow same-origin redirects, for example.
        followRedirects = false
    }
    return Sep31Service("https://anchor.example.org", httpClient = customClient)
}
```

The SDK still enforces HTTPS, path-segment validation, content-type allow-listing, response-body size caps, and JWT redaction in error messages regardless of which `HttpClient` is passed — those rules live in the service layer, not the HTTP client.

## Getting anchor information

`GET /info` returns the assets the Receiving Anchor accepts, their per-asset limits, SEP-12 customer types, SEP-38 quote requirements, and funding methods.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service

suspend fun fetchInfo() {
    val sep31 = Sep31Service.fromDomain("anchor.example.org")
    val jwt = "eyJ..." // SEP-10 token; required by the spec and the SDK type signature.

    // Request English strings.
    val info = sep31.info(jwt = jwt, lang = "en")

    for ((code, asset) in info.receiveAssets) {
        println("$code: min=${asset.minAmount} max=${asset.maxAmount}")
        println("  fee: fixed=${asset.feeFixed} percent=${asset.feePercent}")
        println("  sender SEP-12 types: ${asset.sep12Info.senderTypes.keys}")
        println("  receiver SEP-12 types: ${asset.sep12Info.receiverTypes.keys}")
    }
}
```

Per SEP-31 §"Authentication", a SEP-10 JWT is required on every endpoint, including `GET /info`. The SDK enforces this at the type level — `info(jwt: String, lang: String? = null)` does not accept a `null` token.

### Inspecting funding methods and quote requirements

```kotlin
suspend fun inspectAssetCapabilities() {
    val sep31 = Sep31Service.fromDomain("anchor.example.org")
    val info = sep31.info(jwt = "eyJ...")
    val usdc = info.receiveAssets["USDC"] ?: return

    // Funding methods are anchor-defined strings (for example "SEPA", "SWIFT", "ACH").
    // The SDK does not validate them against an allow-list; the spec is expected
    // to introduce new values over time.
    usdc.fundingMethods?.forEach { method ->
        println("Funding method: $method")
    }

    // Both flags are nullable. quotesRequired == true means a SEP-38 quote_id is
    // mandatory on POST /transactions for off-chain delivery; quotesSupported == true
    // means the anchor accepts a quote_id but does not require it.
    when {
        usdc.quotesRequired == true -> println("SEP-38 quote_id is required")
        usdc.quotesSupported == true -> println("SEP-38 quote_id is optional")
        else -> println("No SEP-38 quotes for this asset")
    }
}
```

## Full payment flow

The Sending Anchor flow has many spec-defined steps. The numbered list below shows the SDK-side path; the spec's "Detailed Sending Anchor Flow" enumerates additional out-of-band steps (deposit collection from the user, regulatory checks, on-chain submission).

See [SEP-0031 §Detailed Sending Anchor Flow](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#detailed-sending-anchor-flow) for the full sequence.

```kotlin
import com.soneso.stellar.sdk.Asset
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MemoHash
import com.soneso.stellar.sdk.MemoId
import com.soneso.stellar.sdk.MemoText
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.PaymentOperation
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
suspend fun fullPaymentFlow() {
    // 1. Initialize the SEP-31 service against the Receiving Anchor's domain.
    val sep31 = Sep31Service.fromDomain("anchor.example.org")

    // 2. Authenticate the Sending Anchor account via SEP-10. The JWT acquisition
    //    pattern is shown in docs/sep/sep-10.md.
    val jwt = "eyJ..."

    // 3. Discover the asset configuration.
    val info = sep31.info(jwt = jwt)
    val usdc = info.receiveAssets["USDC"]
        ?: error("Receiving Anchor does not accept USDC")

    // 4. Collect SEP-12 KYC for both customers via PUT /customer. The pattern is
    //    shown in docs/sep/sep-12.md. The result is two opaque customer ids:
    val senderId = "11111111-1111-1111-1111-111111111111"
    val receiverId = "22222222-2222-2222-2222-222222222222"

    // 5. If the anchor requires SEP-38 quotes, request a firm quote first. The
    //    full SEP-38 flow is in docs/sep/sep-38.md. quoteId is the resulting id.
    val quoteId: String? =
        if (usdc.quotesRequired == true) "33333333-3333-3333-3333-333333333333" else null

    // 6. Initiate the transaction. fundingMethod is required by SEP-31 v3.1.0 and
    //    must match a value advertised in usdc.fundingMethods. Refund memo and the
    //    SEP-38 quoteId remain optional.
    val request = Sep31PostTransactionsRequest(
        amount = 100.0,
        assetCode = "USDC",
        fundingMethod = "SWIFT",
        senderId = senderId,
        receiverId = receiverId,
        quoteId = quoteId,
        refundMemo = "REFUND-INV-42",
        refundMemoType = "text",
    )
    val post = sep31.postTransactions(request, jwt)

    // 7. Build the Stellar payment using the exact memo returned by the anchor.
    val sendingKeyPair = KeyPair.fromSecretSeed(
        "SCH27VUZZ6UAKB67BDNF6FA42YMBMQCBKXWGMFD5TZ6S5ZZCZFLRXKHS",
    )
    val horizon = HorizonServer("https://horizon-testnet.stellar.org")
    val sourceAccount = horizon.loadAccount(sendingKeyPair.getAccountId())

    val destination = post.stellarAccountId
        ?: error("Anchor has not issued payment instructions yet; poll until pending_sender")
    val memo = when (post.stellarMemoType) {
        "id" -> MemoId(post.stellarMemo!!.toULong())
        "text" -> MemoText(post.stellarMemo!!)
        // Per SEP-31, stellar_memo for memo_type "hash" is base64-encoded
        // (32 raw bytes after decoding). MemoHash(String) parses hex, so the
        // payload must be base64-decoded into ByteArray first.
        "hash" -> MemoHash(Base64.decode(post.stellarMemo!!))
        else -> error("Unsupported memo type: ${post.stellarMemoType}")
    }

    val usdcAsset = Asset.createNonNativeAsset(
        "USDC",
        "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    )
    val payment = PaymentOperation(
        destination = destination,
        asset = usdcAsset,
        amount = "100.00",
    )
    val tx = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(payment)
        .addMemo(memo)
        .setBaseFee(100)
        .setTimeout(180)
        .build()
    tx.sign(sendingKeyPair)
    horizon.submitTransaction(tx.toEnvelopeXdrBase64())

    // 8. Poll for status.
    val current = sep31.getTransaction(post.id, jwt)
    println("Transaction status: ${current.status}")
}
```

### Registering sender and receiver via SEP-12

Step 4 above accepts `senderId` and `receiverId` as opaque strings. Use `KYCService.putCustomerInfo` to produce them. The `type` argument must match a key exposed by `Sep31ReceiveAssetInfo.sep12Info.senderTypes` or `receiverTypes` — the anchor declares which KYC type each role must use.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep12.KYCService
import com.soneso.stellar.sdk.sep.sep12.PutCustomerInfoRequest

suspend fun registerCustomers(jwt: String): Pair<String, String> {
    val kyc = KYCService.fromDomain("anchor.example.org")

    val sender = kyc.putCustomerInfo(
        PutCustomerInfoRequest(
            jwt = jwt,
            type = "sep31-sender",
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Alice",
                    lastName = "Doe",
                    emailAddress = "alice@example.com",
                ),
            ),
        ),
    )
    val receiver = kyc.putCustomerInfo(
        PutCustomerInfoRequest(
            jwt = jwt,
            type = "sep31-receiver",
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Bob",
                    lastName = "Smith",
                    emailAddress = "bob@example.com",
                ),
            ),
        ),
    )
    return sender.id to receiver.id
}
```

See [SEP-12: KYC API](sep-12.md) for the full request shape, document uploads, organization KYC, and recovery flows.

### Acquiring a firm quote via SEP-38

When `Sep31ReceiveAssetInfo.quotesRequired` is `true`, the Receiving Anchor requires a SEP-38 quote id. The `context` argument must be `"sep31"` so the anchor knows the quote will back a SEP-31 transaction. Pass exactly one of `sellAmount` / `buyAmount`.

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService
import com.soneso.stellar.sdk.sep.sep38.Sep38QuoteRequest

suspend fun acquireQuote(jwt: String): String {
    val quotes = QuoteService.fromDomain("anchor.example.org")

    val quote = quotes.postQuote(
        Sep38QuoteRequest(
            context = "sep31",
            sellAsset = "iso4217:USD",
            buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sellAmount = "100.00",
        ),
        jwt,
    )
    // The quote is binding only until quote.expiresAt; submit postTransactions
    // and the on-chain payment before that deadline.
    return quote.id
}
```

See [SEP-38: Anchor RFQ API](sep-38.md) for the full request shape, indicative prices, and asset-identifier conventions.

## Tracking transaction status

SEP-31 defines ten lifecycle statuses. [Sep31TransactionResponse.status] is a raw `String`; use [Sep31TransactionStatus.fromString] for typed dispatch.

The ten statuses are: `pending_sender`, `pending_stellar`, `pending_customer_info_update`, `pending_transaction_info_update`, `pending_receiver`, `pending_external`, `completed`, `refunded`, `expired`, `error`.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionStatus

suspend fun handleStatus(sep31: Sep31Service, txId: String, jwt: String): Sep31TransactionResponse {
    val response = sep31.getTransaction(txId, jwt)

    when (Sep31TransactionStatus.fromString(response.status)) {
        Sep31TransactionStatus.PENDING_SENDER ->
            println("Submit Stellar payment to ${response.stellarAccountId} with memo ${response.stellarMemo}")
        Sep31TransactionStatus.PENDING_STELLAR ->
            println("Stellar payment seen; waiting for on-network confirmation")
        Sep31TransactionStatus.PENDING_CUSTOMER_INFO_UPDATE ->
            println("KYC update required; see the Handling KYC update requests section below")
        Sep31TransactionStatus.PENDING_TRANSACTION_INFO_UPDATE ->
            println("Legacy per-transaction fields update required; see required_info_updates")
        Sep31TransactionStatus.PENDING_RECEIVER ->
            println("Receiving Anchor is processing the off-chain delivery")
        Sep31TransactionStatus.PENDING_EXTERNAL ->
            println("Off-chain payment submitted; awaiting external confirmation")
        Sep31TransactionStatus.COMPLETED ->
            println("Delivered to Receiving Client")
        Sep31TransactionStatus.REFUNDED ->
            println("Funds refunded; see refunds field for details")
        Sep31TransactionStatus.EXPIRED ->
            println("Transaction expired (quote expiry, payment window timeout, etc.)")
        Sep31TransactionStatus.ERROR ->
            println("Error: ${response.statusMessage}")
        null ->
            println("Unknown status: ${response.status}") // forward-compatible
    }
    return response
}
```

### Amount-formula identities

The SEP-31 spec defines four equality identities that hold for every completed or partially-refunded transaction. The Sending Anchor must compute these locally rather than trust anchor-supplied aggregates.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionResponse
import kotlin.math.abs

// Example uses Double for compactness; production code should plug in a
// precision-preserving decimal type (java.math.BigDecimal on JVM, a
// platform-specific decimal elsewhere). SEP-31 amount fields are String?
// precisely so the application picks the arithmetic type.
private const val TOLERANCE = 1e-9

fun verifyAmountIdentities(
    tx: Sep31TransactionResponse,
    quoteSellAmount: String?,
    quoteBuyAmount: String?,
) {
    // Spec identity 1: amount_out = amount_in - amount_fee - refunds.amount_refunded - refunds.amount_fee
    val amountIn = tx.amountIn!!.toDouble()
    val amountOut = tx.amountOut!!.toDouble()
    val totalFee = tx.feeDetails?.total?.toDouble()
        ?: error("fee_details required to recompute amount_out")
    val refundedAmount = tx.refunds?.amountRefunded?.toDouble() ?: 0.0
    val refundFee = tx.refunds?.amountFee?.toDouble() ?: 0.0
    val recomputedOut = amountIn - totalFee - refundedAmount - refundFee
    require(abs(recomputedOut - amountOut) < TOLERANCE) {
        "amount_out identity failed: expected=$amountOut recomputed=$recomputedOut"
    }

    // Spec identity 2: refunds.amount_refunded = sum(refunds.payments[].amount)
    tx.refunds?.let { refunds ->
        val sumAmounts = refunds.payments.sumOf { it.amount.toDouble() }
        require(abs(sumAmounts - refunds.amountRefunded.toDouble()) < TOLERANCE) {
            "refunds.amount_refunded identity failed"
        }

        // Spec identity 3: refunds.amount_fee = sum(refunds.payments[].fee)
        val sumFees = refunds.payments.sumOf { it.fee.toDouble() }
        require(abs(sumFees - refunds.amountFee.toDouble()) < TOLERANCE) {
            "refunds.amount_fee identity failed"
        }
    }

    // Spec identity 4 (only when quote_id is used):
    //   amount_in == quote.sell_amount and amount_out == quote.buy_amount
    if (tx.quoteId != null) {
        require(quoteSellAmount != null && tx.amountIn == quoteSellAmount) {
            "amount_in must equal quote.sell_amount for quoted transactions"
        }
        require(quoteBuyAmount != null && tx.amountOut == quoteBuyAmount) {
            "amount_out must equal quote.buy_amount for quoted transactions"
        }
    }
}
```

### Fee breakdown

When `fee_details.details` is non-null, the line-item amounts sum to `fee_details.total`.

```kotlin
suspend fun printFeeBreakdown(tx: Sep31TransactionResponse) {
    val fees = tx.feeDetails ?: return
    println("Total fee: ${fees.total} ${fees.asset}")
    for (line in fees.details.orEmpty()) {
        val description = line.description ?: "no description"
        println("  ${line.name}: ${line.amount} ($description)")
    }
}
```

### Refund handling

When the `refunds` aggregate is present, walk the `payments` list for individual on-chain refund transactions.

```kotlin
suspend fun printRefunds(tx: Sep31TransactionResponse) {
    val refunds = tx.refunds ?: return
    println("Total refunded: ${refunds.amountRefunded} (refund fees: ${refunds.amountFee})")
    for (payment in refunds.payments) {
        // payment.id is the Stellar transaction hash of the refund payment.
        println("  refund ${payment.id}: amount=${payment.amount} fee=${payment.fee}")
    }
}
```

### Polling for status changes

Sending Anchors that have not registered a callback can poll `getTransaction`. The anchor may advertise an estimated time to the next status transition via `Sep31TransactionResponse.statusEta` (seconds). When `statusEta` is present, prefer it over a fixed interval; otherwise back off exponentially with a ceiling to avoid hot-looping while a long-running off-chain transfer settles.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionResponse
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionStatus
import kotlinx.coroutines.delay

private val terminalStatuses = setOf(
    Sep31TransactionStatus.COMPLETED,
    Sep31TransactionStatus.REFUNDED,
    Sep31TransactionStatus.EXPIRED,
    Sep31TransactionStatus.ERROR,
)

suspend fun pollUntilTerminal(
    sep31: Sep31Service,
    txId: String,
    jwt: String,
): Sep31TransactionResponse {
    var backoffMs = 2_000L
    val ceilingMs = 60_000L
    while (true) {
        val tx = sep31.getTransaction(txId, jwt)
        if (Sep31TransactionStatus.fromString(tx.status) in terminalStatuses) {
            return tx
        }
        // statusEta is the anchor's hint in seconds. Clamp the lower bound so a
        // hostile or buggy anchor cannot drive the client into a busy loop.
        val waitMs = tx.statusEta?.let { (it * 1000L).coerceAtLeast(1_000L) } ?: backoffMs
        delay(waitMs)
        backoffMs = (backoffMs * 2).coerceAtMost(ceilingMs)
    }
}
```

### Handling KYC update requests

When the transaction status is `pending_customer_info_update`, the Receiving Anchor needs additional or corrected KYC fields from one or both customers. Call `KYCService.getCustomerInfo` with the transaction id to discover which fields are needed, then `putCustomerInfo` to submit them.

```kotlin
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
    // Passing transactionId scopes the response to the fields the anchor needs
    // for this specific transaction, not the customer's global KYC state.
    val needs = kyc.getCustomerInfo(
        GetCustomerInfoRequest(jwt = jwt, id = customerId, transactionId = transactionId),
    )
    if (needs.status != CustomerStatus.NEEDS_INFO) return

    // Collect the missing values from the user. The example uses a hardcoded
    // address; production code reads from the application's UI or storage.
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

After the SEP-12 round-trip completes, the Receiving Anchor re-evaluates the transaction and advances its status. Resume polling (or wait for a callback) to observe the next state. See [SEP-12: KYC API](sep-12.md) for the field catalog, document uploads, and verification codes.

## Transaction status callbacks

A Sending Anchor can register an HTTPS callback URL so the Receiving Anchor pushes status transitions instead of being polled.

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionCallbackNotSupportedException

suspend fun registerCallback() {
    val sep31 = Sep31Service.fromDomain("anchor.example.org")
    val jwt = "eyJ..."
    val txId = "11111111-1111-1111-1111-111111111111"
    val callbackUrl = "https://wallet.example.org/sep31-callback"

    try {
        sep31.putTransactionCallback(id = txId, callbackUrl = callbackUrl, jwt = jwt)
        println("Callback registered for $txId")
    } catch (e: Sep31TransactionCallbackNotSupportedException) {
        // Anchor returns HTTP 404 to signal it does not support callbacks.
        // Fall back to polling getTransaction on a timer.
        println("Anchor does not support callbacks; falling back to polling.")
    }
}
```

### Verifying callback signatures

The SDK exposes [putTransactionCallback] for registration and [CallbackSignatureVerifier] (in `com.soneso.stellar.sdk.sep.common`) for verifying incoming callback signatures.

> **Security callout — common implementation mistakes**
>
> 1. **Not verifying the signature at all.** An unsigned callback can be forged by any caller that knows the URL.
> 2. **Verifying the signature but not the timestamp.** The verifier handles freshness for you; do not override `freshnessSeconds` above 120 in production.
> 3. **Incorrect canonicalization.** The verifier assembles the canonical payload internally.
> 4. **Wrong `SIGNING_KEY` lookup.** The Receiving Anchor's stellar.toml `SIGNING_KEY` is the only correct verification key. Do not reuse the JWT-issuer key from SEP-10 — it may differ from `SIGNING_KEY`. You pass the key to the verifier's constructor.
> 5. **Treating callbacks as non-idempotent.** The same `(transaction_id, new_status)` may legitimately be delivered more than once when the Receiving Anchor retries. Consumer state machines must dedupe by `(transaction_id, status)` so duplicate state transitions and duplicate customer notifications are avoided. The verifier returns `Valid` for legitimate retries by design.

The Receiving Anchor sends the signature in either the `Signature` HTTP header (preferred) or the deprecated `X-Stellar-Signature` header. Pass both header values to the verifier; it prefers `Signature` when both are present.

```kotlin
import com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier

// `signingKey` is the Receiving Anchor's SIGNING_KEY from its stellar.toml.
// Fetch once via `StellarToml.fromDomain(anchorDomain).generalInformation.signingKey`
// and cache for the lifetime of the anchor connection — TOML rarely changes and
// the fetch would otherwise add latency to every callback.
suspend fun verifyCallback(
    signingKey: String,
    signatureHeader: String?,
    xStellarSignatureHeader: String?,
    body: String,
): Boolean {
    val verifier = CallbackSignatureVerifier(
        signingKey = signingKey,
        registeredCallbackUrl = "https://wallet.example.org/sep31-callback",
    )

    return when (val result = verifier.verify(signatureHeader, xStellarSignatureHeader, body)) {
        CallbackSignatureVerifier.Result.Valid -> true
        is CallbackSignatureVerifier.Result.Stale -> {
            println("Callback stale by ${result.ageSeconds}s")
            false
        }
        CallbackSignatureVerifier.Result.SignatureMismatch,
        CallbackSignatureVerifier.Result.MalformedHeader,
        CallbackSignatureVerifier.Result.MissingHeader -> false
    }
}
```

## Error handling

Every public method on [Sep31Service] maps anchor responses to a specific subclass of [Sep31Exception]. The try/catch matrix below covers every SEP-31 exception type:

```kotlin
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ConfigurationException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31CustomerInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31Exception
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ForbiddenException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionCallbackNotSupportedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionNotFoundException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnauthorizedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnknownResponseException

suspend fun errorMatrix() {
    val request = Sep31PostTransactionsRequest(
        amount = 100.0,
        assetCode = "USDC",
        fundingMethod = "SWIFT",
    )
    val jwt = "eyJ..."

    try {
        // fromDomain throws Sep31ConfigurationException up front when the anchor TOML
        // is missing, malformed, or advertises a non-HTTPS DIRECT_PAYMENT_SERVER.
        val sep31 = Sep31Service.fromDomain("anchor.example.org")

        sep31.postTransactions(request, jwt)
        sep31.getTransaction("11111111-1111-1111-1111-111111111111", jwt)
        sep31.putTransactionCallback(
            id = "11111111-1111-1111-1111-111111111111",
            callbackUrl = "https://wallet.example.org/cb",
            jwt = jwt,
        )
    } catch (e: Sep31ConfigurationException) {
        // No DIRECT_PAYMENT_SERVER, bad domain, non-HTTPS endpoint, or TOML fetch failed.
        println("SEP-31 configuration: ${e.message}")
    } catch (e: Sep31CustomerInfoNeededException) {
        // HTTP 400 with error="customer_info_needed" on POST /transactions.
        // Submit SEP-12 PUT /customer for type=${e.type} and retry.
        println("KYC required: type=${e.type}")
    } catch (e: Sep31TransactionInfoNeededException) {
        // HTTP 400 with error="transaction_info_needed" on POST /transactions.
        @Suppress("DEPRECATION")
        println("Legacy fields required: ${e.fields?.keys}")
    } catch (e: Sep31BadRequestException) {
        // Generic HTTP 400 (validation, out-of-range amount, unsupported asset).
        println("Bad request (${e.statusCode}): ${e.message}")
    } catch (e: Sep31UnauthorizedException) {
        // HTTP 401 — JWT missing, expired, or malformed.
        println("Unauthorized (${e.statusCode}): ${e.message}")
    } catch (e: Sep31ForbiddenException) {
        // HTTP 403 — spec-mandated authentication failure path.
        println("Forbidden (${e.statusCode}): ${e.message}")
    } catch (e: Sep31TransactionCallbackNotSupportedException) {
        // HTTP 404 on PUT /transactions/:id/callback — anchor opts out of callbacks.
        println("Callbacks not supported by this anchor")
    } catch (e: Sep31TransactionNotFoundException) {
        // HTTP 404 on GET/PATCH /transactions/:id — transaction id is unknown.
        println("Transaction not found (${e.statusCode}): ${e.message}")
    } catch (e: Sep31InvalidResponseException) {
        // 2xx with malformed body, unexpected Content-Type, or response over the size cap.
        println("Anchor response malformed: ${e.message}")
    } catch (e: Sep31UnknownResponseException) {
        // Unmapped status code (5xx, 3xx, 429, etc.). Body is captured for diagnostics.
        println("Unexpected HTTP ${e.statusCode}: ${e.responseBody}")
    } catch (e: Sep31Exception) {
        // Catch-all base class; should not be reached if the above arms are exhaustive.
        println("SEP-31 error: ${e.message}")
    }
}
```

### Debugging anchor responses with `rawResponseBody`

`Sep31*Exception.message` (and `Sep31UnknownResponseException.responseBody`) are sanitized: JWT-shaped substrings are replaced with `<redacted-jwt>`, control characters are stripped, and the body is truncated to 1024 chars. This is the only string the SDK exposes that is safe to log in production.

Every exception that surfaces anchor response content also exposes a sibling `rawResponseBody: String?` field that preserves the original body **without** JWT redaction. The field is intended for local debugging — for example, when an anchor returns an error whose meaning depends on the token it rejected. The 1024-char cap and the control-character scrub still apply, so the field is log-injection-safe; only JWT redaction is disabled.

```kotlin
try {
    sep31.postTransactions(request, jwt)
} catch (e: Sep31BadRequestException) {
    // Safe in production: redacted, capped, log-injection-safe.
    log.error("SEP-31 failed: ${e.message}")

    // Debugging only: contains the verbatim JWT if the anchor echoed it.
    // DO NOT log this to Sentry, Datadog, Splunk, or any other shared aggregator.
    if (localDebugMode) {
        e.rawResponseBody?.let { println("Raw anchor body: $it") }
    }
}
```

`rawResponseBody` is `null` only when the SDK had no body to capture for that error path (for example, a content-type rejection that fails before any body bytes are read).

## Deprecated PATCH transaction info

The PATCH endpoint supports the legacy `pending_transaction_info_update` workflow defined by SEP-31 v2.5.0. New integrations register customer KYC via SEP-12 `PUT /customer` and pass `senderId` / `receiverId` on the original [postTransactions] request instead.

```kotlin
suspend fun legacyPatch() {
    val sep31 = Sep31Service.fromDomain("anchor.example.org")
    val jwt = "eyJ..."

    @Suppress("DEPRECATION")
    val updated = sep31.patchTransaction(
        id = "11111111-1111-1111-1111-111111111111",
        fields = mapOf(
            "transaction" to mapOf("receiver_account_number" to "0987654321"),
        ),
        jwt = jwt,
    )
    println("Updated status: ${updated.status}")
}
```

The method is annotated [`@Deprecated`][Deprecated]. The SDK still returns the updated transaction response (the SEP-31 v3.1.0 spec mandates the PATCH response body match `GET /transactions/:id`).

## Transaction statuses

| Status | Meaning |
|--------|---------|
| `pending_sender` | Receiving Anchor awaits the on-chain Stellar payment from the Sending Anchor. |
| `pending_stellar` | Stellar payment submitted; awaiting on-network confirmation. |
| `pending_customer_info_update` | SEP-12 customer KYC must be updated before the transaction can advance. |
| `pending_transaction_info_update` | Legacy per-transaction fields require an update (superseded by SEP-12). |
| `pending_receiver` | Receiving Anchor is processing the off-chain delivery. |
| `pending_external` | Off-chain payment submitted; awaiting external (e.g., bank) confirmation. |
| `completed` | Funds delivered to the Receiving Client. |
| `refunded` | Funds returned to the Sending Anchor; inspect `refunds` for details. |
| `expired` | Transaction abandoned (quote expired, payment window timed out, etc.). |
| `error` | Unspecified terminal error; inspect `statusMessage` for context. |

## SDK classes and exceptions

| Class | Description |
|-------|-------------|
| `Sep31Service` | Client for the Receiving Anchor's `DIRECT_PAYMENT_SERVER`. Methods: `info`, `postTransactions`, `getTransaction`, `putTransactionCallback`, `patchTransaction` (deprecated). |
| `Sep31InfoResponse` | Parsed `GET /info` response. Exposes `receiveAssets: Map<String, Sep31ReceiveAssetInfo>`. |
| `Sep31ReceiveAssetInfo` | Per-asset configuration: limits, fee model, SEP-12 customer types, SEP-38 quote requirements, funding methods. |
| `Sep31Sep12TypesInfo` | SEP-12 sender and receiver customer type maps for one asset. |
| `Sep31PostTransactionsRequest` | Request body for `POST /transactions`. |
| `Sep31PostTransactionsResponse` | Response from `POST /transactions`. Carries `id`, `stellarAccountId`, `stellarMemoType`, `stellarMemo`. |
| `Sep31TransactionResponse` | Full transaction state returned by `GET /transactions/:id` and `PATCH /transactions/:id`. |
| `Sep31TransactionStatus` | Enum of the ten lifecycle statuses with `fromString(value)` for typed dispatch. |
| `Sep31FeeDetails` | Structured fee breakdown: `total`, `asset`, optional `details` line items. |
| `Sep31FeeDetailsDetails` | Single line item in a fee breakdown: `name`, `amount`, optional `description`. |
| `Sep31Refunds` | Aggregate refund details: `amountRefunded`, `amountFee`, `payments`. |
| `Sep31RefundPayment` | One on-chain refund payment: `id` (Stellar tx hash), `amount`, `fee`. |
| `Sep31Exception` | Base class for every SEP-31 error. |
| `Sep31BadRequestException` | HTTP 400 generic. Carries `statusCode = 400`. |
| `Sep31CustomerInfoNeededException` | HTTP 400 with `error="customer_info_needed"`. Exposes `type` and `error`. |
| `Sep31TransactionInfoNeededException` | HTTP 400 with `error="transaction_info_needed"`. Exposes `fields` (primitive leaves) and `error`. Deprecated. |
| `Sep31UnauthorizedException` | HTTP 401. Carries `statusCode = 401`. |
| `Sep31ForbiddenException` | HTTP 403 (spec-mandated auth failure path). Carries `statusCode = 403`. |
| `Sep31TransactionNotFoundException` | HTTP 404 on `GET`/`PATCH /transactions/:id`. Carries `statusCode = 404`. |
| `Sep31TransactionCallbackNotSupportedException` | HTTP 404 on `PUT /transactions/:id/callback`. Carries `statusCode = 404`. |
| `Sep31InvalidResponseException` | 2xx with a malformed body, unexpected Content-Type, or response above the SDK size cap. Carries `statusCode` (default 200). |
| `Sep31UnknownResponseException` | Unmapped HTTP status code. Carries `statusCode` and `responseBody`. |
| `Sep31ConfigurationException` | `fromDomain` configuration failure (bad domain, missing `DIRECT_PAYMENT_SERVER`, non-HTTPS URL, TOML fetch failure). |

## Important notes and related SEPs

> The Sending Anchor application must attach the exact `stellarMemo` returned by `Sep31Service.postTransactions` (or by a subsequent `getTransaction`) to the Stellar payment, using the matching `stellarMemoType`. Per SEP-31 §"Authentication", the Receiving Anchor matches incoming Stellar payments to its transaction record using only the memo. The Stellar source account may differ from the SEP-10 authentication account and must not be relied on for matching. Sending the wrong memo will route the payment to a different transaction (or none), and the funds will not be delivered as expected.

- **Source account independence.** The Stellar account that submits the on-chain payment is not authenticated to the Receiving Anchor by Stellar protocol mechanisms. SEP-10 establishes who the Sending Anchor is at the API level; the memo establishes which transaction record the on-chain payment refunds against.
- **Quote expiration.** When the anchor returns a SEP-38 `quote_id`, the guaranteed rate is only honored if the Stellar payment lands before the quote's `expires_at`. After expiry the anchor may reject the payment, refund it at the spot rate, or mark the transaction `expired`.
- **KYC must precede the transaction.** When the anchor advertises `sep12.sender.types` or `sep12.receiver.types`, register the customers via SEP-12 `PUT /customer` and pass the resulting customer ids as `senderId` / `receiverId` on `postTransactions`. Without valid customer ids the anchor returns HTTP 400 with `error="customer_info_needed"`.
- **HTTPS only (loopback excepted).** Every URL the SDK accepts must use `https://`, except `http://` against the loopback authorities `localhost`, `127.0.0.1`, and `[::1]` (each optionally with a port) for local development. The constructor, `fromDomain` factory, and `putTransactionCallback` all enforce this rule before sending the JWT on the wire.
- **Authentication.** Every endpoint requires a SEP-10 JWT in the `Authorization: Bearer <jwt>` header, including `info()`. The SDK enforces this at the type level — there is no nullable-JWT escape hatch.

**Related SEPs**:
- [SEP-1: Stellar TOML](sep-01.md) — `DIRECT_PAYMENT_SERVER` and `SIGNING_KEY` discovery
- [SEP-10: Stellar Web Authentication](sep-10.md) — JWT acquisition
- [SEP-12: KYC API](sep-12.md) — customer registration prior to `postTransactions`
- [SEP-38: Anchor RFQ API](sep-38.md) — firm quote acquisition for `quoteId`

**Specification**: [SEP-0031: Cross-Border Payments](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep31`

**Last Updated**: 2026-05-16
