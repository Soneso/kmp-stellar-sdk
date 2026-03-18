# SEP-38: Anchor RFQ API

**Purpose:** Get exchange quotes between Stellar assets and off-chain assets for use in SEP-6, SEP-24, and SEP-31 flows.
**Prerequisites:** JWT from SEP-10 required for `postQuote()` and `getQuote()`; optional for `info()`, `prices()`, and `price()`
**Package:** `com.soneso.stellar.sdk.sep.sep38`
**Spec:** SEP-0038

## Table of Contents

1. [Creating the Service](#1-creating-the-service)
2. [Asset Identification Format](#2-asset-identification-format)
3. [GET /info -- Available Assets](#3-get-info----available-assets)
4. [GET /prices -- Indicative Prices (Multi-Asset)](#4-get-prices----indicative-prices-multi-asset)
5. [GET /price -- Indicative Price (Single Pair)](#5-get-price----indicative-price-single-pair)
6. [POST /quote -- Request a Firm Quote](#6-post-quote----request-a-firm-quote)
7. [GET /quote/:id -- Retrieve a Firm Quote](#7-get-quoteid----retrieve-a-firm-quote)
8. [Response Objects Reference](#8-response-objects-reference)
9. [Error Handling](#9-error-handling)
10. [Price Formulas](#10-price-formulas)
11. [Common Pitfalls](#11-common-pitfalls)

---

## 1. Creating the Service

### From domain (recommended)

`QuoteService.fromDomain()` is a `suspend` function in the companion object. It fetches the domain's `stellar.toml`, reads the `ANCHOR_QUOTE_SERVER` field, and returns a configured service instance. Pass only the bare domain -- no protocol prefix.

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

// Fetches stellar.toml from anchor.example.com, reads ANCHOR_QUOTE_SERVER
val quoteService = QuoteService.fromDomain("anchor.example.com")
```

Throws `IllegalStateException` if the stellar.toml fetch fails or `ANCHOR_QUOTE_SERVER` is absent.

Signature:
```kotlin
suspend fun fromDomain(
    domain: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
): QuoteService
```

### With a direct URL

Use the constructor directly when you already know the quote server address. This is NOT a suspend function.

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

// WRONG: QuoteService.fromUrl(...) -- there is no fromUrl factory
// CORRECT: use the constructor directly
val quoteService = QuoteService("https://anchor.example.com/sep38")
```

Constructor signature:
```kotlin
class QuoteService(
    serviceAddress: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
)
```

### With custom HTTP client or headers

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService
import io.ktor.client.*

val quoteService = QuoteService(
    serviceAddress = "https://anchor.example.com/sep38",
    httpClient = HttpClient { /* configure */ },
    httpRequestHeaders = mapOf("X-App-Version" to "1.0")
)
```

---

## 2. Asset Identification Format

SEP-38 uses a specific string format to identify assets. Always use this format directly as a plain string -- do not construct `Asset` objects.

| Asset type | Format | Example |
|------------|--------|---------|
| Stellar asset | `stellar:CODE:ISSUER` | `stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN` |
| Fiat currency | `iso4217:CODE` | `iso4217:USD` |

```kotlin
// WRONG: passing a Stellar Asset object -- SEP-38 methods expect String, not Asset
// CORRECT: use the string identifier format
val sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val buyAsset = "iso4217:BRL"
```

---

## 3. GET /info -- Available Assets

Returns all assets the anchor supports for exchange, with optional delivery methods and country restrictions. Authentication is optional.

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val quoteService = QuoteService.fromDomain("anchor.example.com")

// JWT is optional -- omit for unauthenticated call
val info = quoteService.info(jwtToken = jwtToken)

for (asset in info.assets) {
    println("Asset: ${asset.asset}")

    // Country codes for fiat assets -- null if no restriction
    asset.countryCodes?.let { codes ->
        println("  Countries: ${codes.joinToString(", ")}")
    }

    // Methods for delivering off-chain assets TO the anchor (e.g. user sends BRL via PIX)
    asset.sellDeliveryMethods?.forEach { method ->
        println("  Sell via ${method.name}: ${method.description}")
    }

    // Methods for receiving off-chain assets FROM the anchor (e.g. user receives BRL via ACH)
    asset.buyDeliveryMethods?.forEach { method ->
        println("  Buy via ${method.name}: ${method.description}")
    }
}
```

Signature:
```kotlin
suspend fun info(jwtToken: String? = null): Sep38InfoResponse
// throws: Sep38BadRequestException (HTTP 400), Sep38UnknownResponseException (other)
```

### Sep38InfoResponse properties

| Property | Type | Description |
|----------|------|-------------|
| `assets` | `List<Sep38Asset>` | All supported assets |

### Sep38Asset properties

| Property | Type | Description |
|----------|------|-------------|
| `asset` | `String` | Asset identifier in SEP-38 format |
| `sellDeliveryMethods` | `List<Sep38DeliveryMethod>?` | Methods for delivering this asset to the anchor; null if none |
| `buyDeliveryMethods` | `List<Sep38DeliveryMethod>?` | Methods for receiving this asset from the anchor; null if none |
| `countryCodes` | `List<String>?` | ISO country codes where asset is available; null if unrestricted |

---

## 4. GET /prices -- Indicative Prices (Multi-Asset)

Returns indicative (non-binding) prices for all tradeable assets given a sell asset and amount (or a buy asset and amount). Use this to show users all available exchange options before they commit to a specific pair.

You must provide exactly one of `sellAsset` or `buyAsset`, not both. When `sellAsset` is provided, the response contains `buyAssets`. When `buyAsset` is provided, the response contains `sellAssets`. Authentication is optional.

### Selling a specific asset

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val quoteService = QuoteService.fromDomain("anchor.example.com")

val response = quoteService.prices(
    sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "100",
    jwtToken = jwtToken // optional
)

// When selling, response contains buyAssets
response.buyAssets?.forEach { buyAsset ->
    println("${buyAsset.asset}: price=${buyAsset.price}, decimals=${buyAsset.decimals}")
}
```

### Buying a specific asset

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val quoteService = QuoteService.fromDomain("anchor.example.com")

val response = quoteService.prices(
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    buyAmount = "100",
    jwtToken = jwtToken
)

// When buying, response contains sellAssets
response.sellAssets?.forEach { sellAsset ->
    println("${sellAsset.asset}: price=${sellAsset.price}, decimals=${sellAsset.decimals}")
}
```

### With delivery method and country code

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val quoteService = QuoteService.fromDomain("anchor.example.com")

// What can I buy for 500 BRL sent via PIX in Brazil?
val response = quoteService.prices(
    sellAsset = "iso4217:BRL",
    sellAmount = "500",
    sellDeliveryMethod = "PIX",  // name from info().assets[n].sellDeliveryMethods
    countryCode = "BR",          // ISO 3166-1 alpha-2
    jwtToken = jwtToken
)
```

Signature:
```kotlin
suspend fun prices(
    sellAsset: String? = null,
    buyAsset: String? = null,
    sellAmount: String? = null,
    buyAmount: String? = null,
    sellDeliveryMethod: String? = null,
    buyDeliveryMethod: String? = null,
    countryCode: String? = null,
    jwtToken: String? = null
): Sep38PricesResponse
// throws: IllegalArgumentException (both/neither sellAsset and buyAsset),
//         Sep38BadRequestException (HTTP 400), Sep38UnknownResponseException (other)
```

### Sep38PricesResponse properties

| Property | Type | Description |
|----------|------|-------------|
| `buyAssets` | `List<Sep38BuyAsset>?` | Assets available to buy (present when sellAsset is provided) |
| `sellAssets` | `List<Sep38SellAsset>?` | Assets available to sell (present when buyAsset is provided) |

### Sep38BuyAsset / Sep38SellAsset properties

Both classes have identical structure:

| Property | Type | Description |
|----------|------|-------------|
| `asset` | `String` | Asset identifier in SEP-38 format |
| `price` | `String` | Indicative price |
| `decimals` | `Int` | Decimal precision for this asset |

---

## 5. GET /price -- Indicative Price (Single Pair)

Returns an indicative price for a specific asset pair with fee details. You must provide either `sellAmount` or `buyAmount`, but not both. Authentication is optional.

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val quoteService = QuoteService.fromDomain("anchor.example.com")

// Query by sell amount: how much BRL do I receive for 100 USDC?
val response = quoteService.price(
    context = "sep6",    // "sep6", "sep24", or "sep31"
    sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    buyAsset = "iso4217:BRL",
    sellAmount = "100",  // provide sellAmount OR buyAmount, not both
    jwtToken = jwtToken
)

println("Total price (with fees): ${response.totalPrice}")
println("Price (without fees):    ${response.price}")
println("Sell amount:             ${response.sellAmount}")
println("Buy amount:              ${response.buyAmount}")
println("Fee total:               ${response.fee.total} ${response.fee.asset}")
```

### Query by buy amount

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val quoteService = QuoteService.fromDomain("anchor.example.com")

// How much USDC do I need to sell to receive 500 BRL?
val response = quoteService.price(
    context = "sep6",
    sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    buyAsset = "iso4217:BRL",
    buyAmount = "500",
    jwtToken = jwtToken
)

println("You need to sell: ${response.sellAmount} USDC")
println("You will receive: ${response.buyAmount} BRL")
```

### With delivery methods

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val quoteService = QuoteService.fromDomain("anchor.example.com")

val response = quoteService.price(
    context = "sep6",
    sellAsset = "iso4217:BRL",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "500",
    sellDeliveryMethod = "PIX",
    countryCode = "BR",
    jwtToken = jwtToken
)
```

### Reading fee details

The response always includes a `Sep38Fee` object. The optional `details` list contains itemized fee components:

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val response = quoteService.price(
    context = "sep6",
    sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    buyAsset = "iso4217:BRL",
    sellAmount = "100"
)

val fee = response.fee
println("Fee total: ${fee.total} (${fee.asset})")

fee.details?.forEach { detail ->
    val desc = detail.description?.let { " ($it)" } ?: ""
    println("  ${detail.name}: ${detail.amount}$desc")
}
```

Signature:
```kotlin
suspend fun price(
    context: String,
    sellAsset: String,
    buyAsset: String,
    sellAmount: String? = null,
    buyAmount: String? = null,
    sellDeliveryMethod: String? = null,
    buyDeliveryMethod: String? = null,
    countryCode: String? = null,
    jwtToken: String? = null
): Sep38PriceResponse
// throws: IllegalArgumentException (both/neither amount),
//         Sep38BadRequestException (HTTP 400), Sep38UnknownResponseException (other)
```

### Sep38PriceResponse properties

| Property | Type | Description |
|----------|------|-------------|
| `totalPrice` | `String` | Total price including fees: `sell_amount = total_price * buy_amount` |
| `price` | `String` | Exchange rate without fees |
| `sellAmount` | `String` | Amount of the sell asset |
| `buyAmount` | `String` | Amount of the buy asset |
| `fee` | `Sep38Fee` | Fee structure (always present) |

---

## 6. POST /quote -- Request a Firm Quote

A firm quote is a binding commitment from the anchor to exchange assets at the given rate, valid until `expiresAt`. Authentication is **required**. Either `sellAmount` or `buyAmount` must be set in the request, but not both.

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService
import com.soneso.stellar.sdk.sep.sep38.Sep38QuoteRequest

val quoteService = QuoteService.fromDomain("anchor.example.com")

val request = Sep38QuoteRequest(
    context = "sep6",    // "sep6", "sep24", or "sep31"
    sellAsset = "iso4217:USD",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "100"   // OR buyAmount -- not both
)

// JWT is required -- passed as second positional parameter
val quote = quoteService.postQuote(request, jwtToken)

println("Quote ID:    ${quote.id}")
println("Expires at:  ${quote.expiresAt}")  // String in ISO 8601 format
println("Total price: ${quote.totalPrice}")
println("Price:       ${quote.price}")
println("Sell:        ${quote.sellAmount} ${quote.sellAsset}")
println("Buy:         ${quote.buyAmount} ${quote.buyAsset}")
println("Fee:         ${quote.fee.total} ${quote.fee.asset}")
```

### Request with expiration preference

Use `expireAfter` to request a minimum quote validity period. The anchor may grant a longer expiration. The value is an ISO 8601 UTC timestamp string.

```kotlin
import com.soneso.stellar.sdk.sep.sep38.Sep38QuoteRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

// WRONG: expireAfter is a String (ISO 8601), not a DateTime object
// CORRECT: pass an ISO 8601 string
val request = Sep38QuoteRequest(
    context = "sep6",
    sellAsset = "iso4217:USD",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "100",
    expireAfter = "2026-04-30T07:42:23Z"  // ISO 8601 UTC string
)

val quote = quoteService.postQuote(request, jwtToken)
println("Expires: ${quote.expiresAt}")
```

### Request with delivery methods

Include delivery method names (from `info()`) when exchanging off-chain assets:

```kotlin
import com.soneso.stellar.sdk.sep.sep38.Sep38QuoteRequest

val request = Sep38QuoteRequest(
    context = "sep6",
    sellAsset = "iso4217:BRL",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    buyAmount = "100",
    sellDeliveryMethod = "PIX",   // name from info().assets[n].sellDeliveryMethods
    countryCode = "BR"
)

val quote = quoteService.postQuote(request, jwtToken)
```

Signature:
```kotlin
suspend fun postQuote(request: Sep38QuoteRequest, jwtToken: String): Sep38QuoteResponse
// throws: IllegalArgumentException (both/neither amount in request),
//         Sep38BadRequestException (HTTP 400),
//         Sep38PermissionDeniedException (HTTP 403),
//         Sep38UnknownResponseException (other)
```

### Sep38QuoteRequest constructor

```kotlin
@Serializable
data class Sep38QuoteRequest(
    val context: String,                    // "sep6", "sep24", or "sep31"
    val sellAsset: String,                  // SEP-38 asset identifier
    val buyAsset: String,                   // SEP-38 asset identifier
    val sellAmount: String? = null,         // provide one of sellAmount or buyAmount
    val buyAmount: String? = null,          // provide one of sellAmount or buyAmount
    val expireAfter: String? = null,        // ISO 8601 UTC timestamp
    val sellDeliveryMethod: String? = null, // delivery method name from info()
    val buyDeliveryMethod: String? = null,  // delivery method name from info()
    val countryCode: String? = null         // ISO 3166-1 alpha-2 or ISO 3166-2
)
```

---

## 7. GET /quote/:id -- Retrieve a Firm Quote

Retrieves a previously-created firm quote by its ID. Authentication is **required**.

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService

val quoteService = QuoteService.fromDomain("anchor.example.com")

val quoteId = "de762cda-a193-4961-861e-57b31fed6eb3" // from postQuote() response
val quote = quoteService.getQuote(quoteId, jwtToken)

println("Quote ID:    ${quote.id}")
println("Expires at:  ${quote.expiresAt}")  // String -- ISO 8601
println("Sell: ${quote.sellAmount} ${quote.sellAsset}")
println("Buy:  ${quote.buyAmount} ${quote.buyAsset}")
```

Signature:
```kotlin
suspend fun getQuote(id: String, jwtToken: String): Sep38QuoteResponse
// throws: Sep38BadRequestException (HTTP 400),
//         Sep38PermissionDeniedException (HTTP 403),
//         Sep38NotFoundException (HTTP 404),
//         Sep38UnknownResponseException (other)
```

### Sep38QuoteResponse properties

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | Unique quote identifier |
| `expiresAt` | `String` | When this quote expires (ISO 8601 string) |
| `totalPrice` | `String` | Total price including fees |
| `price` | `String` | Exchange rate without fees |
| `sellAsset` | `String` | The asset being sold (SEP-38 format) |
| `sellAmount` | `String` | Amount of the sell asset |
| `sellDeliveryMethod` | `String?` | Delivery method for sell asset |
| `buyAsset` | `String` | The asset being purchased (SEP-38 format) |
| `buyAmount` | `String` | Amount of the buy asset |
| `buyDeliveryMethod` | `String?` | Delivery method for buy asset |
| `fee` | `Sep38Fee` | Fee structure (always present) |

---

## 8. Response Objects Reference

### Sep38Fee

Represents the total fee and optional itemized breakdown.

```kotlin
val fee = quote.fee

println("Total fee: ${fee.total}")   // String -- total fee amount
println("Fee asset: ${fee.asset}")   // String -- SEP-38 format, e.g. "iso4217:BRL"

// details is null when the anchor does not provide an itemized breakdown
fee.details?.forEach { detail ->
    println("${detail.name}: ${detail.amount}")       // String, String
    detail.description?.let { println("  $it") }      // String?
}
```

### Sep38Fee properties

| Property | Type | Description |
|----------|------|-------------|
| `total` | `String` | Total fee amount |
| `asset` | `String` | Asset the fee is charged in (SEP-38 format) |
| `details` | `List<Sep38FeeDetail>?` | Itemized breakdown; null if not provided by anchor |

### Sep38FeeDetail properties

| Property | Type | Description |
|----------|------|-------------|
| `name` | `String` | Fee component name (e.g. `"Service fee"`, `"PIX fee"`) |
| `amount` | `String` | Amount for this component |
| `description` | `String?` | Optional human-readable explanation |

### Sep38DeliveryMethod

The KMP SDK uses a single `Sep38DeliveryMethod` class for both sell and buy delivery methods (unlike the Flutter SDK which has separate classes).

| Property | Type | Description |
|----------|------|-------------|
| `name` | `String` | Identifier used as parameter value (e.g. `"PIX"`, `"ACH"`, `"cash"`) |
| `description` | `String` | Human-readable description of the delivery method |

Use the `name` value as the `sellDeliveryMethod` or `buyDeliveryMethod` parameter in `prices()`, `price()`, and `Sep38QuoteRequest`.

```kotlin
// Discover delivery methods from info, then use the name in subsequent calls
val info = quoteService.info()

for (asset in info.assets) {
    if (asset.asset == "iso4217:BRL") {
        asset.sellDeliveryMethods?.forEach { method ->
            // method.name is the value to pass as sellDeliveryMethod
            println("${method.name}: ${method.description}")
        }
    }
}
```

---

## 9. Error Handling

All exception types extend `Sep38Exception`. Wrap quote service calls in `try-catch` blocks in production:

```kotlin
import com.soneso.stellar.sdk.sep.sep38.QuoteService
import com.soneso.stellar.sdk.sep.sep38.Sep38QuoteRequest
import com.soneso.stellar.sdk.sep.sep38.exceptions.*

val quoteService = QuoteService.fromDomain("anchor.example.com")

try {
    val request = Sep38QuoteRequest(
        context = "sep6",
        sellAsset = "iso4217:USD",
        buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
        sellAmount = "100"
    )
    val quote = quoteService.postQuote(request, jwtToken)
    println("Quote ID: ${quote.id}")

} catch (e: IllegalArgumentException) {
    // Both sellAmount and buyAmount provided, or neither -- thrown before HTTP call
    println("Invalid request: ${e.message}")

} catch (e: Sep38BadRequestException) {
    // HTTP 400 -- invalid params, unsupported asset pair, unknown context
    println("Bad request: ${e.error}")

} catch (e: Sep38PermissionDeniedException) {
    // HTTP 403 -- missing JWT, expired JWT, or user not authorized
    println("Permission denied: ${e.error}")

} catch (e: Sep38NotFoundException) {
    // HTTP 404 -- quote ID not found (getQuote only)
    println("Quote not found: ${e.error}")

} catch (e: Sep38UnknownResponseException) {
    // Other HTTP errors (5xx, etc.)
    println("Unexpected error: status=${e.statusCode}, body=${e.responseBody}")
}
```

### Exception reference

| Exception | HTTP Status | Thrown by | Common cause |
|-----------|-------------|-----------|--------------|
| `IllegalArgumentException` | N/A | `prices()`, `price()`, `postQuote()` | Both or neither of the required mutual-exclusion params |
| `Sep38BadRequestException` | 400 | all methods | Invalid asset format, unsupported pair, missing required field |
| `Sep38PermissionDeniedException` | 403 | `postQuote()`, `getQuote()` | Missing or expired JWT, user not authorized |
| `Sep38NotFoundException` | 404 | `getQuote()` | Quote ID doesn't exist or has expired |
| `Sep38UnknownResponseException` | other | all methods | Server error or unexpected response |

All `Sep38*` exceptions extend `Sep38Exception` (which extends `Exception`) and expose an `error` property (`String`). `Sep38UnknownResponseException` exposes `statusCode` (`Int`) and `responseBody` (`String`) instead.

```kotlin
// Accessing error details from different exception types
} catch (e: Sep38BadRequestException) {
    println(e.error)       // String -- error message from anchor
    println(e.message)     // "Bad request (400). <error>"
    println(e)             // "SEP-38 bad request - error: <error>"

} catch (e: Sep38UnknownResponseException) {
    println(e.statusCode)   // Int -- HTTP status code
    println(e.responseBody) // String -- raw response body
    println(e)              // "SEP-38 unknown response - status: N, body: <body>"
}
```

---

## 10. Price Formulas

The relationship between price, totalPrice, amounts, and fees:

```
sell_amount = total_price * buy_amount
```

When the fee is denominated in the **sell** asset:
```
sell_amount - fee.total = price * buy_amount
```

When the fee is denominated in the **buy** asset:
```
sell_amount = price * (buy_amount + fee.total)
```

`totalPrice` always includes fees. `price` is the raw exchange rate before fees.

```kotlin
// Example: selling 542 BRL to buy 100 USDC, fee = 42.00 BRL (in sell asset)
// totalPrice = "5.42", price = "5.00"
// Verification: sell_amount = total_price * buy_amount => 542 = 5.42 * 100

val r = quoteService.price(
    context = "sep6",
    sellAsset = "iso4217:BRL",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "542"
)
val totalPriceNum = r.totalPrice.toDouble()
val buyAmountNum = r.buyAmount.toDouble()
// Effective sell cost including fees:
val effectiveSell = totalPriceNum * buyAmountNum
```

---

## 11. Common Pitfalls

**Wrong: using `SEP38QuoteService` (Flutter class name) instead of `QuoteService`**

```kotlin
// WRONG: the KMP SDK class is QuoteService, not SEP38QuoteService
val service = SEP38QuoteService.fromDomain("anchor.example.com") // does not exist

// CORRECT: use QuoteService
val service = QuoteService.fromDomain("anchor.example.com")
```

**Wrong: providing both sellAmount and buyAmount**

```kotlin
// WRONG: throws IllegalArgumentException before the HTTP call
quoteService.price(
    context = "sep6",
    sellAsset = "iso4217:USD",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "100",
    buyAmount = "95"  // WRONG: cannot provide both
)

// CORRECT: provide exactly one
quoteService.price(
    context = "sep6",
    sellAsset = "iso4217:USD",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "100"
)
```

**Wrong: providing both sellAsset and buyAsset to `prices()`**

```kotlin
// WRONG: prices() requires exactly one of sellAsset or buyAsset, not both
quoteService.prices(
    sellAsset = "stellar:USDC:GA5ZS...",
    buyAsset = "iso4217:BRL",
    sellAmount = "100"
) // throws IllegalArgumentException

// CORRECT: provide only sellAsset (to get buy options) or buyAsset (to get sell options)
quoteService.prices(
    sellAsset = "stellar:USDC:GA5ZS...",
    sellAmount = "100"
)
```

**Wrong: treating expiresAt as a DateTime object**

```kotlin
// WRONG: expiresAt is String in the KMP SDK (NOT DateTime like the Flutter SDK)
val expiresAt: DateTime = quote.expiresAt  // type error

// CORRECT: expiresAt is a String in ISO 8601 format
println(quote.expiresAt) // e.g. "2026-04-30T07:42:23Z"

// To compare against current time, parse it:
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

val expiresAt = Instant.parse(quote.expiresAt)
val isValid = Clock.System.now() < expiresAt
```

**Wrong: treating expireAfter in Sep38QuoteRequest as a DateTime**

```kotlin
// WRONG: expireAfter is a String, not a DateTime
val request = Sep38QuoteRequest(
    context = "sep6",
    sellAsset = "iso4217:USD",
    buyAsset = "stellar:USDC:GA5ZS...",
    sellAmount = "100",
    expireAfter = Clock.System.now().plus(30, DateTimeUnit.MINUTE)  // type error
)

// CORRECT: pass an ISO 8601 UTC string
val request = Sep38QuoteRequest(
    context = "sep6",
    sellAsset = "iso4217:USD",
    buyAsset = "stellar:USDC:GA5ZS...",
    sellAmount = "100",
    expireAfter = "2026-04-30T07:42:23Z"
)
```

**Wrong: assuming fee.details is always present**

```kotlin
// WRONG: details is null when the anchor omits the itemized breakdown
for (detail in quote.fee.details!!) { /* NullPointerException if details is null */ }

// CORRECT: null-check first
quote.fee.details?.forEach { detail ->
    println("${detail.name}: ${detail.amount}")
}
```

**Wrong: using totalPrice as the raw exchange rate for display**

```kotlin
// WRONG: totalPrice includes fees -- it is not the raw exchange rate
val rate = price.totalPrice.toDouble() // misleading for display

// CORRECT: use price for the raw rate; totalPrice for the effective sell cost calculation
val rawRate = price.price.toDouble()              // exchange rate without fees
val effectiveRate = price.totalPrice.toDouble()   // satisfies: sell = totalPrice * buy
```

**Wrong: `fromDomain()` is suspend -- must be called from a coroutine**

```kotlin
// WRONG: fromDomain() is a suspend function -- cannot call from non-suspend context
fun setup(): QuoteService {
    return QuoteService.fromDomain("anchor.example.com") // compile error
}

// CORRECT: call from a suspend function or coroutine scope
suspend fun setup(): QuoteService {
    return QuoteService.fromDomain("anchor.example.com")
}

// CORRECT: direct constructor is NOT suspend
val quoteService = QuoteService("https://anchor.example.com/sep38")
```

**Wrong: using Sep38SellDeliveryMethod / Sep38BuyDeliveryMethod (Flutter class names)**

```kotlin
// WRONG: the KMP SDK uses a single Sep38DeliveryMethod class, not separate sell/buy classes
val method: Sep38SellDeliveryMethod = ...  // does not exist

// CORRECT: both sell and buy delivery methods use Sep38DeliveryMethod
asset.sellDeliveryMethods?.forEach { method: Sep38DeliveryMethod ->
    println("${method.name}: ${method.description}")
}
asset.buyDeliveryMethods?.forEach { method: Sep38DeliveryMethod ->
    println("${method.name}: ${method.description}")
}
```

**Wrong: accessing `buyAssets` on `Sep38PricesResponse` without considering `sellAssets`**

```kotlin
// WRONG: assuming buyAssets is always populated -- it depends on the query direction
val prices = quoteService.prices(buyAsset = "stellar:USDC:GA5ZS...", buyAmount = "100")
prices.buyAssets!!.forEach { ... } // NullPointerException -- buyAssets is null when querying by buyAsset

// CORRECT: check which list is populated based on query direction
// Querying by sellAsset -> response has buyAssets
// Querying by buyAsset -> response has sellAssets
val prices = quoteService.prices(buyAsset = "stellar:USDC:GA5ZS...", buyAmount = "100")
prices.sellAssets?.forEach { sellAsset ->
    println("${sellAsset.asset}: ${sellAsset.price}")
}
```

---

## Related SEPs

- `references/sep-10.md` -- Web Authentication (provides JWT for authenticated endpoints)
- `references/sep-01.md` -- stellar.toml (provides `ANCHOR_QUOTE_SERVER` consumed by `fromDomain()`)
- `references/sep-06.md` -- Deposit/Withdrawal API (use `context = "sep6"`)
- SEP-24 -- Interactive Deposit/Withdrawal (use `context = "sep24"`)
- SEP-31 -- Cross-Border Payments (use `context = "sep31"`)
