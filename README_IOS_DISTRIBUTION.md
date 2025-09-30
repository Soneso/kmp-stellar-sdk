# iOS Distribution - Final Status & Options

## ✅ What We Have

- ✅ **iOS Simulator support** - Fully working
- ✅ **XCFramework created** - `stellar_sdk.xcframework`
- ✅ **Sample app running** - Tested and verified
- ✅ **libsodium for Simulator** - Built and stored in project

## ⚠️ Important Discovery

**Kotlin/Native static frameworks do NOT embed external static libraries.**

Even with `-Wl,-all_load` and `-force_load` flags, libsodium symbols remain **undefined** in the framework. This is a Kotlin/Native limitation.

```bash
# Check symbols:
nm -g stellar_sdk.xcframework/.../stellar_sdk | grep sodium_init
# Output:  U _sodium_init     # U = Undefined!
```

---

## 📦 Distribution Options

### Option 1: XCFramework + Separate libsodium (Current)

**How it works:**
- Distribute `stellar_sdk.xcframework`
- **Users must also link libsodium** to their Xcode project

**User setup:**
```swift
// In Xcode project:
// 1. Add stellar_sdk.xcframework
// 2. Add OTHER_LDFLAGS:
OTHER_LDFLAGS = (
    "$(inherited)",
    "-framework", "stellar_sdk",
    "$(SRCROOT)/path/to/libsodium.a"  // Required!
);
```

**Pros:**
- ✅ Works now
- ✅ Standard XCFramework format

**Cons:**
- ❌ Users must manually handle libsodium
- ❌ Not plug-and-play

---

### Option 2: CocoaPods with libsodium Dependency (RECOMMENDED)

**How it works:**
- Create a CocoaPod that depends on libsodium
- CocoaPods automatically handles libsodium

**StellarSDK.podspec:**
```ruby
Pod::Spec.new do |spec|
  spec.name         = 'StellarSDK'
  spec.version      = '1.0.0'
  spec.summary      = 'Stellar KMP SDK for iOS'

  spec.platform     = :ios, '13.0'
  spec.source       = {
    :http => 'https://github.com/yourorg/kmp-stellar-sdk/releases/download/v1.0.0/stellar_sdk.xcframework.zip'
  }

  spec.vendored_frameworks = 'stellar_sdk.xcframework'

  # Automatically link libsodium
  spec.dependency 'libsodium', '~> 1.0'
end
```

**User setup:**
```ruby
# Podfile
pod 'StellarSDK', '~> 1.0.0'
```

That's it! CocoaPods handles libsodium automatically.

**Pros:**
- ✅ **Plug-and-play** for users
- ✅ libsodium handled automatically
- ✅ Standard iOS distribution
- ✅ Version management

**Cons:**
- ⏭️ Requires CocoaPods setup
- ⏭️ Need to publish to CocoaPods trunk

---

### Option 3: Swift Package Manager with Binary Target

**How it works:**
- Distribute XCFramework via SPM
- Users also add libsodium via SPM

**Package.swift:**
```swift
// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "StellarSDK",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "StellarSDK", targets: ["StellarSDK", "libsodium"])
    ],
    dependencies: [
        // User must add libsodium dependency
    ],
    targets: [
        .binaryTarget(
            name: "StellarSDK",
            url: "https://github.com/yourorg/kmp-stellar-sdk/releases/download/v1.0.0/stellar_sdk.xcframework.zip",
            checksum: "sha256..."
        )
    ]
)
```

**Pros:**
- ✅ Modern distribution method
- ✅ Git-based

**Cons:**
- ❌ User still needs to handle libsodium dependency
- ⏭️ More complex setup

---

### Option 4: Build libsodium INTO Framework (NOT POSSIBLE)

We tried this extensively. **It doesn't work** with Kotlin/Native static frameworks.

The linker options like `-force_load` only apply at the **app link stage**, not framework link stage.

---

## 🎯 Recommended Approach: CocoaPods

**Why CocoaPods is best:**

1. **Automatic dependency management** -  Users don't manually handle libsodium
2. **Standard iOS workflow** - Familiar to iOS developers
3. **Version control** - Easy to update
4. **Proven solution** - Many Kotlin/Native libraries use this approach

### Implementation Steps:

