# SEP-6 Compatibility Matrix

## Overview

| Property | Value |
|----------|-------|
| SEP | SEP-0006 |
| Title | Deposit and Withdrawal API |
| Version | 4.3.0 |
| SDK Version | 1.2.1 |
| Status | Active |
| Overall Coverage | 100% (95/95 fields) |

SEP-6 enables programmatic deposit and withdrawal flows between Stellar and external systems (bank accounts, other blockchains, etc.) without interactive web flows. This is the non-interactive counterpart to SEP-24.

## Implementation Files

| File | Description |
|------|-------------|
| `Sep06Service.kt` | Main service class with 9 methods |
| `Sep06Requests.kt` | 8 request data classes |
| `Sep06Responses.kt` | 17+ response data classes |
| `Sep06TransactionStatus.kt` | 17 transaction status values |
| `Sep06TransactionKind.kt` | 4 transaction kind values |
| `exceptions/*.kt` | 7 exception classes |

## Endpoints Coverage

| Endpoint | Method | Supported | Service Method | Notes |
|----------|--------|-----------|----------------|-------|
| `/info` | GET | Yes | `info()` | Anchor capabilities |
| `/deposit` | GET | Yes | `deposit()` | Standard deposit |
| `/deposit-exchange` | GET | Yes | `depositExchange()` | SEP-38 exchange deposit |
| `/withdraw` | GET | Yes | `withdraw()` | Standard withdrawal |
| `/withdraw-exchange` | GET | Yes | `withdrawExchange()` | SEP-38 exchange withdrawal |
| `/fee` | GET | Yes | `fee()` | Deprecated, use SEP-38 |
| `/transactions` | GET | Yes | `transactions()` | Transaction history |
| `/transaction` | GET | Yes | `transaction()` | Single transaction |
| `/transaction/:id` | PATCH | Yes | `patchTransaction()` | Update transaction info |

**Endpoints: 9/9 (100%)**

## Request Parameters

### Deposit Request (Sep06DepositRequest)

| Parameter | Required | Supported | Deprecated | Notes |
|-----------|----------|-----------|------------|-------|
| asset_code | Yes | Yes | No | Stellar asset code |
| account | Yes | Yes | No | Destination Stellar account |
| jwt | Yes | Yes | No | SEP-10 authentication token |
| asset_issuer | No | Yes | No | For multi-issuer assets |
| memo_type | No | Yes | No | Memo type |
| memo | No | Yes | No | Memo value |
| email_address | No | Yes | No | User email |
| type | No | Yes | Yes | Use funding_method |
| funding_method | No | Yes | No | Deposit method |
| amount | No | Yes | No | Deposit amount |
| country_code | No | Yes | No | ISO 3166-1 alpha-2 |
| claimable_balance_supported | No | Yes | No | Boolean flag |
| customer_id | No | Yes | No | SEP-12 customer ID |
| location_id | No | Yes | No | Location ID |
| wallet_name | No | Yes | Yes | Use client_domain |
| wallet_url | No | Yes | Yes | Use client_domain |
| lang | No | Yes | No | RFC 4646 language |
| on_change_callback | No | Yes | No | Webhook URL |

**Deposit Request: 18/18 (100%)**

### Withdraw Request (Sep06WithdrawRequest)

| Parameter | Required | Supported | Deprecated | Notes |
|-----------|----------|-----------|------------|-------|
| asset_code | Yes | Yes | No | Stellar asset code |
| type | Yes | Yes | Yes | Use funding_method |
| jwt | Yes | Yes | No | SEP-10 authentication token |
| funding_method | No | Yes | No | Withdrawal method |
| dest | No | Yes | No | Destination address |
| dest_extra | No | Yes | No | Additional destination info |
| account | No | Yes | No | Source Stellar account |
| memo | No | Yes | No | Memo value |
| memo_type | No | Yes | Yes | Memo type |
| amount | No | Yes | No | Withdrawal amount |
| country_code | No | Yes | No | ISO 3166-1 alpha-2 |
| refund_memo | No | Yes | No | Refund memo |
| refund_memo_type | No | Yes | No | Refund memo type |
| customer_id | No | Yes | No | SEP-12 customer ID |
| location_id | No | Yes | No | Location ID |
| wallet_name | No | Yes | Yes | Use client_domain |
| wallet_url | No | Yes | Yes | Use client_domain |
| lang | No | Yes | No | RFC 4646 language |
| on_change_callback | No | Yes | No | Webhook URL |

