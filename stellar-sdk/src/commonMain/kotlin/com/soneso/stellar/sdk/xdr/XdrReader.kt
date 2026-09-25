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
     * Reads the element count of a variable-length XDR array and validates it against the
     * remaining bytes before the caller allocates the array.
     *
     * Every XDR array element occupies at least 4 bytes (scalars, enum and union discriminants,
     * optional flags and length prefixes are 4 bytes; fixed opaque data is padded to 4), so a
     * count above a quarter of the remaining bytes cannot be satisfied by the buffer. Rejecting
     * it here keeps a hostile count from sizing a large allocation.
     *
     * @return The element count, between 0 and a quarter of the remaining bytes (inclusive).
     * @throws IllegalArgumentException if the count is negative or exceeds a quarter of the
     *   remaining bytes.
     */
    fun readArrayLength(): Int

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
