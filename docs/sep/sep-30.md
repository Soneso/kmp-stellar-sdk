# SEP-30: Account Recovery

**[SEP-0030 Compatibility Matrix](../../compatibility/sep/SEP-0030_COMPATIBILITY_MATRIX.md)** - Full implementation coverage details

SEP-30 defines a protocol for multi-party recovery of Stellar accounts. Users register their accounts with one or more recovery servers and provide alternative authentication methods (email, phone number, or Stellar address). When keys are lost, authenticated users request these servers to sign recovery transactions. No single server has full control -- multiple servers must cooperate to restore access.

**Use Cases**:
- Recover access to a Stellar account after losing private keys
- Share account access across devices or between individuals
- Social recovery using trusted contacts as identity verifiers

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep30.*
import com.soneso.stellar.sdk.horizon.*
```

## Quick Start

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep30.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun accountRecoveryExample() {
    // Create the recovery service (URL provided directly, not via stellar.toml)
    val sep30 = Sep30Service("https://recovery.example.com")

    // Define identity with authentication methods
    val ownerIdentity = Sep30RequestIdentity(
        role = "owner",
        authMethods = listOf(
            Sep30AuthMethod(type = "email", value = "user@example.com"),
            Sep30AuthMethod(type = "phone_number", value = "+14155551234")
        )
    )
    val request = Sep30Request(identities = listOf(ownerIdentity))

    // Register the account (requires SEP-10 JWT proving high threshold control)
    val sep10Jwt = "eyJ..." // Obtained via SEP-10 authentication
    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
    val registration = sep30.registerAccount(accountAddress, request, sep10Jwt)

    // The server returns its signing key -- add it as a signer on the account
    val serverSignerKey = registration.signers.first().key
    println("Add this key as a signer on your account: $serverSignerKey")

    // Later, when recovery is needed: sign a recovery transaction
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val sourceAccount = server.loadAccount(accountAddress)
    val newKeyPair = KeyPair.random()

    // Build a transaction to add a new signer to the account
    val recoveryTx = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(
            SetOptionsOperation(
                signer = SignerKey.ed25519PublicKey(newKeyPair.getPublicKey()),
                signerWeight = 1
            )
        )
        .build()

    // Request the recovery server to sign
    val externalJwt = "eyJ..." // JWT from external auth (email/phone verification)
    val sigResponse = sep30.signTransaction(
        address = accountAddress,
        signingAddress = serverSignerKey,
        transaction = recoveryTx.toEnvelopeXdrBase64(),
        jwt = externalJwt
    )

    println("Signature: ${sigResponse.signature}")
    println("Network: ${sigResponse.networkPassphrase}")
}
```

## Service Initialization

SEP-30 does not define stellar.toml discovery. The recovery server URL must be provided directly.

### Constructor

```kotlin
val sep30 = Sep30Service("https://recovery.example.com")
```

### With Custom HTTP Client

```kotlin
import io.ktor.client.*

val customClient = HttpClient { /* custom configuration */ }
val sep30 = Sep30Service(
    serviceUrl = "https://recovery.example.com",
    httpClient = customClient
)
```

### With Custom Headers

```kotlin
val sep30 = Sep30Service(
    serviceUrl = "https://recovery.example.com",
    httpRequestHeaders = mapOf("X-Custom-Header" to "value")
)
```

## Account Registration

Registers an account with the recovery server. The server generates a signing key for the account and returns it in the response. The client must then add this key as a signer on the Stellar account.

Registration requires a SEP-10 JWT proving high threshold control of the account. Other endpoints accept either SEP-10 or external authentication JWTs.

