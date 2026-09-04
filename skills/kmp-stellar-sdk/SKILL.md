---
name: kmp-stellar-sdk
description: Build Stellar blockchain applications with the Soneso KMP (Kotlin Multiplatform) SDK. Covers keypair generation, transaction building, Horizon queries, Soroban smart contracts, smart accounts (OpenZeppelin) with passkey / WebAuthn authentication, XDR encoding, and SEP integrations. Use when the developer is working with Kotlin, KMP, or Android and mentions Stellar, blockchain, cryptocurrency, passkey, or smart wallet operations.
license: Apache-2.0
compatibility: Requires Kotlin 2.2+ and com.soneso.stellar:stellar-sdk 1.12.0. Supports JVM (Java 17+), Android (API 24+), iOS, macOS, and JavaScript (Browser/Node.js).
metadata:
  author: soneso
  version: "1.2.0"
  sdk_repo: https://github.com/Soneso/kmp-stellar-sdk
---

# Stellar SDK for Kotlin Multiplatform

## Overview

The KMP Stellar SDK (`com.soneso.stellar:stellar-sdk`) is a Kotlin Multiplatform library for building Stellar blockchain applications on JVM/Android, iOS/macOS, and JavaScript (Browser/Node.js). It provides full Horizon API coverage, full Soroban RPC coverage, 27 Stellar operations, and 15 SEP implementations. Crypto operations (KeyPair, signing) are `suspend` functions. Uses Ktor for networking and kotlinx.coroutines for async.

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.12.0")
}
```

> All code examples below run inside a `suspend` context (coroutine) and assume these imports: `com.soneso.stellar.sdk.*`, `com.soneso.stellar.sdk.horizon.*`, `com.soneso.stellar.sdk.contract.*`, and `com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order`.
>
> If you can't find a constructor or method in this file or the topic references, grep `references/api_reference.md`.

## 1. Stellar Basics

### Keys and KeyPairs

```kotlin
// IMPORTANT: KeyPair.random() and KeyPair.fromSecretSeed() are suspend functions
val keyPair = KeyPair.random()
val accountId: String = keyPair.getAccountId()   // G... public address
val secretSeed: CharArray? = keyPair.getSecretSeed() // S... secret seed (CharArray for security)

// From existing seed (also suspend). Load the seed from platform-secure storage
// (Keychain / Android Keystore / OS keyring) — NEVER commit a real seed to source.
val restored = KeyPair.fromSecretSeed("S_YOUR_SECRET_SEED_HERE")   // 56-char S... strkey
val publicOnly = KeyPair.fromAccountId(accountId) // public-key-only, cannot sign

// WRONG: KeyPair.random() without suspend — it IS a suspend function
// WRONG: keyPair.accountId — property access does NOT exist
// CORRECT: keyPair.getAccountId() — use the getter method
// WRONG: keyPair.secretSeed — property access does NOT exist
// CORRECT: keyPair.getSecretSeed() — returns CharArray? (null if public-only)
```

### Accounts

```kotlin
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.responses.AccountResponse

val server = HorizonServer("https://horizon-testnet.stellar.org")
val keyPair = KeyPair.random()
val accountId = keyPair.getAccountId()

// Fund on testnet using FriendBot (10,000 test XLM)
// WRONG: FriendBot.fundTestAccount(...) — method does NOT exist
// CORRECT: FriendBot.fundTestnetAccount(accountId) — returns Boolean
FriendBot.fundTestnetAccount(accountId)

// Query account
// WRONG: HorizonServer.TESTNET or StellarSDK.TESTNET — no static factory
// CORRECT: HorizonServer("https://horizon-testnet.stellar.org")
val account: AccountResponse = server.accounts().account(accountId)
println("Sequence: ${account.sequenceNumber}")

for (balance in account.balances) {
    if (balance.assetType == "native") println("XLM: ${balance.balance}")
    else println("${balance.assetCode}: ${balance.balance}")
}

for (signer in account.signers) {
    println("${signer.key} weight=${signer.weight} type=${signer.type}")
}
```

### Assets

```kotlin
// Native asset (XLM)
val xlm: Asset = AssetTypeNative // Singleton object, not a function call

