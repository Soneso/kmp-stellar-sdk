# SEP-0010 (Stellar Web Authentication) Compatibility Matrix

**Generated:** 2026-02-13 20:09:45

**SEP Version:** 3.4.1<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md

## SEP Summary

This SEP defines the standard way for clients such as wallets or exchanges to create authenticated web sessions on behalf of a user who holds a Stellar account. A wallet may want to authenticate with any web service which requires a Stellar account ownership verification, for example, to upload KYC information to an anchor in an authenticated way as described in [SEP-12](sep-0012.md). This SEP also supports authenticating users of shared, omnibus, or pooled Stellar accounts. Clients can use [memos](#memos) or [muxed accounts](#muxed-accounts) to distinguish users or sub-accounts of shared accounts.

## Overall Coverage

**Total Coverage:** 100.0% (9/9 fields)

- ✅ **Implemented:** 9/9
- ❌ **Not Implemented:** 0/9
- **Required Fields:** 100.0% (1/1)

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

- **`parses`**
- **`AuthToken`** - Methods: isExpired, toString, parse, decodeBase64UrlSafe
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
| JWT Features | 100.0% | 1/1 | 9 | 9 |

## Detailed Field Comparison

### JWT Features

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `Content` |  | ✅ | `(handled by sendSignedChallenge)` | -Type: `application/x-www-form-urlencoded`, body: `transaction=<signed XDR (URL-encoded)>`) |
| `decode` |  | ✅ | `validateChallenge` | the received input as a base64-urlencoded XDR representation of Stellar transaction envelope; |
| `verify` |  | ✅ | `validateChallenge` | that transaction source account is equal to the **Server Account** |
| `if` | ✓ | ✅ | `validateChallenge` | the first operation's source account exists: - verify that the remaining signature count is one o... |
| `iss` |  | ✅ | `token` | (the principal that issued a token, [RFC7519, Section 4.1.1](https://tools.ietf.org/html/rfc7519#section-4.1.1)) — a [Uniform Resource Identifier (URI)] for the issuer (`https://example.com` or `https://example.com/G...`) |
| `sub` |  | ✅ | `token` | (the principal that is the subject of the JWT, [RFC7519, Section 4.1.2](https://tools.ietf.org/html/rfc7519#section-4.1.2)) — there are several possible formats: - If the **Client Account** is a muxed account (`M...`), the `sub` value should be the muxed account (`M...`). - If the **Client Account** is a stellar account (`G...`): - And, a memo was attached to the challenge transaction, the `sub` should be the stellar account appended with the memo, separated by a colon (`G...:17509749319012223907`). - Otherwise, the `sub` value should be Stellar account (`G...`). |
| `iat` |  | ✅ | `token` | (the time at which the JWT was issued [RFC7519, Section 4.1.6](https://tools.ietf.org/html/rfc7519#section-4.1.6)) — current timestamp (`1530644093`) |
| `exp` |  | ✅ | `token` | (the expiration time on or after which the JWT must not be accepted for processing, [RFC7519, Section 4.1.4](https://tools.ietf.org/html/rfc7519#section-4.1.4)) — a server can pick its own expiration period for the token (`1530730493`) |
| `client_domain` |  | ✅ | `token` | - (optional) a nonstandard JWT claim containing the client home domain, included if the challenge... |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0010](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0010`
