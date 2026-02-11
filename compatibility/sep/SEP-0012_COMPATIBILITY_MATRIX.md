# SEP-0012 (KYC API) Compatibility Matrix

**Generated:** 2026-02-11 15:11:20

**SEP Version:** 1.15.0<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md

## SEP Summary

Defines a standard way for stellar clients to upload KYC (or other) information to anchors and other services. SEP-6 and SEP-31 use this protocol, but it can serve as a stand-alone service as well. Supports authentication via SEP-10, handles image and binary data, supports SEP-9 fields, and gives customers control over their data.

## Overall Coverage

**Total Coverage:** 100.0% (28/28 fields)

- ✅ **Implemented:** 28/28
- ❌ **Not Implemented:** 0/28

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/KYCService.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerInfoRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerInfoResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerVerificationRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerCallbackRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/CustomerFileResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerFilesResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/CustomerStatus.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/FieldStatus.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoField.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoProvidedField.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/CallbackSignatureVerifier.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/KYCException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/CustomerNotFoundException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/UnauthorizedException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/FileTooLargeException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/CustomerAlreadyExistsException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep12/exceptions/InvalidFieldException.kt`

### Key Classes

- **`KYCService`** - Methods: fromDomain, getCustomerInfo, putCustomerInfo, putCustomerVerification, deleteCustomer, putCustomerCallback, postCustomerFile, getCustomerFiles
- **`GetCustomerInfoRequest`**
- **`GetCustomerInfoResponse`**
- **`PutCustomerInfoRequest`**
- **`PutCustomerInfoResponse`**
- **`PutCustomerVerificationRequest`**
- **`PutCustomerCallbackRequest`**
- **`CustomerFileResponse`**
- **`GetCustomerFilesResponse`**
- **`CustomerStatus`**
- **`FieldStatus`**
- **`GetCustomerInfoField`**
- **`GetCustomerInfoProvidedField`**
- **`CallbackSignatureVerifier`** - Methods: verify

### Test Coverage

**Tests:** 105 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep12/CustomerStatusTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep12/FieldStatusTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep12/GetCustomerInfoResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep12/PutCustomerInfoRequestTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep12/CustomerFileResponseTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep12/KYCServiceTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep12/CallbackSignatureVerifierTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep12/MuxedAccountParsingTest.kt`

## Coverage by Section

| Section | Coverage | Implemented | Total |
|---------|----------|-------------|-------|
| Customer Callback PUT | 100.0% | 4 | 4 |
| Customer DELETE | 100.0% | 3 | 3 |
| Customer Files GET | 100.0% | 2 | 2 |
| Customer Files POST | 100.0% | 1 | 1 |
| Customer GET | 100.0% | 7 | 7 |
| Customer PUT | 100.0% | 9 | 9 |
| Customer Verification PUT (Deprecated) | 100.0% | 2 | 2 |

## Detailed Field Comparison

### Customer Callback PUT

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `url` | ✓ | ✅ | `PutCustomerCallbackRequest.url` | Callback URL for status updates |
| `id` |  | ✅ | `PutCustomerCallbackRequest.id` | Customer ID |
| `account` |  | ✅ | `PutCustomerCallbackRequest.account` | Stellar account (G.../M.../C...) |
| `memo` |  | ✅ | `PutCustomerCallbackRequest.memo` | Memo if account is shared |

### Customer DELETE

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `account` | ✓ | ✅ | `KYCService.deleteCustomer(account)` | Stellar account ID in URL path |
| `memo` |  | ✅ | `KYCService.deleteCustomer(memo)` | Memo if account is shared |
| `memo_type` |  | ✅ | `KYCService.deleteCustomer(memoType)` | Type of memo (text, id, or hash) |

### Customer Files GET

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `file_id` |  | ✅ | `KYCService.getCustomerFiles(fileId)` | File ID to retrieve specific file info |
| `customer_id` |  | ✅ | `KYCService.getCustomerFiles(customerId)` | Customer ID to retrieve all files for a customer |

### Customer Files POST

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `file` | ✓ | ✅ | `KYCService.postCustomerFile(file)` | Binary file data |

### Customer GET

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` |  | ✅ | `GetCustomerInfoRequest.id` | ID of the customer as returned in a previous PUT request |
| `account` |  | ✅ | `GetCustomerInfoRequest.account` | Stellar account (G.../M.../C...) - deprecated, use JWT sub instead |
| `memo` |  | ✅ | `GetCustomerInfoRequest.memo` | Memo uniquely identifying the customer for shared accounts |
| `memo_type` |  | ✅ | `GetCustomerInfoRequest.memoType` | Type of memo (text, id, or hash) - deprecated, use id only |
| `type` |  | ✅ | `GetCustomerInfoRequest.type` | Type of action the customer is being KYC'd for (e.g., sep31-sender) |
| `transaction_id` |  | ✅ | `GetCustomerInfoRequest.transactionId` | Transaction ID with which customer's info is associated |
| `lang` |  | ✅ | `GetCustomerInfoRequest.lang` | Language code for human readable content (ISO 639-1) |

### Customer PUT

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` |  | ✅ | `PutCustomerInfoRequest.id` | Customer ID from previous PUT request |
| `account` |  | ✅ | `PutCustomerInfoRequest.account` | Stellar account (G.../M.../C...) - deprecated, use JWT sub instead |
| `memo` |  | ✅ | `PutCustomerInfoRequest.memo` | Memo uniquely identifying the customer for shared accounts |
| `memo_type` |  | ✅ | `PutCustomerInfoRequest.memoType` | Type of memo (text, id, or hash) - deprecated, use id only |
| `type` |  | ✅ | `PutCustomerInfoRequest.type` | Type of action the customer is being KYC'd for |
| `transaction_id` |  | ✅ | `PutCustomerInfoRequest.transactionId` | Transaction ID with which customer's info is associated |
| `SEP-9 fields` |  | ✅ | `PutCustomerInfoRequest.kycFields` | Any SEP-9 standard KYC fields (text and binary) |
| `verification fields` |  | ✅ | `PutCustomerInfoRequest.verificationFields` | Verification codes with _verification suffix (e.g., email_address_verification) |
| `file references` |  | ✅ | `PutCustomerInfoRequest.fileReferences` | File IDs with _file_id suffix (e.g., photo_id_front_file_id) |

### Customer Verification PUT (Deprecated)

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `id` | ✓ | ✅ | `PutCustomerVerificationRequest.id` | Customer ID |
| `verification fields` | ✓ | ✅ | `PutCustomerVerificationRequest.verificationFields` | Field verification codes |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0012](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0012`
