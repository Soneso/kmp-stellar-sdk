package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assertXdrRoundTrip
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.contractId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int64
import kotlin.test.Test

class XdrContractEventTest {

    @Test fun testContractEventTypeEnum() { for (e in ContractEventTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> ContractEventTypeXdr.decode(r) }) }
    @Test fun testTransactionEventStageEnum() { for (e in TransactionEventStageXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> TransactionEventStageXdr.decode(r) }) }

    private fun scVoid() = SCValXdr.Void(SCValTypeXdr.SCV_VOID)

    @Test
    fun testContractEventV0() {
        val v0 = ContractEventV0Xdr(
            topics = listOf(SCValXdr.B(true), SCValXdr.U32(uint32(42u))),
            data = SCValXdr.I64(Int64Xdr(100L))
        )
        assertXdrRoundTrip(v0, { v, w -> v.encode(w) }, { r -> ContractEventV0Xdr.decode(r) })
    }

    @Test
    fun testContractEventV0EmptyTopics() {
        assertXdrRoundTrip(ContractEventV0Xdr(emptyList(), scVoid()), { v, w -> v.encode(w) }, { r -> ContractEventV0Xdr.decode(r) })
    }

    @Test
    fun testContractEventBody() {
        val body = ContractEventBodyXdr.V0(ContractEventV0Xdr(listOf(SCValXdr.B(false)), scVoid()))
        assertXdrRoundTrip(body, { v, w -> v.encode(w) }, { r -> ContractEventBodyXdr.decode(r) })
    }

    private fun contractEvent(withContractId: Boolean = true) = ContractEventXdr(
        ext = ExtensionPointXdr.Void,
        contractId = if (withContractId) contractId() else null,
        type = ContractEventTypeXdr.CONTRACT,
        body = ContractEventBodyXdr.V0(
            ContractEventV0Xdr(
                topics = listOf(SCValXdr.Sym(SCSymbolXdr("transfer"))),
                data = SCValXdr.I128(Int128PartsXdr(int64(0L), uint64(100UL)))
            )
        )
    )

    @Test fun testContractEvent() { assertXdrRoundTrip(contractEvent(), { v, w -> v.encode(w) }, { r -> ContractEventXdr.decode(r) }) }
    @Test fun testContractEventNoContractId() { assertXdrRoundTrip(contractEvent(false), { v, w -> v.encode(w) }, { r -> ContractEventXdr.decode(r) }) }

    @Test
    fun testContractEventSystemType() {
        val event = ContractEventXdr(
            ext = ExtensionPointXdr.Void,
            contractId = null,
            type = ContractEventTypeXdr.SYSTEM,
            body = ContractEventBodyXdr.V0(ContractEventV0Xdr(emptyList(), scVoid()))
        )
        assertXdrRoundTrip(event, { v, w -> v.encode(w) }, { r -> ContractEventXdr.decode(r) })
    }

    @Test
    fun testDiagnosticEventSuccess() {
        assertXdrRoundTrip(DiagnosticEventXdr(true, contractEvent()), { v, w -> v.encode(w) }, { r -> DiagnosticEventXdr.decode(r) })
    }

    @Test
    fun testDiagnosticEventFailure() {
        assertXdrRoundTrip(DiagnosticEventXdr(false, contractEvent()), { v, w -> v.encode(w) }, { r -> DiagnosticEventXdr.decode(r) })
    }

    @Test
    fun testTransactionEvent() {
        assertXdrRoundTrip(
            TransactionEventXdr(TransactionEventStageXdr.TRANSACTION_EVENT_STAGE_AFTER_TX, contractEvent()),
            { v, w -> v.encode(w) }, { r -> TransactionEventXdr.decode(r) }
        )
    }

    @Test
    fun testTransactionEventBeforeAll() {
        assertXdrRoundTrip(
            TransactionEventXdr(TransactionEventStageXdr.TRANSACTION_EVENT_STAGE_BEFORE_ALL_TXS, contractEvent(false)),
            { v, w -> v.encode(w) }, { r -> TransactionEventXdr.decode(r) }
        )
    }

    @Test
    fun testInvokeHostFunctionSuccessPreImage() {
        assertXdrRoundTrip(
            InvokeHostFunctionSuccessPreImageXdr(SCValXdr.B(true), listOf(contractEvent())),
            { v, w -> v.encode(w) }, { r -> InvokeHostFunctionSuccessPreImageXdr.decode(r) }
        )
    }

    @Test
    fun testInvokeHostFunctionSuccessPreImageEmpty() {
        assertXdrRoundTrip(
            InvokeHostFunctionSuccessPreImageXdr(scVoid(), emptyList()),
            { v, w -> v.encode(w) }, { r -> InvokeHostFunctionSuccessPreImageXdr.decode(r) }
        )
    }
}
