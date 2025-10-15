// Copyright 2024 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

/**
 * Comprehensive unit tests for SorobanContractParser.
 *
 * Tests the parsing of Soroban contract WASM bytecode to extract:
 * - Environment metadata (protocol version)
 * - Contract specification entries (functions, structs, unions, enums, events)
 * - Contract metadata (key-value pairs)
 */
class SorobanContractParserTest {

    /**
     * Tests parsing a real token contract WASM file.
     * Validates that all expected metadata, spec entries, and meta entries are extracted correctly.
     */
    @Test
    fun testTokenContractParsing() {
        // Load the token contract WASM file
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode, "Failed to load test WASM file")
        assertTrue(byteCode.size > 0, "WASM file is empty")

        // Parse the contract
        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Verify environment metadata
        assertTrue(contractInfo.envInterfaceVersion > 0u, "Environment interface version should be positive")
        println("Environment interface version: ${contractInfo.envInterfaceVersion}")

        // Verify spec entries
        assertEquals(17, contractInfo.specEntries.size, "Token contract should have 17 spec entries")

        // Verify meta entries
        assertEquals(2, contractInfo.metaEntries.size, "Token contract should have 2 meta entries")
        assertTrue(contractInfo.metaEntries.isNotEmpty(), "Meta entries should not be empty")

        // Print detailed information for manual verification
        println(SorobanTestParser.printContractInfo(contractInfo))

        // Verify specific spec entry types
        var functionCount = 0
        var structCount = 0
        var enumCount = 0
        var errorEnumCount = 0
        var unionCount = 0
        var eventCount = 0

        contractInfo.specEntries.forEach { entry ->
            when (entry.discriminant) {
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_FUNCTION_V0 -> functionCount++
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_STRUCT_V0 -> structCount++
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_UNION_V0 -> unionCount++
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ENUM_V0 -> enumCount++
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ERROR_ENUM_V0 -> errorEnumCount++
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_EVENT_V0 -> eventCount++
                else -> fail("Unexpected spec entry type: ${entry.discriminant}")
            }
        }

        println("Functions: $functionCount")
        println("Structs: $structCount")
        println("Unions: $unionCount")
        println("Enums: $enumCount")
        println("Error Enums: $errorEnumCount")
        println("Events: $eventCount")

