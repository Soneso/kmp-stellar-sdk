#!/bin/bash
set -e

echo "🔨 Building libsodium XCFramework for iOS"
echo ""
echo "This script downloads and builds libsodium for iOS from source."
echo "This may take several minutes..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build/libsodium-build"
DIST_DIR="$SCRIPT_DIR/distribution"

# Clean and create build directory
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
mkdir -p "$DIST_DIR"

cd "$BUILD_DIR"

# Download libsodium
if [ ! -f "libsodium-1.0.20.tar.gz" ]; then
    echo "📥 Downloading libsodium 1.0.20..."
    curl -L https://github.com/jedisct1/libsodium/releases/download/1.0.20-RELEASE/libsodium-1.0.20.tar.gz -o libsodium-1.0.20.tar.gz
fi

# Extract
echo "📦 Extracting..."
tar xzf libsodium-1.0.20.tar.gz
cd libsodium-1.0.20

# Build XCFramework
echo "🔨 Building libsodium XCFramework..."
echo ""
echo "This will build for:"
echo "  - iOS device (arm64)"
echo "  - iOS Simulator (arm64 + x86_64)"
echo "  - macOS (arm64 + x86_64)"
echo ""

./dist-build/apple-xcframework.sh

# Check if build succeeded
if [ -d "libsodium-apple/libsodium.xcframework" ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "Copying libsodium.xcframework to distribution/"
    cp -R libsodium-apple/libsodium.xcframework "$DIST_DIR/"

    echo ""
    echo "📦 XCFramework created at:"
    echo "   $DIST_DIR/libsodium.xcframework"
    echo ""
    echo "Size:"
    du -sh "$DIST_DIR/libsodium.xcframework"
    echo ""
else
    echo "❌ Build failed!"
    echo ""
    echo "Check the logs above for errors."
    echo "Common issues:"
    echo "  - Xcode command line tools not installed: xcode-select --install"
    echo "  - Wrong Xcode version selected: sudo xcode-select -s /Applications/Xcode.app"
    exit 1
fi
