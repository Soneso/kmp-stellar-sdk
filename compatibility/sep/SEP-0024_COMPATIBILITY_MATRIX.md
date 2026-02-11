# SEP-0024 (Hosted Deposit and Withdrawal) Compatibility Matrix

**Generated:** 2026-02-11 15:11:22

**SEP Version:** 3.8.0<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0024.md

## SEP Summary

This SEP defines the standard way for anchors and wallets to interact on behalf of users. This improves user experience by allowing wallets and other clients to interact with anchors directly without the user needing to leave the wallet to go to the anchor's site. It supports interactive deposit and withdrawal flows with KYC handling, transaction status monitoring, and history viewing.

## Overall Coverage

**Total Coverage:** 100.0% (128/128 fields)

- ✅ **Implemented:** 128/128
- ❌ **Not Implemented:** 0/128

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24Service.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24Requests.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24Responses.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24TransactionStatus.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24AuthenticationRequiredException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24InvalidRequestException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24ServerErrorException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep24/exceptions/Sep24TransactionNotFoundException.kt`

### Key Classes

- **`Sep24Service`** - Methods: fromDomain, info, fee, deposit, withdraw, transactions, transaction, pollTransaction
- **`Sep24DepositRequest`**
- **`Sep24WithdrawRequest`**
- **`Sep24FeeRequest`**
- **`Sep24TransactionsRequest`**
- **`Sep24TransactionRequest`**
- **`Sep24InfoResponse`**
- **`Sep24AssetInfo`**
- **`Sep24FeeEndpointInfo`**
- **`Sep24Features`**
- **`Sep24InteractiveResponse`**
- **`Sep24FeeResponse`**
- **`Sep24TransactionResponse`**
- **`Sep24TransactionsResponse`**
- **`Sep24Transaction`**
- **`Sep24FeeDetails`**
- **`Sep24FeeDetail`**
- **`Sep24Refunds`**
- **`Sep24RefundPayment`**
- **`Sep24TransactionStatus`**
- **`Sep24Exception`**
- **`Sep24AuthenticationRequiredException`**
- **`Sep24InvalidRequestException`**
- **`Sep24ServerErrorException`**
- **`Sep24TransactionNotFoundException`**

### Test Coverage

**Tests:** 47 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24ServiceTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep24/Sep24IntegrationTest.kt`

## Coverage by Section

| Section | Coverage | Implemented | Total |
|---------|----------|-------------|-------|
| Asset Info Fields | 100.0% | 6 | 6 |
| Deposit Endpoint | 100.0% | 1 | 1 |
| Deposit Request Fields | 100.0% | 13 | 13 |
| Features Fields | 100.0% | 2 | 2 |
| Fee Detail Fields | 100.0% | 3 | 3 |
| Fee Details Fields | 100.0% | 3 | 3 |
| Fee Endpoint | 100.0% | 1 | 1 |
| Fee Endpoint Info Fields | 100.0% | 2 | 2 |
| Fee Request Fields | 100.0% | 4 | 4 |
| Fee Response Fields | 100.0% | 1 | 1 |
| Info Endpoint | 100.0% | 1 | 1 |
| Info Request Parameters | 100.0% | 1 | 1 |
| Info Response Fields | 100.0% | 4 | 4 |
| Interactive Response Fields | 100.0% | 3 | 3 |
| Refund Payment Fields | 100.0% | 4 | 4 |
| Refunds Fields | 100.0% | 3 | 3 |
| Transaction Endpoint | 100.0% | 1 | 1 |
| Transaction Fields (Deposit-specific) | 100.0% | 3 | 3 |
| Transaction Fields (Shared) | 100.0% | 25 | 25 |
| Transaction Fields (Withdraw-specific) | 100.0% | 3 | 3 |
| Transaction Request Fields | 100.0% | 4 | 4 |
| Transaction Response Fields | 100.0% | 1 | 1 |
| Transaction Status Values | 100.0% | 16 | 16 |
| Transactions Endpoint | 100.0% | 1 | 1 |
| Transactions Request Fields | 100.0% | 6 | 6 |
| Transactions Response Fields | 100.0% | 1 | 1 |
| Withdraw Endpoint | 100.0% | 1 | 1 |
| Withdraw Request Fields | 100.0% | 14 | 14 |