// 1-4 char code -> AssetTypeCreditAlphaNum4; 5-12 char -> AssetTypeCreditAlphaNum12
val usdc = Asset.createNonNativeAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")

// From canonical form ("native" or "CODE:ISSUER")
val parsed = Asset.create("USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")

// WRONG: Asset.NATIVE or AssetTypeNative() — it's a data object, not a constructor
// CORRECT: AssetTypeNative (no parentheses)

// Access code and issuer (cast to AssetTypeCreditAlphaNum)
if (usdc is AssetTypeCreditAlphaNum) {
    val code: String = usdc.code
    val issuer: String = usdc.issuer
}
// toString() returns canonical form: "native" for XLM, "USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN" for custom

// Network passphrases: Network.TESTNET, Network.PUBLIC, Network.FUTURENET
// Horizon servers: construct with URL string
val publicServer = HorizonServer("https://horizon.stellar.org")
```

## 2. Horizon API - Fetching Data

Query patterns for retrieving blockchain data. All request builders support `.cursor()`, `.limit()`, `.order()` for pagination.

### Query Accounts

```kotlin
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

val server = HorizonServer("https://horizon-testnet.stellar.org")

// Single account
val account = server.accounts().account(accountId)

// Builder pattern: forSigner(), forAsset(), limit(), order(), cursor()
val bySigner = server.accounts()
    .forSigner(accountId)
    .limit(10)
    .order(Order.DESC)
    .execute()
for (acct in bySigner.records) {
    println("Account: ${acct.accountId}")
}
```

### Query Transactions

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")
val txPage = server.transactions()
    .forAccount(accountId)
    .order(Order.DESC)
    .limit(5)
    .execute()

for (tx in txPage.records) {
    println("${tx.hash} ledger=${tx.ledger}")
}

// Pagination: cursor from last result
val nextPage = server.transactions()
    .forAccount(accountId)
    .cursor(txPage.records.last().pagingToken)
    .limit(5).order(Order.DESC).execute()

// Single transaction by hash
val single = server.transactions().transaction("abc123...")
```

For all Horizon endpoints, advanced queries, and pagination patterns:
[Horizon API Reference](./references/horizon_api.md)

## 3. Horizon API - Streaming

Real-time update patterns using Server-Sent Events (SSE). Set cursor to `"now"` for real-time events. Store the `SSEStream` to close later.

### Stream Payments

```kotlin
import com.soneso.stellar.sdk.horizon.requests.EventListener
import com.soneso.stellar.sdk.horizon.requests.SSEStream
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PaymentOperationResponse
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")

// SSE stream pattern — callback-based with EventListener interface
val stream = server.payments().forAccount(accountId).cursor("now")
    .stream(
        serializer = OperationResponse.serializer(),
        listener = object : EventListener<OperationResponse> {
            override fun onEvent(event: OperationResponse) {
                if (event is PaymentOperationResponse) {
                    println("${event.amount} from ${event.from}")
                }
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: ${error?.message}")
            }
        }
    )
// stream.close() — always close to prevent resource leaks
```

Streams reconnect automatically. For all streaming endpoints: [Horizon Streaming Guide](./references/horizon_streaming.md)

## 4. Transactions & Operations

Complete transaction lifecycle: Build -> Sign -> Submit.

### Transaction Lifecycle

```kotlin
// getAccountId, senderSecret: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")

// 1. Load sender keypair (suspend function)
val senderKeyPair = KeyPair.fromSecretSeed(senderSecret)
val senderAccountId = senderKeyPair.getAccountId()

// 2. Load source account (provides sequence number)
// WRONG: server.accounts().account(id) then wrap manually
// CORRECT: server.loadAccount(id) — returns Account ready for TransactionBuilder
val senderAccount = server.loadAccount(senderAccountId)

// 3. Build transaction — requires Network as second parameter
val transaction = TransactionBuilder(senderAccount, network)
    .addOperation(
        PaymentOperation(
            destination = destinationAccountId,
            asset = AssetTypeNative,
            amount = "100.50"
        )
    )
    .addMemo(MemoText("payment"))
    .setBaseFee(200) // stroops per operation (minimum: 100)
    .setTimeout(300)  // seconds from now
    .build()

// 4. Sign transaction (suspend function)
transaction.sign(senderKeyPair)

// 5. Submit to Horizon
val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
if (response.successful) {
    println("Success! Hash: ${response.hash}")
} else {
    println("Failed! Check result XDR: ${response.resultXdr}")
}

// WRONG: TransactionBuilder(account) — missing Network parameter
// CORRECT: TransactionBuilder(account, network) — Network is required

// WRONG: transaction.sign(keyPair, network) — Network is NOT a parameter of sign()
// CORRECT: transaction.sign(keyPair) — Network is set in TransactionBuilder

// WRONG: server.submitTransaction(transaction) — does NOT accept Transaction object
// CORRECT: server.submitTransaction(transaction.toEnvelopeXdrBase64()) — accepts String
```

