# CLAUDE.md - Integration Tests

This file provides guidance for porting integration tests from the Flutter Stellar SDK to the KMP Stellar SDK.

## Overview

The Flutter Stellar SDK (`/Users/chris/projects/Stellar/stellar_flutter_sdk/test/`) contains comprehensive integration tests that should be ported to the KMP SDK to ensure feature parity. This document captures learnings from successful test ports.

## Porting Process

### 1. Identify Source Test

- **Location**: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/`
- **Common test files**:
  - `account_test.dart` - Account operations ✅ PORTED
  - `transaction_test.dart` - Transaction operations
  - `operations_test.dart` - Stellar operations
  - `payment_test.dart` - Payment operations
  - `assets_test.dart` - Asset operations
  - `effects_test.dart` - Effects queries
  - `ledger_test.dart` - Ledger queries
  - `offers_test.dart` - Offers and trading
  - `claimable_balances_test.dart` - Claimable balances
  - `liquidity_pools_test.dart` - Liquidity pools
  - `soroban_test.dart` - Soroban/smart contracts

### 2. Understand Test Structure

Flutter tests use:
```dart
test('test name', () async {
    // Test code
});
```

KMP tests use:
```kotlin
@Test
fun testName() = runBlocking { // or runTest for unit tests
    // Test code
}
```

### 3. Create Test File

- **Location**: `stellar-sdk/src/commonTest/kotlin/com/stellar/sdk/integrationTests/`
- **Naming**: `[Feature]IntegrationTest.kt` (e.g., `AccountIntegrationTest.kt`)
- **Do NOT add @Ignore** - Tests should run automatically with FriendBot funding

## Key Mappings: Flutter → Kotlin

### Async/Await

| Flutter | Kotlin |
|---------|--------|
| `async { }` | `runBlocking { }` or `suspend fun` |
| `await someFunction()` | `someFunction()` (in suspend context) |
| `Future.delayed(Duration(seconds: 3))` | `delay(3000)` |
| `Future<T>` | `suspend fun(): T` |

### Types

| Flutter | Kotlin |
|---------|--------|
| `String` | `String` |
| `int` | `Int` or `Long` |
| `BigInt` | `Long` |
| `bool` | `Boolean` |
| `List<T>` | `List<T>` |
| `Uint8List` | `ByteArray` |
| `var` | `val` or `var` |

### SDK Classes

| Flutter | Kotlin |
|---------|--------|
| `StellarSDK.TESTNET` | `HorizonServer("https://horizon-testnet.stellar.org")` |
| `Network.TESTNET` | `Network.TESTNET` |
| `KeyPair.random()` | `KeyPair.random()` (suspend) |
| `KeyPair.fromSecretSeed(seed)` | `KeyPair.fromSecretSeed(seed)` (suspend) |
| `Asset.NATIVE` | `AssetTypeNative` (data object, not class) |
| `AssetTypeCreditAlphaNum4("CODE", "ISSUER")` | `AssetTypeCreditAlphaNum4("CODE", "ISSUER")` |
| `FriendBot.fundTestAccount(id)` | `FriendBot.fundTestnetAccount(id)` (suspend) |

### Operations

All operations are already implemented. Common mappings:

| Flutter | Kotlin |
|---------|--------|
| `CreateAccountOperationBuilder(dest, amount).build()` | `CreateAccountOperation(destination = dest, startingBalance = amount)` |
| `PaymentOperationBuilder(dest, asset, amount).build()` | `PaymentOperation(destination = dest, asset = asset, amount = amount)` |
| `SetOptionsOperationBuilder()` | `SetOptionsOperation(...)` (named parameters) |
| `ChangeTrustOperationBuilder(asset, limit).build()` | `ChangeTrustOperation(asset = asset, limit = limit)` |
| `AccountMergeOperationBuilder(dest).build()` | `AccountMergeOperation(destination = dest)` |
| `BumpSequenceOperationBuilder(seqNum).build()` | `BumpSequenceOperation(bumpTo = seqNum)` |
| `ManageDataOperationBuilder(key, value).build()` | `ManageDataOperation(name = key, value = value)` |

### Transactions

| Flutter | Kotlin |
|---------|--------|
| `TransactionBuilder(account)` | `TransactionBuilder(sourceAccount = Account(id, seq), network = network)` |
| `.addOperation(op)` | `.addOperation(op)` |
| `.addMemo(Memo.text("..."))` | `.addMemo(MemoText("..."))` |
| `.build()` | `.setTimeout(...).setBaseFee(...).build()` |
| `transaction.sign(keyPair, network)` | `transaction.sign(keyPair)` (suspend) |
| `sdk.submitTransaction(tx)` | `horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())` (suspend) |

### Response Handling

| Flutter | Kotlin |
|---------|--------|
| `response.success` | `response.successful` |
| `response.hash` | `response.hash` |
| `account.sequenceNumber` | `account.sequenceNumber` (Long) |
| `account.thresholds.highThreshold` | `account.thresholds.highThreshold` (Int) |
| `account.flags.authRequired` | `account.flags.authRequired` (Boolean) |
| `account.data.getDecoded(key)` | Access via `account.data[key]` (base64 string) |

### Assertions

| Flutter | Kotlin |
|---------|--------|
| `assert(condition)` | `assertTrue(condition)` or `assert(condition)` |
| `assert(a == b)` | `assertEquals(expected, actual)` |
| `assert(a != b)` | `assertNotEquals(a, b)` |
| `assert(a > b)` | `assertTrue(a > b)` |

### Streaming (SSE)

| Flutter | Kotlin |
|---------|--------|
| `sdk.transactions.forAccount(id).cursor("now").stream().listen((event) { })` | `horizonServer.transactions().forAccount(id).cursor("now").stream(serializer, listener)` |
| `subscription.cancel()` | `stream.close()` |
| Stream listener callback | `EventListener<T>` interface with `onEvent()` and `onFailure()` |

**Important**: For streaming tests, use `runBlocking` instead of `runTest` because streaming requires real-time execution.

## Common Patterns

### Pattern 1: Account Funding

```kotlin
// Create and fund account
val keyPair = KeyPair.random()
val accountId = keyPair.getAccountId()

