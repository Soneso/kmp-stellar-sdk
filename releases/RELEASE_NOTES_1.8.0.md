# Release Notes - Version 1.8.0

## Overview

Version 1.8.0 adds Protocol 27 (CAP-71) Soroban authorization support: the new authorization credential types and delegated account authorization. All additions are additive and opt-in — the legacy `SOROBAN_CREDENTIALS_ADDRESS` credential remains the default and stays fully valid. This release also fixes Horizon account parsing when `last_modified_time` is absent.

## Added

### Protocol 27 authorization (CAP-71)

- New `SorobanCredentialsXdr` arms `AddressV2` (`SOROBAN_CREDENTIALS_ADDRESS_V2`) and `AddressWithDelegates` (`SOROBAN_CREDENTIALS_ADDRESS_WITH_DELEGATES`), with the recursive `SorobanDelegateSignatureXdr` and `SorobanAddressCredentialsWithDelegatesXdr`.
- `ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS` and the matching `HashIDPreimageSorobanAuthorizationWithAddressXdr` preimage arm.
- `Auth.authorizeInvocation(...)` gained an `authV2` flag selecting between the `Address` and `AddressV2` credential arms.
- `Auth.authorizeEntry(...)` accepts `AuthOptions(forAddress = ...)` to target a delegate address in the tree; the default signs the top-level address.
- `Auth.attachDelegates(...)` builds an `AddressWithDelegates` entry from an `Address` or `AddressV2` entry, sorting and validating the delegate tree; `DelegateDescriptor` describes a delegate node.

The V2 and WITH_DELEGATES arms are valid only on Protocol 27 and later networks.

## Changed

- `AssembledTransaction.needsNonInvokerSigningBy()` and `signAuthEntries()` are arm-aware: they walk the delegate tree depth-first and report or sign matching delegate nodes as well as the top-level address.
- SEP-45 (`WebAuthForContracts`) and the smart-account / OpenZeppelin signing paths select the correct hash preimage per credential arm. No API change for callers.
- The new optional parameters change the JVM binary signatures of `Auth.authorizeEntry` and `Auth.authorizeInvocation`: callers using named arguments are source-compatible but need a recompile; precompiled JVM consumers and positional calls that pass arguments after the new parameter fail loudly and need updating.
- `SorobanCredentialsXdr` and `HashIDPreimageXdr` gain new sealed arms, and `SorobanCredentialsTypeXdr` and `EnvelopeTypeXdr` gain new entries; an exhaustive `when` over any of these public types must add branches for the new Protocol 27 arms (or an `else`) to compile.
- Compatibility matrices regenerated against Horizon v27.0.0 and Soroban RPC v27.0.0. The RPC `simulateTransaction` `useUpgradedAuth` parameter is intentionally not yet implemented — server-side support has not shipped; it will be added once released.

## Fixed

- `AccountResponse.lastModifiedTime` is now nullable, fixing Horizon account parsing when the field is absent (#39).

## Smart Account Demo App

- Clear the passkey name field after registering a signer.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.8.0`

## References

- Protocol 27 support: https://github.com/Soneso/kmp-stellar-sdk/pull/38
- AccountResponse nullable last-modified fix: https://github.com/Soneso/kmp-stellar-sdk/pull/39
