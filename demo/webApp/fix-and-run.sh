#!/bin/bash
# Stellar SDK Web App - Fix and Run Script
# This script recovers from stuck webpack/Kotlin builds and starts the development server

set -e

echo "========================================="
echo "Stellar SDK Web App - Recovery Script"
echo "========================================="
echo ""

# Step 1: Kill stuck processes
echo "[1/4] Killing stuck processes..."
pkill -9 -f "kotlin-compiler" 2>/dev/null || true
pkill -9 -f "webpack" 2>/dev/null || true
pkill -9 -f "gradlew.*webApp" 2>/dev/null || true
echo "  ✓ Processes killed"

# Step 2: Stop Gradle daemons
echo ""
echo "[2/4] Stopping Gradle daemons..."
./gradlew --stop || true
echo "  ✓ Daemons stopped"

# Step 3: Clean build directories
echo ""
echo "[3/4] Cleaning build directories..."
rm -rf demo/webApp/build build/js 2>/dev/null || true
echo "  ✓ Build directories cleaned"

# Step 4: Start development server
echo ""
echo "[4/4] Starting development server..."
echo "  → This will take about 30-60 seconds..."
echo ""
./gradlew :demo:webApp:jsDevelopmentRun

# The gradle task will keep running, so this message appears if it's interrupted
echo ""
echo "========================================="
echo "Web app stopped"
echo "========================================="
