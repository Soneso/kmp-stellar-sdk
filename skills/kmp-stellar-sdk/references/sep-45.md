# SEP-45: Web Authentication for Contract Accounts

**Purpose:** Authenticate Soroban smart contract accounts (C... addresses) with anchor services and receive a JWT token for subsequent SEP calls.
**Prerequisites:** Requires SEP-01 stellar.toml (provides `WEB_AUTH_FOR_CONTRACTS_ENDPOINT`, `WEB_AUTH_CONTRACT_ID`, `SIGNING_KEY`)
**SEP-45 vs SEP-10:** SEP-45 is for contract accounts (C...). SEP-10 is for traditional accounts (G... and M...).

## Table of Contents

- [Quick Start](#quick-start)
- [Creating WebAuthForContracts](#creating-webauthforcontracts)
- [jwtToken() -- the Complete Flow](#jwttoken----the-complete-flow)
- [Contracts Without Signature Requirements](#contracts-without-signature-requirements)
- [Client Domain Verification](#client-domain-verification)
- [Step-by-Step Authentication](#step-by-step-authentication)
- [Request Format](#request-format)
- [Response Objects](#response-objects)
- [Error Handling](#error-handling)
- [Common Pitfalls](#common-pitfalls)
- [SEP-45 vs SEP-10 Comparison](#sep-45-vs-sep-10-comparison)

---

## Quick Start

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts

// Your contract account (C... address) -- must implement __check_auth
val contractId = "CCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"

// Signer registered in your contract's __check_auth -- must have private key
val signer = KeyPair.fromSecretSeed(contractSignerSeed)

// Load config from anchor's stellar.toml and authenticate in one call
val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
val authToken = webAuth.jwtToken(contractId, listOf(signer))

println("Authenticated! Token: ${authToken.token.substring(0, 50)}...")
```

---

## Creating WebAuthForContracts

### From domain (recommended)

`WebAuthForContracts.fromDomain()` is a `suspend` factory function. It fetches the anchor's
`stellar.toml`, reads `WEB_AUTH_FOR_CONTRACTS_ENDPOINT`, `WEB_AUTH_CONTRACT_ID`, and
`SIGNING_KEY`, and returns a configured instance.

```kotlin
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45NoEndpointException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45NoContractIdException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45NoSigningKeyException

try {
    val webAuth = WebAuthForContracts.fromDomain(
        "anchor.example.com",
        Network.TESTNET,
    )
} catch (e: Sep45NoEndpointException) {
    println("No WEB_AUTH_FOR_CONTRACTS_ENDPOINT for ${e.domain}")
} catch (e: Sep45NoContractIdException) {
    println("No WEB_AUTH_CONTRACT_ID for ${e.domain}")
} catch (e: Sep45NoSigningKeyException) {
    println("No SIGNING_KEY for ${e.domain}")
} catch (e: Exception) {
    println("Failed to load WebAuth config: $e")
}
```

Signature:
```
suspend fun fromDomain(
    domain: String,
    network: Network,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null,
): WebAuthForContracts
```

### Manual construction

Use when you have the values directly (e.g., cached or for tests).

```kotlin
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts

val webAuth = WebAuthForContracts(
    authEndpoint = "https://auth.anchor.example.com/sep45",
    webAuthContractId = "CCALHRGH5RXIDJDRLPPG4ZX2S563TB2QKKJR4STWKVQCYB6JVPYQXHRG", // C...
    serverSigningKey = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",   // G...
    serverHomeDomain = "anchor.example.com",
    network = Network.TESTNET,
)
```

Constructor signature:
```
class WebAuthForContracts(
    val authEndpoint: String,         // WEB_AUTH_FOR_CONTRACTS_ENDPOINT -- must be a valid URL
    val webAuthContractId: String,    // WEB_AUTH_CONTRACT_ID -- must start with 'C'
    val serverSigningKey: String,     // SIGNING_KEY -- must start with 'G'
    val serverHomeDomain: String,     // domain name -- must not be blank
    val network: Network,
    private val httpClient: HttpClient? = null,
    private val httpRequestHeaders: Map<String, String>? = null,
    val sorobanRpcUrl: String? = null // defaults to soroban-testnet.stellar.org / soroban.stellar.org
)
```

The `init` block throws `IllegalArgumentException` if any parameter is invalid (wrong prefix, bad URL, blank domain).

```kotlin
// WRONG: webAuthContractId and serverSigningKey are swapped
WebAuthForContracts(
    authEndpoint = endpoint,
    webAuthContractId = serverSigningKey,  // G... in C... slot
    serverSigningKey = webAuthContractId,  // C... in G... slot
    serverHomeDomain = domain,
    network = Network.TESTNET,
)
// -> IllegalArgumentException: webAuthContractId must be a contract address starting with 'C'

// CORRECT: webAuthContractId (C...) and serverSigningKey (G...) in the right parameters
WebAuthForContracts(
    authEndpoint = endpoint,
    webAuthContractId = webAuthContractId,  // C...
    serverSigningKey = serverSigningKey,    // G...
    serverHomeDomain = domain,
    network = Network.TESTNET,
)
```

### Custom Soroban RPC URL

By default the SDK uses `https://soroban-testnet.stellar.org` (testnet) or
`https://soroban.stellar.org` (pubnet). Pass `sorobanRpcUrl` to override.

```kotlin
val webAuth = WebAuthForContracts(
    authEndpoint = "https://auth.anchor.example.com/sep45",
    webAuthContractId = webAuthContractId,
    serverSigningKey = serverSigningKey,
    serverHomeDomain = "anchor.example.com",
    network = Network.TESTNET,
    sorobanRpcUrl = "https://my-rpc.example.com",
)
```

---

## jwtToken() -- the Complete Flow

`jwtToken()` executes the entire SEP-45 flow in one call:

1. GET challenge from server (`authorization_entries` + optional `network_passphrase`)
2. Validate `network_passphrase` if present
3. Decode and validate all authorization entries (contract address, function name, args, server signature, nonce consistency)
4. Auto-fetch current ledger via Soroban RPC to set `signatureExpirationLedger` (if signers provided and no explicit expiration)
5. Sign the client authorization entry with the provided keypairs
6. POST signed entries to server and return a `Sep45AuthToken`

Challenge entries are validated and signed across all three Soroban credential
arms (legacy `ADDRESS`, `ADDRESS_V2`, and `ADDRESS_WITH_DELEGATES`), selecting
the matching hash preimage automatically. This is transparent: there is no API
change for callers.

Method signature:
```
suspend fun jwtToken(
    clientAccountId: String,                                       // C... contract address to authenticate
    signers: List<KeyPair> = emptyList(),                          // keypairs with private keys; can be empty
    homeDomain: String? = null,                                    // defaults to serverHomeDomain from stellar.toml
    clientDomain: String? = null,                                  // wallet domain for client attribution
    clientDomainAccountKeyPair: KeyPair? = null,                   // wallet signing keypair (local signing)
    clientDomainSigningDelegate: Sep45ClientDomainSigningDelegate? = null, // delegate for remote client domain signing
    signatureExpirationLedger: Long? = null,                       // defaults to current ledger + 10
): Sep45AuthToken
```

Returns `Sep45AuthToken` containing the JWT token string and parsed claims. Throws on any failure -- see [Error Handling](#error-handling).

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts

val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)

val contractId = "CCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"
val signer = KeyPair.fromSecretSeed(contractSignerSeed)

// Simple: auto-expiration, default home domain
val authToken = webAuth.jwtToken(contractId, listOf(signer))
println("JWT: ${authToken.token}")

// With explicit home domain and custom expiration
val authToken2 = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signer),
    homeDomain = "anchor.example.com",
    signatureExpirationLedger = 1500000L,
)
```

**Signature expiration:** When signers are provided and `signatureExpirationLedger` is `null`, the SDK calls `SorobanServer.getLatestLedger()` and sets expiration to `sequence + 10` (~50-60 seconds). If the signers list is empty, this Soroban RPC call is skipped entirely.

---

## Contracts Without Signature Requirements

Some contracts implement `__check_auth` without requiring signature verification. Pass an empty list for signers:

```kotlin
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts

val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)

// Empty signers list -- no signatures added, no Soroban RPC call made
val authToken = webAuth.jwtToken(
    clientAccountId = "CCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ",
    signers = emptyList(),
)
```

This only works if the anchor also supports signature-less authentication.

---

## Client Domain Verification

Non-custodial wallets can prove their domain identity so the anchor can attribute requests to a specific wallet. The wallet's `stellar.toml` must publish a `SIGNING_KEY`.

### Local signing (wallet owns the key)

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts

val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)

val contractId = "CCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"
val signer = KeyPair.fromSecretSeed(contractSignerSeed)
val clientDomainKeyPair = KeyPair.fromSecretSeed(walletSigningSecretSeed)

val authToken = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signer),
    homeDomain = "anchor.example.com",
    clientDomain = "wallet.example.com",
    clientDomainAccountKeyPair = clientDomainKeyPair,
)
```

### Remote signing via delegate

When the client domain signing key is on a separate server, provide a `Sep45ClientDomainSigningDelegate`. The delegate receives a base64-encoded `SorobanAuthorizationEntryXdr` (the client domain entry) and must return it signed as base64.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep45.Sep45ClientDomainSigningDelegate
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)

val contractId = "CCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"
val signer = KeyPair.fromSecretSeed(contractSignerSeed)

// Delegate receives and returns base64-encoded SorobanAuthorizationEntryXdr
val signingDelegate = Sep45ClientDomainSigningDelegate { entryXdr ->
    val client = HttpClient()
    try {
        val response = client.post("https://signing-server.wallet.example.com/sign-sep45") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $signingServerToken")
            setBody("""{"authorization_entry":"$entryXdr","network_passphrase":"Test SDF Network ; September 2015"}""")
        }
        if (response.status.value != 200) {
            throw Exception("Remote signing failed: ${response.bodyAsText()}")
        }
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json["authorization_entry"]?.jsonPrimitive?.content
            ?: throw Exception("Missing authorization_entry in response")
    } finally {
        client.close()
    }
}

// When using delegate (no clientDomainAccountKeyPair), the SDK fetches the
// client domain's stellar.toml to get its SIGNING_KEY -- one extra HTTP request
val authToken = webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signer),
    clientDomain = "wallet.example.com",
    clientDomainSigningDelegate = signingDelegate,
)
```

Delegate interface:
```kotlin
fun interface Sep45ClientDomainSigningDelegate {
    suspend fun signEntry(entryXdr: String): String
    // entryXdr: base64-encoded SorobanAuthorizationEntryXdr (unsigned)
    // returns: base64-encoded SorobanAuthorizationEntryXdr (signed)
}
```

```kotlin
// WRONG: SEP-10 delegate pattern -- receives/returns transaction XDR string
val sep10Delegate: (String) -> String = { transactionXdr -> /* ... */ }

// CORRECT: SEP-45 delegate -- receives and returns base64 SorobanAuthorizationEntryXdr
val sep45Delegate = Sep45ClientDomainSigningDelegate { entryXdr ->
    // sign and return base64 entry XDR
    signedEntryXdr
}
```

When `clientDomain` is provided, you must supply either `clientDomainAccountKeyPair` or `clientDomainSigningDelegate`. Providing neither throws `Sep45MissingClientDomainException`.

```kotlin
// WRONG: clientDomain provided without either signing means -- throws Sep45MissingClientDomainException
webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signer),
    clientDomain = "wallet.example.com",
    // missing clientDomainAccountKeyPair and clientDomainSigningDelegate
)

// CORRECT: provide one of the two signing options
webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signer),
    clientDomain = "wallet.example.com",
    clientDomainAccountKeyPair = walletKeyPair,   // option A: local keypair
    // or:
    // clientDomainSigningDelegate = signingDelegate,  // option B: remote delegate
)
```

You also cannot provide both at the same time:

```kotlin
// WRONG: both clientDomainAccountKeyPair and clientDomainSigningDelegate -- throws Sep45MissingClientDomainException
webAuth.jwtToken(
    clientAccountId = contractId,
    signers = listOf(signer),
    clientDomain = "wallet.example.com",
    clientDomainAccountKeyPair = walletKeyPair,
    clientDomainSigningDelegate = signingDelegate,
)

