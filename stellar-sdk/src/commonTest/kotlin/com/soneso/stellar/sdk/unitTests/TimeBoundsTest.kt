package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
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
}
