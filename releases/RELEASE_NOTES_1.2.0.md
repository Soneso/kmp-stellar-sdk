# Release Notes - Version 1.2.0

## Overview

Version 1.2.0 adds SEP-5 support for hierarchical deterministic (HD) wallets, enabling users to derive multiple Stellar accounts from a single mnemonic phrase using BIP-39 and SLIP-0010 key derivation.

## What's New

### SEP-5: Key Derivation Methods for Stellar Keys

Full implementation of SEP-5 for HD wallet support:

**Mnemonic Generation**
- Generate BIP-39 mnemonic phrases with 12, 15, 18, 21, or 24 words
- 9 language support: English, Japanese, Korean, Spanish, Chinese (Simplified/Traditional), French, Italian, Malay
- Cryptographically secure entropy generation on all platforms

**Key Derivation**
- SLIP-0010 hierarchical deterministic key derivation for Ed25519
- Stellar-specific derivation path: `m/44'/148'/x'`
- Derive unlimited accounts from a single mnemonic
- Optional BIP-39 passphrase for additional security

**Validation**
- Mnemonic validation with checksum verification
- Automatic language detection
- Word list membership validation

**Security**
- PBKDF2-HMAC-SHA512 with 2048 iterations for seed derivation
- Secure memory cleanup via `close()` method
- Platform-specific audited crypto libraries (BouncyCastle, libsodium)

### Usage Example

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic

// Generate a 24-word mnemonic
val phrase = Mnemonic.generate24WordsMnemonic()

// Derive accounts
val mnemonic = Mnemonic.from(phrase)
val account0 = mnemonic.getKeyPair(index = 0)
val account1 = mnemonic.getKeyPair(index = 1)

println("Account 0: ${account0.getAccountId()}")
println("Account 1: ${account1.getAccountId()}")

// Clean up
mnemonic.close()
```

## Test Coverage

- 182 unit tests for SEP-5 functionality
- All 5 official SEP-5 test vectors validated (10 accounts each)
- Tests passing on JVM, JS Node, JS Browser, macOS native, and iOS simulator

## Platform Support

All platforms fully supported:
- JVM (Android API 24+, Server Java 17+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

## Documentation

- SEP-5 guide: `docs/sep/sep-05.md`
- Compatibility matrix: `compatibility/sep/SEP-0005_COMPATIBILITY_MATRIX.md` (100% coverage)
- SDK usage examples updated with HD Wallets section

## Dependencies

New platform-specific dependencies for SEP-5 crypto:
- JVM: BouncyCastle (existing dependency, used for PBKDF2)
- JS: libsodium-wrappers-sumo (existing dependency)
- Native: libsodium (existing dependency)

No new external dependencies added.

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v1.1.0...v1.2.0