```kotlin
suspend fun registerAccountExample() {
    val sep30 = Sep30Service("https://recovery.example.com")
    val sep10Jwt = "eyJ..." // SEP-10 JWT proving high threshold control

    // Define owner identity with multiple auth methods
    val ownerIdentity = Sep30RequestIdentity(
        role = "owner",
        authMethods = listOf(
            Sep30AuthMethod(type = "email", value = "alice@example.com"),
            Sep30AuthMethod(type = "phone_number", value = "+14155551234"),
            Sep30AuthMethod(type = "stellar_address", value = "GAJZR5RMNUNEK7CRXJVEWXZ5XUXWT7FJGILCDDOITF7EC26RPWJ4UVOE")
        )
    )

    val request = Sep30Request(identities = listOf(ownerIdentity))
    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

    val response = sep30.registerAccount(accountAddress, request, sep10Jwt)

    println("Registered: ${response.address}")
    response.signers.forEach { signer ->
        println("Server signer key: ${signer.key}")
    }

    // Add the server's signing key as a signer on the Stellar account
    val serverSignerKey = response.signers.first().key
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val senderKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
    val sourceAccount = server.loadAccount(senderKeyPair.getAccountId())

    val addSignerTx = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(
            SetOptionsOperation(
                signer = SignerKey.ed25519PublicKey(serverSignerKey),
                signerWeight = 1
            )
        )
        .build()
    addSignerTx.sign(senderKeyPair)
    server.submitTransaction(addSignerTx.toEnvelopeXdrBase64())
}
```

### Multiple Identities (Account Sharing)

Register with multiple identities for shared account access. Roles distinguish participants.

```kotlin
suspend fun registerSharedAccountExample() {
    val sep30 = Sep30Service("https://recovery.example.com")
    val sep10Jwt = "eyJ..."

    val senderIdentity = Sep30RequestIdentity(
        role = "sender",
        authMethods = listOf(
            Sep30AuthMethod(type = "email", value = "alice@example.com"),
            Sep30AuthMethod(type = "phone_number", value = "+14155551234")
        )
    )

    val receiverIdentity = Sep30RequestIdentity(
        role = "receiver",
        authMethods = listOf(
            Sep30AuthMethod(type = "email", value = "bob@example.com"),
            Sep30AuthMethod(type = "stellar_address", value = "GDVEU3DD4KOFECV66VIHWEZOYX4ZKR3WV27L464SIIPOU2IUI3JCZA57")
        )
    )

    val request = Sep30Request(identities = listOf(senderIdentity, receiverIdentity))
    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

    val response = sep30.registerAccount(accountAddress, request, sep10Jwt)
    response.identities.forEach { identity ->
        println("Role: ${identity.role}")
    }
}
```

## Updating Identities

Replaces all existing identities with the provided list. This is a full replacement, not a merge. Any identity not included in the request is removed.

```kotlin
suspend fun updateIdentitiesExample() {
    val sep30 = Sep30Service("https://recovery.example.com")
    val jwt = "eyJ..." // SEP-10 or external auth JWT

    // Replace all identities with a new configuration
    val updatedIdentity = Sep30RequestIdentity(
        role = "owner",
        authMethods = listOf(
            Sep30AuthMethod(type = "email", value = "newemail@example.com"),
            Sep30AuthMethod(type = "phone_number", value = "+14155559999")
        )
    )

    val request = Sep30Request(identities = listOf(updatedIdentity))
    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

    val response = sep30.updateIdentitiesForAccount(accountAddress, request, jwt)
    println("Updated identities for: ${response.address}")
    response.identities.forEach { identity ->
        println("Role: ${identity.role}")
    }
}
```

## Signing Transactions

Requests the recovery server to sign a transaction using one of the account's registered signing keys. The `signingAddress` must match a signer key returned during registration or from `accountDetails()`.