## Detailed Field Comparison

### Asset Info Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `enabled` | ✓ | ✅ | `Sep24AssetInfo.enabled` | Whether this asset is currently enabled for the operation |
| `min_amount` |  | ✅ | `Sep24AssetInfo.minAmount` | Minimum amount that can be deposited/withdrawn |
| `max_amount` |  | ✅ | `Sep24AssetInfo.maxAmount` | Maximum amount that can be deposited/withdrawn |
| `fee_fixed` |  | ✅ | `Sep24AssetInfo.feeFixed` | Fixed fee charged for the operation |
| `fee_percent` |  | ✅ | `Sep24AssetInfo.feePercent` | Percentage fee charged for the operation |
| `fee_minimum` |  | ✅ | `Sep24AssetInfo.feeMinimum` | Minimum fee charged regardless of amount |

### Deposit Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `deposit_endpoint` | ✓ | ✅ | `Sep24Service.deposit()` | POST /transactions/deposit/interactive - Initiates an interactive deposit flow |

### Deposit Request Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset_code` | ✓ | ✅ | `Sep24DepositRequest.assetCode` | Code of the Stellar asset to receive |
| `asset_issuer` |  | ✅ | `Sep24DepositRequest.assetIssuer` | Issuer of the Stellar asset to receive |
| `source_asset` |  | ✅ | `Sep24DepositRequest.sourceAsset` | Off-chain asset user will send in Asset Identification Format |
| `amount` |  | ✅ | `Sep24DepositRequest.amount` | Amount the user intends to deposit |
| `quote_id` |  | ✅ | `Sep24DepositRequest.quoteId` | SEP-38 quote ID for firm pricing |
| `account` |  | ✅ | `Sep24DepositRequest.account` | Stellar account (G...), muxed (M...), or contract (C...) that will receive funds |
| `memo` |  | ✅ | `Sep24DepositRequest.memo` | Memo to attach to the Stellar payment |
| `memo_type` |  | ✅ | `Sep24DepositRequest.memoType` | Type of memo: text, id, or hash |
| `wallet_name` |  | ✅ | `Sep24DepositRequest.walletName` | Name of the wallet application (deprecated) |
| `wallet_url` |  | ✅ | `Sep24DepositRequest.walletUrl` | URL of the wallet application (deprecated) |
| `lang` |  | ✅ | `Sep24DepositRequest.lang` | Language code (RFC 4646) for interactive UI |
| `claimable_balance_supported` |  | ✅ | `Sep24DepositRequest.claimableBalanceSupported` | Whether client supports receiving deposits as claimable balances |
| `customer_id` |  | ✅ | `Sep24DepositRequest.customerId` | SEP-12 customer ID if KYC already completed |

### Features Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `account_creation` |  | ✅ | `Sep24Features.accountCreation` | Whether the anchor can create Stellar accounts for users |
| `claimable_balances` |  | ✅ | `Sep24Features.claimableBalances` | Whether the anchor supports sending deposits as claimable balances |

### Fee Detail Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `name` | ✓ | ✅ | `Sep24FeeDetail.name` | Name of the fee component |
| `amount` | ✓ | ✅ | `Sep24FeeDetail.amount` | Amount of this fee component |
| `description` |  | ✅ | `Sep24FeeDetail.description` | Human-readable description of the fee |

### Fee Details Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `total` | ✓ | ✅ | `Sep24FeeDetails.total` | Total amount of fee applied |
| `asset` | ✓ | ✅ | `Sep24FeeDetails.asset` | Asset in which fee is applied in Asset Identification Format |
| `breakdown` |  | ✅ | `Sep24FeeDetails.breakdown` | Array of fee components |

### Fee Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `fee_endpoint` |  | ✅ | `Sep24Service.fee()` | GET /fee - Returns fee for a deposit or withdrawal (deprecated, use SEP-38 instead) |

### Fee Endpoint Info Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `enabled` | ✓ | ✅ | `Sep24FeeEndpointInfo.enabled` | Whether the /fee endpoint is available |
| `authentication_required` |  | ✅ | `Sep24FeeEndpointInfo.authenticationRequired` | Whether authentication is required to access the /fee endpoint |

