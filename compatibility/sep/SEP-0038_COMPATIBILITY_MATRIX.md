# SEP-0038 (Anchor RFQ API) Compatibility Matrix

**Generated:** 2026-02-11 15:11:25

**SEP Version:** 2.5.0<br>
**SEP Status:** Draft<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0038.md

## SEP Summary

This protocol enables anchors to accept off-chain assets in exchange for different on-chain assets, and vice versa. Specifically, it enables anchors to provide quotes that can be referenced within the context of existing Stellar Ecosystem Proposals. How the exchange of assets is facilitated is outside the scope of this document.

## Overall Coverage

**Total Coverage:** 100.0% (63/63 fields)

- ✅ **Implemented:** 63/63
- ❌ **Not Implemented:** 0/63

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/QuoteService.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38InfoResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38Asset.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38DeliveryMethod.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38PricesResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38SellAsset.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38BuyAsset.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38PriceResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38Fee.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38FeeDetail.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38QuoteRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38QuoteResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38BadRequestException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38PermissionDeniedException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38NotFoundException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38UnknownResponseException.kt`

### Key Classes

- **`QuoteService`** - Methods: fromDomain, info, prices, price, postQuote, getQuote
- **`Sep38InfoResponse`**
- **`Sep38Asset`**
- **`Sep38DeliveryMethod`**
- **`Sep38PricesResponse`**
- **`Sep38SellAsset`**
- **`Sep38BuyAsset`**
- **`Sep38PriceResponse`**
- **`Sep38Fee`**
- **`Sep38FeeDetail`**
- **`Sep38QuoteRequest`**
- **`Sep38QuoteResponse`**
- **`Sep38Exception`**
- **`Sep38BadRequestException`**
- **`Sep38PermissionDeniedException`**
- **`Sep38NotFoundException`**
- **`Sep38UnknownResponseException`**

### Test Coverage

**Tests:** 42 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38InfoResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38PriceResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38ExceptionsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38QuoteRequestTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38QuoteResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38PricesResponseTest.kt`

## Coverage by Section

| Section | Coverage | Implemented | Total |
|---------|----------|-------------|-------|
| Asset Fields | 100.0% | 4 | 4 |
| Buy Asset Fields | 100.0% | 3 | 3 |
| Delivery Method Fields | 100.0% | 2 | 2 |
| Fee Details Fields | 100.0% | 3 | 3 |
| Fee Fields | 100.0% | 3 | 3 |
| Get Quote Endpoint | 100.0% | 1 | 1 |
| Info Endpoint | 100.0% | 1 | 1 |
| Info Response Fields | 100.0% | 1 | 1 |
| Post Quote Endpoint | 100.0% | 1 | 1 |
| Post Quote Request Fields | 100.0% | 9 | 9 |
| Price Endpoint | 100.0% | 1 | 1 |
| Price Request Parameters | 100.0% | 8 | 8 |
| Price Response Fields | 100.0% | 5 | 5 |
| Prices Endpoint | 100.0% | 1 | 1 |
| Prices Request Parameters | 100.0% | 7 | 7 |
| Prices Response Fields | 100.0% | 2 | 2 |
| Quote Response Fields | 100.0% | 11 | 11 |

## Detailed Field Comparison

### Asset Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset` | ✓ | ✅ | `Sep38Asset.asset` | Asset identifier in Asset Identification Format (stellar:CODE:ISSUER or iso4217:CUR) |
| `sell_delivery_methods` |  | ✅ | `Sep38Asset.sellDeliveryMethods` | Array of delivery methods for selling this asset (off-chain assets only) |
| `buy_delivery_methods` |  | ✅ | `Sep38Asset.buyDeliveryMethods` | Array of delivery methods for buying this asset (off-chain assets only) |
| `country_codes` |  | ✅ | `Sep38Asset.countryCodes` | Array of ISO 3166-2 or ISO 3166-1 alpha-2 country codes (fiat assets only) |