// CORRECT: use exactly one
```

---

## Step-by-Step Authentication

For maximum control, call each step individually.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts

val contractId = "CCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"
val signer = KeyPair.fromSecretSeed(contractSignerSeed)
val homeDomain = "anchor.example.com"

val webAuth = WebAuthForContracts.fromDomain(homeDomain, Network.TESTNET)

try {
    // Step 1: GET challenge from server
    val challengeResponse = webAuth.getChallenge(
        clientAccountId = contractId,
        homeDomain = homeDomain,
    )

    // Step 2: Decode authorization entries from base64 XDR
    val authorizationEntries = challengeResponse.authorizationEntries
        ?: throw Exception("Missing authorization_entries")
    val authEntries = webAuth.decodeAuthorizationEntries(authorizationEntries)

    // Step 3: Validate challenge (security checks -- always do before signing)
    webAuth.validateChallenge(
        authEntries = authEntries,
        clientAccountId = contractId,
        homeDomain = homeDomain,
    )

    // Step 4: Get current ledger for signature expiration
    val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")
    val latestLedger = sorobanServer.getLatestLedger()
    val expirationLedger = latestLedger.sequence + 10
    sorobanServer.close()

    // Step 5: Sign client authorization entries
    val signedEntries = webAuth.signAuthorizationEntries(
        authEntries = authEntries,
        clientAccountId = contractId,
        signers = listOf(signer),
        signatureExpirationLedger = expirationLedger,
    )

    // Step 6: POST signed entries and get JWT
    val authToken = webAuth.sendSignedChallenge(signedEntries)

    println("JWT Token: ${authToken.token}")
    println("Account: ${authToken.account}")
    println("Expires: ${authToken.expiresAt}")
} catch (e: Exception) {
    println("Error: $e")
}
```

