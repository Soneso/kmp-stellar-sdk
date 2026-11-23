package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.DataValueXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.ManageDataOpXdr
import com.soneso.stellar.sdk.xdr.MemoXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SCVecXdr
import com.soneso.stellar.sdk.xdr.SequenceNumberXdr
import com.soneso.stellar.sdk.xdr.String64Xdr
import com.soneso.stellar.sdk.xdr.TimeBoundsXdr
import com.soneso.stellar.sdk.xdr.TimePointXdr
import com.soneso.stellar.sdk.xdr.TransactionV0ExtXdr
import com.soneso.stellar.sdk.xdr.TransactionV0Xdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XdrBooleanTest {
    // The one-byte data name occupies a length word and a padded word.
    private val dataFlagOffset = 8
    // An SCVal discriminant occupies one word before its bool or optional arm.
    private val scValFlagOffset = 4
    // TransactionV0 starts with a 32-byte account, a 4-byte fee and an 8-byte sequence.
    private val timeBoundsFlagOffset = 44
    private val vec = SCValXdr.Vec(SCVecXdr(listOf(SCValXdr.B(true))))
    private val map = SCValXdr.Map(SCMapXdr(listOf(SCMapEntryXdr(SCValXdr.B(false), SCValXdr.B(true)))))

    private fun manageData(present: Boolean) = ManageDataOpXdr(
        String64Xdr("k"), if (present) DataValueXdr(byteArrayOf(7)) else null
    )

    private fun transaction(present: Boolean) = TransactionV0Xdr(
        Uint256Xdr(ByteArray(32)), Uint32Xdr(100u), SequenceNumberXdr(Int64Xdr(1)),
        if (present) TimeBoundsXdr(TimePointXdr(Uint64Xdr(1uL)), TimePointXdr(Uint64Xdr(2uL))) else null,
        MemoXdr.Void, emptyList(), TransactionV0ExtXdr.Void
    )

    private fun encoded(encode: (XdrWriter) -> Unit): ByteArray = XdrWriter().also(encode).toByteArray()

    private fun assertFlag(bytes: ByteArray, offset: Int, value: Int) {
        assertContentEquals(byteArrayOf(0, 0, 0, value.toByte()), bytes.copyOfRange(offset, offset + 4))
    }

    private fun malformed(encode: (XdrWriter) -> Unit, offset: Int): ByteArray {
        val bytes = encoded(encode)
        assertFlag(bytes, offset, 1)
        // Replace the low byte of the asserted four-byte big-endian flag.
        bytes[offset + 3] = 2
        return bytes
    }

    private fun rejects(offset: Int, value: Int = 2, read: () -> Unit) {
        val error = assertFailsWith<IllegalArgumentException>(block = read)
        assertEquals("XDR boolean at offset $offset must be 0 or 1, got $value", error.message)
    }

    private fun <T> roundTrip(
        value: T, offset: Int, flag: Int, encode: (T, XdrWriter) -> Unit, decode: (XdrReader) -> T
    ) {
        val bytes = encoded { encode(value, it) }
        assertFlag(bytes, offset, flag)
        val reader = XdrReader(bytes)
        val decoded = decode(reader)
        assertEquals(bytes.size, reader.position)
        assertContentEquals(bytes, encoded { encode(decoded, it) })
    }

    @Test fun readerZeroIsFalse() {
        assertEquals(false, XdrReader(byteArrayOf(0, 0, 0, 0)).readBoolean())
    }

    @Test fun readerOneIsTrue() {
        assertEquals(true, XdrReader(byteArrayOf(0, 0, 0, 1)).readBoolean())
    }

    @Test fun readerRejectsTwo() = rejects(0) {
        XdrReader(byteArrayOf(0, 0, 0, 2)).readBoolean()
    }

    @Test fun readerRejectsNegativeOne() = rejects(0, -1) {
        XdrReader(byteArrayOf(-1, -1, -1, -1)).readBoolean()
    }

    @Test fun readerRejectsNonzeroUpperBytes() {
        rejects(0, 16777216) { XdrReader(byteArrayOf(1, 0, 0, 0)).readBoolean() }
        rejects(0, 257) { XdrReader(byteArrayOf(0, 0, 1, 1)).readBoolean() }
        rejects(0, Int.MIN_VALUE) { XdrReader(byteArrayOf(-128, 0, 0, 0)).readBoolean() }
    }

    @Test fun readerRejectsAtNonzeroOffset() {
        val reader = XdrReader(byteArrayOf(0, 0, 0, 7, 0, 0, 0, 2))
        assertEquals(7, reader.readInt())
        rejects(4) { reader.readBoolean() }
    }

    @Test fun manageDataRejectsInvalidPresence() {
        val bytes = malformed(manageData(true)::encode, dataFlagOffset)
        rejects(dataFlagOffset) { ManageDataOpXdr.decode(XdrReader(bytes)) }
    }

    @Test fun manageDataAbsentRoundTrips() = roundTrip(
        manageData(false), dataFlagOffset, 0, ManageDataOpXdr::encode, ManageDataOpXdr::decode
    )

    @Test fun manageDataPresentRoundTrips() = roundTrip(
        manageData(true), dataFlagOffset, 1, ManageDataOpXdr::encode, ManageDataOpXdr::decode
    )

    @Test fun vecRejectsInvalidPresence() {
        val bytes = malformed(vec::encode, scValFlagOffset)
        rejects(scValFlagOffset) { SCValXdr.decode(XdrReader(bytes)) }
    }

    @Test fun vecAbsentRoundTrips() = roundTrip(
        SCValXdr.Vec(null), scValFlagOffset, 0, SCValXdr::encode, SCValXdr::decode
    )

    @Test fun vecPresentRoundTrips() = roundTrip(
        vec, scValFlagOffset, 1, SCValXdr::encode, SCValXdr::decode
    )

    @Test fun mapRejectsInvalidPresence() {
        val bytes = malformed(map::encode, scValFlagOffset)
        rejects(scValFlagOffset) { SCValXdr.decode(XdrReader(bytes)) }
    }

    @Test fun mapAbsentRoundTrips() = roundTrip(
        SCValXdr.Map(null), scValFlagOffset, 0, SCValXdr::encode, SCValXdr::decode
    )

    @Test fun mapPresentRoundTrips() = roundTrip(
        map, scValFlagOffset, 1, SCValXdr::encode, SCValXdr::decode
    )

    @Test fun boolArmRejectsTwo() {
        val bytes = malformed(SCValXdr.B(true)::encode, scValFlagOffset)
        rejects(scValFlagOffset) { SCValXdr.decode(XdrReader(bytes)) }
    }

    @Test fun boolArmFalseRoundTrips() = roundTrip(
        SCValXdr.B(false), scValFlagOffset, 0, SCValXdr::encode, SCValXdr::decode
    )

    @Test fun boolArmTrueRoundTrips() = roundTrip(
        SCValXdr.B(true), scValFlagOffset, 1, SCValXdr::encode, SCValXdr::decode
    )

    @Test fun transactionV0RejectsInvalidPresence() {
        val bytes = malformed(transaction(true)::encode, timeBoundsFlagOffset)
        rejects(timeBoundsFlagOffset) { TransactionV0Xdr.decode(XdrReader(bytes)) }
    }

    @Test fun transactionV0AbsentTimeBoundsRoundTrips() = roundTrip(
        transaction(false), timeBoundsFlagOffset, 0, TransactionV0Xdr::encode, TransactionV0Xdr::decode
    )

    @Test fun transactionV0PresentTimeBoundsRoundTrips() = roundTrip(
        transaction(true), timeBoundsFlagOffset, 1, TransactionV0Xdr::encode, TransactionV0Xdr::decode
    )
}
