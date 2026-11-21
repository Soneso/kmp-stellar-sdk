package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.SorobanContractInfo
import com.soneso.stellar.sdk.contract.SorobanContractParser
import com.soneso.stellar.sdk.contract.SorobanContractParserException
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

/** Tests [SorobanContractParser] on modules built from the token contract against this file's own section walker. */
class SorobanContractParserSectionsTest {

    /** A section as the test's walker reads it; [name] is null for a section other than a custom one. */
    private class RawSection(val name: String?, val bytes: ByteArray, val contentOffset: Int, val content: ByteArray)

    private val tokenWasm = TestResourceUtil.readWasmFile("soroban_token_contract.wasm")
    private val codeSections = sectionsOf(tokenWasm).filter { it.name == null }.map { it.bytes }.toTypedArray()
    private val specContent = sectionOf(tokenWasm, SPEC).content
    private val envMetaContent = sectionOf(tokenWasm, ENV_META).content
    private val metaContent = sectionOf(tokenWasm, META).content
    private val expectedToken = expectedInfo(tokenWasm)

    @Test
    fun everyShippedWasmParsesToItsFirstSpecAndEnvMetaAndEveryMetaSection() {
        assertContentEquals(tokenWasm, token())
        assertEquals(25, expectedToken.specEntries.size)
        assertEquals(2, expectedToken.metaEntries.size)
        val modules = SHIPPED_WASM.associateWith { TestResourceUtil.readWasmFile(it) }
        assertEquals(4, modules.values.count { module -> sectionsOf(module).count { it.name == META } > 1 })
        for ((file, module) in modules) assertEquals(expectedInfo(module), parse(module), file)
    }

    @Test
    fun specSectionFollowedByAnotherCustomSectionParsesToTheFullList() {
        assertEquals(expectedToken, parse(token(afterSpec = customSection("other", bytes(1, 2, 3)))))
    }

    @Test
    fun everySectionIsReadEntryByEntryToItsEnd() {
        val secondVersion = xdrBytes { envMetaEntry(99u).encode(it) }
        val thirdMeta = xdrBytes { metaEntry("source", "repo").encode(it) }
        val info = parse(token(envMeta = envMetaContent + secondVersion, meta = metaContent + thirdMeta))
        assertEquals(expectedToken.envInterfaceVersion, info.envInterfaceVersion)
        assertEquals(expectedToken.specEntries, info.specEntries)
        assertEquals(expectedToken.metaEntries + ("source" to "repo"), info.metaEntries)
    }

    @Test
    fun unknownSpecEntryKindFailsWithItsOffset() {
        val first = xdrBytes { expectedToken.specEntries[0].encode(it) }
        val module = token(spec = first + bytes(0, 0, 0, 6) + specContent.copyOfRange(first.size, specContent.size))
        val offset = sectionOf(module, SPEC).contentOffset + first.size
        val cause = "Unknown SCSpecEntryKindXdr value: 6"
        assertInvalid(module, "the $SPEC entry at byte offset $offset does not decode: $cause")
    }

    @Test
    fun trailingBytesThatDoNotFormAnEntryFailWithTheirOffset() {
        val trailing = bytes(0, 0, 0)
        val modules = listOf(
            SPEC to token(spec = specContent + trailing),
            ENV_META to token(envMeta = envMetaContent + trailing),
            META to token(meta = metaContent + trailing)
        )
        for ((name, module) in modules) {
            val section = sectionOf(module, name)
            val size = section.content.size
            val offset = section.contentOffset + size - 3
            val cause = "XDR decode requires 4 byte(s) at offset ${size - 3} but only 3 remain in a $size-byte buffer"
            assertInvalid(module, "the $name entry at byte offset $offset does not decode: $cause")
        }
    }

