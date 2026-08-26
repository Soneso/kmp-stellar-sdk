# SEP-0051 (XDR-JSON) Compatibility Matrix

**Generated:** 2026-08-26 11:28:45

**SEP Version:** 2.0.1  
**SEP Status:** Draft  
**SDK Version:** 1.12.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0051.md

## SEP Summary

Defines XDR-JSON, a lossless bi-directional mapping between Stellar's XDR structures and JSON, so protocol data can be read and edited as text instead of base64.

## Overall Coverage

**Total Coverage:** 100.0% (37/37 fields)

- ✅ **Implemented:** 37/37
- ❌ **Not Implemented:** 0/37

**Required Fields:** 100.0% (34/34)

**Optional Fields:** 100.0% (3/3)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Address Types | 100.0% | 12/12 | 12 | 12 |
| Asset Code Types | 100.0% | 3/3 | 3 | 3 |
| Backward Compatibility | 100.0% | N/A | 2 | 2 |
| Integer Types | 100.0% | 4/4 | 4 | 4 |
| JSON Schema | 100.0% | N/A | 1 | 1 |
| XDR Data Types | 100.0% | 15/15 | 15 | 15 |

## Detailed Field Comparison

### Address Types

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sc_address` | ✓ | ✅ | `SCAddressXdr.toXdrJsonElement` | SCAddress maps to a G, C, M, B or L strkey depending on the arm |
| `account_id` | ✓ | ✅ | `AccountIDXdr.toXdrJsonElement` | AccountID maps to a G strkey |
| `contract_id` | ✓ | ✅ | `ContractIDXdr.toXdrJsonElement` | ContractID maps to a C strkey |
| `muxed_account` | ✓ | ✅ | `MuxedAccountXdr.toXdrJsonElement` | MuxedAccount maps to a G strkey for ed25519 and an M strkey for muxed ed25519 |
| `muxed_account_med25519` | ✓ | ✅ | `MuxedAccountMed25519Xdr.toXdrJsonElement` | MuxedAccountMed25519 maps to an M strkey |
| `muxed_ed25519_account` | ✓ | ✅ | `MuxedEd25519AccountXdr.toXdrJsonElement` | MuxedEd25519Account maps to an M strkey |
| `pool_id` | ✓ | ✅ | `PoolIDXdr.toXdrJsonElement` | PoolID maps to an L strkey |
| `claimable_balance_id` | ✓ | ✅ | `ClaimableBalanceIDXdr.toXdrJsonElement` | ClaimableBalanceID maps to a B strkey |
| `public_key` | ✓ | ✅ | `PublicKeyXdr.toXdrJsonElement` | PublicKey maps to a G strkey |
| `node_id` | ✓ | ✅ | `NodeIDXdr.toXdrJsonElement` | NodeID maps to a G strkey |
| `signer_key` | ✓ | ✅ | `SignerKeyXdr.toXdrJsonElement` | SignerKey maps to a G, T, X or P strkey depending on the arm |
| `signer_key_ed25519_signed_payload` | ✓ | ✅ | `SignerKeyEd25519SignedPayloadXdr.toXdrJsonElement` | SignerKeyEd25519SignedPayload maps to a P strkey |

### Asset Code Types

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `asset_code` | ✓ | ✅ | `AssetCodeXdr.toXdrJsonElement` | AssetCode maps according to AssetCode4 or AssetCode12 |
| `asset_code4` | ✓ | ✅ | `AssetCode4Xdr.toXdrJsonElement` | AssetCode4 drops all trailing zero bytes, then applies the string escaping |
| `asset_code12` | ✓ | ✅ | `AssetCode12Xdr.toXdrJsonElement` | AssetCode12 drops trailing zero bytes down to the 6th, keeping at least 5 characters |

### Backward Compatibility

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `hyper_accepts_json_number` |  | ✅ | `XdrJson.int64` | Deserializing a JSON number for Hyper is supported, for compatibility with XDR-JSON v1 |
| `unsigned_hyper_accepts_json_number` |  | ✅ | `XdrJson.uint64` | Deserializing a JSON number for Unsigned Hyper is supported, for compatibility with XDR-JSON v1 |

### Integer Types

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `uint128_parts` | ✓ | ✅ | `XdrJson.uint128ToDecimalString` | UInt128Parts maps to a base-10 JSON string of the 128-bit unsigned value |
| `int128_parts` | ✓ | ✅ | `XdrJson.int128ToDecimalString` | Int128Parts maps to a base-10 JSON string of the 128-bit signed value |
| `uint256_parts` | ✓ | ✅ | `XdrJson.uint256ToDecimalString` | UInt256Parts maps to a base-10 JSON string of the 256-bit unsigned value |
| `int256_parts` | ✓ | ✅ | `XdrJson.int256ToDecimalString` | Int256Parts maps to a base-10 JSON string of the 256-bit signed value |

### JSON Schema

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `schema_property` |  | ✅ | `XdrJson.stripSchema` | JSON objects allow, but do not require, a $schema property holding a JSON Schema URL |

### XDR Data Types

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `integer_32` | ✓ | ✅ | `XdrJson.int32` | Signed 32-bit integer maps to a JSON number |
| `unsigned_integer_32` | ✓ | ✅ | `XdrJson.uint32` | Unsigned 32-bit integer maps to a JSON number |
| `hyper_integer_64` | ✓ | ✅ | `XdrJson.int64` | Signed 64-bit integer maps to a base-10 JSON string |
| `unsigned_hyper_integer_64` | ✓ | ✅ | `XdrJson.uint64` | Unsigned 64-bit integer maps to a base-10 JSON string |
| `boolean` | ✓ | ✅ | `XdrJson.bool` | Boolean maps to a JSON boolean |
| `opaque_fixed_length` | ✓ | ✅ | `XdrJson.hex` | Fixed-length opaque data maps to a hexadecimal string |
| `opaque_variable_length` | ✓ | ✅ | `XdrJson.hex` | Variable-length opaque data maps to a hexadecimal string |
| `string` | ✓ | ✅ | `XdrJson.escapedString` | String maps to ASCII with NUL, tab, newline, return and backslash escaped, other bytes as \xNN |
| `array_fixed_length` | ✓ | ✅ | `XdrJson.array` | Fixed-length array maps to a JSON array of encoded elements |
| `array_variable_length` | ✓ | ✅ | `XdrJson.array` | Variable-length array maps to a JSON array of encoded elements |
| `enum` | ✓ | ✅ | `SCValTypeXdr.xdrJsonName` | Enum maps to its member name in snake_case with any shared prefix removed |
| `struct` | ✓ | ✅ | `TimeBoundsXdr.toXdrJsonElement` | Struct maps to a JSON object keyed by member names in snake_case |
| `discriminated_union` | ✓ | ✅ | `AssetXdr.toXdrJsonElement` | Union maps to the arm name for a void arm, otherwise to a single-key object |
| `void` | ✓ | ✅ | `AssetXdr.Void` | Void carries no JSON value of its own |
| `optional_data` | ✓ | ✅ | `XdrJson.optional` | Optional data maps to null when unset, otherwise to the encoded value |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0051!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0051](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0051.md)

**Implementation Package:** `com.soneso.stellar.sdk.xdr`
