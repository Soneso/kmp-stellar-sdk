# Architecture Guide

This document provides a comprehensive overview of the Stellar KMP SDK's architecture, design decisions, and implementation details.

## Table of Contents

- [Project Structure](#project-structure)
- [Multiplatform Architecture](#multiplatform-architecture)
- [Cryptographic Implementation](#cryptographic-implementation)
- [Security Architecture](#security-architecture)
- [Async API Design](#async-api-design)
- [Module Organization](#module-organization)
- [Data Flow Architecture](#data-flow-architecture)
- [Design Patterns](#design-patterns)
- [Performance Considerations](#performance-considerations)

## Project Structure

```
kmp-stellar-sdk/
├── stellar-sdk/                    # Main SDK library
│   ├── src/
│   │   ├── commonMain/            # Shared Kotlin code (95% of SDK)
│   │   │   ├── contract/          # Smart contract client
│   │   │   ├── crypto/            # Crypto abstractions
│   │   │   ├── horizon/           # Horizon API client
│   │   │   ├── rpc/               # Soroban RPC client
│   │   │   ├── scval/             # Smart contract values
│   │   │   └── xdr/               # XDR serialization
│   │   ├── commonTest/            # Shared tests
│   │   ├── jvmMain/               # JVM-specific (BouncyCastle)
│   │   ├── jsMain/                # JS-specific (libsodium.js)
│   │   ├── nativeMain/            # Native shared (libsodium)
│   │   ├── iosMain/               # iOS-specific
│   │   └── macosMain/             # macOS-specific
│   └── build.gradle.kts
└── stellarSample/                 # Sample applications
    ├── shared/                    # Shared business logic
    ├── androidApp/                # Android UI
    ├── iosApp/                   # iOS UI
    ├── macosApp/                 # macOS UI
    └── webApp/                   # Web UI
```

## Multiplatform Architecture

### Core Principles

1. **Maximum Code Sharing**: ~95% of code is in `commonMain`, shared across all platforms
2. **Platform-Specific Optimizations**: Critical components (crypto, networking) use platform-native implementations
3. **Consistent API Surface**: Same API across all platforms, with platform-specific implementations hidden
4. **Type Safety**: Leverage Kotlin's type system for compile-time safety

### Source Set Hierarchy

```mermaid
graph TD
    commonMain[commonMain<br/>Shared Logic]
    commonTest[commonTest<br/>Shared Tests]

    jvmMain[jvmMain<br/>JVM/Android]
    jsMain[jsMain<br/>Browser/Node.js]
    nativeMain[nativeMain<br/>Native Shared]

    iosMain[iosMain<br/>iOS Specific]
    macosMain[macosMain<br/>macOS Specific]

    commonMain --> jvmMain
    commonMain --> jsMain
    commonMain --> nativeMain

    nativeMain --> iosMain
    nativeMain --> macosMain

    commonTest --> jvmTest
    commonTest --> jsTest
    commonTest --> iosTest
    commonTest --> macosTest
```

### Expect/Actual Pattern

The SDK uses Kotlin's expect/actual mechanism for platform-specific implementations:

```kotlin
// commonMain - Declaration
expect object Ed25519 {
    suspend fun generatePrivateKey(): ByteArray
    suspend fun derivePublicKey(privateKey: ByteArray): ByteArray
    suspend fun sign(message: ByteArray, privateKey: ByteArray): ByteArray
    suspend fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}

// jvmMain - Implementation
actual object Ed25519 {
    actual suspend fun generatePrivateKey(): ByteArray {
        // BouncyCastle implementation
    }
    // ...
}

// jsMain - Implementation
actual object Ed25519 {
    actual suspend fun generatePrivateKey(): ByteArray {
        // libsodium.js implementation
    }
    // ...
}
```

## Cryptographic Implementation

### Platform-Specific Libraries

| Platform | Library | Algorithm | Security Features |
|----------|---------|-----------|-------------------|
| JVM | BouncyCastle 1.78 | Ed25519 (RFC 8032) | FIPS 140-2 Level 1, Constant-time |
| JavaScript | libsodium-wrappers-sumo 0.7.13 | Ed25519, SHA-256 | Audited, WebAssembly sandboxed, Sumo for SHA-256 |
| iOS/macOS | libsodium (native) | Ed25519 (crypto_sign) | Audited, Constant-time, Memory-safe |

### Cryptographic Operations Flow

```mermaid
sequenceDiagram
    participant App
    participant KeyPair
    participant Ed25519
    participant Platform

    App->>KeyPair: random()
    KeyPair->>Ed25519: generatePrivateKey()
    Ed25519->>Platform: Platform-specific RNG
    Platform-->>Ed25519: 32 random bytes
    Ed25519->>Ed25519: derivePublicKey()
    Ed25519-->>KeyPair: public + private keys
    KeyPair-->>App: KeyPair instance

    App->>KeyPair: sign(data)
    KeyPair->>Ed25519: sign(data, privateKey)
    Ed25519->>Platform: Platform crypto
    Platform-->>Ed25519: 64-byte signature
    Ed25519-->>KeyPair: signature
    KeyPair-->>App: signature bytes
```

### Key Generation Security

#### JVM (BouncyCastle)
```kotlin
// Uses java.security.SecureRandom
val keyParams = Ed25519PrivateKeyParameters(SecureRandom())
```

#### JavaScript (libsodium.js)
```kotlin
// Uses crypto.getRandomValues() or Node.js crypto
await sodium.ready
const privateKey = sodium.crypto_sign_keypair().privateKey
```

#### Native (iOS/macOS)
```kotlin
// Uses arc4random_buf() system CSPRNG
val privateKey = ByteArray(32)
randombytes_buf(privateKey.refTo(0), 32)
```

## Security Architecture

### Defense in Depth

1. **Input Validation Layer**
   - All inputs validated before processing
   - Length checks, format validation
   - Type safety through Kotlin's type system

2. **Cryptographic Layer**
   - Only audited, production-ready libraries
   - No custom crypto implementations
   - Constant-time operations

3. **Memory Management Layer**
   - Defensive copies of sensitive data
   - CharArray for secrets (can be zeroed)
   - Immutable public APIs

4. **Network Security Layer**
   - HTTPS only for Horizon/RPC
   - Certificate pinning support (platform-specific)
   - Retry logic with exponential backoff

### Threat Model and Mitigations

| Threat | Mitigation |
|--------|------------|
| Private key exposure | CharArray usage, defensive copies, immutable KeyPair |
| Timing attacks | Constant-time crypto operations |
| Memory dumps | Minimize secret lifetime, zero arrays after use |
| Network MITM | HTTPS enforcement, optional cert pinning |
| Replay attacks | Network-specific signatures, nonces |
| Invalid crypto params | Comprehensive input validation |

### Security Boundaries

```mermaid
graph LR
    subgraph "Untrusted"
        Network[Network I/O]
        UserInput[User Input]
    end

    subgraph "SDK Boundary"
        Validation[Input Validation]
        Crypto[Crypto Operations]
        State[State Management]
    end

    subgraph "Trusted"
        Keys[Key Storage]
        Platform[Platform Crypto]
    end

    UserInput --> Validation
    Network --> Validation
    Validation --> Crypto
    Crypto --> Platform
    State --> Keys
```

## Async API Design

### Why Suspend Functions?

The SDK uses Kotlin's `suspend` functions for all cryptographic operations:

```kotlin
class KeyPair {
    suspend fun sign(data: ByteArray): ByteArray
    suspend fun verify(data: ByteArray, signature: ByteArray): Boolean

    companion object {
        suspend fun random(): KeyPair
        suspend fun fromSecretSeed(seed: String): KeyPair
    }
}
```

**Rationale:**

1. **JavaScript Requirement**: libsodium.js requires async initialization
2. **Consistency**: Same API pattern across all platforms
3. **Zero Overhead**: On JVM/Native, suspend functions that don't actually suspend compile to regular functions
4. **Future-Proof**: Allows for async hardware wallet integration

### Coroutine Context Usage

```kotlin
// Android
lifecycleScope.launch {
    val keypair = KeyPair.random()
    updateUI(keypair)
}

// iOS (from Swift)
Task {
    let keypair = try await KeyPair.companion.random()
    DispatchQueue.main.async {
        self.updateUI(keypair)
    }
}

// JavaScript
MainScope().launch {
    val keypair = KeyPair.random()
    updateDOM(keypair)
}

// Server/JVM
runBlocking {
    val keypair = KeyPair.random()
    processKeypair(keypair)
}
```

## Module Organization

### Core Modules

#### `com.soneso.stellar.sdk`
Core SDK functionality:
- `KeyPair` - Key management
- `Transaction` - Transaction building
- `Operation` - All Stellar operations
- `Asset` - Asset representation
- `Network` - Network configuration

#### `com.soneso.stellar.sdk.horizon`
Horizon API client:
- `HorizonServer` - Main client
- `responses/` - Response models
- `requests/` - Request builders

#### `com.soneso.stellar.sdk.rpc`
Soroban RPC client:
- `SorobanServer` - RPC client
- Contract data queries
- Transaction simulation

#### `com.soneso.stellar.sdk.contract`
Smart contract interaction:
- `ContractClient` - High-level client with dual API patterns
  - Simple Map-based invoke for beginners (auto-converts native types)
  - Advanced XDR-based invokeWithXdr for power users
  - One-step and two-step deployment capabilities
- `AssembledTransaction` - Transaction lifecycle management
- `ClientOptions` - Configuration for contract operations
- `ContractSpec` - Type conversion between native and XDR
- `exception/` - Contract-specific exceptions (10 types)

#### `com.soneso.stellar.sdk.xdr`
XDR serialization:
- 470+ XDR type definitions
- Encoding/decoding logic
- Binary serialization

### Dependency Graph

```mermaid
graph TD
    App[Application]
    SDK[stellar.sdk]
    Horizon[horizon]
    RPC[rpc]
    Contract[contract]
    XDR[xdr]
    Crypto[crypto]

    App --> Contract
    App --> SDK
    Contract --> SDK
    Contract --> RPC
    SDK --> XDR
    SDK --> Crypto
    Horizon --> SDK
    RPC --> SDK
```

## Data Flow Architecture

### Transaction Flow

```mermaid
sequenceDiagram
    participant User
    participant SDK
    participant Horizon
    participant Network

    User->>SDK: Build Transaction
    SDK->>SDK: Serialize to XDR
    SDK->>SDK: Sign with KeyPair
    SDK->>Horizon: Submit Transaction
    Horizon->>Network: Broadcast
    Network-->>Horizon: Consensus Result
    Horizon-->>SDK: Response
    SDK-->>User: Transaction Result
```

### Smart Contract Interaction Flow

```mermaid
sequenceDiagram
    participant App
    participant ContractClient
    participant AssembledTx
    participant SorobanRPC
    participant Network

    App->>ContractClient: invoke(method, params)
    ContractClient->>AssembledTx: create
    AssembledTx->>SorobanRPC: simulate
    SorobanRPC-->>AssembledTx: resources
    AssembledTx->>AssembledTx: sign
    AssembledTx->>SorobanRPC: submit
    SorobanRPC->>Network: broadcast
    Network-->>SorobanRPC: result
    SorobanRPC-->>AssembledTx: status
    AssembledTx->>AssembledTx: poll status
    AssembledTx-->>App: final result
```

## Design Patterns

### Builder Pattern

Used extensively for transaction construction:

```kotlin
val transaction = TransactionBuilder(account, network)
    .addOperation(PaymentOperation(...))
    .addOperation(CreateAccountOperation(...))
    .addMemo(Memo.text("Payment"))
    .setBaseFee(100)
    .setTimeout(300)
    .build()
```

### Factory Pattern

KeyPair creation uses static factory methods:

```kotlin
companion object {
    suspend fun random(): KeyPair
    suspend fun fromSecretSeed(seed: String): KeyPair
    fun fromAccountId(accountId: String): KeyPair
    fun fromPublicKey(publicKey: ByteArray): KeyPair
}
```

### Immutable Objects

All public API objects are immutable:

```kotlin
class KeyPair private constructor(
    private val publicKey: ByteArray,
    private val privateKey: ByteArray?
) {
    // No setters, all properties are private/readonly
    fun getPublicKey(): ByteArray = publicKey.copyOf()
}
```

### Result Pattern

Used for error handling without exceptions:

```kotlin
sealed class Result<T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Error<T>(val error: Exception) : Result<T>()
}

fun parseAsset(input: String): Result<Asset> {
    return try {
        Result.Success(Asset.fromCanonical(input))
    } catch (e: Exception) {
        Result.Error(e)
    }
}
```

## Performance Considerations

### Platform-Specific Optimizations

#### JVM
- Direct ByteBuffer usage for large data
- Efficient array operations
- JIT compilation benefits

#### JavaScript
- WebAssembly for crypto (near-native speed)
- Efficient typed arrays
- Worker support for heavy operations

#### Native (iOS/macOS)
- Direct C interop (zero overhead)
- Memory-mapped I/O for large files
- Objective-C runtime optimizations

### Caching Strategies

```kotlin
class HorizonServer {
    private val accountCache = LRUCache<String, AccountResponse>(100)

    suspend fun loadAccount(accountId: String): AccountResponse {
        return accountCache.getOrPut(accountId) {
            fetchAccountFromNetwork(accountId)
        }
    }
}
```

### Lazy Initialization

```kotlin
class Transaction {
    private val _hash: Lazy<ByteArray> = lazy {
        // Expensive hash calculation
        sha256(getEnvelopeBytes())
    }

    val hash: ByteArray
        get() = _hash.value
}
```

### Resource Management

```kotlin
class SorobanServer : Closeable {
    private val httpClient = HttpClient()

    override fun close() {
        httpClient.close()
        // Clean up resources
    }
}

// Usage with use block
SorobanServer(url).use { server ->
    // Server is automatically closed
    server.simulateTransaction(tx)
}
```

## Architecture Decision Records (ADRs)

### ADR-001: Async Crypto API

**Status**: Accepted

**Context**: JavaScript's libsodium requires async initialization

**Decision**: Use suspend functions for all crypto operations

**Consequences**:
- ✅ Consistent API across platforms
- ✅ Zero overhead on JVM/Native
- ✅ Future-proof for async hardware wallets
- ⚠️ Requires coroutine context

### ADR-002: Production Crypto Only

**Status**: Accepted

**Context**: Security is paramount for a financial SDK

**Decision**: Only use audited, production crypto libraries

**Consequences**:
- ✅ High security confidence
- ✅ Avoid custom crypto pitfalls
- ✅ Community trust
- ⚠️ Platform-specific dependencies

### ADR-003: Immutable Public API

**Status**: Accepted

**Context**: Thread safety and predictability

**Decision**: All public API objects are immutable

**Consequences**:
- ✅ Thread-safe by default
- ✅ Predictable behavior
- ✅ Easier to reason about
- ⚠️ More object creation

---

**Navigation**: [← Getting Started](getting-started.md) | [API Reference →](api-reference.md)