# Platform-Specific Guides

Platform-specific setup instructions and considerations for the Stellar KMP SDK.

## Available Platforms

- **[JVM/Android](jvm.md)** - Java, Kotlin, and Android application setup
- **[JavaScript/Web](javascript.md)** - Browser and Node.js configuration
- **[iOS](ios.md)** - iOS development with Swift
- **[macOS](macos.md)** - macOS native application setup

## Platform Support

| Platform | Minimum Version | Crypto Library | HTTP Client |
|----------|----------------|----------------|-------------|
| JVM | Java 17+ | BouncyCastle | Ktor CIO |
| Android | API 24+ | BouncyCastle | Ktor CIO |
| JavaScript | ES2015+ | libsodium.js | Ktor JS |
| iOS | 14.0+ | libsodium | Ktor Darwin |
| macOS | 11.0+ | libsodium | Ktor Darwin |

## Quick Navigation

**Getting Started**: Refer to each platform guide for dependency setup, initialization, and platform-specific considerations.

**Demo Apps**: See the [Demo App Guide](../demo-app.md) for complete working examples on each platform.

---

**Navigation**: [← Back to Documentation](../README.md)
