package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test

/**
 * Tests for LedgerUpgrade, LedgerHeader, StellarValue, and related types.
 */
class XdrLedgerUpgradeTest {

    // ---- LedgerUpgradeType enum ----

    @Test fun testLedgerUpgradeTypeEnum() {
        for (e in LedgerUpgradeTypeXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(e, { a, w -> a.encode(w) }, { r -> LedgerUpgradeTypeXdr.decode(r) })
        }
    }

    // ---- LedgerUpgrade union (all cases) ----

    @Test fun testLedgerUpgradeNewLedgerVersion() {
        val v = LedgerUpgradeXdr.NewLedgerVersion(Uint32Xdr(22u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerUpgradeXdr.decode(r) })
    }

    @Test fun testLedgerUpgradeNewBaseFee() {
        val v = LedgerUpgradeXdr.NewBaseFee(Uint32Xdr(100u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerUpgradeXdr.decode(r) })
    }

    @Test fun testLedgerUpgradeNewMaxTxSetSize() {
        val v = LedgerUpgradeXdr.NewMaxTxSetSize(Uint32Xdr(1000u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerUpgradeXdr.decode(r) })
    }

    @Test fun testLedgerUpgradeNewBaseReserve() {
        val v = LedgerUpgradeXdr.NewBaseReserve(Uint32Xdr(5000000u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerUpgradeXdr.decode(r) })
    }

    @Test fun testLedgerUpgradeNewFlags() {
        val v = LedgerUpgradeXdr.NewFlags(Uint32Xdr(1u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerUpgradeXdr.decode(r) })
    }

