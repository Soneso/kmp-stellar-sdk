package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assertXdrRoundTrip
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hashXdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.muxedAccountEd25519
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.sequenceNumber
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import kotlin.test.Test

class XdrTxSetTest {

    @Test fun testTxSetComponentTypeEnum() { for (e in TxSetComponentTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> TxSetComponentTypeXdr.decode(r) }) }

    private fun simpleOperation() = OperationXdr(
        sourceAccount = null,
        body = OperationBodyXdr.PaymentOp(
            PaymentOpXdr(muxedAccountEd25519(), AssetXdr.Void, int64(100L))
        )
    )

    private fun simpleTransactionXdr() = TransactionXdr(
        sourceAccount = muxedAccountEd25519(),
        fee = uint32(100u),
        seqNum = sequenceNumber(1L),
        cond = PreconditionsXdr.Void,
        memo = MemoXdr.Void,
        operations = listOf(simpleOperation()),
        ext = TransactionExtXdr.Void
    )

    private fun simpleDecoratedSig() = DecoratedSignatureXdr(
        hint = SignatureHintXdr(byteArrayOf(1, 2, 3, 4)),
        signature = SignatureXdr(ByteArray(32) { it.toByte() })
    )

    private fun simpleTransactionEnvelope(): TransactionEnvelopeXdr =
        TransactionEnvelopeXdr.V1(TransactionV1EnvelopeXdr(tx = simpleTransactionXdr(), signatures = listOf(simpleDecoratedSig())))

    // ========== TxSetComponent ==========

    @Test
    fun testTxSetComponentWithBaseFee() {
        val comp = TxSetComponentTxsMaybeDiscountedFeeXdr(baseFee = int64(100L), txs = listOf(simpleTransactionEnvelope()))
        assertXdrRoundTrip(comp, { v, w -> v.encode(w) }, { r -> TxSetComponentTxsMaybeDiscountedFeeXdr.decode(r) })
    }

    @Test
    fun testTxSetComponentNoBaseFee() {
        val comp = TxSetComponentTxsMaybeDiscountedFeeXdr(baseFee = null, txs = listOf(simpleTransactionEnvelope()))
        assertXdrRoundTrip(comp, { v, w -> v.encode(w) }, { r -> TxSetComponentTxsMaybeDiscountedFeeXdr.decode(r) })
    }

    @Test
    fun testTxSetComponentEmpty() {
        val comp = TxSetComponentTxsMaybeDiscountedFeeXdr(baseFee = null, txs = emptyList())
        assertXdrRoundTrip(comp, { v, w -> v.encode(w) }, { r -> TxSetComponentTxsMaybeDiscountedFeeXdr.decode(r) })
    }

    @Test
    fun testTxSetComponent() {
        val comp = TxSetComponentXdr.TxsMaybeDiscountedFee(
            TxSetComponentTxsMaybeDiscountedFeeXdr(baseFee = int64(50L), txs = listOf(simpleTransactionEnvelope()))
        )
        assertXdrRoundTrip(comp, { v, w -> v.encode(w) }, { r -> TxSetComponentXdr.decode(r) })
    }

    // ========== TransactionPhase ==========

    @Test
    fun testTransactionPhaseV0Components() {
        val phase = TransactionPhaseXdr.V0Components(listOf(
            TxSetComponentXdr.TxsMaybeDiscountedFee(
                TxSetComponentTxsMaybeDiscountedFeeXdr(baseFee = int64(100L), txs = listOf(simpleTransactionEnvelope()))
            )
        ))
        assertXdrRoundTrip(phase, { v, w -> v.encode(w) }, { r -> TransactionPhaseXdr.decode(r) })
    }

    @Test
    fun testTransactionPhaseV0Empty() {
        assertXdrRoundTrip(TransactionPhaseXdr.V0Components(emptyList()), { v, w -> v.encode(w) }, { r -> TransactionPhaseXdr.decode(r) })
    }

    // ========== ParallelTx types ==========

    @Test
    fun testDependentTxCluster() {
        assertXdrRoundTrip(DependentTxClusterXdr(listOf(simpleTransactionEnvelope())), { v, w -> v.encode(w) }, { r -> DependentTxClusterXdr.decode(r) })
    }

    @Test
    fun testDependentTxClusterEmpty() {
        assertXdrRoundTrip(DependentTxClusterXdr(emptyList()), { v, w -> v.encode(w) }, { r -> DependentTxClusterXdr.decode(r) })
    }

    @Test
    fun testParallelTxExecutionStage() {
        assertXdrRoundTrip(
            ParallelTxExecutionStageXdr(listOf(DependentTxClusterXdr(listOf(simpleTransactionEnvelope())))),
            { v, w -> v.encode(w) }, { r -> ParallelTxExecutionStageXdr.decode(r) }
        )
    }

    @Test
    fun testParallelTxExecutionStageEmpty() {
        assertXdrRoundTrip(ParallelTxExecutionStageXdr(emptyList()), { v, w -> v.encode(w) }, { r -> ParallelTxExecutionStageXdr.decode(r) })
    }

    @Test
    fun testParallelTxsComponentWithBaseFee() {
        val comp = ParallelTxsComponentXdr(
            baseFee = int64(200L),
            executionStages = listOf(ParallelTxExecutionStageXdr(listOf(DependentTxClusterXdr(listOf(simpleTransactionEnvelope())))))
        )
        assertXdrRoundTrip(comp, { v, w -> v.encode(w) }, { r -> ParallelTxsComponentXdr.decode(r) })
    }

    @Test
    fun testParallelTxsComponentNoBaseFee() {
        assertXdrRoundTrip(ParallelTxsComponentXdr(baseFee = null, executionStages = emptyList()), { v, w -> v.encode(w) }, { r -> ParallelTxsComponentXdr.decode(r) })
    }

    @Test
    fun testTransactionPhaseParallel() {
        val phase = TransactionPhaseXdr.ParallelTxsComponent(
            ParallelTxsComponentXdr(
                baseFee = int64(100L),
                executionStages = listOf(ParallelTxExecutionStageXdr(listOf(DependentTxClusterXdr(listOf(simpleTransactionEnvelope())))))
            )
        )
        assertXdrRoundTrip(phase, { v, w -> v.encode(w) }, { r -> TransactionPhaseXdr.decode(r) })
    }

    // ========== TransactionHistoryEntry ==========

    @Test fun testTransactionHistoryEntryExtVoid() { assertXdrRoundTrip(TransactionHistoryEntryExtXdr.Void, { v, w -> v.encode(w) }, { r -> TransactionHistoryEntryExtXdr.decode(r) }) }
    @Test fun testTransactionHistoryResultEntryExtVoid() { assertXdrRoundTrip(TransactionHistoryResultEntryExtXdr.Void, { v, w -> v.encode(w) }, { r -> TransactionHistoryResultEntryExtXdr.decode(r) }) }

    @Test
    fun testTransactionHistoryEntry() {
        val entry = TransactionHistoryEntryXdr(
            ledgerSeq = uint32(1000u),
            txSet = TransactionSetXdr(previousLedgerHash = hashXdr(), txs = listOf(simpleTransactionEnvelope())),
            ext = TransactionHistoryEntryExtXdr.Void
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> TransactionHistoryEntryXdr.decode(r) })
    }

    @Test
    fun testTransactionHistoryEntryGeneralized() {
        val entry = TransactionHistoryEntryXdr(
            ledgerSeq = uint32(2000u),
            txSet = TransactionSetXdr(previousLedgerHash = hashXdr(), txs = emptyList()),
            ext = TransactionHistoryEntryExtXdr.GeneralizedTxSet(
                GeneralizedTransactionSetXdr.V1TxSet(
                    TransactionSetV1Xdr(
                        previousLedgerHash = hashXdr(),
                        phases = listOf(TransactionPhaseXdr.V0Components(listOf(
                            TxSetComponentXdr.TxsMaybeDiscountedFee(TxSetComponentTxsMaybeDiscountedFeeXdr(null, listOf(simpleTransactionEnvelope())))
                        )))
                    )
                )
            )
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> TransactionHistoryEntryXdr.decode(r) })
    }
}
