package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assertXdrRoundTrip
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.accountId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.accountIdAlt
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetNative
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetAlphaNum4
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hashXdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hashXdrAlt
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.muxedAccountEd25519
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.muxedAccountMed25519
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.poolId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.sequenceNumber
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint256Xdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint256XdrAlt
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hash32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.price
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.contractId
import kotlin.test.Test

class XdrTransactionEnvelopeTest {

    // ========== Helper builders ==========

    private fun simplePaymentOp() = OperationBodyXdr.PaymentOp(
        PaymentOpXdr(muxedAccountEd25519(), assetNative(), int64(1000L))
    )

    private fun operationXdr(withSource: Boolean = false) = OperationXdr(
        sourceAccount = if (withSource) muxedAccountEd25519() else null,
        body = simplePaymentOp()
    )

    private fun timeBounds() = TimeBoundsXdr(
        minTime = TimePointXdr(uint64(0UL)),
        maxTime = TimePointXdr(uint64(1000UL))
    )

    private fun ledgerBounds() = LedgerBoundsXdr(
        minLedger = uint32(100u),
        maxLedger = uint32(200u)
    )

    private fun preconditionsNone(): PreconditionsXdr = PreconditionsXdr.Void

    private fun preconditionsTime(): PreconditionsXdr = PreconditionsXdr.TimeBounds(timeBounds())

    private fun preconditionsV2(): PreconditionsXdr = PreconditionsXdr.V2(
        PreconditionsV2Xdr(
            timeBounds = timeBounds(),
            ledgerBounds = ledgerBounds(),
            minSeqNum = sequenceNumber(100L),
            minSeqAge = DurationXdr(uint64(60UL)),
            minSeqLedgerGap = uint32(10u),
            extraSigners = listOf(
                SignerKeyXdr.Ed25519(uint256Xdr())
            )
        )
    )

    private fun decoratedSignature() = DecoratedSignatureXdr(
        hint = SignatureHintXdr(byteArrayOf(1, 2, 3, 4)),
        signature = SignatureXdr(ByteArray(64) { it.toByte() })
    )

    private fun transactionXdr(
        cond: PreconditionsXdr = preconditionsNone(),
        memo: MemoXdr = MemoXdr.Void,
        ext: TransactionExtXdr = TransactionExtXdr.Void
    ) = TransactionXdr(
        sourceAccount = muxedAccountEd25519(),
        fee = uint32(100u),
        seqNum = sequenceNumber(12345L),
        cond = cond,
        memo = memo,
        operations = listOf(operationXdr()),
        ext = ext
    )

    private fun transactionV0Xdr() = TransactionV0Xdr(
        sourceAccountEd25519 = uint256Xdr(),
        fee = uint32(200u),
        seqNum = sequenceNumber(100L),
        timeBounds = timeBounds(),
        memo = MemoXdr.Void,
        operations = listOf(operationXdr()),
        ext = TransactionV0ExtXdr.Void
    )

    // ========== Memo tests ==========

    @Test
    fun testMemoNone() {
        val memo: MemoXdr = MemoXdr.Void
        assertXdrRoundTrip(memo, { v, w -> v.encode(w) }, { r -> MemoXdr.decode(r) })
    }

    @Test
    fun testMemoText() {
        val memo = MemoXdr.Text("hello world")
        assertXdrRoundTrip(memo, { v, w -> v.encode(w) }, { r -> MemoXdr.decode(r) })
    }

    @Test
    fun testMemoId() {
        val memo = MemoXdr.Id(uint64(999UL))
        assertXdrRoundTrip(memo, { v, w -> v.encode(w) }, { r -> MemoXdr.decode(r) })
    }

    @Test
    fun testMemoHash() {
        val memo = MemoXdr.Hash(hashXdr())
        assertXdrRoundTrip(memo, { v, w -> v.encode(w) }, { r -> MemoXdr.decode(r) })
    }

