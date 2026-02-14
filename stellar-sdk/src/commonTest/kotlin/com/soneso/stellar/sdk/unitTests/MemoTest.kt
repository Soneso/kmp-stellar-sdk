package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

class MemoTest {

    @Test
    fun testMemoNone() {
        val memo = MemoNone

        assertTrue(memo is MemoNone)
        assertEquals("", memo.toString())

        val xdr = memo.toXdr()
        assertTrue(xdr is MemoXdr.Void)
        assertEquals(MemoTypeXdr.MEMO_NONE, xdr.discriminant)
    }

    @Test
    fun testMemoNoneFromXdr() {
        val xdr = MemoXdr.Void
        val memo = Memo.fromXdr(xdr)

        assertTrue(memo is MemoNone)
        assertEquals(MemoNone, memo)
    }

    @Test
    fun testMemoText() {
        val text = "Hello Stellar"
        val memo = MemoText(text)

        assertEquals(text, memo.text)
        assertEquals(text.encodeToByteArray().size, memo.bytes.size)
        assertEquals(text, memo.toString())

        val xdr = memo.toXdr()
        assertTrue(xdr is MemoXdr.Text)
        assertEquals(text, (xdr as MemoXdr.Text).value)
    }

    @Test
    fun testMemoTextFromBytes() {
        val text = "Test"
        val bytes = text.encodeToByteArray()
        val memo = MemoText(bytes)

        assertEquals(text, memo.text)
        assertTrue(memo.bytes.contentEquals(bytes))
    }

    @Test
    fun testMemoTextMaxLength() {
        // 28 bytes is the maximum
        val text28Bytes = "1234567890123456789012345678"
        assertEquals(28, text28Bytes.encodeToByteArray().size)

        val memo = MemoText(text28Bytes)
        assertEquals(text28Bytes, memo.text)
    }

    @Test
    fun testMemoTextTooLong() {
        val text29Bytes = "12345678901234567890123456789"
        assertEquals(29, text29Bytes.encodeToByteArray().size)

        assertFailsWith<IllegalArgumentException> {
            MemoText(text29Bytes)
        }
    }

    @Test
    fun testMemoTextUtf8() {
        // UTF-8 characters can be multiple bytes
        val text = "Hello 世界" // "Hello World" in Chinese
        val bytes = text.encodeToByteArray()

        if (bytes.size <= 28) {
            val memo = MemoText(text)
            assertEquals(text, memo.text)
        } else {
            assertFailsWith<IllegalArgumentException> {
                MemoText(text)
            }
        }
    }

    @Test
    fun testMemoTextEquality() {
        val memo1 = MemoText("test")
        val memo2 = MemoText("test")
        val memo3 = MemoText("different")

        assertEquals(memo1, memo2)
        assertNotEquals(memo1, memo3)
        assertEquals(memo1.hashCode(), memo2.hashCode())
    }

    @Test
    fun testMemoTextFromXdr() {
        val text = "Test memo"
        val xdr = MemoXdr.Text(text)
        val memo = Memo.fromXdr(xdr) as MemoText

        assertEquals(text, memo.text)
    }

    @Test
    fun testMemoId() {
        val id = 123456789UL
        val memo = MemoId(id)

        assertEquals(id, memo.id)
        assertEquals(id.toString(), memo.toString())

        val xdr = memo.toXdr()
        assertTrue(xdr is MemoXdr.Id)
        assertEquals(id, (xdr as MemoXdr.Id).value.value)
    }

    @Test
    fun testMemoIdZero() {
        val memo = MemoId(0UL)
        assertEquals(0UL, memo.id)
        assertEquals("0", memo.toString())
    }

    @Test
    fun testMemoIdMaxValue() {
        val memo = MemoId(ULong.MAX_VALUE)
        assertEquals(ULong.MAX_VALUE, memo.id)
    }

    @Test
    fun testMemoIdEquality() {
        val memo1 = MemoId(123UL)
        val memo2 = MemoId(123UL)
        val memo3 = MemoId(456UL)

        assertEquals(memo1, memo2)
        assertNotEquals(memo1, memo3)
        assertEquals(memo1.hashCode(), memo2.hashCode())
    }