    @Test
    fun malformedFramingBeforeOrOfTheSpecSectionFails() {
        val prefix = wasmModule(*codeSections)
        val at = prefix.size
        val rest = tokenWasm.copyOfRange(at, tokenWasm.size)
        assertInvalid(prefix + bytes(1, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F) + rest, declares(at, 1, 4294967295, rest.size))
        assertInvalid(prefix + bytes(1, 0x80, 0x80), "${sizeAt(at)} is cut off at byte offset ${at + 3}")
        val metaFirst = prefix + customSection(ENV_META, envMetaContent) + customSection(META, metaContent)
        val specAt = metaFirst.size
        val payload = leb128(SPEC.length) + SPEC.encodeToByteArray() + specContent
        val oneByteShort = metaFirst + bytes(0) + leb128(payload.size + 1) + payload
        assertInvalid(oneByteShort, declares(specAt, 0, payload.size + 1L, payload.size))
        assertInvalid(metaFirst + bytes(0, 0x8F), "${sizeAt(specAt)} is cut off at byte offset ${specAt + 2}")
    }

    @Test
    fun sectionAfterTheSpecSectionIsCheckedLikeEveryOtherSection() {
        assertInvalid(tokenWasm + bytes(0, 5, 1, 0x78), declares(tokenWasm.size, 0, 5, 2))
    }

    @Test
    fun laterSpecAndEnvMetaSectionsAreIgnoredAndEveryMetaSectionIsReadInOrder() {
        val otherFunction = SCSpecFunctionV0Xdr("", SCSymbolXdr("other"), emptyList(), emptyList())
        val laterMeta = xdrBytes { metaEntry("rsver", "0.0.0").encode(it); metaEntry("source", "repo").encode(it) }
        val later = customSection(SPEC, xdrBytes { SCSpecEntryXdr.FunctionV0(otherFunction).encode(it) }) +
            customSection(ENV_META, xdrBytes { envMetaEntry(99u).encode(it) }) + customSection(META, laterMeta)
        val info = parse(token(end = later))
        assertEquals(expectedToken.copy(metaEntries = info.metaEntries), info)
        val rssdkver = "rssdkver" to expectedToken.metaEntries.getValue("rssdkver")
        assertEquals(listOf("rsver" to "0.0.0", rssdkver, "source" to "repo"), info.metaEntries.toList())

        val broken = token(end = customSection(META, laterMeta + bytes(0, 0, 0, 6)))
        val offset = sectionsOf(broken).last { it.name == META }.contentOffset + laterMeta.size
        assertInvalid(broken, "the $META entry at byte offset $offset does not decode: Unknown SCMetaKindXdr value: 6")
    }

    @Test
    fun metaEntryCutAcrossTwoSectionsFailsAtItsOffsetInTheFirst() {
        val cut = xdrBytes { SCMetaEntryXdr.decode(XdrReader(metaContent)).encode(it) }.size + 4
        val rest = customSection(META, metaContent.copyOfRange(cut, metaContent.size))
        val module = token(meta = metaContent.copyOf(cut), end = rest)
        val offset = sectionOf(module, META).contentOffset + cut - 4
        val cause = "XDR decode requires 4 byte(s) at offset $cut but only 0 remain in a $cut-byte buffer"
        assertInvalid(module, "the $META entry at byte offset $offset does not decode: $cause")
    }

    @Test
    fun repeatedSepEntriesInOneMetaSectionAreJoined() {
        assertSepJoined(token(meta = metaContent + sepEntry("41") + sepEntry("45, 46")))
    }

    @Test
    fun repeatedSepEntriesInTwoMetaSectionsAreJoinedInModuleOrder() {
        assertSepJoined(token(meta = metaContent + sepEntry("41"), end = customSection(META, sepEntry("45, 46"))))
    }

    @Test
    fun sectionNameInsideADataSegmentDoesNotStartTheSpec() {
        val segment = SPEC.encodeToByteArray() + bytes(0, 0, 0, 9)
        val data = wasmSection(11, bytes(1, 0, 0x41, 0, 0x0B) + leb128(segment.size) + segment)
        assertEquals(expectedToken, parse(token(beforeSpec = data)))
    }

