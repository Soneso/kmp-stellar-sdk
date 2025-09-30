package com.stellar.sdk

import kotlin.test.Test

class StrKeyDebugTest {

    @Test
    fun debugEncoding() {
        val publicKey = byteArrayOf(
            0x3f.toByte(), 0x0c.toByte(), 0x34.toByte(), 0xbf.toByte(),
            0x93.toByte(), 0xad.toByte(), 0x0d.toByte(), 0x99.toByte(),
            0x71.toByte(), 0xd0.toByte(), 0x4c.toByte(), 0xcc.toByte(),
            0x90.toByte(), 0xf7.toByte(), 0x05.toByte(), 0x51.toByte(),
            0x1c.toByte(), 0x83.toByte(), 0x8a.toByte(), 0xad.toByte(),
            0x97.toByte(), 0x34.toByte(), 0xa4.toByte(), 0xa2.toByte(),
            0xfb.toByte(), 0x0d.toByte(), 0x7a.toByte(), 0x03.toByte(),
            0xfc.toByte(), 0x7f.toByte(), 0xe8.toByte(), 0x9a.toByte()
        )

        val encoded = StrKey.encodeEd25519PublicKey(publicKey)
        println("Encoded: $encoded")
        println("Expected: GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D")

        val decoded = StrKey.decodeEd25519PublicKey(encoded)
        println("Decoded matches: ${publicKey.contentEquals(decoded)}")
    }
}