# SEP-02: Federation Protocol

**Purpose:** Resolve human-readable Stellar addresses (`name*domain.com`) to account IDs and memo instructions; perform reverse lookups and forward routing.
**Prerequisites:** None (auto-discovers federation server via SEP-01 for address lookups)

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep02.*
import com.soneso.stellar.sdk.sep.sep02.exceptions.*
```

## Table of Contents

1. [Resolve Stellar Address (name lookup)](#1-resolve-stellar-address-name-lookup)
2. [Resolve Account ID (reverse lookup)](#2-resolve-account-id-reverse-lookup)
3. [Resolve Transaction ID (txid lookup)](#3-resolve-transaction-id-txid-lookup)
4. [Resolve Forward](#4-resolve-forward)
5. [FederationResponse Fields](#5-federationresponse-fields)
6. [Using the Memo in a Payment](#6-using-the-memo-in-a-payment)
7. [Custom HTTP Client](#7-custom-http-client)
8. [Testing with MockEngine](#8-testing-with-mockengine)
9. [Common Pitfalls](#9-common-pitfalls)

---

## 1. Resolve Stellar Address (name lookup)

`FederationService.resolveStellarAddress()` is a static suspend method. It accepts a
federation address in the format `name*domain.com`, automatically fetches the
domain's `stellar.toml` to discover the federation server, then performs a
`type=name` query.

The username portion can be a simple name, an email address
(`maria@gmail.com*domain.com`), or an E.164 phone number
(`+14155550100*domain.com`).

```kotlin
import com.soneso.stellar.sdk.sep.sep02.FederationService

// Resolve bob*soneso.com to a Stellar account ID
val response = FederationService.resolveStellarAddress("bob*soneso.com")

println(response.stellarAddress) // bob*soneso.com
println(response.accountId)      // GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI
println(response.memoType)       // text  (null if no memo required)
println(response.memo)           // hello memo text  (null if no memo required)
```

Throws:
- `Sep02InvalidAddressException` if the address does not contain `*`, has empty parts, or has multiple `*`
- `Sep02FederationNotFoundException` if the domain's `stellar.toml` has no `FEDERATION_SERVER` entry
- `Sep02InvalidResponseException` if the HTTP request fails, the server returns a non-2xx status, or the response JSON is malformed

```kotlin
import com.soneso.stellar.sdk.sep.sep02.FederationService
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02InvalidAddressException
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02FederationNotFoundException
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02InvalidResponseException

try {
    val response = FederationService.resolveStellarAddress("bob*example.com")
    println("Account: ${response.accountId}")
} catch (e: Sep02InvalidAddressException) {
    println("Bad address format: ${e.message}")
} catch (e: Sep02FederationNotFoundException) {
    println("No federation server: ${e.message}")
} catch (e: Sep02InvalidResponseException) {
    println("Server error: ${e.message}")
}
```

---

## 2. Resolve Account ID (reverse lookup)

`resolveAccountId()` is an instance method that performs a `type=id` query. You
must first create a `FederationService` with a known federation server URL or
via `fromDomain()`.

```kotlin
import com.soneso.stellar.sdk.sep.sep02.FederationService

// Option A: auto-discover via fromDomain (fetches stellar.toml)
val service = FederationService.fromDomain("soneso.com")

val response = service.resolveAccountId(
    "GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI"
)

println(response.stellarAddress) // bob*soneso.com
println(response.accountId)      // GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI

// Option B: construct directly with a known federation server URL
val service2 = FederationService(
    federationServerUrl = "https://stellarid.io/federation/"
)
val response2 = service2.resolveAccountId("GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI")
```

**Note:** Reverse lookups are ambiguous when an anchor sends transactions on
behalf of users -- the account ID will be the anchor's, not the individual
user's. In that case use `resolveTransactionId` instead.

---

## 3. Resolve Transaction ID (txid lookup)

`resolveTransactionId()` performs a `type=txid` query. Returns the federation
record of the sender of the transaction, if known by the server.

```kotlin
import com.soneso.stellar.sdk.sep.sep02.FederationService

