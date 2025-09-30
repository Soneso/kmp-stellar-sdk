# Stellar KMP SDK - iOS Sample App

This is a comprehensive iOS integration test app for the Stellar KMP SDK. It demonstrates all major features of the SDK on iOS and runs an extensive test suite.

## Features

### 🎯 Interactive UI
- **Generate Keypairs**: Create new Ed25519 keypairs with one tap
- **Copy Account IDs**: Easily copy generated account IDs to clipboard
- **Run Tests**: Execute comprehensive test suite from the app
- **View Results**: See detailed test results with timing information

### 🧪 Comprehensive Tests

The sample app includes 10 comprehensive tests:

1. **Random KeyPair Generation** - Verifies unique keypair generation
2. **KeyPair from Secret Seed** - Tests keypair derivation from known seed
3. **KeyPair from Account ID** - Tests public-only keypair creation
4. **Sign and Verify** - Tests digital signature creation and verification
5. **Cross-KeyPair Verification** - Tests verification with public-only keypairs
6. **Invalid Secret Seed** - Validates error handling for invalid seeds
7. **Invalid Account ID** - Validates error handling for invalid account IDs
8. **StrKey Encoding/Decoding** - Tests Base32 encoding/decoding
9. **StrKey Validation** - Tests account ID validation
10. **Memory Safety** - Stress tests with 100 keypairs

## Prerequisites

- Xcode 15.0 or later
- macOS with Apple Silicon (ARM64) or Intel

## Quick Start

### Option 1: Using Distribution XCFramework (Recommended)

This is the simplest way to get started and mirrors how users will integrate the SDK.

1. **Open in Xcode:**
   ```bash
   open iosSample/iosSample.xcodeproj
   ```

2. **Add libsodium dependency:**
   - In Xcode: File → Add Package Dependencies
   - URL: `https://github.com/jedisct1/swift-sodium`
   - Version: 0.9.1 or later

3. **Build and Run:**
   - Select an iOS Simulator (iPhone 16 Pro recommended)
   - Press ⌘R to build and run

The XCFramework is already configured in the project at `../distribution/stellar_sdk.xcframework`.

### Option 2: Development Build

If you're actively developing the SDK and need to rebuild frequently:

1. **Build the framework:**
   ```bash
   # From project root
   ./build-xcframework.sh
   ```

2. **Follow steps 1-3 from Option 1**

## Project Structure

```
iosSample/
├── iosSample/
│   ├── iosSampleApp.swift        # App entry point
│   ├── ContentView.swift         # Main UI
│   ├── StellarSDKTests.swift     # Comprehensive test suite
│   └── Assets.xcassets/          # App assets
├── iosSample.xcodeproj/          # Xcode project
└── README.md                      # This file
```

## Usage

### Generate a New Keypair

1. Tap "Generate New Keypair"
2. The account ID will be displayed
3. Tap "Copy" to copy the account ID to clipboard

### Run Tests

1. Tap "Run Comprehensive Tests"
2. Wait for tests to complete (usually < 1 second)
3. View individual test results with pass/fail status
4. See detailed messages and timing for each test

## Framework Integration Examples

The app demonstrates how to:

### Import the Framework

```swift
import stellar_sdk
```

### Generate Keypairs

```swift
// Generate random keypair
let keypair = KeyPair.Companion().random()
let accountId = keypair.getAccountId()

// From secret seed
let keypair = KeyPair.Companion().fromSecretSeed(
    seed: "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
)

// From account ID (public-only)
let keypair = KeyPair.Companion().fromAccountId(
    accountId: "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
)
```

### Sign and Verify Messages

```swift
// Sign data
let message = "Hello Stellar".data(using: .utf8)!
let signature = keypair.sign(data: Array(message))

// Verify
let isValid = keypair.verify(data: Array(message), signature: signature)
```

### Get Crypto Library Info

```swift
let cryptoLib = KeyPair.Companion().getCryptoLibraryName()
print("Using: \(cryptoLib)")  // Prints: "libsodium"
```

### StrKey Encoding/Decoding

```swift
// Decode account ID
let publicKeyBytes = try StrKey.Companion().decodeEd25519PublicKey(
    data: "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
)

// Encode back
let accountId = StrKey.Companion().encodeEd25519PublicKey(data: publicKeyBytes)

// Validate
let isValid = StrKey.Companion().isValidEd25519PublicKey(accountId: accountId)
```

## Architecture

### Cryptographic Implementation

The iOS implementation uses **libsodium** for cryptographic operations:

- **Algorithm**: Ed25519 (RFC 8032)
- **Library**: libsodium (via Swift Package Manager)
- **Framework Size**: ~7 MB (release), ~20 MB (with simulator)
- **Distribution**: XCFramework + SPM dependency

### Why libsodium?

- ✅ **Production-proven**: Same library used by Stellar Core
- ✅ **Cross-platform**: Consistent Ed25519 across iOS, JVM, JS
- ✅ **Audited**: Security-reviewed, constant-time operations
- ✅ **Performance**: Native C performance

### Framework Configuration

The Stellar SDK is built as an **XCFramework**:

- Supports both iOS device (arm64) and simulator (arm64 + x86_64)
- Static framework with libsodium symbols resolved at runtime
- libsodium provided by app via Swift Package Manager

## Troubleshooting

### Framework Not Found

If you see "No such module 'stellar_sdk'":

1. Check that `stellar_sdk.xcframework` exists at `../distribution/`
2. Clean build folder: Product → Clean Build Folder (⌘⇧K)
3. Rebuild the framework: `./build-xcframework.sh`

### libsodium Errors

If you see undefined symbols for libsodium:

1. Make sure you added the swift-sodium package (see Quick Start)
2. Clean and rebuild

### Wrong Architecture

If you get architecture errors:

- The XCFramework supports both arm64 and x86_64 simulators
- Make sure you're using a recent simulator (iOS 13.0+)

### Build Script Warnings

You may see warnings about "Run script build phase 'Embed Frameworks'". These are harmless and can be ignored.

## Performance

Expected performance on iPhone 16 Pro Simulator:

- **Keypair Generation**: ~1-2ms
- **Sign Operation**: ~1-2ms
- **Verify Operation**: ~1-2ms
- **Full Test Suite**: < 1 second

## Security Notes

- Private keys are stored securely in memory
- Keys are defensively copied to prevent external modification
- libsodium uses constant-time operations to prevent timing attacks
- Memory is properly zeroed after use

## Distribution

This sample app demonstrates the **recommended distribution approach** for iOS:

1. **XCFramework**: Distribute `stellar_sdk.xcframework` (20 MB)
2. **SPM Dependency**: Users add libsodium via Swift Package Manager
3. **Simple Integration**: Drag framework + add package = done

See `../distribution/README.md` for complete integration guide.

## Related Documentation

- [Distribution Package](../distribution/README.md)
- [Integration Guide](../distribution/INTEGRATION_GUIDE.md)
- [Crypto Implementation](../CRYPTO_IMPLEMENTATIONS.md)

## License

Same as parent project (Stellar KMP SDK) - Apache 2.0
