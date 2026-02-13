# SEP-0008 (Regulated Assets) Compatibility Matrix

**Generated:** 2026-02-13 22:05:28

**SEP Version:** 1.7.4<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md

## SEP Summary

Defines how assets that require per-transaction issuer approval are identified, and the protocol for performing compliance checks before submission.

## Overall Coverage

**Total Coverage:** 100.0% (32/32 fields)

- ✅ **Implemented:** 32/32
- ❌ **Not Implemented:** 0/32
- **Required Fields:** 100.0% (27/27)

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/RegulatedAsset.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/Sep08PostActionResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/Sep08PostTransactionResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/Sep08Service.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/exceptions/Sep08Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/exceptions/Sep08IncompleteInitDataException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/exceptions/Sep08InvalidActionResponseException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/exceptions/Sep08InvalidTransactionResponseException.kt`

### Key Classes

- **`wraps`** - Methods: toXdr, toAsset, toString, compareTo, equals, hashCode
- **`RegulatedAsset`** - Methods: toXdr, toAsset, toString, compareTo, equals, hashCode
- **`of`**
- **`Sep08PostActionResponse`** - Methods: fromJson, parseNextUrl
- **`Done`** - Methods: fromJson, parseNextUrl
- **`NextUrl`** - Methods: fromJson, parseNextUrl
- **`returned`** - Methods: parseNextUrl
- **`variant`** - Methods: parseNextUrl
- **`of`**
- **`Sep08PostTransactionResponse`** - Methods: fromJson, parseSuccess, parseRevised, parsePending, parseActionRequired, parseRejected
- **`Success`** - Methods: fromJson, parseSuccess, parseRevised, parsePending, parseActionRequired, parseRejected
- **`Revised`** - Methods: fromJson, parseSuccess, parseRevised, parsePending, parseActionRequired, parseRejected
- **`Pending`** - Methods: fromJson, parseSuccess, parseRevised, parsePending, parseActionRequired, parseRejected
- **`ActionRequired`** - Methods: fromJson, parseSuccess, parseRevised, parsePending, parseActionRequired, parseRejected
- **`Rejected`** - Methods: fromJson, parseSuccess, parseRevised, parsePending, parseActionRequired, parseRejected
- **`returned`** - Methods: parseSuccess, parseRevised, parsePending, parseActionRequired, parseRejected
- **`variant`** - Methods: parseSuccess, parseRevised, parsePending, parseActionRequired, parseRejected
- **`Sep08Service`** - Methods: fromDomain, authorizationRequired, postTransaction, postAction, buildHeaders
- **`for`** - Methods: toString
- **`to`** - Methods: toString
- **`Sep08Exception`** - Methods: toString
- **`Sep08IncompleteInitDataException`** - Methods: toString
- **`Sep08InvalidActionResponseException`** - Methods: toString
- **`Sep08InvalidTransactionResponseException`** - Methods: toString

### Test Coverage

**Tests:** 108 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/sep/sep08/Sep08IntegrationTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep08/Sep08ExceptionsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep08/Sep08ResponseParsingTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep08/Sep08ServiceTest.kt`

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Action Required Response Fields | 100.0% | 3/3 | 5 | 5 |
| Action URL Handling | 100.0% | 4/4 | 4 | 4 |
| Approval Endpoint | 100.0% | 1/1 | 1 | 1 |
| Authorization Flags | 100.0% | 2/2 | 2 | 2 |
| Pending Response Fields | 100.0% | 2/2 | 3 | 3 |
| Rejected Response Fields | 100.0% | 2/2 | 2 | 2 |
| Request Parameters | 100.0% | 1/1 | 1 | 1 |
| Response Statuses | 100.0% | 5/5 | 5 | 5 |
| Revised Response Fields | 100.0% | 3/3 | 3 | 3 |
| Stellar TOML Fields | 100.0% | 2/2 | 3 | 3 |
| Success Response Fields | 100.0% | 2/2 | 3 | 3 |

## Detailed Field Comparison

### Action Required Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `status` | ✓ | ✅ | `(implicit)` | Status value "action_required" |
| `message` | ✓ | ✅ | `message` | A human readable string containing information regarding the action required |
| `action_url` | ✓ | ✅ | `actionUrl` | A URL that allows the user to complete the actions required to have the transaction approved |
| `action_method` |  | ✅ | `actionMethod` | GET or POST, indicating the type of request that should be made to the action_url. If not provide... |
| `action_fields` |  | ✅ | `actionFields` | An array of additional fields defined by SEP-9 Standard KYC / AML fields that the client may opti... |

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
| `timeout` | ✓ | ✅ | `timeout` | Number of milliseconds to wait before submitting the same transaction again. Use 0 if the wait ti... |
| `message` |  | ✅ | `message` | A human readable string containing information to pass on to the user |

### Rejected Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `status` | ✓ | ✅ | `(implicit)` | Status value "rejected" |
| `error` | ✓ | ✅ | `error` | A human readable string explaining why the transaction is not compliant and could not be made com... |

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
| `tx` | ✓ | ✅ | `tx` | Transaction envelope XDR, base64 encoded. This transaction is a revised compliant version of the ... |
| `message` | ✓ | ✅ | `message` | A human readable string explaining the modifications made to the transaction to make it compliant |

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
| `tx` | ✓ | ✅ | `tx` | Transaction envelope XDR, base64 encoded. This transaction will have both the original signature(... |
| `message` |  | ✅ | `message` | A human readable string containing information to pass on to the user |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0008](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0008`
