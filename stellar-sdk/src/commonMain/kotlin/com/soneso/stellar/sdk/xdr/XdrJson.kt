package com.soneso.stellar.sdk.xdr

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The shared runtime behind the XDR-JSON (SEP-0051) conversion methods on the XDR types.
 *
 * Every rule that governs the JSON form lives here rather than at each of the thousands of
 * call sites, so the mapping is defined once and tested once.
 *
 * The rules, as SEP-0051 states them:
 *
 * - 32-bit integers are JSON numbers. 64-bit integers are base-10 JSON **strings**, because a
 *   JSON number cannot carry 64 bits of precision on every platform that reads it. A 64-bit
 *   value is also accepted as a number on input, for documents written against version 1 of
 *   the specification.
 * - Opaque data is a lowercase hexadecimal string; empty opaque is `""`.
 * - Strings are escaped byte by byte with the ladder in [escapedString].
 * - Arrays are JSON arrays and are always present, empty ones as `[]`.
 * - An optional value is `null` or the value, and its key stays present in the parent object.
 * - A `$schema` property is accepted anywhere an object is read, ignored, and never emitted.
 * - A struct object carries its declared keys and nothing else. A key that names no field is
 *   rejected rather than ignored, so a misspelled field name fails loudly instead of silently
 *   dropping the value it carried. `$schema` is the single exception, because SEP-0051 requires
 *   that it be accepted wherever an object is read.
 *
 * - An object carries each key once. A repeated key names two values where the format admits
 *   one, so it is rejected rather than resolved to either occurrence. The check runs on the
 *   document text, because a parsed [JsonObject] is a map and no longer shows the repetition.
 *
 * Decoding is deliberately stricter than encoding is lax: this SDK accepts only the exact
 * spelling it emits. Uppercase hexadecimal, uppercase `\xNN` escapes and unrecognised escapes
 * are rejected, so no two distinct documents decode to the same value.
 *
 * Every malformed input raises [IllegalArgumentException], naming the type and the offending
 * key, matching the contract the binary decoder already holds.
 */
internal object XdrJson {

    /**
     * Maximum nesting depth accepted when decoding, matching the cap the binary decoder
     * applies to recursive types. Decoding walks the tree with the call stack, so an
     * unbounded document would otherwise exhaust it.
     */
    const val RECURSION_CAP: Int = 128

    /** Truncation length for untrusted values quoted back in error messages. */
    private const val PREVIEW_LIMIT = 80

    /**
     * How many unknown keys an error message names before it falls back to counting the rest,
     * so a document carrying thousands of them cannot turn one rejection into an unbounded
     * message.
     */
    private const val UNKNOWN_KEY_LIMIT = 5

    private const val HEX_DIGITS = "0123456789abcdef"

    private const val ZERO_BYTE: Byte = 0

    /**
     * The canonical output form: compact, never pretty-printed. Objects are built in field
     * declaration order and [JsonObject] preserves insertion order, so the emitted byte
     * sequence is fully determined by the generated code rather than by map iteration.
     */
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    private val TWO_64 = BigInteger.TWO.pow(64)
    private val TWO_128 = BigInteger.TWO.pow(128)
    private val TWO_192 = BigInteger.TWO.pow(192)
    private val TWO_256 = BigInteger.TWO.pow(256)
    private val INT128_MIN = BigInteger.TWO.pow(127).negate()
    private val INT128_MAX = BigInteger.TWO.pow(127) - BigInteger.ONE
    private val UINT128_MAX = TWO_128 - BigInteger.ONE
    private val INT256_MIN = BigInteger.TWO.pow(255).negate()
    private val INT256_MAX = BigInteger.TWO.pow(255) - BigInteger.ONE
    private val UINT256_MAX = TWO_256 - BigInteger.ONE

    /** Canonical base-10 integer text: no sign on zero, no leading zeros, no exponent. */
    private val SIGNED_INTEGER = Regex("""^(0|-?[1-9][0-9]*)$""")
    private val UNSIGNED_INTEGER = Regex("""^(0|[1-9][0-9]*)$""")

    // ---------------------------------------------------------------------------------------
    // Encoding
    // ---------------------------------------------------------------------------------------

    fun int32(value: Int): JsonElement = JsonPrimitive(value)

    fun uint32(value: UInt): JsonElement = JsonPrimitive(value.toLong())

    fun int64(value: Long): JsonElement = JsonPrimitive(value.toString())

    fun uint64(value: ULong): JsonElement = JsonPrimitive(value.toString())

    fun bool(value: Boolean): JsonElement = JsonPrimitive(value)

    /** Opaque data as lowercase hexadecimal; empty data becomes `""`, never `"0"`. */
    fun hex(bytes: ByteArray): JsonElement = JsonPrimitive(toHex(bytes))

