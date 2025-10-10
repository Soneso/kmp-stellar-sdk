# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin Multiplatform (KMP) project for building a Stellar SDK. The SDK provides APIs to:
- Build Stellar transactions
- Connect to Horizon (Stellar's API server)
- Connect to Stellar RPC Server

## Current State

The KMP project structure is now set up with:
- Gradle configuration for Kotlin Multiplatform
- Platform targets: JVM, JS (Browser & Node.js), iOS (iosX64, iosArm64, iosSimulatorArm64), macOS (macosX64, macosArm64)
- Source sets configured for common and platform-specific code
- Production-ready cryptography on all platforms
- Comprehensive dependencies for networking, serialization, and coroutines

## Architecture Notes

### Cryptographic Implementation

The SDK uses **production-ready, audited cryptographic libraries** - no custom/experimental crypto:

#### JVM Platform
- **Library**: BouncyCastle (`org.bouncycastle:bcprov-jdk18on:1.78`)
- **Algorithm**: Ed25519 (RFC 8032 compliant)
- **Implementation**: `Ed25519Signer`, `Ed25519PrivateKeyParameters`, `Ed25519PublicKeyParameters`
- **Security**: Mature, widely-audited, constant-time operations
- **Provider**: Registered as JCA security provider on initialization

#### Native Platforms (iOS/macOS)
- **Library**: libsodium (via C interop)
- **Algorithm**: Ed25519 (`crypto_sign_*` functions)
- **Implementation**: `crypto_sign_seed_keypair`, `crypto_sign_detached`, `crypto_sign_verify_detached`
- **Security**: Audited, constant-time, memory-safe operations
- **Random**: `randombytes_buf()` using system CSPRNG (`arc4random_buf()`)
- **Distribution**:
  - Framework build: Uses static libsodium from `native-libs/libsodium-ios/`
  - User apps: Add libsodium via Swift Package Manager (Clibsodium)
  - No Homebrew installation required for iOS apps

#### JavaScript Platforms (Browser & Node.js)
- **Library**: libsodium-wrappers (0.7.13 via npm)
  - Same audited C library compiled to WebAssembly
  - Universal compatibility (all browsers, Node.js)
  - **Automatic initialization**: SDK handles libsodium initialization internally
- **Algorithm**: Ed25519 (`crypto_sign_*` functions)
- **Random**: `crypto.getRandomValues()` (CSPRNG) for key generation
- **Security**: Audited library, same as native implementation
- **Installation**: Declared as npm dependency, bundled automatically

#### Base32 Encoding (StrKey)
- **JVM**: Apache Commons Codec (`commons-codec:commons-codec:1.16.1`)
- **JS**: Pure Kotlin implementation (not security-critical)
- **Native**: Pure Kotlin implementation (not security-critical)

### Security Principles

1. **No Experimental Crypto**: Only battle-tested, audited libraries
2. **Constant-Time Operations**: Protection against timing attacks
3. **Memory Safety**:
   - Defensive copies of all keys
   - CharArray for secrets (can be zeroed)
   - Proper cleanup in native code
4. **Input Validation**: All inputs validated before crypto operations
5. **Error Handling**: Comprehensive validation with clear error messages

### Async API Design

The SDK uses Kotlin's `suspend` functions for cryptographic operations to properly support JavaScript's async libsodium initialization while maintaining zero overhead on JVM and Native platforms.

#### Why Suspend Functions?

- **JavaScript**: libsodium requires async initialization - suspend functions allow proper event loop integration
- **JVM/Native**: Zero overhead - suspend functions that don't actually suspend compile to regular functions
- **Consistent API**: Same async pattern works correctly on all platforms
- **Coroutine-friendly**: Natural integration with Kotlin coroutines ecosystem

#### Which Methods Are Suspend?

**KeyPair - Suspend (crypto operations)**:
- `suspend fun random(): KeyPair`
- `suspend fun fromSecretSeed(seed: String): KeyPair`
- `suspend fun fromSecretSeed(seed: CharArray): KeyPair`
- `suspend fun fromSecretSeed(seed: ByteArray): KeyPair`
- `suspend fun sign(data: ByteArray): ByteArray`
- `suspend fun signDecorated(data: ByteArray): DecoratedSignature`
- `suspend fun verify(data: ByteArray, signature: ByteArray): Boolean`

**KeyPair - Not Suspend (data operations)**:
- `fun fromAccountId(accountId: String): KeyPair`
- `fun fromPublicKey(publicKey: ByteArray): KeyPair`
- `fun getCryptoLibraryName(): String`
- `fun canSign(): Boolean`
- `fun getAccountId(): String`
- `fun getSecretSeed(): CharArray?`
- `fun getPublicKey(): ByteArray`

**Transactions**:
- `suspend fun sign(signer: KeyPair)` - Transaction and FeeBumpTransaction signing

#### Usage Examples

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

// In JavaScript/Web
MainScope().launch {
    val keypair = KeyPair.random()
}
```

### Code Organization

- `commonMain`: Shared Stellar protocol logic, StrKey, KeyPair API
- `jvmMain`: JVM-specific crypto (BouncyCastle), Base32 (Apache Commons)
- `jsMain`: JS-specific crypto (libsodium-wrappers with automatic initialization)
- `nativeMain`: Native crypto (libsodium), shared by iOS/macOS
- Platform-specific networking goes in respective source sets
- XDR types will be central to transaction handling

## Development Commands

### Building
- **Build all**: `./gradlew build`
- **Clean build**: `./gradlew clean build`
- **Assemble artifacts**: `./gradlew assemble`
- **Check (build + test)**: `./gradlew check`

### Testing
- **Run all tests**: `./gradlew test`
- **Run JVM tests**: `./gradlew jvmTest`
- **Run single test class**: `./gradlew :stellar-sdk:jvmTest --tests "KeyPairTest"`
- **Run single test method**: `./gradlew :stellar-sdk:jvmTest --tests "KeyPairTest.testRandomKeyPair"`
- **Run tests with pattern**: `./gradlew :stellar-sdk:jvmTest --tests "*Key*"`
- **Run JS tests (Browser)**: `./gradlew jsBrowserTest` (requires Chrome)
- **Run JS tests (Node.js)**:
  - Individual test class: `./gradlew :stellar-sdk:jsNodeTest --tests "KeyPairTest"`
  - Pattern matching: `./gradlew :stellar-sdk:jsNodeTest --tests "*Key*"`
  - **Note**: Running all Node.js tests together currently hangs (see JS Testing Notes below)
- **Run macOS tests**: `./gradlew macosArm64Test` or `./gradlew macosX64Test`
- **Run iOS Simulator tests**: `./gradlew iosSimulatorArm64Test` or `./gradlew iosX64Test`

#### JS Testing Notes

**Current Status**: Individual test classes work perfectly on both Node.js and Browser, but running all tests together fails.

**Working Approach**:
```bash
# Node.js - Run specific test classes
./gradlew :stellar-sdk:jsNodeTest --tests "KeyPairTest"
./gradlew :stellar-sdk:jsNodeTest --tests "StrKeyTest"

# Browser - Run specific test classes
./gradlew :stellar-sdk:jsBrowserTest --tests "KeyPairTest"
./gradlew :stellar-sdk:jsBrowserTest --tests "StrKeyTest"

# Or use patterns
./gradlew :stellar-sdk:jsNodeTest --tests "*Test"
./gradlew :stellar-sdk:jsBrowserTest --tests "*Test"
```

**Why This Happens**:
- ✅ Libsodium initialization works correctly
- ✅ Individual async tests pass (including crypto tests)
- ✅ NODE_PATH and Karma are properly configured
- ⚠️ Running all tests in a single bundle causes failures (likely Kotlin/JS test bundling issue)
- **Node.js**: Tests hang (Kotlin/JS + Mocha interaction)
- **Browser**: Webpack fails with crypto module errors when bundling all tests

**Investigation Done**:
- Attempted Mocha `--no-parallel` configuration for Node.js
- Added webpack fallback configuration for browser (helps but doesn't fully resolve)
- Tried `.mocharc.js` with sequential settings
- Confirmed not a timeout issue (individual tests complete quickly)
- Root cause appears to be test bundling/compilation interaction in Kotlin/JS plugin

**Recommendation**: Use test filtering (common pattern for large test suites) or run test classes individually in CI/CD. This is a Kotlin/JS tooling limitation, not an SDK issue - the web sample app proves browser compatibility works perfectly.

#### Integration Tests

The SDK includes comprehensive integration tests that validate against a live Stellar Testnet:

- **Location**: `stellar-sdk/src/commonTest/kotlin/com/stellar/sdk/contract/ContractClientIntegrationTest.kt`
- **Documentation**: See `stellar-sdk/src/commonTest/kotlin/com/stellar/sdk/contract/INTEGRATION_TESTS_README.md` for detailed setup
- **Status**: Integration tests are `@Ignore`d by default (require testnet account funding)
- **Coverage**: ContractClient, AssembledTransaction, authorization, state restoration, error handling, polling, custom result parsing
- **Run tests**: Remove `@Ignore` annotation, then run:
  ```bash
  ./gradlew :stellar-sdk:jvmTest --tests "ContractClientIntegrationTest"
  ```

**Prerequisites**:
- Testnet connectivity to `https://soroban-testnet.stellar.org:443`
- Funded test account via Friendbot
- Pre-deployed contract (contract ID provided in tests)

**Duration**: 3-5 minutes for full suite (network latency dependent)

### Sample Apps

The `stellarSample` directory demonstrates **proper KMP architecture** with shared business logic and platform-specific UIs:

- **Shared module** (`stellarSample/shared`): Common Kotlin business logic (KeyPair operations, signing, verification)
- **Android** (`stellarSample/androidApp`): Jetpack Compose UI
  ```bash
  ./gradlew :stellarSample:androidApp:installDebug
  ```
- **iOS** (`stellarSample/iosApp`): SwiftUI
  ```bash
  ./gradlew :stellarSample:shared:linkDebugFrameworkIosSimulatorArm64
  cd stellarSample/iosApp && xcodegen generate && open StellarSample.xcodeproj
  ```
- **macOS** (`stellarSample/macosApp`): SwiftUI (requires `brew install libsodium`)
  ```bash
  ./gradlew :stellarSample:shared:linkDebugFrameworkMacosArm64
  cd stellarSample/macosApp && xcodegen generate && open StellarSampleMac.xcodeproj
  ```
- **Web** (`stellarSample/webApp`): Kotlin/JS with HTML
  ```bash
  # Development mode
  ./gradlew :stellarSample:webApp:jsBrowserDevelopmentRun
  # Production build
  ./gradlew :stellarSample:webApp:jsBrowserProductionWebpack
  ```

**Architecture**: ~500 lines of shared business logic written once, runs on all platforms with platform-native UIs (200 lines each). See `stellarSample/README.md` for architecture details and code examples.

### Native Development
- **Build iOS framework**: `./gradlew :stellar-sdk:linkDebugFrameworkIosSimulatorArm64`
- **Build libsodium XCFramework**: `./build-libsodium-xcframework.sh` (for iOS framework distribution)
- **Build SDK XCFramework**: `./build-xcframework.sh` (for SDK framework distribution)
- **Note**: End-user iOS apps should add libsodium via Swift Package Manager (Clibsodium), not via Homebrew

## Module Structure

- **stellar-sdk**: Main library module containing the Stellar SDK implementation
  - `commonMain`: Shared Kotlin code for all platforms
  - `commonTest`: Shared test code
  - `jvmMain`: JVM-specific implementations
  - `jvmTest`: JVM-specific tests
  - `jsMain`: JavaScript-specific implementations
  - `jsTest`: JavaScript-specific tests
  - `nativeMain`: Shared native code (libsodium interop)
  - `iosMain`: iOS-specific implementations (shared across iosX64, iosArm64, iosSimulatorArm64)
  - `iosTest`: iOS-specific tests
  - `macosMain`: macOS-specific implementations (useful for local development)
  - `macosTest`: macOS-specific tests

- **stellarSample**: KMP sample application demonstrating shared business logic
  - `shared`: Shared Kotlin module with StellarDemo class and tests
  - `androidApp`: Android app with Jetpack Compose UI
  - `iosApp`: iOS app with SwiftUI (Xcode project)
  - `webApp`: Web app with Kotlin/JS and HTML

## Dependencies

### Common
- **kotlinx-serialization**: JSON serialization for API responses and transaction data
- **kotlinx-coroutines**: Async operations for network calls
- **kotlinx-datetime**: Date/time handling for Stellar timestamps

### JVM
- **ktor-client-cio**: HTTP client for JVM
- **BouncyCastle** (`org.bouncycastle:bcprov-jdk18on:1.78`): Ed25519 cryptography
- **Apache Commons Codec** (`commons-codec:commons-codec:1.16.1`): Base32 encoding

### JavaScript (Browser & Node.js)
- **ktor-client-js**: HTTP client for JavaScript
- **libsodium-wrappers** (0.7.13 via npm): Ed25519 cryptography with automatic async initialization
- **kotlinx-coroutines-core**: Required for async crypto operations

### Native (iOS/macOS)
- **ktor-client-darwin**: HTTP client for Apple platforms
- **libsodium**: Ed25519 cryptography (via C interop)
  - Framework build: Uses static libsodium from `native-libs/libsodium-ios/`
  - User apps: Add libsodium via Swift Package Manager (Clibsodium package)
  - No Homebrew installation required for iOS apps

## Implemented Features

### KeyPair (`com.stellar.sdk.KeyPair`)
- ✅ Generate random keypairs with cryptographically secure randomness
- ✅ Create from secret seed (String, CharArray, or ByteArray)
- ✅ Create from account ID (public key only)
- ✅ Create from raw public key bytes
- ✅ Sign data with Ed25519
- ✅ Verify Ed25519 signatures
- ✅ Export to strkey format (G... for accounts, S... for seeds)
- ✅ Comprehensive input validation and error handling
- ✅ Thread-safe, immutable design
- ✅ **Async API**: Crypto operations use `suspend` functions for proper JavaScript support

### StrKey (`com.stellar.sdk.StrKey`)
- ✅ Encode/decode Ed25519 public keys (G...)
- ✅ Encode/decode Ed25519 secret seeds (S...)
- ✅ CRC16-XModem checksum validation
- ✅ Version byte validation
- ✅ Base32 encoding (platform-specific)

### Auth (`com.stellar.sdk.Auth`)
- ✅ Sign Soroban authorization entries for smart contract invocations
- ✅ Build new authorization entries from scratch
- ✅ Support for custom Signer interface (hardware wallets, multi-sig)
- ✅ Network replay protection (network ID included in signatures)
- ✅ Signature verification after signing
- ✅ Immutable design (clones entries to avoid mutation)
- ✅ **Async API**: All methods use `suspend` functions for proper JavaScript support
- ✅ **Cross-platform compatible**: Signatures identical across JVM, JS, and Native
- ✅ **Java SDK compatible**: Same test vectors, identical behavior

### ContractClient & AssembledTransaction (`com.stellar.sdk.contract.*`)
- ✅ High-level API for Soroban smart contract interactions
- ✅ Full transaction lifecycle management (simulate → sign → submit → parse)
- ✅ Type-safe generic results with custom parser support
- ✅ Automatic simulation and resource estimation
- ✅ Read-only vs write call detection
- ✅ Authorization handling (auto-auth and custom auth)
- ✅ Automatic state restoration when needed
- ✅ Transaction status polling with exponential backoff
- ✅ Comprehensive error handling with 10 exception types
- ✅ **Developer-friendly**: Reduces contract calls from ~20 lines to 2-3 lines
- ✅ **Async API**: All methods use `suspend` functions
- ✅ **Cross-platform compatible**: Works on JVM, JS, iOS, macOS
- ✅ **Production-ready**: Full feature parity with Java SDK

## Testing

All cryptographic operations have comprehensive test coverage:
- Round-trip encoding/decoding
- Known test vectors from Java Stellar SDK
- Sign/verify operations
- Error handling and edge cases
- Input validation

Run tests:
- JVM: `./gradlew :stellar-sdk:jvmTest`
- Native: `./gradlew :stellar-sdk:macosArm64Test` or `./gradlew :stellar-sdk:iosSimulatorArm64Test`
- **Note**: All tests use `runTest { }` wrapper for suspend function support

Sample app tests:
- macOS tests: `./gradlew :stellarSample:shared:macosArm64Test`
- **Note**: Sample app demonstrates async KeyPair API usage with coroutines

## Reference Implementation

When implementing features, use the Java Stellar SDK as a reference:
- Located at: `/Users/chris/projects/Stellar/java-stellar-sdk`
- Provides production-tested implementations of Stellar protocols
- Use as a guide for API design and feature completeness