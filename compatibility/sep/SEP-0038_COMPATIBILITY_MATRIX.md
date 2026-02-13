# SEP-0038 (Anchor RFQ API) Compatibility Matrix

**Generated:** 2026-02-13 21:02:50

**SEP Version:** 2.5.0<br>
**SEP Status:** Draft<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0038.md

## SEP Summary

Lets anchors provide quotes for exchanging on-chain assets for off-chain assets and vice versa.

## Overall Coverage

**Total Coverage:** 100.0% (69/69 fields)

- ✅ **Implemented:** 69/69
- ❌ **Not Implemented:** 0/69
- **Required Fields:** 100.0% (2/2)

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/QuoteService.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38Asset.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38BuyAsset.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38DeliveryMethod.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38Fee.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38FeeDetail.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38InfoResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38PriceResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38PricesResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38QuoteRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38QuoteResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/Sep38SellAsset.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38BadRequestException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38NotFoundException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38PermissionDeniedException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep38/exceptions/Sep38UnknownResponseException.kt`

### Key Classes

- **`QuoteService`** - Methods: fromDomain, info, prices, price, postQuote, getQuote, extractErrorMessage
- **`Sep38Asset`**
- **`Sep38BuyAsset`**
- **`Sep38DeliveryMethod`**
- **`Sep38Fee`**
- **`Sep38FeeDetail`**
- **`Sep38InfoResponse`**
- **`Sep38PriceResponse`**
- **`Sep38PricesResponse`**
- **`Sep38QuoteRequest`**
- **`Sep38QuoteResponse`**
- **`Sep38SellAsset`**
- **`Sep38BadRequestException`** - Methods: toString
- **`for`** - Methods: toString
- **`to`** - Methods: toString
- **`Sep38Exception`** - Methods: toString
- **`QuoteCache`**
- **`CachedQuote`**
- **`Sep38NotFoundException`** - Methods: toString
- **`Sep38Client`**
- **`Sep38PermissionDeniedException`** - Methods: toString
- **`QuoteResult`**
- **`Success`**
- **`Retryable`**
- **`Failed`**
- **`Sep38UnknownResponseException`** - Methods: toString

### Test Coverage

**Tests:** 93 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/sep/sep38/QuoteServiceIntegrationTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/QuoteServiceTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/Sep38AssetTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/Sep38DeliveryMethodTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/Sep38ExceptionsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/Sep38InfoResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/Sep38PriceResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/Sep38PricesResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/Sep38QuoteRequestTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep38/Sep38QuoteResponseTest.kt`

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| GET Info | 100.0% | N/A | 7 | 7 |
| GET Price | 100.0% | N/A | 19 | 19 |
| GET Prices | 100.0% | N/A | 12 | 12 |
| GET Quote | 100.0% | 1/1 | 11 | 11 |
| POST Quote | 100.0% | 1/1 | 20 | 20 |

## Detailed Field Comparison

