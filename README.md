# Stellar SDK for Kotlin Multiplatform

**Status: ALPHA - Work In Progress**

A Kotlin Multiplatform SDK for building applications on the Stellar network. Write your Stellar integration once in Kotlin and deploy it across JVM (Android, Server), iOS, macOS, and Web (Browser/Node.js) platforms.

**Version:** 0.1.0-SNAPSHOT

## Platform Support

| Platform | Status | Crypto Library |
|----------|--------|----------------|
| JVM (Android, Server) | Supported | BouncyCastle |
| iOS | Supported | libsodium (native) |
| macOS | Supported | libsodium (native) |
| JavaScript (Browser) | Supported | libsodium.js (WebAssembly) |
| JavaScript (Node.js) | Supported | libsodium.js (WebAssembly) |

## What Is This?

The Stellar SDK for Kotlin Multiplatform enables you to:

- Build and sign Stellar transactions
- Generate and manage Ed25519 keypairs
- Connect to Horizon (Stellar's REST API server)
- Interact with Soroban smart contracts via RPC
- Run the same business logic on mobile, web, and server platforms

This SDK uses production-ready, audited cryptographic libraries on all platforms - no experimental or custom crypto implementations.

## Current Implementation Status

### Implemented Features

#### KeyPair Management
- Generate random keypairs with cryptographically secure randomness
- Create keypairs from secret seeds (String, CharArray, or ByteArray)
- Create public-only keypairs from account IDs
- Sign data with Ed25519
- Verify Ed25519 signatures
- Export to Stellar strkey format (G... accounts, S... seeds)
- Comprehensive input validation and error handling
- Thread-safe, immutable design

#### StrKey Encoding
- Encode/decode Ed25519 public keys (G... addresses)
- Encode/decode Ed25519 secret seeds (S... seeds)
- CRC16-XModem checksum validation
- Version byte validation
- Platform-specific Base32 implementations

#### Soroban Authorization
- Sign authorization entries for smart contract invocations
- Build authorization entries from scratch
- Custom signer interface support (hardware wallets, multi-sig)
- Network replay protection (network ID in signatures)
- Signature verification
- Immutable design (clones entries to avoid mutation)

#### Smart Contract Interaction
- High-level ContractClient API for contract calls
- AssembledTransaction for full transaction lifecycle
- Type-safe generic results with custom parsers
- Automatic simulation and resource estimation
- Read-only vs write call detection
- Authorization handling (auto-auth and custom)
- Automatic state restoration when needed
- Transaction polling with exponential backoff
- Comprehensive error handling (10+ exception types)

#### Transaction Support
- Transaction building and signing
- Fee bump transactions
- Multi-signature support
- Decorator signatures
- XDR serialization/deserialization

#### Network Communication
- Horizon REST API client
- Soroban RPC client
- Health endpoint monitoring
- Automatic retries and error handling

### Planned Features

- Additional transaction types
- Asset management
- Claimable balances
- Liquidity pools
- Path payments
- Additional Horizon endpoints
- Complete Soroban RPC coverage

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
1. File -> Add Package Dependencies
2. Search for: `https://github.com/jedisct1/swift-sodium`
3. Select the Clibsodium package

Or add to `Package.swift`:
```swift
dependencies: [
    .package(url: "https://github.com/jedisct1/swift-sodium", from: "0.9.1")
]
```

**Note:** The SDK framework includes static libsodium, but your app needs the Clibsodium headers for compilation.

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

// In a coroutine context
suspend fun example() {
    // Generate a random keypair
    val keypair = KeyPair.random()

    println("Account ID: ${keypair.getAccountId()}")
    println("Secret Seed: ${keypair.getSecretSeed()?.concatToString()}")
    println("Can Sign: ${keypair.canSign()}")
}

// Or use runBlocking for quick tests
fun main() = runBlocking {
    val keypair = KeyPair.random()
    println(keypair.getAccountId())
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

### Create Public-Only KeyPair

```kotlin
fun fromAccountId() {
    val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
    val keypair = KeyPair.fromAccountId(accountId)

    println("Can Sign: ${keypair.canSign()}") // false
    println("Account ID: ${keypair.getAccountId()}")
}
```

### Sign and Verify Data

```kotlin
suspend fun signAndVerify() {
    val keypair = KeyPair.random()
    val message = "Hello Stellar!"
    val data = message.encodeToByteArray()

    // Sign the data
    val signature = keypair.sign(data)
    println("Signature (${signature.size} bytes): ${signature.toHexString()}")

    // Verify with the same keypair
    val isValid = keypair.verify(data, signature)
    println("Signature valid: $isValid") // true

    // Create public-only keypair and verify
    val publicKeypair = KeyPair.fromAccountId(keypair.getAccountId())
    val stillValid = publicKeypair.verify(data, signature)
    println("Still valid with public key only: $stillValid") // true
}
```

### Platform-Specific Integration

#### Android (Jetpack Compose)

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StellarApp()
        }
    }
}

