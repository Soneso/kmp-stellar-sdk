//
//  DecoratedSignatureTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.DecoratedSignature
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.DecoratedSignatureXdr
import com.soneso.stellar.sdk.xdr.SignatureHintXdr
import com.soneso.stellar.sdk.xdr.SignatureXdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for DecoratedSignature: construction, XDR round-trip, and value-based
 * equals/hashCode/toString overrides.
 */
class DecoratedSignatureTest {

    private val hint = byteArrayOf(1, 2, 3, 4)
    private val signature = ByteArray(64) { it.toByte() }

    @Test
    fun testConstructionExposesHintAndSignature() {
        val ds = DecoratedSignature(hint, signature)
        assertTrue(hint.contentEquals(ds.hint))
        assertTrue(signature.contentEquals(ds.signature))
    }

    @Test
    fun testConstructorRejectsWrongHintSize() {
        val exception = assertFailsWith<IllegalArgumentException> {
            DecoratedSignature(byteArrayOf(1, 2, 3), signature)
        }
        assertTrue(exception.message!!.contains("4 bytes"))
    }

    @Test
    fun testConstructorRejectsOversizedSignature() {
        val exception = assertFailsWith<IllegalArgumentException> {
            DecoratedSignature(hint, ByteArray(65))
        }
        assertTrue(exception.message!!.contains("at most 64 bytes"))
    }

    @Test
    fun testConstructorAcceptsShorterSignature() {
        // Signatures are validated with an upper bound only; shorter arrays (e.g. hashX
        // preimages used as signatures) are valid.
        val ds = DecoratedSignature(hint, ByteArray(10))
        assertEquals(10, ds.signature.size)
    }

    @Test
    fun testToXdrRoundTrip() {
        val ds = DecoratedSignature(hint, signature)
        val xdr = ds.toXdr()
        assertTrue(hint.contentEquals(xdr.hint.value))
        assertTrue(signature.contentEquals(xdr.signature.value))

        val restored = DecoratedSignature.fromXdr(xdr)
        assertEquals(ds, restored)
    }

    @Test
    fun testFromXdr() {
        val xdr = DecoratedSignatureXdr(
            hint = SignatureHintXdr(hint),
            signature = SignatureXdr(signature)
        )
        val ds = DecoratedSignature.fromXdr(xdr)
        assertTrue(hint.contentEquals(ds.hint))
        assertTrue(signature.contentEquals(ds.signature))
    }

    @Test
    fun testEqualsReflexiveNullAndDifferentType() {
        val ds = DecoratedSignature(hint, signature)
        assertEquals(ds, ds)
        assertFalse(ds.equals(null))
        assertFalse(ds.equals("not a decorated signature"))
    }

    @Test
    fun testEqualsAndHashCodeForEqualValues() {
        val ds1 = DecoratedSignature(hint.copyOf(), signature.copyOf())
        val ds2 = DecoratedSignature(hint.copyOf(), signature.copyOf())
        assertEquals(ds1, ds2)
        assertEquals(ds1.hashCode(), ds2.hashCode())
    }

    @Test
    fun testNotEqualsForDifferentHint() {
        val ds1 = DecoratedSignature(byteArrayOf(1, 2, 3, 4), signature)
        val ds2 = DecoratedSignature(byteArrayOf(5, 6, 7, 8), signature)
        assertFalse(ds1 == ds2)
    }

    @Test
    fun testNotEqualsForDifferentSignature() {
        val ds1 = DecoratedSignature(hint, ByteArray(64) { it.toByte() })
        val ds2 = DecoratedSignature(hint, ByteArray(64) { (it + 1).toByte() })
        assertFalse(ds1 == ds2)
    }

    @Test
    fun testToStringContainsHexEncodedHint() {
        val ds = DecoratedSignature(hint, signature)
        val str = ds.toString()
        assertTrue(str.contains(Util.bytesToHex(hint)))
        assertTrue(str.contains("DecoratedSignature"))
    }
}