    @Test
    fun badHeaderFailsWithAFramingError() {
        val wrongMagic = tokenWasm.copyOf().also { it[0] = 1 }
        assertInvalid(wrongMagic, "expected the wasm magic 00 61 73 6d at byte offset 0, found 01 61 73 6d")
        val wrongVersion = tokenWasm.copyOf().also { it[4] = 2 }
        assertInvalid(wrongVersion, "expected the wasm version 01 00 00 00 at byte offset 4, found 02 00 00 00")
        val header = "the 8 bytes of the wasm header at byte offset 0"
        assertInvalid(tokenWasm.copyOf(7), "the module has 7 bytes, fewer than $header")
    }

    @Test
    fun leb128ValuesAreBoundedAndPaddedFormsAreAccepted() {
        val at = tokenWasm.size
        val custom = "the custom section at byte offset $at"
        assertInvalid(tokenWasm + bytes(0, 0x80, 0x80, 0x80, 0x80, 0x10), "${sizeAt(at)} exceeds the u32 range")
        assertInvalid(tokenWasm + bytes(0, 0x80, 0x80, 0x80, 0x80, 0x80, 0), "${sizeAt(at)} is longer than 5 bytes")
        assertInvalid(tokenWasm + bytes(0, 0x80, 0x80), "${sizeAt(at)} is cut off at byte offset ${at + 3}")
        assertInvalid(tokenWasm + bytes(0, 1, 0x80), "the name length of $custom is cut off at byte offset ${at + 3}")
        val nameOverrun = "the name of $custom declares 5 bytes, but 1 remain in the section"
        assertInvalid(tokenWasm + bytes(0, 2, 5, 0x61), nameOverrun)
        val payload = bytes(0x8E, 0x80, 0x80, 0x80, 0) + SPEC.encodeToByteArray() + specContent
        val paddedSpec = bytes(0) + fiveByteLeb128(payload.size) + payload
        val zeroSizedSection = bytes(1, 0x80, 0x80, 0x80, 0x80, 0)
        assertEquals(expectedToken, parse(token(beforeSpec = zeroSizedSection, specSection = paddedSpec)))
    }

    private fun parse(module: ByteArray) = SorobanContractParser.parseContractByteCode(module)

    private fun assertInvalid(module: ByteArray, detail: String) {
        val failure = assertFailsWith<SorobanContractParserException> { parse(module) }
        assertEquals("Invalid byte code: $detail.", failure.message)
    }

    private fun sizeAt(offset: Int) = "the size of the section at byte offset $offset"

    private fun declares(offset: Int, id: Int, size: Long, remaining: Int) =
        "the section at byte offset $offset (id $id) declares $size bytes, but $remaining remain"

    /** The token module with the given section contents and bytes inserted around its spec section or at its end. */
    private fun token(
        spec: ByteArray = specContent, envMeta: ByteArray = envMetaContent, meta: ByteArray = metaContent,
        beforeSpec: ByteArray = ByteArray(0), specSection: ByteArray = customSection(SPEC, spec),
        afterSpec: ByteArray = ByteArray(0), end: ByteArray = ByteArray(0)
    ): ByteArray = wasmModule(
        *codeSections, beforeSpec, specSection, afterSpec,
        customSection(ENV_META, envMeta), customSection(META, meta), end
    )

    private fun envMetaEntry(protocol: UInt) =
        SCEnvMetaEntryXdr.InterfaceVersion(SCEnvMetaEntryInterfaceVersionXdr(Uint32Xdr(protocol), Uint32Xdr(0u)))

    private fun metaEntry(key: String, value: String) = SCMetaEntryXdr.V0(SCMetaV0Xdr(key, value))

    private fun sepEntry(value: String) = xdrBytes { metaEntry("sep", value).encode(it) }

