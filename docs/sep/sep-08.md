# SEP-8: Regulated Assets

**[SEP-0008 Compatibility Matrix](../../compatibility/sep/SEP-0008_COMPATIBILITY_MATRIX.md)** - Full implementation coverage details

SEP-8 defines a protocol for assets that require issuer approval before transactions can be submitted to the Stellar network. The issuer publishes an approval server URL in their stellar.toml, and all transactions involving the regulated asset must be submitted to this server for authorization.

**Use Cases**:
- Securities tokens where a transfer agent must validate each trade
- Stablecoins subject to sanctions screening or geographic restrictions
- Assets with daily transfer limits or tiered access controls

## Quick Start

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep08.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun regulatedAssetExample() {
    // Initialize from issuer's domain
    val sep08 = Sep08Service.fromDomain("issuer.example.com")

    // Discover regulated assets
    sep08.regulatedAssets.forEach { asset ->
        println("${asset.code}:${asset.issuer}")
        println("Approval server: ${asset.approvalServer}")
        asset.approvalCriteria?.let { println("Criteria: $it") }
    }

    // Check issuer authorization flags
    val asset = sep08.regulatedAssets.first()
    val isRegulated = sep08.authorizationRequired(asset)
    println("Authorization required: $isRegulated")

    // Build a payment transaction involving the regulated asset
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val senderKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")
    val sourceAccount = server.accounts().account(senderKeyPair.getAccountId())
    val destination = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"

    val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(
            PaymentOperation(destination, asset.toAsset(), "100")
        )
        .build()
    transaction.sign(senderKeyPair)

    // Submit to approval server
    val response = sep08.postTransaction(
        tx = transaction.toEnvelopeXdrBase64(),
        approvalServer = asset.approvalServer
    )

    // Handle response
    when (response) {
        is Sep08PostTransactionResponse.Success -> {
            println("Approved: ${response.message}")
            // Submit response.tx to the Stellar network
        }
        is Sep08PostTransactionResponse.Revised -> {
            println("Revised: ${response.message}")
            // Review and sign the revised transaction, then submit
        }
        is Sep08PostTransactionResponse.Pending -> {
            println("Pending - retry after ${response.timeout} milliseconds")
        }
        is Sep08PostTransactionResponse.ActionRequired -> {
            println("Action required: ${response.message}")
            println("URL: ${response.actionUrl}")
        }
        is Sep08PostTransactionResponse.Rejected -> {
            println("Rejected: ${response.error}")
        }
    }
}
```

## Service Initialization

### From Domain (Recommended)

Discovers regulated assets and network configuration from the issuer's stellar.toml.

```kotlin
// In a coroutine scope
val sep08 = Sep08Service.fromDomain("issuer.example.com")
```

The service resolves the network and Horizon URL in this order:
1. Explicit parameters if provided
2. `NETWORK_PASSPHRASE` and `HORIZON_URL` from stellar.toml
3. Default Horizon URL for known networks (public, testnet, futurenet)

### With Explicit Configuration

```kotlin
// In a coroutine scope
val sep08 = Sep08Service.fromDomain(
    domain = "issuer.example.com",
    horizonUrl = "https://horizon-testnet.stellar.org",
    network = Network.TESTNET
)
```

### Direct Constructor

When you already have the stellar.toml data and regulated asset list:

```kotlin
val sep08 = Sep08Service(
    tomlData = stellarToml,
    regulatedAssets = listOf(regulatedAsset),
    network = Network.TESTNET,
    horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
)
```

## Regulated Asset Discovery

The service extracts regulated assets from the `[[CURRENCIES]]` entries in stellar.toml. An asset is considered regulated when it has `regulated = true`, a `code`, an `issuer`, and an `approval_server` defined.

```kotlin
// In a coroutine scope
val sep08 = Sep08Service.fromDomain("issuer.example.com")

sep08.regulatedAssets.forEach { asset ->
    println("Code: ${asset.code}")
    println("Issuer: ${asset.issuer}")
    println("Approval server: ${asset.approvalServer}")
    asset.approvalCriteria?.let { println("Criteria: $it") }
    println("Asset type: ${asset.type}")
}

// Use the regulated asset in SDK operations
val asset = sep08.regulatedAssets.first()
val stellarAsset = asset.toAsset()  // Returns Asset (AlphaNum4 or AlphaNum12)
val assetXdr = asset.toXdr()        // Returns AssetXdr
```

## Authorization Check

Verify that the issuer account has both `auth_required` and `auth_revocable` flags set.

```kotlin
// In a coroutine scope
val asset = sep08.regulatedAssets.first()
val isConfigured = sep08.authorizationRequired(asset)