**Withdraw Request: 19/19 (100%)**

## Response Fields

### Deposit Response (Sep06DepositResponse)

| Field | Required | Supported | Deprecated | Notes |
|-------|----------|-----------|------------|-------|
| how | No | Yes | Yes | Use instructions |
| id | No | Yes | No | Transaction ID |
| eta | No | Yes | No | ETA in seconds |
| min_amount | No | Yes | No | Minimum amount |
| max_amount | No | Yes | No | Maximum amount |
| fee_fixed | No | Yes | No | Fixed fee |
| fee_percent | No | Yes | No | Percentage fee |
| extra_info | No | Yes | No | Additional info |
| instructions | No | Yes | No | Deposit instructions map |

**Deposit Response: 9/9 (100%)**

### Withdraw Response (Sep06WithdrawResponse)

| Field | Required | Supported | Notes |
|-------|----------|-----------|-------|
| account_id | Yes | Yes | Anchor's Stellar account |
| memo_type | Yes | Yes | Memo type for payment |
| memo | No | Yes | Memo value |
| id | No | Yes | Transaction ID |
| eta | No | Yes | ETA in seconds |
| min_amount | No | Yes | Minimum amount |
| max_amount | No | Yes | Maximum amount |
| fee_fixed | No | Yes | Fixed fee |
| fee_percent | No | Yes | Percentage fee |
| extra_info | No | Yes | Additional info |

**Withdraw Response: 10/10 (100%)**

### Transaction Fields (Sep06Transaction)

| # | Field | Required | Supported | Deprecated | Notes |
|---|-------|----------|-----------|------------|-------|
| 1 | id | Yes | Yes | No | Unique transaction ID |
| 2 | kind | Yes | Yes | No | Transaction type |
| 3 | status | Yes | Yes | No | Current status |
| 4 | status_eta | No | Yes | No | ETA in seconds |
| 5 | more_info_url | No | Yes | No | URL for details |
| 6 | amount_in | No | Yes | No | Amount received |
| 7 | amount_in_asset | No | Yes | No | Asset of amount_in |
| 8 | amount_out | No | Yes | No | Amount sent |
| 9 | amount_out_asset | No | Yes | No | Asset of amount_out |
| 10 | amount_fee | No | Yes | Yes | Use fee_details |
| 11 | amount_fee_asset | No | Yes | Yes | Use fee_details |
| 12 | fee_details | No | Yes | No | Detailed fee breakdown |
| 13 | quote_id | No | Yes | No | SEP-38 quote ID |
| 14 | from | No | Yes | No | Sending account |
| 15 | to | No | Yes | No | Receiving account |
| 16 | external_extra | No | Yes | No | Routing number, BIC |
| 17 | external_extra_text | No | Yes | No | Bank name |
| 18 | deposit_memo | No | Yes | No | Deposit memo |
| 19 | deposit_memo_type | No | Yes | No | Deposit memo type |
| 20 | withdraw_anchor_account | No | Yes | No | Anchor's account |
| 21 | withdraw_memo | No | Yes | No | Withdrawal memo |
| 22 | withdraw_memo_type | No | Yes | No | Withdrawal memo type |
| 23 | started_at | No | Yes | No | ISO 8601 timestamp |
| 24 | updated_at | No | Yes | No | ISO 8601 timestamp |
| 25 | completed_at | No | Yes | No | ISO 8601 timestamp |
| 26 | user_action_required_by | No | Yes | No | User action deadline |
| 27 | stellar_transaction_id | No | Yes | No | Stellar tx hash |
| 28 | external_transaction_id | No | Yes | No | External tx ID |
| 29 | message | No | Yes | No | Status message |
| 30 | refunded | No | Yes | Yes | Use refunds object |
| 31 | refunds | No | Yes | No | Refund details |
| 32 | required_info_message | No | Yes | No | Info update message |
| 33 | required_info_updates | No | Yes | No | Fields to update |
| 34 | instructions | No | Yes | No | Deposit instructions |
| 35 | claimable_balance_id | No | Yes | No | Claimable balance ID |