    @Test
    fun testMemoReturn() {
        val memo = MemoXdr.RetHash(hashXdr())
        assertXdrRoundTrip(memo, { v, w -> v.encode(w) }, { r -> MemoXdr.decode(r) })
    }

    // ========== TimeBounds / LedgerBounds ==========

    @Test
    fun testTimeBounds() {
        val tb = timeBounds()
        assertXdrRoundTrip(tb, { v, w -> v.encode(w) }, { r -> TimeBoundsXdr.decode(r) })
    }

    @Test
    fun testLedgerBounds() {
        val lb = ledgerBounds()
        assertXdrRoundTrip(lb, { v, w -> v.encode(w) }, { r -> LedgerBoundsXdr.decode(r) })
    }

    // ========== Preconditions ==========

    @Test
    fun testPreconditionsNone() {
        assertXdrRoundTrip(preconditionsNone(), { v, w -> v.encode(w) }, { r -> PreconditionsXdr.decode(r) })
    }

    @Test
    fun testPreconditionsTime() {
        assertXdrRoundTrip(preconditionsTime(), { v, w -> v.encode(w) }, { r -> PreconditionsXdr.decode(r) })
    }

    @Test
    fun testPreconditionsV2() {
        assertXdrRoundTrip(preconditionsV2(), { v, w -> v.encode(w) }, { r -> PreconditionsXdr.decode(r) })
    }

    @Test
    fun testPreconditionsV2NullOptionals() {
        val v2 = PreconditionsXdr.V2(
            PreconditionsV2Xdr(
                timeBounds = null,
                ledgerBounds = null,
                minSeqNum = null,
                minSeqAge = DurationXdr(uint64(0UL)),
                minSeqLedgerGap = uint32(0u),
                extraSigners = emptyList()
            )
        )
        assertXdrRoundTrip(v2, { v, w -> v.encode(w) }, { r -> PreconditionsXdr.decode(r) })
    }

    // ========== DecoratedSignature / SignatureHint ==========

    @Test
    fun testDecoratedSignature() {
        val sig = decoratedSignature()
        assertXdrRoundTrip(sig, { v, w -> v.encode(w) }, { r -> DecoratedSignatureXdr.decode(r) })
    }