    /**
     * Applies the SEP-0051 string ladder byte by byte, first match winning: NUL, tab, newline
     * and carriage return take their short escapes, a backslash doubles, any other byte in the
     * printable range 0x20 to 0x7E is kept verbatim, and everything else becomes `\xNN` with
     * two lowercase hexadecimal digits. The JSON encoder then escapes the resulting
     * backslashes a second time, which is why a single input backslash reaches the wire as
     * four characters.
     */
    fun escapedString(bytes: ByteArray): JsonElement = JsonPrimitive(escape(bytes))

    /**
     * Escapes text using its XDR byte representation, so the JSON and binary forms agree.
     *
     * A `String`-typed member carries text, not arbitrary bytes: its value is the UTF-8 decoding
     * of the wire bytes. Text that is valid UTF-8 therefore survives unchanged, and text that is
     * not was already replaced with the Unicode replacement character before it reached this
     * point, so the escape is applied to the replacement rather than to the original byte. A
     * member that must preserve arbitrary bytes exactly is typed `ByteArray` and uses
     * [escapedString] with that argument instead.
     */
    fun escapedString(value: String): JsonElement = escapedString(value.encodeToByteArray())

    /**
     * The JSON string that stands for a whole value rather than for a field of one: an enum
     * member's name, the name of a union arm that carries nothing, or a strkey. Such a name is
     * an identifier or an encoded key, so it never needs the escape ladder.
     */
    fun name(value: String): JsonElement = JsonPrimitive(value)

    fun <T> array(values: List<T>, encode: (T) -> JsonElement): JsonElement =
        JsonArray(values.map(encode))

    fun <T> optional(value: T?, encode: (T) -> JsonElement): JsonElement =
        if (value == null) JsonNull else encode(value)

    /** Serialises a tree to the canonical compact form. */
    fun encodeToString(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

    // ---------------------------------------------------------------------------------------
    // Decoding
    // ---------------------------------------------------------------------------------------

    /**
     * Parses a document, first scanning its text for the two faults the parser would not
     * report: nesting past [RECURSION_CAP], which must be caught before the parser descends,
     * and a repeated object key, which the parser resolves silently in favour of the last
     * occurrence.
     */
    fun parse(text: String, type: String): JsonElement {
        checkText(text, type)
        return try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            fail(type, "input is not valid JSON: ${e.message}")
        }
    }

    /**
     * Reads an object and drops the optional `$schema` property, leaving the remaining keys
     * unchecked. A union names its selected arm in the key, so [singleKeyObject] reads it from
     * here and checks the shape itself; a struct knows the keys it declares and goes through the
     * [obj] overload that takes them.
     */
    fun obj(element: JsonElement, type: String): JsonObject {
        val value = element as? JsonObject
            ?: fail(type, "expects a JSON object, got ${preview(element)}")
        return stripSchema(value)
    }

    /**
     * Reads a struct object, rejecting any key that is not one of [declaredKeys]. The `$schema`
     * property is dropped before the check, so it is accepted here as it is anywhere else.
     *
     * [declaredKeys] holds every spelling the type answers to, so a field that also accepts a
     * historical spelling contributes both; supplying both at once is caught by [field], which
     * is what knows the two name one field.
     */
    fun obj(element: JsonElement, type: String, declaredKeys: Array<String>): JsonObject {
        val value = obj(element, type)
        for (key in value.keys) {
            if (!declaredKeys.contains(key)) {
                rejectUnknownKeys(value, declaredKeys, type)
            }
        }
        return value
    }

    /** Removes the `$schema` property, which is accepted on input but never emitted. */
    fun stripSchema(value: JsonObject): JsonObject =
        if (value.containsKey("\$schema")) {
            JsonObject(value.filterKeys { it != "\$schema" })
        } else {
            value
        }

    /**
     * Reads a required key. A key holding an optional value must still be present; only its
     * value may be `null`.
     */
    fun field(value: JsonObject, key: String, type: String): JsonElement =
        value[key] ?: fail(type, "is missing the required key \"$key\"")

    /**
     * Reads a required key that also answers to a historical spelling. Only [key] is ever
     * emitted; [alias] is accepted so documents written by older tooling still decode.
     *
     * The two spellings name one field, so a document carrying both is rejected rather than
     * silently resolved in favour of either one.
     */
    fun field(value: JsonObject, key: String, alias: String, type: String): JsonElement {
        val primary = value[key]
        val secondary = value[alias]
        if (primary != null && secondary != null) {
            fail(type, "has both \"$key\" and \"$alias\", which are two spellings of one key")
        }
        return primary ?: secondary ?: fail(type, "is missing the required key \"$key\"")
    }

    /** Returns null for a JSON null, otherwise the element itself. */
    fun optional(element: JsonElement): JsonElement? = if (element is JsonNull) null else element

