package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XdrDeclaredMaximaTest {
    private fun intBytes(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun bytes(n: Int) = ByteArray(n) { 97 }
    private fun opaque(n: Int) = intBytes(n) + bytes(n) + ByteArray((4 - n % 4) % 4)
    private fun repeated(n: Int, item: ByteArray) = ByteArray(n * item.size) { item[it % item.size] }
    private fun encoded(write: (XdrWriter) -> Unit) = XdrWriter().also(write).toByteArray()
    private val key = Uint256Xdr(ByteArray(32))
    private val account = MuxedAccountXdr.Ed25519(key)
    private val claimant = ClaimantXdr.V0(ClaimantV0Xdr(AccountIDXdr(PublicKeyXdr.Ed25519(key)), ClaimPredicateXdr.Void))
    private val operation = OperationXdr(null, OperationBodyXdr.Void(OperationTypeXdr.INFLATION))

    private fun transaction(
        ops: List<OperationXdr> = emptyList(), memo: MemoXdr = MemoXdr.Void,
        cond: PreconditionsXdr = PreconditionsXdr.Void
    ) = TransactionXdr(account, Uint32Xdr(0u), SequenceNumberXdr(Int64Xdr(0)), cond, memo, ops, TransactionExtXdr.Void)

    private fun envelope(tx: TransactionXdr, signatures: List<DecoratedSignatureXdr> = emptyList()) =
        TransactionEnvelopeXdr.V1(TransactionV1EnvelopeXdr(tx, signatures)).toXdrBase64()
    private fun opEnvelope(body: OperationBodyXdr) = envelope(transaction(listOf(OperationXdr(null, body))))
    private fun conditions(signers: List<SignerKeyXdr>) =
        PreconditionsV2Xdr(null, null, null, DurationXdr(Uint64Xdr(0u)), Uint32Xdr(0u), signers)

    private class Vector(
        val maximum: Int, val raw: (Int) -> ByteArray, val encode: (Int) -> ByteArray,
        val decode: (ByteArray) -> Any, val roundTrip: (ByteArray) -> ByteArray, val base64: (Int) -> String
    )

    private fun <T : Any> vector(
        maximum: Int, raw: (Int) -> ByteArray, value: (Int) -> T,
        decode: (XdrReader) -> T, encode: (T, XdrWriter) -> Unit, base64: (T) -> String
    ) = Vector(maximum, raw, { n -> encoded { encode(value(n), it) } },
        { input -> decode(XdrReader(input)) },
        { input -> encoded { encode(decode(XdrReader(input)), it) } }, { base64(value(it)) })

    private val memo = vector(28, { intBytes(1) + opaque(it) }, { MemoXdr.Text("a".repeat(it)) },
        MemoXdr::decode, MemoXdr::encode, { envelope(transaction(memo = it)) })
    private val operations = vector(100, { ByteArray(56) + intBytes(it) + repeated(it, intBytes(0) + intBytes(9)) + intBytes(0) },
        { transaction(List(it) { operation }) }, TransactionXdr::decode, TransactionXdr::encode, { envelope(it) })
    private val signers = vector(2, { ByteArray(24) + intBytes(it) + ByteArray(it * 36) },
        { conditions(List(it) { SignerKeyXdr.Ed25519(key) }) }, PreconditionsV2Xdr::decode, PreconditionsV2Xdr::encode,
        { envelope(transaction(cond = PreconditionsXdr.V2(it))) })
    private val claimants = vector(10, { ByteArray(12) + intBytes(it) + ByteArray(it * 44) },
        { CreateClaimableBalanceOpXdr(AssetXdr.Void, Int64Xdr(0), List(it) { claimant }) },
        CreateClaimableBalanceOpXdr::decode, CreateClaimableBalanceOpXdr::encode,
        { opEnvelope(OperationBodyXdr.CreateClaimableBalanceOp(it)) })
    private val path = vector(5, { ByteArray(60) + intBytes(it) + ByteArray(it * 4) },
        { PathPaymentStrictReceiveOpXdr(AssetXdr.Void, Int64Xdr(0), account, AssetXdr.Void, Int64Xdr(0), List(it) { AssetXdr.Void }) },
        PathPaymentStrictReceiveOpXdr::decode, PathPaymentStrictReceiveOpXdr::encode,
        { opEnvelope(OperationBodyXdr.PathPaymentStrictReceiveOp(it)) })
    private val dataValue = vector(64, ::opaque, { DataValueXdr(bytes(it)) }, DataValueXdr::decode, DataValueXdr::encode,
        { opEnvelope(OperationBodyXdr.ManageDataOp(ManageDataOpXdr(String64Xdr("n"), it))) })
    private val signature = vector(64, ::opaque, { SignatureXdr(bytes(it)) }, SignatureXdr::decode, SignatureXdr::encode,
        { envelope(transaction(), listOf(DecoratedSignatureXdr(SignatureHintXdr(ByteArray(4)), it))) })
    private val payload = vector(64, { ByteArray(32) + opaque(it) }, { SignerKeyEd25519SignedPayloadXdr(key, bytes(it)) },
        SignerKeyEd25519SignedPayloadXdr::decode, SignerKeyEd25519SignedPayloadXdr::encode,
        { envelope(transaction(cond = PreconditionsXdr.V2(conditions(listOf(SignerKeyXdr.Ed25519SignedPayload(it)))))) })
    private val symbol = vector(32, { intBytes(15) + opaque(it) }, { SCValXdr.Sym(SCSymbolXdr("a".repeat(it))) },
        SCValXdr::decode, SCValXdr::encode, { it.toXdrBase64() })
    private val predicates = vector(2, { intBytes(1) + intBytes(it) + ByteArray(it * 4) },
        { ClaimPredicateXdr.AndPredicates(List(it) { ClaimPredicateXdr.Void }) }, ClaimPredicateXdr::decode, ClaimPredicateXdr::encode,
        { opEnvelope(OperationBodyXdr.CreateClaimableBalanceOp(CreateClaimableBalanceOpXdr(AssetXdr.Void, Int64Xdr(0),
            listOf(ClaimantXdr.V0(ClaimantV0Xdr(AccountIDXdr(PublicKeyXdr.Ed25519(key)), it)))))) })

    private fun accepts(v: Vector) {
        assertContentEquals(v.raw(v.maximum), v.encode(v.maximum))
        assertContentEquals(v.raw(v.maximum), v.roundTrip(v.raw(v.maximum)))
    }
    private fun rejects(v: Vector, action: () -> Any) {
        val error = assertFailsWith<IllegalArgumentException> { action() }
        assertTrue(error.message.orEmpty().contains("${v.maximum + 1} exceeds maximum ${v.maximum}"))
    }
    private fun rejectsDecode(v: Vector) = rejects(v) { v.decode(v.raw(v.maximum + 1)) }
    private fun rejectsEncode(v: Vector) = rejects(v) { v.encode(v.maximum + 1) }
    private fun rejectsBase64(v: Vector) = rejects(v) { v.base64(v.maximum + 1) }

    @Test fun memoMaximum() = accepts(memo)
    @Test fun memoDecodeRejects() = rejectsDecode(memo)
    @Test fun memoEncodeRejects() = rejectsEncode(memo)
    @Test fun memoBase64Rejects() = rejectsBase64(memo)
    @Test fun operationsMaximum() = accepts(operations)
    @Test fun operationsDecodeRejects() = rejectsDecode(operations)
    @Test fun operationsEncodeRejects() = rejectsEncode(operations)
    @Test fun operationsBase64Rejects() = rejectsBase64(operations)
    @Test fun signersMaximum() = accepts(signers)
    @Test fun signersDecodeRejects() = rejectsDecode(signers)
    @Test fun signersEncodeRejects() = rejectsEncode(signers)
    @Test fun signersBase64Rejects() = rejectsBase64(signers)
    @Test fun claimantsMaximum() = accepts(claimants)
    @Test fun claimantsDecodeRejects() = rejectsDecode(claimants)
    @Test fun claimantsEncodeRejects() = rejectsEncode(claimants)
    @Test fun claimantsBase64Rejects() = rejectsBase64(claimants)
    @Test fun pathMaximum() = accepts(path)
    @Test fun pathDecodeRejects() = rejectsDecode(path)
    @Test fun pathEncodeRejects() = rejectsEncode(path)
    @Test fun pathBase64Rejects() = rejectsBase64(path)
    @Test fun dataMaximum() = accepts(dataValue)
    @Test fun dataDecodeRejects() = rejectsDecode(dataValue)
    @Test fun dataEncodeRejects() = rejectsEncode(dataValue)
    @Test fun dataBase64Rejects() = rejectsBase64(dataValue)
    @Test fun signatureMaximum() = accepts(signature)
    @Test fun signatureDecodeRejects() = rejectsDecode(signature)
    @Test fun signatureEncodeRejects() = rejectsEncode(signature)
    @Test fun signatureBase64Rejects() = rejectsBase64(signature)
    @Test fun payloadMaximum() = accepts(payload)
    @Test fun payloadDecodeRejects() = rejectsDecode(payload)
    @Test fun payloadEncodeRejects() = rejectsEncode(payload)
    @Test fun payloadBase64Rejects() = rejectsBase64(payload)
    @Test fun symbolMaximum() = accepts(symbol)
    @Test fun symbolDecodeRejects() = rejectsDecode(symbol)
    @Test fun symbolEncodeRejects() = rejectsEncode(symbol)
    @Test fun symbolBase64Rejects() = rejectsBase64(symbol)
    @Test fun predicatesMaximum() = accepts(predicates)
    @Test fun predicatesDecodeRejects() = rejectsDecode(predicates)
    @Test fun predicatesEncodeRejects() = rejectsEncode(predicates)
    @Test fun predicatesBase64Rejects() = rejectsBase64(predicates)

    private fun padding(raw: ByteArray, decode: (ByteArray) -> ByteArray, reject: Boolean) {
        if (reject) {
            val bad = raw.copyOf().also { it[it.lastIndex] = 1 }
            assertFailsWith<IllegalArgumentException> { decode(bad) }
        } else {
            assertContentEquals(raw, decode(raw))
        }
    }
    private fun memoPadding(reject: Boolean) = padding(intBytes(1) + intBytes(3) + byteArrayOf(97, 98, 99, 0), memo.roundTrip, reject)
    private fun dataPadding(reject: Boolean) = padding(opaque(3), dataValue.roundTrip, reject)
    private fun signerPadding(reject: Boolean) = padding(intBytes(3) + ByteArray(32) + opaque(3),
        { input -> encoded { SignerKeyXdr.decode(XdrReader(input)).encode(it) } }, reject)
    private fun scPadding(type: Int, reject: Boolean) = padding(intBytes(type) + opaque(3),
        { input -> encoded { SCValXdr.decode(XdrReader(input)).encode(it) } }, reject)

    @Test fun memoPaddingZero() = memoPadding(false)
    @Test fun memoPaddingRejects() = memoPadding(true)
    @Test fun dataPaddingZero() = dataPadding(false)
    @Test fun dataPaddingRejects() = dataPadding(true)
    @Test fun signerPaddingZero() = signerPadding(false)
    @Test fun signerPaddingRejects() = signerPadding(true)
    @Test fun scStringPaddingZero() = scPadding(14, false)
    @Test fun scStringPaddingRejects() = scPadding(14, true)
    @Test fun scBytesPaddingZero() = scPadding(13, false)
    @Test fun scBytesPaddingRejects() = scPadding(13, true)

    @Test fun unboundedSCBytes() {
        val raw = opaque(70 * 1024)
        assertContentEquals(raw, encoded { SCBytesXdr(bytes(70 * 1024)).encode(it) })
        assertContentEquals(bytes(70 * 1024), SCBytesXdr.decode(XdrReader(raw)).value)
    }
    @Test fun unboundedUploadWasm() {
        val raw = intBytes(2) + opaque(70 * 1024)
        assertContentEquals(raw, encoded { HostFunctionXdr.Wasm(bytes(70 * 1024)).encode(it) })
        assertContentEquals(bytes(70 * 1024), (HostFunctionXdr.decode(XdrReader(raw)) as HostFunctionXdr.Wasm).value)
    }
    @Test fun unboundedContractCode() {
        val raw = ByteArray(36) + opaque(70 * 1024)
        assertContentEquals(raw, encoded { ContractCodeEntryXdr(ContractCodeEntryExtXdr.Void, HashXdr(ByteArray(32)), bytes(70 * 1024)).encode(it) })
        assertContentEquals(bytes(70 * 1024), ContractCodeEntryXdr.decode(XdrReader(raw)).code)
    }
}
