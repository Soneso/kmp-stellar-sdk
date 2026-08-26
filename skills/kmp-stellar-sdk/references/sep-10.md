# SEP-10: Web Authentication

**Purpose:** Prove ownership of a Stellar account to an anchor or service and receive a JWT token for authenticated API calls.
**Prerequisites:** Requires SEP-01 stellar.toml (provides `WEB_AUTH_ENDPOINT` and `SIGNING_KEY`)
**Package:** `com.soneso.stellar.sdk.sep.sep10`
**Spec:** SEP-0010

SEP-10 is the authentication foundation for SEP-06, SEP-12, SEP-24, SEP-30, and SEP-38. All anchor APIs that require authentication expect a Bearer token obtained through this flow.

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.sep.sep10.exceptions.*
```

## Table of Contents

- [Quick Start](#quick-start)
- [Creating WebAuth](#creating-webauth)
- [jwtToken() -- the Complete Flow](#jwttoken----the-complete-flow)
- [AuthToken -- Parsed JWT](#authtoken----parsed-jwt)
- [Standard Authentication](#standard-authentication)
- [Multi-Signature Authentication](#multi-signature-authentication)
- [Memo-Based Authentication](#memo-based-authentication)
- [Muxed Account Authentication](#muxed-account-authentication)
- [Client Domain Verification](#client-domain-verification)
- [Multiple Home Domains](#multiple-home-domains)
- [Low-Level API](#low-level-api)
- [Response Objects](#response-objects)
- [Error Handling](#error-handling)
- [Exception Hierarchy](#exception-hierarchy)
- [Testing with Mock HTTP Client](#testing-with-mock-http-client)
- [Common Pitfalls](#common-pitfalls)

---

## Quick Start

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
val userSecretSeed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"

// Load config from anchor's stellar.toml and run the full SEP-10 flow in one call
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

val userKeyPair = KeyPair.fromSecretSeed(userSecretSeed)
val authToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
)

// Use authToken as Bearer token for SEP-06, SEP-12, SEP-24, SEP-30, SEP-38, etc.
println("Authenticated! Token: ${authToken.token}")
println("Expires: ${authToken.exp}")
```

---

## Creating WebAuth

### From domain (recommended)

`WebAuth.fromDomain()` is a `suspend` function. It fetches the anchor's `stellar.toml`, reads `WEB_AUTH_ENDPOINT` and `SIGNING_KEY`, and returns a configured `WebAuth` instance.

```kotlin
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep10.exceptions.ChallengeRequestException

try {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
} catch (e: ChallengeRequestException) {
    // stellar.toml missing, or WEB_AUTH_ENDPOINT / SIGNING_KEY absent
    println("Failed to load WebAuth config: ${e.message}")
}
```

Signature:
```kotlin
suspend fun fromDomain(
    domain: String,
    network: Network,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null,
    clientDomainSigningDelegate: ClientDomainSigningDelegate? = null
): WebAuth
```

### Manual construction

Use when you already have the endpoint and signing key (e.g., loaded stellar.toml separately, or for tests with known values).

```kotlin
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth

val webAuth = WebAuth(
    authEndpoint = "https://testanchor.stellar.org/auth",
    network = Network.TESTNET,
    serverSigningKey = "GCUZ6YLL5RQBTYLTTQLPCM73C5XAIUGK2TIMWQH7HPSGWVS2KJ2F3CHS",
    serverHomeDomain = "testanchor.stellar.org"
)
```

Constructor signature:
```kotlin
class WebAuth(
    val authEndpoint: String,
    val network: Network,
    val serverSigningKey: String,
    val serverHomeDomain: String,
    val gracePeriodSeconds: Int = 300,
    private val httpClient: HttpClient? = null,
    private val httpRequestHeaders: Map<String, String>? = null,
    val clientDomainSigningDelegate: ClientDomainSigningDelegate? = null
)
```

Note: parameter order is `authEndpoint`, `network`, `serverSigningKey`, `serverHomeDomain`.

```kotlin
// WRONG: wrong parameter order -- serverSigningKey before network
WebAuth("https://example.com/auth", serverSigningKey, Network.TESTNET, "example.com")

// CORRECT: authEndpoint, network, serverSigningKey, serverHomeDomain
WebAuth(
    authEndpoint = "https://example.com/auth",
    network = Network.TESTNET,
    serverSigningKey = serverSigningKey,
    serverHomeDomain = "example.com"
)
```

---

## jwtToken() -- the Complete Flow

`jwtToken()` performs all SEP-10 steps internally:

1. Requests a challenge transaction from the auth endpoint (GET)
2. Fetches client domain's stellar.toml if `clientDomain` is provided (for SIGNING_KEY)
3. Validates the challenge (sequence number = 0, server signature, time bounds, operation types, source accounts, home domain, web\_auth\_domain)
4. Signs the transaction with the provided signers (and client domain key/delegate if provided)
5. Submits the signed transaction to the auth endpoint (POST)
6. Returns an `AuthToken` with the parsed JWT

Method signature:
```kotlin
suspend fun jwtToken(
    clientAccountId: String,               // G... or M... account address
    signers: List<KeyPair>,                // must include private keys
    memo: Long? = null,                    // ID memo for shared accounts (G... only)
    homeDomain: String? = null,            // override home domain when server serves multiple
    clientDomain: String? = null,          // wallet domain for client attribution
    clientDomainKeyPair: KeyPair? = null,  // wallet signing keypair (if local)
    clientDomainSigningDelegate: ClientDomainSigningDelegate? = null // callback for remote signing
): AuthToken
```

Returns `AuthToken` (not a raw String). Throws exceptions on any failure -- see [Error Handling](#error-handling).

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: jwtToken() returns AuthToken, not String
val token: String = webAuth.jwtToken(accountId, listOf(keyPair))

// CORRECT: returns AuthToken; use .token for the raw JWT string
val authToken: AuthToken = webAuth.jwtToken(accountId, listOf(keyPair))
val jwtString: String = authToken.token
```

---

## AuthToken -- Parsed JWT

`jwtToken()` returns an `AuthToken` that parses the JWT payload and exposes its claims. The SDK does NOT verify JWT signatures -- that is the server's responsibility.

```kotlin
// header, httpClient: from the previous steps of this flow
import com.soneso.stellar.sdk.sep.sep10.AuthToken
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

val authToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
)

// Parsed JWT claims
println("Raw token: ${authToken.token}")       // the full JWT string
println("Issuer: ${authToken.iss}")            // auth server domain
println("Subject: ${authToken.sub}")           // account ID (may include ":memo")
println("Issued at: ${authToken.iat}")         // Unix epoch seconds
println("Expires: ${authToken.exp}")           // Unix epoch seconds
println("JWT ID: ${authToken.jti}")            // unique token identifier
println("Client domain: ${authToken.clientDomain}") // if client domain verification was used

// Computed properties
println("Account: ${authToken.account}")       // G... or M... extracted from sub
println("Memo: ${authToken.memo}")             // memo string extracted from sub, or null

// Check expiration
if (authToken.isExpired()) {
    println("Token expired, re-authenticate")
}

// Use as Bearer token -- toString() returns the raw token string
httpClient.get("https://anchor.example.com/sep24/info") {
    header("Authorization", "Bearer $authToken")  // uses toString()
}
```

| Property | Type | Description |
|----------|------|-------------|
| `token` | `String` | Raw JWT string (always present) |
| `iss` | `String?` | Issuer -- auth server domain |
| `sub` | `String?` | Subject -- `"G..."`, `"M..."`, or `"G...:memo"` |
| `iat` | `Long?` | Issued-at timestamp (Unix epoch seconds) |
| `exp` | `Long?` | Expiration timestamp (Unix epoch seconds) |
| `jti` | `String?` | Unique token identifier |
| `clientDomain` | `String?` | Client domain (when domain verification was used) |
| `account` | `String?` | Account ID extracted from `sub` (computed) |
| `memo` | `String?` | Memo extracted from `sub` if format is `"G...:memo"` (computed) |

`AuthToken.parse(jwtString)` performs lenient parsing: if the JWT is malformed, it returns an `AuthToken` with only the `token` field populated and all claims as `null`.

---

## Standard Authentication

For a single-signature account that owns its own keys. The account does not need to exist on-chain -- SEP-10 only proves key ownership.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
val userSecretSeed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"

suspend fun authenticate() {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

    val userKeyPair = KeyPair.fromSecretSeed(userSecretSeed)

    val authToken = webAuth.jwtToken(
        clientAccountId = userKeyPair.getAccountId(),
        signers = listOf(userKeyPair)
    )

    println("JWT: ${authToken.token}")
}
```

---

## Multi-Signature Authentication

For accounts that require multiple signers to meet the server's threshold. Provide all required keypairs -- their combined weight must satisfy the server's requirements.

```kotlin
// getAccountId, secretSeed1, secretSeed2: from the previous steps of this flow
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth

suspend fun authenticateMultiSig() {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

    val signer1 = KeyPair.fromSecretSeed(secretSeed1)
    val signer2 = KeyPair.fromSecretSeed(secretSeed2)

    // Both signers sign the challenge. Combined weight must meet threshold.
    val authToken = webAuth.jwtToken(
        clientAccountId = signer1.getAccountId(), // the account being authenticated
        signers = listOf(signer1, signer2)
    )

    println("JWT: ${authToken.token}")
}
```

---

## Memo-Based Authentication

