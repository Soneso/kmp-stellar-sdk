package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.*
import com.soneso.stellar.sdk.contract.ContractSpec
import com.soneso.stellar.sdk.contract.SorobanContractInfo
import com.soneso.stellar.sdk.contract.SorobanContractParser
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for Soroban contract parser functionality.
 *
 * These tests verify the SDK's ability to parse Soroban contract byte code (WASM),
 * extract contract metadata, spec entries, and validate contract structure.
 *
 * The tests use a real token contract WASM file and validate:
 * - Contract metadata parsing (environment version, meta entries)
 * - Spec entry extraction (functions, structs, unions, enums, events)
 * - ContractSpec utility methods (funcs(), udtStructs(), events(), etc.)
 * - SorobanContractInfo convenience properties (supportedSeps, funcs, events, etc.)
 * - Detailed validation of token contract structure
 *
 * **Test Network**: Local WASM file parsing (no network required)
 *
 * ## Running Tests
 *
 * These tests do not require network access:
 * ```bash
 * ./gradlew :stellar-sdk:jvmTest --tests "SorobanParserTest"
 * ```
 *
 * ## Ported From
 *
 * These tests are ported from the Flutter Stellar SDK's soroban_test_parser.dart:
 * - test token contract parsing
 * - test SorobanContractInfo supportedSeps parsing
 * - test token contract validation
 * - test contract spec methods
 *
 * **Reference**: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_test_parser.dart`
 */
class SorobanParserTest {

    /**
     * Helper method to get type information from a spec type definition.
     *
     * This method recursively processes SCSpecTypeDef unions to produce
     * human-readable type descriptions for contract functions, structs, and events.
     *
     * Ported from Flutter SDK's _getSpecTypeInfo() helper method.
     */
    private fun getSpecTypeInfo(specType: SCSpecTypeDefXdr): String {
        return when (specType.discriminant) {
            SCSpecTypeXdr.SC_SPEC_TYPE_VAL -> "val"
            SCSpecTypeXdr.SC_SPEC_TYPE_BOOL -> "bool"
            SCSpecTypeXdr.SC_SPEC_TYPE_VOID -> "void"
            SCSpecTypeXdr.SC_SPEC_TYPE_ERROR -> "error"
            SCSpecTypeXdr.SC_SPEC_TYPE_U32 -> "u32"
            SCSpecTypeXdr.SC_SPEC_TYPE_I32 -> "i32"
            SCSpecTypeXdr.SC_SPEC_TYPE_U64 -> "u64"
            SCSpecTypeXdr.SC_SPEC_TYPE_I64 -> "i64"
            SCSpecTypeXdr.SC_SPEC_TYPE_TIMEPOINT -> "timepoint"
            SCSpecTypeXdr.SC_SPEC_TYPE_DURATION -> "duration"
            SCSpecTypeXdr.SC_SPEC_TYPE_U128 -> "u128"
            SCSpecTypeXdr.SC_SPEC_TYPE_I128 -> "i128"
            SCSpecTypeXdr.SC_SPEC_TYPE_U256 -> "u256"
            SCSpecTypeXdr.SC_SPEC_TYPE_I256 -> "i256"
            SCSpecTypeXdr.SC_SPEC_TYPE_BYTES -> "bytes"
            SCSpecTypeXdr.SC_SPEC_TYPE_STRING -> "string"
            SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL -> "symbol"
            SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS -> "address"
            SCSpecTypeXdr.SC_SPEC_TYPE_MUXED_ADDRESS -> "muxed address"
            SCSpecTypeXdr.SC_SPEC_TYPE_OPTION -> {
                val option = (specType as SCSpecTypeDefXdr.Option).value
                val valueType = getSpecTypeInfo(option.valueType)
                "option (value type: $valueType)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_RESULT -> {
                val result = (specType as SCSpecTypeDefXdr.Result).value
                val okType = getSpecTypeInfo(result.okType)
                val errorType = getSpecTypeInfo(result.errorType)
                "result (ok type: $okType , error type: $errorType)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_VEC -> {
                val vec = (specType as SCSpecTypeDefXdr.Vec).value
                val elementType = getSpecTypeInfo(vec.elementType)
                "vec (element type: $elementType)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_MAP -> {
                val map = (specType as SCSpecTypeDefXdr.Map).value
                val keyType = getSpecTypeInfo(map.keyType)
                val valueType = getSpecTypeInfo(map.valueType)
                "map (key type: $keyType , value type: $valueType)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_TUPLE -> {
                val tuple = (specType as SCSpecTypeDefXdr.Tuple).value
                val valueTypesStr = tuple.valueTypes.joinToString(",", "[", "]") { getSpecTypeInfo(it) }
                "tuple (value types: $valueTypesStr)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_BYTES_N -> {
                val bytesN = (specType as SCSpecTypeDefXdr.BytesN).value
                "bytesN (n: ${bytesN.n.value})"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_UDT -> {
                val udt = (specType as SCSpecTypeDefXdr.Udt).value
                "udt (name: ${udt.name})"
            }
        }
    }

    /**
     * Helper method to format and print a contract function specification.
     *
     * Ported from Flutter SDK's _printFunction() helper method.
     */
    private fun printFunction(function: SCSpecFunctionV0Xdr): String {
        val builder = StringBuilder()
        builder.append("Function: ${function.name.value}\n")

        function.inputs.forEachIndexed { index, input ->
            builder.append("input[$index] name: ${input.name}\n")
            builder.append("input[$index] type: ${getSpecTypeInfo(input.type)}\n")
            if (input.doc.isNotEmpty()) {
                builder.append("input[$index] doc: ${input.doc}\n")
            }
        }

        function.outputs.forEachIndexed { index, output ->
            builder.append("output[$index] type: ${getSpecTypeInfo(output)}\n")
        }

        if (function.doc.isNotEmpty()) {
            builder.append("doc : ${function.doc}\n")
        }

        return builder.toString().trimEnd()
    }

    /**
     * Helper method to format and print a UDT struct specification.
     *
     * Ported from Flutter SDK's _printUdtStruct() helper method.
     */
    private fun printUdtStruct(udtStruct: SCSpecUDTStructV0Xdr): String {
        val builder = StringBuilder()
        builder.append("UDT Struct: ${udtStruct.name}\n")

        if (udtStruct.lib.isNotEmpty()) {
            builder.append("lib : ${udtStruct.lib}\n")
        }

        udtStruct.fields.forEachIndexed { index, field ->
            builder.append("field[$index] name: ${field.name}\n")
            builder.append("field[$index] type: ${getSpecTypeInfo(field.type)}\n")
            if (field.doc.isNotEmpty()) {
                builder.append("field[$index] doc: ${field.doc}\n")
            }
        }

        if (udtStruct.doc.isNotEmpty()) {
            builder.append("doc : ${udtStruct.doc}\n")
        }

        return builder.toString().trimEnd()
    }

    /**
     * Helper method to format and print a UDT union specification.
     *
     * Ported from Flutter SDK's _printUdtUnion() helper method.
     */
    private fun printUdtUnion(udtUnion: SCSpecUDTUnionV0Xdr): String {
        val builder = StringBuilder()
        builder.append("UDT Union: ${udtUnion.name}\n")

        if (udtUnion.lib.isNotEmpty()) {
            builder.append("lib : ${udtUnion.lib}\n")
        }

        udtUnion.cases.forEachIndexed { index, uCase ->
            when (uCase) {
                is SCSpecUDTUnionCaseV0Xdr.VoidCase -> {
                    builder.append("case[$index] is voidV0\n")
                    builder.append("case[$index] name: ${uCase.value.name}\n")
                    if (uCase.value.doc.isNotEmpty()) {
                        builder.append("case[$index] doc: ${uCase.value.doc}\n")
                    }
                }
                is SCSpecUDTUnionCaseV0Xdr.TupleCase -> {
                    builder.append("case[$index] is tupleV0\n")
                    builder.append("case[$index] name: ${uCase.value.name}\n")
                    val valueTypesStr = uCase.value.type.joinToString(",", "[", "]") { getSpecTypeInfo(it) }
                    builder.append("case[$index] types: $valueTypesStr\n")
                    if (uCase.value.doc.isNotEmpty()) {
                        builder.append("case[$index] doc: ${uCase.value.doc}\n")
                    }
                }
            }
        }

        if (udtUnion.doc.isNotEmpty()) {
            builder.append("doc : ${udtUnion.doc}\n")
        }

        return builder.toString().trimEnd()
    }

    /**
     * Helper method to format and print a UDT enum specification.
     *
     * Ported from Flutter SDK's _printUdtEnum() helper method.
     */
    private fun printUdtEnum(udtEnum: SCSpecUDTEnumV0Xdr): String {
        val builder = StringBuilder()
        builder.append("UDT Enum : ${udtEnum.name}\n")

        if (udtEnum.lib.isNotEmpty()) {
            builder.append("lib : ${udtEnum.lib}\n")
        }

        udtEnum.cases.forEachIndexed { index, uCase ->
            builder.append("case[$index] name: ${uCase.name}\n")
            builder.append("case[$index] value: ${uCase.value}\n")
            if (uCase.doc.isNotEmpty()) {
                builder.append("case[$index] doc: ${uCase.doc}\n")
            }
        }

        if (udtEnum.doc.isNotEmpty()) {
            builder.append("doc : ${udtEnum.doc}\n")
        }

        return builder.toString().trimEnd()
    }

    /**
     * Helper method to format and print a UDT error enum specification.
     *
     * Ported from Flutter SDK's _printUdtErrorEnum() helper method.
     */
    private fun printUdtErrorEnum(udtErrorEnum: SCSpecUDTErrorEnumV0Xdr): String {
        val builder = StringBuilder()
        builder.append("UDT Error Enum : ${udtErrorEnum.name}\n")

        if (udtErrorEnum.lib.isNotEmpty()) {
            builder.append("lib : ${udtErrorEnum.lib}\n")
        }

        udtErrorEnum.cases.forEachIndexed { index, uCase ->
            builder.append("case[$index] name: ${uCase.name}\n")
            builder.append("case[$index] value: ${uCase.value}\n")
            if (uCase.doc.isNotEmpty()) {
                builder.append("case[$index] doc: ${uCase.doc}\n")
            }
        }

        if (udtErrorEnum.doc.isNotEmpty()) {
            builder.append("doc : ${udtErrorEnum.doc}\n")
        }

        return builder.toString().trimEnd()
    }

    /**
     * Helper method to format and print an event specification.
     *
     * Ported from Flutter SDK's _printEvent() helper method.
     */
    private fun printEvent(event: SCSpecEventV0Xdr): String {
        val builder = StringBuilder()
        builder.append("Event: ${event.name.value}\n")
        builder.append("lib: ${event.lib}\n")

        event.prefixTopics.forEachIndexed { index, prefixTopic ->
            builder.append("prefixTopic[$index] name: ${prefixTopic.value}\n")
        }

        event.params.forEachIndexed { index, param ->
            builder.append("param[$index] name: ${param.name}\n")
            if (param.doc.isNotEmpty()) {
                builder.append("param[$index] doc : ${param.doc}\n")
            }
            builder.append("param[$index] type: ${getSpecTypeInfo(param.type)}\n")

            val locationStr = when (param.location) {
                SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_DATA -> "data"
                SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_TOPIC_LIST -> "topic list"
            }
            builder.append("param[$index] location: $locationStr\n")
        }

        val dataFormatStr = when (event.dataFormat) {
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_SINGLE_VALUE -> "single value"
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_MAP -> "map"
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_VEC -> "vec"
        }
        builder.append("data format: $dataFormatStr\n")

        if (event.doc.isNotEmpty()) {
            builder.append("doc : ${event.doc}\n")
        }

        return builder.toString().trimEnd()
    }

    /**
     * Tests basic contract parsing functionality.
     *
     * This test validates:
     * 1. Parsing contract byte code from WASM file
     * 2. Extracting environment interface version
     * 3. Extracting meta entries (contract metadata)
     * 4. Extracting spec entries (functions, structs, unions, events)
     * 5. Printing contract information using helper methods
     *
     * The test demonstrates complete contract parsing workflow and validates
     * that all major components can be extracted and displayed.
     *
     * **Duration**: <1 second (file I/O only)
     *
     * **Reference**: Ported from Flutter SDK's test token contract parsing
     * (soroban_test_parser.dart lines 234-283)
     */
    @Test
    fun testTokenContractParsing() = runTest(timeout = 30.seconds) {
        // Given: Load token contract WASM
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertTrue(byteCode.isNotEmpty(), "Contract byte code should not be empty")

        // When: Parse contract
        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Then: Validate basic structure
        assertEquals(25, contractInfo.specEntries.size, "Contract should have 25 spec entries")
        assertEquals(2, contractInfo.metaEntries.size, "Contract should have 2 meta entries")

        // Print contract info (validates all formatters work)
        println("--------------------------------")
        println("Env Meta:")
        println("")
        println("Interface version: ${contractInfo.envInterfaceVersion}")
        assertTrue(contractInfo.envInterfaceVersion > 0u, "Interface version should be greater than 0")
        println("--------------------------------")
        println("Contract Meta:")
        println("")
        contractInfo.metaEntries.forEach { (key, value) ->
            println("$key: $value")
        }
        println("--------------------------------")
        println("Contract Spec:")
        println("")

        var index = 0
        contractInfo.specEntries.forEach { specEntry ->
            when (specEntry.discriminant) {
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_FUNCTION_V0 -> {
                    val function = when (specEntry) {
                        is SCSpecEntryXdr.FunctionV0 -> specEntry.value
                        else -> null
                    }
                    function?.let { println(printFunction(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_STRUCT_V0 -> {
                    val udtStruct = when (specEntry) {
                        is SCSpecEntryXdr.UdtStructV0 -> specEntry.value
                        else -> null
                    }
                    udtStruct?.let { println(printUdtStruct(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_UNION_V0 -> {
                    val udtUnion = when (specEntry) {
                        is SCSpecEntryXdr.UdtUnionV0 -> specEntry.value
                        else -> null
                    }
                    udtUnion?.let { println(printUdtUnion(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ENUM_V0 -> {
                    val udtEnum = when (specEntry) {
                        is SCSpecEntryXdr.UdtEnumV0 -> specEntry.value
                        else -> null
                    }
                    udtEnum?.let { println(printUdtEnum(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ERROR_ENUM_V0 -> {
                    val udtErrorEnum = when (specEntry) {
                        is SCSpecEntryXdr.UdtErrorEnumV0 -> specEntry.value
                        else -> null
                    }
                    udtErrorEnum?.let { println(printUdtErrorEnum(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_EVENT_V0 -> {
                    val event = when (specEntry) {
                        is SCSpecEntryXdr.EventV0 -> specEntry.value
                        else -> null
                    }
                    event?.let { println(printEvent(it)) }
                }
            }
            println("")
            index++
        }
        println("--------------------------------")
    }

    /**
     * Tests SorobanContractInfo.supportedSeps property parsing.
     *
     * This test validates the parsing of SEP (Stellar Ecosystem Proposal) numbers
     * from contract metadata. The test covers:
     * 1. Multiple SEPs (comma-separated)
     * 2. Single SEP
     * 3. No SEP meta entry
     * 4. Empty SEP value
     * 5. SEPs with extra spaces
     * 6. Trailing/leading commas
     * 7. Duplicate SEPs (should be deduplicated)
     *
     * **Duration**: <1 second (in-memory parsing only)
     *
     * **Reference**: Ported from Flutter SDK's test SorobanContractInfo supportedSeps parsing
     * (soroban_test_parser.dart lines 285-323)
     */
    @Test
    fun testSupportedSepsParsing() = runTest(timeout = 30.seconds) {
        // Test with multiple SEPs
        val info1 = SorobanContractInfo(
            envInterfaceVersion = 1u,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "1, 10, 24", "other" to "value")
        )
        assertEquals(listOf("1", "10", "24"), info1.supportedSeps, "Should parse multiple SEPs")

        // Test with single SEP
        val info2 = SorobanContractInfo(
            envInterfaceVersion = 1u,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "47")
        )
        assertEquals(listOf("47"), info2.supportedSeps, "Should parse single SEP")

        // Test with no SEP meta entry
        val info3 = SorobanContractInfo(
            envInterfaceVersion = 1u,
            specEntries = emptyList(),
            metaEntries = mapOf("other" to "value")
        )
        assertTrue(info3.supportedSeps.isEmpty(), "Should return empty list when no sep meta entry")

        // Test with empty SEP value
        val info4 = SorobanContractInfo(
            envInterfaceVersion = 1u,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "")
        )
        assertTrue(info4.supportedSeps.isEmpty(), "Should return empty list for empty sep value")

        // Test with SEPs containing extra spaces
        val info5 = SorobanContractInfo(
            envInterfaceVersion = 1u,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "  1  ,  2  ,  3  ")
        )
        assertEquals(listOf("1", "2", "3"), info5.supportedSeps, "Should trim spaces from SEPs")

        // Test with trailing/leading commas
        val info6 = SorobanContractInfo(
            envInterfaceVersion = 1u,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to ",1,2,")
        )
        assertEquals(listOf("1", "2"), info6.supportedSeps, "Should handle trailing/leading commas")

        // Test with duplicate SEPs (should be deduplicated)
        val info7 = SorobanContractInfo(
            envInterfaceVersion = 1u,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "1, 10, 1, 24, 10")
        )
        assertEquals(listOf("1", "10", "24"), info7.supportedSeps, "Should deduplicate SEPs")
    }

    /**
     * Tests comprehensive token contract validation.
     *
     * This test performs detailed validation of the parsed token contract structure:
     * 1. Environment interface version validation
     * 2. Meta entries validation (rsver, rssdkver keys)
     * 3. Spec entries count validation (25 total)
     * 4. Functions count and name validation (13 functions)
     * 5. UDT structs validation (3 structs with field validation)
     * 6. UDT unions validation (1 union with case validation)
     * 7. UDT enums validation (0 enums)
     * 8. UDT error enums validation (0 error enums)
     * 9. Events validation (8 events with structure validation)
     * 10. Function signature validation (balance, mint functions)
     *
     * This is the most comprehensive validation test, ensuring the parser
     * correctly extracts all contract metadata and spec entries.
     *
     * **Duration**: <1 second (file I/O + parsing)
     *
     * **Reference**: Ported from Flutter SDK's test token contract validation
     * (soroban_test_parser.dart lines 325-542)
     */
    @Test
    fun testTokenContractValidation() = runTest(timeout = 30.seconds) {
        // Given: Load and parse token contract
        val contractCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        val contractInfo = SorobanContractParser.parseContractByteCode(contractCode)

        // Validate environment interface version
        assertTrue(
            contractInfo.envInterfaceVersion > 0u,
            "Environment interface version should be greater than 0"
        )

        // Validate meta entries
        assertEquals(2, contractInfo.metaEntries.size, "Contract should have exactly 2 meta entries")
        assertTrue(contractInfo.metaEntries.containsKey("rsver"), "Meta entries should contain rsver key")
        assertTrue(contractInfo.metaEntries.containsKey("rssdkver"), "Meta entries should contain rssdkver key")

        // Validate total spec entries count
        assertEquals(25, contractInfo.specEntries.size, "Contract should have exactly 25 spec entries")

        // Validate functions count and specific function names
        assertEquals(13, contractInfo.funcs.size, "Contract should have exactly 13 functions")

        val functionNames = contractInfo.funcs.map { it.name.value }

        // Validate critical token functions exist
        assertTrue(functionNames.contains("__constructor"), "Contract should have __constructor function")
        assertTrue(functionNames.contains("mint"), "Contract should have mint function")
        assertTrue(functionNames.contains("burn"), "Contract should have burn function")
        assertTrue(functionNames.contains("transfer"), "Contract should have transfer function")
        assertTrue(functionNames.contains("transfer_from"), "Contract should have transfer_from function")
        assertTrue(functionNames.contains("balance"), "Contract should have balance function")
        assertTrue(functionNames.contains("approve"), "Contract should have approve function")
        assertTrue(functionNames.contains("allowance"), "Contract should have allowance function")
        assertTrue(functionNames.contains("decimals"), "Contract should have decimals function")
        assertTrue(functionNames.contains("name"), "Contract should have name function")
        assertTrue(functionNames.contains("symbol"), "Contract should have symbol function")
        assertTrue(functionNames.contains("set_admin"), "Contract should have set_admin function")
        assertTrue(functionNames.contains("burn_from"), "Contract should have burn_from function")

        // Validate UDT structs count and specific struct names
        assertEquals(3, contractInfo.udtStructs.size, "Contract should have exactly 3 UDT structs")

        val structNames = contractInfo.udtStructs.map { it.name }
        assertTrue(structNames.contains("AllowanceDataKey"), "Contract should have AllowanceDataKey struct")
        assertTrue(structNames.contains("AllowanceValue"), "Contract should have AllowanceValue struct")
        assertTrue(structNames.contains("TokenMetadata"), "Contract should have TokenMetadata struct")

        // Validate AllowanceDataKey struct fields
        val allowanceDataKey = contractInfo.udtStructs.firstOrNull { it.name == "AllowanceDataKey" }
        assertNotNull(allowanceDataKey, "AllowanceDataKey struct should be found")
        assertEquals(2, allowanceDataKey.fields.size, "AllowanceDataKey should have 2 fields")
        assertEquals("from", allowanceDataKey.fields[0].name, "First field of AllowanceDataKey should be named 'from'")
        assertEquals("spender", allowanceDataKey.fields[1].name, "Second field of AllowanceDataKey should be named 'spender'")

        // Validate TokenMetadata struct fields
        val tokenMetadata = contractInfo.udtStructs.firstOrNull { it.name == "TokenMetadata" }
        assertNotNull(tokenMetadata, "TokenMetadata struct should be found")
        assertEquals(3, tokenMetadata.fields.size, "TokenMetadata should have 3 fields")
        assertEquals("decimal", tokenMetadata.fields[0].name, "First field of TokenMetadata should be named 'decimal'")
        assertEquals("name", tokenMetadata.fields[1].name, "Second field of TokenMetadata should be named 'name'")
        assertEquals("symbol", tokenMetadata.fields[2].name, "Third field of TokenMetadata should be named 'symbol'")

        // Validate UDT unions count and specific union names
        assertEquals(1, contractInfo.udtUnions.size, "Contract should have exactly 1 UDT union")

        val unionNames = contractInfo.udtUnions.map { it.name }
        assertTrue(unionNames.contains("DataKey"), "Contract should have DataKey union")

        // Validate DataKey union cases
        val dataKey = contractInfo.udtUnions[0]
        assertEquals("DataKey", dataKey.name, "Union should be named DataKey")
        assertEquals(4, dataKey.cases.size, "DataKey union should have 4 cases")

        // Validate UDT enums count (should be zero for this contract)
        assertEquals(0, contractInfo.udtEnums.size, "Contract should have 0 UDT enums")

        // Validate UDT error enums count (should be zero for this contract)
        assertEquals(0, contractInfo.udtErrorEnums.size, "Contract should have 0 UDT error enums")

        // Validate events count and specific event names
        assertEquals(8, contractInfo.events.size, "Contract should have exactly 8 events")

        val eventNames = contractInfo.events.map { it.name.value }
        assertTrue(eventNames.contains("SetAdmin"), "Contract should have SetAdmin event")
        assertTrue(eventNames.contains("Approve"), "Contract should have Approve event")
        assertTrue(eventNames.contains("Transfer"), "Contract should have Transfer event")
        assertTrue(eventNames.contains("TransferWithAmountOnly"), "Contract should have TransferWithAmountOnly event")
        assertTrue(eventNames.contains("Burn"), "Contract should have Burn event")
        assertTrue(eventNames.contains("Mint"), "Contract should have Mint event")
        assertTrue(eventNames.contains("MintWithAmountOnly"), "Contract should have MintWithAmountOnly event")
        assertTrue(eventNames.contains("Clawback"), "Contract should have Clawback event")

        // Validate Transfer event structure
        val transferEvent = contractInfo.events.firstOrNull { it.name.value == "Transfer" }
        assertNotNull(transferEvent, "Transfer event should be found")
        assertEquals(1, transferEvent.prefixTopics.size, "Transfer event should have 1 prefix topic")
        assertEquals("transfer", transferEvent.prefixTopics[0].value, "Transfer event prefix topic should be 'transfer'")
        assertEquals(4, transferEvent.params.size, "Transfer event should have 4 parameters")

        // Validate Approve event structure
        val approveEvent = contractInfo.events.firstOrNull { it.name.value == "Approve" }
        assertNotNull(approveEvent, "Approve event should be found")
        assertEquals(1, approveEvent.prefixTopics.size, "Approve event should have 1 prefix topic")
        assertEquals("approve", approveEvent.prefixTopics[0].value, "Approve event prefix topic should be 'approve'")
        assertEquals(4, approveEvent.params.size, "Approve event should have 4 parameters")

        // Validate balance function signature
        val balanceFunc = contractInfo.funcs.firstOrNull { it.name.value == "balance" }
        assertNotNull(balanceFunc, "balance function should be found")
        assertEquals(1, balanceFunc.inputs.size, "balance function should have 1 input parameter")
        assertEquals("id", balanceFunc.inputs[0].name, "balance function input should be named 'id'")
        assertEquals(1, balanceFunc.outputs.size, "balance function should have 1 output")

        // Validate mint function signature
        val mintFunc = contractInfo.funcs.firstOrNull { it.name.value == "mint" }
        assertNotNull(mintFunc, "mint function should be found")
        assertEquals(2, mintFunc.inputs.size, "mint function should have 2 input parameters")
        assertEquals("to", mintFunc.inputs[0].name, "First parameter of mint function should be named 'to'")
        assertEquals("amount", mintFunc.inputs[1].name, "Second parameter of mint function should be named 'amount'")
        assertEquals(0, mintFunc.outputs.size, "mint function should have no outputs (void return)")
    }

    /**
     * Tests ContractSpec utility methods.
     *
     * This test validates the ContractSpec class methods for filtering and
     * finding spec entries:
     * 1. funcs() - Returns all function specifications
     * 2. udtStructs() - Returns all UDT struct specifications
     * 3. udtUnions() - Returns all UDT union specifications
     * 4. udtEnums() - Returns all UDT enum specifications
     * 5. udtErrorEnums() - Returns all UDT error enum specifications
     * 6. events() - Returns all event specifications
     * 7. getFunc(name) - Finds a specific function by name
     * 8. findEntry(name) - Finds any spec entry by name
     *
     * The test ensures that ContractSpec correctly filters spec entries by type
     * and can locate specific entries by name.
     *
     * **Duration**: <1 second (file I/O + parsing)
     *
     * **Reference**: Ported from Flutter SDK's test contract spec methods
     * (soroban_test_parser.dart lines 544-760)
     */
    @Test
    fun testContractSpecMethods() = runTest(timeout = 30.seconds) {
        // Given: Load and parse token contract
        val contractCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        val contractInfo = SorobanContractParser.parseContractByteCode(contractCode)

        // When: Create ContractSpec instance
        val contractSpec = ContractSpec(contractInfo.specEntries)

        // Test funcs() method - should return 13 functions
        val functions = contractSpec.funcs()
        assertEquals(13, functions.size, "ContractSpec funcs() should return exactly 13 functions")

        // Validate specific function names exist
        val functionNames = functions.map { it.name.value }
        assertTrue(functionNames.contains("__constructor"), "Functions should include __constructor")
        assertTrue(functionNames.contains("mint"), "Functions should include mint")
        assertTrue(functionNames.contains("burn"), "Functions should include burn")
        assertTrue(functionNames.contains("transfer"), "Functions should include transfer")
        assertTrue(functionNames.contains("transfer_from"), "Functions should include transfer_from")
        assertTrue(functionNames.contains("balance"), "Functions should include balance")
        assertTrue(functionNames.contains("approve"), "Functions should include approve")
        assertTrue(functionNames.contains("allowance"), "Functions should include allowance")
        assertTrue(functionNames.contains("decimals"), "Functions should include decimals")
        assertTrue(functionNames.contains("name"), "Functions should include name")
        assertTrue(functionNames.contains("symbol"), "Functions should include symbol")
        assertTrue(functionNames.contains("set_admin"), "Functions should include set_admin")
        assertTrue(functionNames.contains("burn_from"), "Functions should include burn_from")

        // Test udtStructs() method - should return 3 structs
        val structs = contractSpec.udtStructs()
        assertEquals(3, structs.size, "ContractSpec udtStructs() should return exactly 3 structs")

        // Validate specific struct names exist
        val structNames = structs.map { it.name }
        assertTrue(structNames.contains("AllowanceDataKey"), "Structs should include AllowanceDataKey")
        assertTrue(structNames.contains("AllowanceValue"), "Structs should include AllowanceValue")
        assertTrue(structNames.contains("TokenMetadata"), "Structs should include TokenMetadata")

        // Validate AllowanceDataKey struct has expected fields
        val allowanceDataKey = structs.firstOrNull { it.name == "AllowanceDataKey" }
        assertNotNull(allowanceDataKey, "AllowanceDataKey struct should be found")
        assertEquals(2, allowanceDataKey.fields.size, "AllowanceDataKey should have 2 fields")
        assertEquals("from", allowanceDataKey.fields[0].name, "First field should be named 'from'")
        assertEquals("spender", allowanceDataKey.fields[1].name, "Second field should be named 'spender'")

        // Test udtUnions() method - should return 1 union
        val unions = contractSpec.udtUnions()
        assertEquals(1, unions.size, "ContractSpec udtUnions() should return exactly 1 union")


        // Validate specific union names exist
        val unionNames = unions.map { it.name }
        assertTrue(unionNames.contains("DataKey"), "Unions should include DataKey")

        // Validate DataKey union has expected cases
        val dataKey = unions[0]
        assertEquals("DataKey", dataKey.name, "Union should be named DataKey")
        assertEquals(4, dataKey.cases.size, "DataKey union should have 4 cases")

        // Test udtEnums() method - should return 0 enums
        val enums = contractSpec.udtEnums()
        assertEquals(0, enums.size, "ContractSpec udtEnums() should return 0 enums for this contract")

        // Test udtErrorEnums() method - should return 0 error enums
        val errorEnums = contractSpec.udtErrorEnums()
        assertEquals(0, errorEnums.size, "ContractSpec udtErrorEnums() should return 0 error enums for this contract")

        // Test events() method - should return 8 events
        val events = contractSpec.events()
        assertEquals(8, events.size, "ContractSpec events() should return exactly 8 events")

        // Validate specific event names exist
        val eventNames = events.map { it.name.value }
        assertTrue(eventNames.contains("SetAdmin"), "Events should include SetAdmin")
        assertTrue(eventNames.contains("Approve"), "Events should include Approve")
        assertTrue(eventNames.contains("Transfer"), "Events should include Transfer")
        assertTrue(eventNames.contains("TransferWithAmountOnly"), "Events should include TransferWithAmountOnly")
        assertTrue(eventNames.contains("Burn"), "Events should include Burn")
        assertTrue(eventNames.contains("Mint"), "Events should include Mint")
        assertTrue(eventNames.contains("MintWithAmountOnly"), "Events should include MintWithAmountOnly")
        assertTrue(eventNames.contains("Clawback"), "Events should include Clawback")

        // Validate Transfer event structure from ContractSpec
        val transferEvent = events.firstOrNull { it.name.value == "Transfer" }
        assertNotNull(transferEvent, "Transfer event should be found")
        assertEquals(1, transferEvent.prefixTopics.size, "Transfer event should have 1 prefix topic")
        assertEquals("transfer", transferEvent.prefixTopics[0].value, "Transfer event prefix topic should be 'transfer'")
        assertEquals(4, transferEvent.params.size, "Transfer event should have 4 parameters")

        // Validate that ContractSpec can find specific functions by name using getFunc()
        val balanceFunc = contractSpec.getFunc("balance")
        assertNotNull(balanceFunc, "ContractSpec getFunc() should find balance function")
        assertEquals("balance", balanceFunc.name.value, "Found function should have correct name")
        assertEquals(1, balanceFunc.inputs.size, "balance function should have 1 input parameter")

        // Validate that getFunc() returns null for non-existent function
        val nonExistentFunc = contractSpec.getFunc("non_existent_function")
        assertNull(nonExistentFunc, "ContractSpec getFunc() should return null for non-existent function")

        // Validate that findEntry() can locate entries by name
        val mintEntry = contractSpec.findEntry("mint")
        assertNotNull(mintEntry, "ContractSpec findEntry() should find mint entry")
        assertEquals(SCSpecEntryKindXdr.SC_SPEC_ENTRY_FUNCTION_V0, mintEntry.discriminant, "mint entry should be a function type")

        val dataKeyEntry = contractSpec.findEntry("DataKey")
        assertNotNull(dataKeyEntry, "ContractSpec findEntry() should find DataKey entry")
        assertEquals(SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_UNION_V0, dataKeyEntry.discriminant, "DataKey entry should be a union type")

        val transferEventEntry = contractSpec.findEntry("Transfer")
        assertNotNull(transferEventEntry, "ContractSpec findEntry() should find Transfer entry")
        assertEquals(SCSpecEntryKindXdr.SC_SPEC_ENTRY_EVENT_V0, transferEventEntry.discriminant, "Transfer entry should be an event type")

        // Validate that findEntry() returns null for non-existent entry
        val nonExistentEntry = contractSpec.findEntry("NonExistentEntry")
        assertNull(nonExistentEntry, "ContractSpec findEntry() should return null for non-existent entry")
    }
}
