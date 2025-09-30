# XCFramework Distribution Guide

This guide explains how to distribute the Stellar KMP SDK for iOS/macOS using XCFramework, which bundles libsodium and makes it easy for end users.

---

## 📦 What is XCFramework?

**XCFramework** is Apple's modern binary framework format that:
- ✅ Bundles multiple architectures in one package (iOS Simulator, iOS Device, macOS)
- ✅ Works with both Swift Package Manager and CocoaPods
- ✅ Supports static and dynamic frameworks
- ✅ Automatically selects correct architecture at build time
- ✅ Easier than managing separate `.framework` files

---

## 🎯 Current Problem

Right now, users need to:
1. Build the Kotlin framework
2. **Manually link libsodium.a** to their Xcode project
3. Manage different builds for Simulator vs Device

This is **not user-friendly** for distribution!

---

## ✅ Solution: XCFramework with Embedded libsodium

Create a **single XCFramework** that:
- Contains the Stellar KMP SDK framework
- Has libsodium **already embedded**
- Works on all iOS architectures
- Users just add it to their project - no manual setup!

---

## 🛠️ How to Build XCFramework

### Step 1: Build libsodium for all iOS architectures

```bash
cd /tmp/libsodium

# Build for iOS Device (ARM64)
./dist-build/ios.sh

# This creates:
# - libsodium-ios/lib/libsodium.a (for real devices)
# - libsodium-iossimulator-arm64/lib/libsodium.a (M1/M2 simulators)
# - libsodium-iossimulator-x86_64/lib/libsodium.a (Intel simulators)
```

### Step 2: Copy libsodium builds to project

```bash
# Copy all architectures to project
cp -r /tmp/libsodium/libsodium-ios \
      stellar-sdk/native-libs/

# Directory structure:
# stellar-sdk/native-libs/
#   libsodium-ios/
#     lib/libsodium.a (device arm64)
#   libsodium-iossimulator-arm64/
#     lib/libsodium.a (simulator arm64)
#   libsodium-iossimulator-x86_64/
#     lib/libsodium.a (simulator x86_64)
```

### Step 3: Update cinterop for each architecture

**stellar-sdk/src/nativeInterop/cinterop/libsodium.def:**
```ini
headers = sodium.h
headerFilter = sodium.h sodium/**
package = libsodium

# macOS (for development/testing)
compilerOpts.osx = -I/opt/homebrew/include
linkerOpts.osx = -L/opt/homebrew/lib -lsodium

# iOS Device (real iPhones/iPads)
compilerOpts.iosArm64 = -I$projectDir/native-libs/libsodium-ios/include
linkerOpts.iosArm64 = -Wl,-force_load,$projectDir/native-libs/libsodium-ios/lib/libsodium.a

# iOS Simulator ARM64 (M1/M2 Macs)
compilerOpts.iosSimulatorArm64 = -I$projectDir/native-libs/libsodium-iossimulator-arm64/include
linkerOpts.iosSimulatorArm64 = -Wl,-force_load,$projectDir/native-libs/libsodium-iossimulator-arm64/lib/libsodium.a

# iOS Simulator x86_64 (Intel Macs)
compilerOpts.iosX64 = -I$projectDir/native-libs/libsodium-iossimulator-x86_64/include
linkerOpts.iosX64 = -Wl,-force_load,$projectDir/native-libs/libsodium-iossimulator-x86_64/lib/libsodium.a
```

### Step 4: Build frameworks for all architectures

```bash
# Build for each target
./gradlew :stellar-sdk:linkReleaseFrameworkIosArm64        # Device
./gradlew :stellar-sdk:linkReleaseFrameworkIosSimulatorArm64  # M1/M2 Simulator
./gradlew :stellar-sdk:linkReleaseFrameworkIosX64          # Intel Simulator
```

**Output locations:**
```
stellar-sdk/build/bin/
  iosArm64/releaseFramework/stellar_sdk.framework
  iosSimulatorArm64/releaseFramework/stellar_sdk.framework
  iosX64/releaseFramework/stellar_sdk.framework
```

### Step 5: Create XCFramework

```bash
xcodebuild -create-xcframework \
  -framework stellar-sdk/build/bin/iosArm64/releaseFramework/stellar_sdk.framework \
  -framework stellar-sdk/build/bin/iosSimulatorArm64/releaseFramework/stellar_sdk.framework \
  -framework stellar-sdk/build/bin/iosX64/releaseFramework/stellar_sdk.framework \
  -output stellar_sdk.xcframework
```

This creates: **`stellar_sdk.xcframework`** - A single file that works everywhere!

### Step 6: Verify it worked

```bash
# Check the XCFramework structure
ls -la stellar_sdk.xcframework/

# Output should show:
# ios-arm64/stellar_sdk.framework         (for devices)
# ios-arm64_x86_64-simulator/stellar_sdk.framework  (for simulators)
```

---

## 📱 How Users Would Use It

### Option 1: Manual Integration

```swift
// 1. Drag stellar_sdk.xcframework into Xcode project
// 2. Xcode automatically links it
// 3. Import and use:

import stellar_sdk

let keypair = KeyPair.Companion().random()
print(keypair.getAccountId())
```

**No manual libsodium setup required!** ✅

### Option 2: Swift Package Manager (SPM)

Create `Package.swift`:
```swift
// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "StellarSDK",
    platforms: [.iOS(.v13), .macOS(.v11)],
    products: [
        .library(name: "StellarSDK", targets: ["StellarSDK"])
    ],
    targets: [
        .binaryTarget(
            name: "StellarSDK",
            url: "https://github.com/your-org/kmp-stellar-sdk/releases/download/v1.0.0/stellar_sdk.xcframework.zip",
            checksum: "sha256-checksum-here"
        )
    ]
)
```

