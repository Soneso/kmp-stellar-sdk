// Copyright 2024 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.xdr.*

/**
 * Parses a Soroban contract byte code to extract Environment Meta, Contract Spec, and Contract Meta.
 *
 * This parser implements the Soroban contract specification as described in:
 * https://developers.stellar.org/docs/tools/sdks/build-your-own
 *
 * The contract byte code (WASM) contains embedded metadata in custom sections:
 * - contractenvmetav0: Environment metadata (protocol version)
 * - contractspecv0: Contract specification entries (functions, structs, unions, enums, events)
 * - contractmetav0: Contract metadata (key-value pairs for application/tooling use)
 *
 * The parser walks the sections of the module by their wasm framing. It reads the first
 * contractenvmetav0 and the first contractspecv0 section, and every contractmetav0 section in
 * module order.
 *
 * Example usage:
 * ```kotlin
 * val wasmBytes = // ... load WASM file
 * try {
 *     val contractInfo = SorobanContractParser.parseContractByteCode(wasmBytes)
 *     println("Protocol version: ${contractInfo.envInterfaceVersion}")
 *     contractInfo.specEntries.forEach { entry ->
 *         // Process spec entries
 *     }
 * } catch (e: SorobanContractParserException) {
 *     println("Failed to parse contract: ${e.message}")
 * }
 * ```
 */
object SorobanContractParser {

    private const val ENV_META_SECTION = "contractenvmetav0"
    private const val SPEC_SECTION = "contractspecv0"
    private const val META_SECTION = "contractmetav0"
    private val SECTION_NAMES = setOf(ENV_META_SECTION, SPEC_SECTION, META_SECTION)
    private const val SEP_KEY = "sep"
    private const val CUSTOM_SECTION_ID = 0
    private val WASM_MAGIC = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
    private val WASM_VERSION = byteArrayOf(0x01, 0x00, 0x00, 0x00)

    /**
     * Parses a Soroban contract byte code to extract Environment Meta, Contract Spec, and Contract Meta.
     *
     * Every section of the module must be well framed. The first `contractenvmetav0` and the first
     * `contractspecv0` custom section are read and later sections of these names are ignored.
     * Every `contractmetav0` section is read, in module order, and a later entry replaces an earlier
     * one with the same key, except that the values of repeated `sep` entries are joined with a
     * comma, as SEP-47 defines. Each section is read entry by entry to its end, no entry spans two
     * sections, and every entry must decode. The interface version comes from the first environment
     * meta entry; a module without a `contractmetav0` section has no meta entries.
     *
     * @param byteCode The WASM byte code of the Soroban contract
     * @return [SorobanContractInfo] containing the parsed environment version, spec entries, and meta entries
     * @throws SorobanContractParserException if the byte code is not a wasm module, a section is
     *   malformed, the environment meta or spec section is missing, the first environment meta
     *   section holds no entry, or an entry does not decode, such as an entry of an unknown kind.
     *   Framing and entry decoding errors name the byte offset in the module.
     */
    fun parseContractByteCode(byteCode: ByteArray): SorobanContractInfo {
        val sections = findSections(byteCode)

        val envMeta = sections[ENV_META_SECTION].orEmpty().firstOrNull()
            ?: throw SorobanContractParserException("Invalid byte code: environment meta not found.")
        val envMetaEntries = decodeEntries(byteCode, envMeta) { SCEnvMetaEntryXdr.decode(it) }
        val interfaceVersion = when (val first = envMetaEntries.firstOrNull()) {
            is SCEnvMetaEntryXdr.InterfaceVersion -> first.value.protocol.value.toULong()
            null -> throw SorobanContractParserException("Invalid byte code: environment interface version not found.")
        }

        val spec = sections[SPEC_SECTION].orEmpty().firstOrNull()
            ?: throw SorobanContractParserException("Invalid byte code: spec entries not found.")
        val specEntries = decodeEntries(byteCode, spec) { SCSpecEntryXdr.decode(it) }

        val metaEntries = mutableMapOf<String, String>()
        for (meta in sections[META_SECTION].orEmpty()) {
            for (entry in decodeEntries(byteCode, meta) { SCMetaEntryXdr.decode(it) }) {
                when (entry) {
                    is SCMetaEntryXdr.V0 -> {
                        val (key, value) = entry.value.key to entry.value.`val`
                        val earlier = metaEntries[key]
                        metaEntries[key] = if (key == SEP_KEY && earlier != null) "$earlier,$value" else value
                    }
                }
            }
        }

        return SorobanContractInfo(
            envInterfaceVersion = interfaceVersion,
            specEntries = specEntries,
            metaEntries = metaEntries
        )
    }