### Method signatures for low-level access

```
suspend fun getChallenge(
    clientAccountId: String,
    homeDomain: String? = null,    // defaults to serverHomeDomain
    clientDomain: String? = null,
): Sep45ChallengeResponse

fun decodeAuthorizationEntries(base64Xdr: String): List<SorobanAuthorizationEntryXdr>

suspend fun validateChallenge(
    authEntries: List<SorobanAuthorizationEntryXdr>,
    clientAccountId: String,
    homeDomain: String? = null,            // defaults to serverHomeDomain
    clientDomainAccountId: String? = null,
)

suspend fun signAuthorizationEntries(
    authEntries: List<SorobanAuthorizationEntryXdr>,
    clientAccountId: String,
    signers: List<KeyPair>,
    signatureExpirationLedger: Long?,
    clientDomainKeyPair: KeyPair? = null,
    clientDomainAccountId: String? = null,
    clientDomainSigningDelegate: Sep45ClientDomainSigningDelegate? = null,
): List<SorobanAuthorizationEntryXdr>

suspend fun sendSignedChallenge(
    signedEntries: List<SorobanAuthorizationEntryXdr>,
): Sep45AuthToken
// returns Sep45AuthToken with parsed JWT claims

fun encodeAuthorizationEntries(entries: List<SorobanAuthorizationEntryXdr>): String
// encodes entries to base64 XDR (length-prefixed array)
```

