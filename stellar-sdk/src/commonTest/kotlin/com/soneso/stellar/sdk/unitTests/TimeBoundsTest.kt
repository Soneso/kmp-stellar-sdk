package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.TimeBoundsXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TimeBoundsTest {

    @Test
    fun testTimeBoundsConstruction() {
        val timeBounds = TimeBounds(100, 200)
        assertEquals(100L, timeBounds.minTime)
        assertEquals(200L, timeBounds.maxTime)
    }

    @Test
    fun testTimeBoundsZeroValues() {
        // 0 means no minimum/maximum
        val noMin = TimeBounds(0, 100)
        assertEquals(0L, noMin.minTime)
        assertEquals(100L, noMin.maxTime)

        val noMax = TimeBounds(100, 0)
        assertEquals(100L, noMax.minTime)
        assertEquals(0L, noMax.maxTime)

        val noBounds = TimeBounds(0, 0)
        assertEquals(0L, noBounds.minTime)
        assertEquals(0L, noBounds.maxTime)
    }

    @Test
    fun testTimeBoundsValidation() {
        // minTime must be <= maxTime (unless maxTime is 0)
        assertFails {
            TimeBounds(200, 100)
        }

        // But these should work
        TimeBounds(100, 100) // Equal is OK
        TimeBounds(200, 0)   // maxTime=0 means infinite
    }

    @Test
    fun testTimeBoundsNegativeValuesFail() {
        assertFails {
            TimeBounds(-1, 100)
        }

        assertFails {
            TimeBounds(100, -1)
        }
    }

    @Test
    fun testExpiresAfterDuration() {
        val timeBounds = TimeBounds.expiresAfter(60.seconds)

        assertEquals(0L, timeBounds.minTime)
        assertTrue(timeBounds.maxTime > 0)
    }

    @Test
    fun testExpiresAfterSeconds() {
        val timeBounds = TimeBounds.expiresAfter(300L)

        assertEquals(0L, timeBounds.minTime)
        assertTrue(timeBounds.maxTime > 0)
    }

    @Test
    fun testXdrRoundTrip() {
        val original = TimeBounds(1000, 2000)
        val xdr = original.toXdr()
        val restored = TimeBounds.fromXdr(xdr)

        assertNotNull(restored)
        assertEquals(original.minTime, restored.minTime)
        assertEquals(original.maxTime, restored.maxTime)
    }

    @Test
    fun testXdrRoundTripWithZeroValues() {
        val original = TimeBounds(0, 0)
        val xdr = original.toXdr()
        val restored = TimeBounds.fromXdr(xdr)

        assertNotNull(restored)
        assertEquals(0L, restored.minTime)
        assertEquals(0L, restored.maxTime)
    }

    @Test
    fun testXdrRoundTripWithLargeValues() {
        // Test with large timestamp values
        val original = TimeBounds(1000000000L, 2000000000L)
        val xdr = original.toXdr()
        val restored = TimeBounds.fromXdr(xdr)

        assertNotNull(restored)
        assertEquals(original.minTime, restored.minTime)
        assertEquals(original.maxTime, restored.maxTime)
    }

    @Test
    fun testFromXdrNull() {
        val restored = TimeBounds.fromXdr(null)
        assertNull(restored)
    }

    @Test
    fun testEquality() {
        val tb1 = TimeBounds(100, 200)
        val tb2 = TimeBounds(100, 200)
        val tb3 = TimeBounds(100, 300)

        assertEquals(tb1, tb2)
        assertNotEquals(tb1, tb3)
        assertNotEquals(tb1, TimeBounds(101, 200))
        assertNotEquals<Any>(tb1, LedgerBounds(100, 200))
        assertEquals(tb1.hashCode(), tb2.hashCode())
    }

    @Test
    fun testTimeoutInfiniteConstant() {
        assertEquals(0L, TimeBounds.TIMEOUT_INFINITE)
    }

    @Test
    fun testToString() {
        val timeBounds = TimeBounds(100, 200)
        val str = timeBounds.toString()

        assertTrue(str.contains("100"))
        assertTrue(str.contains("200"))
    }

    @Test
    fun testExactValuesAtAndAboveLongRange() {
        // A uint64 TimePoint is written as eight big-endian bytes.
        val encodings = mapOf(
            Long.MAX_VALUE.toULong() to "7fffffffffffffff",
            Long.MAX_VALUE.toULong() + 1uL to "8000000000000000",
            ULong.MAX_VALUE to "ffffffffffffffff"
        )
        for ((value, encoded) in encodings) {
            val bounds = TimeBounds.ofUnsigned(value, value)
            val writer = XdrWriter()
            bounds.toXdr().encode(writer)
            assertEquals(encoded + encoded, Util.bytesToHex(writer.toByteArray()))
            val restored = TimeBounds.fromXdr(TimeBoundsXdr.decode(XdrReader(writer.toByteArray())))
            assertEquals(bounds, restored)
            assertEquals(value, restored!!.minTimeExact)
            assertEquals(value, restored.maxTimeExact)
        }
    }

    @Test
    fun testSignedAccessThrowsAboveLongRange() {
        val bounds = TimeBounds.ofUnsigned(1uL, ULong.MAX_VALUE)
        assertEquals(1L, bounds.minTime)
        assertEquals(1L, bounds.component1())
        assertFailsWith<IllegalStateException> { bounds.maxTime }
        assertFailsWith<IllegalStateException> { bounds.component2() }
        assertFailsWith<IllegalStateException> { bounds.copy(minTime = 2) }
        assertEquals("TimeBounds(minTime=1, maxTime=18446744073709551615)", bounds.toString())
        assertEquals(Long.MAX_VALUE, TimeBounds.ofUnsigned(0uL, Long.MAX_VALUE.toULong()).maxTime)
    }

    @Test
    fun testSignedAndUnsignedConstructionAreEqual() {
        val signed = TimeBounds(100, 200)
        assertEquals(signed, TimeBounds.ofUnsigned(100uL, 200uL))
        assertEquals(signed.hashCode(), TimeBounds.ofUnsigned(100uL, 200uL).hashCode())
        assertEquals(TimeBounds(100, 300), signed.copy(maxTime = 300))
        assertEquals("TimeBounds(minTime=100, maxTime=200)", signed.toString())
        assertFailsWith<IllegalArgumentException> { TimeBounds.ofUnsigned(ULong.MAX_VALUE, 1uL) }
        assertEquals(ULong.MAX_VALUE, TimeBounds.ofUnsigned(ULong.MAX_VALUE, 0uL).minTimeExact)
    }

    @Test
    fun testMaxTimeOfUint64MaxFromEnvelope() = runTest {
        // A V1 envelope with two signatures and max_time 2^64-1; the hash was computed from the
        // envelope bytes without the SDK.
        val envelope = "AAAAAgAAAADuNWWO3rX2RzlCu6U+/qrXObnsLObpsei7i3Z2iQ+c1AAAAGQAAABAAAAACQAAAAEAAAAAadGmAP//////////AAAAAAAAAAEAAAABAAAAAFcDGhVgMTQUM2bhKpDZboi2XN1axvAEogNuScrpNRNpAAAAAgAAAAAAAAAADRzvAQAAAACZ1fT9fwPHqbSviKB8N1RNe1BjcaulgCYSfOLBdG7+OwAAAAFVU0QAAAAAAO41ZY7etfZHOUK7pT7+qtc5uews5umx6LuLdnaJD5zUAAAAC6PvJ/gAAAABAAAAAkxPTkdBU1NFVDEyAAAAAADuNWWO3rX2RzlCu6U+/qrXObnsLObpsei7i3Z2iQ+c1AAAAAAAAAACiQ+c1AAAAEC57iWQ9bokm5s4MWxr4wG0HXiSSUAmHN3P1yaCAEzPGynUN1L2uo5wkMPiSSghN1uWfnTP/Fgrn0uBXru2KRIA6TUTaQAAAECghJYyQwNseQG6tGN+Vzx/I63JCGVL3pBG6qoF/DQD06+XiI7v0SVI2aBANo0LvqC6SbGEVRVtgittC6NQehwM"
        val hash = "056ee5d0f5634053a37d8fa07cc2ccc72dcf6546048b4c73aec12c5982ae9c8b"
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        assertEquals(TimeBounds.ofUnsigned(1775347200uL, ULong.MAX_VALUE), tx.preconditions.timeBounds)
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals(hash, tx.hashHex())

        // The same transaction inside a fee bump.
        val feeBump = FeeBumpTransaction.createWithBaseFee(tx.sourceAccount, 200, tx)
        val restored = AbstractTransaction.fromEnvelopeXdr(feeBump.toEnvelopeXdrBase64(), Network.TESTNET)
        restored as FeeBumpTransaction
        assertEquals(ULong.MAX_VALUE, restored.innerTransaction.preconditions.timeBounds!!.maxTimeExact)
        assertEquals(feeBump.toEnvelopeXdrBase64(), restored.toEnvelopeXdrBase64())
        assertEquals(hash, restored.innerTransaction.hashHex())
    }
}
