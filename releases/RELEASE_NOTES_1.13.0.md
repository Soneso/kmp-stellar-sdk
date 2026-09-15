# Release Notes - Version 1.13.0

## Overview

Version 1.13.0 adds `SCValXdr.toNative()`, which converts a smart-contract value to a native Kotlin value without requiring the contract spec, so contract invocation and simulation results can be consumed directly instead of parsing the raw XDR union by hand. The XDR definitions move to stellar-xdr `c40231c`, which carries one source-incompatible type change, listed under Compatibility. Generated contract bindings and the strkey address rules are now documented, and the versioned API docs publish reliably on release.

## Added

### Native ScVal conversion

`SCValXdr.toNative()` converts a smart contract value to native Kotlin values without requiring the contract spec, matching the JS and Python SDKs: `UInt`/`Int` and `ULong`/`Long` for the 32- and 64-bit arms, `BigInteger` for the 128- and 256-bit arms, strkey strings for addresses, `ByteArray` for bytes, lists for vecs, and insertion-ordered maps for maps. A value with no faithful native representation, such as an error or a map whose keys cannot serve as Kotlin map keys, comes back as the `SCValXdr` itself, so a caller detects the fallback with `is SCValXdr`; the method never throws. It complements the spec-driven `ContractSpec.scValToNative`, which stays the right tool when the spec is available. Contributed by @ngybnc (#67, #68).

Details: [spec-less conversion with toNative](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.13.0/docs/sdk-usage-examples.md#spec-less-conversion-with-tonative).

### Documentation

- [addresses.md](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.13.0/docs/addresses.md) documents the strkey encodings and the address types: `KeyPair`, `Address`, `MuxedAccount`, `ClaimableBalanceId`, and `SignerKey`.
- [Generated contract bindings](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.13.0/docs/sdk-usage-examples.md#contract-bindings) are documented in the usage examples and the bundled [agent skill](https://github.com/Soneso/kmp-stellar-sdk/tree/v1.13.0/skills).

## Changed

- XDR definitions regenerated from stellar/stellar-xdr commit `c40231c`. Contract spec names may now be up to 1024 bytes (the new `SC_SPEC_TYPE_NAME_LIMIT` constant): the `name` fields of `SCSpecTypeUDT`, `SCSpecUDTStructV0`, `SCSpecUDTUnionV0`, `SCSpecUDTEnumV0` and `SCSpecUDTErrorEnumV0` were capped at 60, and `SCSpecEventV0.name` is a plain XDR string where it was an `SCSymbol` capped at 32. The binary encoding and the XDR-JSON rendering of the affected types are unchanged.
- Publishing a release now reliably deploys the versioned API docs; after 1.12.0 they required a manual workflow dispatch.

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.13.0`

One source-incompatible change: `SCSpecEventV0Xdr.name` is `String`, previously `SCSymbolXdr`. Code that read `name.value` drops the `.value`, and code constructing the type passes a `String`; the constructor and `copy()` signatures changed, so precompiled JVM and Android consumers that use the type must rebuild. The wire form is unchanged.

## References

Full change list: [CHANGELOG](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.13.0/CHANGELOG.md)