### GET Info

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `assets` |  | ✅ | `assets` | An array of objects describing the assets available in exchange for one or more of the other asse... |
| `asset` |  | ✅ | `asset` | The [Asset Identification Format](#asset-identification-format) value. |
| `sell_delivery_methods` |  | ✅ | `sellDeliveryMethods` | (optional) Only for non-Stellar assets. An array of objects describing the methods a client can u... |
| `buy_delivery_methods` |  | ✅ | `buyDeliveryMethods` | (optional) Only for non-Stellar assets. An array of objects describing the methods a client can u... |
| `country_codes` |  | ✅ | `countryCodes` | (optional) Only for fiat assets. A list of [ISO 3166-2](https://en.wikipedia.org/wiki/ISO_3166-2) codes of the countries where the Anchor operates for fiat transactions. Anchor may not require second part of the ISO 3166-2 to be passed (i.e. use [ISO-3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) instead). |
| `name` |  | ✅ | `name` | The value to use when making `POST /quote` requests. |
| `description` |  | ✅ | `description` | A human readable description of the method identified by `name`. |

### GET Price

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sell_asset` |  | ✅ | `sellAsset` | The asset the client would like to sell. Ex. `USDC:G...`, `iso4217:ARS` |
| `buy_asset` |  | ✅ | `buyAsset` | The asset the client would like to exchange for `sell_asset`. |
| `sell_amount` |  | ✅ | `sellAmount` | The amount of `sell_asset` the client would like to exchange for `buy_asset`. |
| `buy_amount` |  | ✅ | `buyAmount` | The amount of `buy_asset` the client would like to purchase with `sell_asset`. |
| `sell_delivery_method` |  | ✅ | `sellDeliveryMethod` | (optional) One of the `name` values specified by the `sell_delivery_methods` array for the associ... |
| `buy_delivery_method` |  | ✅ | `buyDeliveryMethod` | (optional) One of the `name` values specified by the `buy_delivery_methods` array for the associa... |
| `country_code` |  | ✅ | `countryCode` | (optional) The [ISO 3166-2](https://en.wikipedia.org/wiki/ISO_3166-2) or [ISO-3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) code of the user's current address. Should be provided if there are two or more country codes available for the desired asset in [`GET /info`](#get-info). |
| `context` |  | ✅ | `context` | The context for what this quote will be used for. Must be one of `sep6` or `sep31`. |
| `total_price` |  | ✅ | `totalPrice` | The total conversion price offered by the anchor for one unit of `buy_asset` in terms of `sell_as... |
| `price` |  | ✅ | `price` | The conversion price offered by the anchor for one unit of `buy_asset` in terms of `sell_asset`, ... |
| `sell_amount` |  | ✅ | `sellAmount` | The amount of `sell_asset` the anchor will exchange for `buy_asset`. It could be different from t... |
| `buy_amount` |  | ✅ | `buyAmount` | The amount of `buy_asset` the anchor will provide with `sell_asset`. It could be different from t... |
| `fee` |  | ✅ | `fee` | An object describing the fee used to calculate the conversion price. This can be used to detail t... |
| `total` |  | ✅ | `total` | The total amount of fee applied. |
| `asset` |  | ✅ | `asset` | The asset in which the fee is applied, represented through the [Asset Identification Format](#ass... |
| `details` |  | ✅ | `details` | (optional) An array of objects detailing the fees that were used to calculate the conversion pric... |
| `name` |  | ✅ | `name` | The name of the fee, for example `ACH fee`, `Brazilian conciliation fee`, `Service fee`, etc. |
| `description` |  | ✅ | `description` | (optional) A text describing the fee. |
| `amount` |  | ✅ | `amount` | The amount of asset applied. If `fee.details` is provided, `sum(fee.details.amount)` should be eq... |

### GET Prices

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sell_asset` |  | ✅ | `sellAsset` | The asset you want to sell, using the [Asset Identification Format](#asset-identification-format)... |
| `buy_asset` |  | ✅ | `buyAsset` | The asset you want to buy, using the [Asset Identification Format](#asset-identification-format).... |
| `sell_amount` |  | ✅ | `sellAmount` | The amount of `sell_asset` the client would exchange for each of the `buy_assets`. The `sell_amou... |
| `buy_amount` |  | ✅ | `buyAmount` | The amount of `buy_asset` the client would exchange for each of the `sell_assets`. The `buy_amoun... |
| `sell_delivery_method` |  | ✅ | `sellDeliveryMethod` | (optional) One of the `name` values specified by the `sell_delivery_methods` array for the associ... |
| `buy_delivery_method` |  | ✅ | `buyDeliveryMethod` | (optional) One of the `name` values specified by the `buy_delivery_methods` array for the associa... |
| `country_code` |  | ✅ | `countryCode` | (optional) The [ISO 3166-2](https://en.wikipedia.org/wiki/ISO_3166-2) or [ISO-3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) code of the user's current address. Should be provided if there are two or more country codes available for the desired asset in [`GET /info`](#get-info). |
| `buy_assets` |  | ✅ | `buyAssets` | An array of objects containing information on the assets that the client will receive when the `s... |
| `sell_assets` |  | ✅ | `sellAssets` | An array of objects containing information on the assets that the client will receive when the `b... |
| `asset` |  | ✅ | `asset` | The [Asset Identification Format](#asset-identification-format) value. |
| `price` |  | ✅ | `price` | The price offered by the anchor for one unit of `asset` in terms of `sell_asset`. In traditional ... |
| `decimals` |  | ✅ | `decimals` | The number of decimals needed to represent `asset`. |

### GET Quote

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` |  | ✅ | `id` | The unique identifier for the quote. Same as the `id` returned in the [`POST /quote`](#post-quote... |
| `id` |  | ✅ | `id` | The `id` specified in the request. |
| `expires_at` |  | ✅ | `expiresAt` | The date and time by which the anchor must receive funds from the client. |
| `price` |  | ✅ | `price` | The price offered by the anchor for one unit of `buy_asset` in terms of `sell_asset`. In traditio... |
| `sell_asset` |  | ✅ | `sellAsset` | The asset the client would like to sell. Ex. `USDC:G...`, `iso4217:ARS` |
| `sell_amount` |  | ✅ | `sellAmount` | The amount of `sell_asset` to be exchanged for `buy_asset`. |
| `sell_delivery_method` |  | ✅ | `sellDeliveryMethod` | (optional) The method by which the user plans to deliver an off-chain asset to the anchor. This w... |
| `buy_asset` |  | ✅ | `buyAsset` | The asset the client would like to exchange for `sell_asset`. |
| `buy_amount` | ✓ | ✅ | `buyAmount` | The amount of `buy_asset` to be exchanged for `sell_asset`. `price * buy_amount = sell_amount` mu... |
| `buy_delivery_method` |  | ✅ | `buyDeliveryMethod` | (optional) The method by which the user plans to receive an off-chain asset from the anchor. This... |
| `fee` |  | ✅ | `fee` | An object describing the fee used to calculate the conversion price. This can be used to datail t... |

### POST Quote

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sell_asset` |  | ✅ | `sellAsset` | Same as the definition of `sell_asset` in `GET /price`. |
| `buy_asset` |  | ✅ | `buyAsset` | Same as the definition of `buy_asset` in `GET /price`. |
| `sell_amount` |  | ✅ | `sellAmount` | Same as the definition of `sell_amount` in `GET /price`. |
| `buy_amount` |  | ✅ | `buyAmount` | The same definition of `buy_amount` in `GET /price`. |
| `expire_after` |  | ✅ | `expireAfter` | (optional) The client's desired `expires_at` date and time for the quote. Anchors may choose an `... |
| `sell_delivery_method` |  | ✅ | `sellDeliveryMethod` | (optional) One of the `name` values specified by the `sell_delivery_methods` array for the associ... |
| `buy_delivery_method` |  | ✅ | `buyDeliveryMethod` | (optional) One of the `name` values specified by the `buy_delivery_methods` array for the associa... |
| `country_code` |  | ✅ | `countryCode` | (optional) The [ISO 3166-2](https://en.wikipedia.org/wiki/ISO_3166-2) or [ISO-3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) code of the user's current address. Should be provided if there are two or more country codes available for the desired asset in [`GET /info`](#get-info). |
| `context` |  | ✅ | `context` | The context for what this quote will be used for. Must be one of `sep6`, `sep24` or `sep31`. |
| `id` |  | ✅ | `id` | The unique identifier for the quote to be used in other Stellar Ecosystem Proposals (SEPs). |
| `expires_at` |  | ✅ | `expiresAt` | The date and time by which the anchor must receive funds from the client. |
| `total_price` |  | ✅ | `totalPrice` | The total conversion price offered by the anchor for one unit of `buy_asset` in terms of `sell_as... |
| `price` |  | ✅ | `price` | The conversion price offered by the anchor for one unit of `buy_asset` in terms of `sell_asset`, ... |
| `sell_asset` |  | ✅ | `sellAsset` | The asset the client would like to sell. Ex. `USDC:G...`, `iso4217:ARS` |
| `sell_amount` |  | ✅ | `sellAmount` | The amount of `sell_asset` to be exchanged for `buy_asset`. It could be different from the `sell_... |
| `sell_delivery_method` |  | ✅ | `sellDeliveryMethod` | (optional) The method by which the user plans to deliver an off-chain asset to the anchor. This w... |
| `buy_asset` |  | ✅ | `buyAsset` | The asset the client would like to exchange for `sell_asset`. |
| `buy_amount` | ✓ | ✅ | `buyAmount` | The amount of `buy_asset` to be exchanged for `sell_asset`. It could be different from the `buy_a... |
| `buy_delivery_method` |  | ✅ | `buyDeliveryMethod` | (optional) The method by which the user plans to receive an off-chain asset from the anchor. This... |
| `fee` |  | ✅ | `fee` | An object describing the fee used to calculate the conversion price. This can be used to datail t... |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0038](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0038.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0038`
