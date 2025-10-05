# Implementation Plan: Async KeyPair API (Solution B)

**Status**: Active Implementation Plan
**Date**: 2025-10-05
**Goal**: Make KeyPair API async-friendly with `suspend` functions to properly support JavaScript's async libsodium initialization

---

## Executive Summary

Convert the KeyPair API from synchronous to asynchronous using Kotlin's `suspend` functions. This allows JavaScript to properly handle async libsodium initialization without blocking the event loop, while maintaining zero performance cost on JVM and Native platforms.

---

## Why This Approach?

### Problem with Current Approach
- JavaScript's libsodium requires async initialization
- Cannot make async code truly synchronous in JS without blocking event loop
- Blocked event loop prevents promises from resolving → infinite hang
- Busy-waiting doesn't work in single-threaded JavaScript

### Why Suspend Functions Solve This
1. **JS**: Properly yields to event loop, allows promises to resolve
2. **JVM/Native**: Zero overhead - `suspend` functions that don't suspend compile to regular functions
3. **Test Framework**: `runTest` handles async tests correctly on all platforms
4. **Architecturally Correct**: Async operations are properly modeled as async

### Why Now?
- SDK is new, no external users yet (only sample app)
- Breaking change is acceptable
- Future-proof architecture for other async operations (network, etc.)

---

## Architecture Overview

### Current Architecture
```
Common API (sync) ←→ Platform Implementations (sync/async mismatch on JS)
     ↓
  JS tries to fake sync with busy-wait → Event loop blocked → Hang
```

### New Architecture
```
Common API (suspend) ←→ Platform Implementations (properly async/sync)
     ↓
  JS: Actually suspends, event loop processes promises ✅
  JVM/Native: suspend = no-op, compiles to regular functions ✅
```

---

## Implementation Steps

### Phase 1: Update Common API (Breaking Changes)

#### 1.1 Update Ed25519Crypto Interface
**File**: `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/crypto/Ed25519.kt`

**Changes**:
```kotlin
interface Ed25519Crypto {
    val libraryName: String

    suspend fun generatePrivateKey(): ByteArray  // ← Add suspend
    suspend fun derivePublicKey(privateKey: ByteArray): ByteArray  // ← Add suspend
    suspend fun sign(data: ByteArray, privateKey: ByteArray): ByteArray  // ← Add suspend
    suspend fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean  // ← Add suspend
}
```

**Rationale**: Making the crypto interface async allows platform implementations to be truly async where needed.

---

#### 1.2 Update KeyPair Class
**File**: `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/KeyPair.kt`

**Changes**:
```kotlin
class KeyPair private constructor(...) {
    companion object {
        suspend fun fromSecretSeed(seed: CharArray): KeyPair  // ← Add suspend
        suspend fun fromSecretSeed(seed: String): KeyPair  // ← Add suspend
        suspend fun fromSecretSeed(seed: ByteArray): KeyPair  // ← Add suspend
        suspend fun random(): KeyPair  // ← Add suspend

        // These remain sync (no crypto operations)
        fun fromAccountId(accountId: String): KeyPair
        fun fromPublicKey(publicKey: ByteArray): KeyPair
        fun getCryptoLibraryName(): String
    }

    // Instance methods
    suspend fun sign(data: ByteArray): ByteArray  // ← Add suspend
    suspend fun signDecorated(data: ByteArray): DecoratedSignature  // ← Add suspend
    suspend fun verify(data: ByteArray, signature: ByteArray): Boolean  // ← Add suspend

    // These remain sync (no crypto operations)
    fun canSign(): Boolean
    fun getAccountId(): String
    fun getSecretSeed(): CharArray?
    fun getPublicKey(): ByteArray
    fun getXdrAccountId(): AccountIDXdr
}
```

**Key Decisions**:
- ✅ **Add suspend**: Methods that perform crypto operations (key generation, signing, verification)
- ❌ **Keep sync**: Methods that just read/format data (getAccountId, canSign, etc.)