@Composable
fun StellarApp() {
    val scope = rememberCoroutineScope()
    var accountId by remember { mutableStateOf("") }

    Column {
        Button(onClick = {
            scope.launch {
                val keypair = KeyPair.random()
                accountId = keypair.getAccountId()
            }
        }) {
            Text("Generate KeyPair")
        }

        if (accountId.isNotEmpty()) {
            Text("Account: $accountId")
        }
    }
}
```

#### iOS (SwiftUI)

```swift
import SwiftUI
import stellar_sdk

class StellarViewModel: ObservableObject {
    @Published var accountId: String = ""

    func generateKeyPair() {
        Task {
            let keypair = try await KeyPair.companion.random()
            DispatchQueue.main.async {
                self.accountId = keypair.getAccountId()
            }
        }
    }
}

struct ContentView: View {
    @StateObject private var viewModel = StellarViewModel()

    var body: some View {
        VStack {
            Button("Generate KeyPair") {
                viewModel.generateKeyPair()
            }

            if !viewModel.accountId.isEmpty {
                Text("Account: \(viewModel.accountId)")
            }
        }
    }
}
```

#### JavaScript (Browser)

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
    val scope = MainScope()

    document.getElementById("generateBtn")?.addEventListener("click", {
        scope.launch {
            val keypair = KeyPair.random()
            document.getElementById("accountId")?.textContent = keypair.getAccountId()
        }
    })
}
```

#### JavaScript (Node.js)

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Generating Stellar keypair...")

    val keypair = KeyPair.random()
    println("Account ID: ${keypair.getAccountId()}")
    println("Secret Seed: ${keypair.getSecretSeed()?.concatToString()}")
    println("Crypto Library: ${KeyPair.getCryptoLibraryName()}")
}
```

### Smart Contract Interaction

```kotlin
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.SorobanServer

suspend fun callContract() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")
    val contractId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    val client = ContractClient(
        contractId = contractId,
        sorobanServer = server,
        network = Network.TESTNET
    )

    // Call a read-only method
    val result = client.invoke(
        method = "get_count",
        parameters = emptyList()
    )

    // For write operations, sign and submit
    val sourceKeypair = KeyPair.fromSecretSeed("SXXX...")
    val writeResult = client.invoke(
        method = "increment",
        parameters = emptyList(),
        source = sourceKeypair
    ).sign(sourceKeypair).send()
}
```

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
- **Library**: libsodium-wrappers (0.7.13 via npm)
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

### Async API Design

The SDK uses Kotlin's `suspend` functions for crypto operations:

- **JavaScript**: Required for async libsodium initialization
- **JVM/Native**: Zero overhead - suspend functions that don't suspend compile to regular functions
- **Consistent API**: Same async pattern works correctly on all platforms
- **Coroutine-friendly**: Natural integration with Kotlin coroutines ecosystem

## Testing

The SDK includes comprehensive test coverage across all platforms.

### Run Tests

```bash
# All tests
./gradlew test

# JVM tests only
./gradlew :stellar-sdk:jvmTest

# Run a specific test class
./gradlew :stellar-sdk:jvmTest --tests "KeyPairTest"

# Run a specific test method
./gradlew :stellar-sdk:jvmTest --tests "KeyPairTest.testRandomKeyPair"

# JavaScript tests (Node.js)
./gradlew :stellar-sdk:jsNodeTest --tests "KeyPairTest"

# JavaScript tests (Browser - requires Chrome)
./gradlew :stellar-sdk:jsBrowserTest --tests "KeyPairTest"

# Native tests
./gradlew :stellar-sdk:macosArm64Test
./gradlew :stellar-sdk:iosSimulatorArm64Test
```

**Note**: JavaScript tests work perfectly when run individually or with filters. Running all JS tests together is not currently supported due to Kotlin/JS bundling limitations.

### Integration Tests

The SDK includes integration tests against live Stellar Testnet:

- Location: `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/contract/ContractClientIntegrationTest.kt`
- Documentation: See `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/contract/INTEGRATION_TESTS_README.md`
- Status: `@Ignore`d by default (require testnet funding)

To run integration tests:
1. Remove the `@Ignore` annotation
2. Ensure testnet connectivity to `https://soroban-testnet.stellar.org:443`
3. Run: `./gradlew :stellar-sdk:jvmTest --tests "ContractClientIntegrationTest"`

## Sample Applications

The `stellarSample` directory contains a complete Kotlin Multiplatform sample app demonstrating best practices:

- **shared**: Common Kotlin business logic (KeyPair operations, signing, verification)
- **androidApp**: Android app with Jetpack Compose UI
- **iosApp**: iOS app with SwiftUI
- **macosApp**: macOS app with SwiftUI
- **webApp**: Web app with Kotlin/JS and HTML

**Architecture**: 500 lines of shared business logic written once, runs on all platforms with platform-native UIs.

### Run the Sample Apps