For services that distinguish users sharing a single Stellar account via an integer memo. The `memo` parameter is `Long?`.

```kotlin
// getAccountId, sharedSecretSeed: from the previous steps of this flow
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth

suspend fun authenticateWithMemo() {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

    val sharedAccountKeyPair = KeyPair.fromSecretSeed(sharedSecretSeed)
    val userId = 1234567890L // Long, not Int

    val authToken = webAuth.jwtToken(
        clientAccountId = sharedAccountKeyPair.getAccountId(), // G... address
        signers = listOf(sharedAccountKeyPair),
        memo = userId
    )

    println("JWT for user $userId: ${authToken.token}")
    println("Token sub: ${authToken.sub}") // "G...:1234567890"
}
```

**Important:** `memo` only works with G... (non-muxed) account IDs. Providing a memo together with an M... address throws `NoMemoForMuxedAccountsException`.

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: memo type is Int -- use Long
webAuth.jwtToken(accountId, listOf(keyPair), memo = 12345) // compiles but 12345 is Int literal

// CORRECT: use Long literal or explicit Long type
webAuth.jwtToken(accountId, listOf(keyPair), memo = 12345L)
```

---

## Muxed Account Authentication

Muxed accounts (M... addresses) embed a user ID into the account address as an alternative to memos. Pass the M... address as `clientAccountId` and the underlying G... keypair in `signers`.

```kotlin
// baseSecretSeed: from the previous steps of this flow
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth

suspend fun authenticateMuxed() {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

    // Muxed account address (M...) -- encodes both the G... account and a memo ID
    val muxedAccountId = "MB4L7JUU5DENUXYH3ANTLVYQL66KQLDDJTN5SF7MWEDGWSGUA375UAAAAAAACMICQP7P4"

    // Signing keypair is the underlying G... account's keypair
    val baseKeyPair = KeyPair.fromSecretSeed(baseSecretSeed)

    val authToken = webAuth.jwtToken(
        clientAccountId = muxedAccountId, // M... address
        signers = listOf(baseKeyPair)     // sign with the underlying G... keypair
    )

    println("JWT: ${authToken.token}")
}
```

```kotlin
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: memo with M... address -- throws NoMemoForMuxedAccountsException
webAuth.jwtToken("MAAAAAAAAAAAAAB7BQ2L7E5NBWMXDUCMZSIPOBKRDSBYVLMXGSSKF6YNPIB7Y77ITLVL6", listOf(keyPair), memo = 12345L)

// CORRECT: use one method of user identification, never both
webAuth.jwtToken("MAAAAAAAAAAAAAB7BQ2L7E5NBWMXDUCMZSIPOBKRDSBYVLMXGSSKF6YNPIB7Y77ITLVL6", listOf(keyPair))                   // muxed account only
webAuth.jwtToken("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", listOf(keyPair), memo = 12345L)     // G... + memo only
```

---

## Client Domain Verification

Non-custodial wallets can prove their identity to anchors by providing a client domain signature. The anchor can then tailor the experience for users of known, trusted wallets.

### Local signing (wallet has the key)

Provide `clientDomain` and `clientDomainKeyPair`. The wallet's `stellar.toml` must publish a `SIGNING_KEY` that matches the keypair.

```kotlin
// walletSigningSecretSeed: from the previous steps of this flow
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
val userSecretSeed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"

suspend fun authenticateWithClientDomain() {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)

    val userKeyPair = KeyPair.fromSecretSeed(userSecretSeed)
    val clientDomainKeyPair = KeyPair.fromSecretSeed(walletSigningSecretSeed)

    val authToken = webAuth.jwtToken(
        clientAccountId = userKeyPair.getAccountId(),
        signers = listOf(userKeyPair),
        clientDomain = "mywallet.com",
        clientDomainKeyPair = clientDomainKeyPair
    )

    println("JWT: ${authToken.token}")
}
```

### Remote signing delegate (key on a separate server)

When the wallet's signing key is stored on a dedicated signing server, provide a `ClientDomainSigningDelegate`. The SDK fetches the wallet's `stellar.toml` to get its `SIGNING_KEY` for validation, then calls the delegate with the base64-encoded transaction XDR. The delegate must return the signed transaction as base64-encoded XDR.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.ClientDomainSigningDelegate
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
val userSecretSeed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"

suspend fun authenticateWithRemoteSigning() {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
    val userKeyPair = KeyPair.fromSecretSeed(userSecretSeed)

    // Delegate: receives base64 XDR, must return signed base64 XDR
    val signingDelegate = ClientDomainSigningDelegate { transactionXdr ->
        val client = HttpClient()
        try {
            val response = client.post("https://signing-server.mywallet.com/sign-sep-10") {
                contentType(ContentType.Application.Json)
                setBody("""{"transaction": "$transactionXdr"}""")
            }
            response.body<String>() // must return signed transaction XDR
        } finally {
            client.close()
        }
    }

    val authToken = webAuth.jwtToken(
        clientAccountId = userKeyPair.getAccountId(),
        signers = listOf(userKeyPair),
        clientDomain = "mywallet.com",
        clientDomainSigningDelegate = signingDelegate
    )

    println("JWT: ${authToken.token}")
}
```

