# Release Notes - Version 1.9.0

## Overview

Version 1.9.0 adds support for the stellar-rpc v27.1 API: the `useUpgradedAuth` simulateTransaction flag and the new `getHealth` ledger close-time fields. It also introduces a spec-free contract-invocation path — the foundation for the upcoming Kotlin Multiplatform target of the community bindings generator ([stellar-contract-bindings](https://github.com/lightsail-network/stellar-contract-bindings)) — checks in generated contract-binding fixtures with unit and testnet integration tests, and refreshes CI dependencies. All additions are additive and opt-in; there are no source-breaking changes. The new optional parameters do change the JVM binary signatures of the affected functions and constructors — precompiled JVM consumers and positional calls that pass arguments after a new parameter need a recompile (see Compatibility).

## Added

### useUpgradedAuth simulation flag

- A `useUpgradedAuth` flag on `SorobanServer.simulateTransaction(...)`, `SorobanServer.prepareTransaction(...)`, `SimulateTransactionRequest`, and `ClientOptions`. When set, recording-mode simulation on a supporting RPC (stellar-rpc v27.1.0+) returns `AddressV2` credential entries (Protocol 27, CAP-71) instead of legacy `Address` entries. The flag is optional and defaults to omitted, so the request wire shape is unchanged when it is not set; RPCs without support silently ignore it and return legacy entries — detect support by inspecting the credential arm of the returned entries, not by expecting an error.

### getHealth ledger close times

- `GetHealthResponse` gains `latestLedgerCloseTime` and `oldestLedgerCloseTime` (`Long?`), the unix timestamps (seconds) at which the latest and oldest ledgers closed, returned by stellar-rpc v27.1.0+. On older servers the fields are `null`.

### Spec-free contract invocation (preparation for generated bindings)

This spec-free path is the foundation for the upcoming Kotlin Multiplatform target of the community bindings generator ([stellar-contract-bindings](https://github.com/lightsail-network/stellar-contract-bindings)), which is not yet released. Generated contract clients encode and decode all values themselves and invoke contracts through this path.

- `ContractClient` gains positional `invoke(functionName, parameters: List<SCValXdr>, ...)` and `buildInvoke(functionName, parameters: List<SCValXdr>, ...)` overloads that take pre-encoded XDR arguments and require no loaded `ContractSpec`. They replicate the full behavior of the Map-based overloads (build, simulate, read/write auto-detection, signer-required-for-write check, auto submit) minus the spec-driven argument conversion and method-name validation. These are the entry points for generated contract bindings.
- `ContractClient.forContractWithoutSpec(contractId, rpcUrl, network)`: a non-suspend factory that constructs a client without the network spec-load round-trip. The Map-based overloads and spec-backed helpers remain unavailable on such a client (they throw `IllegalStateException`); use the positional overloads instead.

### Contract-binding fixtures and tests

Generated bindings are checked in with unit and testnet integration tests verifying that the generated code compiles and runs correctly: binding clients for the hello, auth, atomic-swap, and token demo contracts, exercised alongside their Map-based variants in the SorobanClient integration test, plus two purpose-built fixtures:

- `BindingsSpecTestContract`, generated from the generator repository's reference contract, covers the entire contract-spec type surface.
- `OptionShapesContract` covers option values in nested positions and a Kotlin soft-keyword parameter name; its contract source lives in Soneso/bindings-test-contracts.

## Changed

- The `com.ionspin.kotlin:bignum` dependency is now exposed via `api` (was `implementation`) in `commonMain`, because the SDK's public surface and generated bindings return `com.ionspin.kotlin.bignum.integer.BigInteger`.
- The new optional parameters change the JVM binary signatures of `SorobanServer.simulateTransaction`, `SorobanServer.prepareTransaction`, and the `SimulateTransactionRequest` and `ClientOptions` constructors.
- Compatibility matrices regenerated; the Soroban RPC baseline moved to v27.1.1.
- Bumped pinned GitHub Actions via Dependabot: `actions/checkout` to v7.0.0, `actions/setup-java` to v5.4.0, and `codecov/codecov-action` to v7.0.0.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.9.0`
- No source-breaking changes. Callers using named arguments are source-compatible and only need a recompile. Precompiled JVM consumers and positional calls that pass arguments after a new optional parameter on `simulateTransaction`, `prepareTransaction`, `SimulateTransactionRequest`, or `ClientOptions` need updating.

## References

- Changelog: [CHANGELOG.md](../CHANGELOG.md)
- Advanced usage (Soroban auth, opting into V2): [docs/advanced.md](../docs/advanced.md)
