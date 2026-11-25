package com.soneso.stellar.sdk.xdr

class XdrReader(input: ByteArray) {
    private val data = input
    private var offset = 0
    private var recursionDepth: Int = 0

    /** The number of bytes read so far, which is the offset of the next byte in the input. */
    internal val position: Int
        get() = offset

    /**
     * Fails unless [count] more bytes can be read at the current [offset].
     *
     * XDR carries no stream-level length prefix, so a truncated or malformed buffer is only
     * detectable at the read that would run past the end. Length prefixes are checked here
     * before any allocation, so a hostile prefix cannot trigger a large one.
     *
     * @param count Number of bytes the caller is about to consume.
     * @throws IllegalArgumentException if [count] is negative or exceeds the remaining bytes.
     */
    private fun requireAvailable(count: Int) {
        if (count < 0) {
            throw IllegalArgumentException("XDR decode length cannot be negative, got $count")
        }
        // Subtraction rather than offset + count: the sum overflows for a hostile length.
        if (count > data.size - offset) {
            val remaining = if (offset >= data.size) 0 else data.size - offset
            throw IllegalArgumentException(
                "XDR decode requires $count byte(s) at offset $offset " +
                    "but only $remaining remain in a ${data.size}-byte buffer"
            )
        }
    }

    fun readInt(): Int {
        requireAvailable(4)
        val value = ((data[offset].toInt() and 0xFF) shl 24) or
                    ((data[offset + 1].toInt() and 0xFF) shl 16) or
                    ((data[offset + 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 3].toInt() and 0xFF)
        offset += 4
        return value
    }

    fun readUnsignedInt(): UInt = readInt().toUInt()

    fun readLong(): Long {
        val high = readInt().toLong()
        val low = readInt().toLong() and 0xFFFFFFFFL
        return (high shl 32) or low
    }

    fun readUnsignedLong(): ULong = readLong().toULong()

    fun readFloat(): Float = Float.fromBits(readInt())

    fun readDouble(): Double = Double.fromBits(readLong())

    /**
     * Reads an XDR boolean or optional presence flag: 0 is false and 1 is true.
     *
     * @throws IllegalArgumentException if the value is neither 0 nor 1.
     */
    fun readBoolean(): Boolean {
        val index = offset
        val value = readInt()
        require(value == 0 || value == 1) { "XDR boolean at offset $index must be 0 or 1, got $value" }
        return value == 1
    }

    /**
     * Reads a string of any length and decodes its bytes as strict UTF-8, every leading byte
     * order mark kept.
     *
     * @throws CharacterCodingException if the bytes are not valid UTF-8
     */
    fun readString(): String = readFixedOpaque(readInt()).decodeToString(throwOnInvalidSequence = true)

    fun readFixedOpaque(length: Int): ByteArray {
        requireAvailable(length)
        val bytes = data.copyOfRange(offset, offset + length)
        offset += length
        // Padding is required and every padding byte must be zero.
        val padding = (4 - (length % 4)) % 4
        requireAvailable(padding)
        repeat(padding) {
            val index = offset++
            val value = data[index].toInt() and 0xFF
            require(value == 0) { "XDR padding at offset $index must be zero, got $value" }
        }
        return bytes
    }

    fun readVariableOpaque(): ByteArray {
        val length = readInt()
        return readFixedOpaque(length)
    }

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
    fun readArrayLength(): Int {
        val count = readInt()
        if (count < 0) {
            throw IllegalArgumentException("XDR array count cannot be negative, got $count")
        }
        // The count was read in full, so offset does not exceed data.size here.
        val remaining = data.size - offset
        if (count > remaining / 4) {
            throw IllegalArgumentException(
                "XDR array count $count exceeds the maximum of ${remaining / 4} " +
                    "for the $remaining byte(s) remaining at offset $offset"
            )
        }
        return count
    }

    /**
     * Checks [cap] before incrementing the shared recursion depth counter.
     *
     * Call on entry to a self-containing XDR decode function.
     * Must be paired with [exitRecursion] in a try/finally block.
     *
     * @param cap Maximum allowed nesting depth (inclusive)
     * @throws IllegalArgumentException if depth exceeds [cap]
     */
    fun enterRecursion(cap: Int) {
        if (recursionDepth >= cap) {
            throw IllegalArgumentException(
                "XDR decode recursion depth ${recursionDepth + 1} exceeds cap $cap"
            )
        }
        recursionDepth++
    }

    /**
     * Decrements the recursion depth counter.
     *
     * Must be called in a finally block after each successful [enterRecursion].
     */
    fun exitRecursion() {
        recursionDepth--
    }
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