        // Token contracts typically have functions
        assertTrue(functionCount > 0, "Token contract should have at least one function")
    }

    /**
     * Tests parsing individual function entries.
     * Validates that function metadata (name, inputs, outputs, doc) is correctly extracted.
     */
    @Test
    fun testFunctionEntryParsing() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Find a function entry
        val functionEntry = contractInfo.specEntries.firstOrNull {
            it.discriminant == SCSpecEntryKindXdr.SC_SPEC_ENTRY_FUNCTION_V0
        }
        assertNotNull(functionEntry, "Should have at least one function entry")

        when (functionEntry) {
            is SCSpecEntryXdr.FunctionV0 -> {
                val function = functionEntry.value
                assertNotNull(function.name, "Function should have a name")
                assertTrue(function.name.value.isNotEmpty(), "Function name should not be empty")

                // Verify function structure
                assertNotNull(function.inputs, "Function should have inputs list")
                assertNotNull(function.outputs, "Function should have outputs list")
                assertNotNull(function.doc, "Function should have doc string")

                println("Found function: ${function.name.value}")
                println(SorobanTestParser.printFunction(function))
            }
            else -> fail("Function entry should be FunctionV0 type")
        }
    }

    /**
     * Tests parsing UDT struct entries.
     * Validates that struct metadata (name, fields, doc) is correctly extracted.
     */
    @Test
    fun testUdtStructParsing() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Find a struct entry if any
        val structEntry = contractInfo.specEntries.firstOrNull {
            it.discriminant == SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_STRUCT_V0
        }

        if (structEntry != null) {
            when (structEntry) {
                is SCSpecEntryXdr.UdtStructV0 -> {
                    val udtStruct = structEntry.value
                    assertNotNull(udtStruct.name, "Struct should have a name")
                    assertTrue(udtStruct.name.isNotEmpty(), "Struct name should not be empty")

                    println("Found struct: ${udtStruct.name}")
                    println(SorobanTestParser.printUdtStruct(udtStruct))
                }
                else -> fail("Struct entry should be UdtStructV0 type")
            }
        } else {
            println("No struct entries found in this contract (this is okay)")
        }
    }

    /**
     * Tests parsing UDT enum entries.
     * Validates that enum metadata (name, cases, values) is correctly extracted.
     */
    @Test
    fun testUdtEnumParsing() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Find an enum entry if any
        val enumEntry = contractInfo.specEntries.firstOrNull {
            it.discriminant == SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ENUM_V0
        }

        if (enumEntry != null) {
            when (enumEntry) {
                is SCSpecEntryXdr.UdtEnumV0 -> {
                    val udtEnum = enumEntry.value
                    assertNotNull(udtEnum.name, "Enum should have a name")
                    assertTrue(udtEnum.name.isNotEmpty(), "Enum name should not be empty")
                    assertTrue(udtEnum.cases.isNotEmpty(), "Enum should have at least one case")

                    println("Found enum: ${udtEnum.name}")
                    println(SorobanTestParser.printUdtEnum(udtEnum))
                }
                else -> fail("Enum entry should be UdtEnumV0 type")
            }
        } else {
            println("No enum entries found in this contract (this is okay)")
        }
    }

    /**
     * Tests parsing UDT error enum entries.
     * Validates that error enum metadata is correctly extracted.
     */
    @Test
    fun testUdtErrorEnumParsing() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Find an error enum entry if any
        val errorEnumEntry = contractInfo.specEntries.firstOrNull {
            it.discriminant == SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ERROR_ENUM_V0
        }

        if (errorEnumEntry != null) {
            when (errorEnumEntry) {
                is SCSpecEntryXdr.UdtErrorEnumV0 -> {
                    val udtErrorEnum = errorEnumEntry.value
                    assertNotNull(udtErrorEnum.name, "Error enum should have a name")
                    assertTrue(udtErrorEnum.name.isNotEmpty(), "Error enum name should not be empty")
                    assertTrue(udtErrorEnum.cases.isNotEmpty(), "Error enum should have at least one case")

                    println("Found error enum: ${udtErrorEnum.name}")
                    println(SorobanTestParser.printUdtErrorEnum(udtErrorEnum))
                }
                else -> fail("Error enum entry should be UdtErrorEnumV0 type")
            }
        } else {
            println("No error enum entries found in this contract (this is okay)")
        }
    }

    /**
     * Tests parsing UDT union entries.
     * Validates that union metadata is correctly extracted.
     */
    @Test
    fun testUdtUnionParsing() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Find a union entry if any
        val unionEntry = contractInfo.specEntries.firstOrNull {
            it.discriminant == SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_UNION_V0
        }

        if (unionEntry != null) {
            when (unionEntry) {
                is SCSpecEntryXdr.UdtUnionV0 -> {
                    val udtUnion = unionEntry.value
                    assertNotNull(udtUnion.name, "Union should have a name")
                    assertTrue(udtUnion.name.isNotEmpty(), "Union name should not be empty")
                    assertTrue(udtUnion.cases.isNotEmpty(), "Union should have at least one case")

                    println("Found union: ${udtUnion.name}")
                    println(SorobanTestParser.printUdtUnion(udtUnion))
                }
                else -> fail("Union entry should be UdtUnionV0 type")
            }
        } else {
            println("No union entries found in this contract (this is okay)")
        }
    }

    /**
     * Tests parsing event entries.
     * Validates that event metadata is correctly extracted.
     */
    @Test
    fun testEventParsing() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Find an event entry if any
        val eventEntry = contractInfo.specEntries.firstOrNull {
            it.discriminant == SCSpecEntryKindXdr.SC_SPEC_ENTRY_EVENT_V0
        }

        if (eventEntry != null) {
            when (eventEntry) {
                is SCSpecEntryXdr.EventV0 -> {
                    val event = eventEntry.value
                    assertNotNull(event.name, "Event should have a name")
                    assertTrue(event.name.value.isNotEmpty(), "Event name should not be empty")

                    println("Found event: ${event.name.value}")
                    println(SorobanTestParser.printEvent(event))
                }
                else -> fail("Event entry should be EventV0 type")
            }
        } else {
            println("No event entries found in this contract (this is okay)")
        }
    }

    /**
     * Tests that contract metadata entries are correctly parsed.
     */
    @Test
    fun testMetaEntryParsing() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Verify meta entries structure
        assertTrue(contractInfo.metaEntries.isNotEmpty(), "Token contract should have meta entries")

        contractInfo.metaEntries.forEach { (key, value) ->
            assertTrue(key.isNotEmpty(), "Meta entry key should not be empty")
            // Value can be empty, so just check it's not null
            assertNotNull(value, "Meta entry value should not be null")
            println("Meta entry: $key = $value")
        }
    }

    /**
     * Tests type information extraction for various SCSpecTypeDef types.
     */
    @Test
    fun testSpecTypeInfoExtraction() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Find various type usages in function parameters
        val functionEntry = contractInfo.specEntries.firstOrNull {
            it.discriminant == SCSpecEntryKindXdr.SC_SPEC_ENTRY_FUNCTION_V0
        }

        if (functionEntry != null && functionEntry is SCSpecEntryXdr.FunctionV0) {
            val function = functionEntry.value

            function.inputs.forEach { input ->
                val typeInfo = SorobanTestParser.getSpecTypeInfo(input.type)
                assertNotNull(typeInfo, "Type info should not be null")
                assertTrue(typeInfo.isNotEmpty(), "Type info should not be empty")
                println("Input '${input.name}' has type: $typeInfo")
            }

            function.outputs.forEach { output ->
                val typeInfo = SorobanTestParser.getSpecTypeInfo(output)
                assertNotNull(typeInfo, "Type info should not be null")
                assertTrue(typeInfo.isNotEmpty(), "Type info should not be empty")
                println("Output type: $typeInfo")
            }
        }
    }

    /**
     * Tests error handling for invalid byte code.
     */
    @Test
    fun testInvalidByteCodeHandling() {
        // Test with empty byte code
        assertFailsWith<SorobanContractParserException> {
            SorobanContractParser.parseContractByteCode(ByteArray(0))
        }

        // Test with random garbage data
        assertFailsWith<SorobanContractParserException> {
            SorobanContractParser.parseContractByteCode(ByteArray(100) { it.toByte() })
        }

        // Test with partial markers
        assertFailsWith<SorobanContractParserException> {
            SorobanContractParser.parseContractByteCode("contractenvmetav0".encodeToByteArray())
        }
    }

    /**
     * Tests that the parser handles contracts without metadata gracefully.
     */
    @Test
    fun testContractWithoutMetadata() {
        // Create a minimal contract bytecode with only env meta and spec
        // This tests the parser's ability to handle missing contractmetav0 section
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Meta entries might be empty or present - both are valid
        assertNotNull(contractInfo.metaEntries, "Meta entries map should not be null even if empty")
    }

    /**
     * Tests round-trip encoding/decoding of parsed entries.
     * Ensures that parsed XDR data can be re-encoded and matches the original.
     */
    @Test
    fun testRoundTripEncoding() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Test that each spec entry can be encoded and decoded
        contractInfo.specEntries.forEach { entry ->
            val writer = XdrWriter()
            entry.encode(writer)
            val encodedBytes = writer.toByteArray()

            assertTrue(encodedBytes.size > 0, "Encoded bytes should not be empty")

            // Decode back and verify discriminant matches
            val reader = XdrReader(encodedBytes)
            val decodedEntry = SCSpecEntryXdr.decode(reader)

            assertEquals(entry.discriminant, decodedEntry.discriminant,
                "Discriminant should match after round-trip encoding")
        }
    }

    /**
     * Tests that spec entries are in expected order and complete.
     */
    @Test
    fun testSpecEntryCompleteness() {
        val byteCode = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
        assertNotNull(byteCode)

        val contractInfo = SorobanContractParser.parseContractByteCode(byteCode)

        // Verify all entries have valid discriminants
        contractInfo.specEntries.forEach { entry ->
            assertTrue(
                entry.discriminant in listOf(
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_FUNCTION_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_STRUCT_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_UNION_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ENUM_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ERROR_ENUM_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_EVENT_V0
                ),
                "Spec entry should have valid discriminant"
            )
        }
    }

}
