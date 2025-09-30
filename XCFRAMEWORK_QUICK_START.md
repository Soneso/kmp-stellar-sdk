# XCFramework - Quick Start (Simulator Only)

## Current Status

✅ **Working**: iOS Simulator ARM64 (M1/M2 Macs)
⚠️ **TODO**: iOS Device (real iPhones/iPads) - requires additional libsodium build

This guide shows how to create an XCFramework for **iOS Simulator testing** right now.

---

## Why Simulator-Only First?

Building libsodium for iOS requires complex cross-compilation with proper SDK paths. The official libsodium build scripts have issues with newer Xcode versions.

**Pragmatic approach:**
1. ✅ Get Simulator working (done!)
2. ✅ Create Simulator XCFramework (easy)
3. ⏭️ Add Device support later (requires fixing libsodium build)

**99% of development happens in Simulator anyway!**

---

## Create Simulator-Only XCFramework

### Step 1: We already have libsodium for Simulator

```bash
# Already built and stored:
ls stellar-sdk/native-libs/libsodium-ios/

# Output:
# include/      (headers)
# lib/libsodium.a  (static library for iOS Simulator ARM64)
```

### Step 2: Build the framework

```bash
./gradlew :stellar-sdk:linkReleaseFrameworkIosSimulatorArm64
```

Output: `stellar-sdk/build/bin/iosSimulatorArm64/releaseFramework/stellar_sdk.framework`

### Step 3: Create XCFramework (Simulator only)

```bash
xcodebuild -create-xcframework \
  -framework stellar-sdk/build/bin/iosSimulatorArm64/releaseFramework/stellar_sdk.framework \
  -output stellar_sdk.xcframework
```

### Step 4: Test it

```bash
# Copy to sample app
cp -r stellar_sdk.xcframework iosSample/

# Update Xcode project to use XCFramework instead of .framework
# (Update project.pbxproj to link stellar_sdk.xcframework)
```

---

## Using the XCFramework

### In Your iOS App:

1. **Add to Xcode:**
   - Drag `stellar_sdk.xcframework` into project
   - Xcode → Target → General → Frameworks → Add stellar_sdk.xcframework

2. **Import and use:**
```swift
import stellar_sdk

let keypair = KeyPair.Companion().random()
print(keypair.getAccountId())
```

3. **Test in Simulator:**
   - ⌘R to run
   - ✅ Works on M1/M2 Mac simulators!

### Limitations:

- ⚠️ **Only works in iOS Simulator** (M1/M2 Macs)
- ❌ Won't run on real iOS devices yet
- ❌ Won't work on Intel Mac simulators (need to build libsodium for x86_64)

---

## Adding Real Device Support (TODO)

To support real iPhones/iPads, you need to:

### 1. Build libsodium for iOS Device

The challenge: libsodium's build scripts fail with modern Xcode. Options:

**Option A: Use pre-built libsodium**
```bash
# Use CocoaPods to get pre-built library
pod 'libsodium', '~> 1.0'

# Extract libsodium.a from:
# Pods/libsodium/dist-build/lib/libsodium.a
```

**Option B: Manual build with explicit SDK**
```bash
SDK_PATH="/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk"

cd /tmp/libsodium
./configure --host=aarch64-apple-darwin \
  --prefix=/tmp/libsodium-ios-device \
  CFLAGS="-arch arm64 -mios-version-min=13.0 -isysroot $SDK_PATH" \
  LDFLAGS="-arch arm64 -mios-version-min=13.0 -isysroot $SDK_PATH"

make && make install
```

**Option C: Use xcframework from libsodium repo**
```bash
# Download pre-built XCFramework
# https://github.com/jedisct1/libsodium/releases
```

### 2. Update cinterop

**stellar-sdk/src/nativeInterop/cinterop/libsodium.def:**
```ini
# Add iOS Device configuration
compilerOpts.iosArm64 = -I$projectDir/native-libs/libsodium-ios-device/include
linkerOpts.iosArm64 = -Wl,-force_load,$projectDir/native-libs/libsodium-ios-device/lib/libsodium.a
```

### 3. Build device framework

```bash
./gradlew :stellar-sdk:linkReleaseFrameworkIosArm64
```

### 4. Create multi-architecture XCFramework

```bash
xcodebuild -create-xcframework \
  -framework build/iosArm64/releaseFramework/stellar_sdk.framework \
  -framework build/iosSimulatorArm64/releaseFramework/stellar_sdk.framework \
  -output stellar_sdk.xcframework
```

Now it works on both Simulator AND real devices! ✅

---

## Current Recommendation

**For Development:**
- ✅ Use current Simulator-only XCFramework
- ✅ Test everything in Simulator (M1/M2 Macs)
- ✅ 99% of development works this way

**For Production:**
- ⏭️ Add Device support when needed
- ⏭️ Or use libsodium CocoaPods dependency
- ⏭️ Or wait for pre-built libsodium XCFramework

---

## Alternative: Let Users Link libsodium

Instead of XCFramework, you can:

1. **Distribute just the Kotlin framework**
2. **Users add libsodium via CocoaPods:**

```ruby
# Podfile
pod 'libsodium', '~> 1.0'
pod 'StellarSDK', :path => 'path/to/stellar_sdk.framework'
```

This way:
- ✅ Users get libsodium automatically
- ✅ Works on all platforms
- ✅ You don't need to build libsodium

---

## Summary

**Current state:**
- ✅ iOS Simulator ARM64: **WORKS**
- ⚠️ iOS Device: Needs libsodium build
- ⚠️ Intel Simulator: Needs libsodium build

**Quick win:**
Create Simulator-only XCFramework now, add Device support later.

**See XCFRAMEWORK_DISTRIBUTION.md for full multi-architecture guide.**