### Fee Request Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `operation` | ✓ | ✅ | `Sep24FeeRequest.operation` | Type of operation: deposit or withdraw |
| `asset_code` | ✓ | ✅ | `Sep24FeeRequest.assetCode` | Code of the asset for the operation |
| `amount` | ✓ | ✅ | `Sep24FeeRequest.amount` | Amount for which to calculate the fee |
| `type` |  | ✅ | `Sep24FeeRequest.type` | Type of deposit or withdrawal method |

### Fee Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `fee` |  | ✅ | `Sep24FeeResponse.fee` | The fee amount as a string |

### Info Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `info_endpoint` | ✓ | ✅ | `Sep24Service.info()` | GET /info - Returns anchor capabilities and supported assets for deposit and withdrawal |

### Info Request Parameters

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `lang` |  | ✅ | `Sep24Service.info(lang)` | Language code (RFC 4646) for localized descriptions |

### Info Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `deposit` |  | ✅ | `Sep24InfoResponse.depositAssets` | Map of asset codes to deposit configuration |
| `withdraw` |  | ✅ | `Sep24InfoResponse.withdrawAssets` | Map of asset codes to withdrawal configuration |
| `fee` |  | ✅ | `Sep24InfoResponse.feeEndpoint` | Configuration for the optional /fee endpoint (deprecated) |
| `features` |  | ✅ | `Sep24InfoResponse.features` | Feature flags indicating anchor capabilities |

### Interactive Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `type` | ✓ | ✅ | `Sep24InteractiveResponse.type` | Always interactive_customer_info_needed |
| `url` | ✓ | ✅ | `Sep24InteractiveResponse.url` | URL to display in a webview for user interaction |
| `id` | ✓ | ✅ | `Sep24InteractiveResponse.id` | Anchor's internal ID for this transaction |

### Refund Payment Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` | ✓ | ✅ | `Sep24RefundPayment.id` | Payment ID (Stellar hash or off-chain reference) |
| `id_type` | ✓ | ✅ | `Sep24RefundPayment.idType` | stellar or external |
| `amount` | ✓ | ✅ | `Sep24RefundPayment.amount` | Amount sent back for this payment |
| `fee` | ✓ | ✅ | `Sep24RefundPayment.fee` | Fee charged for this refund payment |

### Refunds Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `amount_refunded` | ✓ | ✅ | `Sep24Refunds.amountRefunded` | Total amount refunded to the user |
| `amount_fee` | ✓ | ✅ | `Sep24Refunds.amountFee` | Total fee charged for processing refunds |
| `payments` | ✓ | ✅ | `Sep24Refunds.payments` | List of individual refund payments |

### Transaction Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `transaction_endpoint` | ✓ | ✅ | `Sep24Service.transaction()` | GET /transaction - Returns details for a single transaction |

### Transaction Fields (Deposit-specific)

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `deposit_memo` |  | ✅ | `Sep24Transaction.depositMemo` | Memo used to transfer asset to the Stellar address |
| `deposit_memo_type` |  | ✅ | `Sep24Transaction.depositMemoType` | Type of deposit_memo |
| `claimable_balance_id` |  | ✅ | `Sep24Transaction.claimableBalanceId` | ID of the Claimable Balance if used |

