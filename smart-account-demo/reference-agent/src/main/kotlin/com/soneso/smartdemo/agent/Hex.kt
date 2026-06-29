package com.soneso.smartdemo.agent

/**
 * Lowercase-hex encoding and validation for raw byte values.
 *
 * The agent renders 32-byte Ed25519 seeds and public keys as 64-character
 * lowercase hex (matching the demo's "Delegate to agent" screen), and parses
 * operator-supplied hex seeds back into bytes.
 */
object Hex {
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    /** Encodes [bytes] as a lowercase hex string with no separators or prefix. */
    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            out.append(HEX_DIGITS[value ushr 4])
            out.append(HEX_DIGITS[value and 0x0F])
        }
        return out.toString()
    }

    /**
     * Returns whether [value] is a non-empty, even-length string of only hex
     * digits.
     */
    fun isHexString(value: String): Boolean {
        if (value.isEmpty() || value.length % 2 != 0) return false
        return value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    /**
     * Decodes [value] into raw bytes, or returns `null` when [value] is not a
     * valid even-length hex string.
     */
    fun decode(value: String): ByteArray? {
        if (!isHexString(value)) return null
        val out = ByteArray(value.length / 2)
        var i = 0
        while (i < value.length) {
            val hi = Character.digit(value[i], 16)
            val lo = Character.digit(value[i + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i / 2] = ((hi shl 4) or lo).toByte()
            i += 2
        }
        return out
    }
}
