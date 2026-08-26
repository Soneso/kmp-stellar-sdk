# SEP-10: Stellar Web Authentication

**[SEP-0010 Compatibility Matrix](../../compatibility/sep/SEP-0010_COMPATIBILITY_MATRIX.md)** - Full implementation coverage details

Stellar Web Authentication provides secure authentication for wallets and applications using a challenge-response protocol with transaction signing. Authenticate with Stellar services to access protected endpoints for SEP-6, SEP-12, SEP-24, SEP-31, and other protocols.

**Use Cases**:
- Authenticate users with anchor services for deposits and withdrawals
- Prove account ownership for KYC submission
- Access protected APIs requiring user authentication
- Prove wallet identity to receive premium service benefits
- Enable multi-signature authentication workflows

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.sep.sep10.exceptions.*
```

## Basic Authentication

```kotlin
// Initialize from domain's stellar.toml
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

// Generate keypair
val userKeyPair = KeyPair.random()

// Authenticate and get JWT token
val authToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
)

// Use token in API requests (httpClient setup omitted for brevity)
// Example: Authorization header with Bearer token
// header("Authorization", "Bearer ${authToken.token}")
```

## Using the JWT Token

```kotlin
// httpClient, signers: from the previous steps of this flow
val clientAccountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// Authenticate and get JWT token
val authToken = webAuth.jwtToken(clientAccountId, signers)

// Use the JWT token in Authorization header for protected endpoints
val response = httpClient.get("https://testanchor.stellar.org/kyc/customer") {
    header("Authorization", "Bearer ${authToken.token}")
}

// Check token expiration before making requests
if (authToken.isExpired()) {
    // Token expired, re-authenticate
    val newToken = webAuth.jwtToken(clientAccountId, signers)
}

// Access token claims for debugging or logging
println("Authenticated as: ${authToken.account}")
println("Token expires at: ${authToken.exp}")
```

## Muxed Account Authentication

```kotlin
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// Authenticate with muxed account (M... address)
val muxedAccountAddress = "MAAAAAAAAAAAAAB7BQ2L7E5NBWMXDUCMZSIPOBKRDSBYVLMXGSSKF6YNPIB7Y77ITLVL6"

val authToken = webAuth.jwtToken(
    clientAccountId = muxedAccountAddress,
    signers = listOf(userKeyPair)
)
```

## Memo-Based Sub-Account Authentication

```kotlin
// custodialAccountId: from the previous steps of this flow
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// For custodial services using memos to identify users
val authToken = webAuth.jwtToken(
    clientAccountId = custodialAccountId,
    signers = listOf(userKeyPair),
    memo = 12345L
)

// Token sub claim will be "GACCOUNT...:12345"
println("Sub: ${authToken.sub}")
println("Account: ${authToken.account}")
println("Memo: ${authToken.memo}")
```

## Client Domain Verification

```kotlin
val userAccountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// Prove your wallet identity to receive premium benefits from the anchor
// clientDomainSeed from your wallet's stellar.toml SIGNING_KEY
val clientDomainKeyPair = KeyPair.fromSecretSeed(clientDomainSeed)

val authToken = webAuth.jwtToken(
    clientAccountId = userAccountId,
    signers = listOf(userKeyPair),
    clientDomain = "wallet.mycompany.com",
    clientDomainKeyPair = clientDomainKeyPair
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
val userAccountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

// When wallet domain key is managed by your backend server
val signingDelegate = ClientDomainSigningDelegate { transactionXdr ->
    // Send transaction to your wallet's backend server for signing
    val httpClient = HttpClient()
    val response = httpClient.post("https://wallet.mycompany.com/sign-sep10") {
        contentType(ContentType.Application.Json)
        setBody("""{"transaction": "$transactionXdr"}""")
    }

    // Backend returns signed transaction XDR
    val signedXdr = response.bodyAsText()
    signedXdr
}

val authToken = webAuth.jwtToken(
    clientAccountId = userAccountId,
    signers = listOf(userKeyPair),
    clientDomain = "wallet.mycompany.com",
    clientDomainSigningDelegate = signingDelegate
)
```

## Multi-Signature Authentication

```kotlin
// multiSigAccountId: from the previous steps of this flow
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// Authenticate with multiple signers for multi-sig account
val signer1 = KeyPair.random()
val signer2 = KeyPair.random()
val signer3 = KeyPair.random()

val authToken = webAuth.jwtToken(
    clientAccountId = multiSigAccountId,
    signers = listOf(signer1, signer2, signer3)
)
```

## Low-Level API (Advanced)

Most developers should use `jwtToken()` for authentication. Use the low-level API only when you need custom validation logic, multi-step user approval flows, or integration with external signing systems.

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
// Manual control over each authentication step
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

// Step 1: Request challenge
val challenge = webAuth.getChallenge(clientAccountId = accountId)

// Step 2: Validate challenge (14 security checks)
webAuth.validateChallenge(
    challengeXdr = challenge.transaction,
    clientAccountId = accountId
)

// Step 3: Sign challenge
val signedChallenge = webAuth.signTransaction(
    challengeXdr = challenge.transaction,
    signers = listOf(userKeyPair)
)

// Step 4: Submit and get token
val authToken = webAuth.sendSignedChallenge(signedChallenge)
```

## Error Handling

```kotlin
// signers: from the previous steps of this flow
val clientAccountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
try {
    val authToken = webAuth.jwtToken(clientAccountId, signers)
} catch (e: ChallengeRequestException) {
    // Challenge request failed (network, server error)
    println("Challenge request failed: ${e.errorMessage}")
} catch (e: ChallengeValidationException) {
    // Challenge validation failed (security checks)
    println("Challenge validation failed: ${e.message}")
} catch (e: InvalidSignatureException) {
    // Server signature invalid (possible MITM attack)
    println("CRITICAL: Invalid server signature")
} catch (e: TokenSubmissionException) {
    // Token submission failed (signature verification)
    println("Token submission failed: ${e.message}")
}
```

## API Reference

**Main Methods**:
- `WebAuth.fromDomain(domain, network)` - Initialize from stellar.toml
- `webAuth.jwtToken(clientAccountId, signers, ...)` - Complete authentication flow
- `webAuth.getChallenge(clientAccountId, ...)` - Request challenge (low-level)
- `webAuth.validateChallenge(challengeXdr, ...)` - Validate challenge (low-level)
- `webAuth.signTransaction(challengeXdr, signers, ...)` - Sign challenge (low-level)
- `webAuth.sendSignedChallenge(signedChallengeXdr)` - Submit challenge (low-level)

**Data Classes**:
- `AuthToken` - JWT token with parsed claims (account, iss, sub, iat, exp, jti, clientDomain)
- `ChallengeResponse` - Challenge transaction from server (transaction, networkPassphrase)

**Interfaces**:
- `ClientDomainSigningDelegate` - External signing for wallet backend infrastructure

**Exception Types**:
- `ChallengeRequestException` - Challenge request failed (network, server error)
- `ChallengeValidationException` - Base class for validation errors
- `InvalidSignatureException` - Server signature invalid
- `InvalidSequenceNumberException` - Sequence number not zero
- `InvalidTimeBoundsException` - Time bounds invalid or expired
- `InvalidHomeDomainException` - Home domain mismatch
- `InvalidOperationTypeException` - Non-ManageData operation
- `InvalidSourceAccountException` - Source account mismatch
- `TokenSubmissionException` - Token submission failed

**Specification**: [SEP-10: Stellar Web Authentication](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep10`

**Last Updated**: 2025-11-25