    fun int32(element: JsonElement, type: String, key: String): Int {
        val text = numberText(element, type, key, "a 32-bit signed integer", allowString = false)
        requireLiteral(SIGNED_INTEGER, text, type, key, "a 32-bit signed integer")
        return text.toIntOrNull() ?: outOfRange(type, key, text, "a 32-bit signed integer")
    }

    fun uint32(element: JsonElement, type: String, key: String): UInt {
        val text = numberText(element, type, key, "a 32-bit unsigned integer", allowString = false)
        requireLiteral(UNSIGNED_INTEGER, text, type, key, "a 32-bit unsigned integer")
        return text.toUIntOrNull() ?: outOfRange(type, key, text, "a 32-bit unsigned integer")
    }

    fun int64(element: JsonElement, type: String, key: String): Long {
        val text = numberText(element, type, key, "a 64-bit signed integer", allowString = true)
        requireLiteral(SIGNED_INTEGER, text, type, key, "a 64-bit signed integer")
        return text.toLongOrNull() ?: outOfRange(type, key, text, "a 64-bit signed integer")
    }

    fun uint64(element: JsonElement, type: String, key: String): ULong {
        val text = numberText(element, type, key, "a 64-bit unsigned integer", allowString = true)
        requireLiteral(UNSIGNED_INTEGER, text, type, key, "a 64-bit unsigned integer")
        return text.toULongOrNull() ?: outOfRange(type, key, text, "a 64-bit unsigned integer")
    }

    fun bool(element: JsonElement, type: String, key: String): Boolean {
        val primitive = primitive(element, type, key, "a JSON boolean")
        if (primitive.isString) {
            fail(type, "key \"$key\" expects a JSON boolean, got the string ${preview(element)}")
        }
        return when (primitive.content) {
            "true" -> true
            "false" -> false
            else -> fail(type, "key \"$key\" expects a JSON boolean, got ${preview(element)}")
        }
    }

    /**
     * Reads the JSON string that stands for a whole value, the counterpart of [name]. There is
     * no enclosing key at this position, so the message names only the type.
     */
    fun name(element: JsonElement, type: String): String {
        val primitive = element as? JsonPrimitive
        if (element is JsonNull || primitive == null || !primitive.isString) {
            fail(type, "expects a JSON string, got ${preview(element)}")
        }
        return primitive.content
    }

    /** Reports a union arm key that names no arm of [type]. */
    fun unknownArm(type: String, arm: String): Nothing =
        fail(type, "has no arm named ${preview(JsonPrimitive(arm))}")

    /** Reports a string that names no member of the enum [type]. */
    fun unknownMember(type: String, member: String): Nothing =
        fail(type, "has no member named ${preview(JsonPrimitive(member))}")

    /**
     * Reads the discriminant of an integer-discriminated union back out of its arm key, which
     * is the letter `v` followed by the case value in base 10.
     */
    fun intArm(type: String, arm: String): Int {
        if (arm.startsWith("v")) {
            val digits = arm.substring(1)
            if (UNSIGNED_INTEGER.matches(digits)) {
                return digits.toIntOrNull() ?: unknownArm(type, arm)
            }
        }
        unknownArm(type, arm)
    }

    /**
     * Decodes a strkey with [decode], reporting a malformed one under the uniform error
     * contract rather than in whatever form the codec raises it.
     *
     * @param expected the strkey form named in the failure message, such as "a G strkey"
     */
    fun strkey(text: String, type: String, expected: String, decode: (String) -> ByteArray): ByteArray =
        try {
            decode(text)
        } catch (e: Exception) {
            fail(type, "expects $expected, got ${preview(JsonPrimitive(text))}")
        }

    /** Reads a JSON string, rejecting numbers, booleans, null and containers. */
    fun string(element: JsonElement, type: String, key: String): String {
        val primitive = primitive(element, type, key, "a JSON string")
        if (!primitive.isString) {
            fail(type, "key \"$key\" expects a JSON string, got ${preview(element)}")
        }
        return primitive.content
    }

    /**
     * Reads opaque data from lowercase hexadecimal.
     *
     * @param expectedLength exact byte count for fixed-length opaque data
     * @param maxLength declared maximum for variable-length opaque data
     */
    fun hex(
        element: JsonElement,
        type: String,
        key: String,
        expectedLength: Int? = null,
        maxLength: Int? = null
    ): ByteArray {
        val text = string(element, type, key)
        if (text.length % 2 != 0) {
            fail(type, "key \"$key\" expects hexadecimal of even length, got ${preview(element)}")
        }
        val bytes = ByteArray(text.length / 2)
        for (index in bytes.indices) {
            val high = hexDigit(text[index * 2], type, key, element)
            val low = hexDigit(text[index * 2 + 1], type, key, element)
            bytes[index] = ((high shl 4) or low).toByte()
        }
        if (expectedLength != null && bytes.size != expectedLength) {
            fail(type, "key \"$key\" expects exactly $expectedLength bytes, got ${bytes.size}")
        }
        if (maxLength != null && bytes.size > maxLength) {
            fail(type, "key \"$key\" allows at most $maxLength bytes, got ${bytes.size}")
        }
        return bytes
    }

