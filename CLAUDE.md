# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin Multiplatform (KMP) project for building a Stellar SDK. The SDK provides APIs to:
- Build Stellar transactions
- Connect to Horizon (Stellar's API server)
- Connect to Stellar RPC Server
- Build smart account wallets with passkey authentication using [OpenZeppelin smart contracts](https://github.com/OpenZeppelin/stellar-contracts)

## Current State

The SDK is **production-ready** with comprehensive functionality implemented:

### Platform Support
- **JVM**: Android API 24+, Server applications (Java 17+)
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
- **SEP Support**: SEP-1 (Stellar TOML), SEP-2 (Federation Protocol), SEP-5 (Key Derivation), SEP-6 (Deposit and Withdrawal API), SEP-8 (Regulated Assets), SEP-9/12 (KYC), SEP-10 (Web Authentication), SEP-24 (Hosted Deposit/Withdrawal), SEP-30 (Account Recovery), SEP-31 (Cross-Border Payments), SEP-38 (Anchor RFQ), SEP-45 (Web Authentication for Contract Accounts), SEP-46 (Contract Meta), SEP-47 (Contract Interface Discovery), SEP-48 (Contract Interface Specification), SEP-51 (XDR-JSON), SEP-53 (Sign and Verify Messages)

### Smart Accounts (OpenZeppelin)
- **Contracts**: [OpenZeppelin stellar-contracts](https://github.com/OpenZeppelin/stellar-contracts) v0.7.0
- **SDK Layer**: `stellar-sdk/src/commonMain/.../smartaccount/` with two sub-packages:
  - `core/` — Platform-independent types, builders, errors, signatures, auth payload, CBOR parser
  - `oz/` — OZ-specific kit, managers, clients, config, storage, WebAuthn provider interface
- **Platform Adapters**: WebAuthn providers and storage adapters in `androidMain/`, `jsMain/`, `nativeMain/`
- **Features**: Wallet lifecycle (create, connect, disconnect), multi-signer authorization, context rules, policies (threshold, weighted threshold, spending limit), token transfers, relayer fee sponsoring, indexer credential lookup
- **Documentation**: `docs/smart-accounts/` (API reference, onboarding guide, platform WebAuthn guides)

### Demo Application
- **Platforms**: Android, iOS, macOS, Desktop (JVM), Web
- **Architecture**: Compose Multiplatform with 95% code sharing
- **UI**: Modern celestial-themed design with color-coded cards (purple/gold/teal/blue/red)
- **Features**: 11 comprehensive demos (key generation, funding, account details, trustlines, payments, fetch transaction, contract details, deploy contract, invoke hello world, invoke auth, invoke token contract)
- **Location**: `demo/` directory with platform-specific apps

### Smart Account Demo Application
- **Platforms**: Android, iOS, macOS, Web
- **Architecture**: Compose Multiplatform (Android, iOS, Web) + native SwiftUI (macOS) with Kotlin bridge
- **Features**: Wallet creation/connection, token transfers, context rule management, policy configuration, multi-signer flows
- **Location**: `smart-account-demo/` directory

### Agent Skill
- An [Agent Skill](https://agentskills.io) that teaches AI coding agents how to use this SDK
- **Location**: `skills/kmp-stellar-sdk/` (`SKILL.md` + `references/`)
- **Plugin config**: `skills/.claude-plugin/plugin.json`, `.claude-plugin/marketplace.json`

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
- **JS**: Pure Kotlin implementation
- **Native**: Pure Kotlin implementation
- **Strict alphabet**: All three accept only the 32 characters of the base32 alphabet. The pad character `=`, whitespace, lowercase letters and every other byte are rejected, so the same string gets the same verdict on JVM, Android, JS and Native. Alphabet membership is decided in the SDK rather than delegated to Commons Codec, which also maps lowercase and the pad character.

### Security Principles

1. **No Experimental Crypto**: Only battle-tested, audited libraries
2. **Constant-Time Operations**: Protection against timing attacks
3. **Memory Safety**:
   - Defensive copies of all keys
   - CharArray for secrets (can be zeroed)
   - Proper cleanup in native code
4. **Input Validation**: All inputs validated before crypto operations
5. **Error Handling**: Comprehensive validation with clear error messages

### SEP-5 Key Derivation (HD Wallets)

The SDK implements SEP-5 (Key Derivation Methods for Stellar Keys) for hierarchical deterministic wallet support:

#### Features
- **BIP-39 Mnemonic**: Generation and validation (12, 15, 18, 21, 24 words)
- **9 Languages**: English, Chinese (Simplified/Traditional), French, Italian, Japanese, Korean, Spanish, Malay
- **SLIP-0010**: Ed25519 hierarchical key derivation
- **Stellar Path**: `m/44'/148'/x'` (all hardened indices)
- **Passphrase Support**: Optional BIP-39 passphrase for additional security

#### Package Structure
```
com.soneso.stellar.sdk.sep.sep05/
├── Mnemonic.kt              # High-level mnemonic and HD key derivation API
├── MnemonicUtils.kt       # BIP-39 mnemonic operations
├── WordList.kt            # 9 language word lists (2048 words each)
├── MnemonicLanguage.kt    # Language enum
├── MnemonicStrength.kt    # Word count/entropy enum
├── MnemonicConstants.kt   # BIP-39/SLIP-0010 constants
├── HexCodec.kt            # Hex encoding utilities
├── crypto/                # Platform-specific crypto (PBKDF2, HMAC-SHA512, SHA-256)
└── exceptions/            # SEP-5 specific exceptions
```

#### Usage Example
```kotlin
// Generate 24-word mnemonic phrase
val phrase = Mnemonic.generate24WordsMnemonic()

// Create Mnemonic instance from phrase
val mnemonic = Mnemonic.from(phrase)

// Derive multiple accounts
val account0 = mnemonic.getKeyPair(index = 0)  // m/44'/148'/0'
val account1 = mnemonic.getKeyPair(index = 1)  // m/44'/148'/1'

// With passphrase
val secureMnemonic = Mnemonic.from(phrase, passphrase = "secret")

// Cleanup
mnemonic.close()
secureMnemonic.close()
```

#### Security Features
- CSPRNG for entropy generation (SecureRandom/randombytes_buf/crypto.getRandomValues)
- O(1) HashMap word lookup (timing attack mitigation)
- Constant-time checksum comparison
- NFKD normalization for mnemonic and passphrase
- Memory cleanup (entropy zeroed after use, close() zeros seed)
- PBKDF2-HMAC-SHA512 with 2048 iterations

### SEP-2 Federation Protocol

The SDK implements SEP-2 (Federation Protocol) for resolving human-readable Stellar addresses to account IDs.

#### Package Structure
```
com.soneso.stellar.sdk.sep.sep02/
├── FederationService.kt              # Federation service client (fromDomain, 4 query methods)
├── FederationResponse.kt             # Federation response data class
└── exceptions/
    ├── Sep02Exception.kt                    # Base exception
    ├── Sep02InvalidAddressException.kt      # Invalid stellar address format
    ├── Sep02FederationNotFoundException.kt  # No federation server in stellar.toml
    └── Sep02InvalidResponseException.kt     # Malformed server response
```

#### Usage Example
```kotlin
// Resolve a stellar address
val response = FederationService.resolveStellarAddress("bob*stellar.org")
println("Account: ${response.accountId}")
println("Memo: ${response.memo} (${response.memoType})")

// Create service for a specific domain
val service = FederationService.fromDomain("stellar.org")
val result = service.resolveAccountId("GABC...")
```

### SEP-8 Regulated Assets

The SDK implements SEP-8 (Regulated Assets) for assets requiring issuer approval before transactions can be submitted.

#### Package Structure
```
com.soneso.stellar.sdk.sep.sep08/
├── Sep08Service.kt                    # Service client (fromDomain, postTransaction, postAction)
├── Sep08PostTransactionResponse.kt    # Sealed class: Success, Revised, Pending, ActionRequired, Rejected
├── Sep08PostActionResponse.kt         # Sealed class: Done, NextUrl
├── RegulatedAsset.kt                  # Regulated asset with approval server URL
└── exceptions/
    ├── Sep08Exception.kt                          # Base exception
    ├── Sep08IncompleteInitDataException.kt        # Missing network/Horizon config
    ├── Sep08InvalidTransactionResponseException.kt # Malformed approval server response
    └── Sep08InvalidActionResponseException.kt     # Malformed action URL response
```

### SEP-30 Account Recovery

The SDK implements SEP-30 (Account Recovery) for multi-party recovery of Stellar accounts using alternative authentication methods.

#### Package Structure
```
com.soneso.stellar.sdk.sep.sep30/
├── Sep30Service.kt                    # Service client (registerAccount, updateIdentitiesForAccount, signTransaction, accountDetails, deleteAccount, accounts)
├── Sep30Request.kt                    # Registration/update request with identities
├── Sep30RequestIdentity.kt            # Identity with role and auth methods
├── Sep30AuthMethod.kt                 # Authentication method (email, phone, stellar_address)
├── Sep30AccountResponse.kt            # Account response with address, identities, signers
├── Sep30AccountsResponse.kt           # List accounts response
├── Sep30SignatureResponse.kt          # Transaction signature response
├── Sep30ResponseIdentity.kt           # Response identity with role and authenticated flag
├── Sep30ResponseSigner.kt             # Response signer with key
└── exceptions/
    ├── Sep30Exception.kt                      # Base exception
    ├── Sep30BadRequestException.kt            # HTTP 400
    ├── Sep30UnauthorizedException.kt          # HTTP 401
    ├── Sep30NotFoundException.kt              # HTTP 404
    ├── Sep30ConflictException.kt              # HTTP 409
    ├── Sep30UnknownResponseException.kt       # Other HTTP errors
    └── Sep30InvalidResponseException.kt       # Malformed 200 responses
```

### SEP-31 Cross-Border Payments

The SDK implements SEP-31 (Cross-Border Payments) from the Sending Anchor side. The service client talks to a Receiving Anchor's `DIRECT_PAYMENT_SERVER` to discover supported assets, initiate cross-border payments, register status callbacks, and track transaction lifecycle. Authentication uses SEP-10; KYC uses SEP-12; optional firm quotes use SEP-38.

#### Package Structure
```
com.soneso.stellar.sdk.sep.sep31/
├── Sep31Service.kt                       # Service client (info, postTransactions, getTransaction, putTransactionCallback, patchTransaction)
├── Sep31InfoResponse.kt                  # Parsed GET /info response (receiveAssets map)
├── Sep31ReceiveAssetInfo.kt              # Per-asset configuration (limits, fee model, funding methods)
├── Sep31Sep12TypesInfo.kt                # SEP-12 sender/receiver customer types for one asset
├── Sep31PostTransactionsRequest.kt       # POST /transactions request body
├── Sep31PostTransactionsResponse.kt      # POST /transactions response (id, stellarAccountId, stellarMemo)
├── Sep31TransactionResponse.kt           # Full transaction state (GET/PATCH /transactions/:id)
├── Sep31TransactionStatus.kt             # Lifecycle status enum (10 values, fromString)
├── Sep31FeeDetails.kt                    # Fee breakdown (total, asset, details)
├── Sep31FeeDetailsDetails.kt             # Single fee line item
├── Sep31Refunds.kt                       # Refund aggregate (amountRefunded, amountFee, payments)
├── Sep31RefundPayment.kt                 # Single on-chain refund payment
└── exceptions/
    ├── Sep31Exception.kt                              # Base exception
    ├── Sep31BadRequestException.kt                    # HTTP 400
    ├── Sep31CustomerInfoNeededException.kt            # HTTP 400 with error=customer_info_needed
    ├── Sep31TransactionInfoNeededException.kt         # HTTP 400 with error=transaction_info_needed (deprecated)
    ├── Sep31UnauthorizedException.kt                  # HTTP 401
    ├── Sep31ForbiddenException.kt                     # HTTP 403
    ├── Sep31TransactionNotFoundException.kt           # HTTP 404 on GET/PATCH /transactions/:id
    ├── Sep31TransactionCallbackNotSupportedException.kt # HTTP 404 on PUT /transactions/:id/callback
    ├── Sep31InvalidResponseException.kt               # Malformed 2xx body
    ├── Sep31UnknownResponseException.kt               # Unmapped HTTP status
    └── Sep31ConfigurationException.kt                 # fromDomain configuration failure
```

### SEP-51 XDR-JSON

Every generated XDR type carries `toXdrJson()`, `toXdrJsonElement()`,
`Companion.fromXdrJson(String)` and `Companion.fromXdrJsonElement(JsonElement)`. There is
deliberately no `sep/sep51` package: the methods are emitted onto the types in
`com.soneso.stellar.sdk.xdr`, over the hand-written runtime `xdr/XdrJson.kt`, which is where
every escaping, hex, strkey and validation rule lives. Hand-written conformance tests are in
`unitTests/sep/sep51/`; the per-type suites in `unitTests/xdr/json/` are emitted.

`kotlinx-serialization-json` is an `api` dependency because `JsonElement` is in the public
signature.

#### Maintenance contract

The JSON form is derived from the same `.x` files as `encode`/`decode`, so it tracks the XDR
pin — provided regeneration covers it. After any bump of `tools/xdrgen-kt/xdr-source.cfg`:

```bash
make -C tools/xdrgen-kt clean-all generate   # regenerates encode/decode and the JSON methods
make -C tools/xdrgen-kt emit-json-tests      # regenerates unitTests/xdr/json/
ruby tools/sep-51-oracle/emitted_names.rb    # committed Kotlin vs the frozen name table
ruby tools/sep-51-oracle/name_map.rb --diff  # the name table vs the reference build
bash tools/sep-51-corpus/refresh_corpus.sh   # the conformance corpus vs the reference build
```

The last two need the pinned reference CLI (`cargo install stellar-xdr --features cli --version
28.0.0 --locked`) and run locally, not in CI. The first three need only Ruby and run in CI as the
`xdr-generator` job of `tests.yml`. A new enum member or union arm changes the wire surface, so
review whether any newly added type needs a type-level override in
`tools/xdrgen-kt/lib/xdrgen/generators/kotlin_json_overrides.rb`.

Never hand-edit a generated XDR type or an emitted test: the CI drift guard regenerates both and
fails on any difference.

## Documentation Standards

All SDK documentation must follow the guidelines defined in `docs/documentation-strategy.md`. Key requirements:

- **Target Audience**: SDK users (developers integrating the SDK), not contributors
- **SDK Code Only**: No app architecture code, no internal implementation details, no hypothetical code
- **Examples Over Theory**: Complete, runnable code examples with all variables defined
- **Accuracy**: Use actual SDK classes, methods, and properties
- **Conciseness**: Professional tone, no filler words, no unnecessary superlatives
- **Platform Compatibility**: Examples work across all KMP targets
- **One Framework Per Platform**: Avoid decision paralysis with multiple options

Before adding or modifying documentation:
1. Read `docs/documentation-strategy.md` completely
2. Verify all code examples against SDK source
3. Test code examples compile and run
4. Review against quality checklist in documentation-strategy.md

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
- **Run all tests (unit only, no integration)**: `./gradlew test -PexcludeIntegrationTests`
- **Run JVM tests**: `./gradlew jvmTest`
- **Run JVM unit tests only**: `./gradlew jvmTest -PexcludeIntegrationTests`
- **Run single test class**: `./gradlew :stellar-sdk:jvmTest --tests "KeyPairTest"`
- **Run single test method**: `./gradlew :stellar-sdk:jvmTest --tests "KeyPairTest.testRandomKeyPair"`
- **Run tests with pattern**: `./gradlew :stellar-sdk:jvmTest --tests "*Key*"`
- **Run JS tests (Browser)**: `./gradlew jsBrowserTest` (requires Chrome)
- **Run JS tests (Node.js)**: `./gradlew :stellar-sdk:jsNodeTest`
  - Single test class: `./gradlew :stellar-sdk:jsNodeTest --tests "KeyPairTest"`
  - Pattern matching: `./gradlew :stellar-sdk:jsNodeTest --tests "*Key*"`
- **Run macOS tests**: `./gradlew macosArm64Test` or `./gradlew macosX64Test`
- **Run iOS Simulator tests**: `./gradlew iosSimulatorArm64Test` (unit tests only — see note below)

#### JS Testing Notes

JS Node and JS Browser tests all pass. Browser tests require Chrome to be installed. Karma configuration and WASM file serving are handled automatically by the Gradle build config — no manual setup needed.

#### iOS Simulator Limitation

iOS simulator integration tests are currently skipped. The simulator does not trust the Sectigo root CA used by Stellar's servers (`NSURLErrorDomain Code=-1202`), causing all network-dependent tests to fail. Unit tests (3983) pass fine. Run iOS simulator tests with `-PexcludeIntegrationTests`. Integration tests are validated on macOS native, JVM, and JS Node.

#### Integration Tests

The SDK includes comprehensive integration tests that validate against a live Stellar Testnet:

- **Location**: `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/`
- **Status**: Integration tests run by default when executing tests locally. In CI, they are excluded via `-PexcludeIntegrationTests`.
- **Coverage**: Accounts, payments, sponsorships, claimable balances, clawback, fee bumps, ContractClient, AssembledTransaction, authorization, state restoration, and more.
- **Funding**: Test accounts are automatically funded via Friendbot (no manual setup needed).
- **Timing**: Use `realDelay()` instead of `delay()` in integration tests — `runTest` uses virtual time, so `delay()` completes instantly and won't actually wait for network operations.
- **Run all tests (including integration)**:
  ```bash
  ./gradlew :stellar-sdk:jvmTest
  ```
- **Run unit tests only (exclude integration tests)**:
  ```bash
  ./gradlew :stellar-sdk:jvmTest -PexcludeIntegrationTests
  ```

#### CI Workflow

The GitHub Actions CI uses `-PexcludeIntegrationTests` on all jobs (integration tests require testnet connectivity):

- **JVM unit tests**: Runs on push to `main` and PRs to `main`. Matrix across JDK 17, 21, and 25. Code coverage collected on JDK 25.
- **JS Node unit tests**: Runs on push to `main` and PRs to `main` (Ubuntu runner).
- **macOS native unit tests**: Runs on PRs to `main` only (macOS runner — restricted to PRs to save runner minutes).

### Demo Apps

The `demo` directory demonstrates **comprehensive SDK usage** with a Compose Multiplatform architecture:

- **Shared module** (`demo/shared`): Compose Multiplatform UI + business logic
  - 11 feature screens (key generation, funding, account details, trust asset, payments, fetch transaction, contract details, deploy contract, invoke hello world, invoke auth, invoke token contract)
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
  brew install libsodium
  ./gradlew :demo:shared:linkDebugFrameworkMacosArm64
  cd demo/macosApp && xcodegen generate && open StellarDemo.xcodeproj
  ```

- **Desktop** (`demo/desktopApp`): JVM Compose (recommended for macOS)
  ```bash
  ./gradlew :demo:desktopApp:run
  ```

- **Web** (`demo/webApp`): Kotlin/JS with Compose
  ```bash
  # Development server with Vite (hot reload) - RECOMMENDED
  ./gradlew :demo:webApp:viteDev
  # Opens at http://localhost:8081

  # Production build (creates optimized bundle and copies to dist/)
  ./gradlew :demo:webApp:productionDist
  # Output: demo/webApp/dist/

  # Preview production build
  ./gradlew :demo:webApp:vitePreview
  # Opens at http://localhost:8082
  ```

**Key Features**:
- Vite dev server: Lightning-fast hot module replacement
- Webpack production: Optimized bundles with code splitting
- Build time: ~5 seconds for production
- Bundle size: 28 MB unminified (2.7 MB gzipped)
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
  - `shared`: Compose Multiplatform UI + business logic (11 feature screens)
  - `androidApp`: Android entry point (Jetpack Compose)
  - `iosApp`: iOS entry point (SwiftUI wrapper for Compose)
  - `macosApp`: macOS native SwiftUI app (native SwiftUI, not Compose)
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

Decoding accepts canonical strkeys only, and rejects everything else with
`IllegalArgumentException`:
- The encoded length is checked against the requested type before any base32 decoding runs.
- `=` padding, anything following a `=`, whitespace anywhere in the string, lowercase base32 and characters outside the ASCII range are all rejected. `IndexOutOfBoundsException` escapes no decode entry point.
- Signed payload (P...) framing is validated on encode and decode: declared length 1..64, a data size that fits that length exactly, zero padding after the payload.
- Claimable balance (B...) payloads must carry the `CLAIMABLE_BALANCE_ID_TYPE_V0` discriminant, on encode and decode, in the single byte the strkey body leads with and in the four big-endian bytes the XDR wire form carries. `Address` rejects any other value at construction rather than at `toSCAddress()`.
- `StrKey.encodeClaimableBalance` takes three widths: the 32-byte hash, the 33-byte strkey body and the 36-byte XDR wire form. Nothing in between is padded or truncated to one of them.

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

`ClaimableBalanceId` is the one resolver for a balance id. It reads the `B...` strkey, the bare
hash as 64 hexadecimal characters, and that hash behind a discriminant of one byte (66
characters) or four (72 characters). `ClaimClaimableBalanceOperation`,
`ClawbackClaimableBalanceOperation`, `Sponsorship.ClaimableBalance` and the Horizon request
builders all read an id through it, judge it where the id is given, and report the 72-character
form back. `TransactionResponse.getCreatedClaimableBalanceId()` reports a created balance as a
strkey. Horizon serves no `/claimable_balances/{id}/effects` route, so `EffectsRequestBuilder`
has no claimable balance method.

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
- ✅ ContractClient: Type-safe contract interaction with automatic spec loading
  - **Factory method**: `forContract()` loads contract spec from network
  - **Beginner API**: `invoke()` with Map<String, Any?> arguments and auto-execution
  - **Advanced API**: `buildInvoke()` with Map<String, Any?> arguments for transaction control (essential for multi-signature workflows)
  - **Type conversion helpers**:
    - `funcArgsToXdrSCValues()` - Convert native types to XDR arguments
    - `funcResToNative()` - Convert XDR results to native types (inverse of nativeToXdrSCVal)
    - Automatic type mapping based on contract spec
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

## Test Placement Conventions

All tests in the stellar-sdk module follow one placement scheme, enforced by the `verifyTestNamespaces` Gradle task (part of `check`). Allowed namespaces in test source sets:

- `com.soneso.stellar.sdk.unitTests.*` — unit tests, mirroring the commonMain package tree (e.g. code in `smartaccount/oz/` is tested in `unitTests/smartaccount/oz/`)
- `com.soneso.stellar.sdk.integrationTests.*` — testnet integration tests, class names suffixed `*IntegrationTest`
- `com.soneso.stellar.sdk.util` — cross-tree expect/actual test helpers (`TestResourceUtil` only)
- `com.soneso.stellar.sdk.contract.bindings` — generated contract binding fixtures (`@generated`; the production-namespace package is required for regeneration parity)

Rules for writing tests:

- Extend an existing test class first; if the topic does not fit, add to an existing topic-matching file; create a new file only when no matching file exists.
- Name tests for the topic under test. Never name a test file, class, or method after the act of covering ("coverage", "boost", "extended", "enhanced", "gap").
- Unit test classes are suffixed `Test`; one subject per test class.
- No unit test may perform live network I/O. All HTTP goes through Ktor MockEngine; if production code prevents injecting a mock, raise the missing seam for discussion instead of calling a live endpoint.
- Coverage is measured with the Codecov formula (a line counts only when all its branches are covered). Check locally with:
  ```bash
  ./gradlew :stellar-sdk:jvmTest :stellar-sdk:koverXmlReportJvmOnly -PexcludeIntegrationTests
  python3 tools/coverage/coverage_summary.py --min 90
  ```
  CI enforces the 90% minimum on the JDK 25 job; the Codecov project status requires 90% as well.

## Reference Implementation

When implementing features, use the Java Stellar SDK as a reference:
- Located at: `/Users/chris/projects/Stellar/java-stellar-sdk`
- Provides production-tested implementations of Stellar protocols
- Use as a guide for API design and feature completeness
- never mark tests with the ignore annotation
- the main purpose of the demo app is to showcase sdk functionality for new developers who want to learn ho to use the sdk. when implementing business logic in the demo app use the sdk functionality available, do not implement functionality that is already available in the sdk
- never mark integration tests with the @Ingnore annotation as they always have testnet connectivity and the accounts are funded by friendbot

## Demo UI Component Architecture

### Reusable UI Components

**Location**: `demo/shared/src/commonMain/kotlin/com/soneso/demo/ui/components/`

The demo app uses shared UI components to eliminate code duplication across 11 screens (eliminated ~670 lines of duplicate code in Oct 2025 refactoring):

#### StellarTopBar Component
- Gradient TopBar with navigation
- Usage: `StellarTopBar(title = "Screen Title", onNavigationClick = { navigator.pop() })`
- Replaced 40+ lines per screen across all 11 screens

#### AnimatedButton Component
- Button with hover/press animations and loading state
- Usage: `AnimatedButton(onClick = {...}, isLoading = isLoading, enabled = !isLoading) { Text("Action") }`
- Replaced 25-75 lines per button across 9 screens

#### FormValidation Utility
- **Location**: `demo/shared/src/commonMain/kotlin/com/soneso/demo/ui/FormValidation.kt`
- Centralized Stellar protocol validation (account IDs, secret seeds, contract IDs, transaction hashes, asset codes)
- Usage: `FormValidation.validateAccountIdField(value)?.let { errors["accountId"] = it }`
- Replaced 180 lines of duplicate validation across 10 screens

**When to Use**:
- Use `FormValidation` for Stellar protocol format validation (G... addresses, S... seeds, C... contracts, transaction hashes, asset codes)
- Keep domain-specific business rules inline (amount ranges, trust limits, screen-specific constraints)

### Key Pattern for Adding Components

1. Analyze duplication across screens
2. Create component with all features from existing code
3. Migrate screens one at a time, verifying compilation
4. Delete unused imports after migration

**Reference**: See `demo/FORMVALIDATION_COMPLETE.md` and `REFACTOR_SESSION_PROGRESS.md` for detailed migration history.

## Git Safety Rules

CRITICAL - Agents must NEVER run these commands unless explicitly requested:
- `git reset` (in any form)
- `git checkout -- .` or `git restore .`
- `git clean -fd`
- `git reset --hard`
- Any command that discards uncommitted changes

Safe git operations only:
- `git status` - always allowed
- `git diff` - always allowed
- `git log` - always allowed
- `git add` - only when preparing to commit
- `git commit` - only when user explicitly asks to commit
- `git stash` - ask user first

Before ANY git operation that modifies history or working directory:
1. Ask user for explicit permission
2. Explain what will happen
3. Wait for confirmation
- the stellar token interface is defined here https://developers.stellar.org/docs/tokens/token-interface
