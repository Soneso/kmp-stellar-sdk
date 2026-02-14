package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.accountId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hashXdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.muxedAccountEd25519
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.sequenceNumber
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint256Xdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.thresholds
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.string32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.contractId
import kotlin.test.Test
import kotlin.test.assertEquals

class XdrExtensionsTest {

    private fun simpleLedgerKey() = LedgerKeyXdr.Account(LedgerKeyAccountXdr(accountId()))

    private fun simpleTransactionEnvelope(): TransactionEnvelopeXdr {
        val tx = TransactionXdr(
            sourceAccount = muxedAccountEd25519(),
            fee = uint32(100u),
            seqNum = sequenceNumber(1L),
            cond = PreconditionsXdr.Void,
            memo = MemoXdr.Void,
            operations = listOf(OperationXdr(null, OperationBodyXdr.PaymentOp(
                PaymentOpXdr(muxedAccountEd25519(), AssetXdr.Void, int64(100L))
            ))),
            ext = TransactionExtXdr.Void
        )
        return TransactionEnvelopeXdr.V1(TransactionV1EnvelopeXdr(
            tx = tx,
            signatures = listOf(DecoratedSignatureXdr(
                SignatureHintXdr(byteArrayOf(1, 2, 3, 4)),
                SignatureXdr(ByteArray(32) { it.toByte() })
            ))
        ))
    }

    private fun simpleLedgerEntryData(): LedgerEntryDataXdr = LedgerEntryDataXdr.Account(
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
    )

    private fun simpleSorobanTransactionData() = SorobanTransactionDataXdr(
        ext = SorobanTransactionDataExtXdr.Void,
        resources = SorobanResourcesXdr(
            footprint = LedgerFootprintXdr(readOnly = emptyList(), readWrite = emptyList()),
            instructions = uint32(100u),
            diskReadBytes = uint32(200u),
            writeBytes = uint32(300u),
        ),
        resourceFee = int64(1000L)
    )

    private fun simpleSorobanAuthEntry() = SorobanAuthorizationEntryXdr(
        credentials = SorobanCredentialsXdr.Void,
        rootInvocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    SCAddressXdr.ContractId(contractId()),
                    SCSymbolXdr("test"),
                    emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    )

    private fun simpleTransactionResult() = TransactionResultXdr(
        feeCharged = int64(100L),
        result = TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_LATE),
        ext = TransactionResultExtXdr.Void
    )

    private fun simpleLedgerEntry() = LedgerEntryXdr(
        lastModifiedLedgerSeq = uint32(100u),
        data = simpleLedgerEntryData(),
        ext = LedgerEntryExtXdr.Void
    )

    private fun contractEvent() = ContractEventXdr(
        ext = ExtensionPointXdr.Void,
        contractId = contractId(),
        type = ContractEventTypeXdr.CONTRACT,
        body = ContractEventBodyXdr.V0(ContractEventV0Xdr(emptyList(), SCValXdr.Void(SCValTypeXdr.SCV_VOID)))
    )

    private fun transactionEvent() = TransactionEventXdr(
        stage = TransactionEventStageXdr.TRANSACTION_EVENT_STAGE_AFTER_TX,
        event = contractEvent()
    )

    // ========== toXdrBase64 / fromXdrBase64 ==========

    @Test
    fun testLedgerKeyBase64() {
        val key = simpleLedgerKey()
        val b64 = key.toXdrBase64()
        val decoded = LedgerKeyXdr.fromXdrBase64(b64)
        val b64Again = decoded.toXdrBase64()
        assertEquals(b64, b64Again)
    }

    @Test
    fun testTransactionEnvelopeBase64() {
        val env = simpleTransactionEnvelope()
        val b64 = env.toXdrBase64()
        val decoded = TransactionEnvelopeXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testLedgerEntryDataBase64() {
        val data = simpleLedgerEntryData()
        val b64 = data.toXdrBase64()
        val decoded = LedgerEntryDataXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testSorobanTransactionDataBase64() {
        val data = simpleSorobanTransactionData()
        val b64 = data.toXdrBase64()
        val decoded = SorobanTransactionDataXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testSorobanAuthorizationEntryBase64() {
        val entry = simpleSorobanAuthEntry()
        val b64 = entry.toXdrBase64()
        val decoded = SorobanAuthorizationEntryXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testTransactionResultBase64() {
        val result = simpleTransactionResult()
        val b64 = result.toXdrBase64()
        val decoded = TransactionResultXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testTransactionMetaBase64() {
        val meta = TransactionMetaXdr.Operations(emptyList())
        val b64 = meta.toXdrBase64()
        val decoded = TransactionMetaXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testDiagnosticEventBase64() {
        val event = DiagnosticEventXdr(true, contractEvent())
        val b64 = event.toXdrBase64()
        val decoded = DiagnosticEventXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testContractEventBase64() {
        val event = contractEvent()
        val b64 = event.toXdrBase64()
        val decoded = ContractEventXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testLedgerEntryBase64() {
        val entry = simpleLedgerEntry()
        val b64 = entry.toXdrBase64()
        val decoded = LedgerEntryXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testSCValBase64() {
        val v = SCValXdr.U32(uint32(42u))
        val b64 = v.toXdrBase64()
        val decoded = SCValXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }

    @Test
    fun testTransactionEventBase64() {
        val event = transactionEvent()
        val b64 = event.toXdrBase64()
        val decoded = TransactionEventXdr.fromXdrBase64(b64)
        assertEquals(b64, decoded.toXdrBase64())
    }
}
