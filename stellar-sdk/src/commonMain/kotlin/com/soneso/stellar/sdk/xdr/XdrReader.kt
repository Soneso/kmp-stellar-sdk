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
     * Checks [cap] before incrementing the shared recursion depth counter.
     *
     * Call on entry to a self-containing XDR decode function.
     * Must be paired with [exitRecursion] in a try/finally block.
     *
     * @param cap Maximum allowed nesting depth (inclusive)
     * @throws IllegalArgumentException if depth exceeds [cap]
     */
    fun enterRecursion(cap: Int)

    /**
     * Decrements the recursion depth counter.
     *
     * Must be called in a finally block after each successful [enterRecursion].
     */
    fun exitRecursion()
}

/** Reads a string whose UTF-8 byte length does not exceed [maxLength]. */
internal fun XdrReader.readString(maxLength: Int): String {
    val length = readInt()
    require(length <= maxLength) { "XDR string length $length exceeds maximum $maxLength" }
    return readFixedOpaque(length).decodeToString()
}

/** Reads an opaque value whose byte length does not exceed [maxLength]. */
internal fun XdrReader.readVariableOpaque(maxLength: Int): ByteArray {
    val length = readInt()
    require(length <= maxLength) { "XDR opaque length $length exceeds maximum $maxLength" }
    return readFixedOpaque(length)
}

/** Reads an array count bounded by [maxCount] and the remaining bytes. */
internal fun XdrReader.readArrayLength(maxCount: Int): Int {
    val count = readArrayLength()
    require(count <= maxCount) { "XDR array count $count exceeds maximum $maxCount" }
    return count
}
