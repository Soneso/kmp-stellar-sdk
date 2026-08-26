# Release Notes - Version 1.12.0

## Overview

Version 1.12.0 adds Protocol 28 support: contracts whose executable is a CAP-85 external reference resolve, load, and deploy through the same entry points as wasm-backed contracts. Authorization moves to the CAP-71 `ADDRESS_V2` defaults introduced with Protocol 27, matching js-stellar-sdk v17. Strkey and claimable-balance-id parsing are hardened to SEP-23 strictness, and hex id parsing to the ASCII hex alphabet. Four source-breaking changes and twelve behavior changes are listed under Compatibility.

## Added

### Protocol 28: CAP-85 external references

A contract created from an external reference names an owner contract and a tag; the owner holds a persistent entry under that tag whose value is the hash of an already-uploaded wasm.

- `SorobanServer.loadContractCodeForContractId`, `loadContractInfoForContractId`, and `ContractClient.forContract` resolve external-reference executables through the owner's tag entry — one extra ledger read, the owner contract is never invoked. These previously answered null or "Contract spec not found" for such contracts. An unresolvable reference throws `IllegalStateException` naming the owner and the tag.
- `SorobanServer.getExternalRefWasmHash(ref)` resolves a reference directly. A non-contract owner is rejected with `IllegalArgumentException` before any request.
- `InvokeHostFunctionOperation.createContractFromExternalRef` builds the create operation, with `tag` as `String` (UTF-8 encoded) or `ByteArray`, and rejects a non-contract owner.
- `ContractClient.deployFromExternalRef` deploys from an owner and tag; parameters mirror `deployFromWasmId`. The reference is resolved before the transaction is built, so an unresolvable reference fails client-side. One tag value feeds both the resolution and the built operation, byte for byte.
- `Address.deriveContractId(deployer, salt, network)` returns the contract id a deployment creates, without deploying.
- `ContractSpec.scValToNative` converts `SCV_EXECUTABLE_TAG` values — to `String` for UTF-8 tags, to `ByteArray` otherwise.

