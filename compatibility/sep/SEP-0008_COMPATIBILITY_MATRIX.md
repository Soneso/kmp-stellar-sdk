# SEP-0008 (Regulated Assets) Compatibility Matrix

**Generated:** 2026-02-10 12:00:00

**SEP Version:** 1.0.0<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.0<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md

## SEP Summary

SEP-8 defines a protocol for regulated assets that require issuer approval before transactions can be submitted to the Stellar network. Issuers publish an approval server URL in their stellar.toml, and clients submit transactions to the approval server for authorization before network submission.

## Overall Implementation

**Implementation Type:** Client-Side Only

This SDK implements the client-side of SEP-8 regulated assets. The implementation provides service discovery from stellar.toml, authorization flag checking, transaction submission to approval servers, and action URL handling.

## Overall Coverage

**Total Coverage:** 100% (22/22 features)

- ✅ **Implemented:** 22/22
- ❌ **Not Implemented:** 0/22

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/Sep08Service.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/Sep08PostTransactionResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/Sep08PostActionResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/RegulatedAsset.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep08/exceptions/` (4 exception types)

### Key Classes

- **`Sep08Service`** - Main client class with methods: fromDomain, authorizationRequired, postTransaction, postAction
- **`Sep08PostTransactionResponse`** - Sealed class with 5 response variants: Success, Revised, Pending, ActionRequired, Rejected
- **`Sep08PostActionResponse`** - Sealed class with 2 response variants: Done, NextUrl
- **`RegulatedAsset`** - Regulated asset with code, issuer, approval server URL, and optional criteria

### Test Coverage

**Tests:** 108 test cases across 4 test files (2,950 lines of test code)

**Test Files:**
- `Sep08ExceptionsTest.kt` - Exception hierarchy and error handling
- `Sep08ResponseParsingTest.kt` - All response type parsing and validation
- `Sep08ServiceTest.kt` - Service initialization, regulated asset discovery, HTTP client behavior
- `Sep08IntegrationTest.kt` - Live testnet integration

## Detailed Feature Matrix

### Service Discovery and Initialization

| Feature | Status | Notes |
|---------|--------|-------|
| Initialize from domain (stellar.toml) | ✅ | `Sep08Service.fromDomain()` |
| Network passphrase resolution from stellar.toml | ✅ | Falls back to NETWORK_PASSPHRASE in toml |
| Horizon URL resolution from stellar.toml | ✅ | Falls back to HORIZON_URL in toml |
| Explicit network override | ✅ | Optional `network` parameter |
| Explicit Horizon URL override | ✅ | Optional `horizonUrl` parameter |
| Direct constructor initialization | ✅ | For pre-configured scenarios |
| Custom HTTP client support | ✅ | Injectable HttpClient for testing/proxies |
| Custom HTTP headers | ✅ | Add custom headers to requests |

### Regulated Asset Discovery

| Feature | Status | Notes |
|---------|--------|-------|
| Extract regulated assets from stellar.toml | ✅ | Filters currencies with `regulated = true` |
| Asset code extraction | ✅ | `RegulatedAsset.code` |
| Asset issuer extraction | ✅ | `RegulatedAsset.issuer` |
| Approval server URL extraction | ✅ | `RegulatedAsset.approvalServer` |
| Approval criteria extraction | ✅ | `RegulatedAsset.approvalCriteria` (optional) |
| Underlying Asset conversion | ✅ | `toAsset()` returns AlphaNum4 or AlphaNum12 |
| XDR conversion | ✅ | `toXdr()` returns AssetXdr |

### Authorization Flag Checking

| Feature | Status | Notes |
|---------|--------|-------|
| Check AUTH_REQUIRED flag | ✅ | Via `authorizationRequired()` |
| Check AUTH_REVOCABLE flag | ✅ | Both flags validated together |

### Transaction Approval (POST /tx_approve)

| Feature | Status | Notes |
|---------|--------|-------|
| Submit transaction XDR to approval server | ✅ | `postTransaction(tx, approvalServer)` |
| Parse "success" response | ✅ | `Sep08PostTransactionResponse.Success` with tx and optional message |
| Parse "revised" response | ✅ | `Sep08PostTransactionResponse.Revised` with tx and message |
| Parse "pending" response | ✅ | `Sep08PostTransactionResponse.Pending` with timeout and optional message |
| Parse "action_required" response | ✅ | `Sep08PostTransactionResponse.ActionRequired` with actionUrl, actionMethod, actionFields |
| Parse "rejected" response (200) | ✅ | `Sep08PostTransactionResponse.Rejected` with error |
| Parse "rejected" response (400) | ✅ | HTTP 400 with status "rejected" handled |
| Unexpected HTTP status handling | ✅ | Throws Sep08InvalidTransactionResponseException |

### Action URL Handling (POST action_url)

| Feature | Status | Notes |
|---------|--------|-------|
| Submit action fields to action URL | ✅ | `postAction(url, actionFields)` |
| Parse "no_further_action_required" response | ✅ | `Sep08PostActionResponse.Done` |
| Parse "follow_next_url" response | ✅ | `Sep08PostActionResponse.NextUrl` with nextUrl and optional message |
| Unexpected HTTP status handling | ✅ | Throws Sep08InvalidActionResponseException |

### Error Handling

| Feature | Status | Notes |
|---------|--------|-------|
| Base exception class | ✅ | `Sep08Exception` |
| Incomplete init data exception | ✅ | `Sep08IncompleteInitDataException` |
| Invalid transaction response exception | ✅ | `Sep08InvalidTransactionResponseException` |
| Invalid action response exception | ✅ | `Sep08InvalidActionResponseException` |

### Server-Side Features (Not Applicable)

| Feature | Status | Notes |
|---------|--------|-------|
| Approval server implementation | ⚪ N/A | Server-side functionality - not in scope for client SDK |
| Transaction evaluation and compliance rules | ⚪ N/A | Server-side functionality - not in scope for client SDK |
| Action URL server implementation | ⚪ N/A | Server-side functionality - not in scope for client SDK |

## Platform Support

All features work across all supported platforms:
- JVM (Android, Server)
- iOS
- macOS
- JavaScript (Browser & Node.js)

## Additional Information

**Documentation:** See `docs/sep/sep-08.md` for usage examples and API reference

**Specification:** [SEP-0008: Regulated Assets](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep08`

**Integration Tests:** Live testnet integration with regulated asset approval flow

**Last Updated:** 2026-02-10

## Legend

- ✅ **Implemented**: Feature is fully supported in the SDK
- ❌ **Not Implemented**: Feature is not currently supported
- ⚪ **N/A**: Not applicable (server-side feature)