---

## Request Format

By default the SDK submits signed challenges as `application/x-www-form-urlencoded`.
To switch to JSON, set the public field:

```kotlin
// Default: form-urlencoded (useFormUrlEncoded = true)
webAuth.useFormUrlEncoded = true

// Switch to application/json
webAuth.useFormUrlEncoded = false
```

---

## Response Objects

### Sep45ChallengeResponse

Returned by `getChallenge()`. Contains the authorization entries to decode, validate, and sign.

| Field | Type | Description |
|-------|------|-------------|
| `authorizationEntries` | `String?` | Base64-encoded XDR array of `SorobanAuthorizationEntryXdr` objects |
| `networkPassphrase` | `String?` | Optional -- server's network passphrase for validation |

```kotlin
val challengeResponse = webAuth.getChallenge(contractId)
val xdr = challengeResponse.authorizationEntries  // may be null
val passphrase = challengeResponse.networkPassphrase  // may be null
```

JSON mapping: `authorization_entries` (or `authorizationEntries`) -> `authorizationEntries`, `network_passphrase` (or `networkPassphrase`) -> `networkPassphrase`. The `Sep45ChallengeResponse.fromJson()` parser handles both snake_case and camelCase.

### Sep45TokenResponse

Internal response from the token POST endpoint. `jwtToken()` extracts the token automatically; you only encounter this directly when using `sendSignedChallenge()`.

| Field | Type | Description |
|-------|------|-------------|
| `token` | `String?` | JWT token on success (JSON field: `token`) |
| `error` | `String?` | Error message on failure (JSON field: `error`) |

```kotlin
// WRONG: the JSON field is 'token', not 'jwt_token'
// CORRECT: Sep45TokenResponse.token reads the 'token' JSON field
```

### Sep45AuthToken

Returned by `jwtToken()` and `sendSignedChallenge()`. Parses the JWT token and exposes claims.

| Property | Type | Description |
|----------|------|-------------|
| `token` | `String` | Raw JWT token string (always present) |
| `account` | `String` | Subject claim (`sub`) -- authenticated contract account (C... address) |
| `issuedAt` | `Long` | Issued-at timestamp (`iat`) -- Unix epoch seconds |
| `expiresAt` | `Long` | Expiration timestamp (`exp`) -- Unix epoch seconds |
| `issuer` | `String` | Issuer claim (`iss`) -- authentication server domain |
| `clientDomain` | `String?` | Present when client domain verification was performed |

