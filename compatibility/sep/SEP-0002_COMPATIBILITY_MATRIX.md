# SEP-0002 (Federation protocol) Compatibility Matrix

**Generated:** 2026-02-13 16:19:39

**SEP Version:** 1.1.0<br>
**SEP Status:** Final<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md

## SEP Summary

The Stellar federation protocol maps Stellar addresses to more information about a given user. It resolves email-like addresses such as name*yourdomain.com into account IDs. Stellar addresses provide an easy way for users to share payment details by using a syntax that interoperates across different domains and providers.

## Overall Coverage

**Total Coverage:** 100.0% (10/10 fields)

- ✅ **Implemented:** 10/10
- ❌ **Not Implemented:** 0/10

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep02/FederationService.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep02/FederationResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep02/exceptions/Sep02Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep02/exceptions/Sep02InvalidAddressException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep02/exceptions/Sep02FederationNotFoundException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep02/exceptions/Sep02InvalidResponseException.kt`

### Key Classes

- **`FederationService`** - Methods: fromDomain, resolveStellarAddress, parseAddress, resolveStellarAddress, resolveAccountId, resolveTransactionId, resolveForward
- **`FederationResponse`** - Methods: fromJson

### Test Coverage

**Tests:** 34 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep02/FederationServiceTest.kt`

## Coverage by Section

| Section | Coverage | Implemented | Total |
|---------|----------|-------------|-------|
| Request Parameters | 100.0% | 2 | 2 |
| Request Types | 100.0% | 4 | 4 |
| Response Fields | 100.0% | 4 | 4 |

## Detailed Field Comparison

### Request Parameters

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `q` | ✓ | ✅ | `FederationService.executeQuery() q parameter` | String to look up (stellar address, account ID, or transaction ID) |
| `type` | ✓ | ✅ | `FederationService.executeQuery() type parameter` | Type of lookup (name, id, txid, or forward) |

### Request Types

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `name` | ✓ | ✅ | `FederationService.resolveStellarAddress()` | returns the federation record for the given Stellar address. |
| `id` | ✓ | ✅ | `FederationService.resolveAccountId()` | returns the federation record of the Stellar address associated with the given account ID. In som... |
| `txid` |  | ✅ | `FederationService.resolveTransactionId()` | returns the federation record of the sender of the transaction if known by the server. |
| `forward` |  | ✅ | `FederationService.resolveForward()` | Used for forwarding the payment on to a different network or different financial institution. The... |

### Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `stellar_address` | ✓ | ✅ | `FederationResponse.stellarAddress` | stellar address |
| `account_id` | ✓ | ✅ | `FederationResponse.accountId` | Stellar public key / account ID |
| `memo_type` |  | ✅ | `FederationResponse.memoType` | type of memo to attach to transaction, one of text, id or hash |
| `memo` |  | ✅ | `FederationResponse.memo` | value of memo to attach to transaction, for hash this should be base64-encoded. This field should... |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0002](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep02`
