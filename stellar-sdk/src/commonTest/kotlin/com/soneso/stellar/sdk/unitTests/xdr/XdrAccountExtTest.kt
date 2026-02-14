package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test

/**
 * Tests for Account entry extensions, LedgerEntry extensions,
 * TrustLine extensions, ClaimableBalance extensions, and related types.
 */
class XdrAccountExtTest {

    // ---- Account Entry Extensions ----

    @Test fun testAccountEntryExtV1Void() {
        val v = AccountEntryExtensionV1Xdr(
            liabilities = LiabilitiesXdr(Int64Xdr(100L), Int64Xdr(200L)),
            ext = AccountEntryExtensionV1ExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> AccountEntryExtensionV1Xdr.decode(r) })
    }

    @Test fun testAccountEntryExtV1WithV2() {
        val v2 = AccountEntryExtensionV2Xdr(
            numSponsored = Uint32Xdr(3u),
            numSponsoring = Uint32Xdr(2u),
            signerSponsoringIDs = listOf(
                SponsorshipDescriptorXdr(XdrTestHelpers.accountId()),
                SponsorshipDescriptorXdr(null)
            ),
            ext = AccountEntryExtensionV2ExtXdr.Void
        )
        val v1 = AccountEntryExtensionV1Xdr(
            liabilities = LiabilitiesXdr(Int64Xdr(500L), Int64Xdr(300L)),
            ext = AccountEntryExtensionV1ExtXdr.V2(v2)
        )
        XdrTestHelpers.assertXdrRoundTrip(v1, { a, w -> a.encode(w) }, { r -> AccountEntryExtensionV1Xdr.decode(r) })
    }

    @Test fun testAccountEntryExtensionV2WithV3() {
        val v3 = AccountEntryExtensionV3Xdr(
            ext = ExtensionPointXdr.Void,
            seqLedger = Uint32Xdr(12345u),
            seqTime = TimePointXdr(Uint64Xdr(1700000000UL))
        )
        val v2 = AccountEntryExtensionV2Xdr(
            numSponsored = Uint32Xdr(1u),
            numSponsoring = Uint32Xdr(0u),
            signerSponsoringIDs = emptyList(),
            ext = AccountEntryExtensionV2ExtXdr.V3(v3)
        )
        XdrTestHelpers.assertXdrRoundTrip(v2, { a, w -> a.encode(w) }, { r -> AccountEntryExtensionV2Xdr.decode(r) })
    }

    @Test fun testAccountEntryExtensionV3() {
        val v3 = AccountEntryExtensionV3Xdr(
            ext = ExtensionPointXdr.Void,
            seqLedger = Uint32Xdr(999u),
            seqTime = TimePointXdr(Uint64Xdr(1699999999UL))
        )
        XdrTestHelpers.assertXdrRoundTrip(v3, { a, w -> a.encode(w) }, { r -> AccountEntryExtensionV3Xdr.decode(r) })
    }

    @Test fun testAccountEntryExtV1Case() {
        val ext = AccountEntryExtXdr.V1(
            AccountEntryExtensionV1Xdr(
                liabilities = LiabilitiesXdr(Int64Xdr(10L), Int64Xdr(20L)),
                ext = AccountEntryExtensionV1ExtXdr.Void
            )
        )
        XdrTestHelpers.assertXdrRoundTrip(ext, { a, w -> a.encode(w) }, { r -> AccountEntryExtXdr.decode(r) })
    }

    // ---- Liabilities ----

    @Test fun testLiabilities() {
        val v = LiabilitiesXdr(Int64Xdr(1000L), Int64Xdr(2000L))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LiabilitiesXdr.decode(r) })
    }

    // ---- SponsorshipDescriptor ----

    @Test fun testSponsorshipDescriptorPresent() {
        val v = SponsorshipDescriptorXdr(XdrTestHelpers.accountId())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SponsorshipDescriptorXdr.decode(r) })
    }

    @Test fun testSponsorshipDescriptorNull() {
        val v = SponsorshipDescriptorXdr(null)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SponsorshipDescriptorXdr.decode(r) })
    }

    // ---- Account Flags enum ----

    @Test fun testAccountFlagsEnum() {
        for (flag in AccountFlagsXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(flag, { a, w -> a.encode(w) }, { r -> AccountFlagsXdr.decode(r) })
        }
    }

    // ---- LedgerEntry Extensions ----

    @Test fun testLedgerEntryExtensionV1Void() {
        val v = LedgerEntryExtensionV1Xdr(
            sponsoringId = SponsorshipDescriptorXdr(XdrTestHelpers.accountId()),
            ext = LedgerEntryExtensionV1ExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryExtensionV1Xdr.decode(r) })
    }

    @Test fun testLedgerEntryExtensionV1NullSponsor() {
        val v = LedgerEntryExtensionV1Xdr(
            sponsoringId = SponsorshipDescriptorXdr(null),
            ext = LedgerEntryExtensionV1ExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerEntryExtensionV1Xdr.decode(r) })
    }

    // ---- TrustLine Extensions ----

    @Test fun testTrustLineEntryV1Void() {
        val v = TrustLineEntryV1Xdr(
            liabilities = LiabilitiesXdr(Int64Xdr(50L), Int64Xdr(75L)),
            ext = TrustLineEntryV1ExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> TrustLineEntryV1Xdr.decode(r) })
    }

    @Test fun testTrustLineEntryV1WithV2() {
        val tlv2 = TrustLineEntryExtensionV2Xdr(
            liquidityPoolUseCount = Int32Xdr(5),
            ext = TrustLineEntryExtensionV2ExtXdr.Void
        )
        val v = TrustLineEntryV1Xdr(
            liabilities = LiabilitiesXdr(Int64Xdr(100L), Int64Xdr(200L)),
            ext = TrustLineEntryV1ExtXdr.V2(tlv2)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> TrustLineEntryV1Xdr.decode(r) })
    }

    @Test fun testTrustLineEntryExtensionV2() {
        val v = TrustLineEntryExtensionV2Xdr(
            liquidityPoolUseCount = Int32Xdr(42),
            ext = TrustLineEntryExtensionV2ExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> TrustLineEntryExtensionV2Xdr.decode(r) })
    }

    @Test fun testTrustLineEntryExtXdrV1() {
        val ext = TrustLineEntryExtXdr.V1(
            TrustLineEntryV1Xdr(
                liabilities = LiabilitiesXdr(Int64Xdr(10L), Int64Xdr(20L)),
                ext = TrustLineEntryV1ExtXdr.Void
            )
        )
        XdrTestHelpers.assertXdrRoundTrip(ext, { a, w -> a.encode(w) }, { r -> TrustLineEntryExtXdr.decode(r) })
    }

    // ---- TrustLine Flags ----

    @Test fun testTrustLineFlagsEnum() {
        for (flag in TrustLineFlagsXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(flag, { a, w -> a.encode(w) }, { r -> TrustLineFlagsXdr.decode(r) })
        }
    }

    // ---- ClaimableBalance Extensions ----

    @Test fun testClaimableBalanceEntryExtensionV1() {
        val v = ClaimableBalanceEntryExtensionV1Xdr(
            ext = ClaimableBalanceEntryExtensionV1ExtXdr.Void,
            flags = Uint32Xdr(1u)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ClaimableBalanceEntryExtensionV1Xdr.decode(r) })
    }

    @Test fun testClaimableBalanceFlagsEnum() {
        for (flag in ClaimableBalanceFlagsXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(flag, { a, w -> a.encode(w) }, { r -> ClaimableBalanceFlagsXdr.decode(r) })
        }
    }

    // ---- Offer Entry Flags ----

    @Test fun testOfferEntryFlagsEnum() {
        for (flag in OfferEntryFlagsXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(flag, { a, w -> a.encode(w) }, { r -> OfferEntryFlagsXdr.decode(r) })
        }
    }

    // ---- Threshold Indexes ----

    @Test fun testThresholdIndexesEnum() {
        for (idx in ThresholdIndexesXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(idx, { a, w -> a.encode(w) }, { r -> ThresholdIndexesXdr.decode(r) })
        }
    }

    // ---- Crypto types ----

    @Test fun testCurve25519Public() {
        val v = Curve25519PublicXdr(XdrTestHelpers.hash32())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> Curve25519PublicXdr.decode(r) })
    }

    @Test fun testCurve25519Secret() {
        val v = Curve25519SecretXdr(XdrTestHelpers.hash32())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> Curve25519SecretXdr.decode(r) })
    }

    @Test fun testHmacSha256Key() {
        val v = HmacSha256KeyXdr(XdrTestHelpers.hash32())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> HmacSha256KeyXdr.decode(r) })
    }

    @Test fun testHmacSha256Mac() {
        val v = HmacSha256MacXdr(XdrTestHelpers.hash32())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> HmacSha256MacXdr.decode(r) })
    }

    @Test fun testShortHashSeed() {
        val v = ShortHashSeedXdr(ByteArray(16) { it.toByte() })
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ShortHashSeedXdr.decode(r) })
    }

    // ---- NodeID / Value / UpgradeType ----

    @Test fun testNodeID() {
        val v = NodeIDXdr(XdrTestHelpers.publicKeyEd25519())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> NodeIDXdr.decode(r) })
    }

    @Test fun testValueXdr() {
        val v = ValueXdr(byteArrayOf(1, 2, 3, 4, 5))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ValueXdr.decode(r) })
    }

    @Test fun testUpgradeType() {
        val v = UpgradeTypeXdr(byteArrayOf(10, 20, 30))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> UpgradeTypeXdr.decode(r) })
    }

    // ---- RevokeSponsorship Signer ----

    @Test fun testRevokeSponsorshipOpSigner() {
        val v = RevokeSponsorshipOpSignerXdr(
            accountId = XdrTestHelpers.accountId(),
            signerKey = SignerKeyXdr.Ed25519(XdrTestHelpers.uint256Xdr())
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> RevokeSponsorshipOpSignerXdr.decode(r) })
    }
}
