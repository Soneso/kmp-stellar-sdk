# SEP-45: Web Authentication for Contract Accounts

**[SEP-0045 Compatibility Matrix](../../compatibility/sep/SEP-0045_COMPATIBILITY_MATRIX.md)** - Full implementation coverage details

Stellar Web Authentication for Contract Accounts provides secure authentication for Soroban smart contract accounts (C... addresses) using a challenge-response protocol with authorization entry signing. This complements SEP-10, which handles traditional Stellar accounts (G... and M... addresses).

**Use Cases**:
- Authenticate contract accounts with anchor services
- Prove contract ownership for deposits and withdrawals
- Access protected APIs requiring contract authentication
- Enable smart wallet authentication workflows
- Support custom authentication policies via contract logic

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep45.*
import com.soneso.stellar.sdk.sep.sep45.exceptions.*
```

## Basic Authentication

```kotlin
// Initialize from domain's stellar.toml
val webAuth = WebAuthForContracts.fromDomain("testanchor.stellar.org", Network.TESTNET)

// Contract account to authenticate
val contractId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

// Signer keypair (contract's authentication key)
val signerKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")

// Authenticate and get JWT token
val authToken = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signerKeyPair)
)

// Use token in API requests
// Example: Authorization header with Bearer token
// header("Authorization", "Bearer ${authToken.token}")
```

## Using the JWT Token

```kotlin
// httpClient, signers: from the previous steps of this flow
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
// Authenticate and get JWT token
val authToken = webAuth.jwtToken(contractId, signers)

// Use the JWT token in Authorization header for protected endpoints
val response = httpClient.get("https://testanchor.stellar.org/sep24/info") {
    header("Authorization", "Bearer ${authToken.token}")
}

// Check token expiration before making requests
if (authToken.isExpired()) {
    // Token expired, re-authenticate
    val newToken = webAuth.jwtToken(contractId, signers)
}

// Access token claims for debugging or logging
println("Authenticated contract: ${authToken.account}")
println("Token expires at: ${authToken.expiresAt}")
```

## Contracts Without Signature Requirements

```kotlin
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
// Some contracts implement __check_auth without requiring signatures
// (e.g., always-pass contracts for testing, or time-based access)
val authToken = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = emptyList()  // Valid for such contracts
)
```

## Multi-Signature Authentication

```kotlin
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
// Authenticate with multiple signers for threshold contracts
val signer1 = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val signer2 = KeyPair.fromSecretSeed("SABAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAFNE7")
val signer3 = KeyPair.fromSecretSeed("SABQGAYDAMBQGAYDAMBQGAYDAMBQGAYDAMBQGAYDAMBQGAYDAMBQGC45")

val authToken = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signer1, signer2, signer3)
)
```

## Client Domain Verification

```kotlin
val signerKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
// Prove your wallet identity to receive premium benefits from the anchor
// clientDomainSeed from your wallet's stellar.toml SIGNING_KEY
val clientDomainKeyPair = KeyPair.fromSecretSeed(clientDomainSeed)

val authToken = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signerKeyPair),
    clientDomain = "wallet.mycompany.com",
    clientDomainAccountKeyPair = clientDomainKeyPair
)

// Token includes client_domain claim - anchor can now verify your wallet
println("Client Domain: ${authToken.clientDomain}")
```

## Client Domain with External Signing (Wallet Backend)

```kotlin
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
val signerKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")

val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
// When wallet domain key is managed by your backend server
val signingDelegate = Sep45ClientDomainSigningDelegate { entryXdr ->
    // Send authorization entry to your wallet's backend server for signing
    val httpClient = HttpClient()
    val response = httpClient.post("https://wallet.mycompany.com/sign-sep45") {
        contentType(ContentType.Application.Json)
        setBody("""{"entry": "$entryXdr"}""")
    }

    // Backend returns signed authorization entry XDR
    val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    json["signed_entry"]?.jsonPrimitive?.content
        ?: throw Exception("Signing failed")
}

val authToken = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signerKeyPair),
    clientDomain = "wallet.mycompany.com",
    clientDomainSigningDelegate = signingDelegate
)
```

## Custom Signature Expiration

```kotlin
// currentLedger: from the previous steps of this flow
val signerKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
// By default, signatures expire at current ledger + 10 (~50-60 seconds)
// Specify custom expiration for longer validity
val authToken = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signerKeyPair),
    signatureExpirationLedger = currentLedger + 100  // ~8-10 minutes
)
```

## Low-Level API (Advanced)

Most developers should use `jwtToken()` for authentication. Use the low-level API when you need custom validation logic, multi-step user approval flows, or integration with external signing systems.

> Challenge entries are validated and signed across all three Soroban credential arms — legacy `ADDRESS`, `ADDRESS_V2`, and `ADDRESS_WITH_DELEGATES` (CAP-71) — and the matching hash preimage is selected automatically. This is transparent: no API change is required for any of the calls below.

```kotlin
// currentLedger: from the previous steps of this flow
val signerKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
// Manual control over each authentication step
val webAuth = WebAuthForContracts.fromDomain("testanchor.stellar.org", Network.TESTNET)

// Step 1: Request challenge
val challenge = webAuth.getChallenge(
    clientAccountId = contractId,
    homeDomain = "testanchor.stellar.org"
)

