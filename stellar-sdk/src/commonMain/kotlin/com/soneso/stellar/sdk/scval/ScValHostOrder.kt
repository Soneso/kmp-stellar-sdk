//
//  ScValHostOrder.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.scval

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter

/**
 * Orders two [SCValXdr] values the way the Soroban host does.
 *
 * The host rejects an `SCV_MAP` contract argument with `InvalidInput` unless its keys are
 * strictly ascending in this order; [Scv.toMap] sorts map entries with it. Variable-length
 * values compare by content because their XDR encoding starts with the length.
 *
 * Ordering:
 * - Values of different types compare by their `SCValType` discriminant.
 * - `I32`, `I64`, `I128`, `I256` and `LedgerKeyNonce` compare numerically as signed integers.
 * - `Bytes`, `String`, `Symbol` and `ExecutableTag` compare by content, byte for byte
 *   (unsigned); the shorter value sorts first on a common prefix.
 * - `Vec` compares element-wise and `Map` entry-wise (key, then value), recursively; the
 *   shorter one sorts first on a common prefix. A null backing list counts as empty.
 * - `ContractInstance` compares by executable (type, then payload, with an external-ref tag
 *   compared by content), then by storage: absent storage first, then entry-wise as a `Map`.
 * - The remaining types (`Bool`, `Void`, `Error`, `U32`, `U64`, `Timepoint`, `Duration`,
 *   `U128`, `U256`, `Address`, `LedgerKeyContractInstance`) have a fixed width within each
 *   union arm and encode only unsigned or non-negative fields, so their XDR encoding
 *   compares in host order.
 *
 * @return a negative number, zero, or a positive number as [a] sorts before, equal to, or after [b]
 */
fun compareScValHostOrder(a: SCValXdr, b: SCValXdr): Int {
    val typeA = a.discriminant.value
    val typeB = b.discriminant.value
    if (typeA != typeB) return typeA.compareTo(typeB)

    return when (a) {
        is SCValXdr.Vec ->
            compareLists(a.value?.value.orEmpty(), (b as SCValXdr.Vec).value?.value.orEmpty(), ::compareScValHostOrder)
        is SCValXdr.Map ->
            compareMapEntries(a.value?.value.orEmpty(), (b as SCValXdr.Map).value?.value.orEmpty())
        is SCValXdr.Bytes ->
            Util.compareBytesUnsigned(a.value.value, (b as SCValXdr.Bytes).value.value)
        is SCValXdr.Str ->
            Util.compareBytesUnsigned(
                a.value.value.encodeToByteArray(),
                (b as SCValXdr.Str).value.value.encodeToByteArray()
            )
        is SCValXdr.Sym ->
            Util.compareBytesUnsigned(
                a.value.value.encodeToByteArray(),
                (b as SCValXdr.Sym).value.value.encodeToByteArray()
            )
        is SCValXdr.ExecutableTag ->
            Util.compareBytesUnsigned(a.value, (b as SCValXdr.ExecutableTag).value)
        is SCValXdr.I32 -> a.value.value.compareTo((b as SCValXdr.I32).value.value)
        is SCValXdr.I64 -> a.value.value.compareTo((b as SCValXdr.I64).value.value)
        is SCValXdr.NonceKey -> a.value.nonce.value.compareTo((b as SCValXdr.NonceKey).value.nonce.value)
        is SCValXdr.I128 -> {
            val other = (b as SCValXdr.I128).value
            a.value.hi.value.compareTo(other.hi.value).takeIf { it != 0 }
                ?: a.value.lo.value.compareTo(other.lo.value)
        }
        is SCValXdr.I256 -> {
            val x = a.value
            val y = (b as SCValXdr.I256).value
            x.hiHi.value.compareTo(y.hiHi.value).takeIf { it != 0 }
                ?: x.hiLo.value.compareTo(y.hiLo.value).takeIf { it != 0 }
                ?: x.loHi.value.compareTo(y.loHi.value).takeIf { it != 0 }
                ?: x.loLo.value.compareTo(y.loLo.value)
        }
        is SCValXdr.Instance -> {
            val x = a.value
            val y = (b as SCValXdr.Instance).value
            val executable = compareExecutables(x.executable, y.executable)
            when {
                executable != 0 -> executable
                x.storage == null || y.storage == null -> (x.storage != null).compareTo(y.storage != null)
                else -> compareMapEntries(x.storage.value, y.storage.value)
            }
        }
        else -> Util.compareBytesUnsigned(encodeXdr(a::encode), encodeXdr(b::encode))
    }
}

/** Orders executables by type, then payload; an external-ref tag compares by content. */
private fun compareExecutables(a: ContractExecutableXdr, b: ContractExecutableXdr): Int {
    if (a is ContractExecutableXdr.ExternalRef && b is ContractExecutableXdr.ExternalRef) {
        val owner = Util.compareBytesUnsigned(
            encodeXdr(a.value.executableOwner::encode),
            encodeXdr(b.value.executableOwner::encode)
        )
        return if (owner != 0) owner else Util.compareBytesUnsigned(a.value.tag, b.value.tag)
    }
    return Util.compareBytesUnsigned(encodeXdr(a::encode), encodeXdr(b::encode))
}

private fun compareMapEntries(a: List<SCMapEntryXdr>, b: List<SCMapEntryXdr>): Int =
    compareLists(a, b) { x, y ->
        compareScValHostOrder(x.key, y.key).takeIf { it != 0 } ?: compareScValHostOrder(x.`val`, y.`val`)
    }

private inline fun <T> compareLists(a: List<T>, b: List<T>, compare: (T, T) -> Int): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val cmp = compare(a[i], b[i])
        if (cmp != 0) return cmp
    }
    return a.size.compareTo(b.size)
}

private fun encodeXdr(encode: (XdrWriter) -> Unit): ByteArray {
    val writer = XdrWriter()
    encode(writer)
    return writer.toByteArray()
}