Delegate interface:
```kotlin
fun interface ClientDomainSigningDelegate {
    suspend fun signTransaction(transactionXdr: String): String
}
```

```kotlin
// signingDelegate: from the previous steps of this flow
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: both clientDomainKeyPair and clientDomainSigningDelegate -- throws IllegalArgumentException
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com",
    clientDomainKeyPair = clientDomainKeyPair,
    clientDomainSigningDelegate = signingDelegate // cannot use both!
)

// CORRECT: use one signing method, not both
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com",
    clientDomainKeyPair = clientDomainKeyPair     // local signing
)
// OR
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com",
    clientDomainSigningDelegate = signingDelegate  // remote signing
)
```

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: clientDomain without any signing method -- throws IllegalArgumentException
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com"
    // missing: clientDomainKeyPair or clientDomainSigningDelegate
)

// CORRECT: always provide a signing method with clientDomain
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com",
    clientDomainKeyPair = clientDomainKeyPair
)
```

---

## Multiple Home Domains

When an anchor's auth server handles multiple home domains, use `homeDomain` to specify which domain the challenge should be issued for.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
val userSecretSeed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"

suspend fun authenticateMultiDomain() {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
    val userKeyPair = KeyPair.fromSecretSeed(userSecretSeed)

    val authToken = webAuth.jwtToken(
        clientAccountId = userKeyPair.getAccountId(),
        signers = listOf(userKeyPair),
        homeDomain = "other-domain.com"
    )

    println("JWT: ${authToken.token}")
}
```

---

## Low-Level API

For custom flows or debugging, you can call each step of the SEP-10 flow individually.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
val userSecretSeed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"