---

### Phase 2: Update Platform Implementations

#### 2.1 JVM Implementation (No Real Changes)
**File**: `stellar-sdk/src/jvmMain/kotlin/com/stellar/sdk/crypto/Ed25519.jvm.kt`

**Changes**: Just add `suspend` keyword - implementation stays the same

```kotlin
internal class JvmEd25519Crypto : Ed25519Crypto {
    override suspend fun generatePrivateKey(): ByteArray {
        // Same BouncyCastle code, just marked suspend
        // No actual suspension - returns immediately
    }
    // ... same for other methods
}
```

**Zero Performance Cost**: Kotlin compiler optimizes away `suspend` when function doesn't actually suspend.

---

#### 2.2 Native Implementation (No Real Changes)
**File**: `stellar-sdk/src/nativeMain/kotlin/com/stellar/sdk/crypto/Ed25519.native.kt`

**Changes**: Just add `suspend` keyword - implementation stays the same

```kotlin
internal class NativeEd25519Crypto : Ed25519Crypto {
    override suspend fun generatePrivateKey(): ByteArray {
        // Same libsodium C interop code, just marked suspend
        // No actual suspension - returns immediately
    }
    // ... same for other methods
}
```

---

#### 2.3 JavaScript Implementation (Major Changes)
**File**: `stellar-sdk/src/jsMain/kotlin/com/stellar/sdk/crypto/Ed25519.js.kt`

**Remove**: Synchronous wrapper (`JsEd25519Crypto` class with busy-wait)

**Keep**: Async implementation (`JsEd25519CryptoAsync`) but rename it

**Changes**:
```kotlin
// Rename JsEd25519CryptoAsync → JsEd25519Crypto
internal class JsEd25519Crypto : Ed25519Crypto {
    override suspend fun generatePrivateKey(): ByteArray {
        // No need to initialize for random generation
        // Use Web Crypto API directly
    }

    override suspend fun derivePublicKey(privateKey: ByteArray): ByteArray {
        LibsodiumInit.ensureInitialized()  // ← Actually suspends properly!
        val sodium = LibsodiumInit.getSodium()
        // ... use sodium
    }

    // ... same for sign() and verify()
}

actual fun getEd25519Crypto(): Ed25519Crypto = JsEd25519Crypto()
```

**Why This Works**: The `suspend` modifier allows `ensureInitialized()` to properly yield to the event loop while waiting for libsodium's promise to resolve.

---

### Phase 3: Update All Tests

#### 3.1 Common Tests
**File**: `stellar-sdk/src/commonTest/kotlin/com/stellar/sdk/KeyPairTest.kt`

**Changes**: Wrap all tests with `runTest { }`

```kotlin
class KeyPairTest {
    @Test
    fun testRandomGeneration() = runTest {  // ← Add runTest
        val keypair = KeyPair.random()  // ← Now suspend
        assertNotNull(keypair)
    }

    @Test
    fun testFromSecretSeed() = runTest {  // ← Add runTest
        val seed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        val keypair = KeyPair.fromSecretSeed(seed)  // ← Now suspend
        // ...
    }

    @Test
    fun testSignAndVerify() = runTest {  // ← Add runTest
        val keypair = KeyPair.random()  // ← Now suspend
        val data = "test".encodeToByteArray()
        val signature = keypair.sign(data)  // ← Now suspend
        val valid = keypair.verify(data, signature)  // ← Now suspend
        assertTrue(valid)
    }

    // Apply to ALL test methods that use KeyPair
}
```

**Pattern**:
- Every `@Test` function that calls KeyPair methods with crypto operations becomes `= runTest { }`
- Tests that only call non-suspend methods (like `fromAccountId`, `getAccountId`) don't need changes

---

#### 3.2 Platform-Specific Tests
Apply the same `runTest` pattern to all platform-specific tests:
- `stellar-sdk/src/jvmTest/...`
- `stellar-sdk/src/jsTest/...` (if any exist)
- Native tests (if any exist)

