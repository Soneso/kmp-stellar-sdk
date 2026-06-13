package com.soneso.stellar.sdk.xdr

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

    /**
     * Increments the recursion depth counter and throws if the depth exceeds [cap].
     *
     * Call before each recursive invocation of a self-referential XDR decode function.
     * Must be paired with [exitRecursion] in a try/finally block.
     *
     * @param cap Maximum allowed nesting depth (inclusive)
     * @throws IllegalArgumentException if depth exceeds [cap]
     */
    fun enterRecursion(cap: Int)

    /**
     * Decrements the recursion depth counter.
     *
     * Must be called in a finally block after each [enterRecursion].
     */
    fun exitRecursion()
}
