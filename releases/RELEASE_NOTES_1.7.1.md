# Release Notes - Version 1.7.1

## Overview

Version 1.7.1 brings improvements to the smart account module and the smart account demo: configurable token decimals for transfers and spending limits, stricter amount validation, typed context type and policy install builders, removal of non-functional API, and corrected documentation.

## Added

### Configurable token decimals

`transfer`, `multiSignerTransfer`, and spending limit amounts accept a `decimals` parameter. Transfers fetch the token's on-chain `decimals()` when not specified (`fetchTokenDecimals` is public); spending limits default to 7. `amountToBaseUnits` is available for manual conversions.

### Typed builders

Typed context type builders (`createDefaultContextType`, `createCallContractContextType`, `createCreateContractContextType`) and typed policy install params (`PolicyInstallParams` with public `toScVal()`) with a matching `addPolicy` overload.

### New query surface

- `OZExternalSignerManager`: `get`, `hasSigners`, and `hasWalletAdapter`.
- `OZSmartAccountKit.getDeployer()` is now public; `SmartAccountBuilders.getPublicKeyFromSigner` was added.

## Changed

- Amount validation is stricter: amounts with more fractional digits than the token's decimals are rejected instead of silently rounded, and invalid amounts throw `ValidationException.InvalidAmount` instead of `IllegalArgumentException`.
- The new optional `decimals` parameter changes the JVM binary signatures of `transfer`, `multiSignerTransfer`, and `addSpendingLimit`: callers using named arguments are source-compatible but need a recompile; precompiled JVM consumers and positional calls that pass arguments after the new parameter fail loudly and need updating.

## Removed / Deprecated

- Removed the non-functional external wallet connection persistence: `WalletConnectionStorage`, `ExternalWalletAdapter.reconnect`, `OZExternalSignerManager.addFromWallet` / `restoreConnections`, and the `walletConnectionStorage` constructor parameter. The kit never wired connection storage, so the reconnect path was unreachable.
- Removed the orphan policy param builders from `SmartAccountBuilders` (`createThresholdParams`, `createWeightedThresholdParams`, `createSpendingLimitParams`); use `PolicyInstallParams` instead.
- `describeSignerType` is deprecated; map signer types to display labels in your app.

## Fixed

- `OZSmartAccountKit.close()` now clears in-memory external signer secrets, closes the relayer HTTP client, and removes event listeners.
- `InMemoryStorageAdapter.clear()` also clears the stored session.

## Documentation

Corrected the smart account docs and skill references: platform setup instructions (Android Digital Asset Links, macOS libsodium linking, iOS package source), configuration snippets, stale conceptual descriptions, and throws documentation; trimmed redundant content.

## Smart Account Demo App

- The macOS app is aligned with the Compose UI: in-place policy editing, cross-rule signer reuse, validation parity, and shared view components.
- Shared flow fixes for all platforms: weighted-threshold weight prefill for account signers, pending-list deployments provision XLM and demo tokens, pending credential delete failures are surfaced, allowance fetch failures are logged.
- Context rule edits submit removals before additions, fixing contract errors when replacing signers or policies in one edit.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.7.1`
- The removed APIs (connection persistence, orphan policy param builders) had no known consumers; see Changed for the JVM binary-signature note on the new decimals parameters