suspend fun authenticateLowLevel() {
    val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
    val userKeyPair = KeyPair.fromSecretSeed(userSecretSeed)
    val accountId = userKeyPair.getAccountId()

    // Step 1: Request challenge
    val challenge = webAuth.getChallenge(clientAccountId = accountId)
    println("Challenge XDR: ${challenge.transaction}")

    // Step 2: Validate challenge (critical security step -- 13 checks)
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
    println("JWT: ${authToken.token}")
}
```

### getChallenge()

```kotlin
suspend fun getChallenge(
    clientAccountId: String,
    memo: Long? = null,
    homeDomain: String? = null,
    clientDomain: String? = null
): ChallengeResponse
```

### validateChallenge()

Performs 13 security validation checks. Throws `ChallengeValidationException` subclasses on failure.

```kotlin
suspend fun validateChallenge(
    challengeXdr: String,
    clientAccountId: String,
    clientDomainAccountId: String? = null,
    expectedMemo: Long? = null
)
```

### signTransaction()

```kotlin
suspend fun signTransaction(
    challengeXdr: String,
    signers: List<KeyPair>,
    clientDomainKeyPair: KeyPair? = null,
    clientDomainSigningDelegate: ClientDomainSigningDelegate? = null
): String  // returns base64 XDR
```

### sendSignedChallenge()

```kotlin
suspend fun sendSignedChallenge(
    signedChallengeXdr: String
): AuthToken
```

---

## Response Objects

### ChallengeResponse

Returned from `getChallenge()`. You only encounter this directly when using the low-level API -- `jwtToken()` handles it internally.

| Field | Type | Description |
|-------|------|-------------|
| `transaction` | `String` | Base64-encoded XDR transaction envelope to sign |
| `networkPassphrase` | `String?` | Optional: server's network passphrase for verification |

JSON mapping: `transaction` maps to `"transaction"` field, `networkPassphrase` maps to `"network_passphrase"` field.

### TokenSubmissionResponse (internal)

The `TokenSubmissionResponse` is an `internal` class -- you do not access it directly. `sendSignedChallenge()` parses it and returns an `AuthToken`.

JSON mapping: the server returns `"token"` (not `"jwt_token"`).

```kotlin
// signedXdr: from the previous steps of this flow
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: trying to access TokenSubmissionResponse directly -- it is internal
// CORRECT: use sendSignedChallenge() which returns AuthToken
val authToken = webAuth.sendSignedChallenge(signedXdr)
val jwt = authToken.token
```

---

## Error Handling

All SEP-10 exceptions extend the sealed class `WebAuthException`, which extends `Exception`. This allows catching all SEP-10 errors with a single catch block, or matching specific error types.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep10.exceptions.*
val userSecretSeed = "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4"

suspend fun authenticateWithErrorHandling() {
    try {
        val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
        val userKeyPair = KeyPair.fromSecretSeed(userSecretSeed)

        val authToken = webAuth.jwtToken(
            clientAccountId = userKeyPair.getAccountId(),
            signers = listOf(userKeyPair)
        )
        println("JWT: ${authToken.token}")

    } catch (e: ChallengeRequestException) {
        // stellar.toml missing, WEB_AUTH_ENDPOINT absent, SIGNING_KEY absent,
        // or server rejected the challenge GET request
        println("Challenge request failed (HTTP ${e.statusCode}): ${e.errorMessage}")

    } catch (e: InvalidSequenceNumberException) {
        // SECURITY: challenge has a non-zero sequence number -- could be executable
        println("SECURITY: invalid sequence number -- do not sign: ${e.message}")

    } catch (e: InvalidSignatureException) {
        // Challenge not signed by the expected server key
        println("Invalid server signature -- check stellar.toml SIGNING_KEY: ${e.message}")

    } catch (e: InvalidTimeBoundsException) {
        // Challenge expired or not yet valid -- request a fresh challenge
        println("Challenge expired -- retry to get a fresh one: ${e.message}")

    } catch (e: InvalidHomeDomainException) {
        // First operation's data name does not match "<serverHomeDomain> auth"
        println("Home domain mismatch in challenge: ${e.message}")

    } catch (e: InvalidWebAuthDomainException) {
        // web_auth_domain op value does not match the auth endpoint host
        println("web_auth_domain mismatch: ${e.message}")

    } catch (e: InvalidSourceAccountException) {
        // Wrong source account on an operation
        println("Invalid source account in challenge operation: ${e.message}")

    } catch (e: InvalidOperationTypeException) {
        // SECURITY: challenge contains a non-ManageData operation
        println("SECURITY: non-ManageData op in challenge -- server may be malicious: ${e.message}")

    } catch (e: InvalidMemoTypeException) {
        // Memo in challenge is not MEMO_ID
        println("Invalid memo type in challenge: ${e.message}")

    } catch (e: InvalidMemoValueException) {
        // Memo value missing or doesn't match the requested memo
        println("Memo value mismatch in challenge: ${e.message}")

    } catch (e: MemoWithMuxedAccountException) {
        // Challenge has both a memo and an M... source account
        println("Challenge has both memo and muxed account: ${e.message}")

    } catch (e: InvalidSignatureCountException) {
        // Challenge does not have exactly 1 signature (server's)
        println("Invalid signature count: ${e.message}")

    } catch (e: InvalidClientDomainSourceException) {
        // client_domain operation source does not match expected signing key
        println("Client domain source mismatch: ${e.message}")

    } catch (e: NoMemoForMuxedAccountsException) {
        // memo provided with an M... account address
        println("Cannot use memo with a muxed (M...) account: ${e.message}")

    } catch (e: ChallengeValidationException) {
        // Catch-all for other challenge validation issues (malformed XDR, etc.)
        println("Challenge validation failed: ${e.message}")

    } catch (e: TokenSubmissionException) {
        // Server rejected signed challenge (bad signature, insufficient signers, etc.)
        println("Token submission failed: ${e.message}")

    } catch (e: WebAuthException) {
        // Catch-all for any SEP-10 error
        println("SEP-10 authentication failed: ${e.message}")

    } catch (e: IllegalArgumentException) {
        // Invalid inputs: empty signers, both clientDomainKeyPair and delegate, etc.
        println("Invalid arguments: ${e.message}")
    }
}
```

---

## Exception Hierarchy

```text
WebAuthException (sealed)
├── ChallengeRequestException
│     Properties: statusCode (Int), errorMessage (String?)
│     Thrown by: fromDomain(), getChallenge()
│
├── ChallengeValidationException (sealed)
│   ├── InvalidSequenceNumberException        -- seq != 0 (SECURITY)
│   ├── InvalidSignatureException             -- bad server signature (SECURITY)
│   ├── InvalidSignatureCountException        -- != 1 server signature
│   ├── InvalidTimeBoundsException            -- expired or future-dated
│   ├── InvalidHomeDomainException            -- first op key != "<domain> auth"
│   ├── InvalidWebAuthDomainException         -- web_auth_domain mismatch
│   ├── InvalidSourceAccountException         -- wrong source on operation
│   ├── InvalidOperationTypeException         -- non-ManageData op (SECURITY)
│   ├── InvalidMemoTypeException              -- memo is not MEMO_ID
│   ├── InvalidMemoValueException             -- memo value mismatch
│   ├── MemoWithMuxedAccountException         -- both memo and M... address
│   ├── InvalidClientDomainSourceException    -- client_domain source mismatch
│   └── GenericChallengeValidationException   -- other validation failures
│
├── NoMemoForMuxedAccountsException
│     Thrown by: getChallenge(), jwtToken()
│
└── TokenSubmissionException
      Thrown by: sendSignedChallenge()
```

