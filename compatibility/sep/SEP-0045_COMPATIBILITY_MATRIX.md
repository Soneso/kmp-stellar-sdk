# SEP-0045 (Stellar Web Authentication for Contract Accounts) Compatibility Matrix

**Generated:** 2026-04-04 00:01:18

**SEP Version:** 0.1.1  
**SEP Status:** Draft  
**SDK Version:** 1.3.1  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0045.md

## SEP Summary

Web authentication for contract accounts (`C...` addresses). Extends SEP-10 to smart contract wallets; services supporting both account types should implement both SEPs.

## Overall Coverage

**Total Coverage:** 100.0% (19/19 fields)

- ✅ **Implemented:** 19/19
- ❌ **Not Implemented:** 0/19

**Required Fields:** 100.0% (13/13)

**Optional Fields:** 100.0% (6/6)

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/Sep45AuthToken.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/Sep45ChallengeResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/Sep45ClientDomainSigningDelegate.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/Sep45TokenResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/WebAuthForContracts.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45ChallengeRequestException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45ChallengeValidationException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidAccountException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidArgsException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidContractAddressException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidFunctionNameException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidHomeDomainException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidNetworkPassphraseException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidNonceException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidServerSignatureException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45InvalidWebAuthDomainException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45MissingClientDomainException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45MissingClientEntryException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45MissingServerEntryException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45NoContractIdException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45NoEndpointException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45NoSigningKeyException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45SubInvocationsFoundException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45TimeoutException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45TokenSubmissionException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/Sep45UnknownResponseException.kt`

### Key Classes

- **`Sep45AuthToken`** - Methods: isExpired, toString, parse
- **`Sep45ChallengeResponse`** - Methods: fromJson
- **`Sep45TokenResponse`**
- **`WebAuthForContracts`** - Methods: fromDomain, getChallenge, validateChallenge, signAuthorizationEntries, sendSignedChallenge, decodeAuthorizationEntries, encodeAuthorizationEntries, extractArgsFromEntry, verifyServerSignature, scAddressToString, entryToBase64, base64ToEntry
- **`Sep45ChallengeRequestException`**
- **`Sep45ChallengeValidationException`**
- **`Sep45InvalidAccountException`**
- **`Sep45InvalidArgsException`**
- **`Sep45InvalidContractAddressException`**
- **`Sep45InvalidFunctionNameException`**
- **`Sep45InvalidHomeDomainException`**
- **`Sep45InvalidNetworkPassphraseException`**
- **`Sep45InvalidNonceException`**
- **`Sep45InvalidServerSignatureException`**
- **`Sep45InvalidWebAuthDomainException`**
- **`Sep45MissingClientDomainException`**
- **`Sep45MissingClientEntryException`**
- **`Sep45MissingServerEntryException`**
- **`Sep45NoContractIdException`**
- **`Sep45NoEndpointException`**
- **`Sep45NoSigningKeyException`**
- **`Sep45SubInvocationsFoundException`**
- **`Sep45TimeoutException`**
- **`Sep45TokenSubmissionException`**
- **`Sep45UnknownResponseException`**

### Test Coverage

**Tests:** 162 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/sep/sep45/Sep45IntegrationTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep45/Sep45AuthTokenTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep45/Sep45ChallengeValidationTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep45/Sep45ExceptionsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep45/Sep45ResponseParsingTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep45/WebAuthForContractsTest.kt`

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Challenge Request Parameters | 100.0% | 1/1 | 3 | 3 |
| Challenge Response Fields | 100.0% | 1/1 | 2 | 2 |
| Contract Verification Function Arguments | 100.0% | 5/5 | 7 | 7 |
| JWT Claims | 100.0% | 4/4 | 5 | 5 |
| Token Request Parameters | 100.0% | 1/1 | 1 | 1 |
| Token Response Fields | 100.0% | 1/1 | 1 | 1 |

## Detailed Field Comparison

### Challenge Request Parameters

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `account` | ✓ | ✅ | `clientAccountId` | The Client Account address (C...) that the Client wishes to authenticate |
| `home_domain` |  | ✅ | `homeDomain` | A Home Domain. Servers that generate tokens for multiple Home Domains can use this parameter |
| `client_domain` |  | ✅ | `clientDomain` | a Client Domain. Supplied by Clients that intend to verify their domain in addition |

### Challenge Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `authorization_entries` | ✓ | ✅ | `authorizationEntries` | XDR-encoded SorobanAuthorizationEntries. It contains an entry for the Client Account |
| `network_passphrase` |  | ✅ | `networkPassphrase` | Stellar network passphrase used by the Server. This allows a Client to verify |

### Contract Verification Function Arguments

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `account` | ✓ | ✅ | `clientAccountId` | The client account address |
| `home_domain` | ✓ | ✅ | `homeDomain` | The home domain |
| `web_auth_domain` | ✓ | ✅ | `serverHomeDomain` | The server's domain |
| `web_auth_domain_account` | ✓ | ✅ | `serverSigningKey` | The server's SIGNING_KEY |
| `client_domain` |  | ✅ | `clientDomain` | The client domain |
| `client_domain_account` |  | ✅ | `clientDomainAccountId` | The client domain's SIGNING_KEY |
| `nonce` | ✓ | ✅ | `nonce` | A random string generated by the server to prevent replay attacks |

### JWT Claims

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `iss` | ✓ | ✅ | `issuer` | a Uniform Resource Identifier (URI) for the issuer |
| `sub` | ✓ | ✅ | `account` | the Client Account's address (C...) |
| `iat` | ✓ | ✅ | `issuedAt` | current timestamp |
| `exp` | ✓ | ✅ | `expiresAt` | a server can pick its own expiration period for the token |
| `client_domain` |  | ✅ | `clientDomain` | included if the challenge transaction contained a client_domain |

### Token Request Parameters

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `authorization_entries` | ✓ | ✅ | `authorizationEntries` | XDR-encoded SorobanAuthorizationEntries |

### Token Response Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `token` | ✓ | ✅ | `token` | The JWT that can be used to authenticate future endpoint calls with the anchor |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0045!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0045](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0045.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0045`