### Common Operations

**Change Trust (Establish Trustline):**

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val usdc = Asset.createNonNativeAsset("USDC", issuerAccountId)
val trustline = ChangeTrustOperation(asset = usdc) // default limit = max
// With custom limit: ChangeTrustOperation(asset = usdc, limit = "1000.00")
```

**Manage Sell Offer (DEX):**

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val selling = AssetTypeNative
val buying = Asset.createNonNativeAsset("USDC", issuerAccountId)

// Create new offer (offerId = 0 means new offer)
val newOffer = ManageSellOfferOperation(
    selling = selling,
    buying = buying,
    amount = "100.0",
    price = Price.fromString("0.5") // 0.5 USDC per XLM
)
```

For all 27 operations with parameters and examples:
[Operations Reference](./references/operations.md)

## 5. Soroban RPC API

RPC endpoint patterns for Soroban smart contract queries.

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer

val rpcServer = SorobanServer("https://soroban-testnet.stellar.org:443")
val health = rpcServer.getHealth() // .status, .latestLedger
```

For all RPC methods including event queries and transaction simulation:
[RPC Reference](./references/rpc.md)

## 6. Smart Contracts

Contract deployment and invocation using the high-level `ContractClient`.

### Deploy Contract

```kotlin
// getAccountId, secretSeed, wasmBytes: from the previous steps of this flow
import com.soneso.stellar.sdk.contract.ContractClient

val keyPair = KeyPair.fromSecretSeed(secretSeed)
val rpcUrl = "https://soroban-testnet.stellar.org:443"
val source = keyPair.getAccountId()

// One-step deploy: upload WASM + create contract instance
val client = ContractClient.deploy(
    wasmBytes = wasmBytes, source = source, signer = keyPair,
    network = Network.TESTNET, rpcUrl = rpcUrl
)
println("Contract ID: ${client.contractId}")

// Two-step: install() returns wasmId, then deployFromWasmId() (reuse WASM)
val wasmId = ContractClient.install(
    wasmBytes = wasmBytes, source = source, signer = keyPair,
    network = Network.TESTNET, rpcUrl = rpcUrl
)
val client2 = ContractClient.deployFromWasmId(
    wasmId = wasmId, source = source, signer = keyPair,
    network = Network.TESTNET, rpcUrl = rpcUrl
)
```

### Invoke Contract Function

```kotlin
val amount = "100"
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val rpcUrl = "https://soroban-testnet.stellar.org"
val fromAddr = "GCFIRY65OQE7DFP5KLNS2PF2LVZMUZYJX4OZIEQ36N2IQANUB5XVYOJR"
val toAddr = "GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U"
// Create client for an existing contract (loads spec from network)
val client = ContractClient.forContract(
    contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC",
    rpcUrl = rpcUrl,
    network = Network.TESTNET
)

// Invoke with automatic type conversion (recommended); name the result type
// Read calls return immediately; write calls auto-sign and submit
val result: UInt = client.invoke(
    functionName = "get_count",
    arguments = emptyMap(),
    source = keyPair.getAccountId(),
    signer = null  // null for read-only calls
)

// Write call with arguments
val writeResult: UInt = client.invoke(
    functionName = "increment",
    arguments = mapOf("value" to 5),
    source = keyPair.getAccountId(),
    signer = keyPair  // required for write calls
)

