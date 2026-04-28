# SEP-0010 (Stellar Web Authentication) Compatibility Matrix

**Generated:** 2026-04-28 02:38:11

**SEP Version:** 3.4.1  
**SEP Status:** Active  
**SDK Version:** 1.5.1  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md

## SEP Summary

Lets wallets and exchanges create authenticated web sessions by proving Stellar account ownership. Supports individual, shared, and muxed accounts.

## Overall Coverage

**Total Coverage:** 100.0% (24/24 fields)

- ✅ **Implemented:** 24/24
- ❌ **Not Implemented:** 0/24

**Required Fields:** 100.0% (19/19)

**Optional Fields:** 100.0% (5/5)

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/AuthToken.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/ChallengeResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/TokenSubmissionRequest.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/TokenSubmissionResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/WebAuth.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/ChallengeRequestException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/ChallengeValidationException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/GenericChallengeValidationException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidClientDomainSourceException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidHomeDomainException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidMemoTypeException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidMemoValueException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidOperationTypeException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidSequenceNumberException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidSignatureCountException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidSignatureException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidSourceAccountException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidTimeBoundsException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/InvalidWebAuthDomainException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/MemoWithMuxedAccountException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/NoMemoForMuxedAccountsException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/TokenSubmissionException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep10/exceptions/WebAuthException.kt`

### Key Classes

- **`AuthToken`** - Methods: isExpired, toString, parse
- **`ChallengeResponse`**
- **`TokenSubmissionRequest`**
- **`TokenSubmissionResponse`**
- **`WebAuth`** - Methods: fromDomain, jwtToken, getChallenge, validateChallengeRequest, buildChallengeUrl, validateChallenge, validateOperations, validateTimeBounds, validateServerSignature, signTransaction, sendSignedChallenge
- **`ChallengeRequestException`**
- **`ChallengeValidationException`**
- **`GenericChallengeValidationException`**
- **`InvalidClientDomainSourceException`**
- **`InvalidHomeDomainException`**
- **`InvalidMemoTypeException`**
- **`InvalidMemoValueException`**
- **`InvalidOperationTypeException`**
- **`InvalidSequenceNumberException`**
- **`InvalidSignatureCountException`**
- **`InvalidSignatureException`**
- **`InvalidSourceAccountException`**
- **`InvalidTimeBoundsException`**
- **`InvalidWebAuthDomainException`**
- **`MemoWithMuxedAccountException`**
- **`NoMemoForMuxedAccountsException`**
- **`TokenSubmissionException`**
- **`WebAuthException`**

### Test Coverage

**Tests:** 165 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/sep/sep10/WebAuthIntegrationTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/AuthTokenEnhancedTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/AuthTokenTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/Sep10ExceptionsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/WebAuthChallengeTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/WebAuthClientDomainSigningDelegateTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/WebAuthJwtTokenTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/WebAuthSigningTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/WebAuthTokenSubmissionTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep10/WebAuthValidationTest.kt`

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Authentication Endpoints | 100.0% | 2/2 | 2 | 2 |
| Challenge Transaction Features | 100.0% | 8/8 | 9 | 9 |
| Client Domain Features | 100.0% | N/A | 3 | 3 |
| JWT Token Features | 100.0% | 4/4 | 4 | 4 |
| Verification Features | 100.0% | 5/5 | 6 | 6 |

## Detailed Field Comparison

### Authentication Endpoints

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `get_auth_challenge` | ✓ | ✅ | `getChallenge` | GET /auth endpoint - Returns challenge transaction |
| `post_auth_token` | ✓ | ✅ | `sendSignedChallenge` | POST /auth endpoint - Validates signed challenge and returns JWT token |

### Challenge Transaction Features

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `challenge_transaction_generation` | ✓ | ✅ | `getChallenge` | Generate challenge transaction with proper structure |
| `transaction_envelope_format` | ✓ | ✅ | `validateChallenge` | Challenge uses proper Stellar transaction envelope format |
| `sequence_number_zero` | ✓ | ✅ | `validateChallenge` | Challenge transaction has sequence number 0 |
| `manage_data_operations` | ✓ | ✅ | `validateChallenge` | Challenge uses ManageData operations for auth data |
| `home_domain_operation` | ✓ | ✅ | `validateChallenge` | First operation contains home_domain + " auth" as data name |
| `web_auth_domain_operation` |  | ✅ | `validateChallenge` | Optional operation with web_auth_domain for domain verification |
| `timebounds_enforcement` | ✓ | ✅ | `validateChallenge` | Challenge transaction has timebounds for expiration |
| `server_signature` | ✓ | ✅ | `validateChallenge` | Challenge is signed by server before sending to client |
| `nonce_generation` | ✓ | ✅ | `getChallenge` | Random nonce in ManageData operation value |

### Client Domain Features

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `client_domain_parameter` |  | ✅ | `getChallenge` | Support optional client_domain parameter in GET /auth |
| `client_domain_operation` |  | ✅ | `validateChallenge` | Add client_domain ManageData operation to challenge |
| `client_domain_signature` |  | ✅ | `signTransaction` | Require signature from client domain account |

### JWT Token Features

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `jwt_token_generation` | ✓ | ✅ | `sendSignedChallenge` | Generate JWT token after successful challenge validation |
| `jwt_token_response` | ✓ | ✅ | `sendSignedChallenge` | Return JWT token in JSON response with "token" field |
| `jwt_expiration` | ✓ | ✅ | `isExpired` | JWT token includes expiration time |
| `jwt_claims` | ✓ | ✅ | `parse` | JWT token includes required claims (sub, iat, exp) |

### Verification Features

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `challenge_validation` | ✓ | ✅ | `validateChallenge` | Validate challenge transaction structure and content |
| `signature_verification` | ✓ | ✅ | `validateChallenge` | Verify all signatures on challenge transaction |
| `multi_signature_support` | ✓ | ✅ | `signTransaction` | Support multiple signatures on challenge (client account + signers) |
| `timebounds_validation` | ✓ | ✅ | `validateChallenge` | Validate challenge is within valid time window |
| `home_domain_validation` | ✓ | ✅ | `validateChallenge` | Validate home domain in challenge matches server |
| `memo_support` |  | ✅ | `getChallenge` | Support optional memo in challenge for muxed accounts |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0010!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0010](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0010`
