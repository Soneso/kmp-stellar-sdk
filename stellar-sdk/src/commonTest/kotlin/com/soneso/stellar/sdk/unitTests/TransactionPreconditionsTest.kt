package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlin.test.*

class TransactionPreconditionsTest {

    private val testPublicKey = ByteArray(32) { it.toByte() }

    @Test
    fun testDefaultPreconditions() {
        val preconditions = TransactionPreconditions()

        assertNull(preconditions.timeBounds)
        assertNull(preconditions.ledgerBounds)
        assertNull(preconditions.minSequenceNumber)
        assertEquals(0L, preconditions.minSequenceAge)
        assertEquals(0, preconditions.minSequenceLedgerGap)
        assertTrue(preconditions.extraSigners.isEmpty())

        assertFalse(preconditions.hasV2())
    }

    @Test
    fun testPreconditionsWithTimeBounds() {
        val timeBounds = TimeBounds(100, 200)
        val preconditions = TransactionPreconditions(timeBounds = timeBounds)

        assertEquals(timeBounds, preconditions.timeBounds)
        assertFalse(preconditions.hasV2())
    }

    @Test
    fun testPreconditionsWithLedgerBounds() {
        val ledgerBounds = LedgerBounds(100, 200)
        val preconditions = TransactionPreconditions(ledgerBounds = ledgerBounds)

        assertEquals(ledgerBounds, preconditions.ledgerBounds)
        assertTrue(preconditions.hasV2())
    }

    @Test
    fun testPreconditionsWithMinSequenceNumber() {
        val preconditions = TransactionPreconditions(minSequenceNumber = 1000L)

        assertEquals(1000L, preconditions.minSequenceNumber)
        assertTrue(preconditions.hasV2())
    }

    @Test
    fun testPreconditionsWithMinSequenceAge() {
        val preconditions = TransactionPreconditions(minSequenceAge = 60L)

        assertEquals(60L, preconditions.minSequenceAge)
        assertTrue(preconditions.hasV2())
    }

    @Test
    fun testPreconditionsWithMinSequenceLedgerGap() {
        val preconditions = TransactionPreconditions(minSequenceLedgerGap = 10)

        assertEquals(10, preconditions.minSequenceLedgerGap)
        assertTrue(preconditions.hasV2())
    }

    @Test
    fun testPreconditionsWithExtraSigners() {
        val signer = SignerKey.ed25519PublicKey(testPublicKey)
        val preconditions = TransactionPreconditions(extraSigners = listOf(signer))

        assertEquals(1, preconditions.extraSigners.size)
        assertEquals(signer, preconditions.extraSigners[0])
        assertTrue(preconditions.hasV2())
    }

    @Test
    fun testPreconditionsMaxExtraSigners() {
        assertEquals(2, TransactionPreconditions.MAX_EXTRA_SIGNERS_COUNT)

        val signer1 = SignerKey.ed25519PublicKey(testPublicKey)
        val signer2 = SignerKey.ed25519PublicKey(ByteArray(32) { 1.toByte() })

        // Two signers should work
        TransactionPreconditions(extraSigners = listOf(signer1, signer2))

        // Three signers should fail
        val signer3 = SignerKey.ed25519PublicKey(ByteArray(32) { 2.toByte() })
        assertFails {
            TransactionPreconditions(extraSigners = listOf(signer1, signer2, signer3))
        }
    }

    @Test
    fun testPreconditionsWithAllFields() {
        val timeBounds = TimeBounds(100, 200)
        val ledgerBounds = LedgerBounds(1000, 2000)
        val signer = SignerKey.ed25519PublicKey(testPublicKey)

        val preconditions = TransactionPreconditions(
            timeBounds = timeBounds,
            ledgerBounds = ledgerBounds,
            minSequenceNumber = 5000L,
            minSequenceAge = 60L,
            minSequenceLedgerGap = 10,
            extraSigners = listOf(signer)
        )

        assertEquals(timeBounds, preconditions.timeBounds)
        assertEquals(ledgerBounds, preconditions.ledgerBounds)
        assertEquals(5000L, preconditions.minSequenceNumber)
        assertEquals(60L, preconditions.minSequenceAge)
        assertEquals(10, preconditions.minSequenceLedgerGap)
        assertEquals(1, preconditions.extraSigners.size)
        assertTrue(preconditions.hasV2())
    }

