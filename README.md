# Stellar SDK for Kotlin Multiplatform

[![Version](https://img.shields.io/badge/version-1.12.0-blue)](https://github.com/Soneso/kmp-stellar-sdk/releases)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-blueviolet?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Maven Central](https://img.shields.io/maven-central/v/com.soneso.stellar/stellar-sdk)](https://search.maven.org/artifact/com.soneso.stellar/stellar-sdk)
[![codecov](https://codecov.io/gh/Soneso/kmp-stellar-sdk/branch/main/graph/badge.svg)](https://codecov.io/gh/Soneso/kmp-stellar-sdk)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Soneso/kmp-stellar-sdk)

A Kotlin Multiplatform SDK for building applications on the Stellar Network. Write your Stellar integration once in Kotlin and deploy it across JVM (Android, Server), iOS, macOS, and Web (Browser/Node.js) platforms.

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.rpc.*
import com.soneso.stellar.sdk.rpc.responses.*
```

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
- [Demo App](demo/README.md) - Multi-platform sample application
- [Smart Accounts](docs/smart-accounts/README.md) - OpenZeppelin smart account support (passkeys, multi-signer, policies)
- [Smart Account Demo](smart-account-demo/README.md) - Smart account demo with passkey authentication
- [Development Guide](CLAUDE.md) - Architecture and development guidelines
- [Horizon API Compatibility](compatibility/horizon/HORIZON_COMPATIBILITY_MATRIX.md) - Supported Horizon endpoints
- [Soroban RPC Compatibility](compatibility/rpc/RPC_COMPATIBILITY_MATRIX.md) - Supported Soroban RPC methods
- [SEP Implementations](docs/sep/README.md) - Stellar Ecosystem Proposals support

## What Is This?

The Stellar SDK for Kotlin Multiplatform enables you to:

- Build and sign Stellar transactions
- Connect to Horizon (Stellar's REST API server)
- Interact with Soroban smart contracts via RPC
- Build smart account wallets with passkey authentication using [OpenZeppelin smart contracts](https://github.com/OpenZeppelin/stellar-contracts)
- Run the same business logic on mobile, web, desktop, and server platforms

This SDK uses audited cryptographic libraries on all platforms.

## What Can I Build?

With this SDK, you can create:

- **Smart Account Wallets** - Passkey-authenticated wallets with multi-signer authorization and policy-based access control
- **Wallets & Payment Apps** - Send/receive XLM and assets, manage accounts across platforms
- **DEX Interfaces** - Build trading interfaces, liquidity pools, and order book managers
- **Soroban DApps** - Deploy and interact with smart contracts from any platform
- **Token Issuance Platforms** - Create and distribute custom assets with trustline management
- **Cross-Border Payment Systems** - Leverage path payments for currency conversion
- **Account Services** - Multi-signature support, account recovery, and sponsorship flows
- **Mobile-First Apps** - iOS and Android apps sharing business logic with desktop, web and server

See the [demo app](demo/README.md) for examples.

## Features

The SDK provides:

- **Cryptography** - Ed25519 keypairs, signing, verification with production-ready libraries (BouncyCastle, libsodium)
- **Transaction Building** - TransactionBuilder with fluent API, all 27 Stellar operations, memos, time bounds, multi-signature support
- **Assets & Accounts** - Native (XLM) and issued assets, trustlines, muxed accounts, SAC contract ID derivation
- **Horizon API Client** - Full REST API coverage with request builders, SSE streaming, automatic retries, SEP-29 validation
- **Soroban Smart Contracts** - High-level ContractClient with beginner-friendly Map-based API and power-user mode, plus typed clients generated with stellar-contract-bindings
- **Soroban RPC Client** - Transaction simulation, event queries, ledger data, contract deployment and invocation
- **Contract Deployment** - One-step deploy() or two-step install/deployFromWasmId for WASM reuse
- **Authorization** - Automatic and custom auth handling with signature verification
- **Smart Accounts** - [OpenZeppelin smart account](https://github.com/OpenZeppelin/stellar-contracts) support with WebAuthn passkeys, multi-signer authorization, context rules, and policies (threshold, weighted threshold, spending limit)
- **SEP Support** - SEP-1 (stellar.toml), SEP-2 (Federation), SEP-5 (Key Derivation), SEP-6 (Deposit and Withdrawal API), SEP-8 (Regulated Assets), SEP-9/12 (KYC), SEP-10 (Web Authentication), SEP-24 (Hosted Deposit/Withdrawal), SEP-30 (Account Recovery), SEP-31 (Cross-Border Payments), SEP-38 (Anchor RFQ), SEP-45 (Web Authentication for Contracts), SEP-46 (Contract Meta), SEP-47 (Contract Interface Discovery), SEP-48 (Contract Interface Specification), SEP-51 (XDR-JSON), SEP-53 (Sign and Verify Messages)

## Installation

Add the SDK as a Maven dependency (recommended for most projects):

```kotlin
// In your module's build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.12.0")
}
```

See [Platform-Specific Requirements](#platform-specific-requirements) below and [docs/platforms/](docs/platforms/) for detailed setup instructions

### Platform-Specific Requirements

Most platforms require no additional setup. The SDK includes all necessary cryptographic libraries (BouncyCastle for JVM/Android, libsodium.js for JavaScript/Web).

**Exception - Native iOS/macOS SwiftUI/UIKit Apps:**
For native Swift apps where Swift code directly uses SDK types, add libsodium:
- **iOS**: Add via Swift Package Manager: `https://github.com/jedisct1/swift-sodium`
- **macOS Native**: Install via Homebrew: `brew install libsodium`

See [docs/platforms/](docs/platforms/) for detailed platform-specific instructions.

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

### Fetch Data from Horizon

```kotlin
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun fetchAccountData() {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"

    val account = server.accounts().account(accountId)
    println("Sequence: ${account.sequenceNumber}")
    println("Balances: ${account.balances}")
}
```

### Build and Sign a Transaction

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun sendPayment() {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val sourceKeypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
    val sourceAccount = server.loadAccount(sourceKeypair.getAccountId())

    val destination = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"

    val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
        .setBaseFee(100) // stroops per operation
        .addOperation(
            PaymentOperation(destination, AssetTypeNative, "10.0")
        )
        .addMemo(MemoText("Test payment"))
        .build()

    transaction.sign(sourceKeypair)

    val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
    println("Transaction successful: ${response.hash}")
}
```

### Fetch Data from Soroban RPC

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus

suspend fun fetchTransactionData() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")
    val txHash = "3389e9f0f1a65f19736cacf544c2e825313e8447f569233bb8db39aa607c8889"

    val response = server.getTransaction(txHash)
    when (response.status) {
        GetTransactionStatus.SUCCESS -> {
            println("Transaction succeeded in ledger ${response.ledger}")
            println("Result: ${response.resultXdr}")
        }
        GetTransactionStatus.FAILED -> {
            println("Transaction failed: ${response.resultXdr}")
        }
        GetTransactionStatus.NOT_FOUND -> {
            println("Transaction not yet in ledger")
        }
    }

    server.close()
}
```

### Interact with Soroban Smart Contracts

The SDK provides a high-level ContractClient API with automatic type conversion:

**Simple invocation** with auto-execution:
```kotlin
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.scval.Scv

val account = Account("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", 1L)
val amount = "100"
// accountAddress, fromAddress, keypair, sourceAccount, toAddress, toLong: from the previous steps of this flow

suspend fun callContract() {
    // Load contract spec from network
    val client = ContractClient.forContract(
        contractId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // Query with Map-based arguments (auto-executes)
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

**Multi-signature workflows** with buildInvoke for manual control:
```kotlin
val amount = "100"
val sourceKeypair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
val rpcUrl = "https://soroban-testnet.stellar.org"
// getKeypairFor: your app's lookup from signer address to KeyPair
suspend fun multiSigContractCall() {
    val client = ContractClient.forContract(contractId, rpcUrl, Network.TESTNET)
    val assembled = client.buildInvoke<Unit>(
        functionName = "transfer",
        arguments = mapOf(
            "from" to sourceKeypair.getAccountId(),
            "to" to "GCVSEBHB6CTMEHUHIUY4DDFMWQ7PJTHFZGOK2JUD5EG2ARNVS6S22E3K",
            "amount" to 10L
        ),
        source = sourceKeypair.getAccountId(),
        signer = null // sign later, after collecting the other signatures
    )

    // Detect and sign for additional signers
    val whoNeedsToSign = assembled.needsNonInvokerSigningBy()
    whoNeedsToSign.forEach { assembled.signAuthEntries(getKeypairFor(it)) }

    val result = assembled.signAndSubmit(sourceKeypair)
}
```

**Typed clients** generated from a contract spec with [stellar-contract-bindings](https://github.com/lightsail-network/stellar-contract-bindings) (`stellar-contract-bindings kmp`) replace the map-based arguments and result parsers above with one Kotlin method per contract function; see [Contract Bindings](docs/sdk-usage-examples.md#contract-bindings).

For deployment examples, authorization patterns, and advanced usage, see the [Getting Started Guide](docs/getting-started.md) and [Demo App](demo/README.md).

## Demo Applications

This repository includes two demo applications that showcase different aspects of the SDK.

### SDK Demo

The [demo app](demo/README.md) showcases SDK usage across all platforms with 11 features:

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
11. **Invoke Token Contract** - SEP-41 token contract interaction with multi-signature workflows

See [demo/README.md](demo/README.md) for screenshots and platform-specific build instructions.

### Smart Accounts Demo

The [smart account demo](smart-account-demo/README.md) is a Kotlin Multiplatform application (Android, iOS, macOS, Web) that exercises the Smart Account Kit SDK with WebAuthn passkey authentication on Stellar testnet. It covers wallet creation and connection, XLM and token transfers, multi-signer authorization, and on-chain context rule management (context rules, policies, signer configuration).

See [smart-account-demo/README.md](smart-account-demo/README.md) for feature details, architecture, and platform build instructions.

## Agent Skill

This repository includes an [Agent Skill](https://agentskills.io) that teaches AI coding agents how to use this SDK. See [skills/](skills/) for installation instructions.

## Documentation

### SDK Documentation
- [Docs folder](docs/README.md) - containing getting started guide, many sdk usage examples, advanced topics, api documentation and more.

### External Resources
- [Stellar Documentation](https://developers.stellar.org/)
- [Horizon API Reference](https://developers.stellar.org/api/horizon)
- [Soroban Documentation](https://soroban.stellar.org/)

## Cryptography

This SDK uses **production-ready, audited cryptographic libraries** on all platforms:

- **JVM**: BouncyCastle for Ed25519 operations
- **iOS/macOS**: libsodium (native C interop)
- **JavaScript**: libsodium-wrappers-sumo (WebAssembly)

All implementations provide constant-time operations, proper memory safety, and strict input validation. No experimental or custom cryptography.

## Testing

See [Testing Guide](docs/testing.md) for information on running tests.

## Requirements

- **Kotlin**: 2.2.20
- **Gradle**: 9.0+
- **JVM**: Java 17+
- **Android**: API 24+ (Android 7.0)
- **iOS**: iOS 14.0+
- **macOS**: macOS 11.0+
- **JavaScript**: Node.js 14+, modern browsers with WebAssembly support

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on reporting issues, proposing features, and submitting pull requests.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- Cryptography powered by [BouncyCastle](https://www.bouncycastle.org/), [libsodium](https://libsodium.org/), and [libsodium.js](https://github.com/jedisct1/libsodium.js)
- Network communication via [Ktor](https://ktor.io/)
- Inspired by the [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk)
- Built with [Claude Code](https://claude.com/claude-code) - AI-powered development assistant
