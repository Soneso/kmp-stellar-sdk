# SEP-2: Federation Protocol

SEP-2 maps Stellar addresses to account information. It resolves email-like addresses such as `bob*stellar.org` into account IDs, enabling users to share payment details using a syntax that works across domains.

**Use Cases**:
- Resolve human-readable addresses to account IDs for payments
- Look up memo requirements for payments to specific addresses
- Reverse lookups: find addresses from account IDs or transaction IDs
- Forward payments to other networks or financial institutions

## Quick Start

```kotlin
import com.soneso.stellar.sdk.sep.sep02.*

suspend fun quickExample() {
    // Resolve a Stellar address to an account ID
    val response = FederationService.resolveStellarAddress("bob*stellar.org")

    println("Account ID: ${response.accountId}")
    if (response.memoType != null && response.memo != null) {
        println("Memo required: ${response.memoType} = ${response.memo}")
    }
}
```

## Service Initialization

### From Domain (Recommended)

Discovers the federation server from the domain's stellar.toml file.

```kotlin
// In a coroutine scope
val service = FederationService.fromDomain("stellar.org")
```

### Direct Initialization

Use when the federation server URL is known.

```kotlin
val service = FederationService(
    federationServerUrl = "https://stellar.org/federation"
)
```

## Resolve Stellar Address

Convert a Stellar address to account information. This is the primary federation use case.

### Instance Method

```kotlin
val service = FederationService.fromDomain("stellar.org")
val response = service.resolveStellarAddress("bob*stellar.org")

println("Account ID: ${response.accountId}")
response.memo?.let { memo ->
    println("Memo: $memo (type: ${response.memoType})")
}
```

### Static Method

Automatically discovers the federation server from the address domain.

```kotlin
// In a coroutine scope
val response = FederationService.resolveStellarAddress("bob*stellar.org")
println("Account ID: ${response.accountId}")
```

### Email Address Format

Email addresses may be used as the username part.

```kotlin
// In a coroutine scope
val response = FederationService.resolveStellarAddress("maria@gmail.com*stellar.org")
println("Account ID: ${response.accountId}")
```

### Phone Number Format

Phone numbers should follow ITU-T E.123 and E.164 recommendations (international notation with leading `+`, no spaces).

```kotlin
// In a coroutine scope
val response = FederationService.resolveStellarAddress("+14155550100*stellar.org")
println("Account ID: ${response.accountId}")
```

### Using Response Data for Payments

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun sendPayment() {
    // Resolve the address
    val response = FederationService.resolveStellarAddress("bob*stellar.org")

    // Build the payment transaction
    val server = HorizonServer("https://horizon.stellar.org")
    val senderKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")
    val sourceAccount = server.accounts().account(senderKeyPair.getAccountId())

    val txBuilder = TransactionBuilder(sourceAccount, Network.PUBLIC)
        .addOperation(
            PaymentOperation(
                destination = response.accountId,
                asset = AssetTypeNative(),
                amount = "10"
            )
        )

    // Attach memo if required
    if (response.memoType != null && response.memo != null) {
        when (response.memoType) {
            "text" -> txBuilder.addMemo(MemoText(response.memo))
            "id" -> txBuilder.addMemo(MemoId(response.memo.toULong()))
            "hash" -> {
                val bytes = kotlin.io.encoding.Base64.decode(response.memo)
                txBuilder.addMemo(MemoHash(bytes))
            }
        }
    }

    val transaction = txBuilder.build()
    transaction.sign(senderKeyPair)
    server.submitTransaction(transaction.toEnvelopeXdrBase64())
}
```

## Resolve Account ID (Reverse Lookup)

Look up the Stellar address associated with an account ID.

```kotlin
val service = FederationService.fromDomain("stellar.org")
val response = service.resolveAccountId("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ")

response.stellarAddress?.let { address ->
    println("Stellar address: $address")
}
```

Note that reverse lookups may be ambiguous. If an anchor sends transactions on behalf of users, the account ID belongs to the anchor and may not resolve to a specific user. Use transaction ID resolution in such cases.

## Resolve Transaction ID

Look up the sender's Stellar address from a transaction hash.

```kotlin
val service = FederationService.fromDomain("stellar.org")
val response = service.resolveTransactionId("c1b368c00e9852351361e07cc58c54277e7a6366580044ab152b8db9cd8ec52a")

