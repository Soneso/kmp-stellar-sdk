#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build/xcframework"
STELLAR_SDK_DIR="$SCRIPT_DIR/stellar-sdk"
DISTRIBUTION_DIR="$SCRIPT_DIR/distribution"

echo "🏗️  Building Stellar SDK XCFramework with libsodium..."

# Clean previous builds
rm -rf "$BUILD_DIR"
rm -rf "$DISTRIBUTION_DIR"
mkdir -p "$BUILD_DIR"
mkdir -p "$DISTRIBUTION_DIR"

# Step 1: Build Stellar SDK frameworks for all architectures
echo "📦 Step 1: Building Stellar SDK frameworks (Release)..."
cd "$SCRIPT_DIR"

./gradlew clean
./gradlew :stellar-sdk:linkReleaseFrameworkIosArm64
./gradlew :stellar-sdk:linkReleaseFrameworkIosSimulatorArm64
./gradlew :stellar-sdk:linkReleaseFrameworkIosX64

# Copy frameworks to build directory
echo "📋 Copying frameworks..."
mkdir -p "$BUILD_DIR/iosArm64"
mkdir -p "$BUILD_DIR/iosSimulatorArm64"
mkdir -p "$BUILD_DIR/iosX64"

cp -R stellar-sdk/build/bin/iosArm64/releaseFramework/stellar_sdk.framework "$BUILD_DIR/iosArm64/"
cp -R stellar-sdk/build/bin/iosSimulatorArm64/releaseFramework/stellar_sdk.framework "$BUILD_DIR/iosSimulatorArm64/"
cp -R stellar-sdk/build/bin/iosX64/releaseFramework/stellar_sdk.framework "$BUILD_DIR/iosX64/"

# Step 2: Create fat simulator framework
echo "🔨 Step 2: Creating fat simulator framework..."
mkdir -p "$BUILD_DIR/simulator"
cp -R "$BUILD_DIR/iosSimulatorArm64/stellar_sdk.framework" "$BUILD_DIR/simulator/"

lipo -create \
    "$BUILD_DIR/iosSimulatorArm64/stellar_sdk.framework/stellar_sdk" \
    "$BUILD_DIR/iosX64/stellar_sdk.framework/stellar_sdk" \
    -output "$BUILD_DIR/simulator/stellar_sdk.framework/stellar_sdk"

# Step 3: Create XCFramework for Stellar SDK
echo "📦 Step 3: Creating Stellar SDK XCFramework..."
xcodebuild -create-xcframework \
    -framework "$BUILD_DIR/iosArm64/stellar_sdk.framework" \
    -framework "$BUILD_DIR/simulator/stellar_sdk.framework" \
    -output "$DISTRIBUTION_DIR/stellar_sdk.xcframework"

# Step 4: Check if libsodium is already built
echo "📦 Step 4: Checking libsodium..."

LIBSODIUM_DIR="/opt/homebrew/Cellar/libsodium/1.0.20"
if [ -d "$LIBSODIUM_DIR" ]; then
    echo "✅ Found libsodium at $LIBSODIUM_DIR"

    # Create static libraries for iOS
    mkdir -p "$BUILD_DIR/libsodium-ios-device"
    mkdir -p "$BUILD_DIR/libsodium-ios-simulator"

    # Copy headers
    cp -R "$LIBSODIUM_DIR/include" "$BUILD_DIR/libsodium-ios-device/"
    cp -R "$LIBSODIUM_DIR/include" "$BUILD_DIR/libsodium-ios-simulator/"

    # Copy library (this is macOS arm64, we'll need to build for iOS)
    echo "⚠️  Homebrew libsodium is for macOS, need to build for iOS..."

    # Download and build libsodium for iOS
    cd "$BUILD_DIR"

    if [ ! -d "libsodium" ]; then
        echo "📥 Downloading libsodium..."
        curl -L https://github.com/jedisct1/libsodium/releases/download/1.0.20-RELEASE/libsodium-1.0.20.tar.gz -o libsodium.tar.gz
        tar xzf libsodium.tar.gz
        mv libsodium-1.0.20 libsodium
    fi

    cd libsodium

    # Build XCFramework for all Apple platforms (iOS, iOS Simulator, macOS)
    echo "🔨 Building libsodium XCFramework for Apple platforms..."
    ./dist-build/apple-xcframework.sh

    # The apple-xcframework.sh script creates libsodium-apple/libsodium.xcframework
    if [ -d "libsodium-apple/libsodium.xcframework" ]; then
        cp -R libsodium-apple/libsodium.xcframework "$DISTRIBUTION_DIR/"
        echo "✅ Created libsodium XCFramework"
    else
        echo "❌ Failed to create libsodium XCFramework"
        echo "Looking for output in:"
        ls -la libsodium-apple/ 2>/dev/null || echo "No libsodium-apple directory found"
        exit 1
    fi
else
    echo "❌ libsodium not found. Install with: brew install libsodium"
    exit 1
fi

# Step 5: Create distribution README
echo "📝 Step 5: Creating README..."
cat > "$DISTRIBUTION_DIR/README.md" << 'EOF'
# Stellar KMP SDK - iOS Distribution

This package contains the Stellar SDK for iOS as an XCFramework bundle.

## Contents

- `stellar_sdk.xcframework` - The Stellar SDK framework
- `libsodium-apple.xcframework` - The libsodium cryptography library

## Installation

### Option 1: Drag and Drop (Recommended)

1. Drag both `stellar_sdk.xcframework` and `libsodium-apple.xcframework` into your Xcode project
2. In your target's "General" tab, under "Frameworks, Libraries, and Embedded Content":
   - Ensure both frameworks are set to "Embed & Sign"
3. Build and run

### Option 2: Manual Linking

1. Copy both XCFrameworks to your project directory
2. Add them to your Xcode project
3. In Build Settings, add to "Framework Search Paths":
   ```
   $(PROJECT_DIR)/path/to/frameworks
   ```

## Usage

```swift
import stellar_sdk

// Generate a random keypair
let keypair = KeyPair.Companion().random()
let accountId = keypair.getAccountId()
print("Account ID: \(accountId)")

// Sign data
let data = "Hello, Stellar!".data(using: .utf8)!
let signature = keypair.sign(data: Array(data))

// Verify signature
let isValid = keypair.verify(data: Array(data), signature: signature)
print("Signature valid: \(isValid)")
```

## Crypto Library

This SDK uses **libsodium** for cryptographic operations, providing:

- ✅ Production-proven security (used by Stellar Core)
- ✅ Cross-platform consistency (JVM, JS, Native all use same algorithms)
- ✅ Audited implementation
- ✅ Constant-time operations (side-channel protection)

## Requirements

- iOS 13.0+
- Xcode 14.0+
- Swift 5.7+

## Platform Support

| Platform | Support | Crypto Library |
|----------|---------|----------------|
| iOS | ✅ | libsodium |
| macOS | ✅ | libsodium |
| JVM | ✅ | BouncyCastle |
| JavaScript | ✅ | libsodium.js |

## License

Apache 2.0

## Support

For issues and questions, visit: https://github.com/stellar/stellar-sdk
EOF

echo ""
echo "✅ XCFramework build complete!"
echo ""
echo "📦 Distribution package created at:"
echo "   $DISTRIBUTION_DIR"
echo ""
echo "Contents:"
ls -lh "$DISTRIBUTION_DIR"
echo ""
echo "To test, drag both XCFrameworks into your iOS project and set to 'Embed & Sign'"