### Buy Asset Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset` | ✓ | ✅ | `Sep38BuyAsset.asset / Sep38SellAsset.asset` | Asset identifier in Asset Identification Format |
| `price` | ✓ | ✅ | `Sep38BuyAsset.price / Sep38SellAsset.price` | Price offered by anchor for one unit of buy_asset |
| `decimals` | ✓ | ✅ | `Sep38BuyAsset.decimals / Sep38SellAsset.decimals` | Number of decimals for the buy asset |

### Delivery Method Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `name` | ✓ | ✅ | `Sep38DeliveryMethod.name` | Delivery method name identifier |
| `description` | ✓ | ✅ | `Sep38DeliveryMethod.description` | Human-readable description of the delivery method |

### Fee Details Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `name` | ✓ | ✅ | `Sep38FeeDetail.name` | Name identifier for the fee component |
| `description` |  | ✅ | `Sep38FeeDetail.description` | Human-readable description of the fee |
| `amount` | ✓ | ✅ | `Sep38FeeDetail.amount` | Fee amount as decimal string |

### Fee Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `total` | ✓ | ✅ | `Sep38Fee.total` | Total fee amount as decimal string |
| `asset` | ✓ | ✅ | `Sep38Fee.asset` | Asset identifier for the fee |
| `details` |  | ✅ | `Sep38Fee.details` | Optional array of fee breakdown objects |

### Get Quote Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `get_quote_endpoint` | ✓ | ✅ | `QuoteService.getQuote()` | GET /quote/:id - Fetch a previously-provided firm quote |

### Info Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `info_endpoint` | ✓ | ✅ | `QuoteService.info()` | GET /info - Returns supported Stellar and off-chain assets available for trading |

### Info Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `assets` | ✓ | ✅ | `Sep38InfoResponse.assets` | Array of asset objects supported for trading |

### Post Quote Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `post_quote_endpoint` | ✓ | ✅ | `QuoteService.postQuote()` | POST /quote - Request a firm quote for asset exchange |

### Post Quote Request Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `context` | ✓ | ✅ | `Sep38QuoteRequest.context` | Context for quote usage (sep6, sep24, or sep31) |
| `sell_asset` | ✓ | ✅ | `Sep38QuoteRequest.sellAsset` | Asset client would like to sell |
| `buy_asset` | ✓ | ✅ | `Sep38QuoteRequest.buyAsset` | Asset client would like to exchange for sell_asset |
| `sell_amount` |  | ✅ | `Sep38QuoteRequest.sellAmount` | Amount of sell_asset to exchange (mutually exclusive with buy_amount) |
| `buy_amount` |  | ✅ | `Sep38QuoteRequest.buyAmount` | Amount of buy_asset to exchange for (mutually exclusive with sell_amount) |
| `expire_after` |  | ✅ | `Sep38QuoteRequest.expireAfter` | Requested expiration timestamp for the quote (ISO 8601) |
| `sell_delivery_method` |  | ✅ | `Sep38QuoteRequest.sellDeliveryMethod` | Delivery method for off-chain sell asset |
| `buy_delivery_method` |  | ✅ | `Sep38QuoteRequest.buyDeliveryMethod` | Delivery method for off-chain buy asset |
| `country_code` |  | ✅ | `Sep38QuoteRequest.countryCode` | ISO 3166-2 or ISO 3166-1 alpha-2 country code |

### Price Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `price_endpoint` | ✓ | ✅ | `QuoteService.price()` | GET /price - Returns indicative price for a specific asset pair |

### Price Request Parameters

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `context` | ✓ | ✅ | `QuoteService.price(context)` | Context for quote usage (sep6, sep24, or sep31) |
| `sell_asset` | ✓ | ✅ | `QuoteService.price(sellAsset)` | Asset to sell using Asset Identification Format |
| `buy_asset` | ✓ | ✅ | `QuoteService.price(buyAsset)` | Asset to buy using Asset Identification Format |
| `sell_amount` |  | ✅ | `QuoteService.price(sellAmount)` | Amount of sell_asset to exchange (mutually exclusive with buy_amount) |
| `buy_amount` |  | ✅ | `QuoteService.price(buyAmount)` | Amount of buy_asset to receive (mutually exclusive with sell_amount) |
| `sell_delivery_method` |  | ✅ | `QuoteService.price(sellDeliveryMethod)` | Delivery method for off-chain sell asset |
| `buy_delivery_method` |  | ✅ | `QuoteService.price(buyDeliveryMethod)` | Delivery method for off-chain buy asset |
| `country_code` |  | ✅ | `QuoteService.price(countryCode)` | ISO 3166-2 or ISO 3166-1 alpha-2 country code |

