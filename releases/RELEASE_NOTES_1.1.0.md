# Release Notes - Version 1.1.0

## Overview

Version 1.1.0 adds test coverage infrastructure, 133 new unit test files (~3,984 tests across 5 platforms), and fixes critical cross-platform bugs in BigInteger encoding and native crypto operations discovered during testing.

## What's New

### Test Infrastructure

- **Kover Code Coverage**: Added Kover plugin for JVM code coverage with HTML and XML reports
- **Codecov Integration**: CI uploads coverage to Codecov; badge added to README
- **CI Workflow**: Expanded GitHub Actions workflow
  - JVM tests across JDK 17, 21, 25 (push + PR)
  - JS Node tests (push + PR)
  - macOS native tests (PR only)
- **Integration Test Exclusion**: `-PexcludeIntegrationTests` flag to skip integration tests in CI
- **Test Reorganization**: Split `commonTest` into `unitTests/` and `integrationTests/` directories
- **Real Wall-Clock Delays**: `platformDelay()` and `realDelay()` utilities for integration tests that need actual waiting (bypasses `runTest` virtual time)

### Unit Tests

133 new unit test files covering:
- Crypto, StrKey, KeyPair
- Transactions, operations, assets, accounts, memos
- Horizon request builders and response deserialization
- All operation response types and effect types
- Soroban RPC, contract client, assembled transactions
- SEP-1/6/9/10/12/24/38/45
- XDR round-trips

~3,984 unit tests passing on JVM, JS Node, JS Browser, macOS native, and iOS simulator.

## Bug Fixes

### BigInteger Two's Complement (JS & Native)

`bigIntegerToBytesSigned()` used magnitude-only encoding instead of proper two's complement. Negative Int128/Int256 values were silently corrupted on JS and Native targets. JVM was unaffected.

### Native Empty Data Crypto

- SHA-256 of empty data crashed (invalid assertion)
- Ed25519 sign/verify of empty data crashed (`addressOf(0)` on empty ByteArray)

### SorobanServer.pollTransaction()

Added `withContext(Dispatchers.Default)` for real wall-clock delay during polling, fixing too-fast polling on JS and Native.

## Breaking Changes

### JVM Target: Java 11 to Java 17

The minimum JVM target has been bumped from Java 11 to Java 17. This is required by Android AGP 8.x and Gradle 8+.

**Migration**: Update your project to use JDK 17 or later.

## Platform Support

All platforms fully supported:
- JVM (Android API 24+, Server Java 17+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

## Dependencies

No dependency changes from 1.0.0.

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v1.0.0...v1.1.0