response.stellarAddress?.let { address ->
    println("Sender: $address")
}
```

This is particularly useful when an anchor sends transactions on behalf of users. The transaction ID lookup can resolve to the specific user who initiated the transaction.

## Forward Resolution

Used for forwarding payments to different networks or financial institutions. The stellar.toml file of the federation server specifies what parameters are accepted.

### Bank Account Example

```kotlin
val service = FederationService.fromDomain("stellar.org")
val response = service.resolveForward(mapOf(
    "forward_type" to "bank_account",
    "swift" to "BOPBPHMM",
    "acct" to "2382376"
))

println("Forward to: ${response.accountId}")
```

### Remittance Center Example

```kotlin
val response = service.resolveForward(mapOf(
    "forward_type" to "remittance_center",
    "first_name" to "Jhun",
    "last_name" to "Matahari",
    "address" to "17A Sales",
    "city" to "Angeles",
    "postal_code" to "12121",
    "country" to "PH",
    "mobile" to "0911111112"
))

println("Forward to: ${response.accountId}")
```

The federation server validates the forward parameters and returns an account ID where the payment should be sent. Consult the federation server's stellar.toml for supported forward types and required fields.

## Address Parsing

Validate and parse a Stellar address into username and domain components.

```kotlin
import com.soneso.stellar.sdk.sep.sep02.FederationService
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02InvalidAddressException

fun parseAddress(address: String) {
    try {
        val (username, domain) = FederationService.parseAddress("bob*stellar.org")
        println("Username: $username")
        println("Domain: $domain")
    } catch (e: Sep02InvalidAddressException) {
        println("Invalid address: ${e.message}")
    }
}
```

This validates:
- Address contains exactly one `*` separator
- Username (before `*`) is not empty
- Domain (after `*`) is not empty

## Error Handling

```kotlin
import com.soneso.stellar.sdk.sep.sep02.exceptions.*