    /** Reverses the [escapedString] ladder, returning the original bytes. */
    fun unescapeStringBytes(
        element: JsonElement,
        type: String,
        key: String,
        maxLength: Int? = null
    ): ByteArray {
        val bytes = unescape(string(element, type, key), type, key)
        if (maxLength != null && bytes.size > maxLength) {
            fail(type, "key \"$key\" allows at most $maxLength bytes, got ${bytes.size}")
        }
        return bytes
    }

    /**
     * Reverses the [escapedString] ladder, returning text in the SDK's string representation.
     *
     * The unescaped bytes are decoded as UTF-8, matching how a `String`-typed member is read from
     * the binary form. Bytes that are not valid UTF-8 become the Unicode replacement character,
     * so this path is lossless for text and lossy for arbitrary bytes; use [unescapeStringBytes]
     * where the exact bytes matter.
     */
    fun unescapeString(
        element: JsonElement,
        type: String,
        key: String,
        maxLength: Int? = null
    ): String = unescapeStringBytes(element, type, key, maxLength).decodeToString()

    /**
     * Reads an array.
     *
     * @param expectedLength exact element count for a fixed-length array
     * @param maxLength declared maximum for a variable-length array
     */
    fun array(
        element: JsonElement,
        type: String,
        key: String,
        expectedLength: Int? = null,
        maxLength: Int? = null
    ): JsonArray {
        val value = element as? JsonArray
            ?: fail(type, "key \"$key\" expects a JSON array, got ${preview(element)}")
        if (expectedLength != null && value.size != expectedLength) {
            fail(type, "key \"$key\" expects exactly $expectedLength elements, got ${value.size}")
        }
        if (maxLength != null && value.size > maxLength) {
            fail(type, "key \"$key\" allows at most $maxLength elements, got ${value.size}")
        }
        return value
    }

    /**
     * Reads the `{"<arm>": value}` form a union takes when its selected arm carries a value,
     * returning the arm key and its value. A void arm is a bare string instead and is read
     * with [string].
     */
    fun singleKeyObject(element: JsonElement, type: String): Pair<String, JsonElement> {
        val value = obj(element, type)
        if (value.size != 1) {
            fail(
                type,
                "expects an object with exactly one key naming the union arm, got ${value.size} " +
                    "keys in ${preview(element)}"
            )
        }
        val entry = value.entries.first()
        return entry.key to entry.value
    }

    /**
     * Rejects a tree nested more deeply than [RECURSION_CAP], returning [element] so a decoder
     * can check and descend in one expression. Call at the entry of a decode that starts from
     * an already-parsed tree — never per nested value, which would walk the tree once per
     * level; [parse] applies the same limit to text.
     *
     * Depth counts containers, since a container is what costs the decoder a stack frame. A
     * primitive is not a level of its own, so the two entry points admit exactly the same
     * documents: the text form counts brackets and cannot see the leaves at all.
     */
    fun checkDepth(element: JsonElement, type: String): JsonElement {
        val elements = ArrayDeque<JsonElement>()
        val depths = ArrayDeque<Int>()
        elements.addLast(element)
        depths.addLast(1)

        while (elements.isNotEmpty()) {
            val current = elements.removeLast()
            val depth = depths.removeLast()
            val children = when (current) {
                is JsonObject -> current.values
                is JsonArray -> current
                else -> continue
            }
            if (depth > RECURSION_CAP) {
                fail(type, "input nests deeper than the limit of $RECURSION_CAP")
            }
            children.forEach {
                elements.addLast(it)
                depths.addLast(depth + 1)
            }
        }
        return element
    }

    // ---------------------------------------------------------------------------------------
    // Asset codes
    // ---------------------------------------------------------------------------------------

    /**
     * Drops the trailing NUL padding an asset code carries on the wire, never going below
     * [minimumLength] bytes. The minimum is what keeps the twelve-character form
     * distinguishable from the four-character one once both have been trimmed.
     */
    fun trimAssetCode(bytes: ByteArray, minimumLength: Int): ByteArray {
        var end = bytes.size
        while (end > minimumLength && bytes[end - 1] == ZERO_BYTE) {
            end--
        }
        return bytes.copyOf(end)
    }

    /** Restores a trimmed asset code to its fixed width, rejecting one that does not fit. */
    fun padAssetCode(bytes: ByteArray, type: String, width: Int): ByteArray {
        if (bytes.size > width) {
            fail(type, "expects an asset code of at most $width bytes, got ${bytes.size}")
        }
        return bytes.copyOf(width)
    }

