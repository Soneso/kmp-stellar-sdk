package com.soneso.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Converts this [SCValXdr] to a native Kotlin value without a contract spec.
 *
 * The conversion is total: it never throws, whatever the receiver contains. Every arm
 * has a defined result, and where a value has no native representation the result is
 * the [SCValXdr] itself (the same instance, never a copy), so a caller detects that
 * case with `is SCValXdr`. The receiver and its children are never mutated. The
 * `ByteArray` returned for a bytes value is the stored payload array, so writing to it
 * writes through to the receiver.
 *
 * ## Value conversion
 *
 * - `SCV_BOOL` → `Boolean`
 * - `SCV_VOID` → `null`
 * - `SCV_U32` → `UInt`; `SCV_I32` → `Int`
 * - `SCV_U64`, `SCV_TIMEPOINT`, `SCV_DURATION` → `ULong`; `SCV_I64` → `Long`
 * - `SCV_U128`, `SCV_I128`, `SCV_U256`, `SCV_I256` → `BigInteger`
 *   (`com.ionspin.kotlin.bignum.integer.BigInteger`, as returned by [Scv.fromUint128],
 *   [Scv.fromInt128], [Scv.fromUint256] and [Scv.fromInt256])
 * - `SCV_BYTES` → `ByteArray`
 * - `SCV_STRING`, `SCV_SYMBOL` → `String`
 * - `SCV_ADDRESS` → strkey `String` (`G...`, `C...`, `M...`, `B...` or `L...`)
 * - `SCV_VEC` → `List<Any?>` with each element converted recursively, in order; an
 *   absent payload yields an empty list
 * - `SCV_MAP` → `Map<Any?, Any?>` under the key rules below; an absent payload yields
 *   an empty map
 * - `SCV_ERROR`, `SCV_CONTRACT_INSTANCE`, `SCV_LEDGER_KEY_CONTRACT_INSTANCE`,
 *   `SCV_LEDGER_KEY_NONCE`, `SCV_EXECUTABLE_TAG` → the [SCValXdr] itself. The same holds
 *   for an address whose bytes have no strkey encoding and for a [SCValXdr.Void] whose
 *   discriminant is anything other than `SCV_VOID`. The `Scv.from*` accessors, for
 *   example [Scv.fromError] or [Scv.fromExecutableTagBytes], read those payloads on
 *   request.
 *
 * ## Map keys
 *
 * Map keys need value-based equality, which `ByteArray` and the generated XDR types do
 * not provide, so keys use a narrower conversion than values:
 *
 * - `SCV_SYMBOL`, `SCV_STRING` → `String`
 * - `SCV_U32` → `UInt`; `SCV_I32` → `Int`
 * - `SCV_U64`, `SCV_TIMEPOINT`, `SCV_DURATION` → `ULong`; `SCV_I64` → `Long`
 * - `SCV_U128`, `SCV_I128`, `SCV_U256`, `SCV_I256` → `BigInteger`
 * - `SCV_BOOL` → `Boolean`
 * - `SCV_VOID` → `null`
 * - `SCV_BYTES` → lowercase hex `String`; this is the one asymmetry between the two
 *   tables, since a bytes value stays a `ByteArray`
 * - `SCV_ADDRESS` → strkey `String`, the same representation as for a value
 *
 * Every other key arm is unrepresentable. A map with an unrepresentable key, or with two
 * entries whose converted keys are equal, converts to the [SCValXdr] itself as a whole.
 * A nested map that falls back is contained: the enclosing vec or map still converts,
 * with that map left as an [SCValXdr] element.
 *
 * Key equality is plain Kotlin `equals`/`hashCode` on the converted keys, and a key is
 * looked up with the exact type the table above gives, so a 128-bit or 256-bit key is
 * looked up with a `BigInteger`. The primitive integer types are never normalized
 * against each other, and a wide-integer key and a primitive integer key are compared
 * by numeric value:
 *
 * - Primitive integer keys of equal value from different arms are distinct: a `U32` key
 *   `5` (`UInt`) and a `U64` key `5` (`ULong`) coexist as two entries.
 * - A wide-integer key and a primitive integer key of equal value are the same key: an
 *   `I128` key `5` and a `U64` key `5` collide, so such a map falls back. Keys whose
 *   values differ never collide, so an `I128` key `-1` and a `U64` key
 *   `18446744073709551615` coexist.
 * - Wide-integer keys of equal value collide even across arms: a `U128` key `5` and an
 *   `I256` key `5` are the same key, so such a map falls back.
 * - A bytes key collides with a symbol or string key spelling its hex: the bytes
 *   `[0x30, 0x31]` and the symbol `"3031"` are the same key, so such a map falls back.
 *
 * The resulting map preserves the entry order of the XDR map.
 *
 * ## Relation to ContractSpec
 *
 * [com.soneso.stellar.sdk.contract.ContractSpec.funcResToNative] and
 * [com.soneso.stellar.sdk.contract.ContractSpec.scValToNative] convert with a contract
 * spec, which lets them reconstruct structs, unions and enums. This function is the
 * spec-less companion for values whose spec is unavailable or unneeded, and its output
 * shapes differ: a map becomes a `Map` here and a list of pairs on the spec path, and
 * error, contract-instance and executable-tag values are returned as the [SCValXdr]
 * itself here where the spec path unwraps their payloads.
 *
 * ## Usage
 *
 * ```kotlin
 * val scVal = Scv.toMap(linkedMapOf(
 *     Scv.toSymbol("balance") to Scv.toInt128(BigInteger.fromLong(1_000L)),
 *     Scv.toSymbol("tags") to Scv.toVec(listOf(Scv.toString("a"), Scv.toString("b")))
 * ))
 * val native = scVal.toNative() as Map<*, *>
 * native["balance"]  // BigInteger 1000
 * native["tags"]     // ["a", "b"]
 *
 * // A value with no native form comes back unchanged
 * val tag = Scv.toExecutableTag("v1").toNative()
 * check(tag is SCValXdr)
 * ```
 *
 * @receiver the value to convert
 * @return the native Kotlin value, `null` for `SCV_VOID`, or this [SCValXdr] itself when
 * the value has no native representation
 */
