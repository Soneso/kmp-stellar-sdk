# SEP-38: Anchor RFQ API

Anchor RFQ (Request for Quote) API enables price discovery and firm quotes for exchanging on-chain Stellar assets with off-chain assets such as fiat currencies. Anchors use this protocol to provide exchange rates without requiring one-for-one reserve-backed Stellar assets.

**Use Cases**:
- Get exchange rates for deposit/withdrawal operations (SEP-6, SEP-24)
- Request firm quotes with guaranteed rates for cross-border payments (SEP-31)
- Compare prices across delivery methods (bank transfer, PIX, cash)
- Build exchange interfaces with real-time pricing

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep38.*
import com.soneso.stellar.sdk.sep.sep38.exceptions.*
import com.soneso.stellar.sdk.sep.sep10.*
```

## Quick Start

```kotlin
// Initialize from domain's stellar.toml
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")

// Discover supported assets
val info = quoteService.info()
info.assets.forEach { asset ->
    println("Asset: ${asset.asset}")
}

// Get indicative price (non-binding, no authentication required)
val price = quoteService.price(
    context = "sep6",
    sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    buyAsset = "iso4217:BRL",
    sellAmount = "100"
)
println("Rate: ${price.price}, Fee: ${price.fee.total}")

// Request firm quote (requires SEP-10 authentication)
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
val jwtToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
).token

val quoteRequest = Sep38QuoteRequest(
    context = "sep6",
    sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    buyAsset = "iso4217:BRL",
    sellAmount = "100"
)
val quote = quoteService.postQuote(quoteRequest, jwtToken)
println("Quote ID: ${quote.id}, Expires: ${quote.expiresAt}")
```

## Service Initialization

### From Domain (Recommended)

```kotlin
// Discovers ANCHOR_QUOTE_SERVER from stellar.toml
```

### Direct Initialization

```kotlin
// Use when endpoint is known
val quoteService = QuoteService(
    serviceAddress = "https://testanchor.stellar.org/sep38"
)
```

## Endpoints

### Get Info

Discover supported assets and delivery methods.

```kotlin
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
val info = quoteService.info()

info.assets.forEach { asset ->
    println("Asset: ${asset.asset}")

    // Country restrictions (fiat only)
    asset.countryCodes?.let { codes ->
        println("  Countries: ${codes.joinToString(", ")}")
    }

    // How users deliver off-chain assets to anchor
    asset.sellDeliveryMethods?.forEach { method ->
        println("  Sell via: ${method.name} - ${method.description}")
    }

    // How users receive off-chain assets from anchor
    asset.buyDeliveryMethods?.forEach { method ->
        println("  Buy via: ${method.name} - ${method.description}")
    }
}
```

**Response Structure**:
- `assets` - List of `Sep38Asset` objects
  - `asset` - Asset identifier (e.g., "stellar:USDC:G...", "iso4217:BRL")
  - `sellDeliveryMethods` - Methods to deliver off-chain assets to anchor
  - `buyDeliveryMethods` - Methods to receive off-chain assets from anchor
  - `countryCodes` - ISO 3166-1/2 country codes where asset is available

### Get Prices

Get indicative prices for multiple assets. Provide either `sellAsset` or `buyAsset`, but not both.

**Selling an asset** (what can I buy?):

```kotlin
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
val prices = quoteService.prices(
    sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "100",
    buyDeliveryMethod = "PIX",
    countryCode = "BR"
)

prices.buyAssets?.forEach { buyAsset ->
    println("Buy ${buyAsset.asset} at ${buyAsset.price} (${buyAsset.decimals} decimals)")
}
```

**Buying an asset** (what do I need to sell?):

```kotlin
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
val prices = quoteService.prices(
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    buyAmount = "100",
    sellDeliveryMethod = "PIX",
    countryCode = "BR"
)

prices.sellAssets?.forEach { sellAsset ->
    println("Sell ${sellAsset.asset} at ${sellAsset.price}")
}
```

**Response Structure**:
- `buyAssets` - List of `Sep38BuyAsset` (present when selling)
- `sellAssets` - List of `Sep38SellAsset` (present when buying)
  - `asset` - Asset identifier
  - `price` - Indicative price for one unit
  - `decimals` - Decimal precision for amounts

### Get Price

Get indicative price for a specific asset pair. Provide either `sellAmount` or `buyAmount`, but not both.

```kotlin
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
val price = quoteService.price(
    context = "sep6",
    sellAsset = "iso4217:BRL",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "542",
    sellDeliveryMethod = "PIX",
    countryCode = "BR"
)

