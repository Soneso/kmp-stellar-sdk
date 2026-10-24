# Release Notes - Version 1.16.0

## Overview

Version 1.16.0 completes Protocol 30 support and closes [issue #114](https://github.com/Soneso/kmp-stellar-sdk/issues/114), "SDK Updates for Protocol 30 Compatibility". Version 1.15.0 shipped the Protocol 30 XDR: muxed contract addresses (CAP-0084), the ML-DSA cost types (CAP-0087), and millisecond close times (CAP-0088). This release adds the Horizon 30 fields of contract credited effects and checks contract addresses before any request where Protocol 30 accepts no muxed address. In addition, it fixes the defects of the Q4 2026 hardening round in `ContractSpec` integer inputs (#124), stellar.toml parsing and fetching (#126, #130, #120), and the rebuild and polling of `AssembledTransaction` (#116, #118).

## Added

- `ContractCreditedEffectResponse` gains `contractMuxed` and `contractMuxedId` for a transfer or mint to a muxed contract address, as Horizon 30 reports it. They hold the `W...` strkey and the muxed id as a decimal string, and both are null for other credits (#113).

## Changed

### Contract addresses

These calls check before any request that the address is a contract (`C...`) address. Other input throws `IllegalArgumentException`. The message names the address's role and, for a recognized address, its kind. For a muxed contract the message ends with "use its base contract (C...) address", the address to pass. In 1.15.0 the calls apart from `getSACBalance` did not check the kind of address.

- `InvokeHostFunctionOperation.invokeContractFunction` and `ContractClient.forContractWithoutSpec`: "The contract to invoke must be a contract (C...) address" (#115).
- `ContractClient.forContract`, `SorobanServer.loadContractCodeForContractId`, and `SorobanServer.loadContractInfoForContractId`: "The contract to load must be a contract (C...) address" (#115).
- `SorobanServer.getContractData`: "The contract data owner must be a contract (C...) address". A key that holds a muxed address, directly or at any depth within vectors and maps, fails with "Contract data keys cannot contain muxed account (M...) or muxed contract (W...) addresses" (#115).
- `SorobanServer.getSACBalance`: "The balance holder must be a contract (C...) address". Its 1.15.0 message named no kind (#115).

### Other changes

- The documentation and the agent skill describe the muxed contract strkey of SEP-23 revision 1.4.0, the integer inputs of `ContractSpec`, the stellar.toml parser and fetch rules, and both outcomes of a timed-out `AssembledTransaction.submit`. The advanced guide's examples compile as printed.
- The 22 compatibility matrices are regenerated for 1.16.0; the Horizon and RPC matrices target Horizon v30.0.0 and stellar-rpc v30.0.0 at unchanged coverage. The SEP-23 matrix assesses revision 1.4.0 with the muxed contract key type and its seven test vectors (#112).
- The agent skill is version 1.6.0, and its API reference lists the two new effect properties.
- The CI workflows pin `actions/setup-java` v6.1.0 and `ruby/setup-ruby` v1.335.0.

## Fixed

### stellar.toml parsing

- `StellarToml.parse` reads TOML 1.0 strings: it decodes escapes in basic strings, keeps the content of literal and multi-line strings, and ignores comments outside strings. It reads CRLF and CR line endings as LF and nested multi-line arrays in full. In 1.15.0 these values kept their TOML syntax and such arrays ended early (#126).
- A value or array element that is a single `"` character reads as the text `"`. In 1.15.0 it threw an index exception (#130).

### ContractSpec integer arguments

- `ContractSpec` takes a `BigInteger`, a full-range `ULong`, and a decimal string beyond the `Long` range for u64, timepoint, duration, and the 128-bit and 256-bit types. A value outside the type's range throws `ContractSpecException`. In 1.15.0 the native conversions of these types passed through `Long`, even for the wider types (#124).
- A `ULong` greater than `Long.MAX_VALUE` for a u32, i32, or i64 parameter throws `ContractSpecException` naming the unsigned value and the type. In 1.15.0 the value wrapped to a negative `Long` first (#124).
- A whole-number `Double` or `Float` converts exactly within the type's range; a fractional, NaN, or infinite value throws `ContractSpecException`. In 1.15.0 the SDK cut the fraction, read NaN as 0, and clamped values beyond the `Long` range (#124).

### AssembledTransaction

- `AssembledTransaction.signAuthEntries` rebuilds the transaction with the fee, memo, preconditions, and Soroban data of the simulated one. In 1.15.0 the rebuild dropped the memo and added the resource fee a second time (#116).
- `AssembledTransaction.submit` keeps polling when a `getTransaction` request fails after the send. At the end of `ClientOptions.submitTimeout` it throws `TransactionStillPendingException` if a status request returned a response, and otherwise the last request failure. In 1.15.0 the first failed status request ended the call (#118).

### stellar.toml fetching

- `StellarToml.fromDomain` throws `IllegalArgumentException` for a domain that is not a host with an optional port, before any request. In 1.15.0 the domain went into the request URL unchecked (#120).
- `StellarToml.fromDomain` and `StellarToml.currencyFromUrl` give the client they create a connect timeout of 10 seconds and a request timeout of 30 seconds. In 1.15.0 the created client set no timeout (#120).
- Both throw `IllegalStateException` for a file larger than 102,400 bytes, the SEP-1 limit of 100 KB. In 1.15.0 they read a file of any size (#120).

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.16.0`
- Protocol 30 XDR from stellar-xdr commit `579a90b` on the `curr` branch, which matches the v30.0 tag (`4f524bb`) in every production definition; only a test-only struct behind `TEST_FEATURE` differs

## References

- Full change list: [CHANGELOG](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.16.0/CHANGELOG.md)
- Protocol 30: [issue #114](https://github.com/Soneso/kmp-stellar-sdk/issues/114)