```kotlin
suspend fun signTransactionExample() {
    val sep30 = Sep30Service("https://recovery.example.com")
    val jwt = "eyJ..." // SEP-10 or external auth JWT

    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
    val signingAddress = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM" // From registration response signers

    // Build the recovery transaction
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val sourceAccount = server.loadAccount(accountAddress)
    val newKeyPair = KeyPair.random()

    val recoveryTx = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(
            SetOptionsOperation(
                signer = SignerKey.ed25519PublicKey(newKeyPair.getPublicKey()),
                signerWeight = 10
            )
        )
        .build()

    val signatureResponse = sep30.signTransaction(
        address = accountAddress,
        signingAddress = signingAddress,
        transaction = recoveryTx.toEnvelopeXdrBase64(),
        jwt = jwt
    )

    println("Signature: ${signatureResponse.signature}")
    println("Network passphrase: ${signatureResponse.networkPassphrase}")

    // Add the server's signature to the transaction
    val signerKeyPair = KeyPair.fromAccountId(signingAddress)
    val sigBytes = kotlin.io.encoding.Base64.decode(signatureResponse.signature)
    val hint = signerKeyPair.getPublicKey().takeLast(4).toByteArray()
    val decoratedSignature = DecoratedSignature(hint = hint, signature = sigBytes)
    recoveryTx.signatures.add(decoratedSignature)

    // Submit the transaction to the Stellar network
    server.submitTransaction(recoveryTx.toEnvelopeXdrBase64())
}
```

## Account Details

Retrieves the recovery configuration for a registered account.

```kotlin
suspend fun accountDetailsExample() {
    val sep30 = Sep30Service("https://recovery.example.com")
    val jwt = "eyJ..." // SEP-10 or external auth JWT
    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

    val details = sep30.accountDetails(accountAddress, jwt)

    println("Account: ${details.address}")
    details.identities.forEach { identity ->
        println("Role: ${identity.role ?: "not specified"}")
        if (identity.authenticated == true) {
            println("  Currently authenticated")
        }
    }
    details.signers.forEach { signer ->
        println("Signer key: ${signer.key}")
    }
}
```

## Deleting Accounts

Permanently removes the account's recovery registration from the server. This operation is irreversible -- the server will no longer hold a signing key for the account.

```kotlin
suspend fun deleteAccountExample() {
    val sep30 = Sep30Service("https://recovery.example.com")
    val jwt = "eyJ..." // SEP-10 or external auth JWT
    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

    // Returns the account details as they were before deletion
    val deleted = sep30.deleteAccount(accountAddress, jwt)
    println("Deleted recovery registration for: ${deleted.address}")
}
```

## Listing Accounts

Returns all accounts accessible by the authenticated user. Supports cursor-based pagination using the `after` parameter.

```kotlin
suspend fun listAccountsExample() {
    val sep30 = Sep30Service("https://recovery.example.com")
    val jwt = "eyJ..." // SEP-10 or external auth JWT

    // Get first page
    val page1 = sep30.accounts(jwt = jwt)
    page1.accounts.forEach { account ->
        println("Account: ${account.address}")
        account.identities.forEach { identity ->
            val authStatus = if (identity.authenticated == true) " (authenticated)" else ""
            println("  Role: ${identity.role ?: "none"}$authStatus")
        }
    }

    // Get next page using the last address as cursor
    if (page1.accounts.isNotEmpty()) {
        val lastAddress = page1.accounts.last().address
        val page2 = sep30.accounts(jwt = jwt, after = lastAddress)
        println("Page 2 accounts: ${page2.accounts.size}")
    }
}
```

## Multi-Server Recovery Workflow

In production, register with two or more recovery servers. Configure account thresholds so that both servers must cooperate to authorize transactions -- no single server can act alone.

