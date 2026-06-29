# Release Notes - Version 1.8.1

## Overview

Version 1.8.1 adds headless smart-account connect to the OpenZeppelin kit and Soroban-RPC-visibility polling for wallet auto-funding, together with an agent-signer flow demo in the smart-account demo. It also fixes the agent-skill API-reference generator and shrinks the published Maven Central artifact by emptying the javadoc jar. All additions are additive and opt-in; there are no breaking changes.

## Added

### Headless smart-account connect

- `connectToContract(contractId)` on the OpenZeppelin smart-account kit connects to a deployed account by its contract address with no passkey. The resulting connection is usable through the multi-signer / external-signer pipeline. Single-passkey paths reject a headless connection.
- The public `isHeadless` discriminator exposes whether a connection was opened headless.
- `SmartAccountEvent.HeadlessConnected` is emitted when a headless connection is established.
- `WalletException.HeadlessConnection` (error code 2004) reports headless-connection failures.

### Wallet auto-funding

- Auto-funding now polls Soroban RPC for account visibility instead of waiting a fixed delay, so funded accounts and contracts are used as soon as they are observable.

### Agent-signer flow demo

- The smart-account demo gains an agent-signer flow: delegate-to-agent plus an approval inbox, backed by a Ktor coordination server and a Kotlin reference agent.

## Changed

- The published `-javadoc.jar` is now empty, keeping the Maven Central artifact size under the free threshold. The Dokka HTML documentation still deploys to GitHub Pages.

## Fixed

- The agent-skill API-reference generator now handles Kotlin nested block comments, so members declared after a KDoc that contains an inner block comment are no longer dropped from the generated reference.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.8.1`
- No breaking changes; upgrading from 1.8.0 requires no source changes.

## References

- Changelog: [CHANGELOG.md](../CHANGELOG.md)
- Smart accounts guide: [docs/smart-accounts/README.md](../docs/smart-accounts/README.md)
