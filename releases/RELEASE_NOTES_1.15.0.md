# Release Notes - Version 1.15.0

## Overview

Version 1.15.0 adds muxed contract addresses (`W...`, CAP-0084) with the XDR definitions that carry them. It tightens the address rules of `ContractSpec` and of the Soroban authorization functions. It fixes three behaviors the host rejected: the signature expiration ledger of an entry that already carries a signature, the key order of map arguments, and the order of two assets with the same code. Diagnostic events are read at the transaction's top level. Eight changes are breaking for code that relied on the previous behavior; the migration guide lists every situation.

## Added

- Muxed contract addresses. `StrKey` gains `encodeMuxedContract`, `decodeMuxedContract` and `isValidMuxedContract`. `Address` parses `W...` as `AddressType.MUXED_CONTRACT`, converts to and from `SCAddressXdr.MuxedContract` and provides `fromMuxedContract(ByteArray)`. `MuxedContract` pairs a contract id with an optional `ULong` id and converts between the strkey, the `SCAddressXdr` arm and `Address`. Contract results and `SCValXdr.toNative` render the arm as its `W...` strkey (#99).
- The SEP-23 and SEP-29 compatibility matrices, with the guides `docs/sep/sep-23.md` and `docs/sep/sep-29.md` (#76).

## Changed

### Breaking: XDR definitions

The XDR definitions follow stellar-xdr commits `c2612cf` and `579a90b`. `StellarValueTypeXdr` gains `STELLAR_VALUE_SIGNED_MS` and `STELLAR_VALUE_EMPTY_TX_SET_MS`, `StellarValueExtXdr` the arms `SignedMsValue` and `ProposedMsValue`, and the typedef `TimePointMsXdr` is new (#82). `SCAddressTypeXdr` gains `SC_ADDRESS_TYPE_MUXED_CONTRACT`, `SCAddressXdr` the arm `MuxedContract` with `MuxedContractXdr`, and `ContractCostTypeXdr` nine ML-DSA members (#99). Existing members, arms, and XDR-JSON keys keep their values. A `when` over one of these types without an `else` branch needs the new branches; all other code compiles unchanged.

### Breaking: ContractSpec address parameters

`ContractSpec.nativeToXdrSCVal` and `funcArgsToXdrSCValues` take for an `Address` parameter a `G...` or `C...` strkey or an `Address` of type `ACCOUNT` or `CONTRACT`, and for a `MuxedAddress` parameter also an `M...` or `W...` strkey or an `Address` of type `MUXED_ACCOUNT` or `MUXED_CONTRACT`. A `B...` or `L...` address, a muxed address for an `Address` parameter, a malformed string, or a value of another type throws `ContractSpecException` naming the parameter type, the kinds it takes and the value. `null` converts to void and a prebuilt `SCValXdr` passes through (#100).

### Breaking: Soroban authorization addresses

`Auth.authorizeEntry` rejects an entry whose credential address or `forAddress` target is an `M...` or `W...` address, `Auth.authorizeInvocation` such a `publicKey`, and `Auth.attachDelegates` such a delegate, with `IllegalArgumentException`: "Muxed account (M...) and muxed contract (W...) addresses are not valid Soroban auth addresses" (`delegate addresses` for `attachDelegates`), followed by the address (#99).

### Breaking: AssetCode12 in XDR-JSON

`AssetCode12Xdr` rejects an XDR-JSON string that unescapes to fewer than five bytes, as the reference implementation does since 30.0.0. A short code carries escaped NUL bytes in the document, `"ABC\0\0"` for a three-character code (#104).

### Diagnostic events

`TransactionInfo.diagnosticEventsXdr` and `TransactionInfo.parseDiagnosticEventsXdr()` are the current API for a transaction list, because stellar-rpc supplies a transaction's diagnostic events at the top level; version 1.14.0 had deprecated them in favor of the nested `Events` members, which are deprecated instead (#80).

### Other changes

- The Horizon compatibility matrix targets Horizon v29.0.0 and the Soroban RPC matrix targets stellar-rpc v29.0.1; both keep their reported coverage.
- `AGENTS.md` and `CONTRIBUTING.md` document the build, test, generation, and contribution rules; CI checks that every path `AGENTS.md` names exists (#102).
- The integration-test README states the timeout rule and names the streaming coverage (#106).
- The user documentation and the agent skill describe the 1.15.0 behavior. They cover muxed contract addresses in every strkey list and section, the `ContractSpec` address rules, and the authorization guards. They state the expiration rule, the host key order of maps, the asset order, the `AssetCode12` input floor, and the top-level diagnostic events.

## Deprecated

- `Events.diagnosticEventsXdr` and `Events.parseDiagnosticEventsXdr()`. Read `GetTransactionResponse.diagnosticEventsXdr` for a single transaction or `TransactionInfo.diagnosticEventsXdr` for a transaction list, and parse them with the `parseDiagnosticEventsXdr()` of those classes. The deprecated members continue decoding the nested field in older response data (#80).

## Fixed

### Breaking: signature expiration ledger

`AssembledTransaction.signAuthEntries` keeps the signature expiration ledger of an entry that already carries a signature on any node, because every signature on the entry commits to that one value. Without `validUntilLedgerSequence` it reuses the stored value and fetches the latest ledger at most once per call, only for entries that need the default. An explicit value that differs from the stored one raises `IllegalArgumentException` naming both values, and the transaction keeps its auth entries. `Auth.authorizeEntry` applies the same check to `validUntilLedgerSeq`. A signature value of void or an empty vector counts as unsigned, which matches the placeholder js-stellar-sdk writes; `Auth.carriesSignature` exposes the check (#91, #110).

### Breaking: map key order

`Scv.toMap` emits map entries in the Soroban host's key order and raises `IllegalArgumentException` for two keys that are equal in that order. The overload `Scv.toMap(List<SCMapEntryXdr>)` keeps keys that convert to the same value apart. `ContractSpec` builds map and struct values through it and reports a duplicate key as `ContractSpecException`. The comparator is public as `scval.compareScValHostOrder`. Decoding keeps the wire order (#91).

### Breaking: asset order

`Asset.compareTo` orders credit assets with the same code by the issuer's raw 32-byte public key, as stellar-core does. The `LiquidityPool` order check, its decoding from XDR and the pool id follow this order (#91).

### Other fixes

- `GetTransactionResponse` reads the top-level `diagnosticEventsXdr` list that stellar-rpc sends and parses it with `parseDiagnosticEventsXdr()` (#80).
- The footers of the SEP compatibility matrices link to `docs/sep/README.md`.

## Migration

Migrating to 1.15.0: https://github.com/Soneso/kmp-stellar-sdk/blob/v1.15.0/docs/migration/1.15.0.md lists each situation with the symptom and the action.

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.15.0`

## References

Full change list: [CHANGELOG](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.15.0/CHANGELOG.md)
