# SEP-30: Account Recovery

**Purpose:** Recover access to Stellar accounts when the owner loses their private key. Recovery servers act as cosigners: register your account with one or more servers, then call on them to sign a key-rotation transaction if you ever lose your private key.
**Prerequisites:** Requires JWT from SEP-10 (see `sep-10.md`) for registration and updates. Recovery (signing) uses a JWT from the server's alternate auth flow (email/phone/stellar_address).

## Table of Contents

1. [How Recovery Works](#1-how-recovery-works)
2. [Creating the Service](#2-creating-the-service)
3. [Registering an Account](#3-registering-an-account)
4. [Adding the Recovery Signer to Your Stellar Account](#4-adding-the-recovery-signer-to-your-stellar-account)
5. [Signing a Recovery Transaction](#5-signing-a-recovery-transaction)
6. [Updating Identity Information](#6-updating-identity-information)
7. [Getting Account Details](#7-getting-account-details)
8. [Listing Accounts](#8-listing-accounts)
9. [Deleting a Registration](#9-deleting-a-registration)
10. [Error Handling](#10-error-handling)
11. [Request and Response Objects](#11-request-and-response-objects)
12. [Common Pitfalls](#12-common-pitfalls)

---

## 1. How Recovery Works

1. **Register**: Call `registerAccount()` with your account address, one or more identities (role + auth methods), and your SEP-10 JWT. The server returns a signer public key.
2. **Add Signer**: Add the server's signer key to your Stellar account via `SetOptionsOperation` with `signerWeight = 1`. Set account thresholds so the server alone cannot control the account.
3. **Recovery**: If you lose your key, authenticate to the recovery server via alternate means (email, phone, etc.). The server issues a JWT proving that identity.
4. **Sign Transaction**: Build a transaction that adds your new key. Call `signTransaction()` with the recovery JWT and the signing address from the registered account. The server returns a base64 signature.
5. **Attach Signature**: Decode the base64 signature and attach it to the transaction envelope as a `DecoratedSignature`.
6. **Submit**: Submit the signed transaction to Horizon to regain control.

---

## 2. Creating the Service

```kotlin
import com.soneso.stellar.sdk.sep.sep30.Sep30Service

// Basic: service URL only
val service = Sep30Service("https://recovery.example.com")

// With custom HTTP client and headers
import io.ktor.client.*

val service = Sep30Service(
    serviceUrl = "https://recovery.example.com",
    httpClient = HttpClient(),
    httpRequestHeaders = mapOf("X-Custom-Header" to "value")
)
```

Constructor signature:
```
Sep30Service(
    serviceUrl: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
)
```

All service methods are `suspend` functions -- call them from a coroutine scope.

---

## 3. Registering an Account

Call `registerAccount()` with:
- `address` -- the Stellar account address (G... format)
- `request` -- a `Sep30Request` containing one or more `Sep30RequestIdentity` objects
- `jwt` -- a SEP-10 JWT proving you control the account

```kotlin
import com.soneso.stellar.sdk.sep.sep30.*
import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30ConflictException

val service = Sep30Service("https://recovery.example.com")

// Build authentication methods -- multiple methods provide fallback options
val emailAuth = Sep30AuthMethod(type = "email", value = "person@example.com")
val phoneAuth = Sep30AuthMethod(type = "phone_number", value = "+10000000001") // E.164 format
val stellarAuth = Sep30AuthMethod(
    type = "stellar_address",
    value = "GBUCAAMD7DYS7226CWUUOZ5Y2QF4JBJWIYU3UWJAFDGJVCR6EU5NJM5H"
)

// Single identity with role "owner"
val ownerIdentity = Sep30RequestIdentity(
    role = "owner",
    authMethods = listOf(emailAuth, phoneAuth, stellarAuth)
)
val request = Sep30Request(identities = listOf(ownerIdentity))

try {
    val response: Sep30AccountResponse =
        service.registerAccount(accountId, request, jwtToken)

    println("Account: ${response.address}")
    for (signer in response.signers) {
        println("Add signer to account: ${signer.key}")
    }
    for (identity in response.identities) {
        println("Identity role: ${identity.role ?: "unspecified"}")
    }
} catch (e: Sep30ConflictException) {
    // Account already registered -- use updateIdentitiesForAccount() instead
    println("Already registered: ${e.message}")
}
```

Multiple identities (e.g., sender + receiver for shared accounts):

```kotlin
val senderIdentity = Sep30RequestIdentity(
    role = "sender",
    authMethods = listOf(
        Sep30AuthMethod(type = "stellar_address", value = "GBUCAAMD7DYS7226CWUUOZ5Y2QF4JBJWIYU3UWJAFDGJVCR6EU5NJM5H"),
        Sep30AuthMethod(type = "phone_number", value = "+10000000001"),
        Sep30AuthMethod(type = "email", value = "person1@example.com"),
    )
)
val receiverIdentity = Sep30RequestIdentity(
    role = "receiver",
    authMethods = listOf(
        Sep30AuthMethod(type = "stellar_address", value = "GDIL76BC2XGDWLDPXCZVYB3AIZX4MYBN6JUBQPAX5OHRWPSNX3XMLNCS"),
        Sep30AuthMethod(type = "phone_number", value = "+10000000002"),
        Sep30AuthMethod(type = "email", value = "person2@example.com"),
    )
)

val request = Sep30Request(identities = listOf(senderIdentity, receiverIdentity))
val response = service.registerAccount(accountId, request, jwtToken)
```

Method signature:
```
suspend fun registerAccount(address: String, request: Sep30Request, jwt: String): Sep30AccountResponse
```

---

## 4. Adding the Recovery Signer to Your Stellar Account

After registration, add the server's signer key to your account:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer

val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
val accountKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")

// Signer key comes from the registerAccount() response
val signerKey = response.signers[0].key

val account = horizonServer.accounts().account(accountKeyPair.getAccountId())
val transaction = TransactionBuilder(account, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(
        SetOptionsOperation(
            signer = SignerKey.ed25519PublicKey(signerKey),
            signerWeight = 1
        )
    )
    // Optional: set thresholds so the server cannot act alone
    // (your key has default weight=1, server has weight=1, threshold=2)
    .addOperation(
        SetOptionsOperation(
            highThreshold = 2,
            mediumThreshold = 2,
            lowThreshold = 2
        )
    )
    .setTimeout(180)
    .build()

transaction.sign(accountKeyPair)
horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
println("Recovery signer added.")
```

**Multi-server setup:** Register with two servers, add both signer keys with `signerWeight = 1`, set thresholds to 2. Either server alone cannot control the account; both must cooperate for recovery.

---

## 5. Signing a Recovery Transaction

When you need to recover an account, build a transaction that adds your new key, then get the recovery server to sign it.

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep30.*
import kotlin.io.encoding.Base64

val service = Sep30Service("https://recovery.example.com")
val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")

// Use a JWT from alternate authentication (email/phone), not your main key
// Step 1: Find the signing address (the server's signer key for this account)
val accountDetails = service.accountDetails(accountId, recoveryJwt)
val signingAddress = accountDetails.signers[0].key

// Step 2: Generate a new keypair to replace the lost key
val newKeyPair = KeyPair.random()

// Step 3: Build the recovery transaction (uses the lost account's current sequence)
val account = horizonServer.accounts().account(accountId)
val transaction = TransactionBuilder(account, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(
        SetOptionsOperation(
            signer = SignerKey.ed25519PublicKey(newKeyPair.getAccountId()),
            signerWeight = 10  // high weight to regain control
        )
    )
    .setTimeout(180)
    .build()

// Step 4: Serialize to base64 XDR -- this is what signTransaction() expects
val txBase64 = transaction.toEnvelopeXdrBase64()

// Step 5: Request the recovery server to sign it
val signatureResponse: Sep30SignatureResponse = service.signTransaction(
    address = accountId,
    signingAddress = signingAddress,
    transaction = txBase64,
    jwt = recoveryJwt
)

// Step 6: Attach the server's signature to the transaction
val signerPublicKey = KeyPair.fromAccountId(signingAddress).getPublicKey()
val hint = signerPublicKey.copyOfRange(signerPublicKey.size - 4, signerPublicKey.size)
val signatureBytes = Base64.decode(signatureResponse.signature)
val decoratedSignature = DecoratedSignature(hint = hint, signature = signatureBytes)
transaction.signatures.add(decoratedSignature)

// For multi-server recovery: repeat steps 5-6 for each server, then submit
horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
println("Account recovered! New seed: ${String(newKeyPair.getSecretSeed()!!)}")
println("Store this seed securely!")
```

Method signature:
```
suspend fun signTransaction(address: String, signingAddress: String, transaction: String, jwt: String): Sep30SignatureResponse
```

The `transaction` parameter is the base64-encoded XDR envelope string (from `transaction.toEnvelopeXdrBase64()`).

`Sep30SignatureResponse` fields:
- `signature` -- base64-encoded signature bytes
- `networkPassphrase` -- the Stellar network passphrase the signature is valid for

---

## 6. Updating Identity Information

Replace all existing identities on a registered account. This is a **full replacement**, not a merge -- any identity not included in the request will be removed.

```kotlin
import com.soneso.stellar.sdk.sep.sep30.*

val service = Sep30Service("https://recovery.example.com")

// New set of identities -- completely replaces existing ones
val newEmail = Sep30AuthMethod(type = "email", value = "newemail@example.com")
val newPhone = Sep30AuthMethod(type = "phone_number", value = "+14155559999")
val ownerIdentity = Sep30RequestIdentity(
    role = "owner",
    authMethods = listOf(newEmail, newPhone)
)

val request = Sep30Request(identities = listOf(ownerIdentity))
val response: Sep30AccountResponse =
    service.updateIdentitiesForAccount(accountId, request, jwtToken)

println("Update successful.")
for (identity in response.identities) {
    println("Role: ${identity.role ?: "unspecified"}")
}
```

Method signature:
```
suspend fun updateIdentitiesForAccount(address: String, request: Sep30Request, jwt: String): Sep30AccountResponse
```

---

## 7. Getting Account Details

Retrieve the current registration state: identities, authentication status, and signer keys.

```kotlin
import com.soneso.stellar.sdk.sep.sep30.*

val service = Sep30Service("https://recovery.example.com")

val response: Sep30AccountResponse = service.accountDetails(accountId, jwtToken)

println("Address: ${response.address}")

for (identity in response.identities) {
    // authenticated is Boolean? -- null when the server does not return the field
    val authStatus = if (identity.authenticated == true) " (authenticated)" else ""
    println("  Role: ${identity.role ?: "unspecified"}$authStatus")
}

for (signer in response.signers) {
    println("  Signer: ${signer.key}")
}

// Use the signer key for recovery (pass to signTransaction)
val signingAddress = response.signers[0].key
```

Method signature:
```
suspend fun accountDetails(address: String, jwt: String): Sep30AccountResponse
```

---

## 8. Listing Accounts

List all accounts the authenticated identity has access to. Results are paginated; use the last account's address as the `after` cursor for the next page.

```kotlin
import com.soneso.stellar.sdk.sep.sep30.*

val service = Sep30Service("https://recovery.example.com")

// First page (no cursor)
val response: Sep30AccountsResponse = service.accounts(jwt = jwtToken)

println("Found ${response.accounts.size} accounts")
for (account in response.accounts) {
    println("  ${account.address}")
    for (identity in account.identities) {
        val auth = if (identity.authenticated == true) " (you)" else ""
        println("    Role: ${identity.role ?: "unspecified"}$auth")
    }
}

// Next page: pass the last account address as cursor
if (response.accounts.isNotEmpty()) {
    val lastAddress = response.accounts.last().address
    val nextPage: Sep30AccountsResponse =
        service.accounts(jwt = jwtToken, after = lastAddress)
    println("Next page: ${nextPage.accounts.size} accounts")
}
```

Method signature:
```
suspend fun accounts(jwt: String, after: String? = null): Sep30AccountsResponse
```

The `after` parameter is a cursor (account address string). Omit it or pass null for the first page.

---

## 9. Deleting a Registration

Remove an account from the recovery server. This is **irrecoverable**. After deletion, also remove the server's signer key from your Stellar account.

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep30.*

val service = Sep30Service("https://recovery.example.com")
val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")

// Get signer key before deletion so we can remove it from the account
val details = service.accountDetails(accountId, jwtToken)
val signerToRemove = details.signers[0].key

// Delete from recovery server
val response: Sep30AccountResponse = service.deleteAccount(accountId, jwtToken)
println("Deleted from recovery server.")

// Remove the signer from the Stellar account (signerWeight = 0 removes a signer)
val accountKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val account = horizonServer.accounts().account(accountId)
val transaction = TransactionBuilder(account, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(
        SetOptionsOperation(
            signer = SignerKey.ed25519PublicKey(signerToRemove),
            signerWeight = 0  // weight = 0 removes the signer
        )
    )
    .setTimeout(180)
    .build()
transaction.sign(accountKeyPair)
horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
println("Recovery signer removed from Stellar account.")
```

Method signature:
```
suspend fun deleteAccount(address: String, jwt: String): Sep30AccountResponse
```

Returns the final account state before deletion.

---

## 10. Error Handling

The SDK throws typed exceptions for each HTTP error code. All exceptions extend `Sep30Exception`.

```kotlin
import com.soneso.stellar.sdk.sep.sep30.*
import com.soneso.stellar.sdk.sep.sep30.exceptions.*

val service = Sep30Service("https://recovery.example.com")

try {
    val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
    val ownerIdentity = Sep30RequestIdentity(role = "owner", authMethods = listOf(emailAuth))
    val request = Sep30Request(identities = listOf(ownerIdentity))
    val response = service.registerAccount(accountId, request, jwtToken)

} catch (e: Sep30BadRequestException) {
    // HTTP 400: invalid request data, missing required fields,
    // invalid auth method types/values, or malformed transaction XDR
    println("Bad request (400): ${e.message}")

} catch (e: Sep30UnauthorizedException) {
    // HTTP 401: JWT missing, invalid, expired, or does not prove account ownership
    println("Unauthorized (401): ${e.message}")

} catch (e: Sep30NotFoundException) {
    // HTTP 404: account not registered, signing address not recognized,
    // or authenticated identity does not have access to this account
    println("Not found (404): ${e.message}")

} catch (e: Sep30ConflictException) {
    // HTTP 409: account already registered (use updateIdentitiesForAccount() instead),
    // or update conflicts with server state
    println("Conflict (409): ${e.message}")

} catch (e: Sep30InvalidResponseException) {
    // HTTP 200 but response body is malformed or missing required fields
    println("Invalid response: ${e.message}")

} catch (e: Sep30UnknownResponseException) {
    // Other HTTP errors (5xx, etc.) -- raw HTTP status code and body available
    println("Unknown error (${e.statusCode}): ${e.responseBody}")

} catch (e: Sep30Exception) {
    // Catch-all for any SEP-30 error
    println("SEP-30 error: ${e.message}")

} catch (e: Exception) {
    // Network-level failures: connection refused, timeout, DNS failure, etc.
    println("Network error: $e")
}
```

### Exception reference

| Exception | HTTP | Key properties | Typical cause |
|-----------|------|----------------|---------------|
| `Sep30BadRequestException` | 400 | `.message` | Invalid fields, bad auth method values |
| `Sep30UnauthorizedException` | 401 | `.message` | Missing/expired/invalid JWT |
| `Sep30NotFoundException` | 404 | `.message` | Account not registered, signing address unknown |
| `Sep30ConflictException` | 409 | `.message` | Account already registered, state conflict |
| `Sep30InvalidResponseException` | 200 | `.message` | Server returned malformed JSON body |
| `Sep30UnknownResponseException` | Other | `.statusCode` (Int), `.responseBody` (String) | 5xx errors, unexpected server responses |

All exceptions extend `Sep30Exception` which extends `Exception`. Access the error description via `.message`. `Sep30UnknownResponseException` additionally exposes `.statusCode` (Int) and `.responseBody` (String).

---

## 11. Request and Response Objects

### Sep30AuthMethod

Single authentication method for an identity.

```kotlin
// Constructor -- data class with named parameters
Sep30AuthMethod(type: String, value: String)

// Standard types
Sep30AuthMethod(type = "email", value = "person@example.com")
Sep30AuthMethod(type = "phone_number", value = "+10000000001")   // E.164 format: +[country][number], no spaces
Sep30AuthMethod(type = "stellar_address", value = "GBUCA...H")  // G... Stellar address

// Access properties directly
method.type   // String
method.value  // String
```

### Sep30RequestIdentity

Identity with a role and one or more authentication methods. The role is a client-defined label; the JSON key for auth methods is `auth_methods`.

```kotlin
// Constructor -- data class with named parameters
Sep30RequestIdentity(role: String, authMethods: List<Sep30AuthMethod>)

identity.role         // String
identity.authMethods  // List<Sep30AuthMethod>
```

Common roles: `"owner"` (single user), `"sender"` / `"receiver"` (account sharing), `"other"` (additional signers with sign-only permissions).

### Sep30Request

Container for one or more identities. Serializes to `{"identities": [...]}`.

```kotlin
// Constructor -- data class with named parameters
Sep30Request(identities: List<Sep30RequestIdentity>)

request.identities  // List<Sep30RequestIdentity>
```

### Sep30AccountResponse

Returned by `registerAccount()`, `updateIdentitiesForAccount()`, `accountDetails()`, and `deleteAccount()`.

```kotlin
response.address     // String -- the Stellar account address
response.identities  // List<Sep30ResponseIdentity>
response.signers     // List<Sep30ResponseSigner>
```

### Sep30ResponseIdentity

```kotlin
identity.role           // String? -- e.g. "owner", "sender", "receiver"; null if server omits
identity.authenticated  // Boolean? -- true if this identity authenticated the current request,
                        // false if explicitly unauthenticated, null if server did not return the field
```

### Sep30ResponseSigner

```kotlin
signer.key  // String -- G... public key to add as a signer on the Stellar account
```

### Sep30SignatureResponse

Returned by `signTransaction()`.

```kotlin
response.signature         // String -- base64-encoded signature bytes
response.networkPassphrase // String -- e.g. "Test SDF Network ; September 2015"
```

### Sep30AccountsResponse

Returned by `accounts()`.

```kotlin
response.accounts  // List<Sep30AccountResponse>
```

---

## 12. Common Pitfalls

**Wrong: re-registering instead of updating**

```kotlin
// WRONG: calling registerAccount() on an already-registered account throws Sep30ConflictException
service.registerAccount(accountId, request, jwt)

// CORRECT: use updateIdentitiesForAccount() for changes to an existing registration
service.updateIdentitiesForAccount(accountId, request, jwt)
```

**Wrong: passing the Transaction object instead of base64 XDR to signTransaction()**

```kotlin
// WRONG: signTransaction() expects a String, not a Transaction object
service.signTransaction(accountId, signingAddress, transaction, jwt)

// CORRECT: serialize to base64 XDR envelope first
val txBase64 = transaction.toEnvelopeXdrBase64()
service.signTransaction(accountId, signingAddress, txBase64, jwt)
```

**Wrong: using the account address instead of the signing address for the signature hint**

```kotlin
// WRONG: using the account address to derive the hint
val wrongKey = KeyPair.fromAccountId(accountId).getPublicKey()
val hint = wrongKey.copyOfRange(wrongKey.size - 4, wrongKey.size)

// CORRECT: use the signing address (the server's signer key, from accountDetails.signers[0].key)
val signerPublicKey = KeyPair.fromAccountId(signingAddress).getPublicKey()
val hint = signerPublicKey.copyOfRange(signerPublicKey.size - 4, signerPublicKey.size)
val signatureBytes = Base64.decode(signatureResponse.signature)  // kotlin.io.encoding.Base64
val decoratedSig = DecoratedSignature(hint = hint, signature = signatureBytes)
transaction.signatures.add(decoratedSig)
```

**Wrong: using SetOptionsOperationBuilder (does not exist in KMP SDK)**

```kotlin
// WRONG: there is no SetOptionsOperationBuilder in the KMP SDK
SetOptionsOperationBuilder().setSigner(...).build()

// CORRECT: use SetOptionsOperation data class directly with named parameters
SetOptionsOperation(
    signer = SignerKey.ed25519PublicKey(signerKey),
    signerWeight = 1
)
```

**Wrong: phone number format**

```kotlin
// WRONG: spaces, missing +, or missing country code
Sep30AuthMethod(type = "phone_number", value = "415 555 1234")     // missing + and country code
Sep30AuthMethod(type = "phone_number", value = "+1 415 555 1234")  // has spaces

// CORRECT: E.164 format -- leading +, country code, digits only, no spaces
Sep30AuthMethod(type = "phone_number", value = "+14155551234")
```

**Wrong: forgetting to null-check `authenticated`**

```kotlin
// WRONG: authenticated is Boolean? -- direct access without null-check throws NPE
if (identity.authenticated) { ... }           // compiler error: Boolean? cannot be used as Boolean

// CORRECT: explicit comparison
if (identity.authenticated == true) { ... }
// or
if (identity.authenticated ?: false) { ... }  // null-coalescing with Elvis operator
```

**Wrong: forgetting to remove server signer after deleteAccount()**

```kotlin
// WRONG: deleting from the recovery server but leaving the signer on-chain
// The signer still exists on the Stellar account and could be misused.
service.deleteAccount(accountId, jwt)
// (no follow-up on-chain operation)

// CORRECT: also remove the signer from the Stellar account using signerWeight = 0
val tx = TransactionBuilder(account, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(
        SetOptionsOperation(
            signer = SignerKey.ed25519PublicKey(signerKey),
            signerWeight = 0
        )
    )
    .setTimeout(180)
    .build()
```

**Note: updateIdentitiesForAccount() fully replaces identities**

The PUT operation is not additive. If you have two identities and call `updateIdentitiesForAccount()` with only one, the second identity is deleted. Always include all identities you want to keep.

**Note: JWT is passed without "Bearer " prefix -- the SDK adds it**

```kotlin
// WRONG: including the prefix yourself
service.registerAccount(accountId, request, "Bearer eyJhbGci...")

// CORRECT: pass the raw JWT token string -- the SDK adds "Bearer " to the Authorization header
service.registerAccount(accountId, request, "eyJhbGci...")
```

**Note: all service methods are suspend functions**

```kotlin
// WRONG: calling from non-suspend context without a coroutine scope
val response = service.registerAccount(accountId, request, jwt) // compile error

// CORRECT: call from a coroutine scope
runBlocking {
    val response = service.registerAccount(accountId, request, jwt)
}
// or from another suspend function
suspend fun register() {
    val response = service.registerAccount(accountId, request, jwt)
}
```

---