Users add to their project:
```swift
dependencies: [
    .package(url: "https://github.com/your-org/kmp-stellar-sdk", from: "1.0.0")
]
```

### Option 3: CocoaPods

Create `StellarSDK.podspec`:
```ruby
Pod::Spec.new do |spec|
  spec.name         = 'StellarSDK'
  spec.version      = '1.0.0'
  spec.summary      = 'Stellar KMP SDK for iOS/macOS'
  spec.homepage     = 'https://github.com/your-org/kmp-stellar-sdk'
  spec.license      = { :type => 'Apache-2.0' }
  spec.author       = { 'Your Name' => 'you@example.com' }

  spec.platform     = :ios, '13.0'
  spec.source       = {
    :http => 'https://github.com/your-org/kmp-stellar-sdk/releases/download/v1.0.0/stellar_sdk.xcframework.zip'
  }

  spec.vendored_frameworks = 'stellar_sdk.xcframework'
end
```

Users add to `Podfile`:
```ruby
pod 'StellarSDK', '~> 1.0.0'
```

---

## 🎨 Automation Script

Create **`build-xcframework.sh`**:

```bash
#!/bin/bash
set -e

echo "🏗️  Building XCFramework for Stellar KMP SDK..."

# Clean previous builds
rm -rf stellar-sdk/build/bin
rm -rf stellar_sdk.xcframework

# Build for all iOS targets
echo "📦 Building iOS Device framework..."
./gradlew :stellar-sdk:linkReleaseFrameworkIosArm64

echo "📦 Building iOS Simulator (ARM64) framework..."
./gradlew :stellar-sdk:linkReleaseFrameworkIosSimulatorArm64

echo "📦 Building iOS Simulator (x86_64) framework..."
./gradlew :stellar-sdk:linkReleaseFrameworkIosX64

# Create XCFramework
echo "🔨 Creating XCFramework..."
xcodebuild -create-xcframework \
  -framework stellar-sdk/build/bin/iosArm64/releaseFramework/stellar_sdk.framework \
  -framework stellar-sdk/build/bin/iosSimulatorArm64/releaseFramework/stellar_sdk.framework \
  -framework stellar-sdk/build/bin/iosX64/releaseFramework/stellar_sdk.framework \
  -output stellar_sdk.xcframework

# Zip for distribution
echo "📦 Zipping XCFramework..."
zip -r stellar_sdk.xcframework.zip stellar_sdk.xcframework

# Calculate checksum for SPM
echo "🔐 Calculating checksum..."
CHECKSUM=$(swift package compute-checksum stellar_sdk.xcframework.zip)

echo ""
echo "✅ XCFramework built successfully!"
echo "📍 Location: ./stellar_sdk.xcframework"
echo "📦 Zip: ./stellar_sdk.xcframework.zip"
echo "🔐 SPM Checksum: $CHECKSUM"
echo ""
echo "📤 Upload to GitHub Release:"
echo "   gh release create v1.0.0 stellar_sdk.xcframework.zip"
```

---

## 📊 Comparison: Before vs After

### Before (Current State)
```swift
// Users must:
// 1. Build Kotlin framework
// 2. Manually download/build libsodium for iOS
// 3. Add both to Xcode project
// 4. Configure linker flags
// 5. Different setup for Simulator vs Device

// Xcode configuration needed:
OTHER_LDFLAGS = (
    "-framework", "stellar_sdk",
    "/path/to/libsodium.a"  // ⚠️ Manual!
);
```

### After (With XCFramework)
```swift
// Users just:
// 1. Add stellar_sdk.xcframework to project
// 2. Import and use!

import stellar_sdk

let keypair = KeyPair.Companion().random()
// ✅ It just works!
```

---

## 🚀 Benefits

### For SDK Developers:
- ✅ One build process
- ✅ Automated with CI/CD
- ✅ Version controlled
- ✅ Easy to distribute

### For SDK Users:
- ✅ **No manual libsodium setup**
- ✅ Works on Simulator & Device
- ✅ Standard iOS integration
- ✅ Swift Package Manager compatible
- ✅ CocoaPods compatible
- ✅ Just drag & drop

---

## 📋 Checklist for Production

- [ ] Build libsodium for all iOS architectures
- [ ] Update cinterop with `-force_load` flags
- [ ] Test framework on device & simulator
- [ ] Create XCFramework
- [ ] Verify symbols with `nm -g`
- [ ] Test in sample iOS app
- [ ] Create GitHub release
- [ ] Publish to CocoaPods (optional)
- [ ] Create SPM Package.swift (optional)
- [ ] Write integration docs for users

---

## 🔍 Verify XCFramework Contains libsodium

```bash
# Extract and check symbols
nm -g stellar_sdk.xcframework/ios-arm64/stellar_sdk.framework/stellar_sdk | grep sodium

# Should show (not U = undefined):
# 0000000000123456 T _sodium_init
# 0000000000789abc T _crypto_sign_detached
# ...
```

If you see `U _sodium_init`, libsodium is NOT embedded - need to fix `-force_load` flags!

---

## 💡 Summary

**XCFramework = Easy Distribution**

Instead of users dealing with libsodium, you:
1. Build libsodium once for all architectures
2. Embed it in framework with `-force_load`
3. Package everything in XCFramework
4. Distribute as single file

Users get a **plug-and-play** framework! 🎉