```kotlin
val authToken = webAuth.jwtToken(contractId, listOf(signer))

// Access parsed claims
println("Contract: ${authToken.account}")
println("Issuer: ${authToken.issuer}")
println("Expires at: ${authToken.expiresAt}")

// Check expiry before use
if (authToken.isExpired()) {
    println("Token expired -- re-authenticate")
}

// Use token in API requests -- toString() returns the raw JWT string
val bearerToken = "Bearer $authToken"
```

```kotlin
// WRONG: accessing .jwt or .jwtToken on Sep45AuthToken
authToken.jwt       // does not exist
authToken.jwtToken  // does not exist

// CORRECT: use .token for raw JWT string, or toString() in string contexts
authToken.token     // String: the raw JWT
"$authToken"        // calls toString(), returns the raw JWT
```

**Graceful parsing:** If the JWT is malformed, `Sep45AuthToken.parse()` returns a token with defaults (empty `account`, 0 timestamps) rather than throwing. The raw `token` string is always preserved.

---

## Error Handling

All SEP-45 exceptions extend `Sep45Exception`, which extends `Exception`. Challenge validation exceptions extend the sealed class `Sep45ChallengeValidationException`. All exception classes are in the `com.soneso.stellar.sdk.sep.sep45.exceptions` package.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts
import com.soneso.stellar.sdk.sep.sep45.exceptions.*

val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
val contractId = "CCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"
val signer = KeyPair.fromSecretSeed(contractSignerSeed)