println("Exchange rate: ${price.price} BRL per USDC")
println("Effective rate (with fees): ${price.totalPrice}")
println("You send: ${price.sellAmount} BRL")
println("You receive: ${price.buyAmount} USDC")
println("Fee: ${price.fee.total} ${price.fee.asset}")

// Display fee breakdown
price.fee.details?.forEach { detail ->
    println("  ${detail.name}: ${detail.amount}")
    detail.description?.let { println("    $it") }
}
```

**Response Structure**:
- `totalPrice` - Price including fees (sellAmount / buyAmount)
- `price` - Exchange rate excluding fees
- `sellAmount` - Amount to sell
- `buyAmount` - Amount to receive
- `fee` - Fee breakdown (`Sep38Fee`)
  - `total` - Total fee amount
  - `asset` - Fee denomination
  - `details` - Optional list of `Sep38FeeDetail`

**Price Formulas**:
```text
sell_amount = total_price * buy_amount

// When fee is in sell asset:
sell_amount - fee = price * buy_amount

// When fee is in buy asset:
sell_amount = price * (buy_amount + fee)
```

### Request Firm Quote

Create a binding quote with guaranteed rate. **Requires SEP-10 authentication**.

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// Authenticate via SEP-10
val jwtToken = webAuth.jwtToken(accountId, signers).token

val request = Sep38QuoteRequest(
    context = "sep6",                    // "sep6", "sep24", or "sep31"
    sellAsset = "iso4217:BRL",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "542",                  // Provide sellAmount OR buyAmount
    sellDeliveryMethod = "PIX",
    countryCode = "BR"
)

val quote = quoteService.postQuote(request, jwtToken)

println("Quote ID: ${quote.id}")
println("Valid until: ${quote.expiresAt}")
println("Guaranteed rate: ${quote.price}")
println("Total with fees: ${quote.totalPrice}")
println("Sell: ${quote.sellAmount} ${quote.sellAsset}")
println("Buy: ${quote.buyAmount} ${quote.buyAsset}")
```

**Request Parameters** (`Sep38QuoteRequest`):
- `context` - Usage context: "sep6", "sep24", or "sep31"
- `sellAsset` - Asset to sell
- `buyAsset` - Asset to buy
- `sellAmount` - Amount to sell (mutually exclusive with buyAmount)
- `buyAmount` - Amount to buy (mutually exclusive with sellAmount)
- `expireAfter` - Optional desired expiration (ISO 8601)
- `sellDeliveryMethod` - Delivery method for off-chain sell asset
- `buyDeliveryMethod` - Delivery method for off-chain buy asset
- `countryCode` - ISO 3166-1/2 country code

**Response Structure** (`Sep38QuoteResponse`):
- `id` - Unique quote identifier for use in SEP-6/24/31
- `expiresAt` - Expiration timestamp (ISO 8601)
- `totalPrice`, `price`, `sellAmount`, `buyAmount`, `fee` - Same as price response
- `sellAsset`, `buyAsset` - Asset identifiers
- `sellDeliveryMethod`, `buyDeliveryMethod` - Delivery methods if provided

### Get Quote

Retrieve an existing quote by ID. **Requires SEP-10 authentication**.

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
// jwtToken, quoteService: from the previous steps of this flow
val quoteId = "de762cda-a193-4961-861e-57b31fed6eb3"
val quote = quoteService.getQuote(quoteId, jwtToken)

