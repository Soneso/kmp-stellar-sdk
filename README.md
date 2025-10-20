# Stellar SDK for Kotlin Multiplatform

**Status: ALPHA - Work In Progress, do not use in production**

A comprehensive Kotlin Multiplatform SDK for building applications on the Stellar network. Write your Stellar integration once in Kotlin and deploy it across JVM (Android, Server), iOS, macOS, and Web (Browser/Node.js) platforms.

**Version:** 0.1.0-SNAPSHOT

## Platform Support

| Platform | Status | Crypto Library | Notes |
|----------|--------|----------------|-------|
| JVM (Android, Server) | Supported | BouncyCastle | Production-ready |
| iOS | Supported | libsodium (native) | iOS 14.0+ |
| macOS | Supported | libsodium (native) | macOS 11.0+ |
| JavaScript (Browser) | Supported | libsodium.js (WebAssembly) | Modern browsers |
| JavaScript (Node.js) | Supported | libsodium.js (WebAssembly) | Node.js 14+ |

## Quick Links

- [Getting Started](docs/getting-started.md) - Installation and basic usage
- [Features Guide](docs/features.md) - Complete features list with examples
- [Platform Guide](docs/platforms.md) - Platform-specific setup and notes
- [Testing Guide](docs/testing.md) - Running tests across platforms
- [Demo App](demo/README.md) - Multi-platform sample application
- [Development Guide](CLAUDE.md) - Architecture and development guidelines

## What Is This?

The Stellar SDK for Kotlin Multiplatform enables you to:

