# SEP-0008 (Regulated Assets) Compatibility Matrix

**Generated:** 2026-05-29 19:51:48

**SEP Version:** 1.7.4  
**SEP Status:** Active  
**SDK Version:** 1.6.1  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md

## SEP Summary

Defines how assets that require per-transaction issuer approval are identified, and the protocol for performing compliance checks before submission.

## Overall Coverage

**Total Coverage:** 65.62% (21/32 fields)

- ✅ **Implemented:** 21/32
- ❌ **Not Implemented:** 11/32

**Required Fields:** 74.07% (20/27)

**Optional Fields:** 20.0% (1/5)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Action Required Response Fields | 20.0% | 1/3 | 1 | 5 |
| Action URL Handling | 100.0% | 4/4 | 4 | 4 |
| Approval Endpoint | 100.0% | 1/1 | 1 | 1 |
| Authorization Flags | 100.0% | 2/2 | 2 | 2 |
| Pending Response Fields | 33.33% | 1/2 | 1 | 3 |
| Rejected Response Fields | 50.0% | 1/2 | 1 | 2 |
| Request Parameters | 100.0% | 1/1 | 1 | 1 |
| Response Statuses | 100.0% | 5/5 | 5 | 5 |
| Revised Response Fields | 33.33% | 1/3 | 1 | 3 |
| Stellar TOML Fields | 100.0% | 2/2 | 3 | 3 |
| Success Response Fields | 33.33% | 1/2 | 1 | 3 |

## Detailed Field Comparison

### Action Required Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `status` | ✓ | ✅ | `(implicit)` | Status value "action_required" |
| `message` | ✓ | ❌ | - | A human readable string containing information regarding the action required |
| `action_url` | ✓ | ❌ | - | A URL that allows the user to complete the actions required to have the transaction approved |
| `action_method` |  | ❌ | - | GET or POST, indicating the type of request that should be made to the action_url. If not provide... |
| `action_fields` |  | ❌ | - | An array of additional fields defined by SEP-9 Standard KYC / AML fields that the client may opti... |

### Action URL Handling

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `action_url_get` | ✓ | ✅ | `postAction` | Support for GET method to action_url with query parameters |
| `action_url_post` | ✓ | ✅ | `postAction` | Support for POST method to action_url with JSON body |
| `action_url_post_response_no_further_action` | ✓ | ✅ | `Done` | Handle POST response with result "no_further_action_required" |
| `action_url_post_response_follow_next_url` | ✓ | ✅ | `NextUrl` | Handle POST response with result "follow_next_url" and next_url field |

### Approval Endpoint

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `tx_approve` | ✓ | ✅ | `postTransaction` | POST /tx_approve - Approval server endpoint that receives a signed transaction, checks for compli... |

### Authorization Flags

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `authorization_required` | ✓ | ✅ | `(Stellar account flags)` | Authorization Required flag must be set on issuer account |
| `authorization_revocable` | ✓ | ✅ | `(Stellar account flags)` | Authorization Revocable flag must be set on issuer account |

### Pending Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `status` | ✓ | ✅ | `(implicit)` | Status value "pending" |
| `timeout` | ✓ | ❌ | - | Number of milliseconds to wait before submitting the same transaction again. Use 0 if the wait ti... |
| `message` |  | ❌ | - | A human readable string containing information to pass on to the user |

### Rejected Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `status` | ✓ | ✅ | `(implicit)` | Status value "rejected" |
| `error` | ✓ | ❌ | - | A human readable string explaining why the transaction is not compliant and could not be made com... |

### Request Parameters

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `tx` | ✓ | ✅ | `(handled by postTransaction)` | A base64 encoded transaction envelope XDR signed by the user. This is the transaction that will b... |

### Response Statuses

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `success` | ✓ | ✅ | `Success` | Transaction was found compliant and signed without being revised |
| `revised` | ✓ | ✅ | `Revised` | Transaction was revised to be made compliant |
| `pending` | ✓ | ✅ | `Pending` | Issuer could not determine whether to approve the transaction at the time of receiving it |
| `action_required` | ✓ | ✅ | `ActionRequired` | User must complete an action before this transaction can be approved |
| `rejected` | ✓ | ✅ | `Rejected` | Transaction is not compliant and could not be revised to be made compliant |

### Revised Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `status` | ✓ | ✅ | `(implicit)` | Status value "revised" |
| `tx` | ✓ | ❌ | - | Transaction envelope XDR, base64 encoded. This transaction is a revised compliant version of the ... |
| `message` | ✓ | ❌ | - | A human readable string explaining the modifications made to the transaction to make it compliant |

### Stellar TOML Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `regulated` | ✓ | ✅ | `(in Currency.regulated)` | A boolean indicating whether or not this is a regulated asset. If missing, false is assumed. |
| `approval_server` | ✓ | ✅ | `approvalServer` | The URL of an approval service that signs validated transactions |
| `approval_criteria` |  | ✅ | `approvalCriteria` | A human readable string that explains the issuer's requirements for approving transactions |

### Success Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `status` | ✓ | ✅ | `(implicit)` | Status value "success" |
| `tx` | ✓ | ❌ | - | Transaction envelope XDR, base64 encoded. This transaction will have both the original signature(... |
| `message` |  | ❌ | - | A human readable string containing information to pass on to the user |

## Implementation Gaps

### 🟠 High Priority (7 gaps)

- **`tx`** (Required)
  - Section: Success Response Fields
  - Transaction envelope XDR, base64 encoded. This transaction will have both the original signature(s) from the request as well as one or multiple additional signatures from the issuer.
- **`tx`** (Required)
  - Section: Revised Response Fields
  - Transaction envelope XDR, base64 encoded. This transaction is a revised compliant version of the original request transaction, signed by the issuer.
- **`message`** (Required)
  - Section: Revised Response Fields
  - A human readable string explaining the modifications made to the transaction to make it compliant
- **`timeout`** (Required)
  - Section: Pending Response Fields
  - Number of milliseconds to wait before submitting the same transaction again. Use 0 if the wait time cannot be determined.
- **`message`** (Required)
  - Section: Action Required Response Fields
  - A human readable string containing information regarding the action required
- **`action_url`** (Required)
  - Section: Action Required Response Fields
  - A URL that allows the user to complete the actions required to have the transaction approved
- **`error`** (Required)
  - Section: Rejected Response Fields
  - A human readable string explaining why the transaction is not compliant and could not be made compliant

### 🟢 Low Priority (4 gaps)

- **`message`** (Optional)
  - Section: Success Response Fields
  - A human readable string containing information to pass on to the user
- **`message`** (Optional)
  - Section: Pending Response Fields
  - A human readable string containing information to pass on to the user
- **`action_method`** (Optional)
  - Section: Action Required Response Fields
  - GET or POST, indicating the type of request that should be made to the action_url. If not provided, GET is assumed.
- **`action_fields`** (Optional)
  - Section: Action Required Response Fields
  - An array of additional fields defined by SEP-9 Standard KYC / AML fields that the client may optionally provide to the approval service when sending the request to the action_url

## Recommendations

2. **High Priority**: Implement 7 high-priority field(s)
3. **Required Fields**: Complete implementation of 7 required field(s)

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0008](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0008`
