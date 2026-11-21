package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.xdr.SCEnvMetaEntryInterfaceVersionXdr
import com.soneso.stellar.sdk.xdr.SCEnvMetaEntryXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter

/** The 8-byte wasm header: the magic `00 61 73 6d` and version 1. */
internal val WASM_HEADER = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)

/** The shortest unsigned LEB128 encoding of [value]. */
internal fun leb128(value: Int): ByteArray {
    val bytes = mutableListOf<Byte>()
    var rest = value
    do {
        bytes.add(((rest and 0x7F) or (if (rest ushr 7 != 0) 0x80 else 0)).toByte())
        rest = rest ushr 7
    } while (rest != 0)
    return bytes.toByteArray()
}

/** A wasm section with the id [id] and [payload], its size in the shortest LEB128. */
internal fun wasmSection(id: Int, payload: ByteArray): ByteArray =
    byteArrayOf(id.toByte()) + leb128(payload.size) + payload

/** A custom section (id 0) named [name] that holds [content]. */
internal fun customSection(name: String, content: ByteArray): ByteArray {
    val nameBytes = name.encodeToByteArray()
    return wasmSection(0, leb128(nameBytes.size) + nameBytes + content)
}

/** A wasm module: the header followed by [sections]. */
internal fun wasmModule(vararg sections: ByteArray): ByteArray =
    sections.fold(WASM_HEADER) { module, section -> module + section }

internal fun xdrBytes(encode: (XdrWriter) -> Unit): ByteArray = XdrWriter().also(encode).toByteArray()

/** A module with an environment meta section (interface version 23) and a `contractspecv0` section of [spec]. */
internal fun moduleWithSpec(spec: ByteArray): ByteArray = wasmModule(
    customSection("contractenvmetav0", xdrBytes {
        SCEnvMetaEntryXdr.InterfaceVersion(SCEnvMetaEntryInterfaceVersionXdr(Uint32Xdr(23u), Uint32Xdr(0u))).encode(it)
    }),
    customSection("contractspecv0", spec)
)

/** A module whose `contractspecv0` section holds 4 bytes, at byte offset 57, that do not decode as a spec entry. */
internal fun undecodableSpecModule(): ByteArray = moduleWithSpec(ByteArray(4) { 0xFF.toByte() })

/** The parser's message for [undecodableSpecModule]. */
internal const val UNDECODABLE_SPEC_ERROR = "Invalid byte code: the contractspecv0 entry at byte offset 57 " +
    "does not decode: Unknown SCSpecEntryKindXdr value: -1."