- Build and sign Stellar transactions
- Generate and manage Ed25519 keypairs
- Connect to Horizon (Stellar's REST API server)
- Interact with Soroban smart contracts via RPC
- Run the same business logic on mobile, web, and server platforms

This SDK uses production-ready, audited cryptographic libraries on all platforms - no experimental or custom crypto implementations.

## Current Implementation Status

### Core Features

#### Cryptography & Key Management
- Ed25519 keypair generation with cryptographically secure randomness
- Sign and verify operations
- KeyPair creation from secret seeds (String, CharArray, ByteArray) or account IDs
- StrKey encoding/decoding (G... accounts, S... seeds, M... muxed accounts, C... contracts)
- Production cryptography: BouncyCastle (JVM), libsodium (Native), libsodium.js (JS)

#### Transaction Building
- Transaction and FeeBumpTransaction support
- TransactionBuilder with fluent API
- All 27 Stellar operations implemented
- Memo support (text, ID, hash, return hash)
- Time bounds, ledger bounds, and preconditions
- Multi-signature support
- XDR serialization/deserialization

#### Assets & Accounts
- Native asset (Lumens/XLM)
- Issued assets (AlphaNum4, AlphaNum12)
- Asset creation, validation, and parsing
- Contract ID derivation for Stellar Asset Contracts (SAC)
- Account management and sequence number handling
- Muxed accounts (M... addresses)

#### Horizon API Client
- Comprehensive REST API coverage
- Request builders for all endpoints
- Server-Sent Events (SSE) streaming
- Automatic retries and error handling
- Endpoints: Accounts, Assets, Claimable Balances, Effects, Ledgers, Liquidity Pools, Offers, Operations, Payments, Trades, Transactions
- Fee statistics and health monitoring
- SEP-29 account memo validation

#### Soroban Smart Contracts
- High-level ContractClient API with dual-mode design:
  - **Beginner-friendly**: Map-based invoke() with native Kotlin types
  - **Power-user**: XDR-based invokeWithXdr() for full control
- Factory methods for client creation:
  - `fromNetwork()`: Loads contract spec for automatic type conversion
  - `withoutSpec()`: Manual XDR mode for advanced users
- Smart contract deployment:
  - `deploy()`: One-step WASM upload and deployment with constructor args
  - `install()` + `deployFromWasmId()`: Two-step for WASM reuse
- AssembledTransaction for full transaction lifecycle
- Type-safe generic results with custom parsers
- Automatic simulation and resource estimation
- Auto-execution: Read calls return results, write calls auto-sign and submit
- Read-only vs write call detection via auth entries
- Authorization handling (auto-auth and custom auth)
- Automatic state restoration when needed
- Transaction polling with exponential backoff
- Contract spec parsing and WASM analysis

#### Soroban RPC Client
- Full RPC API coverage
- Transaction simulation
- Event queries and filtering
- Ledger and contract data retrieval
- Network information queries
- Health monitoring

### Implemented Operations

All 27 Stellar operations are implemented:

**Account Operations**
- CreateAccount
- AccountMerge
- BumpSequence
- SetOptions
- ManageData

**Payment Operations**
- Payment
- PathPaymentStrictReceive
- PathPaymentStrictSend

**Asset Operations**
- ChangeTrust
- AllowTrust
- SetTrustLineFlags

**Trading Operations**
- ManageSellOffer
- ManageBuyOffer
- CreatePassiveSellOffer

**Claimable Balance Operations**
- CreateClaimableBalance
- ClaimClaimableBalance
- ClawbackClaimableBalance

**Liquidity Pool Operations**
- LiquidityPoolDeposit
- LiquidityPoolWithdraw

**Sponsorship Operations**
- BeginSponsoringFutureReserves
- EndSponsoringFutureReserves
- RevokeSponsorship

**Clawback Operations**
- Clawback

**Soroban Operations**
- InvokeHostFunction
- ExtendFootprintTTL
- RestoreFootprint

**Deprecated Operations**
- Inflation (deprecated in protocol 12)

## Installation

**Note:** This SDK is currently in alpha. Installation instructions will be provided when artifacts are published to Maven Central.

For now, you can include it as a source dependency in your Kotlin Multiplatform project:

```kotlin
// settings.gradle.kts
includeBuild("/path/to/kmp-stellar-sdk")

// In your module's build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:0.1.0-SNAPSHOT")
}
```

### Platform-Specific Requirements

#### iOS/macOS
Add libsodium via Swift Package Manager. In Xcode:
1. File → Add Package Dependencies
2. Search for: `https://github.com/jedisct1/swift-sodium`
3. Select the Clibsodium package

Or add to `Package.swift`:
```swift
dependencies: [
    .package(url: "https://github.com/jedisct1/swift-sodium", from: "0.9.1")
]
```

#### JavaScript
No additional setup required. The SDK automatically bundles and initializes libsodium.js.

#### JVM
No additional setup required. BouncyCastle is included as a dependency.

## Quick Start

### Generate a Random KeyPair

All cryptographic operations use Kotlin's `suspend` functions for proper async support across platforms.

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.runBlocking

suspend fun example() {
    // Generate a random keypair
    val keypair = KeyPair.random()

    println("Account ID: ${keypair.getAccountId()}")
    println("Secret Seed: ${keypair.getSecretSeed()?.concatToString()}")
}
```

### Create KeyPair from Secret Seed

```kotlin
suspend fun fromSeed() {
    val seed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
    val keypair = KeyPair.fromSecretSeed(seed)

    println("Account ID: ${keypair.getAccountId()}")
    // Output: GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D
}
```

### Build and Sign a Transaction

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun sendPayment() {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val sourceKeypair = KeyPair.fromSecretSeed("SXXX...")
    val sourceAccount = server.accounts().account(sourceKeypair.getAccountId())

    val destination = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"

    val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(
            PaymentOperation(
                destination = destination,
                asset = AssetTypeNative,
                amount = "10.0"
            )
        )
        .addMemo(MemoText("Test payment"))
        .setTimeout(300)
        .setBaseFee(100)
        .build()

    transaction.sign(sourceKeypair)

    val response = server.submitTransaction(transaction)
    println("Transaction successful: ${response.hash}")
}
```

### Interact with Soroban Smart Contracts

```kotlin
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.scval.Scv

suspend fun callContract() {
    // Load contract spec from network for automatic type conversion
    val client = ContractClient.fromNetwork(
        contractId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // Read-only call with simplified Map-based API
    val count = client.invoke<Long>(
        functionName = "get_count",
        arguments = emptyMap(),  // Native Kotlin types
        source = accountId,
        signer = null,  // No signer needed for read calls
        parseResultXdrFn = { Scv.fromUInt32(it).toLong() }
    )
    // Automatically executes and returns result directly

    println("Current count: $count")

    client.close()
}

// Example with arguments - token balance query
suspend fun getTokenBalance() {
    val client = ContractClient.fromNetwork(tokenContractId, rpcUrl, Network.TESTNET)

    val balance = client.invoke<Long>(
        functionName = "balance",
        arguments = mapOf("account" to accountAddress),  // Simple Map with native types
        source = sourceAccount,
        signer = null,
        parseResultXdrFn = { Scv.fromInt128(it).toLong() }
    )

    println("Balance: $balance")
}

// Example write operation - auto signs and submits
suspend fun transferTokens() {
    val client = ContractClient.fromNetwork(tokenContractId, rpcUrl, Network.TESTNET)

    client.invoke<Unit>(
        functionName = "transfer",
        arguments = mapOf(
            "from" to fromAddress,
            "to" to toAddress,
            "amount" to 1000
        ),
        source = sourceAccount,
        signer = keypair,  // Required for write operations
        parseResultXdrFn = null
    )
    // Automatically signs, submits, and polls for completion
}

// Deploy a new smart contract
suspend fun deployContract() {
    val wasmBytes = File("token.wasm").readBytes()

    // One-step deployment with constructor arguments
    val client = ContractClient.deploy(
        wasmBytes = wasmBytes,
        constructorArgs = mapOf(
            "admin" to adminAddress,
            "name" to "MyToken",
            "symbol" to "MTK",
            "decimals" to 7
        ),
        source = sourceAccount,
        signer = keypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org:443"
    )

    println("Contract deployed at: ${client.contractId}")
    // Client is ready to use with loaded spec
}

// Advanced: Deploy multiple contracts from same WASM
suspend fun deployMultipleContracts() {
    val wasmBytes = File("token.wasm").readBytes()

    // Step 1: Install WASM once
    val wasmId = ContractClient.install(
        wasmBytes = wasmBytes,
        source = sourceAccount,
        signer = keypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org:443"
    )

    // Step 2: Deploy multiple instances (saves fees)
    val token1 = ContractClient.deployFromWasmId(
        wasmId = wasmId,
        constructorArgs = listOf(
            Scv.toAddress(adminAddress),
            Scv.toString("Token1"),
            Scv.toString("TK1")
        ),
        source = sourceAccount,
        signer = keypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org:443"
    )

    val token2 = ContractClient.deployFromWasmId(
        wasmId = wasmId,
        constructorArgs = listOf(
            Scv.toAddress(adminAddress),
            Scv.toString("Token2"),
            Scv.toString("TK2")
        ),
        source = sourceAccount,
        signer = keypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org:443"
    )
}

// Power user: Manual XDR control
suspend fun advancedContractCall() {
    val client = ContractClient.withoutSpec(contractId, rpcUrl, Network.TESTNET)

    // Build XDR manually
    val assembled = client.invokeWithXdr(
        functionName = "transfer",
        parameters = listOf(
            Scv.toAddress(fromAddress),
            Scv.toAddress(toAddress),
            Scv.toInt128(1000)
        ),
        source = sourceAccount,
        signer = keypair
    )

    // Manual lifecycle control
    assembled.simulate()
    val result = assembled.signAndSubmit(keypair)
}
```

For more examples, see the [Getting Started Guide](docs/getting-started.md) and explore the [Demo App](demo/README.md).

## Demo Application

The [demo app](demo/README.md) showcases SDK usage across all platforms with 7 comprehensive features:

1. **Key Generation** - Generate and manage Ed25519 keypairs
2. **Fund Testnet Account** - Get free test XLM from Friendbot
3. **Fetch Account Details** - Retrieve account information from Horizon
4. **Trust Asset** - Establish trustlines for issued assets
5. **Send Payment** - Transfer XLM and issued assets
6. **Fetch Smart Contract Details** - Parse and inspect Soroban contracts
7. **Deploy Smart Contract** - Deploy Soroban WASM contracts to testnet

Run the demo:

```bash
# Android
./gradlew :demo:androidApp:installDebug

# iOS (requires Xcode)
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64
cd demo/iosApp && xcodegen generate && open StellarDemo.xcodeproj

# Desktop (macOS/Windows/Linux)
./gradlew :demo:desktopApp:run

# Web
./gradlew :demo:webApp:jsBrowserDevelopmentRun
```

## Documentation

### SDK Documentation
- [Getting Started](docs/getting-started.md) - Installation, setup, and first steps
- [Features Guide](docs/features.md) - Complete features list with code examples
- [Platform Guide](docs/platforms.md) - Platform-specific setup and requirements
- [Testing Guide](docs/testing.md) - Running and writing tests
- [Architecture Guide](CLAUDE.md) - Technical implementation details

### Demo App
- [Demo App Overview](demo/README.md) - Multi-platform sample application
- [Android Demo](demo/androidApp/README.md)
- [iOS Demo](demo/iosApp/README.md)
- [macOS Demo](demo/macosApp/README.md)
- [Desktop Demo](demo/desktopApp/README.md)
- [Web Demo](demo/webApp/README.md)

### External Resources
- [Stellar Documentation](https://developers.stellar.org/)
- [Horizon API Reference](https://developers.stellar.org/api/horizon)
- [Soroban Documentation](https://soroban.stellar.org/)
- [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk) (reference implementation)

## Cryptography

This SDK uses **production-ready, audited cryptographic libraries** exclusively:

### JVM Platform
- **Library**: BouncyCastle (org.bouncycastle:bcprov-jdk18on:1.78)
- **Algorithm**: Ed25519 (RFC 8032 compliant)
- **Features**: Mature, widely-audited, constant-time operations
- **Security**: Registered as JCA security provider

### Native Platforms (iOS/macOS)
- **Library**: libsodium (via C interop)
- **Algorithm**: Ed25519 via `crypto_sign_*` functions
- **Features**: Audited, constant-time, memory-safe operations
- **Random**: `randombytes_buf()` using system CSPRNG

### JavaScript Platforms (Browser & Node.js)
- **Library**: libsodium-wrappers-sumo (0.7.13 via npm)
- **Why Sumo**: Required for SHA-256 (crypto_hash_sha256) - not in standard build
- **Algorithm**: Ed25519 via `crypto_sign_*` functions
- **Features**: Same audited C library compiled to WebAssembly
- **Random**: `crypto.getRandomValues()` for key generation
- **Initialization**: Automatic - SDK handles async initialization internally

### Security Principles

1. **No Experimental Crypto**: Only battle-tested, audited libraries
2. **Constant-Time Operations**: Protection against timing attacks
3. **Memory Safety**: Defensive copies, CharArray for secrets, proper cleanup
4. **Input Validation**: All inputs validated before crypto operations
5. **Error Handling**: Comprehensive validation with clear error messages

## Testing

The SDK includes comprehensive test coverage across all platforms.

```bash
# All tests
./gradlew test

# JVM tests
./gradlew :stellar-sdk:jvmTest

# Specific test class
./gradlew :stellar-sdk:jvmTest --tests "KeyPairTest"

# JavaScript tests (Node.js)
./gradlew :stellar-sdk:jsNodeTest --tests "KeyPairTest"

# JavaScript tests (Browser - requires Chrome)
./gradlew :stellar-sdk:jsBrowserTest --tests "KeyPairTest"

# Native tests
./gradlew :stellar-sdk:macosArm64Test
./gradlew :stellar-sdk:iosSimulatorArm64Test
```

For detailed testing information, see the [Testing Guide](docs/testing.md).

## Requirements

- **Kotlin**: 2.0.21+
- **Gradle**: 8.0+
- **JVM**: Java 11+
- **Android**: API 24+ (Android 7.0)
- **iOS**: iOS 14+
- **macOS**: macOS 11+
- **Web**: Modern browsers with WebAssembly support

## Reference Implementation

This SDK uses the [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk) as a reference for API design and feature completeness. The goal is to provide a similar developer experience with the benefits of Kotlin Multiplatform.

## Contributing

This project is currently in alpha development. Contribution guidelines will be provided as the project matures.

For development:
1. Clone the repository
2. Open in IntelliJ IDEA or Android Studio
3. Run tests: `./gradlew test`
4. See [CLAUDE.md](CLAUDE.md) for detailed development guidelines

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- Cryptography powered by [BouncyCastle](https://www.bouncycastle.org/), [libsodium](https://libsodium.org/), and [libsodium.js](https://github.com/jedisct1/libsodium.js)
- Network communication via [Ktor](https://ktor.io/)
- Inspired by the [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk)
- Built with [Claude Code](https://claude.com/claude-code) - AI-powered development assistant

## Support

- **Stellar Network**: [stellar.org](https://stellar.org)
- **Stellar Developers**: [developers.stellar.org](https://developers.stellar.org)
- **Discord**: [Stellar Developers Discord](https://discord.gg/stellardev)

---

**Note**: This SDK is under active development. Do not use in production.
