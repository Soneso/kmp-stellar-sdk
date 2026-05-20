# SEP-0006 (Deposit and Withdrawal API) Compatibility Matrix

**Generated:** 2026-05-20 11:38:47

**SEP Version:** 4.3.0  
**SEP Status:** Active (Interactive components are deprecated in favor of SEP-24)  
**SDK Version:** 1.6.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0006.md

## SEP Summary

A programmatic API for anchors and wallets to handle deposits and withdrawals without the user leaving the wallet. For interactive flows, see SEP-24.

## Overall Coverage

**Total Coverage:** 100.0% (73/73 fields)

- ✅ **Implemented:** 73/73
- ❌ **Not Implemented:** 0/73

**Required Fields:** 100.0% (4/4)

**Optional Fields:** 100.0% (69/69)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Deposit | 100.0% | 1/1 | 16 | 16 |
| Deposit Exchange | 100.0% | 1/1 | 18 | 18 |
| Info | 100.0% | N/A | 1 | 1 |
| Withdraw | 100.0% | 1/1 | 18 | 18 |
| Withdraw Exchange | 100.0% | 1/1 | 20 | 20 |

## Detailed Field Comparison

### Deposit

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset_code` |  | ✅ | `assetCode` | The code of the on-chain asset the user wants to get from the Anchor after doing an off-chain dep... |
| `account` |  | ✅ | `account` | The classic account, contract account or muxed account ID of the user that wants to deposit to. T... |
| `funding_method` | ✓ | ✅ | `fundingMethod` | A method supported by the Anchor for transferring or settling assets. Must match one of the value... |
| `memo_type` |  | ✅ | `memoType` | (optional) Type of memo that the anchor should attach to the Stellar payment transaction, one of ... |
| `memo` |  | ✅ | `memo` | (optional) Value of memo to attach to transaction, for `hash` this should be base64-encoded. Beca... |
| `email_address` |  | ✅ | `emailAddress` | (optional) Email address of depositor. If desired, an anchor can use this to send email updates t... |
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en` if not specified or if the specified language is not supported. Langu... |
| `on_change_callback` |  | ✅ | `onChangeCallback` | (optional) A URL that the anchor should `POST` a JSON message to when the `status` property of th... |
| `amount` |  | ✅ | `amount` | (optional) The amount of the asset the user would like to deposit with the anchor. This field may... |
| `country_code` |  | ✅ | `countryCode` | (optional) The [ISO 3166-1 alpha-3](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-3) code of the user's current address. This field may be necessary for the anchor to determine what KYC information is necessary to collect. |
| `claimable_balance_supported` |  | ✅ | `claimableBalanceSupported` | (optional) `true` if the client supports receiving deposit transactions as a claimable balance, `... |
| `customer_id` |  | ✅ | `customerId` | (optional) id of an off-chain account (managed by the anchor) associated with this user's Stellar... |
| `location_id` |  | ✅ | `locationId` | (optional) optional) id of the chosen location to drop off cash |
| `type` |  | ✅ | `type` | (**Deprecated** in favor of `funding_method`) Type of deposit. If the Anchor supports multiple de... |
| `wallet_name` |  | ✅ | `walletName` | (**Deprecated**, optional) In communications / pages about the deposit, anchor should display the... |
| `wallet_url` |  | ✅ | `walletUrl` | (**Deprecated**, optional) Anchor should link to this when notifying the user that the transactio... |

### Deposit Exchange

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `destination_asset` |  | ✅ | `destinationAsset` | The code of the on-chain asset the user wants to get from the Anchor after doing an off-chain dep... |
| `source_asset` |  | ✅ | `sourceAsset` | The off-chain asset the Anchor will receive from the user. The value must match one of the `asset... |
| `amount` |  | ✅ | `amount` | The amount of the `source_asset` the user would like to deposit to the anchor's off-chain account... |
| `funding_method` | ✓ | ✅ | `fundingMethod` | A method supported by the Anchor for transferring or settling assets. Must match one of the value... |
| `account` |  | ✅ | `account` | The classic account, muxed account, or contract account where the clients wants the deposit to be... |
| `quote_id` |  | ✅ | `quoteId` | (optional) The `id` returned from a `SEP-38 POST /quote` response. If this parameter is provided ... |
| `memo_type` |  | ✅ | `memoType` | (optional) Type of memo that the anchor should attach to the Stellar payment transaction, one of ... |
| `memo` |  | ✅ | `memo` | (optional) Value of memo to attach to transaction, for `hash` this should be base64-encoded. Beca... |
| `email_address` |  | ✅ | `emailAddress` | (optional) Email address of depositor. If desired, an anchor can use this to send email updates t... |
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en` if not specified or if the specified language is not supported. Langu... |
| `on_change_callback` |  | ✅ | `onChangeCallback` | (optional) A URL that the anchor should `POST` a JSON message to when the `status` property of th... |
| `country_code` |  | ✅ | `countryCode` | (optional) The [ISO 3166-1 alpha-3](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-3) code of the user's current address. This field may be necessary for the anchor to determine what KYC information is necessary to collect. |
| `claimable_balance_supported` |  | ✅ | `claimableBalanceSupported` | (optional) `true` if the client supports receiving deposit transactions as a claimable balance, `... |
| `customer_id` |  | ✅ | `customerId` | (optional) id of an off-chain account (managed by the anchor) associated with this user's Stellar... |
| `location_id` |  | ✅ | `locationId` | (optional) optional) id of the chosen location to drop off cash |
| `type` |  | ✅ | `type` | (**Deprecated** in favor of `funding_method`) Type of deposit. If the Anchor supports multiple de... |
| `wallet_name` |  | ✅ | `walletName` | (**Deprecated**, optional) In communications / pages about the deposit, anchor should display the... |
| `wallet_url` |  | ✅ | `walletUrl` | (**Deprecated**, optional) Anchor should link to this when notifying the user that the transactio... |

