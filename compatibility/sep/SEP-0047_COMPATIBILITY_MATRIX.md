# SEP-0047 (Contract Interface Discovery) Compatibility Matrix

**Generated:** 2026-05-29 19:51:53

**SEP Version:** 0.1.0  
**SEP Status:** Draft  
**SDK Version:** 1.6.1  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0047.md

## SEP Summary

Defines how Soroban contracts advertise which SEPs they implement via a `sep` key in the contract metadata section, enabling clients to discover supported protocols at runtime.

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
| Implementation Support | 100.0% | 3/3 | 3 | 3 |
| Meta Entry Format | 100.0% | 3/3 | 3 | 3 |
| SEP Declaration | 100.0% | 3/3 | 3 | 3 |

## Detailed Field Comparison

### Implementation Support

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `parse_supported_seps` | ✓ | ✅ | `supportedSeps` | Parse and extract list of supported SEPs |
| `expose_supported_seps` | ✓ | ✅ | `supportedSeps` | Expose supportedSeps property on contract info |
| `validate_sep_format` | ✓ | ✅ | `supportedSeps` | Validate SEP number format and filter invalid entries |

### Meta Entry Format

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sep_number_format` | ✓ | ✅ | `supportedSeps` | Parse SEP numbers (e.g., '41', '0041', 'SEP-41') |
| `whitespace_handling` | ✓ | ✅ | `supportedSeps` | Trim whitespace from SEP numbers |
| `empty_value_handling` | ✓ | ✅ | `supportedSeps` | Handle empty/missing 'sep' entries gracefully |

### SEP Declaration

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `sep_meta_key` | ✓ | ✅ | `supportedSeps` | Support for 'sep' meta entry key |
| `comma_separated_list` | ✓ | ✅ | `supportedSeps` | Parse comma-separated SEP numbers |
| `multiple_sep_entries` | ✓ | ✅ | `supportedSeps` | Support for multiple 'sep' meta entries |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0047!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0047](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0047.md)

**Implementation Package:** `com.soneso.stellar.sdk.contract`