    @Test
    fun testPreconditionsBuilder() {
        val timeBounds = TimeBounds(100, 200)
        val ledgerBounds = LedgerBounds(1000, 2000)
        val signer = SignerKey.ed25519PublicKey(testPublicKey)

        val preconditions = TransactionPreconditions.builder()
            .timeBounds(timeBounds)
            .ledgerBounds(ledgerBounds)
            .minSequenceNumber(5000L)
            .minSequenceAge(60L)
            .minSequenceLedgerGap(10)
            .addExtraSigner(signer)
            .build()

        assertEquals(timeBounds, preconditions.timeBounds)
        assertEquals(ledgerBounds, preconditions.ledgerBounds)
        assertEquals(5000L, preconditions.minSequenceNumber)
        assertEquals(60L, preconditions.minSequenceAge)
        assertEquals(10, preconditions.minSequenceLedgerGap)
        assertEquals(1, preconditions.extraSigners.size)
    }

    @Test
    fun testPreconditionsValidation() {
        val preconditions = TransactionPreconditions()
        preconditions.validate() // Should not throw
    }

    @Test
    fun testPreconditionsNegativeValuesFail() {
        assertFails {
            TransactionPreconditions(minSequenceAge = -1L)
        }

        assertFails {
            TransactionPreconditions(minSequenceLedgerGap = -1)
        }

        assertFails {
            TransactionPreconditions(minSequenceNumber = -1L)
        }
    }

    @Test
    fun testXdrRoundTripPrecondNone() {
        val original = TransactionPreconditions()
        val xdr = original.toXdr()
        val restored = TransactionPreconditions.fromXdr(xdr)

        assertNull(restored.timeBounds)
        assertNull(restored.ledgerBounds)
        assertNull(restored.minSequenceNumber)
        assertEquals(0L, restored.minSequenceAge)
        assertEquals(0, restored.minSequenceLedgerGap)
        assertTrue(restored.extraSigners.isEmpty())
    }

    @Test
    fun testXdrRoundTripPrecondTime() {
        val timeBounds = TimeBounds(100, 200)
        val original = TransactionPreconditions(timeBounds = timeBounds)
        val xdr = original.toXdr()
        val restored = TransactionPreconditions.fromXdr(xdr)

        assertNotNull(restored.timeBounds)
        assertEquals(100L, restored.timeBounds!!.minTime)
        assertEquals(200L, restored.timeBounds!!.maxTime)
        assertNull(restored.ledgerBounds)
        assertNull(restored.minSequenceNumber)
    }

    @Test
    fun testXdrRoundTripPrecondV2() {
        val timeBounds = TimeBounds(100, 200)
        val ledgerBounds = LedgerBounds(1000, 2000)
        val signer = SignerKey.ed25519PublicKey(testPublicKey)

        val original = TransactionPreconditions(
            timeBounds = timeBounds,
            ledgerBounds = ledgerBounds,
            minSequenceNumber = 5000L,
            minSequenceAge = 60L,
            minSequenceLedgerGap = 10,
            extraSigners = listOf(signer)
        )

        val xdr = original.toXdr()
        val restored = TransactionPreconditions.fromXdr(xdr)

        assertNotNull(restored.timeBounds)
        assertEquals(100L, restored.timeBounds!!.minTime)
        assertEquals(200L, restored.timeBounds!!.maxTime)

        assertNotNull(restored.ledgerBounds)
        assertEquals(1000, restored.ledgerBounds!!.minLedger)
        assertEquals(2000, restored.ledgerBounds!!.maxLedger)

        assertEquals(5000L, restored.minSequenceNumber)
        assertEquals(60L, restored.minSequenceAge)
        assertEquals(10, restored.minSequenceLedgerGap)
        assertEquals(1, restored.extraSigners.size)
    }