### Transaction Fields (Shared)

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` | ✓ | ✅ | `Sep24Transaction.id` | Unique anchor-generated id for the deposit/withdrawal |
| `kind` | ✓ | ✅ | `Sep24Transaction.kind` | deposit or withdrawal |
| `status` | ✓ | ✅ | `Sep24Transaction.status` | Processing status of deposit/withdrawal |
| `status_eta` |  | ✅ | `Sep24Transaction.statusEta` | Estimated seconds until status change |
| `kyc_verified` |  | ✅ | `Sep24Transaction.kycVerified` | Whether anchor has verified user's KYC information |
| `more_info_url` |  | ✅ | `Sep24Transaction.moreInfoUrl` | URL for additional transaction information |
| `amount_in` |  | ✅ | `Sep24Transaction.amountIn` | Amount received by anchor at start of transaction |
| `amount_in_asset` |  | ✅ | `Sep24Transaction.amountInAsset` | Asset received by anchor in Asset Identification Format |
| `amount_out` |  | ✅ | `Sep24Transaction.amountOut` | Amount sent by anchor to user at end of transaction |
| `amount_out_asset` |  | ✅ | `Sep24Transaction.amountOutAsset` | Asset delivered to user in Asset Identification Format |
| `amount_fee` |  | ✅ | `Sep24Transaction.amountFee` | Amount of fee charged (deprecated, use fee_details) |
| `amount_fee_asset` |  | ✅ | `Sep24Transaction.amountFeeAsset` | Asset in which fees are calculated (deprecated, use fee_details) |
| `fee_details` |  | ✅ | `Sep24Transaction.feeDetails` | Detailed fee breakdown |
| `quote_id` |  | ✅ | `Sep24Transaction.quoteId` | SEP-38 quote ID if used |
| `started_at` |  | ✅ | `Sep24Transaction.startedAt` | UTC ISO 8601 timestamp when transaction started |
| `completed_at` |  | ✅ | `Sep24Transaction.completedAt` | UTC ISO 8601 timestamp when transaction completed |
| `updated_at` |  | ✅ | `Sep24Transaction.updatedAt` | UTC ISO 8601 timestamp when transaction was last updated |
| `user_action_required_by` |  | ✅ | `Sep24Transaction.userActionRequiredBy` | UTC ISO 8601 timestamp by when user action is required |
| `stellar_transaction_id` |  | ✅ | `Sep24Transaction.stellarTransactionId` | Stellar transaction hash |
| `external_transaction_id` |  | ✅ | `Sep24Transaction.externalTransactionId` | External payment system transaction ID |
| `message` |  | ✅ | `Sep24Transaction.message` | Human-readable explanation of transaction status |
| `refunded` |  | ✅ | `Sep24Transaction.refunded` | Whether transaction was refunded (deprecated, use refunds object) |
| `refunds` |  | ✅ | `Sep24Transaction.refunds` | Refund information |
| `from` |  | ✅ | `Sep24Transaction.from` | Sending address |
| `to` |  | ✅ | `Sep24Transaction.to` | Receiving address |

### Transaction Fields (Withdraw-specific)

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `withdraw_anchor_account` |  | ✅ | `Sep24Transaction.withdrawAnchorAccount` | Anchor's Stellar account that user transferred asset to |
| `withdraw_memo` |  | ✅ | `Sep24Transaction.withdrawMemo` | Memo used when user transferred to withdraw_anchor_account |
| `withdraw_memo_type` |  | ✅ | `Sep24Transaction.withdrawMemoType` | Type of withdraw_memo |

### Transaction Request Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` |  | ✅ | `Sep24TransactionRequest.id` | Anchor's unique identifier for the transaction |
| `stellar_transaction_id` |  | ✅ | `Sep24TransactionRequest.stellarTransactionId` | Stellar transaction hash for the on-chain payment |
| `external_transaction_id` |  | ✅ | `Sep24TransactionRequest.externalTransactionId` | External payment system transaction identifier |
| `lang` |  | ✅ | `Sep24TransactionRequest.lang` | Language code (RFC 4646) for returned messages |

### Transaction Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `transaction` | ✓ | ✅ | `Sep24TransactionResponse.transaction` | The transaction details |

### Transaction Status Values

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `incomplete` |  | ✅ | `Sep24TransactionStatus.INCOMPLETE` | Not enough information to initiate transaction |
| `pending_user_transfer_start` |  | ✅ | `Sep24TransactionStatus.PENDING_USER_TRANSFER_START` | User has not yet initiated transfer to anchor |
| `pending_user_transfer_complete` |  | ✅ | `Sep24TransactionStatus.PENDING_USER_TRANSFER_COMPLETE` | Off-chain funds available for pickup (withdrawal only) |
| `pending_external` |  | ✅ | `Sep24TransactionStatus.PENDING_EXTERNAL` | Submitted to external network, not yet confirmed |
| `pending_anchor` |  | ✅ | `Sep24TransactionStatus.PENDING_ANCHOR` | Being processed internally by anchor |
| `on_hold` |  | ✅ | `Sep24TransactionStatus.ON_HOLD` | On hold for additional checks |
| `pending_stellar` |  | ✅ | `Sep24TransactionStatus.PENDING_STELLAR` | Submitted to Stellar network, not yet confirmed |
| `pending_trust` |  | ✅ | `Sep24TransactionStatus.PENDING_TRUST` | User must add trustline for the asset |
| `pending_user` |  | ✅ | `Sep24TransactionStatus.PENDING_USER` | User must take additional action |
| `completed` |  | ✅ | `Sep24TransactionStatus.COMPLETED` | Transaction completed successfully (terminal) |
| `refunded` |  | ✅ | `Sep24TransactionStatus.REFUNDED` | Transaction refunded (terminal) |
| `expired` |  | ✅ | `Sep24TransactionStatus.EXPIRED` | Transaction expired (terminal) |
| `no_market` |  | ✅ | `Sep24TransactionStatus.NO_MARKET` | No satisfactory market available (terminal) |
| `too_small` |  | ✅ | `Sep24TransactionStatus.TOO_SMALL` | Amount less than min_amount (terminal) |
| `too_large` |  | ✅ | `Sep24TransactionStatus.TOO_LARGE` | Amount exceeded max_amount (terminal) |
| `error` |  | ✅ | `Sep24TransactionStatus.ERROR` | Error occurred (terminal) |