// Step 2: Decode authorization entries
val authEntries = webAuth.decodeAuthorizationEntries(
    challenge.authorizationEntries!!
)

// Step 3: Validate challenge (13 security checks)
webAuth.validateChallenge(
    authEntries = authEntries,
    clientAccountId = contractId,
    homeDomain = "testanchor.stellar.org"
)

// Step 4: Sign authorization entries
val signedEntries = webAuth.signAuthorizationEntries(
    authEntries = authEntries,
    clientAccountId = contractId,
    signers = listOf(signerKeyPair),
    signatureExpirationLedger = currentLedger + 10
)

// Step 5: Submit and get token
val authToken = webAuth.sendSignedChallenge(signedEntries)
```

## Error Handling

```kotlin
// signers: from the previous steps of this flow
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
try {
    val authToken = webAuth.jwtToken(contractId, signers)
} catch (e: Sep45ChallengeRequestException) {
    // Challenge request failed (network, server error)
    println("Challenge request failed: ${e.errorMessage}")
} catch (e: Sep45InvalidServerSignatureException) {
    // Server signature invalid (possible MITM attack)
    println("CRITICAL: Invalid server signature")
} catch (e: Sep45InvalidContractAddressException) {
    // Contract address mismatch
    println("Contract address mismatch: expected ${e.expected}, got ${e.actual}")
} catch (e: Sep45ChallengeValidationException) {
    // Other validation failure (base class)
    println("Challenge validation failed: ${e.message}")
} catch (e: Sep45TokenSubmissionException) {
    // Token submission failed (signature verification)
    println("Token submission failed: ${e.message}")
} catch (e: Sep45Exception) {
    // Any other SEP-45 error (base class)
    println("Authentication failed: ${e.message}")
}
```

## Key Differences from SEP-10

| Aspect | SEP-10 | SEP-45 |
|--------|--------|--------|
| Account Type | G... / M... (Stellar accounts) | C... (Contract accounts) |
| Challenge Format | Transaction XDR | SorobanAuthorizationEntry XDR |
| Authentication Method | Transaction signing | Authorization entry signing |
| Server Function | ManageData operations | `web_auth_verify` contract call |
| Memo Support | Yes (memo ID) | No (contracts do not use memos) |
| stellar.toml Fields | WEB_AUTH_ENDPOINT | WEB_AUTH_FOR_CONTRACTS_ENDPOINT, WEB_AUTH_CONTRACT_ID |

## API Reference

**Main Methods**:
- `WebAuthForContracts.fromDomain(domain, network)` - Initialize from stellar.toml
- `webAuth.jwtToken(clientAccountId, signers, ...)` - Complete authentication flow
- `webAuth.getChallenge(clientAccountId, ...)` - Request challenge (low-level)
- `webAuth.validateChallenge(authEntries, ...)` - Validate challenge (low-level)
- `webAuth.signAuthorizationEntries(authEntries, ...)` - Sign entries (low-level)
- `webAuth.sendSignedChallenge(signedEntries)` - Submit challenge (low-level)
- `webAuth.decodeAuthorizationEntries(base64Xdr)` - Decode XDR entries
- `webAuth.encodeAuthorizationEntries(entries)` - Encode entries to XDR

**Data Classes**:
- `Sep45AuthToken` - JWT token with parsed claims (account, issuer, issuedAt, expiresAt, clientDomain)
- `Sep45ChallengeResponse` - Challenge response from server (authorizationEntries, networkPassphrase)
- `Sep45TokenResponse` - Token response from server (token, error)

**Interfaces**:
- `Sep45ClientDomainSigningDelegate` - External signing for wallet backend infrastructure

**Exception Types (22 Total)**:
- `Sep45Exception` - Base exception for all SEP-45 errors
- `Sep45ChallengeRequestException` - Challenge request failures (network, HTTP errors)
- `Sep45ChallengeValidationException` - Base class for validation failures
- `Sep45InvalidContractAddressException` - Contract address mismatch
- `Sep45InvalidFunctionNameException` - Function name not "web_auth_verify"
- `Sep45InvalidHomeDomainException` - Home domain mismatch
- `Sep45InvalidWebAuthDomainException` - Web auth domain mismatch
- `Sep45InvalidAccountException` - Account parameter mismatch
- `Sep45InvalidNonceException` - Nonce missing or inconsistent
- `Sep45InvalidArgsException` - Invalid arguments in authorization entry
- `Sep45InvalidServerSignatureException` - Server signature verification failed
- `Sep45InvalidNetworkPassphraseException` - Network passphrase mismatch
- `Sep45SubInvocationsFoundException` - Sub-invocations not allowed
- `Sep45MissingServerEntryException` - Server entry not found
- `Sep45MissingClientEntryException` - Client entry not found
- `Sep45MissingClientDomainException` - Client domain configuration error
- `Sep45NoEndpointException` - WEB_AUTH_FOR_CONTRACTS_ENDPOINT missing
- `Sep45NoContractIdException` - WEB_AUTH_CONTRACT_ID missing
- `Sep45NoSigningKeyException` - SIGNING_KEY missing
- `Sep45TokenSubmissionException` - Token submission failures
- `Sep45TimeoutException` - Request timeout (HTTP 504)
- `Sep45UnknownResponseException` - Unexpected HTTP response

**Specification**: [SEP-45: Web Authentication for Contract Accounts](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0045.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep45`

**Last Updated**: 2026-01-14