    /** The content of the custom section [name], from byte offset [start] to [end] (exclusive) in the module. */
    private class Section(val name: String, val start: Int, val end: Int)

    /**
     * Walks every section of [byteCode] and returns the custom sections of each name in
     * [SECTION_NAMES], in module order. Each section needs a LEB128 u32 size whose payload lies
     * within the module, and a custom section also a LEB128 u32 name length whose name lies within
     * the section. Other payloads are skipped unread, so a section name inside a data segment or
     * the code section never matches.
     */
    private fun findSections(byteCode: ByteArray): Map<String, List<Section>> {
        checkHeader(byteCode)
        val found = mutableMapOf<String, MutableList<Section>>()
        var offset = WASM_MAGIC.size + WASM_VERSION.size
        while (offset < byteCode.size) {
            val sectionOffset = offset
            val id = byteCode[offset].toInt() and 0xFF
            val (size, payloadStart) =
                readU32(byteCode, offset + 1, byteCode.size, "size of the section at byte offset $sectionOffset")
            val remaining = byteCode.size - payloadStart
            if (size > remaining) {
                throw invalid(
                    "the section at byte offset $sectionOffset (id $id) declares $size bytes, but $remaining remain"
                )
            }
            val end = payloadStart + size.toInt()
            if (id == CUSTOM_SECTION_ID) {
                val nameLengthOf = "name length of the custom section at byte offset $sectionOffset"
                val (nameLength, nameStart) = readU32(byteCode, payloadStart, end, nameLengthOf)
                if (nameLength > end - nameStart) {
                    throw invalid(
                        "the name of the custom section at byte offset $sectionOffset declares $nameLength bytes, " +
                            "but ${end - nameStart} remain in the section"
                    )
                }
                val nameEnd = nameStart + nameLength.toInt()
                val name = byteCode.copyOfRange(nameStart, nameEnd).decodeToString()
                if (name in SECTION_NAMES) {
                    found.getOrPut(name) { mutableListOf() }.add(Section(name, nameEnd, end))
                }
            }
            offset = end
        }
        return found
    }

    private fun checkHeader(byteCode: ByteArray) {
        val headerSize = WASM_MAGIC.size + WASM_VERSION.size
        if (byteCode.size < headerSize) {
            throw invalid(
                "the module has ${byteCode.size} bytes, fewer than the $headerSize bytes of the wasm header " +
                    "at byte offset 0"
            )
        }
        val magic = byteCode.copyOfRange(0, WASM_MAGIC.size)
        if (!magic.contentEquals(WASM_MAGIC)) {
            throw invalid("expected the wasm magic ${hex(WASM_MAGIC)} at byte offset 0, found ${hex(magic)}")
        }
        val version = byteCode.copyOfRange(WASM_MAGIC.size, headerSize)
        if (!version.contentEquals(WASM_VERSION)) {
            throw invalid(
                "expected the wasm version ${hex(WASM_VERSION)} at byte offset ${WASM_MAGIC.size}, " +
                    "found ${hex(version)}"
            )
        }
    }

    /**
     * Reads the LEB128 u32 [what] that starts at [offset] and must end before [end]. It has at most
     * 5 bytes, and the fifth holds only the top 4 bits of the value; padded forms such as
     * `80 80 80 80 00` are valid wasm.
     *
     * @return the value and the offset after its last byte
     */
    private fun readU32(bytes: ByteArray, offset: Int, end: Int, what: String): Pair<Long, Int> {
        var value = 0L
        var position = offset
        var shift = 0
        while (true) {
            if (position >= end) throw invalid("the $what is cut off at byte offset $end")
            val byte = bytes[position++].toInt() and 0xFF
            if (shift == 28 && (byte and 0xF0) != 0) {
                val problem = if ((byte and 0x80) != 0) "is longer than 5 bytes" else "exceeds the u32 range"
                throw invalid("the $what $problem")
            }
            value = value or ((byte and 0x7F).toLong() shl shift)
            if ((byte and 0x80) == 0) return value to position
            shift += 7
        }
    }