if (isConfigured) {
    println("${asset.code} requires transaction approval via ${asset.approvalServer}")
} else {
    println("${asset.code} issuer flags not properly configured for SEP-8")
}
```

- `auth_required` ensures all trustlines must be approved by the issuer
- `auth_revocable` allows the issuer to revoke authorization when needed

## Transaction Approval Flow

Build a transaction involving the regulated asset, sign it, and submit it to the approval server.

```kotlin
suspend fun approvalFlowExample() {
    val sep08 = Sep08Service.fromDomain("issuer.example.com")
    val asset = sep08.regulatedAssets.first()

    // Build the transaction
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val senderKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")
    val sourceAccount = server.accounts().account(senderKeyPair.getAccountId())
    val destination = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"

    val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(
            PaymentOperation(destination, asset.toAsset(), "100")
        )
        .build()
    transaction.sign(senderKeyPair)

    // Submit to the approval server
    val response = sep08.postTransaction(
        tx = transaction.toEnvelopeXdrBase64(),
        approvalServer = asset.approvalServer
    )
}
```

## Handling Responses

The approval server returns one of five response types. Use a `when` expression to handle each one.

### Success

The transaction was approved without modifications. Submit `response.tx` to the Stellar network.

```kotlin
when (response) {
    is Sep08PostTransactionResponse.Success -> {
        // response.tx contains the approved (possibly co-signed) transaction XDR
        response.message?.let { println("Message: $it") }

        // Submit to Stellar network (response.tx is base64-encoded XDR)
        val txResponse = server.submitTransaction(response.tx)
        println("Submitted: ${txResponse.hash}")
    }
    // ... other branches
}
```

### Revised

The approval server modified the transaction (e.g., added compliance operations). Review the changes, sign the revised transaction, and submit it.

```kotlin
is Sep08PostTransactionResponse.Revised -> {
    println("Revised: ${response.message}")
    // response.tx contains the modified transaction XDR
    // Review the changes, sign, and submit to the network
}
```

### Pending

The approval server needs more time. Wait and resubmit.

```kotlin
is Sep08PostTransactionResponse.Pending -> {
    println("Pending - retry after ${response.timeout} milliseconds")
    response.message?.let { println("Message: $it") }
    // Wait response.timeout milliseconds, then resubmit the same transaction
}
```

### Action Required

The user must complete an action (e.g., KYC) before the transaction can be approved.

```kotlin
is Sep08PostTransactionResponse.ActionRequired -> {
    println("Action required: ${response.message}")
    println("URL: ${response.actionUrl}")
    println("Method: ${response.actionMethod}")  // "GET" or "POST"
    response.actionFields?.let { fields ->
        println("Required fields: ${fields.joinToString()}")
    }
}
```

### Rejected

The transaction was rejected and cannot be approved.

```kotlin
is Sep08PostTransactionResponse.Rejected -> {
    println("Rejected: ${response.error}")
}
```

### Exhaustive Handling

```kotlin
when (response) {
    is Sep08PostTransactionResponse.Success -> {
        server.submitTransaction(response.tx)
    }
    is Sep08PostTransactionResponse.Revised -> {
        println("Revised: ${response.message}")
        // Review, sign, and submit response.tx
    }
    is Sep08PostTransactionResponse.Pending -> {
        delay(response.timeout.toLong())
        // Resubmit the original transaction
    }
    is Sep08PostTransactionResponse.ActionRequired -> {
        // See Action URL Handling section below
        println("Action required: ${response.message}")
        println("URL: ${response.actionUrl}")
    }
    is Sep08PostTransactionResponse.Rejected -> {
        println("Rejected: ${response.error}")
    }
}
```

## Action URL Handling

### GET Actions (Browser-Based)

When `actionMethod` is `"GET"` (the default), open the URL in a browser or webview. After the user completes the action (e.g., a KYC form), resubmit the original transaction.

```kotlin
// In a coroutine scope
val actionRequired = response as Sep08PostTransactionResponse.ActionRequired

if (actionRequired.actionMethod == "GET") {
    // Open actionRequired.actionUrl in a browser or webview
    println("Direct user to: ${actionRequired.actionUrl}")

    // After the user completes the action, resubmit the transaction
    val retryResponse = sep08.postTransaction(
        tx = transaction.toEnvelopeXdrBase64(),
        approvalServer = asset.approvalServer
    )
}
```

### POST Actions (Programmatic)

When `actionMethod` is `"POST"`, submit the required fields programmatically using `postAction()`.

```kotlin
// In a coroutine scope
val actionRequired = response as Sep08PostTransactionResponse.ActionRequired

