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
    fun testParseContractByteCodeWithUndecodableEnvMeta() {
        // An environment meta section whose bytes do not decode; its content starts at byte 28.
        val module = wasmModule(customSection("contractenvmetav0", ByteArray(10) { 0xFF.toByte() }))
        val cause = "Unknown SCEnvMetaKindXdr value: -1"
        val expected = "Invalid byte code: the contractenvmetav0 entry at byte offset 28 does not decode: $cause."
        assertEquals(expected, parseFailure(module))
    }

    private fun parseFailure(code: ByteArray): String? =
        assertFailsWith<SorobanContractParserException> { SorobanContractParser.parseContractByteCode(code) }.message

    /** The marker-delimited layout of a module without its wasm framing is refused at the header. */
    private fun assertRefusedWithoutFraming(byteCode: ByteArray) = assertEquals(
        "Invalid byte code: expected the wasm magic 00 61 73 6d at byte offset 0, found 63 6f 6e 74.",
        parseFailure(byteCode)
    )

    private fun encodedBytes(block: (XdrWriter) -> Unit): ByteArray {
        val writer = XdrWriter()
        block(writer)
        return writer.toByteArray()
    }

    private fun envMetaBytes(protocol: UInt = 21u): ByteArray = encodedBytes { writer ->
        SCEnvMetaEntryXdr.InterfaceVersion(
            SCEnvMetaEntryInterfaceVersionXdr(protocol = Uint32Xdr(protocol), preRelease = Uint32Xdr(0u))
        ).encode(writer)
    }

    private fun funcSpecEntryBytes(name: String = "hello"): ByteArray = encodedBytes { writer ->
        SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(doc = "", name = SCSymbolXdr(name), inputs = emptyList(), outputs = emptyList())
        ).encode(writer)
    }

    private fun metaEntryBytes(key: String, value: String): ByteArray = encodedBytes { writer ->
        SCMetaEntryXdr.V0(SCMetaV0Xdr(key = key, `val` = value)).encode(writer)
    }

    @Test
    fun testParseContractByteCodeThrowsWhenSpecSectionMissing() {
        // A module without a spec section reports the missing spec rather than an empty spec list.
        val module = wasmModule(customSection("contractenvmetav0", envMetaBytes()))
        assertEquals("Invalid byte code: spec entries not found.", parseFailure(module))
    }

    @Test
    fun testParseContractByteCodeThrowsWhenEnvMetaSectionMissingOrEmpty() {
        val spec = customSection("contractspecv0", funcSpecEntryBytes())
        assertEquals("Invalid byte code: environment meta not found.", parseFailure(wasmModule(spec)))
        val emptyEnvMeta = customSection("contractenvmetav0", ByteArray(0))
        val noVersion = "Invalid byte code: environment interface version not found."
        assertEquals(noVersion, parseFailure(wasmModule(spec, emptyEnvMeta)))
        // The first environment meta section counts, also when a later one holds the version.
        val laterEnvMeta = customSection("contractenvmetav0", envMetaBytes())
        assertEquals(noVersion, parseFailure(wasmModule(spec, emptyEnvMeta, laterEnvMeta)))
    }

    @Test
    fun testParseContractByteCodeParsesSingleSpecEntryToExhaustion() {
        // The spec section holds exactly one entry, so reading ends at the section end.
        val byteCode = wasmModule(
            customSection("contractspecv0", funcSpecEntryBytes()),
            customSection("contractenvmetav0", envMetaBytes())
        )
        val info = SorobanContractParser.parseContractByteCode(byteCode)
        assertEquals(1, info.specEntries.size)
        assertEquals(1, info.funcs.size)
        assertEquals("hello", info.funcs[0].name.value)
        assertEquals(emptyMap(), info.metaEntries)
        assertRefusedWithoutFraming(
            "contractspecv0".encodeToByteArray() + funcSpecEntryBytes() +
                "contractenvmetav0".encodeToByteArray() + envMetaBytes()
        )
    }

    @Test
    fun testParseContractByteCodeParsesMetaEntryToExhaustion() {
        // Layout: environment meta, then contract meta, then contract spec.
        val byteCode = wasmModule(
            customSection("contractenvmetav0", envMetaBytes()),
            customSection("contractmetav0", metaEntryBytes("rsdk_version", "1.0.0")),
            customSection("contractspecv0", funcSpecEntryBytes())
        )
        val info = SorobanContractParser.parseContractByteCode(byteCode)
        assertEquals(21UL, info.envInterfaceVersion)
        assertEquals(1, info.specEntries.size)
        assertEquals("1.0.0", info.metaEntries["rsdk_version"])
        assertRefusedWithoutFraming(
            "contractenvmetav0".encodeToByteArray() + envMetaBytes() +
                "contractmetav0".encodeToByteArray() + metaEntryBytes("rsdk_version", "1.0.0") +
                "contractspecv0".encodeToByteArray() + funcSpecEntryBytes()
        )
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
    fun testSorobanContractInfoUdtErrorEnums_filtersErrorEnumEntries() {
        val funcEntry = SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(doc = "", name = SCSymbolXdr("hello"), inputs = emptyList(), outputs = emptyList())
        )
        val errorEnumEntry = SCSpecEntryXdr.UdtErrorEnumV0(
            SCSpecUDTErrorEnumV0Xdr(doc = "", lib = "", name = "MyError", cases = emptyList())
        )
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = listOf(funcEntry, errorEnumEntry),
            metaEntries = emptyMap()
        )
        assertEquals(1, info.udtErrorEnums.size)
        assertEquals("MyError", info.udtErrorEnums[0].name)
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
    fun testSorobanContractInfoEvents_filtersEventEntries() {
        val funcEntry = SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(doc = "", name = SCSymbolXdr("hello"), inputs = emptyList(), outputs = emptyList())
        )
        val eventEntry = SCSpecEntryXdr.EventV0(
            SCSpecEventV0Xdr(
                doc = "",
                lib = "",
                name = "Transfer",
                prefixTopics = emptyList(),
                params = emptyList(),
                dataFormat = SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_SINGLE_VALUE
            )
        )
        val info = SorobanContractInfo(
            envInterfaceVersion = 21UL,
            specEntries = listOf(funcEntry, eventEntry),
            metaEntries = emptyMap()
        )
        assertEquals(1, info.events.size)
        assertEquals("Transfer", info.events[0].name)
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
