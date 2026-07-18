//
//  ScValHostOrder.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter

/**
 * Orders two `SCVal` ScMap keys the way the Soroban host does.
 *
 * The host stores and validates ScMap keys in a semantic order and rejects a map whose
 * keys are not in that order when it materializes the map from an `SCVal` contract
 * argument. Sorting by the XDR-encoded key bytes instead is length-major — the four-byte
 * length prefix of a variable-length payload is compared before its content — which
 * diverges from the host whenever two keys' variable-length fields differ in length, and
 * the host then rejects the map with `InvalidInput`.
 *
 * Ordering:
 * - Values of different types compare by their `SCValType` discriminant.
 * - `Vec` compares element-wise (recursively); the shorter vec sorts first on a prefix tie.
 * - `Map` compares entry-wise (key, then value, recursively); the map with fewer entries
 *   sorts first on a prefix tie.
 * - `Bytes`, `String`, and `Symbol` compare by content, byte for byte (unsigned); the
 *   shorter value sorts first on a prefix tie (length is the tiebreaker, never the primary
 *   key).
 * - All remaining values compare by their XDR encoding. For the fixed-width types that can
 *   appear in smart-account map keys (addresses, unsigned scalars) this equals a content
 *   comparison. Signed integer scalars would compare by their two's-complement bytes rather
 *   than numerically; they cannot appear as smart-account map keys.
 */
internal fun compareScValHostOrder(a: SCValXdr, b: SCValXdr): Int {
    val typeA = a.discriminant.value
    val typeB = b.discriminant.value
    if (typeA != typeB) return typeA.compareTo(typeB)

    return when (a) {
        is SCValXdr.Vec -> {
            val elementsA = a.value?.value ?: emptyList()
            val elementsB = (b as SCValXdr.Vec).value?.value ?: emptyList()
            val shared = minOf(elementsA.size, elementsB.size)
            for (i in 0 until shared) {
                val cmp = compareScValHostOrder(elementsA[i], elementsB[i])
                if (cmp != 0) return cmp
            }
            elementsA.size.compareTo(elementsB.size)
        }
        is SCValXdr.Map -> {
            val entriesA = a.value?.value ?: emptyList()
            val entriesB = (b as SCValXdr.Map).value?.value ?: emptyList()
            val shared = minOf(entriesA.size, entriesB.size)
            for (i in 0 until shared) {
                val keyCmp = compareScValHostOrder(entriesA[i].key, entriesB[i].key)
                if (keyCmp != 0) return keyCmp
                val valCmp = compareScValHostOrder(entriesA[i].`val`, entriesB[i].`val`)
                if (valCmp != 0) return valCmp
            }
            entriesA.size.compareTo(entriesB.size)
        }
        is SCValXdr.Bytes ->
            compareBytesUnsigned(a.value.value, (b as SCValXdr.Bytes).value.value)
        is SCValXdr.Str ->
            compareBytesUnsigned(
                a.value.value.encodeToByteArray(),
                (b as SCValXdr.Str).value.value.encodeToByteArray()
            )
        is SCValXdr.Sym ->
            compareBytesUnsigned(
                a.value.value.encodeToByteArray(),
                (b as SCValXdr.Sym).value.value.encodeToByteArray()
            )
        else ->
            compareBytesUnsigned(scValToXdrBytesForOrder(a), scValToXdrBytesForOrder(b))
    }
}

/**
 * Compares two byte arrays element-wise as unsigned bytes; on a prefix tie the shorter
 * array is smaller. This matches the Soroban host's ordering of `Bytes`/`String`/`Symbol`
 * content (Rust slice `Ord`).
 */
private fun compareBytesUnsigned(a: ByteArray, b: ByteArray): Int {
    val shared = minOf(a.size, b.size)
    for (i in 0 until shared) {
        val cmp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
        if (cmp != 0) return cmp
    }
    return a.size.compareTo(b.size)
}

private fun scValToXdrBytesForOrder(value: SCValXdr): ByteArray {
    val writer = XdrWriter()
    value.encode(writer)
    return writer.toByteArray()
}