### Exception reference table

| Exception class | When thrown | Action |
|-----------------|------------|--------|
| `ChallengeRequestException` | `fromDomain()`: stellar.toml missing/invalid, WEB\_AUTH\_ENDPOINT or SIGNING\_KEY absent; `getChallenge()`: server HTTP error | Check domain supports SEP-10; check `statusCode` for HTTP details |
| `NoMemoForMuxedAccountsException` | memo provided with M... account | Use memo OR muxed, not both |
| `InvalidSequenceNumberException` | Sequence number != 0 | **Security risk** -- abort |
| `InvalidSignatureException` | Wrong server signature | **Security risk** -- verify stellar.toml SIGNING\_KEY |
| `InvalidSignatureCountException` | Challenge has != 1 signature | Possible tampering -- abort |
| `InvalidTimeBoundsException` | Challenge expired or future-dated | Retry -- get fresh challenge |
| `InvalidHomeDomainException` | First op key != `"<serverHomeDomain> auth"` | Check serverHomeDomain config |
| `InvalidWebAuthDomainException` | web\_auth\_domain op value != auth endpoint host | Server config mismatch |
| `InvalidSourceAccountException` | Wrong source on any operation | Server config issue |
| `InvalidOperationTypeException` | Non-ManageData op in challenge | **Security risk** -- server may be malicious |
| `InvalidMemoTypeException` | Memo is not MEMO\_ID | Server config issue |
| `InvalidMemoValueException` | Memo missing or value mismatch | Server config issue |
| `MemoWithMuxedAccountException` | Challenge has both memo and M... address | Server config issue |
| `InvalidClientDomainSourceException` | client\_domain op source mismatch | Check client domain stellar.toml |
| `GenericChallengeValidationException` | Other validation failure (malformed XDR, zero operations) | Unexpected server behavior |
| `TokenSubmissionException` | Server rejected signed challenge (HTTP 400/401/etc.) | Provide all required signers; check signature validity |

### Exception properties

```kotlin
// ChallengeRequestException
// e.statusCode -- Int: HTTP status code (0 for network/config errors)
// e.errorMessage -- String?: detailed error message
// e.message -- String: overall description (inherited from Exception)
// e.cause -- Throwable?: underlying cause

// ChallengeValidationException and all subclasses
// e.message -- String: description of the validation failure

// TokenSubmissionException
// e.message -- String: description with HTTP status info
// e.cause -- Throwable?: underlying cause

// NoMemoForMuxedAccountsException
// e.message -- String: description of the error
```

---

## Testing with Mock HTTP Client

Pass a custom `HttpClient` with a mock engine to the `WebAuth` constructor. No network calls are made.

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.AuthToken
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
val account = Account("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", 1L)

class Sep10Test {
    // Server configuration -- must match what WebAuth is initialized with
    private val serverAccountId = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
    private val serverSecretSeed = "SAWDHXQG6ROJSU4QGCW7NSTYFHPTPIVC2NC7QKVTO7PZCSO2WEBGM54W"

    private val domain = "place.domain.com"
    private val authServer = "http://api.stellar.org/auth"

    // Client keypair
    private val clientSecretSeed = "SBAYNYLQFXVLVAHW4BXDQYNJLMDQMZ5NQDDOHVJD3PTBAUIJRNRK5LGX"

    private val successJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJzdWIiOiJHQTZVSVhYUEVXN1dKSjdSNlFBVTNFRjVDTkxGTTc2Tk5PRU9XTUFDVFRSSERQQlRBM0FPNkdLMiIsImlhdCI6MTYwMDAwMDAwMCwiZXhwIjo5OTk5OTk5OTk5fQ." +
        "signature"

    private fun generateNonce(length: Int = 48): ByteArray {
        return Random.nextBytes(length)
    }

