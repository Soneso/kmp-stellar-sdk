package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

class MuxedAccountTest {

    private val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    @Test
    fun testCreateFromAccountId() {
        val muxed = MuxedAccount(accountId)

        assertEquals(accountId, muxed.accountId)
        assertNull(muxed.id)
        assertEquals(accountId, muxed.address)
    }

    @Test
    fun testCreateFromAccountIdWithMuxedId() {
        val muxedId = 123456789UL
        val muxed = MuxedAccount(accountId, muxedId)

        assertEquals(accountId, muxed.accountId)
        assertEquals(muxedId, muxed.id)
        assertTrue(muxed.address.startsWith("M"))
        assertNotEquals(accountId, muxed.address)
    }

    @Test
    fun testCreateFromRegularAddressString() {
        val muxed = MuxedAccount(accountId)

        assertEquals(accountId, muxed.accountId)
        assertNull(muxed.id)
        assertEquals(accountId, muxed.address)
    }

    @Test
    fun testInvalidAccountId() {
        assertFailsWith<IllegalArgumentException> {
            MuxedAccount("INVALID")
        }
    }

    @Test
    fun testInvalidAddress() {
        assertFailsWith<IllegalArgumentException> {
            MuxedAccount("SINVALID")
        }
    }

    @Test
    fun testToXdrRegularAccount() {
        val muxed = MuxedAccount(accountId)
        val xdr = muxed.toXdr()

        assertTrue(xdr is MuxedAccountXdr.Ed25519)
        assertEquals(CryptoKeyTypeXdr.KEY_TYPE_ED25519, xdr.discriminant)

        val ed25519Xdr = xdr as MuxedAccountXdr.Ed25519
        val decodedKey = StrKey.decodeEd25519PublicKey(accountId)
        assertTrue(ed25519Xdr.value.value.contentEquals(decodedKey))
    }

    @Test
    fun testToXdrMuxedAccount() {
        val muxedId = 123456789UL
        val muxed = MuxedAccount(accountId, muxedId)
        val xdr = muxed.toXdr()

        assertTrue(xdr is MuxedAccountXdr.Med25519)
        assertEquals(CryptoKeyTypeXdr.KEY_TYPE_MUXED_ED25519, xdr.discriminant)

        val med25519Xdr = xdr as MuxedAccountXdr.Med25519
        assertEquals(muxedId, med25519Xdr.value.id.value)

        val decodedKey = StrKey.decodeEd25519PublicKey(accountId)
        assertTrue(med25519Xdr.value.ed25519.value.contentEquals(decodedKey))
    }

    @Test
    fun testFromXdrRegularAccount() {
        val ed25519Bytes = StrKey.decodeEd25519PublicKey(accountId)
        val xdr = MuxedAccountXdr.Ed25519(Uint256Xdr(ed25519Bytes))

        val muxed = MuxedAccount.fromXdr(xdr)

        assertEquals(accountId, muxed.accountId)
        assertNull(muxed.id)
        assertEquals(accountId, muxed.address)
    }

    @Test
    fun testFromXdrMuxedAccount() {
        val muxedId = 999999UL
        val ed25519Bytes = StrKey.decodeEd25519PublicKey(accountId)
        val med25519 = MuxedAccountMed25519Xdr(
            id = Uint64Xdr(muxedId),
            ed25519 = Uint256Xdr(ed25519Bytes)
        )
        val xdr = MuxedAccountXdr.Med25519(med25519)

        val muxed = MuxedAccount.fromXdr(xdr)

        assertEquals(accountId, muxed.accountId)
        assertEquals(muxedId, muxed.id)
        assertTrue(muxed.address.startsWith("M"))
    }

    @Test
    fun testXdrRoundTrip() {
        // Regular account
        val muxed1 = MuxedAccount(accountId)
        val xdr1 = muxed1.toXdr()
        val restored1 = MuxedAccount.fromXdr(xdr1)
        assertEquals(muxed1, restored1)

        // Muxed account
        val muxed2 = MuxedAccount(accountId, 12345UL)
        val xdr2 = muxed2.toXdr()
        val restored2 = MuxedAccount.fromXdr(xdr2)
        assertEquals(muxed2, restored2)
    }

    @Test
    fun testAddressRoundTrip() {
        // Regular account
        val muxed1 = MuxedAccount(accountId)
        val address1 = muxed1.address
        val restored1 = MuxedAccount(address1)
        assertEquals(muxed1, restored1)

        // Muxed account - round trip through address
        val muxed2 = MuxedAccount(accountId, 99999UL)
        val address2 = muxed2.address
        val restored2 = MuxedAccount(address2)
        assertEquals(muxed2, restored2)
    }

    @Test
    fun testEquality() {
        val muxed1 = MuxedAccount(accountId)
        val muxed2 = MuxedAccount(accountId)
        val muxed3 = MuxedAccount(accountId, 123UL)
        val muxed4 = MuxedAccount(accountId, 123UL)
        val muxed5 = MuxedAccount(accountId, 456UL)

        assertEquals(muxed1, muxed2)
        assertNotEquals(muxed1, muxed3)
        assertEquals(muxed3, muxed4)
        assertNotEquals(muxed3, muxed5)

        assertEquals(muxed1.hashCode(), muxed2.hashCode())
        assertEquals(muxed3.hashCode(), muxed4.hashCode())
    }

    @Test
    fun testToString() {
        val muxed1 = MuxedAccount(accountId)
        val str1 = muxed1.toString()
        assertTrue(str1.contains(accountId))

        val muxed2 = MuxedAccount(accountId, 123UL)
        val str2 = muxed2.toString()
        assertTrue(str2.contains(accountId))
        assertTrue(str2.contains("123"))
        assertTrue(str2.contains("M"))
    }

    @Test
    fun testMuxedIdRange() {
        // Test minimum ID
        val muxed1 = MuxedAccount(accountId, 0UL)
        assertEquals(0UL, muxed1.id)

        // Test maximum ID
        val muxed2 = MuxedAccount(accountId, ULong.MAX_VALUE)
        assertEquals(ULong.MAX_VALUE, muxed2.id)

        // Round trip with max value
        val address = muxed2.address
        val restored = MuxedAccount(address)
        assertEquals(muxed2.id, restored.id)
    }

    @Test
    fun testDifferentAccountIds() {
        val accountId2 = "GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEYVXM3XOJMDS674JZ"

        val muxed1 = MuxedAccount(accountId, 123UL)
        val muxed2 = MuxedAccount(accountId2, 123UL)

        assertNotEquals(muxed1, muxed2)
        assertNotEquals(muxed1.address, muxed2.address)
    }

    @Test
    fun testMuxedAddressFormat() {
        val muxedId = 100UL
        val muxed = MuxedAccount(accountId, muxedId)

        val address = muxed.address
        assertTrue(address.startsWith("M"), "Muxed address should start with M")
        assertTrue(StrKey.isValidMed25519PublicKey(address), "Should be a valid M... address")

        // Verify we can decode it back
        val decoded = MuxedAccount(address)
        assertEquals(accountId, decoded.accountId)
        assertEquals(muxedId, decoded.id)
    }

    @Test
    fun testNullMuxedId() {
        val muxed1 = MuxedAccount(accountId, null)
        val muxed2 = MuxedAccount(accountId)

        assertEquals(muxed1, muxed2)
        assertNull(muxed1.id)
        assertNull(muxed2.id)
        assertEquals(accountId, muxed1.address)
        assertEquals(accountId, muxed2.address)
    }
}
