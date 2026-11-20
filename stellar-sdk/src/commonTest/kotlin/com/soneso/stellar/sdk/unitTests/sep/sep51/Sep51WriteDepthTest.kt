package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.unitTests.xdr.json.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The XDR-JSON writer stops at the 128 levels the reader accepts, for every recursive XDR type.
 * Each family nests one recursive type inside the deepest record that carries it. Expected
 * depths are counted on the document text, and expected bytes are the input's own.
 */
class Sep51WriteDepthTest {

    /**
     * The deepest value of each family within 128 levels round-trips binary to JSON to binary
     * byte-exact; one wrapping more, and the deepest value the binary decoder accepts, are refused.
     */
    @Test
    fun everyFamilyWritesUpToTheLimitAndRefusesItsDeepestDecodableValue() {
        FAMILIES.forEach { it.check() }
    }

    /** Transactions nesting 98 maps and an authorization tree of 245 levels decode but do not write. */
    @Test
    fun transactionsNestedPastTheLimitAreRefusedByTheWriter() {
        val args = InvokeContractArgsXdr(address(), SCSymbolXdr("echo"), listOf(chain(98, u32(5u), ::map)))
        val maps = sample(::sampleInvokeHostFunctionOpXdr)
            .copy(hostFunction = HostFunctionXdr.InvokeContract(args), auth = emptyList())
        val auth = SorobanAuthorizationEntryXdr(SorobanCredentialsXdr.Void, authTree(244))
        val bodies = listOf(
            OperationBodyXdr.InvokeHostFunctionOp(maps) to "SCMapXdr",
            invokeBody(auth) to "AlphaNum4Xdr",
        )
        bodies.forEach { (body, refusedBy) ->
            val envelope = TransactionEnvelopeXdr.V1(v1Envelope(body))
            val base64 = envelope.toXdrBase64()
            assertEquals(base64, TransactionEnvelopeXdr.fromXdrBase64(base64).toXdrBase64())
            refusesWrite(refusedBy) { envelope.toXdrJson() }
        }
    }
}

/** The binary and XDR-JSON entry points of a record type. */
internal class Codec<T>(
    val encode: (T, XdrWriter) -> Unit,
    val decode: (XdrReader) -> T,
    val write: (T) -> String,
    val read: (String) -> T,
) {
    fun bytes(value: T): ByteArray = XdrWriter().also { encode(value, it) }.toByteArray()
}

internal val LCM = Codec<LedgerCloseMetaXdr>(
    { v, w -> v.encode(w) }, { LedgerCloseMetaXdr.decode(it) },
    { it.toXdrJson() }, { LedgerCloseMetaXdr.fromXdrJson(it) },
)
private val SPEC = Codec<SCSpecEntryXdr>(
    { v, w -> v.encode(w) }, { SCSpecEntryXdr.decode(it) }, { it.toXdrJson() }, { SCSpecEntryXdr.fromXdrJson(it) },
)

/**
 * A record carrying one recursive type wrapped `n` times by [value]: [deepest] wrappings nest the
 * document [depth] levels deep, the most at or under 128; [guard] is the most the binary decoder accepts.
 */
private class Family<T>(
    val name: String, val codec: Codec<T>, val deepest: Int, val depth: Int, val guard: Int, val value: (Int) -> T,
) {
    fun check() {
        val bytes = codec.bytes(value(deepest))
        val json = codec.write(codec.decode(XdrReader(bytes)))
        assertEquals(depth, textDepth(json), name)
        assertContentEquals(bytes, codec.bytes(codec.read(json)), name)
        val (pastDeepest, atGuard) = REFUSED_BY.getValue(name)
        refusesWrite(pastDeepest, name) { codec.write(value(deepest + 1)) }
        val witness = codec.decode(XdrReader(codec.bytes(value(guard))))
        refusesWrite(atGuard, name) { codec.write(witness) }
    }
}