---

#### 3.3 Other Component Tests
Update all tests that use KeyPair:
- ✅ `TransactionTest.kt`
- ✅ `FeeBumpTransactionTest.kt`
- ✅ `SignerKeyTest.kt`
- ✅ `AccountTest.kt`
- ✅ Any other tests that create/use KeyPairs

**Search Pattern**: `KeyPair.random()`, `KeyPair.fromSecretSeed()`

---

### Phase 4: Update Sample Applications

#### 4.1 Shared Sample Logic
**File**: `stellarSample/shared/src/commonMain/kotlin/com/soneso/sample/StellarDemo.kt`

**Changes**: Make methods suspend

```kotlin
class StellarDemo {
    private var currentKeyPair: KeyPair? = null

    suspend fun generateRandomKeyPair(): KeyPairInfo {  // ← Add suspend
        val keypair = KeyPair.random()  // ← Now works properly
        currentKeyPair = keypair
        return KeyPairInfo(...)
    }

    suspend fun createFromSeed(seed: String): Result<KeyPairInfo> {  // ← Add suspend
        return try {
            val keypair = KeyPair.fromSecretSeed(seed)
            // ...
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signMessage(message: String): Result<SignatureResult> {  // ← Add suspend
        val kp = currentKeyPair ?: return Result.failure(...)
        val signature = kp.sign(data)  // ← Now suspend
        // ...
    }

    suspend fun runTestSuite(): List<TestResult> {  // ← Add suspend
        return listOf(
            testRandomGeneration(),  // All test methods also suspend
            testFromSeed(),
            // ...
        )
    }

    private suspend fun testRandomGeneration(): TestResult { // ← Add suspend
        val info = generateRandomKeyPair()
        // ...
    }

    // ... all private test methods also become suspend
}
```

---

#### 4.2 Shared Sample Tests
**File**: `stellarSample/shared/src/commonTest/kotlin/...`

**Changes**: Wrap with `runTest`

```kotlin
class StellarDemoTest {
    @Test
    fun testGenerateRandom() = runTest {  // ← Add runTest
        val demo = StellarDemo()
        val info = demo.generateRandomKeyPair()  // ← Now suspend
        // ...
    }
}
```

---

#### 4.3 Web App
**File**: `stellarSample/webApp/src/jsMain/kotlin/com/soneso/web/Main.kt`

**Current (Manual Initialization)**:
```kotlin
@JsModule("libsodium-wrappers")
@JsNonModule
external val sodium: dynamic

fun main() {
    sodium.ready.then {
        window._sodium = sodium
        initializeApp()
    }
}
```

**After (SDK Handles It)**:
```kotlin
// Remove manual libsodium import and initialization!

fun main() {
    // Launch coroutine for async initialization
    MainScope().launch {
        initializeApp()
    }
}

suspend fun initializeApp() {  // ← Now suspend
    val root = document.getElementById("root") as? HTMLDivElement ?: return

    root.innerHTML = ""
    root.append {
        // ... UI setup
    }
}

// Button handlers become suspend
suspend fun generateRandom() {  // ← Add suspend
    try {
        currentKeypair = demo.generateRandomKeyPair()  // ← Now suspend
        displayKeypair(currentKeypair!!)
    } catch (e: Exception) {
        showError("keypair-info", "Error: ${e.message}")
    }
}

// Call from UI using coroutine
button(classes = "btn btn-primary") {
    +"Generate Random"
    onClickFunction = {
        MainScope().launch {  // ← Launch coroutine
            generateRandom()
        }
    }
}
```

**Benefits**:
- ✅ No manual libsodium initialization
- ✅ SDK handles everything automatically
- ✅ Cleaner, simpler code
- ✅ No global state pollution

---

#### 4.4 Android App
**File**: `stellarSample/androidApp/...`

