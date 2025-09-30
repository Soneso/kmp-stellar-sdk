package com.stellar.sdk

import org.apache.commons.codec.binary.Base32

/**
 * JVM-specific base32 encoding/decoding using Apache Commons Codec.
 */
internal actual object Base32Codec {
    private val codec = Base32()

    actual fun encode(data: ByteArray): ByteArray {
        return codec.encode(data)
    }

    actual fun decode(data: ByteArray): ByteArray {
        return codec.decode(data)
    }

    actual fun isInAlphabet(data: ByteArray): Boolean {
        return codec.isInAlphabet(data, true)
    }
}