package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlin.test.*

class LedgerBoundsTest {

    @Test
    fun testLedgerBoundsConstruction() {
        val ledgerBounds = LedgerBounds(100, 200)
        assertEquals(100, ledgerBounds.minLedger)
        assertEquals(200, ledgerBounds.maxLedger)
    }

    @Test
    fun testLedgerBoundsZeroValues() {
        // 0 means no minimum/maximum
        val noMin = LedgerBounds(0, 100)
        assertEquals(0, noMin.minLedger)
        assertEquals(100, noMin.maxLedger)

        val noMax = LedgerBounds(100, 0)
        assertEquals(100, noMax.minLedger)
        assertEquals(0, noMax.maxLedger)

        val noBounds = LedgerBounds(0, 0)
        assertEquals(0, noBounds.minLedger)
        assertEquals(0, noBounds.maxLedger)
    }

    @Test
    fun testLedgerBoundsValidation() {
        // minLedger must be <= maxLedger (unless maxLedger is 0)
        assertFails {
            LedgerBounds(200, 100)
        }

        // But these should work
        LedgerBounds(100, 100) // Equal is OK
        LedgerBounds(200, 0)   // maxLedger=0 means no max
    }

    @Test
    fun testLedgerBoundsNegativeValuesFail() {
        assertFails {
            LedgerBounds(-1, 100)
        }

        assertFails {
            LedgerBounds(100, -1)
        }
    }

    @Test
    fun testXdrRoundTrip() {
        val original = LedgerBounds(1000, 2000)
        val xdr = original.toXdr()
        val restored = LedgerBounds.fromXdr(xdr)

        assertEquals(original.minLedger, restored.minLedger)
        assertEquals(original.maxLedger, restored.maxLedger)
    }

    @Test
    fun testXdrRoundTripWithZeroValues() {
        val original = LedgerBounds(0, 0)
        val xdr = original.toXdr()
        val restored = LedgerBounds.fromXdr(xdr)

        assertEquals(0, restored.minLedger)
        assertEquals(0, restored.maxLedger)
    }

    @Test
    fun testXdrRoundTripWithLargeValues() {
        // Test with large ledger numbers (within 32-bit unsigned range)
        val original = LedgerBounds(1000000, 2000000)
        val xdr = original.toXdr()
        val restored = LedgerBounds.fromXdr(xdr)

        assertEquals(original.minLedger, restored.minLedger)
        assertEquals(original.maxLedger, restored.maxLedger)
    }

    @Test
    fun testEquality() {
        val lb1 = LedgerBounds(100, 200)
        val lb2 = LedgerBounds(100, 200)
        val lb3 = LedgerBounds(100, 300)

        assertEquals(lb1, lb2)
        assertNotEquals(lb1, lb3)
        assertEquals(lb1.hashCode(), lb2.hashCode())
    }

    @Test
    fun testToString() {
        val ledgerBounds = LedgerBounds(100, 200)
        val str = ledgerBounds.toString()

        assertTrue(str.contains("100"))
        assertTrue(str.contains("200"))
    }

    @Test
    fun testSameMinMax() {
        val ledgerBounds = LedgerBounds(500, 500)
        assertEquals(500, ledgerBounds.minLedger)
        assertEquals(500, ledgerBounds.maxLedger)
    }
}