    @Test
    fun testMemoIdFromXdr() {
        val id = 999999UL
        val xdr = MemoXdr.Id(Uint64Xdr(id))
        val memo = Memo.fromXdr(xdr) as MemoId

        assertEquals(id, memo.id)
    }

    @Test
    fun testMemoHash() {
        val hash = ByteArray(32) { it.toByte() }
        val memo = MemoHash(hash)

        assertTrue(memo.bytes.contentEquals(hash))
        assertEquals(32, memo.bytes.size)

        val xdr = memo.toXdr()
        assertTrue(xdr is MemoXdr.Hash)
        assertTrue((xdr as MemoXdr.Hash).value.value.contentEquals(hash))
    }

    @Test
    fun testMemoHashFromHexString() {
        val hexString = "e98869bba8bce08c10b78406202127f3888c25454cd37b02600862452751f526"
        val memo = MemoHash(hexString)

        assertEquals(32, memo.bytes.size)
        assertEquals(hexString, memo.hexValue)
        assertEquals(hexString, memo.toString())
    }

    @Test
    fun testMemoHashFromUppercaseHex() {
        val hexString = "E98869BBA8BCE08C10B78406202127F3888C25454CD37B02600862452751F526"
        val memo = MemoHash(hexString)

        assertEquals(32, memo.bytes.size)
        assertEquals(hexString.lowercase(), memo.hexValue)
    }

    @Test
    fun testMemoHashInvalidLength() {
        val hash31Bytes = ByteArray(31)
        assertFailsWith<IllegalArgumentException> {
            MemoHash(hash31Bytes)
        }

        val hash33Bytes = ByteArray(33)
        assertFailsWith<IllegalArgumentException> {
            MemoHash(hash33Bytes)
        }
    }

    @Test
    fun testMemoHashInvalidHexLength() {
        val hexString63Chars = "e98869bba8bce08c10b78406202127f3888c25454cd37b02600862452751f52"
        assertFailsWith<IllegalArgumentException> {
            MemoHash(hexString63Chars)
        }

        val hexString65Chars = "e98869bba8bce08c10b78406202127f3888c25454cd37b02600862452751f5266"
        assertFailsWith<IllegalArgumentException> {
            MemoHash(hexString65Chars)
        }
    }

    @Test
    fun testMemoHashEquality() {
        val hash = ByteArray(32) { it.toByte() }
        val memo1 = MemoHash(hash)
        val memo2 = MemoHash(hash.copyOf())
        val memo3 = MemoHash(ByteArray(32))

        assertEquals(memo1, memo2)
        assertNotEquals(memo1, memo3)
        assertEquals(memo1.hashCode(), memo2.hashCode())
    }

    @Test
    fun testMemoHashFromXdr() {
        val hash = ByteArray(32) { it.toByte() }
        val xdr = MemoXdr.Hash(HashXdr(hash))
        val memo = Memo.fromXdr(xdr) as MemoHash

        assertTrue(memo.bytes.contentEquals(hash))
    }

    @Test
    fun testMemoReturn() {
        val hash = ByteArray(32) { (31 - it).toByte() }
        val memo = MemoReturn(hash)

        assertTrue(memo.bytes.contentEquals(hash))
        assertEquals(32, memo.bytes.size)

        val xdr = memo.toXdr()
        assertTrue(xdr is MemoXdr.RetHash)
        assertTrue((xdr as MemoXdr.RetHash).value.value.contentEquals(hash))
    }

    @Test
    fun testMemoReturnFromHexString() {
        val hexString = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        val memo = MemoReturn(hexString)

        assertEquals(32, memo.bytes.size)
        assertEquals(hexString, memo.hexValue)
        assertEquals(hexString, memo.toString())
    }

    @Test
    fun testMemoReturnFromUppercaseHex() {
        val hexString = "FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210"
        val memo = MemoReturn(hexString)

        assertEquals(32, memo.bytes.size)
        assertEquals(hexString.lowercase(), memo.hexValue)
    }

