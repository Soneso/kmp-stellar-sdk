package com.soneso.stellar.sdk.xdr

class XdrWriter {
    private val buffer = mutableListOf<Byte>()

    fun writeInt(value: Int) {
        buffer.add((value shr 24).toByte())
        buffer.add((value shr 16).toByte())
        buffer.add((value shr 8).toByte())
        buffer.add(value.toByte())
    }

    fun writeUnsignedInt(value: UInt) = writeInt(value.toInt())

    fun writeLong(value: Long) {
        writeInt((value shr 32).toInt())
        writeInt(value.toInt())
    }

    fun writeUnsignedLong(value: ULong) = writeLong(value.toLong())

    fun writeFloat(value: Float) = writeInt(value.toBits())

    fun writeDouble(value: Double) = writeLong(value.toBits())

    fun writeBoolean(value: Boolean) = writeInt(if (value) 1 else 0)

    fun writeString(value: String) {
        val bytes = value.encodeToByteArray()
        writeInt(bytes.size)
        buffer.addAll(bytes.toList())
        // Pad to 4-byte boundary
        val padding = (4 - (bytes.size % 4)) % 4
        repeat(padding) { buffer.add(0) }
    }

    fun writeFixedOpaque(value: ByteArray, expectedLength: Int? = null) {
        expectedLength?.let {
            require(value.size == it) { "Expected $it bytes, got ${value.size}" }
        }
        buffer.addAll(value.toList())
        // Pad to 4-byte boundary
        val padding = (4 - (value.size % 4)) % 4
        repeat(padding) { buffer.add(0) }
    }

    fun writeVariableOpaque(value: ByteArray) {
        writeInt(value.size)
        writeFixedOpaque(value)
    }

    fun flush() {} // No-op for in-memory buffer

    fun toByteArray(): ByteArray = buffer.toByteArray()
}

/** Writes [value] after checking its UTF-8 byte length against [maxLength]. */
internal fun XdrWriter.writeString(value: String, maxLength: Int) {
    writeStringBytes(value.encodeToByteArray(), maxLength)
}

/** Writes the bytes [value] of a string after checking their length against [maxLength]. */
internal fun XdrWriter.writeStringBytes(value: ByteArray, maxLength: Int) {
    require(value.size <= maxLength) { "XDR string length ${value.size} exceeds maximum $maxLength" }
    writeVariableOpaque(value)
}

/** Writes [value] after checking its byte length against [maxLength]. */
internal fun XdrWriter.writeVariableOpaque(value: ByteArray, maxLength: Int) {
    require(value.size <= maxLength) { "XDR opaque length ${value.size} exceeds maximum $maxLength" }
    writeVariableOpaque(value)
}
