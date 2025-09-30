package com.stellar.sdk.xdr

expect class XdrReader(input: ByteArray) {
    fun readInt(): Int
    fun readUnsignedInt(): UInt
    fun readLong(): Long
    fun readUnsignedLong(): ULong
    fun readFloat(): Float
    fun readDouble(): Double
    fun readBoolean(): Boolean
    fun readString(): String
    fun readFixedOpaque(length: Int): ByteArray
    fun readVariableOpaque(): ByteArray
}
