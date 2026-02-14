package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

/**
 * Unit tests for SorobanContractParser.
 *
 * Tests cover:
 * - SorobanContractInfo data class properties (supportedSeps, funcs, structs, etc.)
 * - SorobanContractParserException construction
 * - parseContractByteCode error handling
 * - SorobanContractInfo accessors on manually constructed instances
 */
class SorobanContractParserTest {

    // ========== SorobanContractParserException Tests ==========

    @Test
    fun testSorobanContractParserExceptionMessage() {
        val exception = SorobanContractParserException("Invalid byte code: environment meta not found.")
        assertEquals("Invalid byte code: environment meta not found.", exception.message)
    }

    @Test
    fun testSorobanContractParserExceptionIsException() {
        val exception = SorobanContractParserException("test")
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testSorobanContractParserExceptionCanBeThrown() {
        val caught = assertFailsWith<SorobanContractParserException> {
            throw SorobanContractParserException("parse failed")
        }
        assertEquals("parse failed", caught.message)
    }

    // ========== parseContractByteCode Error Handling ==========

    @Test
    fun testParseContractByteCodeWithEmptyByteCode() {
        assertFailsWith<SorobanContractParserException> {
            SorobanContractParser.parseContractByteCode(ByteArray(0))
        }
    }

    @Test
    fun testParseContractByteCodeWithRandomBytes() {
        val randomBytes = ByteArray(100) { it.toByte() }
        assertFailsWith<SorobanContractParserException> {
            SorobanContractParser.parseContractByteCode(randomBytes)
        }
    }

    @Test
    fun testParseContractByteCodeWithOnlyEnvMetaMarker() {
        // Byte code with just the marker but no valid XDR after it
        val marker = "contractenvmetav0".encodeToByteArray()
        val garbage = ByteArray(10) { 0xFF.toByte() }
        val byteCode = marker + garbage
        assertFailsWith<SorobanContractParserException> {
            SorobanContractParser.parseContractByteCode(byteCode)
        }
    }

    // ========== SorobanContractInfo Tests ==========

    @Test
    fun testSorobanContractInfoSupportedSeps_empty() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        assertEquals(emptyList(), info.supportedSeps)
    }

    @Test
    fun testSorobanContractInfoSupportedSeps_blankValue() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "   ")
        )
        assertEquals(emptyList(), info.supportedSeps)
    }

    @Test
    fun testSorobanContractInfoSupportedSeps_singleSep() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "10")
        )
        assertEquals(listOf("10"), info.supportedSeps)
    }

    @Test
    fun testSorobanContractInfoSupportedSeps_multipleSeps() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "1, 10, 24")
        )
        assertEquals(listOf("1", "10", "24"), info.supportedSeps)
    }

    @Test
    fun testSorobanContractInfoSupportedSeps_duplicatesRemoved() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "1, 10, 1, 24, 10")
        )
        assertEquals(listOf("1", "10", "24"), info.supportedSeps)
    }

    @Test
    fun testSorobanContractInfoSupportedSeps_whitespaceHandling() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = mapOf("sep" to "  1 ,  10  , 24  ")
        )
        assertEquals(listOf("1", "10", "24"), info.supportedSeps)
    }

    @Test
    fun testSorobanContractInfoFuncs_emptyEntries() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        assertEquals(emptyList(), info.funcs)
    }

    @Test
    fun testSorobanContractInfoFuncs_filtersFunctionEntries() {
        val funcEntry = SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("hello"),
                inputs = emptyList(),
                outputs = emptyList()
            )
        )
        val structEntry = SCSpecEntryXdr.UdtStructV0(
            SCSpecUDTStructV0Xdr(
                doc = "",
                name = "MyStruct",
                lib = "",
                fields = emptyList()
            )
        )
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = listOf(funcEntry, structEntry),
            metaEntries = emptyMap()
        )
        assertEquals(1, info.funcs.size)
        assertEquals("hello", info.funcs[0].name.value)
    }

    @Test
    fun testSorobanContractInfoUdtStructs_filtersStructEntries() {
        val funcEntry = SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("hello"),
                inputs = emptyList(),
                outputs = emptyList()
            )
        )
        val structEntry = SCSpecEntryXdr.UdtStructV0(
            SCSpecUDTStructV0Xdr(
                doc = "",
                name = "MyStruct",
                lib = "",
                fields = emptyList()
            )
        )
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = listOf(funcEntry, structEntry),
            metaEntries = emptyMap()
        )
        assertEquals(1, info.udtStructs.size)
        assertEquals("MyStruct", info.udtStructs[0].name)
    }

    @Test
    fun testSorobanContractInfoUdtUnions_empty() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        assertEquals(emptyList(), info.udtUnions)
    }

    @Test
    fun testSorobanContractInfoUdtEnums_empty() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        assertEquals(emptyList(), info.udtEnums)
    }

    @Test
    fun testSorobanContractInfoUdtErrorEnums_empty() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        assertEquals(emptyList(), info.udtErrorEnums)
    }

    @Test
    fun testSorobanContractInfoEvents_empty() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        assertEquals(emptyList(), info.events)
    }

    @Test
    fun testSorobanContractInfoDataClassEquality() {
        val info1 = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = mapOf("key" to "value")
        )
        val info2 = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = mapOf("key" to "value")
        )
        assertEquals(info1, info2)
    }

    @Test
    fun testSorobanContractInfoDataClassInequality() {
        val info1 = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        val info2 = SorobanContractInfo(
            envInterfaceVersion = 22UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        assertNotEquals(info1, info2)
    }

    @Test
    fun testSorobanContractInfoEnvInterfaceVersion() {
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = emptyMap()
        )
        assertEquals(21UL, info.envInterfaceVersion)
    }

    @Test
    fun testSorobanContractInfoMetaEntries() {
        val meta = mapOf("rsdk_version" to "1.0.0", "rsdk_name" to "soroban-sdk")
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = emptyList(),
            metaEntries = meta
        )
        assertEquals("1.0.0", info.metaEntries["rsdk_version"])
        assertEquals("soroban-sdk", info.metaEntries["rsdk_name"])
    }
}
