#!/bin/bash

# Build script for iOS Sample App

set -e

echo "=================================================="
echo "Building Stellar KMP SDK - iOS Sample App"
echo "=================================================="
echo ""

# Detect architecture
ARCH=$(uname -m)
echo "📱 Detected architecture: $ARCH"
echo ""

# Choose the right task based on architecture
if [ "$ARCH" = "arm64" ]; then
    FRAMEWORK_TASK="linkDebugFrameworkIosSimulatorArm64"
    FRAMEWORK_PATH="stellar-sdk/build/bin/iosSimulatorArm64/debugFramework/stellar_sdk.framework"
else
    FRAMEWORK_TASK="linkDebugFrameworkIosX64"
    FRAMEWORK_PATH="stellar-sdk/build/bin/iosX64/debugFramework/stellar_sdk.framework"
fi

echo "🔨 Building iOS framework..."
echo "   Task: $FRAMEWORK_TASK"
./gradlew :stellar-sdk:$FRAMEWORK_TASK --console=plain --quiet

if [ $? -ne 0 ]; then
    echo "❌ Framework build failed"
    exit 1
fi

echo "✅ Framework built successfully"
echo "   Location: $FRAMEWORK_PATH"
echo ""

echo "📦 Framework details:"
ls -lh "$FRAMEWORK_PATH"
echo ""

echo "=================================================="
echo "✅ Build complete!"
echo "=================================================="
echo ""
echo "Next steps:"
echo "  1. Open Xcode:"
echo "     cd iosSample && open iosSample.xcodeproj"
echo ""
echo "  2. Select an iOS Simulator"
echo ""
echo "  3. Press ⌘R to run the app"
echo ""
echo "The app will:"
echo "  • Generate new Stellar keypairs"
echo "  • Run 10 comprehensive tests"
echo "  • Show results in real-time"
echo ""