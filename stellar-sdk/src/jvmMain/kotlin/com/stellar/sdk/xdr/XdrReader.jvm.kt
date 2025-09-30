// JVM implementation of XDR Reader
package com.stellar.sdk.xdr

import java.io.ByteArrayInputStream
import java.io.DataInputStream

actual class XdrReader actual constructor(input: ByteArray) {
    private val stream = DataInputStream(ByteArrayInputStream(input))

    actual fun readInt(): Int = stream.readInt()

    actual fun readUnsignedInt(): UInt = stream.readInt().toUInt()

    actual fun readLong(): Long = stream.readLong()

    actual fun readUnsignedLong(): ULong = stream.readLong().toULong()

    actual fun readFloat(): Float = stream.readFloat()

    actual fun readDouble(): Double = stream.readDouble()

    actual fun readBoolean(): Boolean = stream.readInt() != 0

    actual fun readString(): String {
        val length = stream.readInt()
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        // Skip padding to 4-byte boundary
        val padding = (4 - (length % 4)) % 4
        stream.skipBytes(padding)
        return bytes.decodeToString()
    }

    actual fun readFixedOpaque(length: Int): ByteArray {
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        // Skip padding to 4-byte boundary
        val padding = (4 - (length % 4)) % 4
        stream.skipBytes(padding)
        return bytes
    }

    actual fun readVariableOpaque(): ByteArray {
        val length = stream.readInt()
        return readFixedOpaque(length)
    }
}
