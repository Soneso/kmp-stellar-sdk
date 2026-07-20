# SEP-0031 (Cross-Border Payments API) Compatibility Matrix

**Generated:** 2026-07-20 11:43:11

**SEP Version:** 3.1.0  
**SEP Status:** Active  
**SDK Version:** 1.10.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md

## SEP Summary

Cross-Border Payments: a programmatic API for Sending Anchors to deliver on-chain payments to Receiving Anchors, who then settle the off-chain leg with the Receiving Client. Covers asset discovery, SEP-12 KYC linkage, optional SEP-38 quotes, status polling, and refunds.

## Overall Coverage

**Total Coverage:** 100.0% (71/71 fields)

- ✅ **Implemented:** 71/71
- ❌ **Not Implemented:** 0/71

**Required Fields:** 100.0% (26/26)

**Optional Fields:** 100.0% (45/45)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Fee Details Breakdown Fields | 100.0% | 2/2 | 3 | 3 |
| Fee Details Fields | 100.0% | 2/2 | 3 | 3 |
| Info Response Fields | 100.0% | 1/1 | 1 | 1 |
| POST /transactions Request Fields | 100.0% | 3/3 | 12 | 12 |
| POST /transactions Response Fields | 100.0% | 1/1 | 4 | 4 |
| Receive Asset Info Fields | 100.0% | 1/1 | 11 | 11 |
| Refund Payment Fields | 100.0% | 3/3 | 3 | 3 |
| Refunds Fields | 100.0% | 3/3 | 3 | 3 |
| SEP-12 Types Info Fields | 100.0% | 2/2 | 2 | 2 |
| Service Endpoints | 100.0% | 5/5 | 5 | 5 |
| Transaction Response Fields | 100.0% | 3/3 | 24 | 24 |

## Detailed Field Comparison

### Fee Details Breakdown Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `name` | ✓ | ✅ | `Sep31FeeDetailsDetails.name` | Name of the fee line item |
| `amount` | ✓ | ✅ | `Sep31FeeDetailsDetails.amount` | Amount of this fee line item |
| `description` |  | ✅ | `Sep31FeeDetailsDetails.description` | Optional description of the fee line item |

### Fee Details Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `total` | ✓ | ✅ | `Sep31FeeDetails.total` | Aggregate fee amount charged |
| `asset` | ✓ | ✅ | `Sep31FeeDetails.asset` | SEP-38 asset of the fee |
| `details` |  | ✅ | `Sep31FeeDetails.details` | Array of fee line items |

### Info Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `receive` | ✓ | ✅ | `Sep31InfoResponse.receiveAssets` | Map of asset code to per-asset receive configuration |

### POST /transactions Request Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `amount` | ✓ | ✅ | `Sep31PostTransactionsRequest.amount` | Amount of the Stellar asset to send |
| `asset_code` | ✓ | ✅ | `Sep31PostTransactionsRequest.assetCode` | Code of the Stellar asset being sent |
| `asset_issuer` |  | ✅ | `Sep31PostTransactionsRequest.assetIssuer` | Issuer of the Stellar asset |
| `destination_asset` |  | ✅ | `Sep31PostTransactionsRequest.destinationAsset` | SEP-38 off-chain asset to deliver |
| `quote_id` |  | ✅ | `Sep31PostTransactionsRequest.quoteId` | SEP-38 firm quote id |
| `sender_id` |  | ✅ | `Sep31PostTransactionsRequest.senderId` | SEP-12 customer id of the Sending Client |
| `receiver_id` |  | ✅ | `Sep31PostTransactionsRequest.receiverId` | SEP-12 customer id of the Receiving Client |
| `fields` |  | ✅ | `Sep31PostTransactionsRequest.fields` | Deprecated per-transaction field values |
| `lang` |  | ✅ | `Sep31PostTransactionsRequest.lang` | ISO 639-1 language code |
| `refund_memo` |  | ✅ | `Sep31PostTransactionsRequest.refundMemo` | Memo to attach when issuing refunds |
| `refund_memo_type` |  | ✅ | `Sep31PostTransactionsRequest.refundMemoType` | Type of refund_memo (id, text, or hash) |
| `funding_method` | ✓ | ✅ | `Sep31PostTransactionsRequest.fundingMethod` | Anchor funding method to use for delivery |

### POST /transactions Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` | ✓ | ✅ | `Sep31PostTransactionsResponse.id` | Persistent transaction identifier |
| `stellar_account_id` |  | ✅ | `Sep31PostTransactionsResponse.stellarAccountId` | Receiving Anchor Stellar account to pay |
| `stellar_memo_type` |  | ✅ | `Sep31PostTransactionsResponse.stellarMemoType` | Type of stellar_memo (text, hash, or id) |
| `stellar_memo` |  | ✅ | `Sep31PostTransactionsResponse.stellarMemo` | Memo to attach to the on-chain payment |

