package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
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
            ManageDataOperation("  ", ByteArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            ManageDataOperation("a".repeat(65), ByteArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            ManageDataOperation("key", ByteArray(65))
        }
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
            ExtendFootprintTTLOperation(0)
        }
        assertFailsWith<IllegalArgumentException> {
            ExtendFootprintTTLOperation(-5)
        }
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
    fun testClaimClaimableBalanceInvalidLength() {
        assertFailsWith<IllegalArgumentException> {
            ClaimClaimableBalanceOperation("abc")
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
        assertTrue(restored.sponsorship is Sponsorship.ClaimableBalance)
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
    fun testClaimPredicateNotCannotNest() {
        val inner = ClaimPredicate.BeforeAbsoluteTime(1700000000L)
        val notPred = ClaimPredicate.Not(inner)
        assertFailsWith<IllegalArgumentException> {
            ClaimPredicate.Not(notPred)
        }
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
}