**Changes**: Minimal - can use `lifecycleScope` or `viewModelScope`

```kotlin
// Before (still works on JVM without suspend)
fun onCreate() {
    val keypair = KeyPair.random()  // Works but shows warning
}

// After (proper coroutine usage)
fun onCreate() {
    lifecycleScope.launch {
        val keypair = KeyPair.random()  // ← suspend call
        // ...
    }
}

// Or in ViewModel
class MyViewModel : ViewModel() {
    fun generateKeypair() {
        viewModelScope.launch {
            val keypair = KeyPair.random()
            // ...
        }
    }
}
```

**Note**: On JVM, the suspend function doesn't actually suspend, so minimal impact.

---

#### 4.5 iOS App
**File**: `stellarSample/iosApp/...` (Swift)

**Changes**: Use async/await bridge if available, or callback-based approach

Swift doesn't directly support Kotlin suspend functions (yet), but options:
1. Wrap in callback-based API for Swift
2. Use Kotlin/Native async support (experimental)
3. Keep it synchronous for now (works on native)

**Simplest**: Since Native doesn't actually suspend, Swift code continues to work as-is:
```swift
let keypair = KeyPair.Companion().random()  // Still synchronous on Native
```

---

### Phase 5: Update Transaction/Operation Classes

Check if any Transaction or Operation classes create KeyPairs internally:

#### Files to Check:
- `Transaction.kt`
- `FeeBumpTransaction.kt`
- `TransactionBuilder.kt`
- Any operation classes that might sign

**If they create KeyPairs**: Make those methods `suspend`
**If they only use existing KeyPairs**: No changes needed

---

### Phase 6: Documentation Updates

#### 6.1 Update CLAUDE.md
**File**: `CLAUDE.md`

**Add Section**:
```markdown
## Async API Design

The Stellar SDK uses Kotlin's `suspend` functions for cryptographic operations
to properly support JavaScript's async libsodium initialization.

### Why Suspend?

- **JavaScript**: libsodium requires async initialization
- **JVM/Native**: Zero overhead - suspend functions compile to regular functions
- **Consistent API**: Same async pattern works on all platforms

### Usage

```kotlin
// In a coroutine
suspend fun example() {
    val keypair = KeyPair.random()  // Suspend function
    val signature = keypair.sign(data)
}

// In tests
@Test
fun test() = runTest {
    val keypair = KeyPair.random()
    // ...
}

// In Android
lifecycleScope.launch {
    val keypair = KeyPair.random()
}

// In JS
MainScope().launch {
    val keypair = KeyPair.random()
}
```

### Which Methods are Suspend?

**Suspend (crypto operations)**:
- `KeyPair.random()`
- `KeyPair.fromSecretSeed(...)`
- `KeyPair.sign(...)`
- `KeyPair.signDecorated(...)`
- `KeyPair.verify(...)`

**Not Suspend (data operations)**:
- `KeyPair.fromAccountId(...)`
- `KeyPair.fromPublicKey(...)`
- `keypair.getAccountId()`
- `keypair.getPublicKey()`
- `keypair.canSign()`
```

---

#### 6.2 Create Migration Guide
**File**: `MIGRATION_ASYNC_API.md` (new)

```markdown
# Migration Guide: Async KeyPair API

## Overview

Version X.Y.Z introduces `suspend` functions for cryptographic operations.

## Breaking Changes

### KeyPair Methods Now Suspend

```kotlin
// Before (v0.X.X)
val keypair = KeyPair.random()

// After (v1.0.0)
suspend fun example() {
    val keypair = KeyPair.random()
}
```

### Affected Methods

- `KeyPair.random()`
- `KeyPair.fromSecretSeed(...)`
- `keypair.sign(...)`
- `keypair.signDecorated(...)`
- `keypair.verify(...)`

### Migration Steps

1. **Tests**: Wrap with `runTest { }`
2. **Android**: Use `lifecycleScope.launch { }`
3. **JavaScript**: Use `MainScope().launch { }`
4. **Server/JVM**: Use `runBlocking { }` or proper coroutine scope

## Platform-Specific Notes

### JVM
Zero performance overhead - suspend functions compile to regular functions.

### JavaScript
Properly handles async libsodium initialization automatically.

### Native (iOS/macOS)
Zero overhead - works synchronously under the hood.
```