suspend fun errorHandlingExample() {
    try {
        val response = FederationService.resolveStellarAddress("bob*stellar.org")
        println("Account ID: ${response.accountId}")
    } catch (e: Sep02InvalidAddressException) {
        // Malformed address format (missing *, empty parts, multiple *)
        println("Invalid address format: ${e.message}")
        // Prompt user to correct the address
    } catch (e: Sep02FederationNotFoundException) {
        // stellar.toml does not contain FEDERATION_SERVER field
        println("Federation server not found: ${e.message}")
        // Fall back to direct account ID lookup
    } catch (e: Sep02InvalidResponseException) {
        // Malformed server response or HTTP error
        println("Invalid federation response: ${e.message}")
        // Check if HTTP status indicates rate limiting or server error
    } catch (e: Sep02Exception) {
        // Other SEP-2 errors
        println("Federation error: ${e.message}")
    }
}
```

### Exhaustive Error Handling

```kotlin
suspend fun exhaustiveErrorHandling() {
    try {
        val service = FederationService.fromDomain("stellar.org")
        val response = service.resolveStellarAddress("bob*stellar.org")

        // Process response
        println("Account ID: ${response.accountId}")
    } catch (e: Sep02Exception) {
        when (e) {
            is Sep02InvalidAddressException -> {
                // Address format validation failed
                println("Fix address format: ${e.message}")
            }
            is Sep02FederationNotFoundException -> {
                // Federation server not configured for this domain
                println("Domain does not support federation: ${e.message}")
            }
            is Sep02InvalidResponseException -> {
                // Server response malformed or HTTP error
                println("Server error: ${e.message}")
            }
            else -> {
                // Other SEP-2 errors
                println("Unexpected error: ${e.message}")
            }
        }
    }
}
```

**Exception Types**:
- `Sep02InvalidAddressException` - Address format validation failed (missing `*`, empty parts, multiple `*`)
- `Sep02FederationNotFoundException` - stellar.toml does not contain FEDERATION_SERVER field
- `Sep02InvalidResponseException` - Malformed server response, missing required fields, or HTTP errors
- `Sep02Exception` - Base exception for other errors

## Browser Compatibility

Federation servers must set the following HTTP header to enable CORS:

```
Access-Control-Allow-Origin: *
```

Without this header, browser-based applications will not be able to query the federation server due to same-origin policy restrictions. If you encounter CORS errors when resolving addresses from a browser, contact the federation server administrator to configure CORS properly.

## Complete Integration Example

Full workflow demonstrating address resolution and payment submission:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep02.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun completeExample() {
    // 1. Resolve the Stellar address
    val address = "bob*stellar.org"
    val federationResponse = try {
        FederationService.resolveStellarAddress(address)
    } catch (e: Sep02InvalidAddressException) {
        println("Invalid address format: ${e.message}")
        return
    } catch (e: Sep02FederationNotFoundException) {
        println("Federation server not found: ${e.message}")
        return
    } catch (e: Sep02InvalidResponseException) {
        println("Server error: ${e.message}")
        return
    }

    println("Resolved $address to ${federationResponse.accountId}")

    // 2. Check memo requirements
    if (federationResponse.memoType != null && federationResponse.memo != null) {
        println("Memo required: ${federationResponse.memoType} = ${federationResponse.memo}")
    }

    // 3. Build the payment transaction
    val server = HorizonServer("https://horizon.stellar.org")
    val senderKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")
    val sourceAccount = server.accounts().account(senderKeyPair.getAccountId())

    val txBuilder = TransactionBuilder(sourceAccount, Network.PUBLIC)
        .addOperation(
            PaymentOperation(
                destination = federationResponse.accountId,
                asset = AssetTypeNative(),
                amount = "10"
            )
        )

    // 4. Attach memo if required
    if (federationResponse.memoType != null && federationResponse.memo != null) {
        when (federationResponse.memoType) {
            "text" -> txBuilder.addMemo(MemoText(federationResponse.memo))
            "id" -> txBuilder.addMemo(MemoId(federationResponse.memo.toULong()))
            "hash" -> {
                val bytes = kotlin.io.encoding.Base64.decode(federationResponse.memo)
                txBuilder.addMemo(MemoHash(bytes))
            }
            else -> {
                println("Unknown memo type: ${federationResponse.memoType}")
                return
            }
        }
    }

    // 5. Sign and submit the transaction
    val transaction = txBuilder.build()
    transaction.sign(senderKeyPair)

    val submitResponse = server.submitTransaction(transaction.toEnvelopeXdrBase64())
    println("Payment sent! Transaction hash: ${submitResponse.hash}")
}
```

## API Reference

**Main Class**:
- `FederationService` - SEP-2 federation protocol client

**Factory Methods**:
- `FederationService.fromDomain(domain, httpClient?, httpRequestHeaders?)` - Initialize from stellar.toml
- `FederationService.resolveStellarAddress(address, httpClient?, httpRequestHeaders?)` - Static resolution

**Constructor**:
- `FederationService(federationServerUrl, httpClient?, httpRequestHeaders?)` - Direct initialization

**Methods**:
- `resolveStellarAddress(address)` - Resolve Stellar address to account information
- `resolveAccountId(accountId)` - Reverse lookup: account ID to Stellar address
- `resolveTransactionId(txId)` - Look up sender from transaction hash
- `resolveForward(forwardParams)` - Forward resolution with custom parameters

**Static Methods**:
- `FederationService.parseAddress(address)` - Parse and validate Stellar address format

**Properties**:
- `federationServerUrl` - Federation server endpoint URL

**Response Class**:
- `FederationResponse` - Federation query result
  - `stellarAddress` - Resolved Stellar address (optional)
  - `accountId` - Stellar account ID (required)
  - `memoType` - Memo type: "text", "id", or "hash" (optional)
  - `memo` - Memo value as string (optional, always string even for "id" type)

**Exception Types**:
- `Sep02Exception` - Base exception
- `Sep02InvalidAddressException` - Invalid address format
- `Sep02FederationNotFoundException` - Federation server not found in stellar.toml
- `Sep02InvalidResponseException` - Malformed server response or HTTP error

**Specification**: [SEP-2: Federation Protocol](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep02`

**Last Updated**: 2026-02-13
