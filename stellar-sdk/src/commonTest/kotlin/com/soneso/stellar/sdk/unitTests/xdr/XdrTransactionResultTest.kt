package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertEquals

class XdrTransactionResultTest {

    private fun rtResult(value: TransactionResultResultXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> TransactionResultResultXdr.decode(r) })

    private fun rtTxResult(value: TransactionResultXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> TransactionResultXdr.decode(r) })

    @Test fun testResultTooEarly() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_EARLY))
    @Test fun testResultTooLate() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_LATE))
    @Test fun testResultMissingOperation() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txMISSING_OPERATION))
    @Test fun testResultBadSeq() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_SEQ))
    @Test fun testResultBadAuth() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_AUTH))
    @Test fun testResultInsufficientBalance() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txINSUFFICIENT_BALANCE))
    @Test fun testResultNoAccount() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txNO_ACCOUNT))
    @Test fun testResultInsufficientFee() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txINSUFFICIENT_FEE))
    @Test fun testResultBadAuthExtra() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_AUTH_EXTRA))
    @Test fun testResultInternalError() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txINTERNAL_ERROR))
    @Test fun testResultNotSupported() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txNOT_SUPPORTED))
    @Test fun testResultBadSponsorship() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_SPONSORSHIP))
    @Test fun testResultBadMinSeqAgeOrGap() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txBAD_MIN_SEQ_AGE_OR_GAP))
    @Test fun testResultMalformed() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txMALFORMED))
    @Test fun testResultSorobanInvalid() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txSOROBAN_INVALID))

    @Test fun testResultSuccessEmptyOps() = rtResult(TransactionResultResultXdr.Results(emptyList()))

    @Test fun testResultSuccessWithOps() {
        val opResult = OperationResultXdr.Tr(
            OperationResultTrXdr.PaymentResult(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SUCCESS))
        )
        rtResult(TransactionResultResultXdr.Results(listOf(opResult)))
    }

    @Test fun testResultSuccessMultipleOps() {
        val op1 = OperationResultXdr.Tr(OperationResultTrXdr.CreateAccountResult(
            CreateAccountResultXdr.Void(CreateAccountResultCodeXdr.CREATE_ACCOUNT_SUCCESS)))
        val op2 = OperationResultXdr.Tr(OperationResultTrXdr.PaymentResult(
            PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SUCCESS)))
        val op3 = OperationResultXdr.Void(OperationResultCodeXdr.opBAD_AUTH)
        rtResult(TransactionResultResultXdr.Results(listOf(op1, op2, op3)))
    }

    @Test fun testFullTransactionResultSuccess() = rtTxResult(TransactionResultXdr(
        feeCharged = Int64Xdr(100L),
        result = TransactionResultResultXdr.Results(emptyList()),
        ext = TransactionResultExtXdr.Void
    ))

    @Test fun testFullTransactionResultFailed() {
        val opResult = OperationResultXdr.Tr(OperationResultTrXdr.ChangeTrustResult(
            ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_MALFORMED)))
        rtTxResult(TransactionResultXdr(
            feeCharged = Int64Xdr(200L),
            result = TransactionResultResultXdr.Results(listOf(opResult)),
            ext = TransactionResultExtXdr.Void
        ))
    }

    @Test fun testFullTransactionResultTooLate() = rtTxResult(TransactionResultXdr(
        feeCharged = Int64Xdr(50L),
        result = TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_LATE),
        ext = TransactionResultExtXdr.Void
    ))

    @Test fun testTransactionResultExtVoid() {
        val writer = XdrWriter()
        TransactionResultExtXdr.Void.encode(writer)
        val decoded = TransactionResultExtXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(TransactionResultExtXdr.Void, decoded)
    }

    @Test fun testFullTransactionResultWithInvokeHostFn() {
        val opResult = OperationResultXdr.Tr(OperationResultTrXdr.InvokeHostFunctionResult(
            InvokeHostFunctionResultXdr.Success(XdrTestHelpers.hashXdr())))
        rtTxResult(TransactionResultXdr(
            feeCharged = Int64Xdr(1000L),
            result = TransactionResultResultXdr.Results(listOf(opResult)),
            ext = TransactionResultExtXdr.Void
        ))
    }
}
