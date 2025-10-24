# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
