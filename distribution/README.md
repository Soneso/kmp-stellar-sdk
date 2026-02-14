# Stellar KMP SDK - iOS Distribution

This package contains the Stellar SDK for iOS as an XCFramework.

## Contents

- `stellar_sdk.xcframework` - The Stellar SDK framework

## Dependencies

The Stellar SDK requires **libsodium** for cryptographic operations. You need to add libsodium to your project.

## Installation

### Step 1: Add Stellar SDK

Drag `stellar_sdk.xcframework` into your Xcode project. In your target's "General" tab, under "Frameworks, Libraries, and Embedded Content", ensure it's set to **"Embed & Sign"**.

### Step 2: Add libsodium

You have three options for adding libsodium:

#### Option A: Swift Package Manager (Recommended)

1. In Xcode, go to File > Add Package Dependencies
2. Enter the URL: `https://github.com/jedisct1/swift-libsodium`
3. Select "Up to Next Major Version" with version 1.2.0
4. Click "Add Package"

#### Option B: CocoaPods

Add to your `Podfile`:
```ruby
pod 'SwiftLibsodium', '~> 1.0'
```

Then run:
```bash
pod install
```

#### Option C: Manual Static Library

1. Download pre-built libsodium from: https://github.com/jedisct1/libsodium/releases
2. Extract and navigate to the iOS build directory
3. Add the static library to your project
4. Add to "Framework Search Paths" in Build Settings

### Step 3: Link Frameworks

Ensure both frameworks are properly linked in your target's build phases.

## Usage

```swift
import stellar_sdk

// Generate a random keypair
let keypair = KeyPair.Companion().random()
let accountId = keypair.getAccountId()
print("Account ID: \(accountId)")

// Create keypair from secret seed
let seed = "SXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
let keypairFromSeed = KeyPair.Companion().fromSecretSeed(seed: seed)

// Sign data
let data = "Hello, Stellar!".data(using: .utf8)!
let signature = keypair.sign(data: Array(data))
print("Signature: \(signature)")

// Verify signature
let isValid = keypair.verify(data: Array(data), signature: signature)
print("Signature valid: \(isValid)")

// Get crypto library info
let cryptoLib = KeyPair.Companion().getCryptoLibraryName()
print("Using: \(cryptoLib)") // Prints: "libsodium"
```

## Cryptographic Implementation

This SDK uses **libsodium** for iOS/macOS cryptographic operations:

- ✅ **Production-proven**: Same library used by Stellar Core
- ✅ **Cross-platform**: Consistent Ed25519 implementation across all platforms
- ✅ **Audited**: Security-reviewed, constant-time operations
- ✅ **Side-channel safe**: Protection against timing attacks
- ✅ **Well-maintained**: Active development and security updates

### Platform-Specific Implementations

| Platform | Library | Algorithm |
|----------|---------|-----------|
| iOS/macOS | libsodium | Ed25519 (RFC 8032) |
| JVM | BouncyCastle | Ed25519 (RFC 8032) |
| JavaScript | libsodium.js | Ed25519 (RFC 8032) |

All platforms produce compatible signatures and can verify each other's work.

## Requirements

- **iOS 13.0+**
- **Xcode 14.0+**
- **Swift 5.7+**

## Platform Support

| Platform | Status | Size Impact |
|----------|--------|-------------|
| iOS | ✅ | ~7 MB (device) |
| macOS | ✅ | ~7 MB |
| JVM | ✅ | ~3 MB (includes BouncyCastle) |
| JavaScript | ✅ | ~200 KB (gzipped) |

## Troubleshooting

### Build Errors

**"No such module 'stellar_sdk'"**
- Ensure `stellar_sdk.xcframework` is added to your project
- Check that it's set to "Embed & Sign" in target settings
- Clean build folder (Cmd+Shift+K) and rebuild

**"Undefined symbols for libsodium"**
- Ensure libsodium is properly installed via one of the methods above
- Check that libsodium is linked in Build Phases > Link Binary With Libraries

**"Framework not found"**
- Add framework search paths in Build Settings
- Use `$(PROJECT_DIR)` or absolute paths as needed

### Runtime Errors

**"dyld: Library not loaded"**
- Ensure framework is set to "Embed & Sign", not just "Do Not Embed"
- Check that the framework is present in your app bundle

**"Failed to generate keypair"**
- Ensure libsodium is properly installed and linked
- Check that you're not running in a restricted sandbox environment

## Architecture

The Stellar SDK is built with Kotlin Multiplatform, sharing 100% of the business logic across all platforms while using platform-native crypto implementations:

```
┌─────────────────────────────────────────┐
│   Common Kotlin Code (Business Logic)   │
│   • KeyPair management                   │
│   • StrKey encoding/decoding             │
│   • Transaction building (future)        │
└─────────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼────┐   ┌───▼────┐   ┌───▼────┐
│  iOS   │   │  JVM   │   │   JS   │
│libsodium│  │ Bouncy │  │libsodium│
│        │   │ Castle │   │  .js   │
└────────┘   └────────┘   └────────┘
```

## Building from Source

If you need to build the framework yourself:

```bash
# Clone the repository
git clone https://github.com/stellar/kmp-stellar-sdk
cd kmp-stellar-sdk

# Build the iOS framework
./gradlew :stellar-sdk:linkDebugFrameworkIosArm64
./gradlew :stellar-sdk:linkDebugFrameworkIosSimulatorArm64

# Create XCFramework bundle
./build-xcframework.sh
```

## License

Apache 2.0

## Support

- **GitHub Issues**: https://github.com/stellar/kmp-stellar-sdk/issues
- **Stellar Development**: https://developers.stellar.org
- **Stellar Discord**: https://discord.gg/stellar

## Security

For security issues, please email security@stellar.org instead of posting a public issue.

## Version

- **Stellar SDK**: 0.1.0-alpha
- **libsodium**: 1.0.20
- **Kotlin**: 2.1.0
- **Minimum iOS**: 13.0

## Changelog

### 0.1.0-alpha (2025-09-30)

- Initial release
- KeyPair generation and management
- Ed25519 signing and verification
- StrKey encoding/decoding support
- iOS and macOS native support with libsodium
- JVM support with BouncyCastle
- JavaScript support with libsodium.js
