package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * XDR round-trip tests for all operation types.
 * Each test creates an operation, converts to XDR, and back.
 */
class OperationsXdrRoundtripTest {

    companion object {
        const val ACCOUNT_A = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        const val ACCOUNT_B = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        const val ACCOUNT_C = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        val ASSET_NATIVE = AssetTypeNative
        val ASSET_USD = AssetTypeCreditAlphaNum4("USD", ACCOUNT_A)
        val ASSET_EUR = AssetTypeCreditAlphaNum4("EUR", ACCOUNT_B)
        val ASSET_LONG = AssetTypeCreditAlphaNum12("LONGASSET", ACCOUNT_A)
        val MUXED_B: String = MuxedAccount(ACCOUNT_B, 42UL).address

        const val BALANCE_HASH_HEX = ClaimableBalanceVectors.hashHex

        /** That balance in the spelling Horizon reports and the operations report back. */
        const val BALANCE_PADDED_HEX = ClaimableBalanceVectors.paddedHex

        val BALANCE_SPELLINGS = ClaimableBalanceVectors.spellings

        /** The pool of testnet transaction 25e65885d13542f523597f9f46844cd80eb1fd9b87fc6b1a3c8811487b625053. */
        const val POOL_ID = "74b85a9c1802cc0b3882858ab907b4678bc05209768088b8bc713f41442ff090"

        /**
         * Ids naming no claimable balance, beyond [ClaimableBalanceVectors.rejections]: lengths
         * no spelling has, and an XDR form carrying its discriminant in the last byte.
         */
        val BALANCE_REJECTIONS = listOf(
            "abc",
            BALANCE_HASH_HEX.dropLast(1),
            BALANCE_PADDED_HEX + "0",
            "00000001$BALANCE_HASH_HEX"
        ) + ClaimableBalanceVectors.rejections

        /** The XDR bytes [body] encodes to, which is what equivalent spellings must share. */
        fun encodedBody(body: OperationBodyXdr): ByteArray {
            val writer = XdrWriter()
            body.encode(writer)
            return writer.toByteArray()
        }
    }

