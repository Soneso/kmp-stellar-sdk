package com.soneso.stellar.sdk.unitTests.sep.sep05.crypto

import com.soneso.stellar.sdk.sep.sep05.crypto.intToBytesBigEndian
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** Helpers of the SEP-5 crypto layer, checked against literal bytes on every target. */
class Sep05CryptoTest {

    @Test
    fun testIntToBytesBigEndian() {
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), intToBytesBigEndian(0x01020304))
        assertContentEquals(byteArrayOf(-1, -1, -1, -1), intToBytesBigEndian(-1))
    }
}