### Price Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `total_price` | ✓ | ✅ | `Sep38PriceResponse.totalPrice` | Total conversion price including fees |
| `price` | ✓ | ✅ | `Sep38PriceResponse.price` | Base conversion price excluding fees |
| `sell_amount` | ✓ | ✅ | `Sep38PriceResponse.sellAmount` | Amount of sell_asset to be exchanged |
| `buy_amount` | ✓ | ✅ | `Sep38PriceResponse.buyAmount` | Amount of buy_asset to be received |
| `fee` | ✓ | ✅ | `Sep38PriceResponse.fee` | Fee object with total, asset, and optional details |

### Prices Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `prices_endpoint` | ✓ | ✅ | `QuoteService.prices()` | GET /prices - Returns indicative prices of off-chain assets in exchange for Stellar assets |

### Prices Request Parameters

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sell_asset` |  | ✅ | `QuoteService.prices(sellAsset)` | Asset to sell using Asset Identification Format (mutually exclusive with buy_asset) |
| `buy_asset` |  | ✅ | `QuoteService.prices(buyAsset)` | Asset to buy using Asset Identification Format (mutually exclusive with sell_asset) |
| `sell_amount` |  | ✅ | `QuoteService.prices(sellAmount)` | Amount of sell_asset to exchange (required when sell_asset is provided) |
| `buy_amount` |  | ✅ | `QuoteService.prices(buyAmount)` | Amount of buy_asset to receive (required when buy_asset is provided) |
| `sell_delivery_method` |  | ✅ | `QuoteService.prices(sellDeliveryMethod)` | Delivery method for off-chain sell asset |
| `buy_delivery_method` |  | ✅ | `QuoteService.prices(buyDeliveryMethod)` | Delivery method for off-chain buy asset |
| `country_code` |  | ✅ | `QuoteService.prices(countryCode)` | ISO 3166-2 or ISO 3166-1 alpha-2 country code |

### Prices Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `buy_assets` |  | ✅ | `Sep38PricesResponse.buyAssets` | Array of buy asset objects with prices (when sell_asset is provided) |
| `sell_assets` |  | ✅ | `Sep38PricesResponse.sellAssets` | Array of sell asset objects with prices (when buy_asset is provided) |

### Quote Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` | ✓ | ✅ | `Sep38QuoteResponse.id` | Unique identifier for the quote |
| `expires_at` | ✓ | ✅ | `Sep38QuoteResponse.expiresAt` | Expiration timestamp for the quote (ISO 8601) |
| `total_price` | ✓ | ✅ | `Sep38QuoteResponse.totalPrice` | Total conversion price including fees |
| `price` | ✓ | ✅ | `Sep38QuoteResponse.price` | Base conversion price excluding fees |
| `sell_asset` | ✓ | ✅ | `Sep38QuoteResponse.sellAsset` | Asset to be sold |
| `sell_amount` | ✓ | ✅ | `Sep38QuoteResponse.sellAmount` | Amount of sell_asset to be exchanged |
| `sell_delivery_method` |  | ✅ | `Sep38QuoteResponse.sellDeliveryMethod` | Delivery method for off-chain sell asset |
| `buy_asset` | ✓ | ✅ | `Sep38QuoteResponse.buyAsset` | Asset to be bought |
| `buy_amount` | ✓ | ✅ | `Sep38QuoteResponse.buyAmount` | Amount of buy_asset to be received |
| `buy_delivery_method` |  | ✅ | `Sep38QuoteResponse.buyDeliveryMethod` | Delivery method for off-chain buy asset |
| `fee` | ✓ | ✅ | `Sep38QuoteResponse.fee` | Fee object with total, asset, and optional details |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0038](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0038.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0038`
