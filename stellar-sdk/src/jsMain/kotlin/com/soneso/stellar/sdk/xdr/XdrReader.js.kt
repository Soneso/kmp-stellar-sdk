// JS implementation of XDR Reader
package com.soneso.stellar.sdk.xdr

actual class XdrReader actual constructor(input: ByteArray) {
    private val data = input
    private var offset = 0

    actual fun readInt(): Int {
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
        val bytes = data.sliceArray(offset until offset + length)
        offset += length
        // Skip padding
        val padding = (4 - (length % 4)) % 4
        offset += padding
        return bytes.decodeToString()
    }

    actual fun readFixedOpaque(length: Int): ByteArray {
        val bytes = data.sliceArray(offset until offset + length)
        offset += length
        // Skip padding
        val padding = (4 - (length % 4)) % 4
        offset += padding
        return bytes
    }

    actual fun readVariableOpaque(): ByteArray {
        val length = readInt()
        return readFixedOpaque(length)
    }
}
