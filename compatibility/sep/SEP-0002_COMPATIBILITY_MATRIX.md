# SEP-0002 (Federation protocol) Compatibility Matrix

**Generated:** 2026-07-20 11:43:06

**SEP Version:** 1.1.0  
**SEP Status:** Final  
**SDK Version:** 1.10.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md

## SEP Summary

The federation protocol resolves human-readable addresses like `name*yourdomain.com` into Stellar account IDs, so users can share payment details without exchanging raw public keys.

## Overall Coverage

**Total Coverage:** 100.0% (10/10 fields)

- ✅ **Implemented:** 10/10
- ❌ **Not Implemented:** 0/10

**Required Fields:** 100.0% (6/6)

**Optional Fields:** 100.0% (4/4)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Request Parameters | 100.0% | 2/2 | 2 | 2 |
| Request Types | 100.0% | 2/2 | 4 | 4 |
| Response Fields | 100.0% | 2/2 | 4 | 4 |

## Detailed Field Comparison

### Request Parameters

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `q` | ✓ | ✅ | `(handled by service methods)` | String to look up (stellar address, account ID, or transaction ID) |
| `type` | ✓ | ✅ | `(handled by service methods)` | Type of lookup (name, id, txid, or forward) |

### Request Types

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `name` | ✓ | ✅ | `resolveStellarAddress` | returns the federation record for the given Stellar address. |
| `forward` |  | ✅ | `resolveForward` | Used for forwarding the payment on to a different network or different financial institution. The... |
| `id` | ✓ | ✅ | `resolveAccountId` | returns the federation record of the Stellar address associated with the given account ID. In som... |
| `txid` |  | ✅ | `resolveTransactionId` | returns the federation record of the sender of the transaction if known by the server. |

### Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `stellar_address` | ✓ | ✅ | `stellarAddress` | stellar address |
| `account_id` | ✓ | ✅ | `accountId` | Stellar public key / account ID |
| `memo_type` |  | ✅ | `memoType` | type of memo to attach to transaction, one of text, id or hash |
| `memo` |  | ✅ | `memo` | value of memo to attach to transaction, for hash this should be base64-encoded |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0002!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0002](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0002`