    // Build a valid challenge transaction (mimics what the server would produce)
    private suspend fun buildChallengeJson(accountId: String, memo: Long? = null): String {
        val serverKeyPair = KeyPair.fromSecretSeed(serverSecretSeed)

        // Account with sequence -1: build() increments it to 0 (required by SEP-10)
        val transactionAccount = Account(serverAccountId, -1L)

        // First op: "<domain> auth", source = client account
        val firstOp = ManageDataOperation(
            name = "$domain auth",
            value = generateNonce()
        ).apply {
            sourceAccount = accountId
        }

        // Second op: "web_auth_domain", value = host of authServer, source = server
        val secondOp = ManageDataOperation(
            name = "web_auth_domain",
            value = "api.stellar.org".encodeToByteArray()
        ).apply {
            sourceAccount = serverAccountId
        }

        @OptIn(kotlin.time.ExperimentalTime::class)
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val preconditions = TransactionPreconditions(
            timeBounds = TimeBounds(now - 1, now + 300)
        )

        val transaction = TransactionBuilder(transactionAccount, Network.TESTNET)
            .addOperation(firstOp)
            .addOperation(secondOp)
            .addMemo(if (memo != null) MemoId(memo.toULong()) else MemoNone)
            .addPreconditions(preconditions)
            .build()

        transaction.sign(serverKeyPair)

        return """{"transaction": "${transaction.toEnvelopeXdrBase64()}"}"""
    }

