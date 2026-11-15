# Release Notes - Version 1.18.0

## Overview

Version 1.18.0 supports smart accounts on OpenZeppelin stellar-accounts 0.8 (#160). The smart account demo deploys the 0.8.0 account and offers the 0.8 policies, which closes #153. Before it asks the first signer of an operation, the kit reads the WASM hash the wallet runs. It signs the 0.8 digest for the 0.8.0 build `c514d35e...` and for every hash that `OZSmartAccountConfig.authDigestSchemes` maps to `AuthDigestScheme.PREIMAGE_V1`. Configured entries can also map `c514d35e...` to `AuthDigestScheme.LEGACY_CONCAT`. Every other wallet signs the 0.7 digest as before. A custom build that implements `PREIMAGE_V1` needs its hash in `authDigestSchemes`; without the entry the contract rejects the authorization after the signers have signed.

## Breaking: context rule types and Delegated signers

- `ContextRuleType` has the subtype `CreateContractExternalRef(owner, tag)`, so an exhaustive `when` over it needs a new branch.
- `OZSignerManager.addDelegated`, `OZContextRuleManager.addContextRule`, and the signing pipelines refuse to add a contract (`C...`) address as a Delegated signer, because a contract delegate can let anyone authorize the account (GHSA-jpg9-m7w4-c7wc).

The migration guide describes each affected situation and what to do, including wallets that already hold a contract delegate.

## Added

- `SmartAccountAuth.computeEntryAuthDigest` returns the signature payload, the digest, and the delegated `__check_auth` arguments of an auth entry for an `AuthDigestScheme`.
- `OZBuilders.createCreateContractExternalRefContextType(owner, tag)` builds a rule for contracts deployed from an executable reference (CAP-0085). Automatic context rule resolution matches such a deploy to that rule or to a Default rule.
- `ContractErrorCodes` decodes 3017 `DelegatedSignerIsContextContract` and 3018 `SelfDelegatedSigner`.

## Changed

- The smart account pages and the agent skill describe the 0.8 digest, the new rule kind, the refusal to add contract delegates, and the error codes 3017 and 3018.

## Deprecated

- `SmartAccountAuth.buildAuthDigest` computes the 0.7 digest only; use `computeEntryAuthDigest` with the wallet's scheme.

## Security

- `addDelegated`, `addContextRule`, and the signing pipelines refuse the shared default deployer as a Delegated signer, because its secret derives from a public string.

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.18.0`
- Android: minimum SDK 24 (Android 7.0), compile SDK 34 or later
- Smart accounts on OpenZeppelin stellar-accounts 0.8.0 and 0.7
- Horizon v30.0.1 and stellar-rpc v30.0.1, the targets of the compatibility matrices

## Details

The [CHANGELOG](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.18.0/CHANGELOG.md) lists every change of this release.

Migrating to 1.18.0: [docs/migration/1.18.0.md](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.18.0/docs/migration/1.18.0.md)