try {
    val authToken = webAuth.jwtToken(contractId, listOf(signer))
    println("JWT: ${authToken.token}")

} catch (e: IllegalArgumentException) {
    // Bad parameters: non-C... clientAccountId, invalid constructor args
    // (wrong prefixes, bad URL, blank domain)
    println("Invalid arguments: $e")

} catch (e: Sep45NoEndpointException) {
    // fromDomain(): stellar.toml is missing WEB_AUTH_FOR_CONTRACTS_ENDPOINT
    println("No WEB_AUTH_FOR_CONTRACTS_ENDPOINT for ${e.domain}")

} catch (e: Sep45NoContractIdException) {
    // fromDomain(): stellar.toml is missing WEB_AUTH_CONTRACT_ID
    println("No WEB_AUTH_CONTRACT_ID for ${e.domain}")

} catch (e: Sep45NoSigningKeyException) {
    // fromDomain(): stellar.toml is missing SIGNING_KEY
    println("No SIGNING_KEY for ${e.domain}")

} catch (e: Sep45MissingClientDomainException) {
    // clientDomain without signing means, or both keypair and delegate provided
    println("Client domain config error: ${e.message}")

} catch (e: Sep45ChallengeRequestException) {
    // GET challenge failed -- bad account, rate limit, server error
    // e.statusCode: Int?; e.errorMessage: String?
    println("Challenge request failed (HTTP ${e.statusCode}): ${e.errorMessage}")

} catch (e: Sep45SubInvocationsFoundException) {
    // SECURITY CRITICAL: challenge contains sub-invocations -- do NOT sign
    println("SECURITY ALERT: sub-invocations in challenge from anchor")
    throw e

} catch (e: Sep45InvalidContractAddressException) {
    // Entry contract address != WEB_AUTH_CONTRACT_ID -- substitution attack
    println("Security error: contract address mismatch: expected ${e.expected}, got ${e.actual}")

} catch (e: Sep45InvalidServerSignatureException) {
    // Server entry not signed by expected SIGNING_KEY -- possible MITM
    println("Security error: invalid server signature: ${e.message}")

} catch (e: Sep45InvalidFunctionNameException) {
    // Function name != "web_auth_verify"
    println("Invalid challenge: wrong function name: expected ${e.expected}, got ${e.actual}")

} catch (e: Sep45InvalidNetworkPassphraseException) {
    // network_passphrase in response != configured network -- cross-network attack
    println("Network passphrase mismatch: expected ${e.expected}, got ${e.actual}")

} catch (e: Sep45InvalidAccountException) {
    // account arg in entries != clientAccountId
    println("Invalid challenge: account mismatch: expected ${e.expected}, got ${e.actual}")

} catch (e: Sep45InvalidHomeDomainException) {
    // home_domain arg != expected home domain
    println("Invalid challenge: home domain mismatch: expected ${e.expected}, got ${e.actual}")

} catch (e: Sep45InvalidWebAuthDomainException) {
    // web_auth_domain arg != host of the auth endpoint URL
    println("Invalid challenge: web auth domain mismatch: expected ${e.expected}, got ${e.actual}")

} catch (e: Sep45InvalidNonceException) {
    // Nonce missing or inconsistent across entries -- replay protection violated
    println("Invalid challenge: nonce inconsistency: ${e.message}")

} catch (e: Sep45InvalidArgsException) {
    // Args not in expected Map<Symbol, String> format, web_auth_domain_account
    // != server SIGNING_KEY, or client_domain_account mismatch
    println("Invalid challenge: bad args: ${e.message}")

} catch (e: Sep45MissingServerEntryException) {
    // No authorization entry for the server account
    println("Invalid challenge: missing server entry: ${e.message}")

} catch (e: Sep45MissingClientEntryException) {
    // No authorization entry for the client contract or client domain account
    println("Invalid challenge: missing client entry: ${e.message}")

} catch (e: Sep45ChallengeValidationException) {
    // Catch-all for other validation failures (malformed XDR, empty entries, etc.)
    // Sep45ChallengeValidationException is sealed -- all subtypes listed above
    println("Challenge validation failed: ${e.message}")

} catch (e: Sep45TokenSubmissionException) {
    // Server rejected signed entries -- signer not registered in __check_auth,
    // insufficient weight, invalid signature. HTTP 200 or 400 with 'error' field.
    // e.statusCode: Int?; e.errorMessage: String?
    println("Authentication rejected (HTTP ${e.statusCode}): ${e.errorMessage}")

} catch (e: Sep45TimeoutException) {
    // HTTP 504 Gateway Timeout -- server overloaded during transaction simulation
    println("Server timeout -- retry later")

} catch (e: Sep45UnknownResponseException) {
    // Unexpected HTTP status (not 200, 400, or 504)
    // e.code: Int; e.body: String
    println("Unexpected response (HTTP ${e.code}): ${e.body}")

} catch (e: Sep45Exception) {
    // Catch-all for any SEP-45 error not matched above
    println("SEP-45 error: ${e.message}")
}
```

### Exception hierarchy

```
Sep45Exception (base -- extends Exception)
├── Sep45ChallengeRequestException       (statusCode: Int?, errorMessage: String?)
├── Sep45ChallengeValidationException    (sealed)
│   ├── Sep45SubInvocationsFoundException
│   ├── Sep45InvalidContractAddressException  (expected: String, actual: String)
│   ├── Sep45InvalidFunctionNameException     (expected: String, actual: String)
│   ├── Sep45InvalidNetworkPassphraseException(expected: String, actual: String)
│   ├── Sep45InvalidAccountException          (expected: String, actual: String)
│   ├── Sep45InvalidHomeDomainException       (expected: String, actual: String)
│   ├── Sep45InvalidWebAuthDomainException    (expected: String, actual: String)
│   ├── Sep45InvalidNonceException
│   ├── Sep45InvalidArgsException
│   ├── Sep45InvalidServerSignatureException
│   ├── Sep45MissingServerEntryException
│   └── Sep45MissingClientEntryException
├── Sep45TokenSubmissionException         (statusCode: Int?, errorMessage: String?)
├── Sep45TimeoutException
├── Sep45UnknownResponseException         (code: Int, body: String)
├── Sep45MissingClientDomainException
├── Sep45NoEndpointException              (domain: String)
├── Sep45NoContractIdException            (domain: String)
└── Sep45NoSigningKeyException            (domain: String)
```

### Exception reference table

| Exception class | Trigger | Notes |
|-----------------|---------|-------|
| `IllegalArgumentException` (Kotlin built-in) | Non-C... clientAccountId; bad constructor params (wrong prefixes, bad URL, blank domain) | Fix calling code |
| `Sep45NoEndpointException` | `fromDomain()`: stellar.toml missing `WEB_AUTH_FOR_CONTRACTS_ENDPOINT`; field `domain` | Check domain supports SEP-45 |
| `Sep45NoContractIdException` | `fromDomain()`: stellar.toml missing `WEB_AUTH_CONTRACT_ID`; field `domain` | Check domain supports SEP-45 |
| `Sep45NoSigningKeyException` | `fromDomain()`: stellar.toml missing `SIGNING_KEY`; field `domain` | Check domain supports SEP-45 |
| `Sep45MissingClientDomainException` | `clientDomain` without signing means, or both keypair and delegate provided | Provide exactly one signing option |
| `Sep45ChallengeRequestException` | GET challenge failed; fields `statusCode` (Int?) + `errorMessage` (String?) | Check account format |
| `Sep45SubInvocationsFoundException` | Challenge has sub-invocations | **CRITICAL** -- abort, do not sign |
| `Sep45InvalidContractAddressException` | Entry contract address != `WEB_AUTH_CONTRACT_ID`; fields `expected`, `actual` | **CRITICAL** -- substitution attack |
| `Sep45InvalidServerSignatureException` | Server entry not signed by expected `SIGNING_KEY` | **CRITICAL** -- possible MITM |
| `Sep45InvalidFunctionNameException` | Function name != `"web_auth_verify"`; fields `expected`, `actual` | **CRITICAL** -- wrong function |
| `Sep45InvalidNetworkPassphraseException` | `network_passphrase` != configured network; fields `expected`, `actual` | High -- cross-network attack |
| `Sep45InvalidAccountException` | `account` arg != client contract ID; fields `expected`, `actual` | High -- account substitution |
| `Sep45InvalidHomeDomainException` | `home_domain` arg != expected home domain; fields `expected`, `actual` | High -- domain confusion |
| `Sep45InvalidWebAuthDomainException` | `web_auth_domain` arg != auth endpoint host; fields `expected`, `actual` | High -- server spoofing |
| `Sep45InvalidNonceException` | Nonce missing or inconsistent across entries | High -- replay attack |
| `Sep45InvalidArgsException` | Args not in Map format, `web_auth_domain_account` != server key, client domain mismatch | High |
| `Sep45MissingServerEntryException` | No entry whose credentials address = server signing key | High |
| `Sep45MissingClientEntryException` | No entry whose credentials address = client contract or client domain | High |
| `Sep45ChallengeValidationException` | Generic validation failure (sealed base class) | Unexpected server behavior |
| `Sep45TokenSubmissionException` | Server rejected signed entries; fields `statusCode` (Int?) + `errorMessage` (String?) | Check signer registration |
| `Sep45TimeoutException` | HTTP 504 Gateway Timeout | Retry with backoff |
| `Sep45UnknownResponseException` | Unexpected HTTP status; fields `code` (Int) + `body` (String) | Unexpected server behavior |

---

## Common Pitfalls

**WRONG: passing a G... or M... address to jwtToken()**

```kotlin
// WRONG: jwtToken() requires a C... contract address -- throws IllegalArgumentException
webAuth.jwtToken("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP", listOf(signer))