Details: [external references](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.12.0/docs/advanced.md#external-reference-executables-cap-85), [deployment](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.12.0/docs/advanced.md#deployment-from-an-external-reference-protocol-28).

### Claimable balance ids

- New `ClaimableBalanceId` type reads a balance id in any of its four spellings — `B...` strkey, 64-character hash hex, and the 66- or 72-character discriminant-carrying hex forms — and reports each spelling back (`hashHex`, `toPaddedHex()`, `toStrKey()`, `toXdr()`). Every byte of a discriminant is checked.
- `ClaimClaimableBalanceOperation`, `ClawbackClaimableBalanceOperation`, `Sponsorship.ClaimableBalance`, and the claimable-balance request-builder routes accept any spelling and validate at construction, before any request.
- `TransactionResponse.getCreatedClaimableBalanceId(operationIndex)` reports the id a successful operation created, as a `B...` strkey, reading fee bumps through to the inner transaction.
- `StrKey.encodeClaimableBalance` and `Address.fromClaimableBalance` additionally accept the 36-byte XDR wire form.

## Changed

### Breaking: the CAP-85 executable tag is raw bytes

The ledger matches an executable tag byte for byte, and the XDR `string` type admits arbitrary bytes; decoding into a Kotlin `String` replaced non-UTF-8 bytes, so such a tag could neither round-trip nor resolve. `ContractExecutableExternalRefXdr.tag` and the `SCValXdr.ExecutableTag` payload — both shipped in 1.11.0 as `SCStringXdr` — are now `ByteArray`. Text stays available: `tagString` decodes strictly and throws `CharacterCodingException` on non-UTF-8; `Scv.fromExecutableTag` throws `IllegalArgumentException`; `Scv.toExecutableTagBytes` / `fromExecutableTagBytes` carry the exact bytes; and the `String`-taking factories keep compiling. Note both positions live in data classes over `ByteArray`, so `==` and `hashCode` follow array identity — compare via `contentEquals` or the string views. The wire form is unchanged.

### Breaking: authorization defaults to ADDRESS_V2 (CAP-71)

Matching the Protocol 27+ default of js-stellar-sdk v17:

- `Auth.authorizeInvocation` builds `ADDRESS_V2` credentials by default (`authV2 = false` opts out).
- `SimulateTransactionRequest.useUpgradedAuth`, `SorobanServer.simulateTransaction` / `prepareTransaction`, and `ClientOptions.useUpgradedAuth` default to true, and the key is sent on every simulate request. The `SorobanServer` parameters and the `SimulateTransactionRequest` property changed `Boolean?` to `Boolean` — Kotlin callers passing `null` must pass a `Boolean`, and precompiled JVM/Android consumers must rebuild; `ClientOptions.useUpgradedAuth` was already `Boolean` and only its default moved from false to true.
- The OpenZeppelin smart-account kit requests `ADDRESS_V2` entries from its simulations and builds `ADDRESS_V2` credentials in the `fundWallet` conversion (`OZSmartAccountConfig.useUpgradedAuth = false` opts out, for relayer services on the pre-protocol-27 schema) and for delegated wallet-signer entries (`useUpgradedAuthForWalletSigners = false` opts out, for wallets that cannot sign the address-bound preimage).
- `SmartAccountAuth.buildSourceAccountAuthPayloadHash` gains a required `address` parameter (second position) and a trailing `useUpgradedAuth` — every existing call site must supply the address.

On a network below Protocol 27, `ADDRESS_V2` entries invalidate the transaction: pass the opt-outs there.

### Breaking: `EffectsRequestBuilder.forClaimableBalance` is removed

Horizon serves no `/claimable_balances/{id}/effects` route; the method could only build requests that fail. Query the balance's operations or transactions instead.

### ContractClient deployments always submit CREATE_CONTRACT_V2

`deploy`, `deployFromWasmId`, and `deployFromExternalRef` submit the `CREATE_CONTRACT_V2` host function with the constructor vector — empty when no arguments are given. Without constructor arguments they previously submitted plain `CREATE_CONTRACT`; the on-chain result is the same, but anything pinning submitted XDR must expect the V2 arm. The plain form stays available through `InvokeHostFunctionOperation.createContract` / `createContractFromExternalRef`.

## Fixed

### Strkey decoding is strict (SEP-23)

Matching the validation level of js-stellar-sdk v17. Input that at least one target previously accepted now raises `IllegalArgumentException` on all of them: trailing `=` padding, whitespace anywhere, lowercase base32, and non-ASCII characters; wrong lengths are now rejected before the codec runs rather than after. Padding could previously defeat the unused-bits check, so two different strings named one account; a non-ASCII character was narrowed to its low byte with the same effect. Signed-payload framing is enforced on decode and encode, and non-V0 claimable-balance strkeys fail at decode instead of only at `toSCAddress()`. Every platform now reaches the same verdict with the same exception type — JVM and Android previously accepted strings that JavaScript and Native rejected.

### Hex parsing is strict

Hex carrying sign characters or non-ASCII digits previously produced different bytes than its characters spell (`"-1"` read as `0xFF`); it is rejected with `IllegalArgumentException` across memos, liquidity-pool operations, contract creation, contract-spec arguments, and credential storage. `loadContractCodeForWasmId` / `loadContractInfoForWasmId` reject a wrong-length wasm id at the entry point with a message naming the rule.

### Amount strings

- Trailing zeros past the seventh decimal place are accepted: `"0.50000000"` builds 0.5 where it was previously rejected. The seven-decimal limit counts significant decimals; `"0.12345678"` is still rejected.
- A sign character inside the fraction is rejected: `"1.-5"` previously built 0.95 instead of failing.

### Other fixes

- `AssetContractBalanceChange.from` / `.to` are nullable: Horizon omits `from` on mint and `to` on burn and clawback, and an operations page carrying one previously failed to parse as a whole.
- SEP-10 and SEP-45 challenge validation propagates coroutine cancellation instead of reporting it as an invalid server signature.
- SEP-12 callback signature verification fails closed on a malformed configured signing key; SEP-8 `fromDomain` rejects a malformed issuer in `stellar.toml` uniformly on every platform.
- `Sponsorship.ClaimableBalance` read from XDR reports the 72-character Horizon form (previously the bare 64-character hash) and validates ids at construction.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.12.0`
- Four source-breaking changes:
  - The CAP-85 tag positions are `ByteArray` (were `SCStringXdr` in 1.11.0); typed reads and pattern matches must handle bytes.
  - `SmartAccountAuth.buildSourceAccountAuthPayloadHash` requires the credential address as its second parameter.
  - The `useUpgradedAuth` parameters of `simulateTransaction`, `prepareTransaction`, and `SimulateTransactionRequest` are `Boolean` (were `Boolean?`); passing `null` no longer compiles, and their JVM binary signatures (including the `SimulateTransactionRequest` constructor and `copy()`) changed — rebuild precompiled consumers. The `OZSmartAccountConfig` constructor and `copy()` signatures also changed, through the two new flag parameters.
  - `EffectsRequestBuilder.forClaimableBalance` is removed.
- Twelve behavior changes to be aware of:
  - Authorization defaults to `ADDRESS_V2` across `Auth.authorizeInvocation`, simulation, and the OZ kit; opt out below Protocol 27 (`authV2 = false`, `useUpgradedAuth = false`, `useUpgradedAuthForWalletSigners = false`).
  - `ContractClient` deployments always submit `CREATE_CONTRACT_V2`; XDR-pinning tests must expect the V2 arm.
  - Strkey decoding rejects padded, whitespace-bearing, lowercase, and non-ASCII input on every platform; trim and normalize pasted input before validating.
  - Signed-payload (`P...`) framing is enforced: malformed stored values that previously decoded — possibly to a different strkey — now raise `IllegalArgumentException`.
  - Non-V0 claimable-balance strkeys fail at decode and `Address` construction, no longer only at `toSCAddress()`.
  - Hex ids with sign characters or non-ASCII digits are rejected instead of silently misread.
  - Claimable-balance operations and request builders validate ids at construction; `Sponsorship.ClaimableBalance` reports the 72-character form on read-back — normalize stored ids via `ClaimableBalanceId.forId(...).toPaddedHex()`.
  - Amount strings accept trailing zeros past seven decimals and reject sign characters in the fraction.
  - `AssetContractBalanceChange.from` / `.to` are nullable.
  - SEP-8 `fromDomain` fails on JVM and Android for a malformed `stellar.toml` issuer, as it already did on JavaScript and Native.
  - SEP-12 callback signature verification returns false for a malformed configured signing key; on JVM and Android such a key previously decoded and verification could pass.
  - SEP-10 and SEP-45 challenge validation surfaces coroutine cancellation as `CancellationException`.

## References

- [Migration guide](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.12.0/docs/migration/1.12.0.md) — one note per situation.
- [CHANGELOG](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.12.0/CHANGELOG.md) — the itemized record.
