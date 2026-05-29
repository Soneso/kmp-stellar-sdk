# Release Notes - Version 1.6.1

## Overview

Version 1.6.1 adds Ed25519 external signers to OpenZeppelin smart-account multi-signer signing and unifies all external (non-passkey) signing behind a single kit-owned manager. The release is additive and backwards compatible with 1.6.0.

## Added

### Ed25519 external signers

`SelectedSigner.Ed25519(verifierAddress, publicKey)` is a third multi-signer kind alongside passkey and wallet (G-address) signers. It is accepted anywhere a `selectedSigners` list is — `multiSignerTransfer`, `multiSignerContractCall`, `multiSignerExecuteAndSubmit`, `submitWithMultipleSigners`, and the context-rule operations (`addContextRule`, `removeContextRule`, `updateName`, `updateValidUntil`).

An Ed25519 signer provides a signature through one of two custody models:

- **In-memory key** — register a raw 32-byte seed at runtime:

  ```kotlin
  kit.externalSigners.addEd25519FromRawKey(secretKeyBytes, verifierAddress)
  ```

- **Adapter** — supply an `OZExternalEd25519SignerAdapter` (hardware wallet, HSM, or remote signing service) at kit construction; the SDK never sees the key:

  ```kotlin
  val config = OZSmartAccountConfig.builder(/* ... */)
      .externalEd25519Adapter(myHardwareAdapter)
      .build()
  ```

The on-chain signature is a raw `BytesN<64>` produced over the auth digest, verified by the configured Ed25519 verifier contract.

### Kit-owned external-signer manager

`OZSmartAccountKit.externalSigners` is the non-null, kit-owned `OZExternalSignerManager` that fronts all external (non-passkey) signers. Use it to register in-memory keys (`addFromSecret` for wallet signers, `addEd25519FromRawKey` for Ed25519 signers) and to check signing capability (`canSignFor`, `canSignEd25519For`) before submitting a multi-signer operation.

### New configuration field

`OZSmartAccountConfig.externalEd25519Adapter` supplies the Ed25519 adapter, the symmetric sibling of `externalWallet`. Both adapters are injected at kit construction and consumed by `kit.externalSigners`.

## Changed

### Unified external signing

External (non-passkey) signing is unified behind the kit-owned `OZExternalSignerManager`. The multi-signer pipeline resolves and signs both wallet (G-address) and Ed25519 signers through `kit.externalSigners`. Each kind offers two custody models — a config-injected adapter (`config.externalWallet` / `config.externalEd25519Adapter`) or an in-memory key registered at runtime.

Resolution precedence per kind: a wallet signer tries the in-memory keypair first, then the adapter; an Ed25519 signer tries the adapter first, then the in-memory key.

Wallet signing behaviour is unchanged. `config.externalWallet` continues to work exactly as in 1.6.0; this release adds the Ed25519 path and the `kit.externalSigners` accessor without altering existing behaviour.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.6.1`
- Backwards compatible with 1.6.0 (additive; no breaking changes)
- Stellar Protocol 23 compatible
- Horizon API: full REST coverage
- Soroban RPC: full method coverage
- SEPs implemented: SEP-1, SEP-2, SEP-5, SEP-6, SEP-8, SEP-9, SEP-10, SEP-12, SEP-24, SEP-30, SEP-31, SEP-38, SEP-45, SEP-46, SEP-47, SEP-48, SEP-53
