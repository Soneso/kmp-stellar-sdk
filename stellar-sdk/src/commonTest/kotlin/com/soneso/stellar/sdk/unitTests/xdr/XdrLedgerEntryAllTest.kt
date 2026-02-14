package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertEquals

class XdrLedgerEntryAllTest {

    private fun rtData(value: LedgerEntryDataXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> LedgerEntryDataXdr.decode(r) })

    private fun rtEntry(value: LedgerEntryXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> LedgerEntryXdr.decode(r) })

    // ---- AccountEntry tests ----

    @Test
    fun testAccountEntryBasic() {
        val entry = AccountEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            balance = XdrTestHelpers.int64(10000000L),
            seqNum = XdrTestHelpers.sequenceNumber(12345L),
            numSubEntries = XdrTestHelpers.uint32(3u),
            inflationDest = null,
            flags = XdrTestHelpers.uint32(0u),
            homeDomain = XdrTestHelpers.string32("stellar.org"),
            thresholds = XdrTestHelpers.thresholds(),
            signers = emptyList(),
            ext = AccountEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.Account(entry))
    }

    @Test
    fun testAccountEntryWithInflationDest() {
        val entry = AccountEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            balance = XdrTestHelpers.int64(50000000L),
            seqNum = XdrTestHelpers.sequenceNumber(99L),
            numSubEntries = XdrTestHelpers.uint32(1u),
            inflationDest = XdrTestHelpers.accountIdAlt(),
            flags = XdrTestHelpers.uint32(4u),
            homeDomain = XdrTestHelpers.string32(""),
            thresholds = XdrTestHelpers.thresholds(),
            signers = emptyList(),
            ext = AccountEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.Account(entry))
    }

    @Test
    fun testAccountEntryWithSigners() {
        val signer = SignerXdr(
            key = SignerKeyXdr.Ed25519(XdrTestHelpers.uint256Xdr()),
            weight = XdrTestHelpers.uint32(1u)
        )
        val entry = AccountEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            balance = XdrTestHelpers.int64(20000000L),
            seqNum = XdrTestHelpers.sequenceNumber(100L),
            numSubEntries = XdrTestHelpers.uint32(2u),
            inflationDest = null,
            flags = XdrTestHelpers.uint32(0u),
            homeDomain = XdrTestHelpers.string32("test.com"),
            thresholds = XdrTestHelpers.thresholds(),
            signers = listOf(signer),
            ext = AccountEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.Account(entry))
    }

    @Test
    fun testAccountEntryMultipleSigners() {
        val signer1 = SignerXdr(
            key = SignerKeyXdr.Ed25519(XdrTestHelpers.uint256Xdr()),
            weight = XdrTestHelpers.uint32(1u)
        )
        val signer2 = SignerXdr(
            key = SignerKeyXdr.PreAuthTx(XdrTestHelpers.uint256XdrAlt()),
            weight = XdrTestHelpers.uint32(2u)
        )
        val signer3 = SignerXdr(
            key = SignerKeyXdr.HashX(XdrTestHelpers.uint256Xdr()),
            weight = XdrTestHelpers.uint32(3u)
        )
        val entry = AccountEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            balance = XdrTestHelpers.int64(30000000L),
            seqNum = XdrTestHelpers.sequenceNumber(200L),
            numSubEntries = XdrTestHelpers.uint32(5u),
            inflationDest = XdrTestHelpers.accountId(),
            flags = XdrTestHelpers.uint32(7u),
            homeDomain = XdrTestHelpers.string32("multi.io"),
            thresholds = XdrTestHelpers.thresholds(),
            signers = listOf(signer1, signer2, signer3),
            ext = AccountEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.Account(entry))
    }

    // ---- TrustLineEntry tests ----

    @Test
    fun testTrustLineEntryAlphaNum4() {
        val entry = TrustLineEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            asset = TrustLineAssetXdr.AlphaNum4(XdrTestHelpers.alphaNum4()),
            balance = XdrTestHelpers.int64(5000L),
            limit = XdrTestHelpers.int64(Long.MAX_VALUE),
            flags = XdrTestHelpers.uint32(1u),
            ext = TrustLineEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.TrustLine(entry))
    }

    @Test
    fun testTrustLineEntryAlphaNum12() {
        val entry = TrustLineEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            asset = TrustLineAssetXdr.AlphaNum12(XdrTestHelpers.alphaNum12()),
            balance = XdrTestHelpers.int64(10000L),
            limit = XdrTestHelpers.int64(1000000L),
            flags = XdrTestHelpers.uint32(0u),
            ext = TrustLineEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.TrustLine(entry))
    }

    @Test
    fun testTrustLineEntryPoolShare() {
        val entry = TrustLineEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            asset = TrustLineAssetXdr.LiquidityPoolID(XdrTestHelpers.poolId()),
            balance = XdrTestHelpers.int64(1000L),
            limit = XdrTestHelpers.int64(999999L),
            flags = XdrTestHelpers.uint32(0u),
            ext = TrustLineEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.TrustLine(entry))
    }

    @Test
    fun testTrustLineAssetNative() {
        val writer = XdrWriter()
        TrustLineAssetXdr.Void.encode(writer)
        val decoded = TrustLineAssetXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(TrustLineAssetXdr.Void, decoded)
    }

    // ---- OfferEntry tests ----

    @Test
    fun testOfferEntryNativeToAlphaNum4() {
        val entry = OfferEntryXdr(
            sellerId = XdrTestHelpers.accountId(),
            offerId = XdrTestHelpers.int64(12345L),
            selling = XdrTestHelpers.assetNative(),
            buying = XdrTestHelpers.assetAlphaNum4(),
            amount = XdrTestHelpers.int64(10000L),
            price = XdrTestHelpers.price(1, 2),
            flags = XdrTestHelpers.uint32(0u),
            ext = OfferEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.Offer(entry))
    }

    @Test
    fun testOfferEntryAlphaNum12ToNative() {
        val entry = OfferEntryXdr(
            sellerId = XdrTestHelpers.accountId(),
            offerId = XdrTestHelpers.int64(99999L),
            selling = XdrTestHelpers.assetAlphaNum12(),
            buying = XdrTestHelpers.assetNative(),
            amount = XdrTestHelpers.int64(50000L),
            price = XdrTestHelpers.price(3, 1),
            flags = XdrTestHelpers.uint32(1u),
            ext = OfferEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.Offer(entry))
    }

    // ---- DataEntry tests ----

    @Test
    fun testDataEntry() {
        val entry = DataEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            dataName = XdrTestHelpers.string64("my-data-key"),
            dataValue = DataValueXdr(byteArrayOf(10, 20, 30, 40)),
            ext = DataEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.Data(entry))
    }

    @Test
    fun testDataEntryEmptyValue() {
        val entry = DataEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            dataName = XdrTestHelpers.string64("empty"),
            dataValue = DataValueXdr(byteArrayOf()),
            ext = DataEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.Data(entry))
    }

    // ---- ClaimableBalance tests ----

    @Test
    fun testClaimableBalanceEntry() {
        val claimant = ClaimantXdr.V0(
            ClaimantV0Xdr(
                destination = XdrTestHelpers.accountId(),
                predicate = ClaimPredicateXdr.Void
            )
        )
        val entry = ClaimableBalanceEntryXdr(
            balanceId = ClaimableBalanceIDXdr.V0(XdrTestHelpers.hashXdr()),
            claimants = listOf(claimant),
            asset = XdrTestHelpers.assetNative(),
            amount = XdrTestHelpers.int64(10000L),
            ext = ClaimableBalanceEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.ClaimableBalance(entry))
    }

    @Test
    fun testClaimableBalanceMultipleClaimants() {
        val claimant1 = ClaimantXdr.V0(
            ClaimantV0Xdr(
                destination = XdrTestHelpers.accountId(),
                predicate = ClaimPredicateXdr.Void
            )
        )
        val claimant2 = ClaimantXdr.V0(
            ClaimantV0Xdr(
                destination = XdrTestHelpers.accountIdAlt(),
                predicate = ClaimPredicateXdr.AbsBefore(XdrTestHelpers.int64(1700000000L))
            )
        )
        val entry = ClaimableBalanceEntryXdr(
            balanceId = ClaimableBalanceIDXdr.V0(XdrTestHelpers.hashXdr()),
            claimants = listOf(claimant1, claimant2),
            asset = XdrTestHelpers.assetAlphaNum4(),
            amount = XdrTestHelpers.int64(5000L),
            ext = ClaimableBalanceEntryExtXdr.Void
        )
        rtData(LedgerEntryDataXdr.ClaimableBalance(entry))
    }

    // ---- TTLEntry tests ----

    @Test
    fun testTTLEntry() {
        rtData(LedgerEntryDataXdr.Ttl(TTLEntryXdr(
            keyHash = XdrTestHelpers.hashXdr(),
            liveUntilLedgerSeq = XdrTestHelpers.uint32(1000000u)
        )))
    }

    // ---- ContractData tests ----

    @Test
    fun testContractDataEntry() {
        val entry = ContractDataEntryXdr(
            ext = ExtensionPointXdr.Void,
            contract = SCAddressXdr.ContractId(XdrTestHelpers.contractId()),
            key = SCValXdr.Sym(SCSymbolXdr("balance")),
            durability = ContractDataDurabilityXdr.PERSISTENT,
            `val` = SCValXdr.I128(Int128PartsXdr(Int64Xdr(0L), Uint64Xdr(1000UL)))
        )
        rtData(LedgerEntryDataXdr.ContractData(entry))
    }

    @Test
    fun testContractDataEntryTemporary() {
        val entry = ContractDataEntryXdr(
            ext = ExtensionPointXdr.Void,
            contract = SCAddressXdr.ContractId(XdrTestHelpers.contractId()),
            key = SCValXdr.U32(Uint32Xdr(0u)),
            durability = ContractDataDurabilityXdr.TEMPORARY,
            `val` = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        )
        rtData(LedgerEntryDataXdr.ContractData(entry))
    }

    // ---- ContractCode tests ----

    @Test
    fun testContractCodeEntry() {
        val entry = ContractCodeEntryXdr(
            ext = ContractCodeEntryExtXdr.Void,
            hash = XdrTestHelpers.hashXdr(),
            code = byteArrayOf(0, 97, 115, 109)
        )
        rtData(LedgerEntryDataXdr.ContractCode(entry))
    }

    // ---- Full LedgerEntry tests ----

    @Test
    fun testFullLedgerEntryAccount() {
        val accountEntry = AccountEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            balance = XdrTestHelpers.int64(10000000L),
            seqNum = XdrTestHelpers.sequenceNumber(1L),
            numSubEntries = XdrTestHelpers.uint32(0u),
            inflationDest = null,
            flags = XdrTestHelpers.uint32(0u),
            homeDomain = XdrTestHelpers.string32(""),
            thresholds = XdrTestHelpers.thresholds(),
            signers = emptyList(),
            ext = AccountEntryExtXdr.Void
        )
        rtEntry(LedgerEntryXdr(
            lastModifiedLedgerSeq = XdrTestHelpers.uint32(100u),
            data = LedgerEntryDataXdr.Account(accountEntry),
            ext = LedgerEntryExtXdr.Void
        ))
    }

    @Test
    fun testFullLedgerEntryOffer() {
        val offerEntry = OfferEntryXdr(
            sellerId = XdrTestHelpers.accountId(),
            offerId = XdrTestHelpers.int64(7777L),
            selling = XdrTestHelpers.assetNative(),
            buying = XdrTestHelpers.assetAlphaNum4(),
            amount = XdrTestHelpers.int64(50000L),
            price = XdrTestHelpers.price(5, 3),
            flags = XdrTestHelpers.uint32(0u),
            ext = OfferEntryExtXdr.Void
        )
        rtEntry(LedgerEntryXdr(
            lastModifiedLedgerSeq = XdrTestHelpers.uint32(200u),
            data = LedgerEntryDataXdr.Offer(offerEntry),
            ext = LedgerEntryExtXdr.Void
        ))
    }

    @Test
    fun testFullLedgerEntryTrustline() {
        val entry = TrustLineEntryXdr(
            accountId = XdrTestHelpers.accountId(),
            asset = TrustLineAssetXdr.AlphaNum4(XdrTestHelpers.alphaNum4()),
            balance = XdrTestHelpers.int64(1000000L),
            limit = XdrTestHelpers.int64(Long.MAX_VALUE),
            flags = XdrTestHelpers.uint32(1u),
            ext = TrustLineEntryExtXdr.Void
        )
        rtEntry(LedgerEntryXdr(
            lastModifiedLedgerSeq = XdrTestHelpers.uint32(300u),
            data = LedgerEntryDataXdr.TrustLine(entry),
            ext = LedgerEntryExtXdr.Void
        ))
    }

    @Test
    fun testFullLedgerEntryTTL() {
        rtEntry(LedgerEntryXdr(
            lastModifiedLedgerSeq = XdrTestHelpers.uint32(400u),
            data = LedgerEntryDataXdr.Ttl(TTLEntryXdr(
                keyHash = XdrTestHelpers.hashXdr(),
                liveUntilLedgerSeq = XdrTestHelpers.uint32(500000u)
            )),
            ext = LedgerEntryExtXdr.Void
        ))
    }

    // ---- SignerKey tests ----

    @Test
    fun testSignerKeyEd25519() {
        XdrTestHelpers.assertXdrRoundTrip(
            SignerKeyXdr.Ed25519(XdrTestHelpers.uint256Xdr()),
            { v, w -> v.encode(w) }, { r -> SignerKeyXdr.decode(r) })
    }

    @Test
    fun testSignerKeyPreAuthTx() {
        XdrTestHelpers.assertXdrRoundTrip(
            SignerKeyXdr.PreAuthTx(XdrTestHelpers.uint256Xdr()),
            { v, w -> v.encode(w) }, { r -> SignerKeyXdr.decode(r) })
    }

    @Test
    fun testSignerKeyHashX() {
        XdrTestHelpers.assertXdrRoundTrip(
            SignerKeyXdr.HashX(XdrTestHelpers.uint256Xdr()),
            { v, w -> v.encode(w) }, { r -> SignerKeyXdr.decode(r) })
    }

    @Test
    fun testSignerKeyEd25519SignedPayload() {
        val payload = SignerKeyEd25519SignedPayloadXdr(
            ed25519 = XdrTestHelpers.uint256Xdr(),
            payload = ByteArray(32) { (it + 50).toByte() }
        )
        XdrTestHelpers.assertXdrRoundTrip(
            SignerKeyXdr.Ed25519SignedPayload(payload),
            { v, w -> v.encode(w) }, { r -> SignerKeyXdr.decode(r) })
    }

    // ---- LedgerEntryExt tests ----

    @Test
    fun testLedgerEntryExtVoid() {
        val writer = XdrWriter()
        LedgerEntryExtXdr.Void.encode(writer)
        val decoded = LedgerEntryExtXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(LedgerEntryExtXdr.Void, decoded)
    }

    // ---- ClaimPredicate tests ----

    @Test
    fun testClaimPredicateAnd() {
        val pred = ClaimPredicateXdr.AndPredicates(listOf(
            ClaimPredicateXdr.Void,
            ClaimPredicateXdr.AbsBefore(XdrTestHelpers.int64(1700000000L))
        ))
        val writer = XdrWriter()
        pred.encode(writer)
        val decoded = ClaimPredicateXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(pred, decoded)
    }

    @Test
    fun testClaimPredicateOr() {
        val pred = ClaimPredicateXdr.OrPredicates(listOf(
            ClaimPredicateXdr.AbsBefore(XdrTestHelpers.int64(1600000000L)),
            ClaimPredicateXdr.RelBefore(XdrTestHelpers.int64(86400L))
        ))
        val writer = XdrWriter()
        pred.encode(writer)
        val decoded = ClaimPredicateXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(pred, decoded)
    }

    @Test
    fun testClaimPredicateNotWithValue() {
        val pred = ClaimPredicateXdr.NotPredicate(
            ClaimPredicateXdr.AbsBefore(XdrTestHelpers.int64(1700000000L))
        )
        val writer = XdrWriter()
        pred.encode(writer)
        val decoded = ClaimPredicateXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(pred, decoded)
    }

    @Test
    fun testClaimPredicateNotNull() {
        val pred = ClaimPredicateXdr.NotPredicate(null)
        val writer = XdrWriter()
        pred.encode(writer)
        val decoded = ClaimPredicateXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(pred, decoded)
    }

    @Test
    fun testClaimPredicateRelBefore() {
        val pred = ClaimPredicateXdr.RelBefore(XdrTestHelpers.int64(604800L))
        val writer = XdrWriter()
        pred.encode(writer)
        val decoded = ClaimPredicateXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(pred, decoded)
    }
}
