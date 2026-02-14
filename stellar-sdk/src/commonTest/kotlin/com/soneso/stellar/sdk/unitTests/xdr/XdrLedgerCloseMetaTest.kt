package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test

/**
 * Tests for LedgerCloseMeta types (V0, V1, V2), LedgerCloseMetaExt.
 */
class XdrLedgerCloseMetaTest {

    private fun stellarValueBasic() = StellarValueXdr(
        txSetHash = XdrTestHelpers.hashXdr(),
        closeTime = TimePointXdr(Uint64Xdr(1700000000UL)),
        upgrades = emptyList(),
        ext = StellarValueExtXdr.Void
    )

    private fun ledgerHeader() = LedgerHeaderXdr(
        ledgerVersion = Uint32Xdr(21u),
        previousLedgerHash = XdrTestHelpers.hashXdr(),
        scpValue = stellarValueBasic(),
        txSetResultHash = XdrTestHelpers.hashXdr(),
        bucketListHash = XdrTestHelpers.hashXdr(),
        ledgerSeq = Uint32Xdr(1u),
        totalCoins = Int64Xdr(1000L),
        feePool = Int64Xdr(100L),
        inflationSeq = Uint32Xdr(0u),
        idPool = Uint64Xdr(1UL),
        baseFee = Uint32Xdr(100u),
        baseReserve = Uint32Xdr(5000000u),
        maxTxSetSize = Uint32Xdr(100u),
        skipList = Array(4) { XdrTestHelpers.hashXdr() },
        ext = LedgerHeaderExtXdr.Void
    )

    private fun headerHistoryEntry() = LedgerHeaderHistoryEntryXdr(
        hash = XdrTestHelpers.hashXdr(),
        header = ledgerHeader(),
        ext = LedgerHeaderHistoryEntryExtXdr.Void
    )