### Transactions Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `transactions_endpoint` | ✓ | ✅ | `Sep24Service.transactions()` | GET /transactions - Returns transaction history for the authenticated account |

### Transactions Request Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset_code` | ✓ | ✅ | `Sep24TransactionsRequest.assetCode` | Code of the asset of interest |
| `no_older_than` |  | ✅ | `Sep24TransactionsRequest.noOlderThan` | UTC ISO 8601 datetime - only return transactions on or after this date |
| `limit` |  | ✅ | `Sep24TransactionsRequest.limit` | Maximum number of transactions to return |
| `kind` |  | ✅ | `Sep24TransactionsRequest.kind` | Filter by transaction kind: deposit or withdrawal |
| `paging_id` |  | ✅ | `Sep24TransactionsRequest.pagingId` | Pagination cursor - return transactions prior to this ID |
| `lang` |  | ✅ | `Sep24TransactionsRequest.lang` | Language code (RFC 4646) for returned messages |

### Transactions Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `transactions` | ✓ | ✅ | `Sep24TransactionsResponse.transactions` | List of transaction details |

### Withdraw Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `withdraw_endpoint` | ✓ | ✅ | `Sep24Service.withdraw()` | POST /transactions/withdraw/interactive - Initiates an interactive withdrawal flow |

### Withdraw Request Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset_code` | ✓ | ✅ | `Sep24WithdrawRequest.assetCode` | Code of the Stellar asset to withdraw |
| `asset_issuer` |  | ✅ | `Sep24WithdrawRequest.assetIssuer` | Issuer of the Stellar asset to withdraw |
| `destination_asset` |  | ✅ | `Sep24WithdrawRequest.destinationAsset` | Off-chain asset user will receive in Asset Identification Format |
| `amount` |  | ✅ | `Sep24WithdrawRequest.amount` | Amount the user intends to withdraw |
| `quote_id` |  | ✅ | `Sep24WithdrawRequest.quoteId` | SEP-38 quote ID for firm pricing |
| `account` |  | ✅ | `Sep24WithdrawRequest.account` | Stellar account (G...), muxed (M...), or contract (C...) that will send funds |
| `memo` |  | ✅ | `Sep24WithdrawRequest.memo` | Deprecated: Use sub value in SEP-10 JWT |
| `memo_type` |  | ✅ | `Sep24WithdrawRequest.memoType` | Deprecated: Use sub value in SEP-10 JWT |
| `wallet_name` |  | ✅ | `Sep24WithdrawRequest.walletName` | Name of the wallet application (deprecated) |
| `wallet_url` |  | ✅ | `Sep24WithdrawRequest.walletUrl` | URL of the wallet application (deprecated) |
| `lang` |  | ✅ | `Sep24WithdrawRequest.lang` | Language code (RFC 4646) for interactive UI |
| `refund_memo` |  | ✅ | `Sep24WithdrawRequest.refundMemo` | Memo to use if anchor needs to refund the withdrawal |
| `refund_memo_type` |  | ✅ | `Sep24WithdrawRequest.refundMemoType` | Type of refund memo: text, id, or hash |
| `customer_id` |  | ✅ | `Sep24WithdrawRequest.customerId` | SEP-12 customer ID if KYC already completed |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0024](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0024.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0024`
