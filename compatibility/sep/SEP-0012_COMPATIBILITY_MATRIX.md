# SEP-0012 (KYC API) Compatibility Matrix

**Generated:** 2026-04-08 22:01:34

**SEP Version:** 1.15.0  
**SEP Status:** Active  
**SDK Version:** 1.4.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md

## SEP Summary

A standard API for wallets to upload KYC data to anchors. Customers enter their information once and reuse it across multiple services.

## Overall Coverage

**Total Coverage:** 100.0% (21/21 fields)

- ✅ **Implemented:** 21/21
- ❌ **Not Implemented:** 0/21

**Required Fields:** 100% (0/0)

**Optional Fields:** 100.0% (21/21)

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/CallbackSignatureVerifier.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/CustomerFileResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/CustomerStatus.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/FieldStatus.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerFilesResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoField.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoProvidedField.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/KYCService.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerCallbackRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerInfoRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerInfoResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerVerificationRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/CustomerAlreadyExistsException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/CustomerNotFoundException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/FileTooLargeException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/InvalidFieldException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/KYCException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/UnauthorizedException.kt`

### Key Classes

- **`CallbackSignatureVerifier`** - Methods: verify, parseSignatureHeader
- **`CustomerFileResponse`**
- **`CustomerStatus`** - Methods: fromString
- **`FieldStatus`** - Methods: fromString
- **`GetCustomerFilesResponse`**
- **`GetCustomerInfoField`**
- **`GetCustomerInfoProvidedField`**
- **`GetCustomerInfoRequest`**
- **`GetCustomerInfoResponse`**
- **`KYCService`** - Methods: fromDomain, getCustomerInfo, putCustomerInfo, putCustomerVerification, deleteCustomer, putCustomerCallback, postCustomerFile, getCustomerFiles, extractFieldName, extractAccountId, extractCustomerId, extractFileSize
- **`PutCustomerCallbackRequest`**
- **`PutCustomerInfoRequest`**
- **`PutCustomerInfoResponse`**
- **`PutCustomerVerificationRequest`**
- **`CustomerAlreadyExistsException`**
- **`CustomerNotFoundException`**
- **`FileTooLargeException`**
- **`InvalidFieldException`**
- **`UnauthorizedException`**

### Test Coverage

**Tests:** 151 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/sep/sep12/KYCServiceIntegrationTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/CallbackSignatureVerifierTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/CustomerFileResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/CustomerStatusTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/FieldStatusTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/GetCustomerInfoResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/KYCServiceTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/MuxedAccountParsingTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/PutCustomerInfoRequestTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep12/Sep12ExceptionsTest.kt`

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Customer DELETE | 100.0% | N/A | 2 | 2 |
| Customer GET | 100.0% | N/A | 12 | 12 |
| Customer PUT | 100.0% | N/A | 7 | 7 |

## Detailed Field Comparison

### Customer DELETE

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `memo` |  | ✅ | `memo` | (optional) the client-generated [memo](https://developers.stellar.org/docs/glossary/transactions/#memo) that uniquely identifies the customer. If a memo is present in the decoded SEP-10 JWT's `sub` value, it must match this parameter value. If a muxed account is used as the JWT's `sub` value, memos sent in requests must match the 64-bit integer subaccount ID of the muxed account. If the `account` is a `C...` account, the `memo` must not be specified. See the [Shared Accounts](#shared-omnibus-or-pooled-accounts) section for more information. |
| `memo_type` |  | ✅ | `memoType` | (**deprecated**, optional) type of `memo`. One of `text`, `id` or `hash`. Deprecated because memo... |

### Customer GET

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` |  | ✅ | `id` | (optional) The ID of the customer as returned in the response of a previous `PUT` request. If the... |
| `account` |  | ✅ | `account` | (**deprecated**, optional) The server should infer the account from the `sub` value in the SEP-10... |
| `memo` |  | ✅ | `memo` | (optional) the client-generated [memo](https://developers.stellar.org/docs/glossary/transactions/#memo) that uniquely identifies the customer. If a memo is present in the decoded SEP-10 JWT's `sub` value, it must match this parameter value. If a muxed account is used as the JWT's `sub` value, memos sent in requests must match the 64-bit integer subaccount ID of the muxed account. If the `account` is a `C...` account, the `memo` must not be specified. See the [Shared Accounts](#shared-omnibus-or-pooled-accounts) section for more information. |
| `memo_type` |  | ✅ | `memoType` | (**deprecated**, optional) type of `memo`. One of `text`, `id` or `hash`. Deprecated because memo... |
| `type` |  | ✅ | `type` | (optional) the type of action the customer is being KYCd for. See the [Type Specification](#type-... |
| `transaction_id` |  | ✅ | `transactionId` | (optional) The transaction id with which the customer's info is associated. When information from... |
| `lang` |  | ✅ | `lang` | (optional) Defaults to `en`. Language code specified using [ISO 639-1](https://en.wikipedia.org/wiki/ISO_639-1). Human readable descriptions, choices, and messages should be in this language. |
| `id` |  | ✅ | `id` | (optional) ID of the customer, if the customer has already been created via a `PUT /customer` req... |
| `status` |  | ✅ | `status` | Status of the customers KYC process. |
| `fields` |  | ✅ | `fields` | (optional) An object containing the fields the anchor has not yet received for the given customer... |
| `provided_fields` |  | ✅ | `providedFields` | (optional) An object containing the fields the anchor has received for the given customer. See [P... |
| `message` |  | ✅ | `message` | (optional) Human readable message describing the current state of customer's KYC process. |

### Customer PUT

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` |  | ✅ | `id` | (optional) The `id` value returned from a previous call to this endpoint. If specified, no other ... |
| `account` |  | ✅ | `account` | (**deprecated**, optional) The server should infer the account from the `sub` value in the SEP-10... |
| `memo` |  | ✅ | `memo` | (optional) the client-generated [memo](https://developers.stellar.org/docs/glossary/transactions/#memo) that uniquely identifies the customer. If a memo is present in the decoded SEP-10 JWT's `sub` value, it must match this parameter value. If a muxed account is used as the JWT's `sub` value, memos sent in requests must match the 64-bit integer subaccount ID of the muxed account. If the `account` is a `C...` account, the `memo` must not be specified. See the [Shared Accounts](#shared-omnibus-or-pooled-accounts) section for more information. |
| `memo_type` |  | ✅ | `memoType` | (**deprecated**, optional) type of `memo`. One of `text`, `id` or `hash`. Deprecated because memo... |
| `type` |  | ✅ | `type` | (optional) The type of the customer as defined in the [Type Specification](#type-specification). |
| `transaction_id` |  | ✅ | `transactionId` | (optional) The transaction id with which the customer's info is associated. When information from... |
| `id` |  | ✅ | `id` | An identifier for the updated or created customer |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0012!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0012](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0012`