---

#### 6.3 Update README
Add note about async API and link to migration guide.

---

### Phase 7: Testing & Validation

#### 7.1 Test Execution Order
1. ✅ JVM tests: `./gradlew jvmTest`
2. ✅ JS Node tests: `./gradlew jsNodeTest`
3. ✅ JS Browser tests: `./gradlew jsBrowserTest`
4. ✅ Native tests: `./gradlew macosArm64Test`
5. ✅ Sample app tests: `./gradlew :stellarSample:shared:allTests`

#### 7.2 Sample App Validation
1. ✅ Web app builds and runs
2. ✅ Web app can generate keypairs
3. ✅ Web app can sign/verify
4. ✅ Android app works
5. ✅ iOS app works (if applicable)

#### 7.3 Performance Validation
- Benchmark JVM performance (should be identical)
- Benchmark Native performance (should be identical)
- Check JS initialization time (first operation slightly slower, rest normal)

---

## Implementation Checklist

### Core SDK
- [ ] Update `Ed25519Crypto` interface with suspend
- [ ] Update `KeyPair` class with suspend
- [ ] Update JVM implementation (add suspend keyword)
- [ ] Update Native implementation (add suspend keyword)
- [ ] Update JS implementation (remove sync wrapper, keep async)
- [ ] Update `LibsodiumInit` (already done)

### Tests
- [ ] Update `KeyPairTest.kt` (common)
- [ ] Update all other common tests using KeyPair
- [ ] Update platform-specific tests
- [ ] Verify all tests pass on all platforms

### Sample App
- [ ] Update `StellarDemo.kt` (shared logic)
- [ ] Update `StellarDemoTest.kt` (shared tests)
- [ ] Update web app `Main.kt`
- [ ] Update Android app (if needed)
- [ ] Update iOS app (if needed)
- [ ] Test all sample apps work

### Documentation
- [ ] Update `CLAUDE.md`
- [ ] Create `MIGRATION_ASYNC_API.md`
- [ ] Update README
- [ ] Add code examples

### Cleanup
- [ ] Remove old synchronous wrapper from JS
- [ ] Remove manual libsodium initialization from web app
- [ ] Remove old implementation plan (IMPLEMENTATION_PLAN_JS_ASYNC.md)
- [ ] Verify no unused code remains

---

## Success Criteria

### Must Have
- ✅ All platform tests pass (JVM, JS Node, JS Browser, Native)
- ✅ Sample web app works without manual libsodium setup
- ✅ No performance regression on JVM/Native
- ✅ Clear documentation of async API

### Should Have
- ✅ Migration guide for users
- ✅ Code examples for each platform
- ✅ All sample apps working

### Nice to Have
- ✅ Performance benchmarks
- ✅ Comparison with Java Stellar SDK (explain why async)

---

## Timeline Estimate

**Total: 4-6 hours**

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Core SDK Updates | 1-2 hours | None |
| Test Updates | 1-2 hours | Core SDK |
| Sample App Updates | 1-2 hours | Core SDK |
| Documentation | 1 hour | All above |
| Validation & Cleanup | 1 hour | All above |

---

## Rollback Plan

If issues arise:
1. Revert to commit before changes
2. Keep LibsodiumInit module (it's useful)
3. Document that JS platform requires manual initialization
4. Plan proper async API for next major version

---

## Next Steps

1. ✅ Get approval for this plan
2. Start with Phase 1 (Update Common API)
3. Use task agents for complex refactoring
4. Test continuously after each phase
5. Update documentation as you go
