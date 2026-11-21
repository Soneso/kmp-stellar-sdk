package com.soneso.stellar.sdk.xdr

expect class XdrReader(input: ByteArray) {
    /** The number of bytes read so far, which is the offset of the next byte in the input. */
    internal val position: Int

    fun readInt(): Int
    fun readUnsignedInt(): UInt
    fun readLong(): Long
    fun readUnsignedLong(): ULong
    fun readFloat(): Float
    fun readDouble(): Double
    fun readBoolean(): Boolean

    /**
     * Reads a string of any length and decodes its bytes as strict UTF-8, every leading byte
     * order mark kept.
     *
     * @throws CharacterCodingException if the bytes are not valid UTF-8
     */
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

/**
 * Reads a string whose byte length does not exceed [maxLength] and decodes its bytes as strict
 * UTF-8, every leading byte order mark kept.
 *
 * @throws CharacterCodingException if the bytes are not valid UTF-8
 */
internal fun XdrReader.readString(maxLength: Int): String =
    readStringBytes(maxLength).decodeToString(throwOnInvalidSequence = true)

/** Reads the bytes of a string whose byte length does not exceed [maxLength]. */
internal fun XdrReader.readStringBytes(maxLength: Int): ByteArray {
    val length = readInt()
    require(length <= maxLength) { "XDR string length $length exceeds maximum $maxLength" }
    return readFixedOpaque(length)
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
