package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TransactionPreconditionsDecodedTypeTest {

    companion object {
        // Mainnet transaction b4a6d6a2... of ledger 55000000: PRECOND_V2 with time bounds and every other
        // V2 field at its default.
        private const val MAINNET_V2_ENVELOPE =
            "AAAAAgAAAAAjIV2QSjkjVC2NRbEBkV2PJmIBq2UKJLQ8WtBuCDXJ2wAAAfQCynanAAIn3gAAAAIAAAABAAAAAAAAAAAAAAAAZ21qPwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAQAAAAA7ngKfAUS0/FGZa4KJx3HXU6lwFOcBJPM4wex7LzuKWQAAAAIAAAAAAAAAAACVE5oAAAAAO54CnwFEtPxRmWuCicdx11OpcBTnASTzOMHsey87ilkAAAABeVhMTQAAAAAiNtdw/g0+AVIZddXPesSEK3CV1rGp12jT6aYy6ZkGvgAAAAAAlROaAAAAAgAAAAFQWUJDAAAAAGoebauqH6pI95KR+EBZVmms+p4HXgOxFvrW2rIJm6+gAAAAAUdPTEQAAAAARdyZXmQpu5u4/qV3g+/setPieuf7JpEh1TZIeT30F1MAAAAAAAAAAgg1ydsAAABA4b8Q29wH2OARp5SSjIFp68OG8fXL40Ds16TjPsspD1GqyAx4GDfjfZ+IEeSnYa7Qr/xzCdVk0fyJh4qwfHBFAi87ilkAAABAuutRndzo0SxfqbLK4IYQFwOr0blwGWSv4RMDO8x9uYwESqNHCtL43vMIjrpB3/irBxisBvjqMwATggWhAB4wCA=="
        private const val MAINNET_V2_HASH = "b4a6d6a2dfc62268a5f864f51d761207641e0c72bd8eb277159005f0f3503db9"

        // Testnet V1 envelopes written as raw XDR: source key bytes 1 to 32, fee 100, sequence number
        // 4294967297, one BUMP_SEQUENCE operation and one placeholder signature. They differ only in the
        // precondition. Each hash is sha256(network id || envelope type || transaction), computed outside the SDK.
        private const val V2_DEFAULT_ENVELOPE =
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAACwAAAAEAAAAEAAAAAAAAAAEdHh8gAAAAQKurq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6s="
        private const val V2_DEFAULT_HASH = "6f4b21b28769e8fafa2920ea70269613ce81a713a79da22661ad09cbb8261bd6"
        private const val NONE_ENVELOPE =
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAALAAAAAQAAAAQAAAAAAAAAAR0eHyAAAABAq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urqw=="
        private const val NONE_HASH = "473c95e454ac18936db00daf39e89ae5ca651a430c29aeb94e88c9872b8ac32a"
        private const val TIME_ENVELOPE =
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAEAAAAAZVPxAAAAAABlU/8QAAAAAAAAAAEAAAAAAAAACwAAAAEAAAAEAAAAAAAAAAEdHh8gAAAAQKurq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6s="
        private const val TIME_HASH = "367aaddf1d0d4daf80d32f78eb6a7b0c7a0691b04451db3b70948f45bbd7eff7"

        // Fee bump envelope written as raw XDR: fee source key bytes 65 to 96, fee 400, one placeholder
        // signature, around the transaction above with a PRECOND_V2 that holds only the time bounds
        // 1700000000 to 1700003600. The fee bump hash uses envelope type 5 over the fee bump transaction,
        // which holds the inner envelope with its signature.
        private const val FEE_BUMP_ENVELOPE =
            "AAAABQAAAABBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAAAAAGQAAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAIAAAABAAAAAGVT8QAAAAAAZVP/EAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAsAAAABAAAABAAAAAAAAAABHR4fIAAAAECrq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urAAAAAAAAAAFdXl9gAAAAQM3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc0="
        private const val FEE_BUMP_INNER_HASH = "3be5b142f1888501f56e9b8cd65545f6494924d65f9d10f91968cb4aa95ec586"
        private const val FEE_BUMP_HASH = "db14294568dcae40f35fd1b76fb8f72143795e2ac7a023937235909d4cdb913f"

        // PRECOND_V2 with every field at its default, without and with the time bounds 1700000000 to 1700003600.
        private const val V2_DEFAULT_HEX = "0000000200000000000000000000000000000000000000000000000000000000"
        private const val V2_TIME_HEX =
            "0000000200000001000000006553f100000000006553ff10000000000000000000000000000000000000000000000000"
    }

    private val timeBounds = TimeBounds(1700000000, 1700003600)

    private fun decode(hex: String): TransactionPreconditions =
        TransactionPreconditions.fromXdr(PreconditionsXdr.decode(XdrReader(Util.hexToBytes(hex))))

    private fun encodeHex(preconditions: TransactionPreconditions): String {
        val writer = XdrWriter()
        preconditions.toXdr().encode(writer)
        return Util.bytesToHex(writer.toByteArray())
    }

    private fun signer(fill: Int) = SignerKey.ed25519PublicKey(ByteArray(32) { fill.toByte() })

    @Test
    fun testMainnetV2EnvelopeKeepsHashAndBytes() = runTest {
        val tx = AbstractTransaction.fromEnvelopeXdr(MAINNET_V2_ENVELOPE, Network.PUBLIC) as Transaction

        assertEquals(MAINNET_V2_HASH, tx.hashHex())
        assertEquals(MAINNET_V2_ENVELOPE, tx.toEnvelopeXdrBase64())
        assertTrue(tx.preconditions.hasV2())
    }

    @Test
    fun testDefaultV2EnvelopeWithoutTimeBoundsKeepsHashAndBytes() = runTest {
        val tx = AbstractTransaction.fromEnvelopeXdr(V2_DEFAULT_ENVELOPE, Network.TESTNET) as Transaction

        assertEquals(V2_DEFAULT_HASH, tx.hashHex())
        assertEquals(V2_DEFAULT_ENVELOPE, tx.toEnvelopeXdrBase64())
        assertTrue(tx.preconditions.hasV2())
    }

    @Test
    fun testDefaultV2PreconditionsWriteBackAsRead() {
        assertEquals(V2_DEFAULT_HEX, encodeHex(decode(V2_DEFAULT_HEX)))
        assertEquals(V2_TIME_HEX, encodeHex(decode(V2_TIME_HEX)))
    }

    @Test
    fun testFeeBumpKeepsInnerDefaultV2HashesAndBytes() = runTest {
        val feeBump = AbstractTransaction.fromEnvelopeXdr(FEE_BUMP_ENVELOPE, Network.TESTNET) as FeeBumpTransaction

        assertEquals(FEE_BUMP_INNER_HASH, feeBump.innerTransaction.hashHex())
        assertEquals(FEE_BUMP_HASH, feeBump.hashHex())
        assertEquals(FEE_BUMP_ENVELOPE, feeBump.toEnvelopeXdrBase64())
        assertTrue(feeBump.innerTransaction.preconditions.hasV2())
    }

    @Test
    fun testNoneAndTimeEnvelopesKeepTheirType() = runTest {
        for ((envelope, hash) in listOf(NONE_ENVELOPE to NONE_HASH, TIME_ENVELOPE to TIME_HASH)) {
            val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction

            assertEquals(hash, tx.hashHex())
            assertEquals(envelope, tx.toEnvelopeXdrBase64())
            assertFalse(tx.preconditions.hasV2())
        }
    }

    @Test
    fun testBuiltValuesChooseTheTypeFromTheirValues() {
        assertEquals(PreconditionTypeXdr.PRECOND_NONE, TransactionPreconditions().toXdr().discriminant)
        assertEquals(PreconditionTypeXdr.PRECOND_NONE, TransactionPreconditions.builder().build().toXdr().discriminant)
        assertEquals(PreconditionTypeXdr.PRECOND_TIME, TransactionPreconditions(timeBounds).toXdr().discriminant)
        assertEquals(
            PreconditionTypeXdr.PRECOND_TIME,
            TransactionPreconditions.builder().timeBounds(timeBounds).build().toXdr().discriminant
        )

        val v2Only = mapOf(
            "ledgerBounds" to TransactionPreconditions(ledgerBounds = LedgerBounds(0, 0)),
            "minSequenceNumber" to TransactionPreconditions(minSequenceNumber = 0L),
            "minSequenceAge" to TransactionPreconditions(minSequenceAge = 1L),
            "minSequenceLedgerGap" to TransactionPreconditions(minSequenceLedgerGap = 1),
            "extraSigners" to TransactionPreconditions(extraSigners = listOf(signer(1)))
        )
        for ((field, preconditions) in v2Only) {
            assertEquals(PreconditionTypeXdr.PRECOND_V2, preconditions.toXdr().discriminant, field)
        }
    }

    @Test
    fun testCopyOfDecodedDefaultV2ChoosesTheTypeFromItsValues() {
        assertEquals(PreconditionTypeXdr.PRECOND_NONE, decode(V2_DEFAULT_HEX).copy().toXdr().discriminant)
        assertEquals(PreconditionTypeXdr.PRECOND_TIME, decode(V2_TIME_HEX).copy().toXdr().discriminant)
        assertEquals(
            PreconditionTypeXdr.PRECOND_V2,
            decode(V2_TIME_HEX).copy(minSequenceAge = 1L).toXdr().discriminant
        )
    }

    @Test
    fun testDecodedDefaultV2WithTimeBoundsDiffersFromBuiltTimeBounds() {
        val built = TransactionPreconditions(timeBounds = timeBounds)
        val decoded = decode(V2_TIME_HEX)

        assertEquals(built.timeBounds, decoded.timeBounds)
        assertNotEquals(built, decoded)
        assertNotEquals(decoded, built)
    }

    @Test
    fun testDecodedDefaultV2WithoutTimeBoundsDiffersFromEmptyBuiltValue() {
        val decoded = decode(V2_DEFAULT_HEX)

        assertNotEquals(TransactionPreconditions(), decoded)
        assertNotEquals(decoded, TransactionPreconditions())
    }

    @Test
    fun testDecodedV2WithLedgerBoundsEqualsBuiltValue() {
        val built = TransactionPreconditions(timeBounds = timeBounds, ledgerBounds = LedgerBounds(10, 20))
        val decoded = TransactionPreconditions.fromXdr(built.toXdr())

        assertEquals(built, decoded)
        assertEquals(decoded, built)
        assertEquals(built.hashCode(), decoded.hashCode())
    }

    @Test
    fun testDecodedDefaultV2ValuesWithEqualFieldsAreEqual() {
        for (hex in listOf(V2_DEFAULT_HEX, V2_TIME_HEX)) {
            val first = decode(hex)
            val second = decode(hex)

            assertEquals(first, second)
            assertEquals(first.hashCode(), second.hashCode())
        }
    }

    @Test
    fun testValuesDifferingInOnePropertyAreNotEqual() {
        val base = TransactionPreconditions(
            timeBounds = timeBounds,
            ledgerBounds = LedgerBounds(10, 20),
            minSequenceNumber = 5L,
            minSequenceAge = 6L,
            minSequenceLedgerGap = 7,
            extraSigners = listOf(signer(1))
        )
        val variants = mapOf(
            "timeBounds" to base.copy(timeBounds = TimeBounds(1700000000, 1700003601)),
            "ledgerBounds" to base.copy(ledgerBounds = LedgerBounds(10, 21)),
            "minSequenceNumber" to base.copy(minSequenceNumber = 4L),
            "minSequenceAge" to base.copy(minSequenceAge = 5L),
            "minSequenceLedgerGap" to base.copy(minSequenceLedgerGap = 8),
            "extraSigners" to base.copy(extraSigners = listOf(signer(2)))
        )

        assertEquals(base, base.copy())
        for ((property, variant) in variants) {
            assertNotEquals(base, variant, property)
        }
    }

    @Test
    fun testEqualsHandlesSelfNullAndOtherTypes() {
        val decoded = decode(V2_TIME_HEX)

        assertEquals(decoded, decoded)
        assertFalse(decoded.equals(null))
        assertFalse(decoded.equals(decoded.toXdr()))
    }
}
