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
