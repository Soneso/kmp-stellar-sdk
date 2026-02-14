package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assertXdrRoundTrip
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.accountId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hashXdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.sequenceNumber
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.thresholds
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.string32
import kotlin.test.Test

class XdrBucketEntryTest {

    // ========== Enum types ==========

    @Test
    fun testBucketEntryTypeEnum() {
        for (e in BucketEntryTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> BucketEntryTypeXdr.decode(r) })
        }
    }

    @Test
    fun testBucketListTypeEnum() {
        for (e in BucketListTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> BucketListTypeXdr.decode(r) })
        }
    }

    @Test
    fun testHotArchiveBucketEntryTypeEnum() {
        for (e in HotArchiveBucketEntryTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> HotArchiveBucketEntryTypeXdr.decode(r) })
        }
    }

    // ========== BucketMetadata ==========

    @Test
    fun testBucketMetadataExtVoid() {
        val ext = BucketMetadataExtXdr.Void
        assertXdrRoundTrip(ext, { v, w -> v.encode(w) }, { r -> BucketMetadataExtXdr.decode(r) })
    }

    @Test
    fun testBucketMetadataExtWithType() {
        val ext = BucketMetadataExtXdr.BucketListType(BucketListTypeXdr.LIVE)
        assertXdrRoundTrip(ext, { v, w -> v.encode(w) }, { r -> BucketMetadataExtXdr.decode(r) })
    }

    @Test
    fun testBucketMetadata() {
        val meta = BucketMetadataXdr(
            ledgerVersion = uint32(21u),
            ext = BucketMetadataExtXdr.Void
        )
        assertXdrRoundTrip(meta, { v, w -> v.encode(w) }, { r -> BucketMetadataXdr.decode(r) })
    }

    @Test
    fun testBucketMetadataWithBucketListType() {
        val meta = BucketMetadataXdr(
            ledgerVersion = uint32(22u),
            ext = BucketMetadataExtXdr.BucketListType(BucketListTypeXdr.HOT_ARCHIVE)
        )
        assertXdrRoundTrip(meta, { v, w -> v.encode(w) }, { r -> BucketMetadataXdr.decode(r) })
    }

    // ========== BucketEntry ==========

    private fun simpleLedgerEntry() = LedgerEntryXdr(
        lastModifiedLedgerSeq = uint32(100u),
        data = LedgerEntryDataXdr.Account(
            AccountEntryXdr(
                accountId = accountId(),
                balance = int64(1000L),
                seqNum = sequenceNumber(1L),
                numSubEntries = uint32(0u),
                inflationDest = null,
                flags = uint32(0u),
                homeDomain = string32(""),
                thresholds = thresholds(),
                signers = emptyList(),
                ext = AccountEntryExtXdr.Void
            )
        ),
        ext = LedgerEntryExtXdr.Void
    )

    private fun simpleLedgerKey() = LedgerKeyXdr.Account(
        LedgerKeyAccountXdr(accountId())
    )

    @Test
    fun testBucketEntryLiveEntry() {
        val entry = BucketEntryXdr.LiveEntry(simpleLedgerEntry())
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> BucketEntryXdr.decode(r) })
    }

    @Test
    fun testBucketEntryDeadEntry() {
        val entry = BucketEntryXdr.DeadEntry(simpleLedgerKey())
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> BucketEntryXdr.decode(r) })
    }

    @Test
    fun testBucketEntryMetaEntry() {
        val entry = BucketEntryXdr.MetaEntry(
            BucketMetadataXdr(
                ledgerVersion = uint32(21u),
                ext = BucketMetadataExtXdr.Void
            )
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> BucketEntryXdr.decode(r) })
    }

    // ========== HotArchiveBucketEntry ==========

    @Test
    fun testHotArchiveBucketEntryArchived() {
        val entry = HotArchiveBucketEntryXdr.ArchivedEntry(simpleLedgerEntry())
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> HotArchiveBucketEntryXdr.decode(r) })
    }

    @Test
    fun testHotArchiveBucketEntryLive() {
        val entry = HotArchiveBucketEntryXdr.Key(simpleLedgerKey())
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> HotArchiveBucketEntryXdr.decode(r) })
    }

    @Test
    fun testHotArchiveBucketEntryMeta() {
        val entry = HotArchiveBucketEntryXdr.MetaEntry(
            BucketMetadataXdr(
                ledgerVersion = uint32(22u),
                ext = BucketMetadataExtXdr.BucketListType(BucketListTypeXdr.HOT_ARCHIVE)
            )
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> HotArchiveBucketEntryXdr.decode(r) })
    }
}
