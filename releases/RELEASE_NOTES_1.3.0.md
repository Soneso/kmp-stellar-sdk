# Release Notes - Version 1.3.0

## Overview

Version 1.3.0 adds three new SEP implementations: SEP-2 (Federation Protocol), SEP-30 (Account Recovery), and SEP-53 (Sign and Verify Messages). It also introduces automated SEP compatibility matrix generation.

## What's New

### SEP-2: Federation Protocol

Resolve human-readable Stellar addresses to account IDs and perform reverse lookups.

- `FederationService` with `fromDomain()` factory and 4 query methods
- Supports all federation query types: name, account ID, transaction ID, and forward lookups
- Typed exceptions for invalid addresses, missing federation servers, and malformed responses

```kotlin
import com.soneso.stellar.sdk.sep.sep02.*

// Resolve a stellar address
val response = FederationService.resolveStellarAddress("bob*stellar.org")
println("Account: ${response.accountId}")
println("Memo: ${response.memo} (${response.memoType})")

// Create service for a specific domain
val service = FederationService.fromDomain("stellar.org")
val result = service.resolveAccountId("GABC...")
```

### SEP-30: Account Recovery

Multi-party account recovery using alternative authentication methods. Users register with recovery servers that hold signing keys; when keys are lost, authenticated users collect signatures from multiple servers to restore access.

- `Sep30Service` with all 6 spec endpoints: register, update identities, sign transaction, account details, delete, list accounts
- 9 data model classes with JSON serialization/deserialization
- 7 typed exceptions mapping HTTP status codes (400, 401, 404, 409, unknown, malformed 200)

```kotlin
import com.soneso.stellar.sdk.sep.sep30.*

val service = Sep30Service("https://recovery.example.com", authToken)

// Register an account for recovery
val request = Sep30Request(
    identities = listOf(
        Sep30RequestIdentity(
            role = "owner",
            authMethods = listOf(
                Sep30AuthMethod(type = "email", value = "user@example.com")
            )
        )
    )
)
val response = service.registerAccount("GABC...", request)
```

### SEP-53: Sign and Verify Messages

Off-chain message signing and verification using Ed25519 keypairs, enabling proof of account ownership without on-chain transactions.

```kotlin
import com.soneso.stellar.sdk.KeyPair

val keyPair = KeyPair.fromSecretSeed("S...")

// Sign a message
val signedMessage = keyPair.signMessage("Hello, Stellar!")

// Verify a signed message
val isValid = KeyPair.verifySignedMessage(signedMessage)
```

### SEP Compatibility Matrix Automation

Automated 3-stage Python pipeline that generates field-by-field coverage reports for all implemented SEPs:

1. `sep_parser.py` - Fetches and parses SEP specifications from GitHub
2. `sep_analyzer.py` - Scans SDK Kotlin source and maps spec fields to implementation
3. `generate_sep_comparison.py` - Compares definitions against implementation and generates markdown matrices

Run with: `python3 tools/sdk-analysis/sep/run_sep_analysis.py`

## Test Coverage

- SEP-2: Unit tests with MockEngine + integration test against live federation server
- SEP-30: 83 unit tests covering response parsing, exceptions, and service operations
- SEP-53: Unit tests with known test vectors
- All tests passing on JVM, JS Node, and macOS native

## Platform Support

All platforms fully supported:
- JVM (Android API 24+, Server Java 17+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

## Documentation

- SEP-2 guide: `docs/sep/sep-02.md`
- SEP-30 guide: `docs/sep/sep-30.md`
- SEP-53 guide: `docs/sep/sep-53.md`
- Compatibility matrices: `compatibility/sep/`

## Dependencies

No new external dependencies added. Uses existing Ktor HTTP client and kotlinx-serialization.

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v1.2.1...v1.3.0
