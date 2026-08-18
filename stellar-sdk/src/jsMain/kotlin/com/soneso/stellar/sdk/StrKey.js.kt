package com.soneso.stellar.sdk

/**
 * JavaScript base32 codec.
 *
 * A pure Kotlin implementation, used in both browser and Node.js environments, that carries the
 * same strictness as the codecs on the other platforms: the 32 alphabet characters are the only
 * input it accepts, so the pad character, whitespace and every other byte are rejected.
 */
internal actual object Base32Codec {
    private val NOT_IN_ALPHABET: Byte = 0xff.toByte()

    private val decodingTable: ByteArray = ByteArray(256) { NOT_IN_ALPHABET }.apply {
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

    /**
     * Decodes [data], which must have passed [isInAlphabet].
     *
     * A byte outside the alphabet carries no five-bit value, so decoding it would either
     * corrupt the result or cut it short. Such a byte is reported instead.
     *
     * @throws IllegalArgumentException if [data] holds a byte outside the base32 alphabet
     */
    actual fun decode(data: ByteArray): ByteArray {
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            val value = decodingTable[byte.toInt() and 0xFF]
            require(value != NOT_IN_ALPHABET) { INVALID_BASE32_MESSAGE }

            buffer = (buffer shl 5) or value.toInt()
            bitsLeft += 5

            if (bitsLeft >= 8) {
                output.add(((buffer ushr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }

        return output.toByteArray()
    }

    /**
     * Reports whether every byte of [data] is one of the 32 base32 alphabet characters.
     *
     * The pad character and whitespace are not among them: a strkey is written without padding,
     * and accepting either would let one key be written as more than one string.
     */
    actual fun isInAlphabet(data: ByteArray): Boolean {
        for (byte in data) {
            if (decodingTable[byte.toInt() and 0xFF] == NOT_IN_ALPHABET) {
                return false
            }
        }
        return true
    }
}