// Advanced: get AssembledTransaction for manual control (multi-sig workflows)
val tx = client.buildInvoke<Unit>(
    functionName = "transfer",
    arguments = mapOf("from" to fromAddr, "to" to toAddr, "amount" to 1000),
    source = keyPair.getAccountId(),
    signer = keyPair
)
tx.signAndSubmit(keyPair)
```

For contract authorization, multi-auth workflows, low-level deploy/invoke, and typed clients generated with stellar-contract-bindings (`stellar-contract-bindings kmp`):
[Smart Contracts Guide](./references/soroban_contracts.md)

## 7. Smart Accounts (OpenZeppelin)

Passkey-authenticated Soroban smart accounts: biometric auth, multiple signers (passkey/delegated/Ed25519), context rules, policies, and optional fee sponsoring via a relayer. Entry point: `OZSmartAccountKit.create(OZSmartAccountConfig(...))` — requires `rpcUrl`, `networkPassphrase`, `accountWasmHash` (hex), `webauthnVerifierAddress` (C-address), plus platform-specific `webauthnProvider` and `storage`.

- [Smart Accounts Guide](./references/smart_accounts.md) — kit config, wallet create/connect, signers, transactions, credentials, events, `submit` / `fundWallet`, external signer manager, indexer
- [Context Rules & Policies](./references/smart_accounts_policies.md) — context rules, policies, multi-signer, common scenarios (recovery, rotation, `__check_auth` debugging), contract error codes
- [WebAuthn Platform Setup](./references/smart_accounts_webauthn.md) — Android, iOS, macOS, Web adapters + rpId/DAL/AASA

## 8. XDR Encoding & Decoding

XDR (External Data Representation) is Stellar's binary serialization format.

### Transaction XDR Roundtrip

```kotlin
// transaction: from the previous steps of this flow
// Encode: Transaction -> base64 XDR
val xdrBase64: String = transaction.toEnvelopeXdrBase64()

// Decode: base64 XDR -> AbstractTransaction
val decoded = AbstractTransaction.fromEnvelopeXdr(xdrBase64, Network.TESTNET)
if (decoded is Transaction) {
    println("Source: ${decoded.sourceAccount}, Fee: ${decoded.fee}")
    println("Operations: ${decoded.operations.size}")
}
```

### Working with Soroban XDR Values (Scv)

```kotlin
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr

// Factory methods: toBoolean(), toUint32(), toInt64(), toString(), toSymbol(), toVoid()
val symVal  = Scv.toSymbol("transfer")
val addrVal = Scv.toAddress(Address("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54").toSCAddress())
val vecVal  = Scv.toVec(listOf(Scv.toUint32(1u), Scv.toUint32(2u)))
val mapVal  = Scv.toMap(linkedMapOf(symVal to Scv.toUint32(42u)))

// WRONG: Scv.toU32() / Scv.fromU32() — those method names do NOT exist
// CORRECT: Scv.toUint32() / Scv.fromUint32() — full type name, not abbreviated
// WRONG: XdrSCVal.forSymbol("transfer") — no such API in this SDK
// CORRECT: Scv.toSymbol("transfer") — KMP uses the Scv utility object

// Read back: fromBoolean(), fromUint32(), fromInt64(), fromString(), fromSymbol()
val intValue: UInt = Scv.fromUint32(Scv.toUint32(42u))
```

To submit a pre-signed XDR envelope: `server.submitTransaction(signedXdrBase64)`.

For all Scv factory methods and type mapping:
[XDR Reference](./references/xdr.md) | [Contract Arguments](./references/soroban_contracts.md)

## 9. Error Handling & Troubleshooting

```kotlin
import com.soneso.stellar.sdk.horizon.exceptions.*
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")

try {
    val account = server.accounts().account(accountId)
} catch (e: BadRequestException) {
    println("Horizon error ${e.code}: ${e.body}")   // HTTP 4xx: 404, 400, ...
} catch (e: TooManyRequestsException) {
    println("Rate limited: ${e.code}")              // 429
} catch (e: ConnectionErrorException) {
    println("Connection error: ${e.message}")       // network/connectivity
}

