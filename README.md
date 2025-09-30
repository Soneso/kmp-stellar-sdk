# Stellar SDK for Kotlin Multiplatform

The Stellar SDK for Kotlin Multiplatform enables you to build Stellar applications that run on **Android**, **iOS**, and **Web** platforms with shared business logic written in Kotlin.

## Features

- 🎯 **True Multiplatform**: Write your Stellar integration once, deploy everywhere
- 🔐 **Ed25519 Cryptography**: Native crypto support on all platforms (libsodium on iOS/Web, native on Android)
- 💼 **Complete Stellar APIs**: Build transactions, connect to Horizon and Soroban RPC
- ✅ **Production Ready**: Used in production applications

## Quick Start

### KMP Sample App

The `stellarSample` directory contains a complete Kotlin Multiplatform sample application demonstrating shared business logic across all platforms:

```
stellarSample/
├── shared/          # Shared Kotlin code (KeyPair generation, signing, tests)
├── androidApp/      # Android app with Jetpack Compose UI
├── iosApp/          # iOS app with SwiftUI
└── webApp/          # Web app with Kotlin/JS
```

**Try it out:**

```bash
# Android
./gradlew :stellarSample:androidApp:installDebug

# iOS
cd stellarSample/iosApp
xcodegen generate
open StellarSample.xcodeproj

# Web
./gradlew :stellarSample:webApp:jsBrowserProductionWebpack
```

See [`stellarSample/README.md`](stellarSample/README.md) for detailed architecture and implementation examples.

## Installation

[Installation instructions to be added]

## Documentation

The Kotlin Multiplatform Stellar SDK provides APIs to build transactions and connect to Horizon and Stellar RPC Server.