// CORRECT: pass the C... contract address
webAuth.jwtToken("CCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", listOf(signer))
```

**WRONG: signers must contain KeyPairs with private keys**

```kotlin
// WRONG: KeyPair.fromAccountId() has no private key and cannot sign
val publicOnly = KeyPair.fromAccountId(accountId)
webAuth.jwtToken(contractId, listOf(publicOnly))
// -> server rejects (Sep45TokenSubmissionException)

// CORRECT: KeyPair.fromSecretSeed() includes the private key
val signer = KeyPair.fromSecretSeed(secretSeed)
webAuth.jwtToken(contractId, listOf(signer))
```

**WRONG: treating SubInvocationsFound as a recoverable error**

```kotlin
// WRONG: logging and continuing -- could authorize unintended contract operations
} catch (e: Sep45SubInvocationsFoundException) {
    println("Warning: $e") // Do NOT retry or sign
}

// CORRECT: abort and rethrow
} catch (e: Sep45SubInvocationsFoundException) {
    // This indicates a potentially malicious server -- abort immediately
    throw e
}
```

**WRONG: network mismatch between WebAuthForContracts and the anchor**

```kotlin
// WRONG: pubnet WebAuthForContracts against a testnet anchor
val webAuth = WebAuthForContracts(
    authEndpoint = endpoint,
    webAuthContractId = contractId,
    serverSigningKey = signingKey,
    serverHomeDomain = domain,
    network = Network.PUBLIC,
)
// -> Sep45InvalidServerSignatureException or Sep45InvalidNetworkPassphraseException

// CORRECT: match the network to the anchor's actual network
val webAuth = WebAuthForContracts(
    authEndpoint = endpoint,
    webAuthContractId = contractId,
    serverSigningKey = signingKey,
    serverHomeDomain = domain,
    network = Network.TESTNET,
)
```

**WRONG: wrong constructor parameter order (positional)**

```kotlin
// WRONG: confusing webAuthContractId (C...) and serverSigningKey (G...)
WebAuthForContracts(endpoint, serverSigningKey, webAuthContractId, domain, Network.TESTNET)
// -> IllegalArgumentException: webAuthContractId must be a contract address starting with 'C'

// CORRECT: use named parameters to avoid confusion
WebAuthForContracts(
    authEndpoint = endpoint,
    webAuthContractId = webAuthContractId,  // C...
    serverSigningKey = serverSigningKey,    // G...
    serverHomeDomain = domain,
    network = Network.TESTNET,
)
```

**WRONG: using fromDomain() then assigning a mock httpClient**

`fromDomain()` uses its own `HttpClient` for the stellar.toml fetch. After `fromDomain()` returns, the internal client is already set. For testing, always construct `WebAuthForContracts` manually and pass the mock via the `httpClient` parameter.

```kotlin
// WRONG: fromDomain() uses real network for stellar.toml -- mock arrives too late
val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
// no way to inject mock after construction

