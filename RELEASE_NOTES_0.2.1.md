# Release Notes: Version 0.2.1

**Release Date**: October 25, 2025
**Status**: Released

## Overview

Version 0.2.1 is a patch release that fixes Maven repository metadata and improves documentation. This release contains no code changes or breaking changes - only metadata and documentation fixes.

## What's Fixed

### Maven Publishing Metadata

The Maven Central package metadata has been corrected:

- **Repository URLs**: Now correctly point to `https://github.com/Soneso/kmp-stellar-sdk` (previously pointed to incorrect repository name `stellar-kotlin-multiplatform-sdk`)
- **Package Description**: Updated to "Kotlin Multiplatform Stellar SDK" for better clarity

These fixes ensure that developers can easily find the correct repository from Maven Central and other package repositories.

### Documentation Improvements

Several documentation issues have been resolved:

- **Broken Links**: Removed references to non-existent documentation files (docs/testing.md, docs/features.md, docs/platforms.md)
- **Platform Guide Link**: Updated to correctly point to the `docs/platforms/` directory
- **macOS Setup**: Corrected macOS demo app setup instructions to properly require `brew install libsodium`

### Production Status

Documentation has been updated to reflect the production-ready status of the SDK:

- Removed beta status warnings from README
- Added standard Apache License warranty notice

## Migration from 0.2.0

No code changes are required. Simply update your dependency version:

```kotlin
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:0.2.1")
}
```

## Platform Support

No changes to platform support:
- JVM (Android API 24+, Server Java 11+)
- iOS 14.0+
- macOS 11.0+
- JavaScript (Browser and Node.js 14+)

## Compatibility

Version 0.2.1 is fully compatible with 0.2.0. All APIs remain unchanged.

## Getting Help

If you encounter any issues:

1. Check the [documentation](https://github.com/Soneso/kmp-stellar-sdk)
2. Review the [Getting Started Guide](docs/getting-started.md)
3. Explore the [demo app](demo/README.md)
4. Open an issue on [GitHub](https://github.com/Soneso/kmp-stellar-sdk/issues)

## Acknowledgments

This release improves the discoverability and usability of the SDK through better metadata and documentation.

Built with Claude Code - AI-powered development assistant.

---

**Previous Release**: [Version 0.2.0](RELEASE_NOTES_0.2.0.md) - ContractClient API Simplification