### Receive Asset Info Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sep12` |  | ✅ | `Sep31ReceiveAssetInfo.sep12Info` | Per-asset SEP-12 customer type requirements (deprecated) |
| `min_amount` |  | ✅ | `Sep31ReceiveAssetInfo.minAmount` | Minimum amount the anchor accepts |
| `max_amount` |  | ✅ | `Sep31ReceiveAssetInfo.maxAmount` | Maximum amount the anchor accepts |
| `fee_fixed` |  | ✅ | `Sep31ReceiveAssetInfo.feeFixed` | Fixed fee charged by the anchor |
| `fee_percent` |  | ✅ | `Sep31ReceiveAssetInfo.feePercent` | Percentage fee charged by the anchor |
| `sender_sep12_type` |  | ✅ | `Sep31ReceiveAssetInfo.senderSep12Type` | Deprecated sender SEP-12 type identifier |
| `receiver_sep12_type` |  | ✅ | `Sep31ReceiveAssetInfo.receiverSep12Type` | Deprecated receiver SEP-12 type identifier |
| `fields` |  | ✅ | `Sep31ReceiveAssetInfo.fields` | Deprecated per-transaction field requirements |
| `quotes_supported` |  | ✅ | `Sep31ReceiveAssetInfo.quotesSupported` | Whether anchor accepts an optional SEP-38 quote_id |
| `quotes_required` |  | ✅ | `Sep31ReceiveAssetInfo.quotesRequired` | Whether anchor requires a SEP-38 quote_id |
| `funding_methods` | ✓ | ✅ | `Sep31ReceiveAssetInfo.fundingMethods` | Methods the anchor uses to deliver the off-chain asset |

### Refund Payment Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` | ✓ | ✅ | `Sep31RefundPayment.id` | Stellar transaction hash of the refund payment |
| `amount` | ✓ | ✅ | `Sep31RefundPayment.amount` | Amount returned in this refund payment |
| `fee` | ✓ | ✅ | `Sep31RefundPayment.fee` | Fee charged for processing this refund payment |

### Refunds Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `amount_refunded` | ✓ | ✅ | `Sep31Refunds.amountRefunded` | Total amount refunded across all payments |
| `amount_fee` | ✓ | ✅ | `Sep31Refunds.amountFee` | Total fee charged for processing the refunds |
| `payments` | ✓ | ✅ | `Sep31Refunds.payments` | Array of individual refund payments |

### SEP-12 Types Info Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sender` | ✓ | ✅ | `Sep31Sep12TypesInfo.senderTypes` | SEP-12 sender customer types map |
| `receiver` | ✓ | ✅ | `Sep31Sep12TypesInfo.receiverTypes` | SEP-12 receiver customer types map |

### Service Endpoints

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `GET /info` | ✓ | ✅ | `Sep31Service.info` | Discover assets, limits, fees, and KYC requirements |
| `POST /transactions` | ✓ | ✅ | `Sep31Service.postTransactions` | Initiate a cross-border payment |
| `GET /transactions/:id` | ✓ | ✅ | `Sep31Service.getTransaction` | Fetch transaction status |
| `PATCH /transactions/:id` | ✓ | ✅ | `Sep31Service.patchTransaction` | Update transaction info (deprecated) |
| `PUT /transactions/:id/callback` | ✓ | ✅ | `Sep31Service.putTransactionCallback` | Register status callback URL |

### Transaction Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` | ✓ | ✅ | `Sep31TransactionResponse.id` | Transaction identifier matching POST /transactions |
| `status` | ✓ | ✅ | `Sep31TransactionResponse.status` | Lifecycle status of the transaction |
| `status_eta` |  | ✅ | `Sep31TransactionResponse.statusEta` | Estimated seconds until next status change |
| `status_message` |  | ✅ | `Sep31TransactionResponse.statusMessage` | Human-readable status description |
| `amount_in` |  | ✅ | `Sep31TransactionResponse.amountIn` | Amount received by the Receiving Anchor |
| `amount_in_asset` |  | ✅ | `Sep31TransactionResponse.amountInAsset` | SEP-38 asset of the inbound amount |
| `amount_out` |  | ✅ | `Sep31TransactionResponse.amountOut` | Amount sent to the Receiving Client |
| `amount_out_asset` |  | ✅ | `Sep31TransactionResponse.amountOutAsset` | SEP-38 asset of the delivered amount |
| `amount_fee` |  | ✅ | `Sep31TransactionResponse.amountFee` | Deprecated aggregate fee charged |
| `amount_fee_asset` |  | ✅ | `Sep31TransactionResponse.amountFeeAsset` | Deprecated fee asset |
| `fee_details` | ✓ | ✅ | `Sep31TransactionResponse.feeDetails` | Structured fee breakdown |
| `quote_id` |  | ✅ | `Sep31TransactionResponse.quoteId` | SEP-38 quote id used by this transaction |
| `stellar_account_id` |  | ✅ | `Sep31TransactionResponse.stellarAccountId` | Receiving Anchor Stellar account |
| `stellar_memo_type` |  | ✅ | `Sep31TransactionResponse.stellarMemoType` | Type of stellar_memo |
| `stellar_memo` |  | ✅ | `Sep31TransactionResponse.stellarMemo` | Memo attached to the on-chain payment |
| `started_at` |  | ✅ | `Sep31TransactionResponse.startedAt` | UTC ISO 8601 transaction creation timestamp |
| `updated_at` |  | ✅ | `Sep31TransactionResponse.updatedAt` | UTC ISO 8601 last status transition timestamp |
| `completed_at` |  | ✅ | `Sep31TransactionResponse.completedAt` | UTC ISO 8601 completion timestamp |
| `stellar_transaction_id` |  | ✅ | `Sep31TransactionResponse.stellarTransactionId` | Stellar transaction hash of the on-chain payment |
| `external_transaction_id` |  | ✅ | `Sep31TransactionResponse.externalTransactionId` | External off-chain transaction identifier |
| `refunded` |  | ✅ | `Sep31TransactionResponse.refunded` | Deprecated full-refund flag |
| `refunds` |  | ✅ | `Sep31TransactionResponse.refunds` | Structured refund aggregate |
| `required_info_message` |  | ✅ | `Sep31TransactionResponse.requiredInfoMessage` | Message accompanying required_info_updates |
| `required_info_updates` |  | ✅ | `Sep31TransactionResponse.requiredInfoUpdates` | Fields requiring update from the Sending Anchor |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0031!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0031](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0031`