println("Quote ID: ${quote.id}")
println("Expires: ${quote.expiresAt}")
println("Rate: ${quote.price}")
```

## Asset Identification Format

Assets use a scheme-based format: `<scheme>:<identifier>`

| Scheme | Format | Example |
|--------|--------|---------|
| `stellar` | `stellar:CODE:ISSUER` | `stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN` |
| `iso4217` | `iso4217:CODE` | `iso4217:USD`, `iso4217:BRL` |

**Stellar Assets**: Use the issuer's account ID (56 characters starting with G).

**Fiat Currencies**: Use ISO 4217 three-character currency codes.

## Error Handling

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
try {
    val quote = quoteService.postQuote(request, jwtToken)
} catch (e: Sep38BadRequestException) {
    // Invalid parameters (400)
    println("Bad request: ${e.error}")
} catch (e: Sep38PermissionDeniedException) {
    // Authentication failed (403)
    println("Permission denied: ${e.error}")
    // Re-authenticate via SEP-10
} catch (e: Sep38NotFoundException) {
    // Quote not found (404)
    println("Quote not found: ${e.error}")
} catch (e: Sep38UnknownResponseException) {
    // Unexpected status code
    println("Status ${e.statusCode}: ${e.responseBody}")
}
```

**Exception Types**:
- `Sep38BadRequestException` - Invalid asset format, missing parameters, invalid delivery method
- `Sep38PermissionDeniedException` - JWT token missing, expired, or invalid
- `Sep38NotFoundException` - Quote ID does not exist or expired
- `Sep38UnknownResponseException` - Server error (5xx) or rate limiting (429)

## Complete Integration Example

Full workflow for a SEP-6 deposit with firm quote:

```kotlin
val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
// jwtToken: from the previous steps of this flow
// 1. Initialize services
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

// 2. Authenticate with user's keypair
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val jwtToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
).token

// 3. Discover available assets
val info = quoteService.info()
val brlAsset = info.assets.find { it.asset == "iso4217:BRL" }
val pixMethod = brlAsset?.sellDeliveryMethods?.find { it.name == "PIX" }

// 4. Get indicative price
val indicativePrice = quoteService.price(
    context = "sep6",
    sellAsset = "iso4217:BRL",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "500",
    sellDeliveryMethod = "PIX",
    countryCode = "BR"
)
println("Indicative: ${indicativePrice.buyAmount} USDC for 500 BRL")

// 5. Request firm quote
val quoteRequest = Sep38QuoteRequest(
    context = "sep6",
    sellAsset = "iso4217:BRL",
    buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
    sellAmount = "500",
    sellDeliveryMethod = "PIX",
    countryCode = "BR"
)
val quote = quoteService.postQuote(quoteRequest, jwtToken)
println("Firm quote: ${quote.buyAmount} USDC, expires ${quote.expiresAt}")

// 6. Use quote ID in SEP-6 deposit
// Pass quote.id to SEP-6 deposit request
// Anchor honors guaranteed rate if funds received before expiration
```

## API Reference

**Main Class**:
- `QuoteService` - SEP-38 client

**Factory Methods**:
- `QuoteService.fromDomain(domain)` - Initialize from stellar.toml
- `QuoteService(serviceAddress)` - Direct initialization

**Methods**:
- `info(jwtToken?)` - Get supported assets and delivery methods
- `prices(sellAsset?, buyAsset?, sellAmount?, buyAmount?, ...)` - Get indicative prices
- `price(context, sellAsset, buyAsset, sellAmount?, buyAmount?, ...)` - Get indicative price
- `postQuote(request, jwtToken)` - Request firm quote
- `getQuote(id, jwtToken)` - Retrieve existing quote

**Data Classes**:
- `Sep38InfoResponse` - Info endpoint response
- `Sep38Asset` - Asset with delivery methods
- `Sep38DeliveryMethod` - Delivery method (name, description)
- `Sep38PricesResponse` - Prices endpoint response
- `Sep38BuyAsset`, `Sep38SellAsset` - Asset with price and decimals
- `Sep38PriceResponse` - Price endpoint response
- `Sep38QuoteRequest` - Firm quote request
- `Sep38QuoteResponse` - Firm quote response
- `Sep38Fee` - Fee with total, asset, and optional details
- `Sep38FeeDetail` - Individual fee component

**Exception Types**:
- `Sep38Exception` - Base exception
- `Sep38BadRequestException` - Invalid request (400)
- `Sep38PermissionDeniedException` - Auth failed (403)
- `Sep38NotFoundException` - Quote not found (404)
- `Sep38UnknownResponseException` - Unexpected response

**Specification**: [SEP-38: Anchor RFQ API](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0038.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep38`

**Last Updated**: 2025-12-12
