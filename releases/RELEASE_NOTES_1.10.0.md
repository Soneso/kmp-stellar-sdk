# Release Notes - Version 1.10.0

## Overview

Version 1.10.0 hardens the smart-account implementation and the SDK's web target. Smart accounts gain constructor-time policy installation, a complete contract error-code catalog, and client-side validation of the contract limits; the map-key ordering used in auth payloads and policy encoding is corrected to match the Soroban host. Network failures on Kotlin/JS now surface as the documented typed exceptions across the smart-account, RPC, Horizon, and SEP boundaries, where the web HTTP engine previously reported them as a `kotlin.Error` that escaped `catch (Exception)`. The default smart-account indexer moves to the Mercury endpoints. All API additions are additive and there are no source-breaking changes; three behavior changes are listed under Compatibility.

## Added

### Constructor-time policies for smart accounts

- Policies can be installed on the default context rule at wallet creation: `OZSmartAccountConfig` gains `defaultPolicies`, and `createWallet` and `deployPendingCredential` gain an optional `policies` parameter that overrides the config default. The map takes the same policy-address-to-install-params shape `addContextRule` uses.
- The policy map is validated before the passkey ceremony starts, so an invalid configuration fails without leaving an orphaned credential. Constructor arguments are not part of the contract address preimage, so the derived wallet address is unchanged by the policies.
- Contract constraints apply at deploy time: constructor policies land on the default rule, so a spending-limit policy — which installs only on call-contract rules — cannot be installed at deploy time; a threshold must not exceed the signer count; and threshold 1 keeps a rule at 1-of-N as signers are added.

### Contract error-code catalog

- `ContractErrorCodes` gains the full set of named constants for the smart-account contract's own error enum (`SmartAccountError`, codes 3000-3016; previously only five were exposed). `decode` resolves any known raw code — smart account, WebAuthn verifier, simple threshold, weighted threshold, or spending limit — into the new `OZContractError` (defining contract enum plus variant name), and `decodeFromMessage` resolves the first known error code inside a transaction failure message, so a failed submission can be reported with the contract's own error name instead of a raw code.

### Client-side contract limits

- Context-rule names (20 UTF-8 bytes) and external-signer key data (256 bytes) are validated before submission, and the per-rule policy limit (5) is enforced for constructor policies as well, so violations fail fast instead of on-chain. The limits are exposed as `OZConstants.MAX_NAME_SIZE`, `MAX_EXTERNAL_KEY_SIZE`, and `MAX_POLICIES`.

## Changed

- The default smart-account indexer endpoints for testnet and mainnet now point at the Mercury smart-account indexer (previously the SDF ecosystem workers endpoints). Consumers that set a custom indexer URL are unaffected.
- Smart-account indexer requests no longer send client-identification headers. Custom headers force a CORS preflight in browsers, and indexer providers only allowlist standard headers, which blocked every request from the web target.
- Coroutine cancellation now propagates out of SDK network calls instead of being swallowed or converted into error results.
- Updated XDR definitions to stellar-xdr `df0c200` (declaration reordering only; generated types unchanged apart from doc comments).
- Migrated the demo web apps to Vite 8 (Rolldown bundler) and removed the unused terser dependency. Demo tooling only; not part of the published SDK artifact.

## Fixed

- **Smart-account map-key ordering**: map keys in auth payloads and policy install parameters are now sorted in the Soroban host's content-wise key order. The previous length-major sort over XDR-encoded bytes produced orderings the host rejects, which failed authentication and constructor materialization for affected key sets.
- **Kotlin/JS network errors**: on the web target the HTTP engine reports connectivity failures as `kotlin.Error`, which is a `Throwable` but not an `Exception`, so it escaped the SDK's error handling. Relayer calls broke their documented no-throw contract, indexer and RPC failures escaped unwrapped, and a transient glitch aborted transaction polling. RPC connectivity errors now surface as `ConnectionErrorException`, indexer errors as `IndexerException`, relayer errors in the returned `RelayerResponse`, and FriendBot funding failures as the documented `Exception`; polling retries them. The same treatment covers the Horizon and SEP layers: Horizon requests and submissions surface connectivity failures as `ConnectionErrorException`, the SEP-10 and SEP-45 web-auth calls throw their documented `ChallengeRequestException` / `TokenSubmissionException` (and their SEP-45 counterparts), SEP-31 configuration loading throws `Sep31ConfigurationException`, and the SSE stream reconnects instead of crashing. Behavior for `Exception`-typed failures on JVM and native is unchanged.
- **`SorobanServer.pollTransaction`**: when every polling attempt fails, the last failure is now thrown; previously the method crashed with a null-pointer error. A received response is still returned even if later attempts fail.

## Security

- XDR generator: xdrgen is consumed from the Soneso fork so concurrent-ruby resolves to >= 1.3.7 (GHSA-h8w8-99g7-qmvj and two further advisories); temporary until stellar/xdrgen#231 is merged.
- Demo web apps: updated vite past GHSA-fx2h-pf6j-xcff and GHSA-v6wh-96g9-6wx3; esbuild is no longer in the dependency tree (GHSA-g7r4-m6w7-qqqr). Development-only dependencies.

## Smart Account Demo App

- The approval-inbox bell is disabled while the coordination server is unreachable and recovers automatically; a coordination-server outage no longer freezes the app, and rapid repeated taps no longer corrupt the navigation stack.
- Editing a context rule offers signer selection based on the wallet's signer set (a rule edit is authorized by the default rule, not by the edited rule's signers); previously a single-signer rule under a multi-signer default rule submitted with the active signer only and failed on-chain.
- During submission the add-signer and add-policy forms stay visible but disabled instead of disappearing, so the view no longer jumps and the progress indication stays in view. Newly staged signers and policies, validation errors, and failure or partial-success results scroll into view.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.10.0`
- No source-breaking changes. Three behavior changes to be aware of:
  - The default smart-account indexer endpoints switch to Mercury for consumers that do not set a custom indexer URL.
  - Coroutine cancellation propagates out of SDK network calls instead of being converted into error results.
  - `OZPolicyManager.sortMapByKeyXdr` returns entries in the host's ScMap key order instead of XDR-byte order. Consumer-built install-param maps sorted with it pick up the corrected ordering automatically.
- The new optional parameters change the JVM binary signatures of `createWallet`, `deployPendingCredential`, and the `OZSmartAccountConfig` constructor and its generated `copy()`. Callers using named arguments are source-compatible and only need a recompile; precompiled JVM consumers and positional calls that pass arguments after a new parameter need updating.

## References

- Changelog: [CHANGELOG.md](../CHANGELOG.md)
- Smart accounts: [docs/smart-accounts/](../docs/smart-accounts/)