    private fun simpleTransactionResult() = TransactionResultXdr(
        feeCharged = Int64Xdr(100L),
        result = TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_EARLY),
        ext = TransactionResultExtXdr.Void
    )

    private fun txResultMeta() = TransactionResultMetaXdr(
        result = TransactionResultPairXdr(XdrTestHelpers.hashXdr(), simpleTransactionResult()),
        feeProcessing = LedgerEntryChangesXdr(emptyList()),
        txApplyProcessing = TransactionMetaXdr.Operations(emptyList())
    )

    // ---- LedgerCloseMetaExt ----

    @Test fun testLedgerCloseMetaExtVoid() {
        XdrTestHelpers.assertXdrRoundTrip(
            LedgerCloseMetaExtXdr.Void,
            { a, w -> a.encode(w) },
            { r -> LedgerCloseMetaExtXdr.decode(r) }
        )
    }

    @Test fun testLedgerCloseMetaExtV1() {
        val v1 = LedgerCloseMetaExtV1Xdr(
            ext = ExtensionPointXdr.Void,
            sorobanFeeWrite1Kb = Int64Xdr(5000L)
        )
        val v = LedgerCloseMetaExtXdr.V1(v1)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaExtXdr.decode(r) })
    }

    // ---- LedgerCloseMetaExtV1 ----

    @Test fun testLedgerCloseMetaExtV1Direct() {
        val v = LedgerCloseMetaExtV1Xdr(
            ext = ExtensionPointXdr.Void,
            sorobanFeeWrite1Kb = Int64Xdr(3000L)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaExtV1Xdr.decode(r) })
    }

    // ---- LedgerCloseMetaV0 ----

    @Test fun testLedgerCloseMetaV0() {
        val v = LedgerCloseMetaV0Xdr(
            ledgerHeader = headerHistoryEntry(),
            txSet = TransactionSetXdr(XdrTestHelpers.hashXdr(), emptyList()),
            txProcessing = emptyList(),
            upgradesProcessing = emptyList(),
            scpInfo = emptyList()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaV0Xdr.decode(r) })
    }

    @Test fun testLedgerCloseMetaV0WithTxProcessing() {
        val v = LedgerCloseMetaV0Xdr(
            ledgerHeader = headerHistoryEntry(),
            txSet = TransactionSetXdr(XdrTestHelpers.hashXdr(), emptyList()),
            txProcessing = listOf(txResultMeta()),
            upgradesProcessing = emptyList(),
            scpInfo = emptyList()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaV0Xdr.decode(r) })
    }

    // ---- LedgerCloseMetaV1 ----

    @Test fun testLedgerCloseMetaV1() {
        val genTxSet = GeneralizedTransactionSetXdr.V1TxSet(
            TransactionSetV1Xdr(XdrTestHelpers.hashXdr(), emptyList())
        )
        val v = LedgerCloseMetaV1Xdr(
            ext = LedgerCloseMetaExtXdr.Void,
            ledgerHeader = headerHistoryEntry(),
            txSet = genTxSet,
            txProcessing = emptyList(),
            upgradesProcessing = emptyList(),
            scpInfo = emptyList(),
            totalByteSizeOfLiveSorobanState = Uint64Xdr(1000000UL),
            evictedKeys = emptyList(),
            unused = emptyList()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaV1Xdr.decode(r) })
    }

    // ---- LedgerCloseMetaV2 ----

    @Test fun testLedgerCloseMetaV2() {
        val genTxSet = GeneralizedTransactionSetXdr.V1TxSet(
            TransactionSetV1Xdr(XdrTestHelpers.hashXdr(), emptyList())
        )
        val v = LedgerCloseMetaV2Xdr(
            ext = LedgerCloseMetaExtXdr.Void,
            ledgerHeader = headerHistoryEntry(),
            txSet = genTxSet,
            txProcessing = emptyList(),
            upgradesProcessing = emptyList(),
            scpInfo = emptyList(),
            totalByteSizeOfLiveSorobanState = Uint64Xdr(2000000UL),
            evictedKeys = emptyList()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaV2Xdr.decode(r) })
    }

    // ---- LedgerCloseMeta union ----

    @Test fun testLedgerCloseMetaV0Union() {
        val v0 = LedgerCloseMetaV0Xdr(
            ledgerHeader = headerHistoryEntry(),
            txSet = TransactionSetXdr(XdrTestHelpers.hashXdr(), emptyList()),
            txProcessing = emptyList(),
            upgradesProcessing = emptyList(),
            scpInfo = emptyList()
        )
        val v = LedgerCloseMetaXdr.V0(v0)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaXdr.decode(r) })
    }

    @Test fun testLedgerCloseMetaV1Union() {
        val genTxSet = GeneralizedTransactionSetXdr.V1TxSet(
            TransactionSetV1Xdr(XdrTestHelpers.hashXdr(), emptyList())
        )
        val v1 = LedgerCloseMetaV1Xdr(
            ext = LedgerCloseMetaExtXdr.Void,
            ledgerHeader = headerHistoryEntry(),
            txSet = genTxSet,
            txProcessing = emptyList(),
            upgradesProcessing = emptyList(),
            scpInfo = emptyList(),
            totalByteSizeOfLiveSorobanState = Uint64Xdr(0UL),
            evictedKeys = emptyList(),
            unused = emptyList()
        )
        val v = LedgerCloseMetaXdr.V1(v1)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaXdr.decode(r) })
    }

    @Test fun testLedgerCloseMetaV2Union() {
        val genTxSet = GeneralizedTransactionSetXdr.V1TxSet(
            TransactionSetV1Xdr(XdrTestHelpers.hashXdr(), emptyList())
        )
        val v2 = LedgerCloseMetaV2Xdr(
            ext = LedgerCloseMetaExtXdr.Void,
            ledgerHeader = headerHistoryEntry(),
            txSet = genTxSet,
            txProcessing = emptyList(),
            upgradesProcessing = emptyList(),
            scpInfo = emptyList(),
            totalByteSizeOfLiveSorobanState = Uint64Xdr(0UL),
            evictedKeys = emptyList()
        )
        val v = LedgerCloseMetaXdr.V2(v2)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseMetaXdr.decode(r) })
    }
}