private val FAMILIES: List<Family<*>> = listOf(
    Family("SCVal vectors", LCM, 50, 127, 499) { lcmSignature(chain(it, errorLeaf(), ::vec)) },
    Family("SCVal maps", LCM, 33, 126, 249) { lcmSignature(chain(it, SCValXdr.Map(SCMapXdr(emptyList())), ::map)) },
    Family("contract instances", LCM, 24, 125, 166) { n ->
        val ref = ContractExecutableXdr.ExternalRef(ContractExecutableExternalRefXdr(address(), byteArrayOf(1, 2, 3)))
        lcmSignature(chain(n, instance(null, ref)) { instance(SCMapXdr(listOf(SCMapEntryXdr(u32(1u), it))), null) })
    },
    Family("maps of vectors", LCM, 19, 124, 166) { n -> lcmSignature(chain(n, vec(errorLeaf())) { map(vec(it)) }) },
    Family("claim predicates", LCM, 51, 127, 499) { n -> lcmPredicate(chain(n, and(emptyList())) { and(listOf(it)) }) },
    Family("spec types", SPEC, 39, 126, 249) { n -> specEntry(chain(n, tuple(emptyList())) { tuple(listOf(it)) }) },
    Family("authorized invocations", LCM, 50, 128, 499) { authLedger(it) },
    Family("delegates", LCM, 50, 128, 128) { n -> lcmDelegates(chain(n, delegate()) { delegate(listOf(it)) }) },
    Family("quorum sets", LCM, 60, 128, 499) { n -> lcmQuorum(chain(n, quorum(emptyList())) { quorum(listOf(it)) }) },
)

/** Per family, the type that opens level 129 in the value one wrapping past the deepest, and in the witness. */
private val REFUSED_BY: Map<String, Pair<String, String>> = mapOf(
    "SCVal vectors" to ("SCErrorXdr" to "SCVecXdr"),
    "SCVal maps" to ("SCMapXdr" to "SCMapXdr"),
    "contract instances" to ("ContractExecutableExternalRefXdr" to "SCMapEntryXdr"),
    "maps of vectors" to ("SCErrorXdr" to "SCValXdr"),
    "claim predicates" to ("ClaimPredicateXdr" to "ClaimPredicateXdr"),
    "spec types" to ("SCSpecTypeTupleXdr" to "SCSpecTypeTupleXdr"),
    "authorized invocations" to ("AssetXdr" to "AssetXdr"),
    "delegates" to ("SCValXdr" to "SCValXdr"),
    "quorum sets" to ("SCPQuorumSetXdr" to "SCPQuorumSetXdr"),
)

/** Refuses [write] with the writer's depth message, naming [type], whose object or array would open level 129. */
internal fun refusesWrite(type: String, context: String = "", write: () -> Any) {
    val error = assertFailsWith<IllegalArgumentException>(context) { write() }
    assertEquals("$type: value nests deeper than the limit of 128", error.message, context)
}

/** Container nesting depth of JSON text, counted without parsing it; a primitive is not a level. */
internal fun textDepth(json: String): Int {
    var depth = 0
    var deepest = 0
    var inString = false
    var escaped = false
    for (c in json) {
        when {
            escaped -> escaped = false
            inString -> if (c == '\\') escaped = true else if (c == '"') inString = false
            c == '"' -> inString = true
            c == '{' || c == '[' -> deepest = maxOf(deepest, ++depth)
            c == '}' || c == ']' -> depth--
        }
    }
    return deepest
}

/** A ledger close meta whose one authorization tree nests `wrappings + 1` invocations. */
internal fun authLedger(wrappings: Int): LedgerCloseMetaXdr =
    lcmEnvelope(invokeBody(SorobanAuthorizationEntryXdr(SorobanCredentialsXdr.Void, authTree(wrappings))))

private fun <S> sample(factory: (Int, Int) -> S): S = factory(7, SAMPLE_DEPTH)

private fun <C> chain(n: Int, innermost: C, wrap: (C) -> C): C {
    var value = innermost
    repeat(n) { value = wrap(value) }
    return value
}

private fun address(): SCAddressXdr = sample(::sampleSCAddressXdr)
private fun u32(v: UInt): SCValXdr = SCValXdr.U32(Uint32Xdr(v))
private fun errorLeaf(): SCValXdr = SCValXdr.Error(SCErrorXdr.ContractCode(Uint32Xdr(1u)))
private fun vec(inner: SCValXdr): SCValXdr = SCValXdr.Vec(SCVecXdr(listOf(inner)))
private fun map(inner: SCValXdr): SCValXdr = SCValXdr.Map(SCMapXdr(listOf(SCMapEntryXdr(u32(1u), inner))))
private fun instance(storage: SCMapXdr?, executable: ContractExecutableXdr?): SCValXdr =
    SCValXdr.Instance(SCContractInstanceXdr(executable ?: ContractExecutableXdr.Void, storage))
private fun tuple(types: List<SCSpecTypeDefXdr>): SCSpecTypeDefXdr = SCSpecTypeDefXdr.Tuple(SCSpecTypeTupleXdr(types))
private fun and(predicates: List<ClaimPredicateXdr>): ClaimPredicateXdr = ClaimPredicateXdr.AndPredicates(predicates)
private fun delegate(nested: List<SorobanDelegateSignatureXdr> = emptyList()) =
    SorobanDelegateSignatureXdr(address(), errorLeaf(), nested)