val service = FederationService.fromDomain("example.com")

val response = service.resolveTransactionId(
    "ae05181b239bd4a64ba2fb8086901479a0bde86f8e912150e74241fe4f5f0948"
)

println(response.stellarAddress) // sender*example.com
println(response.accountId)      // G...
```

---

## 4. Resolve Forward

`resolveForward()` performs a `type=forward` query for routing payments to
external networks or financial institutions. All institution-specific parameters
are passed as a `Map<String, String>`.

The resulting URL includes `type=forward` plus all entries from your map:
`?type=forward&forward_type=bank_account&swift=BOPBPHMM&acct=2382376`

```kotlin
import com.soneso.stellar.sdk.sep.sep02.FederationService

val service = FederationService.fromDomain("example.com")

val response = service.resolveForward(
    mapOf(
        "forward_type" to "bank_account",
        "swift" to "BOPBPHMM",
        "acct" to "2382376"
    )
)

println(response.accountId) // G... (account to send payment to)
println(response.memoType)  // id
println(response.memo)      // 54321
```

The response provides the `accountId` and optional memo that must be attached
to the Stellar payment to correctly route the forwarded funds.

---

## 5. FederationResponse Fields

All four query methods return a `FederationResponse`. The `accountId` field is
required (throws `Sep02InvalidResponseException` if missing); all other fields
are nullable.

```kotlin
data class FederationResponse(
    val stellarAddress: String? = null, // "name*domain.com" -- set for name/id/txid lookups
    val accountId: String,              // "G..." -- always present (required)
    val memoType: String? = null,       // "text" | "id" | "hash" -- null if no memo required
    val memo: String? = null            // memo value as String -- null if no memo required
)
```

**JSON field mapping** (from the federation server response):

| JSON key          | Kotlin property   |
|-------------------|-------------------|
| `stellar_address` | `stellarAddress`  |
| `account_id`      | `accountId`       |
| `memo_type`       | `memoType`        |
| `memo`            | `memo`            |

**memo field:** Always a `String` regardless of memo type. For `id` memo, the
server returns an integer as a string (e.g., `"12345"`). Parse with
`response.memo!!.toULong()` when building the transaction memo (`MemoId` takes
`ULong`).

---

## 6. Using the Memo in a Payment

After resolving a federation address, always check for memo instructions and
attach them to your payment transaction:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep02.FederationService
import com.soneso.stellar.sdk.sep.sep02.FederationResponse
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
suspend fun payFederationAddress(
    federationAddress: String,
    amountXlm: String,
    senderKeyPair: KeyPair
) {
    // 1. Resolve the federation address
    val fed: FederationResponse =
        FederationService.resolveStellarAddress(federationAddress)

    // 2. Build the memo -- required if memoType is set
    val memo: Memo = if (fed.memoType != null && fed.memo != null) {
        when (fed.memoType) {
            "text" -> MemoText(fed.memo!!)
            "id" -> MemoId(fed.memo!!.toULong())
            "hash" -> MemoHash(Base64.decode(fed.memo!!))
            else -> MemoNone
        }
    } else {
        MemoNone
    }

    // 3. Load sender account from Horizon
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val senderAccount = server.accounts().account(senderKeyPair.getAccountId())

    // 4. Build and submit the payment
    val tx = TransactionBuilder(
        sourceAccount = Account(
            senderKeyPair.getAccountId(),
            senderAccount.sequenceNumber
        ),
        network = Network.TESTNET
    )
        .addOperation(
            PaymentOperation(
                destination = fed.accountId,
                asset = AssetTypeNative,
                amount = amountXlm
            )
        )
        .addMemo(memo)
        .setTimeout(300)
        .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
        .build()

    tx.sign(senderKeyPair)
    val result = server.submitTransaction(tx.toEnvelopeXdrBase64())
    println("Success: ${result.successful}")
}
```

**Important:** If the federation response includes `memoType` and `memo`, you
MUST attach that memo to the payment or it may be unroutable at the destination.