    @Test fun testLedgerUpgradeNewConfig() {
        val v = LedgerUpgradeXdr.NewConfig(
            ConfigUpgradeSetKeyXdr(XdrTestHelpers.contractId(), XdrTestHelpers.hashXdr())
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerUpgradeXdr.decode(r) })
    }

    @Test fun testLedgerUpgradeNewMaxSorobanTxSetSize() {
        val v = LedgerUpgradeXdr.NewMaxSorobanTxSetSize(Uint32Xdr(50u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerUpgradeXdr.decode(r) })
    }

    // ---- StellarValueType enum ----

    @Test fun testStellarValueTypeEnum() {
        for (e in StellarValueTypeXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(e, { a, w -> a.encode(w) }, { r -> StellarValueTypeXdr.decode(r) })
        }
    }

    // ---- StellarValueExt ----

    @Test fun testStellarValueExtVoid() {
        XdrTestHelpers.assertXdrRoundTrip(StellarValueExtXdr.Void, { a, w -> a.encode(w) }, { r -> StellarValueExtXdr.decode(r) })
    }

    @Test fun testStellarValueExtSigned() {
        val sig = LedgerCloseValueSignatureXdr(
            nodeId = NodeIDXdr(XdrTestHelpers.publicKeyEd25519()),
            signature = SignatureXdr(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        )
        val v = StellarValueExtXdr.LcValueSignature(sig)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> StellarValueExtXdr.decode(r) })
    }

    // ---- LedgerCloseValueSignature ----

    @Test fun testLedgerCloseValueSignature() {
        val v = LedgerCloseValueSignatureXdr(
            nodeId = NodeIDXdr(XdrTestHelpers.publicKeyEd25519()),
            signature = SignatureXdr(byteArrayOf(10, 20, 30, 40))
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerCloseValueSignatureXdr.decode(r) })
    }

    // ---- StellarValue ----

    @Test fun testStellarValueBasic() {
        val v = StellarValueXdr(
            txSetHash = XdrTestHelpers.hashXdr(),
            closeTime = TimePointXdr(Uint64Xdr(1700000000UL)),
            upgrades = emptyList(),
            ext = StellarValueExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> StellarValueXdr.decode(r) })
    }

    @Test fun testStellarValueWithUpgrades() {
        val v = StellarValueXdr(
            txSetHash = XdrTestHelpers.hashXdr(),
            closeTime = TimePointXdr(Uint64Xdr(1700000000UL)),
            upgrades = listOf(UpgradeTypeXdr(byteArrayOf(1, 2, 3))),
            ext = StellarValueExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> StellarValueXdr.decode(r) })
    }

    // ---- LedgerHeaderFlags enum ----

    @Test fun testLedgerHeaderFlagsEnum() {
        for (e in LedgerHeaderFlagsXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(e, { a, w -> a.encode(w) }, { r -> LedgerHeaderFlagsXdr.decode(r) })
        }
    }

    // ---- LedgerHeaderExtensionV1 ----

    @Test fun testLedgerHeaderExtensionV1() {
        val v = LedgerHeaderExtensionV1Xdr(
            flags = Uint32Xdr(1u),
            ext = LedgerHeaderExtensionV1ExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerHeaderExtensionV1Xdr.decode(r) })
    }

    // ---- LedgerHeaderExt ----

    @Test fun testLedgerHeaderExtVoid() {
        XdrTestHelpers.assertXdrRoundTrip(LedgerHeaderExtXdr.Void, { a, w -> a.encode(w) }, { r -> LedgerHeaderExtXdr.decode(r) })
    }

    @Test fun testLedgerHeaderExtV1() {
        val v = LedgerHeaderExtXdr.V1(
            LedgerHeaderExtensionV1Xdr(Uint32Xdr(1u), LedgerHeaderExtensionV1ExtXdr.Void)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerHeaderExtXdr.decode(r) })
    }

    // ---- LedgerHeader ----

    private fun stellarValueBasic() = StellarValueXdr(
        txSetHash = XdrTestHelpers.hashXdr(),
        closeTime = TimePointXdr(Uint64Xdr(1700000000UL)),
        upgrades = emptyList(),
        ext = StellarValueExtXdr.Void
    )

    @Test fun testLedgerHeader() {
        val v = LedgerHeaderXdr(
            ledgerVersion = Uint32Xdr(21u),
            previousLedgerHash = XdrTestHelpers.hashXdr(),
            scpValue = stellarValueBasic(),
            txSetResultHash = XdrTestHelpers.hashXdr(),
            bucketListHash = XdrTestHelpers.hashXdr(),
            ledgerSeq = Uint32Xdr(50000u),
            totalCoins = Int64Xdr(1050000000000000000L),
            feePool = Int64Xdr(100000L),
            inflationSeq = Uint32Xdr(0u),
            idPool = Uint64Xdr(999UL),
            baseFee = Uint32Xdr(100u),
            baseReserve = Uint32Xdr(5000000u),
            maxTxSetSize = Uint32Xdr(100u),
            skipList = Array(4) { XdrTestHelpers.hashXdr() },
            ext = LedgerHeaderExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerHeaderXdr.decode(r) })
    }

    // ---- LedgerHeaderHistoryEntry ----

    @Test fun testLedgerHeaderHistoryEntry() {
        val header = LedgerHeaderXdr(
            Uint32Xdr(21u), XdrTestHelpers.hashXdr(), stellarValueBasic(),
            XdrTestHelpers.hashXdr(), XdrTestHelpers.hashXdr(), Uint32Xdr(1u),
            Int64Xdr(1000L), Int64Xdr(100L), Uint32Xdr(0u), Uint64Xdr(1UL),
            Uint32Xdr(100u), Uint32Xdr(5000000u), Uint32Xdr(100u),
            Array(4) { XdrTestHelpers.hashXdr() }, LedgerHeaderExtXdr.Void
        )
        val v = LedgerHeaderHistoryEntryXdr(
            hash = XdrTestHelpers.hashXdr(),
            header = header,
            ext = LedgerHeaderHistoryEntryExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerHeaderHistoryEntryXdr.decode(r) })
    }

    // ---- UpgradeEntryMeta ----

    @Test fun testUpgradeEntryMeta() {
        val v = UpgradeEntryMetaXdr(
            upgrade = LedgerUpgradeXdr.NewBaseFee(Uint32Xdr(200u)),
            changes = LedgerEntryChangesXdr(emptyList())
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> UpgradeEntryMetaXdr.decode(r) })
    }
}