    @Test
    fun testSep10StandardAuth() = runTest {
        val clientKeyPair = KeyPair.fromSecretSeed(clientSecretSeed)
        val clientAccountId = clientKeyPair.getAccountId()

        val mockEngine = MockEngine { request ->
            when {
                // Challenge GET
                request.method == HttpMethod.Get &&
                    request.url.parameters["account"] == clientAccountId -> {
                    respond(
                        content = buildChallengeJson(clientAccountId),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                // Token POST -- verify signature count and return JWT
                request.method == HttpMethod.Post -> {
                    val body = request.body.toByteArray().decodeToString()
                    // Simple check: just return success JWT
                    respond(
                        content = """{"token": "$successJwt"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    respond(
                        content = """{"error": "Bad request"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val webAuth = WebAuth(
            authEndpoint = authServer,
            network = Network.TESTNET,
            serverSigningKey = serverAccountId,
            serverHomeDomain = domain,
            httpClient = mockClient
        )

        val authToken = webAuth.jwtToken(clientAccountId, listOf(clientKeyPair))
        assertEquals(successJwt, authToken.token)
        assertFalse(authToken.isExpired())
    }
}
```

**Key details for building a valid mock challenge:**
- The `Account` sequence starts at `-1L` -- `build()` increments it to 0 (required by SEP-10)
- First ManageData op `name` must be `"<serverHomeDomain> auth"`, `sourceAccount` must be the client account ID
- The `web_auth_domain` op `sourceAccount` must be the server signing key account ID; its `value` must be the **host** of the auth URL (e.g., `"api.stellar.org"`, not the full URL)
- The transaction must be signed by the server's keypair with the correct `Network`
- Time bounds must include the current time

```kotlin
// WRONG: Account sequence 0 -- build() increments to 1, fails SEP-10 validation
val account = Account(serverAccountId, 0L)

// CORRECT: Account sequence -1 -- build() increments to 0
val account = Account(serverAccountId, -1L)
```

---

## Common Pitfalls

**Wrong: `memo` with M... muxed account**

```kotlin
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: throws NoMemoForMuxedAccountsException
webAuth.jwtToken("MAAAAAAAAAAAAAB7BQ2L7E5NBWMXDUCMZSIPOBKRDSBYVLMXGSSKF6YNPIB7Y77ITLVL6", listOf(keyPair), memo = 12345L)

// CORRECT: choose one method of user identification
webAuth.jwtToken("MAAAAAAAAAAAAAB7BQ2L7E5NBWMXDUCMZSIPOBKRDSBYVLMXGSSKF6YNPIB7Y77ITLVL6", listOf(keyPair))                  // muxed account encodes the memo
webAuth.jwtToken("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", listOf(keyPair), memo = 12345L)    // G... account + separate memo
```

**Wrong: network passphrase mismatch**

The `Network` passed to `WebAuth` must match the network the server signed the challenge with. If they differ, `InvalidSignatureException` is thrown even though the challenge was technically valid on its own network.

```kotlin
val domain = "testanchor.stellar.org"
// WRONG: WebAuth on public network but anchor signed for testnet
// -> InvalidSignatureException (signatures won't verify)
val webAuth = WebAuth(endpoint, Network.PUBLIC, signingKey, domain)

// CORRECT: match the network to the anchor's actual network
val webAuth = WebAuth(endpoint, Network.TESTNET, signingKey, domain)
```

**Wrong: `signers` list must contain KeyPairs with secret keys**

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: KeyPair.fromAccountId() has no private key and cannot sign
val publicOnly = KeyPair.fromAccountId(accountId)
webAuth.jwtToken(accountId, listOf(publicOnly))
// -> server rejects (TokenSubmissionException)

// CORRECT: KeyPair.fromSecretSeed() includes the private key
val fullKeyPair = KeyPair.fromSecretSeed(secretSeed) // suspend function
webAuth.jwtToken(accountId, listOf(fullKeyPair))
```

**Wrong: empty signers list**

```kotlin
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
// WRONG: throws IllegalArgumentException("Signers list cannot be empty")
webAuth.jwtToken(accountId, emptyList())

// CORRECT: provide at least one signer
webAuth.jwtToken(accountId, listOf(userKeyPair))
```

**Wrong: both `clientDomainKeyPair` and `clientDomainSigningDelegate`**

```kotlin
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: throws IllegalArgumentException -- cannot use both signing methods
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com",
    clientDomainKeyPair = localKey,
    clientDomainSigningDelegate = remoteDelegate
)

// CORRECT: choose one signing method
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com",
    clientDomainKeyPair = localKey                    // local signing
)
```

**Wrong: `clientDomain` without any signing method**

```kotlin
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: throws IllegalArgumentException
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com"
    // missing: clientDomainKeyPair or clientDomainSigningDelegate
)

// CORRECT: always provide a signing method with clientDomain
webAuth.jwtToken(accountId, listOf(keyPair),
    clientDomain = "mywallet.com",
    clientDomainKeyPair = clientDomainKeyPair
)
```

**Wrong: treating security exceptions as recoverable**

`InvalidSequenceNumberException` and `InvalidOperationTypeException` indicate potential malicious server behavior. Never retry or ignore them.

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
try {
    val authToken = webAuth.jwtToken(accountId, listOf(keyPair))
} catch (e: InvalidSequenceNumberException) {
    // Non-zero sequence number: signing could execute a real transaction
    // CORRECT: treat as fatal, do not retry
    throw RuntimeException("SECURITY: auth server returned challenge with non-zero seq nr", e)
} catch (e: InvalidOperationTypeException) {
    // Non-ManageData op: could be a payment or account modification
    // CORRECT: treat as fatal, do not retry
    throw RuntimeException("SECURITY: auth server returned challenge with non-ManageData op", e)
}
```

**Wrong: expecting jwtToken() to return String**

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
// WRONG: jwtToken() returns AuthToken, not String
val jwt: String = webAuth.jwtToken(accountId, listOf(keyPair)).toString()
// This works but is misleading -- toString() returns the raw token

// CORRECT: use the AuthToken directly, access .token for the raw string
val authToken = webAuth.jwtToken(accountId, listOf(keyPair))
val jwt: String = authToken.token

// For Bearer headers, AuthToken.toString() works directly:
header("Authorization", "Bearer $authToken")
```

**Wrong: forgetting that all WebAuth methods are `suspend`**

```kotlin
// WRONG: calling suspend function outside a coroutine
fun main() {
    val webAuth = WebAuth.fromDomain("anchor.example.com", Network.TESTNET) // compile error
}

// CORRECT: call from a suspend function or coroutine scope
suspend fun main() {
    val webAuth = WebAuth.fromDomain("anchor.example.com", Network.TESTNET)
    // ...
}
// OR
fun main() = runBlocking {
    val webAuth = WebAuth.fromDomain("anchor.example.com", Network.TESTNET)
    // ...
}
```

---

## JWT Token Structure

The JWT returned by `jwtToken()` is a standard JSON Web Token, parsed into an `AuthToken`. Use `authToken.token` for the raw string, or inspect parsed claims directly on the `AuthToken` object.

Standard claims in the token:

| Claim | AuthToken property | Description |
|-------|--------------------|-------------|
| `sub` | `sub` / `account` / `memo` | Authenticated account -- G..., M..., or `G...:memo` for memo auth |
| `iss` | `iss` | Token issuer (the authentication server URL) |
| `iat` | `iat` | Issued-at timestamp (Unix epoch seconds) |
| `exp` | `exp` | Expiration timestamp (Unix epoch seconds) |
| `jti` | `jti` | Unique token identifier |
| `client_domain` | `clientDomain` | Present when client domain verification was performed |

Use the token as a `Bearer` header for SEP-06 (deposit/withdrawal), SEP-12 (KYC), SEP-24 (interactive deposit/withdrawal), SEP-30 (account recovery), SEP-38 (anchor RFQ), and any other authenticated anchor API.

```kotlin
// Using JWT with Ktor HttpClient for a SEP-24 endpoint
val response = httpClient.get("https://anchor.example.com/sep24/info") {
    header("Authorization", "Bearer ${authToken.token}")
}
```

---

## Related SEPs

- [sep-01.md](sep-01.md) -- stellar.toml discovery (provides `WEB_AUTH_ENDPOINT` and `SIGNING_KEY`)
- SEP-06 -- Deposit/Withdrawal API (requires SEP-10 JWT)
- SEP-12 -- KYC API (requires SEP-10 JWT)
- SEP-24 -- Interactive Deposit/Withdrawal (requires SEP-10 JWT)
- SEP-30 -- Account Recovery (requires SEP-10 JWT)
- SEP-38 -- Anchor RFQ API (requires SEP-10 JWT)