```kotlin
suspend fun multiServerRecoveryWorkflow() {
    // Phase 1: Set up two recovery servers
    val recovery1 = Sep30Service("https://recovery1.example.com")
    val recovery2 = Sep30Service("https://recovery2.example.com")

    val sep10Jwt1 = "eyJ..." // SEP-10 JWT for server 1
    val sep10Jwt2 = "eyJ..." // SEP-10 JWT for server 2
    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
    val ownerKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")

    val ownerIdentity = Sep30RequestIdentity(
        role = "owner",
        authMethods = listOf(
            Sep30AuthMethod(type = "email", value = "user@example.com"),
            Sep30AuthMethod(type = "phone_number", value = "+14155551234")
        )
    )
    val request = Sep30Request(identities = listOf(ownerIdentity))

    // Phase 2: Register on both servers
    val reg1 = recovery1.registerAccount(accountAddress, request, sep10Jwt1)
    val reg2 = recovery2.registerAccount(accountAddress, request, sep10Jwt2)

    val signer1Key = reg1.signers.first().key
    val signer2Key = reg2.signers.first().key

    // Phase 3: Add both server signing keys and configure thresholds
    // Each server gets weight 1, thresholds set to 2 so both must sign
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val sourceAccount = server.loadAccount(ownerKeyPair.getAccountId())

    val setupTx = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(
            SetOptionsOperation(
                signer = SignerKey.ed25519PublicKey(signer1Key),
                signerWeight = 1
            )
        )
        .addOperation(
            SetOptionsOperation(
                signer = SignerKey.ed25519PublicKey(signer2Key),
                signerWeight = 1
            )
        )
        .addOperation(
            SetOptionsOperation(
                lowThreshold = 2,
                mediumThreshold = 2,
                highThreshold = 2
            )
        )
        .build()
    setupTx.sign(ownerKeyPair)
    server.submitTransaction(setupTx.toEnvelopeXdrBase64())

    // Phase 4: Recovery -- build a transaction to add a new key
    val recoveryAccount = server.loadAccount(accountAddress)
    val newKeyPair = KeyPair.random()

    val recoveryTx = TransactionBuilder(recoveryAccount, Network.TESTNET)
        .addOperation(
            SetOptionsOperation(
                signer = SignerKey.ed25519PublicKey(newKeyPair.getPublicKey()),
                signerWeight = 10
            )
        )
        .build()

    val txXdr = recoveryTx.toEnvelopeXdrBase64()

    // Collect signatures from both servers
    val externalJwt1 = "eyJ..." // External auth JWT for server 1
    val externalJwt2 = "eyJ..." // External auth JWT for server 2

    val sig1 = recovery1.signTransaction(
        address = accountAddress,
        signingAddress = signer1Key,
        transaction = txXdr,
        jwt = externalJwt1
    )
    val sig2 = recovery2.signTransaction(
        address = accountAddress,
        signingAddress = signer2Key,
        transaction = txXdr,
        jwt = externalJwt2
    )

    // Phase 5: Add both signatures and submit
    val signer1KeyPair = KeyPair.fromAccountId(signer1Key)
    val sig1Bytes = kotlin.io.encoding.Base64.decode(sig1.signature)
    val hint1 = signer1KeyPair.getPublicKey().takeLast(4).toByteArray()
    recoveryTx.signatures.add(DecoratedSignature(hint = hint1, signature = sig1Bytes))

    val signer2KeyPair = KeyPair.fromAccountId(signer2Key)
    val sig2Bytes = kotlin.io.encoding.Base64.decode(sig2.signature)
    val hint2 = signer2KeyPair.getPublicKey().takeLast(4).toByteArray()
    recoveryTx.signatures.add(DecoratedSignature(hint = hint2, signature = sig2Bytes))

    server.submitTransaction(recoveryTx.toEnvelopeXdrBase64())
    println("Account recovered with new key: ${newKeyPair.getAccountId()}")
}
```

## Error Handling

