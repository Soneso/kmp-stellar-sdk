package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test

class XdrLedgerEntryChangeTest {

    private fun simpleLedgerEntry(): LedgerEntryXdr {
        val acct = AccountEntryXdr(
            accountId = XdrTestHelpers.accountId(), balance = Int64Xdr(10000000L),
            seqNum = XdrTestHelpers.sequenceNumber(), numSubEntries = Uint32Xdr(0u),
            inflationDest = null, flags = Uint32Xdr(0u), homeDomain = XdrTestHelpers.string32(""),
            thresholds = XdrTestHelpers.thresholds(), signers = emptyList(), ext = AccountEntryExtXdr.Void
        )
        return LedgerEntryXdr(lastModifiedLedgerSeq = Uint32Xdr(100u), data = LedgerEntryDataXdr.Account(acct), ext = LedgerEntryExtXdr.Void)
    }

    private fun simpleAccountLedgerKey(): LedgerKeyXdr =
        LedgerKeyXdr.Account(LedgerKeyAccountXdr(XdrTestHelpers.accountId()))

    @Test fun testLedgerEntryChangeTypeEnum() {
        for (e in LedgerEntryChangeTypeXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(e, { a, w -> a.encode(w) }, { r -> LedgerEntryChangeTypeXdr.decode(r) })
        }
    }

