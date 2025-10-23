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
- [Horizon API Compatibility](compatibility/horizon/HORIZON_COMPATIBILITY_MATRIX.md) - Supported Horizon endpoints
- [Soroban RPC Compatibility](compatibility/rpc/RPC_COMPATIBILITY_MATRIX.md) - Supported Soroban RPC methods

## What Is This?

The Stellar SDK for Kotlin Multiplatform enables you to:

- Build and sign Stellar transactions
- Generate and manage Ed25519 keypairs
- Connect to Horizon (Stellar's REST API server)
- Interact with Soroban smart contracts via RPC
- Run the same business logic on mobile, web, and server platforms

This SDK uses production-ready, audited cryptographic libraries on all platforms - no experimental or custom crypto implementations.

## What Can I Build?

With this SDK, you can create:

- **Wallets & Payment Apps** - Send/receive XLM and assets, manage accounts across platforms
- **DEX Interfaces** - Build trading interfaces, liquidity pools, and order book managers
- **Soroban DApps** - Deploy and interact with smart contracts from any platform
- **Token Issuance Platforms** - Create and distribute custom assets with trustline management
- **Cross-Border Payment Systems** - Leverage path payments for currency conversion
- **Account Services** - Multi-signature support, account recovery, and sponsorship flows
- **Mobile-First Apps** - iOS and Android apps sharing business logic with web and server

See the [demo app](demo/README.md) for working examples of each feature category.

## Features

The SDK provides comprehensive Stellar functionality:

- **Cryptography** - Ed25519 keypairs, signing, verification with production-ready libraries (BouncyCastle, libsodium)
- **Transaction Building** - TransactionBuilder with fluent API, all 27 Stellar operations, memos, time bounds, multi-signature support
- **Assets & Accounts** - Native (XLM) and issued assets, trustlines, muxed accounts, SAC contract ID derivation
- **Horizon API Client** - Full REST API coverage with request builders, SSE streaming, automatic retries, SEP-29 validation
- **Soroban Smart Contracts** - High-level ContractClient with beginner-friendly Map-based API and power-user XDR mode
- **Soroban RPC Client** - Transaction simulation, event queries, ledger data, contract deployment and invocation
- **Contract Deployment** - One-step deploy() or two-step install/deployFromWasmId for WASM reuse
- **Authorization** - Automatic and custom auth handling with signature verification

See the [Features Guide](docs/features.md) for detailed documentation and examples.

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

The SDK provides a high-level ContractClient API with two modes:

**Beginner-friendly mode** with automatic type conversion:
```kotlin
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.scval.Scv

suspend fun callContract() {
    // Load contract spec from network
    val client = ContractClient.fromNetwork(
        contractId = "CDLZ...",
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // Query with Map-based arguments
    val balance = client.invoke<Long>(
        functionName = "balance",
        arguments = mapOf("account" to accountAddress),
        source = sourceAccount,
        signer = null,  // No signer for read calls
        parseResultXdrFn = { Scv.fromInt128(it).toLong() }
    )
    println("Balance: $balance")

    // Write operation - auto signs and submits
    client.invoke<Unit>(
        functionName = "transfer",
        arguments = mapOf(
            "from" to fromAddress,
            "to" to toAddress,
            "amount" to 1000
        ),
        source = sourceAccount,
        signer = keypair,  // Required for writes
        parseResultXdrFn = null
    )

    client.close()
}
```

**Power user mode** with manual XDR control:
```kotlin
suspend fun advancedContractCall() {
    val client = ContractClient.withoutSpec(contractId, rpcUrl, Network.TESTNET)

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

    assembled.simulate()
    val result = assembled.signAndSubmit(keypair)
}
```

For deployment examples, authorization patterns, and advanced usage, see the [Getting Started Guide](docs/getting-started.md) and [Demo App](demo/README.md).

## Demo Application

The [demo app](demo/README.md) showcases SDK usage across all platforms with 10 comprehensive features:

1. **Key Generation** - Generate and manage Ed25519 keypairs
2. **Fund Testnet Account** - Get free test XLM from Friendbot
3. **Fetch Account Details** - Retrieve account information from Horizon
4. **Trust Asset** - Establish trustlines for issued assets
5. **Send Payment** - Transfer XLM and issued assets
6. **Fetch Transaction Details** - View transaction operations and events from Horizon or Soroban RPC
7. **Fetch Smart Contract Details** - Parse and inspect Soroban contracts
8. **Deploy Smart Contract** - Deploy Soroban WASM contracts to testnet
9. **Invoke Hello World Contract** - Simple contract invocation with automatic result parsing
10. **Invoke Auth Contract** - Dynamic authorization handling for same-invoker and different-invoker scenarios

Run the demo:

```bash
# Android
./gradlew :demo:androidApp:installDebug

# iOS (requires Xcode)
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64
cd demo/iosApp && xcodegen generate && open StellarDemo.xcodeproj

# macOS Native (requires Xcode + libsodium)
brew install libsodium
./gradlew :demo:shared:linkDebugFrameworkMacosArm64
cd demo/macosApp && xcodegen generate && open StellarDemo.xcodeproj

# Desktop (macOS/Windows/Linux - Compose)
./gradlew :demo:desktopApp:run

# Web (Vite dev server with hot reload)
./gradlew :demo:webApp:viteDev
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

## Cryptography

This SDK uses **production-ready, audited cryptographic libraries** on all platforms:

- **JVM**: BouncyCastle for Ed25519 operations
- **iOS/macOS**: libsodium (native C interop)
- **JavaScript**: libsodium-wrappers-sumo (WebAssembly)

All implementations provide constant-time operations, proper memory safety, and comprehensive input validation. No experimental or custom cryptography. See the [Platform Guide](docs/platforms.md) for detailed implementation information.

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
- Inspired by the [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk) and the [Flutter Stellar SDK](https://github.com/Soneso/stellar_flutter_sdk)
- Built with [Claude Code](https://claude.com/claude-code) - AI-powered development assistant

---

**Note**: This SDK is under active development. Do not use in production.
