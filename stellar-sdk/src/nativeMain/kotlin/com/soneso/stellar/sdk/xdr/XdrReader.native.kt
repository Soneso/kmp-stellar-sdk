// Native implementation of XDR Reader
package com.soneso.stellar.sdk.xdr

actual class XdrReader actual constructor(input: ByteArray) {
    private val data = input
    private var offset = 0
    private var recursionDepth: Int = 0

    /**
     * Fails unless [count] more bytes can be read at the current [offset].
     *
     * XDR carries no stream-level length prefix, so a truncated or malformed buffer is only
     * detectable at the read that would run past the end. The check is explicit rather than
     * left to array-index behavior, which is not uniform across Kotlin targets.
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

    actual fun readInt(): Int {
        requireAvailable(4)
        val value = ((data[offset].toInt() and 0xFF) shl 24) or
                    ((data[offset + 1].toInt() and 0xFF) shl 16) or
                    ((data[offset + 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 3].toInt() and 0xFF)
        offset += 4
        return value
    }

    actual fun readUnsignedInt(): UInt = readInt().toUInt()

    actual fun readLong(): Long {
        val high = readInt().toLong()
        val low = readInt().toLong() and 0xFFFFFFFFL
        return (high shl 32) or low
    }

    actual fun readUnsignedLong(): ULong = readLong().toULong()

    actual fun readFloat(): Float = Float.fromBits(readInt())

    actual fun readDouble(): Double = Double.fromBits(readLong())

    actual fun readBoolean(): Boolean = readInt() != 0

    actual fun readString(): String {
        val length = readInt()
        requireAvailable(length)
        val bytes = data.sliceArray(offset until offset + length)
        offset += length
        // Trailing padding is skipped but not required to be present, matching the JVM reader;
        // a buffer ending on the last data byte then fails at the next read instead of here.
        val padding = (4 - (length % 4)) % 4
        offset += padding
        return bytes.decodeToString()
    }

    actual fun readFixedOpaque(length: Int): ByteArray {
        requireAvailable(length)
        val bytes = data.sliceArray(offset until offset + length)
        offset += length
        // Trailing padding is skipped but not required to be present, matching the JVM reader.
        val padding = (4 - (length % 4)) % 4
        offset += padding
        return bytes
    }

    actual fun readVariableOpaque(): ByteArray {
        val length = readInt()
        return readFixedOpaque(length)
    }

    actual fun enterRecursion(cap: Int) {
        recursionDepth++
        if (recursionDepth > cap) {
            throw IllegalArgumentException(
                "XDR decode recursion depth $recursionDepth exceeds cap $cap"
            )
        }
    }

    actual fun exitRecursion() {
        recursionDepth--
    }
}
