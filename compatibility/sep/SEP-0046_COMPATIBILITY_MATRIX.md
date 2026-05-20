# SEP-0046 (Contract Meta) Compatibility Matrix

**Generated:** 2026-05-20 11:38:54

**SEP Version:** 1.0.0  
**SEP Status:** Active  
**SDK Version:** 1.6.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0046.md

## SEP Summary

Defines a standard Wasm custom section (`contractmetav0`) for embedding arbitrary key-value metadata into deployed Soroban contracts, using XDR-encoded SCMetaEntry pairs.

## Overall Coverage

**Total Coverage:** 100.0% (9/9 fields)

- ✅ **Implemented:** 9/9
- ❌ **Not Implemented:** 0/9

**Required Fields:** 100.0% (9/9)

**Optional Fields:** 100% (0/0)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Encoding Format | 100.0% | 3/3 | 3 | 3 |
| Implementation Support | 100.0% | 3/3 | 3 | 3 |
| Metadata Storage | 100.0% | 3/3 | 3 | 3 |

## Detailed Field Comparison

### Encoding Format

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `scmetaentry_xdr` | ✓ | ✅ | `SCMetaEntryXdr` | Uses SCMetaEntry XDR type for encoding |
| `binary_stream_encoding` | ✓ | ✅ | `parseMeta` | Encodes entries as binary stream |
| `key_value_pairs` | ✓ | ✅ | `metaEntries` | Stores metadata as key-value string pairs |

### Implementation Support

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `parse_contract_meta` | ✓ | ✅ | `parseContractByteCode` | Parse contract metadata from bytecode |
| `extract_meta_entries` | ✓ | ✅ | `metaEntries` | Extract meta entries as key-value pairs |
| `decode_scmetaentry` | ✓ | ✅ | `SCMetaEntryXdr` | Decode SCMetaEntry XDR structures |

### Metadata Storage

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `contractmetav0_section` | ✓ | ✅ | `parseMeta` | Support for contractmetav0 Wasm custom section |
| `multiple_entries_single_section` | ✓ | ✅ | `parseMeta` | Support for multiple entries in single section |
| `multiple_sections` | ✓ | ✅ | `parseMeta` | Support for multiple sections interpreted sequentially |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0046!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0046](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0046.md)

**Implementation Package:** `com.soneso.stellar.sdk.contract`