    // ---------------------------------------------------------------------------------------
    // Strkey payloads
    // ---------------------------------------------------------------------------------------

    /**
     * Packs the payload an M-strkey carries: the 32-byte account key followed by the
     * multiplexing id, most significant byte first. The strkey orders the two the other way
     * round from the XDR structure, which lists the id first.
     */
    fun muxedPayload(ed25519: ByteArray, id: ULong): ByteArray {
        require(ed25519.size == 32) { "Muxed account key must be 32 bytes, got ${ed25519.size}" }
        val payload = ed25519.copyOf(40)
        for (index in 39 downTo 32) {
            payload[index] = ((id shr ((39 - index) * 8)) and 0xFFuL).toByte()
        }
        return payload
    }

    /** Reads the multiplexing id back out of the 40-byte payload an M-strkey carries. */
    fun muxedId(payload: ByteArray): ULong {
        var id = 0uL
        for (index in 32 until 40) {
            id = (id shl 8) or (payload[index].toUByte().toULong())
        }
        return id
    }

    /**
     * Packs the payload a P-strkey carries: the 32-byte signer key, the payload length as four
     * bytes most significant first, the payload, and zero padding to a four-byte boundary.
     *
     * Every payload length XDR admits has a strkey, except one. The strkey codec bounds the
     * packed key at 40 to 100 bytes, which the padding satisfies for any payload of 1 to 64
     * bytes: a single byte pads to four and reaches the 40-byte floor. An empty payload pads to
     * nothing and leaves 36 bytes, so it has no P-strkey and therefore no XDR-JSON form.
     *
     * That value is refused here, by name, rather than further down in the strkey codec, whose
     * byte-count message describes the packed key and not the payload that produced it.
     */
    fun signedPayloadKey(ed25519: ByteArray, payload: ByteArray, type: String): ByteArray {
        require(ed25519.size == 32) { "Signer key must be 32 bytes, got ${ed25519.size}" }
        if (payload.isEmpty()) {
            fail(type, "has an empty signed payload, which has no strkey form")
        }
        val padded = (payload.size + 3) / 4 * 4
        val key = ByteArray(36 + padded)
        ed25519.copyInto(key)
        for (index in 32 until 36) {
            key[index] = ((payload.size shr ((35 - index) * 8)) and 0xFF).toByte()
        }
        payload.copyInto(key, 36)
        return key
    }

    /**
     * Reads the signed payload back out of the payload a P-strkey carries, rejecting a declared
     * length that disagrees with the bytes present or leaves the padding unaccounted for.
     */
    fun signedPayload(key: ByteArray, type: String): ByteArray {
        var length = 0
        for (index in 32 until 36) {
            length = (length shl 8) or (key[index].toInt() and 0xFF)
        }
        if (36 + (length + 3) / 4 * 4 != key.size) {
            fail(type, "declares a signed payload of $length bytes, which does not fit ${key.size - 36} bytes")
        }
        return key.copyOfRange(36, 36 + length)
    }

    // ---------------------------------------------------------------------------------------
    // 128-bit and 256-bit integers
    // ---------------------------------------------------------------------------------------

    fun int128ToDecimalString(hi: Long, lo: ULong): String =
        (BigInteger.fromLong(hi) * TWO_64 + BigInteger.fromULong(lo)).toString()

    fun uint128ToDecimalString(hi: ULong, lo: ULong): String =
        (BigInteger.fromULong(hi) * TWO_64 + BigInteger.fromULong(lo)).toString()

    fun int256ToDecimalString(hiHi: Long, hiLo: ULong, loHi: ULong, loLo: ULong): String =
        (
            BigInteger.fromLong(hiHi) * TWO_192 +
                BigInteger.fromULong(hiLo) * TWO_128 +
                BigInteger.fromULong(loHi) * TWO_64 +
                BigInteger.fromULong(loLo)
            ).toString()

    fun uint256ToDecimalString(hiHi: ULong, hiLo: ULong, loHi: ULong, loLo: ULong): String =
        (
            BigInteger.fromULong(hiHi) * TWO_192 +
                BigInteger.fromULong(hiLo) * TWO_128 +
                BigInteger.fromULong(loHi) * TWO_64 +
                BigInteger.fromULong(loLo)
            ).toString()

    fun decimalStringToInt128(element: JsonElement, type: String, key: String): Int128Limbs {
        val value = decimal(element, type, key, INT128_MIN, INT128_MAX, "a 128-bit signed integer")
        val magnitude = if (value.signum() < 0) value + TWO_128 else value
        return Int128Limbs(
            hi = limb(magnitude / TWO_64).toLong(),
            lo = limb(magnitude % TWO_64)
        )
    }

    fun decimalStringToUInt128(element: JsonElement, type: String, key: String): UInt128Limbs {
        val value = decimal(
            element, type, key, BigInteger.ZERO, UINT128_MAX, "a 128-bit unsigned integer"
        )
        return UInt128Limbs(hi = limb(value / TWO_64), lo = limb(value % TWO_64))
    }