    // ========== CreateAccountOperation ==========
    @Test
    fun testCreateAccountRoundtrip() {
        val op = CreateAccountOperation(ACCOUNT_B, "100.0000000")
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.CreateAccountOp -> CreateAccountOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ACCOUNT_B, restored.destination)
        assertEquals("100.0000000", restored.startingBalance)
    }

    @Test
    fun testCreateAccountWithSourceAccount() {
        val op = CreateAccountOperation(ACCOUNT_B, "50.0000000")
        op.sourceAccount = ACCOUNT_C
        val opXdr = op.toXdr()
        val restored = Operation.fromXdr(opXdr)
        assertTrue(restored is CreateAccountOperation)
        assertEquals(ACCOUNT_C, restored.sourceAccount)
        assertEquals(ACCOUNT_B, restored.destination)
    }

    @Test
    fun testCreateAccountInvalidDestination() {
        assertFailsWith<IllegalArgumentException> {
            CreateAccountOperation("INVALID", "100.0")
        }
    }

    // ========== PaymentOperation ==========
    @Test
    fun testPaymentRoundtrip() {
        val op = PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "200.0000000")
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.PaymentOp -> PaymentOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ACCOUNT_B, restored.destination)
        assertEquals(ASSET_NATIVE, restored.asset)
        assertEquals("200.0000000", restored.amount)
    }

    @Test
    fun testPaymentWithCreditAsset() {
        val op = PaymentOperation(ACCOUNT_B, ASSET_USD, "10.5000000")
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.PaymentOp -> PaymentOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ASSET_USD, restored.asset)
    }

    // ========== PathPaymentStrictReceive ==========
    @Test
    fun testPathPaymentStrictReceiveRoundtrip() {
        val op = PathPaymentStrictReceiveOperation(
            sendAsset = ASSET_NATIVE,
            sendMax = "100.0000000",
            destination = ACCOUNT_B,
            destAsset = ASSET_USD,
            destAmount = "50.0000000",
            path = listOf(ASSET_EUR)
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.PathPaymentStrictReceiveOp -> PathPaymentStrictReceiveOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ASSET_NATIVE, restored.sendAsset)
        assertEquals("100.0000000", restored.sendMax)
        assertEquals(ACCOUNT_B, restored.destination)
        assertEquals(ASSET_USD, restored.destAsset)
        assertEquals("50.0000000", restored.destAmount)
        assertEquals(1, restored.path.size)
        assertEquals(ASSET_EUR, restored.path[0])
    }

    @Test
    fun testPathPaymentStrictReceiveEmptyPath() {
        val op = PathPaymentStrictReceiveOperation(
            sendAsset = ASSET_NATIVE,
            sendMax = "100.0000000",
            destination = ACCOUNT_B,
            destAsset = ASSET_USD,
            destAmount = "50.0000000"
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.PathPaymentStrictReceiveOp -> PathPaymentStrictReceiveOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.path.isEmpty())
    }

    @Test
    fun testPathPaymentStrictReceiveTooManyPaths() {
        assertFailsWith<IllegalArgumentException> {
            PathPaymentStrictReceiveOperation(
                sendAsset = ASSET_NATIVE,
                sendMax = "100.0000000",
                destination = ACCOUNT_B,
                destAsset = ASSET_USD,
                destAmount = "50.0000000",
                path = listOf(ASSET_USD, ASSET_EUR, ASSET_NATIVE, ASSET_LONG, ASSET_USD, ASSET_EUR)
            )
        }
    }

    // ========== PathPaymentStrictSend ==========
    @Test
    fun testPathPaymentStrictSendRoundtrip() {
        val op = PathPaymentStrictSendOperation(
            sendAsset = ASSET_USD,
            sendAmount = "25.0000000",
            destination = ACCOUNT_B,
            destAsset = ASSET_NATIVE,
            destMin = "10.0000000",
            path = listOf(ASSET_EUR)
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.PathPaymentStrictSendOp -> PathPaymentStrictSendOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ASSET_USD, restored.sendAsset)
        assertEquals("25.0000000", restored.sendAmount)
        assertEquals(ACCOUNT_B, restored.destination)
        assertEquals(ASSET_NATIVE, restored.destAsset)
        assertEquals("10.0000000", restored.destMin)
        assertEquals(1, restored.path.size)
    }

    // ========== ManageSellOffer ==========
    @Test
    fun testManageSellOfferRoundtrip() {
        val op = ManageSellOfferOperation(
            selling = ASSET_USD,
            buying = ASSET_NATIVE,
            amount = "100.0000000",
            price = Price(1, 2),
            offerId = 12345
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ManageSellOfferOp -> ManageSellOfferOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ASSET_USD, restored.selling)
        assertEquals(ASSET_NATIVE, restored.buying)
        assertEquals("100.0000000", restored.amount)
        assertEquals(Price(1, 2), restored.price)
        assertEquals(12345L, restored.offerId)
    }

    @Test
    fun testManageSellOfferNewOffer() {
        val op = ManageSellOfferOperation(
            selling = ASSET_USD,
            buying = ASSET_NATIVE,
            amount = "100.0000000",
            price = Price(1, 1)
        )
        assertEquals(0L, op.offerId)
    }

    // ========== ManageBuyOffer ==========
    @Test
    fun testManageBuyOfferRoundtrip() {
        val op = ManageBuyOfferOperation(
            selling = ASSET_NATIVE,
            buying = ASSET_USD,
            buyAmount = "50.0000000",
            price = Price(3, 4),
            offerId = 99
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ManageBuyOfferOp -> ManageBuyOfferOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ASSET_NATIVE, restored.selling)
        assertEquals(ASSET_USD, restored.buying)
        assertEquals("50.0000000", restored.buyAmount)
        assertEquals(Price(3, 4), restored.price)
        assertEquals(99L, restored.offerId)
    }

    // ========== CreatePassiveSellOffer ==========
    @Test
    fun testCreatePassiveSellOfferRoundtrip() {
        val op = CreatePassiveSellOfferOperation(
            selling = ASSET_USD,
            buying = ASSET_NATIVE,
            amount = "200.0000000",
            price = Price(1, 1)
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.CreatePassiveSellOfferOp -> CreatePassiveSellOfferOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ASSET_USD, restored.selling)
        assertEquals(ASSET_NATIVE, restored.buying)
        assertEquals("200.0000000", restored.amount)
        assertEquals(Price(1, 1), restored.price)
    }

    // ========== SetOptions ==========
    @Test
    fun testSetOptionsAllFields() {
        val signerPubKey = StrKey.decodeEd25519PublicKey(ACCOUNT_B)
        val signerKey = SignerKey.Ed25519PublicKey(signerPubKey)
        val op = SetOptionsOperation(
            inflationDestination = ACCOUNT_B,
            clearFlags = 1,
            setFlags = 2,
            masterKeyWeight = 10,
            lowThreshold = 1,
            mediumThreshold = 5,
            highThreshold = 10,
            homeDomain = "example.com",
            signer = signerKey,
            signerWeight = 5
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.SetOptionsOp -> SetOptionsOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ACCOUNT_B, restored.inflationDestination)
        assertEquals(1, restored.clearFlags)
        assertEquals(2, restored.setFlags)
        assertEquals(10, restored.masterKeyWeight)
        assertEquals(1, restored.lowThreshold)
        assertEquals(5, restored.mediumThreshold)
        assertEquals(10, restored.highThreshold)
        assertEquals("example.com", restored.homeDomain)
        assertNotNull(restored.signer)
        assertEquals(5, restored.signerWeight)
    }

    @Test
    fun testSetOptionsMinimal() {
        val op = SetOptionsOperation()
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.SetOptionsOp -> SetOptionsOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertNull(restored.inflationDestination)
        assertNull(restored.clearFlags)
        assertNull(restored.setFlags)
        assertNull(restored.masterKeyWeight)
        assertNull(restored.lowThreshold)
        assertNull(restored.mediumThreshold)
        assertNull(restored.highThreshold)
        assertNull(restored.homeDomain)
        assertNull(restored.signer)
        assertNull(restored.signerWeight)
    }

    @Test
    fun testSetOptionsValidation() {
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(masterKeyWeight = 256)
        }
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(lowThreshold = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(mediumThreshold = 300)
        }
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(highThreshold = -5)
        }
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(signerWeight = 256)
        }
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(clearFlags = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(setFlags = -1)
        }
        // signer without weight
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(signer = SignerKey.Ed25519PublicKey(StrKey.decodeEd25519PublicKey(ACCOUNT_B)))
        }
        // weight without signer
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(signerWeight = 5)
        }
    }

    @Test
    fun testSetOptionsSignerWeightAboveRangeFromV0Envelope() = runTest {
        // A V0 envelope encoded by the reference XDR tool, with time bounds {0, 2^64-1} and three signer
        // weights above 255, which the network accepted before protocol 10; the hash was computed from
        // the envelope bytes without the SDK.
        val envelope = "AAAAAGfCkKADrIUPUvc+CgLveti1zPocODhBQUZ9T6Rp6ARfAAABLAAAAEAAAAACAAAAAQAAAAAAAAAA//////////8AAAAAAAAAAwAAAAAAAAAFAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAFcDGhVgMTQUM2bhKpDZboi2XN1axvAEogNuScrpNRNpAAABAAAAAAAAAAAFAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAFcDGhVgMTQUM2bhKpDZboi2XN1axvAEogNuScrpNRNpgAAAAAAAAAAAAAAFAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAFcDGhVgMTQUM2bhKpDZboi2XN1axvAEogNuScrpNRNp/////wAAAAAAAAAA"
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        assertEquals(TimeBounds.ofUnsigned(0uL, ULong.MAX_VALUE), tx.preconditions.timeBounds)
        val weights = tx.operations.map { (it as SetOptionsOperation).signerWeightExact }
        assertEquals(listOf(256u, 2147483648u, UInt.MAX_VALUE), weights)
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals("5cf83e9fc1b9985c5bcd1b04bda669b14aa5d439d06792062bdc5597f1044725", tx.hashHex())
    }

    @Test
    fun testSetOptionsReadSignerWeightAboveRange() {
        val signer = SignerKey.Ed25519PublicKey(StrKey.decodeEd25519PublicKey(ACCOUNT_B))
        fun read(weight: UInt) = SetOptionsOperation.fromXdr(
            SetOptionsOpXdr(
                inflationDest = null, clearFlags = null, setFlags = null, masterWeight = null, lowThreshold = null,
                medThreshold = null, highThreshold = null, homeDomain = null,
                signer = SignerXdr(signer.toXdr(), Uint32Xdr(weight))
            )
        )
        val max = read(UInt.MAX_VALUE)
        assertFailsWith<IllegalStateException> { max.signerWeight }
        assertFailsWith<IllegalStateException> { max.component10() }
        assertFailsWith<IllegalStateException> { max.copy() }
        assertEquals(
            "SetOptionsOperation(inflationDestination=null, clearFlags=null, setFlags=null, masterKeyWeight=null, " +
                "lowThreshold=null, mediumThreshold=null, highThreshold=null, homeDomain=null, signer=$signer, " +
                "signerWeight=4294967295)",
            max.toString()
        )
        assertEquals(256, read(256u).signerWeight)
        assertFailsWith<IllegalArgumentException> { read(256u).copy() }
        assertNotEquals(read(256u), read(257u))
        val built = SetOptionsOperation(signer = signer, signerWeight = 255)
        assertEquals(built, read(255u))
        assertEquals(built.hashCode(), read(255u).hashCode())
        assertEquals(built, read(255u).copy())
    }

    @Test
    fun testSetOptionsDestructuringAndEqualityCoverEveryProperty() {
        val signer = SignerKey.Ed25519PublicKey(StrKey.decodeEd25519PublicKey(ACCOUNT_B))
        val op = SetOptionsOperation(ACCOUNT_B, 1, 2, 3, 4, 5, 6, "example.com", signer, 7)
        val (inflationDestination, clearFlags, setFlags, masterKeyWeight, lowThreshold, mediumThreshold,
            highThreshold, homeDomain, signerKey, signerWeight) = op
        assertEquals(
            listOf<Any?>(ACCOUNT_B, 1, 2, 3, 4, 5, 6, "example.com", signer, 7),
            listOf(inflationDestination, clearFlags, setFlags, masterKeyWeight, lowThreshold, mediumThreshold,
                highThreshold, homeDomain, signerKey, signerWeight)
        )
        val otherSigner = SignerKey.Ed25519PublicKey(StrKey.decodeEd25519PublicKey(ACCOUNT_C))
        val variants = listOf(
            op.copy(inflationDestination = ACCOUNT_A), op.copy(clearFlags = 9), op.copy(setFlags = 9),
            op.copy(masterKeyWeight = 9), op.copy(lowThreshold = 9), op.copy(mediumThreshold = 9),
            op.copy(highThreshold = 9), op.copy(homeDomain = "other.com"), op.copy(signer = otherSigner),
            op.copy(signerWeight = 8)
        )
        variants.forEach { assertNotEquals(op, it) }
        assertNotEquals<Any>(op, InflationOperation())
    }

    @Test
    fun testSetOptionsSignerWithEmptyPayloadFails() {
        val empty = SignerKey.ed25519SignedPayload(StrKey.decodeEd25519PublicKey(ACCOUNT_B), ByteArray(0))
        assertFailsWith<IllegalArgumentException> { SetOptionsOperation(signer = empty, signerWeight = 1) }
        val body = SetOptionsOpXdr(
            inflationDest = null, clearFlags = null, setFlags = null, masterWeight = null, lowThreshold = null,
            medThreshold = null, highThreshold = null, homeDomain = null,
            signer = SignerXdr(empty.toXdr(), Uint32Xdr(1u))
        )
        assertFailsWith<IllegalArgumentException> { SetOptionsOperation.fromXdr(body) }
    }

    @Test
    fun testSetOptionsHomeDomainTooLong() {
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(homeDomain = "a".repeat(33))
        }
    }

    @Test
    fun testSetOptionsInvalidInflationDestination() {
        assertFailsWith<IllegalArgumentException> {
            SetOptionsOperation(inflationDestination = "INVALID")
        }
    }

    // ========== ManageData ==========
    @Test
    fun testManageDataRoundtrip() {
        val value = "hello world".encodeToByteArray()
        val op = ManageDataOperation("test_key", value)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ManageDataOp -> ManageDataOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals("test_key", restored.name)
        assertTrue(value.contentEquals(restored.value!!))
    }

    @Test
    fun testManageDataDeleteEntry() {
        val op = ManageDataOperation("test_key", null)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ManageDataOp -> ManageDataOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals("test_key", restored.name)
        assertNull(restored.value)
    }

    @Test
    fun testManageDataForString() {
        val op = ManageDataOperation.forString("test_key", "hello")
        assertEquals("test_key", op.name)
        assertTrue("hello".encodeToByteArray().contentEquals(op.value!!))
    }

    @Test
    fun testManageDataForStringNull() {
        val op = ManageDataOperation.forString("test_key", null)
        assertNull(op.value)
    }

    @Test
    fun testManageDataValidation() {
        assertFailsWith<IllegalArgumentException> {
            ManageDataOperation("", ByteArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            ManageDataOperation("a".repeat(65), ByteArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            ManageDataOperation("key", ByteArray(65))
        }
    }

    @Test
    fun testManageDataEmptyNameThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ManageDataOperation("", null)
        }
        assertEquals("Data entry name cannot be empty", exception.message)
    }

    @Test
    fun testManageDataNameOfSpacesRoundtrip() {
        // stellar-core accepts a name of 1 to 64 printable ASCII bytes, and a space is printable.
        // The body is type 10 (MANAGE_DATA), the name as string64 (length, bytes, zero padding)
        // and the present value "v".
        val encodedNames = mapOf(" " to "0000000120000000", "  " to "0000000220200000")
        for ((name, encodedName) in encodedNames) {
            val op = ManageDataOperation(name, "v".encodeToByteArray())
            val bytes = encodedBody(op.toOperationBody())
            assertEquals("0000000a${encodedName}000000010000000176000000", Util.bytesToHex(bytes))
            val decoded = OperationBodyXdr.decode(XdrReader(bytes)) as OperationBodyXdr.ManageDataOp
            assertEquals(op, ManageDataOperation.fromXdr(decoded.value), "\"$name\" must read back equal")
        }
    }

    @Test
    fun testManageDataNameOfSpaceFromAppliedTransaction() = runTest {
        // A testnet transaction applied with txSUCCESS in ledger 5829124.
        val envelope = "AAAAAgAAAAAkns0kPl6G+DY63Bb7+24+SWf4l79qWyuv1/dZxJzwlAAAA+gAWPIAAAAAAQAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAACgAAAAEgAAAAAAAAAQAAAAF2AAAAAAAAAAAAAAHEnPCUAAAAQA8R0mGR0d8fsDaUYNour6JNcAWPRau5UE4Z7ECVp36iPJcjoVLKduTZvq2qIBnmQ1fQouMB9bZRGpkbV0troAY="
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        assertEquals(ManageDataOperation(" ", "v".encodeToByteArray()), tx.operations.single())
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals("b6ffe005434126d1afec562da2e7b0d78bd2e06a6c86b786ce7e4da1fb1b51a0", tx.hashHex())
    }

    @Test
    fun testManageDataEquality() {
        val op1 = ManageDataOperation("key", "value".encodeToByteArray())
        val op2 = ManageDataOperation("key", "value".encodeToByteArray())
        val op3 = ManageDataOperation("key", null)
        val op4 = ManageDataOperation("key2", "value".encodeToByteArray())
        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1, op3)
        assertNotEquals(op1, op4)
        assertNotEquals(op3, op1)
    }

    // ========== BumpSequence ==========
    @Test
    fun testBumpSequenceRoundtrip() {
        val op = BumpSequenceOperation(12345678L)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.BumpSequenceOp -> BumpSequenceOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(12345678L, restored.bumpTo)
    }

    @Test
    fun testBumpSequenceZero() {
        val op = BumpSequenceOperation(0)
        assertEquals(0L, op.bumpTo)
    }

    @Test
    fun testBumpSequenceNegative() {
        assertFailsWith<IllegalArgumentException> {
            BumpSequenceOperation(-1)
        }
    }

    // ========== AccountMerge ==========
    @Test
    fun testAccountMergeRoundtrip() {
        val op = AccountMergeOperation(ACCOUNT_B)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.Destination -> AccountMergeOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ACCOUNT_B, restored.destination)
    }

    @Test
    fun testAccountMergeInvalidDestination() {
        assertFailsWith<IllegalArgumentException> {
            AccountMergeOperation("INVALID")
        }
    }

    // ========== Inflation ==========
    @Test
    fun testInflationRoundtrip() {
        val op = InflationOperation()
        val xdr = op.toOperationBody()
        assertTrue(xdr is OperationBodyXdr.Void)
    }

    @Test
    fun testInflationEquality() {
        val op1 = InflationOperation()
        val op2 = InflationOperation()
        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
    }

    // ========== BeginSponsoringFutureReserves ==========
    @Test
    fun testBeginSponsoringRoundtrip() {
        val op = BeginSponsoringFutureReservesOperation(ACCOUNT_B)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.BeginSponsoringFutureReservesOp -> BeginSponsoringFutureReservesOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ACCOUNT_B, restored.sponsoredId)
    }

    @Test
    fun testBeginSponsoringInvalidAccount() {
        assertFailsWith<IllegalArgumentException> {
            BeginSponsoringFutureReservesOperation("INVALID")
        }
    }

    // ========== EndSponsoringFutureReserves ==========
    @Test
    fun testEndSponsoringRoundtrip() {
        val op = EndSponsoringFutureReservesOperation()
        val xdr = op.toOperationBody()
        assertTrue(xdr is OperationBodyXdr.Void)
    }

    @Test
    fun testEndSponsoringEquality() {
        val op1 = EndSponsoringFutureReservesOperation()
        val op2 = EndSponsoringFutureReservesOperation()
        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
    }

    // ========== SetTrustLineFlags ==========
    @Test
    fun testSetTrustLineFlagsRoundtrip() {
        val op = SetTrustLineFlagsOperation(
            trustor = ACCOUNT_B,
            asset = ASSET_USD,
            clearFlags = SetTrustLineFlagsOperation.TRUSTLINE_CLAWBACK_ENABLED_FLAG,
            setFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.SetTrustLineFlagsOp -> SetTrustLineFlagsOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ACCOUNT_B, restored.trustor)
        assertEquals(ASSET_USD, restored.asset)
        assertEquals(SetTrustLineFlagsOperation.TRUSTLINE_CLAWBACK_ENABLED_FLAG, restored.clearFlags)
        assertEquals(SetTrustLineFlagsOperation.AUTHORIZED_FLAG, restored.setFlags)
    }

    @Test
    fun testSetTrustLineFlagsNoFlags() {
        val op = SetTrustLineFlagsOperation(
            trustor = ACCOUNT_B,
            asset = ASSET_USD
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.SetTrustLineFlagsOp -> SetTrustLineFlagsOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertNull(restored.clearFlags)
        assertNull(restored.setFlags)
    }

    @Test
    fun testSetTrustLineFlagsInvalidTrustor() {
        assertFailsWith<IllegalArgumentException> {
            SetTrustLineFlagsOperation("INVALID", ASSET_USD)
        }
    }

    @Test
    fun testSetTrustLineFlagsCannotUseNativeAsset() {
        assertFailsWith<IllegalArgumentException> {
            SetTrustLineFlagsOperation(ACCOUNT_B, ASSET_NATIVE)
        }
    }

    @Test
    fun testSetTrustLineFlagsConstants() {
        assertEquals(1, SetTrustLineFlagsOperation.AUTHORIZED_FLAG)
        assertEquals(2, SetTrustLineFlagsOperation.AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG)
        assertEquals(4, SetTrustLineFlagsOperation.TRUSTLINE_CLAWBACK_ENABLED_FLAG)
    }

    // ========== ExtendFootprintTTL ==========
    @Test
    fun testExtendFootprintTTLRoundtrip() {
        val op = ExtendFootprintTTLOperation(10000)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ExtendFootprintTTLOp -> ExtendFootprintTTLOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(10000, restored.extendTo)
    }

    @Test
    fun testExtendFootprintTTLInvalidValue() {
        assertFailsWith<IllegalArgumentException> {
            ExtendFootprintTTLOperation(-5)
        }
    }

    @Test
    fun testExtendFootprintTTLZeroRoundtrip() {
        // stellar-core accepts extendTo = 0; it rejects only values above the maximum entry TTL.
        val op = ExtendFootprintTTLOperation(0)
        val restored = Operation.fromXdr(op.toXdr())
        assertEquals(0, (restored as ExtendFootprintTTLOperation).extendTo)
    }

    @Test
    fun testExtendFootprintTTLZeroFromAppliedTransaction() {
        // Testnet transaction 21f5b9f42f5166038d3fb0d1bda5d13cc8b1fc1cdffaedb0bd76680c44180dec
        // (ledger 3619212), applied with txSUCCESS.
        val envelope = "AAAAAgAAAADk3ricDJaFupVzD6HMeSxXdKSFjYEhxD+gPzdOckqKAAAALiAANxvsAAAABQAAAAAAAAAAAAAAAQAAAAAAAAAZAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAALbwAAAABckqKAAAAAECiDbdEWMvR2egvrPbSUS2u4aiA0/lbN1h6ZjYw9VEscaChGj1SWAwmitqs5UOSF3v9eWSJlzDwPVo8lWbZBvQJ"
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        assertEquals(0, (tx.operations.single() as ExtendFootprintTTLOperation).extendTo)
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
    }

    // ========== RestoreFootprint ==========
    @Test
    fun testRestoreFootprintRoundtrip() {
        val op = RestoreFootprintOperation()
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RestoreFootprintOp -> RestoreFootprintOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertNotNull(restored)
    }

    @Test
    fun testRestoreFootprintEquality() {
        val op1 = RestoreFootprintOperation()
        val op2 = RestoreFootprintOperation()
        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
    }

    // ========== ChangeTrust ==========
    @Test
    fun testChangeTrustAssetRoundtrip() {
        val op = ChangeTrustOperation(ASSET_USD, "1000.0000000")
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ChangeTrustOp -> ChangeTrustOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ASSET_USD, restored.asset)
        assertEquals("1000.0000000", restored.limit)
    }

    @Test
    fun testChangeTrustDefaultLimit() {
        val op = ChangeTrustOperation(ASSET_USD)
        assertEquals(ChangeTrustOperation.MAX_LIMIT, op.limit)
    }

    @Test
    fun testChangeTrustCannotUseNativeAsset() {
        assertFailsWith<IllegalArgumentException> {
            ChangeTrustOperation(ASSET_NATIVE)
        }
    }

    @Test
    fun testChangeTrustWithLiquidityPool() {
        val pool = LiquidityPool(ASSET_EUR, ASSET_USD)
        val op = ChangeTrustOperation(pool)
        assertEquals(ChangeTrustOperation.MAX_LIMIT, op.limit)
        assertNotNull(op.line)
        assertFailsWith<UnsupportedOperationException> {
            op.asset
        }
    }

    // ========== AllowTrust ==========
    @Test
    fun testAllowTrustRoundtripAlphaNum4() {
        val op = AllowTrustOperation(
            trustor = ACCOUNT_B,
            assetCode = "USD",
            authorize = 1
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.AllowTrustOp -> AllowTrustOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ACCOUNT_B, restored.trustor)
        assertEquals("USD", restored.assetCode)
        assertEquals(1, restored.authorize)
    }

    @Test
    fun testAllowTrustRoundtripAlphaNum12() {
        val op = AllowTrustOperation(
            trustor = ACCOUNT_B,
            assetCode = "LONGCODE",
            authorize = 2
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.AllowTrustOp -> AllowTrustOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals("LONGCODE", restored.assetCode)
        assertEquals(2, restored.authorize)
    }

    @Test
    fun testAllowTrustValidation() {
        assertFailsWith<IllegalArgumentException> {
            AllowTrustOperation("INVALID", "USD", 1)
        }
        assertFailsWith<IllegalArgumentException> {
            AllowTrustOperation(ACCOUNT_B, "", 1)
        }
        assertFailsWith<IllegalArgumentException> {
            AllowTrustOperation(ACCOUNT_B, "A".repeat(13), 1)
        }
        assertFailsWith<IllegalArgumentException> {
            AllowTrustOperation(ACCOUNT_B, "USD", 3)
        }
        assertFailsWith<IllegalArgumentException> {
            AllowTrustOperation(ACCOUNT_B, "USD", -1)
        }
    }

    // ========== CreateClaimableBalance ==========
    @Test
    fun testCreateClaimableBalanceRoundtrip() {
        val claimants = listOf(
            Claimant(ACCOUNT_B, ClaimPredicate.Unconditional),
            Claimant(ACCOUNT_C, ClaimPredicate.BeforeAbsoluteTime(1700000000))
        )
        val op = CreateClaimableBalanceOperation(ASSET_NATIVE, "100.0000000", claimants)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.CreateClaimableBalanceOp -> CreateClaimableBalanceOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ASSET_NATIVE, restored.asset)
        assertEquals("100.0000000", restored.amount)
        assertEquals(2, restored.claimants.size)
        assertEquals(ACCOUNT_B, restored.claimants[0].destination)
        assertTrue(restored.claimants[0].predicate is ClaimPredicate.Unconditional)
    }

    @Test
    fun testCreateClaimableBalanceEmptyClaimants() {
        assertFailsWith<IllegalArgumentException> {
            CreateClaimableBalanceOperation(ASSET_NATIVE, "100.0000000", emptyList())
        }
    }

    @Test
    fun testCreateClaimableBalanceMaximumClaimants() {
        // Stellar-transaction.x: Claimant claimants<10>;
        val claimants = List(10) { Claimant(ACCOUNT_B, ClaimPredicate.Unconditional) }
        val op = CreateClaimableBalanceOperation(ASSET_NATIVE, "100.0000000", claimants)
        assertEquals(claimants, op.claimants)
        assertEquals(10, (op.toOperationBody() as OperationBodyXdr.CreateClaimableBalanceOp).value.claimants.size)
    }

    @Test
    fun testCreateClaimableBalanceTooManyClaimants() {
        // Stellar-transaction.x: Claimant claimants<10>;
        val error = assertFailsWith<IllegalArgumentException> {
            CreateClaimableBalanceOperation(ASSET_NATIVE, "100.0000000",
                List(11) { Claimant(ACCOUNT_B, ClaimPredicate.Unconditional) })
        }
        assertEquals("Claimant count 11 exceeds maximum 10", error.message)
    }

    // ========== ClaimClaimableBalance ==========
    @Test
    fun testClaimClaimableBalanceRoundtrip() {
        val balanceId = "00000000" + "a".repeat(64)
        val op = ClaimClaimableBalanceOperation(balanceId)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ClaimClaimableBalanceOp -> ClaimClaimableBalanceOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(balanceId, restored.balanceId)
    }

    @Test
    fun testClaimClaimableBalanceAcceptsEverySpelling() {
        val canonical = ClaimClaimableBalanceOperation(BALANCE_PADDED_HEX).toOperationBody()
        for (spelling in BALANCE_SPELLINGS) {
            val op = ClaimClaimableBalanceOperation(spelling)
            assertTrue(
                encodedBody(op.toOperationBody()).contentEquals(encodedBody(canonical)),
                "spelling $spelling must reach the wire as the same bytes"
            )
            val restored = when (val xdr = op.toOperationBody()) {
                is OperationBodyXdr.ClaimClaimableBalanceOp ->
                    ClaimClaimableBalanceOperation.fromXdr(xdr.value)
                else -> fail("Wrong XDR type")
            }
            assertEquals(
                BALANCE_PADDED_HEX, restored.balanceId,
                "spelling $spelling must be reported in one form"
            )
        }
    }

    @Test
    fun testClaimClaimableBalanceRejectsAnIdNamingNoBalance() {
        for (candidate in BALANCE_REJECTIONS) {
            val resolver = assertFailsWith<IllegalArgumentException>(
                "\"$candidate\" must name no balance"
            ) { ClaimableBalanceId.forId(candidate) }
            val failure = assertFailsWith<IllegalArgumentException>("\"$candidate\" must be rejected") {
                ClaimClaimableBalanceOperation(candidate)
            }
            assertEquals(
                resolver.message, failure.message,
                "\"$candidate\": the operation must reject as the resolver does"
            )
        }
    }

    @Test
    fun testClaimClaimableBalanceCopyJudgesTheNewId() {
        // copy() runs the primary constructor, so an id put in this way is held to the same
        // rules as one given at construction.
        val op = ClaimClaimableBalanceOperation(BALANCE_PADDED_HEX)
        for (candidate in BALANCE_REJECTIONS) {
            assertFailsWith<IllegalArgumentException>("copy to \"$candidate\" must be rejected") {
                op.copy(balanceId = candidate)
            }
        }
    }

    // ========== ClawbackClaimableBalance ==========
    @Test
    fun testClawbackClaimableBalanceRoundtrip() {
        val balanceId = "00000000" + "b".repeat(64)
        val op = ClawbackClaimableBalanceOperation(balanceId)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ClawbackClaimableBalanceOp -> ClawbackClaimableBalanceOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(balanceId, restored.balanceId)
    }

    @Test
    fun testClawbackClaimableBalanceAcceptsEverySpelling() {
        val canonical = ClawbackClaimableBalanceOperation(BALANCE_PADDED_HEX).toOperationBody()
        for (spelling in BALANCE_SPELLINGS) {
            val op = ClawbackClaimableBalanceOperation(spelling)
            assertTrue(
                encodedBody(op.toOperationBody()).contentEquals(encodedBody(canonical)),
                "spelling $spelling must reach the wire as the same bytes"
            )
            val restored = when (val xdr = op.toOperationBody()) {
                is OperationBodyXdr.ClawbackClaimableBalanceOp ->
                    ClawbackClaimableBalanceOperation.fromXdr(xdr.value)
                else -> fail("Wrong XDR type")
            }
            assertEquals(
                BALANCE_PADDED_HEX, restored.balanceId,
                "spelling $spelling must be reported in one form"
            )
        }
    }

    @Test
    fun testClawbackClaimableBalanceRejectsAnIdNamingNoBalance() {
        for (candidate in BALANCE_REJECTIONS) {
            val resolver = assertFailsWith<IllegalArgumentException>(
                "\"$candidate\" must name no balance"
            ) { ClaimableBalanceId.forId(candidate) }
            val failure = assertFailsWith<IllegalArgumentException>("\"$candidate\" must be rejected") {
                ClawbackClaimableBalanceOperation(candidate)
            }
            assertEquals(
                resolver.message, failure.message,
                "\"$candidate\": the operation must reject as the resolver does"
            )
        }
    }

    @Test
    fun testClawbackClaimableBalanceCopyJudgesTheNewId() {
        val op = ClawbackClaimableBalanceOperation(BALANCE_PADDED_HEX)
        for (candidate in BALANCE_REJECTIONS) {
            assertFailsWith<IllegalArgumentException>("copy to \"$candidate\" must be rejected") {
                op.copy(balanceId = candidate)
            }
        }
    }

    // ========== Clawback ==========
    @Test
    fun testClawbackRoundtrip() {
        val op = ClawbackOperation(
            from = ACCOUNT_B,
            asset = ASSET_USD,
            amount = "500.0000000"
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.ClawbackOp -> ClawbackOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(ACCOUNT_B, restored.from)
        assertEquals(ASSET_USD, restored.asset)
        assertEquals("500.0000000", restored.amount)
    }

    @Test
    fun testClawbackCannotUseNative() {
        assertFailsWith<IllegalArgumentException> {
            ClawbackOperation(ACCOUNT_B, ASSET_NATIVE, "100.0000000")
        }
    }

    // ========== LiquidityPoolDeposit ==========
    @Test
    fun testLiquidityPoolDepositRoundtrip() {
        val poolId = "a".repeat(64)
        val op = LiquidityPoolDepositOperation(
            liquidityPoolId = poolId,
            maxAmountA = "100.0000000",
            maxAmountB = "200.0000000",
            minPrice = Price(1, 2),
            maxPrice = Price(2, 1)
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.LiquidityPoolDepositOp -> LiquidityPoolDepositOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(poolId, restored.liquidityPoolId)
        assertEquals("100.0000000", restored.maxAmountA)
        assertEquals("200.0000000", restored.maxAmountB)
    }

    @Test
    fun testLiquidityPoolDepositInvalidPoolId() {
        assertFailsWith<IllegalArgumentException> {
            LiquidityPoolDepositOperation("short", "1.0", "1.0", Price(1,1), Price(1,1))
        }
    }

    // ========== LiquidityPoolWithdraw ==========
    @Test
    fun testLiquidityPoolWithdrawRoundtrip() {
        val poolId = "b".repeat(64)
        val op = LiquidityPoolWithdrawOperation(
            liquidityPoolId = poolId,
            amount = "50.0000000",
            minAmountA = "10.0000000",
            minAmountB = "20.0000000"
        )
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.LiquidityPoolWithdrawOp -> LiquidityPoolWithdrawOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals(poolId, restored.liquidityPoolId)
        assertEquals("50.0000000", restored.amount)
    }

    // ========== InvokeHostFunction ==========
    @Test
    fun testInvokeHostFunctionUploadWasm() {
        val wasmBytes = "test wasm bytes".encodeToByteArray()
        val op = InvokeHostFunctionOperation.uploadContractWasm(wasmBytes)
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.InvokeHostFunctionOp -> InvokeHostFunctionOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.hostFunction is HostFunctionXdr.Wasm)
    }

    @Test
    fun testInvokeHostFunctionCreateContract() {
        val wasmId = "a".repeat(64)
        val address = Address(ACCOUNT_A)
        val salt = ByteArray(32) { 0 }
        val op = InvokeHostFunctionOperation.createContract(
            wasmId = wasmId,
            address = address,
            salt = salt
        )
        assertNotNull(op)
        assertTrue(op.hostFunction is HostFunctionXdr.CreateContract)
    }

    @Test
    fun testInvokeHostFunctionCreateContractV2WithConstructorArgs() {
        val wasmId = "a".repeat(64)
        val address = Address(ACCOUNT_A)
        val salt = ByteArray(32) { 0 }
        val op = InvokeHostFunctionOperation.createContract(
            wasmId = wasmId,
            address = address,
            constructorArgs = listOf(SCValXdr.B(true)),
            salt = salt
        )
        assertNotNull(op)
        assertTrue(op.hostFunction is HostFunctionXdr.CreateContractV2)
    }

    @Test
    fun testInvokeHostFunctionCreateContractInvalidWasmId() {
        assertFailsWith<IllegalArgumentException> {
            InvokeHostFunctionOperation.createContract(
                wasmId = "short",
                address = Address(ACCOUNT_A),
                salt = ByteArray(32)
            )
        }
    }

    @Test
    fun testInvokeHostFunctionCreateContractInvalidSalt() {
        assertFailsWith<IllegalArgumentException> {
            InvokeHostFunctionOperation.createContract(
                wasmId = "a".repeat(64),
                address = Address(ACCOUNT_A),
                salt = ByteArray(16)
            )
        }
    }

    /**
     * The operation body that deploys the Stellar Asset Contract of [asset], built by hand: a
     * CREATE_CONTRACT host function with the asset preimage, the Stellar asset executable and no
     * authorization entry.
     */
    private fun stellarAssetContractBody(asset: AssetXdr): OperationBodyXdr =
        OperationBodyXdr.InvokeHostFunctionOp(
            InvokeHostFunctionOpXdr(
                hostFunction = HostFunctionXdr.CreateContract(
                    CreateContractArgsXdr(
                        contractIdPreimage = ContractIDPreimageXdr.FromAsset(asset),
                        executable = ContractExecutableXdr.Void
                    )
                ),
                auth = emptyList()
            )
        )

    /** Asserts that [body] decodes through [InvokeHostFunctionOperation.fromXdr] and re-encodes to its bytes. */
    private fun assertInvokeHostFunctionBodyRoundTrips(body: OperationBodyXdr) {
        val restored = InvokeHostFunctionOperation.fromXdr(assertIs<OperationBodyXdr.InvokeHostFunctionOp>(body).value)
        assertContentEquals(encodedBody(body), encodedBody(restored.toOperationBody()))
    }

    @Test
    fun testInvokeHostFunctionCreateStellarAssetContractNative() {
        val body = InvokeHostFunctionOperation.createStellarAssetContract(ASSET_NATIVE).toOperationBody()

        assertContentEquals(encodedBody(stellarAssetContractBody(AssetXdr.Void)), encodedBody(body))
        assertInvokeHostFunctionBodyRoundTrips(body)
    }

    @Test
    fun testInvokeHostFunctionCreateStellarAssetContractIssuedAsset() {
        val usd = AssetXdr.AlphaNum4(
            AlphaNum4Xdr(
                AssetCode4Xdr(byteArrayOf(0x55, 0x53, 0x44, 0)),
                KeyPair.fromAccountId(ACCOUNT_A).getXdrAccountId()
            )
        )

        val body = InvokeHostFunctionOperation.createStellarAssetContract(ASSET_USD).toOperationBody()

        assertContentEquals(encodedBody(stellarAssetContractBody(usd)), encodedBody(body))
        assertInvokeHostFunctionBodyRoundTrips(body)
    }

    @Test
    fun testInvokeHostFunctionCreateStellarAssetContractPreimageDerivesTheContractId() = runTest {
        val vectors = listOf(
            Triple(AssetTypeNative, Network.TESTNET, "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"),
            Triple(
                AssetTypeCreditAlphaNum4("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"),
                Network.PUBLIC,
                "CCW67TSZV3SSS2HXMBQ5JFGCKJNXKZM7UQUWUZPUTHXSTZLEO7SJMI75"
            )
        )
        for ((asset, network, contractId) in vectors) {
            val hostFunction = InvokeHostFunctionOperation.createStellarAssetContract(asset).hostFunction
            val preimage = HashIDPreimageXdr.ContractID(
                HashIDPreimageContractIDXdr(
                    networkId = HashXdr(network.networkId()),
                    contractIdPreimage = assertIs<HostFunctionXdr.CreateContract>(hostFunction).value.contractIdPreimage
                )
            )
            val writer = XdrWriter()
            preimage.encode(writer)

            assertEquals(contractId, StrKey.encodeContract(Util.hash(writer.toByteArray())), "$asset")
            assertEquals(contractId, asset.getContractId(network), "$asset")
        }
    }

    // ========== RevokeSponsorship ==========
    @Test
    fun testRevokeSponsorshipAccountRoundtrip() {
        val op = RevokeSponsorshipOperation(Sponsorship.Account(ACCOUNT_B))
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.sponsorship is Sponsorship.Account)
        assertEquals(ACCOUNT_B, (restored.sponsorship as Sponsorship.Account).accountId)
    }

    @Test
    fun testRevokeSponsorshipTrustLineRoundtrip() {
        val op = RevokeSponsorshipOperation(Sponsorship.TrustLine(ACCOUNT_B, ASSET_USD))
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.sponsorship is Sponsorship.TrustLine)
    }

    @Test
    fun testRevokeSponsorshipTrustLineNativeRoundtrip() {
        val op = RevokeSponsorshipOperation(Sponsorship.TrustLine(ACCOUNT_B, ASSET_NATIVE))
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.sponsorship is Sponsorship.TrustLine)
    }

    @Test
    fun testRevokeSponsorshipTrustLineAlpha12() {
        val op = RevokeSponsorshipOperation(Sponsorship.TrustLine(ACCOUNT_B, ASSET_LONG))
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.sponsorship is Sponsorship.TrustLine)
    }

    @Test
    fun testRevokeSponsorshipOfferRoundtrip() {
        val op = RevokeSponsorshipOperation(Sponsorship.Offer(ACCOUNT_B, 12345))
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.sponsorship is Sponsorship.Offer)
        val offer = restored.sponsorship as Sponsorship.Offer
        assertEquals(ACCOUNT_B, offer.sellerId)
        assertEquals(12345L, offer.offerId)
    }

    @Test
    fun testRevokeSponsorshipDataRoundtrip() {
        val op = RevokeSponsorshipOperation(Sponsorship.Data(ACCOUNT_B, "test_data"))
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.sponsorship is Sponsorship.Data)
        val data = restored.sponsorship as Sponsorship.Data
        assertEquals(ACCOUNT_B, data.accountId)
        assertEquals("test_data", data.dataName)
    }

    @Test
    fun testRevokeSponsorshipClaimableBalanceRoundtrip() {
        val hash = "a".repeat(64)
        val op = RevokeSponsorshipOperation(Sponsorship.ClaimableBalance(hash))
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        val sponsorship = restored.sponsorship
        assertTrue(sponsorship is Sponsorship.ClaimableBalance)
        assertEquals("00000000" + hash, sponsorship.balanceId)
    }

    @Test
    fun testRevokeSponsorshipClaimableBalanceAcceptsEverySpelling() {
        val canonical =
            RevokeSponsorshipOperation(Sponsorship.ClaimableBalance(BALANCE_PADDED_HEX))
                .toOperationBody()
        for (spelling in BALANCE_SPELLINGS) {
            val op = RevokeSponsorshipOperation(Sponsorship.ClaimableBalance(spelling))
            assertTrue(
                encodedBody(op.toOperationBody()).contentEquals(encodedBody(canonical)),
                "spelling $spelling must reach the wire as the same bytes"
            )
            val restored = when (val xdr = op.toOperationBody()) {
                is OperationBodyXdr.RevokeSponsorshipOp ->
                    RevokeSponsorshipOperation.fromXdr(xdr.value)
                else -> fail("Wrong XDR type")
            }
            val sponsorship = restored.sponsorship
            assertTrue(sponsorship is Sponsorship.ClaimableBalance, "spelling: $spelling")
            assertEquals(
                BALANCE_PADDED_HEX, sponsorship.balanceId,
                "spelling $spelling must be reported in one form"
            )
        }
    }

    @Test
    fun testRevokeSponsorshipClaimableBalanceRejectsAnIdNamingNoBalance() {
        // The rejection belongs where the id is given: a ledger key built from an id naming no
        // balance would otherwise fail only once the transaction is built.
        for (candidate in BALANCE_REJECTIONS) {
            val resolver = assertFailsWith<IllegalArgumentException>(
                "\"$candidate\" must name no balance"
            ) { ClaimableBalanceId.forId(candidate) }
            val failure = assertFailsWith<IllegalArgumentException>("\"$candidate\" must be rejected") {
                Sponsorship.ClaimableBalance(candidate)
            }
            assertEquals(
                resolver.message, failure.message,
                "\"$candidate\": the sponsorship must reject as the resolver does"
            )
        }
    }

    @Test
    fun testRevokeSponsorshipClaimableBalanceCopyJudgesTheNewId() {
        val sponsorship = Sponsorship.ClaimableBalance(BALANCE_PADDED_HEX)
        for (candidate in BALANCE_REJECTIONS) {
            assertFailsWith<IllegalArgumentException>("copy to \"$candidate\" must be rejected") {
                sponsorship.copy(balanceId = candidate)
            }
        }
    }

    @Test
    fun testRevokeSponsorshipSignerRoundtrip() {
        val signerKey = SignerKey.Ed25519PublicKey(StrKey.decodeEd25519PublicKey(ACCOUNT_B))
        val op = RevokeSponsorshipOperation(Sponsorship.Signer(ACCOUNT_A, signerKey))
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertTrue(restored.sponsorship is Sponsorship.Signer)
        val s = restored.sponsorship as Sponsorship.Signer
        assertEquals(ACCOUNT_A, s.accountId)
    }

    @Test
    fun testRevokeSponsorshipSignerWithEmptyPayloadFromEnvelope() = runTest {
        // A V1 envelope built byte by byte without the SDK: the revoke of a signed payload signer with
        // an empty payload, which the network includes in a ledger as a failed operation.
        val envelope = "AAAAAgAAAABnwpCgA6yFD1L3PgoC73rYtcz6HDg4QUFGfU+kaegEXwAAAGQAAABAAAAAAwAAAAAAAAAAAAAAAQAAAAAAAAASAAAAAQAAAADuNWWO3rX2RzlCu6U+/qrXObnsLObpsei7i3Z2iQ+c1AAAAANXAxoVYDE0FDNm4SqQ2W6ItlzdWsbwBKIDbknK6TUTaQAAAAAAAAAAAAAAAA=="
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        val op = tx.operations.single() as RevokeSponsorshipOperation
        val signer = (op.sponsorship as Sponsorship.Signer).signerKey as SignerKey.Ed25519SignedPayload
        assertEquals(0, signer.payload.size)
        assertEquals("GBLQGGQVMAYTIFBTM3QSVEGZN2ELMXG5LLDPABFCANXETSXJGUJWT46I", signer.encodedEd25519PublicKey())
        assertTrue(op.toString().contains("empty payload"))
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals("8a872fd86630c1f88203dc5329b1535e6942ae269a7c0e71981da34c278218c7", tx.hashHex())
    }

    // ========== Operation.fromXdr full integration ==========
    @Test
    fun testOperationFromXdrEndSponsoring() {
        val op = EndSponsoringFutureReservesOperation()
        val xdr = op.toXdr()
        val restored = Operation.fromXdr(xdr)
        assertTrue(restored is EndSponsoringFutureReservesOperation)
    }

    @Test
    fun testOperationFromXdrInflation() {
        val op = InflationOperation()
        val xdr = op.toXdr()
        val restored = Operation.fromXdr(xdr)
        assertTrue(restored is InflationOperation)
    }

    // ========== ClaimPredicate ==========
    @Test
    fun testClaimPredicateUnconditional() {
        val p = ClaimPredicate.Unconditional
        val xdr = p.toXdr()
        val restored = ClaimPredicate.fromXdr(xdr)
        assertTrue(restored is ClaimPredicate.Unconditional)
    }

    @Test
    fun testClaimPredicateBeforeAbsoluteTime() {
        val p = ClaimPredicate.BeforeAbsoluteTime(1700000000L)
        val xdr = p.toXdr()
        val restored = ClaimPredicate.fromXdr(xdr)
        assertTrue(restored is ClaimPredicate.BeforeAbsoluteTime)
        assertEquals(1700000000L, (restored as ClaimPredicate.BeforeAbsoluteTime).timestamp)
    }

    @Test
    fun testClaimPredicateBeforeRelativeTime() {
        val p = ClaimPredicate.BeforeRelativeTime(3600L)
        val xdr = p.toXdr()
        val restored = ClaimPredicate.fromXdr(xdr)
        assertTrue(restored is ClaimPredicate.BeforeRelativeTime)
        assertEquals(3600L, (restored as ClaimPredicate.BeforeRelativeTime).seconds)
    }

    @Test
    fun testClaimPredicateNot() {
        val inner = ClaimPredicate.BeforeAbsoluteTime(1700000000L)
        val p = ClaimPredicate.Not(inner)
        val xdr = p.toXdr()
        val restored = ClaimPredicate.fromXdr(xdr)
        assertTrue(restored is ClaimPredicate.Not)
    }

    @Test
    fun testClaimPredicateNestedNotRoundtrip() {
        // stellar-core accepts a NOT predicate inside a NOT predicate.
        val p = ClaimPredicate.Not(ClaimPredicate.Not(ClaimPredicate.Unconditional))
        val writer = XdrWriter()
        p.toXdr().encode(writer)
        val bytes = writer.toByteArray()
        // NOT (3) with a present inner predicate (1), twice, then UNCONDITIONAL (0).
        assertEquals("0000000300000001000000030000000100000000", Util.bytesToHex(bytes))
        assertEquals(p, ClaimPredicate.fromXdr(ClaimPredicateXdr.decode(XdrReader(bytes))))
    }

    @Test
    fun testClaimPredicateNestedNotFromAppliedTransaction() = runTest {
        // A testnet transaction applied with txSUCCESS in ledger 5829126.
        val envelope = "AAAAAgAAAAAkns0kPl6G+DY63Bb7+24+SWf4l79qWyuv1/dZxJzwlAAAA+gAWPIAAAAAAgAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAADgAAAAAAAAAAAJiWgAAAAAEAAAAAAAAAAD3xC/lKAd+zFQkshKZmfBlmQZ8WRLUYtF/gLk/y58BAAAAAAwAAAAEAAAADAAAAAQAAAAAAAAAAAAAAAcSc8JQAAABAjysNG4nraKjECsvzWi2TreQi0Fru4UMeaoL6eJB0XPYyrRh8nG7kcUgon8bFdpS5UFlM4vuNTMqJEnsdvex7CQ=="
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        val op = tx.operations.single() as CreateClaimableBalanceOperation
        assertEquals(
            ClaimPredicate.Not(ClaimPredicate.Not(ClaimPredicate.Unconditional)),
            op.claimants.single().predicate
        )
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals("c2123aaec96c3b0215f8b9b770ab9b16164339affa2ee4ea2d7ba5fcf128ff67", tx.hashHex())
    }

    @Test
    fun testClaimPredicateAnd() {
        val left = ClaimPredicate.BeforeAbsoluteTime(1700000000L)
        val right = ClaimPredicate.BeforeRelativeTime(3600L)
        val p = ClaimPredicate.And(left, right)
        val xdr = p.toXdr()
        val restored = ClaimPredicate.fromXdr(xdr)
        assertTrue(restored is ClaimPredicate.And)
    }

    @Test
    fun testClaimPredicateOr() {
        val left = ClaimPredicate.Unconditional
        val right = ClaimPredicate.BeforeAbsoluteTime(1700000000L)
        val p = ClaimPredicate.Or(left, right)
        val xdr = p.toXdr()
        val restored = ClaimPredicate.fromXdr(xdr)
        assertTrue(restored is ClaimPredicate.Or)
    }

    // ========== Claimant validation ==========
    @Test
    fun testClaimantInvalidDestination() {
        assertFailsWith<IllegalArgumentException> {
            Claimant("INVALID", ClaimPredicate.Unconditional)
        }
    }

    @Test
    fun testClaimantValid() {
        val c = Claimant(ACCOUNT_B, ClaimPredicate.Unconditional)
        assertEquals(ACCOUNT_B, c.destination)
    }

    // ========== Operation.Companion.toXdrAmount error branches ==========

    @Test
    fun testAmountWithNonNumericWholePartThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "abc.5000000")
        }
        assertTrue(exception.message!!.contains("Invalid amount format"))
    }

    @Test
    fun testAmountWithTooManyDecimalPlacesThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "1.12345678")
        }
        assertTrue(exception.message!!.contains("more than 7 decimal places"))
    }

    @Test
    fun testAmountTrailingZerosBeyondSevenDecimalsAccepted() {
        val operation = PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "0.50000000")
        val body = operation.toOperationBody() as OperationBodyXdr.PaymentOp
        assertEquals(5_000_000L, body.value.amount.value)
    }

    @Test
    fun testAmountSevenSignificantDecimalsWithTrailingZerosAccepted() {
        val operation = PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "100.123456700")
        val body = operation.toOperationBody() as OperationBodyXdr.PaymentOp
        assertEquals(1_001_234_567L, body.value.amount.value)
    }

    @Test
    fun testAmountAllZeroFractionLongerThanSevenAccepted() {
        val operation = PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "1.0000000000")
        val body = operation.toOperationBody() as OperationBodyXdr.PaymentOp
        assertEquals(10_000_000L, body.value.amount.value)
    }

    @Test
    fun testAmountEightSignificantDecimalsStillThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "0.123456780")
        }
        assertTrue(exception.message!!.contains("more than 7 decimal places"))
        assertTrue(exception.message!!.contains("got 8"))
    }

    @Test
    fun testAmountWithSignInFractionThrows() {
        // A sign read by toLong inside the fraction would build 0.95 XLM from "1.-5"
        val exception = assertFailsWith<IllegalArgumentException> {
            PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "1.-5")
        }
        assertTrue(exception.message!!.contains("Invalid amount format"))
    }

    @Test
    fun testAmountWithSignInLongFractionThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "1.+50000000")
        }
        assertTrue(exception.message!!.contains("Invalid amount format"))
    }

    @Test
    fun testAmountWithNonNumericFractionThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "1.5a")
        }
        assertTrue(exception.message!!.contains("Invalid amount format"))
    }

    // ========== Operation.Companion.formatAmountScale ==========

    @Test
    fun testFormatAmountScaleBlankThrows() {
        assertFailsWith<IllegalArgumentException> {
            Operation.formatAmountScale("")
        }
    }

    @Test
    fun testFormatAmountScaleTooManyPartsThrows() {
        assertFailsWith<IllegalArgumentException> {
            Operation.formatAmountScale("1.2.3")
        }
    }

    @Test
    fun testFormatAmountScaleFractionTooLongThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Operation.formatAmountScale("1.12345678")
        }
        assertTrue(exception.message!!.contains("scale"))
    }

    @Test
    fun testFormatAmountScalePadsFraction() {
        assertEquals("1.5000000", Operation.formatAmountScale("1.5"))
    }

    @Test
    fun testFormatAmountScaleTrailingZerosBeyondSevenDecimalsAccepted() {
        assertEquals("1.5000000", Operation.formatAmountScale("1.50000000"))
    }

    @Test
    fun testFormatAmountScaleEightSignificantDecimalsStillThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Operation.formatAmountScale("1.123456780")
        }
        assertTrue(exception.message!!.contains("scale"))
        assertTrue(exception.message!!.contains("got 8"))
    }

    @Test
    fun testFormatAmountScaleNonNumericFractionThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Operation.formatAmountScale("1.-5")
        }
        assertTrue(exception.message!!.contains("Invalid amount format"))
    }

    @Test
    fun testFormatAmountScaleWholeNumberGetsZeroFraction() {
        assertEquals("42.0000000", Operation.formatAmountScale("42"))
    }

    // ========== Operation.fromXdr dispatch: Soroban void-free operations ==========

    @Test
    fun testOperationFromXdrDispatchesExtendFootprintTTL() {
        val op = ExtendFootprintTTLOperation(10000)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is ExtendFootprintTTLOperation)
        assertEquals(10000, (restored as ExtendFootprintTTLOperation).extendTo)
    }

    @Test
    fun testOperationFromXdrDispatchesRestoreFootprint() {
        val op = RestoreFootprintOperation()
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is RestoreFootprintOperation)
    }

    // ========== Operation.fromXdr: unknown void operation discriminant ==========

    @Test
    fun testOperationFromXdrUnknownVoidOperationTypeThrows() {
        // A well-formed Void body only occurs for INFLATION and END_SPONSORING_FUTURE_RESERVES;
        // construct one with a different discriminant to reach the defensive else branch.
        val bogusBody = OperationBodyXdr.Void(OperationTypeXdr.PAYMENT)
        val xdr = OperationXdr(sourceAccount = null, body = bogusBody)

        val exception = assertFailsWith<IllegalArgumentException> {
            Operation.fromXdr(xdr)
        }
        assertTrue(exception.message!!.contains("Unknown void operation type"))
    }

    // ========== Operation.fromXdr dispatch: remaining operation types ==========
    // Each of these round-trips through the top-level Operation.fromXdr(OperationXdr) dispatcher
    // (not the per-operation fromXdr(opBody)) to exercise the corresponding `is OperationBodyXdr.*`
    // arm of the when in Operation.Companion.fromXdr.

    @Test
    fun testOperationFromXdrDispatchesPathPaymentStrictReceive() {
        val op = PathPaymentStrictReceiveOperation(
            sendAsset = ASSET_NATIVE,
            sendMax = "10.0000000",
            destination = ACCOUNT_B,
            destAsset = ASSET_USD,
            destAmount = "5.0000000"
        )
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is PathPaymentStrictReceiveOperation)
        assertEquals(ACCOUNT_B, restored.destination)
    }

    @Test
    fun testOperationFromXdrDispatchesPathPaymentStrictSend() {
        val op = PathPaymentStrictSendOperation(
            sendAsset = ASSET_USD,
            sendAmount = "10.0000000",
            destination = ACCOUNT_B,
            destAsset = ASSET_NATIVE,
            destMin = "5.0000000"
        )
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is PathPaymentStrictSendOperation)
        assertEquals(ACCOUNT_B, restored.destination)
    }

    @Test
    fun testOperationFromXdrDispatchesManageSellOffer() {
        val op = ManageSellOfferOperation(ASSET_USD, ASSET_NATIVE, "1.0000000", Price(1, 1))
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is ManageSellOfferOperation)
        assertEquals(ASSET_USD, restored.selling)
    }

    @Test
    fun testOperationFromXdrDispatchesCreatePassiveSellOffer() {
        val op = CreatePassiveSellOfferOperation(ASSET_USD, ASSET_NATIVE, "1.0000000", Price(1, 1))
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is CreatePassiveSellOfferOperation)
        assertEquals(ASSET_USD, restored.selling)
    }

    @Test
    fun testOperationFromXdrDispatchesSetOptions() {
        val op = SetOptionsOperation(masterKeyWeight = 5)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is SetOptionsOperation)
        assertEquals(5, restored.masterKeyWeight)
    }

    @Test
    fun testOperationFromXdrDispatchesChangeTrust() {
        val op = ChangeTrustOperation(ASSET_USD, "500.0000000")
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is ChangeTrustOperation)
        assertEquals(ASSET_USD, restored.asset)
    }

    @Test
    fun testOperationFromXdrDispatchesAllowTrust() {
        val op = AllowTrustOperation(ACCOUNT_B, "USD", 1)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is AllowTrustOperation)
        assertEquals("USD", restored.assetCode)
    }

    @Test
    fun testOperationFromXdrDispatchesAccountMerge() {
        val op = AccountMergeOperation(ACCOUNT_B)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is AccountMergeOperation)
        assertEquals(ACCOUNT_B, restored.destination)
    }

    @Test
    fun testOperationFromXdrDispatchesBumpSequence() {
        val op = BumpSequenceOperation(5000L)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is BumpSequenceOperation)
        assertEquals(5000L, restored.bumpTo)
    }

    @Test
    fun testOperationFromXdrDispatchesManageBuyOffer() {
        val op = ManageBuyOfferOperation(ASSET_NATIVE, ASSET_USD, "1.0000000", Price(1, 1))
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is ManageBuyOfferOperation)
        assertEquals(ASSET_USD, restored.buying)
    }

    @Test
    fun testOperationFromXdrDispatchesCreateClaimableBalance() {
        val op = CreateClaimableBalanceOperation(
            ASSET_NATIVE,
            "10.0000000",
            listOf(Claimant(ACCOUNT_B, ClaimPredicate.Unconditional))
        )
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is CreateClaimableBalanceOperation)
        assertEquals(1, restored.claimants.size)
    }

    @Test
    fun testOperationFromXdrDispatchesClaimClaimableBalance() {
        val balanceId = "00000000" + "c".repeat(64)
        val op = ClaimClaimableBalanceOperation(balanceId)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is ClaimClaimableBalanceOperation)
        assertEquals(balanceId, restored.balanceId)
    }

    @Test
    fun testOperationFromXdrDispatchesBeginSponsoringFutureReserves() {
        val op = BeginSponsoringFutureReservesOperation(ACCOUNT_B)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is BeginSponsoringFutureReservesOperation)
        assertEquals(ACCOUNT_B, restored.sponsoredId)
    }

    @Test
    fun testOperationFromXdrDispatchesRevokeSponsorship() {
        val op = RevokeSponsorshipOperation(Sponsorship.Account(ACCOUNT_B))
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is RevokeSponsorshipOperation)
        assertTrue(restored.sponsorship is Sponsorship.Account)
    }

    @Test
    fun testOperationFromXdrDispatchesClawback() {
        val op = ClawbackOperation(ACCOUNT_B, ASSET_USD, "10.0000000")
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is ClawbackOperation)
        assertEquals(ACCOUNT_B, restored.from)
    }

    @Test
    fun testOperationFromXdrDispatchesClawbackClaimableBalance() {
        val balanceId = "00000000" + "d".repeat(64)
        val op = ClawbackClaimableBalanceOperation(balanceId)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is ClawbackClaimableBalanceOperation)
        assertEquals(balanceId, restored.balanceId)
    }

    @Test
    fun testOperationFromXdrDispatchesSetTrustLineFlags() {
        val op = SetTrustLineFlagsOperation(ACCOUNT_B, ASSET_USD, setFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG)
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is SetTrustLineFlagsOperation)
        assertEquals(ACCOUNT_B, restored.trustor)
    }

    @Test
    fun testOperationFromXdrDispatchesLiquidityPoolDeposit() {
        val op = LiquidityPoolDepositOperation(
            liquidityPoolId = "e".repeat(64),
            maxAmountA = "10.0000000",
            maxAmountB = "20.0000000",
            minPrice = Price(1, 2),
            maxPrice = Price(2, 1)
        )
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is LiquidityPoolDepositOperation)
        assertEquals("e".repeat(64), restored.liquidityPoolId)
    }

    @Test
    fun testOperationFromXdrDispatchesLiquidityPoolWithdraw() {
        val op = LiquidityPoolWithdrawOperation(
            liquidityPoolId = "f".repeat(64),
            amount = "5.0000000",
            minAmountA = "1.0000000",
            minAmountB = "2.0000000"
        )
        val restored = Operation.fromXdr(op.toXdr())
        assertTrue(restored is LiquidityPoolWithdrawOperation)
        assertEquals("f".repeat(64), restored.liquidityPoolId)
    }

    // ========== Operation.Companion.toXdrAmount: blank, multi-dot and empty whole part ==========

    @Test
    fun testAmountBlankThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "")
        }
        assertTrue(exception.message!!.contains("cannot be blank"))
    }

    @Test
    fun testAmountWithTooManyDotsThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PaymentOperation(ACCOUNT_B, ASSET_NATIVE, "1.2.3")
        }
        assertTrue(exception.message!!.contains("Invalid amount format"))
    }

    @Test
    fun testAmountWithEmptyWholePartParsesAsZero() {
        val op = PaymentOperation(ACCOUNT_B, ASSET_NATIVE, ".5000000")
        val xdr = op.toOperationBody()
        val restored = when (xdr) {
            is OperationBodyXdr.PaymentOp -> PaymentOperation.fromXdr(xdr.value)
            else -> fail("Wrong XDR type")
        }
        assertEquals("0.5000000", restored.amount)
    }

    // ========== Destination validation: muxed addresses and invalid strings ==========

    @Test
    fun testPaymentDestinationAcceptsMuxedAddress() {
        val op = PaymentOperation(MUXED_B, ASSET_NATIVE, "1.0000000")
        assertEquals(MUXED_B, op.destination)
    }

    @Test
    fun testPaymentInvalidDestinationThrows() {
        assertFailsWith<IllegalArgumentException> {
            PaymentOperation("NOT-AN-ADDRESS", ASSET_NATIVE, "1.0000000")
        }
    }

    @Test
    fun testPathPaymentStrictReceiveDestinationAcceptsMuxedAddress() {
        val op = PathPaymentStrictReceiveOperation(
            sendAsset = ASSET_NATIVE,
            sendMax = "10.0000000",
            destination = MUXED_B,
            destAsset = ASSET_USD,
            destAmount = "5.0000000"
        )
        assertEquals(MUXED_B, op.destination)
    }

    @Test
    fun testPathPaymentStrictReceiveInvalidDestinationThrows() {
        assertFailsWith<IllegalArgumentException> {
            PathPaymentStrictReceiveOperation(
                sendAsset = ASSET_NATIVE,
                sendMax = "10.0000000",
                destination = "NOT-AN-ADDRESS",
                destAsset = ASSET_USD,
                destAmount = "5.0000000"
            )
        }
    }

    @Test
    fun testPathPaymentStrictSendDestinationAcceptsMuxedAddress() {
        val op = PathPaymentStrictSendOperation(
            sendAsset = ASSET_USD,
            sendAmount = "10.0000000",
            destination = MUXED_B,
            destAsset = ASSET_NATIVE,
            destMin = "5.0000000"
        )
        assertEquals(MUXED_B, op.destination)
    }

    @Test
    fun testPathPaymentStrictSendInvalidDestinationThrows() {
        assertFailsWith<IllegalArgumentException> {
            PathPaymentStrictSendOperation(
                sendAsset = ASSET_USD,
                sendAmount = "10.0000000",
                destination = "NOT-AN-ADDRESS",
                destAsset = ASSET_NATIVE,
                destMin = "5.0000000"
            )
        }
    }

    @Test
    fun testPathPaymentStrictSendTooManyPathsThrows() {
        assertFailsWith<IllegalArgumentException> {
            PathPaymentStrictSendOperation(
                sendAsset = ASSET_USD,
                sendAmount = "10.0000000",
                destination = ACCOUNT_B,
                destAsset = ASSET_NATIVE,
                destMin = "5.0000000",
                path = listOf(ASSET_USD, ASSET_EUR, ASSET_NATIVE, ASSET_LONG, ASSET_USD, ASSET_EUR)
            )
        }
    }

    // ========== Offer validations: same asset / negative offer id ==========

    @Test
    fun testManageSellOfferSameAssetThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ManageSellOfferOperation(ASSET_USD, ASSET_USD, "1.0000000", Price(1, 1))
        }
        assertTrue(exception.message!!.contains("must be different"))
    }

    @Test
    fun testManageSellOfferNegativeOfferIdRoundtrip() {
        // offerID is int64; the network refuses a negative id as malformed from protocol 15 on.
        for (offerId in listOf(-1L, Long.MIN_VALUE)) {
            val op = ManageSellOfferOperation(ASSET_USD, ASSET_NATIVE, "1.0000000", Price(1, 1), offerId = offerId)
            val xdr = op.toOperationBody() as OperationBodyXdr.ManageSellOfferOp
            assertEquals(offerId, xdr.value.offerId.value)
            assertEquals(op, ManageSellOfferOperation.fromXdr(xdr.value))
        }
    }

    @Test
    fun testManageSellOfferNegativeOfferIdFromV0Envelope() = runTest {
        // A V0 envelope with offer id -1; the hash was computed from the envelope bytes without the SDK.
        val envelope = "AAAAAGfCkKADrIUPUvc+CgLveti1zPocODhBQUZ9T6Rp6ARfAAAAZAAAAEAAAAABAAAAAAAAAAAAAAABAAAAAAAAAAMAAAACTE9OR0FTU0VUMTIAAAAAAGfCkKADrIUPUvc+CgLveti1zPocODhBQUZ9T6Rp6ARfAAAAAAAAAOjUpRAAAAAAAQAAAAH//////////wAAAAAAAAABaegEXwAAAEAJhNSpNHRocZ2Ukgm5MOvYB9uPDfE3ScHZw7qLEfwSQCrLH5cnecYdP938XXAjWbjmSGRJ9k99M9yY7nMKJGoM"
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        assertEquals(-1L, (tx.operations.single() as ManageSellOfferOperation).offerId)
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals("b6753b14c03b8bbbbd6fad75bfd26b5a97169fcf7b04ba596dc2205560b7cf7f", tx.hashHex())
    }

    @Test
    fun testManageBuyOfferSameAssetThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ManageBuyOfferOperation(ASSET_USD, ASSET_USD, "1.0000000", Price(1, 1))
        }
        assertTrue(exception.message!!.contains("must be different"))
    }

    @Test
    fun testManageBuyOfferNegativeOfferIdRoundtrip() {
        for (offerId in listOf(-1L, Long.MIN_VALUE)) {
            val op = ManageBuyOfferOperation(ASSET_USD, ASSET_NATIVE, "1.0000000", Price(1, 1), offerId = offerId)
            val xdr = op.toOperationBody() as OperationBodyXdr.ManageBuyOfferOp
            assertEquals(offerId, xdr.value.offerId.value)
            assertEquals(op, ManageBuyOfferOperation.fromXdr(xdr.value))
        }
    }

    @Test
    fun testCreatePassiveSellOfferSameAssetThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CreatePassiveSellOfferOperation(ASSET_USD, ASSET_USD, "1.0000000", Price(1, 1))
        }
        assertTrue(exception.message!!.contains("must be different"))
    }

    // ========== Balance ID hex / length validation ==========

    @Test
    fun testClaimClaimableBalanceInvalidHexThrows() {
        val badHex = "z".repeat(72)
        val exception = assertFailsWith<IllegalArgumentException> {
            ClaimClaimableBalanceOperation(badHex)
        }
        assertEquals("claimable balance id \"$badHex\" is not hexadecimal", exception.message)
    }

    @Test
    fun testClawbackClaimableBalanceInvalidHexThrows() {
        val badHex = "z".repeat(72)
        val exception = assertFailsWith<IllegalArgumentException> {
            ClawbackClaimableBalanceOperation(badHex)
        }
        assertEquals("claimable balance id \"$badHex\" is not hexadecimal", exception.message)
    }

    // ========== Clawback source (from) address validation ==========

    @Test
    fun testClawbackFromAcceptsMuxedAddress() {
        val op = ClawbackOperation(MUXED_B, ASSET_USD, "1.0000000")
        assertEquals(MUXED_B, op.from)
    }

    @Test
    fun testClawbackInvalidFromThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ClawbackOperation("NOT-AN-ADDRESS", ASSET_USD, "1.0000000")
        }
        assertTrue(exception.message!!.contains("Invalid from account"))
    }

    // ========== equals() for parameterless void operations ==========

    @Test
    fun testInflationEqualsReflexiveNullAndDifferentType() {
        val op = InflationOperation()
        assertEquals(op, op)
        assertFalse(op.equals(null))
        assertFalse(op.equals("not an operation"))
        assertEquals(op, InflationOperation())
    }

    @Test
    fun testEndSponsoringEqualsReflexiveNullAndDifferentType() {
        val op = EndSponsoringFutureReservesOperation()
        assertEquals(op, op)
        assertFalse(op.equals(null))
        assertFalse(op.equals("not an operation"))
        assertEquals(op, EndSponsoringFutureReservesOperation())
    }

    @Test
    fun testRestoreFootprintEqualsReflexiveNullAndDifferentType() {
        val op = RestoreFootprintOperation()
        assertEquals(op, op)
        assertFalse(op.equals(null))
        assertFalse(op.equals("not an operation"))
        assertEquals(op, RestoreFootprintOperation())
    }

    // ========== SetOptions: opposite range boundary for each threshold field ==========

    @Test
    fun testSetOptionsValidationOppositeBoundary() {
        assertFailsWith<IllegalArgumentException> { SetOptionsOperation(masterKeyWeight = -1) }
        assertFailsWith<IllegalArgumentException> { SetOptionsOperation(lowThreshold = 256) }
        assertFailsWith<IllegalArgumentException> { SetOptionsOperation(mediumThreshold = -1) }
        assertFailsWith<IllegalArgumentException> { SetOptionsOperation(highThreshold = 256) }
        assertFailsWith<IllegalArgumentException> { SetOptionsOperation(signerWeight = -1) }
    }

    // ========== ManageData: equals() edge cases ==========

    @Test
    fun testManageDataEqualsSameInstance() {
        val op = ManageDataOperation("key", "value".encodeToByteArray())
        assertEquals(op, op)
    }

    @Test
    fun testManageDataEqualsNullAndDifferentType() {
        val op = ManageDataOperation("key", "value".encodeToByteArray())
        assertFalse(op.equals(null))
        assertFalse(op.equals("not an operation"))
    }

    @Test
    fun testManageDataEqualsDifferentValueContent() {
        val op1 = ManageDataOperation("key", byteArrayOf(1, 2, 3))
        val op2 = ManageDataOperation("key", byteArrayOf(4, 5, 6))
        assertNotEquals(op1, op2)
    }

    @Test
    fun testManageDataEqualsBothValuesNull() {
        val op1 = ManageDataOperation("key", null)
        val op2 = ManageDataOperation("key", null)
        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
    }

    // ========== RevokeSponsorship: decode-only edge cases (manual XDR) ==========

    @Test
    fun testRevokeSponsorshipPoolShareTrustLineFromAppliedTransaction() = runTest {
        // A testnet transaction applied with txSUCCESS in ledger 5829133.
        val envelope = "AAAAAgAAAAAkns0kPl6G+DY63Bb7+24+SWf4l79qWyuv1/dZxJzwlAAAA+gAWPIAAAAABQAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAEgAAAAAAAAABAAAAAD3xC/lKAd+zFQkshKZmfBlmQZ8WRLUYtF/gLk/y58BAAAAAA3S4WpwYAswLOIKFirkHtGeLwFIJdoCIuLxxP0FEL/CQAAAAAAAAAAHEnPCUAAAAQC86K+u8RD2h+a17q4IrBTQNBlUEISLJgOUHSAhHh3rrA4yAIJndPBpkQajyNfsi61zyHZWFkCRs7n3H4ayf9gI="
        val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction
        val trustLine = (tx.operations.single() as RevokeSponsorshipOperation).sponsorship as Sponsorship.TrustLine
        assertEquals("GA67CC7ZJIA57MYVBEWIJJTGPQMWMQM7CZCLKGFUL7QC4T7S47AEBQL3", trustLine.accountId)
        assertEquals(POOL_ID, trustLine.liquidityPoolId)
        assertEquals(envelope, tx.toEnvelopeXdrBase64())
        assertEquals("25e65885d13542f523597f9f46844cd80eb1fd9b87fc6b1a3c8811487b625053", tx.hashHex())
    }

    @Test
    fun testRevokeSponsorshipPoolShareTrustLineUppercaseId() {
        val upper = Sponsorship.TrustLine.forLiquidityPool(ACCOUNT_B, POOL_ID.uppercase())
        val body = RevokeSponsorshipOperation(upper).toOperationBody() as OperationBodyXdr.RevokeSponsorshipOp
        val decoded = RevokeSponsorshipOperation.fromXdr(body.value).sponsorship
        assertEquals(POOL_ID, upper.liquidityPoolId)
        for (same in listOf(Sponsorship.TrustLine.forLiquidityPool(ACCOUNT_B, POOL_ID), decoded)) {
            assertEquals(same to same.hashCode(), upper to upper.hashCode())
        }
    }

    @Test
    fun testRevokeSponsorshipPoolShareTrustLine() {
        val trustLine = Sponsorship.TrustLine.forLiquidityPool(ACCOUNT_B, POOL_ID)
        val body = RevokeSponsorshipOperation(trustLine).toOperationBody() as OperationBodyXdr.RevokeSponsorshipOp
        val key = ((body.value as RevokeSponsorshipOpXdr.LedgerKey).value as LedgerKeyXdr.TrustLine).value
        assertEquals(POOL_ID, Util.bytesToHex((key.asset as TrustLineAssetXdr.LiquidityPoolID).value.value.value))
        assertEquals(trustLine, RevokeSponsorshipOperation.fromXdr(body.value).sponsorship)
        assertEquals(Sponsorship.TrustLine.forLiquidityPool(ACCOUNT_B, POOL_ID).hashCode(), trustLine.hashCode())
        val assetLine = Sponsorship.TrustLine(ACCOUNT_B, ASSET_USD)
        assertNotEquals(assetLine, trustLine)
        assertNotEquals(Sponsorship.TrustLine.forLiquidityPool(ACCOUNT_A, POOL_ID), trustLine)
        assertNotEquals(Sponsorship.TrustLine.forLiquidityPool(ACCOUNT_B, POOL_ID.reversed()), trustLine)
        assertNotEquals<Sponsorship>(trustLine, Sponsorship.Account(ACCOUNT_B))
        assertEquals(Sponsorship.TrustLine(ACCOUNT_B, ASSET_USD).hashCode(), assetLine.hashCode())
        val (accountId, asset) = assetLine
        assertEquals(ACCOUNT_B to ASSET_USD, accountId to asset)
        assertEquals(ACCOUNT_B, trustLine.component1())
        assertFailsWith<UnsupportedOperationException> { trustLine.component2() }
        assertNull(assetLine.liquidityPoolId)
        assertEquals("TrustLine(accountId=$ACCOUNT_B, asset=$ASSET_USD)", assetLine.toString())
        assertEquals("TrustLine(accountId=$ACCOUNT_B, liquidityPoolId=$POOL_ID)", trustLine.toString())
        assertFailsWith<UnsupportedOperationException> { trustLine.asset }
        assertFailsWith<UnsupportedOperationException> { trustLine.copy() }
        assertFailsWith<IllegalArgumentException> { Sponsorship.TrustLine.forLiquidityPool(ACCOUNT_B, POOL_ID.drop(2)) }
        assertFailsWith<IllegalArgumentException> {
            Sponsorship.TrustLine.forLiquidityPool(ACCOUNT_B, "zz" + POOL_ID.drop(2))
        }
    }

    @Test
    fun testRevokeSponsorshipUnknownLedgerKeyTypeThrows() {
        val ledgerKey = RevokeSponsorshipOpXdr.LedgerKey(
            LedgerKeyXdr.Ttl(LedgerKeyTtlXdr(HashXdr(ByteArray(32))))
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            RevokeSponsorshipOperation.fromXdr(ledgerKey)
        }
        assertTrue(exception.message!!.contains("Unknown ledger key type"))
    }

    // ========== LiquidityPoolWithdraw: pool ID length validation ==========

    @Test
    fun testLiquidityPoolWithdrawInvalidPoolIdThrows() {
        assertFailsWith<IllegalArgumentException> {
            LiquidityPoolWithdrawOperation("short", "1.0", "1.0", "1.0")
        }
    }

    // ========== InvokeHostFunction.createContract: salt and constructorArgs branches ==========

    @Test
    fun testInvokeHostFunctionCreateContractGeneratesRandomSaltWhenNull() {
        val op1 = InvokeHostFunctionOperation.createContract(
            wasmId = "a".repeat(64),
            address = Address(ACCOUNT_A)
        )
        val op2 = InvokeHostFunctionOperation.createContract(
            wasmId = "a".repeat(64),
            address = Address(ACCOUNT_A)
        )
        val preimage1 = (op1.hostFunction as HostFunctionXdr.CreateContract).value.contractIdPreimage
        val preimage2 = (op2.hostFunction as HostFunctionXdr.CreateContract).value.contractIdPreimage
        assertTrue(preimage1 is ContractIDPreimageXdr.FromAddress)
        assertTrue(preimage2 is ContractIDPreimageXdr.FromAddress)
        val salt1 = (preimage1 as ContractIDPreimageXdr.FromAddress).value.salt.value
        val salt2 = (preimage2 as ContractIDPreimageXdr.FromAddress).value.salt.value
        assertEquals(32, salt1.size)
        assertEquals(32, salt2.size)
        assertFalse(salt1.contentEquals(salt2))
    }

    @Test
    fun testInvokeHostFunctionCreateContractDefaultSaltVariesInEveryByte() {
        val samples = (1..16).map { defaultCreateContractSalt() }
        for (index in 0 until 32) {
            val values = samples.map { it[index] }.toSet()
            assertTrue(values.size > 1, "Salt byte $index was identical across all samples")
        }
    }

    @Test
    fun testInvokeHostFunctionCreateContractEmptyConstructorArgsUsesV1() {
        val op = InvokeHostFunctionOperation.createContract(
            wasmId = "a".repeat(64),
            address = Address(ACCOUNT_A),
            constructorArgs = emptyList(),
            salt = ByteArray(32) { 0 }
        )
        assertTrue(op.hostFunction is HostFunctionXdr.CreateContract)
    }

    private fun defaultCreateContractSalt(): ByteArray {
        val op = InvokeHostFunctionOperation.createContract(
            wasmId = "a".repeat(64),
            address = Address(ACCOUNT_A)
        )
        val preimage = (op.hostFunction as HostFunctionXdr.CreateContract).value.contractIdPreimage
        return (preimage as ContractIDPreimageXdr.FromAddress).value.salt.value
    }

    // ========== ClaimPredicate: decode-only edge cases (manual XDR) ==========

    @Test
    fun testClaimPredicateNotWithNullValueThrows() {
        val xdr = ClaimPredicateXdr.NotPredicate(null)
        val exception = assertFailsWith<IllegalArgumentException> {
            ClaimPredicate.fromXdr(xdr)
        }
        assertTrue(exception.message!!.contains("NOT predicate cannot have null value"))
    }

    @Test
    fun testClaimPredicateAndWrongSizeThrows() {
        val single = ClaimPredicateXdr.AndPredicates(listOf(ClaimPredicateXdr.Void))
        assertFailsWith<IllegalArgumentException> {
            ClaimPredicate.fromXdr(single)
        }

        val triple = ClaimPredicateXdr.AndPredicates(
            listOf(ClaimPredicateXdr.Void, ClaimPredicateXdr.Void, ClaimPredicateXdr.Void)
        )
        assertFailsWith<IllegalArgumentException> {
            ClaimPredicate.fromXdr(triple)
        }
    }

    @Test
    fun testClaimPredicateOrWrongSizeThrows() {
        val single = ClaimPredicateXdr.OrPredicates(listOf(ClaimPredicateXdr.Void))
        assertFailsWith<IllegalArgumentException> {
            ClaimPredicate.fromXdr(single)
        }
    }
}