```bash
# Android
./gradlew :stellarSample:androidApp:installDebug

# iOS
./gradlew :stellarSample:shared:linkDebugFrameworkIosSimulatorArm64
cd stellarSample/iosApp && xcodegen generate && open StellarSample.xcodeproj

# macOS (requires brew install libsodium)
./gradlew :stellarSample:shared:linkDebugFrameworkMacosArm64
cd stellarSample/macosApp && xcodegen generate && open StellarSampleMac.xcodeproj

# Web (development mode)
./gradlew :stellarSample:webApp:jsBrowserDevelopmentRun

# Web (production build)
./gradlew :stellarSample:webApp:jsBrowserProductionWebpack
```

See `stellarSample/README.md` for detailed architecture documentation and code examples.

## Building

```bash
# Build all modules and run tests
./gradlew build

# Clean build
./gradlew clean build

# Assemble artifacts without tests
./gradlew assemble

# Build and test (same as build)
./gradlew check
```

### Native Development

```bash
# Build iOS framework
./gradlew :stellar-sdk:linkDebugFrameworkIosSimulatorArm64

# Build libsodium XCFramework (for framework distribution)
./build-libsodium-xcframework.sh

# Build SDK XCFramework (for framework distribution)
./build-xcframework.sh
```

## Project Structure

```
kmp-stellar-sdk/
├── stellar-sdk/                 # Main SDK library
│   ├── src/
│   │   ├── commonMain/          # Shared Kotlin code
│   │   ├── commonTest/          # Shared tests
│   │   ├── jvmMain/             # JVM-specific (BouncyCastle)
│   │   ├── jvmTest/             # JVM tests
│   │   ├── jsMain/              # JS-specific (libsodium.js)
│   │   ├── jsTest/              # JS tests
│   │   ├── nativeMain/          # Native shared code
│   │   ├── iosMain/             # iOS-specific
│   │   ├── iosTest/             # iOS tests
│   │   ├── macosMain/           # macOS-specific
│   │   └── macosTest/           # macOS tests
│   └── build.gradle.kts
│
├── stellarSample/               # Sample KMP application
│   ├── shared/                  # Shared business logic
│   ├── androidApp/              # Android UI
│   ├── iosApp/                  # iOS UI
│   ├── macosApp/                # macOS UI
│   └── webApp/                  # Web UI
│
├── CLAUDE.md                    # Detailed technical documentation
├── README.md                    # This file
├── LICENSE                      # Apache 2.0 License
└── build.gradle.kts             # Root build configuration
```

## Documentation

- **CLAUDE.md**: Comprehensive technical documentation, architecture notes, and development guidelines
- **stellarSample/README.md**: Sample app architecture and code examples
- **stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/contract/INTEGRATION_TESTS_README.md**: Integration test setup

## Dependencies

### Common
- kotlinx-serialization (1.6.3): JSON serialization
- kotlinx-coroutines (1.8.0): Async operations
- kotlinx-datetime (0.5.0): Date/time handling
- ktor-client (2.3.8): HTTP client
- bignum (0.3.9): BigInteger support

### JVM
- ktor-client-cio: JVM HTTP engine
- BouncyCastle (1.78): Ed25519 cryptography
- Apache Commons Codec (1.16.1): Base32 encoding

### JavaScript
- ktor-client-js: JS HTTP engine
- libsodium-wrappers (0.7.13): Ed25519 cryptography

### Native (iOS/macOS)
- ktor-client-darwin: Darwin HTTP engine
- libsodium: Ed25519 cryptography (via C interop)

## Requirements

- **Kotlin**: 2.0.21+
- **Gradle**: 8.0+
- **JVM**: Java 11+
- **Android**: API 24+ (Android 7.0)
- **iOS**: iOS 15+
- **macOS**: macOS 12+
- **Web**: Modern browsers with WebAssembly support

## Reference Implementation

This SDK uses the [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk) as a reference for API design and feature completeness. The goal is to provide a similar developer experience with the benefits of Kotlin Multiplatform.

## Contributing

This project is currently in alpha development. Contribution guidelines will be provided as the project matures.

For development:
1. Clone the repository
2. Open in IntelliJ IDEA or Android Studio
3. Run tests: `./gradlew test`
4. See `CLAUDE.md` for detailed development guidelines

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- Cryptography powered by [BouncyCastle](https://www.bouncycastle.org/), [libsodium](https://libsodium.org/), and [libsodium.js](https://github.com/jedisct1/libsodium.js)
- Network communication via [Ktor](https://ktor.io/)
- Inspired by the [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk)

## Support

- **Stellar Network**: [stellar.org](https://stellar.org)
- **Stellar Developers**: [developers.stellar.org](https://developers.stellar.org)
- **Discord**: [Stellar Developers Discord](https://discord.gg/stellardev)

---

**Note**: This SDK is under active development. APIs may change between releases. Use in production at your own risk until a stable 1.0 release.