**Transaction Fields: 35/35 (100%)**

## Transaction Status Values

| Status | Supported | Category | Description |
|--------|-----------|----------|-------------|
| incomplete | Yes | Initial | Transaction not yet started |
| pending_user_transfer_start | Yes | Pending | Waiting for user to send funds |
| pending_user_transfer_complete | Yes | Pending | User has sent funds |
| pending_external | Yes | Pending | Waiting for external system |
| pending_anchor | Yes | Pending | Anchor is processing |
| pending_stellar | Yes | Pending | Stellar transaction pending |
| pending_trust | Yes | Pending | Waiting for trustline |
| pending_user | Yes | Pending | Waiting for user action |
| pending_customer_info_update | Yes | Pending | KYC info update needed |
| pending_transaction_info_update | Yes | Pending | Transaction info update needed |
| completed | Yes | Terminal | Successfully completed |
| refunded | Yes | Terminal | Funds returned |
| expired | Yes | Terminal | Transaction expired |
| error | Yes | Terminal/Error | Processing failed |
| no_market | Yes | Terminal/Error | No market for conversion |
| too_small | Yes | Terminal/Error | Amount below minimum |
| too_large | Yes | Terminal/Error | Amount above maximum |

**Transaction Statuses: 17/17 (100%)**

## Transaction Kind Values

| Kind | Supported | Description |
|------|-----------|-------------|
| deposit | Yes | Standard deposit |
| withdrawal | Yes | Standard withdrawal |
| deposit-exchange | Yes | Deposit with SEP-38 conversion |
| withdrawal-exchange | Yes | Withdrawal with SEP-38 conversion |

**Transaction Kinds: 4/4 (100%)**

## Exception Handling

| Exception | HTTP Status | Description |
|-----------|-------------|-------------|
| Sep06Exception | - | Base exception class |
| Sep06AuthenticationRequiredException | 403 | JWT missing or invalid |
| Sep06CustomerInformationNeededException | 403 | SEP-12 KYC required |
| Sep06CustomerInformationStatusException | 403 | KYC pending or denied |
| Sep06InvalidRequestException | 400 | Malformed request |
| Sep06ServerErrorException | 5xx | Server error |
| Sep06TransactionNotFoundException | 404 | Transaction not found |

**Exception Types: 7/7 (100%)**

## SEP Integration

| SEP | Integration | Status |
|-----|-------------|--------|
| SEP-1 | Service discovery via stellar.toml | Yes |
| SEP-10 | JWT authentication | Yes |
| SEP-12 | KYC integration | Yes |
| SEP-38 | Quote integration | Yes |

## Feature Support

| Feature | Supported | Notes |
|---------|-----------|-------|
| Programmatic deposits | Yes | Full /deposit support |
| Programmatic withdrawals | Yes | Full /withdraw support |
| Exchange operations | Yes | SEP-38 quotes |
| Transaction tracking | Yes | /transactions and /transaction |
| Transaction updates | Yes | PATCH /transaction/:id |
| Claimable balances | Yes | claimable_balance_supported flag |
| Callback notifications | Yes | on_change_callback parameter |
| Refund tracking | Yes | Full refunds object |
| Fee details | Yes | Detailed fee breakdown |

## Coverage Summary

| Section | Coverage | Required | Notes |
|---------|----------|----------|-------|
| Endpoints | 9/9 (100%) | 9/9 | All endpoints implemented |
| Deposit Request | 18/18 (100%) | 2/2 | All parameters |
| Withdraw Request | 19/19 (100%) | 2/2 | All parameters |
| Deposit Response | 9/9 (100%) | 1/1 | All fields |
| Withdraw Response | 10/10 (100%) | 2/2 | All fields |
| Transaction Fields | 35/35 (100%) | 4/4 | All 35 fields |
| Transaction Statuses | 17/17 (100%) | 4/4 | All statuses |
| Transaction Kinds | 4/4 (100%) | 4/4 | All kinds |
| Exception Types | 7/7 (100%) | 3/3 | All error types |
| **Overall** | **95/95 (100%)** | **22/22** | Complete coverage |
