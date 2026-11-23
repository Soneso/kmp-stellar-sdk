//
//  XdrFixedLengthJsonTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.AssetCode12Xdr
import com.soneso.stellar.sdk.xdr.AssetCode4Xdr
import com.soneso.stellar.sdk.xdr.DecoratedSignatureXdr
import com.soneso.stellar.sdk.xdr.FreezeBypassTxsXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractCodeXdr
import com.soneso.stellar.sdk.xdr.MemoXdr
import com.soneso.stellar.sdk.xdr.ShortHashSeedXdr
import com.soneso.stellar.sdk.xdr.SignatureHintXdr
import com.soneso.stellar.sdk.xdr.SignatureXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * A fixed-length opaque value (`opaque[N]` inline or through a typedef such as `Hash`) and an
 * asset code have exactly the width their XDR definition declares. The XDR-JSON writers refuse
 * any other width with the message of the binary encoder, so no value reaches XDR-JSON text
 * that the readers refuse or read back as another value. A value of the declared width is
 * written as lowercase hexadecimal, an asset code as its code with the trailing NUL padding
 * trimmed, and reads back unchanged.
 */
class XdrFixedLengthJsonTest {

    // The bytes 0x00 to 0x1f: the 32-byte value of a `Hash`, and its first 31 bytes.
    private val hash32 = ByteArray(32) { it.toByte() }
    private val hash32Hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    private val hash31 = ByteArray(31) { it.toByte() }
    private val hash31Hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e"

    // The bytes 0x00 to 0x20, one byte more than a `Hash` holds.
    private val hash33 = ByteArray(33) { it.toByte() }
    private val hash33Hex = hash32Hex + "20"

    // The bytes 0x20 to 0x3f: a second, distinct 32-byte value.
    private val otherHash32 = ByteArray(32) { (it + 0x20).toByte() }
    private val otherHash32Hex = "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f"

    /**
     * Asserts that the XDR-JSON writer and the binary encoder both refuse a value with
     * [message].
     */
    private fun assertBothRefuse(message: String, toJson: () -> String, encode: (XdrWriter) -> Unit) {
        assertEquals(message, assertFailsWith<IllegalArgumentException> { toJson() }.message)
        assertEquals(message, assertFailsWith<IllegalArgumentException> { encode(XdrWriter()) }.message)
    }

    private fun assertReadRefused(message: String, read: () -> Any) {
        assertEquals(message, assertFailsWith<IllegalArgumentException> { read() }.message)
    }

    // -------------------------------------------------------------------------------------
    // A `Hash` struct field
    // -------------------------------------------------------------------------------------

    @Test
    fun hashFieldOfThirtyTwoBytesWritesItsHexAndReadsBack() {
        val text = """{"hash":"$hash32Hex"}"""
        assertEquals(text, LedgerKeyContractCodeXdr(HashXdr(hash32)).toXdrJson())
        assertContentEquals(hash32, LedgerKeyContractCodeXdr.fromXdrJson(text).hash.value)
    }

    @Test
    fun hashFieldOfThirtyOneBytesIsRefused() {
        val value = LedgerKeyContractCodeXdr(HashXdr(hash31))
        assertBothRefuse("Expected 32 bytes, got 31", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("HashXdr: key \"value\" expects exactly 32 bytes, got 31") {
            LedgerKeyContractCodeXdr.fromXdrJson("""{"hash":"$hash31Hex"}""")
        }
    }

    @Test
    fun hashFieldOfThirtyThreeBytesIsRefused() {
        val value = LedgerKeyContractCodeXdr(HashXdr(hash33))
        assertBothRefuse("Expected 32 bytes, got 33", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("HashXdr: key \"value\" expects exactly 32 bytes, got 33") {
            LedgerKeyContractCodeXdr.fromXdrJson("""{"hash":"$hash33Hex"}""")
        }
    }

    // -------------------------------------------------------------------------------------
    // A typedef `opaque[4]` field: the hint of a decorated signature
    // -------------------------------------------------------------------------------------

    // The signature is a variable-length `opaque<64>`; the two bytes 0xab 0xcd stand for it.
    private val signature = SignatureXdr(byteArrayOf(0xab.toByte(), 0xcd.toByte()))

    @Test
    fun signatureHintOfFourBytesWritesItsHexAndReadsBack() {
        val text = """{"hint":"01020304","signature":"abcd"}"""
        val value = DecoratedSignatureXdr(SignatureHintXdr(byteArrayOf(1, 2, 3, 4)), signature)
        assertEquals(text, value.toXdrJson())
        val read = DecoratedSignatureXdr.fromXdrJson(text)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), read.hint.value)
        assertContentEquals(signature.value, read.signature.value)
    }