public fun SCValXdr.toNative(): Any? = when (this) {
    is SCValXdr.B -> value
    is SCValXdr.Void -> if (discriminant == SCValTypeXdr.SCV_VOID) null else this
    is SCValXdr.Error -> this
    is SCValXdr.U32 -> value.value
    is SCValXdr.I32 -> value.value
    is SCValXdr.U64 -> value.value
    is SCValXdr.I64 -> value.value
    is SCValXdr.Timepoint -> value.value.value
    is SCValXdr.Duration -> value.value.value
    is SCValXdr.U128 -> Scv.fromUint128(this)
    is SCValXdr.I128 -> Scv.fromInt128(this)
    is SCValXdr.U256 -> Scv.fromUint256(this)
    is SCValXdr.I256 -> Scv.fromInt256(this)
    is SCValXdr.Bytes -> value.value
    is SCValXdr.Str -> value.value
    is SCValXdr.Sym -> value.value
    is SCValXdr.Vec -> value?.value?.map { it.toNative() } ?: emptyList<Any?>()
    is SCValXdr.Map -> mapToNative(value?.value ?: emptyList()) ?: this
    is SCValXdr.Address -> try {
        Address.fromSCAddress(value).toString()
    } catch (_: Exception) {
        this
    }
    is SCValXdr.Instance -> this
    is SCValXdr.NonceKey -> this
    is SCValXdr.ExecutableTag -> this
}

/**
 * Marker returned by [mapKeyToNative] for a key with no native representation.
 *
 * `null` cannot serve as that marker because it is the legitimate result for an
 * `SCV_VOID` key, so an identity-compared object is used instead.
 */
private val unrepresentableKey = Any()

/**
 * Assembles a native map from [entries] in order, or returns `null` when the map has no
 * native representation: a key is unrepresentable, or two entries collide on equal
 * converted keys.
 *
 * A wide-integer key and a primitive integer key of equal value are a collision, which
 * is detected by tracking the numeric values of both kinds of integer key as
 * `BigInteger`s and checking each integer key against the values of the other kind.
 * Every other collision is detected by the assembled map being smaller than the entry
 * list, since a later entry with an equal key overwrites the earlier one.
 */
private fun mapToNative(entries: List<SCMapEntryXdr>): Map<Any?, Any?>? {
    val result = LinkedHashMap<Any?, Any?>(entries.size)
    val wideIntegerKeys = HashSet<BigInteger>()
    val primitiveIntegerKeys = HashSet<BigInteger>()
    for (entry in entries) {
        val key = mapKeyToNative(entry.key)
        if (key === unrepresentableKey) {
            return null
        }
        if (key is BigInteger) {
            if (key in primitiveIntegerKeys) {
                return null
            }
            wideIntegerKeys.add(key)
        } else {
            val numericValue = primitiveIntegerValue(key)
            if (numericValue != null) {
                if (numericValue in wideIntegerKeys) {
                    return null
                }
                primitiveIntegerKeys.add(numericValue)
            }
        }
        result[key] = entry.`val`.toNative()
    }
    return if (result.size == entries.size) result else null
}

/**
 * Returns the numeric value of a primitive integer map key as a [BigInteger], or `null`
 * for a key of any other type.
 */
private fun primitiveIntegerValue(key: Any?): BigInteger? = when (key) {
    is UInt -> BigInteger.fromUInt(key)
    is Int -> BigInteger.fromInt(key)
    is ULong -> BigInteger.fromULong(key)
    is Long -> BigInteger.fromLong(key)
    else -> null
}

/**
 * Converts a map key to a value with value-based `equals`/`hashCode`, or returns
 * [unrepresentableKey] when the key arm has no such representation.
 */
private fun mapKeyToNative(key: SCValXdr): Any? = when (key) {
    is SCValXdr.Sym -> key.value.value
    is SCValXdr.Str -> key.value.value
    is SCValXdr.U32 -> key.value.value
    is SCValXdr.I32 -> key.value.value
    is SCValXdr.U64 -> key.value.value
    is SCValXdr.I64 -> key.value.value
    is SCValXdr.Timepoint -> key.value.value.value
    is SCValXdr.Duration -> key.value.value.value
    is SCValXdr.U128 -> Scv.fromUint128(key)
    is SCValXdr.I128 -> Scv.fromInt128(key)
    is SCValXdr.U256 -> Scv.fromUint256(key)
    is SCValXdr.I256 -> Scv.fromInt256(key)
    is SCValXdr.B -> key.value
    is SCValXdr.Void -> if (key.discriminant == SCValTypeXdr.SCV_VOID) null else unrepresentableKey
    is SCValXdr.Bytes -> Util.bytesToHex(key.value.value)
    is SCValXdr.Address -> try {
        Address.fromSCAddress(key.value).toString()
    } catch (_: Exception) {
        unrepresentableKey
    }
    is SCValXdr.Vec,
    is SCValXdr.Map,
    is SCValXdr.Error,
    is SCValXdr.Instance,
    is SCValXdr.NonceKey,
    is SCValXdr.ExecutableTag -> unrepresentableKey
}
