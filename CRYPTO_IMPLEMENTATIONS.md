# Cryptographic Implementation

This document explains the cryptographic implementation approach used in the Stellar KMP SDK.

## Overview

The Stellar KMP SDK uses **platform-specific, production-ready cryptographic libraries** for Ed25519 operations:

| Platform | Library | Implementation |
|----------|---------|----------------|
| iOS/macOS | libsodium | C interop via Kotlin/Native |
| JVM | BouncyCastle | Java library |
| JavaScript | libsodium.js | WebAssembly + Web Crypto API |

All implementations use the same **Ed25519 (RFC 8032)** algorithm and produce compatible signatures.

## iOS/macOS: libsodium

### Why libsodium?

- ✅ **Production-proven**: Same library used by Stellar Core
- ✅ **Cross-platform**: Works on iOS, macOS, Linux, Android
- ✅ **Audited**: Professionally audited, constant-time operations
- ✅ **Ecosystem standard**: Used by Java SDK and other Stellar tools
- ✅ **Well-documented**: Extensive documentation and community support
- ✅ **Platform consistency**: Same crypto across all native platforms

### Distribution

For iOS apps, libsodium is distributed via **Swift Package Manager**:

```swift
// Add to Xcode project:
// File → Add Package Dependencies
// URL: https://github.com/jedisct1/swift-sodium
// Product: Clibsodium
```

This approach provides:
- ✅ One-click installation in Xcode
- ✅ Automatic architecture handling (device/simulator)
- ✅ Standard iOS workflow
- ✅ No manual configuration needed

### Framework Size

- **Release build**: ~7 MB (device), ~13 MB (simulator fat binary)
- **Debug build**: ~52 MB (device), ~64 MB (simulator) - includes debug symbols

The XCFramework distribution contains only the Stellar SDK code. libsodium (~200 KB) is added separately via SPM.

### Building

```bash
# Build release XCFramework (recommended for distribution)
./build-xcframework.sh

# Or manually:
./gradlew :stellar-sdk:linkReleaseFrameworkIosArm64
./gradlew :stellar-sdk:linkReleaseFrameworkIosSimulatorArm64
```

## JVM: BouncyCastle

The JVM platform uses **BouncyCastle** for Ed25519 operations:

- **Library**: org.bouncycastle:bcprov-jdk18on
- **Size**: ~2.8 MB (bundled in JAR)
- **Compatibility**: RFC 8032 compliant Ed25519

BouncyCastle is automatically included as a dependency and requires no additional setup.

## JavaScript: libsodium.js

The JavaScript platform uses a dual approach:

1. **Primary**: libsodium.js (WebAssembly)
2. **Fallback**: Web Crypto API (when available)

- **Size**: ~200 KB (gzipped)
- **Browser support**: All modern browsers
- **Node.js**: Fully supported

## Security Considerations

All implementations provide:

- ✅ **Constant-time operations**: Protection against timing attacks
- ✅ **Memory safety**: Proper handling of sensitive data
- ✅ **RFC 8032 compliance**: Standard Ed25519 implementation
- ✅ **Signature compatibility**: All platforms can verify each other's signatures

## Why Not CryptoKit?

CryptoKit (Apple's native crypto framework) was evaluated but **not used** because:

1. **Platform-specific**: Only available on iOS 13+ and macOS 10.15+
2. **Distribution complexity**: Requires complex Xcode configuration
3. **Ecosystem consistency**: Would create iOS-specific behavior
4. **No clear advantage**: libsodium provides same security guarantees

The decision prioritizes:
- Cross-platform consistency
- Simple distribution
- Ecosystem alignment
- Production reliability

## Testing

Each platform implementation is tested for:

- ✅ Keypair generation
- ✅ Signing operations
- ✅ Signature verification
- ✅ Cross-platform compatibility
- ✅ Edge cases and error handling

See `stellar-sdk/src/commonTest/` for shared tests and platform-specific test suites.

## Future Considerations

The architecture supports adding alternative crypto implementations if needed:

- The `Ed25519Crypto` interface abstracts platform details
- Platform-specific implementations via `expect/actual`
- Easy to add new platforms or crypto libraries

However, libsodium remains the recommended choice for:
- Production reliability
- Cross-platform consistency
- Ecosystem compatibility
