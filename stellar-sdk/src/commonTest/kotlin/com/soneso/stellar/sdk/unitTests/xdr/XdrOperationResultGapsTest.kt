package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assertXdrRoundTrip
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.accountId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetNative
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetAlphaNum4
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hashXdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.muxedAccountEd25519
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.poolId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.price
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint256Xdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.contractId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.string32
import kotlin.test.Test

/**
 * Tests for operation result decode branches that were not covered yet.
 * Focus: all Void/error branches plus success branches with complex data.
 */
class XdrOperationResultGapsTest {

    // ========== Payment result - all error codes ==========
    @Test fun testPaymentUnderfunded() { rt(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_UNDERFUNDED)) }
    @Test fun testPaymentSrcNoTrust() { rt(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SRC_NO_TRUST)) }
    @Test fun testPaymentSrcNotAuthorized() { rt(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SRC_NOT_AUTHORIZED)) }
    @Test fun testPaymentNoDestination() { rt(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_NO_DESTINATION)) }
    @Test fun testPaymentNoTrust() { rt(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_NO_TRUST)) }
    @Test fun testPaymentNotAuthorized() { rt(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_NOT_AUTHORIZED)) }
    @Test fun testPaymentLineFull() { rt(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_LINE_FULL)) }
    @Test fun testPaymentNoIssuer() { rt(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_NO_ISSUER)) }

    private fun rt(v: PaymentResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> PaymentResultXdr.decode(r) })

    // ========== SetOptionsResult - all error codes ==========
    @Test fun testSetOptionsSuccess() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_SUCCESS)) }
    @Test fun testSetOptionsTooManySigners() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_TOO_MANY_SIGNERS)) }
    @Test fun testSetOptionsThreshold() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_THRESHOLD_OUT_OF_RANGE)) }
    @Test fun testSetOptionsBadSigner() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_BAD_SIGNER)) }
    @Test fun testSetOptionsInvalidInflation() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_INVALID_INFLATION)) }
    @Test fun testSetOptionsCantChange() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_CANT_CHANGE)) }
    @Test fun testSetOptionsUnknownFlag() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_UNKNOWN_FLAG)) }
    @Test fun testSetOptionsBadFlags() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_BAD_FLAGS)) }
    @Test fun testSetOptionsInvalidHomeDomain() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_INVALID_HOME_DOMAIN)) }
    @Test fun testSetOptionsAuthRevocableRequired() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_AUTH_REVOCABLE_REQUIRED)) }
    @Test fun testSetOptionsLowReserve() { rtSO(SetOptionsResultXdr.Void(SetOptionsResultCodeXdr.SET_OPTIONS_LOW_RESERVE)) }

    private fun rtSO(v: SetOptionsResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> SetOptionsResultXdr.decode(r) })

    // ========== ChangeTrustResult ==========
    @Test fun testChangeTrustSuccess() { rtCT(ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_SUCCESS)) }
    @Test fun testChangeTrustMalformed() { rtCT(ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_MALFORMED)) }
    @Test fun testChangeTrustNoIssuer() { rtCT(ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_NO_ISSUER)) }
    @Test fun testChangeTrustInvalidLimit() { rtCT(ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_INVALID_LIMIT)) }
    @Test fun testChangeTrustLowReserve() { rtCT(ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_LOW_RESERVE)) }
    @Test fun testChangeTrustSelfNotAllowed() { rtCT(ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_SELF_NOT_ALLOWED)) }
    @Test fun testChangeTrustTrustLineMissing() { rtCT(ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_TRUST_LINE_MISSING)) }
    @Test fun testChangeTrustCannotDelete() { rtCT(ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_CANNOT_DELETE)) }

    private fun rtCT(v: ChangeTrustResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> ChangeTrustResultXdr.decode(r) })

    // ========== AllowTrustResult ==========
    @Test fun testAllowTrustSuccess() { rtAT(AllowTrustResultXdr.Void(AllowTrustResultCodeXdr.ALLOW_TRUST_SUCCESS)) }
    @Test fun testAllowTrustMalformed() { rtAT(AllowTrustResultXdr.Void(AllowTrustResultCodeXdr.ALLOW_TRUST_MALFORMED)) }
    @Test fun testAllowTrustNoTrustLine() { rtAT(AllowTrustResultXdr.Void(AllowTrustResultCodeXdr.ALLOW_TRUST_NO_TRUST_LINE)) }
    @Test fun testAllowTrustNotRequired() { rtAT(AllowTrustResultXdr.Void(AllowTrustResultCodeXdr.ALLOW_TRUST_TRUST_NOT_REQUIRED)) }
    @Test fun testAllowTrustCantRevoke() { rtAT(AllowTrustResultXdr.Void(AllowTrustResultCodeXdr.ALLOW_TRUST_CANT_REVOKE)) }
    @Test fun testAllowTrustSelfNotAllowed() { rtAT(AllowTrustResultXdr.Void(AllowTrustResultCodeXdr.ALLOW_TRUST_SELF_NOT_ALLOWED)) }
    @Test fun testAllowTrustLowReserve() { rtAT(AllowTrustResultXdr.Void(AllowTrustResultCodeXdr.ALLOW_TRUST_LOW_RESERVE)) }

    private fun rtAT(v: AllowTrustResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> AllowTrustResultXdr.decode(r) })

    // ========== AccountMergeResult ==========
    @Test fun testAccountMergeSuccess() {
        assertXdrRoundTrip(
            AccountMergeResultXdr.SourceAccountBalance(int64(5000L)),
            { v, w -> v.encode(w) }, { r -> AccountMergeResultXdr.decode(r) }
        )
    }
    @Test fun testAccountMergeMalformed() { rtAM(AccountMergeResultXdr.Void(AccountMergeResultCodeXdr.ACCOUNT_MERGE_MALFORMED)) }
    @Test fun testAccountMergeNoAccount() { rtAM(AccountMergeResultXdr.Void(AccountMergeResultCodeXdr.ACCOUNT_MERGE_NO_ACCOUNT)) }
    @Test fun testAccountMergeImmutable() { rtAM(AccountMergeResultXdr.Void(AccountMergeResultCodeXdr.ACCOUNT_MERGE_IMMUTABLE_SET)) }
    @Test fun testAccountMergeHasSubEntries() { rtAM(AccountMergeResultXdr.Void(AccountMergeResultCodeXdr.ACCOUNT_MERGE_HAS_SUB_ENTRIES)) }
    @Test fun testAccountMergeSeqTooFar() { rtAM(AccountMergeResultXdr.Void(AccountMergeResultCodeXdr.ACCOUNT_MERGE_SEQNUM_TOO_FAR)) }
    @Test fun testAccountMergeDestFull() { rtAM(AccountMergeResultXdr.Void(AccountMergeResultCodeXdr.ACCOUNT_MERGE_DEST_FULL)) }

    private fun rtAM(v: AccountMergeResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> AccountMergeResultXdr.decode(r) })

    // ========== ManageSellOfferResult - error branches ==========
    @Test fun testManageSellOfferMalformed() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_MALFORMED)) }
    @Test fun testManageSellOfferSellNoTrust() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_SELL_NO_TRUST)) }
    @Test fun testManageSellOfferBuyNoTrust() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_BUY_NO_TRUST)) }
    @Test fun testManageSellOfferUnderfunded() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_UNDERFUNDED)) }
    @Test fun testManageSellOfferCrossSelf() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_CROSS_SELF)) }
    @Test fun testManageSellOfferSellNotAuth() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_SELL_NOT_AUTHORIZED)) }
    @Test fun testManageSellOfferBuyNotAuth() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_BUY_NOT_AUTHORIZED)) }
    @Test fun testManageSellOfferLineFull() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_LINE_FULL)) }
    @Test fun testManageSellOfferNotFound() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_NOT_FOUND)) }
    @Test fun testManageSellOfferLowReserve() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_LOW_RESERVE)) }
    @Test fun testManageSellOfferSellNoIssuer() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_SELL_NO_ISSUER)) }
    @Test fun testManageSellOfferBuyNoIssuer() { rtMS(ManageSellOfferResultXdr.Void(ManageSellOfferResultCodeXdr.MANAGE_SELL_OFFER_BUY_NO_ISSUER)) }

    private fun rtMS(v: ManageSellOfferResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> ManageSellOfferResultXdr.decode(r) })

    // ========== ManageBuyOfferResult - error branches ==========
    @Test fun testManageBuyOfferMalformed() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_MALFORMED)) }
    @Test fun testManageBuyOfferSellNoTrust() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_SELL_NO_TRUST)) }
    @Test fun testManageBuyOfferBuyNoTrust() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_BUY_NO_TRUST)) }
    @Test fun testManageBuyOfferUnderfunded() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_UNDERFUNDED)) }
    @Test fun testManageBuyOfferCrossSelf() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_CROSS_SELF)) }
    @Test fun testManageBuyOfferSellNotAuth() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_SELL_NOT_AUTHORIZED)) }
    @Test fun testManageBuyOfferBuyNotAuth() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_BUY_NOT_AUTHORIZED)) }
    @Test fun testManageBuyOfferLineFull() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_LINE_FULL)) }
    @Test fun testManageBuyOfferNotFound() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_NOT_FOUND)) }
    @Test fun testManageBuyOfferLowReserve() { rtMB(ManageBuyOfferResultXdr.Void(ManageBuyOfferResultCodeXdr.MANAGE_BUY_OFFER_LOW_RESERVE)) }

    private fun rtMB(v: ManageBuyOfferResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> ManageBuyOfferResultXdr.decode(r) })

    // ========== PathPaymentStrictSend result - error branches ==========
    @Test fun testPPSSMalformed() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_MALFORMED)) }
    @Test fun testPPSSUnderfunded() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_UNDERFUNDED)) }
    @Test fun testPPSSSrcNoTrust() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_SRC_NO_TRUST)) }
    @Test fun testPPSSSrcNotAuth() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_SRC_NOT_AUTHORIZED)) }
    @Test fun testPPSSNoDest() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_NO_DESTINATION)) }
    @Test fun testPPSSNoTrust() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_NO_TRUST)) }
    @Test fun testPPSSNotAuth() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_NOT_AUTHORIZED)) }
    @Test fun testPPSSLineFull() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_LINE_FULL)) }
    @Test fun testPPSSNoIssuer() {
        assertXdrRoundTrip(
            PathPaymentStrictSendResultXdr.NoIssuer(assetNative()),
            { v, w -> v.encode(w) }, { r -> PathPaymentStrictSendResultXdr.decode(r) }
        )
    }
    @Test fun testPPSSTooFewOffers() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_TOO_FEW_OFFERS)) }
    @Test fun testPPSSOfferCrossSelf() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_OFFER_CROSS_SELF)) }
    @Test fun testPPSSOverSendmax() { rtPPSS(PathPaymentStrictSendResultXdr.Void(PathPaymentStrictSendResultCodeXdr.PATH_PAYMENT_STRICT_SEND_UNDER_DESTMIN)) }

    private fun rtPPSS(v: PathPaymentStrictSendResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> PathPaymentStrictSendResultXdr.decode(r) })

    // ========== PathPaymentStrictReceive result - additional branches ==========
    @Test fun testPPSRNoIssuer() {
        assertXdrRoundTrip(
            PathPaymentStrictReceiveResultXdr.NoIssuer(assetAlphaNum4()),
            { v, w -> v.encode(w) }, { r -> PathPaymentStrictReceiveResultXdr.decode(r) }
        )
    }
    @Test fun testPPSRMalformed() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_MALFORMED)) }
    @Test fun testPPSRUnderfunded() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_UNDERFUNDED)) }
    @Test fun testPPSRSrcNoTrust() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_SRC_NO_TRUST)) }
    @Test fun testPPSRSrcNotAuth() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_SRC_NOT_AUTHORIZED)) }
    @Test fun testPPSRNoDest() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_NO_DESTINATION)) }
    @Test fun testPPSRNotAuth() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_NOT_AUTHORIZED)) }
    @Test fun testPPSRLineFull() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_LINE_FULL)) }
    @Test fun testPPSRTooFewOffers() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_TOO_FEW_OFFERS)) }
    @Test fun testPPSROfferCrossSelf() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_OFFER_CROSS_SELF)) }
    @Test fun testPPSROverSendmax() { rtPPSR(PathPaymentStrictReceiveResultXdr.Void(PathPaymentStrictReceiveResultCodeXdr.PATH_PAYMENT_STRICT_RECEIVE_OVER_SENDMAX)) }

    private fun rtPPSR(v: PathPaymentStrictReceiveResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> PathPaymentStrictReceiveResultXdr.decode(r) })

    // ========== LiquidityPool results ==========
    @Test fun testLiquidityPoolDepositMalformed() { rtLPD(LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_MALFORMED)) }
    @Test fun testLiquidityPoolDepositNoTrust() { rtLPD(LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_NO_TRUST)) }
    @Test fun testLiquidityPoolDepositNotAuthorized() { rtLPD(LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_NOT_AUTHORIZED)) }
    @Test fun testLiquidityPoolDepositUnderfunded() { rtLPD(LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_UNDERFUNDED)) }
    @Test fun testLiquidityPoolDepositLineFull() { rtLPD(LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_LINE_FULL)) }
    @Test fun testLiquidityPoolDepositBadPrice() { rtLPD(LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_BAD_PRICE)) }
    @Test fun testLiquidityPoolDepositPoolFull() { rtLPD(LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_POOL_FULL)) }
    @Test fun testLiquidityPoolDepositSuccess() { rtLPD(LiquidityPoolDepositResultXdr.Void(LiquidityPoolDepositResultCodeXdr.LIQUIDITY_POOL_DEPOSIT_SUCCESS)) }

    private fun rtLPD(v: LiquidityPoolDepositResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> LiquidityPoolDepositResultXdr.decode(r) })

    @Test fun testLiquidityPoolWithdrawMalformed() { rtLPW(LiquidityPoolWithdrawResultXdr.Void(LiquidityPoolWithdrawResultCodeXdr.LIQUIDITY_POOL_WITHDRAW_MALFORMED)) }
    @Test fun testLiquidityPoolWithdrawNoTrust() { rtLPW(LiquidityPoolWithdrawResultXdr.Void(LiquidityPoolWithdrawResultCodeXdr.LIQUIDITY_POOL_WITHDRAW_NO_TRUST)) }
    @Test fun testLiquidityPoolWithdrawUnderfunded() { rtLPW(LiquidityPoolWithdrawResultXdr.Void(LiquidityPoolWithdrawResultCodeXdr.LIQUIDITY_POOL_WITHDRAW_UNDERFUNDED)) }
    @Test fun testLiquidityPoolWithdrawLineFull() { rtLPW(LiquidityPoolWithdrawResultXdr.Void(LiquidityPoolWithdrawResultCodeXdr.LIQUIDITY_POOL_WITHDRAW_LINE_FULL)) }
    @Test fun testLiquidityPoolWithdrawUnderMinimum() { rtLPW(LiquidityPoolWithdrawResultXdr.Void(LiquidityPoolWithdrawResultCodeXdr.LIQUIDITY_POOL_WITHDRAW_UNDER_MINIMUM)) }
    @Test fun testLiquidityPoolWithdrawSuccess() { rtLPW(LiquidityPoolWithdrawResultXdr.Void(LiquidityPoolWithdrawResultCodeXdr.LIQUIDITY_POOL_WITHDRAW_SUCCESS)) }

    private fun rtLPW(v: LiquidityPoolWithdrawResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> LiquidityPoolWithdrawResultXdr.decode(r) })

    // ========== Other result types ==========
    @Test fun testSetTrustLineFlagsSuccess() { rtSTLF(SetTrustLineFlagsResultXdr.Void(SetTrustLineFlagsResultCodeXdr.SET_TRUST_LINE_FLAGS_SUCCESS)) }
    @Test fun testSetTrustLineFlagsMalformed() { rtSTLF(SetTrustLineFlagsResultXdr.Void(SetTrustLineFlagsResultCodeXdr.SET_TRUST_LINE_FLAGS_MALFORMED)) }
    @Test fun testSetTrustLineFlagsNoTrust() { rtSTLF(SetTrustLineFlagsResultXdr.Void(SetTrustLineFlagsResultCodeXdr.SET_TRUST_LINE_FLAGS_NO_TRUST_LINE)) }
    @Test fun testSetTrustLineFlagsCantRevoke() { rtSTLF(SetTrustLineFlagsResultXdr.Void(SetTrustLineFlagsResultCodeXdr.SET_TRUST_LINE_FLAGS_CANT_REVOKE)) }
    @Test fun testSetTrustLineFlagsInvalid() { rtSTLF(SetTrustLineFlagsResultXdr.Void(SetTrustLineFlagsResultCodeXdr.SET_TRUST_LINE_FLAGS_INVALID_STATE)) }
    @Test fun testSetTrustLineFlagsLowReserve() { rtSTLF(SetTrustLineFlagsResultXdr.Void(SetTrustLineFlagsResultCodeXdr.SET_TRUST_LINE_FLAGS_LOW_RESERVE)) }

    private fun rtSTLF(v: SetTrustLineFlagsResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> SetTrustLineFlagsResultXdr.decode(r) })

    @Test fun testRevokeSponsorshipSuccess() { rtRS(RevokeSponsorshipResultXdr.Void(RevokeSponsorshipResultCodeXdr.REVOKE_SPONSORSHIP_SUCCESS)) }
    @Test fun testRevokeSponsorshipNotExist() { rtRS(RevokeSponsorshipResultXdr.Void(RevokeSponsorshipResultCodeXdr.REVOKE_SPONSORSHIP_DOES_NOT_EXIST)) }
    @Test fun testRevokeSponsorshipNotSponsor() { rtRS(RevokeSponsorshipResultXdr.Void(RevokeSponsorshipResultCodeXdr.REVOKE_SPONSORSHIP_NOT_SPONSOR)) }
    @Test fun testRevokeSponsorshipLowReserve() { rtRS(RevokeSponsorshipResultXdr.Void(RevokeSponsorshipResultCodeXdr.REVOKE_SPONSORSHIP_LOW_RESERVE)) }
    @Test fun testRevokeSponsorshipOnlyTransferable() { rtRS(RevokeSponsorshipResultXdr.Void(RevokeSponsorshipResultCodeXdr.REVOKE_SPONSORSHIP_ONLY_TRANSFERABLE)) }
    @Test fun testRevokeSponsorshipMalformed() { rtRS(RevokeSponsorshipResultXdr.Void(RevokeSponsorshipResultCodeXdr.REVOKE_SPONSORSHIP_MALFORMED)) }

    private fun rtRS(v: RevokeSponsorshipResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> RevokeSponsorshipResultXdr.decode(r) })

    @Test fun testClaimClaimableBalanceSuccess() { rtCCB(ClaimClaimableBalanceResultXdr.Void(ClaimClaimableBalanceResultCodeXdr.CLAIM_CLAIMABLE_BALANCE_SUCCESS)) }
    @Test fun testClaimClaimableBalanceDoesNotExist() { rtCCB(ClaimClaimableBalanceResultXdr.Void(ClaimClaimableBalanceResultCodeXdr.CLAIM_CLAIMABLE_BALANCE_DOES_NOT_EXIST)) }
    @Test fun testClaimClaimableBalanceCannotClaim() { rtCCB(ClaimClaimableBalanceResultXdr.Void(ClaimClaimableBalanceResultCodeXdr.CLAIM_CLAIMABLE_BALANCE_CANNOT_CLAIM)) }
    @Test fun testClaimClaimableBalanceLineFull() { rtCCB(ClaimClaimableBalanceResultXdr.Void(ClaimClaimableBalanceResultCodeXdr.CLAIM_CLAIMABLE_BALANCE_LINE_FULL)) }
    @Test fun testClaimClaimableBalanceNoTrust() { rtCCB(ClaimClaimableBalanceResultXdr.Void(ClaimClaimableBalanceResultCodeXdr.CLAIM_CLAIMABLE_BALANCE_NO_TRUST)) }
    @Test fun testClaimClaimableBalanceNotAuthorized() { rtCCB(ClaimClaimableBalanceResultXdr.Void(ClaimClaimableBalanceResultCodeXdr.CLAIM_CLAIMABLE_BALANCE_NOT_AUTHORIZED)) }

    private fun rtCCB(v: ClaimClaimableBalanceResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> ClaimClaimableBalanceResultXdr.decode(r) })

    @Test fun testClawbackSuccess() { rtCB(ClawbackResultXdr.Void(ClawbackResultCodeXdr.CLAWBACK_SUCCESS)) }
    @Test fun testClawbackMalformed() { rtCB(ClawbackResultXdr.Void(ClawbackResultCodeXdr.CLAWBACK_MALFORMED)) }
    @Test fun testClawbackNotClawbackEnabled() { rtCB(ClawbackResultXdr.Void(ClawbackResultCodeXdr.CLAWBACK_NOT_CLAWBACK_ENABLED)) }
    @Test fun testClawbackNoTrust() { rtCB(ClawbackResultXdr.Void(ClawbackResultCodeXdr.CLAWBACK_NO_TRUST)) }
    @Test fun testClawbackUnderfunded() { rtCB(ClawbackResultXdr.Void(ClawbackResultCodeXdr.CLAWBACK_UNDERFUNDED)) }

    private fun rtCB(v: ClawbackResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> ClawbackResultXdr.decode(r) })

    @Test fun testClawbackCBSuccess() { rtCCBal(ClawbackClaimableBalanceResultXdr.Void(ClawbackClaimableBalanceResultCodeXdr.CLAWBACK_CLAIMABLE_BALANCE_SUCCESS)) }
    @Test fun testClawbackCBDoesNotExist() { rtCCBal(ClawbackClaimableBalanceResultXdr.Void(ClawbackClaimableBalanceResultCodeXdr.CLAWBACK_CLAIMABLE_BALANCE_DOES_NOT_EXIST)) }
    @Test fun testClawbackCBNotIssuer() { rtCCBal(ClawbackClaimableBalanceResultXdr.Void(ClawbackClaimableBalanceResultCodeXdr.CLAWBACK_CLAIMABLE_BALANCE_NOT_ISSUER)) }
    @Test fun testClawbackCBNotEnabled() { rtCCBal(ClawbackClaimableBalanceResultXdr.Void(ClawbackClaimableBalanceResultCodeXdr.CLAWBACK_CLAIMABLE_BALANCE_NOT_CLAWBACK_ENABLED)) }

    private fun rtCCBal(v: ClawbackClaimableBalanceResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> ClawbackClaimableBalanceResultXdr.decode(r) })

    @Test fun testBeginSponsoringSuccess() { rtBS(BeginSponsoringFutureReservesResultXdr.Void(BeginSponsoringFutureReservesResultCodeXdr.BEGIN_SPONSORING_FUTURE_RESERVES_SUCCESS)) }
    @Test fun testBeginSponsoringMalformed() { rtBS(BeginSponsoringFutureReservesResultXdr.Void(BeginSponsoringFutureReservesResultCodeXdr.BEGIN_SPONSORING_FUTURE_RESERVES_MALFORMED)) }
    @Test fun testBeginSponsoringAlready() { rtBS(BeginSponsoringFutureReservesResultXdr.Void(BeginSponsoringFutureReservesResultCodeXdr.BEGIN_SPONSORING_FUTURE_RESERVES_ALREADY_SPONSORED)) }
    @Test fun testBeginSponsoringRecursive() { rtBS(BeginSponsoringFutureReservesResultXdr.Void(BeginSponsoringFutureReservesResultCodeXdr.BEGIN_SPONSORING_FUTURE_RESERVES_RECURSIVE)) }

    private fun rtBS(v: BeginSponsoringFutureReservesResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> BeginSponsoringFutureReservesResultXdr.decode(r) })

    @Test fun testManageDataSuccess() { rtMD(ManageDataResultXdr.Void(ManageDataResultCodeXdr.MANAGE_DATA_SUCCESS)) }
    @Test fun testManageDataNotSupported() { rtMD(ManageDataResultXdr.Void(ManageDataResultCodeXdr.MANAGE_DATA_NOT_SUPPORTED_YET)) }
    @Test fun testManageDataNameNotFound() { rtMD(ManageDataResultXdr.Void(ManageDataResultCodeXdr.MANAGE_DATA_NAME_NOT_FOUND)) }
    @Test fun testManageDataLowReserve() { rtMD(ManageDataResultXdr.Void(ManageDataResultCodeXdr.MANAGE_DATA_LOW_RESERVE)) }
    @Test fun testManageDataInvalidName() { rtMD(ManageDataResultXdr.Void(ManageDataResultCodeXdr.MANAGE_DATA_INVALID_NAME)) }

    private fun rtMD(v: ManageDataResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> ManageDataResultXdr.decode(r) })

    @Test fun testExtendFootprintTTLSuccess() { rtEF(ExtendFootprintTTLResultXdr.Void(ExtendFootprintTTLResultCodeXdr.EXTEND_FOOTPRINT_TTL_SUCCESS)) }
    @Test fun testExtendFootprintTTLMalformed() { rtEF(ExtendFootprintTTLResultXdr.Void(ExtendFootprintTTLResultCodeXdr.EXTEND_FOOTPRINT_TTL_MALFORMED)) }
    @Test fun testExtendFootprintTTLResourceLimit() { rtEF(ExtendFootprintTTLResultXdr.Void(ExtendFootprintTTLResultCodeXdr.EXTEND_FOOTPRINT_TTL_RESOURCE_LIMIT_EXCEEDED)) }
    @Test fun testExtendFootprintTTLInsufficient() { rtEF(ExtendFootprintTTLResultXdr.Void(ExtendFootprintTTLResultCodeXdr.EXTEND_FOOTPRINT_TTL_INSUFFICIENT_REFUNDABLE_FEE)) }

    private fun rtEF(v: ExtendFootprintTTLResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> ExtendFootprintTTLResultXdr.decode(r) })

    @Test fun testRestoreFootprintSuccess() { rtRF(RestoreFootprintResultXdr.Void(RestoreFootprintResultCodeXdr.RESTORE_FOOTPRINT_SUCCESS)) }
    @Test fun testRestoreFootprintMalformed() { rtRF(RestoreFootprintResultXdr.Void(RestoreFootprintResultCodeXdr.RESTORE_FOOTPRINT_MALFORMED)) }
    @Test fun testRestoreFootprintResourceLimit() { rtRF(RestoreFootprintResultXdr.Void(RestoreFootprintResultCodeXdr.RESTORE_FOOTPRINT_RESOURCE_LIMIT_EXCEEDED)) }
    @Test fun testRestoreFootprintInsufficient() { rtRF(RestoreFootprintResultXdr.Void(RestoreFootprintResultCodeXdr.RESTORE_FOOTPRINT_INSUFFICIENT_REFUNDABLE_FEE)) }

    private fun rtRF(v: RestoreFootprintResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> RestoreFootprintResultXdr.decode(r) })

    // ========== InnerTransactionResultResultXdr - more Void branches ==========
    @Test fun testInnerTxMissingOp() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txMISSING_OPERATION)) }
    @Test fun testInnerTxBadAuth() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_AUTH)) }
    @Test fun testInnerTxInsufficient() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txINSUFFICIENT_BALANCE)) }
    @Test fun testInnerTxNoAccount() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txNO_ACCOUNT)) }
    @Test fun testInnerTxBadAuthExtra() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_AUTH_EXTRA)) }
    @Test fun testInnerTxInternalError() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txINTERNAL_ERROR)) }
    @Test fun testInnerTxNotSupported() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txNOT_SUPPORTED)) }
    @Test fun testInnerTxBadSponsorship() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_SPONSORSHIP)) }
    @Test fun testInnerTxBadMinSeq() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_MIN_SEQ_AGE_OR_GAP)) }
    @Test fun testInnerTxMalformed() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txMALFORMED)) }
    @Test fun testInnerTxSorobanInvalid() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txSOROBAN_INVALID)) }
    @Test fun testInnerTxInsufficientFee() { rtITR(InnerTransactionResultResultXdr.Void(TransactionResultCodeXdr.txINSUFFICIENT_FEE)) }

    private fun rtITR(v: InnerTransactionResultResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> InnerTransactionResultResultXdr.decode(r) })

    // ========== SCError - all variants ==========
    @Test fun testSCErrorContract() { rtSCE(SCErrorXdr.ContractCode(uint32(1u))) }
    @Test fun testSCErrorWasm() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_ARITH_DOMAIN)) }
    @Test fun testSCErrorContext() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_INTERNAL_ERROR)) }
    @Test fun testSCErrorStorage() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_EXCEEDED_LIMIT)) }
    @Test fun testSCErrorObject() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_INVALID_ACTION)) }
    @Test fun testSCErrorCrypto() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_MISSING_VALUE)) }
    @Test fun testSCErrorEvents() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_UNEXPECTED_TYPE)) }
    @Test fun testSCErrorBudget() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_UNEXPECTED_SIZE)) }
    @Test fun testSCErrorValue() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_INVALID_INPUT)) }
    @Test fun testSCErrorAuth() { rtSCE(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_EXISTING_VALUE)) }

    private fun rtSCE(v: SCErrorXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> SCErrorXdr.decode(r) })

    // ========== ChangeTrustAsset ==========
    @Test fun testChangeTrustAssetNative() {
        assertXdrRoundTrip(ChangeTrustAssetXdr.Void, { v, w -> v.encode(w) }, { r -> ChangeTrustAssetXdr.decode(r) })
    }
    @Test fun testChangeTrustAssetPoolShare() {
        val params = LiquidityPoolParametersXdr.ConstantProduct(
            LiquidityPoolConstantProductParametersXdr(assetNative(), assetAlphaNum4(), int32(30))
        )
        assertXdrRoundTrip(ChangeTrustAssetXdr.LiquidityPool(params), { v, w -> v.encode(w) }, { r -> ChangeTrustAssetXdr.decode(r) })
    }

    // ========== TransactionResultMetaV1 ==========
    @Test
    fun testTransactionResultMetaV1() {
        val meta = TransactionResultMetaV1Xdr(
            ext = ExtensionPointXdr.Void,
            result = TransactionResultPairXdr(
                transactionHash = hashXdr(),
                result = TransactionResultXdr(
                    feeCharged = int64(100L),
                    result = TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_LATE),
                    ext = TransactionResultExtXdr.Void
                )
            ),
            feeProcessing = LedgerEntryChangesXdr(emptyList()),
            txApplyProcessing = TransactionMetaXdr.Operations(emptyList()),
            postTxApplyFeeProcessing = LedgerEntryChangesXdr(emptyList())
        )
        assertXdrRoundTrip(meta, { v, w -> v.encode(w) }, { r -> TransactionResultMetaV1Xdr.decode(r) })
    }

    // ========== XdrPrimitiveExtensions (writeFixedOpaque / readFixedOpaque / writeVariableOpaque / readVariableOpaque) ==========
    @Test
    fun testXdrPrimitiveExtensionsViaShortHashSeed() {
        val seed = ShortHashSeedXdr(ByteArray(16) { (it * 3).toByte() })
        assertXdrRoundTrip(seed, { v, w -> v.encode(w) }, { r -> ShortHashSeedXdr.decode(r) })
    }

    // ========== HostFunction ==========
    @Test
    fun testHostFunctionInvokeContract() {
        val hf = HostFunctionXdr.InvokeContract(
            InvokeContractArgsXdr(
                SCAddressXdr.ContractId(contractId()),
                SCSymbolXdr("fn"),
                emptyList()
            )
        )
        assertXdrRoundTrip(hf, { v, w -> v.encode(w) }, { r -> HostFunctionXdr.decode(r) })
    }

    @Test
    fun testHostFunctionCreateContract() {
        val hf = HostFunctionXdr.CreateContract(
            CreateContractArgsXdr(
                ContractIDPreimageXdr.FromAsset(assetNative()),
                ContractExecutableXdr.Void
            )
        )
        assertXdrRoundTrip(hf, { v, w -> v.encode(w) }, { r -> HostFunctionXdr.decode(r) })
    }

    @Test
    fun testHostFunctionUploadWasm() {
        val hf = HostFunctionXdr.Wasm(ByteArray(10) { it.toByte() })
        assertXdrRoundTrip(hf, { v, w -> v.encode(w) }, { r -> HostFunctionXdr.decode(r) })
    }

    @Test
    fun testHostFunctionCreateContractV2() {
        val hf = HostFunctionXdr.CreateContractV2(
            CreateContractArgsV2Xdr(
                ContractIDPreimageXdr.FromAsset(assetNative()),
                ContractExecutableXdr.Void,
                emptyList()
            )
        )
        assertXdrRoundTrip(hf, { v, w -> v.encode(w) }, { r -> HostFunctionXdr.decode(r) })
    }

    // ========== InvokeHostFunctionResult ==========
    @Test
    fun testInvokeHostFunctionResultSuccess() {
        assertXdrRoundTrip(
            InvokeHostFunctionResultXdr.Success(hashXdr()),
            { v, w -> v.encode(w) }, { r -> InvokeHostFunctionResultXdr.decode(r) }
        )
    }
    @Test fun testInvokeHostFunctionMalformed() { rtIHF(InvokeHostFunctionResultXdr.Void(InvokeHostFunctionResultCodeXdr.INVOKE_HOST_FUNCTION_MALFORMED)) }
    @Test fun testInvokeHostFunctionTrapped() { rtIHF(InvokeHostFunctionResultXdr.Void(InvokeHostFunctionResultCodeXdr.INVOKE_HOST_FUNCTION_TRAPPED)) }
    @Test fun testInvokeHostFunctionResourceLimit() { rtIHF(InvokeHostFunctionResultXdr.Void(InvokeHostFunctionResultCodeXdr.INVOKE_HOST_FUNCTION_RESOURCE_LIMIT_EXCEEDED)) }
    @Test fun testInvokeHostFunctionEntryArchived() { rtIHF(InvokeHostFunctionResultXdr.Void(InvokeHostFunctionResultCodeXdr.INVOKE_HOST_FUNCTION_ENTRY_ARCHIVED)) }
    @Test fun testInvokeHostFunctionInsufficient() { rtIHF(InvokeHostFunctionResultXdr.Void(InvokeHostFunctionResultCodeXdr.INVOKE_HOST_FUNCTION_INSUFFICIENT_REFUNDABLE_FEE)) }

    private fun rtIHF(v: InvokeHostFunctionResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> InvokeHostFunctionResultXdr.decode(r) })

    // ========== CreateClaimableBalanceResult ==========
    @Test
    fun testCreateClaimableBalanceSuccess() {
        assertXdrRoundTrip(
            CreateClaimableBalanceResultXdr.BalanceID(ClaimableBalanceIDXdr.V0(hashXdr())),
            { v, w -> v.encode(w) }, { r -> CreateClaimableBalanceResultXdr.decode(r) }
        )
    }
    @Test fun testCreateClaimableBalanceMalformed() { rtCreateCB(CreateClaimableBalanceResultXdr.Void(CreateClaimableBalanceResultCodeXdr.CREATE_CLAIMABLE_BALANCE_MALFORMED)) }
    @Test fun testCreateClaimableBalanceLowReserve() { rtCreateCB(CreateClaimableBalanceResultXdr.Void(CreateClaimableBalanceResultCodeXdr.CREATE_CLAIMABLE_BALANCE_LOW_RESERVE)) }
    @Test fun testCreateClaimableBalanceNoTrust() { rtCreateCB(CreateClaimableBalanceResultXdr.Void(CreateClaimableBalanceResultCodeXdr.CREATE_CLAIMABLE_BALANCE_NO_TRUST)) }
    @Test fun testCreateClaimableBalanceNotAuth() { rtCreateCB(CreateClaimableBalanceResultXdr.Void(CreateClaimableBalanceResultCodeXdr.CREATE_CLAIMABLE_BALANCE_NOT_AUTHORIZED)) }
    @Test fun testCreateClaimableBalanceUnderfunded() { rtCreateCB(CreateClaimableBalanceResultXdr.Void(CreateClaimableBalanceResultCodeXdr.CREATE_CLAIMABLE_BALANCE_UNDERFUNDED)) }

    private fun rtCreateCB(v: CreateClaimableBalanceResultXdr) = assertXdrRoundTrip(v, { v2, w -> v2.encode(w) }, { r -> CreateClaimableBalanceResultXdr.decode(r) })

    // ========== SetOptionsOp (large struct - exercise encode/decode) ==========
    @Test
    fun testSetOptionsOpFull() {
        val op = SetOptionsOpXdr(
            inflationDest = accountId(),
            clearFlags = uint32(1u),
            setFlags = uint32(2u),
            masterWeight = uint32(10u),
            lowThreshold = uint32(1u),
            medThreshold = uint32(2u),
            highThreshold = uint32(3u),
            homeDomain = string32("example.com"),
            signer = SignerXdr(SignerKeyXdr.Ed25519(uint256Xdr()), uint32(1u))
        )
        assertXdrRoundTrip(op, { v, w -> v.encode(w) }, { r -> SetOptionsOpXdr.decode(r) })
    }

    @Test
    fun testSetOptionsOpAllNull() {
        val op = SetOptionsOpXdr(
            inflationDest = null,
            clearFlags = null,
            setFlags = null,
            masterWeight = null,
            lowThreshold = null,
            medThreshold = null,
            highThreshold = null,
            homeDomain = null,
            signer = null
        )
        assertXdrRoundTrip(op, { v, w -> v.encode(w) }, { r -> SetOptionsOpXdr.decode(r) })
    }
}
