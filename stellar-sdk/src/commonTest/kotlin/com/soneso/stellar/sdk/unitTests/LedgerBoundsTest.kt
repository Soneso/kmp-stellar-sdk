package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.LedgerBoundsXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
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
        assertNotEquals(lb1, LedgerBounds(101, 200))
        assertNotEquals<Any>(lb1, TimeBounds(100, 200))
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

    @Test
    fun testExactValuesAtAndAboveIntRange() {
        // A uint32 ledger number is written as four big-endian bytes.
        val encodings = mapOf(
            Int.MAX_VALUE.toUInt() to "7fffffff",
            Int.MAX_VALUE.toUInt() + 1u to "80000000",
            UInt.MAX_VALUE to "ffffffff"
        )
        for ((value, encoded) in encodings) {
            val bounds = LedgerBounds.ofUnsigned(value, value)
            val writer = XdrWriter()
            bounds.toXdr().encode(writer)
            assertEquals(encoded + encoded, Util.bytesToHex(writer.toByteArray()))
            val restored = LedgerBounds.fromXdr(LedgerBoundsXdr.decode(XdrReader(writer.toByteArray())))
            assertEquals(bounds, restored)
            assertEquals(value, restored.minLedgerExact)
            assertEquals(value, restored.maxLedgerExact)
        }
    }

    @Test
    fun testSignedAccessThrowsAboveIntRange() {
        val bounds = LedgerBounds.ofUnsigned(1u, UInt.MAX_VALUE)
        assertEquals(1, bounds.minLedger)
        assertEquals(1, bounds.component1())
        assertFailsWith<IllegalStateException> { bounds.maxLedger }
        assertFailsWith<IllegalStateException> { bounds.component2() }
        assertFailsWith<IllegalStateException> { bounds.copy(minLedger = 2) }
        assertEquals("LedgerBounds(minLedger=1, maxLedger=4294967295)", bounds.toString())
        assertEquals(Int.MAX_VALUE, LedgerBounds.ofUnsigned(0u, Int.MAX_VALUE.toUInt()).maxLedger)
    }

    @Test
    fun testSignedAndUnsignedConstructionAreEqual() {
        val signed = LedgerBounds(100, 200)
        assertEquals(signed, LedgerBounds.ofUnsigned(100u, 200u))
        assertEquals(signed.hashCode(), LedgerBounds.ofUnsigned(100u, 200u).hashCode())
        assertEquals(LedgerBounds(100, 300), signed.copy(maxLedger = 300))
        assertEquals("LedgerBounds(minLedger=100, maxLedger=200)", signed.toString())
        assertFailsWith<IllegalArgumentException> { LedgerBounds.ofUnsigned(UInt.MAX_VALUE, 1u) }
        assertEquals(UInt.MAX_VALUE, LedgerBounds.ofUnsigned(UInt.MAX_VALUE, 0u).minLedgerExact)
    }

    @Test
    fun testMaxLedgerOfUint32MaxFromAppliedTransaction() = runTest {
        // A testnet transaction applied with txSUCCESS in ledger 5829128.
        val envelope = "AAAAAgAAAAAkns0kPl6G+DY63Bb7+24+SWf4l79qWyuv1/dZxJzwlAAAA+gAWPIAAAAAAwAAAAIAAAAAAAAAAQAAAAD/////AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAKAAAAAmx3AAAAAAABAAAAATIAAAAAAAAAAAAAAcSc8JQAAABAejHirJo6xA7PD+ySEysVgUlUpvTZTZQs/nbi4jqmnC0J3d3Aa/5QK5kc3VAHyp1El+rP6D8/JSE2C74Grn60Bg=="
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        assertEquals(LedgerBounds.ofUnsigned(0u, UInt.MAX_VALUE), tx.preconditions.ledgerBounds)
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals("5fa3a2529c7b232efc9f39e35ec51ec8c3076a96989611eca41fa0e2f48655e4", tx.hashHex())
    }
}
