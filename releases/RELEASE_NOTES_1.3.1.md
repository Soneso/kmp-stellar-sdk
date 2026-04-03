# Release Notes - Version 1.3.1

## Overview

Version 1.3.1 is a hotfix release that corrects asset code validation to allow lowercase letters.

## Fixed

### Asset Code Validation

Asset code validation was rejecting lowercase letters (a-z), preventing interaction with real mainnet assets that use lowercase characters (e.g., yUSDC, yETH). Validation now accepts both uppercase and lowercase letters, matching the behavior of the JS and Python Stellar SDKs.

See [#14](https://github.com/Soneso/kmp-stellar-sdk/issues/14) for details.

## Platform Support

All platforms fully supported (unchanged from 1.3.0):
- JVM (Android API 24+, Server Java 17+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

## Installation

```kotlin
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.3.1")
}
```

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v1.3.0...v1.3.1
