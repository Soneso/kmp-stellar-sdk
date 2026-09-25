package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class XdrTransactionResultTest {

    private fun rtResult(value: TransactionResultResultXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> TransactionResultResultXdr.decode(r) })

    private fun rtTxResult(value: TransactionResultXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> TransactionResultXdr.decode(r) })

    /** Fee charged, `txSUCCESS` discriminant and operation result count of a `TransactionResult`. */
    private fun successResultHeader(opResultCount: Int): XdrWriter = XdrWriter().apply {
        writeLong(100L)
        writeInt(TransactionResultCodeXdr.txSUCCESS.value)
        writeInt(opResultCount)
    }

    @Test fun testHandBuiltSuccessResultDecodes() {
        val writer = successResultHeader(1)
        OperationResultXdr.Void(OperationResultCodeXdr.opBAD_AUTH).encode(writer)
        TransactionResultExtXdr.Void.encode(writer)

        val decoded = TransactionResultXdr.fromXdrBase64(Base64.encode(writer.toByteArray()))
        assertEquals(
            TransactionResultXdr(
                feeCharged = Int64Xdr(100L),
                result = TransactionResultResultXdr.Results(
                    TransactionResultCodeXdr.txSUCCESS,
                    listOf(OperationResultXdr.Void(OperationResultCodeXdr.opBAD_AUTH))
                ),
                ext = TransactionResultExtXdr.Void
            ),
            decoded
        )
    }

    @Test fun testHostileOperationResultCountIsRejectedBeforeAllocation() {
        // 16 bytes announcing 2^30 operation results, none of which follow
        val bytes = successResultHeader(0x40000000).toByteArray()
        assertEquals(16, bytes.size)

        val exception = assertFailsWith<IllegalArgumentException> {
            TransactionResultXdr.fromXdrBase64(Base64.encode(bytes))
        }
        assertTrue(
            exception.message.orEmpty().contains("XDR array count 1073741824"),
            "Message should name the rejected count, got: ${exception.message}"
        )
    }

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
    @Test fun testResultFrozenKeyAccessed() = rtResult(TransactionResultResultXdr.Void(TransactionResultCodeXdr.txFROZEN_KEY_ACCESSED))

    @Test fun testResultSuccessEmptyOps() = rtResult(TransactionResultResultXdr.Results(TransactionResultCodeXdr.txSUCCESS, emptyList()))

    @Test fun testResultSuccessWithOps() {
        val opResult = OperationResultXdr.Tr(
            OperationResultTrXdr.PaymentResult(PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SUCCESS))
        )
        rtResult(TransactionResultResultXdr.Results(TransactionResultCodeXdr.txSUCCESS, listOf(opResult)))
    }

    @Test fun testResultSuccessMultipleOps() {
        val op1 = OperationResultXdr.Tr(OperationResultTrXdr.CreateAccountResult(
            CreateAccountResultXdr.Void(CreateAccountResultCodeXdr.CREATE_ACCOUNT_SUCCESS)))
        val op2 = OperationResultXdr.Tr(OperationResultTrXdr.PaymentResult(
            PaymentResultXdr.Void(PaymentResultCodeXdr.PAYMENT_SUCCESS)))
        val op3 = OperationResultXdr.Void(OperationResultCodeXdr.opBAD_AUTH)
        rtResult(TransactionResultResultXdr.Results(TransactionResultCodeXdr.txSUCCESS, listOf(op1, op2, op3)))
    }

    @Test fun testFullTransactionResultSuccess() = rtTxResult(TransactionResultXdr(
        feeCharged = Int64Xdr(100L),
        result = TransactionResultResultXdr.Results(TransactionResultCodeXdr.txSUCCESS, emptyList()),
        ext = TransactionResultExtXdr.Void
    ))

    @Test fun testFullTransactionResultFailed() {
        val opResult = OperationResultXdr.Tr(OperationResultTrXdr.ChangeTrustResult(
            ChangeTrustResultXdr.Void(ChangeTrustResultCodeXdr.CHANGE_TRUST_MALFORMED)))
        rtTxResult(TransactionResultXdr(
            feeCharged = Int64Xdr(200L),
            result = TransactionResultResultXdr.Results(TransactionResultCodeXdr.txFAILED, listOf(opResult)),
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
            result = TransactionResultResultXdr.Results(TransactionResultCodeXdr.txSUCCESS, listOf(opResult)),
            ext = TransactionResultExtXdr.Void
        ))
    }
}
