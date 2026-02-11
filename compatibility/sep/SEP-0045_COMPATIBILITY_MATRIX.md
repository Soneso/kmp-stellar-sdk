# SEP-0045 (Web Authentication for Contract Accounts) Compatibility Matrix

**Generated:** 2026-01-14

**SEP Version:** 1.0.0 (draft)<br>
**SEP Status:** Draft<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0045.md

## SEP Summary

SEP-45 defines a standard for authenticating Soroban smart contract accounts (C... addresses) using a challenge-response mechanism with authorization entry signing. This specification complements SEP-10, which handles traditional Stellar accounts (G... and M... addresses). SEP-45 enables contracts implementing custom authentication policies to prove ownership to service providers such as anchors.

## Overall Implementation

**Implementation Type:** Client-Side Only

This SDK implements the client-side of SEP-45 authentication. The implementation provides both high-level and low-level APIs for interacting with SEP-45 authentication servers.

## Overall Coverage

**Total Coverage:** 100% (35/35 features)

- Implemented: 35/35
- Not Implemented: 0/35

## Implementation Status

**Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/WebAuthForContracts.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/Sep45AuthToken.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/Sep45ChallengeResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/Sep45TokenResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/Sep45ClientDomainSigningDelegate.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep45/exceptions/` (22 exception types)

### Key Classes

- **`WebAuthForContracts`** - Main client class with methods: fromDomain, jwtToken, getChallenge, validateChallenge, signAuthorizationEntries, sendSignedChallenge, decodeAuthorizationEntries, encodeAuthorizationEntries
- **`Sep45AuthToken`** - JWT token parser with properties: token, account, issuedAt, expiresAt, issuer, clientDomain
- **`Sep45ClientDomainSigningDelegate`** - Functional interface for external domain signing (HSM/wallet backend)
- **`Sep45ChallengeResponse`** - Server challenge response wrapper (authorizationEntries, networkPassphrase)
- **`Sep45TokenResponse`** - Token submission response wrapper (token, error)

### Test Coverage

**Tests:** 161 test cases across 6 test files

**Test Files:**
- `Sep45AuthTokenTest.kt` - JWT parsing and validation (19 tests)
- `Sep45ChallengeValidationTest.kt` - All 13 validation checks (26 tests)
- `Sep45ExceptionsTest.kt` - Exception hierarchy and properties (34 tests)
- `Sep45ResponseParsingTest.kt` - Response parsing (35 tests)
- `WebAuthForContractsTest.kt` - High-level API and integration (46 tests)
- `Sep45IntegrationTest.kt` - Live testnet integration (1 test)

## Detailed Feature Matrix

### Client-Side Features

| Feature | Status | Notes |
|---------|--------|-------|
| **Challenge Request (GET)** | Implemented | Full support with all query parameters |
| Account parameter | Implemented | Required parameter - C... contract addresses only |
| Home domain parameter | Implemented | Optional home domain for multi-domain servers |
| Client domain parameter | Implemented | Optional client domain for wallet verification |
| Challenge response parsing | Implemented | JSON response with authorization_entries and network_passphrase |
| Flexible field name parsing | Implemented | Supports both snake_case and camelCase field names |
| **Challenge Validation (13 Checks)** | Implemented | All SEP-45 security requirements enforced |
| 1. Contract address validation | Implemented | Must match WEB_AUTH_CONTRACT_ID from stellar.toml |
| 2. Function name validation | Implemented | Must be "web_auth_verify" |
| 3. Server entry presence | Implemented | Entry with server signing key must exist |
| 4. Client entry presence | Implemented | Entry with client contract ID must exist |
| 5. Server signature verification | Implemented | Cryptographic Ed25519 signature validation |
| 6. Nonce consistency | Implemented | Nonce must be present and consistent across entries |
| 7. home_domain validation | Implemented | Must match expected home domain |
| 8. web_auth_domain validation | Implemented | Must match auth endpoint host |
| 9. web_auth_domain_account validation | Implemented | Must match SIGNING_KEY from stellar.toml |
| 10. account validation | Implemented | Must match client contract ID |
| 11. No sub-invocations | Implemented | Authorization entries must not contain nested calls |
| 12. client_domain validation | Implemented | If provided, must match expected domain |
| 13. client_domain_account validation | Implemented | If provided, must match client domain signing key |
| **Authorization Entry Signing** | Implemented | Client-side signing with multiple signers |
| Single signature | Implemented | Sign with single keypair |
| Multi-signature support | Implemented | Sign with multiple keypairs for threshold contracts |
| No signature (empty signers) | Implemented | Support for contracts without signature requirements |
| Client domain signing (local) | Implemented | Sign with client domain keypair |
| Client domain signing (external) | Implemented | Delegate signing to HSM/wallet backend |
| Signature expiration ledger | Implemented | Automatic (current+10) or custom expiration |
| **Token Submission (POST)** | Implemented | Submit signed authorization entries for JWT token |
| Form-urlencoded format | Implemented | Default content type (application/x-www-form-urlencoded) |
| JSON format | Implemented | Alternative content type (application/json) |
| Signed entries XDR | Implemented | Base64-encoded authorization entries array |
| Token response parsing | Implemented | Extract JWT and error from JSON response |
| **JWT Token Handling** | Implemented | Complete JWT parsing |
| JWT parsing | Implemented | Parse standard and SEP-45 claims |
| Standard claims (iss, sub, iat, exp) | Implemented | All RFC 7519 claims supported |
| SEP-45 claims (client_domain) | Implemented | Client domain claim extraction |
| Contract account extraction | Implemented | Extract C... address from sub claim |
| Expiration checking | Implemented | isExpired() method with system time comparison |
| Graceful error handling | Implemented | Returns token with defaults on parse error |
| **Account Types** | Implemented | Contract accounts only |
| Contract addresses (C...) | Implemented | Stellar smart contract accounts |
| **Error Handling** | Implemented | 22 exception types covering all failures |
| Challenge request errors (400, 403, 404, 5xx) | Implemented | Sep45ChallengeRequestException with status codes |
| Validation errors | Implemented | 13 specific Sep45ChallengeValidationException subtypes |
| Token submission errors (400, 401, 403, 5xx) | Implemented | Sep45TokenSubmissionException with status codes |
| Timeout errors (504) | Implemented | Sep45TimeoutException |
| Configuration errors | Implemented | Sep45NoEndpointException, Sep45NoContractIdException, Sep45NoSigningKeyException |
| Network errors | Implemented | Network failure handling with retries |
| **Advanced Features** | Implemented | Enterprise and wallet infrastructure support |
| Automatic configuration from stellar.toml | Implemented | WebAuthForContracts.fromDomain() discovers endpoints and keys |
| High-level API | Implemented | jwtToken() - one-line authentication |
| Low-level API | Implemented | Manual step-by-step control for custom flows |
| Custom HTTP client support | Implemented | Injectable HttpClient for testing/proxies |
| Custom HTTP headers | Implemented | Add custom headers to requests |
| HTTP request retries | Implemented | Automatic retry on server errors (3 attempts) |
| Network passphrase validation | Implemented | Validates network_passphrase in response if present |
| Automatic Soroban RPC URL | Implemented | Defaults based on network (testnet/mainnet) |

### Server-Side Features (Not Applicable)

| Feature | Status | Notes |
|---------|--------|-------|
| Challenge generation | N/A | Server-side functionality - not in scope for client SDK |
| Signature verification via simulation | N/A | Server-side functionality - not in scope for client SDK |
| JWT token generation | N/A | Server-side functionality - not in scope for client SDK |
| Contract authentication policy | N/A | Server-side functionality - not in scope for client SDK |

## API Levels

### High-Level API

```kotlin
// One-line authentication
val webAuth = WebAuthForContracts.fromDomain("testanchor.stellar.org", Network.TESTNET)
val authToken = webAuth.jwtToken(contractId, signers)
```

### Low-Level API

```kotlin
// Manual control over each step
val challenge = webAuth.getChallenge(contractId)
val authEntries = webAuth.decodeAuthorizationEntries(challenge.authorizationEntries!!)
webAuth.validateChallenge(authEntries, contractId)
val signedEntries = webAuth.signAuthorizationEntries(authEntries, contractId, signers, expirationLedger)
val authToken = webAuth.sendSignedChallenge(signedEntries)
```

## Exception Types (22 Total)

| Exception | Purpose |
|-----------|---------|
| `Sep45Exception` | Base exception for all SEP-45 errors |
| `Sep45ChallengeRequestException` | Challenge request failures (network, HTTP errors) |
| `Sep45ChallengeValidationException` | Base class for validation failures (sealed) |
| `Sep45InvalidContractAddressException` | Contract address mismatch |
| `Sep45InvalidFunctionNameException` | Function name not "web_auth_verify" |
| `Sep45InvalidHomeDomainException` | Home domain mismatch |
| `Sep45InvalidWebAuthDomainException` | Web auth domain mismatch |
| `Sep45InvalidAccountException` | Account parameter mismatch |
| `Sep45InvalidNonceException` | Nonce missing or inconsistent |
| `Sep45InvalidArgsException` | Invalid arguments in entry |
| `Sep45InvalidServerSignatureException` | Server signature invalid |
| `Sep45InvalidNetworkPassphraseException` | Network passphrase mismatch |
| `Sep45SubInvocationsFoundException` | Sub-invocations found |
| `Sep45MissingServerEntryException` | Server entry not found |
| `Sep45MissingClientEntryException` | Client entry not found |
| `Sep45MissingClientDomainException` | Client domain config error |
| `Sep45NoEndpointException` | Missing WEB_AUTH_FOR_CONTRACTS_ENDPOINT |
| `Sep45NoContractIdException` | Missing WEB_AUTH_CONTRACT_ID |
| `Sep45NoSigningKeyException` | Missing SIGNING_KEY |
| `Sep45TokenSubmissionException` | Token submission failures |
| `Sep45TimeoutException` | Request timeout (HTTP 504) |
| `Sep45UnknownResponseException` | Unexpected HTTP response |

## Security Features

| Feature | Status | Implementation |
|---------|--------|---------------|
| Server signature verification | Implemented | Ed25519 cryptographic verification using Auth preimage |
| Contract address validation | Implemented | Prevents contract substitution attacks |
| Function name validation | Implemented | Prevents unauthorized function calls |
| No sub-invocations check | Implemented | Prevents nested attack vectors |
| Domain validation | Implemented | home_domain and web_auth_domain checks |
| Nonce validation | Implemented | Prevents replay attacks |
| Network passphrase validation | Implemented | Ensures correct network |
| MITM attack prevention | Implemented | Server signature validation before signing |

## Comparison with SEP-10

| Aspect | SEP-10 | SEP-45 |
|--------|--------|--------|
| Account Type | G.../M... (Stellar accounts) | C... (Contract accounts) |
| Challenge Format | Transaction XDR | SorobanAuthorizationEntry XDR array |
| Authentication Method | Transaction signing | Authorization entry signing |
| Server Function | ManageData operations | web_auth_verify contract call |
| Memo Support | Yes (MEMO_ID) | No |
| stellar.toml Endpoint | WEB_AUTH_ENDPOINT | WEB_AUTH_FOR_CONTRACTS_ENDPOINT |
| stellar.toml Contract | N/A | WEB_AUTH_CONTRACT_ID |
| Validation Checks | 13 checks | 13 checks |

## Platform Support

All features work across all supported platforms:
- JVM (Android, Server)
- iOS
- macOS
- JavaScript (Browser and Node.js)

## Additional Information

**Documentation:** See `docs/sep/sep-45.md` for usage examples and API reference

**Specification:** [SEP-0045: Web Authentication for Contract Accounts](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0045.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep45`

**Last Updated:** 2026-01-14

## Legend

- **Implemented**: Feature is fully supported in the SDK
- **Not Implemented**: Feature is not currently supported
- **Partial**: Feature is partially supported with limitations
- **N/A**: Not applicable (server-side feature)
