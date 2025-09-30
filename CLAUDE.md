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
- **Primary Library**: Web Crypto API (native browser implementation)
  - Available: Chrome 113+, Firefox 120+, Safari 17+, Edge 113+
  - Hardware-accelerated, fastest performance
- **Fallback Library**: libsodium-wrappers (0.7.13)
  - Same audited C library compiled to WebAssembly
  - Universal compatibility (all browsers, Node.js)
  - Automatic detection and graceful fallback
- **Random**: `crypto.getRandomValues()` (CSPRNG)
- **Installation**:
  - Browser: Include via CDN or npm
  - Node.js: `npm install libsodium-wrappers`

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

### Code Organization

- `commonMain`: Shared Stellar protocol logic, StrKey, KeyPair API
- `jvmMain`: JVM-specific crypto (BouncyCastle), Base32 (Apache Commons)
- `jsMain`: JS-specific crypto (Web Crypto API + libsodium-wrappers fallback)
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
- **Run JS tests (Browser)**: `./gradlew jsBrowserTest` (requires Chrome)
- **Run JS tests (Node.js)**: `./gradlew jsNodeTest`
- **Run macOS tests**: `./gradlew macosArm64Test` or `./gradlew macosX64Test`
- **Run iOS Simulator tests**: `./gradlew iosSimulatorArm64Test` or `./gradlew iosX64Test`

### Sample Apps
- **Android**: `./gradlew :stellarSample:androidApp:installDebug`
- **iOS**: `./gradlew :stellarSample:shared:linkDebugFrameworkIosSimulatorArm64` then open in Xcode
- **Web (dev)**: `./gradlew :stellarSample:webApp:jsBrowserDevelopmentRun`
- **Web (production)**: `./gradlew :stellarSample:webApp:jsBrowserProductionWebpack`

### Native Development
- **Build iOS framework**: `./gradlew :stellar-sdk:linkDebugFrameworkIosSimulatorArm64`
- **Build libsodium XCFramework**: `./build-libsodium-xcframework.sh`

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
- **libsodium-wrappers** (0.7.13 via npm): Ed25519 cryptography fallback
  - Browser: CDN or bundled
  - Node.js: `npm install libsodium-wrappers`

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

### StrKey (`com.stellar.sdk.StrKey`)
- ✅ Encode/decode Ed25519 public keys (G...)
- ✅ Encode/decode Ed25519 secret seeds (S...)
- ✅ CRC16-XModem checksum validation
- ✅ Version byte validation
- ✅ Base32 encoding (platform-specific)

## Testing

All cryptographic operations have comprehensive test coverage:
- Round-trip encoding/decoding
- Known test vectors from Java Stellar SDK
- Sign/verify operations
- Error handling and edge cases
- Input validation

Run tests:
- JVM: `./gradlew jvmTest`
- JS Browser: `./gradlew jsBrowserTest` (requires Chrome)
- JS Node: `./gradlew jsNodeTest` (requires Node.js + libsodium-wrappers)
- Native: `./gradlew macosArm64Test` or `./gradlew iosSimulatorArm64Test`

Sample app tests:
- Shared tests: `./gradlew :stellarSample:shared:allTests`
- Android tests: `./gradlew :stellarSample:shared:testDebugUnitTest`
- iOS tests: `./gradlew :stellarSample:shared:iosSimulatorArm64Test`
- JS tests: `./gradlew :stellarSample:shared:jsTest`

## Reference Implementation

When implementing features, use the Java Stellar SDK as a reference:
- Located at: `/Users/chris/projects/Stellar/java-stellar-sdk`
- Provides production-tested implementations of Stellar protocols
- Use as a guide for API design and feature completeness