private fun quorum(inner: List<SCPQuorumSetXdr>) = SCPQuorumSetXdr(Uint32Xdr(1u), emptyList(), inner)

private fun authTree(wrappings: Int): SorobanAuthorizedInvocationXdr {
    val asset = ContractIDPreimageXdr.FromAsset(AssetXdr.AlphaNum4(sample(::sampleAlphaNum4Xdr)))
    val args = CreateContractArgsXdr(asset, ContractExecutableXdr.Void)
    val function = SorobanAuthorizedFunctionXdr.CreateContractHostFn(args)
    return chain(wrappings, SorobanAuthorizedInvocationXdr(function, emptyList())) {
        SorobanAuthorizedInvocationXdr(function, listOf(it))
    }
}

private fun v1Envelope(body: OperationBodyXdr) = sample(::sampleTransactionV1EnvelopeXdr)
    .copy(tx = sample(::sampleTransactionXdr).copy(operations = listOf(OperationXdr(null, body))))

private fun invokeBody(entry: SorobanAuthorizationEntryXdr): OperationBodyXdr =
    OperationBodyXdr.InvokeHostFunctionOp(sample(::sampleInvokeHostFunctionOpXdr).copy(auth = listOf(entry)))

/** The deepest place a ledger close meta carries an operation: a fee bump in a v1 transaction set. */
private fun lcmEnvelope(body: OperationBodyXdr): LedgerCloseMetaXdr {
    val inner = FeeBumpTransactionInnerTxXdr.V1(v1Envelope(body))
    val feeBump = sample(::sampleFeeBumpTransactionXdr).copy(innerTx = inner)
    val envelope = TransactionEnvelopeXdr.FeeBump(sample(::sampleFeeBumpTransactionEnvelopeXdr).copy(tx = feeBump))
    val txs = sample(::sampleTxSetComponentTxsMaybeDiscountedFeeXdr).copy(txs = listOf(envelope))
    val phase = TransactionPhaseXdr.V0Components(listOf(TxSetComponentXdr.TxsMaybeDiscountedFee(txs)))
    val txSet = GeneralizedTransactionSetXdr.V1TxSet(sample(::sampleTransactionSetV1Xdr).copy(phases = listOf(phase)))
    return LedgerCloseMetaXdr.V1(sample(::sampleLedgerCloseMetaV1Xdr).copy(txSet = txSet))
}

private fun lcmCredentials(signature: SCValXdr, delegates: List<SorobanDelegateSignatureXdr>): LedgerCloseMetaXdr {
    val address = sample(::sampleSorobanAddressCredentialsXdr).copy(signature = signature)
    val credentials = SorobanAddressCredentialsWithDelegatesXdr(address, delegates)
    val entry = SorobanAuthorizationEntryXdr(SorobanCredentialsXdr.AddressWithDelegates(credentials), authTree(0))
    return lcmEnvelope(invokeBody(entry))
}

private fun lcmSignature(signature: SCValXdr) = lcmCredentials(signature, emptyList())
private fun lcmDelegates(delegate: SorobanDelegateSignatureXdr) = lcmCredentials(errorLeaf(), listOf(delegate))

private fun lcmPredicate(predicate: ClaimPredicateXdr): LedgerCloseMetaXdr {
    val claimant = ClaimantXdr.V0(sample(::sampleClaimantV0Xdr).copy(predicate = predicate))
    val op = sample(::sampleCreateClaimableBalanceOpXdr).copy(claimants = listOf(claimant))
    return lcmEnvelope(OperationBodyXdr.CreateClaimableBalanceOp(op))
}

private fun lcmQuorum(quorumSet: SCPQuorumSetXdr): LedgerCloseMetaXdr {
    val entry = SCPHistoryEntryXdr.V0(sample(::sampleSCPHistoryEntryV0Xdr).copy(quorumSets = listOf(quorumSet)))
    return LedgerCloseMetaXdr.V1(sample(::sampleLedgerCloseMetaV1Xdr).copy(scpInfo = listOf(entry)))
}

private fun specEntry(type: SCSpecTypeDefXdr): SCSpecEntryXdr {
    val case = SCSpecUDTUnionCaseV0Xdr.TupleCase(SCSpecUDTUnionCaseTupleV0Xdr("", "C", listOf(type)))
    return SCSpecEntryXdr.UdtUnionV0(SCSpecUDTUnionV0Xdr("", "", "U", listOf(case)))
}