    @Test
    fun signatureHintOfThreeBytesIsRefused() {
        val value = DecoratedSignatureXdr(SignatureHintXdr(byteArrayOf(1, 2, 3)), signature)
        assertBothRefuse("Expected 4 bytes, got 3", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("SignatureHintXdr: key \"value\" expects exactly 4 bytes, got 3") {
            DecoratedSignatureXdr.fromXdrJson("""{"hint":"010203","signature":"abcd"}""")
        }
    }

    @Test
    fun signatureHintOfFiveBytesIsRefused() {
        val value = DecoratedSignatureXdr(SignatureHintXdr(byteArrayOf(1, 2, 3, 4, 5)), signature)
        assertBothRefuse("Expected 4 bytes, got 5", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("SignatureHintXdr: key \"value\" expects exactly 4 bytes, got 5") {
            DecoratedSignatureXdr.fromXdrJson("""{"hint":"0102030405","signature":"abcd"}""")
        }
    }

    // -------------------------------------------------------------------------------------
    // An inline `opaque[16]` struct field
    // -------------------------------------------------------------------------------------

    @Test
    fun shortHashSeedOfSixteenBytesWritesItsHexAndReadsBack() {
        val seed = ByteArray(16) { it.toByte() }
        val text = """{"seed":"000102030405060708090a0b0c0d0e0f"}"""
        assertEquals(text, ShortHashSeedXdr(seed).toXdrJson())
        assertContentEquals(seed, ShortHashSeedXdr.fromXdrJson(text).seed)
    }

    @Test
    fun shortHashSeedOfFifteenBytesIsRefused() {
        val value = ShortHashSeedXdr(ByteArray(15) { it.toByte() })
        assertBothRefuse("Expected 16 bytes, got 15", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("ShortHashSeedXdr: key \"seed\" expects exactly 16 bytes, got 15") {
            ShortHashSeedXdr.fromXdrJson("""{"seed":"000102030405060708090a0b0c0d0e"}""")
        }
    }

    @Test
    fun shortHashSeedOfSeventeenBytesIsRefused() {
        val value = ShortHashSeedXdr(ByteArray(17) { it.toByte() })
        assertBothRefuse("Expected 16 bytes, got 17", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("ShortHashSeedXdr: key \"seed\" expects exactly 16 bytes, got 17") {
            ShortHashSeedXdr.fromXdrJson("""{"seed":"000102030405060708090a0b0c0d0e0f10"}""")
        }
    }

    // -------------------------------------------------------------------------------------
    // A union arm: the hash of a memo
    // -------------------------------------------------------------------------------------

    @Test
    fun memoHashOfThirtyTwoBytesWritesItsHexAndReadsBack() {
        val text = """{"hash":"$hash32Hex"}"""
        assertEquals(text, MemoXdr.Hash(HashXdr(hash32)).toXdrJson())
        assertContentEquals(hash32, assertIs<MemoXdr.Hash>(MemoXdr.fromXdrJson(text)).value.value)
    }

    @Test
    fun memoHashOfThirtyOneBytesIsRefused() {
        val value = MemoXdr.Hash(HashXdr(hash31))
        assertBothRefuse("Expected 32 bytes, got 31", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("HashXdr: key \"value\" expects exactly 32 bytes, got 31") {
            MemoXdr.fromXdrJson("""{"hash":"$hash31Hex"}""")
        }
    }

    // -------------------------------------------------------------------------------------
    // An element of an array of `Hash`
    // -------------------------------------------------------------------------------------

    @Test
    fun hashArrayOfThirtyTwoByteElementsWritesEachElementAsHexAndReadsBack() {
        val text = """{"tx_hashes":["$hash32Hex","$otherHash32Hex"]}"""
        assertEquals(text, FreezeBypassTxsXdr(listOf(HashXdr(hash32), HashXdr(otherHash32))).toXdrJson())
        val read = FreezeBypassTxsXdr.fromXdrJson(text).txHashes
        assertEquals(2, read.size)
        assertContentEquals(hash32, read[0].value)
        assertContentEquals(otherHash32, read[1].value)
    }

    @Test
    fun hashArrayWithAThirtyOneByteElementIsRefused() {
        val value = FreezeBypassTxsXdr(listOf(HashXdr(hash32), HashXdr(hash31)))
        assertBothRefuse("Expected 32 bytes, got 31", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("HashXdr: key \"value\" expects exactly 32 bytes, got 31") {
            FreezeBypassTxsXdr.fromXdrJson("""{"tx_hashes":["$hash32Hex","$hash31Hex"]}""")
        }
    }

    // -------------------------------------------------------------------------------------
    // Asset codes
    // -------------------------------------------------------------------------------------

    // The ASCII bytes of "USD" (0x55 0x53 0x44), "USDCX", "ABCDEFGHIJK" and "ABCDEFGHIJKLM".
    private val usd = byteArrayOf(0x55, 0x53, 0x44)
    private val usdcx = byteArrayOf(0x55, 0x53, 0x44, 0x43, 0x58)
    private val elevenLetters = "ABCDEFGHIJK".encodeToByteArray()
    private val thirteenLetters = "ABCDEFGHIJKLM".encodeToByteArray()

    @Test
    fun assetCodeOfFourBytesWithOneNulByteWritesTheCodeWithoutTheNulAndReadsBack() {
        val code = usd + byteArrayOf(0)
        assertEquals("\"USD\"", AssetCode4Xdr(code).toXdrJson())
        assertContentEquals(code, AssetCode4Xdr.fromXdrJson("\"USD\"").value)
    }

    @Test
    fun assetCodeOfFourHoldingThreeBytesIsRefused() {
        val value = AssetCode4Xdr(usd)
        assertBothRefuse("Expected 4 bytes, got 3", { value.toXdrJson() }, { value.encode(it) })
    }

    @Test
    fun assetCodeOfFourHoldingFiveBytesIsRefused() {
        val value = AssetCode4Xdr(usdcx)
        assertBothRefuse("Expected 4 bytes, got 5", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("AssetCode4Xdr: expects an asset code of at most 4 bytes, got 5") {
            AssetCode4Xdr.fromXdrJson("\"USDCX\"")
        }
    }

    @Test
    fun assetCodeOfTwelveBytesWithOneNulByteWritesTheCodeWithoutTheNulAndReadsBack() {
        val code = elevenLetters + byteArrayOf(0)
        assertEquals("\"ABCDEFGHIJK\"", AssetCode12Xdr(code).toXdrJson())
        assertContentEquals(code, AssetCode12Xdr.fromXdrJson("\"ABCDEFGHIJK\"").value)
    }

    @Test
    fun assetCodeOfTwelveHoldingElevenBytesIsRefused() {
        val value = AssetCode12Xdr(elevenLetters)
        assertBothRefuse("Expected 12 bytes, got 11", { value.toXdrJson() }, { value.encode(it) })
    }

    @Test
    fun assetCodeOfTwelveHoldingThirteenBytesIsRefused() {
        val value = AssetCode12Xdr(thirteenLetters)
        assertBothRefuse("Expected 12 bytes, got 13", { value.toXdrJson() }, { value.encode(it) })
        assertReadRefused("AssetCode12Xdr: expects an asset code of at most 12 bytes, got 13") {
            AssetCode12Xdr.fromXdrJson("\"ABCDEFGHIJKLM\"")
        }
    }
}