    @Test
    fun testSignatureHint() {
        val hint = SignatureHintXdr(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()))
        assertXdrRoundTrip(hint, { v, w -> v.encode(w) }, { r -> SignatureHintXdr.decode(r) })
    }

    // ========== Operation ==========

    @Test
    fun testOperationWithoutSource() {
        val op = operationXdr(withSource = false)
        assertXdrRoundTrip(op, { v, w -> v.encode(w) }, { r -> OperationXdr.decode(r) })
    }

    @Test
    fun testOperationWithSource() {
        val op = operationXdr(withSource = true)
        assertXdrRoundTrip(op, { v, w -> v.encode(w) }, { r -> OperationXdr.decode(r) })
    }

    // ========== TransactionXdr ==========

    @Test
    fun testTransactionBasic() {
        val tx = transactionXdr()
        assertXdrRoundTrip(tx, { v, w -> v.encode(w) }, { r -> TransactionXdr.decode(r) })
    }

    @Test
    fun testTransactionWithMemo() {
        val tx = transactionXdr(memo = MemoXdr.Text("test"))
        assertXdrRoundTrip(tx, { v, w -> v.encode(w) }, { r -> TransactionXdr.decode(r) })
    }

    @Test
    fun testTransactionExtVoid() {
        val ext = TransactionExtXdr.Void
        assertXdrRoundTrip(ext, { v, w -> v.encode(w) }, { r -> TransactionExtXdr.decode(r) })
    }

    // ========== TransactionV0Xdr ==========

    @Test
    fun testTransactionV0() {
        val tx = transactionV0Xdr()
        assertXdrRoundTrip(tx, { v, w -> v.encode(w) }, { r -> TransactionV0Xdr.decode(r) })
    }

    @Test
    fun testTransactionV0NoTimeBounds() {
        val tx = TransactionV0Xdr(
            sourceAccountEd25519 = uint256Xdr(),
            fee = uint32(100u),
            seqNum = sequenceNumber(1L),
            timeBounds = null,
            memo = MemoXdr.Void,
            operations = listOf(operationXdr()),
            ext = TransactionV0ExtXdr.Void
        )
        assertXdrRoundTrip(tx, { v, w -> v.encode(w) }, { r -> TransactionV0Xdr.decode(r) })
    }

    @Test
    fun testTransactionV0ExtVoid() {
        val ext = TransactionV0ExtXdr.Void
        assertXdrRoundTrip(ext, { v, w -> v.encode(w) }, { r -> TransactionV0ExtXdr.decode(r) })
    }

    // ========== Envelopes ==========

    @Test
    fun testTransactionV0Envelope() {
        val env = TransactionV0EnvelopeXdr(
            tx = transactionV0Xdr(),
            signatures = listOf(decoratedSignature())
        )
        assertXdrRoundTrip(env, { v, w -> v.encode(w) }, { r -> TransactionV0EnvelopeXdr.decode(r) })
    }

    @Test
    fun testTransactionV1Envelope() {
        val env = TransactionV1EnvelopeXdr(
            tx = transactionXdr(),
            signatures = listOf(decoratedSignature())
        )
        assertXdrRoundTrip(env, { v, w -> v.encode(w) }, { r -> TransactionV1EnvelopeXdr.decode(r) })
    }

    // ========== FeeBumpTransaction ==========

    @Test
    fun testFeeBumpTransactionExtVoid() {
        val ext = FeeBumpTransactionExtXdr.Void
        assertXdrRoundTrip(ext, { v, w -> v.encode(w) }, { r -> FeeBumpTransactionExtXdr.decode(r) })
    }

    @Test
    fun testFeeBumpTransactionInnerTx() {
        val inner = FeeBumpTransactionInnerTxXdr.V1(
            TransactionV1EnvelopeXdr(
                tx = transactionXdr(),
                signatures = listOf(decoratedSignature())
            )
        )
        assertXdrRoundTrip(inner, { v, w -> v.encode(w) }, { r -> FeeBumpTransactionInnerTxXdr.decode(r) })
    }

    @Test
    fun testFeeBumpTransaction() {
        val fbTx = FeeBumpTransactionXdr(
            feeSource = muxedAccountEd25519(),
            fee = int64(200L),
            innerTx = FeeBumpTransactionInnerTxXdr.V1(
                TransactionV1EnvelopeXdr(
                    tx = transactionXdr(),
                    signatures = listOf(decoratedSignature())
                )
            ),
            ext = FeeBumpTransactionExtXdr.Void
        )
        assertXdrRoundTrip(fbTx, { v, w -> v.encode(w) }, { r -> FeeBumpTransactionXdr.decode(r) })
    }

    @Test
    fun testFeeBumpTransactionEnvelope() {
        val env = FeeBumpTransactionEnvelopeXdr(
            tx = FeeBumpTransactionXdr(
                feeSource = muxedAccountMed25519(),
                fee = int64(500L),
                innerTx = FeeBumpTransactionInnerTxXdr.V1(
                    TransactionV1EnvelopeXdr(
                        tx = transactionXdr(),
                        signatures = listOf(decoratedSignature())
                    )
                ),
                ext = FeeBumpTransactionExtXdr.Void
            ),
            signatures = listOf(decoratedSignature())
        )
        assertXdrRoundTrip(env, { v, w -> v.encode(w) }, { r -> FeeBumpTransactionEnvelopeXdr.decode(r) })
    }

    // ========== TransactionEnvelope ==========

    @Test
    fun testTransactionEnvelopeV0() {
        val env = TransactionEnvelopeXdr.V0(
            TransactionV0EnvelopeXdr(
                tx = transactionV0Xdr(),
                signatures = listOf(decoratedSignature())
            )
        )
        assertXdrRoundTrip(env, { v, w -> v.encode(w) }, { r -> TransactionEnvelopeXdr.decode(r) })
    }

    @Test
    fun testTransactionEnvelopeV1() {
        val env = TransactionEnvelopeXdr.V1(
            TransactionV1EnvelopeXdr(
                tx = transactionXdr(),
                signatures = listOf(decoratedSignature())
            )
        )
        assertXdrRoundTrip(env, { v, w -> v.encode(w) }, { r -> TransactionEnvelopeXdr.decode(r) })
    }

    @Test
    fun testTransactionEnvelopeFeeBump() {
        val env = TransactionEnvelopeXdr.FeeBump(
            FeeBumpTransactionEnvelopeXdr(
                tx = FeeBumpTransactionXdr(
                    feeSource = muxedAccountEd25519(),
                    fee = int64(300L),
                    innerTx = FeeBumpTransactionInnerTxXdr.V1(
                        TransactionV1EnvelopeXdr(
                            tx = transactionXdr(),
                            signatures = listOf(decoratedSignature())
                        )
                    ),
                    ext = FeeBumpTransactionExtXdr.Void
                ),
                signatures = listOf(decoratedSignature())
            )
        )
        assertXdrRoundTrip(env, { v, w -> v.encode(w) }, { r -> TransactionEnvelopeXdr.decode(r) })
    }

    // ========== TransactionSignaturePayload ==========

    @Test
    fun testTransactionSignaturePayloadTx() {
        val payload = TransactionSignaturePayloadXdr(
            networkId = hashXdr(),
            taggedTransaction = TransactionSignaturePayloadTaggedTransactionXdr.Tx(transactionXdr())
        )
        assertXdrRoundTrip(payload, { v, w -> v.encode(w) }, { r -> TransactionSignaturePayloadXdr.decode(r) })
    }

    @Test
    fun testTransactionSignaturePayloadFeeBump() {
        val fbTx = FeeBumpTransactionXdr(
            feeSource = muxedAccountEd25519(),
            fee = int64(200L),
            innerTx = FeeBumpTransactionInnerTxXdr.V1(
                TransactionV1EnvelopeXdr(
                    tx = transactionXdr(),
                    signatures = listOf(decoratedSignature())
                )
            ),
            ext = FeeBumpTransactionExtXdr.Void
        )
        val payload = TransactionSignaturePayloadXdr(
            networkId = hashXdr(),
            taggedTransaction = TransactionSignaturePayloadTaggedTransactionXdr.FeeBump(fbTx)
        )
        assertXdrRoundTrip(payload, { v, w -> v.encode(w) }, { r -> TransactionSignaturePayloadXdr.decode(r) })
    }

    // ========== InnerTransactionResult ==========

    @Test
    fun testInnerTransactionResultVoid() {
        val result = InnerTransactionResultXdr(
            feeCharged = int64(0L),
            result = InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_EARLY),
            ext = InnerTransactionResultExtXdr.Void
        )
        assertXdrRoundTrip(result, { v, w -> v.encode(w) }, { r -> InnerTransactionResultXdr.decode(r) })
    }

    @Test
    fun testInnerTransactionResultSuccess() {
        val opResult = OperationResultXdr.Tr(
            OperationResultTrXdr.PaymentResult(
                PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SUCCESS)
            )
        )
        val result = InnerTransactionResultXdr(
            feeCharged = int64(100L),
            result = InnerTransactionResultResultXdr.Results(listOf(opResult)),
            ext = InnerTransactionResultExtXdr.Void
        )
        assertXdrRoundTrip(result, { v, w -> v.encode(w) }, { r -> InnerTransactionResultXdr.decode(r) })
    }

    @Test
    fun testInnerTransactionResultPair() {
        val pair = InnerTransactionResultPairXdr(
            transactionHash = hashXdr(),
            result = InnerTransactionResultXdr(
                feeCharged = int64(0L),
                result = InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_SEQ),
                ext = InnerTransactionResultExtXdr.Void
            )
        )
        assertXdrRoundTrip(pair, { v, w -> v.encode(w) }, { r -> InnerTransactionResultPairXdr.decode(r) })
    }

    // ========== TransactionResultSet / TransactionHistoryResultEntry ==========

    @Test
    fun testTransactionResultSet() {
        val resultPair = TransactionResultPairXdr(
            transactionHash = hashXdr(),
            result = TransactionResultXdr(
                feeCharged = int64(100L),
                result = TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_LATE),
                ext = TransactionResultExtXdr.Void
            )
        )
        val set = TransactionResultSetXdr(listOf(resultPair))
        assertXdrRoundTrip(set, { v, w -> v.encode(w) }, { r -> TransactionResultSetXdr.decode(r) })
    }

    @Test
    fun testTransactionHistoryResultEntry() {
        val entry = TransactionHistoryResultEntryXdr(
            ledgerSeq = uint32(42u),
            txResultSet = TransactionResultSetXdr(emptyList()),
            ext = TransactionHistoryResultEntryExtXdr.Void
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> TransactionHistoryResultEntryXdr.decode(r) })
    }

    // ========== Enum types ==========

    @Test
    fun testMemoTypeEnum() {
        for (e in MemoTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> MemoTypeXdr.decode(r) })
        }
    }

    @Test
    fun testPreconditionTypeEnum() {
        for (e in PreconditionTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> PreconditionTypeXdr.decode(r) })
        }
    }

    @Test
    fun testEnvelopeTypeEnum() {
        for (e in EnvelopeTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> EnvelopeTypeXdr.decode(r) })
        }
    }

    @Test
    fun testAssetTypeEnum() {
        for (e in AssetTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> AssetTypeXdr.decode(r) })
        }
    }

    @Test
    fun testPublicKeyTypeEnum() {
        for (e in PublicKeyTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> PublicKeyTypeXdr.decode(r) })
        }
    }

    @Test
    fun testCryptoKeyTypeEnum() {
        for (e in CryptoKeyTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> CryptoKeyTypeXdr.decode(r) })
        }
    }

    @Test
    fun testSignerKeyTypeEnum() {
        for (e in SignerKeyTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SignerKeyTypeXdr.decode(r) })
        }
    }

    @Test
    fun testLedgerEntryTypeEnum() {
        for (e in LedgerEntryTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> LedgerEntryTypeXdr.decode(r) })
        }
    }

    @Test
    fun testInflationResultCodeEnum() {
        for (e in InflationResultCodeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> InflationResultCodeXdr.decode(r) })
        }
    }

    @Test
    fun testManageOfferEffectEnum() {
        for (e in ManageOfferEffectXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> ManageOfferEffectXdr.decode(r) })
        }
    }

    @Test
    fun testRevokeSponsorshipTypeEnum() {
        for (e in RevokeSponsorshipTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> RevokeSponsorshipTypeXdr.decode(r) })
        }
    }

    @Test
    fun testClaimAtomTypeEnum() {
        for (e in ClaimAtomTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> ClaimAtomTypeXdr.decode(r) })
        }
    }

    @Test
    fun testClaimPredicateTypeEnum() {
        for (e in ClaimPredicateTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> ClaimPredicateTypeXdr.decode(r) })
        }
    }

    @Test
    fun testClaimableBalanceIDTypeEnum() {
        for (e in ClaimableBalanceIDTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> ClaimableBalanceIDTypeXdr.decode(r) })
        }
    }

    @Test
    fun testClaimantTypeEnum() {
        for (e in ClaimantTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> ClaimantTypeXdr.decode(r) })
        }
    }

    @Test
    fun testPathPaymentStrictSendResultCodeEnum() {
        for (e in PathPaymentStrictSendResultCodeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> PathPaymentStrictSendResultCodeXdr.decode(r) })
        }
    }
}
