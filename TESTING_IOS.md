# Testing iOS Implementation

The Stellar KMP SDK native implementation uses libsodium for cryptographic operations. Here are your options for testing on iOS:

## ✅ Option 1: Test macOS Native (Same Implementation)

The iOS and macOS implementations use the **exact same code** (`nativeMain`), so testing on macOS validates the iOS implementation.

```bash
# Run all tests on macOS ARM64
./gradlew :stellar-sdk:macosArm64Test

# Run all tests on macOS X64 (Intel)
./gradlew :stellar-sdk:macosX64Test
```

**Status:** ✅ All 24 tests passing on macOS

---

## 🔧 Option 2: Build libsodium for iOS Simulator

To run tests in iOS Simulator, you need libsodium compiled for iOS simulator architectures.

### Using CocoaPods/SPM

1. Add libsodium to your iOS project:
   ```ruby
   # Podfile
   pod 'libsodium', '~> 1.0.20'
   ```

2. Update the `.def` file to point to the CocoaPod framework

### Manual Build

```bash
# Clone libsodium
git clone https://github.com/jedisct1/libsodium.git
cd libsodium
git checkout 1.0.20

# Build for iOS Simulator (ARM64)
./dist-build/ios.sh

# This creates:
# libsodium-ios/lib/libsodium.a (for all iOS architectures)
```

Then update `libsodium.def`:
```
compilerOpts.iosSimulatorArm64 = -I/path/to/libsodium-ios/include
linkerOpts.iosSimulatorArm64 = -L/path/to/libsodium-ios/lib -lsodium
```

---

## 📱 Option 3: Create iOS Sample App (Recommended for Real Testing)

Create a simple iOS app that uses the SDK:

### 1. Create iOS App Project

```bash
cd kmp-stellar-sdk
mkdir ios-sample
cd ios-sample
# Create Xcode project or use SwiftUI
```

### 2. Add Framework Dependency

In your iOS app's `build.gradle.kts` or via CocoaPods:

```kotlin
// In ios app module
dependencies {
    implementation(project(":stellar-sdk"))
}
```

### 3. Example Usage in Swift

```swift
import StellarSDK

func testStellarSDK() {
    // Generate new keypair
    let keypair = KeyPair.Companion().random()
    print("Account ID: \(keypair.getAccountId())")

    // Sign data
    let data = "Hello Stellar".data(using: .utf8)!
    let signature = try! keypair.sign(data: KotlinByteArray(data: data))

    // Verify signature
    let isValid = keypair.verify(
        data: KotlinByteArray(data: data),
        signature: signature
    )
    print("Signature valid: \(isValid)")
}
```

---

## 🚀 Option 4: Framework Generation (For Distribution)

Generate an iOS framework for use in any iOS project:

```bash
# Generate XCFramework
./gradlew :stellar-sdk:assembleXCFramework

# Output will be in:
# stellar-sdk/build/XCFrameworks/release/stellar-sdk.xcframework
```

Then import into any iOS project via Xcode.

---

## Current Test Status

| Platform | Status | Notes |
|----------|--------|-------|
| JVM | ✅ All 24 tests passing | BouncyCastle implementation |
| JS (Browser) | ✅ Compiles | Web Crypto API + libsodium.js |
| JS (Node.js) | ✅ Compiles | libsodium-wrappers |
| macOS ARM64 | ✅ All 24 tests passing | Native libsodium |
| macOS X64 | ✅ All 24 tests passing | Native libsodium |
| iOS ARM64 | ✅ Compiles | Native libsodium (needs device/simulator setup for tests) |
| iOS Simulator | ✅ Compiles | Needs iOS-specific libsodium build for tests |

---

## Quick Test Commands

```bash
# Test all platforms that have libsodium available
./gradlew test                        # JVM tests
./gradlew :stellar-sdk:macosArm64Test # macOS native tests (same code as iOS)

# Build all platforms (without tests)
./gradlew assemble                    # ✅ All platforms compile successfully

# Test specific platform
./gradlew :stellar-sdk:jvmTest
./gradlew :stellar-sdk:jsTest         # Browser + Node.js
./gradlew :stellar-sdk:macosArm64Test
```

---

## Why macOS Tests Validate iOS

Both iOS and macOS use the **exact same Kotlin code** from `nativeMain`:
- Same `Ed25519.native.kt` implementation
- Same libsodium C library
- Same memory management
- Same cryptographic operations

The only difference is the target architecture and linking, but the cryptographic logic is identical.

**Recommendation:** Use macOS tests for development, then create an iOS sample app for integration testing before release.