package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertEquals

class XdrOperationResultTrTest {

    private fun rt(value: OperationResultTrXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> OperationResultTrXdr.decode(r) })

    private fun manageOfferSuccess() = ManageOfferSuccessResultXdr(
        offersClaimed = emptyList(), offer = ManageOfferSuccessResultOfferXdr.Void
    )

    @Test fun testCreateAccountSuccess() = rt(OperationResultTrXdr.CreateAccountResult(
        CreateAccountResultXdr.Void(CreateAccountResultCodeXdr.CREATE_ACCOUNT_SUCCESS)))
    @Test fun testCreateAccountMalformed() = rt(OperationResultTrXdr.CreateAccountResult(
        CreateAccountResultXdr.Void(CreateAccountResultCodeXdr.CREATE_ACCOUNT_MALFORMED)))
    @Test fun testCreateAccountUnderfunded() = rt(OperationResultTrXdr.CreateAccountResult(
        CreateAccountResultXdr.Void(CreateAccountResultCodeXdr.CREATE_ACCOUNT_UNDERFUNDED)))
    @Test fun testCreateAccountLowReserve() = rt(OperationResultTrXdr.CreateAccountResult(
        CreateAccountResultXdr.Void(CreateAccountResultCodeXdr.CREATE_ACCOUNT_LOW_RESERVE)))
    @Test fun testCreateAccountAlreadyExist() = rt(OperationResultTrXdr.CreateAccountResult(
        CreateAccountResultXdr.Void(CreateAccountResultCodeXdr.CREATE_ACCOUNT_ALREADY_EXIST)))

    @Test fun testPaymentSuccess() = rt(OperationResultTrXdr.PaymentResult(
        PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SUCCESS)))
    @Test fun testPaymentMalformed() = rt(OperationResultTrXdr.PaymentResult(
        PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_MALFORMED)))
    @Test fun testPaymentUnderfunded() = rt(OperationResultTrXdr.PaymentResult(
        PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_UNDERFUNDED)))
    @Test fun testPaymentNoIssuer() = rt(OperationResultTrXdr.PaymentResult(
        PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_NO_ISSUER)))

    @Test fun testPathPaymentStrictReceiveSuccess() = rt(OperationResultTrXdr.PathPaymentStrictReceiveResult(
        PathPaymentStrictReceiveResultXdr.Success(PathPaymentStrictReceiveResultSuccessXdr(
            offers = emptyList(), last = SimplePaymentResultXdr(
                destination = XdrTestHelpers.accountId(), asset = XdrTestHelpers.assetNative(), amount = XdrTestHelpers.int64(1000L))
        ))))
    @Test fun testPathPaymentStrictReceiveNoIssuer() = rt(OperationResultTrXdr.PathPaymentStrictReceiveResult(
        PathPaymentStrictReceiveResultXdr.NoIssuer(XdrTestHelpers.assetAlphaNum4())))
    @Test fun testPathPaymentStrictReceiveMalformed() = rt(OperationResultTrXdr.PathPaymentStrictReceiveResult(
        PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_MALFORMED)))

    @Test fun testManageSellOfferSuccess() = rt(OperationResultTrXdr.ManageSellOfferResult(
        ManageSellOfferResultXdr.Success(manageOfferSuccess())))
    @Test fun testManageSellOfferMalformed() = rt(OperationResultTrXdr.ManageSellOfferResult(
        ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_MALFORMED)))

    @Test fun testCreatePassiveSellOfferResult() = rt(OperationResultTrXdr.CreatePassiveSellOfferResult(
        ManageSellOfferResultXdr.Success(manageOfferSuccess())))

    @Test fun testSetOptionsSuccess() = rt(OperationResultTrXdr.SetOptionsResult(
        SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_SUCCESS)))
    @Test fun testChangeTrustSuccess() = rt(OperationResultTrXdr.ChangeTrustResult(
        ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_SUCCESS)))
    @Test fun testAllowTrustSuccess() = rt(OperationResultTrXdr.AllowTrustResult(
        AllowTrustResultXdr.Void(AllowTrustResultCodeXdr.ALLOW_TRUST_SUCCESS)))

    @Test fun testAccountMergeSuccess() = rt(OperationResultTrXdr.AccountMergeResult(
        AccountMergeResultXdr.SourceAccountBalance(XdrTestHelpers.int64(50000L))))
    @Test fun testAccountMergeMalformed() = rt(OperationResultTrXdr.AccountMergeResult(
        AccountMergeResultXdr.Void(AccountMergeResultCodeXdr.ACCOUNT_MERGE_MALFORMED)))

    @Test fun testInflationSuccess() {
        val payout = InflationPayoutXdr(destination = XdrTestHelpers.accountId(), amount = XdrTestHelpers.int64(10000L))
        rt(OperationResultTrXdr.InflationResult(InflationResultXdr.Payouts(listOf(payout))))
    }
    @Test fun testInflationNotTime() = rt(OperationResultTrXdr.InflationResult(InflationResultXdr.Void))

    @Test fun testManageDataSuccess() = rt(OperationResultTrXdr.ManageDataResult(
        ManageDataResultXdr.Void(ManageDataResultCodeXdr.MANAGE_DATA_SUCCESS)))
    @Test fun testBumpSeqSuccess() = rt(OperationResultTrXdr.BumpSeqResult(
        BumpSequenceResultXdr.Void(BumpSequenceResultCodeXdr.BUMP_SEQUENCE_SUCCESS)))

    @Test fun testManageBuyOfferSuccess() = rt(OperationResultTrXdr.ManageBuyOfferResult(
        ManageBuyOfferResultXdr.Success(manageOfferSuccess())))
    @Test fun testManageBuyOfferMalformed() = rt(OperationResultTrXdr.ManageBuyOfferResult(
        ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_MALFORMED)))

    @Test fun testPathPaymentStrictSendSuccess() = rt(OperationResultTrXdr.PathPaymentStrictSendResult(
        PathPaymentStrictSendResultXdr.Success(PathPaymentStrictSendResultSuccessXdr(
            offers = emptyList(), last = SimplePaymentResultXdr(
                destination = XdrTestHelpers.accountId(), asset = XdrTestHelpers.assetNative(), amount = XdrTestHelpers.int64(1000L))
        ))))
    @Test fun testPathPaymentStrictSendNoIssuer() = rt(OperationResultTrXdr.PathPaymentStrictSendResult(
        PathPaymentStrictSendResultXdr.NoIssuer(XdrTestHelpers.assetNative())))

    @Test fun testCreateClaimableBalanceSuccess() = rt(OperationResultTrXdr.CreateClaimableBalanceResult(
        CreateClaimableBalanceResultXdr.BalanceID(ClaimableBalanceIDXdr.V0(XdrTestHelpers.hashXdr()))))
    @Test fun testCreateClaimableBalanceMalformed() = rt(OperationResultTrXdr.CreateClaimableBalanceResult(
        CreateClaimableBalanceResultXdr.Void(CreateClaimableBalanceResultCodeXdr.CREATE_CLAIMABLE_BALANCE_MALFORMED)))

    @Test fun testClaimClaimableBalanceSuccess() = rt(OperationResultTrXdr.ClaimClaimableBalanceResult(
        ClaimClaimableBalanceResultXdr.Void(ClaimClaimableBalanceResultCodeXdr.CLAIM_CLAIMABLE_BALANCE_SUCCESS)))
    @Test fun testBeginSponsoringResult() = rt(OperationResultTrXdr.BeginSponsoringFutureReservesResult(
        BeginSponsoringFutureReservesResultXdr.Void(BeginSponsoringFutureReservesResultCodeXdr.BEGIN_SPONSORING_FUTURE_RESERVES_SUCCESS)))
    @Test fun testEndSponsoringResult() = rt(OperationResultTrXdr.EndSponsoringFutureReservesResult(
        EndSponsoringFutureReservesResultXdr.Void(EndSponsoringFutureReservesResultCodeXdr.END_SPONSORING_FUTURE_RESERVES_SUCCESS)))
    @Test fun testRevokeSponsorshipResult() = rt(OperationResultTrXdr.RevokeSponsorshipResult(
        RevokeSponsorshipResultXdr.Void(RevokeSponsorshipResultCodeXdr.REVOKE_SPONSORSHIP_SUCCESS)))
    @Test fun testClawbackResult() = rt(OperationResultTrXdr.ClawbackResult(
        ClawbackResultXdr.Void(ClawbackResultCodeXdr.CLAWBACK_SUCCESS)))
    @Test fun testClawbackClaimableBalanceResult() = rt(OperationResultTrXdr.ClawbackClaimableBalanceResult(
        ClawbackClaimableBalanceResultXdr.Void(ClawbackClaimableBalanceResultCodeXdr.CLAWBACK_CLAIMABLE_BALANCE_SUCCESS)))
    @Test fun testSetTrustLineFlagsResult() = rt(OperationResultTrXdr.SetTrustLineFlagsResult(
        SetTrustLineFlagsResultXdr.Void(SetTrustLineFlagsResultCodeXdr.SET_TRUST_LINE_FLAGS_SUCCESS)))
    @Test fun testLiquidityPoolDepositResult() = rt(OperationResultTrXdr.LiquidityPoolDepositResult(
        LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_SUCCESS)))
    @Test fun testLiquidityPoolWithdrawResult() = rt(OperationResultTrXdr.LiquidityPoolWithdrawResult(
        LiquidityPoolWithdrawResultXdr.Void(LiquidityPoolWithdrawResultCodeXdr.LIQUIDITY_POOL_WITHDRAW_SUCCESS)))
    @Test fun testInvokeHostFunctionSuccess() = rt(OperationResultTrXdr.InvokeHostFunctionResult(
        InvokeHostFunctionResultXdr.Success(XdrTestHelpers.hashXdr())))
    @Test fun testInvokeHostFunctionMalformed() = rt(OperationResultTrXdr.InvokeHostFunctionResult(
        InvokeHostFunctionResultXdr.Void(InvokeHostFunctionResultCodeXdr.INVOKE_HOST_FUNCTION_MALFORMED)))
    @Test fun testExtendFootprintTTLResult() = rt(OperationResultTrXdr.ExtendFootprintTTLResult(
        ExtendFootprintTTLResultXdr.Void(ExtendFootprintTTLResultCodeXdr.EXTEND_FOOTPRINT_TTL_SUCCESS)))
    @Test fun testRestoreFootprintResult() = rt(OperationResultTrXdr.RestoreFootprintResult(
        RestoreFootprintResultXdr.Void(RestoreFootprintResultCodeXdr.RESTORE_FOOTPRINT_SUCCESS)))

    // OperationResultXdr wrappers
    @Test fun testOperationResultInner() {
        val inner = OperationResultTrXdr.PaymentResult(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SUCCESS))
        XdrTestHelpers.assertXdrRoundTrip(
            OperationResultXdr.Tr(inner),
            { v, w -> v.encode(w) }, { r -> OperationResultXdr.decode(r) })
    }

    @Test fun testOperationResultBadAuth() {
        XdrTestHelpers.assertXdrRoundTrip(
            OperationResultXdr.Void(OperationResultCodeXdr.opBAD_AUTH),
            { v, w -> v.encode(w) }, { r -> OperationResultXdr.decode(r) })
    }

    @Test fun testOperationResultNoAccount() {
        XdrTestHelpers.assertXdrRoundTrip(
            OperationResultXdr.Void(OperationResultCodeXdr.opNO_ACCOUNT),
            { v, w -> v.encode(w) }, { r -> OperationResultXdr.decode(r) })
    }

    @Test fun testOperationResultNotSupported() {
        XdrTestHelpers.assertXdrRoundTrip(
            OperationResultXdr.Void(OperationResultCodeXdr.opNOT_SUPPORTED),
            { v, w -> v.encode(w) }, { r -> OperationResultXdr.decode(r) })
    }

    @Test fun testOperationResultTooManySubentries() {
        XdrTestHelpers.assertXdrRoundTrip(
            OperationResultXdr.Void(OperationResultCodeXdr.opTOO_MANY_SUBENTRIES),
            { v, w -> v.encode(w) }, { r -> OperationResultXdr.decode(r) })
    }

    @Test fun testOperationResultExceededWorkLimit() {
        XdrTestHelpers.assertXdrRoundTrip(
            OperationResultXdr.Void(OperationResultCodeXdr.opEXCEEDED_WORK_LIMIT),
            { v, w -> v.encode(w) }, { r -> OperationResultXdr.decode(r) })
    }
}