if (testOn == "testnet") {
    FriendBot.fundTestnetAccount(accountId)
} else {
    FriendBot.fundFuturenetAccount(accountId)
}

delay(3000) // Wait for account creation
```

### Pattern 2: Transaction Building

```kotlin
val transaction = TransactionBuilder(
    sourceAccount = Account(accountId, account.sequenceNumber),
    network = network
)
    .addOperation(someOperation)
    .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

transaction.sign(keyPair)

val response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
assertTrue(response.successful, "Transaction should succeed")
```

### Pattern 3: Query Operations and Effects

```kotlin
// Verify operations can be parsed
val operationsPage = horizonServer.operations().forAccount(accountId).execute()
assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

// Verify effects can be parsed
val effectsPage = horizonServer.effects().forAccount(accountId).execute()
assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
```

### Pattern 4: Error Handling (404)

```kotlin
// Verify account no longer exists
try {
    horizonServer.accounts().account(accountId)
    fail("Account should not exist")
} catch (e: Exception) {
    assertTrue(
        e.message?.contains("404") == true ||
        e.message?.contains("not found") == true,
        "Should get 404 error"
    )
}
```

### Pattern 5: Streaming Events

```kotlin
val stream = horizonServer.transactions()
    .forAccount(accountId)
    .cursor("now")
    .stream(
        serializer = TransactionResponse.serializer(),
        listener = object : EventListener<TransactionResponse> {
            override fun onEvent(event: TransactionResponse) {
                // Handle event
            }

            override fun onFailure(error: Throwable?, responseCode: Int?) {
                // Handle failure
            }
        }
    )

try {
    // Test logic
    delay(30000) // Wait for events
} finally {
    stream.close()
}
```

## Critical Gotchas

### 1. Asset Types
❌ **Wrong**: `Asset.NATIVE` (class instantiation)
✅ **Correct**: `AssetTypeNative` (data object)

### 2. Suspend Functions
All crypto operations and network calls are `suspend` functions:
- `KeyPair.random()`
- `KeyPair.fromSecretSeed()`
- `transaction.sign()`
- `horizonServer.submitTransaction()`
- `horizonServer.accounts().account()`
- `FriendBot.fundTestnetAccount()`

### 3. Test Execution Context
- **Unit tests with mocks**: Use `runTest` (virtual time)
- **Integration tests with real network**: Use `runBlocking` (real time)
- **Streaming tests**: MUST use `runBlocking` (real-time events)

### 4. Network Configuration
```kotlin
private val testOn = "testnet" // or "futurenet"
private val horizonServer = if (testOn == "testnet") {
    HorizonServer("https://horizon-testnet.stellar.org")
} else {
    HorizonServer("https://horizon-futurenet.stellar.org")
}
private val network = if (testOn == "testnet") {
    Network.TESTNET
} else {
    Network.FUTURENET
}
```

### 5. Base Fee
❌ **Wrong**: `TransactionBuilder.BASE_FEE`
✅ **Correct**: `AbstractTransaction.MIN_BASE_FEE`

### 6. Signer Keys
❌ **Wrong**: `SignerKey.Ed25519(publicKey)`
✅ **Correct**: `SignerKey.ed25519PublicKey(publicKey)`

### 7. Transaction Envelope
❌ **Wrong**: `horizonServer.submitTransaction(transaction)`
✅ **Correct**: `horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())`

### 8. Delays
Always add delays after network operations:
```kotlin
FriendBot.fundTestnetAccount(accountId)
delay(3000) // Wait for account creation

horizonServer.submitTransaction(tx)
delay(3000) // Wait for transaction processing
```

### 9. JSON Deserialization (for streaming)
The platform-specific `deserializeJson` functions need:
```kotlin
val jsonConfig = Json {
    ignoreUnknownKeys = true  // Critical for Horizon responses
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}
```

### 10. Response Field Optionality
Make fields optional if Horizon doesn't always return them:
```kotlin
@Serializable
data class TransactionResponse(
    val envelopeXdr: String? = null,  // Optional, not always returned
    val resultXdr: String? = null,     // Optional
    // ...
)
```

## Test Structure

```kotlin
package com.stellar.sdk.integrationTests

