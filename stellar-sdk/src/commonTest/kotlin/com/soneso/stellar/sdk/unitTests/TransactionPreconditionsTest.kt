package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
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
    }

    @Test
    fun testNegativeMinSequenceNumberRoundTrip() {
        // SequenceNumber is int64, so a negative minimum reads and writes back unchanged.
        for (value in listOf(-1L, Long.MIN_VALUE)) {
            val preconditions = TransactionPreconditions(minSequenceNumber = value)
            val xdr = preconditions.toXdr() as PreconditionsXdr.V2
            assertEquals(value, xdr.value.minSeqNum!!.value.value)
            assertEquals(preconditions, TransactionPreconditions.fromXdr(xdr))
        }
    }

    @Test
    fun testExtraSignerWithEmptyPayloadFails() {
        val empty = SignerKey.ed25519SignedPayload(testPublicKey, ByteArray(0))
        assertFailsWith<IllegalArgumentException> { TransactionPreconditions(extraSigners = listOf(empty)) }
        val v2 = PreconditionsV2Xdr(
            timeBounds = null,
            ledgerBounds = null,
            minSeqNum = null,
            minSeqAge = DurationXdr(Uint64Xdr(0uL)),
            minSeqLedgerGap = Uint32Xdr(0u),
            extraSigners = listOf(empty.toXdr())
        )
        assertFailsWith<IllegalArgumentException> { TransactionPreconditions.fromXdr(PreconditionsXdr.V2(v2)) }
        val oneByte = SignerKey.ed25519SignedPayload(testPublicKey, ByteArray(1))
        assertEquals(listOf<SignerKey>(oneByte), TransactionPreconditions(extraSigners = listOf(oneByte)).extraSigners)
    }

    @Test
    fun testExactMinSequenceAgeAndLedgerGapRoundTrip() {
        // A uint64 Duration is written as eight big-endian bytes, a uint32 as four.
        val ages = listOf(Long.MAX_VALUE.toULong() to "7fffffffffffffff", (1uL shl 63) to "8000000000000000",
            ULong.MAX_VALUE to "ffffffffffffffff")
        val gaps = listOf(UInt.MAX_VALUE to "ffffffff", (1u shl 31) to "80000000", Int.MAX_VALUE.toUInt() to "7fffffff")
        for ((age, gap) in ages.zip(gaps)) {
            val preconditions =
                TransactionPreconditions.ofUnsigned(minSequenceAge = age.first, minSequenceLedgerGap = gap.first)
            val writer = XdrWriter()
            preconditions.toXdr().encode(writer)
            val hex = "00000002" + "00000000".repeat(3) + age.second + gap.second + "00000000"
            assertEquals(hex, Util.bytesToHex(writer.toByteArray()))
            val restored = TransactionPreconditions.fromXdr(PreconditionsXdr.decode(XdrReader(writer.toByteArray())))
            assertEquals(preconditions, restored)
            assertEquals(age.first to gap.first, restored.minSequenceAgeExact to restored.minSequenceLedgerGapExact)
        }
    }

    @Test
    fun testSignedMinSequenceAgeAndLedgerGapThrowAboveRange() {
        val age = TransactionPreconditions.ofUnsigned(minSequenceAge = 1uL shl 63)
        val gap = TransactionPreconditions.ofUnsigned(minSequenceLedgerGap = 1u shl 31)
        val ageMessage = "minSequenceAge 9223372036854775808 exceeds Long.MAX_VALUE; read minSequenceAgeExact"
        val gapMessage = "minSequenceLedgerGap 2147483648 exceeds Int.MAX_VALUE; read minSequenceLedgerGapExact"
        for (read in listOf({ age.minSequenceAge }, { age.component4() }, { age.copy() })) {
            assertEquals(ageMessage, assertFailsWith<IllegalStateException> { read() }.message)
        }
        for (read in listOf({ gap.minSequenceLedgerGap }, { gap.component5() }, { gap.copy() })) {
            assertEquals(gapMessage, assertFailsWith<IllegalStateException> { read() }.message)
        }
        assertEquals(0 to 0L, age.minSequenceLedgerGap to gap.minSequenceAge)
        assertTrue(age.toString().contains("minSequenceAge=9223372036854775808, minSequenceLedgerGap=0"))
    }

    @Test
    fun testSignedAndUnsignedPreconditionsAreEqual() {
        val signed = TransactionPreconditions(minSequenceNumber = 3L, minSequenceAge = Long.MAX_VALUE,
            minSequenceLedgerGap = Int.MAX_VALUE)
        val exact = TransactionPreconditions.ofUnsigned(minSequenceNumber = 3L,
            minSequenceAge = Long.MAX_VALUE.toULong(), minSequenceLedgerGap = Int.MAX_VALUE.toUInt())
        assertEquals(signed to signed.hashCode(), exact to exact.hashCode())
        assertEquals(signed, exact.copy())
        val (timeBounds, ledgerBounds, minSequenceNumber, age, gap, extraSigners) = exact
        assertEquals(listOf(null, null, 3L, Long.MAX_VALUE, Int.MAX_VALUE, emptyList<SignerKey>()),
            listOf(timeBounds, ledgerBounds, minSequenceNumber, age, gap, extraSigners))
        val format = "TransactionPreconditions(timeBounds=null, ledgerBounds=null, minSequenceNumber=3, " +
            "minSequenceAge=9223372036854775807, minSequenceLedgerGap=2147483647, extraSigners=[])"
        assertEquals(format, exact.toString())
    }

    @Test
    fun testTransactionBuilderKeepsExactMinSequenceAgeAndLedgerGap() {
        val preconditions = TransactionPreconditions.ofUnsigned(minSequenceAge = ULong.MAX_VALUE,
            minSequenceLedgerGap = UInt.MAX_VALUE)
        val account = Account("GBT4FEFAAOWIKD2S647AUAXPPLMLLTH2DQ4DQQKBIZ6U7JDJ5ACF6OXX", 1L)
        val payment = PaymentOperation("GDXDKZMO3227MRZZIK52KPX6VLLTTOPMFTTOTMPIXOFXM5UJB6ONJPHB", AssetTypeNative, "1")
        for (timeout in listOf(true, false)) {
            val builder = TransactionBuilder(account, Network.TESTNET).setBaseFee(100).addOperation(payment)
                .addPreconditions(preconditions)
            val tx = (if (timeout) builder.setTimeout(30) else builder.addTimeBounds(TimeBounds(0, 0))).build()
            assertEquals(ULong.MAX_VALUE to UInt.MAX_VALUE,
                tx.preconditions.minSequenceAgeExact to tx.preconditions.minSequenceLedgerGapExact)
        }
    }

    @Test
    fun testMinSequenceAgeAndLedgerGapAboveSignedRangeFromEnvelope() = runTest {
        // A V1 envelope built byte by byte without the SDK, with a minimum sequence age of 2^63 and a minimum
        // sequence ledger gap of 2^32-1; the hash was computed from the envelope bytes without the SDK.
        val envelope = "AAAAAgAAAABnwpCgA6yFD1L3PgoC73rYtcz6HDg4QUFGfU+kaegEXwAAAGQAAABAAAAABAAAAAIAAAAAAAAAAAAAAACAAAAAAAAAAP////8AAAAAAAAAAAAAAAEAAAAAAAAAAQAAAADuNWWO3rX2RzlCu6U+/qrXObnsLObpsei7i3Z2iQ+c1AAAAAAAAAAAAJiWgAAAAAAAAAAA"
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        val expected =
            TransactionPreconditions.ofUnsigned(minSequenceAge = 1uL shl 63, minSequenceLedgerGap = UInt.MAX_VALUE)
        assertEquals(expected, tx.preconditions)
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals("b5a36bb02573270386a07fab13e970673234dad224b3af4428bc9c278b2061fe", tx.hashHex())
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
    fun testXdrRoundTripV2WithoutLedgerBounds() {
        // V2 preconditions triggered by minSequenceNumber alone, with no ledger bounds set,
        // exercises the null branch of the ledgerBounds mapping on both encode and decode.
        val original = TransactionPreconditions(minSequenceNumber = 5000L)
        val xdr = original.toXdr()
        val restored = TransactionPreconditions.fromXdr(xdr)

        assertNull(restored.ledgerBounds)
        assertEquals(5000L, restored.minSequenceNumber)
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
