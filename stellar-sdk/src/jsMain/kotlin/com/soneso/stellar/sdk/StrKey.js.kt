package com.soneso.stellar.sdk

/**
 * JavaScript implementation of Base32 encoding/decoding.
 *
 * This is a pure Kotlin implementation that works in both browser and Node.js environments.
 * It's suitable for JavaScript because:
 * - Base32 encoding is not security-critical (just data transformation)
 * - No native JS Base32 library in browsers
 * - Small implementation, well-tested
 */
internal actual object Base32Codec {
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private val decodingTable: ByteArray = ByteArray(256) { 0xff.toByte() }.apply {
        BASE32_ALPHABET.forEachIndexed { index, char ->
            this[char.code] = index.toByte()
        }
    }

    actual fun encode(data: ByteArray): ByteArray {
        if (data.isEmpty()) return byteArrayOf()

        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8

            while (bitsLeft >= 5) {
                val index = (buffer ushr (bitsLeft - 5)) and 0x1F
                output.add(BASE32_ALPHABET[index].code.toByte())
                bitsLeft -= 5
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            output.add(BASE32_ALPHABET[index].code.toByte())
        }

        // Add padding
        while (output.size % 8 != 0) {
            output.add('='.code.toByte())
        }

        return output.toByteArray()
    }

    actual fun decode(data: ByteArray): ByteArray {
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            if (byte == '='.code.toByte()) break

            val value = decodingTable[byte.toInt() and 0xFF].toInt() and 0xFF
            buffer = (buffer shl 5) or value
            bitsLeft += 5

            if (bitsLeft >= 8) {
                output.add(((buffer ushr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }

        return output.toByteArray()
    }

    actual fun isInAlphabet(data: ByteArray): Boolean {
        for (byte in data) {
            if (byte == '='.code.toByte()) continue
            val index = byte.toInt() and 0xFF
            if (index >= decodingTable.size || decodingTable[index] == 0xff.toByte()) {
                return false
            }
        }
        return true
    }
}