# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0] - 2026-04-08

### Added
- **OpenZeppelin Smart Accounts**: SDK support for the [OpenZeppelin smart account contracts](https://github.com/OpenZeppelin/stellar-contracts) on Soroban
  - `OZSmartAccountKit` as the main entry point for wallet creation, connection, and management
  - WebAuthn passkey authentication across all platforms (Android, iOS, macOS, web)
  - Multi-signer support with delegated and external signers
  - Context rule management (Default, CallContract, CreateContract)
  - Policy management (simple threshold, weighted threshold, spending limit)
  - Token transfers and generic contract calls with single and multi-signer authorization
  - Relayer integration for fee-sponsored transactions
  - Indexer integration for credential-to-contract address lookup (testnet and mainnet defaults)
  - Platform-specific storage adapters (EncryptedSharedPreferences, Keychain, UserDefaults, IndexedDB, LocalStorage)
  - Platform-specific WebAuthn providers (CredentialManager, ASAuthorization, navigator.credentials)
  - Documentation in `docs/smart-accounts/`

- **Smart Account Demo App**: Compose Multiplatform demo with native macOS SwiftUI support
  - Wallet creation and connection with passkey registration
  - Token transfers (XLM and custom tokens) with single and multi-signer flows
  - Context rule creation, editing, and removal
  - Policy configuration (threshold, weighted threshold, spending limit)
  - Token approval (SEP-41 allowance)
  - Runs on Android, iOS, macOS, desktop (JVM), and web

## [1.3.1] - 2026-04-03

### Fixed
- Allow lowercase letters (a-z) in asset codes, matching JS and Python Stellar SDK validation (fixes #14)

## [1.3.0] - 2026-02-14

### Added
- **SEP-2 (Federation Protocol)**: Resolve human-readable Stellar addresses (e.g., `bob*stellar.org`) to account IDs and reverse-lookup accounts to addresses
  - `FederationService` with `fromDomain()` factory and 4 query methods: `resolveStellarAddress()`, `resolveAccountId()`, `resolveTransactionId()`, `resolveForward()`
  - `FederationResponse` data class with account ID, memo, memo type, and stellar address
  - 4 typed exceptions for invalid addresses, missing federation servers, and malformed responses
  - Unit tests with MockEngine and integration test against live federation server
  - Documentation in `docs/sep/sep-02.md`

- **SEP-30 (Account Recovery)**: Multi-party account recovery using alternative authentication methods (email, phone, Stellar address)
  - `Sep30Service` with all 6 spec endpoints: register, update identities, sign transaction, account details, delete, list accounts
  - 9 data model classes with JSON serialization/deserialization
  - 7 typed exceptions mapping HTTP status codes (400, 401, 404, 409, unknown, malformed 200)
  - 83 unit tests covering response parsing, exceptions, and service operations
  - Documentation in `docs/sep/sep-30.md`

- **SEP-53 (Sign and Verify Messages)**: Off-chain message signing and verification using Ed25519 keypairs
  - `signMessage()` and `verifySignedMessage()` methods on `KeyPair`
  - Domain-separated signing using SEP-53 payload format (36-byte prefix + SHA-256 hash)
  - Unit tests with known test vectors
  - Documentation in `docs/sep/sep-53.md`

- **SEP Compatibility Matrix Automation**: 3-stage Python pipeline for generating field-by-field coverage reports
  - `sep_parser.py` - Fetches and parses SEP specifications from GitHub
  - `sep_analyzer.py` - Scans SDK Kotlin source and maps spec fields to implementation
  - `generate_sep_comparison.py` - Compares definitions against implementation and generates markdown matrices
  - `run_sep_analysis.py` - Orchestrator for all implemented SEPs

## [1.2.1] - 2026-02-11

### Added
- **SEP-8 (Regulated Assets)**: Production-ready client for assets requiring issuer approval before transactions can be submitted
  - `Sep08Service` class with service discovery and approval server interaction:
    - `fromDomain()` - Initialize from issuer's stellar.toml configuration
    - `postTransaction()` - Submit transactions to the approval server for regulatory approval
    - `postAction()` - Complete required user actions (e.g., KYC verification)
    - `authorizationRequired()` - Check if issuer has authorization required/revocable flags set
    - `regulatedAssets` - Discover regulated assets and their approval server URLs
  - `Sep08PostTransactionResponse` sealed class with 5 response types:
    - `Success` - Transaction approved, ready for submission
    - `Revised` - Transaction modified by approval server (e.g., additional operations added)
    - `Pending` - Approval server needs more time; resubmit after timeout
    - `ActionRequired` - User must complete an action (e.g., KYC) before approval
    - `Rejected` - Transaction rejected with reason
  - `Sep08PostActionResponse` sealed class with 2 response types:
    - `Done` - Action completed, transaction approved
    - `NextUrl` - Additional action required at a new URL
  - `RegulatedAsset` data class with asset code, issuer, approval server URL, and approval criteria
  - 4 exception types:
    - `Sep08Exception` - Base exception
    - `Sep08IncompleteInitDataException` - Missing network/Horizon configuration
    - `Sep08InvalidTransactionResponseException` - Malformed approval server response
    - `Sep08InvalidActionResponseException` - Malformed action URL response
  - 95 unit tests + 13 integration tests against live testnet
  - Documentation in `docs/sep/sep-08.md`
  - SEP-8 compatibility matrix showing 100% feature coverage (22/22 features)

### Removed
- Removed `testDeploySACWithSourceAccount` integration test. The test used `CONTRACT_ID_PREIMAGE_FROM_ADDRESS` with `CONTRACT_EXECUTABLE_STELLAR_ASSET`, a combination no longer accepted by the network. SAC deployment via `CONTRACT_ID_PREIMAGE_FROM_ASSET` (tested in `testSACWithAsset`) remains the correct approach.

## [1.2.0] - 2026-02-04

### Added
- **SEP-5 (Key Derivation Methods for Stellar Keys)**: HD wallet support for deriving multiple Stellar accounts from a single mnemonic phrase
  - `Mnemonic` class with BIP-39 mnemonic generation and SLIP-0010 key derivation:
    - `generate12/15/18/21/24WordsMnemonic()` - Generate mnemonics with varying entropy
    - `from()` - Create Mnemonic instance from phrase with optional passphrase
    - `fromEntropy()` - Create from raw entropy bytes
    - `fromBip39Seed()` / `fromBip39HexSeed()` - Create from pre-computed seed
    - `getKeyPair()` / `getAccountId()` - Derive Stellar accounts at index
    - `getPrivateKey()` / `getPublicKey()` - Get raw key bytes
    - `validate()` - Validate mnemonic phrase and checksum
    - `detectLanguage()` - Auto-detect mnemonic language
    - `close()` - Zero internal seed data for security
  - `MnemonicLanguage` enum with 9 BIP-39 languages:
    - English, Japanese, Korean, Spanish, Chinese Simplified, Chinese Traditional, French, Italian, Malay
  - `MnemonicStrength` enum for word count selection (128-256 bits entropy)
  - `MnemonicUtils` low-level utilities for advanced use cases
  - Exception classes: `InvalidMnemonicException`, `InvalidChecksumException`, `InvalidWordException`, `InvalidEntropyException`, `InvalidPathException`
  - Platform-specific crypto implementations:
    - JVM: BouncyCastle for PBKDF2-HMAC-SHA512
    - JS: libsodium-wrappers-sumo
    - Native: libsodium via C interop
  - 182 unit tests including all 5 official SEP-5 test vectors
  - Documentation in `docs/sep/sep-05.md`
  - SEP-5 compatibility matrix showing 100% feature coverage (31/31 features)

## [1.1.0] - 2026-02-03

### Added
- **Test Infrastructure**: Code coverage tooling and CI workflow improvements
  - Kover plugin for JVM code coverage with HTML and XML reports
  - Codecov integration with coverage badge in README
  - CI workflow: JVM tests across JDK 17/21/25 (push + PR), JS Node tests (push + PR), macOS native tests (PR only)
  - Integration test exclusion via `-PexcludeIntegrationTests` flag
  - Real wall-clock delay utilities (`platformDelay()`, `realDelay()`) for integration tests
  - Test reorganization: Split `commonTest` into `unitTests/` and `integrationTests/` directories
- **Unit Tests**: 133 new unit test files (~3,984 tests across 5 platforms)
  - Coverage includes: crypto, StrKey, KeyPair, transactions, operations, assets, accounts, memos, Horizon request builders, Horizon response deserialization, all operation response types, all effect types, Soroban RPC, contract client, assembled transactions, SEP-1/6/9/10/12/24/38/45, XDR round-trips

### Fixed
- **BigInteger two's complement (JS & Native)**: `bigIntegerToBytesSigned()` used magnitude-only encoding instead of proper two's complement. Negative Int128/Int256 values were silently corrupted on JS and Native targets. JVM was unaffected.
- **Native empty data crypto**: SHA-256 of empty data crashed (invalid assertion); Ed25519 sign/verify of empty data crashed (`addressOf(0)` on empty ByteArray)
- **SorobanServer.pollTransaction()**: Added `withContext(Dispatchers.Default)` for real wall-clock delay during polling, fixing too-fast polling on JS and Native

### Changed
- **JVM Target**: Bumped from Java 11 to Java 17 (required by Android AGP 8.x and Gradle 8+)

## [1.0.0] - 2026-01-15

### Added
- **SEP-45 (Web Authentication for Contract Accounts)**: Production-ready client for authenticating Soroban smart contract accounts
  - `WebAuthForContracts` class with challenge-response authentication flow:
    - `fromDomain()` - Initialize from stellar.toml SEP-45 configuration
    - `jwtToken()` - Complete authentication flow and receive JWT token
    - `getChallenge()` - Request authorization entries from server
    - `validateChallenge()` - Validate server challenge (13 security checks)
    - `signAuthorizationEntries()` - Sign entries with keypair(s)
    - `sendSignedChallenge()` - Submit signed entries for JWT
    - `decodeAuthorizationEntries()` / `encodeAuthorizationEntries()` - XDR utilities
  - `Sep45AuthToken` class with JWT parsing and claim extraction:
    - Extracts `account`, `issuedAt`, `expiresAt`, `issuer`, `clientDomain`
    - `isExpired()` method for token validation
  - `Sep45ClientDomainSigningDelegate` interface for remote signing:
    - String-based API (base64 XDR) optimized for HTTP remote signing
  - `Sep45ChallengeResponse` and `Sep45TokenResponse` data classes
  - 22 exception types with sealed hierarchy:
    - `Sep45Exception` base class
    - `Sep45ChallengeValidationException` sealed class with 12 specific validation exceptions
    - Request/response exceptions for HTTP errors
  - 13 security validation checks matching SEP-45 specification
  - Server signature verification using authorization preimage hash
  - Multi-signature support for contract authentication
  - Client domain verification with remote signing delegate
  - 161 unit tests + 2 integration tests against live testnet
  - Documentation in `docs/sep/sep-45.md` with usage examples
  - SEP-45 compatibility matrix showing 100% feature coverage (35/35 features)

### Documentation
- Added SEP-45 Web Authentication for Contract Accounts guide
- Added SEP-0045 compatibility matrix
- Updated README and CLAUDE.md with SEP-45 support
- Updated SEP README with SEP-45 entry

## [0.9.0] - 2026-01-14

### Added
- **SEP-6 (Deposit and Withdrawal API)**: Production-ready client for programmatic anchor transfers
  - `Sep06Service` class with nine API endpoints:
    - `info()` - Discover anchor capabilities and supported assets
    - `deposit()` - Initiate programmatic deposit
    - `depositExchange()` - Deposit with SEP-38 asset conversion
    - `withdraw()` - Initiate programmatic withdrawal
    - `withdrawExchange()` - Withdrawal with SEP-38 asset conversion
    - `fee()` - Query deposit/withdrawal fees (deprecated endpoint)
    - `transactions()` - Retrieve transaction history with pagination
    - `transaction()` - Get single transaction by ID
    - `patchTransaction()` - Update transaction with additional info
  - 8 request data classes:
    - `Sep06DepositRequest`, `Sep06DepositExchangeRequest`
    - `Sep06WithdrawRequest`, `Sep06WithdrawExchangeRequest`
    - `Sep06TransactionsRequest`, `Sep06TransactionRequest`
    - `Sep06FeeRequest`, `Sep06PatchTransactionRequest`
  - 23 response/data classes including:
    - `Sep06InfoResponse`, `Sep06DepositResponse`, `Sep06WithdrawResponse`
    - `Sep06Transaction` with all 35 SEP-6 fields
    - `Sep06FeeDetails`, `Sep06Refunds`, `Sep06FeatureFlags`
  - `Sep06TransactionStatus` enum with 17 statuses and helper methods (`isTerminal()`, `isError()`, `isPending()`)
  - `Sep06TransactionKind` enum with 4 kinds and helper methods (`isDeposit()`, `isWithdrawal()`, `isExchange()`)
  - 7 exception types for error handling:
    - `Sep06AuthenticationRequiredException` - JWT token required (403)
    - `Sep06CustomerInformationNeededException` - SEP-12 KYC required (403)
    - `Sep06CustomerInformationStatusException` - KYC pending/denied (403)
    - `Sep06InvalidRequestException` - Invalid request parameters (400)
    - `Sep06TransactionNotFoundException` - Transaction not found (404)
    - `Sep06ServerErrorException` - Server-side errors (500+)
    - `Sep06Exception` - Base exception for general errors
  - SEP-10 JWT authentication for all endpoints
  - SEP-38 quote integration for exchange operations
  - SEP-12 KYC integration via customer info exceptions
  - Claimable balance support for deposits
  - Callback notification support via `onChangeCallback` parameter
  - Refund tracking with payment breakdowns
  - 93 unit tests + 12 integration tests against live testnet
  - Documentation in `docs/sep/sep-06.md` with usage examples
  - SEP-6 compatibility matrix showing 100% API coverage (95/95 fields)

### Documentation
- Added SEP-6 Deposit and Withdrawal API guide
- Added SEP-0006 compatibility matrix
- Updated README and CLAUDE.md with SEP-6 support

## [0.8.0] - 2026-01-14

### Added
- **SEP-24 (Hosted Deposit and Withdrawal)**: Production-ready client for interactive anchor transfers
  - `Sep24Service` class with seven API endpoints:
    - `info()` - Discover anchor capabilities and supported assets
    - `deposit()` - Initiate interactive deposit flow
    - `withdraw()` - Initiate interactive withdrawal flow
    - `fee()` - Query deposit/withdrawal fees (deprecated endpoint)
    - `transactions()` - Retrieve transaction history
    - `transaction()` - Get single transaction details
    - `pollTransaction()` - Poll transaction until terminal status with configurable intervals
  - 5 request data classes:
    - `Sep24DepositRequest`, `Sep24WithdrawRequest`
    - `Sep24FeeRequest`, `Sep24TransactionsRequest`, `Sep24TransactionRequest`
  - 13 response/data classes:
    - `Sep24InfoResponse`, `Sep24AssetInfo`, `Sep24FeeEndpointInfo`, `Sep24Features`
    - `Sep24InteractiveResponse`, `Sep24FeeResponse`
    - `Sep24TransactionResponse`, `Sep24TransactionsResponse`, `Sep24Transaction`
    - `Sep24FeeDetails`, `Sep24FeeDetail`, `Sep24Refunds`, `Sep24RefundPayment`
  - `Sep24TransactionStatus` enum with 16 statuses including terminal states
  - 5 exception types for error handling:
    - `Sep24AuthenticationRequiredException` - JWT token required (403)
    - `Sep24InvalidRequestException` - Invalid request parameters (400)
    - `Sep24ServerErrorException` - Server-side errors (500+)
    - `Sep24TransactionNotFoundException` - Transaction not found (404)
    - `Sep24Exception` - Base exception for general errors
  - SEP-10 JWT authentication for all endpoints
  - SEP-38 quote integration for cross-asset transfers
  - Support for KYC fields (SEP-9) in deposit/withdraw requests
  - Transaction polling with callbacks for status changes
  - Claimable balance support for deposits
  - Refund tracking with payment breakdowns
  - 38 unit tests + 9 integration tests against live testnet
  - Documentation in `docs/sep/sep-24.md` with usage examples
  - SEP-24 compatibility matrix showing 100% API coverage (128/128 fields)

### Documentation
- Added SEP-24 Hosted Deposit and Withdrawal guide
- Added SEP-0024 compatibility matrix
- Updated SEP README with SEP-24 entry

## [0.7.0] - 2025-12-17

### Added
- **SEP-38 (Anchor RFQ API)**: Production-ready client for anchor price quotes and exchange rate discovery
  - `QuoteService` class with five API endpoints:
    - `info()` - Discover supported assets and delivery methods
    - `prices()` - Get all available exchange prices for an asset
    - `price()` - Get indicative price for a specific asset pair
    - `postQuote()` - Request a firm quote with guaranteed rate
    - `getQuote()` - Retrieve an existing quote by ID
  - 12 data model classes for requests and responses:
    - `Sep38InfoResponse`, `Sep38PricesResponse`, `Sep38PriceResponse`
    - `Sep38QuoteRequest`, `Sep38QuoteResponse`
    - `Sep38Asset`, `Sep38BuyAsset`, `Sep38SellAsset`
    - `Sep38DeliveryMethod`, `Sep38Fee`, `Sep38FeeDetail`
  - 5 exception types for error handling:
    - `Sep38BadRequestException` - Invalid request parameters (400)
    - `Sep38PermissionDeniedException` - Authentication failure (403)
    - `Sep38NotFoundException` - Quote not found (404)
    - `Sep38UnknownResponseException` - Unexpected response codes
    - `Sep38Exception` - Base exception for general errors
  - SEP-10 JWT authentication for firm quotes
  - Service discovery via stellar.toml (ANCHOR_QUOTE_SERVER)
  - Support for multiple asset identification formats (stellar, iso4217, etc.)
  - Context-aware quoting for SEP-6, SEP-24, and SEP-31 integrations
  - Delivery method filtering for buy/sell operations
  - Detailed fee breakdowns with individual fee components
  - 48 unit tests + 9 integration tests against live testnet
  - Documentation in `docs/sep/sep-38.md` with usage examples
  - SEP-38 compatibility matrix showing 100% API coverage (63/63 fields)

### Documentation
- Added SEP-38 Anchor RFQ API guide with price discovery and firm quote examples
- Added SEP-0038 compatibility matrix

## [0.6.0] - 2025-12-09

### Added
- **SEP-09 (Standard KYC Fields)**: Type-safe data classes for standardized KYC information
  - `NaturalPersonKYCFields` - 34 fields for individual customer data (name, address, documents, etc.)
  - `OrganizationKYCFields` - 17 fields for business customer data with automatic field prefixing
  - `FinancialAccountKYCFields` - 14 fields for bank account information
  - `CardKYCFields` - 11 fields for payment card data with automatic field prefixing
  - `StandardKYCFields` - Composite class combining all field types
  - `LocalDate` for date fields, `ByteArray` for binary data (photos, documents)
  - 128 unit tests covering all field types and edge cases
  - Documentation in `docs/sep/sep-09.md` with usage examples
  - SEP-09 compatibility matrix showing 100% field coverage

- **SEP-12 (KYC API)**: Production-ready client for anchor KYC services
  - `KYCService` class with seven API endpoints:
    - `getCustomer()` - Retrieve customer status and required fields
    - `putCustomer()` - Submit or update customer information
    - `putCustomerVerification()` - Submit verification codes
    - `deleteCustomer()` - Request customer data deletion
    - `getCustomerFiles()` - List uploaded files
    - `putCustomerCallback()` - Register status change callbacks
  - SEP-10 JWT authentication integrated across all endpoints
  - Multipart/form-data support for file uploads (photos, documents)
  - Text fields sent before binary data per specification
  - `CallbackSignatureVerifier` for webhook signature validation
  - Six exception types for granular error handling:
    - `CustomerNotFoundException` - Customer record not found
    - `CustomerAlreadyExistsException` - Duplicate customer registration
    - `InvalidFieldException` - Field validation errors with details
    - `FileTooLargeException` - File size limit exceeded
    - `UnauthorizedException` - Authentication failures
    - `KYCException` - Base exception for other errors
  - Support for standard accounts (G...) and muxed accounts (M...)
  - 105 unit tests + 13 integration tests against live Stellar testnet
  - Documentation in `docs/sep/sep-12.md` with usage examples
  - SEP-12 compatibility matrix showing 100% API coverage

### Documentation
- Added SEP-09 Standard KYC Fields guide with data class examples
- Added SEP-12 KYC API guide with authentication and file upload examples
- Added SEP-0009 and SEP-0012 compatibility matrices
- Updated all compatibility matrices to version 0.6.0
- Updated README with SEP-09 and SEP-12 links

## [0.5.1] - 2025-11-25

### Added
- **SEP-10 (Stellar Web Authentication)**: Production-ready client-side implementation for secure authentication with Stellar anchors and services
  - `WebAuth` class with high-level `jwtToken()` API and low-level methods
  - `AuthToken` class for JWT parsing with property-style API (account, memo, jti, isExpired, etc.)
  - All 13 SEP-10 validation checks implemented correctly
  - Support for standard accounts (G...), muxed accounts (M...), and memo IDs
  - Client domain verification with `ClientDomainSigningDelegate` support
  - Multi-signature transaction signing with signature preservation
  - 115+ test cases (108 unit tests + 4 integration tests + 3 signature reordering tests)
  - 18 exception types with security warnings
  - Integration tests against live Stellar testnet anchor
  - Documentation in `docs/sep/sep-10.md` with usage examples
  - SEP-10 compatibility matrix showing 100% implementation coverage

### Changed
- **Dependencies**: Major version upgrades for improved performance and modern features
  - Ktor 2.3.8 → 3.3.2 (90%+ I/O performance improvement)
  - kotlinx-coroutines 1.8.0 → 1.10.2
  - kotlinx-serialization 1.6.3 → 1.9.0
  - bignum 0.3.9 → 0.3.10

### Fixed
- Ktor HttpTimeout deprecation warnings (INFINITE_TIMEOUT_MS in SSE streams)
- SEP compatibility matrix formatting (metadata fields now appear on separate lines on GitHub)

### Documentation
- Added SEP-10 Web Authentication guide with authentication examples
- Added SEP-0010 compatibility matrix
- Updated all compatibility matrices to version 0.5.1

## [0.4.0] - 2025-11-14

### Added
- **SEP-1 (Stellar TOML)**: Complete implementation with 71 fields across 5 data classes (GeneralInformation, Documentation, PointOfContact, Currency, Validator)
  - `StellarToml.fromDomain()` - Fetch stellar.toml from domains
  - `StellarToml.parse()` - Parse stellar.toml from string
  - `StellarToml.currencyFromUrl()` - Load external currency TOML files
  - Custom TOML parser with error correction
  - 33 tests including integration tests with real-world stellar.toml files (stellar.org, testanchor.stellar.org, circle.com, stellar.moneygram.com)
  - Documentation in `docs/sep/sep-01.md` with 7 usage examples
- **Infrastructure**: GitHub Pages deployment with automated Dokka API documentation generation

### Changed
- **Demo App**: Performance improvements with memoized form validation and smart auto-scroll (v1.3.0)
- **Documentation**: Major accuracy improvements across all platform guides and getting started materials
  - Fixed SDK class/property names and removed hypothetical code examples
  - Consolidated duplicate content and improved navigation structure
  - Added documentation strategy guide defining core principles and guidelines

### Fixed
- **GitHub Pages**: Fixed landing page redirect and Dokka V2 workflow configuration
- **Build**: Resolved Gradle wrapper jar tracking and gitignore rule ordering issues

### Documentation
- Complete documentation overhaul with enhanced accuracy and organization
- Added `docs/documentation-strategy.md` with core principles and quality guidelines
- Enhanced compatibility reports with conditional gaps section
- Updated demo app documentation with screenshots for all platforms

### Infrastructure
- Automated API documentation deployment via GitHub Pages
- Removed build artifacts from repository and updated gitignore rules

## [0.3.0] - 2025-11-08

### Added
- **Horizon API**: Added missing parameters to match official Horizon specification (improved API coverage)
- **Demo App**: Added Invoke Token Contract demo showcasing token interaction
- **Demo App**: Added Info screen with centralized version display and auto-scroll functionality
- **Type Conversion**: Complete scValToNative implementation with error and contract instance support
- **Contract Spec**: Complete SCSpecType support in ContractSpec for all Soroban types
- **Demo App**: Modernized UI with complete redesign and comprehensive app icons for all platforms

### Changed
- **Demo App**: Refactored macOS demo app with updated documentation for 11 demo features
- **Documentation**: Updated for accuracy and conciseness across all docs

### Fixed
- **Web App**: Fixed WASM loading for production deployments
- **RPC**: Fixed getEvents RPC implementation to match Stellar RPC specification

## [0.2.1] - 2025-10-25

### Fixed
- **Maven Publishing**: Repository URLs now correctly point to `https://github.com/Soneso/kmp-stellar-sdk` (previously pointed to incorrect repository `stellar-kotlin-multiplatform-sdk`)
- **Maven Publishing**: Package description updated to "Kotlin Multiplatform Stellar SDK"
- **Documentation**: Removed broken links to non-existent documentation files (docs/testing.md, docs/features.md, docs/platforms.md)
- **Documentation**: Updated Platform Guide link to point to correct docs/platforms/ directory
- **macOS Setup**: Corrected macOS demo app setup instructions to require `brew install libsodium`

### Changed
- **Documentation**: Removed beta status warnings from README to reflect production-ready status
- **Documentation**: Added standard Apache License warranty notice

## [0.2.0] - 2025-10-24

### Breaking Changes - ContractClient API Simplification

#### Removed
- `ContractClient.withoutSpec()` factory method - ContractClient now always requires a contract spec. For low-level contract interaction without a spec, use `SorobanServer` + `TransactionBuilder` directly.
- `ContractClient.invokeWithXdr()` method - Replaced by `buildInvoke()` with ergonomic Map-based arguments

#### Changed
- **Renamed**: `ContractClient.fromNetwork()` → `ContractClient.forContract()`
  - **Rationale**: "forContract" better emphasizes creating a client FOR a contract, not loading FROM network
  - **Migration**: Simple find-and-replace across your codebase

#### Added
- `ContractClient.buildInvoke()` - New method for manual transaction control
  - **Primary use case**: Multi-signature workflows where multiple parties need to sign authorization entries before submission
  - **Arguments**: Takes `Map<String, Any?>` (beginner-friendly) instead of `List<SCValXdr>` (XDR types)
  - **Returns**: `AssembledTransaction` for manual signing and submission
  - **Other use cases**: Adding memos, custom preconditions, simulation inspection, time bounds
  - **Example**:
    ```kotlin
    val assembled = client.buildInvoke<String>(
        functionName = "transfer",
        arguments = mapOf("from" to addr1, "to" to addr2, "amount" to 1000)
    )

    // Detect who needs to sign
    val whoNeedsToSign = assembled.needsNonInvokerSigningBy()
    if (whoNeedsToSign.contains(account1Id)) {
        assembled.signAuthEntries(account1Keypair)
    }

    val result = assembled.signAndSubmit(signer)
    ```

#### Migration Guide

**Factory Method Rename**:
```kotlin
// OLD (0.1.0-beta.1)
val client = ContractClient.fromNetwork(contractId, rpcUrl, Network.TESTNET)

// NEW (0.2.0+)
val client = ContractClient.forContract(contractId, rpcUrl, Network.TESTNET)
```

**Manual Transaction Control**:
```kotlin
// OLD (0.1.0-beta.1) - XDR-based
val xdrArgs = client.funcArgsToXdrSCValues("transfer", listOf(...))
val assembled = client.invokeWithXdr("transfer", xdrArgs)

// NEW (0.2.0+) - Map-based
val assembled = client.buildInvoke<T>(
    functionName = "transfer",
    arguments = mapOf("from" to addr1, "to" to addr2, "amount" to 1000)
)
```

**No-Spec Mode Removed**:
```kotlin
// OLD (0.1.0-beta.1)
val client = ContractClient.withoutSpec(contractId, rpcUrl, Network.TESTNET)

// NEW (0.2.0+) - Use low-level APIs instead
// ContractClient now always requires a spec
// For no-spec scenarios, use SorobanServer + TransactionBuilder directly
```

---

## [0.1.0-beta.1] - 2025-10-23

### Initial Beta Release

This is the first beta release of the Stellar SDK for Kotlin Multiplatform. The SDK provides comprehensive functionality for building Stellar applications across JVM, iOS, macOS, and JavaScript platforms.

**Status**: BETA - Not recommended for production use yet. API may change in subsequent beta releases.

### Platform Support

#### Supported Platforms
- **JVM**: Android API 24+ and Server applications (Java 11+)
- **iOS**: iOS 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS**: macOS 11.0+ (macosX64, macosArm64)
- **JavaScript**: Browser (WebAssembly) and Node.js 14+

### Features

#### Core Cryptography
- Ed25519 keypair generation, signing, and verification
- Production-ready crypto libraries on all platforms:
  - JVM: BouncyCastle (bcprov-jdk18on:1.78)
  - iOS/macOS: libsodium (native C interop)
  - JavaScript: libsodium-wrappers-sumo (WebAssembly)
- Constant-time operations for timing attack protection
- Comprehensive input validation and error handling
- Async API using Kotlin suspend functions for proper cross-platform support

#### StrKey Encoding
- Support for all Stellar address formats:
  - Ed25519 public keys (G... addresses)
  - Ed25519 secret seeds (S... seeds)
  - Muxed accounts (M... addresses)
  - Contract addresses (C... addresses)
- CRC16-XModem checksum validation
- Platform-optimized Base32 encoding

#### Transaction Building
- `TransactionBuilder` with fluent API
- `FeeBumpTransactionBuilder` for fee bump transactions
- All 27 Stellar operations implemented
- Memo support (none, text, ID, hash, return)
- Time bounds and ledger bounds
- Transaction preconditions (min sequence, sequence age/gap, extra signers)
- Multi-signature support
- Soroban transaction data (resource limits, footprint)
- XDR serialization/deserialization

#### Operations (All 27)
**Account Operations**:
- CreateAccount, AccountMerge, BumpSequence, SetOptions, ManageData

**Payment Operations**:
- Payment, PathPaymentStrictReceive, PathPaymentStrictSend

**Asset Operations**:
- ChangeTrust, AllowTrust, SetTrustLineFlags

**Trading Operations**:
- ManageSellOffer, ManageBuyOffer, CreatePassiveSellOffer

**Claimable Balance Operations**:
- CreateClaimableBalance, ClaimClaimableBalance, ClawbackClaimableBalance

**Liquidity Pool Operations**:
- LiquidityPoolDeposit, LiquidityPoolWithdraw

**Sponsorship Operations**:
- BeginSponsoringFutureReserves, EndSponsoringFutureReserves, RevokeSponsorship

**Clawback Operations**:
- Clawback

**Soroban Operations**:
- InvokeHostFunction, ExtendFootprintTTL, RestoreFootprint

**Deprecated Operations**:
- Inflation (protocol 12 deprecated, but supported for compatibility)

#### Assets & Accounts
- AssetTypeNative (XLM/Lumens)
- AssetTypeCreditAlphaNum4 (1-4 character asset codes)
- AssetTypeCreditAlphaNum12 (5-12 character asset codes)
- Asset parsing from canonical strings ("CODE:ISSUER")
- Contract ID derivation for Stellar Asset Contracts (SAC)
- Muxed accounts with ID support
- Account management with automatic sequence number handling

#### Horizon API Client
- Comprehensive REST API coverage
- Request builders for all endpoints:
  - Accounts (details, data entries, balances)
  - Assets (list, search, filter)
  - Claimable Balances (query, filter by sponsor/claimant/asset)
  - Effects (all 60+ effect types, filtering, streaming)
  - Ledgers (list, details, operations, transactions)
  - Liquidity Pools (list, details, operations, trades)
  - Offers (list by account, order books)
  - Operations (all 27 operation types, filtering, streaming)
  - Payments (payment filtering, streaming)
  - Trades (trade history, filtering, aggregations)
  - Transactions (submit, query, filter)
  - Paths (strict send, strict receive path finding)
  - Fee Stats (network fee statistics)
  - Health (server health monitoring)
  - Root (server information)
- Server-Sent Events (SSE) streaming support
- Automatic retries and error handling
- SEP-29 account memo validation (AccountRequiresMemoException)
- Cursor-based pagination
- Order (asc/desc) and limit parameter support

#### Soroban Smart Contracts

**High-Level API**:
- `ContractClient` with dual-mode interaction:
  - Beginner API: `invoke()` with Map<String, Any?> arguments
  - Power API: `invokeWithXdr()` with List<SCValXdr> for manual control (deprecated in 0.2.0, replaced by `buildInvoke()`)
- Factory methods: `fromNetwork()` loads contract spec (renamed to `forContract()` in 0.2.0), `withoutSpec()` for manual mode (removed in 0.2.0)
- Type conversion helpers:
  - `funcArgsToXdrSCValues()` - Convert native types to XDR
  - `funcResToNative()` - Convert XDR results to native types
- Smart contract deployment:
  - One-step: `deploy()` with Map-based constructor args
  - Two-step: `install()` + `deployFromWasmId()` for WASM reuse
- `AssembledTransaction` for full transaction lifecycle
- Type-safe generic results with custom parsers
- Automatic simulation and resource estimation
- Auto-execution: Read calls return results, write calls auto-sign and submit
- Read-only vs write call detection via auth entries

**Authorization**:
- Sign Soroban authorization entries (Auth class)
- Build authorization entries from scratch
- Custom Signer interface support
- Network replay protection
- Signature verification
- Auto-authorization for invoker
- Custom authorization handling for complex scenarios

**Contract Operations**:
- Contract invocation (InvokeHostFunctionOperation)
- WASM upload and contract deployment
- State restoration when expired
- Footprint TTL extension
- Transaction polling with exponential backoff

**RPC Client** (`SorobanServer`):
- Full Soroban RPC API coverage
- Transaction simulation
- Event queries and filtering
- Ledger and contract data retrieval
- Network information queries
- Health monitoring

**Contract Spec & Parsing**:
- ContractSpec parsing from XDR
- WASM analysis and metadata extraction
- Function signature detection
- Type parsing and validation

**Exception Handling** (10 types):
- ContractException (base)
- SimulationFailedException
- SendTransactionFailedException
- TransactionFailedException
- TransactionStillPendingException
- ExpiredStateException
- RestorationFailureException
- NotYetSimulatedException
- NeedsMoreSignaturesException
- NoSignatureNeededException

#### XDR System
- Complete XDR type system (470+ types)
- XDR serialization/deserialization
- Type-safe XDR unions and enums
- XDR validation

#### Scval (Smart Contract Values)
- Type conversions to/from SCValXdr
- Support for all Soroban types
- Address, symbol, bytes, numbers, vectors, maps
- Type validation and error handling

#### Utility Features
- Network support (TESTNET, PUBLIC, custom networks)
- FriendBot integration for testnet account funding
- Comprehensive error handling throughout

### Demo Application

Comprehensive multi-platform demo application showcasing SDK functionality:

**Platforms**:
- Android (Jetpack Compose)
- iOS (SwiftUI wrapper for Compose Multiplatform)
- macOS (Native SwiftUI)
- Desktop (JVM Compose)
- Web (Kotlin/JS with Compose, Vite dev server)

**Features** (10 comprehensive demos):
1. Key Generation - Generate and manage Ed25519 keypairs
2. Fund Testnet Account - Get free test XLM from Friendbot
3. Fetch Account Details - Retrieve account information from Horizon
4. Trust Asset - Establish trustlines for issued assets
5. Send Payment - Transfer XLM and issued assets
6. Fetch Transaction Details - View transaction operations and events
7. Fetch Smart Contract Details - Parse and inspect Soroban contracts
8. Deploy Smart Contract - Deploy Soroban WASM contracts to testnet
9. Invoke Hello World Contract - Simple contract invocation with result parsing
10. Invoke Auth Contract - Dynamic authorization handling

**Architecture**:
- 95% code sharing (Compose UI + business logic in commonMain)
- Real Stellar testnet integration
- Production-ready patterns and best practices

### Dependencies

**Common**:
- kotlinx-serialization: 1.6.3
- kotlinx-coroutines: 1.8.0
- kotlinx-datetime: 0.7.1
- ktor-client-core: 2.3.8
- bignum: 0.3.9

**JVM**:
- BouncyCastle: 1.78
- Apache Commons Codec: 1.16.1

**JavaScript**:
- libsodium-wrappers-sumo: 0.7.13 (via npm)

**Native (iOS/macOS)**:
- libsodium (via C interop)
- ktor-client-darwin: 2.3.8

### Testing

- Comprehensive test coverage across all platforms
- Unit tests for all core functionality
- Integration tests against live Stellar Testnet
- Contract client integration tests
- Platform-specific test suites (JVM, JS Node, JS Browser, Native)

### Documentation

- Comprehensive README with quick start guide
- Getting Started guide
- Features guide with examples
- Platform-specific setup guide
- Testing guide
- Architecture documentation (CLAUDE.md)
- Demo app documentation
- Horizon API compatibility matrix
- Soroban RPC compatibility matrix

### Known Limitations

1. **JavaScript Testing**: Running all test classes together currently hangs due to Kotlin/JS test bundling limitations. Individual test classes work perfectly. Workaround: Run tests with `--tests` filter or individually.

2. **iOS x86_64 Tests**: Disabled because libsodium.a only includes ARM64 architecture. x86_64 simulators are rare with Apple Silicon.

3. **Production Readiness**: This is a beta release. While comprehensive testing has been performed, we recommend additional testing before production use. API may change in subsequent beta releases.

### Breaking Changes

None (initial release).

### Migration Guide

None (initial release).

### Contributors

Built with Claude Code - AI-powered development assistant.

### Acknowledgments

- Inspired by the [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk)
- Uses production cryptography from BouncyCastle and libsodium
- Built with Kotlin Multiplatform, Ktor, and Compose Multiplatform

---

[0.1.0-beta.1]: https://github.com/Soneso/kmp-stellar-sdk/releases/tag/v0.1.0-beta.1
