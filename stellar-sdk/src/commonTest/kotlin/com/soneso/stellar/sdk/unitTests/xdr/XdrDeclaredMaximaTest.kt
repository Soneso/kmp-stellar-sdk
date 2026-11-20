package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XdrDeclaredMaximaTest {
    // XDR int: four bytes, big-endian.
    private fun intBytes(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun bytes(n: Int) = ByteArray(n) { 'a'.code.toByte() }
    // XDR opaque values are zero-padded to a four-byte word boundary.
    private fun opaque(n: Int) = intBytes(n) + bytes(n) + ByteArray((4 - n % 4) % 4)
    private fun repeated(n: Int, item: ByteArray) = ByteArray(n * item.size) { item[it % item.size] }
    private fun encoded(write: (XdrWriter) -> Unit) = XdrWriter().also(write).toByteArray()
    private val key = Uint256Xdr(ByteArray(32))
    private val account = MuxedAccountXdr.Ed25519(key)
    private val claimant = ClaimantXdr.V0(ClaimantV0Xdr(AccountIDXdr(PublicKeyXdr.Ed25519(key)), ClaimPredicateXdr.Void))
    private val operation = OperationXdr(null, OperationBodyXdr.Void(OperationTypeXdr.INFLATION))
    private val decoratedSignature = DecoratedSignatureXdr(SignatureHintXdr(ByteArray(4)), SignatureXdr(ByteArray(0)))

    private fun transaction(
        ops: List<OperationXdr> = emptyList(), memo: MemoXdr = MemoXdr.Void,
        cond: PreconditionsXdr = PreconditionsXdr.Void
    ) = TransactionXdr(account, Uint32Xdr(0u), SequenceNumberXdr(Int64Xdr(0)), cond, memo, ops, TransactionExtXdr.Void)

    private fun transactionV0(ops: List<OperationXdr> = emptyList()) =
        TransactionV0Xdr(key, Uint32Xdr(0u), SequenceNumberXdr(Int64Xdr(0)), null,
            MemoXdr.Void, ops, TransactionV0ExtXdr.Void)

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

    // Stellar-transaction.x: string text<28>;
    private val memo = vector(28, { intBytes(MemoTypeXdr.MEMO_TEXT.value) + opaque(it) }, { MemoXdr.Text("a".repeat(it)) },
        MemoXdr::decode, MemoXdr::encode, { envelope(transaction(memo = it)) })
    // Before operations: sourceAccount (36), fee (4), seqNum (8), cond (4), memo (4).
    // Each operation has an absent optional sourceAccount (zero); Transaction.ext uses switch (int v), case 0.
    private val operations = vector(MAX_OPS_PER_TX,
        { ByteArray(56) + intBytes(it) + repeated(it, intBytes(0) + intBytes(OperationTypeXdr.INFLATION.value)) + intBytes(0) },
        { transaction(List(it) { operation }) }, TransactionXdr::decode, TransactionXdr::encode, { envelope(it) })
    // Stellar-transaction.x: SignerKey extraSigners<2>;
    // Prefix: three optional-presence words (12), minSeqAge (8), minSeqLedgerGap (4); each signer is tag (4) + key (32).
    private val signers = vector(2, { ByteArray(24) + intBytes(it) + ByteArray(it * 36) },
        { conditions(List(it) { SignerKeyXdr.Ed25519(key) }) }, PreconditionsV2Xdr::decode, PreconditionsV2Xdr::encode,
        { envelope(transaction(cond = PreconditionsXdr.V2(it))) })
    // Stellar-transaction.x: Claimant claimants<10>;
    // Prefix: native asset tag (4) + amount (8); each claimant is tag (4) + destination (36) + predicate tag (4).
    private val claimants = vector(10, { ByteArray(12) + intBytes(it) + ByteArray(it * 44) },
        { CreateClaimableBalanceOpXdr(AssetXdr.Void, Int64Xdr(0), List(it) { claimant }) },
        CreateClaimableBalanceOpXdr::decode, CreateClaimableBalanceOpXdr::encode,
        { opEnvelope(OperationBodyXdr.CreateClaimableBalanceOp(it)) })
    // Stellar-transaction.x: Asset path<5>;
    // Prefix: sendAsset (4), sendMax (8), destination (36), destAsset (4), destAmount (8); each native asset is one tag word.
    private val path = vector(5, { ByteArray(60) + intBytes(it) + ByteArray(it * 4) },
        { PathPaymentStrictReceiveOpXdr(AssetXdr.Void, Int64Xdr(0), account, AssetXdr.Void, Int64Xdr(0), List(it) { AssetXdr.Void }) },
        PathPaymentStrictReceiveOpXdr::decode, PathPaymentStrictReceiveOpXdr::encode,
        { opEnvelope(OperationBodyXdr.PathPaymentStrictReceiveOp(it)) })
    // Stellar-ledger-entries.x: typedef opaque DataValue<64>;
    private val dataValue = vector(64, ::opaque, { DataValueXdr(bytes(it)) }, DataValueXdr::decode, DataValueXdr::encode,
        { opEnvelope(OperationBodyXdr.ManageDataOp(ManageDataOpXdr(String64Xdr("n"), it))) })
    // Stellar-types.x: typedef opaque Signature<64>;
    private val signature = vector(64, ::opaque, { SignatureXdr(bytes(it)) }, SignatureXdr::decode, SignatureXdr::encode,
        { envelope(transaction(), listOf(DecoratedSignatureXdr(SignatureHintXdr(ByteArray(4)), it))) })
    // Stellar-types.x: opaque payload<64>; its prefix is the 32-byte uint256 ed25519 key.
    private val payload = vector(64, { ByteArray(32) + opaque(it) }, { SignerKeyEd25519SignedPayloadXdr(key, bytes(it)) },
        SignerKeyEd25519SignedPayloadXdr::decode, SignerKeyEd25519SignedPayloadXdr::encode,
        { envelope(transaction(cond = PreconditionsXdr.V2(conditions(listOf(SignerKeyXdr.Ed25519SignedPayload(it)))))) })
    private val symbol = vector(SCSYMBOL_LIMIT, { intBytes(SCValTypeXdr.SCV_SYMBOL.value) + opaque(it) }, { SCValXdr.Sym(SCSymbolXdr("a".repeat(it))) },
        SCValXdr::decode, SCValXdr::encode, { it.toXdrBase64() })
    // Stellar-ledger-entries.x: ClaimPredicate andPredicates<2>; each unconditional predicate is one four-byte tag.
    private val predicates = vector(2, { intBytes(ClaimPredicateTypeXdr.CLAIM_PREDICATE_AND.value) + intBytes(it) + ByteArray(it * 4) },
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

    private fun rejectsArrayEncode(maximum: Int, encode: (XdrWriter) -> Unit) {
        val error = assertFailsWith<IllegalArgumentException> { encoded(encode) }
        assertEquals("XDR array count ${maximum + 1} exceeds maximum $maximum", error.message)
    }

    @Test fun v1EnvelopeSignaturesEncodeRejects() {
        val maximum = 20 // DecoratedSignature signatures<20>;
        val value = TransactionV1EnvelopeXdr(transaction(), List(maximum + 1) { decoratedSignature })
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun v0EnvelopeSignaturesEncodeRejects() {
        val maximum = 20 // DecoratedSignature signatures<20>;
        val value = TransactionV0EnvelopeXdr(transactionV0(), List(maximum + 1) { decoratedSignature })
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun feeBumpEnvelopeSignaturesEncodeRejects() {
        val maximum = 20 // DecoratedSignature signatures<20>;
        val tx = FeeBumpTransactionXdr(account, Int64Xdr(0),
            FeeBumpTransactionInnerTxXdr.V1(TransactionV1EnvelopeXdr(transaction(), emptyList())),
            FeeBumpTransactionExtXdr.Void)
        val value = FeeBumpTransactionEnvelopeXdr(tx, List(maximum + 1) { decoratedSignature })
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun v0OperationsEncodeRejects() {
        val value = transactionV0(List(MAX_OPS_PER_TX + 1) { operation })
        rejectsArrayEncode(MAX_OPS_PER_TX, value::encode)
    }

    @Test fun accountSignersEncodeRejects() {
        val value = AccountEntryXdr(AccountIDXdr(PublicKeyXdr.Ed25519(key)), Int64Xdr(0),
            SequenceNumberXdr(Int64Xdr(0)), Uint32Xdr(0u), null, Uint32Xdr(0u), String32Xdr(""),
            ThresholdsXdr(ByteArray(4)), List(MAX_SIGNERS + 1) { SignerXdr(SignerKeyXdr.Ed25519(key), Uint32Xdr(1u)) },
            AccountEntryExtXdr.Void)
        rejectsArrayEncode(MAX_SIGNERS, value::encode)
    }

    @Test fun signerSponsoringIDsEncodeRejects() {
        val value = AccountEntryExtensionV2Xdr(Uint32Xdr(0u), Uint32Xdr(0u),
            List(MAX_SIGNERS + 1) { SponsorshipDescriptorXdr(null) }, AccountEntryExtensionV2ExtXdr.Void)
        rejectsArrayEncode(MAX_SIGNERS, value::encode)
    }

    @Test fun contractCostParamsEncodeRejects() {
        val value = ContractCostParamsXdr(List(CONTRACT_COST_COUNT_LIMIT + 1) {
            ContractCostParamEntryXdr(ExtensionPointXdr.Void, Int64Xdr(0), Int64Xdr(0))
        })
        rejectsArrayEncode(CONTRACT_COST_COUNT_LIMIT, value::encode)
    }

    @Test fun strictSendPathEncodeRejects() {
        val maximum = 5 // Asset path<5>;
        val value = PathPaymentStrictSendOpXdr(AssetXdr.Void, Int64Xdr(0), account, AssetXdr.Void,
            Int64Xdr(0), List(maximum + 1) { AssetXdr.Void })
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun balanceClaimantsEncodeRejects() {
        val maximum = 10 // Claimant claimants<10>;
        val value = ClaimableBalanceEntryXdr(ClaimableBalanceIDXdr.V0(HashXdr(ByteArray(32))),
            List(maximum + 1) { claimant }, AssetXdr.Void, Int64Xdr(0), ClaimableBalanceEntryExtXdr.Void)
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun orPredicatesEncodeRejects() {
        val maximum = 2 // ClaimPredicate orPredicates<2>;
        val value = ClaimPredicateXdr.OrPredicates(List(maximum + 1) { ClaimPredicateXdr.Void })
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun stellarValueUpgradesEncodeRejects() {
        val maximum = 6 // UpgradeType upgrades<6>;
        val value = StellarValueXdr(HashXdr(ByteArray(32)), TimePointXdr(Uint64Xdr(0u)),
            List(maximum + 1) { UpgradeTypeXdr(ByteArray(0)) }, StellarValueExtXdr.Void)
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun tupleValueTypesEncodeRejects() {
        val maximum = 12 // SCSpecTypeDef valueTypes<12>;
        val value = SCSpecTypeTupleXdr(List(maximum + 1) { SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U32) })
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun functionOutputsEncodeRejects() {
        val maximum = 1 // SCSpecTypeDef outputs<1>;
        val value = SCSpecFunctionV0Xdr("", SCSymbolXdr("f"), emptyList(),
            List(maximum + 1) { SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U32) })
        rejectsArrayEncode(maximum, value::encode)
    }

    @Test fun eventPrefixTopicsEncodeRejects() {
        val maximum = 2 // SCSymbol prefixTopics<2>;
        val value = SCSpecEventV0Xdr("", "", "event", List(maximum + 1) { SCSymbolXdr("topic") },
            emptyList(), SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_SINGLE_VALUE)
        rejectsArrayEncode(maximum, value::encode)
    }

    private fun padding(raw: ByteArray, decode: (ByteArray) -> ByteArray, reject: Boolean) {
        if (reject) {
            // The smallest nonzero padding byte must be rejected.
            val bad = raw.copyOf().also { it[it.lastIndex] = 1 }
            assertFailsWith<IllegalArgumentException> { decode(bad) }
        } else {
            assertContentEquals(raw, decode(raw))
        }
    }
    // "abc" is three ASCII bytes followed by one zero padding byte.
    private fun memoPadding(reject: Boolean) = padding(intBytes(MemoTypeXdr.MEMO_TEXT.value) + intBytes(3) + byteArrayOf(97, 98, 99, 0), memo.roundTrip, reject)
    // Three-byte payloads leave one padding byte; the signed-payload key occupies 32 bytes before its opaque value.
    private fun dataPadding(reject: Boolean) = padding(opaque(3), dataValue.roundTrip, reject)
    private fun signerPadding(reject: Boolean) = padding(intBytes(SignerKeyTypeXdr.SIGNER_KEY_TYPE_ED25519_SIGNED_PAYLOAD.value) + ByteArray(32) + opaque(3),
        { input -> encoded { SignerKeyXdr.decode(XdrReader(input)).encode(it) } }, reject)
    private fun scPadding(type: Int, reject: Boolean) = padding(intBytes(type) + opaque(3),
        { input -> encoded { SCValXdr.decode(XdrReader(input)).encode(it) } }, reject)

    @Test fun memoPaddingZero() = memoPadding(false)
    @Test fun memoPaddingRejects() = memoPadding(true)
    @Test fun dataPaddingZero() = dataPadding(false)
    @Test fun dataPaddingRejects() = dataPadding(true)
    @Test fun signerPaddingZero() = signerPadding(false)
    @Test fun signerPaddingRejects() = signerPadding(true)
    @Test fun scStringPaddingZero() = scPadding(SCValTypeXdr.SCV_STRING.value, false)
    @Test fun scStringPaddingRejects() = scPadding(SCValTypeXdr.SCV_STRING.value, true)
    @Test fun scBytesPaddingZero() = scPadding(SCValTypeXdr.SCV_BYTES.value, false)
    @Test fun scBytesPaddingRejects() = scPadding(SCValTypeXdr.SCV_BYTES.value, true)

    // SCBytes, HostFunction.wasm, and ContractCodeEntry.code declare no maximum.
    // Each 70 KiB fixture exceeds the largest declared opaque maximum: EncryptedBody<64000>.
    @Test fun unboundedSCBytes() {
        val raw = opaque(70 * 1024)
        assertContentEquals(raw, encoded { SCBytesXdr(bytes(70 * 1024)).encode(it) })
        assertContentEquals(bytes(70 * 1024), SCBytesXdr.decode(XdrReader(raw)).value)
    }
    @Test fun unboundedUploadWasm() {
        val raw = intBytes(HostFunctionTypeXdr.HOST_FUNCTION_TYPE_UPLOAD_CONTRACT_WASM.value) + opaque(70 * 1024)
        assertContentEquals(raw, encoded { HostFunctionXdr.Wasm(bytes(70 * 1024)).encode(it) })
        assertContentEquals(bytes(70 * 1024), (HostFunctionXdr.decode(XdrReader(raw)) as HostFunctionXdr.Wasm).value)
    }
    @Test fun unboundedContractCode() {
        // ContractCodeEntry prefix: the four-byte ext discriminant followed by the 32-byte hash.
        val raw = ByteArray(36) + opaque(70 * 1024)
        assertContentEquals(raw, encoded { ContractCodeEntryXdr(ContractCodeEntryExtXdr.Void, HashXdr(ByteArray(32)), bytes(70 * 1024)).encode(it) })
        assertContentEquals(bytes(70 * 1024), ContractCodeEntryXdr.decode(XdrReader(raw)).code)
    }
}
