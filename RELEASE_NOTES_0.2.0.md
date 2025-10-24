# Release Notes: Version 0.2.0

**Release Date**: October 24, 2025
**Status**: Released

## Overview

Version 0.2.0 introduces a significant simplification of the ContractClient API, making it more intuitive and better suited for real-world smart contract interaction patterns. This release focuses on improving the developer experience while removing unnecessary complexity.

## What's New

### ContractClient API Simplification

The ContractClient API has been streamlined to focus on spec-based contract interaction with two clear modes:

1. **Simple Mode**: `invoke()` with automatic execution (unchanged)
2. **Advanced Mode**: `buildInvoke()` for manual transaction control with Map-based arguments

### New Feature: `buildInvoke()` Method

Added `ContractClient.buildInvoke()` for advanced transaction control:

```kotlin
val assembled = client.buildInvoke<String>(
    functionName = "transfer",
    arguments = mapOf(
        "from" to fromAddress,
        "to" to toAddress,
        "amount" to 1000
    ),
    source = sourceAccount,
    signer = sourceKeypair,
    parseResultXdrFn = { Scv.fromString(it) }
)

// Detect who needs to sign
val whoNeedsToSign = assembled.needsNonInvokerSigningBy()
if (whoNeedsToSign.contains(account1Id)) {
    assembled.signAuthEntries(account1Keypair)
}

val result = assembled.signAndSubmit(sourceKeypair)
```

**Primary Use Case**: Multi-signature workflows where multiple parties need to sign authorization entries before submission.

**Other Use Cases**:
- Adding memos to transactions
- Setting custom preconditions
- Inspecting simulation results before submission
- Controlling time bounds

## Breaking Changes

### 1. Factory Method Renamed

**Before (0.1.0-beta.1)**:
```kotlin
val client = ContractClient.fromNetwork(contractId, rpcUrl, Network.TESTNET)
```

**After (0.2.0)**:
```kotlin
val client = ContractClient.forContract(contractId, rpcUrl, Network.TESTNET)
```

**Rationale**: "forContract" better emphasizes creating a client FOR a contract, not loading FROM network.

**Migration**: Simple find-and-replace across your codebase.

### 2. Removed `withoutSpec()` Factory Method

The `ContractClient.withoutSpec()` factory method has been removed.

**Rationale**: A high-level ContractClient without a contract spec provides no value. For low-level contract interaction without a spec, use `SorobanServer` + `TransactionBuilder` directly.

**Migration**:
- If you need spec-based interaction: Use `forContract()` instead
- If you need no-spec interaction: Use low-level APIs (`SorobanServer` + `TransactionBuilder`)

### 3. Removed `invokeWithXdr()` Method

The `ContractClient.invokeWithXdr()` method has been removed and replaced by `buildInvoke()`.

**Before (0.1.0-beta.1)**:
```kotlin
val xdrArgs = client.funcArgsToXdrSCValues("transfer", listOf(...))
val assembled = client.invokeWithXdr("transfer", xdrArgs)
```

**After (0.2.0)**:
```kotlin
val assembled = client.buildInvoke<T>(
    functionName = "transfer",
    arguments = mapOf("from" to addr1, "to" to addr2, "amount" to 1000)
)
```

**Benefits**:
- More ergonomic: Map-based arguments instead of XDR types
- No manual XDR conversion needed
- Same level of control with cleaner API

## Migration Guide

### Quick Reference

| 0.1.0-beta.1 | 0.2.0 | Notes |
|--------------|-------|-------|
| `fromNetwork()` | `forContract()` | Simple rename |
| `withoutSpec()` | Removed | Use low-level APIs instead |
| `invokeWithXdr()` | `buildInvoke()` | Takes Map instead of XDR |

### Step-by-Step Migration

#### 1. Update Factory Method Calls

Search for `fromNetwork` and replace with `forContract`:

```bash
# Find all occurrences
grep -r "fromNetwork" your-project/

# Replace (or use IDE find-and-replace)
```

#### 2. Remove `withoutSpec()` Usage

If using `withoutSpec()`, migrate to either:

**Option A: Use `forContract()` with spec**:
```kotlin
// Deploy or obtain contract ID with spec
val client = ContractClient.forContract(contractId, rpcUrl, Network.TESTNET)
```

**Option B: Use low-level APIs**:
```kotlin
val server = SorobanServer(rpcUrl)
// Use TransactionBuilder directly for no-spec scenarios
```

#### 3. Update `invokeWithXdr()` to `buildInvoke()`

**Before**:
```kotlin
val args = client.funcArgsToXdrSCValues("myFunction", listOf(arg1, arg2))
val assembled = client.invokeWithXdr("myFunction", args)
val result = assembled.signAndSubmit(keypair)
```

**After**:
```kotlin
val assembled = client.buildInvoke<ReturnType>(
    functionName = "myFunction",
    arguments = mapOf("param1" to arg1, "param2" to arg2),
    source = sourceAccount,
    signer = keypair,
    parseResultXdrFn = { /* parse result */ }
)
val result = assembled.signAndSubmit(keypair)
```

## Improved Features

### Multi-Signature Workflow Support

The new `buildInvoke()` method is designed specifically for multi-signature workflows:

```kotlin
// Build transaction
val assembled = client.buildInvoke<Unit>(
    functionName = "transfer",
    arguments = mapOf("from" to addr1, "to" to addr2, "amount" to 1000)
)

// Detect required signers
val whoNeedsToSign = assembled.needsNonInvokerSigningBy()

// Conditionally sign based on authorization requirements
if (whoNeedsToSign.contains(account1Id)) {
    assembled.signAuthEntries(account1Keypair)
}
if (whoNeedsToSign.contains(account2Id)) {
    assembled.signAuthEntries(account2Keypair)
}

// Submit when ready
val result = assembled.signAndSubmit(sourceKeypair)
```

### Clearer API Intent

- `forContract()` clearly indicates you're creating a client for a specific contract
- `buildInvoke()` clearly indicates you're building a transaction (not auto-executing)
- Map-based arguments are more intuitive than XDR types

## Documentation Updates

All documentation has been updated to reflect the new API:

- README.md: Updated with new API patterns and multi-sig examples
- API Reference: Complete method documentation with examples
- Migration Guide: Comprehensive upgrade instructions
- Demo Apps: Updated to use new API across all platforms

## Testing

All changes have been validated with:
- Unit tests for new API methods
- Integration tests against Stellar Testnet
- Demo app testing on all platforms (Android, iOS, macOS, Desktop, Web)

## Platform Support

No changes to platform support:
- JVM (Android API 24+, Server Java 11+)
- iOS 14.0+
- macOS 11.0+
- JavaScript (Browser and Node.js 14+)

## Deprecation Timeline

This is a breaking change release. The removed methods (`withoutSpec()`, `invokeWithXdr()`, `fromNetwork()`) are no longer available as of version 0.2.0.

If you need to stay on the old API temporarily, use version 0.1.0-beta.1.

## Getting Help

If you encounter issues during migration:

1. Review the migration guide above
2. Check the updated examples in the demo app
3. Refer to the API documentation
4. Open an issue on GitHub

## Acknowledgments

This release simplifies the ContractClient API based on real-world usage patterns and feedback. The focus on multi-signature workflows addresses a critical need for production smart contract applications.

Built with Claude Code - AI-powered development assistant.

---

**Next Release**: Version 0.2.0 focuses on API simplification. Future releases will continue to improve developer experience and add new features based on community feedback.
