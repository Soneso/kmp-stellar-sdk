# SEP-08: Regulated Assets

**Purpose:** Handle assets that require issuer approval for every transaction before submission to the Stellar network.
**Prerequisites:** None (but the asset issuer must have `AUTH_REQUIRED` and `AUTH_REVOCABLE` flags set)

SEP-08 defines a protocol for regulated assets — assets where an issuer-run approval server must evaluate and co-sign every transaction. This enables compliance with securities regulations, KYC/AML requirements, velocity limits, and jurisdiction-based restrictions.

**Spec:** [SEP-0008 v1.7.4](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep08.*
import com.soneso.stellar.sdk.sep.sep08.exceptions.*
```

## Table of Contents

1. [Quick Start](#1-quick-start)
2. [How Regulated Assets Work](#2-how-regulated-assets-work)
3. [Creating the Service](#3-creating-the-service)
4. [RegulatedAsset](#4-regulatedasset)
5. [Checking Authorization Flags](#5-checking-authorization-flags)
6. [postTransaction — Submitting for Approval](#6-posttransaction--submitting-for-approval)
7. [Handling All Response Types](#7-handling-all-response-types)
8. [postAction — Handling Action Required](#8-postaction--handling-action-required)
9. [Complete Workflow Example](#9-complete-workflow-example)
10. [Response Classes Reference](#10-response-classes-reference)
11. [Error Handling](#11-error-handling)
12. [Common Pitfalls](#12-common-pitfalls)

---

## 1. Quick Start

```kotlin
// destinationId: from the previous steps of this flow
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep08.Sep08Service
import com.soneso.stellar.sdk.sep.sep08.Sep08PostTransactionResponse

// Load stellar.toml from issuer domain — extracts regulated asset definitions
val sep08 = Sep08Service.fromDomain("regulated-asset-issuer.com")

// Access discovered regulated assets
val asset = sep08.regulatedAssets.first()
println("${asset.code} issued by ${asset.issuer}")
println("Approval server: ${asset.approvalServer}")

// Build and sign a transaction
val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
val senderAccount = horizonServer.loadAccount(senderKeyPair.getAccountId())

val tx = TransactionBuilder(senderAccount, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(
        PaymentOperation(
            destination = destinationId,
            asset = asset.toAsset(),  // RegulatedAsset -> Asset via toAsset()
            amount = "100"
        )
    )
    .setTimeout(180)
    .build()
tx.sign(senderKeyPair)

// Submit to approval server (NOT directly to Stellar network)
val response = sep08.postTransaction(
    tx = tx.toEnvelopeXdrBase64(),
    approvalServer = asset.approvalServer,
)

when (response) {
    is Sep08PostTransactionResponse.Success -> {
        // Approved — submit the returned signed tx to Stellar network
        horizonServer.submitTransaction(response.tx)
    }
    is Sep08PostTransactionResponse.Rejected -> {
        println("Rejected: ${response.error}")
    }
    else -> { /* handle other cases */ }
}
```

---

## 2. How Regulated Assets Work

Per SEP-08:

1. **Issuer flags**: The asset issuer account must have both `AUTH_REQUIRED` and `AUTH_REVOCABLE` flags set. This allows the issuer to grant and revoke authorization atomically.
2. **stellar.toml discovery**: The issuer's `stellar.toml` (SEP-01) lists assets with `regulated=true` and an `approval_server` URL. The toml **must** include `NETWORK_PASSPHRASE` for the service to initialize.
3. **Transaction composition**: Build and sign the transaction normally using the regulated asset. Do not add authorization operations yourself — the approval server handles that.
4. **Approval**: POST the signed transaction XDR envelope to the approval server (not to Stellar). The server evaluates compliance rules and returns one of five statuses.
5. **Network submission**: If approved (`success` or `revised`), submit the returned XDR to the Stellar network.

---

## 3. Creating the Service

### From domain (recommended)

Fetches `stellar.toml` from the domain's `/.well-known/stellar.toml`, parses it, and extracts all regulated asset definitions. Pass the bare domain — no protocol prefix.

```kotlin
import com.soneso.stellar.sdk.sep.sep08.Sep08Service
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08IncompleteInitDataException

try {
    val sep08 = Sep08Service.fromDomain("regulated-asset-issuer.com")
    println("Assets: ${sep08.regulatedAssets.size}")
} catch (e: Sep08IncompleteInitDataException) {
    println("Failed to initialize: ${e.message}")
}
```

`fromDomain()` signature:
```kotlin
suspend fun fromDomain(
    domain: String,                                // bare domain, no protocol
    horizonUrl: String? = null,                    // override Horizon URL (default: toml HORIZON_URL)
    network: Network? = null,                      // override network (default: toml NETWORK_PASSPHRASE)
    httpClient: HttpClient? = null,                // custom Ktor HTTP client
    httpRequestHeaders: Map<String, String>? = null // custom request headers
): Sep08Service
```

### From StellarToml data

If you have already loaded a `StellarToml` instance, you can construct the service manually. However, you must also provide the resolved `regulatedAssets` list, `network`, and `horizonServer` — the constructor does **not** extract these from the toml automatically. Use `fromDomain()` instead unless you need full control.

```kotlin
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep08.RegulatedAsset
import com.soneso.stellar.sdk.sep.sep08.Sep08Service

val toml = StellarToml.fromDomain("regulated-asset-issuer.com")
val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")

// You must build the regulated assets list yourself
val regulatedAssets = toml.currencies
    ?.filter { it.regulated == true && it.code != null && it.issuer != null && it.approvalServer != null }
    ?.map { RegulatedAsset(code = it.code!!, issuer = it.issuer!!, approvalServer = it.approvalServer!!, approvalCriteria = it.approvalCriteria) }
    ?: emptyList()

val sep08 = Sep08Service(
    tomlData = toml,
    regulatedAssets = regulatedAssets,
    network = Network.TESTNET,
    horizonServer = horizonServer,
)
```

### With custom HTTP client and headers

Inject a custom Ktor `HttpClient` for timeouts, proxies, or SSL configuration. The same client handles both the stellar.toml fetch and all subsequent approval server requests:

```kotlin
import com.soneso.stellar.sdk.sep.sep08.Sep08Service
import io.ktor.client.*

val client = HttpClient { /* configure timeouts, etc. */ }

val sep08Explicit = Sep08Service.fromDomain(
    domain = "regulated-asset-issuer.com",
    httpClient = client,
    httpRequestHeaders = mapOf("User-Agent" to "MyWallet/1.0"),
)
```

---

## 4. RegulatedAsset

`RegulatedAsset` wraps a standard Stellar `Asset` and adds approval server information specific to SEP-08. It is **not** a subclass of `Asset` — use `toAsset()` to get the underlying `Asset` for SDK methods that require one.

```kotlin
import com.soneso.stellar.sdk.sep.sep08.Sep08Service
import com.soneso.stellar.sdk.sep.sep08.RegulatedAsset

val sep08 = Sep08Service.fromDomain("regulated-asset-issuer.com")

for (asset: RegulatedAsset in sep08.regulatedAssets) {
    // RegulatedAsset properties
    asset.code              // String  e.g. "GOAT"
    asset.issuer            // String  G... issuer account ID
    asset.approvalServer    // String  full URL of approval server endpoint
    asset.approvalCriteria  // String? human-readable compliance description (may be null)

    // Convert to SDK Asset for use in operations
    asset.toAsset()         // Asset (AssetTypeCreditAlphaNum4 or AssetTypeCreditAlphaNum12)
    asset.toXdr()           // AssetXdr
    asset.type              // AssetTypeXdr
    asset.toString()        // "CODE:ISSUER"
}
```

Assets are extracted from `stellar.toml` currencies where `regulated == true`, `code != null`, `issuer != null`, and `approvalServer != null`. Entries missing any of these are silently skipped and will not appear in `sep08.regulatedAssets`.

```kotlin
// WRONG: RegulatedAsset does NOT extend Asset — cannot pass it directly where Asset is expected
// PaymentOperation(destination = dest, asset = regulatedAsset, amount = "100") // compile error

// CORRECT: use toAsset() to get the underlying Asset
PaymentOperation(destination = dest, asset = regulatedAsset.toAsset(), amount = "100")
```

---

## 5. Checking Authorization Flags

Before transacting, verify the issuer account has the required flags. Per SEP-08, regulated asset issuers must have both `AUTH_REQUIRED` and `AUTH_REVOCABLE` set:

```kotlin
import com.soneso.stellar.sdk.sep.sep08.Sep08Service

val sep08 = Sep08Service.fromDomain("regulated-asset-issuer.com")
val asset = sep08.regulatedAssets.first()

try {
    val required = sep08.authorizationRequired(asset)
    if (!required) {
        println("Warning: issuer not properly configured for regulated assets")
    }
} catch (e: Exception) {
    // Horizon exceptions (BadRequestException, NetworkException, etc.) if issuer account not found
    println("Could not verify issuer flags: ${e.message}")
}
```

`authorizationRequired()` loads the issuer account from Horizon and checks both `authRequired` and `authRevocable` flags. Returns `true` only when both are set. Throws Horizon exceptions (e.g., `BadRequestException`) if the issuer account does not exist on the network.

---

## 6. postTransaction — Submitting for Approval

Build and sign a transaction normally, then submit the base64-encoded XDR envelope to the approval server. Do not submit directly to Stellar — the approval server must co-sign first.

```kotlin
// destinationId, getAccountId, senderSeed: from the previous steps of this flow
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep08.Sep08Service

val sep08 = Sep08Service.fromDomain("regulated-asset-issuer.com")
val regulatedAsset = sep08.regulatedAssets.first()

val senderKeyPair = KeyPair.fromSecretSeed(senderSeed)
val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
val senderAccount = horizonServer.loadAccount(senderKeyPair.getAccountId())

val tx = TransactionBuilder(senderAccount, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(
        PaymentOperation(
            destination = destinationId,
            asset = regulatedAsset.toAsset(),
            amount = "100"
        )
    )
    .setTimeout(180)
    .build()
tx.sign(senderKeyPair)

// Submit to approval server
val response = sep08.postTransaction(
    tx = tx.toEnvelopeXdrBase64(),
    approvalServer = regulatedAsset.approvalServer,
)
```

`postTransaction()` signature:
```kotlin
suspend fun postTransaction(
    tx: String,              // base64-encoded XDR transaction envelope
    approvalServer: String   // full URL from asset.approvalServer
): Sep08PostTransactionResponse
```

Sends a POST with `Content-Type: application/json` and body `{"tx": "<base64>"}`. Returns a `Sep08PostTransactionResponse` sealed class variant. Throws `Sep08InvalidTransactionResponseException` for HTTP codes other than 200, or 400 without a `"rejected"` status.

---

## 7. Handling All Response Types

The approval server returns one of five response types. Use Kotlin `when` with sealed class matching:

```kotlin
// sep08: from the previous steps of this flow
import com.soneso.stellar.sdk.sep.sep08.Sep08PostTransactionResponse
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.delay
val approvalServer = "https://approval.example.com"

val response = sep08.postTransaction(txXdr, approvalServer)
val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")

when (response) {
    is Sep08PostTransactionResponse.Success -> {
        // Approved without modification — submit returned tx to Stellar network
        response.message?.let { println("Approval message: $it") }  // String?
        horizonServer.submitTransaction(response.tx)
    }

    is Sep08PostTransactionResponse.Revised -> {
        // Transaction was modified for compliance — review before submitting
        // message is REQUIRED (String, never null) for revised
        println("Revised: ${response.message}")
        // WARNING: inspect response.tx vs original — server may have added operations
        horizonServer.submitTransaction(response.tx)
    }

    is Sep08PostTransactionResponse.Pending -> {
        // Approval delayed — retry after timeout milliseconds
        // timeout is Int, defaults to 0 if the server did not provide a value
        val timeoutMs = response.timeout  // Int — milliseconds (0 means unknown)
        if (timeoutMs > 0) {
            println("Retry in ${timeoutMs / 1000} seconds")
            delay(timeoutMs.toLong())
        }
        response.message?.let { println("Message: $it") }  // String?
        // Resubmit the SAME txXdr unchanged after waiting
    }

    is Sep08PostTransactionResponse.ActionRequired -> {
        // User must complete an action before approval — see postAction section
        println("Action required: ${response.message}")       // String (required)
        println("Action URL: ${response.actionUrl}")           // String (required)
        println("Method: ${response.actionMethod}")            // String — "GET" or "POST", defaults to "GET"
        response.actionFields?.let { fields ->
            // List<String> of SEP-9 field names the server is requesting
            println("Fields: ${fields.joinToString(", ")}")
        }
    }

    is Sep08PostTransactionResponse.Rejected -> {
        // Cannot be made compliant — do not retry without addressing the issue
        println("Rejected: ${response.error}")  // String (required)
    }
}
```

### Response summary table

| Class | HTTP | Status value | Key fields |
|---|---|---|---|
| `Sep08PostTransactionResponse.Success` | 200 | `"success"` | `val tx: String`, `val message: String?` |
| `Sep08PostTransactionResponse.Revised` | 200 | `"revised"` | `val tx: String`, `val message: String` |
| `Sep08PostTransactionResponse.Pending` | 200 | `"pending"` | `val timeout: Int` (ms, default 0), `val message: String?` |
| `Sep08PostTransactionResponse.ActionRequired` | 200 | `"action_required"` | `val message: String`, `val actionUrl: String`, `val actionMethod: String` (default `"GET"`), `val actionFields: List<String>?` |
| `Sep08PostTransactionResponse.Rejected` | 400 | `"rejected"` | `val error: String` |

---

## 8. postAction — Handling Action Required

When the server returns `ActionRequired` with `actionMethod == "POST"`, you can programmatically submit the requested SEP-9 fields:

```kotlin
// sep08: from the previous steps of this flow
import com.soneso.stellar.sdk.sep.sep08.Sep08PostTransactionResponse
import com.soneso.stellar.sdk.sep.sep08.Sep08PostActionResponse
val approvalServer = "https://approval.example.com"

val response = sep08.postTransaction(txXdr, approvalServer)

if (response is Sep08PostTransactionResponse.ActionRequired) {
    println("Action required: ${response.message}")

    if (response.actionMethod == "POST") {
        // Wallet has the required fields — submit them programmatically
        val actionResponse = sep08.postAction(
            url = response.actionUrl,
            actionFields = mapOf(
                "email_address" to "user@example.com",
                "mobile_number" to "+1234567890",
            ),
        )

        when (actionResponse) {
            is Sep08PostActionResponse.Done -> {
                // Action complete — resubmit the ORIGINAL transaction unchanged
                val retryResponse = sep08.postTransaction(txXdr, approvalServer)
                // Handle retryResponse (likely Success or Revised now)
            }
            is Sep08PostActionResponse.NextUrl -> {
                // More steps needed — user must complete action in browser
                println("Open in browser: ${actionResponse.nextUrl}")  // String
                actionResponse.message?.let { println("Message: $it") }  // String?
                // After user completes, resubmit original txXdr
            }
        }

    } else {
        // actionMethod is "GET" (or server did not specify — defaults to "GET")
        // Direct user to open the URL in a browser
        println("Open URL in browser: ${response.actionUrl}")
        // After user completes the action, resubmit txXdr unchanged
    }
}
```

`postAction()` signature:
```kotlin
suspend fun postAction(
    url: String,                          // action_url from ActionRequired response
    actionFields: Map<String, String>     // SEP-9 field names and values
): Sep08PostActionResponse
```

Sends a POST with `Content-Type: application/json` and body containing the action fields as JSON. Returns either `Sep08PostActionResponse.Done` or `Sep08PostActionResponse.NextUrl`. Throws `Sep08InvalidActionResponseException` for non-200 HTTP responses or malformed JSON.

### postAction response types

| Class | Result value | Key fields |
|---|---|---|
| `Sep08PostActionResponse.Done` | `"no_further_action_required"` | (data object — no properties; resubmit original tx) |
| `Sep08PostActionResponse.NextUrl` | `"follow_next_url"` | `val nextUrl: String`, `val message: String?` |

---

## 9. Complete Workflow Example

Full flow including all response types, error handling, and network submission:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep08.Sep08Service
import com.soneso.stellar.sdk.sep.sep08.Sep08PostTransactionResponse
import com.soneso.stellar.sdk.sep.sep08.Sep08PostActionResponse
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08IncompleteInitDataException
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08InvalidTransactionResponseException
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08InvalidActionResponseException
import kotlinx.coroutines.delay
val server = HorizonServer("https://horizon-testnet.stellar.org")

suspend fun sendRegulatedAssetPayment(
    domain: String,
    senderSeed: String,
    destinationId: String,
    amount: String,
) {
    // Step 1: Initialize service from issuer's stellar.toml
    val sep08: Sep08Service
    try {
        sep08 = Sep08Service.fromDomain(domain)
    } catch (e: Sep08IncompleteInitDataException) {
        throw Exception("Failed to load stellar.toml from $domain: ${e.message}")
    }

    if (sep08.regulatedAssets.isEmpty()) {
        throw Exception("No regulated assets found in stellar.toml")
    }

    val regulatedAsset = sep08.regulatedAssets.first()
    println("Using asset: ${regulatedAsset.code} / ${regulatedAsset.issuer}")
    regulatedAsset.approvalCriteria?.let { println("Criteria: $it") }

    // Step 2: Verify issuer is properly configured
    try {
        val authRequired = sep08.authorizationRequired(regulatedAsset)
        if (!authRequired) {
            println("Warning: issuer account does not have AUTH_REQUIRED + AUTH_REVOCABLE set")
        }
    } catch (e: Exception) {
        println("Warning: could not verify issuer flags: ${e.message}")
    }

    // Step 3: Build and sign the transaction
    val senderKeyPair = KeyPair.fromSecretSeed(senderSeed)
    val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
    val senderAccount = horizonServer.loadAccount(senderKeyPair.getAccountId())

    val tx = TransactionBuilder(senderAccount, Network.TESTNET)
        .setBaseFee(100)
        .addOperation(
            PaymentOperation(
                destination = destinationId,
                asset = regulatedAsset.toAsset(),
                amount = amount
            )
        )
        .setTimeout(180)
        .build()
    tx.sign(senderKeyPair)
    val txXdr = tx.toEnvelopeXdrBase64()

    // Step 4: Submit for approval and handle all response types
    var approvedTxXdr: String? = null

    try {
        val response = sep08.postTransaction(txXdr, regulatedAsset.approvalServer)

        when (response) {
            is Sep08PostTransactionResponse.Success -> {
                println("Approved")
                response.message?.let { println("Message: $it") }
                approvedTxXdr = response.tx
            }

            is Sep08PostTransactionResponse.Revised -> {
                // Transaction was modified — review what changed
                println("Revised: ${response.message}")
                approvedTxXdr = response.tx
            }

            is Sep08PostTransactionResponse.Pending -> {
                val waitMs = response.timeout  // Int, 0 = unknown
                println(if (waitMs > 0) "Pending. Retry in ${waitMs}ms" else "Pending. Retry after a moment")
                response.message?.let { println("Message: $it") }
                // Resubmit txXdr unchanged after waiting
                return
            }

            is Sep08PostTransactionResponse.ActionRequired -> {
                println("Action required: ${response.message}")

                if (response.actionMethod == "POST") {
                    val actionResponse = sep08.postAction(
                        url = response.actionUrl,
                        actionFields = mapOf("email_address" to "user@example.com"),
                    )

                    when (actionResponse) {
                        is Sep08PostActionResponse.Done -> {
                            // Resubmit original transaction — not the action response
                            val retry = sep08.postTransaction(txXdr, regulatedAsset.approvalServer)
                            when (retry) {
                                is Sep08PostTransactionResponse.Success -> approvedTxXdr = retry.tx
                                is Sep08PostTransactionResponse.Revised -> approvedTxXdr = retry.tx
                                else -> {
                                    println("Unexpected response after action: $retry")
                                    return
                                }
                            }
                        }
                        is Sep08PostActionResponse.NextUrl -> {
                            println("Complete action in browser: ${actionResponse.nextUrl}")
                            actionResponse.message?.let { println(it) }
                            return
                        }
                    }

                } else {
                    // GET — direct user to the browser
                    println("Open in browser: ${response.actionUrl}")
                    return
                }
            }

            is Sep08PostTransactionResponse.Rejected -> {
                throw Exception("Transaction rejected: ${response.error}")
            }
        }

    } catch (e: Sep08InvalidTransactionResponseException) {
        throw Exception("Approval server error: ${e.message}")
    } catch (e: Sep08InvalidActionResponseException) {
        throw Exception("Action endpoint error: ${e.message}")
    }

    // Step 5: Submit approved transaction to Stellar network
    if (approvedTxXdr != null) {
        val result = horizonServer.submitTransaction(approvedTxXdr)
        if (result.successful) {
            println("Submitted: ${result.hash}")
        } else {
            println("Submission failed")
        }
    }
}
```

---

## 10. Response Classes Reference

### Sep08PostTransactionResponse.Success

```kotlin
data class Success(
    val tx: String,           // Base64 XDR envelope — contains original + issuer signatures
    val message: String? = null  // Optional human-readable info for the user
) : Sep08PostTransactionResponse()
```

### Sep08PostTransactionResponse.Revised

```kotlin
data class Revised(
    val tx: String,       // Base64 XDR of revised, issuer-signed transaction
    val message: String   // Required explanation of what was changed (never null)
) : Sep08PostTransactionResponse()
```

### Sep08PostTransactionResponse.Pending

```kotlin
data class Pending(
    val timeout: Int = 0,      // Milliseconds to wait before retrying; 0 = unknown
    val message: String? = null  // Optional human-readable info
) : Sep08PostTransactionResponse()
```

### Sep08PostTransactionResponse.ActionRequired

```kotlin
data class ActionRequired(
    val message: String,                  // Required description of the action needed
    val actionUrl: String,                // URL for completing the action
    val actionMethod: String = "GET",     // "GET" or "POST" — defaults to "GET"
    val actionFields: List<String>? = null  // SEP-9 field names the server requests, or null
) : Sep08PostTransactionResponse()
```

### Sep08PostTransactionResponse.Rejected

```kotlin
data class Rejected(
    val error: String  // Human-readable rejection reason (never null)
) : Sep08PostTransactionResponse()
```

### Sep08PostActionResponse.Done

```kotlin
data object Done : Sep08PostActionResponse()
// No properties — data object signals "no further action required"
// After receiving this, resubmit the original transaction via postTransaction()
```

### Sep08PostActionResponse.NextUrl

```kotlin
data class NextUrl(
    val nextUrl: String,        // URL where user completes remaining steps in browser
    val message: String? = null   // Optional human-readable info
) : Sep08PostActionResponse()
```

---

## 11. Error Handling

```kotlin
// txXdr: from the previous steps of this flow
import com.soneso.stellar.sdk.sep.sep08.Sep08Service
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08Exception
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08IncompleteInitDataException
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08InvalidTransactionResponseException
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08InvalidActionResponseException

try {
    val sep08 = Sep08Service.fromDomain("regulated-asset-issuer.com")
    val response = sep08.postTransaction(txXdr, approvalServer)
    // handle response types...

} catch (e: Sep08IncompleteInitDataException) {
    // stellar.toml missing NETWORK_PASSPHRASE (or custom network with no HORIZON_URL)
    println("stellar.toml incomplete: ${e.message}")

} catch (e: Sep08InvalidTransactionResponseException) {
    // Approval server returned unexpected HTTP code, or malformed/missing JSON fields,
    // or an unrecognized status value
    println("Approval server error: ${e.message}")

} catch (e: Sep08InvalidActionResponseException) {
    // Action endpoint returned non-200 HTTP response, or malformed/missing JSON fields,
    // or an unrecognized result value
    println("Action endpoint error: ${e.message}")

} catch (e: Sep08Exception) {
    // Base class — catches any SEP-08 exception not caught above
    println("SEP-08 error: ${e.message}")

} catch (e: Exception) {
    // stellar.toml fetch failed, network error, or other unexpected error
    println("Error: ${e.message}")
}
```

### Exception reference

| Exception | Thrown by | Trigger |
|---|---|---|
| `Sep08IncompleteInitDataException` | `fromDomain()`, constructor | `stellar.toml` missing `NETWORK_PASSPHRASE`, or custom network with no resolvable Horizon URL |
| `Sep08InvalidTransactionResponseException` | `postTransaction()` | HTTP code other than 200 (or 400 with `"rejected"` status); malformed JSON; missing required fields; unknown `status` value |
| `Sep08InvalidActionResponseException` | `postAction()` | Non-200 HTTP response; malformed JSON; missing required fields; unknown `result` value |
| `Sep08Exception` | (base class) | Parent of all SEP-08 exceptions |

---

## 12. Common Pitfalls

**Wrong: passing `RegulatedAsset` directly where `Asset` is expected**

```kotlin
// dest, regulatedAsset: from the previous steps of this flow
// WRONG: RegulatedAsset does NOT extend Asset — it wraps one internally
PaymentOperation(destination = dest, asset = regulatedAsset, amount = "100")  // compile error

// CORRECT: use toAsset() to get the underlying Asset
PaymentOperation(destination = dest, asset = regulatedAsset.toAsset(), amount = "100")
```

**Wrong: calling `toXdrBase64()` instead of `toEnvelopeXdrBase64()`**

```kotlin
// WRONG: there is no toXdrBase64() on Transaction — use toEnvelopeXdrBase64()
val txXdr = tx.toXdrBase64()  // compile error

// CORRECT: toEnvelopeXdrBase64() serializes the full signed envelope
val txXdr = tx.toEnvelopeXdrBase64()
```

**Wrong: submitting to Stellar network before getting approval**

```kotlin
// asset, horizonServer, sep08: from the previous steps of this flow
// WRONG: submitting directly bypasses the approval server entirely
horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())

// CORRECT: submit to approval server first, then submit the RETURNED transaction
val response = sep08.postTransaction(tx.toEnvelopeXdrBase64(), asset.approvalServer)
if (response is Sep08PostTransactionResponse.Success) {
    // Submit the approval server's returned tx, not the original
    horizonServer.submitTransaction(response.tx)
}
```

**Wrong: `Pending.timeout` is milliseconds, not seconds**

```kotlin
// delay, response: from the previous steps of this flow
// WRONG: treating timeout as seconds
val pending = response as Sep08PostTransactionResponse.Pending
delay(pending.timeout.toLong() * 1000)  // waits 5,000,000ms if timeout=5000!

// CORRECT: timeout is already milliseconds
delay(pending.timeout.toLong())
```

**Wrong: `Pending.timeout` is `Int`, not `Int?`**

```kotlin
// delay, response: from the previous steps of this flow
// WRONG: null-checking timeout — it always defaults to 0 (Int), never null
if (response.timeout == null) { /* ... */ }  // compile warning — always false

// CORRECT: check for 0 to detect "unknown" wait time
if (response.timeout == 0) {
    // Server did not specify — use your own retry strategy
} else {
    delay(response.timeout.toLong())
}
```

**Wrong: `Revised.message` is a required `String`, not `String?`**

```kotlin
// WRONG: null-checking message on Revised (it is always set)
if (response is Sep08PostTransactionResponse.Revised) {
    if (response.message != null) { /* ... */ }  // redundant — always non-null
}

// NOTE: For Success, message IS nullable (String?)
// For Revised, message is always a String
if (response is Sep08PostTransactionResponse.Success) {
    response.message?.let { println(it) }  // correct — nullable here
}
```

**Wrong: `ActionRequired.actionMethod` defaults to `"GET"`, not `null`**

```kotlin
// fields, response, sep08: from the previous steps of this flow
// WRONG: checking for null — actionMethod always has a value (defaults to "GET")
if (response.actionMethod == null) { /* ... */ }  // compile warning — always false

// CORRECT: check for "POST" to use programmatic posting; otherwise use browser ("GET")
if (response.actionMethod == "POST") {
    val actionResponse = sep08.postAction(response.actionUrl, fields)
} else {
    // actionMethod is "GET" (or server omitted it, which also defaults to "GET")
    println("Open in browser: ${response.actionUrl}")
}
```

**Wrong: forgetting to resubmit the ORIGINAL transaction after `PostActionDone`**

```kotlin
// actionResponse, horizonServer, sep08: from the previous steps of this flow
val approvalServer = "https://approval.example.com"
// WRONG: Done is a data object with no properties — there is nothing to submit from it
if (actionResponse is Sep08PostActionResponse.Done) {
    horizonServer.submitTransaction(actionResponse.tx)  // compile error — no such property!
}

// CORRECT: resubmit the ORIGINAL txXdr via postTransaction() again
if (actionResponse is Sep08PostActionResponse.Done) {
    val retryResponse = sep08.postTransaction(txXdr, approvalServer)
    // handle retryResponse as usual
}
```

**Wrong: accessing `regulatedAssets` without checking it is non-empty**

```kotlin
// WRONG: will throw NoSuchElementException if stellar.toml has no qualifying regulated assets
val asset = sep08.regulatedAssets.first()

// A currency entry is skipped if any of these are missing: code, issuer, regulated=true, approval_server
// CORRECT: always check before accessing
if (sep08.regulatedAssets.isEmpty()) {
    throw Exception("No regulated assets found in stellar.toml")
}
```

**Wrong: accessing `issuer` as a method instead of a property**

```kotlin
// WRONG: there are no getter methods on RegulatedAsset
val issuer = asset.getIssuer()   // compile error
val code = asset.getCode()       // compile error

// CORRECT: direct property access
val issuer = asset.issuer  // String
val code = asset.code      // String
```

**Wrong: `postAction` takes `Map<String, String>`, not `Map<String, Any>`**

```kotlin
// WRONG: the parameter type is Map<String, String>, not Map<String, Any>
val fields: Map<String, Any> = mapOf("email_address" to "user@example.com")
sep08.postAction(url, fields)  // compile error

// CORRECT: values must be String
val fields: Map<String, String> = mapOf("email_address" to "user@example.com")
sep08.postAction(url, fields)
```

**Network resolution: explicit parameter vs stellar.toml**

```kotlin
// Pass network explicitly
val sep08 = Sep08Service.fromDomain(
    domain = "example.com",
    horizonUrl = "https://custom-horizon.example.com",
    network = Network.TESTNET,
)

// Or let the service read NETWORK_PASSPHRASE from stellar.toml
val sep08 = Sep08Service.fromDomain("example.com")

// Note: if network is not passed and stellar.toml lacks NETWORK_PASSPHRASE,
// throws Sep08IncompleteInitDataException
```