### Info

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en` if not specified or if the specified language is not supported. Langu... |

### Withdraw

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset_code` |  | ✅ | `assetCode` | Code of the on-chain asset the user wants to withdraw. The value passed must match one of the cod... |
| `funding_method` | ✓ | ✅ | `fundingMethod` | A method supported by the Anchor for transferring or settling assets. Must match one of the value... |
| `account` |  | ✅ | `account` | (optional) The classic account, muxed account, or contract account that the client will use as th... |
| `memo` |  | ✅ | `memo` | (optional) This field should only be used if SEP-10 or SEP-45 authentication is not. It was origi... |
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en` if not specified or if the specified language is not supported. Langu... |
| `on_change_callback` |  | ✅ | `onChangeCallback` | (optional) A URL that the anchor should `POST` a JSON message to when the `status` property of th... |
| `amount` |  | ✅ | `amount` | (optional) The amount of the asset the user would like to withdraw. This field may be necessary f... |
| `country_code` |  | ✅ | `countryCode` | (optional) The [ISO 3166-1 alpha-3](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-3) code of the user's current address. This field may be necessary for the anchor to determine what KYC information is necessary to collect. |
| `refund_memo` |  | ✅ | `refundMemo` | (optional) The memo the anchor must use when sending refund payments back to the user. If not spe... |
| `refund_memo_type` |  | ✅ | `refundMemoType` | (optional) The type of the `refund_memo`. Can be `id`, `text`, or `hash`. See the [memos](https://developers.stellar.org/docs/encyclopedia/memos) documentation for more information. If specified, `refund_memo` must also be specified. |
| `customer_id` |  | ✅ | `customerId` | (optional) id of an off-chain account (managed by the anchor) associated with this user's Stellar... |
| `location_id` |  | ✅ | `locationId` | (optional) id of the chosen location to pick up cash |
| `type` |  | ✅ | `type` | (**Deprecated** in favor of `funding_method`) Type of withdrawal. Can be: `crypto`, `bank_account... |
| `dest` |  | ✅ | `dest` | (**Deprecated**, [see note below](#dest--dest_extra-parameters)) The account that the user wants ... |
| `dest_extra` |  | ✅ | `destExtra` | (**Deprecated**, [see note below](#dest--dest_extra-parameters), optional) Extra information to s... |
| `memo_type` |  | ✅ | `memoType` | (**Deprecated**, optional) Type of `memo`. One of `text`, `id` or `hash`. Deprecated because memo... |
| `wallet_name` |  | ✅ | `walletName` | (**Deprecated**, optional) In communications / pages about the withdrawal, anchor should display ... |
| `wallet_url` |  | ✅ | `walletUrl` | (**Deprecated**, optional) Anchor can show this to the user when referencing the wallet involved ... |

### Withdraw Exchange

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `source_asset` |  | ✅ | `sourceAsset` | Code of the on-chain asset the user wants to withdraw. The value passed must match one of the cod... |
| `destination_asset` |  | ✅ | `destinationAsset` | The off-chain asset the Anchor will deliver to the user's account. The value must match one of th... |
| `amount` |  | ✅ | `amount` | The amount of the on-chain asset (`source_asset`) the user would like to send to the anchor's Ste... |
| `funding_method` | ✓ | ✅ | `fundingMethod` | A method supported by the Anchor for transferring or settling assets. Must match one of the value... |
| `quote_id` |  | ✅ | `quoteId` | (optional) The `id` returned from a `SEP-38 POST /quote` response. If this parameter is provided and the Stellar transaction used to send the asset to the Anchor has a [`created_at`](https://developers.stellar.org/api/resources/transactions/object/) timestamp earlier than the quote's `expires_at` attribute, the Anchor should respect the conversion rate agreed in that quote. If the values of `destination_asset`, `source_asset`, `amount` and `funding_method` conflict with the ones used to create the [SEP-38] quote, this request should be rejected with a `400`. |
| `account` |  | ✅ | `account` | (optional) The classic account, contract account or muxed account of the user that wants to do th... |
| `memo` |  | ✅ | `memo` | (optional) This field should only be used if SEP-10 authentication is not. It was originally inte... |
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en` if not specified or if the specified language is not supported. Langu... |
| `on_change_callback` |  | ✅ | `onChangeCallback` | (optional) A URL that the anchor should `POST` a JSON message to when the `status` property of th... |
| `country_code` |  | ✅ | `countryCode` | (optional) The [ISO 3166-1 alpha-3](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-3) code of the user's current address. This field may be necessary for the anchor to determine what KYC information is necessary to collect. |
| `refund_memo` |  | ✅ | `refundMemo` | (optional) The memo the anchor must use when sending refund payments back to the user. If not spe... |
| `refund_memo_type` |  | ✅ | `refundMemoType` | (optional) The type of the `refund_memo`. Can be `id`, `text`, or `hash`. See the [memos](https://developers.stellar.org/docs/encyclopedia/memos) documentation for more information. If specified, `refund_memo` must also be specified. |
| `customer_id` |  | ✅ | `customerId` | (optional) id of an off-chain account (managed by the anchor) associated with this user's Stellar... |
| `location_id` |  | ✅ | `locationId` | (optional) id of the chosen location to pick up cash |
| `type` |  | ✅ | `type` | (**Deprecated** in favor of `funding_method`) Type of withdrawal. Can be: `crypto`, `bank_account... |
| `dest` |  | ✅ | `dest` | (**Deprecated**, [see note](#dest--dest_extra-parameters)) The account that the user wants to wit... |
| `dest_extra` |  | ✅ | `destExtra` | (**Deprecated**, [see note](#dest--dest_extra-parameters), optional) Extra information to specify... |
| `memo_type` |  | ✅ | `memoType` | (**Deprecated**, optional) Type of `memo`. One of `text`, `id` or `hash`. Deprecated because memo... |
| `wallet_name` |  | ✅ | `walletName` | (**Deprecated**, optional) In communications / pages about the withdrawal, anchor should display ... |
| `wallet_url` |  | ✅ | `walletUrl` | (**Deprecated**, optional) Anchor can show this to the user when referencing the wallet involved ... |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0006!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0006](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0006.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0006`