    fun decimalStringToInt256(element: JsonElement, type: String, key: String): Int256Limbs {
        val value = decimal(element, type, key, INT256_MIN, INT256_MAX, "a 256-bit signed integer")
        val magnitude = if (value.signum() < 0) value + TWO_256 else value
        return Int256Limbs(
            hiHi = limb(magnitude / TWO_192).toLong(),
            hiLo = limb(magnitude / TWO_128 % TWO_64),
            loHi = limb(magnitude / TWO_64 % TWO_64),
            loLo = limb(magnitude % TWO_64)
        )
    }

    fun decimalStringToUInt256(element: JsonElement, type: String, key: String): UInt256Limbs {
        val value = decimal(
            element, type, key, BigInteger.ZERO, UINT256_MAX, "a 256-bit unsigned integer"
        )
        return UInt256Limbs(
            hiHi = limb(value / TWO_192),
            hiLo = limb(value / TWO_128 % TWO_64),
            loHi = limb(value / TWO_64 % TWO_64),
            loLo = limb(value % TWO_64)
        )
    }

    // ---------------------------------------------------------------------------------------
    // Errors
    // ---------------------------------------------------------------------------------------

    fun fail(type: String, detail: String): Nothing =
        throw IllegalArgumentException("$type: $detail")

    /**
     * Renders an untrusted value for an error message: truncated, with control bytes escaped so
     * a hostile document cannot inject line breaks or terminal control sequences into a log.
     */
    fun preview(element: JsonElement): String {
        val rendered = encodeToString(element)
        val escaped = buildString(rendered.length) {
            for (character in rendered) {
                val code = character.code
                if (code < 0x20 || code == 0x7F) {
                    append("\\x")
                    append(HEX_DIGITS[(code shr 4) and 0x0F])
                    append(HEX_DIGITS[code and 0x0F])
                } else {
                    append(character)
                }
            }
        }
        return if (escaped.length <= PREVIEW_LIMIT) escaped else escaped.take(PREVIEW_LIMIT) + "..."
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    /**
     * Names the keys of [value] that no field of [type] declares, in the order the document
     * lists them. Reached only once a key is known to be unknown, so the common decode never
     * pays for building this list.
     */
    private fun rejectUnknownKeys(
        value: JsonObject,
        declaredKeys: Array<String>,
        type: String
    ): Nothing {
        val unknown = value.keys.filter { !declaredKeys.contains(it) }
        val named = unknown.take(UNKNOWN_KEY_LIMIT)
            .joinToString(", ") { preview(JsonPrimitive(it)) }
        val remainder = unknown.size - UNKNOWN_KEY_LIMIT
        val suffix = if (remainder > 0) " and $remainder more" else ""
        val subject = if (unknown.size == 1) "the unknown key" else "the unknown keys"
        fail(type, "has $subject $named$suffix")
    }

    /**
     * Encodes bytes as lowercase hexadecimal against the same digit table the escape ladder and
     * the error preview use, into a single pre-sized buffer rather than one string per byte.
     */
    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value shr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

    private fun escape(bytes: ByteArray): String = buildString(bytes.size) {
        for (byte in bytes) {
            when (val value = byte.toInt() and 0xFF) {
                0x00 -> append("\\0")
                0x09 -> append("\\t")
                0x0A -> append("\\n")
                0x0D -> append("\\r")
                0x5C -> append("\\\\")
                in 0x20..0x7E -> append(value.toChar())
                else -> {
                    append("\\x")
                    append(HEX_DIGITS[value shr 4])
                    append(HEX_DIGITS[value and 0x0F])
                }
            }
        }
    }

    private fun unescape(text: String, type: String, key: String): ByteArray {
        val bytes = ArrayList<Byte>(text.length)
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character != '\\') {
                val code = character.code
                if (code !in 0x20..0x7E) {
                    fail(
                        type,
                        "key \"$key\" contains the unescaped character U+" +
                            code.toString(16).uppercase().padStart(4, '0') +
                            "; bytes outside the printable range must use an escape"
                    )
                }
                bytes.add(code.toByte())
                index++
                continue
            }

            if (index + 1 >= text.length) {
                fail(type, "key \"$key\" ends with a trailing backslash")
            }
            when (val escape = text[index + 1]) {
                '0' -> { bytes.add(0x00); index += 2 }
                't' -> { bytes.add(0x09); index += 2 }
                'n' -> { bytes.add(0x0A); index += 2 }
                'r' -> { bytes.add(0x0D); index += 2 }
                '\\' -> { bytes.add(0x5C); index += 2 }
                'x' -> {
                    if (index + 3 >= text.length) {
                        fail(type, "key \"$key\" has a truncated \\xNN escape")
                    }
                    val high = strictHexDigit(text[index + 2], type, key)
                    val low = strictHexDigit(text[index + 3], type, key)
                    bytes.add(((high shl 4) or low).toByte())
                    index += 4
                }
                else -> fail(type, "key \"$key\" contains the unrecognised escape \"\\$escape\"")
            }
        }
        return bytes.toByteArray()
    }

    private fun primitive(
        element: JsonElement,
        type: String,
        key: String,
        expected: String
    ): JsonPrimitive {
        if (element is JsonNull) {
            fail(type, "key \"$key\" expects $expected, got null")
        }
        return element as? JsonPrimitive
            ?: fail(type, "key \"$key\" expects $expected, got ${preview(element)}")
    }

    /**
     * Returns the literal text of an integer. 64-bit values are emitted as strings and accept
     * either form; 32-bit values accept only a JSON number.
     */
    private fun numberText(
        element: JsonElement,
        type: String,
        key: String,
        expected: String,
        allowString: Boolean
    ): String {
        val primitive = primitive(element, type, key, expected)
        if (primitive.isString && !allowString) {
            fail(
                type,
                "key \"$key\" expects $expected as a JSON number, got the string ${preview(element)}"
            )
        }
        return primitive.content
    }

    private fun requireLiteral(
        pattern: Regex,
        text: String,
        type: String,
        key: String,
        expected: String
    ) {
        if (!pattern.matches(text)) {
            fail(type, "key \"$key\" expects $expected in base 10, got \"${truncate(text)}\"")
        }
    }

    private fun outOfRange(type: String, key: String, text: String, expected: String): Nothing =
        fail(type, "key \"$key\" value \"${truncate(text)}\" is out of range for $expected")

    private fun decimal(
        element: JsonElement,
        type: String,
        key: String,
        minimum: BigInteger,
        maximum: BigInteger,
        expected: String
    ): BigInteger {
        val text = string(element, type, key)
        requireLiteral(SIGNED_INTEGER, text, type, key, expected)
        // Reject an over-long literal before parsing it. The range check alone would also
        // reject it, but only after building an arbitrarily large integer from untrusted input.
        val digitLimit = maxOf(minimum.toString().length, maximum.toString().length)
        if (text.length > digitLimit) {
            outOfRange(type, key, text, expected)
        }
        val value = BigInteger.parseString(text, 10)
        if (value < minimum || value > maximum) {
            outOfRange(type, key, text, expected)
        }
        return value
    }

    /** Narrows a value already reduced to one 64-bit limb. */
    private fun limb(value: BigInteger): ULong = value.ulongValue(exactRequired = true)

    private fun hexDigit(character: Char, type: String, key: String, element: JsonElement): Int =
        when (character) {
            in '0'..'9' -> character - '0'
            in 'a'..'f' -> character - 'a' + 10
            else -> fail(
                type,
                "key \"$key\" expects lowercase hexadecimal, got ${preview(element)}"
            )
        }

    private fun strictHexDigit(character: Char, type: String, key: String): Int =
        when (character) {
            in '0'..'9' -> character - '0'
            in 'a'..'f' -> character - 'a' + 10
            else -> fail(
                type,
                "key \"$key\" has a \\xNN escape with the non-lowercase-hexadecimal digit " +
                    "\"$character\""
            )
        }

    private fun truncate(text: String): String =
        if (text.length <= PREVIEW_LIMIT) text else text.take(PREVIEW_LIMIT) + "..."

    /**
     * Establishes, on the raw text and before the JSON parser builds a tree, the two properties
     * the parser itself does not: that the document nests no deeper than [RECURSION_CAP], and
     * that no object repeats a key.
     *
     * Both checks need the text rather than the tree. Depth must be known before the parser
     * recurses into an over-deep document, and a duplicate key is unobservable afterwards
     * because [JsonObject] is a map: the parser keeps the last occurrence and the earlier value
     * disappears silently. SEP-0051 fixes one document per value, so a repeated key names two
     * values where the format admits one and is refused rather than resolved.
     *
     * The scan is a single forward pass and never recurses, since a guard that recursed could
     * exhaust the stack on the very input it exists to reject. Structure inside a string literal
     * is text, not structure, so each literal is consumed whole.
     *
     * Text that is not valid JSON is left to the parser to report; this scan establishes its two
     * properties and nothing more.
     */
    private fun checkText(text: String, type: String) {
        // One frame per open container, indexed by depth - 1. An object frame allocates its key
        // set on the first key it carries, so an empty object and every array cost nothing.
        val objectFrame = BooleanArray(RECURSION_CAP)
        val frameKeys = arrayOfNulls<MutableSet<String>>(RECURSION_CAP)
        var depth = 0
        var index = 0

        while (index < text.length) {
            when (text[index]) {
                '"' -> {
                    val end = endOfStringLiteral(text, index)
                    if (depth > 0 && objectFrame[depth - 1] && followedByColon(text, end)) {
                        val key = unescapedText(text, index, end)
                        val keys = frameKeys[depth - 1]
                            ?: HashSet<String>().also { frameKeys[depth - 1] = it }
                        if (!keys.add(key)) {
                            fail(type, "repeats the key ${preview(JsonPrimitive(key))}")
                        }
                    }
                    index = end
                }
                '{', '[' -> {
                    depth++
                    if (depth > RECURSION_CAP) {
                        fail(type, "input nests deeper than the limit of $RECURSION_CAP")
                    }
                    objectFrame[depth - 1] = text[index] == '{'
                    frameKeys[depth - 1] = null
                    index++
                }
                '}', ']' -> {
                    if (depth > 0) {
                        // Released rather than pooled, so the next object at this depth starts
                        // empty without paying to clear a set an earlier one may have grown.
                        frameKeys[depth - 1] = null
                        depth--
                    }
                    index++
                }
                else -> index++
            }
        }
    }

    /**
     * The index one past the closing quote of the string literal opening at [start], or the end
     * of [text] if the literal is unterminated. A backslash consumes the character after it, so
     * an escaped quote does not end the literal.
     */
    private fun endOfStringLiteral(text: String, start: Int): Int {
        var index = start + 1
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index += 2
                '"' -> return index + 1
                else -> index++
            }
        }
        return text.length
    }

    /** Whether the next non-whitespace character at or after [index] is `:`, marking a key. */
    private fun followedByColon(text: String, index: Int): Boolean {
        var probe = index
        while (probe < text.length) {
            when (text[probe]) {
                ' ', '\t', '\n', '\r' -> probe++
                else -> return text[probe] == ':'
            }
        }
        return false
    }

    /**
     * Resolves the escapes of the string literal spanning [start] until [end], so two keys
     * written differently but denoting the same text compare equal. Comparison is by UTF-16 code
     * unit, which is all equality needs: a surrogate pair written either as one character or as
     * its two `\uXXXX` escapes yields the same units in the same order.
     *
     * An escape the format does not define is malformed input, which the parser reports; the
     * marker character stands for itself here so the scan can finish the pass.
     *
     * Called only for a literal that closed, which is what makes a backslash safe to read past:
     * an unpaired backslash before the closing quote would have made [endOfStringLiteral] skip
     * that quote and keep looking, so every backslash in the span has its escaped character
     * inside the span too.
     */
    private fun unescapedText(text: String, start: Int, end: Int): String {
        val from = start + 1
        val until = end - 1
        if (until <= from) {
            return ""
        }

        var index = from
        while (index < until && text[index] != '\\') {
            index++
        }
        if (index == until) {
            return text.substring(from, until)
        }

        val resolved = StringBuilder(until - from)
        resolved.append(text, from, index)
        while (index < until) {
            val character = text[index]
            if (character != '\\') {
                resolved.append(character)
                index++
                continue
            }
            index++
            val marker = text[index]
            index++
            when (marker) {
                'b' -> resolved.append('\b')
                'f' -> resolved.append('\u000C')
                'n' -> resolved.append('\n')
                'r' -> resolved.append('\r')
                't' -> resolved.append('\t')
                'u' -> {
                    val code = if (index + 4 <= until) hexCode(text, index) else null
                    if (code == null) {
                        resolved.append(marker)
                    } else {
                        resolved.append(code.toChar())
                        index += 4
                    }
                }
                else -> resolved.append(marker)
            }
        }
        return resolved.toString()
    }

    /** The four hexadecimal digits of a `\uXXXX` escape at [index], or null if they are not. */
    private fun hexCode(text: String, index: Int): Int? {
        var code = 0
        for (offset in 0 until 4) {
            val digit = when (val character = text[index + offset]) {
                in '0'..'9' -> character - '0'
                in 'a'..'f' -> character - 'a' + 10
                in 'A'..'F' -> character - 'A' + 10
                else -> return null
            }
            code = (code shl 4) or digit
        }
        return code
    }
}

/** The two limbs of a 128-bit signed integer: a signed high half and an unsigned low half. */
internal data class Int128Limbs(val hi: Long, val lo: ULong)

/** The two limbs of a 128-bit unsigned integer. */
internal data class UInt128Limbs(val hi: ULong, val lo: ULong)

/** The four limbs of a 256-bit signed integer; only the most significant limb is signed. */
internal data class Int256Limbs(
    val hiHi: Long,
    val hiLo: ULong,
    val loHi: ULong,
    val loLo: ULong
)

/** The four limbs of a 256-bit unsigned integer. */
internal data class UInt256Limbs(
    val hiHi: ULong,
    val hiLo: ULong,
    val loHi: ULong,
    val loLo: ULong
)
