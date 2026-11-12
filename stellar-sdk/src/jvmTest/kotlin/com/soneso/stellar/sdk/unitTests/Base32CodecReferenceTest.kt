package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.Base32Codec
import com.soneso.stellar.sdk.Util
import org.apache.commons.codec.binary.Base32
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Compares [Base32Codec] with the base32 codec of Apache Commons Codec, an independent
 * implementation of RFC 4648.
 *
 * The inputs are random bytes from a fixed seed, so a failure reproduces on every run.
 */
class Base32CodecReferenceTest {

    private val reference = Base32()

    @Test
    fun testEncodeAndDecodeMatchApacheCommonsCodec() {
        val random = Random(SEED)
        for (length in 0..MAX_LENGTH) {
            repeat(INPUTS_PER_LENGTH) {
                val data = random.nextBytes(length)
                val label = "input ${Util.bytesToHex(data)}"

                val encoded = Base32Codec.encode(data)
                assertContentEquals(reference.encode(data), encoded, "encode of $label")

                // Decode reads the form a strkey is written in: the encoding without its padding.
                val padStart = encoded.indexOf('='.code.toByte())
                val unpadded = if (padStart < 0) encoded else encoded.copyOfRange(0, padStart)
                assertContentEquals(
                    reference.decode(unpadded), Base32Codec.decode(unpadded), "decode of $label"
                )
                assertContentEquals(data, Base32Codec.decode(unpadded), "round trip of $label")
            }
        }
    }

    private companion object {
        const val SEED = 20261112L
        const val MAX_LENGTH = 100
        const val INPUTS_PER_LENGTH = 40
    }
}