    @Test
    fun testMemoReturnInvalidLength() {
        val hash31Bytes = ByteArray(31)
        assertFailsWith<IllegalArgumentException> {
            MemoReturn(hash31Bytes)
        }

        val hash33Bytes = ByteArray(33)
        assertFailsWith<IllegalArgumentException> {
            MemoReturn(hash33Bytes)
        }
    }

    @Test
    fun testMemoReturnInvalidHexLength() {
        val hexString63Chars = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba987654321"
        assertFailsWith<IllegalArgumentException> {
            MemoReturn(hexString63Chars)
        }
    }

    @Test
    fun testMemoReturnEquality() {
        val hash = ByteArray(32) { it.toByte() }
        val memo1 = MemoReturn(hash)
        val memo2 = MemoReturn(hash.copyOf())
        val memo3 = MemoReturn(ByteArray(32))

        assertEquals(memo1, memo2)
        assertNotEquals(memo1, memo3)
        assertEquals(memo1.hashCode(), memo2.hashCode())
    }

    @Test
    fun testMemoReturnFromXdr() {
        val hash = ByteArray(32) { it.toByte() }
        val xdr = MemoXdr.RetHash(HashXdr(hash))
        val memo = Memo.fromXdr(xdr) as MemoReturn

        assertTrue(memo.bytes.contentEquals(hash))
    }

    @Test
    fun testMemoXdrRoundTrip() {
        // MemoNone
        val memo1 = MemoNone
        val xdr1 = memo1.toXdr()
        val restored1 = Memo.fromXdr(xdr1)
        assertEquals(memo1, restored1)

        // MemoText
        val memo2 = MemoText("Hello")
        val xdr2 = memo2.toXdr()
        val restored2 = Memo.fromXdr(xdr2)
        assertEquals(memo2, restored2)

        // MemoId
        val memo3 = MemoId(12345UL)
        val xdr3 = memo3.toXdr()
        val restored3 = Memo.fromXdr(xdr3)
        assertEquals(memo3, restored3)

        // MemoHash
        val memo4 = MemoHash("e98869bba8bce08c10b78406202127f3888c25454cd37b02600862452751f526")
        val xdr4 = memo4.toXdr()
        val restored4 = Memo.fromXdr(xdr4)
        assertEquals(memo4, restored4)

        // MemoReturn
        val memo5 = MemoReturn("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210")
        val xdr5 = memo5.toXdr()
        val restored5 = Memo.fromXdr(xdr5)
        assertEquals(memo5, restored5)
    }

    @Test
    fun testMemoTypes() {
        val memoNone: Memo = MemoNone
        val memoText: Memo = MemoText("test")
        val memoId: Memo = MemoId(123UL)
        val memoHash: Memo = MemoHash(ByteArray(32))
        val memoReturn: Memo = MemoReturn(ByteArray(32))

        assertTrue(memoNone is MemoNone)
        assertTrue(memoText is MemoText)
        assertTrue(memoId is MemoId)
        assertTrue(memoHash is MemoHash)
        assertTrue(memoReturn is MemoReturn)
    }

    @Test
    fun testHexConversion() {
        val hexString = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val memoHash = MemoHash(hexString)
        assertEquals(hexString, memoHash.hexValue)

        val memoReturn = MemoReturn(hexString)
        assertEquals(hexString, memoReturn.hexValue)
    }

    @Test
    fun testEmptyText() {
        val memo = MemoText("")
        assertEquals("", memo.text)
        assertEquals(0, memo.bytes.size)
    }

    @Test
    fun testZeroHash() {
        val zeroHash = ByteArray(32) { 0 }
        val memo = MemoHash(zeroHash)

        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", memo.hexValue)
    }

    @Test
    fun testAllOnesHash() {
        val onesHash = ByteArray(32) { 0xFF.toByte() }
        val memo = MemoHash(onesHash)

        assertEquals("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", memo.hexValue)
    }
}
