// JS implementation of XDR Writer
package com.soneso.stellar.sdk.xdr

actual class XdrWriter actual constructor() {
    private val buffer = mutableListOf<Byte>()

    actual fun writeInt(value: Int) {
        buffer.add((value shr 24).toByte())
        buffer.add((value shr 16).toByte())
        buffer.add((value shr 8).toByte())
        buffer.add(value.toByte())
    }

    actual fun writeUnsignedInt(value: UInt) = writeInt(value.toInt())

    actual fun writeLong(value: Long) {
        writeInt((value shr 32).toInt())
        writeInt(value.toInt())
    }

    actual fun writeUnsignedLong(value: ULong) = writeLong(value.toLong())

    actual fun writeFloat(value: Float) = writeInt(value.toBits())

    actual fun writeDouble(value: Double) = writeLong(value.toBits())

    actual fun writeBoolean(value: Boolean) = writeInt(if (value) 1 else 0)

    actual fun writeString(value: String) {
        val bytes = value.encodeToByteArray()
        writeInt(bytes.size)
        buffer.addAll(bytes.toList())
        // Pad to 4-byte boundary
        val padding = (4 - (bytes.size % 4)) % 4
        repeat(padding) { buffer.add(0) }
    }

    actual fun writeFixedOpaque(value: ByteArray, expectedLength: Int?) {
        expectedLength?.let {
            require(value.size == it) { "Expected $it bytes, got ${value.size}" }
        }
        buffer.addAll(value.toList())
        // Pad to 4-byte boundary
        val padding = (4 - (value.size % 4)) % 4
        repeat(padding) { buffer.add(0) }
    }

    actual fun writeVariableOpaque(value: ByteArray) {
        writeInt(value.size)
        writeFixedOpaque(value)
    }

    actual fun flush() {} // No-op for in-memory buffer

    actual fun toByteArray(): ByteArray = buffer.toByteArray()
}
