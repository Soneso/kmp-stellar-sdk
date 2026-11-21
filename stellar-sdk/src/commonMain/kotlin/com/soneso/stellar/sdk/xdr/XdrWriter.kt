package com.soneso.stellar.sdk.xdr

expect class XdrWriter() {
    fun writeInt(value: Int)
    fun writeUnsignedInt(value: UInt)
    fun writeLong(value: Long)
    fun writeUnsignedLong(value: ULong)
    fun writeFloat(value: Float)
    fun writeDouble(value: Double)
    fun writeBoolean(value: Boolean)
    fun writeString(value: String)
    fun writeFixedOpaque(value: ByteArray, expectedLength: Int? = null)
    fun writeVariableOpaque(value: ByteArray)
    fun flush()
    fun toByteArray(): ByteArray
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