    /**
     * Decodes the entries of [section] one after the other until its content is consumed.
     *
     * @throws SorobanContractParserException if an entry does not decode, which includes an
     *   unknown entry kind and trailing bytes that do not form an entry
     */
    private fun <T> decodeEntries(byteCode: ByteArray, section: Section, decode: (XdrReader) -> T): List<T> {
        val content = byteCode.copyOfRange(section.start, section.end)
        val reader = XdrReader(content)
        val entries = mutableListOf<T>()
        while (reader.position < content.size) {
            val entryOffset = section.start + reader.position
            try {
                entries.add(decode(reader))
            } catch (e: Exception) {
                throw invalid("the ${section.name} entry at byte offset $entryOffset does not decode: ${e.message}")
            }
        }
        return entries
    }

    private fun invalid(detail: String) = SorobanContractParserException("Invalid byte code: $detail.")

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/**
 * Exception thrown when the [SorobanContractParser] fails to parse the given byte code.
 *
 * @property message Detailed error message describing the parsing failure
 */
class SorobanContractParserException(message: String) : Exception(message)

/**
 * Stores information parsed from a Soroban contract byte code, including
 * Environment Meta, Contract Spec Entries, and Contract Meta Entries.
 *
 * See also: https://developers.stellar.org/docs/tools/sdks/build-your-own
 *
 * @property envInterfaceVersion Environment interface protocol version from Environment Meta
 * @property specEntries Contract Spec Entries. There is a SCSpecEntry for every function, struct,
 *                       union, enum, error enum, and event exported by the contract.
 * @property metaEntries Contract Meta Entries as key-value pairs. Contracts may store any metadata
 *                       that can be used by applications and tooling off-network.
 */
data class SorobanContractInfo(
    val envInterfaceVersion: ULong,
    val specEntries: List<SCSpecEntryXdr>,
    val metaEntries: Map<String, String>
) {
    /**
     * Returns the list of supported SEP (Stellar Ecosystem Proposal) numbers from the contract metadata.
     *
     * This property parses the "sep" meta value, the comma-separated SEP numbers of every "sep" entry
     * (e.g., "1, 10, 24"). The values are parsed, trimmed, deduplicated, and returned as a list.
     *
     * Example:
     * ```kotlin
     * val contractInfo = SorobanContractParser.parseContractByteCode(wasmBytes)
     * val seps = contractInfo.supportedSeps // e.g., ["1", "10", "24"]
     * ```
     *
     * @return List of SEP numbers as strings, or empty list if no "sep" meta entry exists
     */
    val supportedSeps: List<String>
        get() {
            val sepValue = metaEntries["sep"] ?: return emptyList()
            if (sepValue.isBlank()) return emptyList()

            return sepValue
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }

    /**
     * Returns all function specifications from the contract spec entries.
     *
     * @return List of function specifications
     */
    val funcs: List<SCSpecFunctionV0Xdr>
        get() = specEntries.mapNotNull { entry ->
            when (entry) {
                is SCSpecEntryXdr.FunctionV0 -> entry.value
                else -> null
            }
        }

    /**
     * Returns all UDT struct specifications from the contract spec entries.
     *
     * @return List of struct specifications
     */
    val udtStructs: List<SCSpecUDTStructV0Xdr>
        get() = specEntries.mapNotNull { entry ->
            when (entry) {
                is SCSpecEntryXdr.UdtStructV0 -> entry.value
                else -> null
            }
        }

    /**
     * Returns all UDT union specifications from the contract spec entries.
     *
     * @return List of union specifications
     */
    val udtUnions: List<SCSpecUDTUnionV0Xdr>
        get() = specEntries.mapNotNull { entry ->
            when (entry) {
                is SCSpecEntryXdr.UdtUnionV0 -> entry.value
                else -> null
            }
        }

    /**
     * Returns all UDT enum specifications from the contract spec entries.
     *
     * @return List of enum specifications
     */
    val udtEnums: List<SCSpecUDTEnumV0Xdr>
        get() = specEntries.mapNotNull { entry ->
            when (entry) {
                is SCSpecEntryXdr.UdtEnumV0 -> entry.value
                else -> null
            }
        }

    /**
     * Returns all UDT error enum specifications from the contract spec entries.
     *
     * @return List of error enum specifications
     */
    val udtErrorEnums: List<SCSpecUDTErrorEnumV0Xdr>
        get() = specEntries.mapNotNull { entry ->
            when (entry) {
                is SCSpecEntryXdr.UdtErrorEnumV0 -> entry.value
                else -> null
            }
        }

    /**
     * Returns all event specifications from the contract spec entries.
     *
     * @return List of event specifications
     */
    val events: List<SCSpecEventV0Xdr>
        get() = specEntries.mapNotNull { entry ->
            when (entry) {
                is SCSpecEntryXdr.EventV0 -> entry.value
                else -> null
            }
        }
}