import com.stellar.sdk.*
import com.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for [Feature]-related operations.
 *
 * These tests verify the SDK's [feature] operations against a live Stellar testnet.
 */
class FeatureIntegrationTest {

    private val testOn = "testnet" // or "futurenet"
    private val horizonServer = if (testOn == "testnet") {
        HorizonServer("https://horizon-testnet.stellar.org")
    } else {
        HorizonServer("https://horizon-futurenet.stellar.org")
    }
    private val network = if (testOn == "testnet") {
        Network.TESTNET
    } else {
        Network.FUTURENET
    }

    /**
     * Test description here.
     *
     * This test:
     * 1. Step 1
     * 2. Step 2
     * 3. Step 3
     */
    @Test
    fun testSomething() = runBlocking {
        // Test implementation
    }
}
```

## Best Practices

### 1. Test Documentation
- Add KDoc to each test explaining what it does
- List the steps the test performs
- Reference the Flutter SDK test if porting

### 2. Descriptive Test Names
Use `testFeatureDescription` format:
- ✅ `testSetAccountOptions`
- ✅ `testAccountMergeMuxedAccounts`
- ✅ `testStreamTransactionsForAccount`
- ❌ `testAccount`
- ❌ `test1`

### 3. Clear Assertions
Use descriptive messages:
```kotlin
assertTrue(response.successful, "Transaction should succeed")
assertEquals(expected, actual, "Should have correct value")
assertNotNull(value, "Value should not be null")
```

### 4. Resource Cleanup
Always clean up resources:
```kotlin
try {
    // Test code
} finally {
    stream?.close()
    // Other cleanup
}
```

### 5. Timeouts
Set appropriate timeouts:
```kotlin
@Test
fun testSimpleOperation() = runBlocking {
    withTimeout(30_000) { // 30 seconds
        // Test code
    }
}

@Test
fun testStreamingOperation() = runBlocking {
    withTimeout(90_000) { // 90 seconds for streaming
        // Test code
    }
}
```

## Debugging Tips

### 1. Enable Verbose Logging
```kotlin
println("Account ID: $accountId")
println("Transaction hash: ${response.hash}")
println("Sequence number: ${account.sequenceNumber}")
```

### 2. Check Network Status
- Testnet: https://status.stellar.org/
- FriendBot: https://friendbot.stellar.org/

### 3. Verify in Stellar Laboratory
- https://laboratory.stellar.org/
- Look up accounts, transactions, operations

### 4. Common Failures
- **Timeout**: Increase delay after operations
- **404**: Account not funded yet, add longer delay
- **Insufficient balance**: Check account has enough XLM
- **Invalid sequence**: Reload account before building transaction
- **Deserialization error**: Check response class has all fields (use `ignoreUnknownKeys = true`)

## Porting Checklist

When porting a new integration test:

- [ ] Read the Flutter test completely
- [ ] Identify all operations used
- [ ] Verify KMP SDK has all required operations
- [ ] Create test file in `integrationTests/` folder
- [ ] Use `runBlocking` for real network tests
- [ ] Add FriendBot funding with delays
- [ ] Convert async/await to suspend functions
- [ ] Update type mappings (BigInt → Long, etc.)
- [ ] Convert assertions (assert → assertTrue/assertEquals)
- [ ] Add delays after network operations (3+ seconds)
- [ ] Test operations and effects parsing
- [ ] Add descriptive test documentation
- [ ] Do NOT add @Ignore annotation
- [ ] Run test to verify it passes
- [ ] Update `INTEGRATION_TESTS_README.md` with new test

## References

- **Flutter SDK**: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/`
- **Java SDK**: `/Users/chris/projects/Stellar/java-stellar-sdk` (reference implementation)
- **KMP SDK**: `/Users/chris/projects/Stellar/kmp/kmp-stellar-sdk`
- **Stellar Docs**: https://developers.stellar.org/
- **Horizon API**: https://developers.stellar.org/api/horizon

## Examples

### Successfully Ported Tests

1. **AccountIntegrationTest.kt** (9 tests) - Based on `account_test.dart`
   - All account operations
   - Muxed accounts
   - Account data endpoint
   - Transaction streaming

### Tests To Port

Refer to Flutter SDK test folder for complete list. Priority tests:
- `transaction_test.dart` - Transaction building and signing
- `operations_test.dart` - All Stellar operations
- `payment_test.dart` - Payment flows
- `assets_test.dart` - Asset operations
- `claimable_balances_test.dart` - Claimable balances

## Support

If you encounter issues porting tests:
1. Check this CLAUDE.md for patterns
2. Review existing `AccountIntegrationTest.kt` for examples
3. Check `INTEGRATION_TESTS_README.md` for troubleshooting
4. Verify SDK implementation matches Java SDK
5. Test with Stellar Laboratory for manual verification
