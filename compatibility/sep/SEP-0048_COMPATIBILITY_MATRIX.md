# SEP-0048 (Contract Interface Specification) Compatibility Matrix

**Generated:** 2026-04-28 02:38:15

**SEP Version:** 1.1.0  
**SEP Status:** Active  
**SDK Version:** 1.5.1  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0048.md

## SEP Summary

Defines the binary contract interface specification stored in the `contractspecv0` Wasm custom section, covering function signatures, user-defined types, and the full XDR type system.

## Overall Coverage

**Total Coverage:** 100.0% (31/31 fields)

- ✅ **Implemented:** 31/31
- ❌ **Not Implemented:** 0/31

**Required Fields:** 100.0% (31/31)

**Optional Fields:** 100% (0/0)

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/AssembledTransaction.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/ClientOptions.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/ContractClient.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/ContractSpec.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/NativeUnionVal.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/SorobanContractParser.kt`

### Key Classes

- **`AssembledTransaction`** - Methods: simulate, sign, signAndSubmit, needsNonInvokerSigningBy, result, isReadCall, submit, getSimulationData, toEnvelopeXdrBase64, restoreFootprint, submitInternal
- **`SimulateHostFunctionResult`**
- **`ClientOptions`**
- **`ContractClient`** - Methods: getContractSpec, getMethodNames, funcArgsToXdrSCValues, nativeToXdrSCVal, funcResToNative, close, forContract, install, installInternal, deployFromWasmIdInternal, deployContractInternal, hexStringToByteArray
- **`ContractSpec`** - Methods: funcs, udtStructs, udtUnions, udtEnums, udtErrorEnums, events, getFunc, findEntry, funcArgsToXdrSCValues, funcResToNative, scValToNative, scValUdtToNative, enumToNative, structToNative, unionToNative, nativeToXdrSCVal, handleValueType, parseInteger, handleU128Type, handleI128Type, handleU256Type, handleI256Type, handleBytesType, handleAddressType, handleErrorType, handleValType, handleOptionType, handleResultType, handleVecType, handleMapType, handleTupleType, handleBytesNType, handleUDTType, handleStructType, handleUnionType, handleEnumType, isNumericString, hexToBytes
- **`NativeUnionVal`**
- **`VoidCase`**
- **`TupleCase`**
- **`SorobanContractParser`** - Methods: parseContractByteCode, parseEnvironmentMeta, parseContractSpec, parseMeta, extractBytesBetween, extractBytesToEnd, indexOfBytes
- **`SorobanContractParserException`**
- **`SorobanContractInfo`**

### Test Coverage

**Tests:** 286 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract/AssembledTransactionComprehensiveTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract/ClientOptionsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract/ContractClientAndHelpersTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract/ContractSpecExceptionTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract/ContractSpecTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract/ExponentialBackoffTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract/NativeUnionValTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract/SorobanContractParserTest.kt`

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Entry Types | 100.0% | 6/6 | 6 | 6 |
| Parsing Support | 100.0% | 4/4 | 4 | 4 |
| Type System - Compound | 100.0% | 7/7 | 7 | 7 |
| Type System - Primitive | 100.0% | 6/6 | 6 | 6 |
| WASM Section | 100.0% | 4/4 | 4 | 4 |
| XDR Support | 100.0% | 4/4 | 4 | 4 |

## Detailed Field Comparison

### Entry Types

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `function_specs` | ✓ | ✅ | `funcs` | Parse function specifications |
| `struct_specs` | ✓ | ✅ | `udtStructs` | Parse struct type specifications |
| `union_specs` | ✓ | ✅ | `udtUnions` | Parse union type specifications |
| `enum_specs` | ✓ | ✅ | `udtEnums` | Parse enum type specifications |
| `error_enum_specs` | ✓ | ✅ | `udtErrorEnums` | Parse error enum specifications |
| `event_specs` | ✓ | ✅ | `events` | Parse event specifications |

### Parsing Support

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `parse_contract_bytecode` | ✓ | ✅ | `parseContractByteCode` | Parse contract specifications from WASM |
| `extract_spec_entries` | ✓ | ✅ | `specEntries` | Extract and decode all specification entries |
| `parse_environment_meta` | ✓ | ✅ | `parseContractByteCode` | Parse environment metadata (interface version) |
| `parse_contract_meta` | ✓ | ✅ | `parseContractByteCode` | Parse contract metadata key-value pairs |

### Type System - Compound

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `option_type` | ✓ | ✅ | `SCSpecTypeOptionXdr` | Option<T> type |
| `result_type` | ✓ | ✅ | `SCSpecTypeResultXdr` | Result<T, E> type |
| `vector_type` | ✓ | ✅ | `SCSpecTypeVecXdr` | Vec<T> type |
| `map_type` | ✓ | ✅ | `SCSpecTypeMapXdr` | Map<K, V> type |
| `tuple_type` | ✓ | ✅ | `SCSpecTypeTupleXdr` | Tuple types |
| `bytes_n_type` | ✓ | ✅ | `SCSpecTypeBytesNXdr` | Fixed-length bytes type |
| `user_defined_type` | ✓ | ✅ | `SCSpecTypeUDTXdr` | User-defined types |

### Type System - Primitive

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `boolean_type` | ✓ | ✅ | `SCSpecTypeDefXdr` | Boolean type support |
| `void_type` | ✓ | ✅ | `SCSpecTypeDefXdr` | Void type support |
| `numeric_types` | ✓ | ✅ | `SCSpecTypeDefXdr` | Numeric types (u32, i32, u64, i64, u128, i128, u256, i256) |
| `timepoint_duration` | ✓ | ✅ | `SCSpecTypeDefXdr` | Timepoint and duration types |
| `bytes_string_symbol` | ✓ | ✅ | `SCSpecTypeDefXdr` | Bytes, string, symbol types |
| `address_type` | ✓ | ✅ | `SCSpecTypeDefXdr` | Address type support |

### WASM Section

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `contractspecv0_section` | ✓ | ✅ | `parseContractByteCode` | Contract specification Wasm custom section |
| `contractenvmetav0_section` | ✓ | ✅ | `parseContractByteCode` | Environment metadata Wasm section |
| `contractmetav0_section` | ✓ | ✅ | `parseContractByteCode` | Contract metadata Wasm section |
| `xdr_binary_encoding` | ✓ | ✅ | `SCSpecEntryXdr` | XDR binary encoded specification entries |

### XDR Support

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `decode_scspecentry` | ✓ | ✅ | `SCSpecEntryXdr` | Decode SCSpecEntry structures |
| `decode_scspectypedef` | ✓ | ✅ | `SCSpecTypeDefXdr` | Decode SCSpecTypeDef structures for type definitions |
| `decode_scenvmetaentry` | ✓ | ✅ | `SCEnvMetaEntryXdr` | Decode SCEnvMetaEntry structures |
| `decode_scmetaentry` | ✓ | ✅ | `SCMetaEntryXdr` | Decode SCMetaEntry structures |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0048!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0048](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0048.md)

**Implementation Package:** `com.soneso.stellar.sdk.contract`