    @Test
    fun testXdrRoundTripV2WithoutTimeBounds() {
        // V2 can have no time bounds
        val ledgerBounds = LedgerBounds(1000, 2000)
        val original = TransactionPreconditions(ledgerBounds = ledgerBounds)

        val xdr = original.toXdr()
        val restored = TransactionPreconditions.fromXdr(xdr)

        assertNull(restored.timeBounds)
        assertNotNull(restored.ledgerBounds)
        assertEquals(1000, restored.ledgerBounds!!.minLedger)
        assertEquals(2000, restored.ledgerBounds!!.maxLedger)
    }

    @Test
    fun testTimeoutInfiniteConstant() {
        assertEquals(0L, TransactionPreconditions.TIMEOUT_INFINITE)
    }

    @Test
    fun testEquality() {
        val timeBounds = TimeBounds(100, 200)
        val pc1 = TransactionPreconditions(timeBounds = timeBounds)
        val pc2 = TransactionPreconditions(timeBounds = timeBounds)
        val pc3 = TransactionPreconditions(minSequenceAge = 60L)

        assertEquals(pc1, pc2)
        assertNotEquals(pc1, pc3)
        assertEquals(pc1.hashCode(), pc2.hashCode())
    }

    @Test
    fun testToString() {
        val preconditions = TransactionPreconditions(
            timeBounds = TimeBounds(100, 200)
        )
        val str = preconditions.toString()

        assertTrue(str.contains("TransactionPreconditions"))
    }

    @Test
    fun testHasV2WithMultipleCriteria() {
        // Only time bounds - not V2
        assertFalse(TransactionPreconditions(timeBounds = TimeBounds(100, 200)).hasV2())

        // Ledger bounds - V2
        assertTrue(TransactionPreconditions(ledgerBounds = LedgerBounds(100, 200)).hasV2())

        // Min sequence number - V2
        assertTrue(TransactionPreconditions(minSequenceNumber = 100L).hasV2())

        // Min sequence age - V2
        assertTrue(TransactionPreconditions(minSequenceAge = 60L).hasV2())

        // Min sequence ledger gap - V2
        assertTrue(TransactionPreconditions(minSequenceLedgerGap = 10).hasV2())

        // Extra signers - V2
        assertTrue(TransactionPreconditions(extraSigners = listOf(SignerKey.ed25519PublicKey(testPublicKey))).hasV2())
    }

    @Test
    fun testBuilderExtraSignersOverwrite() {
        val signer1 = SignerKey.ed25519PublicKey(testPublicKey)
        val signer2 = SignerKey.ed25519PublicKey(ByteArray(32) { 1.toByte() })

        val preconditions = TransactionPreconditions.builder()
            .addExtraSigner(signer1)
            .extraSigners(listOf(signer2)) // This should replace
            .build()

        assertEquals(1, preconditions.extraSigners.size)
        assertEquals(signer2, preconditions.extraSigners[0])
    }

    @Test
    fun testBuilderMultipleAddExtraSigner() {
        val signer1 = SignerKey.ed25519PublicKey(testPublicKey)
        val signer2 = SignerKey.ed25519PublicKey(ByteArray(32) { 1.toByte() })

        val preconditions = TransactionPreconditions.builder()
            .addExtraSigner(signer1)
            .addExtraSigner(signer2)
            .build()

        assertEquals(2, preconditions.extraSigners.size)
        assertEquals(signer1, preconditions.extraSigners[0])
        assertEquals(signer2, preconditions.extraSigners[1])
    }
}
