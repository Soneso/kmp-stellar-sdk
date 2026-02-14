# SEP-0030 (Account Recovery: multi-party recovery of Stellar accounts) Compatibility Matrix

**Generated:** 2026-02-14 20:32:36

**SEP Version:** 0.8.1<br>
**SEP Status:** Draft<br>
**SDK Version:** 1.3.0<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md

## SEP Summary

Account Recovery: multi-party recovery of Stellar accounts using alternative authentication methods

## Overall Coverage

**Total Coverage:** 100.0% (35/35 fields)

- ✅ **Implemented:** 35/35
- ❌ **Not Implemented:** 0/35
- **Required Fields:** 100.0% (30/30)

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30AccountResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30AccountsResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30AuthMethod.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30Request.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30RequestIdentity.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30ResponseIdentity.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30ResponseSigner.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30Service.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/Sep30SignatureResponse.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/exceptions/Sep30BadRequestException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/exceptions/Sep30ConflictException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/exceptions/Sep30Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/exceptions/Sep30InvalidResponseException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/exceptions/Sep30NotFoundException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/exceptions/Sep30UnauthorizedException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep30/exceptions/Sep30UnknownResponseException.kt`

### Key Classes

- **`Sep30AccountResponse`** - Methods: fromJson
- **`Sep30AccountsResponse`** - Methods: fromJson
- **`Sep30AuthMethod`** - Methods: toJson
- **`Sep30Request`** - Methods: toJson
- **`Sep30RequestIdentity`** - Methods: toJson
- **`Sep30ResponseIdentity`** - Methods: fromJson
- **`Sep30ResponseSigner`** - Methods: fromJson
- **`Sep30Service`** - Methods: registerAccount, updateIdentitiesForAccount, signTransaction, accountDetails, deleteAccount, accounts, mapToJsonString, anyToJsonElement, mapToJsonElement, handleAccountResponse, handleSignatureResponse, handleAccountsResponse, extractErrorMessage
- **`Sep30SignatureResponse`** - Methods: fromJson
- **`Sep30BadRequestException`** - Methods: toString
- **`Sep30ConflictException`** - Methods: toString
- **`Sep30InvalidResponseException`** - Methods: toString
- **`Sep30NotFoundException`** - Methods: toString
- **`Sep30UnauthorizedException`** - Methods: toString
- **`Sep30UnknownResponseException`** - Methods: toString

### Test Coverage

**Tests:** 83 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep30/Sep30ExceptionsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep30/Sep30ResponseParsingTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep30/Sep30ServiceTest.kt`

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| API Endpoints | 100.0% | 6/6 | 6 | 6 |
| Account Details Response | 100.0% | 5/5 | 6 | 6 |
| Authentication Methods | 100.0% | N/A | 3 | 3 |
| Error Response | 100.0% | 1/1 | 1 | 1 |
| HTTP Error Codes | 100.0% | 4/4 | 4 | 4 |
| List Accounts (GET /accounts) | 100.0% | 1/1 | 2 | 2 |
| Register Account (POST /accounts/<address>) | 100.0% | 5/5 | 5 | 5 |
| Sign Transaction (POST /accounts/<address>/sign/<signing-address>) | 100.0% | 1/1 | 1 | 1 |
| Signature Response | 100.0% | 2/2 | 2 | 2 |
| Update Identities (PUT /accounts/<address>) | 100.0% | 5/5 | 5 | 5 |

## Detailed Field Comparison

### API Endpoints

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `POST /accounts/<address>` | ✓ | ✅ | `Sep30Service.registerAccount` | Register account |
| `PUT /accounts/<address>` | ✓ | ✅ | `Sep30Service.updateIdentitiesForAccount` | Update identities |
| `POST /accounts/<address>/sign/<signing-address>` | ✓ | ✅ | `Sep30Service.signTransaction` | Sign transaction |
| `GET /accounts/<address>` | ✓ | ✅ | `Sep30Service.accountDetails` | Account details |
| `DELETE /accounts/<address>` | ✓ | ✅ | `Sep30Service.deleteAccount` | Delete account |
| `GET /accounts` | ✓ | ✅ | `Sep30Service.accounts` | List accounts |

### Account Details Response

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `address` | ✓ | ✅ | `Sep30AccountResponse.address` | Stellar account address |
| `identities` | ✓ | ✅ | `Sep30AccountResponse.identities` | Array of response identities |
| `identities[].role` |  | ✅ | `Sep30ResponseIdentity.role` | Role of the identity |
| `identities[].authenticated` | ✓ | ✅ | `Sep30ResponseIdentity.authenticated` | Whether identity is authenticated |
| `signers` | ✓ | ✅ | `Sep30AccountResponse.signers` | Array of signers |
| `signers[].key` | ✓ | ✅ | `Sep30ResponseSigner.key` | Signer public key |

### Authentication Methods

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `stellar_address` |  | ✅ | `Sep30AuthMethod type constant` | Stellar address auth method |
| `phone_number` |  | ✅ | `Sep30AuthMethod type constant` | Phone number auth method |
| `email` |  | ✅ | `Sep30AuthMethod type constant` | Email auth method |

### Error Response

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `error` | ✓ | ✅ | `Sep30Exception.message` | Error message |

### HTTP Error Codes

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `400 Bad Request` | ✓ | ✅ | `Sep30BadRequestException` | Invalid request |
| `401 Unauthorized` | ✓ | ✅ | `Sep30UnauthorizedException` | Authentication failed |
| `404 Not Found` | ✓ | ✅ | `Sep30NotFoundException` | Account not found |
| `409 Conflict` | ✓ | ✅ | `Sep30ConflictException` | Account already registered |

### List Accounts (GET /accounts)

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `accounts` | ✓ | ✅ | `Sep30AccountsResponse.accounts` | Array of account objects |
| `after` |  | ✅ | `Sep30Service.accounts parameter` | Pagination cursor |

### Register Account (POST /accounts/<address>)

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `identities` | ✓ | ✅ | `Sep30Request.identities` | Array of identities for registration |
| `identities[].role` | ✓ | ✅ | `Sep30RequestIdentity.role` | Role of the identity (owner, other) |
| `identities[].auth_methods` | ✓ | ✅ | `Sep30RequestIdentity.authMethods` | Authentication methods |
| `identities[].auth_methods[].type` | ✓ | ✅ | `Sep30AuthMethod.type` | Auth method type |
| `identities[].auth_methods[].value` | ✓ | ✅ | `Sep30AuthMethod.value` | Auth method value |

### Sign Transaction (POST /accounts/<address>/sign/<signing-address>)

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `transaction` | ✓ | ✅ | `Sep30Service.signTransaction parameter` | XDR base64 encoded transaction |

### Signature Response

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `signature` | ✓ | ✅ | `Sep30SignatureResponse.signature` | Base64 encoded signature |
| `network_passphrase` | ✓ | ✅ | `Sep30SignatureResponse.networkPassphrase` | Network passphrase |

### Update Identities (PUT /accounts/<address>)

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `identities` | ✓ | ✅ | `Sep30Request.identities` | Replacement identities |
| `identities[].role` | ✓ | ✅ | `Sep30RequestIdentity.role` | Role of the identity |
| `identities[].auth_methods` | ✓ | ✅ | `Sep30RequestIdentity.authMethods` | Authentication methods |
| `identities[].auth_methods[].type` | ✓ | ✅ | `Sep30AuthMethod.type` | Auth method type |
| `identities[].auth_methods[].value` | ✓ | ✅ | `Sep30AuthMethod.value` | Auth method value |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0030](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0030`