    /** [module] holds the `sep` values "41" and then "45, 46"; they are joined with a comma, without a space. */
    private fun assertSepJoined(module: ByteArray) {
        val info = parse(module)
        assertEquals(expectedInfo(module), info)
        assertEquals("41,45, 46", info.metaEntries["sep"])
        assertEquals(listOf("41", "45", "46"), info.supportedSeps)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun fiveByteLeb128(value: Int) =
        ByteArray(5) { i -> (((value ushr (7 * i)) and 0x7F) or (if (i < 4) 0x80 else 0)).toByte() }

    private fun readLeb(bytes: ByteArray, start: Int): Pair<Int, Int> {
        var value = 0
        var position = start
        do {
            val byte = bytes[position].toInt() and 0xFF
            value = value or ((byte and 0x7F) shl (7 * (position++ - start)))
        } while ((byte and 0x80) != 0)
        return value to position
    }

    private fun sectionsOf(module: ByteArray): List<RawSection> {
        val sections = mutableListOf<RawSection>()
        var offset = WASM_HEADER.size
        while (offset < module.size) {
            val (size, payload) = readLeb(module, offset + 1)
            val end = payload + size
            var name: String? = null
            var contentOffset = payload
            if (module[offset].toInt() == 0) {
                val (length, nameStart) = readLeb(module, payload)
                name = module.copyOfRange(nameStart, nameStart + length).decodeToString()
                contentOffset = nameStart + length
            }
            val content = module.copyOfRange(contentOffset, end)
            sections.add(RawSection(name, module.copyOfRange(offset, end), contentOffset, content))
            offset = end
        }
        return sections
    }

    private fun sectionOf(module: ByteArray, name: String) = sectionsOf(module).first { it.name == name }

    /** Decodes [content] entry by entry, advancing by the length each entry encodes to. */
    private fun <T> decodeAll(content: ByteArray, decode: (XdrReader) -> T, encode: (T, XdrWriter) -> Unit): List<T> {
        val entries = mutableListOf<T>()
        var offset = 0
        while (offset < content.size) {
            val entry = decode(XdrReader(content.copyOfRange(offset, content.size)))
            entries.add(entry)
            offset += xdrBytes { encode(entry, it) }.size
        }
        return entries
    }

    /** The info of [module] from its first spec and env meta section and every meta section, by the XDR types. */
    private fun expectedInfo(module: ByteArray): SorobanContractInfo {
        val sections = sectionsOf(module)
        fun content(name: String) = sections.firstOrNull { it.name == name }?.content ?: ByteArray(0)
        val envMeta = decodeAll(content(ENV_META), { SCEnvMetaEntryXdr.decode(it) }, SCEnvMetaEntryXdr::encode)
        val meta = sections.filter { it.name == META }
            .flatMap { section -> decodeAll(section.content, { SCMetaEntryXdr.decode(it) }, SCMetaEntryXdr::encode) }
        val version = envMeta.first() as SCEnvMetaEntryXdr.InterfaceVersion
        return SorobanContractInfo(
            envInterfaceVersion = version.value.protocol.value.toULong(),
            specEntries = decodeAll(content(SPEC), { SCSpecEntryXdr.decode(it) }, SCSpecEntryXdr::encode),
            metaEntries = meta.map { (it as SCMetaEntryXdr.V0).value }.groupBy({ it.key }, { it.`val` })
                .mapValues { (key, values) -> if (key == "sep") values.joinToString(",") else values.last() }
        )
    }

    private companion object {
        const val SPEC = "contractspecv0"
        const val ENV_META = "contractenvmetav0"
        const val META = "contractmetav0"
        val SHIPPED_WASM = listOf(
            "p28_owner_fixture.wasm", "sep_45_account.wasm", "soroban_atomic_swap_contract.wasm",
            "soroban_auth_contract.wasm", "soroban_bindings_option_shapes_contract.wasm",
            "soroban_bindings_spec_test_contract.wasm", "soroban_events_contract.wasm",
            "soroban_hello_world_contract.wasm", "soroban_modular_account_contract.wasm", "soroban_token_contract.wasm"
        )
    }
}