// WRONG: response.success — property does NOT exist on TransactionResponse
// CORRECT: response.successful — Boolean; on failure inspect response.resultXdr
```

A rejected `submitTransaction` raises `BadRequestException` (result codes in the body); a returned `TransactionResponse` reports `successful` and `resultXdr` (`tx_failed`: check operation results; `tx_bad_seq`: reload the account). Soroban contract failures raise the 10 exception types in `com.soneso.stellar.sdk.contract.exception` (SimulationFailedException, SendTransactionFailedException, TransactionFailedException, ExpiredStateException, ...); check RPC health with `SorobanServer.getHealth()` (`status != "healthy"`). Full error catalog and solutions: [Troubleshooting Guide](./references/troubleshooting.md)

## 10. Security Best Practices

Covers secret key management (`getSecretSeed()` returns `CharArray?`), transaction verification, `StrKey` validation, and amount precision. See [Security Guide](./references/security.md).

## 11. SEP Implementations

The KMP SDK implements 15 SEPs: 01, 02, 05, 06, 08, 09, 10, 12, 24, 30, 31, 38, 45, 51, 53. See [SEP Implementations Guide](./references/sep.md).

## Reference Documentation

- [Operations](./references/operations.md) - All 27 Stellar operations with examples
- [Horizon API](./references/horizon_api.md) - Complete Horizon endpoint coverage
- [Horizon Streaming](./references/horizon_streaming.md) - SSE patterns for all streaming endpoints
- [RPC](./references/rpc.md) - All Soroban RPC methods
- [Smart Contracts](./references/soroban_contracts.md) - Contract deployment, invocation, auth, generated bindings
- [Smart Accounts](./references/smart_accounts.md) - OZ kit core: config, wallet creation/connect, signers, transactions, credentials, events
- [Smart Accounts - Policies](./references/smart_accounts_policies.md) - Context rules, policies, multi-signer operations
- [Smart Accounts - WebAuthn](./references/smart_accounts_webauthn.md) - Platform adapters for Android, iOS, macOS, Web
- [XDR](./references/xdr.md) - XDR encoding/decoding and debugging
- [Troubleshooting](./references/troubleshooting.md) - Error codes, platform & environment info
- [Security](./references/security.md) - Platform-specific key storage, production deployment
- [SEP Implementations](./references/sep.md) - 15 SEPs with per-SEP references: [01](./references/sep-01.md), [02](./references/sep-02.md), [05](./references/sep-05.md), [06](./references/sep-06.md), [08](./references/sep-08.md), [09](./references/sep-09.md), [10](./references/sep-10.md), [12](./references/sep-12.md), [24](./references/sep-24.md), [30](./references/sep-30.md), [31](./references/sep-31.md), [38](./references/sep-38.md), [45](./references/sep-45.md), [51](./references/sep-51.md), [53](./references/sep-53.md)
- [Advanced](./references/advanced.md) - Multi-sig, sponsorship, fee bumps, liquidity pools, muxed accounts
- [API Reference](./references/api_reference.md) - All public class/method signatures

## Common Pitfalls

**Suspend functions everywhere:** `KeyPair.random()`, `KeyPair.fromSecretSeed()`, `transaction.sign()`, and all network calls are `suspend` functions. Must be called from a coroutine context.
```kotlin
// WRONG: calling from non-suspend context — compile error
// CORRECT: use suspend fun, or launch coroutine:
suspend fun createAccount() { val kp = KeyPair.random() }
// Android: lifecycleScope.launch { val kp = KeyPair.random() }
// Tests: @Test fun test() = runTest { val kp = KeyPair.random() }
```

**Amounts are always Strings:** All payment amounts, balances, and prices are `String` types (7 decimal places max).

**Sequence number management:** `build()` increments the source account's sequence number. Reload the account before building a new transaction. Don't increment manually or you get `tx_bad_seq`.

**setBaseFee is required:** `TransactionBuilder` requires `setBaseFee()` before `build()`. Omitting it throws `IllegalStateException`. Fee is per operation: N ops at `setBaseFee(200)` = N * 200 stroops total.

**submitTransaction accepts String:** `server.submitTransaction(transaction.toEnvelopeXdrBase64())` -- does NOT accept a Transaction object directly.

**SecretSeed is CharArray:** `keyPair.getSecretSeed()` returns `CharArray?` for secure memory handling. Convert with `String(charArray)` when needed.
