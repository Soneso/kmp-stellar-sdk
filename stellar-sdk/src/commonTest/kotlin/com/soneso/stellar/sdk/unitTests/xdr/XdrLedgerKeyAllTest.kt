package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test

/**
 * Tests for LedgerKey inner types and related types that are 0% covered.
 */
class XdrLedgerKeyAllTest {

    private fun rt(value: LedgerKeyXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> LedgerKeyXdr.decode(r) })

    @Test fun testLedgerKeyClaimableBalance() {
        val key = LedgerKeyXdr.ClaimableBalance(
            LedgerKeyClaimableBalanceXdr(ClaimableBalanceIDXdr.V0(XdrTestHelpers.hashXdr()))
        )
        rt(key)
    }

    @Test fun testLedgerKeyConfigSetting() {
        val key = LedgerKeyXdr.ConfigSetting(
            LedgerKeyConfigSettingXdr(ConfigSettingIDXdr.CONFIG_SETTING_CONTRACT_MAX_SIZE_BYTES)
        )
        rt(key)
    }

    @Test fun testLedgerKeyData() {
        val key = LedgerKeyXdr.Data(
            LedgerKeyDataXdr(XdrTestHelpers.accountId(), XdrTestHelpers.string64("my-data"))
        )
        rt(key)
    }

    @Test fun testLedgerKeyLiquidityPool() {
        val key = LedgerKeyXdr.LiquidityPool(
            LedgerKeyLiquidityPoolXdr(XdrTestHelpers.poolId())
        )
        rt(key)
    }

    @Test fun testLedgerKeyOffer() {
        val key = LedgerKeyXdr.Offer(
            LedgerKeyOfferXdr(XdrTestHelpers.accountId(), Int64Xdr(12345L))
        )
        rt(key)
    }

    @Test fun testLedgerKeyTrustLine() {
        val key = LedgerKeyXdr.TrustLine(
            LedgerKeyTrustLineXdr(XdrTestHelpers.accountId(), TrustLineAssetXdr.AlphaNum4(XdrTestHelpers.alphaNum4()))
        )
        rt(key)
    }

    @Test fun testLedgerKeyTtl() {
        val key = LedgerKeyXdr.Ttl(
            LedgerKeyTtlXdr(XdrTestHelpers.hashXdr())
        )
        rt(key)
    }

    // ---- Direct round-trips for inner key types ----

    @Test fun testLedgerKeyClaimableBalanceXdrDirect() {
        val v = LedgerKeyClaimableBalanceXdr(ClaimableBalanceIDXdr.V0(XdrTestHelpers.hashXdr()))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerKeyClaimableBalanceXdr.decode(r) })
    }

    @Test fun testLedgerKeyConfigSettingXdrDirect() {
        val v = LedgerKeyConfigSettingXdr(ConfigSettingIDXdr.CONFIG_SETTING_STATE_ARCHIVAL)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerKeyConfigSettingXdr.decode(r) })
    }

    @Test fun testLedgerKeyDataXdrDirect() {
        val v = LedgerKeyDataXdr(XdrTestHelpers.accountId(), XdrTestHelpers.string64("test-data"))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerKeyDataXdr.decode(r) })
    }

    @Test fun testLedgerKeyLiquidityPoolXdrDirect() {
        val v = LedgerKeyLiquidityPoolXdr(XdrTestHelpers.poolId())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerKeyLiquidityPoolXdr.decode(r) })
    }

    @Test fun testLedgerKeyOfferXdrDirect() {
        val v = LedgerKeyOfferXdr(XdrTestHelpers.accountId(), Int64Xdr(999L))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerKeyOfferXdr.decode(r) })
    }

    @Test fun testLedgerKeyTrustLineXdrDirect() {
        val v = LedgerKeyTrustLineXdr(XdrTestHelpers.accountId(), TrustLineAssetXdr.Void)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerKeyTrustLineXdr.decode(r) })
    }

    @Test fun testLedgerKeyTtlXdrDirect() {
        val v = LedgerKeyTtlXdr(XdrTestHelpers.hashXdr())
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerKeyTtlXdr.decode(r) })
    }
}
