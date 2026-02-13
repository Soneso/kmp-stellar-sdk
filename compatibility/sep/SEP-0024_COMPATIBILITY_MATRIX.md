# SEP-0024 (Hosted Deposit and Withdrawal) Compatibility Matrix

**Generated:** 2026-02-13 22:05:28

**SEP Version:** 3.8.0<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0024.md

## SEP Summary

An interactive deposit and withdrawal flow where the anchor controls the UI via a popup within the wallet. Based on SEP-06 but limited to the interactive path.

## Overall Coverage

**Total Coverage:** 100.0% (28/28 fields)

- ✅ **Implemented:** 28/28
- ❌ **Not Implemented:** 0/28
- **Required Fields:** 100% (0/0)

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24Requests.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24Responses.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24Service.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24TransactionStatus.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24AuthenticationRequiredException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24InvalidRequestException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24ServerErrorException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24TransactionNotFoundException.kt`

### Key Classes

- **`Sep24DepositRequest`**
- **`Sep24WithdrawRequest`**
- **`is`**
- **`Sep24FeeRequest`**
- **`Sep24TransactionsRequest`**
- **`Sep24TransactionRequest`**
- **`Sep24InfoResponse`** - Methods: getStatusEnum, isTerminal
- **`Sep24AssetInfo`** - Methods: getStatusEnum, isTerminal
- **`Sep24FeeEndpointInfo`** - Methods: getStatusEnum, isTerminal
- **`Sep24Features`** - Methods: getStatusEnum, isTerminal
- **`Sep24InteractiveResponse`** - Methods: getStatusEnum, isTerminal
- **`Sep24FeeResponse`** - Methods: getStatusEnum, isTerminal
- **`Sep24TransactionResponse`** - Methods: getStatusEnum, isTerminal
- **`Sep24TransactionsResponse`** - Methods: getStatusEnum, isTerminal
- **`instead`** - Methods: getStatusEnum, isTerminal
- **`Sep24Transaction`** - Methods: getStatusEnum, isTerminal
- **`instead`** - Methods: getStatusEnum, isTerminal
- **`Sep24FeeDetails`**
- **`Sep24FeeDetail`**
- **`Sep24Refunds`**
- **`Sep24RefundPayment`**
- **`Sep24Service`** - Methods: fromDomain, info, fee, deposit, withdraw, transactions, transaction, buildHeaders, parseErrorMessage, parseErrorType
- **`Sep24TransactionStatus`** - Methods: fromValue, isTerminal
- **`Sep24AuthenticationRequiredException`** - Methods: toString
- **`for`** - Methods: toString
- **`to`** - Methods: toString
- **`Sep24Exception`** - Methods: toString
- **`Sep24InvalidRequestException`** - Methods: toString
- **`Sep24ServerErrorException`** - Methods: toString
- **`Sep24TransactionNotFoundException`** - Methods: toString

### Test Coverage

**Tests:** 74 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/sep/sep24/Sep24IntegrationTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep24/Sep24ExceptionsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep24/Sep24ServiceTest.kt`

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Deposit | 100.0% | N/A | 13 | 13 |
| Info | 100.0% | N/A | 1 | 1 |
| Withdraw | 100.0% | N/A | 14 | 14 |

## Detailed Field Comparison

### Deposit

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset_code` |  | ✅ | `assetCode` | The code of the stellar asset the user wants to receive for their deposit with the anchor. The va... |
| `asset_issuer` |  | ✅ | `assetIssuer` | (optional) The issuer of the stellar asset the user wants to receive for their deposit with the a... |
| `source_asset` |  | ✅ | `sourceAsset` | (optional) The asset user wants to send. Note, that this is the asset user initially holds (off-c... |
| `amount` |  | ✅ | `amount` | (optional) Amount of asset requested to deposit. If this is not provided it will be collected in ... |
| `quote_id` |  | ✅ | `quoteId` | (optional) The `id` returned from a `SEP-38 POST /quote` response. If this parameter is provided ... |
| `account` |  | ✅ | `account` | (optional) The classic account, contract account or muxed account ID of the user that wants to de... |
| `memo_type` |  | ✅ | `memoType` | (optional) Type of memo that anchor should attach to the Stellar transaction, one of `text`, `id`... |
| `memo` |  | ✅ | `memo` | (optional) Value of memo to attach to transaction, for `hash` this should be base64-encoded. Beca... |
| `wallet_name` |  | ✅ | `walletName` | (**deprecated**,optional) In communications / pages about the deposit, anchor should display the ... |
| `wallet_url` |  | ✅ | `walletUrl` | (**deprecated**,optional) Anchor should link to this when notifying the user that the transaction... |
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en` if not specified or if the specified language is not supported. Langu... |
| `claimable_balance_supported` |  | ✅ | `claimableBalanceSupported` | (optional) True if the client supports receiving deposit transactions as a claimable balance, fal... |
| `customer_id` |  | ✅ | `customerId` | (optional) id of an off-chain account (managed by the anchor) associated with this user's Stellar... |

### Info

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en` if not specified or the if the specified language is not supported. L... |

### Withdraw

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset_code` |  | ✅ | `assetCode` | Code of the asset the user wants to withdraw. The value passed must match one of the codes listed... |
| `asset_issuer` |  | ✅ | `assetIssuer` | (optional) The issuer of the stellar asset the user wants to withdraw with the anchor. If `asset_... |
| `destination_asset` |  | ✅ | `destinationAsset` | (optional) The asset user wants to receive. It's an off-chain or fiat asset. If this is not provi... |
| `amount` |  | ✅ | `amount` | (optional) Amount of asset requested to withdraw. If this is not provided it will be collected in... |
| `quote_id` |  | ✅ | `quoteId` | (optional) The `id` returned from a `SEP-38 POST /quote` response. If this parameter is provided ... |
| `account` |  | ✅ | `account` | (optional) The classic account, contract account, or muxed account the client will use as the sou... |
| `memo` |  | ✅ | `memo` | (**deprecated**, optional) This field was originally intended to differentiate users of the same ... |
| `memo_type` |  | ✅ | `memoType` | (**deprecated**, optional) Type of `memo`. One of `text`, `id` or `hash`. Deprecated because memo... |
| `wallet_name` |  | ✅ | `walletName` | (**deprecated**,optional) In communications / pages about the withdrawal, anchor should display t... |
| `wallet_url` |  | ✅ | `walletUrl` | (**deprecated**,optional) Anchor can show this to the user when referencing the wallet involved i... |
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en` if not specified or if the specified language is not supported. Langu... |
| `refund_memo` |  | ✅ | `refundMemo` | (optional) The memo the anchor must use when sending refund payments back to the user. If not spe... |
| `refund_memo_type` |  | ✅ | `refundMemoType` | (optional) The type of the `refund_memo`. Can be `id`, `text`, or `hash`. See the [memos](https://developers.stellar.org/docs/encyclopedia/memos) documentation for more information. If specified, `refund_memo` must also be specified. |
| `customer_id` |  | ✅ | `customerId` | (optional) id of an off-chain account (managed by the anchor) associated with this user's Stellar... |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0024](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0024.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0024`