```kotlin
import com.soneso.stellar.sdk.sep.sep30.exceptions.*

suspend fun errorHandlingExample() {
    val sep30 = Sep30Service("https://recovery.example.com")
    val jwt = "eyJ..."
    val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

    try {
        val ownerIdentity = Sep30RequestIdentity(
            role = "owner",
            authMethods = listOf(Sep30AuthMethod(type = "email", value = "user@example.com"))
        )
        val request = Sep30Request(identities = listOf(ownerIdentity))
        val response = sep30.registerAccount(accountAddress, request, jwt)
    } catch (e: Sep30BadRequestException) {
        // Malformed request or invalid parameters (HTTP 400)
        println("Bad request: ${e.message}")
    } catch (e: Sep30UnauthorizedException) {
        // Missing, invalid, or expired JWT (HTTP 401)
        println("Unauthorized: ${e.message}")
    } catch (e: Sep30NotFoundException) {
        // Account not registered or signing address not found (HTTP 404)
        println("Not found: ${e.message}")
    } catch (e: Sep30ConflictException) {
        // Account already registered (HTTP 409)
        println("Conflict: ${e.message}")
    } catch (e: Sep30InvalidResponseException) {
        // Server returned HTTP 200 but with a malformed response body
        println("Invalid response: ${e.message}")
    } catch (e: Sep30UnknownResponseException) {
        // Unexpected HTTP status code (e.g., 500, 503)
        println("Unexpected HTTP ${e.statusCode}: ${e.responseBody}")
    } catch (e: Sep30Exception) {
        // Base exception for any other SEP-30 error
        println("SEP-30 error: ${e.message}")
    }
}
```

## API Reference

**Main Class**:
- `Sep30Service` - SEP-30 Account Recovery service client

**Constructor**:
- `Sep30Service(serviceUrl, httpClient?, httpRequestHeaders?)` - Direct initialization with recovery server URL

**Methods**:
- `registerAccount(address, request, jwt)` - Register an account with identity providers (requires SEP-10 JWT)
- `updateIdentitiesForAccount(address, request, jwt)` - Replace all identities for a registered account
- `signTransaction(address, signingAddress, transaction, jwt)` - Request the server to sign a transaction
- `accountDetails(address, jwt)` - Retrieve recovery details for a registered account
- `deleteAccount(address, jwt)` - Permanently delete an account's recovery registration
- `accounts(jwt, after?)` - List all accounts accessible by the authenticated user

**Request Data Classes**:
- `Sep30Request(identities)` - Registration/update request body
  - `identities` - `List<Sep30RequestIdentity>`
- `Sep30RequestIdentity(role, authMethods)` - Identity with role and authentication methods
  - `role` - Identity role (e.g., `"owner"`, `"sender"`, `"receiver"`)
  - `authMethods` - `List<Sep30AuthMethod>`
- `Sep30AuthMethod(type, value)` - Authentication method
  - `type` - Method type (`"stellar_address"`, `"phone_number"`, `"email"`)
  - `value` - Method value (address, phone number, or email)

**Response Classes**:
- `Sep30AccountResponse` - Account recovery details
  - `address` - Stellar account ID (`String`)
  - `identities` - `List<Sep30ResponseIdentity>`
  - `signers` - `List<Sep30ResponseSigner>`
- `Sep30AccountsResponse` - List of accounts
  - `accounts` - `List<Sep30AccountResponse>`
- `Sep30SignatureResponse` - Transaction signature result
  - `signature` - Base64-encoded signature (`String`)
  - `networkPassphrase` - Network the signature is valid for (`String`)
- `Sep30ResponseIdentity` - Identity in a response
  - `role` - Identity role (`String?`)
  - `authenticated` - Whether the current client is authenticated as this identity (`Boolean?`)
- `Sep30ResponseSigner` - Signer in a response
  - `key` - Stellar public key of the signer (`String`)

**Exception Types**:
- `Sep30Exception` - Base exception for SEP-30 errors
- `Sep30BadRequestException` - Invalid request parameters (HTTP 400)
- `Sep30UnauthorizedException` - Missing or invalid authentication (HTTP 401)
- `Sep30NotFoundException` - Account or signing address not found (HTTP 404)
- `Sep30ConflictException` - Account already registered (HTTP 409)
- `Sep30InvalidResponseException` - Malformed response body (HTTP 200 with parse failure)
- `Sep30UnknownResponseException` - Unexpected HTTP status code
  - `statusCode` - HTTP status code (`Int`)
  - `responseBody` - Raw response body (`String`)

**Specification**: [SEP-30: Account Recovery](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep30`

**Last Updated**: 2026-02-14