    @Test fun testLedgerEntryChangeCreated() {
        val v = LedgerEntryChangeXdr.Created(simpleLedgerEntry())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryChangeXdr.decode(r) })
    }

    @Test fun testLedgerEntryChangeUpdated() {
        val v = LedgerEntryChangeXdr.Updated(simpleLedgerEntry())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryChangeXdr.decode(r) })
    }

    @Test fun testLedgerEntryChangeRemoved() {
        val v = LedgerEntryChangeXdr.Removed(simpleAccountLedgerKey())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryChangeXdr.decode(r) })
    }

    @Test fun testLedgerEntryChangeState() {
        val v = LedgerEntryChangeXdr.State(simpleLedgerEntry())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryChangeXdr.decode(r) })
    }

    @Test fun testLedgerEntryChangeRestored() {
        val v = LedgerEntryChangeXdr.Restored(simpleLedgerEntry())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryChangeXdr.decode(r) })
    }

    @Test fun testLedgerEntryChangesEmpty() {
        val v = LedgerEntryChangesXdr(emptyList())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryChangesXdr.decode(r) })
    }

    @Test fun testLedgerEntryChangesMultiple() {
        val v = LedgerEntryChangesXdr(listOf(
            LedgerEntryChangeXdr.Created(simpleLedgerEntry()),
            LedgerEntryChangeXdr.Removed(simpleAccountLedgerKey())
        ))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryChangesXdr.decode(r) })
    }

    @Test fun testOperationMeta() {
        val v = OperationMetaXdr(changes = LedgerEntryChangesXdr(emptyList()))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> OperationMetaXdr.decode(r) })
    }

    @Test fun testOperationMetaV2() {
        val v = OperationMetaV2Xdr(ext = ExtensionPointXdr.Void, changes = LedgerEntryChangesXdr(emptyList()), events = emptyList())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> OperationMetaV2Xdr.decode(r) })
    }

    @Test fun testTransactionMetaV0() {
        val v = TransactionMetaXdr.Operations(listOf(OperationMetaXdr(LedgerEntryChangesXdr(emptyList()))))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> TransactionMetaXdr.decode(r) })
    }

    @Test fun testTransactionMetaV1() {
        val v1 = TransactionMetaV1Xdr(txChanges = LedgerEntryChangesXdr(emptyList()), operations = emptyList())
        XdrTestHelpers.assertXdrRoundTrip(TransactionMetaXdr.V1(v1), { a, w -> a.encode(w) }, { r -> TransactionMetaXdr.decode(r) })
    }

    @Test fun testTransactionMetaV2() {
        val v2 = TransactionMetaV2Xdr(LedgerEntryChangesXdr(emptyList()), emptyList(), LedgerEntryChangesXdr(emptyList()))
        XdrTestHelpers.assertXdrRoundTrip(TransactionMetaXdr.V2(v2), { a, w -> a.encode(w) }, { r -> TransactionMetaXdr.decode(r) })
    }

    @Test fun testTransactionMetaV3() {
        val v3 = TransactionMetaV3Xdr(ExtensionPointXdr.Void, LedgerEntryChangesXdr(emptyList()), emptyList(), LedgerEntryChangesXdr(emptyList()), null)
        XdrTestHelpers.assertXdrRoundTrip(TransactionMetaXdr.V3(v3), { a, w -> a.encode(w) }, { r -> TransactionMetaXdr.decode(r) })
    }

    @Test fun testTransactionMetaV3WithSoroban() {
        val sm = SorobanTransactionMetaXdr(SorobanTransactionMetaExtXdr.Void, emptyList(), SCValXdr.Void(SCValTypeXdr.SCV_VOID), emptyList())
        val v3 = TransactionMetaV3Xdr(ExtensionPointXdr.Void, LedgerEntryChangesXdr(emptyList()), emptyList(), LedgerEntryChangesXdr(emptyList()), sm)
        XdrTestHelpers.assertXdrRoundTrip(TransactionMetaXdr.V3(v3), { a, w -> a.encode(w) }, { r -> TransactionMetaXdr.decode(r) })
    }

    @Test fun testTransactionMetaV4() {
        val v4 = TransactionMetaV4Xdr(ExtensionPointXdr.Void, LedgerEntryChangesXdr(emptyList()), emptyList(), LedgerEntryChangesXdr(emptyList()), null, emptyList(), emptyList())
        XdrTestHelpers.assertXdrRoundTrip(TransactionMetaXdr.V4(v4), { a, w -> a.encode(w) }, { r -> TransactionMetaXdr.decode(r) })
    }

    @Test fun testTransactionMetaV4WithSorobanMetaV2() {
        val smV2 = SorobanTransactionMetaV2Xdr(SorobanTransactionMetaExtXdr.Void, SCValXdr.B(true))
        val v4 = TransactionMetaV4Xdr(ExtensionPointXdr.Void, LedgerEntryChangesXdr(emptyList()), emptyList(), LedgerEntryChangesXdr(emptyList()), smV2, emptyList(), emptyList())
        XdrTestHelpers.assertXdrRoundTrip(TransactionMetaXdr.V4(v4), { a, w -> a.encode(w) }, { r -> TransactionMetaXdr.decode(r) })
    }

    @Test fun testTransactionMetaV1Direct() {
        val v = TransactionMetaV1Xdr(
            txChanges = LedgerEntryChangesXdr(listOf(LedgerEntryChangeXdr.Created(simpleLedgerEntry()))),
            operations = listOf(OperationMetaXdr(LedgerEntryChangesXdr(emptyList())))
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> TransactionMetaV1Xdr.decode(r) })
    }

    @Test fun testTransactionMetaV2Direct() {
        val v = TransactionMetaV2Xdr(LedgerEntryChangesXdr(emptyList()), emptyList(), LedgerEntryChangesXdr(emptyList()))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> TransactionMetaV2Xdr.decode(r) })
    }

    @Test fun testTransactionMetaV3Direct() {
        val v = TransactionMetaV3Xdr(ExtensionPointXdr.Void, LedgerEntryChangesXdr(emptyList()), emptyList(), LedgerEntryChangesXdr(emptyList()), null)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> TransactionMetaV3Xdr.decode(r) })
    }

    @Test fun testTransactionMetaV4Direct() {
        val v = TransactionMetaV4Xdr(ExtensionPointXdr.Void, LedgerEntryChangesXdr(emptyList()), emptyList(), LedgerEntryChangesXdr(emptyList()), null, emptyList(), emptyList())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> TransactionMetaV4Xdr.decode(r) })
    }
}
