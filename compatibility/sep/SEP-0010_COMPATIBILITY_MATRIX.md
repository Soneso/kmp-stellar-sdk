# SEP-0010 (Stellar Web Authentication) Compatibility Matrix

**Generated:** 2026-01-14 16:30:00

**SEP Version:** 3.4.1<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md

## SEP Summary

SEP-10 defines a standard for wallet and application authentication using a challenge-response mechanism with Stellar transaction signing. This specification provides secure authentication without exposing private keys, enabling clients to prove account ownership to service providers such as anchors.

## Overall Implementation

**Implementation Type:** Client-Side Only

This SDK implements the client-side of SEP-10 authentication. The implementation provides both high-level and low-level APIs for interacting with SEP-10 authentication servers.

## Overall Coverage

**Total Coverage:** 100% (31/31 features)

- ✅ **Implemented:** 31/31
- ❌ **Not Implemented:** 0/31

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/WebAuth.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/AuthToken.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/ClientDomainSigningDelegate.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/ChallengeResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/` (18 exception types)

### Key Classes

- **`WebAuth`** - Main client class with methods: fromDomain, jwtToken, getChallenge, validateChallenge, signTransaction, sendSignedChallenge
- **`AuthToken`** - JWT token parser with properties: token, iss, sub, iat, exp, jti, clientDomain, account, memo
- **`ClientDomainSigningDelegate`** - Functional interface for external domain signing (HSM/wallet backend)
- **`ChallengeResponse`** - Server challenge response wrapper

### Test Coverage

**Tests:** 115+ test cases across 9 test files (4,656 lines of test code)

**Test Files:**
- `WebAuthValidationTest.kt` - All 13 validation checks
- `WebAuthChallengeTest.kt` - Challenge request flows
- `WebAuthSigningTest.kt` - Transaction signing
- `WebAuthTokenSubmissionTest.kt` - Token submission
- `WebAuthJwtTokenTest.kt` - High-level API
- `WebAuthClientDomainSigningDelegateTest.kt` - External signing
- `AuthTokenTest.kt` - JWT parsing
- `AuthTokenEnhancedTest.kt` - Advanced token features
- `WebAuthIntegrationTest.kt` - Live testnet integration

## Detailed Feature Matrix

### Client-Side Features

| Feature | Status | Notes |
|---------|--------|-------|
| **Challenge Request (GET)** | ✅ | Full support with all query parameters |
| Account parameter | ✅ | Required parameter - G... and M... addresses supported |
| Memo parameter | ✅ | Optional memo ID for sub-account identification |
| Home domain parameter | ✅ | Optional home domain for multi-domain servers |
| Client domain parameter | ✅ | Optional client domain for wallet verification |
| Challenge response parsing | ✅ | JSON response with transaction and networkPassphrase |
| **Challenge Validation (13 Checks)** | ✅ | All SEP-10 security requirements enforced |
| 1. Transaction envelope type | ✅ | Must be ENVELOPE_TYPE_TX |
| 2. Sequence number validation | ✅ | Must be exactly 0 (prevents network submission) |
| 3. Memo type validation | ✅ | Must be MEMO_NONE or MEMO_ID only |
| 4. Memo value validation | ✅ | Must match expected memo if provided |
| 5. Memo/muxed exclusivity | ✅ | Cannot have both memo and muxed account |
| 6. Operation type validation | ✅ | All operations must be ManageData type |
| 7. First operation source | ✅ | Must match client account |
| 8. First operation data name | ✅ | Must be "{serverHomeDomain} auth" |
| 9. Client domain source | ✅ | Client domain operation source verification |
| 10. Web auth domain value | ✅ | Must match endpoint host |
| 11. Time bounds validation | ✅ | With configurable grace period (default 300s) |
| 12. Signature count | ✅ | Must have exactly 1 signature (server's) |
| 13. Server signature verification | ✅ | Cryptographic signature validation |
| **Transaction Signing** | ✅ | Client-side signing with multiple signers |
| Single signature | ✅ | Sign with single keypair |
| Multi-signature support | ✅ | Sign with multiple keypairs for threshold accounts |
| Client domain signing (local) | ✅ | Sign with client domain keypair |
| Client domain signing (external) | ✅ | Delegate signing to HSM/wallet backend |
| Signature preservation | ✅ | Server signature preserved during signing |
| **Token Submission (POST)** | ✅ | Submit signed challenge for JWT token |
| JSON request format | ✅ | Content-Type: application/json |
| Signed transaction XDR | ✅ | Base64-encoded transaction envelope |
| Token response parsing | ✅ | Extract JWT from JSON response |
| **JWT Token Handling** | ✅ | Complete JWT parsing and validation |
| JWT parsing | ✅ | Parse standard and SEP-10 claims |
| Standard claims (iss, sub, iat, exp, jti) | ✅ | All RFC 7519 claims supported |
| SEP-10 claims (client_domain) | ✅ | Client domain claim extraction |
| Account extraction | ✅ | Extract account from sub (handles memo format) |
| Memo extraction | ✅ | Extract memo from sub (format: "ACCOUNT:MEMO") |
| Expiration checking | ✅ | isExpired() method with system time comparison |
| **Account Types** | ✅ | Full support for all account formats |
| Ed25519 public keys (G...) | ✅ | Standard Stellar accounts |
| Muxed accounts (M...) | ✅ | Multiplexed accounts with embedded IDs |
| Memo-based sub-accounts | ✅ | Sub-accounts identified by memo ID |
| **Error Handling** | ✅ | 18 exception types covering all failures |
| Challenge request errors (400, 401, 403, 404, 5xx) | ✅ | ChallengeRequestException with status codes |
| Validation errors | ✅ | 15 specific ChallengeValidationException subtypes |
| Token submission errors (400, 401, 403, 404, 5xx) | ✅ | TokenSubmissionException with status codes |
| Network errors | ✅ | Network failure handling with retries |
| **Advanced Features** | ✅ | Enterprise and wallet infrastructure support |
| Automatic configuration from stellar.toml | ✅ | WebAuth.fromDomain() discovers endpoints and keys |
| High-level API | ✅ | jwtToken() - one-line authentication |
| Low-level API | ✅ | Manual step-by-step control for custom flows |
| Custom HTTP client support | ✅ | Injectable HttpClient for testing/proxies |
| Custom HTTP headers | ✅ | Add custom headers to requests |
| Configurable grace period | ✅ | Time bounds validation with adjustable tolerance |
| HTTP request retries | ✅ | Automatic retry on server errors (3 attempts) |

### Server-Side Features (Not Applicable)

| Feature | Status | Notes |
|---------|--------|-------|
| Challenge generation | ⚪ N/A | Server-side functionality - not in scope for client SDK |
| Signature verification | ⚪ N/A | Server-side functionality - not in scope for client SDK |
| JWT token generation | ⚪ N/A | Server-side functionality - not in scope for client SDK |
| Account threshold validation | ⚪ N/A | Server-side functionality - not in scope for client SDK |

## API Levels

### High-Level API

```kotlin
// One-line authentication
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
val authToken = webAuth.jwtToken(clientAccountId, signers)
```

### Low-Level API

```kotlin
// Manual control over each step
val challenge = webAuth.getChallenge(clientAccountId)
webAuth.validateChallenge(challenge.transaction, clientAccountId)
val signedChallenge = webAuth.signTransaction(challenge.transaction, signers)
val authToken = webAuth.sendSignedChallenge(signedChallenge)
```

## Exception Types (18 Total)

| Exception | Purpose |
|-----------|---------|
| `WebAuthException` | Base exception for all SEP-10 errors |
| `ChallengeRequestException` | Challenge request failures (network, HTTP errors) |
| `ChallengeValidationException` | Base class for validation failures |
| `GenericChallengeValidationException` | Generic validation errors |
| `InvalidSequenceNumberException` | Sequence number not zero |
| `InvalidMemoTypeException` | Invalid memo type (not NONE/ID) |
| `InvalidMemoValueException` | Memo value mismatch |
| `MemoWithMuxedAccountException` | Both memo and muxed account present |
| `NoMemoForMuxedAccountsException` | Memo provided with muxed account |
| `InvalidOperationTypeException` | Non-ManageData operation found |
| `InvalidSourceAccountException` | Operation source mismatch |
| `InvalidHomeDomainException` | Home domain mismatch |
| `InvalidClientDomainSourceException` | Client domain source mismatch |
| `InvalidWebAuthDomainException` | Web auth domain mismatch |
| `InvalidTimeBoundsException` | Time bounds invalid or expired |
| `InvalidSignatureCountException` | Wrong number of signatures |
| `InvalidSignatureException` | Invalid server signature |
| `TokenSubmissionException` | Token submission failures |

## Security Features

| Feature | Status | Implementation |
|---------|--------|---------------|
| Time bounds validation | ✅ | With configurable grace period (default 300 seconds) |
| Replay attack prevention | ✅ | Sequence number zero + time bounds validation |
| Server signature verification | ✅ | Ed25519 cryptographic verification |
| Domain confusion prevention | ✅ | Home domain and web auth domain validation |
| MITM attack prevention | ✅ | Server signature validation before signing |
| Nonce validation | ✅ | ManageData operation value checked |
| Network passphrase validation | ✅ | Transaction hash includes network passphrase |

## Platform Support

All features work across all supported platforms:
- JVM (Android, Server)
- iOS
- macOS
- JavaScript (Browser & Node.js)

## Additional Information

**Documentation:** See `docs/sep/sep-10.md` for usage examples and API reference

**Specification:** [SEP-0010: Stellar Web Authentication](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep10`

**Integration Tests:** Live testnet integration against testanchor.stellar.org

**Last Updated:** 2025-12-09

## Legend

- ✅ **Implemented**: Feature is fully supported in the SDK
- ❌ **Not Implemented**: Feature is not currently supported
- ⚠️ **Partial**: Feature is partially supported with limitations
- ⚪ **N/A**: Not applicable (server-side feature)
