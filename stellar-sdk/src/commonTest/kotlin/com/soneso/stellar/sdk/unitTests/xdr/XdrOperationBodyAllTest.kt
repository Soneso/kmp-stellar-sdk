package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test

class XdrOperationBodyAllTest {

    private fun rt(value: OperationBodyXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> OperationBodyXdr.decode(r) })

    @Test fun testCreateAccountOp() = rt(OperationBodyXdr.CreateAccountOp(
        CreateAccountOpXdr(destination = XdrTestHelpers.accountId(), startingBalance = XdrTestHelpers.int64(10000000L))
    ))

    @Test fun testPaymentOp() = rt(OperationBodyXdr.PaymentOp(
        PaymentOpXdr(destination = XdrTestHelpers.muxedAccountEd25519(), asset = XdrTestHelpers.assetNative(), amount = XdrTestHelpers.int64(5000L))
    ))

    @Test fun testPathPaymentStrictReceiveOp() = rt(OperationBodyXdr.PathPaymentStrictReceiveOp(
        PathPaymentStrictReceiveOpXdr(sendAsset = XdrTestHelpers.assetNative(), sendMax = XdrTestHelpers.int64(1000L),
            destination = XdrTestHelpers.muxedAccountEd25519(), destAsset = XdrTestHelpers.assetAlphaNum4(),
            destAmount = XdrTestHelpers.int64(500L), path = emptyList())
    ))

    @Test fun testManageSellOfferOp() = rt(OperationBodyXdr.ManageSellOfferOp(
        ManageSellOfferOpXdr(selling = XdrTestHelpers.assetNative(), buying = XdrTestHelpers.assetAlphaNum4(),
            amount = XdrTestHelpers.int64(100L), price = XdrTestHelpers.price(), offerId = XdrTestHelpers.int64(0L))
    ))

    @Test fun testCreatePassiveSellOfferOp() = rt(OperationBodyXdr.CreatePassiveSellOfferOp(
        CreatePassiveSellOfferOpXdr(selling = XdrTestHelpers.assetNative(), buying = XdrTestHelpers.assetAlphaNum4(),
            amount = XdrTestHelpers.int64(200L), price = XdrTestHelpers.price())
    ))

    @Test fun testSetOptionsOp() = rt(OperationBodyXdr.SetOptionsOp(
        SetOptionsOpXdr(inflationDest = null, clearFlags = null, setFlags = null, masterWeight = null,
            lowThreshold = null, medThreshold = null, highThreshold = null,
            homeDomain = XdrTestHelpers.string32("example.com"), signer = null)
    ))

    @Test fun testChangeTrustOp() = rt(OperationBodyXdr.ChangeTrustOp(
        ChangeTrustOpXdr(line = ChangeTrustAssetXdr.AlphaNum4(XdrTestHelpers.alphaNum4()), limit = XdrTestHelpers.int64(Long.MAX_VALUE))
    ))

    @Test fun testAllowTrustOp() = rt(OperationBodyXdr.AllowTrustOp(
        AllowTrustOpXdr(trustor = XdrTestHelpers.accountId(), asset = AssetCodeXdr.AssetCode4(XdrTestHelpers.assetCode4Xdr()), authorize = XdrTestHelpers.uint32(1u))
    ))

    @Test fun testDestination() = rt(OperationBodyXdr.Destination(XdrTestHelpers.muxedAccountEd25519()))

    @Test fun testDestinationMuxed() = rt(OperationBodyXdr.Destination(XdrTestHelpers.muxedAccountMed25519()))

    @Test fun testInflationVoid() = rt(OperationBodyXdr.Void(OperationTypeXdr.INFLATION))

    @Test fun testEndSponsoringFutureReservesVoid() = rt(OperationBodyXdr.Void(OperationTypeXdr.END_SPONSORING_FUTURE_RESERVES))

    @Test fun testManageDataOp() = rt(OperationBodyXdr.ManageDataOp(
        ManageDataOpXdr(dataName = XdrTestHelpers.string64("my-key"), dataValue = DataValueXdr(byteArrayOf(1, 2, 3)))
    ))

    @Test fun testBumpSequenceOp() = rt(OperationBodyXdr.BumpSequenceOp(
        BumpSequenceOpXdr(bumpTo = XdrTestHelpers.sequenceNumber(999999L))
    ))

    @Test fun testManageBuyOfferOp() = rt(OperationBodyXdr.ManageBuyOfferOp(
        ManageBuyOfferOpXdr(selling = XdrTestHelpers.assetAlphaNum4(), buying = XdrTestHelpers.assetNative(),
            buyAmount = XdrTestHelpers.int64(300L), price = XdrTestHelpers.price(3, 4), offerId = XdrTestHelpers.int64(0L))
    ))

    @Test fun testPathPaymentStrictSendOp() = rt(OperationBodyXdr.PathPaymentStrictSendOp(
        PathPaymentStrictSendOpXdr(sendAsset = XdrTestHelpers.assetAlphaNum4(), sendAmount = XdrTestHelpers.int64(100L),
            destination = XdrTestHelpers.muxedAccountEd25519(), destAsset = XdrTestHelpers.assetNative(),
            destMin = XdrTestHelpers.int64(50L), path = listOf(XdrTestHelpers.assetAlphaNum12()))
    ))

    @Test fun testCreateClaimableBalanceOp() {
        val claimant = ClaimantXdr.V0(ClaimantV0Xdr(destination = XdrTestHelpers.accountId(), predicate = ClaimPredicateXdr.Void))
        rt(OperationBodyXdr.CreateClaimableBalanceOp(
            CreateClaimableBalanceOpXdr(asset = XdrTestHelpers.assetNative(), amount = XdrTestHelpers.int64(1000L), claimants = listOf(claimant))
        ))
    }

    @Test fun testClaimClaimableBalanceOp() = rt(OperationBodyXdr.ClaimClaimableBalanceOp(
        ClaimClaimableBalanceOpXdr(balanceId = ClaimableBalanceIDXdr.V0(XdrTestHelpers.hashXdr()))
    ))

    @Test fun testBeginSponsoringFutureReservesOp() = rt(OperationBodyXdr.BeginSponsoringFutureReservesOp(
        BeginSponsoringFutureReservesOpXdr(sponsoredId = XdrTestHelpers.accountId())
    ))

    @Test fun testRevokeSponsorshipOpLedgerEntry() {
        val ledgerKey = LedgerKeyXdr.Account(LedgerKeyAccountXdr(XdrTestHelpers.accountId()))
        rt(OperationBodyXdr.RevokeSponsorshipOp(RevokeSponsorshipOpXdr.LedgerKey(ledgerKey)))
    }

    @Test fun testClawbackOp() = rt(OperationBodyXdr.ClawbackOp(
        ClawbackOpXdr(asset = XdrTestHelpers.assetAlphaNum4(), from = XdrTestHelpers.muxedAccountEd25519(), amount = XdrTestHelpers.int64(500L))
    ))

    @Test fun testClawbackClaimableBalanceOp() = rt(OperationBodyXdr.ClawbackClaimableBalanceOp(
        ClawbackClaimableBalanceOpXdr(balanceId = ClaimableBalanceIDXdr.V0(XdrTestHelpers.hashXdr()))
    ))

    @Test fun testSetTrustLineFlagsOp() = rt(OperationBodyXdr.SetTrustLineFlagsOp(
        SetTrustLineFlagsOpXdr(trustor = XdrTestHelpers.accountId(), asset = XdrTestHelpers.assetAlphaNum4(),
            clearFlags = XdrTestHelpers.uint32(0u), setFlags = XdrTestHelpers.uint32(1u))
    ))

    @Test fun testLiquidityPoolDepositOp() = rt(OperationBodyXdr.LiquidityPoolDepositOp(
        LiquidityPoolDepositOpXdr(liquidityPoolId = XdrTestHelpers.poolId(), maxAmountA = XdrTestHelpers.int64(1000L),
            maxAmountB = XdrTestHelpers.int64(2000L), minPrice = XdrTestHelpers.price(1, 2), maxPrice = XdrTestHelpers.price(2, 1))
    ))

    @Test fun testLiquidityPoolWithdrawOp() = rt(OperationBodyXdr.LiquidityPoolWithdrawOp(
        LiquidityPoolWithdrawOpXdr(liquidityPoolId = XdrTestHelpers.poolId(), amount = XdrTestHelpers.int64(500L),
            minAmountA = XdrTestHelpers.int64(100L), minAmountB = XdrTestHelpers.int64(200L))
    ))

    @Test fun testInvokeHostFunctionOp() = rt(OperationBodyXdr.InvokeHostFunctionOp(
        InvokeHostFunctionOpXdr(hostFunction = HostFunctionXdr.Wasm(byteArrayOf(0, 1, 2, 3)), auth = emptyList())
    ))

    @Test fun testExtendFootprintTTLOp() = rt(OperationBodyXdr.ExtendFootprintTTLOp(
        ExtendFootprintTTLOpXdr(ext = ExtensionPointXdr.Void, extendTo = XdrTestHelpers.uint32(1000u))
    ))

    @Test fun testRestoreFootprintOp() = rt(OperationBodyXdr.RestoreFootprintOp(
        RestoreFootprintOpXdr(ext = ExtensionPointXdr.Void)
    ))
}