// CORRECT: construct manually for full mock control
val webAuth = WebAuthForContracts(
    authEndpoint = authServer,
    webAuthContractId = webAuthContractId,
    serverSigningKey = serverAccountId,
    serverHomeDomain = domain,
    network = Network.TESTNET,
    httpClient = mockClient,
)
```

**WRONG: Sep45ChallengeRequestException field names**

```kotlin
// WRONG: no 'code' or 'body' fields on Sep45ChallengeRequestException
println(e.code)    // does not exist
println(e.body)    // does not exist

// CORRECT: use 'statusCode' and 'errorMessage'
println(e.statusCode)    // Int? HTTP status code
println(e.errorMessage)  // String? error message
```

**WRONG: Sep45TokenSubmissionException field names**

```kotlin
// WRONG: no 'error' field on Sep45TokenSubmissionException
println(e.error)   // does not exist

// CORRECT: use 'statusCode' and 'errorMessage'
println(e.statusCode)    // Int? HTTP status code
println(e.errorMessage)  // String? error message
```

**WRONG: Sep45UnknownResponseException field names**

```kotlin
// WRONG: accessing 'statusCode' or 'errorMessage'
println(e.statusCode)    // does not exist
println(e.errorMessage)  // does not exist

// CORRECT: use 'code' and 'body'
println(e.code)   // Int: HTTP status code
println(e.body)   // String: raw response body
```

**WRONG: expecting jwtToken() to return a String**

```kotlin
// WRONG: jwtToken() returns Sep45AuthToken, not String
val token: String = webAuth.jwtToken(contractId, listOf(signer))

// CORRECT: returns Sep45AuthToken; use .token for the raw JWT string
val authToken: Sep45AuthToken = webAuth.jwtToken(contractId, listOf(signer))
val jwtString: String = authToken.token
```

**WRONG: forgetting suspend context**

```kotlin
// WRONG: calling suspend functions outside a coroutine
fun authenticate() {
    val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET) // ERROR
    val token = webAuth.jwtToken(contractId, listOf(signer)) // ERROR
}

// CORRECT: use a suspend function or coroutine scope
suspend fun authenticate() {
    val webAuth = WebAuthForContracts.fromDomain("anchor.example.com", Network.TESTNET)
    val token = webAuth.jwtToken(contractId, listOf(signer))
}
```

---

## SEP-45 vs SEP-10 Comparison

| Aspect | SEP-45 (`WebAuthForContracts`) | SEP-10 (`WebAuth`) |
|--------|-------------------------------|---------------------|
| Account type | Contract accounts (C...) | Traditional accounts (G... and M...) |
| stellar.toml endpoint key | `WEB_AUTH_FOR_CONTRACTS_ENDPOINT` | `WEB_AUTH_ENDPOINT` |
| Extra stellar.toml key | `WEB_AUTH_CONTRACT_ID` | -- |
| Challenge format | Array of `SorobanAuthorizationEntryXdr` (XDR) | Stellar transaction envelope (XDR) |
| Main class | `WebAuthForContracts` | `WebAuth` |
| Return type | `Sep45AuthToken` | JWT `String` |
| Challenge response field | `authorization_entries` | `transaction` |
| Client domain signing | `Sep45ClientDomainSigningDelegate` (base64 entry XDR) | Callback with base64 transaction XDR |
| Memo support | No | Yes (G... accounts only) |
| Muxed account support | No | Yes (M... addresses) |
| Replay protection | Signature expiration ledger + nonce | Transaction time bounds |
| Auth verification | Contract `__check_auth` invoked by server | Server verifies Ed25519 signature |
| Empty signers allowed | Yes (contract may not need signatures) | No |
| Exception base class | `Sep45Exception` | SEP-10 exception hierarchy |
| `fromDomain()` exceptions | `Sep45NoEndpointException`, `Sep45NoContractIdException`, `Sep45NoSigningKeyException` | SEP-10 equivalents |

---

## Related SEPs

- [sep-10.md](sep-10.md) -- Web Authentication for traditional accounts (G... addresses)
- [sep-01.md](sep-01.md) -- stellar.toml discovery (provides `WEB_AUTH_FOR_CONTRACTS_ENDPOINT`, `WEB_AUTH_CONTRACT_ID`, `SIGNING_KEY`)
