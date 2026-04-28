# Release Notes - Version 1.5.1

## Overview

Version 1.5.1 ships two improvements and several bug fixes:

- **Smart-account connect cascade order improved**: `connectWallet` now runs storage → derivation → indexer, and surfaces multi-result indexer responses as a new `ConnectWalletResult.Ambiguous` arm so the caller can let the user pick when a passkey is registered as a signer on multiple smart accounts.
- **XDR generator multi-case discriminant collapse fixed**: five generated XDR types now carry their discriminant correctly, so `txFAILED` and the various `SCError` types are no longer mis-decoded.

Both items above include a small **breaking API surface change** for direct consumers; high-level helpers (Horizon, RPC, `AssembledTransaction`, `ContractClient`, `OZSmartAccountKit` operations) are unaffected. Migration notes are at the bottom of these notes.

## Changed

### Smart-account connect: cascade order improved

The connection cascade in `OZWalletOperations.connectWallet` (and `connectWithCredentials`) now runs **storage → derivation → indexer** (was storage → indexer → derivation). Multi-result indexer responses surface as `ConnectWalletResult.Ambiguous(candidates)` so the caller can render a picker.

Previously, when a passkey was registered as a signer on multiple smart accounts, the indexer's first result (lex-first by contract address) was picked, with no signal that the choice was ambiguous. The new behaviour makes ambiguity explicit and lets the caller choose.

`ConnectWalletResult` is now a sealed type:

```kotlin
sealed class ConnectWalletResult {
    abstract val credentialId: String

    data class Connected(
        override val credentialId: String,
        val contractId: String,
        val restoredFromSession: Boolean
    ) : ConnectWalletResult()

    data class Ambiguous(
        override val credentialId: String,
        val candidates: List<String>
    ) : ConnectWalletResult()
}
```

`Ambiguous` is by-construction unreachable when the caller supplies an explicit `contractId` (the cascade is bypassed). Session-restore and direct-connect callers always see `Connected`.

### XDR constructor signatures (multi-case discriminant types)

Five XDR types affected by the discriminant fix now take `discriminant` as the first constructor argument:

- `TransactionResultResultXdr.Results`
- `InnerResultPair` (inside `TransactionResultPairXdr.InnerResultPair`)
- `BucketEntryXdr.LiveEntry`
- `ManageOfferSuccessResultOfferXdr.Offer`
- `SCErrorXdr.Code`

High-level helper users (Horizon's `successful`, RPC's `status`, `AssembledTransaction`, `ContractClient`) are unaffected — they flow through JSON, not XDR discriminants. Direct constructor users need to add the discriminant argument.

## Fixed

### XDR generator multi-case discriminant collapse

XDR unions where multiple discriminant cases shared one non-void payload were generated with the discriminant hardcoded to the first case. As a result:

- `txFAILED` round-tripped as `txSUCCESS`
- All `SCError` types collapsed to `SCE_WASM_VM` (the error *code* was correct, but the error *type* was always `SCE_WASM_VM`)

The bug surfaced for code that decoded `TransactionResultResultXdr` and inspected `.discriminant` directly, or read Soroban contract errors via `Scv.fromError()` / `ContractSpec.scValToNative()`.

The generator now emits the discriminant as a constructor parameter for these arms, so all multi-case discriminants round-trip correctly.

### Smart-account connect: FAILED-status credentials

A credential whose deployment previously failed silently connected to a non-existent contract. It now throws `WalletException.NotFound` with a message pointing the user at `deployPendingCredential()` for retry, or `deleteCredential()` to start over.

### Smart-account connect: transport-error masking

RPC and indexer transport errors during the connect cascade no longer get laundered as "contract not found." They propagate as their original types (e.g. `SorobanRpcException`, `IndexerException`), so callers can distinguish "contract is not on-chain" from "the lookup itself failed."

### Smart-account indexer JSON parsing

`OZIndexerClient` data classes (`CredentialLookupResponse`, `AddressLookupResponse`, `ContractDetailsResponse`) annotated their top-level fields with `@SerialName` for snake_case, but the hosted indexer returns top-level keys in camelCase (inner fields use snake_case). The annotations have been removed from top-level fields, matching the indexer's actual response shape.

The previous cascade order swallowed the deserialization failure silently; the new order surfaced it, which led to the discovery and fix.

### Test stability

`SmartAccountKitTest.testStoredCredential_equality` no longer flakes on slow JVM runners. The default `createdAt = currentTimeMillis()` could produce different values for back-to-back constructions, breaking the equality assertion non-deterministically. The test now pins `createdAt` explicitly.

## Migration

### `ConnectWalletResult`

Direct field access (`result.contractId`) is no longer valid. Use a `when` expression:

```kotlin
when (val result = walletOps.connectWallet(...)) {
    null -> { /* no session, prompt = false */ }
    is ConnectWalletResult.Connected -> println(result.contractId)
    is ConnectWalletResult.Ambiguous -> {
        // Show a picker; reconnect with the chosen contractId.
        // Reuse result.credentialId to skip a second WebAuthn ceremony.
        val chosen = userPicker(result.candidates)
        walletOps.connectWallet(
            ConnectWalletOptions(
                credentialId = result.credentialId,
                contractId = chosen
            )
        )
    }
}
```

`Ambiguous` is by-construction unreachable when `contractId` is supplied explicitly, so callers that pass an explicit contract address can `when` on `Connected` only.

### XDR constructor signatures

Direct construction of the affected XDR types now requires `discriminant` as the first argument. Example:

```kotlin
// Before
SCErrorXdr.Code(code = 5)

// After
SCErrorXdr.Code(SCErrorTypeXdr.SCE_CONTEXT, 5)
```

Code that uses the high-level helpers (`KeyPair`, `TransactionBuilder`, `HorizonServer`, `SorobanServer`, `AssembledTransaction`, `ContractClient`, `OZSmartAccountKit`, etc.) needs no changes.

## Platform Support

All platforms fully supported (unchanged from 1.5.0):
- JVM (Android API 24+, Server Java 17+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

## Installation

```kotlin
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.5.1")
}
```

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v1.5.0...v1.5.1