if (actionRequired.actionMethod == "POST") {
    val actionResponse = sep08.postAction(
        url = actionRequired.actionUrl,
        actionFields = mapOf(
            "email_address" to "user@example.com",
            "mobile_number" to "+1234567890"
        )
    )

    when (actionResponse) {
        is Sep08PostActionResponse.Done -> {
            // Action complete - resubmit the original transaction
            val retryResponse = sep08.postTransaction(
                tx = transaction.toEnvelopeXdrBase64(),
                approvalServer = asset.approvalServer
            )
        }
        is Sep08PostActionResponse.NextUrl -> {
            // Multi-step flow - direct user to the next URL
            println("Next step: ${actionResponse.nextUrl}")
            actionResponse.message?.let { println("Message: $it") }
        }
    }
}
```

## Issuer Configuration

For asset issuers configuring their accounts for SEP-8, the `AUTH_REQUIRED` and `AUTH_REVOCABLE` flags must be set using `SetOptionsOperation`.

```kotlin
import com.soneso.stellar.sdk.xdr.AccountFlagsXdr

suspend fun configureIssuer() {
    val issuerKeyPair = KeyPair.fromSecretSeed("SISSUER...")
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val issuerAccount = server.accounts().account(issuerKeyPair.getAccountId())

    val setFlagsTransaction = TransactionBuilder(issuerAccount, Network.TESTNET)
        .addOperation(
            SetOptionsOperation(
                setFlags = AccountFlagsXdr.AUTH_REQUIRED_FLAG.value or
                    AccountFlagsXdr.AUTH_REVOCABLE_FLAG.value
            )
        )
        .build()

    setFlagsTransaction.sign(issuerKeyPair)
    server.submitTransaction(setFlagsTransaction.toEnvelopeXdrBase64())
}
```

The issuer must also publish the regulated asset in their stellar.toml with `regulated = true` and an `approval_server` URL.

## Error Handling

```kotlin
import com.soneso.stellar.sdk.sep.sep08.exceptions.*

suspend fun errorHandlingExample() {
    try {
        val sep08 = Sep08Service.fromDomain("issuer.example.com")
        val asset = sep08.regulatedAssets.first()
        val response = sep08.postTransaction(txXdr, asset.approvalServer)
    } catch (e: Sep08IncompleteInitDataException) {
        // Network passphrase or Horizon URL missing from stellar.toml
        println("Missing configuration: ${e.message}")
    } catch (e: Sep08InvalidTransactionResponseException) {
        // Malformed or unexpected response from the approval server
        println("Invalid approval server response: ${e.message}")
    } catch (e: Sep08InvalidActionResponseException) {
        // Malformed response from the action URL
        println("Invalid action response: ${e.message}")
    } catch (e: Sep08Exception) {
        // Base SEP-8 exception
        println("SEP-8 error: ${e.message}")
    }
}
```

## API Reference

**Main Class**:
- `Sep08Service` - SEP-8 Regulated Assets service client

**Factory Methods**:
- `Sep08Service.fromDomain(domain, horizonUrl?, network?)` - Initialize from stellar.toml

**Constructor**:
- `Sep08Service(tomlData, regulatedAssets, network, horizonServer)` - Direct initialization

**Methods**:
- `authorizationRequired(asset)` - Check if issuer has AUTH_REQUIRED and AUTH_REVOCABLE flags
- `postTransaction(tx, approvalServer)` - Submit transaction XDR to approval server
- `postAction(url, actionFields)` - Submit action fields to an action URL

**Properties**:
- `tomlData` - Parsed stellar.toml data (`StellarToml`)
- `regulatedAssets` - List of regulated assets discovered from stellar.toml (`List<RegulatedAsset>`)

**Data Classes**:
- `RegulatedAsset` - Regulated asset with code, issuer, approval server, and optional criteria
  - `code` - Asset code (1-12 characters)
  - `issuer` - Issuer account ID (G... address)
  - `approvalServer` - Approval server URL
  - `approvalCriteria` - Optional approval criteria description
  - `toAsset()` - Returns the underlying `Asset`
  - `toXdr()` - Returns `AssetXdr` representation
  - `type` - Asset type (`AssetTypeXdr`)

**Response Classes**:
- `Sep08PostTransactionResponse` (sealed class):
  - `Success(tx, message?)` - Approved transaction XDR
  - `Revised(tx, message)` - Modified transaction XDR with explanation
  - `Pending(timeout, message?)` - Retry after timeout milliseconds
  - `ActionRequired(message, actionUrl, actionMethod, actionFields?)` - User action needed
  - `Rejected(error)` - Transaction rejected with reason
- `Sep08PostActionResponse` (sealed class):
  - `Done` - Action complete, resubmit transaction
  - `NextUrl(nextUrl, message?)` - Multi-step flow, visit next URL

**Exception Types**:
- `Sep08Exception` - Base exception for SEP-8 errors
- `Sep08IncompleteInitDataException` - Missing network passphrase or Horizon URL
- `Sep08InvalidTransactionResponseException` - Malformed approval server response
- `Sep08InvalidActionResponseException` - Malformed action URL response

**Specification**: [SEP-8: Regulated Assets](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep08`

**Last Updated**: 2026-02-10
