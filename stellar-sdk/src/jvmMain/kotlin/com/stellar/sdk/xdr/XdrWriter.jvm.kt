// JVM implementation of XDR Writer
package com.stellar.sdk.xdr

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

actual class XdrWriter actual constructor() {
    private val byteStream = ByteArrayOutputStream()
    private val stream = DataOutputStream(byteStream)

    actual fun writeInt(value: Int) = stream.writeInt(value)

    actual fun writeUnsignedInt(value: UInt) = stream.writeInt(value.toInt())

    actual fun writeLong(value: Long) = stream.writeLong(value)

    actual fun writeUnsignedLong(value: ULong) = stream.writeLong(value.toLong())

    actual fun writeFloat(value: Float) = stream.writeFloat(value)

    actual fun writeDouble(value: Double) = stream.writeDouble(value)

    actual fun writeBoolean(value: Boolean) = stream.writeInt(if (value) 1 else 0)

    actual fun writeString(value: String) {
        val bytes = value.encodeToByteArray()
        stream.writeInt(bytes.size)
        stream.write(bytes)
        // Pad to 4-byte boundary
        val padding = (4 - (bytes.size % 4)) % 4
        repeat(padding) { stream.writeByte(0) }
    }

    actual fun writeFixedOpaque(value: ByteArray, expectedLength: Int?) {
        expectedLength?.let {
            require(value.size == it) { "Expected $it bytes, got ${value.size}" }
        }
        stream.write(value)
        // Pad to 4-byte boundary
        val padding = (4 - (value.size % 4)) % 4
        repeat(padding) { stream.writeByte(0) }
    }

    actual fun writeVariableOpaque(value: ByteArray) {
        stream.writeInt(value.size)
        writeFixedOpaque(value)
    }

    actual fun flush() = stream.flush()

    actual fun toByteArray(): ByteArray = byteStream.toByteArray()
}