---

## 7. Custom HTTP Client

`FederationService` accepts an optional `httpClient` (Ktor `HttpClient`) and
`httpRequestHeaders` (`Map<String, String>`). Use these for custom timeouts,
proxies, or additional headers.

All three factory/static methods accept these parameters:

```kotlin
import com.soneso.stellar.sdk.sep.sep02.FederationService
import io.ktor.client.*
import io.ktor.client.plugins.*

// Custom client with longer timeouts
val client = HttpClient {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = 60_000
    }
}

// Static resolve with custom client
val response = FederationService.resolveStellarAddress(
    address = "bob*example.com",
    httpClient = client,
    httpRequestHeaders = mapOf("User-Agent" to "MyWallet/1.0")
)

// fromDomain with custom client
val service = FederationService.fromDomain(
    domain = "example.com",
    httpClient = client,
    httpRequestHeaders = mapOf("Authorization" to "Bearer token")
)
```

When constructing `FederationService` directly, pass the parameters to the
constructor:

```kotlin
// client: from the previous steps of this flow
val service = FederationService(
    federationServerUrl = "https://api.example.com/federation",
    httpClient = client,
    httpRequestHeaders = mapOf("X-Custom" to "value")
)
```

---

## 8. Testing with MockEngine

Use Ktor's `MockEngine` to test federation lookups without real network calls.
For the static `resolveStellarAddress`, the mock must handle two request paths:
the `stellar.toml` fetch and the federation query.

```kotlin
import com.soneso.stellar.sdk.sep.sep02.FederationService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FederationMockTest {

    @Test
    fun resolveAddressWithMemo() = runTest {
        val mockClient = HttpClient(MockEngine { request ->
            val url = request.url.toString()

            if (url.contains(".well-known/stellar.toml")) {
                // First request: stellar.toml discovery
                respond(
                    content = """FEDERATION_SERVER="https://api.example.com/federation"""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                // Second request: federation lookup
                respond(
                    content = """{
                        "stellar_address": "alice*example.com",
                        "account_id": "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
                        "memo_type": "id",
                        "memo": "12345"
                    }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        })

        val response = FederationService.resolveStellarAddress(
            address = "alice*example.com",
            httpClient = mockClient
        )

        assertEquals("alice*example.com", response.stellarAddress)
        assertEquals("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP", response.accountId)
        assertEquals("id", response.memoType)
        assertEquals("12345", response.memo)
    }

    @Test
    fun resolveForwardMock() = runTest {
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{
                    "account_id": "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
                    "memo_type": "id",
                    "memo": "54321"
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })

        val service = FederationService(
            federationServerUrl = "https://api.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveForward(
            mapOf(
                "forward_type" to "bank_account",
                "swift" to "BOPBPHMM",
                "acct" to "2382376"
            )
        )

        assertNotNull(response.accountId)
        assertEquals("id", response.memoType)
        assertEquals("54321", response.memo)
    }
}
```

---

## 9. Common Pitfalls

**Missing `*` in the address:**

```kotlin
// WRONG: throws Sep02InvalidAddressException
FederationService.resolveStellarAddress("bob.example.com")

// CORRECT: must use * as separator between username and domain
FederationService.resolveStellarAddress("bob*example.com")
```

**Using static resolve vs instance resolve for non-name lookups:**

```kotlin
// WRONG: FederationService.resolveStellarAddress() is ONLY for name*domain lookups
// There is no static resolveAccountId — you must create a service instance first
// FederationService.resolveAccountId("G...") // does not exist

// CORRECT: create a FederationService instance, then call the instance method
val service = FederationService.fromDomain("soneso.com")
val response = service.resolveAccountId("G...")

// Or construct directly with a known URL
val service2 = FederationService(federationServerUrl = "https://api.example.com/federation")
val response2 = service2.resolveAccountId("G...")
```

**Not attaching the memo when one is required:**