#### 1. Create `.podspec` file

```ruby
Pod::Spec.new do |spec|
  spec.name         = 'StellarKMPSDK'
  spec.version      = '1.0.0'
  spec.summary      = 'Kotlin Multiplatform Stellar SDK for iOS'
  spec.description  = 'A Kotlin Multiplatform SDK for Stellar blockchain operations on iOS'
  spec.homepage     = 'https://github.com/yourorg/kmp-stellar-sdk'
  spec.license      = { :type => 'Apache-2.0', :file => 'LICENSE' }
  spec.author       = { 'Your Name' => 'email@example.com' }
  spec.source       = {
    :http => 'https://github.com/yourorg/kmp-stellar-sdk/releases/download/v1.0.0/stellar_sdk.xcframework.zip'
  }

  spec.platform     = :ios, '13.0'
  spec.vendored_frameworks = 'stellar_sdk.xcframework'

  # Automatic libsodium dependency
  spec.dependency 'libsodium', '~> 1.0'
end
```

#### 2. Publish to CocoaPods

```bash
# Validate
pod spec lint StellarKMPSDK.podspec

# Register (first time only)
pod trunk register email@example.com 'Your Name'

# Push to CocoaPods
pod trunk push StellarKMPSDK.podspec
```

#### 3. Users install it

```ruby
# Podfile
target 'MyApp' do
  pod 'StellarKMPSDK', '~> 1.0'
end
```

```bash
pod install
```

Done! Everything works automatically.

---

## 📊 Comparison

| Method | User Setup | libsodium Handling | Difficulty |
|--------|------------|-------------------|-----------|
| **CocoaPods** | `pod install` | ✅ Automatic | ⭐ Easy |
| XCFramework only | Manual linker flags | ❌ Manual | ⭐⭐⭐ Hard |
| SPM | Package.swift + manual libsodium | ⚠️ Semi-automatic | ⭐⭐ Medium |

---

## 🚀 Quick Start for Users (With CocoaPods)

### Setup:

```ruby
# Podfile
platform :ios, '13.0'

target 'MyApp' do
  use_frameworks!
  pod 'StellarKMPSDK', '~> 1.0'
end
```

```bash
pod install
open MyApp.xcworkspace  # Not .xcodeproj!
```

### Usage:

```swift
import stellar_sdk

// Generate keypair
let keypair = KeyPair.Companion().random()
print("Account ID: \(keypair.getAccountId())")

// Sign data
let data = "Hello Stellar".data(using: .utf8)!
let signature = try keypair.sign(data: data)

// Verify signature
let isValid = keypair.verify(data: data, signature: signature)
print("Valid: \(isValid)")
```

**That's it!** No manual libsodium setup needed.

---

## 📁 Files in This Repo

- ✅ `stellar_sdk.xcframework` - iOS Simulator framework
- ✅ `stellar-sdk/native-libs/libsodium-ios/` - libsodium for Simulator
- ✅ `iosSample/` - Working sample app
- ✅ `build-ios-sample.sh` - Build script
- ✅ `CRYPTO_IMPLEMENTATIONS.md` - Why libsodium (not CryptoKit)
- ✅ `XCFRAMEWORK_DISTRIBUTION.md` - Full XCFramework guide
- ✅ `XCFRAMEWORK_QUICK_START.md` - Simulator-only guide
- ✅ This file - Distribution options

---

## 💡 Next Steps

### For Developers:
1. ⏭️ Create `.podspec` file
2. ⏭️ Publish to CocoaPods
3. ⏭️ Add Device support (build libsodium for iosArm64)
4. ⏭️ Create multi-architecture XCFramework

### For Users (Now):
- ✅ Use the sample app as reference
- ✅ Manually link libsodium for now
- ⏭️ Wait for CocoaPods release

---

## ✅ Summary

**Current Status:**
- ✅ iOS Simulator: Working perfectly
- ✅ XCFramework: Created
- ⚠️ Distribution: Requires CocoaPods for best UX

**Best Approach:**
- 🎯 Publish to CocoaPods with libsodium dependency
- 🎯 Users get plug-and-play experience
- 🎯 Standard iOS distribution method

**Alternative:**
- ⚠️ Distribute XCFramework + require users to link libsodium manually
- ⚠️ More work for users, but doable