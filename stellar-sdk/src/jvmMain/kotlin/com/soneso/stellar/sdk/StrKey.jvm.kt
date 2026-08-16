package com.soneso.stellar.sdk

import org.apache.commons.codec.binary.Base32

/**
 * JVM base32 codec, backed by Apache Commons Codec.
 */
internal actual object Base32Codec {
    private val codec = Base32()

    private val inAlphabet: BooleanArray = BooleanArray(256).apply {
        BASE32_ALPHABET.forEach { char -> this[char.code] = true }
    }

    actual fun encode(data: ByteArray): ByteArray {
        return codec.encode(data)
    }

    /**
     * Decodes [data], which must have passed [isInAlphabet].
     *
     * The underlying codec drops bytes it does not recognise instead of reporting them, and
     * bytes dropped that way would yield a result the input does not spell, so the alphabet is
     * enforced before the codec runs.
     *
     * @throws IllegalArgumentException if [data] holds a byte outside the base32 alphabet
     */
    actual fun decode(data: ByteArray): ByteArray {
        require(isInAlphabet(data)) { "Invalid base32 encoded string" }
        return codec.decode(data)
    }

    /**
     * Reports whether every byte of [data] is one of the 32 base32 alphabet characters.
     *
     * Membership is tested against that alphabet rather than against the underlying codec,
     * which also maps the lowercase letters and the pad character and would therefore admit
     * spellings of a key that no encoder produces.
     */
    actual fun isInAlphabet(data: ByteArray): Boolean {
        for (byte in data) {
            if (!inAlphabet[byte.toInt() and 0xFF]) {
                return false
            }
        }
        return true
    }
}
