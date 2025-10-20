# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin Multiplatform (KMP) project for building a Stellar SDK. The SDK provides APIs to:
- Build Stellar transactions
- Connect to Horizon (Stellar's API server)
- Connect to Stellar RPC Server

## Current State

The SDK is in **alpha development** with comprehensive functionality implemented:

### Platform Support
- **JVM**: Android API 24+, Server applications (Java 11+)
- **iOS**: iOS 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS**: macOS 11.0+ (macosX64, macosArm64)
- **JavaScript**: Browser (WebAssembly) and Node.js 14+

### Core SDK Features (Implemented)
- **Cryptography**: Ed25519 keypair generation, signing, verification
- **StrKey Encoding**: G... (accounts), S... (seeds), M... (muxed), C... (contracts)
- **Transaction Building**: All 27 Stellar operations, TransactionBuilder, FeeBumpTransaction
- **Assets**: Native (XLM), AlphaNum4, AlphaNum12, SAC contract ID derivation
- **Accounts**: Account management, muxed accounts, sequence numbers
- **Horizon Client**: Full REST API coverage, SSE streaming, request builders
- **Soroban RPC**: Contract calls, simulation, state restoration, polling
- **High-Level API**: ContractClient, AssembledTransaction with full lifecycle
- **XDR**: Complete XDR type system and serialization

### Demo Application
- **Platforms**: Android, iOS, macOS, Desktop (JVM), Web
- **Architecture**: Compose Multiplatform with 95% code sharing
- **Features**: 7 comprehensive demos (key generation, funding, account details, trustlines, payments, contracts, deploy contract)
- **Location**: `demo/` directory with platform-specific apps

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
- **Library**: libsodium-wrappers-sumo (0.7.13 via npm)
  - Sumo build required for SHA-256 support (crypto_hash_sha256)
  - Standard build does not include SHA-256 functions
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
- **Status**: Integration tests are NOT ignored and always run with testnet connectivity (accounts are automatically funded by Friendbot)
- **Coverage**: ContractClient, AssembledTransaction, authorization, state restoration, error handling, polling, custom result parsing
- **Run tests**:
  ```bash
  ./gradlew :stellar-sdk:jvmTest --tests "ContractClientIntegrationTest"
  ```

**Prerequisites**:
- Testnet connectivity to `https://soroban-testnet.stellar.org:443`
- Test accounts are automatically funded via Friendbot
- Pre-deployed contract (contract ID provided in tests)

**Duration**: 3-5 minutes for full suite (network latency dependent)

### Demo Apps

The `demo` directory demonstrates **comprehensive SDK usage** with a Compose Multiplatform architecture:

- **Shared module** (`demo/shared`): Compose Multiplatform UI + business logic
  - 7 feature screens (key generation, funding, account details, trust asset, payments, contracts, deploy contract)
  - Platform-specific code only for clipboard access
  - Demonstrates real SDK usage patterns

- **Android** (`demo/androidApp`): Jetpack Compose entry point
  ```bash
  ./gradlew :demo:androidApp:installDebug
  ```

- **iOS** (`demo/iosApp`): SwiftUI wrapper around Compose
  ```bash
  ./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64
  cd demo/iosApp && xcodegen generate && open StellarDemo.xcodeproj
  ```

- **macOS** (`demo/macosApp`): Native SwiftUI implementation (not Compose)
  ```bash
  brew install libsodium  # Required
  ./gradlew :demo:shared:linkDebugFrameworkMacosArm64
  cd demo/macosApp && xcodegen generate && open StellarDemo.xcodeproj
  ```

- **Desktop** (`demo/desktopApp`): JVM Compose (recommended for macOS)
  ```bash
  ./gradlew :demo:desktopApp:run
  ```

- **Web** (`demo/webApp`): Kotlin/JS with Compose
  ```bash
  # Development mode
  ./gradlew :demo:webApp:jsBrowserDevelopmentRun
  # Production build (955 KB, 220 KB gzipped)
  ./gradlew :demo:webApp:jsBrowserProductionWebpack
  ```

**Key Features**:
- 95% code sharing (Compose UI + business logic in commonMain)
- Real Stellar testnet integration
- Demonstrates: KeyPair, Horizon, Soroban, transactions, assets
- See `demo/README.md` and `demo/CLAUDE.md` for architecture details

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

- **demo**: Comprehensive KMP demo application
  - `shared`: Compose Multiplatform UI + business logic (7 feature screens)
  - `androidApp`: Android entry point (Jetpack Compose)
  - `iosApp`: iOS entry point (SwiftUI wrapper for Compose)
  - `macosApp`: macOS native SwiftUI app (17 Swift files, not Compose)
  - `desktopApp`: Desktop JVM app (Compose)
  - `webApp`: Web app (Kotlin/JS with Compose)

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
- **libsodium-wrappers-sumo** (0.7.13 via npm): Ed25519 cryptography and SHA-256 with automatic async initialization
  - Sumo build required for SHA-256 support (crypto_hash_sha256)
  - Standard build does not include SHA-256 functions
- **kotlinx-coroutines-core**: Required for async crypto operations

### Native (iOS/macOS)
- **ktor-client-darwin**: HTTP client for Apple platforms
- **libsodium**: Ed25519 cryptography (via C interop)
  - Framework build: Uses static libsodium from `native-libs/libsodium-ios/`
  - User apps: Add libsodium via Swift Package Manager (Clibsodium package)
  - No Homebrew installation required for iOS apps

## Implemented Features

### Core Cryptography

#### KeyPair (`com.soneso.stellar.sdk.KeyPair`)
- ✅ Generate random keypairs with cryptographically secure randomness
- ✅ Create from secret seed (String, CharArray, or ByteArray)
- ✅ Create from account ID (public key only)
- ✅ Create from raw public key bytes
- ✅ Sign data with Ed25519 (64-byte signatures)
- ✅ Verify Ed25519 signatures
- ✅ Export to strkey format (G... accounts, S... seeds)
- ✅ Comprehensive input validation and error handling
- ✅ Thread-safe, immutable design
- ✅ **Async API**: Crypto operations use `suspend` functions

#### StrKey (`com.soneso.stellar.sdk.StrKey`)
- ✅ Encode/decode Ed25519 public keys (G...)
- ✅ Encode/decode Ed25519 secret seeds (S...)
- ✅ Encode/decode muxed accounts (M...)
- ✅ Encode/decode contracts (C...)
- ✅ CRC16-XModem checksum validation
- ✅ Version byte validation
- ✅ Base32 encoding (platform-specific: Apache Commons on JVM, pure Kotlin on JS/Native)

### Transactions & Operations

#### Transaction Building
- ✅ TransactionBuilder with fluent API
- ✅ FeeBumpTransactionBuilder for fee bumps
- ✅ All 27 Stellar operations implemented
- ✅ Memo support (none, text, ID, hash, return)
- ✅ Time bounds and ledger bounds
- ✅ Transaction preconditions (min sequence, sequence age/gap, extra signers)
- ✅ Multi-signature support
- ✅ Soroban transaction data (resource limits, footprint)
- ✅ XDR serialization/deserialization

#### Operations (All 27)
**Account Operations**:
- ✅ CreateAccount, AccountMerge, BumpSequence, SetOptions, ManageData

**Payment Operations**:
- ✅ Payment, PathPaymentStrictReceive, PathPaymentStrictSend

**Asset Operations**:
- ✅ ChangeTrust, AllowTrust, SetTrustLineFlags

**Trading Operations**:
- ✅ ManageSellOffer, ManageBuyOffer, CreatePassiveSellOffer

**Claimable Balance Operations**:
- ✅ CreateClaimableBalance, ClaimClaimableBalance, ClawbackClaimableBalance

**Liquidity Pool Operations**:
- ✅ LiquidityPoolDeposit, LiquidityPoolWithdraw

**Sponsorship Operations**:
- ✅ BeginSponsoringFutureReserves, EndSponsoringFutureReserves, RevokeSponsorship

**Clawback Operations**:
- ✅ Clawback

**Soroban Operations**:
- ✅ InvokeHostFunction, ExtendFootprintTTL, RestoreFootprint

**Deprecated**:
- ✅ Inflation (protocol 12 deprecated)

### Assets & Accounts

#### Assets (`com.soneso.stellar.sdk.Asset`)
- ✅ AssetTypeNative (XLM/Lumens)
- ✅ AssetTypeCreditAlphaNum4 (1-4 char codes)
- ✅ AssetTypeCreditAlphaNum12 (5-12 char codes)
- ✅ Asset parsing from canonical strings ("CODE:ISSUER")
- ✅ Asset validation (code format, issuer validation)
- ✅ Contract ID derivation for Stellar Asset Contracts (SAC)
- ✅ Asset comparison and sorting

#### Accounts
- ✅ Account management with sequence numbers
- ✅ Muxed accounts (M... addresses with IDs)
- ✅ TransactionBuilderAccount interface
- ✅ Automatic sequence number incrementing

### Horizon API Client

#### HorizonServer (`com.soneso.stellar.sdk.horizon.HorizonServer`)
- ✅ Comprehensive REST API coverage
- ✅ Request builders for all endpoints
- ✅ Server-Sent Events (SSE) streaming
- ✅ Automatic retries and error handling
- ✅ Transaction submission (sync and async)

#### Endpoints
- ✅ Accounts: Details, data entries, balances
- ✅ Assets: List, search, filter
- ✅ Claimable Balances: Query, filter by sponsor/claimant/asset
- ✅ Effects: All effect types (60+), filtering, streaming
- ✅ Ledgers: List, details, operations, transactions
- ✅ Liquidity Pools: List, details, operations, trades
- ✅ Offers: List by account, order books
- ✅ Operations: All operation types (27), filtering, streaming
- ✅ Payments: Payment filtering, streaming
- ✅ Trades: Trade history, filtering, aggregations
- ✅ Transactions: Submit, query, filter
- ✅ Paths: Strict send, strict receive path finding
- ✅ Fee Stats: Network fee statistics
- ✅ Health: Server health monitoring
- ✅ Root: Server information

#### Special Features
- ✅ SEP-29: Account memo validation (AccountRequiresMemoException)
- ✅ Cursor-based pagination
- ✅ Order (asc/desc) support
- ✅ Limit parameter support

### Soroban Smart Contracts

#### High-Level API
- ✅ ContractClient: Dual-mode contract interaction
  - **Factory methods**: `fromNetwork()` loads spec, `withoutSpec()` for manual mode
  - **Beginner API**: `invoke()` with Map<String, Any?> arguments and auto-execution
  - **Power API**: `invokeWithXdr()` with List<SCValXdr> for manual control
  - **Type conversion helpers**: `funcArgsToXdrSCValues()`, `nativeToXdrSCVal()`
- ✅ Smart contract deployment:
  - **One-step**: `deploy()` with Map-based constructor args
  - **Two-step**: `install()` + `deployFromWasmId()` for WASM reuse
- ✅ AssembledTransaction: Full transaction lifecycle
- ✅ Type-safe generic results with custom parsers
- ✅ Automatic simulation and resource estimation
- ✅ Auto-execution: Read calls return results, write calls auto-sign and submit
- ✅ Read-only vs write call detection via auth entries

#### Authorization
- ✅ Sign Soroban authorization entries (`Auth` class)
- ✅ Build authorization entries from scratch
- ✅ Custom Signer interface support
- ✅ Network replay protection
- ✅ Signature verification
- ✅ Auto-authorization for invoker
- ✅ Custom authorization handling

#### Contract Operations
- ✅ Contract invocation (InvokeHostFunctionOperation)
- ✅ Contract deployment
- ✅ WASM upload
- ✅ State restoration when expired
- ✅ Footprint TTL extension
- ✅ Transaction polling with exponential backoff

#### RPC Client (`com.soneso.stellar.sdk.rpc.SorobanServer`)
- ✅ Full Soroban RPC API coverage
- ✅ Transaction simulation
- ✅ Event queries and filtering
- ✅ Ledger and contract data retrieval
- ✅ Network information queries
- ✅ Health monitoring

#### Contract Spec & Parsing
- ✅ ContractSpec parsing from XDR
- ✅ WASM analysis and metadata extraction
- ✅ Function signature detection
- ✅ Type parsing and validation

#### Exception Handling (10 types)
- ✅ ContractException (base)
- ✅ SimulationFailedException
- ✅ SendTransactionFailedException
- ✅ TransactionFailedException
- ✅ TransactionStillPendingException
- ✅ ExpiredStateException
- ✅ RestorationFailureException
- ✅ NotYetSimulatedException
- ✅ NeedsMoreSignaturesException
- ✅ NoSignatureNeededException

### Utility Features

#### Network
- ✅ Network.PUBLIC (mainnet)
- ✅ Network.TESTNET
- ✅ Custom network support
- ✅ Network passphrase handling

#### FriendBot
- ✅ Testnet account funding
- ✅ Error handling for already-funded accounts

#### XDR System
- ✅ Complete XDR type system (470+ types)
- ✅ XDR serialization/deserialization
- ✅ Type-safe XDR unions and enums
- ✅ XDR validation

#### Scval (Smart Contract Values)
- ✅ Type conversions to/from SCValXdr
- ✅ Support for all Soroban types
- ✅ Address, symbol, bytes, numbers, vectors, maps
- ✅ Type validation and error handling

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
- never mark tests with the ignore annotation
- the main purpose of the demo app is to showcase sdk functionality for new developers who want to learn ho to use the sdk. when implementing business logic in the demo app use the sdk functionality available, do not implement functionality that is already available in the sdk
- never mark integration tests with the @Ingnore annotation as they always have testnet connectivity and the accounts are funded by friendbot