```kotlin
// sourceAccount: from the previous steps of this flow
// WRONG: omitting the memo causes unroutable payments at many exchanges
val fed = FederationService.resolveStellarAddress("alice*exchange.com")
TransactionBuilder(sourceAccount, Network.TESTNET)
    .addOperation(PaymentOperation(fed.accountId, AssetTypeNative, "10"))
    // forgot .addMemo(...)
    .setTimeout(300)
    .setBaseFee(100)
    .build()

// CORRECT: always check and attach memo
val memo: Memo = if (fed.memoType != null && fed.memo != null) {
    when (fed.memoType) {
        "text" -> MemoText(fed.memo!!)
        "id" -> MemoId(fed.memo!!.toULong())
        else -> MemoNone
    }
} else {
    MemoNone
}
TransactionBuilder(sourceAccount, Network.TESTNET)
    .addOperation(PaymentOperation(fed.accountId, AssetTypeNative, "10"))
    .addMemo(memo)
    .setTimeout(300)
    .setBaseFee(100)
    .build()
```

**Using wrong type for MemoId -- MemoId takes ULong, not Long or Int:**

```kotlin
// WRONG: MemoId takes ULong, not Long
val memo = MemoId(fed.memo!!.toLong()) // compile error: Long is not ULong

// WRONG: MemoId takes ULong, not Int
val memo = MemoId(fed.memo!!.toInt()) // compile error

// CORRECT: parse the string to ULong
val memo = MemoId(fed.memo!!.toULong())
```

**Treating memo as a number -- it is always String:**

```kotlin
// fed: from the previous steps of this flow
// WRONG: response.memo is String? -- there is no numeric field
val memoId: Int = fed.memo // compile error

// CORRECT: parse the string when building a MemoId
if (fed.memoType == "id") {
    val memo = MemoId(fed.memo!!.toULong())
}
```

**Assuming all FederationResponse fields are non-null:**

```kotlin
// fed, length: from the previous steps of this flow
// WRONG: forward lookups do not return stellarAddress; it will be null
println(fed.stellarAddress!!.length) // throws NullPointerException if null

// CORRECT: stellarAddress and memo fields are String? -- null-check before use
if (fed.stellarAddress != null) {
    println(fed.stellarAddress)
}
println(fed.accountId) // accountId is non-null String (always present)
```

**Forgetting that FederationService methods are suspend functions:**

```kotlin
import kotlinx.coroutines.runBlocking

// WRONG: calling suspend function outside a coroutine scope
fun lookup() {
    val response = FederationService.resolveStellarAddress("bob*soneso.com") // compile error
}

// CORRECT: call from a suspend function or coroutine scope
suspend fun lookup() {
    val response = FederationService.resolveStellarAddress("bob*soneso.com")
}

// Or from a coroutine scope
fun main() = runBlocking {
    val response = FederationService.resolveStellarAddress("bob*soneso.com")
    println(response.accountId)
}
```

**Static resolveStellarAddress makes two HTTP requests -- mock both:**

`FederationService.resolveStellarAddress` first fetches `https://DOMAIN/.well-known/stellar.toml`
to discover `FEDERATION_SERVER`, then queries the federation endpoint. When
mocking, your `MockEngine` must handle both URLs or the lookup will fail.

**Submitting transactions -- pass the XDR string, not the Transaction object:**

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
// WRONG: HorizonServer.submitTransaction does NOT accept a Transaction object
// server.submitTransaction(transaction) // compile error

// CORRECT: convert to envelope XDR base64 string first
val result = server.submitTransaction(transaction.toEnvelopeXdrBase64())
```

**Constructing TransactionBuilder -- requires both sourceAccount and network:**

```kotlin
// sequenceNumber: from the previous steps of this flow
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
// WRONG: TransactionBuilder takes two arguments, not one
// val tx = TransactionBuilder(account).addOperation(op).build()

// CORRECT: pass both sourceAccount and network
val tx = TransactionBuilder(
    sourceAccount = Account(accountId, sequenceNumber),
    network = Network.TESTNET
)
    .addOperation(op)
    .setTimeout(300)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()
```
